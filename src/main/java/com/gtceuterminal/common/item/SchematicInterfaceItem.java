package com.gtceuterminal.common.item;

import com.gtceuterminal.GTCEUTerminalMod;

import com.gtceuterminal.common.ae2.MENetworkScanner;
import com.gtceuterminal.common.item.behavior.SchematicInterfaceBehavior;

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

public class SchematicInterfaceItem extends Item {

    private final SchematicInterfaceBehavior behavior;

    public SchematicInterfaceItem() {
        super(new Item.Properties().stacksTo(1).setNoRepair());
        this.behavior = new SchematicInterfaceBehavior();
        GTCEUTerminalMod.LOGGER.info("SchematicInterfaceItem constructor called - behavior created");
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        return this.behavior.useOn(context);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                  @NotNull InteractionHand usedHand) {
        return this.behavior.use(this, level, player, usedHand);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack))
                .withStyle(s -> s.withColor(0x7D04E3));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Level level,
                                @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);

        tooltipComponents.add(Component.translatable(
                "item.gtceuterminal.schematic_interface.tooltip.blueprint_tool"));
        tooltipComponents.add(Component.literal(""));

        if (MENetworkScanner.isAE2Available()) {
            if (MENetworkScanner.isItemLinked(stack)) {
                tooltipComponents.add(Component.translatable(
                        "item.gtceuterminal.schematic_interface.tooltip.linked"));
                if (level != null && level.isClientSide) {
                    ClientTooltipHelper.appendAE2RangeTooltip(stack, level, tooltipComponents);
                }
            } else {
                tooltipComponents.add(Component.translatable(
                        "item.gtceuterminal.schematic_interface.tooltip.not_linked"));
                tooltipComponents.add(Component.translatable(
                        "item.gtceuterminal.schematic_interface.tooltip.place_in_me_wireless_access_point"));
            }
        }

        tooltipComponents.add(Component.literal(""));
        tooltipComponents.add(
                Component.translatable("item.gtceuterminal.schematic_interface.tooltip.shift_right_click_prefix")
                        .append(Component.translatable("item.gtceuterminal.schematic_interface.tooltip.open_schematic_gui"))
        );
        tooltipComponents.add(
                Component.translatable("item.gtceuterminal.schematic_interface.tooltip.right_click_prefix")
                        .append(Component.translatable("item.gtceuterminal.schematic_interface.tooltip.paste_schematic"))
        );
    }
}