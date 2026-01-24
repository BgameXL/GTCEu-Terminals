package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.SchematicUIFactory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


// Client to Server request to open the Schematic Interface UI.
public class CPacketOpenSchematicUI {

    public CPacketOpenSchematicUI() {}

    public CPacketOpenSchematicUI(FriendlyByteBuf buf) {
        // no data
    }
    
    public static void encode(CPacketOpenSchematicUI msg, FriendlyByteBuf buf) {
        // no data
    }

    public static CPacketOpenSchematicUI decode(FriendlyByteBuf buf) {
        return new CPacketOpenSchematicUI(buf);
    }

    public static void handle(CPacketOpenSchematicUI msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            GTCEUTerminalMod.LOGGER.info("CPacketOpenSchematicUI: player={}", player.getGameProfile().getName());

            try {
                SchematicUIFactory.INSTANCE.openUI(player);
            } catch (Throwable t) {
                GTCEUTerminalMod.LOGGER.error("Failed to open Schematic UI from server packet", t);
            }
        });
        context.setPacketHandled(true);
    }
}