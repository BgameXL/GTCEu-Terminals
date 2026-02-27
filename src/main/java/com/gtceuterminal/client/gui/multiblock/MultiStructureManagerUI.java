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

import com.gtceuterminal.client.highlight.MultiblockHighlighter;
import com.gtceuterminal.common.energy.LinkedMachineData;
import com.gtceuterminal.common.network.CPacketSetCustomMultiblockName;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
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
    private WidgetGroup rootGroup;

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
        this.rootGroup = mainGroup;
        mainGroup.setBackground(new ColorRectTexture(COLOR_BG_DARK));

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

        // 🔆 highlight button
        ButtonWidget highlightBtn = new ButtonWidget(dialogW - 104, 2, 16, 16,
                new ColorRectTexture(0x00000000),
                cd -> toggleHighlight(mb));
        highlightBtn.setButtonTexture(
                new TextTexture("§e◉").setWidth(16).setType(TextTexture.TextType.NORMAL));
        highlightBtn.setHoverTexture(new ColorRectTexture(0x33FFFF00));
        entry.addWidget(highlightBtn);

        // ✎ rename button
        ButtonWidget renameBtn = new ButtonWidget(dialogW - 84, 2, 16, 16,
                new ColorRectTexture(0x00000000),
                cd -> openRenameDialog(mb));
        renameBtn.setButtonTexture(
                new TextTexture("§7✎").setWidth(16).setType(TextTexture.TextType.NORMAL));
        renameBtn.setHoverTexture(new ColorRectTexture(0x33FFFFFF));
        entry.addWidget(renameBtn);

        LabelWidget distLabel = new LabelWidget(dialogW - 66, 5, mb.getDistanceString());
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

    // Highlight the multiblock in the world.
    private void toggleHighlight(MultiblockInfo mb) {
        int durationMs = com.gtceuterminal.common.config.ItemsConfig.getMgrHighlightDurationMs();
        int color      = com.gtceuterminal.common.config.ItemsConfig.getMgrHighlightColor();
        MultiblockHighlighter.highlight(mb, color, durationMs);
        // Close the UI so the player can actually see the highlight in the world
        if (player instanceof net.minecraft.client.player.LocalPlayer lp) {
            lp.closeContainer();
        }
    }

    // Opens a dialog to rename the multiblock. Pre-filled with existing custom name (if any).
    private void openRenameDialog(MultiblockInfo mb) {
        if (rootGroup == null) return;

        final int DW = 240;
        final int DH = 86;
        final int DX = (dialogW - DW) / 2;
        final int DY = (dialogH - DH) / 2;

        DialogWidget dialog = new DialogWidget(rootGroup, true);
        dialog.setBackground(new ColorRectTexture(0xA0000000));
        dialog.setClickClose(false);

        WidgetGroup panel = new WidgetGroup(DX, DY, DW, DH);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(0xFF1E1E1E),
                ColorPattern.GRAY.borderTexture(-1)));
        dialog.addWidget(panel);

        // Title bar
        WidgetGroup titleBar = new WidgetGroup(0, 0, DW, 18);
        titleBar.setBackground(new ColorRectTexture(0xFF2A2A2A));
        panel.addWidget(titleBar);
        LabelWidget title = new LabelWidget(8, 4, "§6Rename Multiblock");
        title.setTextColor(0xFFFFAA00);
        titleBar.addWidget(title);

        // Sub-label showing current machine type
        LabelWidget sub = new LabelWidget(8, 22, "§7" + truncate(mb.getMachineTypeName(), 30));
        sub.setTextColor(0xFFAAAAAA);
        panel.addWidget(sub);

        // Text field — null supplier, pre-filled with existing custom name
        TextFieldWidget textField = new TextFieldWidget(8, 36, DW - 16, 16, null, s -> {});
        textField.setMaxStringLength(32);
        textField.setBordered(true);
        textField.setCurrentString(mb.getCustomDisplayName());
        panel.addWidget(textField);

        // Confirm
        ButtonWidget confirmBtn = new ButtonWidget(8, 58, (DW - 20) / 2, 18,
                new ColorRectTexture(0xFF1A4A1A),
                cd -> {
                    String dimId = LinkedMachineData.dimId(player.level());
                    String key   = mb.posKey(dimId);
                    String name  = textField.getCurrentString().trim();
                    TerminalNetwork.CHANNEL.sendToServer(
                            new CPacketSetCustomMultiblockName(null, key, name, false));
                    // Update local cache immediately so the list reflects the new name
                    mb.setCustomDisplayName(name);
                    dialog.close();
                    refreshUI();
                });
        confirmBtn.setButtonTexture(
                new TextTexture("§aConfirm").setWidth((DW - 20) / 2).setType(TextTexture.TextType.NORMAL));
        confirmBtn.setHoverTexture(new ColorRectTexture(0xFF1E6A1E));
        panel.addWidget(confirmBtn);

        // Clear name
        ButtonWidget clearBtn = new ButtonWidget(DW / 2 + 2, 58, (DW - 20) / 2, 18,
                new ColorRectTexture(0xFF3A2A2A),
                cd -> {
                    String dimId = LinkedMachineData.dimId(player.level());
                    String key   = mb.posKey(dimId);
                    TerminalNetwork.CHANNEL.sendToServer(
                            new CPacketSetCustomMultiblockName(null, key, "", true));
                    mb.setCustomDisplayName(null);
                    dialog.close();
                    refreshUI();
                });
        clearBtn.setButtonTexture(
                new TextTexture("§cClear name").setWidth((DW - 20) / 2).setType(TextTexture.TextType.NORMAL));
        clearBtn.setHoverTexture(new ColorRectTexture(0xFF5A3A3A));
        panel.addWidget(clearBtn);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private void openComponentDetail(MultiblockInfo multiblock) {
        // GTCEUTerminalMod.LOGGER.info("Opening component detail for: {}", multiblock.getName());

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

    // Rebuilds the multiblock list UI.
    private void rebuildList() {
        if (multiblockScroll == null) return;
        multiblockScroll.clearAllWidgets();
        int yPos = 0;
        for (int i = 0; i < multiblocks.size(); i++) {
            multiblockScroll.addWidget(createMultiblockEntry(multiblocks.get(i), i, yPos));
            yPos += 22;
        }
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