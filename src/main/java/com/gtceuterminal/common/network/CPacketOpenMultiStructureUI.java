package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.MultiStructureUIFactory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client to Server request to open the Multi-Structure Manager UI.
public class CPacketOpenMultiStructureUI {

    // Constructor without parameters
    public CPacketOpenMultiStructureUI() {}

    public CPacketOpenMultiStructureUI(FriendlyByteBuf buf) {
        // No data
    }

    public CPacketOpenMultiStructureUI(InteractionHand hand) {
        // No data
    }

    public static void encode(CPacketOpenMultiStructureUI msg, FriendlyByteBuf buffer) {
        // No data
    }

    public static CPacketOpenMultiStructureUI decode(FriendlyByteBuf buffer) {
        return new CPacketOpenMultiStructureUI(buffer);
    }

    public static void handle(CPacketOpenMultiStructureUI msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            GTCEUTerminalMod.LOGGER.info("CPacketOpenMultiStructureUI: player={}", player.getGameProfile().getName());

            try {
                MultiStructureUIFactory.INSTANCE.openUI(player);
            } catch (Throwable t) {
                GTCEUTerminalMod.LOGGER.error("Failed to open Multi-Structure UI from server packet", t);
            }
        });
        context.setPacketHandled(true);
    }
}