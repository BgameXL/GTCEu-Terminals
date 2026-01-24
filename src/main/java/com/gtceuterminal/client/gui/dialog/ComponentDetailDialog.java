package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

// Dialog that displays details of components of a multiblock
public class ComponentDetailDialog extends DialogWidget {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 350;
    private static final int DIALOG_SPACING = 10;

    private static final int UPGRADE_DIALOG_WIDTH = 400;

    // GTCEu Colors
    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF00FF00;

    private final Player player;
    private final MultiblockInfo multiblock;
    private final Runnable onClose;

    public ComponentDetailDialog(WidgetGroup parent, Player player, MultiblockInfo multiblock) {
        this(parent, player, multiblock, null);
    }

    public ComponentDetailDialog(WidgetGroup parent, Player player, MultiblockInfo multiblock, Runnable onClose) {
        super(parent, true);
        this.player = player;
        this.multiblock = multiblock;
        this.onClose = onClose;

        initDialog();
    }

    @Override
    public void close() {
        // Close any upgrade dialogs that may be open alongside this detail dialog.
        for (Widget widget : new ArrayList<>(parent.widgets)) {
            if (widget instanceof ComponentUpgradeDialog) {
                ((ComponentUpgradeDialog) widget).close();
            }
        }
        super.close();
        if (onClose != null) {
            onClose.run();
        }
    }

    private String getDisplayMultiblockName() {
        String raw = multiblock.getName();
        if (raw == null || raw.isEmpty()) return "Unknown Multiblock";
        if (!raw.contains(" ") && raw.contains(".")) {
            String localized = Component.translatable(raw).getString();
            if (localized != null && !localized.isEmpty()) return localized;
        }
        return raw;
    }

    private void initDialog() {
        int totalWidth = UPGRADE_DIALOG_WIDTH + DIALOG_SPACING + DIALOG_WIDTH;

        int startX = (parent.getSize().width - totalWidth) / 2;
        int rightX = startX + UPGRADE_DIALOG_WIDTH + DIALOG_SPACING;
        int centerY = (parent.getSize().height - DIALOG_HEIGHT) / 2;

        setSize(new com.lowdragmc.lowdraglib.utils.Size(DIALOG_WIDTH, DIALOG_HEIGHT));
        setSelfPosition(new com.lowdragmc.lowdraglib.utils.Position(rightX, centerY));

        setBackground(new ColorRectTexture(COLOR_BG_DARK));

        // Borders
        addWidget(new ImageWidget(0, 0, DIALOG_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(0, 0, 2, DIALOG_HEIGHT, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(DIALOG_WIDTH - 2, 0, 2, DIALOG_HEIGHT, new ColorRectTexture(COLOR_BORDER_DARK)));
        addWidget(new ImageWidget(0, DIALOG_HEIGHT - 2, DIALOG_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_DARK)));

        addWidget(createHeader());
        addWidget(createInfoPanel());
        addWidget(createComponentGroupsList());
        addWidget(createCloseButton());
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, DIALOG_WIDTH - 4, 26);
        header.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        String title = "§l§f" + getDisplayMultiblockName() + " - Components";
        LabelWidget titleLabel = new LabelWidget(10, 8, title);
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        return header;
    }

    private WidgetGroup createInfoPanel() {
        WidgetGroup infoPanel = new WidgetGroup(10, 32, DIALOG_WIDTH - 20, 46);
        infoPanel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        int yPos = 6;

        LabelWidget nameLabel = new LabelWidget(10, yPos,
                "§7Multiblock: §f" + getDisplayMultiblockName());
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(nameLabel);

        yPos += 13;

        LabelWidget tierLabel = new LabelWidget(10, yPos,
                "§7Tier: §f" + multiblock.getTierName());
        tierLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(tierLabel);

        LabelWidget distLabel = new LabelWidget(220, yPos,
                "§7Distance: §f" + multiblock.getDistanceString());
        distLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(distLabel);

        yPos += 13;

        List<ComponentGroup> groups = multiblock.getGroupedComponents();
        int totalComponents = multiblock.getComponents().size();
        LabelWidget countLabel = new LabelWidget(10, yPos,
                "§7Components: §f" + totalComponents + " §7(§f" + groups.size() + " §7groups)");
        countLabel.setTextColor(COLOR_TEXT_WHITE);
        infoPanel.addWidget(countLabel);

        return infoPanel;
    }

