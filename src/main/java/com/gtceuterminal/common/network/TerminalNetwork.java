package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class TerminalNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GTCEUTerminalMod.MOD_ID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        GTCEUTerminalMod.LOGGER.info("Registering Terminal Network packets...");

        // ==========================================
        // CLIENT → SERVER PACKETS
        // ==========================================

        CHANNEL.messageBuilder(CPacketBlockReplacement.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketBlockReplacement::encode)
                .decoder(CPacketBlockReplacement::new)
                .consumerMainThread(CPacketBlockReplacement::handle)
                .add();

        CHANNEL.messageBuilder(CPacketSchematicAction.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketSchematicAction::encode)
                .decoder(CPacketSchematicAction::new)
                .consumerMainThread(CPacketSchematicAction::handle)
                .add();

        CHANNEL.messageBuilder(CPacketComponentUpgrade.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketComponentUpgrade::encode)
                .decoder(CPacketComponentUpgrade::decode)
                .consumerMainThread(CPacketComponentUpgrade::handle)
                .add();

        CHANNEL.messageBuilder(CPacketSetCustomMultiblockName.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketSetCustomMultiblockName::encode)
                .decoder(CPacketSetCustomMultiblockName::new)
                .consumerMainThread(CPacketSetCustomMultiblockName::handle)
                .add();

        CHANNEL.messageBuilder(CPacketDismantle.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketDismantle::encode)
                .decoder(CPacketDismantle::decode)
                .consumerMainThread(CPacketDismantle::handle)
                .add();

        CHANNEL.messageBuilder(CPacketOpenDismantlerUI.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketOpenDismantlerUI::encode)
                .decoder(CPacketOpenDismantlerUI::decode)
                .consumerMainThread(CPacketOpenDismantlerUI::handle)
                .add();

        CHANNEL.messageBuilder(CPacketOpenMultiStructureUI.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketOpenMultiStructureUI::encode)
                .decoder(CPacketOpenMultiStructureUI::decode)
                .consumerMainThread(CPacketOpenMultiStructureUI::handle)
                .add();

        CHANNEL.messageBuilder(CPacketOpenSchematicUI.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketOpenSchematicUI::encode)
                .decoder(CPacketOpenSchematicUI::decode)
                .consumerMainThread(CPacketOpenSchematicUI::handle)
                .add();

        CHANNEL.messageBuilder(CPacketOpenManagerSettings.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketOpenManagerSettings::encode)
                .decoder(CPacketOpenManagerSettings::decode)
                .consumerMainThread(CPacketOpenManagerSettings::handle)
                .add();
/**
        CHANNEL.messageBuilder(CPacketOpenPowerLoggerUI.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketOpenPowerLoggerUI::encode)
                .decoder(CPacketOpenPowerLoggerUI::decode)
                .consumerMainThread(CPacketOpenPowerLoggerUI::handle)
                .add();

        CHANNEL.messageBuilder(CPacketOpenPowerMonitorUI.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketOpenPowerMonitorUI::encode)
                .decoder(CPacketOpenPowerMonitorUI::decode)
                .consumerMainThread(CPacketOpenPowerMonitorUI::handle)
                .add();
**/
        GTCEUTerminalMod.LOGGER.info("Registered {} Terminal Network packets", packetId);
        GTCEUTerminalMod.LOGGER.info("Network packets registered successfully!");
    }


    // Send packet from server to specific player
    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                packet
        );
    }
}