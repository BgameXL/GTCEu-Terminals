package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.common.theme.ItemTheme;
import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.dialog.ComponentUpgradeDialog;
import com.gtceuterminal.client.gui.factory.MultiStructureManagerUIFactory;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.multiblock.MultiblockInfo;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.List;

public class ComponentDetailUI {

    private static final int TARGET_W = 500;
    private static final int TARGET_H = 360;

    // GTCEu Colors
    private ItemTheme theme;
    private int COLOR_BG_DARK = 0xFF1A1A1A;
    private int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF00FF00;

    private final IUIHolder holder;
    private final net.minecraft.world.item.ItemStack terminalItem;
    private final Player player;
    private final MultiblockInfo multiblock;
    private ModularUI gui;


    // Calculated (responsive)
    private int uiW, uiH;
    private boolean compact;

    // Layout (calculated in createUI)
    private int headerH, infoH, bottomH, gap, pad;
    private int headerY, infoY, listY, buttonsY;
    private int listH;

    // Font/scale
    private float textScale;

    public ComponentDetailUI(IUIHolder holder, net.minecraft.world.item.ItemStack terminalItem,
                             Player player,
                             MultiblockInfo multiblock) {
        this.holder = holder;
        this.terminalItem = terminalItem;
        this.player = player;
        this.multiblock = multiblock;
        // Apply theme colors
        this.theme              = ItemTheme.load(terminalItem);
        com.gtceuterminal.GTCEUTerminalMod.LOGGER.info(
                "ComponentDetailUI: loaded theme accent=#{} bg=#{} style={} itemTag={}",
                Integer.toHexString(this.theme.accentColor & 0xFFFFFF).toUpperCase(),
                Integer.toHexString(this.theme.bgColor     & 0xFFFFFF).toUpperCase(),
                this.theme.uiStyle,
                terminalItem.getTag() != null
                        ? terminalItem.getTag().contains("Theme") : "NO-TAG");
        this.COLOR_BG_DARK      = theme.bgColor;
        this.COLOR_BG_MEDIUM    = theme.panelColor;
        this.COLOR_BG_LIGHT     = theme.isNativeStyle() ? 0xFF3A3A3A : theme.accent(0xAA);
        this.COLOR_BORDER_LIGHT = theme.isNativeStyle() ? 0xFF555555 : theme.accent(0xFF);
    }

    public ModularUI createUI() {
        int sw = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int margin = 8;

        uiW = Math.min(TARGET_W, sw - margin * 2);
        uiH = Math.min(TARGET_H, sh - margin * 2);

        compact = (uiW < 420) || (uiH < 300);

        // Layout params
        pad = compact ? 6 : 10;
        gap = compact ? 5 : 6;

        headerH = compact ? 24 : 28;
        infoH   = compact ? 44 : 50;
        bottomH = compact ? 26 : 30;

        headerY = 2;
        infoY = headerY + headerH + gap;
        listY = infoY + infoH + gap;
        buttonsY = uiH - bottomH - 8;

        listH = buttonsY - listY - gap;
        if (listH < 90) listH = 90;

        textScale = compact ? 0.85f : 1.0f;

        WidgetGroup mainGroup = new WidgetGroup(0, 0, uiW, uiH);
        mainGroup.addWidget(createMainPanel());
        mainGroup.addWidget(createHeader());
        mainGroup.addWidget(createInfoPanel());
        mainGroup.addWidget(createComponentGroupsList());
        mainGroup.addWidget(createActionButtons());
        mainGroup.addWidget(createBackButton());

        this.gui = new ModularUI(new Size(uiW, uiH), holder, player);
        gui.widget(mainGroup);
        gui.background(theme.modularUIBackground());
        return gui;
    }

