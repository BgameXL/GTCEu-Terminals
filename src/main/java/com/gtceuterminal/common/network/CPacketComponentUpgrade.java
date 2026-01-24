package com.gtceuterminal.common.network;

import com.gtceuterminal.common.item.MultiStructureManagerItem;
import com.gtceuterminal.common.item.SchematicInterfaceItem;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

    // Main constructor used by decode
    public CPacketComponentUpgrade(List<BlockPos> positions, int targetTier) {
        this.positions = positions;
        this.targetTier = targetTier;
    }

    // Convenient constructor for a single component
    public CPacketComponentUpgrade(BlockPos position, int targetTier, BlockPos controllerPos) {
        this.positions = new ArrayList<>();
        this.positions.add(position);  // IMPORTANTE: Agregar la posición
        this.targetTier = targetTier;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
        buf.writeInt(targetTier);
    }

    public static CPacketComponentUpgrade decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        int targetTier = buf.readInt();
        return new CPacketComponentUpgrade(positions, targetTier);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Find wireless terminal in player's hands or inventory
            ItemStack wirelessTerminal = findWirelessTerminal(player);

            int upgraded = 0;
            int failed = 0;

            for (BlockPos pos : positions) {
                var state = player.level().getBlockState(pos);
                var block = state.getBlock();

                ComponentType type = detectComponentType(block);
                int currentTier = detectTier(block);

                if (type == null) {
                    failed++;
                    continue;
                }

                ComponentInfo component = new ComponentInfo(type, currentTier, pos, state);

                // Pass wireless terminal to upgrader
                ComponentUpgrader.UpgradeResult result = ComponentUpgrader.upgradeComponent(
                        component,
                        targetTier,
                        player,
                        player.level(),
                        true,
                        wirelessTerminal
                );

                if (result.success) upgraded++;
                else failed++;
            }

            // Send feedback
            if (upgraded > 0) {
                player.displayClientMessage(
                        Component.literal("§aUpgraded " + upgraded + " component(s)"),
                        true
                );
                player.playSound(SoundEvents.ANVIL_USE, 1.0F, 1.0F);
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

        // Energy hatches
        if (blockId.contains("energy") && blockId.contains("input")) return ComponentType.ENERGY_HATCH;
        if (blockId.contains("dynamo")) return ComponentType.DYNAMO_HATCH;
        if (blockId.contains("energy") && blockId.contains("output")) return ComponentType.DYNAMO_HATCH;

        // Coils
        if (blockId.contains("coil")) return ComponentType.COIL;

        // Fluid hatches
        if (blockId.contains("input_hatch") && !blockId.contains("quadruple") && !blockId.contains("nonuple"))
            return ComponentType.INPUT_HATCH;
        if (blockId.contains("output_hatch") && !blockId.contains("quadruple") && !blockId.contains("nonuple"))
            return ComponentType.OUTPUT_HATCH;
        if (blockId.contains("quadruple") && blockId.contains("input"))
            return ComponentType.QUAD_INPUT_HATCH;
        if (blockId.contains("quadruple") && blockId.contains("output"))
            return ComponentType.QUAD_OUTPUT_HATCH;
        if (blockId.contains("nonuple") && blockId.contains("input"))
            return ComponentType.NONUPLE_INPUT_HATCH;
        if (blockId.contains("nonuple") && blockId.contains("output"))
            return ComponentType.NONUPLE_OUTPUT_HATCH;

        // Item buses
        if (blockId.contains("input_bus")) return ComponentType.INPUT_BUS;
        if (blockId.contains("output_bus")) return ComponentType.OUTPUT_BUS;

        // Maintenance
        if (blockId.contains("maintenance")) return ComponentType.MAINTENANCE;

        // Muffler
        if (blockId.contains("muffler")) return ComponentType.MUFFLER;

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