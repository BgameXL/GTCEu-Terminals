package com.gtceuterminal.client.highlight;

import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.multiblock.MultiblockScanner;
import com.gtceuterminal.common.multiblock.MultiblockStatus;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Manages multiblock highlighting on client side (going to be removed)
public class MultiblockHighlighter {

    private static final Map<BlockPos, HighlightInfo> activeHighlights = new HashMap<>();

    public static class HighlightInfo {
        public final BlockPos controllerPos;
        public final Set<BlockPos> blocks;
        public final int color;
        public final long startTime;
        public final int duration; // milliseconds, -1 = permanent
        public final AABB boundingBox;

        public HighlightInfo(BlockPos controllerPos, Set<BlockPos> blocks, int color, int duration) {
            this.controllerPos = controllerPos;
            this.blocks = blocks;
            this.color = color;
            this.startTime = System.currentTimeMillis();
            this.duration = duration;
            this.boundingBox = calculateBoundingBox(blocks);
        }

        public boolean isExpired() {
            if (duration < 0) return false;
            return (System.currentTimeMillis() - startTime) > duration;
        }

        private AABB calculateBoundingBox(Set<BlockPos> blocks) {
            if (blocks.isEmpty()) return new AABB(0, 0, 0, 0, 0, 0);

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (BlockPos pos : blocks) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }

    public static void highlight(IMultiController controller, int color, int durationMs) {
        BlockPos controllerPos = controller.self().getPos();
        Set<BlockPos> blocks = MultiblockScanner.getMultiblockBlocks(controller);

        if (blocks.isEmpty()) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.warn("No blocks found for multiblock at {}", controllerPos);
            return;
        }

        HighlightInfo info = new HighlightInfo(controllerPos, blocks, color, durationMs);
        activeHighlights.put(controllerPos, info);

        com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Added highlight for {} blocks at {} (color: 0x{}, duration: {}ms)",
                blocks.size(), controllerPos, Integer.toHexString(color), durationMs);
    }

    public static void highlightByStatus(MultiblockInfo multiblock, int durationMs) {
        int color = multiblock.getStatus().getColor();
        highlight(multiblock.getController(), color, durationMs);
    }

    public static void highlight(IMultiController controller) {
        highlight(controller, 0xFFFF00, 10000); // Yellow, 10 seconds
    }

    public static void clearHighlight(BlockPos controllerPos) {
        activeHighlights.remove(controllerPos);
    }

    public static void clearAll() {
        activeHighlights.clear();
    }

    public static Map<BlockPos, HighlightInfo> getActiveHighlights() {
        // Remove expired highlights
        activeHighlights.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return activeHighlights;
    }

    public static boolean isHighlighted(BlockPos controllerPos) {
        return activeHighlights.containsKey(controllerPos);
    }
}
