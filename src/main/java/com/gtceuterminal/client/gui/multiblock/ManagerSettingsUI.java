package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.ManagerSettingsUIFactory;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Manager Settings UI - Auto-Build Configuration
public class ManagerSettingsUI {

    private static final int GUI_WIDTH = 200;
    // Smaller UI: removed Increase Radius option
    private static final int GUI_HEIGHT = 175;

    // GTCEu Colors
    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;

    private final ManagerSettingsUIFactory.SettingsHolder holder;
    private final Player player;

    public ManagerSettingsUI(ManagerSettingsUIFactory.SettingsHolder holder, Player player) {
        this.holder = holder;
        this.player = player;
    }

    public ModularUI createUI() {
        WidgetGroup mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        mainGroup.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        // Borders
        mainGroup.addWidget(new ImageWidget(0, 0, GUI_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        mainGroup.addWidget(new ImageWidget(0, 0, 2, GUI_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        mainGroup.addWidget(new ImageWidget(GUI_WIDTH - 2, 0, 2, GUI_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_DARK)));
        mainGroup.addWidget(new ImageWidget(0, GUI_HEIGHT - 2, GUI_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_DARK)));

        // Title
        LabelWidget title = new LabelWidget(GUI_WIDTH / 2 - 60, 8, "§lManager Settings");
        title.setTextColor(COLOR_TEXT_WHITE);
        mainGroup.addWidget(title);

        // Settings panel
        mainGroup.addWidget(createSettingsPanel());

        ModularUI gui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), holder, player);
        gui.widget(mainGroup);
        gui.background(new ColorRectTexture(0x90000000));

        return gui;
    }

    private WidgetGroup createSettingsPanel() {
        WidgetGroup panel = new WidgetGroup(8, 30, GUI_WIDTH - 16, GUI_HEIGHT - 40);
        panel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        ItemStack itemStack = holder.getTerminalItem();
        int yPos = 8;

        // ═══════════════════════════════════════════════════════════════
        // 1. No Hatch Mode (Build without hatches)
        // ═══════════════════════════════════════════════════════════════
        LabelWidget hatchLabel = new LabelWidget(8, yPos, "§7No Hatch Mode");
        panel.addWidget(hatchLabel);

        ButtonWidget hatchToggle = new ButtonWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                new ColorRectTexture(COLOR_BG_DARK),
                cd -> toggleNoHatchMode(itemStack));
        hatchToggle.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        hatchLabel.setHoverTooltips(Component.literal("§7Build without hatches (Hatches will be ignored when placing blocks)"));
        panel.addWidget(hatchToggle);

        LabelWidget hatchValue = new LabelWidget(GUI_WIDTH - 54, yPos + 2,
                () -> getNoHatchMode(itemStack) == 1 ? "§aYes" : "§cNo");
        panel.addWidget(hatchValue);

        // Hint text
        LabelWidget hatchHint = new LabelWidget(8, yPos + 14, "§8← Click to toggle");
        hatchHint.setTextColor(0xFF666666);
        panel.addWidget(hatchHint);

        yPos += 30;

        // ═══════════════════════════════════════════════════════════════
        // 2. Tier Mode (Component tier for auto-build)
        // ═══════════════════════════════════════════════════════════════
        LabelWidget tierLabel = new LabelWidget(8, yPos, "§7Tier Mode");
        tierLabel.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(tierLabel);

