package com.gtceuterminal.common.item;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.ClientProxy;
import com.gtceuterminal.common.config.ItemsConfig;
import com.gtceuterminal.common.energy.EnergyDataCollector;
import com.gtceuterminal.common.energy.LinkedMachineData;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EnergyAnalyzerItem extends Item {

    private static final String TAG_MACHINES = "LinkedMachines";

    public EnergyAnalyzerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    // ─── Use on block ────────────────────────────────────────────────────────
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        // Only works on GTCEu machines
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine == null) return InteractionResult.PASS;

        String dimId = LinkedMachineData.dimId(level);
        String machineType = machine.getDefinition().getId().getPath()
                .replace("_", " ")
                .replace("/", " ");
        // Capitalize first letter of each word
        machineType = java.util.Arrays.stream(machineType.split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(machineType);

        if (player.isShiftKeyDown()) {
            // Shift+click: link or unlink
            List<LinkedMachineData> machines = loadMachines(stack);

            // Check if already linked — unlink it
            for (int i = 0; i < machines.size(); i++) {
                if (machines.get(i).matches(pos, dimId)) {
                    machines.remove(i);
                    EnergyDataCollector.clearHistory(level, pos);
                    saveMachines(stack, machines);
                    player.displayClientMessage(
                            Component.literal("§cUnlinked: §f" + machineType), true);
                    return InteractionResult.SUCCESS;
                }
            }

            // Check limit
            int max = ItemsConfig.getEAMaxLinkedMachines();
            if (machines.size() >= max) {
                player.displayClientMessage(
                        Component.literal("§cCannot link more than §f" + max + "§c machines. Unlink one first."), true);
                return InteractionResult.FAIL;
            }

            // Check dimension allowed
            if (!ItemsConfig.isEADimensionAllowed(dimId)) {
                player.displayClientMessage(
                        Component.literal("§cDimension §f" + dimId + "§c is not allowed in config."), true);
                return InteractionResult.FAIL;
            }

            // Link it
            machines.add(new LinkedMachineData(pos, dimId, "", machineType));
            saveMachines(stack, machines);
            player.displayClientMessage(
                    Component.literal("§aLinked: §f" + machineType +
                            " §7(" + machines.size() + "/" + max + ")"), true);
            return InteractionResult.SUCCESS;

        } else {
            // Right-click on machine: open directly to that machine
            List<LinkedMachineData> machines = loadMachines(stack);
            int idx = -1;
            for (int i = 0; i < machines.size(); i++) {
                if (machines.get(i).matches(pos, dimId)) { idx = i; break; }
            }
            if (idx < 0) {
                player.displayClientMessage(
                        Component.literal("§eThis machine is not linked. §7Shift+click to link it first."), true);
                return InteractionResult.SUCCESS;
            }
            // Request UI open — send index to open on that machine
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                ClientProxy.openEnergyAnalyzerGUI(sp, idx);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // ─── Use in air ──────────────────────────────────────────────────────────
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        List<LinkedMachineData> machines = loadMachines(stack);
        if (machines.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§eNo machines linked. §7Shift+click a GTCEu machine to link it."), true);
            return InteractionResultHolder.success(stack);
        }

        // Open list UI at first machine
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ClientProxy.openEnergyAnalyzerGUI(sp, 0);
        }
        return InteractionResultHolder.success(stack);
    }

    // ─── NBT helpers ─────────────────────────────────────────────────────────
    public static List<LinkedMachineData> loadMachines(ItemStack stack) {
        List<LinkedMachineData> result = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MACHINES)) return result;
        ListTag list = tag.getList(TAG_MACHINES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try { result.add(LinkedMachineData.fromNBT(list.getCompound(i))); }
            catch (Exception e) { GTCEUTerminalMod.LOGGER.warn("Failed to load linked machine #{}", i); }
        }
        return result;
    }

    public static void saveMachines(ItemStack stack, List<LinkedMachineData> machines) {
        ListTag list = new ListTag();
        for (LinkedMachineData m : machines) list.add(m.toNBT());
        stack.getOrCreateTag().put(TAG_MACHINES, list);
    }

    // ─── Tooltip ─────────────────────────────────────────────────────────────
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.literal("Energy Monitoring Tool").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(""));

        List<LinkedMachineData> machines = loadMachines(stack);
        int max = ItemsConfig.getEAMaxLinkedMachines();

        if (machines.isEmpty()) {
            tooltip.add(Component.literal("No machines linked").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Linked machines: §f" + machines.size() + "§7/" + max)
                    .withStyle(ChatFormatting.GRAY));
            // Show first 3
            int shown = Math.min(3, machines.size());
            for (int i = 0; i < shown; i++) {
                tooltip.add(Component.literal("  §7● §f" + machines.get(i).getDisplayName()));
            }
            if (machines.size() > 3) {
                tooltip.add(Component.literal("  §7... and " + (machines.size() - 3) + " more"));
            }
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Right-click: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Open on that machine").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("Right-click (air): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Open list").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("Shift + Right-click: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Link / Unlink machine").withStyle(ChatFormatting.YELLOW)));
    }
}