    private WidgetGroup createComponentGroupsList() {
        WidgetGroup listPanel = new WidgetGroup(10, 82, DIALOG_WIDTH - 20, 228);
        listPanel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_DARK),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        LabelWidget listLabel = new LabelWidget(10, 4, "§l§7Component Groups:");
        listLabel.setTextColor(COLOR_TEXT_WHITE);
        listPanel.addWidget(listLabel);

        DraggableScrollableWidgetGroup scrollWidget = new DraggableScrollableWidgetGroup(
                5, 22, DIALOG_WIDTH - 35, 198
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
            yPos += 42;
        }

        listPanel.addWidget(scrollWidget);
        return listPanel;
    }

    private WidgetGroup createComponentGroupEntry(ComponentGroup group, int yPos) {
        WidgetGroup entry = new WidgetGroup(0, yPos, DIALOG_WIDTH - 50, 38);
        entry.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            if (!rep.getPossibleUpgradeTiers().isEmpty()) {
                ButtonWidget clickableArea = new ButtonWidget(
                        0, 0, DIALOG_WIDTH - 120, 38,
                        new ColorRectTexture(0x00000000),
                        cd -> openUpgradeDialog(group)
                );
                clickableArea.setHoverTexture(new ColorRectTexture(0x33FFFFFF));
                entry.addWidget(clickableArea);
            }

            entry.addWidget(new ImageWidget(6, 14, 7, 7, new ColorRectTexture(COLOR_SUCCESS)));

            String typeName = group.getType().name().replace("_", " ");
            LabelWidget typeLabel = new LabelWidget(18, 4, "§f" + typeName);
            typeLabel.setTextColor(COLOR_TEXT_WHITE);
            entry.addWidget(typeLabel);

            LabelWidget countLabel = new LabelWidget(18, 16, "§7Count: §f" + group.getCount());
            countLabel.setTextColor(COLOR_TEXT_GRAY);
            entry.addWidget(countLabel);

            LabelWidget tierLabel = new LabelWidget(130, 16, "§7Tier: §f" + rep.getTierName());
            tierLabel.setTextColor(COLOR_TEXT_GRAY);
            entry.addWidget(tierLabel);

            if (!rep.getPossibleUpgradeTiers().isEmpty()) {
                ButtonWidget upgradeBtn = new ButtonWidget(
                        DIALOG_WIDTH - 120, 7, 90, 22,
                        new GuiTextureGroup(
                                new ColorRectTexture(0xFF2E7D32),
                                new ColorBorderTexture(1, COLOR_SUCCESS)
                        ),
                        cd -> openUpgradeDialog(group)
                );

                upgradeBtn.setButtonTexture(new TextTexture("§a§lChange")
                        .setWidth(85)
                        .setType(TextTexture.TextType.NORMAL));

                upgradeBtn.setHoverTexture(new GuiTextureGroup(
                        new ColorRectTexture(0xFF43A047),
                        new ColorBorderTexture(1, COLOR_TEXT_WHITE)
                ));

                entry.addWidget(upgradeBtn);
            } else {
                LabelWidget maxLabel = new LabelWidget(DIALOG_WIDTH - 120, 14, "§7Max Tier");
                maxLabel.setTextColor(COLOR_TEXT_GRAY);
                entry.addWidget(maxLabel);
            }
        }

        return entry;
    }

    private ButtonWidget createCloseButton() {
        ButtonWidget closeBtn = new ButtonWidget(
                DIALOG_WIDTH - 28, 4, 22, 22,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> close()
        );

        closeBtn.setButtonTexture(new TextTexture("§cX")
                .setWidth(22)
                .setType(TextTexture.TextType.NORMAL));

        closeBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(0xFFFF0000),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        return closeBtn;
    }

    private void openUpgradeDialog(ComponentGroup group) {
        GTCEUTerminalMod.LOGGER.info("Opening upgrade dialog for group: {}", group.getType());

        for (Widget widget : new ArrayList<>(parent.widgets)) {
            if (widget instanceof ComponentUpgradeDialog) {
                ((ComponentUpgradeDialog) widget).close();
            }
        }

        new ComponentUpgradeDialog(
                parent,
                null,
                this,
                group,
                multiblock,
                player
        );
    }
}