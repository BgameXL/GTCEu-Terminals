package com.gtceuterminal.common.item;

import com.gtceuterminal.common.ae2.MENetworkScanner;
import com.gtceuterminal.common.item.behavior.MultiStructureManagerBehavior;

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
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand usedHand) {
        return behavior.use(this, level, player, usedHand);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Level level,
                                @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);

        tooltipComponents.add(Component.translatable(
                "item.gtceuterminal.multi_structure_manager.tooltip.tool"
        ));
        tooltipComponents.add(Component.literal(""));

        if (MENetworkScanner.isAE2Available()) {
            if (MENetworkScanner.isItemLinked(stack)) {
                tooltipComponents.add(Component.translatable(
                        "item.gtceuterminal.multi_structure_manager.tooltip.linked"
                ));
                if (level != null && level.isClientSide) {
                    ClientTooltipHelper.appendAE2RangeTooltip(stack, level, tooltipComponents);
                }
            } else {
                tooltipComponents.add(Component.translatable(
                        "item.gtceuterminal.multi_structure_manager.tooltip.not_linked"
                ));
                tooltipComponents.add(Component.translatable(
                        "item.gtceuterminal.multi_structure_manager.tooltip.place_in_me_wireless_access_point"
                ));
            }
        }

        tooltipComponents.add(Component.literal(""));
        tooltipComponents.add(Component.translatable(
                "item.gtceuterminal.multi_structure_manager.tooltip.right_click_settings"
        ));
        tooltipComponents.add(Component.translatable(
                "item.gtceuterminal.multi_structure_manager.tooltip.shift_right_click_manage"
        ));
    }
}