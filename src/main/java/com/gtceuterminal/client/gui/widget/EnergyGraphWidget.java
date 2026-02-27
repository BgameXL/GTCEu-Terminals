package com.gtceuterminal.client.gui.widget;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

// Widget that draws a graph of energy input/output over time, with grid and border.
public class EnergyGraphWidget extends Widget {

    // Colors
    private static final int COLOR_INPUT  = 0xFF00CC55;
    private static final int COLOR_OUTPUT = 0xFFDD3333;
    private static final int COLOR_GRID   = 0x22FFFFFF;
    private static final int COLOR_GRID_MAJOR = 0x44FFFFFF;
    private static final int COLOR_BG     = 0xFF1A1A1A;

    private long[] inputHistory;
    private long[] outputHistory;

    public EnergyGraphWidget(int x, int y, int width, int height,
                              long[] inputHistory, long[] outputHistory) {
        super(x, y, width, height);
        this.inputHistory  = inputHistory  != null ? inputHistory  : new long[0];
        this.outputHistory = outputHistory != null ? outputHistory : new long[0];
    }

    public void updateData(long[] inputHistory, long[] outputHistory) {
        this.inputHistory  = inputHistory  != null ? inputHistory  : new long[0];
        this.outputHistory = outputHistory != null ? outputHistory : new long[0];
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int w = getSize().width;
        int h = getSize().height;

        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(getPosition().x, getPosition().y, 0);
        Matrix4f matrix = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Background
        fillRect(matrix, 0, 0, w, h, COLOR_BG);

        // Grid
        drawGrid(matrix, w, h);

        // Data
        int len = Math.max(inputHistory.length, outputHistory.length);
        if (len >= 2) {
            long max = 1;
            for (long v : inputHistory)  max = Math.max(max, v);
            for (long v : outputHistory) max = Math.max(max, v);

            // Filled areas (translucent)
            drawFilledArea(matrix, inputHistory,  len, w, h, max, COLOR_INPUT,  0.12f);
            drawFilledArea(matrix, outputHistory, len, w, h, max, COLOR_OUTPUT, 0.12f);

            // Lines on top (opaque)
            drawLine(matrix, inputHistory,  len, w, h, max, COLOR_INPUT);
            drawLine(matrix, outputHistory, len, w, h, max, COLOR_OUTPUT);
        }

        // Border
        drawBorder(matrix, w, h);

        RenderSystem.disableBlend();
        ps.popPose();
    }

    // Grid
    private void drawGrid(Matrix4f matrix, int w, int h) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // 4 horizontal lines
        for (int i = 0; i <= 4; i++) {
            float y = (h * i) / 4f;
            int c = (i == 0 || i == 4) ? COLOR_GRID_MAJOR : COLOR_GRID;
            putLine(buf, matrix, 0, y, w, y, c);
        }

        // 8 vertical lines
        for (int i = 0; i <= 8; i++) {
            float x = (w * i) / 8f;
            int c = (i == 0 || i == 8) ? COLOR_GRID_MAJOR : COLOR_GRID;
            putLine(buf, matrix, x, 0, x, h, c);
        }

        tess.end();
    }

    // Filled area
    private void drawFilledArea(Matrix4f matrix, long[] data, int len,
                                 int w, int h, long max, int color, float alpha) {
        if (data.length < 2) return;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < data.length - 1 && i < len - 1; i++) {
            float x1 = (i       * (float) w) / (len - 1);
            float x2 = ((i + 1) * (float) w) / (len - 1);
            float y1 = h - 1 - ((data[i]     * (h - 2f)) / max);
            float y2 = h - 1 - ((data[i + 1] * (h - 2f)) / max);

            buf.vertex(matrix, x1, h - 1, 0).color(r, g, b, alpha).endVertex();
            buf.vertex(matrix, x2, h - 1, 0).color(r, g, b, alpha).endVertex();
            buf.vertex(matrix, x2, y2,    0).color(r, g, b, alpha).endVertex();
            buf.vertex(matrix, x1, y1,    0).color(r, g, b, alpha).endVertex();
        }

        tess.end();
    }

    // Line
    private void drawLine(Matrix4f matrix, long[] data, int len,
                           int w, int h, long max, int color) {
        if (data.length < 2) return;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        for (int i = 0; i < data.length - 1 && i < len - 1; i++) {
            float x1 = (i       * (float) w) / (len - 1);
            float x2 = ((i + 1) * (float) w) / (len - 1);
            float y1 = h - 1 - ((data[i]     * (h - 2f)) / max);
            float y2 = h - 1 - ((data[i + 1] * (h - 2f)) / max);

            buf.vertex(matrix, x1, y1, 0).color(r, g, b, 1.0f).endVertex();
            buf.vertex(matrix, x2, y2, 0).color(r, g, b, 1.0f).endVertex();
        }

        tess.end();
    }

    // Border
    private void drawBorder(Matrix4f matrix, int w, int h) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int c = 0xFF444444;
        putLine(buf, matrix, 0,     0,     w, 0,     c);
        putLine(buf, matrix, 0,     h - 1, w, h - 1, c);
        putLine(buf, matrix, 0,     0,     0, h,     c);
        putLine(buf, matrix, w - 1, 0,     w - 1, h, c);
        tess.end();
    }

    // Helpers
    private static void putLine(BufferBuilder buf, Matrix4f matrix,
                                  float x1, float y1, float x2, float y2, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        buf.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
    }

    private static void fillRect(Matrix4f matrix, int x, int y, int w, int h, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, x,     y,     0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x,     y + h, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x + w, y + h, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x + w, y,     0).color(r, g, b, a).endVertex();
        tess.end();
    }
}