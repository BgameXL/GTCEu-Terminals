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
    private static final int GUI_HEIGHT = 110;

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
        tierInput.setNumbersOnly(1, 16);
        tierInput.setTextColor(COLOR_TEXT_WHITE);
        tierInput.setBackground(new ColorRectTexture(COLOR_BG_DARK));
        tierInput.setWheelDur(1); // Scroll wheel support
        panel.addWidget(tierInput);

        // Hint text
        LabelWidget tierHint = new LabelWidget(8, yPos + 14, "§8↑↓ Scroll or type");
        tierHint.setTextColor(0xFF666666);
        panel.addWidget(tierHint);

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
        public final int noHatchMode;     // 0 = place hatches, 1 = don't place hatches
        public final int tierMode;         // Component tier (1-16)

        public Settings(ItemStack itemStack) {
            CompoundTag tag = itemStack.getTag();
            if (tag != null) {
                //  Store as int directly (0 or 1)
                this.noHatchMode = tag.contains("NoHatchMode") ? tag.getInt("NoHatchMode") : 0;
                this.tierMode = tag.contains("TierMode") ? tag.getInt("TierMode") : 1;
            } else {
                this.noHatchMode = 0; // Default: 0 = place hatches
                this.tierMode = 1;
            }
        }

        public AutoBuildSettings toAutoBuildSettings() {
            AutoBuildSettings settings = new AutoBuildSettings();
            settings.noHatchMode = this.noHatchMode; // Pass directly: 0 or 1
            settings.tierMode = this.tierMode;
            settings.repeatCount = this.tierMode; // Use same value for repetitions
            return settings;
        }
    }


    // Auto-build settings for use when constructing multiblocks
    public static class AutoBuildSettings {
        public int repeatCount = 0;  // Number of repetitions (0 = use default)
        public int noHatchMode = 0;  // 0 = place hatches, 1 = don't place hatches
        public int tierMode = 1;     // Component tier to use
    }
}