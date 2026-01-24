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
public class SchematicUIFactory extends UIFactory<SchematicUIFactory.SchematicHolder> {

    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEUTerminalMod.MOD_ID, "schematic_interface"
    );

    public static final SchematicUIFactory INSTANCE = new SchematicUIFactory();

    private SchematicUIFactory() {
        super(UI_ID);
    }

    public ResourceLocation getUIName() {
        return UI_ID;
    }

    @Override
    protected ModularUI createUITemplate(SchematicHolder holder, Player entityPlayer) {
        GTCEUTerminalMod.LOGGER.info("createUITemplate called");
        holder.attach(entityPlayer);
        try {
            // Load SchematicInterfaceUI using reflection
            Class<?> uiClass = Class.forName("com.gtceuterminal.client.gui.multiblock.SchematicInterfaceUI");
            var ctor = uiClass.getConstructor(SchematicHolder.class, Player.class);
            Object uiObj = ctor.newInstance(holder, entityPlayer);
            var create = uiClass.getMethod("createUI");
            Object result = create.invoke(uiObj);
            GTCEUTerminalMod.LOGGER.info("UI created successfully");
            return (ModularUI) result;
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("Failed to create Schematic Interface UI", t);
            throw new RuntimeException("Failed to create Schematic UI", t);
        }
    }

    @Override
    protected SchematicHolder readHolderFromSyncData(FriendlyByteBuf syncData) {
        return new SchematicHolder(true);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, SchematicHolder holder) {
        // No extra data needed
    }

     // Open UI for a server player.

    public void openUI(ServerPlayer player) {
        if (player == null) {
            GTCEUTerminalMod.LOGGER.error("Cannot open UI: player is null");
            return;
        }
        GTCEUTerminalMod.LOGGER.info("Opening Schematic UI for player: {}", player.getName().getString());
        SchematicHolder holder = new SchematicHolder(false);
        super.openUI(holder, player);  // LDLib handles everything from here
    }

    public static class SchematicHolder implements IUIHolder {
        private final boolean remote;
        private Player player;

        public SchematicHolder(boolean remote) {
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

        public ItemStack getTerminalItem() {
            if (player == null) return ItemStack.EMPTY;

            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty() && mainHand.getDescriptionId().contains("schematic_interface")) {
                return mainHand;
            }

            ItemStack offHand = player.getOffhandItem();
            if (!offHand.isEmpty() && offHand.getDescriptionId().contains("schematic_interface")) {
                return offHand;
            }

            return ItemStack.EMPTY;
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
    }
}