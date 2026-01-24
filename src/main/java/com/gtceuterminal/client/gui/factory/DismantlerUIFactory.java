package com.gtceuterminal.client.gui.factory;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.multiblock.DismantleScanner;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * IMPORTANT:
 * This class must be safe to load on a dedicated server.
 * Do NOT reference net.minecraft.client.* or client-only GUI classes directly.
 * UI creation is done via reflection (only executed on the client).
 */
public class DismantlerUIFactory extends UIFactory<DismantlerUIFactory.DismantlerHolder> {

    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEUTerminalMod.MOD_ID, "dismantler"
    );

    public static final DismantlerUIFactory INSTANCE = new DismantlerUIFactory();

    private DismantlerUIFactory() {
        super(UI_ID);
    }

    public ResourceLocation getUIName() {
        return UI_ID;
    }

    @Override
    protected ModularUI createUITemplate(DismantlerHolder holder, Player entityPlayer) {
        // Ensure we have a player/level on the client before building UI
        holder.attach(entityPlayer);

        try {
            // Reflect to avoid linking client-only GUI classes on dedicated server
            Class<?> uiClass = Class.forName("com.gtceuterminal.client.gui.multiblock.DismantlerUI");
            var m = uiClass.getMethod("create", DismantlerHolder.class, Player.class);
            Object result = m.invoke(null, holder, entityPlayer);
            return (ModularUI) result;
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("Failed to create Dismantler UI (reflection)", t);
            throw new RuntimeException(t);
        }
    }

    @Override
    protected DismantlerHolder readHolderFromSyncData(FriendlyByteBuf syncData) {
        BlockPos pos = syncData.readBlockPos();
        return new DismantlerHolder(pos, true);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, DismantlerHolder holder) {
        syncData.writeBlockPos(holder.getControllerPos());
    }

     // Server-side entry point. Creates a holder and opens the UI for the given player.
    public void openUI(ServerPlayer player, BlockPos controllerPos) {
        if (player == null) return;

        // Validate multiblock on server before opening UI (optional but helpful)
        try {
            BlockEntity be = player.level().getBlockEntity(controllerPos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity mbe) {
                var metaMachine = mbe.getMetaMachine();
                if (metaMachine instanceof MultiblockControllerMachine controller) {
                    if (!controller.isFormed()) {
                        GTCEUTerminalMod.LOGGER.warn("Dismantler UI requested but multiblock is not formed at {}", controllerPos);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.warn("Dismantler UI validation failed at {} (continuing to open UI)", controllerPos, t);
        }

        DismantlerHolder holder = new DismantlerHolder(controllerPos, false);
        this.openUI(holder, player);
    }

    public static class DismantlerHolder implements IUIHolder {
        private final BlockPos controllerPos;
        private final boolean remote;

        // Attached on whichever side actually builds the UI (client)
        private Player player;
        private Level level;

        private DismantleScanner.ScanResult scanResult;

        public DismantlerHolder(BlockPos controllerPos, boolean remote) {
            this.controllerPos = controllerPos;
            this.remote = remote;
        }

        public void attach(Player entityPlayer) {
            if (this.player == null) this.player = entityPlayer;
            if (this.level == null && entityPlayer != null) this.level = entityPlayer.level();
        }

        @Override
        public ModularUI createUI(Player entityPlayer) {
            attach(entityPlayer);
            return INSTANCE.createUITemplate(this, entityPlayer);
        }

        @Override
        public boolean isInvalid() {
            return player != null && player.isRemoved();
        }

        @Override
        public boolean isRemote() {
            return remote;
        }

        @Override
        public void markAsDirty() {
        }

        public Player getPlayer() {
            return player;
        }

        public Level getLevel() {
            return level;
        }

        public BlockPos getControllerPos() {
            return controllerPos;
        }

        public DismantleScanner.ScanResult getScanResult() {
            if (scanResult == null) {
                if (level == null) {
                    GTCEUTerminalMod.LOGGER.warn("DismantlerHolder.getScanResult called before attach(); returning empty result");
                    return DismantleScanner.ScanResult.empty();
                }

                var blockEntity = level.getBlockEntity(controllerPos);
                if (blockEntity instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity mbe) {
                    var metaMachine = mbe.getMetaMachine();
                    if (metaMachine instanceof MultiblockControllerMachine controller) {
                        scanResult = DismantleScanner.scanMultiblock(level, controller);
                    } else {
                        GTCEUTerminalMod.LOGGER.error("MetaMachine is not a controller at {}", controllerPos);
                        scanResult = DismantleScanner.ScanResult.empty();
                    }
                } else {
                    GTCEUTerminalMod.LOGGER.error("BlockEntity is not a machine at {}", controllerPos);
                    scanResult = DismantleScanner.ScanResult.empty();
                }
            }
            return scanResult;
        }
    }
}