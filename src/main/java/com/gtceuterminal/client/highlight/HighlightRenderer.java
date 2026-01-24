package com.gtceuterminal.client.highlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;


  // Renders multiblock highlights in the world (needs more polish)

@Mod.EventBusSubscriber(modid = "gtceuterminal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HighlightRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        var highlights = MultiblockHighlighter.getActiveHighlights();
        if (highlights.isEmpty()) {
            return;
        }

        // Debug
        if (mc.level.getGameTime() % 20 == 0) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Rendering {} highlights", highlights.size());
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        for (var highlight : highlights.values()) {
            renderHighlight(poseStack, bufferSource, highlight, cameraPos);
        }
    }

    private static void renderHighlight(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            MultiblockHighlighter.HighlightInfo highlight,
            Vec3 cameraPos
    ) {
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        float r = 0.0f;   // Red
        float g = 0.5f;   // Green
        float b = 1.0f;   // Blue

        float pulse = (System.currentTimeMillis() % 2000) / 2000f;
        float alpha = 0.3f + (Math.abs((pulse * 2) - 1) * 0.3f); // Pulse between 0.3 and 0.6

        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        // Render bounding box
        AABB box = highlight.boundingBox.inflate(0.02);
        renderBox(poseStack, bufferSource, box, r, g, b, alpha);

        // Render individual block outlines
        for (BlockPos pos : highlight.blocks) {
            AABB blockBox = new AABB(pos).inflate(0.01);
            renderBox(poseStack, bufferSource, blockBox, r, g, b, alpha * 0.6f);
        }

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();

        poseStack.popPose();
    }

    private static void renderBox(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            AABB box,
            float r, float g, float b, float a
    ) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom square
        addLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        addLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top square
        addLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical lines
        addLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        addLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void addLine(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float r, float g, float b, float a
    ) {
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(1, 0, 0).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(1, 0, 0).endVertex();
    }
}