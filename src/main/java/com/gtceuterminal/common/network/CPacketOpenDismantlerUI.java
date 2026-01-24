package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.DismantlerUIFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client to Server request to open the Dismantler UI.
public class CPacketOpenDismantlerUI {

    private final BlockPos controllerPos;

    public CPacketOpenDismantlerUI(BlockPos controllerPos) {
        this.controllerPos = controllerPos;
    }

    public static void encode(CPacketOpenDismantlerUI msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.controllerPos);
    }

    public static CPacketOpenDismantlerUI decode(FriendlyByteBuf buffer) {
        return new CPacketOpenDismantlerUI(buffer.readBlockPos());
    }

    public static void handle(CPacketOpenDismantlerUI msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            GTCEUTerminalMod.LOGGER.info("CPacketOpenDismantlerUI: player={} pos={}", player.getGameProfile().getName(), msg.controllerPos);

            try {
                DismantlerUIFactory.INSTANCE.openUI(player, msg.controllerPos);
            } catch (Throwable t) {
                GTCEUTerminalMod.LOGGER.error("Failed to open Dismantler UI from server packet", t);
            }
        });
        context.setPacketHandled(true);
    }
}