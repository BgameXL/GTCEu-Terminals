package com.gtceuterminal.client;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.network.*;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

// Client-only proxy for handling client-side operations.
@OnlyIn(Dist.CLIENT)
public class ClientProxy {


    // Request server to open Schematic Interface UI
    public static void openSchematicGUI(ItemStack stack, Player player, List<SchematicData> schematics) {
        if (player == null) return;
        GTCEUTerminalMod.LOGGER.info("Client: Sending CPacketOpenSchematicUI to server...");
        TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenSchematicUI());
    }


    // Request server to open Multi-Structure Manager UI (scan multiblocks)
    public static void openMultiStructureGUI(ItemStack stack, Player player) {
        if (player == null) return;
        GTCEUTerminalMod.LOGGER.info("Client: Sending CPacketOpenMultiStructureUI to server...");
        TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenMultiStructureUI());
    }


    // Request server to open Manager Settings UI
    public static void openManagerSettingsGUI(ItemStack stack, Player player) {
        if (player == null) return;
        GTCEUTerminalMod.LOGGER.info("Client: Sending CPacketOpenManagerSettings to server...");
        TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenManagerSettings());
    }


    // Request server to open Dismantler UI
    public static void openDismantlerGUI(ItemStack stack, Player player, BlockPos controllerPos) {
        if (player == null) return;
        GTCEUTerminalMod.LOGGER.info("Client: Sending CPacketOpenDismantlerUI to server at {}...", controllerPos);
        TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenDismantlerUI(controllerPos));
    }


    public static void openEnergyAnalyzerGUI(ServerPlayer player, int machineIndex) {
        if (player == null) return;
        GTCEUTerminalMod.LOGGER.debug("ClientProxy: sending CPacketOpenEnergyAnalyzerUI index={}", machineIndex);
        TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenEnergyAnalyzerUI(machineIndex));
    }

    /**
     *
     // Request server to open Power Monitor UI
     public static void openPowerMonitorGUI(net.minecraft.core.BlockPos monitorPos, Player player) {
     if (player == null) {
     GTCEUTerminalMod.LOGGER.error("ClientProxy.openPowerMonitorGUI: player is null!");
     return;
     }
     GTCEUTerminalMod.LOGGER.info("ClientProxy: Sending CPacketOpenPowerMonitorUI to server for pos {}", monitorPos);

     try {
     TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenPowerMonitorUI(monitorPos));
     GTCEUTerminalMod.LOGGER.info("ClientProxy: Packet sent successfully");
     } catch (Exception e) {
     GTCEUTerminalMod.LOGGER.error("ClientProxy: Error sending packet", e);
     }
     } **/
}