package com.gtceuterminal.common.network;

import com.gtceuterminal.common.item.MultiStructureManagerItem;
import com.gtceuterminal.common.item.SchematicInterfaceItem;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CPacketComponentUpgrade {

    private List<BlockPos> positions = new ArrayList<>();
    private final int targetTier;
    private final String targetUpgradeId; // optional (e.g. maintenance hatch variant)
    private final BlockPos controllerPos;
    private ServerLevel level;

    // Main constructor used by decode
    public CPacketComponentUpgrade(List<BlockPos> positions, int targetTier, String targetUpgradeId, BlockPos controllerPos) {
        this.positions = positions;
        this.targetTier = targetTier;
        this.targetUpgradeId = targetUpgradeId;
        this.controllerPos = controllerPos;
    }

    // Convenient constructor for a single component
    public CPacketComponentUpgrade(BlockPos position, int targetTier, String targetUpgradeId, BlockPos controllerPos) {
        this.positions = new ArrayList<>();
        this.positions.add(position);
        this.targetTier = targetTier;
        this.targetUpgradeId = targetUpgradeId;
        this.controllerPos = controllerPos;
    }

    // Backward compatible constructors (tier-only)
    public CPacketComponentUpgrade(BlockPos position, int targetTier, BlockPos controllerPos) {
        this(position, targetTier, null, controllerPos);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
        buf.writeInt(targetTier);
        buf.writeBlockPos(controllerPos);

        // optional upgradeId
        boolean hasId = targetUpgradeId != null && !targetUpgradeId.isBlank();
        buf.writeBoolean(hasId);
        if (hasId) buf.writeUtf(targetUpgradeId);
    }

    public static CPacketComponentUpgrade decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        int targetTier = buf.readInt();
        BlockPos controllerPos = buf.readBlockPos();

        String upgradeId = null;
        // If older client/server mismatch happens, this may throw, but protocol is controlled by mod.
        boolean hasId = buf.readBoolean();
        if (hasId) {
            upgradeId = buf.readUtf(32767);
        }

        return new CPacketComponentUpgrade(positions, targetTier, upgradeId, controllerPos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Find wireless terminal in player's hands or inventory
            ItemStack wirelessTerminal = findWirelessTerminal(player);

            boolean upgradedCoils = false;

            int upgraded = 0;
            int failed = 0;

            for (BlockPos pos : positions) {
                var state = player.level().getBlockState(pos);
                var block = state.getBlock();

                ComponentType type = detectComponentType(block);
                int currentTier = detectTier(block);

                boolean hasId = targetUpgradeId != null && !targetUpgradeId.isBlank();
                if (type == null && !hasId) {
                    failed++;
                    continue;
                }

                if (type == ComponentType.COIL) {
                }

                ComponentInfo component = new ComponentInfo(type, currentTier, pos, state);

                // Pass wireless terminal to upgrader
                ComponentUpgrader.UpgradeResult result = ComponentUpgrader.upgradeComponent(
                        component,
                        targetTier,
                        targetUpgradeId,
                        player,
                        player.level(),
                        true,
                        wirelessTerminal
                );

                if (result.success) {
                    upgraded++;
                    if (type == ComponentType.COIL) upgradedCoils = true;
                } else {
                    failed++;
                }
            }

            // Send feedback
            if (upgraded > 0) {
                player.displayClientMessage(
                        Component.literal("§aUpgraded " + upgraded + " component(s)"),
                        true
                );
                player.playSound(SoundEvents.ANVIL_USE, 1.0F, 1.0F);
            }

            if (upgradedCoils) {
                refreshController((ServerLevel) player.level(), controllerPos);
            }

            if (upgraded > 0) {
                player.displayClientMessage(
                        Component.literal("§6⚠ Items may be voided or dropped when upgrading/downgrading components"),
                        false
                );
            }

            if (failed > 0) {
                player.displayClientMessage(
                        Component.literal("§cFailed to upgrade " + failed + " component(s)"),
                        true
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    //
    private static void refreshController(ServerLevel level, BlockPos controllerPos) {
        var be = level.getBlockEntity(controllerPos);
        if (be != null) {
            try {
                // MetaMachineBlockEntity#getMetaMachine()
                var getMetaMachine = be.getClass().getMethod("getMetaMachine");
                Object mm = getMetaMachine.invoke(be);

                if (mm != null) {

                    //
                    Object lock = null;
                    try {
                        var getLock = mm.getClass().getMethod("getPatternLock");
                        lock = getLock.invoke(mm);
                        if (lock != null) {
                            var lockM = lock.getClass().getMethod("lock");
                            lockM.invoke(lock);
                        }
                    } catch (NoSuchMethodException ignored) {}

                    try {
                        // 1) invalidate structure to force re-check
                        try {
                            var invalidate = mm.getClass().getMethod("invalidateStructure");
                            invalidate.invoke(mm);
                        } catch (NoSuchMethodException ignored) {}

                        // 2) re-check pattern
                        boolean formed = false;
                        try {
                            var check = mm.getClass().getMethod("checkStructurePattern");
                            Object r = check.invoke(mm);
                            formed = (r instanceof Boolean b) ? b : true;
                        } catch (NoSuchMethodException ignored) {
                            try {
                                var check2 = mm.getClass().getMethod("checkPattern");
                                Object r2 = check2.invoke(mm);
                                formed = (r2 instanceof Boolean b) ? b : true;
                            } catch (NoSuchMethodException ignored2) {}
                        }

                        // 3) if formed, call onFormed, otherwise onInvalid
                        if (formed) {
                            try {
                                var onFormed = mm.getClass().getMethod("onStructureFormed");
                                onFormed.invoke(mm);
                            } catch (NoSuchMethodException ignored) {}
                        } else {
                            // if not formed, we call onStructureInvalidated
                            try {
                                var onInv = mm.getClass().getMethod("onStructureInvalid");
                                onInv.invoke(mm);
                            } catch (NoSuchMethodException ignored) {
                                try {
                                    var onInv2 = mm.getClass().getMethod("onStructureInvalidated");
                                    onInv2.invoke(mm);
                                } catch (NoSuchMethodException ignored2) {}
                            }
                        }

                        return;

                    } finally {
                        // unlock if we locked
                        if (lock != null) {
                            try {
                                var unlockM = lock.getClass().getMethod("unlock");
                                unlockM.invoke(lock);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // hard fallback
        var state = level.getBlockState(controllerPos);
        level.sendBlockUpdated(controllerPos, state, state, 3);
        level.updateNeighborsAt(controllerPos, state.getBlock());
        level.blockUpdated(controllerPos, state.getBlock());
    }

    private ItemStack findWirelessTerminal(ServerPlayer player) {
        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof MultiStructureManagerItem ||
                mainHand.getItem() instanceof SchematicInterfaceItem) {
            return mainHand;
        }

        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof MultiStructureManagerItem ||
                offHand.getItem() instanceof SchematicInterfaceItem) {
            return offHand;
        }

        // Check inventory
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MultiStructureManagerItem ||
                    stack.getItem() instanceof SchematicInterfaceItem) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private ComponentType detectComponentType(net.minecraft.world.level.block.Block block) {
        String blockId = block.builtInRegistryHolder().key().location().toString().toLowerCase();

        // WIRELESS COMPONENTS
        if (blockId.contains("wireless")) {
            if (blockId.contains("energy")) {
                if (blockId.contains("input")) return ComponentType.WIRELESS_ENERGY_INPUT;
                if (blockId.contains("output")) return ComponentType.WIRELESS_ENERGY_OUTPUT;
            }
            if (blockId.contains("laser")) {
                if (blockId.contains("target") || blockId.contains("input")) return ComponentType.WIRELESS_LASER_INPUT;
                if (blockId.contains("source") || blockId.contains("output")) return ComponentType.WIRELESS_LASER_OUTPUT;
            }
        }

        // SUBSTATION HATCHES
        if (blockId.contains("substation")) {
            if (blockId.contains("input")) return ComponentType.SUBSTATION_INPUT_ENERGY;
            if (blockId.contains("output")) return ComponentType.SUBSTATION_OUTPUT_ENERGY;
        }

        // LASER HATCHES
        if (blockId.contains("laser")) {
            if (blockId.contains("target") || blockId.contains("input")) return ComponentType.INPUT_LASER;
            if (blockId.contains("source") || blockId.contains("output")) return ComponentType.OUTPUT_LASER;
        }

        // Energy hatches
        if (blockId.contains("energy") && blockId.contains("input")) return ComponentType.ENERGY_HATCH;
        if (blockId.contains("dynamo")) return ComponentType.DYNAMO_HATCH;
        if (blockId.contains("energy") && blockId.contains("output")) return ComponentType.DYNAMO_HATCH;

        // Coils
        if (blockId.contains("coil")) return ComponentType.COIL;

        // Fluid hatches
        if (blockId.contains("quadruple") && blockId.contains("input"))
            return ComponentType.QUAD_INPUT_HATCH;
        if (blockId.contains("quadruple") && blockId.contains("output"))
            return ComponentType.QUAD_OUTPUT_HATCH;
        if (blockId.contains("nonuple") && blockId.contains("input"))
            return ComponentType.NONUPLE_INPUT_HATCH;
        if (blockId.contains("nonuple") && blockId.contains("output"))
            return ComponentType.NONUPLE_OUTPUT_HATCH;

        // General fluid hatches (after specific types)
        if (blockId.contains("input_hatch")) return ComponentType.INPUT_HATCH;
        if (blockId.contains("output_hatch")) return ComponentType.OUTPUT_HATCH;

        // Item buses
        if (blockId.contains("input_bus")) return ComponentType.INPUT_BUS;
        if (blockId.contains("output_bus")) return ComponentType.OUTPUT_BUS;

        // Special hatches
        if (blockId.contains("maintenance")) return ComponentType.MAINTENANCE;
        if (blockId.contains("muffler")) return ComponentType.MUFFLER;
        if (blockId.contains("parallel")) return ComponentType.PARALLEL_HATCH;

        return null;
    }

    private int detectTier(net.minecraft.world.level.block.Block block) {
        String blockId = block.builtInRegistryHolder().key().location().toString().toLowerCase();

        // Standard voltage tiers
        if (blockId.contains("ulv")) return 0;
        if (blockId.contains("lv")) return 1;
        if (blockId.contains("mv")) return 2;
        if (blockId.contains("hv")) return 3;
        if (blockId.contains("ev")) return 4;
        if (blockId.contains("iv")) return 5;
        if (blockId.contains("luv")) return 6;
        if (blockId.contains("zpm")) return 7;
        if (blockId.contains("uv")) return 8;
        if (blockId.contains("uhv")) return 9;
        if (blockId.contains("uev")) return 10;
        if (blockId.contains("uiv")) return 11;
        if (blockId.contains("uxv")) return 12;
        if (blockId.contains("opv")) return 13;
        if (blockId.contains("max")) return 14;

        // Coil tiers
        if (blockId.contains("cupronickel")) return 0;
        if (blockId.contains("kanthal")) return 1;
        if (blockId.contains("nichrome")) return 2;
        if (blockId.contains("rtm_alloy")) return 3;
        if (blockId.contains("hssg")) return 4;
        if (blockId.contains("naquadah")) return 5;
        if (blockId.contains("trinium")) return 6;
        if (blockId.contains("tritanium")) return 7;

        return 0;
    }
}