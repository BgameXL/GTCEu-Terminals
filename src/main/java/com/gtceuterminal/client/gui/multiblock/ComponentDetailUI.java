package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.dialog.ComponentUpgradeDialog;
import com.gtceuterminal.client.gui.factory.MultiStructureUIFactory;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.multiblock.MultiblockScanner;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

// Multiblock component details screen
public class ComponentDetailUI {

    private static final int GUI_WIDTH = 500;
    private static final int GUI_HEIGHT = 360;

    // GTCEu Colors
    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_HOVER = 0x40FFFFFF;
    private static final int COLOR_SUCCESS = 0xFF00FF00;

    private final MultiStructureUIFactory.MultiStructureHolder holder;
    private final Player player;
    private final MultiblockInfo multiblock;
    private ModularUI gui;

    public ComponentDetailUI(MultiStructureUIFactory.MultiStructureHolder holder,
                             Player player,
                             MultiblockInfo multiblock) {
        this.holder = holder;
        this.player = player;
        this.multiblock = multiblock;
    }

    public ModularUI createUI() {
        WidgetGroup mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        mainGroup.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        // Principal panel
        mainGroup.addWidget(createMainPanel());

        // Header
        mainGroup.addWidget(createHeader());

        // Multiblock info
        mainGroup.addWidget(createInfoPanel());

        // Component groups list
        mainGroup.addWidget(createComponentGroupsList());

        // Action buttons
        mainGroup.addWidget(createActionButtons());

        // Back button
        mainGroup.addWidget(createBackButton());

        this.gui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), holder, player);
        gui.widget(mainGroup);
        gui.background(new ColorRectTexture(0x90000000));

        return gui;
    }

    private WidgetGroup createMainPanel() {
        WidgetGroup panel = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Bordes GTCEu style
        panel.addWidget(new ImageWidget(0, 0, GUI_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        panel.addWidget(new ImageWidget(0, 0, 2, GUI_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        panel.addWidget(new ImageWidget(GUI_WIDTH - 2, 0, 2, GUI_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_DARK)));
        panel.addWidget(new ImageWidget(0, GUI_HEIGHT - 2, GUI_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_DARK)));

        return panel;
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, GUI_WIDTH - 4, 28);
        header.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        // Títle
        String title = "§l§f" + multiblock.getName() + " - Components";
        LabelWidget titleLabel = new LabelWidget(GUI_WIDTH / 2 - 120, 10, title);
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        return header;
    }

    private WidgetGroup createInfoPanel() {
        WidgetGroup infoPanel = new WidgetGroup(10, 35, GUI_WIDTH - 20, 50);
        infoPanel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        int yPos = 8;

        // Multiblock name
        LabelWidget nameLabel = new LabelWidget(10, yPos,
                "§7Multiblock: §f" + multiblock.getName());
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(nameLabel);

        yPos += 14;

        // Tier
        LabelWidget tierLabel = new LabelWidget(10, yPos,
                "§7Tier: §f" + multiblock.getTierName());
        tierLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(tierLabel);

        // Distance
        LabelWidget distLabel = new LabelWidget(200, yPos,
                "§7Distance: §f" + multiblock.getDistanceString());
        distLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(distLabel);

        yPos += 14;

        // Component count
        List<ComponentGroup> groups = multiblock.getGroupedComponents();
        int totalComponents = multiblock.getComponents().size();
        LabelWidget countLabel = new LabelWidget(10, yPos,
                "§7Components: §f" + totalComponents + " §7(§f" + groups.size() + " §7groups)");
        countLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(countLabel);

        return infoPanel;
    }

    private WidgetGroup createComponentGroupsList() {
        WidgetGroup listPanel = new WidgetGroup(10, 90, GUI_WIDTH - 20, 220);
        listPanel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_DARK),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        // Label
        LabelWidget listLabel = new LabelWidget(10, 5, "§l§7Component Groups:");
        listLabel.setTextColor(COLOR_TEXT_WHITE);
        listPanel.addWidget(listLabel);

        // Scrollable list
        DraggableScrollableWidgetGroup scrollWidget = new DraggableScrollableWidgetGroup(
                5, 25, GUI_WIDTH - 35, 185
        );
        scrollWidget.setYScrollBarWidth(8);
        scrollWidget.setYBarStyle(
                new ColorRectTexture(COLOR_BORDER_DARK),
                new ColorRectTexture(COLOR_BORDER_LIGHT)
        );

        List<ComponentGroup> groups = multiblock.getGroupedComponents();
        int yPos = 0;

        for (ComponentGroup group : groups) {
            scrollWidget.addWidget(createComponentGroupEntry(group, yPos));
            yPos += 45;
        }

        listPanel.addWidget(scrollWidget);
        return listPanel;
    }

    private WidgetGroup createComponentGroupEntry(ComponentGroup group, int yPos) {
        WidgetGroup entry = new WidgetGroup(0, yPos, GUI_WIDTH - 50, 40);
        entry.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        // Component type icon/dot
        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            // Dot indicator
            entry.addWidget(new ImageWidget(8, 15, 8, 8,
                    new ColorRectTexture(COLOR_SUCCESS)));

            // Type name
            String typeName = group.getType().name().replace("_", " ");
            LabelWidget typeLabel = new LabelWidget(22, 5, "§f" + typeName);
            typeLabel.setTextColor(COLOR_TEXT_WHITE);
            entry.addWidget(typeLabel);

            // Count
            LabelWidget countLabel = new LabelWidget(22, 17,
                    "§7Count: §f" + group.getCount());
            countLabel.setTextColor(COLOR_TEXT_GRAY);
            entry.addWidget(countLabel);

            // Current tier
            LabelWidget tierLabel = new LabelWidget(150, 17,
                    "§7Tier: §f" + rep.getTierName());
            tierLabel.setTextColor(COLOR_TEXT_GRAY);
            entry.addWidget(tierLabel);

            // Upgrade button
            ButtonWidget upgradeBtn = new ButtonWidget(
                    GUI_WIDTH - 140, 8, 80, 24,
                    new GuiTextureGroup(
                            new ColorRectTexture(COLOR_BG_LIGHT),
                            new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                    ),
                    cd -> openUpgradeDialog(group)
            );

            upgradeBtn.setButtonTexture(new TextTexture("§aUpgrade")
                    .setWidth(80)
                    .setType(TextTexture.TextType.NORMAL));

            upgradeBtn.setHoverTexture(new GuiTextureGroup(
                    new ColorRectTexture(COLOR_BG_LIGHT),
                    new ColorBorderTexture(1, COLOR_TEXT_WHITE)
            ));

            // Check if can upgrade
            if (!rep.getPossibleUpgradeTiers().isEmpty()) {
                entry.addWidget(upgradeBtn);
            } else {
                LabelWidget maxLabel = new LabelWidget(GUI_WIDTH - 140, 15, "§7Max Tier");
                maxLabel.setTextColor(COLOR_TEXT_GRAY);
                entry.addWidget(maxLabel);
            }
        }

        return entry;
    }

    private void openUpgradeDialog(ComponentGroup group) {
        GTCEUTerminalMod.LOGGER.info("Opening upgrade dialog for group: {}", group.getType());

        if (gui != null && gui.mainGroup != null) {
            ComponentUpgradeDialog dialog = new ComponentUpgradeDialog(
                    gui.mainGroup, // parent widget group
                    this,
                    null,
                    group,
                    multiblock,
                    player
            );
        }
    }

    private void openBulkUpgrade() {
        GTCEUTerminalMod.LOGGER.info("Opening bulk upgrade for multiblock: {}", multiblock.getName());
        player.displayClientMessage(
                Component.literal("§7Bulk upgrade not yet implemented"),
                true
        );
    }

    private void scanComponents() {
        GTCEUTerminalMod.LOGGER.info("Scanning components for multiblock: {}", multiblock.getName());
        player.displayClientMessage(
                Component.literal("§aRescanned components"),
                true
        );
    }

    private void goBack() {
        // Cerrar esta UI
        player.displayClientMessage(
                Component.literal("§7Use ESC to close"),
                true
        );
    }

    private WidgetGroup createActionButtons() {
        WidgetGroup buttonPanel = new WidgetGroup(10, 315, GUI_WIDTH - 20, 30);

        // Bulk Upgrade button
        ButtonWidget bulkBtn = new ButtonWidget(
                0, 5, 120, 24,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> openBulkUpgrade()
        );
        bulkBtn.setButtonTexture(new TextTexture("§aBulk Upgrade")
                .setWidth(120)
                .setType(TextTexture.TextType.NORMAL));
        bulkBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));
        buttonPanel.addWidget(bulkBtn);

        // Scan Components button
        ButtonWidget scanBtn = new ButtonWidget(
                130, 5, 120, 24,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> scanComponents()
        );
        scanBtn.setButtonTexture(new TextTexture("§7Scan Components")
                .setWidth(120)
                .setType(TextTexture.TextType.NORMAL));
        scanBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));
        buttonPanel.addWidget(scanBtn);

        return buttonPanel;
    }

    private ButtonWidget createBackButton() {
        ButtonWidget backBtn = new ButtonWidget(
                10, 5, 60, 22,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> goBack()
        );

        backBtn.setButtonTexture(new TextTexture("§7← Back")
                .setWidth(60)
                .setType(TextTexture.TextType.NORMAL));

        backBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        return backBtn;
    }

    public static ModularUI create(MultiStructureUIFactory.MultiStructureHolder holder,
                                   Player player,
                                   MultiblockInfo multiblock) {
        ComponentDetailUI ui = new ComponentDetailUI(holder, player, multiblock);
        return ui.createUI();
    }
}