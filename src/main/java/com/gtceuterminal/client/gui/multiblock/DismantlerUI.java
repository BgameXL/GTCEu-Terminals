package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.client.gui.widget.WallpaperWidget;
import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.BlockListWidget;
import com.gtceuterminal.client.gui.widget.MultiblockPreviewWidget;
import com.gtceuterminal.common.multiblock.DismantleScanner;
import com.gtceuterminal.common.network.CPacketDismantle;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.gtceuterminal.client.gui.factory.DismantlerItemUIFactory;
import com.gtceuterminal.client.gui.theme.ThemeEditorDialog;
import com.gtceuterminal.common.theme.ItemTheme;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class DismantlerUI {

    private static final int GUI_WIDTH  = 500;
    private static final int GUI_HEIGHT = 360;

    // ─── Theme-driven instance colors ─────────────────────────────────────────
    private final int COLOR_BG_DARK;
    private final int COLOR_BG_MEDIUM;
    private final int COLOR_BG_LIGHT;
    private final int COLOR_BORDER_LIGHT;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE  = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY   = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS     = 0xFF00FF00;
    private static final int COLOR_WARNING     = 0xFFFFAA00;
    private static final int COLOR_ERROR       = 0xFFFF0000;

    private WidgetGroup mainGroup;
    private ItemTheme theme;

    private final IUIHolder        uiHolder;
    private final BlockPos         controllerPos;

    private final Player player;
    private DismantleScanner.ScanResult scanResult;

    // ── Constructor B: HeldItemUIFactory path ─────────────────────────────────
    public DismantlerUI(HeldItemUIFactory.HeldItemHolder heldHolder, BlockPos controllerPos) {
        this.uiHolder      = heldHolder;
        this.controllerPos = controllerPos;
        this.player        = heldHolder.player;
        this.theme         = ItemTheme.load(heldHolder.held);
        COLOR_BG_DARK      = theme.bgColor;
        COLOR_BG_MEDIUM    = theme.panelColor;
        COLOR_BG_LIGHT     = theme.isNativeStyle() ? 0xFF3A3A3A : theme.accent(0xAA);
        COLOR_BORDER_LIGHT = theme.isNativeStyle() ? 0xFF555555 : theme.accent(0xFF);
        // Compute scan result directly from world
        this.scanResult    = computeScanResult(player, controllerPos);
    }

    // ── Constructor C: DismantlerItemUIFactory path ───────────────────────────
    public DismantlerUI(DismantlerItemUIFactory.Holder holder, Player player) {
        this.uiHolder      = holder;
        this.controllerPos = holder.controllerPos;
        this.player        = player;
        this.theme         = ItemTheme.load(holder.item);
        COLOR_BG_DARK      = theme.bgColor;
        COLOR_BG_MEDIUM    = theme.panelColor;
        COLOR_BG_LIGHT     = theme.isNativeStyle() ? 0xFF3A3A3A : theme.accent(0xAA);
        COLOR_BORDER_LIGHT = theme.isNativeStyle() ? 0xFF555555 : theme.accent(0xFF);
        this.scanResult    = computeScanResult(player, holder.controllerPos);
    }

    private static DismantleScanner.ScanResult computeScanResult(Player player, BlockPos pos) {
        try {
            var be = player.level().getBlockEntity(pos);
            if (be instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity mbe) {
                var meta = mbe.getMetaMachine();
                if (meta instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine ctrl) {
                    return DismantleScanner.scanMultiblock(player.level(), ctrl);
                }
            }
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.warn("DismantlerUI: could not scan multiblock at {}", pos, t);
        }
        return DismantleScanner.ScanResult.empty();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    public ModularUI createUI() {
        this.mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        WidgetGroup mainGroup = this.mainGroup;
        mainGroup.setBackground(new ColorRectTexture(0x00000000));
        if (!theme.isNativeStyle()) {
            mainGroup.addWidget(new WallpaperWidget(0, 0, GUI_WIDTH, GUI_HEIGHT, () -> this.theme));
        }

        mainGroup.addWidget(createMainPanel());
        mainGroup.addWidget(createHeader());
        mainGroup.addWidget(createPreviewPanel());
        mainGroup.addWidget(createBlockListPanel());
        mainGroup.addWidget(createInfoBar());
        mainGroup.addWidget(createActionButtons());

        return createUIWithViewport(mainGroup);
    }

    private ModularUI createUIWithViewport(WidgetGroup content) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            int margin = 10;
            int maxW   = sw - margin * 2;
            int maxH   = sh - margin * 2;

            if (GUI_WIDTH <= maxW && GUI_HEIGHT <= maxH) {
                ModularUI ui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), uiHolder, player);
                ui.widget(content);
                ui.background(theme.modularUIBackground());
                return ui;
            } else {
                int viewportW = Math.min(GUI_WIDTH, maxW);
                int viewportH = Math.min(GUI_HEIGHT, maxH);
                DraggableScrollableWidgetGroup viewport =
                        new DraggableScrollableWidgetGroup(0, 0, viewportW, viewportH);
                viewport.setYScrollBarWidth(8);
                viewport.setYBarStyle(new ColorRectTexture(COLOR_BORDER_DARK),
                        new ColorRectTexture(COLOR_BORDER_LIGHT));
                viewport.addWidget(content);
                WidgetGroup root = new WidgetGroup(0, 0, viewportW, viewportH);
                root.addWidget(viewport);
                ModularUI ui = new ModularUI(new Size(viewportW, viewportH), uiHolder, player);
                ui.widget(root);
                ui.background(theme.modularUIBackground());
                return ui;
            }
        } catch (Throwable t) {
            ModularUI ui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), uiHolder, player);
            ui.widget(content);
            ui.background(theme.modularUIBackground());
            return ui;
        }
    }

    private WidgetGroup createMainPanel() {
        WidgetGroup panel = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        panel.addWidget(new ImageWidget(0, 0, GUI_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        panel.addWidget(new ImageWidget(0, 0, 2, GUI_HEIGHT, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        panel.addWidget(new ImageWidget(GUI_WIDTH - 2, 0, 2, GUI_HEIGHT, new ColorRectTexture(COLOR_BORDER_DARK)));
        panel.addWidget(new ImageWidget(0, GUI_HEIGHT - 2, GUI_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_DARK)));
        return panel;
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, GUI_WIDTH - 4, 28);
        header.setBackground(theme.panelTexture());

        LabelWidget title = new LabelWidget(GUI_WIDTH / 2 - 80, 9,
                Component.translatable("gui.gtceuterminal.dismantler.title").getString());
        title.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(title);

        String coords = Component.translatable(
                        "gui.gtceuterminal.dismantler.coords_format",
                        controllerPos.getX(),
                        controllerPos.getY(),
                        controllerPos.getZ()
                ).getString();
        LabelWidget coordsLabel = new LabelWidget(GUI_WIDTH - 120, 9, coords);
        coordsLabel.setTextColor(COLOR_TEXT_GRAY);
        header.addWidget(coordsLabel);

        ButtonWidget gearBtn = new ButtonWidget(GUI_WIDTH - 28, 7, 14, 14,
                new ColorRectTexture(0x00000000),
                cd -> ThemeEditorDialog.open(mainGroup, ItemTheme.load(player.getMainHandItem())));
        gearBtn.setButtonTexture(new TextTexture("§7⚙").setWidth(14).setType(TextTexture.TextType.NORMAL));
        gearBtn.setHoverTexture(new ColorRectTexture(0x40FFFFFF));
        gearBtn.setHoverTooltips(Component.translatable("gui.gtceuterminal.theme_settings").getString());
        header.addWidget(gearBtn);

        return header;
    }

    private WidgetGroup createPreviewPanel() {
        WidgetGroup panel = new WidgetGroup(10, 35, 240, 240);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_LIGHT),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)));

        panel.addWidget(new LabelWidget(5, 5,
                Component.translatable("gui.gtceuterminal.dismantler.preview_label").getString())
                .setTextColor(COLOR_TEXT_GRAY));

        if (scanResult != null) {
            panel.addWidget(new MultiblockPreviewWidget(10, 25, 220, 200, scanResult));
        } else {
            panel.addWidget(new LabelWidget(80, 110,
                    Component.translatable("gui.gtceuterminal.dismantler.no_data").getString())
                    .setTextColor(COLOR_ERROR));
        }
        return panel;
    }

    private WidgetGroup createBlockListPanel() {
        WidgetGroup panel = new WidgetGroup(260, 35, 230, 240);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_LIGHT),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)));

        panel.addWidget(new LabelWidget(5, 5,
                Component.translatable("gui.gtceuterminal.dismantler.blocks_to_recover").getString())
                .setTextColor(COLOR_TEXT_GRAY));

        if (scanResult != null) {
            panel.addWidget(new LabelWidget(5, 18,
                    Component.translatable(
                                    "gui.gtceuterminal.dismantler.total_blocks",
                                    scanResult.getTotalBlocks()
                            ).getString())
                    .setTextColor(COLOR_TEXT_WHITE));
            panel.addWidget(new BlockListWidget(5, 35, 220, 200, scanResult));
        }
        return panel;
    }

    private WidgetGroup createInfoBar() {
        WidgetGroup bar = new WidgetGroup(10, 280, GUI_WIDTH - 20, 40);
        bar.setBackground(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_BORDER_DARK)));

        int emptySlots = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).isEmpty()) emptySlots++;
        }

        bar.addWidget(new LabelWidget(10, 8,
                Component.translatable(
                                "gui.gtceuterminal.dismantler.inventory_empty_slots",
                                emptySlots
                        ).getString())
                .setTextColor(COLOR_TEXT_WHITE));

        if (scanResult != null && emptySlots < scanResult.getBlockCounts().size()) {
            bar.addWidget(new LabelWidget(10, 20,
                    Component.translatable("gui.gtceuterminal.dismantler.warning_not_enough_space").getString())
                    .setTextColor(COLOR_WARNING));
        } else {
            bar.addWidget(new LabelWidget(10, 20,
                    Component.translatable("gui.gtceuterminal.dismantler.enough_space").getString())
                    .setTextColor(COLOR_SUCCESS));
        }
        return bar;
    }

    private WidgetGroup createActionButtons() {
        WidgetGroup buttons = new WidgetGroup(10, GUI_HEIGHT - 35, GUI_WIDTH - 20, 28);
        buttons.addWidget(createButton(
                0, 0, 200, 28,
                Component.translatable("gui.gtceuterminal.dismantler.action.dismantle").getString(),
                cd -> performDismantle()));
        buttons.addWidget(createButton(
                GUI_WIDTH - 220, 0, 200, 28,
                Component.translatable("gui.gtceuterminal.dismantler.action.cancel").getString(),
                cd -> closeUI()));
        return buttons;
    }

    private ButtonWidget createButton(int x, int y, int width, int height, String text,
                                      java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> onPress) {
        ButtonWidget btn = new ButtonWidget(x, y, width, height,
                new GuiTextureGroup(new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)), onPress);
        btn.setHoverTexture(new GuiTextureGroup(new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)));
        btn.setButtonTexture(new TextTexture(text).setWidth(width).setType(TextTexture.TextType.NORMAL));
        return btn;
    }

    private void performDismantle() {
        GTCEUTerminalMod.LOGGER.info("Dismantling multiblock at {}", controllerPos);
        TerminalNetwork.CHANNEL.sendToServer(new CPacketDismantle(controllerPos));
        closeUI();
        player.displayClientMessage(
                Component.translatable("gui.gtceuterminal.dismantler.chat.success"),
                false
        );
    }

    private void closeUI() {
        if (player.containerMenu != null) player.closeContainer();
    }

    // ── Static factory methods ────────────────────────────────────────────────
    public static ModularUI create(HeldItemUIFactory.HeldItemHolder heldHolder, BlockPos controllerPos) {
        return new DismantlerUI(heldHolder, controllerPos).createUI();
    }

    public static ModularUI create(DismantlerItemUIFactory.Holder holder, Player player) {
        return new DismantlerUI(holder, player).createUI();
    }
}