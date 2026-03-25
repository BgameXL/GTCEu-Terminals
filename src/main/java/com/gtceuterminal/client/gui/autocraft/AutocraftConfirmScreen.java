package com.gtceuterminal.client.gui.autocraft;

import com.gtceuterminal.common.autocraft.AnalysisResult;
import com.gtceuterminal.common.autocraft.AnalysisResult.Entry;
import com.gtceuterminal.common.network.CPacketConfirmAutobuild;
import com.gtceuterminal.common.network.TerminalNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Confirmation screen shown after the server sends {@link com.gtceuterminal.common.network.SPacketAnalysisResult}.
 */
public class AutocraftConfirmScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int W         = 320;
    private static final int ENTRY_H   = 14;
    private static final int LIST_PAD  = 8;
    private static final int HDR_H     = 28;
    private static final int FOOT_H    = 30;
    private static final int MAX_VISIBLE = 12;  // scroll after this many entries

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG      = 0xFF1A1A1A;
    private static final int C_PANEL   = 0xFF222222;
    private static final int C_BORDER  = 0xFF2E75B6;
    private static final int C_TEXT    = 0xFFDDDDDD;
    private static final int C_DIM     = 0xFF888888;
    private static final int C_OK      = 0xFF1E6B1E;
    private static final int C_OK_B    = 0xFF2E8B2E;
    private static final int C_CANCEL  = 0xFF6B1E1E;
    private static final int C_CANCEL_B= 0xFF8B2E2E;
    private static final int C_GREEN   = 0xFF00CC44;
    private static final int C_YELLOW  = 0xFFFFCC00;
    private static final int C_RED     = 0xFFFF4444;

    // ── State ─────────────────────────────────────────────────────────────────
    private final AnalysisResult result;
    private final Screen         parent;
    private final List<Entry>    entries;

    private int scrollY   = 0;
    private int wx, wy, H;

    // Pre-computed button bounds
    private int confirmX, cancelX, btnY, btnW, btnH;

    public AutocraftConfirmScreen(AnalysisResult result, Screen parent) {
        super(Component.translatable("gui.gtceuterminal.autocraft.title"));
        this.result  = result;
        this.parent  = parent;
        this.entries = result.entries;
    }

    @Override
    protected void init() {
        super.init();
        int visibleEntries = Math.min(entries.size(), MAX_VISIBLE);
        H  = HDR_H + LIST_PAD + visibleEntries * ENTRY_H + LIST_PAD + FOOT_H;
        wx = (width  - W) / 2;
        wy = (height - H) / 2;

        btnH      = 16;
        btnW      = 100;
        btnY      = wy + H - FOOT_H + 7;
        confirmX  = wx + W - LIST_PAD - btnW;
        cancelX   = confirmX - 6 - btnW;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Dim backdrop
        g.fill(0, 0, width, height, 0x88000000);

        // Panel
        g.fill(wx, wy, wx + W, wy + H, C_BG);
        border(g, wx, wy, W, H, C_BORDER);

        // Header
        g.fill(wx, wy, wx + W, wy + HDR_H, C_PANEL);
        String title = result.kind == AnalysisResult.Kind.BUILD
                ? Component.translatable("gui.gtceuterminal.autocraft.header.build").getString()
                : Component.translatable("gui.gtceuterminal.autocraft.header.upgrade").getString();
        g.drawString(font, title, wx + LIST_PAD, wy + 9, C_TEXT, false);
        g.fill(wx, wy + HDR_H - 1, wx + W, wy + HDR_H, C_BORDER);

        // Entry list (scrollable)
        int listTop  = wy + HDR_H + LIST_PAD;
        int listH    = H - HDR_H - LIST_PAD * 2 - FOOT_H;
        int maxScroll = Math.max(0, entries.size() * ENTRY_H - listH);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        // Scissor — RenderSystem is in com.mojang.blaze3d, not net.minecraft.client.renderer
        double scale  = Minecraft.getInstance().getWindow().getGuiScale();
        int    screenH= Minecraft.getInstance().getWindow().getHeight();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                (int)(wx * scale), (int)(screenH - (listTop + listH) * scale),
                (int)(W  * scale), (int)(listH * scale));

        for (int i = 0; i < entries.size(); i++) {
            Entry e  = entries.get(i);
            int   ey = listTop + i * ENTRY_H - scrollY;
            if (ey + ENTRY_H < listTop || ey > listTop + listH) continue;
            renderEntry(g, e, wx + LIST_PAD, ey, W - LIST_PAD * 2);
        }

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        // Footer separator
        int footY = wy + H - FOOT_H;
        g.fill(wx, footY, wx + W, footY + 1, C_BORDER);

        // Summary text
        int missing = result.missingCount();
        String summary = missing == 0
                ? Component.translatable("gui.gtceuterminal.autocraft.summary.all_available").getString()
                : Component.translatable(
                        "gui.gtceuterminal.autocraft.summary.missing",
                        missing
                ).getString();
        g.drawString(font, summary, wx + LIST_PAD, footY + 9, C_TEXT, false);

        // Buttons
        renderBtn(g, confirmX, btnY, btnW, btnH,
                result.allAvailable() ? C_OK : 0xFF2A2A2A,
                result.allAvailable() ? C_OK_B : 0xFF444444,
                mx, my);
        g.drawString(font,
                result.allAvailable()
                        ? (result.kind == AnalysisResult.Kind.BUILD
                                ? Component.translatable("gui.gtceuterminal.autocraft.confirm.build").getString()
                                : Component.translatable("gui.gtceuterminal.autocraft.confirm.upgrade").getString())
                        : Component.translatable("gui.gtceuterminal.autocraft.confirm.unavailable").getString(),
                confirmX + 6, btnY + 4, 0xFFFFFFFF, false);

        renderBtn(g, cancelX, btnY, btnW, btnH, C_CANCEL, C_CANCEL_B, mx, my);
        g.drawString(font,
                Component.translatable("gui.gtceuterminal.autocraft.cancel").getString(),
                cancelX + 6, btnY + 4, 0xFFFFFFFF, false);

        super.render(g, mx, my, pt);
    }

    private void renderEntry(GuiGraphics g, Entry e, int x, int y, int w) {
        long inME    = e.inME;
        int  needed  = e.needed();
        boolean ok   = e.hasAll();
        boolean craft= e.craftable && !ok;

        // Icon color + symbol
        String icon;
        int    iconColor;
        if (ok) {
            icon = "✓"; iconColor = C_GREEN;
        } else if (craft) {
            icon = "⚙"; iconColor = C_YELLOW;
        } else {
            icon = "✗"; iconColor = C_RED;
        }

        g.drawString(font, icon, x, y + 2, iconColor, false);

        // Item name (truncated)
        String name = e.stack.getHoverName().getString();
        if (name.length() > 22) name = name.substring(0, 19) + "…";
        g.drawString(font, "§f" + name, x + 10, y + 2, C_TEXT, false);

        // Right side: need × / ME count
        String inMeColored = (inME > 0 ? (ok ? "§a" : "§e") : "§c") + inME;
        String craftSuffix = craft ? " §e✦" : "";
        String right = Component.translatable(
                "gui.gtceuterminal.autocraft.entry.right",
                needed,
                inMeColored,
                craftSuffix
        ).getString();
        int rw = font.width(right.replaceAll("§.", ""));
        g.drawString(font, right, x + w - rw, y + 2, C_TEXT, false);

        // Divider
        g.fill(x, y + ENTRY_H - 1, x + w, y + ENTRY_H, 0xFF2A2A2A);
    }

    private void renderBtn(GuiGraphics g, int x, int y, int w, int h,
                           int fill, int bdr, int mx, int my) {
        boolean hov = mx >= x && mx < x+w && my >= y && my < y+h;
        g.fill(x, y, x+w, y+h, hov ? brighten(fill) : fill);
        border(g, x, y, w, h, bdr);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x,     y,     x+w, y+1,   c);
        g.fill(x,     y+h-1, x+w, y+h,   c);
        g.fill(x,     y,     x+1, y+h,   c);
        g.fill(x+w-1, y,     x+w, y+h,   c);
    }

    private int brighten(int argb) {
        int a = (argb>>24)&0xFF, r = Math.min(255,((argb>>16)&0xFF)+20),
                gv= Math.min(255,((argb>>8)&0xFF)+20), b=Math.min(255,(argb&0xFF)+20);
        return (a<<24)|(r<<16)|(gv<<8)|b;
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        // Confirm
        if (result.allAvailable()
                && mx >= confirmX && mx < confirmX + btnW
                && my >= btnY    && my < btnY + btnH) {
            doConfirm();
            return true;
        }
        // Cancel
        if (mx >= cancelX && mx < cancelX + btnW
                && my >= btnY && my < btnY + btnH) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int listH    = H - HDR_H - LIST_PAD * 2 - FOOT_H;
        int maxScroll= Math.max(0, entries.size() * ENTRY_H - listH);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int)(delta * ENTRY_H)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER
                && key == GLFW.GLFW_KEY_ESCAPE) {
            onClose(); return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (result.allAvailable()) { doConfirm(); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private void doConfirm() {
        TerminalNetwork.sendToServer(new CPacketConfirmAutobuild(result));
        Minecraft.getInstance().setScreen(null); // close all GUIs, back to game
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}