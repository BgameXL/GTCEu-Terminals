package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.autocraft.AnalysisResult;
import com.gtceuterminal.common.ae2.WirelessTerminalHandler;
import com.gtceuterminal.common.pattern.AdvancedAutoBuilder;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;
import com.gtceuterminal.common.config.ManagerSettings;
import com.gtceuterminal.common.multiblock.ComponentInfo;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: the player confirmed the autocraft/build/upgrade dialog.
 *
 * The server re-runs the settings from the item tag so it doesn't trust
 * the client for item quantities — it just trusts the controller position and
 * kind, and re-validates everything server-side.
 */
public class CPacketConfirmAutobuild {

    private final AnalysisResult.Kind kind;
    private final BlockPos            controllerPos;
    private final int                 targetTier;
    private final String              upgradeId;
    private final List<BlockPos>      componentPositions;

    public CPacketConfirmAutobuild(AnalysisResult result) {
        this.kind               = result.kind;
        this.controllerPos      = result.controllerPos;
        this.targetTier         = result.targetTier;
        this.upgradeId          = result.upgradeId != null ? result.upgradeId : "";
        this.componentPositions = new ArrayList<>(result.componentPositions);
    }

    // ── Encode / Decode ───────────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeBlockPos(controllerPos);
        buf.writeInt(targetTier);
        buf.writeUtf(upgradeId);
        buf.writeInt(componentPositions.size());
        for (BlockPos pos : componentPositions) buf.writeBlockPos(pos);
    }

    public CPacketConfirmAutobuild(FriendlyByteBuf buf) {
        this.kind          = buf.readEnum(AnalysisResult.Kind.class);
        this.controllerPos = buf.readBlockPos();
        this.targetTier    = buf.readInt();
        this.upgradeId     = buf.readUtf();
        int count = buf.readInt();
        this.componentPositions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) componentPositions.add(buf.readBlockPos());
    }

    // ── Handler ───────────────────────────────────────────────────────────────
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            try {
                if (kind == AnalysisResult.Kind.BUILD) {
                    handleBuild(player);
                } else {
                    handleUpgrade(player);
                }
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("CPacketConfirmAutobuild: error", e);
                player.displayClientMessage(
                        Component.translatable(
                                "item.gtceuterminal.autocraft_confirm.message.autocraft_failed",
                                e.getMessage()
                        ),
                        false
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleBuild(ServerPlayer player) {
        BlockEntity be = player.serverLevel().getBlockEntity(controllerPos);
        if (!(be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity mbe)) {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.controller_not_found"),
                    false);
            return;
        }
        MetaMachine machine = mbe.getMetaMachine();
        if (!(machine instanceof IMultiController controller)) {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.target_not_multiblock_controller"),
                    false);
            return;
        }
        if (controller.isFormed()) {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.multiblock_already_formed"),
                    true);
            return;
        }

        // Read settings from the item the player is holding
        ManagerSettings.AutoBuildSettings settings = getSettings(player);

        boolean ok = AdvancedAutoBuilder.autoBuild(player, controller, settings);
        if (ok) {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.multiblock_built"),
                    true);
        } else {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.build_failed_check_materials"),
                    false);
        }
    }

    private void handleUpgrade(ServerPlayer player) {
        if (componentPositions.isEmpty()) {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.no_components_to_upgrade"),
                    false);
            return;
        }

        ItemStack terminal = findTerminal(player);
        int succeeded = 0, failed = 0;

        for (BlockPos pos : componentPositions) {
            // Reconstruct a minimal ComponentInfo from the live world state.
            // We use the block's state; type+tier will be re-inferred by ComponentUpgrader
            // via the targetTier / upgradeId parameters, so we only need position+state here.
            net.minecraft.world.level.block.state.BlockState state =
                    player.serverLevel().getBlockState(pos);

            // Build a minimal ComponentInfo — type/tier don't affect upgradeComponent when
            // upgradeId or targetTier is explicit; they're used only for validation fallbacks.
            ComponentInfo info = new ComponentInfo(
                    com.gtceuterminal.common.multiblock.ComponentType.CASING, // placeholder
                    0,
                    pos,
                    state);

            ComponentUpgrader.UpgradeResult result = ComponentUpgrader.upgradeComponent(
                    info,
                    targetTier,
                    upgradeId.isEmpty() ? null : upgradeId,
                    player,
                    player.serverLevel(),
                    true,
                    terminal);

            if (result.success) succeeded++; else failed++;
        }

        if (succeeded > 0) {
            if (failed > 0) {
                msg(player,
                        Component.translatable(
                                "item.gtceuterminal.autocraft_confirm.message.upgrade_success_with_failed",
                                succeeded, failed),
                        false);
            } else {
                msg(player,
                        Component.translatable(
                                "item.gtceuterminal.autocraft_confirm.message.upgrade_success_no_failed",
                                succeeded),
                        false);
            }
        } else {
            msg(player,
                    Component.translatable("item.gtceuterminal.autocraft_confirm.message.upgrade_failed_check_materials"),
                    false);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static ManagerSettings.AutoBuildSettings getSettings(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            ManagerSettings.Settings s = new ManagerSettings.Settings(held);
            return s.toAutoBuildSettings();
        }
        return new ManagerSettings.AutoBuildSettings();
    }

    private static ItemStack findTerminal(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (WirelessTerminalHandler.isWirelessTerminal(s)
                    && WirelessTerminalHandler.isLinked(s)) return s;
        }
        ItemStack main = player.getMainHandItem();
        if (WirelessTerminalHandler.isWirelessTerminal(main)) return main;
        return ItemStack.EMPTY;
    }

    private static void msg(ServerPlayer p, Component component, boolean actionBar) {
        p.displayClientMessage(component, actionBar);
    }
}