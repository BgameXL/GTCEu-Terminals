package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.DismantlerUIFactory;
import com.gtceuterminal.client.gui.widget.BlockListWidget;
import com.gtceuterminal.client.gui.widget.MultiblockPreviewWidget;
import com.gtceuterminal.common.multiblock.DismantleScanner;
import com.gtceuterminal.common.network.CPacketDismantle;
import com.gtceuterminal.common.network.TerminalNetwork;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class DismantlerUI {

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
    private static final int COLOR_SUCCESS = 0xFF00FF00;
    private static final int COLOR_WARNING = 0xFFFFAA00;
    private static final int COLOR_ERROR = 0xFFFF0000;

    private final DismantlerUIFactory.DismantlerHolder holder;
    private final Player player;
    private DismantleScanner.ScanResult scanResult;

    public DismantlerUI(DismantlerUIFactory.DismantlerHolder holder, Player player) {
        this.holder = holder;
        this.player = player;
        this.scanResult = holder.getScanResult();
    }

    public ModularUI createUI() {
        WidgetGroup mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        mainGroup.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        // Main panel
        mainGroup.addWidget(createMainPanel());

        // Header
        mainGroup.addWidget(createHeader());

        // Preview 3D (left side)
        mainGroup.addWidget(createPreviewPanel());

        // Block list (right side)
        mainGroup.addWidget(createBlockListPanel());

        // Info bar
        mainGroup.addWidget(createInfoBar());

        // Action buttons
        mainGroup.addWidget(createActionButtons());

        ModularUI ui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), holder, player);
        ui.widget(mainGroup);
        ui.background(new ColorRectTexture(0x90000000));

        return ui;
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
        LabelWidget title = new LabelWidget(GUI_WIDTH / 2 - 80, 9,
                "§l§fMultiblock Dismantler");
        title.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(title);

        String coords = String.format("§7[%d, %d, %d]",
                holder.getControllerPos().getX(),
                holder.getControllerPos().getY(),
                holder.getControllerPos().getZ());
        LabelWidget coordsLabel = new LabelWidget(GUI_WIDTH - 120, 9, coords);
        coordsLabel.setTextColor(COLOR_TEXT_GRAY);
        header.addWidget(coordsLabel);

        return header;
    }

    private WidgetGroup createPreviewPanel() {
        WidgetGroup panel = new WidgetGroup(10, 35, 240, 240);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_LIGHT),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        // Label
        LabelWidget label = new LabelWidget(5, 5, "§7Preview");
        label.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(label);

        // Preview 3D Widget
        if (scanResult != null) {
            MultiblockPreviewWidget preview = new MultiblockPreviewWidget(
                    10, 25, 220, 200, scanResult
            );
            panel.addWidget(preview);
        } else {
            LabelWidget errorLabel = new LabelWidget(80, 110, "§cNo data");
            errorLabel.setTextColor(COLOR_ERROR);
            panel.addWidget(errorLabel);
        }

        return panel;
    }

    private WidgetGroup createBlockListPanel() {
        WidgetGroup panel = new WidgetGroup(260, 35, 230, 240);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_LIGHT),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        // Label
        LabelWidget label = new LabelWidget(5, 5, "§7Blocks to Recover");
        label.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(label);

        // Total count
        if (scanResult != null) {
            String totalText = String.format("§7Total: §f%d blocks", scanResult.getTotalBlocks());
            LabelWidget totalLabel = new LabelWidget(5, 18, totalText);
            totalLabel.setTextColor(COLOR_TEXT_WHITE);
            panel.addWidget(totalLabel);

            // Block list
            BlockListWidget blockList = new BlockListWidget(
                    5, 35, 220, 200, scanResult
            );
            panel.addWidget(blockList);
        }

        return panel;
    }

    private WidgetGroup createInfoBar() {
        WidgetGroup bar = new WidgetGroup(10, 280, GUI_WIDTH - 20, 40);
        bar.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)
        ));

        int yPos = 8;

        // Inventory space check
        int emptySlots = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }

        String inventoryText = String.format("§7Inventory: §f%d §7empty slots", emptySlots);
        LabelWidget invLabel = new LabelWidget(10, yPos, inventoryText);
        invLabel.setTextColor(COLOR_TEXT_WHITE);
        bar.addWidget(invLabel);

        // Warning if not enough space
        if (scanResult != null && emptySlots < scanResult.getBlockCounts().size()) {
            LabelWidget warning = new LabelWidget(10, yPos + 12,
                    "§6⚠ Warning: Not enough inventory space! Items will drop on ground.");
            warning.setTextColor(COLOR_WARNING);
            bar.addWidget(warning);
        } else {
            LabelWidget ok = new LabelWidget(10, yPos + 12,
                    "§a✓ Enough space to recover all blocks");
            ok.setTextColor(COLOR_SUCCESS);
            bar.addWidget(ok);
        }

        return bar;
    }

    private WidgetGroup createActionButtons() {
        WidgetGroup buttons = new WidgetGroup(10, GUI_HEIGHT - 35, GUI_WIDTH - 20, 28);

        // Botón Dismantle
        ButtonWidget dismantleBtn = createButton(
                0, 0, 200, 28,
                "§cDismantle Multiblock",
                cd -> performDismantle()
        );
        buttons.addWidget(dismantleBtn);

        // Cancel Button
        ButtonWidget cancelBtn = createButton(
                GUI_WIDTH - 220, 0, 200, 28,
                "Cancel",
                cd -> closeUI()
        );
        buttons.addWidget(cancelBtn);

        return buttons;
    }

    private ButtonWidget createButton(int x, int y, int width, int height, String text,
                                      java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> onPress) {
        ButtonWidget button = new ButtonWidget(x, y, width, height,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                onPress);

        button.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        button.setButtonTexture(new TextTexture(text)
                .setWidth(width)
                .setType(TextTexture.TextType.NORMAL));

        return button;
    }

    private void performDismantle() {
        GTCEUTerminalMod.LOGGER.info("Dismantling multiblock at {}", holder.getControllerPos());
        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketDismantle(holder.getControllerPos())
        );

        closeUI();

        // Mensaje al jugador
        player.displayClientMessage(
                Component.literal("§aMultiblock dismantled successfully!"),
                false
        );
    }

    private void closeUI() {
        if (player.containerMenu != null) {
            player.closeContainer();
        }
    }

    public static ModularUI create(DismantlerUIFactory.DismantlerHolder holder, Player player) {
        DismantlerUI ui = new DismantlerUI(holder, player);
        return ui.createUI();
    }
}