package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.common.ae2.MENetworkScanner;
import com.gtceuterminal.common.theme.ItemTheme;
import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.MultiStructureManagerUIFactory;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ManagerSettingsUI {

    private static final int GUI_WIDTH  = 200;
    private static final int GUI_HEIGHT = 175;

    private int COLOR_BG_DARK      = 0xFF1A1A1A;
    private int COLOR_BG_MEDIUM    = 0xFF2B2B2B;
    private int COLOR_BG_LIGHT     = 0xFF3F3F3F;
    private int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE  = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY   = 0xFFAAAAAA;

    private ItemTheme theme;
    private final IUIHolder uiHolder;   // either SettingsHolder or HeldItemHolder
    private final ItemStack itemStack;  // the actual item — all logic reads this directly
    private final Player player;

    // ── Constructor B: HeldItemUIFactory path (kept for compat) ─────────────
    public ManagerSettingsUI(HeldItemUIFactory.HeldItemHolder heldHolder) {
        this.uiHolder  = heldHolder;
        this.itemStack = heldHolder.held;
        this.player    = heldHolder.player;
        applyTheme();
    }

    // ── Constructor C: MultiStructureManagerUIFactory path ───────────────────
    public ManagerSettingsUI(MultiStructureManagerUIFactory.Holder holder, Player player) {
        this.uiHolder  = holder;
        this.itemStack = holder.getTerminalItem();
        this.player    = player;
        applyTheme();
    }

    private void applyTheme() {
        this.theme          = ItemTheme.load(itemStack);
        COLOR_BG_DARK       = theme.bgColor;
        COLOR_BG_MEDIUM     = theme.panelColor;
        COLOR_BG_LIGHT      = theme.isNativeStyle() ? 0xFF3A3A3A : theme.accent(0xAA);
        COLOR_BORDER_LIGHT  = theme.isNativeStyle() ? 0xFF555555 : theme.accent(0xFF);
    }

    public ModularUI createUI() {
        WidgetGroup mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        mainGroup.setBackground(theme.backgroundTexture());

        mainGroup.addWidget(new ImageWidget(0, 0, GUI_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        mainGroup.addWidget(new ImageWidget(0, 0, 2, GUI_HEIGHT, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        mainGroup.addWidget(new ImageWidget(GUI_WIDTH - 2, 0, 2, GUI_HEIGHT, new ColorRectTexture(COLOR_BORDER_DARK)));
        mainGroup.addWidget(new ImageWidget(0, GUI_HEIGHT - 2, GUI_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_DARK)));

        LabelWidget title = new LabelWidget(GUI_WIDTH / 2 - 60, 8,
                Component.translatable("gui.gtceuterminal.manager_settings.title").getString());
        title.setTextColor(COLOR_TEXT_WHITE);
        mainGroup.addWidget(title);

        mainGroup.addWidget(createSettingsPanel());

        ModularUI gui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), uiHolder, player);
        gui.widget(mainGroup);
        gui.background(theme.modularUIBackground());
        return gui;
    }

    private WidgetGroup createSettingsPanel() {
        WidgetGroup panel = new WidgetGroup(8, 30, GUI_WIDTH - 16, GUI_HEIGHT - 40);
        panel.setBackground(theme.panelTexture());

        int yPos = 8;
        final String yesStr = Component.translatable("gui.gtceuterminal.manager_settings.common.yes").getString();
        final String noStr  = Component.translatable("gui.gtceuterminal.manager_settings.common.no").getString();

        // 1. No Hatch Mode
        LabelWidget hatchLabel = new LabelWidget(8, yPos,
                Component.translatable("gui.gtceuterminal.manager_settings.hatch_mode.label").getString());
        panel.addWidget(hatchLabel);
        ButtonWidget hatchToggle = new ButtonWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                new ColorRectTexture(COLOR_BG_DARK), cd -> toggleNoHatchMode());
        hatchToggle.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        hatchLabel.setHoverTooltips(Component.translatable("gui.gtceuterminal.manager_settings.hatch_mode.tooltip"));
        panel.addWidget(hatchToggle);
        panel.addWidget(new LabelWidget(GUI_WIDTH - 54, yPos + 2,
                () -> getNoHatchMode() == 1 ? yesStr : noStr));
        panel.addWidget(new LabelWidget(8, yPos + 14,
                Component.translatable("gui.gtceuterminal.manager_settings.hatch_mode.hint_toggle").getString())
                .setTextColor(0xFF666666));
        yPos += 30;

        // 2. Tier Mode
        LabelWidget tierLabel = new LabelWidget(8, yPos,
                Component.translatable("gui.gtceuterminal.manager_settings.tier_mode.label").getString());
        tierLabel.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(tierLabel);
        TextFieldWidget tierInput = new TextFieldWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                () -> String.valueOf(getTierMode()),
                value -> setTierMode(parseIntSafe(value, 1)));
        tierInput.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        tierLabel.setHoverTooltips(Component.translatable("gui.gtceuterminal.manager_settings.tier_mode.tooltip"));
        tierInput.setNumbersOnly(1, 16);
        tierInput.setTextColor(COLOR_TEXT_WHITE);
        tierInput.setBackground(theme.backgroundTexture());
        tierInput.setWheelDur(1);
        panel.addWidget(tierInput);
        panel.addWidget(new LabelWidget(8, yPos + 14,
                Component.translatable("gui.gtceuterminal.manager_settings.tier_mode.hint_scroll_type").getString())
                .setTextColor(0xFF666666));
        yPos += 30;

        // 3. Repeat Count
        LabelWidget repeatLabel = new LabelWidget(8, yPos,
                Component.translatable("gui.gtceuterminal.manager_settings.repeat_count.label").getString());
        repeatLabel.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(repeatLabel);
        TextFieldWidget repeatInput = new TextFieldWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                () -> String.valueOf(getRepeatCount()),
                value -> setRepeatCount(parseIntSafe(value, 0)));
        repeatInput.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        repeatLabel.setHoverTooltips(Component.translatable("gui.gtceuterminal.manager_settings.repeat_count.tooltip"));
        repeatInput.setNumbersOnly(0, 32);
        repeatInput.setTextColor(COLOR_TEXT_WHITE);
        repeatInput.setBackground(theme.backgroundTexture());
        repeatInput.setWheelDur(1);
        panel.addWidget(repeatInput);
        panel.addWidget(new LabelWidget(8, yPos + 14,
                Component.translatable("gui.gtceuterminal.manager_settings.repeat_count.hint_layers").getString())
                .setTextColor(0xFF666666));
        yPos += 30;

        // 4. Use AE2
        if (MENetworkScanner.isAE2Available()) {
            LabelWidget aeLabel = new LabelWidget(8, yPos,
                    Component.translatable("gui.gtceuterminal.manager_settings.use_ae2.label").getString());
            panel.addWidget(aeLabel);
            ButtonWidget aeToggle = new ButtonWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                    new ColorRectTexture(COLOR_BG_DARK), cd -> toggleIsUseAE());
            aeToggle.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
            aeLabel.setHoverTooltips(Component.translatable("gui.gtceuterminal.manager_settings.use_ae2.tooltip"));
            panel.addWidget(aeToggle);
            panel.addWidget(new LabelWidget(GUI_WIDTH - 54, yPos + 2,
                    () -> getIsUseAE() == 1 ? yesStr : noStr));
            panel.addWidget(new LabelWidget(8, yPos + 14,
                    Component.translatable("gui.gtceuterminal.manager_settings.use_ae2.hint_use_materials").getString())
                    .setTextColor(0xFF666666));
        }

        return panel;
    }

    // ── NBT helpers (operate on itemStack directly) ───────────────────────────
    private int getNoHatchMode() {
        CompoundTag tag = itemStack.getTag();
        return (tag != null && tag.contains("NoHatchMode")) ? tag.getInt("NoHatchMode") : 0;
    }
    private void toggleNoHatchMode() {
        itemStack.getOrCreateTag().putInt("NoHatchMode", getNoHatchMode() == 1 ? 0 : 1);
    }

    private int getTierMode() {
        CompoundTag tag = itemStack.getTag();
        return (tag != null && tag.contains("TierMode")) ? tag.getInt("TierMode") : 1;
    }
    private void setTierMode(int tier) {
        itemStack.getOrCreateTag().putInt("TierMode", Math.max(1, Math.min(16, tier)));
    }

    private int getRepeatCount() {
        CompoundTag tag = itemStack.getTag();
        return (tag != null && tag.contains("RepeatCount")) ? tag.getInt("RepeatCount") : 0;
    }
    private void setRepeatCount(int count) {
        itemStack.getOrCreateTag().putInt("RepeatCount", Math.max(0, Math.min(99, count)));
    }

    private int getIsUseAE() {
        CompoundTag tag = itemStack.getTag();
        return (tag != null && tag.contains("IsUseAE")) ? tag.getInt("IsUseAE") : 0;
    }
    private void toggleIsUseAE() {
        itemStack.getOrCreateTag().putInt("IsUseAE", getIsUseAE() == 1 ? 0 : 1);
    }

    private int parseIntSafe(String value, int defaultValue) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ── Thin subclasses kept for compile compat ───────────────────────────────
    public static class Settings extends com.gtceuterminal.common.config.ManagerSettings.Settings {
        public Settings(ItemStack itemStack) { super(itemStack); }
    }
    public static class AutoBuildSettings extends com.gtceuterminal.common.config.ManagerSettings.AutoBuildSettings {}
}