        TextFieldWidget tierInput = new TextFieldWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                () -> String.valueOf(getTierMode(itemStack)),
                value -> setTierMode(parseIntSafe(value, 1), itemStack));
        tierInput.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        tierLabel.setHoverTooltips(Component.literal("§7Component tier to use (Example: 1 = LV, 2 = MV, etc.)"));
        tierInput.setNumbersOnly(1, 16);
        tierInput.setTextColor(COLOR_TEXT_WHITE);
        tierInput.setBackground(new ColorRectTexture(COLOR_BG_DARK));
        tierInput.setWheelDur(1); // Scroll wheel support
        panel.addWidget(tierInput);

        // Hint text
        LabelWidget tierHint = new LabelWidget(8, yPos + 14, "§8↑↓ Scroll or type");
        tierHint.setTextColor(0xFF666666);
        panel.addWidget(tierHint);

        yPos += 30;

        // ═══════════════════════════════════════════════════════════════
        // 3. Repeat Count (Number of times to repeat the structure)
        // ═══════════════════════════════════════════════════════════════
        LabelWidget repeatLabel = new LabelWidget(8, yPos, "§7Repeat Count");
        repeatLabel.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(repeatLabel);

        TextFieldWidget repeatInput = new TextFieldWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                () -> String.valueOf(getRepeatCount(itemStack)),
                value -> setRepeatCount(parseIntSafe(value, 0), itemStack));
        repeatInput.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        repeatLabel.setHoverTooltips(Component.literal("§7Number of times to repeat the structure"));
        repeatInput.setNumbersOnly(0, 32);
        repeatInput.setTextColor(COLOR_TEXT_WHITE);
        repeatInput.setBackground(new ColorRectTexture(COLOR_BG_DARK));
        repeatInput.setWheelDur(1); // Scroll wheel support
        panel.addWidget(repeatInput);

        // Hint text
        LabelWidget repeatHint = new LabelWidget(8, yPos + 14, "§8Repeatable layers (0-99)");
        repeatHint.setTextColor(0xFF666666);
        panel.addWidget(repeatHint);

        yPos += 30;

        // ═══════════════════════════════════════════════════════════════
        // 4. Use AE2 (Wireless Terminal required for auto-importing materials from AE2)
        // ═══════════════════════════════════════════════════════════════
        LabelWidget aeLabel = new LabelWidget(8, yPos, "§7Use AE2");
        panel.addWidget(aeLabel);

        ButtonWidget aeToggle = new ButtonWidget(GUI_WIDTH - 70, yPos - 2, 50, 16,
                new ColorRectTexture(COLOR_BG_DARK),
                cd -> toggleIsUseAE(itemStack));
        aeToggle.setHoverTexture(new ColorRectTexture(COLOR_BG_LIGHT));
        aeLabel.setHoverTooltips(Component.literal("§7Wireless Terminal is required"));
        panel.addWidget(aeToggle);

        LabelWidget aeValue = new LabelWidget(GUI_WIDTH - 54, yPos + 2,
                () -> getIsUseAE(itemStack) == 1 ? "§aYes" : "§cNo");
        panel.addWidget(aeValue);

        // Hint text
        LabelWidget aeHint = new LabelWidget(8, yPos + 14, "§8← Use AE2 for materials");
        aeHint.setTextColor(0xFF666666);
        panel.addWidget(aeHint);

        return panel;
    }

    // ═══════════════════════════════════════════════════════════════
    // NO HATCH MODE
    // ═══════════════════════════════════════════════════════════════
    private int getNoHatchMode(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag != null && tag.contains("NoHatchMode")) {
            return tag.getInt("NoHatchMode");
        }
        return 0; // Default 0 = place hatches (No = place hatches)
    }

    private void toggleNoHatchMode(ItemStack itemStack) {
        int current = getNoHatchMode(itemStack);
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt("NoHatchMode", current == 1 ? 0 : 1);
        itemStack.setTag(tag);
        GTCEUTerminalMod.LOGGER.info("No Hatch Mode toggled to: {}", current == 1 ? 0 : 1);
    }

    // ═══════════════════════════════════════════════════════════════
    // TIER MODE
    // ═══════════════════════════════════════════════════════════════
    private int getTierMode(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag != null && tag.contains("TierMode")) {
            return tag.getInt("TierMode");
        }
        return 1; // Default tier 1
    }

    private void setTierMode(int tier, ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt("TierMode", Math.max(1, Math.min(16, tier)));
        itemStack.setTag(tag);
        GTCEUTerminalMod.LOGGER.info("Tier Mode set to: {}", tier);
    }

    // ═══════════════════════════════════════════════════════════════
    // REPEAT COUNT
    // ═══════════════════════════════════════════════════════════════
    private int getRepeatCount(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag != null && tag.contains("RepeatCount")) {
            return tag.getInt("RepeatCount");
        }
        return 0; // Default: 0 repeticiones
    }

    private void setRepeatCount(int count, ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt("RepeatCount", Math.max(0, Math.min(99, count)));
        itemStack.setTag(tag);
        GTCEUTerminalMod.LOGGER.info("Repeat Count set to: {}", count);
    }

    // ═══════════════════════════════════════════════════════════════
    // USE AE2
    // ═══════════════════════════════════════════════════════════════
    private int getIsUseAE(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag != null && tag.contains("IsUseAE")) {
            return tag.getInt("IsUseAE");
        }
        return 0; // Default: No usar AE2
    }

    private void toggleIsUseAE(ItemStack itemStack) {
        int current = getIsUseAE(itemStack);
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt("IsUseAE", current == 1 ? 0 : 1);
        itemStack.setTag(tag);
        GTCEUTerminalMod.LOGGER.info("Use AE2 toggled to: {}", current == 1 ? 0 : 1);
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════
    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Static helper class to read settings from item
    public static class Settings {
        public final int noHatchMode;
        public final int tierMode;
        public final int repeatCount;
        public final int isUseAE;

        public Settings(ItemStack itemStack) {
            CompoundTag tag = itemStack.getTag();
            if (tag != null) {
                this.noHatchMode = tag.contains("NoHatchMode") ? tag.getInt("NoHatchMode") : 0;
                this.tierMode = tag.contains("TierMode") ? tag.getInt("TierMode") : 1;
                this.repeatCount = tag.contains("RepeatCount") ? tag.getInt("RepeatCount") : 0;
                this.isUseAE = tag.contains("IsUseAE") ? tag.getInt("IsUseAE") : 0;
            } else {
                this.noHatchMode = 0;
                this.tierMode = 1;
                this.repeatCount = 0;
                this.isUseAE = 0;
            }
        }

        public AutoBuildSettings toAutoBuildSettings() {
            AutoBuildSettings settings = new AutoBuildSettings();
            settings.noHatchMode = this.noHatchMode;
            settings.tierMode = this.tierMode;
            settings.repeatCount = this.repeatCount;
            settings.isUseAE = this.isUseAE;
            return settings;
        }
    }

    // Auto-build settings for use when constructing multiblocks
    public static class AutoBuildSettings {
        public int repeatCount = 0;  // Number of repetitions (0 = use default)
        public int noHatchMode = 0;  // 0 = place hatches, 1 = don't place hatches
        public int tierMode = 1;     // Component tier to use
        public int isUseAE = 0;
    }
}