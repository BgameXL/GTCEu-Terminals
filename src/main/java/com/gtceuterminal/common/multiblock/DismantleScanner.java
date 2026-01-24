package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

// Scan a multiblock and get information about the blocks that make it up.
public class DismantleScanner {


    // Result of scanning a multiblock
    public static class ScanResult {
        private final BlockPos controllerPos;
        private final Set<BlockPos> allBlocks;
        private final Map<Block, Integer> blockCounts;
        private final Map<Block, List<BlockPos>> blockPositions;
        private final int totalBlocks;

        public ScanResult(BlockPos controllerPos, Set<BlockPos> allBlocks,
                          Map<Block, Integer> blockCounts,
                          Map<Block, List<BlockPos>> blockPositions) {
            this.controllerPos = controllerPos;
            this.allBlocks = allBlocks;
            this.blockCounts = blockCounts;
            this.blockPositions = blockPositions;
            this.totalBlocks = allBlocks.size();
        }

        public static ScanResult empty() {
            return new ScanResult(BlockPos.ZERO, Collections.emptySet(),
                    Collections.emptyMap(), Collections.emptyMap());
        }

        public BlockPos getControllerPos() {
            return controllerPos;
        }

        public Set<BlockPos> getAllBlocks() {
            return allBlocks;
        }

        public Map<Block, Integer> getBlockCounts() {
            return blockCounts;
        }

        public Map<Block, List<BlockPos>> getBlockPositions() {
            return blockPositions;
        }

        public int getTotalBlocks() {
            return totalBlocks;
        }

        // This is used for UI/preview (count per block). Exact refund with NBT
        public List<ItemStack> getItemsToRecover() {
            List<ItemStack> items = new ArrayList<>();
            for (Map.Entry<Block, Integer> entry : blockCounts.entrySet()) {
                ItemStack stack = new ItemStack(entry.getKey().asItem(), entry.getValue());
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            return items;
        }
    }


    // Scan a multiblock and return detailed information
    public static ScanResult scanMultiblock(Level level, MultiblockControllerMachine controller) {
        BlockPos controllerPos = controller.getPos();
        Set<BlockPos> allBlocks = new HashSet<>();
        Map<Block, Integer> blockCounts = new HashMap<>();
        Map<Block, List<BlockPos>> blockPositions = new HashMap<>();

        // Get all blocks from the multiblock from the pattern/cache
        MultiblockState state = controller.getMultiblockState();
        if (state != null) {
            // 1) Prefer the cache: contains the ENTIRE multiblock (casings + parts + controller)
            try {
                Collection<BlockPos> cache = state.getCache();
                if (cache != null && !cache.isEmpty()) {
                    allBlocks.addAll(cache);
                }
            } catch (Throwable ignored) {}

            // 2) Fallback: at least include parts if the cache is unavailable
            if (allBlocks.isEmpty()) {
                controller.getParts().forEach(part -> allBlocks.add(part.self().getPos()));
            }

            // Secure the controller
            allBlocks.add(controllerPos);

            // Count blocks and save positions
            for (BlockPos pos : allBlocks) {
                BlockState blockState = level.getBlockState(pos);
                if (blockState.isAir()) continue;

                Block block = blockState.getBlock();
                blockCounts.put(block, blockCounts.getOrDefault(block, 0) + 1);
                blockPositions.computeIfAbsent(block, k -> new ArrayList<>()).add(pos);
            }
        }

        return new ScanResult(controllerPos, allBlocks, blockCounts, blockPositions);
    }


    // Calculate whether the player has space for all items
    public static boolean hasEnoughSpace(List<ItemStack> items,
                                         int playerInventorySlots,
                                         boolean hasMENetwork,
                                         int nearbyChestSlots) {
        int requiredSlots = items.size();
        int availableSlots = playerInventorySlots;

        if (hasMENetwork) {
            return true; // ME Network has "virtually" infinite space
        }

        availableSlots += nearbyChestSlots;

        return availableSlots >= requiredSlots;
    }
}