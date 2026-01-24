package com.gtceuterminal.common.item;

import com.gtceuterminal.common.ae2.WirelessTerminalHandler;
import com.gtceuterminal.common.item.behavior.MultiStructureManagerBehavior;

import net.minecraft.ChatFormatting;
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

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MultiStructureManagerItem extends Item {

    private final MultiStructureManagerBehavior behavior;

    public MultiStructureManagerItem(Properties properties, int cooldownTicks, boolean enableSounds) {
        super(properties);
        this.behavior = new MultiStructureManagerBehavior(cooldownTicks, enableSounds);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        return behavior.useOn(context);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        return behavior.use(this, level, player, usedHand);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack))
                .withStyle(s -> s.withColor(0x990000));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Level level,
                                @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);

        tooltipComponents.add(Component.literal("Multiblock Management Tool")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal(""));

        if (WirelessTerminalHandler.isLinked(stack)) {
            tooltipComponents.add(Component.literal("✓ Linked to ME Network")
                    .withStyle(ChatFormatting.GREEN));

            if (level != null && !level.isClientSide) {
                Player player = level.getNearestPlayer(
                        stack.getTag() != null ? stack.getTag().getDouble("LastX") : 0,
                        stack.getTag() != null ? stack.getTag().getDouble("LastY") : 0,
                        stack.getTag() != null ? stack.getTag().getDouble("LastZ") : 0,
                        100,
                        p -> !p.isSpectator()
                );

                if (player != null) {
                    if (WirelessTerminalHandler.isInRange(stack, level, player)) {
                        tooltipComponents.add(Component.literal("  ● In Range")
                                .withStyle(ChatFormatting.AQUA));
                    } else {
                        tooltipComponents.add(Component.literal("  ● Out of Range")
                                .withStyle(ChatFormatting.RED));
                    }
                }
            }
        } else {
            tooltipComponents.add(Component.literal("✗ Not Linked")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal("  Place in ME Wireless Access Point to link")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltipComponents.add(Component.literal(""));
        tooltipComponents.add(Component.literal("Right-click: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Settings")
                        .withStyle(ChatFormatting.AQUA)));
        tooltipComponents.add(Component.literal("Shift + Right-click: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Manage Multiblocks")
                        .withStyle(ChatFormatting.RED)));
    }
}