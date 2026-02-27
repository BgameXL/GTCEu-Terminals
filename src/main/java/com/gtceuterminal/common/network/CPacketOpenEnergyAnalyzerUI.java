package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.EnergyAnalyzerUIFactory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client → Server: Request to open the Energy Analyzer UI for a specific linked machine index.
public class CPacketOpenEnergyAnalyzerUI {

    private final int machineIndex;

    public CPacketOpenEnergyAnalyzerUI(int machineIndex) {
        this.machineIndex = machineIndex;
    }

    public CPacketOpenEnergyAnalyzerUI(FriendlyByteBuf buf) {
        this.machineIndex = buf.readInt();
    }

    public static void encode(CPacketOpenEnergyAnalyzerUI msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.machineIndex);
    }

    public static CPacketOpenEnergyAnalyzerUI decode(FriendlyByteBuf buf) {
        return new CPacketOpenEnergyAnalyzerUI(buf);
    }

    public static void handle(CPacketOpenEnergyAnalyzerUI msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            GTCEUTerminalMod.LOGGER.debug("CPacketOpenEnergyAnalyzerUI: index={}", msg.machineIndex);
            try {
                EnergyAnalyzerUIFactory.INSTANCE.openUI(player, msg.machineIndex);
            } catch (Throwable t) {
                GTCEUTerminalMod.LOGGER.error("Failed to open Energy Analyzer UI", t);
            }
        });
        context.setPacketHandled(true);
    }
}