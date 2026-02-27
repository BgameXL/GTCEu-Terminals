package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.energy.LinkedMachineData;
import com.gtceuterminal.common.item.EnergyAnalyzerItem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

// Client → Server: perform an action (unlink or rename) on a linked machine in the Energy Analyzer.
public class CPacketEnergyAnalyzerAction {

    public enum Action { UNLINK, RENAME }

    private final Action action;
    private final int    machineIndex;
    private final String newName; // only used for RENAME

    // ─── Constructors ─────────────────────────────────────────────────────────
    public CPacketEnergyAnalyzerAction(Action action, int machineIndex, String newName) {
        this.action       = action;
        this.machineIndex = machineIndex;
        this.newName      = newName == null ? "" : newName;
    }

    public CPacketEnergyAnalyzerAction(FriendlyByteBuf buf) {
        this.action       = Action.values()[buf.readVarInt()];
        this.machineIndex = buf.readVarInt();
        this.newName      = buf.readUtf(64);
    }

    // ─── Encode / Decode ──────────────────────────────────────────────────────
    public static void encode(CPacketEnergyAnalyzerAction msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action.ordinal());
        buf.writeVarInt(msg.machineIndex);
        buf.writeUtf(msg.newName, 64);
    }

    public static CPacketEnergyAnalyzerAction decode(FriendlyByteBuf buf) {
        return new CPacketEnergyAnalyzerAction(buf);
    }

    // ─── Handler (runs on server main thread) ─────────────────────────────────
    public static void handle(CPacketEnergyAnalyzerAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Find the Energy Analyzer in either hand
            ItemStack stack = findAnalyzer(player);
            if (stack == null || stack.isEmpty()) {
                GTCEUTerminalMod.LOGGER.warn("CPacketEnergyAnalyzerAction: player {} has no Energy Analyzer", player.getName().getString());
                return;
            }

            List<LinkedMachineData> machines = EnergyAnalyzerItem.loadMachines(stack);

            if (msg.machineIndex < 0 || msg.machineIndex >= machines.size()) {
                GTCEUTerminalMod.LOGGER.warn("CPacketEnergyAnalyzerAction: index {} out of range ({})", msg.machineIndex, machines.size());
                return;
            }

            switch (msg.action) {
                case UNLINK -> {
                    LinkedMachineData removed = machines.remove(msg.machineIndex);
                    EnergyAnalyzerItem.saveMachines(stack, machines);
                    GTCEUTerminalMod.LOGGER.debug("Energy Analyzer: unlinked '{}' for player {}", removed.getDisplayName(), player.getName().getString());
                }
                case RENAME -> {
                    String clean = msg.newName.trim();
                    if (clean.length() > 32) clean = clean.substring(0, 32);
                    machines.get(msg.machineIndex).setCustomName(clean);
                    EnergyAnalyzerItem.saveMachines(stack, machines);
                    GTCEUTerminalMod.LOGGER.debug("Energy Analyzer: renamed machine {} to '{}' for player {}", msg.machineIndex, clean, player.getName().getString());
                }
            }

            player.getInventory().setChanged();
        });
        ctx.get().setPacketHandled(true);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private static ItemStack findAnalyzer(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (!s.isEmpty() && s.getItem() instanceof EnergyAnalyzerItem) return s;
        }
        // Also check hotbar in case the player has it there but not in hand
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof EnergyAnalyzerItem) return s;
        }
        return null;
    }
}