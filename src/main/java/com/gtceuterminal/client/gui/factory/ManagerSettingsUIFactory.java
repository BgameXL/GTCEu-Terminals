package com.gtceuterminal.client.gui.factory;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.multiblock.ManagerSettingsUI;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * UI Factory for Manager Settings
 */
public class ManagerSettingsUIFactory extends UIFactory<ManagerSettingsUIFactory.SettingsHolder> {

    public static final ManagerSettingsUIFactory INSTANCE = new ManagerSettingsUIFactory();

    private ManagerSettingsUIFactory() {
        super(ResourceLocation.parse("gtceu_terminal_manager_settings"));
    }

    @Override
    protected ModularUI createUITemplate(SettingsHolder holder, Player entityPlayer) {
        GTCEUTerminalMod.LOGGER.info("Creating Manager Settings UI for player: {}", entityPlayer.getName().getString());
        holder.attach(entityPlayer);

        try {
            // Load UI using reflection to avoid client-side dependency
            Class<?> uiClass = Class.forName("com.gtceuterminal.client.gui.multiblock.ManagerSettingsUI");
            var constructor = uiClass.getConstructor(SettingsHolder.class, Player.class);
            Object uiInstance = constructor.newInstance(holder, entityPlayer);
            var createUIMethod = uiClass.getMethod("createUI");
            Object result = createUIMethod.invoke(uiInstance);
            return (ModularUI) result;
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("Failed to create Manager Settings UI", t);
            throw new RuntimeException("Failed to create Manager Settings UI", t);
        }
    }

    @NotNull
    @Override
    protected SettingsHolder readHolderFromSyncData(FriendlyByteBuf syncData) {
        ItemStack itemStack = syncData.readItem();
        return new SettingsHolder(itemStack);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, SettingsHolder holder) {
        syncData.writeItem(holder.itemStack);
    }

    public void openUI(ServerPlayer player) {
        if (player == null) return;

        ItemStack itemStack = player.getMainHandItem();
        if (itemStack.isEmpty()) {
            GTCEUTerminalMod.LOGGER.warn("Player {} tried to open Manager Settings with empty hand", player.getName().getString());
            return;
        }

        GTCEUTerminalMod.LOGGER.info("Opening Manager Settings UI for player: {}", player.getName().getString());

        SettingsHolder holder = new SettingsHolder(itemStack);
        super.openUI(holder, player);
    }

    public static class SettingsHolder implements IUIHolder {
        private final ItemStack itemStack;
        private Player player;

        public SettingsHolder(ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        public void attach(Player entityPlayer) {
            if (this.player == null) {
                this.player = entityPlayer;
            }
        }

        public ItemStack getTerminalItem() {
            return itemStack;
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
            return player != null && player.level().isClientSide;
        }

        @Override
        public void markAsDirty() {
        }
    }
}