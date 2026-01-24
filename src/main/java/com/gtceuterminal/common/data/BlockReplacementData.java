package com.gtceuterminal.common.data;

import com.gtceuterminal.common.multiblock.BlockCategory;

import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class BlockReplacementData {

    private final Map<BlockState, Integer> blockCounts = new HashMap<>();
    private final Map<String, BlockState> tierRepresentatives = new HashMap<>();
    private final Map<BlockState, BlockState> replacements = new HashMap<>();
    private boolean mirrorMode = false;
    private BlockState fillCasing = null;
    private int maxTierIndex = 10; // Default to UHV

    public void addBlock(BlockState state) {
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        BlockCategory.Category category = BlockCategory.categorize(state);

        // For buses and hatches, group by tier + type
        if (category == BlockCategory.Category.BUSES || category == BlockCategory.Category.HATCHES ||
                category == BlockCategory.Category.ENERGY || category == BlockCategory.Category.LASER_ENERGY ||
                category == BlockCategory.Category.SUBSTATION_ENERGY || category == BlockCategory.Category.DYNAMO_ENERGY) {

            String tier = BlockCategory.getTierFromPath(path);
            boolean isInput = path.contains("input");
            boolean isFluid = path.contains("fluid");
            boolean isEnergy = path.contains("energy");

            String key = category.name() + "_" + tier + "_" + (isInput ? "input" : "output") +
                    (isFluid ? "_fluid" : "") + (isEnergy ? "_energy" : "");

            BlockState representative = tierRepresentatives.computeIfAbsent(key, k -> state);

            blockCounts.merge(representative, 1, Integer::sum);
        } else {
            blockCounts.merge(state, 1, Integer::sum);
        }
    }

    public Map<BlockState, Integer> getBlockCounts() {
        return Collections.unmodifiableMap(blockCounts);
    }

    public int getTotalCount(BlockState state) {
        return blockCounts.getOrDefault(state, 0);
    }

    public void setReplacement(BlockState oldState, BlockState newState) {
        replacements.put(oldState, newState);
    }

    public BlockState getReplacement(BlockState state) {
        return replacements.get(state);
    }

    public Map<BlockState, BlockState> getReplacements() {
        return Collections.unmodifiableMap(replacements);
    }

    public void clearReplacements() {
        replacements.clear();
    }

    public boolean isMirrorMode() {
        return mirrorMode;
    }

    public void setMirrorMode(boolean mirrorMode) {
        this.mirrorMode = mirrorMode;
    }

    public BlockState getFillCasing() {
        return fillCasing;
    }

    public void setFillCasing(BlockState fillCasing) {
        this.fillCasing = fillCasing;
    }

    public int getMaxTierIndex() {
        return maxTierIndex;
    }

    public void setMaxTierIndex(int maxTierIndex) {
        this.maxTierIndex = maxTierIndex;
    }
}
