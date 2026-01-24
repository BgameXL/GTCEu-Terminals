package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.ManagerSettingsUIFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client -> Server request to open Manager Settings UI
public class CPacketOpenManagerSettings {

    public CPacketOpenManagerSettings() {}

    public CPacketOpenManagerSettings(FriendlyByteBuf buf) {
        // No data needed
    }

    public static void encode(CPacketOpenManagerSettings msg, FriendlyByteBuf buffer) {
        // No data to encode
    }

    public static CPacketOpenManagerSettings decode(FriendlyByteBuf buffer) {
        return new CPacketOpenManagerSettings(buffer);
    }

    public static void handle(CPacketOpenManagerSettings msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            GTCEUTerminalMod.LOGGER.info("CPacketOpenManagerSettings: player={}", player.getGameProfile().getName());

            try {
                ManagerSettingsUIFactory.INSTANCE.openUI(player);
            } catch (Throwable t) {
                GTCEUTerminalMod.LOGGER.error("Failed to open Manager Settings UI", t);
            }
        });
        context.setPacketHandled(true);
    }
}