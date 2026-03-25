package com.gtceuterminal.client.gui.theme;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.widget.RGBSliderWidget;
import com.gtceuterminal.common.network.CPacketSaveTheme;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.gtceuterminal.common.theme.ItemTheme;
import com.gtceuterminal.common.theme.ThemePreset;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ThemeEditorDialog {

    private static final int DW     = 340;
    private static final int DH     = 270;
    private static final int HDR_H  = 24;
    private static final int FTR_H  = 30;
    private static final int PAD    = 8;
    private static final int COL_L  = 148;
    private static final int COL_RX = COL_L + PAD * 2;
    private static final int COL_R  = DW - COL_RX - PAD;

    private static final int C_BG     = 0xFF1C1C1C;
    private static final int C_PANEL  = 0xFF272727;
    private static final int C_BORDER = 0xFF3A3A3A;
    private static final int C_HOVER  = 0x33FFFFFF;
    private static final int C_SAVE   = 0xFF1E6B1E;
    private static final int C_RESET  = 0xFF6B1E1E;
    private static final int C_SEL    = 0xFF3A5A8A; // highlight for selected tab

    private final WidgetGroup rootGroup;
    private final ItemTheme   working;

    // 0 = accent, 1 = bg, 2 = panel
    private int editTarget = 0;
    private final int[] ch = new int[4];

    private RGBSliderWidget[] sliders = new RGBSliderWidget[4];
    private ImageWidget   accentSwatch, bgSwatch, panelSwatch;
    // Tab backgrounds to highlight selected
    private ImageWidget[] tabBgs = new ImageWidget[3];
    private LabelWidget   hexLabel;
    private LabelWidget   wallpaperLabel;
    private ImageWidget   wallpaperThumb;

    // Live UI preview widgets
    private ImageWidget   prevBg, prevHeader, prevPanel1, prevPanel2, prevAccentBar;

    private List<String> wallpapers;
    private int          wallpaperIdx = -1;

    public static void open(WidgetGroup rootGroup, ItemTheme current) {
        new ThemeEditorDialog(rootGroup, current).buildAndShow();
    }

    private ThemeEditorDialog(WidgetGroup rootGroup, ItemTheme current) {
        this.rootGroup = rootGroup;
        this.working   = new ItemTheme(current);
        syncChannels(working.accentColor);
        wallpapers   = WallpaperManager.listWallpapers();
        wallpaperIdx = wallpapers.indexOf(current.wallpaper);
    }

    private void buildAndShow() {
        DialogWidget dialog = new DialogWidget(rootGroup, true);
        dialog.setBackground(new ColorRectTexture(0xB0000000));
        dialog.setClickClose(false);

        // Clamp dialog size to available GUI-space so it fits at 3x/4x scale
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        int availW = mc.getWindow().getGuiScaledWidth()  - 16;
        int availH = mc.getWindow().getGuiScaledHeight() - 16;
        int dw = Math.min(DW, availW);
        int dh = Math.min(DH, availH);

        int px = Math.max(0, (rootGroup.getSize().width  - dw) / 2);
        int py = Math.max(0, (rootGroup.getSize().height - dh) / 2);

        WidgetGroup panel = new WidgetGroup(px, py, dw, dh);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(C_BG),
                new ColorBorderTexture(1, C_BORDER)));

        // If the dialog was clamped, wrap content in a scrollable viewport
        if (dw < DW || dh < DH) {
            panel.addWidget(buildHeader(dialog));
            DraggableScrollableWidgetGroup scroll =
                    new DraggableScrollableWidgetGroup(0, HDR_H, dw, dh - HDR_H - FTR_H);
            scroll.setXScrollBarHeight(6);
            scroll.setYScrollBarWidth(6);
            scroll.setXBarStyle(new ColorRectTexture(0xFF1A1A1A), new ColorRectTexture(C_BORDER));
            scroll.setYBarStyle(new ColorRectTexture(0xFF1A1A1A), new ColorRectTexture(C_BORDER));
            // Content group at original size inside the scroll area
            WidgetGroup content = new WidgetGroup(0, 0, DW, DH - HDR_H - FTR_H);
            content.addWidget(buildLeftColAt(0, 0));
            content.addWidget(buildRightColAt(COL_RX, 0));
            scroll.addWidget(content);
            panel.addWidget(scroll);
            panel.addWidget(buildFooterAt(0, dh - FTR_H, dw, dialog));
        } else {
            panel.addWidget(buildHeader(dialog));
            panel.addWidget(buildLeftCol());
            panel.addWidget(buildRightCol());
            panel.addWidget(buildFooter(dialog));
        }

        dialog.addWidget(panel);
    }

    private WidgetGroup buildHeader(DialogWidget dialog) {
        WidgetGroup g = new WidgetGroup(0, 0, DW, HDR_H);
        g.setBackground(new ColorRectTexture(C_PANEL));
        g.addWidget(new LabelWidget(PAD, (HDR_H - 8) / 2,
                Component.translatable("gui.gtceuterminal.theme_editor.title").getString()));
        ButtonWidget close = new ButtonWidget(DW - 20, (HDR_H - 14) / 2, 14, 14,
                new ColorRectTexture(0x00000000), cd -> dialog.setVisible(false));
        close.setButtonTexture(new TextTexture("§c✖").setWidth(14).setType(TextTexture.TextType.NORMAL));
        close.setHoverTexture(new ColorRectTexture(0x33FF0000));
        g.addWidget(close);
        return g;
    }

    private WidgetGroup buildLeftCol() {
        return buildLeftColAt(PAD, HDR_H + PAD);
    }

    private WidgetGroup buildLeftColAt(int x, int y) {
        int colH = DH - HDR_H - FTR_H - PAD * 2;
        WidgetGroup col = new WidgetGroup(x, y, COL_L, colH);

        col.addWidget(new LabelWidget(0, 0,
                Component.translatable("gui.gtceuterminal.theme_editor.presets").getString()));

        ThemePreset[] presets = ThemePreset.values();
        int swSz = 18, swGap = 4;
        for (int i = 0; i < presets.length; i++) {
            final ThemePreset p = presets[i];
            int sx = (i % 4) * (swSz + swGap);
            int sy = 12 + (i / 4) * (swSz + swGap);

            // Special swatch for GTCEu Native — show the GTCEu background texture
            if (p == ThemePreset.GTCEU_NATIVE) {
                col.addWidget(new ImageWidget(sx, sy, swSz, swSz,
                        new com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup(
                                com.gregtechceu.gtceu.api.gui.GuiTextures.BACKGROUND,
                                new ColorBorderTexture(1, 0xFF2E75B6))));
            } else {
                col.addWidget(new ImageWidget(sx, sy, swSz, swSz,
                        new GuiTextureGroup(
                                new ColorRectTexture(p.accentColor),
                                new ColorBorderTexture(1, 0xFF000000))));
            }

            ButtonWidget btn = new ButtonWidget(sx, sy, swSz, swSz,
                    new ColorRectTexture(0x00000000), cd -> applyPreset(p));
            btn.setHoverTexture(new ColorRectTexture(0x55FFFFFF));
            String localized = switch (p) {
                case DEFAULT -> Component.translatable("gui.gtceuterminal.theme_editor.preset.default").getString();
                case GTCEU_RED -> Component.translatable("gui.gtceuterminal.theme_editor.preset.gtceu_red").getString();
                case MATRIX -> Component.translatable("gui.gtceuterminal.theme_editor.preset.matrix").getString();
                case GOLD -> Component.translatable("gui.gtceuterminal.theme_editor.preset.gold").getString();
                case PURPLE -> Component.translatable("gui.gtceuterminal.theme_editor.preset.purple").getString();
                case CYAN -> Component.translatable("gui.gtceuterminal.theme_editor.preset.cyan").getString();
                case ORANGE -> Component.translatable("gui.gtceuterminal.theme_editor.preset.orange").getString();
                case MONO -> Component.translatable("gui.gtceuterminal.theme_editor.preset.mono").getString();
                case GTCEU_NATIVE -> Component.translatable("gui.gtceuterminal.theme_editor.preset.gtceu_native").getString();
            };
            String suffix = p.style == ItemTheme.UiStyle.GTCEU_NATIVE
                    ? Component.translatable("gui.gtceuterminal.theme_editor.preset.gtceu_native_suffix").getString()
                    : "";
            btn.setHoverTooltips(Component.literal(localized + suffix));
            col.addWidget(btn);
        }

        // Style indicator — shows which UiStyle is currently active
        int swRows = (presets.length + 3) / 4;
        int indicatorY = 12 + swRows * (swSz + swGap) + 2;
        LabelWidget styleLabel = new LabelWidget(0, indicatorY, () -> {
            String styleName = working.uiStyle == ItemTheme.UiStyle.GTCEU_NATIVE
                    ? Component.translatable("gui.gtceuterminal.theme_editor.style.gtceu_native").getString()
                    : Component.translatable("gui.gtceuterminal.theme_editor.style.dark").getString();
            return Component.translatable(
                    "gui.gtceuterminal.theme_editor.style_label",
                    styleName
            ).getString();
        });
        styleLabel.setClientSideWidget();
        col.addWidget(styleLabel);

        int sepY = indicatorY + 12;
        col.addWidget(new ImageWidget(0, sepY, COL_L, 1, new ColorRectTexture(C_BORDER)));

        int wy = sepY + 6;
        col.addWidget(new LabelWidget(0, wy,
                Component.translatable("gui.gtceuterminal.theme_editor.wallpaper").getString()));
        wy += 12;

        wallpaperLabel = new LabelWidget(0, wy,
                () -> working.hasWallpaper()
                        ? "§f" + truncate(working.wallpaper, 16)
                        : Component.translatable("gui.gtceuterminal.theme_editor.none").getString());
        wallpaperLabel.setClientSideWidget();
        col.addWidget(wallpaperLabel);
        wy += 12;

        col.addWidget(makeTextBtn(0,  wy, 20, 14, "§7◀", cd -> navigateWallpaper(-1)));
        col.addWidget(makeTextBtn(24, wy, 20, 14, "§7▶", cd -> navigateWallpaper(+1)));
        col.addWidget(makeTextBtn(48, wy, 34, 14,
                Component.translatable("gui.gtceuterminal.theme_editor.none").getString(),
                cd -> clearWallpaper()));
        wy += 18;

        // Live UI preview
        int previewH = 52;
        int previewY = wy;
        col.addWidget(new LabelWidget(0, previewY,
                Component.translatable("gui.gtceuterminal.theme_editor.preview").getString()));
        previewY += 10;

        // BG layer
        prevBg = new ImageWidget(0, previewY, COL_L, previewH, new ColorRectTexture(working.bgColor));
        col.addWidget(prevBg);

        // Header bar (accent)
        prevHeader = new ImageWidget(0, previewY, COL_L, 10, new ColorRectTexture(working.accentColor | 0xFF000000));
        col.addWidget(prevHeader);

        // Two panel blocks
        prevPanel1 = new ImageWidget(2, previewY + 13, 60, 34, new ColorRectTexture(working.panelColor));
        col.addWidget(prevPanel1);
        prevPanel2 = new ImageWidget(66, previewY + 13, COL_L - 68, 34, new ColorRectTexture(working.panelColor));
        col.addWidget(prevPanel2);

        // Accent border line
        prevAccentBar = new ImageWidget(0, previewY + previewH - 3, COL_L, 3,
                new ColorRectTexture(working.accentColor | 0xFF000000));
        col.addWidget(prevAccentBar);

        // Wallpaper thumb below preview
        wy = previewY + previewH + 4;
        int thumbH = Math.min(30, colH - wy - 4);
        if (thumbH > 10) {
            wallpaperThumb = new ImageWidget(0, wy, COL_L, thumbH, new ColorRectTexture(0xFF0A0A0A));
            col.addWidget(wallpaperThumb);
            refreshWallpaperThumb();
        }

        return col;
    }

    private WidgetGroup buildRightCol() {
        return buildRightColAt(COL_RX, HDR_H + PAD);
    }

    private WidgetGroup buildRightColAt(int x, int y) {
        int colH = DH - HDR_H - FTR_H - PAD * 2;
        WidgetGroup col = new WidgetGroup(x, y, COL_R, colH);

        col.addWidget(new LabelWidget(0, 0,
                Component.translatable("gui.gtceuterminal.theme_editor.color").getString()));

        // --- Color target tabs ---
        String[] tNames = {
                Component.translatable("gui.gtceuterminal.theme_editor.tab.accent").getString(),
                Component.translatable("gui.gtceuterminal.theme_editor.tab.bg").getString(),
                Component.translatable("gui.gtceuterminal.theme_editor.tab.panel").getString()
        };
        int tBtnW = (COL_R - 4) / 3;

        accentSwatch = new ImageWidget(0, 0, tBtnW - 4, 6, new ColorRectTexture(working.accentColor));
        bgSwatch     = new ImageWidget(0, 0, tBtnW - 4, 6, new ColorRectTexture(working.bgColor));
        panelSwatch  = new ImageWidget(0, 0, tBtnW - 4, 6, new ColorRectTexture(working.panelColor));
        ImageWidget[] swatches = { accentSwatch, bgSwatch, panelSwatch };

        for (int i = 0; i < 3; i++) {
            final int ti = i;
            int bx = i * (tBtnW + 2);

            // Tab background (highlight when selected)
            tabBgs[i] = new ImageWidget(bx, 11, tBtnW, 22,
                    new ColorRectTexture(ti == editTarget ? C_SEL : C_PANEL));
            col.addWidget(tabBgs[i]);

            // Label
            col.addWidget(new LabelWidget(bx + 2, 13, "§8" + tNames[i]));

            // Swatch
            swatches[i].setSelfPosition(new com.lowdragmc.lowdraglib.utils.Position(bx + 2, 24));
            col.addWidget(swatches[i]);

            // Button — last added so it gets click priority in LDLib
            ButtonWidget sel = new ButtonWidget(bx, 11, tBtnW, 22,
                    new ColorRectTexture(0x00000000), cd -> {
                editTarget = ti;
                syncChannels(currentColor());
                refreshSliders();
                refreshTabHighlights();
                refreshPreview();
            });
            sel.setHoverTexture(new ColorRectTexture(C_HOVER));
            col.addWidget(sel);
        }

        // Hex label
        hexLabel = new LabelWidget(0, 35, () -> "§7" + tNames[editTarget] + ": §f" + hexOf(currentColor()));
        hexLabel.setClientSideWidget();
        col.addWidget(hexLabel);

        // RGBA sliders
        int sliderY = 47;
        int barW    = COL_R - 36;
        String[] chanLabels = { "§cR", "§aG", "§9B", "§7A" };

        int[] CHAN_FILL = { 0xCCFF4444, 0xCC44FF44, 0xCC4444FF, 0xCCCCCCCC };
        for (int c = 0; c < 4; c++) {
            final int ci = c;
            int sy = sliderY + c * 22;

            // Channel label  (R / G / B / A)
            col.addWidget(new LabelWidget(0, sy + 4, chanLabels[c]));

            // Value label on the right
            LabelWidget valLbl = new LabelWidget(10 + barW + 2, sy + 4, () -> String.valueOf(ch[ci]));
            valLbl.setClientSideWidget();
            col.addWidget(valLbl);

            // Draggable slider
            RGBSliderWidget slider = new RGBSliderWidget(
                    10, sy, barW, 14,
                    0xFF0A0A0A,          // track bg
                    CHAN_FILL[ci],        // fill color
                    0xFFFFFFFF,          // thumb white
                    () -> ch[ci],
                    val -> {
                        ch[ci] = val;
                        applyChannels();
                        refreshSliders();
                        refreshSwatches();
                    }
            );
            sliders[ci] = slider;
            col.addWidget(slider);
        }

        int optY = sliderY + 4 * 22 + 6;
        col.addWidget(new ImageWidget(0, optY - 3, COL_R, 1, new ColorRectTexture(C_BORDER)));
        col.addWidget(new LabelWidget(0, optY,
                Component.translatable("gui.gtceuterminal.theme_editor.options").getString()));
        optY += 12;

        col.addWidget(makeToggle(0, optY,
                Component.translatable("gui.gtceuterminal.theme_editor.toggle.compact_mode").getString(),
                working.compactMode,  v -> working.compactMode  = v)); optY += 18;
        col.addWidget(makeToggle(0, optY,
                Component.translatable("gui.gtceuterminal.theme_editor.toggle.show_tooltips").getString(),
                working.showTooltips, v -> working.showTooltips = v)); optY += 18;
        col.addWidget(makeToggle(0, optY,
                Component.translatable("gui.gtceuterminal.theme_editor.toggle.show_borders").getString(),
                working.showBorders,  v -> working.showBorders  = v));

        return col;
    }

    private WidgetGroup buildFooter(DialogWidget dialog) {
        return buildFooterAt(0, DH - FTR_H, DW, dialog);
    }

    private WidgetGroup buildFooterAt(int x, int y, int w, DialogWidget dialog) {
        WidgetGroup ftr = new WidgetGroup(x, y, w, FTR_H);
        ftr.setBackground(new ColorRectTexture(C_PANEL));

        int btnW = 80, btnH = 18;
        int btnY = (FTR_H - btnH) / 2;

        ButtonWidget save = new ButtonWidget(DW / 2 - btnW - 4, btnY, btnW, btnH,
                new GuiTextureGroup(new ColorRectTexture(C_SAVE), new ColorBorderTexture(1, 0xFF2E8B2E)),
                cd -> saveAndClose(dialog));
        save.setButtonTexture(new TextTexture(
                Component.translatable("gui.gtceuterminal.theme_editor.button.save").getString()
        ).setWidth(btnW).setType(TextTexture.TextType.NORMAL));
        save.setHoverTexture(new ColorRectTexture(0x2200FF00));
        ftr.addWidget(save);

        ButtonWidget reset = new ButtonWidget(DW / 2 + 4, btnY, btnW, btnH,
                new GuiTextureGroup(new ColorRectTexture(C_RESET), new ColorBorderTexture(1, 0xFF8B2E2E)),
                cd -> resetDefaults());
        reset.setButtonTexture(new TextTexture(
                Component.translatable("gui.gtceuterminal.theme_editor.button.reset").getString()
        ).setWidth(btnW).setType(TextTexture.TextType.NORMAL));
        reset.setHoverTexture(new ColorRectTexture(0x22FF0000));
        ftr.addWidget(reset);

        return ftr;
    }

    // ─── Actions ──────────────────────────────────────────────────────────────
    private void applyPreset(ThemePreset p) {
        p.applyTo(working);
        syncChannels(currentColor());
        refreshSliders();
        refreshSwatches();
        refreshPreview();
    }

    private void navigateWallpaper(int delta) {
        if (wallpapers == null || wallpapers.isEmpty()) return;
        wallpaperIdx = Math.floorMod(wallpaperIdx + delta, wallpapers.size());
        working.wallpaper = wallpapers.get(wallpaperIdx);
        refreshWallpaperThumb();
    }

    private void clearWallpaper() {
        working.wallpaper = "";
        wallpaperIdx = -1;
        refreshWallpaperThumb();
    }

    private void saveAndClose(DialogWidget dialog) {
        GTCEUTerminalMod.LOGGER.info(
                "ThemeEditorDialog: saving theme accent={} bg={} panel={}",
                String.format("#%06X", working.accentColor & 0xFFFFFF),
                String.format("#%06X", working.bgColor     & 0xFFFFFF),
                String.format("#%06X", working.panelColor  & 0xFFFFFF));

        // Write directly to client-side item NBT so GUI reopens with correct colors
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            net.minecraft.world.item.ItemStack found = net.minecraft.world.item.ItemStack.EMPTY;
            for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
                net.minecraft.world.item.ItemStack s = mc.player.getItemInHand(hand);
                net.minecraft.resources.ResourceLocation rl =
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
                if (rl != null && "gtceuterminal".equals(rl.getNamespace())) { found = s; break; }
            }
            if (found.isEmpty()) {
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack s = mc.player.getInventory().getItem(i);
                    net.minecraft.resources.ResourceLocation rl =
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
                    if (rl != null && "gtceuterminal".equals(rl.getNamespace())) { found = s; break; }
                }
            }
            if (!found.isEmpty()) {
                com.gtceuterminal.common.theme.ItemTheme.save(found, working);
                GTCEUTerminalMod.LOGGER.info("ThemeEditorDialog: wrote theme to client item NBT");
            } else {
                GTCEUTerminalMod.LOGGER.warn("ThemeEditorDialog: no terminal item found in client inventory!");
            }
        }

        try {
            TerminalNetwork.CHANNEL.sendToServer(new CPacketSaveTheme(working));
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("ThemeEditorDialog: failed to send save packet", e);
        }
        dialog.setVisible(false);
        mc.setScreen(null);
    }

    private void resetDefaults() {
        ItemTheme def = new ItemTheme();
        working.accentColor  = def.accentColor;
        working.bgColor      = def.bgColor;
        working.panelColor   = def.panelColor;
        working.textColor    = def.textColor;
        working.compactMode  = def.compactMode;
        working.showTooltips = def.showTooltips;
        working.showBorders  = def.showBorders;
        working.wallpaper    = "";
        wallpaperIdx = -1;
        syncChannels(currentColor());
        refreshSliders();
        refreshSwatches();
        refreshTabHighlights();
        refreshWallpaperThumb();
    }

    // ─── Color helpers ────────────────────────────────────────────────────────
    private void syncChannels(int color) {
        ch[0] = (color >> 16) & 0xFF;
        ch[1] = (color >> 8)  & 0xFF;
        ch[2] =  color        & 0xFF;
        ch[3] = (color >> 24) & 0xFF;
    }

    private int buildColor() {
        return (ch[3] << 24) | (ch[0] << 16) | (ch[1] << 8) | ch[2];
    }

    private void applyChannels() {
        int color = buildColor();
        GTCEUTerminalMod.LOGGER.debug(
                "applyChannels: editTarget={} color=#{}", editTarget,
                String.format("%06X", color & 0xFFFFFF));
        switch (editTarget) {
            case 1  -> working.bgColor    = color;
            case 2  -> working.panelColor = color;
            default -> working.accentColor = color;
        }
    }

    private int currentColor() {
        return switch (editTarget) {
            case 1  -> working.bgColor;
            case 2  -> working.panelColor;
            default -> working.accentColor;
        };
    }

    private void refreshSliders() {
        // RGBSliderWidget is self-drawing — reads ch[] directly each frame
    }

    private void refreshSwatches() {
        if (accentSwatch != null) accentSwatch.setImage(new ColorRectTexture(working.accentColor));
        if (bgSwatch     != null) bgSwatch    .setImage(new ColorRectTexture(working.bgColor));
        if (panelSwatch  != null) panelSwatch .setImage(new ColorRectTexture(working.panelColor));
        refreshPreview();
    }

    private void refreshPreview() {
        if (prevBg        != null) prevBg       .setImage(new ColorRectTexture(working.bgColor));
        if (prevHeader    != null) prevHeader   .setImage(new ColorRectTexture(working.accentColor | 0xFF000000));
        if (prevPanel1    != null) prevPanel1   .setImage(new ColorRectTexture(working.panelColor));
        if (prevPanel2    != null) prevPanel2   .setImage(new ColorRectTexture(working.panelColor));
        if (prevAccentBar != null) prevAccentBar.setImage(new ColorRectTexture(working.accentColor | 0xFF000000));
    }

    private void refreshTabHighlights() {
        for (int i = 0; i < 3; i++) {
            if (tabBgs[i] != null) {
                tabBgs[i].setImage(new ColorRectTexture(i == editTarget ? C_SEL : C_PANEL));
            }
        }
    }

    private void refreshWallpaperThumb() {
        if (wallpaperThumb == null) return;
        if (working.hasWallpaper()) {
            ResourceLocation rl = WallpaperManager.getTexture(working.wallpaper);
            if (rl != null) {
                wallpaperThumb.setImage(
                        new com.lowdragmc.lowdraglib.gui.texture.ResourceTexture(rl.toString()));
                return;
            }
        }
        wallpaperThumb.setImage(new ColorRectTexture(0xFF0A0A0A));
    }

    private int channelFillW(int c, int barW) {
        return Math.max(1, (ch[c] * barW) / 255);
    }

    private int chanColor(int c) {
        return switch (c) {
            case 0  -> 0xFFFF4444;
            case 1  -> 0xFF44FF44;
            case 2  -> 0xFF4444FF;
            default -> 0xFFAAAAAA;
        };
    }

    private static String hexOf(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private ButtonWidget makeTextBtn(int x, int y, int w, int h, String text,
                                     java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> action) {
        ButtonWidget btn = new ButtonWidget(x, y, w, h, new ColorRectTexture(C_PANEL), action);
        btn.setButtonTexture(new TextTexture(text).setWidth(w).setType(TextTexture.TextType.NORMAL));
        btn.setHoverTexture(new ColorRectTexture(C_HOVER));
        return btn;
    }

    private WidgetGroup makeToggle(int x, int y, String label, boolean initial,
                                   java.util.function.Consumer<Boolean> onChange) {
        WidgetGroup g = new WidgetGroup(x, y, COL_R, 14);
        final boolean[] state = { initial };

        ImageWidget box = new ImageWidget(0, 1, 10, 10,
                new GuiTextureGroup(
                        new ColorRectTexture(state[0] ? 0xFF2E75B6 : 0xFF333333),
                        new ColorBorderTexture(1, C_BORDER)));
        g.addWidget(box);

        LabelWidget tick = new LabelWidget(1, 1, () -> state[0] ? "§f✔" : " ");
        tick.setClientSideWidget();
        g.addWidget(tick);

        g.addWidget(new LabelWidget(14, 2, "§7" + label));

        ButtonWidget btn = new ButtonWidget(0, 0, COL_R, 14,
                new ColorRectTexture(0x00000000), cd -> {
            state[0] = !state[0];
            box.setImage(new GuiTextureGroup(
                    new ColorRectTexture(state[0] ? 0xFF2E75B6 : 0xFF333333),
                    new ColorBorderTexture(1, C_BORDER)));
            onChange.accept(state[0]);
        });
        btn.setHoverTexture(new ColorRectTexture(C_HOVER));
        g.addWidget(btn);

        return g;
    }
}