package com.gtceuterminal.client.gui.factory;

import com.gtceuterminal.GTCEUTerminalMod;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * IMPORTANT:
 * This class must be safe to load on a dedicated server.
 * Do NOT reference net.minecraft.client.* or client-only GUI classes directly.
 * UI creation is done via reflection (only executed on the client).
 */
public class MultiStructureUIFactory extends UIFactory<MultiStructureUIFactory.MultiStructureHolder> {

    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEUTerminalMod.MOD_ID, "multi_structure_manager"
    );

    public static final MultiStructureUIFactory INSTANCE = new MultiStructureUIFactory();

    private MultiStructureUIFactory() {
        super(UI_ID);
    }

    public ResourceLocation getUIName() {
        return UI_ID;
    }

    @Override
    protected ModularUI createUITemplate(MultiStructureHolder holder, Player entityPlayer) {
        GTCEUTerminalMod.LOGGER.info("createUITemplate called");
        holder.attach(entityPlayer);
        try {
            // Load MultiStructureManagerUI using reflection
            Class<?> uiClass = Class.forName("com.gtceuterminal.client.gui.multiblock.MultiStructureManagerUI");
            var m = uiClass.getMethod("create", MultiStructureHolder.class, Player.class);
            Object result = m.invoke(null, holder, entityPlayer);
            GTCEUTerminalMod.LOGGER.info("UI created successfully");
            return (ModularUI) result;
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("Failed to create Multi-Structure Manager UI", t);
            throw new RuntimeException("Failed to create Multi-Structure Manager UI", t);
        }
    }

    @Override
    protected MultiStructureHolder readHolderFromSyncData(FriendlyByteBuf syncData) {
        return new MultiStructureHolder(true);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, MultiStructureHolder holder) {
        // No data to write
    }


     // Open UI for a server player

    public void openUI(ServerPlayer player) {
        if (player == null) {
            GTCEUTerminalMod.LOGGER.error("Cannot open UI: player is null");
            return;
        }
        GTCEUTerminalMod.LOGGER.info("Opening Multi-Structure Manager UI for player: {}", player.getName().getString());
        MultiStructureHolder holder = new MultiStructureHolder(false);
        super.openUI(holder, player);  // LDLib handles everything from here
    }

    public static class MultiStructureHolder implements IUIHolder {
        private final boolean remote;
        private Player player;

        public MultiStructureHolder(boolean remote) {
            this.remote = remote;
        }

        public void attach(Player entityPlayer) {
            if (this.player == null) {
                this.player = entityPlayer;
            }
        }

        public Player getPlayer() {
            return player;
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

        public ResourceLocation getUIName() {
            return UI_ID;
        }

        public ItemStack getTerminalItem() {
            return null;
        }
    }
}