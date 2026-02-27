package com.gtceuterminal.client.highlight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;

import java.util.Set;

// Renders colored highlights on blocks that are part of an active multiblock structure.
@Mod.EventBusSubscriber(modid = "gtceuterminal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HighlightRenderer {

    private static final float INSET = 0.002f; // slight inset to avoid z-fighting

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var highlights = MultiblockHighlighter.getActiveHighlights();
        if (highlights.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();

        float pulse = (System.currentTimeMillis() % 2000) / 2000f;
        float alphaMod = 0.25f + Math.abs((pulse * 2) - 1) * 0.20f; // gentle pulse

        for (var hl : highlights.values()) {
            Set<BlockPos> blockSet = hl.blocks;
            if (blockSet.isEmpty()) continue;

            int col = hl.color;
            float r = ((col >> 16) & 0xFF) / 255f;
            float g = ((col >>  8) & 0xFF) / 255f;
            float b = ( col        & 0xFF) / 255f;
            float a = alphaMod;

            for (BlockPos pos : blockSet) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    // Only render face if neighbor is NOT part of this multiblock
                    if (!blockSet.contains(neighbor)) {
                        drawFace(buf, mat, pos, dir, r, g, b, a);
                    }
                }
            }
        }

        ps.popPose();
        tess.end();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // Draws a single face of a block with the given color and alpha.
    private static void drawFace(BufferBuilder buf, Matrix4f mat,
                                 BlockPos pos, Direction dir,
                                 float r, float g, float b, float a) {
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();

        float i = INSET;

        switch (dir) {
            case UP -> {
                float fy = y + 1 - i;
                quad(buf, mat,
                        x+i,   fy, z+i,
                        x+i,   fy, z+1-i,
                        x+1-i, fy, z+1-i,
                        x+1-i, fy, z+i,
                        r, g, b, a);
            }
            case DOWN -> {
                float fy = y + i;
                quad(buf, mat,
                        x+i,   fy, z+i,
                        x+1-i, fy, z+i,
                        x+1-i, fy, z+1-i,
                        x+i,   fy, z+1-i,
                        r, g, b, a);
            }
            case NORTH -> { // -Z face
                float fz = z + i;
                quad(buf, mat,
                        x+i,   y+i,   fz,
                        x+1-i, y+i,   fz,
                        x+1-i, y+1-i, fz,
                        x+i,   y+1-i, fz,
                        r, g, b, a);
            }
            case SOUTH -> { // +Z face
                float fz = z + 1 - i;
                quad(buf, mat,
                        x+i,   y+i,   fz,
                        x+i,   y+1-i, fz,
                        x+1-i, y+1-i, fz,
                        x+1-i, y+i,   fz,
                        r, g, b, a);
            }
            case WEST -> { // -X face
                float fx = x + i;
                quad(buf, mat,
                        fx, y+i,   z+i,
                        fx, y+1-i, z+i,
                        fx, y+1-i, z+1-i,
                        fx, y+i,   z+1-i,
                        r, g, b, a);
            }
            case EAST -> { // +X face
                float fx = x + 1 - i;
                quad(buf, mat,
                        fx, y+i,   z+i,
                        fx, y+i,   z+1-i,
                        fx, y+1-i, z+1-i,
                        fx, y+1-i, z+i,
                        r, g, b, a);
            }
        }
    }

    // Helper
    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        buf.vertex(mat, x3, y3, z3).color(r, g, b, a).endVertex();
        buf.vertex(mat, x4, y4, z4).color(r, g, b, a).endVertex();
    }
}