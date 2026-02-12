package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.MultiStructureUIFactory;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.multiblock.MultiblockScanner;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

// Multi-Structure Manager UI
public class MultiStructureManagerUI {

    private static final int dialogW = 320;
    private static final int dialogH = 240;
    private static final int SCAN_RADIUS = 32;

    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_HOVER = 0x40FFFFFF;

    private final MultiStructureUIFactory.MultiStructureHolder holder;
    private final Player player;
    private List<MultiblockInfo> multiblocks = new ArrayList<>();
    private int selectedIndex = -1;
    private ModularUI gui;

    // Keep a reference so we can disable hover/clicks when modal dialogs are open
    private DraggableScrollableWidgetGroup multiblockScroll;

    public MultiStructureManagerUI(MultiStructureUIFactory.MultiStructureHolder holder, Player player) {
        this.holder = holder;
        this.player = player;
        scanMultiblocks();
    }

    private void scanMultiblocks() {
        this.multiblocks = MultiblockScanner.scanNearbyMultiblocks(
                player, player.level(), SCAN_RADIUS
        );
        GTCEUTerminalMod.LOGGER.info("Scanned {} multiblocks", multiblocks.size());
    }

    public ModularUI createUI() {
        WidgetGroup mainGroup = new WidgetGroup(0, 0, dialogW, dialogH);
        mainGroup.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int margin = 8;

        int uiW = Math.min(dialogW, sw - margin * 2);
        int uiH = Math.min(dialogH, sh - margin * 2);


        mainGroup.addWidget(createMainPanel());
        mainGroup.addWidget(createHeader());
        mainGroup.addWidget(createMultiblockList());
        mainGroup.addWidget(createRefreshButton());

        this.gui = new ModularUI(new Size(dialogW, dialogH), holder, player);
        gui.widget(mainGroup);
        gui.background(new ColorRectTexture(0x90000000));

        return gui;
    }

    private WidgetGroup createMainPanel() {
        WidgetGroup panel = new WidgetGroup(0, 0, dialogW, dialogH);

        panel.addWidget(new ImageWidget(0, 0, dialogW, 2,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        panel.addWidget(new ImageWidget(0, 0, 2, dialogH,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        panel.addWidget(new ImageWidget(dialogW - 2, 0, 2, dialogH,
                new ColorRectTexture(COLOR_BORDER_DARK)));
        panel.addWidget(new ImageWidget(0, dialogH - 2, dialogW, 2,
                new ColorRectTexture(COLOR_BORDER_DARK)));

        return panel;
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, dialogW - 4, 28);
        header.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        String title = "Nearby Multiblocks (" + multiblocks.size() + ")";
        LabelWidget titleLabel = new LabelWidget(10, 10, title);
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        return header;
    }

    private WidgetGroup createMultiblockList() {
        WidgetGroup listGroup = new WidgetGroup(10, 35, dialogW - 20, 180);

        listGroup.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_DARK),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        DraggableScrollableWidgetGroup scrollWidget = new DraggableScrollableWidgetGroup(
                2, 2, dialogW - 30, 176
        );
        this.multiblockScroll = scrollWidget;
        scrollWidget.setYScrollBarWidth(8);
        scrollWidget.setYBarStyle(
                new ColorRectTexture(COLOR_BORDER_DARK),
                new ColorRectTexture(COLOR_BORDER_LIGHT)
        );

        int yPos = 0;
        for (int i = 0; i < multiblocks.size(); i++) {
            final int index = i;
            MultiblockInfo mb = multiblocks.get(i);
            scrollWidget.addWidget(createMultiblockEntry(mb, index, yPos));
            yPos += 22;
        }

        listGroup.addWidget(scrollWidget);
        return listGroup;
    }

    private WidgetGroup createMultiblockEntry(MultiblockInfo mb, int index, int yPos) {
        WidgetGroup entry = new WidgetGroup(0, yPos, dialogW - 40, 20);

        boolean isSelected = (index == selectedIndex);

        ButtonWidget clickBtn = new ButtonWidget(0, 0, dialogW - 40, 20,
                new ColorRectTexture(isSelected ? COLOR_HOVER : 0x00000000),
                cd -> {
                    selectedIndex = index;
                    openComponentDetail(mb);
                });
        clickBtn.setHoverTexture(new ColorRectTexture(COLOR_HOVER));
        entry.addWidget(clickBtn);

        LabelWidget arrow = new LabelWidget(5, 5, "▶");
        arrow.setTextColor(COLOR_TEXT_WHITE);
        entry.addWidget(arrow);

        LabelWidget nameLabel = new LabelWidget(20, 5, mb.getName());
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        entry.addWidget(nameLabel);

        LabelWidget distLabel = new LabelWidget(180, 5, mb.getDistanceString());
        distLabel.setTextColor(COLOR_TEXT_GRAY);
        entry.addWidget(distLabel);

        int statusColor = mb.getStatus().getColor();
        entry.addWidget(new ImageWidget(dialogW - 70, 6, 8, 8,
                new ColorRectTexture(statusColor)));

        return entry;
    }

    // Refresh Button
    private ButtonWidget createRefreshButton() {
        ButtonWidget refreshBtn = new ButtonWidget(
                dialogW - 38, 5, 28, 22,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> {
                    scanMultiblocks();
                    refreshUI();
                }
        );

        refreshBtn.setButtonTexture(new TextTexture("↻")
                .setWidth(28)
                .setType(TextTexture.TextType.NORMAL));

        refreshBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        return refreshBtn;
    }

    private void openComponentDetail(MultiblockInfo multiblock) {
        GTCEUTerminalMod.LOGGER.info("Opening component detail for: {}", multiblock.getName());

        // Disable the underlying list so it doesn't highlight/hover through the dialog.
        if (multiblockScroll != null) {
            multiblockScroll.setActive(false);
        }

        new com.gtceuterminal.client.gui.dialog.ComponentDetailDialog(
                gui.mainGroup,
                player,
                multiblock,
                () -> {
                    // Re-enable list when the dialog closes
                    if (multiblockScroll != null) {
                        multiblockScroll.setActive(true);
                    }
                }
        );
    }

    private void refreshUI() {
        scanMultiblocks();

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§aRefreshed - Found " + multiblocks.size() + " multiblocks"),
                true
        );
    }

    public static ModularUI create(MultiStructureUIFactory.MultiStructureHolder holder, Player player) {
        MultiStructureManagerUI ui = new MultiStructureManagerUI(holder, player);
        return ui.createUI();
    }
}