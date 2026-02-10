package com.gtceuterminal.client.gui.widget;

import com.gtceuterminal.common.data.SchematicData;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchematicPreviewWidget extends WidgetGroup {

    private SchematicData schematic;
    private float rotationX = 30.0F;
    private float rotationY = 45.0F;
    private float zoom = 1.0F;
    private int rotSteps = 0;

    private boolean isDragging = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    private static final float MIN_ZOOM = 0.3F;
    private static final float MAX_ZOOM = 3.0F;
    private static final float ZOOM_STEP = 0.1F;

    // Cache
    private BlockPos cachedMinPos = BlockPos.ZERO;
    private BlockPos cachedSize = BlockPos.ZERO;
    private final List<BlockEntry> renderCache = new ArrayList<>();
    private boolean needsRebuild = true;
    private PreviewLevel previewLevel;

    private static class BlockEntry {
        BlockPos pos;
        BlockState state;

        BlockEntry(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    private static class PreviewLevel implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> blocks;

        PreviewLevel(Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> blockEntities) {
            this.blocks = blocks;
            // Ya no necesitamos blockEntities para renderizado de ítems
        }

        @Override
        public @NotNull BlockState getBlockState(@NotNull BlockPos pos) {
            BlockState state = blocks.get(pos);
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public @NotNull FluidState getFluidState(@NotNull BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(@NotNull BlockPos pos) {
            // Ya no necesitamos BlockEntity para renderizado de ítems
            return null;
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }

        @Override
        public float getShade(@NotNull Direction direction, boolean shade) {
            return 1.0F;
        }

        @Override
        public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver colorResolver) {
            return 0xFFFFFFFF;
        }

        @Override
        public @NotNull LevelLightEngine getLightEngine() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) return mc.level.getLightEngine();
            throw new IllegalStateException("No client level available");
        }
    }

    public SchematicPreviewWidget(int x, int y, int width, int height, SchematicData schematic) {
        super(x, y, width, height);
        this.schematic = schematic;
        updateCache();
    }

    public void setSchematic(SchematicData schematic) {
        this.schematic = schematic;
        this.needsRebuild = true;
        updateCache();
    }

    private void updateCache() {
        if (schematic == null || schematic.getBlocks().isEmpty()) {
            renderCache.clear();
            cachedMinPos = BlockPos.ZERO;
            cachedSize = BlockPos.ZERO;
            needsRebuild = false;
            return;
        }

        // ===Logic Rotation===
        Map<BlockPos, BlockState> rotatedBlocks = new HashMap<>();
        Map<BlockPos, CompoundTag> rotatedBEs = new HashMap<>();

        for (Map.Entry<BlockPos, BlockState> entry : schematic.getBlocks().entrySet()) {
            BlockPos rp = rotatePositionSteps(entry.getKey(), rotSteps);
            BlockState rs = rotateBlockStateSteps(entry.getValue(), rotSteps);
            rotatedBlocks.put(rp, rs);
        }

        for (Map.Entry<BlockPos, CompoundTag> entry : schematic.getBlockEntities().entrySet()) {
            BlockPos rp = rotatePositionSteps(entry.getKey(), rotSteps);
            rotatedBEs.put(rp, entry.getValue().copy());
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : rotatedBlocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        cachedMinPos = new BlockPos(minX, minY, minZ);
        cachedSize = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        if (needsRebuild) {
            renderCache.clear();

            List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(rotatedBlocks.entrySet());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(a.getKey().getY(), b.getKey().getY());
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a.getKey().getZ(), b.getKey().getZ());
                if (cmp != 0) return cmp;
                return Integer.compare(a.getKey().getX(), b.getKey().getX());
            });

            for (Map.Entry<BlockPos, BlockState> entry : sorted) {
                if (!entry.getValue().isAir() && !entry.getValue().is(Blocks.AIR)) {
                    renderCache.add(new BlockEntry(entry.getKey(), entry.getValue()));
                }
            }

            // Rebuild preview level for CTM + ModelData queries
            previewLevel = new PreviewLevel(
                    rotatedBlocks,
                    rotatedBEs
            );

            needsRebuild = false;
        }
    }

    // Logic rotation helpers (GhostBlockRenderer)
    private BlockPos rotatePositionSteps(BlockPos pos, int steps) {
        BlockPos result = pos;
        for (int i = 0; i < steps; i++) {
            // (x, z) -> (-z, x)
            result = new BlockPos(-result.getZ(), result.getY(), result.getX());
        }
        return result;
    }

    private BlockState rotateBlockStateSteps(BlockState state, int steps) {
        BlockState result = state;
        for (int i = 0; i < steps; i++) {
            result = rotateBlockStateOnce(result);
        }
        return result;
    }

    private BlockState rotateBlockStateOnce(BlockState state) {
        try {
            // HORIZONTAL_FACING
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                if (facing.getAxis().isHorizontal()) {
                    state = state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                            facing.getClockWise()
                    );
                }
            }

            // FACING
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    state = state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                            facing.getClockWise()
                    );
                }
            }

            // AXIS: X <-> Z
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
                var axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
                if (axis == net.minecraft.core.Direction.Axis.X) {
                    state = state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                            net.minecraft.core.Direction.Axis.Z
                    );
                } else if (axis == net.minecraft.core.Direction.Axis.Z) {
                    state = state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                            net.minecraft.core.Direction.Axis.X
                    );
                }
            }
        } catch (Exception ignored) {}

        return state;
    }

    public int getRotSteps() {
        return rotSteps;
    }

    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        try {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        } finally {
            RenderSystem.enableCull();
        }

        int x = getPosition().x;
        int y = getPosition().y;
        int w = getSize().width;
        int h = getSize().height;

        graphics.fill(x, y, x + w, y + h, 0xDD000000);
        graphics.fill(x, y, x + w, y + 1, 0xFF555555);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF555555);
        graphics.fill(x, y, x + 1, y + h, 0xFF555555);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF555555);
    }

    @Override
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (needsRebuild) {
            updateCache();
        }

        if (schematic != null && !renderCache.isEmpty()) {
            renderPreview3D(graphics, partialTicks);
        } else {
            drawEmptyMessage(graphics);
        }

        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderPreview3D(GuiGraphics graphics, float partialTicks) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        int x = getPosition().x;
        int y = getPosition().y;
        int w = getSize().width;
        int h = getSize().height;

        try {
            setupScissorWithPadding(x, y, w, h);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            Lighting.setupForFlatItems();

            float centerX = x + w / 2.0F;
            float centerY = y + h / 2.0F;
            poseStack.translate(centerX, centerY, 400.0F);

            float maxDim = Math.max(cachedSize.getX(), Math.max(cachedSize.getY(), cachedSize.getZ()));
            float baseScale = (Math.min(w, h) * 0.45F) / maxDim;
            float finalScale = baseScale * this.zoom;

            poseStack.scale(finalScale, finalScale, finalScale);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

            poseStack.mulPose(Axis.XP.rotationDegrees(this.rotationX));
            poseStack.mulPose(Axis.YP.rotationDegrees(this.rotationY));

            poseStack.translate(
                    -(cachedMinPos.getX() + cachedSize.getX() / 2.0F),
                    -(cachedMinPos.getY() + cachedSize.getY() / 2.0F),
                    -(cachedMinPos.getZ() + cachedSize.getZ() / 2.0F)
            );

            renderBlocks(poseStack);

            Lighting.setupForFlatItems();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();

        } catch (Exception e) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.error("Error rendering schematic preview", e);
        } finally {
            RenderSystem.disableScissor();
            poseStack.popPose();
        }
    }

    private void renderBlocks(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        for (BlockEntry entry : renderCache) {
            poseStack.pushPose();
            poseStack.translate(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());

            try {
                blockRenderer.renderSingleBlock(
                        entry.state,
                        poseStack,
                        bufferSource,
                        15728880,
                        OverlayTexture.NO_OVERLAY
                );
            } catch (Exception e) {
            }

            poseStack.popPose();
        }

        bufferSource.endBatch();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawEmptyMessage(GuiGraphics graphics) {
        String msg = "No Schematic";
        int textWidth = Minecraft.getInstance().font.width(msg);
        graphics.drawString(
                Minecraft.getInstance().font,
                msg,
                getPosition().x + (getSize().width - textWidth) / 2,
                getPosition().y + getSize().height / 2 - 4,
                0xFF888888,
                false
        );
    }

    private void setupScissorWithPadding(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int screenHeight = mc.getWindow().getHeight();

        int scissorX = Math.max(0, (int)((x - 5) * scale));
        int scissorY = Math.max(0, (int)(screenHeight - (y + height + 5) * scale));
        int scissorW = (int)((width + 5 * 2) * scale);
        int scissorH = (int)((height + 5 * 2) * scale);

        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverElement(mouseX, mouseY)) {
            if (button == 0) {
                isDragging = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            } else if (button == 1) {
                resetView();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            double deltaX = mouseX - lastMouseX;
            double deltaY = mouseY - lastMouseY;

            rotationY -= (float) deltaX * 0.5F;  // Cambio aquí
            rotationX += (float) deltaY * 0.5F;

            rotationX = Math.max(-80.0F, Math.min(80.0F, rotationX));

            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isMouseOverElement(mouseX, mouseY)) {
            return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        }

        // Ctrl + wheel = zoom
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            zoom += (float) wheelDelta * ZOOM_STEP;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
            return true;
        }

        // Wheel = rotación lógica
        int dir = wheelDelta > 0 ? 1 : -1;
        rotSteps = (rotSteps + dir) & 3;

        needsRebuild = true; // IMPORTANTÍSIMO: esto fuerza rotación real del cache
        return true;
    }

    public void resetView() {
        rotationX = 30.0F;
        rotationY = 45.0F;
        zoom = 1.0F;
        rotSteps = 0;
        needsRebuild = true;
    }

    public float getRotationX() {
        return rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public float getZoom() {
        return zoom;
    }

    public int getBlockCount() {
        return renderCache.size();
    }

    public String getMultiblockDisplayName() {
        if (schematic == null || schematic.getBlocks().isEmpty()) {
            return "Multiblock Structure";
        }

        for (Map.Entry<BlockPos, BlockState> entry : schematic.getBlocks().entrySet()) {
            BlockState state = entry.getValue();
            String blockId = state.getBlock().getDescriptionId().toLowerCase();

            if (blockId.contains("controller") ||
                    blockId.contains("machine") ||
                    blockId.contains("multiblock") ||
                    blockId.contains("casing")) {

                String displayName = state.getBlock().getName().getString();

                if (displayName.length() > 35) {
                    displayName = displayName.substring(0, 32) + "...";
                }

                return displayName;
            }
        }

        for (Map.Entry<BlockPos, BlockState> entry : schematic.getBlocks().entrySet()) {
            BlockState state = entry.getValue();
            if (!state.isAir()) {
                String displayName = state.getBlock().getName().getString();

                if (displayName.length() > 35) {
                    displayName = displayName.substring(0, 32) + "...";
                }

                return displayName;
            }
        }

        return "Multiblock Structure";
    }
}