    private WidgetGroup createMainPanel() {
        WidgetGroup panel = new WidgetGroup(0, 0, uiW, uiH);

        if (theme.isNativeStyle()) {
            panel.setBackground(com.gregtechceu.gtceu.api.gui.GuiTextures.BACKGROUND);
        } else {
            panel.addWidget(new ImageWidget(0, 0, uiW, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
            panel.addWidget(new ImageWidget(0, 0, 2, uiH, new ColorRectTexture(COLOR_BORDER_LIGHT)));
            panel.addWidget(new ImageWidget(uiW - 2, 0, 2, uiH, new ColorRectTexture(COLOR_BORDER_DARK)));
            panel.addWidget(new ImageWidget(0, uiH - 2, uiW, 2, new ColorRectTexture(COLOR_BORDER_DARK)));
        }

        return panel;
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, headerY, uiW - 4, headerH);
        header.setBackground(theme.headerTexture());

        String title = compact
                ? Component.translatable(
                        "gui.gtceuterminal.component_detail.header.title_compact",
                        multiblock.getName()
                ).getString()
                : Component.translatable(
                        "gui.gtceuterminal.component_detail.header.title_full",
                        multiblock.getName()
                ).getString();

        addText(header, 10, compact ? 7 : 10, uiW - 24, title, textScale);
        return header;
    }

    private WidgetGroup createInfoPanel() {
        WidgetGroup infoPanel = new WidgetGroup(pad, infoY, uiW - pad * 2, infoH);
        infoPanel.setBackground(theme.panelTexture());

        int y = compact ? 6 : 8;

        addText(infoPanel, 10, y, uiW - pad * 2 - 20,
                Component.translatable("gui.gtceuterminal.component_detail.info.multiblock", multiblock.getName()).getString(),
                textScale);

        y += compact ? 12 : 14;

        addText(infoPanel, 10, y, 150,
                Component.translatable("gui.gtceuterminal.component_detail.info.tier", multiblock.getTierName()).getString(),
                textScale);

        String distText = Component.translatable("gui.gtceuterminal.component_detail.info.distance", multiblock.getDistanceString()).getString();
        if (!compact && (uiW >= 430)) {
            addText(infoPanel, 200, y, (uiW - pad * 2) - 210, distText, textScale);
        } else {
            y += compact ? 12 : 14;
            addText(infoPanel, 10, y, uiW - pad * 2 - 20, distText, textScale);
        }

        y += compact ? 12 : 14;

        List<ComponentGroup> groups = multiblock.getGroupedComponents();
        int totalComponents = multiblock.getComponents().size();
        addText(infoPanel, 10, y, uiW - pad * 2 - 20,
                Component.translatable(
                        "gui.gtceuterminal.component_detail.info.components",
                        totalComponents,
                        groups.size()
                ).getString(),
                textScale);

        return infoPanel;
    }

    private WidgetGroup createComponentGroupsList() {
        WidgetGroup listPanel = new WidgetGroup(pad, listY, uiW - pad * 2, listH);
        listPanel.setBackground(theme.panelWithBorderTexture());

        addText(listPanel, 10, 5, uiW - pad * 2 - 20,
                Component.translatable("gui.gtceuterminal.component_detail.list.header").getString(),
                textScale);

        int scrollX = 5;
        int scrollY = compact ? 22 : 25;
        int scrollW = (uiW - pad * 2) - 15;
        int scrollH = listH - scrollY - 6;

        DraggableScrollableWidgetGroup scrollWidget = new DraggableScrollableWidgetGroup(
                scrollX, scrollY, scrollW, scrollH
        );
        scrollWidget.setYScrollBarWidth(8);
        scrollWidget.setYBarStyle(
                new ColorRectTexture(COLOR_BORDER_DARK),
                new ColorRectTexture(COLOR_BORDER_LIGHT)
        );

        List<ComponentGroup> groups = multiblock.getGroupedComponents();
        int yPos = 0;
        int entryH = compact ? 34 : 40;
        int step   = compact ? 38 : 45;

        for (ComponentGroup group : groups) {
            scrollWidget.addWidget(createComponentGroupEntry(group, yPos, scrollW - 10, entryH));
            yPos += step;
        }

        listPanel.addWidget(scrollWidget);
        return listPanel;
    }

    private WidgetGroup createComponentGroupEntry(ComponentGroup group, int yPos, int entryW, int entryH) {
        WidgetGroup entry = new WidgetGroup(0, yPos, entryW, entryH);
        entry.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            int dotY = compact ? 12 : 15;
            entry.addWidget(new ImageWidget(8, dotY, 8, 8, new ColorRectTexture(COLOR_SUCCESS)));

            String typeName = group.getType().getDisplayNameComponent().getString();
            addText(entry, 22, compact ? 4 : 5, entryW - 120, "§f" + typeName, textScale);

            addText(entry, 22, compact ? 16 : 17, 120,
                    Component.translatable("gui.gtceuterminal.component_detail.entry.count", group.getCount()).getString(),
                    textScale);

            addText(entry, compact ? 130 : 150, compact ? 16 : 17, entryW - 220,
                    Component.translatable("gui.gtceuterminal.component_detail.entry.tier", tierNameForList(group, rep)).getString(),
                    textScale);

            int btnW = compact ? 64 : 80;
            int btnH = compact ? 20 : 24;
            int btnX = entryW - btnW - 10;
            int btnY = compact ? 6 : 8;

            if (!rep.getPossibleUpgradeTiers().isEmpty()) {
                ButtonWidget upgradeBtn = new ButtonWidget(
                        btnX, btnY, btnW, btnH,
                        new GuiTextureGroup(
                                new ColorRectTexture(COLOR_BG_LIGHT),
                                new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                        ),
                        cd -> openUpgradeDialog(group)
                );

                upgradeBtn.setButtonTexture(
                        scaledTextTexture(
                                Component.translatable("gui.gtceuterminal.component_detail.entry.upgrade").getString(),
                                btnW,
                                textScale
                        )
                );
                upgradeBtn.setHoverTexture(new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_LIGHT),
                        new ColorBorderTexture(1, COLOR_TEXT_WHITE)
                ));
                entry.addWidget(upgradeBtn);
            } else {
                addText(entry, btnX, compact ? 8 : 15, btnW,
                        Component.translatable("gui.gtceuterminal.component_detail.entry.max").getString(),
                        textScale);
            }
        }

        return entry;
    }

    private static String tierNameForList(ComponentGroup group, ComponentInfo rep) {
        try {
            if (group != null && group.getType() == ComponentType.COIL) {
                return ComponentType.getCoilTierName(rep.getTier());
            }
        } catch (Throwable ignored) {}
        String s = rep != null ? rep.getTierName() : "";
        return s != null ? s : "";
    }

    private WidgetGroup createActionButtons() {
        WidgetGroup buttonPanel = new WidgetGroup(pad, buttonsY, uiW - pad * 2, bottomH);

        int btnH = compact ? 20 : 24;
        int btnY = compact ? 3 : 5;

        int bulkW = compact ? 110 : 120;
        int scanW = compact ? 110 : 120;
        int gapX = 10;

        ButtonWidget bulkBtn = new ButtonWidget(
                0, btnY, bulkW, btnH,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> openBulkUpgrade()
        );
        bulkBtn.setButtonTexture(
                scaledTextTexture(
                        Component.translatable("gui.gtceuterminal.component_detail.actions.bulk_upgrade").getString(),
                        bulkW,
                        textScale
                )
        );
        bulkBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));
        buttonPanel.addWidget(bulkBtn);

        ButtonWidget scanBtn = new ButtonWidget(
                bulkW + gapX, btnY, scanW, btnH,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> scanComponents()
        );
        scanBtn.setButtonTexture(
                scaledTextTexture(
                        Component.translatable("gui.gtceuterminal.component_detail.actions.scan").getString(),
                        scanW,
                        textScale
                )
        );
        scanBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));
        buttonPanel.addWidget(scanBtn);

        return buttonPanel;
    }

    private ButtonWidget createBackButton() {
        int btnW = 60;
        int btnH = compact ? 20 : 22;

        ButtonWidget backBtn = new ButtonWidget(
                pad, compact ? 3 : 5, btnW, btnH,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> goBack()
        );

        backBtn.setButtonTexture(
                scaledTextTexture(
                        Component.translatable("gui.gtceuterminal.component_detail.actions.back").getString(),
                        btnW,
                        textScale
                )
        );
        backBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        return backBtn;
    }

    private void openUpgradeDialog(ComponentGroup group) {
        GTCEUTerminalMod.LOGGER.info("Opening upgrade dialog for group: {}", group.getType());
        if (gui != null && gui.mainGroup != null) {
            new ComponentUpgradeDialog(
                    gui.mainGroup,
                    this,
                    null,
                    group,
                    multiblock,
                    player,
                    theme  // pass resolved theme directly — avoids inventory search issues
            );
        }
    }

    private void openBulkUpgrade() {
        GTCEUTerminalMod.LOGGER.info("Opening bulk upgrade for multiblock: {}", multiblock.getName());
        player.displayClientMessage(
                Component.translatable("gui.gtceuterminal.component_detail.notifications.bulk_not_implemented"),
                true
        );
    }

    private void scanComponents() {
        GTCEUTerminalMod.LOGGER.info("Scanning components for multiblock: {}", multiblock.getName());
        player.displayClientMessage(
                Component.translatable("gui.gtceuterminal.component_detail.notifications.rescanned"),
                true
        );
    }

    private void goBack() {
        player.displayClientMessage(
                Component.translatable("gui.gtceuterminal.component_detail.notifications.use_esc_to_close"),
                true
        );
    }

    // --- text helpers (safe scaling) ---
    private void addText(WidgetGroup parent, int x, int y, int w, String text, float scale) {
        TextTexture tt = scaledTextTexture(text, w, scale);
        parent.addWidget(new ImageWidget(x, y, w, 12, tt));
    }

    private TextTexture scaledTextTexture(String text, int width, float scale) {
        TextTexture tt = new TextTexture(text)
                .setWidth(width)
                .setType(TextTexture.TextType.NORMAL);

        try {
            Method m = tt.getClass().getMethod("setScale", float.class);
            m.invoke(tt, scale);
        } catch (Throwable ignored) {
        }
        return tt;
    }

    public static ModularUI create(MultiStructureManagerUIFactory.Holder holder,
                                   Player player,
                                   MultiblockInfo multiblock) {
        ComponentDetailUI ui = new ComponentDetailUI(holder, holder.getTerminalItem(), player, multiblock);
        return ui.createUI();
    }
}