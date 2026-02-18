package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class ComponentDetailDialog extends DialogWidget {

    private static final int dialogW = 400;
    private static final int dialogH = 350;
    private static final int dialogS = 10;
    private static final int UPGRADE_dialogW = 400;

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
    private int W = dialogW;
    private int H = dialogH;

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
        var mc = Minecraft.getInstance();
        int sw = mc.screen != null ? mc.screen.width : mc.getWindow().getGuiScaledWidth();
        int sh = mc.screen != null ? mc.screen.height : mc.getWindow().getGuiScaledHeight();

        int margin = 10;

        int maxW = sw - margin * 2;
        int maxH = sh - margin * 2;

        int contentW = dialogW;
        int contentH = dialogH;

        int viewportW = Math.min(contentW, maxW);
        int viewportH = Math.min(contentH, maxH);

        int screenX = (sw - viewportW) / 2;
        int screenY = (sh - viewportH) / 2;

        screenX = Mth.clamp(screenX, margin, sw - viewportW - margin);
        screenY = Mth.clamp(screenY, margin, sh - viewportH - margin);

        Position parentAbsPos = parent.getPosition();
        int x = screenX - parentAbsPos.x;
        int y = screenY - parentAbsPos.y;

        if (contentW <= maxW && contentH <= maxH) {
            this.W = contentW;
            this.H = contentH;
            setSize(new Size(contentW, contentH));
            setSelfPosition(new Position(x, y));
            setBackground(new ColorRectTexture(COLOR_BG_DARK));

            addWidget(new ImageWidget(0, 0, contentW, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
            addWidget(new ImageWidget(0, 0, 2, contentH, new ColorRectTexture(COLOR_BORDER_LIGHT)));
            addWidget(new ImageWidget(contentW - 2, 0, 2, contentH, new ColorRectTexture(COLOR_BORDER_DARK)));
            addWidget(new ImageWidget(0, contentH - 2, contentW, 2, new ColorRectTexture(COLOR_BORDER_DARK)));

            addWidget(createHeader());
            addWidget(createInfoPanel());
            addWidget(createComponentGroupsList());
            addWidget(createCloseButton());

        } else {
            this.W = viewportW;
            this.H = viewportH;
            setSize(new Size(viewportW, viewportH));
            setSelfPosition(new Position(x, y));
            setBackground(new ColorRectTexture(COLOR_BG_DARK));

            WidgetGroup content = new WidgetGroup(0, 0, contentW, contentH);
            content.setBackground(new ColorRectTexture(COLOR_BG_DARK));

            content.addWidget(new ImageWidget(0, 0, contentW, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
            content.addWidget(new ImageWidget(0, 0, 2, contentH, new ColorRectTexture(COLOR_BORDER_LIGHT)));
            content.addWidget(new ImageWidget(contentW - 2, 0, 2, contentH, new ColorRectTexture(COLOR_BORDER_DARK)));
            content.addWidget(new ImageWidget(0, contentH - 2, contentW, 2, new ColorRectTexture(COLOR_BORDER_DARK)));

            content.addWidget(createHeader());
            content.addWidget(createInfoPanel());
            content.addWidget(createComponentGroupsList());
            content.addWidget(createCloseButton());

            DraggableScrollableWidgetGroup viewport =
                    new DraggableScrollableWidgetGroup(0, 0, viewportW, viewportH);
            viewport.setYScrollBarWidth(8);
            viewport.setYBarStyle(
                    new ColorRectTexture(COLOR_BORDER_DARK),
                    new ColorRectTexture(COLOR_BORDER_LIGHT)
            );
            viewport.addWidget(content);

            addWidget(viewport);
        }
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, W - 4, 26);
        header.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        String title = "§l§f" + getDisplayMultiblockName() + " - Components";
        LabelWidget titleLabel = new LabelWidget(10, 8, title);
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        return header;
    }

    private WidgetGroup createInfoPanel() {
        WidgetGroup infoPanel = new WidgetGroup(10, 32, W - 20, 46);
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
        int listY = 82;
        int bottomPad = 10;
        int listH = H - listY - bottomPad;

        if (listH < 130) listH = 130;

        WidgetGroup listPanel = new WidgetGroup(10, listY, W - 20, listH);
        listPanel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_DARK),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        LabelWidget listLabel = new LabelWidget(10, 4, "§l§7Component Groups:");
        listLabel.setTextColor(COLOR_TEXT_WHITE);
        listPanel.addWidget(listLabel);

        int scrollX = 5;
        int scrollY = 22;
        int scrollW = (W - 20) - 15;
        int scrollH = listH - 30;

        DraggableScrollableWidgetGroup scrollWidget =
                new DraggableScrollableWidgetGroup(scrollX, scrollY, scrollW, scrollH);

        scrollWidget.setYScrollBarWidth(8);
        scrollWidget.setYBarStyle(
                new ColorRectTexture(COLOR_BORDER_DARK),
                new ColorRectTexture(COLOR_BORDER_LIGHT)
        );

        List<ComponentGroup> groups = multiblock.getGroupedComponents();
        int yPos = 0;

        for (ComponentGroup group : groups) {
            scrollWidget.addWidget(createComponentGroupEntry(group, yPos, scrollW));
            yPos += 42;
        }

        listPanel.addWidget(scrollWidget);
        return listPanel;
    }

    // Creates a single entry for a component group
    private WidgetGroup createComponentGroupEntry(ComponentGroup group, int yPos, int entryW) {
        WidgetGroup entry = new WidgetGroup(0, yPos, entryW, 38);
        entry.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            if (!rep.getPossibleUpgradeTiers().isEmpty()) {
                ButtonWidget clickableArea = new ButtonWidget(
                        0, 0, entryW, 38,
                        new ColorRectTexture(0x00000000),
                        cd -> openUpgradeDialog(group)
                );
                clickableArea.setHoverTexture(new ColorRectTexture(0x22FFFFFF));
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

            String tierText = "§7Tier: §f" + rep.getTierName();
            if (!rep.getPossibleUpgradeTiers().isEmpty()) tierText += " §a→";

            LabelWidget tierLabel = new LabelWidget(Math.max(18, entryW - 130), 16, tierText);
            tierLabel.setTextColor(COLOR_TEXT_GRAY);
            entry.addWidget(tierLabel);
        }

        return entry;
    }

    private ButtonWidget createCloseButton() {
        ButtonWidget closeBtn = new ButtonWidget(
                W - 28, 4, 22, 22,
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
        ComponentUpgradeDialog dialog = new ComponentUpgradeDialog(
                this.gui.mainGroup,
                null,
                this,
                group,
                multiblock,
                player
        );
    }
}