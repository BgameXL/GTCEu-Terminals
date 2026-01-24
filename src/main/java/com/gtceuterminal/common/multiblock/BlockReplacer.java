package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.ae2.MENetworkItemExtractor;
import com.gtceuterminal.common.data.BlockReplacementData;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class BlockReplacer {


    // Replace blocks with ME Network support
    public static boolean replaceBlocks(IMultiController controller, Player player,
                                        BlockReplacementData data, ItemStack wirelessTerminal) {
        Level level = controller.self().getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }

        BlockPos controllerPos = controller.self().getPos();

        Map<Block, Integer> required = calculateRequiredBlocks(data);

        // Try ME Network first, then inventory
        boolean hasResources = false;
        MENetworkItemExtractor.ExtractionSource extractionSource = MENetworkItemExtractor.ExtractionSource.NONE;

        if (!player.isCreative()) {
            // Convert Block to Item for ME extraction
            Map<Item, Integer> requiredItems = new HashMap<>();
            for (Map.Entry<Block, Integer> entry : required.entrySet()) {
                Item item = entry.getKey().asItem();
                if (item != net.minecraft.world.item.Items.AIR) {
                    requiredItems.put(item, entry.getValue());
                }
            }

            // Try extract from ME or inventory
            if (wirelessTerminal != null && !wirelessTerminal.isEmpty()) {
                MENetworkItemExtractor.ExtractResult result =
                        MENetworkItemExtractor.tryExtractFromMEOrInventory(
                                wirelessTerminal, level, player, requiredItems
                        );

                hasResources = result.success;
                extractionSource = result.source;
            } else {
                // No wireless terminal, use traditional method
                hasResources = verifyAndConsumeInventory(player, required);
                extractionSource = hasResources ? MENetworkItemExtractor.ExtractionSource.PLAYER_INVENTORY : MENetworkItemExtractor.ExtractionSource.NONE;
            }

            if (!hasResources) {
                GTCEUTerminalMod.LOGGER.warn("Player {} doesn't have required resources", player.getName().getString());
                return false;
            }
        }

        com.gregtechceu.gtceu.api.pattern.MultiblockState state = controller.getMultiblockState();
        if (state == null) {
            return false;
        }

        java.util.Collection<BlockPos> positions = null;
        try {
            positions = state.getCache();
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error getting cache for replacement", e);
            return false;
        }

        if (positions == null || positions.isEmpty()) {
            return false;
        }

        Map<Block, Integer> returned = new HashMap<>();

        BlockState fillCasing = data.getFillCasing();

        Map<String, BlockState> typeReplacements = new HashMap<>();
        for (Map.Entry<BlockState, BlockState> entry : data.getReplacements().entrySet()) {
            String oldPath = entry.getKey().getBlock().builtInRegistryHolder().key().location().getPath();
            typeReplacements.put(oldPath, entry.getValue());
        }

        for (BlockPos pos : positions) {
            BlockState currentState = level.getBlockState(pos);
            String currentPath = currentState.getBlock().builtInRegistryHolder().key().location().getPath();

            BlockState newState = data.getReplacement(currentState);

            if (newState == null && typeReplacements.containsKey(currentPath)) {
                newState = typeReplacements.get(currentPath);
            }

            if (newState != null && !currentState.equals(newState)) {
                BlockState finalState = copyProperties(currentState, newState);
                level.setBlock(pos, finalState, 3);
                returned.merge(currentState.getBlock(), 1, Integer::sum);

                updateNeighborBlocks(level, pos, finalState.getBlock());
            } else if (fillCasing != null && isHatchOrBus(currentState)) {
                boolean wasReplaced = data.getReplacements().containsKey(currentState) || typeReplacements.containsKey(currentPath);
                if (!wasReplaced) {
                    BlockState finalCasing = copyProperties(currentState, fillCasing);
                    level.setBlock(pos, finalCasing, 3);
                    returned.merge(currentState.getBlock(), 1, Integer::sum);

                    updateNeighborBlocks(level, pos, finalCasing.getBlock());
                }
            }
        }

        returnBlocks(player, returned);

        try {
            controller.checkPattern();

            String sourceMsg = extractionSource == MENetworkItemExtractor.ExtractionSource.ME_NETWORK
                    ? " (from ME Network)"
                    : extractionSource == MENetworkItemExtractor.ExtractionSource.PLAYER_INVENTORY
                    ? " (from Inventory)"
                    : "";

            GTCEUTerminalMod.LOGGER.info("Multiblock pattern re-checked after block replacement{}", sourceMsg);
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error rechecking pattern after replacement", e);
        }

        return true;
    }

    // Backward compatibility - replace blocks without wireless terminal
    public static boolean replaceBlocks(IMultiController controller, Player player,
                                        BlockReplacementData data) {
        return replaceBlocks(controller, player, data, ItemStack.EMPTY);
    }

    private static boolean isHatchOrBus(BlockState state) {
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        return path.contains("hatch") || path.contains("bus");
    }

    private static void updateNeighborBlocks(Level level, BlockPos pos, Block newBlock) {
        BlockPos[] neighbors = {
                pos,           // The block itself
                pos.above(), pos.below(),
                pos.north(), pos.south(),
                pos.east(), pos.west()
        };

        for (BlockPos neighborPos : neighbors) {
            BlockState neighborState = level.getBlockState(neighborPos);
            level.neighborChanged(neighborPos, newBlock, pos);
            level.sendBlockUpdated(neighborPos, neighborState, neighborState, 11);
        }

        if (!level.isClientSide) {
            var chunk = level.getChunkAt(pos);
            chunk.setUnsaved(true);
            level.getChunkSource().getLightEngine().checkBlock(pos);
        }
    }

    private static BlockState copyProperties(BlockState oldState, BlockState newState) {
        try {
            for (net.minecraft.world.level.block.state.properties.Property<?> property : oldState.getProperties()) {
                if (newState.hasProperty(property)) {
                    newState = copyProperty(oldState, newState, property);
                }
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.debug("Could not copy all properties: {}", e.getMessage());
        }
        return newState;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState oldState, BlockState newState,
                                                                     net.minecraft.world.level.block.state.properties.Property<T> property) {
        return newState.setValue(property, oldState.getValue(property));
    }

    private static Map<Block, Integer> calculateRequiredBlocks(BlockReplacementData data) {
        Map<Block, Integer> required = new HashMap<>();

        for (Map.Entry<BlockState, BlockState> entry : data.getReplacements().entrySet()) {
            BlockState newState = entry.getValue();
            int count = data.getBlockCounts().getOrDefault(entry.getKey(), 0);

            if (data.isMirrorMode()) {
                count *= 2;
            }

            required.merge(newState.getBlock(), count, Integer::sum);
        }

        return required;
    }


    // Verify and consume from player inventory (fallback method)
    private static boolean verifyAndConsumeInventory(Player player, Map<Block, Integer> required) {
        if (player.isCreative()) {
            return true;
        }

        Map<Block, Integer> available = new HashMap<>();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            Block block = Block.byItem(stack.getItem());
            if (block != null && required.containsKey(block)) {
                available.merge(block, stack.getCount(), Integer::sum);
            }
        }

        // Verify first
        for (Map.Entry<Block, Integer> entry : required.entrySet()) {
            int needed = entry.getValue();
            int have = available.getOrDefault(entry.getKey(), 0);

            if (have < needed) {
                return false;
            }
        }

        // Then consume
        consumeBlocks(player, required);
        return true;
    }

    private static void consumeBlocks(Player player, Map<Block, Integer> blocks) {
        if (player.isCreative()) {
            return;
        }

        for (Map.Entry<Block, Integer> entry : blocks.entrySet()) {
            Block block = entry.getKey();
            int needed = entry.getValue();

            for (ItemStack stack : player.getInventory().items) {
                if (needed <= 0) break;

                if (Block.byItem(stack.getItem()) == block) {
                    int toConsume = Math.min(stack.getCount(), needed);
                    stack.shrink(toConsume);
                    needed -= toConsume;
                }
            }
        }
    }

    private static void returnBlocks(Player player, Map<Block, Integer> blocks) {
        // Don't return blocks in creative mode
        if (player.isCreative()) {
            return;
        }

        for (Map.Entry<Block, Integer> entry : blocks.entrySet()) {
            ItemStack stack = new ItemStack(entry.getKey().asItem(), entry.getValue());

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}