package com.gtceuterminal.common.upgrade;

import com.gtceuterminal.common.ae2.MENetworkItemExtractor;
import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentUpgrader {

    /**
     * Upgrade to component with optional ME Network support
     * @param component The component to upgrade
     * @param targetTier Target tier
     * @param player Player performing upgrade
     * @param level Current level
     * @param consumeMaterials Whether to consume materials
     * @param wirelessTerminal Optional wireless terminal for ME access (can be null/empty)
     * @return UpgradeResult with extraction source info
     */
    public static UpgradeResult upgradeComponent(
            ComponentInfo component,
            int targetTier,
            Player player,
            Level level,
            boolean consumeMaterials,
            ItemStack wirelessTerminal
    ) {
        boolean isCreative = player.isCreative();
        String extractionSource = "";

        if (!isCreative && !ComponentUpgradeHelper.canUpgrade(component, targetTier)) {
            return new UpgradeResult(false, "Cannot upgrade to tier " + targetTier);
        }

        Map<Item, Integer> required = ComponentUpgradeHelper.getUpgradeItems(component, targetTier);
        if (required.isEmpty()) {
            return new UpgradeResult(false, "No upgrade item found for this component");
        }

        if (!isCreative && consumeMaterials) {
            // Try ME Network first if wireless terminal is provided
            if (wirelessTerminal != null && !wirelessTerminal.isEmpty()) {
                MENetworkItemExtractor.ExtractResult meResult =
                        MENetworkItemExtractor.tryExtractFromMEOrInventory(
                                wirelessTerminal, level, player, required
                        );

                if (meResult.success) {
                    if (meResult.source == MENetworkItemExtractor.ExtractionSource.ME_NETWORK) {
                        extractionSource = " §a(ME Network)";
                    } else if (meResult.source == MENetworkItemExtractor.ExtractionSource.PLAYER_INVENTORY) {
                        extractionSource = " §7(Inventory)";
                    }
                } else {
                    // Fallback to traditional method
                    List<MaterialAvailability> materials = MaterialCalculator.checkMaterialsAvailability(
                            required, player, level
                    );

                    if (!MaterialCalculator.hasEnoughMaterials(materials)) {
                        Map<Item, Integer> missing = MaterialCalculator.getMissingMaterials(materials);
                        return new UpgradeResult(false, "Missing materials: " + formatMissing(missing));
                    }

                    boolean extracted = MaterialCalculator.extractMaterials(
                            materials, player, level, true, true
                    );

                    if (!extracted) {
                        return new UpgradeResult(false, "Failed to extract materials");
                    }
                    extractionSource = " §7(Inventory)";
                }
            } else {
                // No wireless terminal, use traditional method
                List<MaterialAvailability> materials = MaterialCalculator.checkMaterialsAvailability(
                        required, player, level
                );

                if (!MaterialCalculator.hasEnoughMaterials(materials)) {
                    Map<Item, Integer> missing = MaterialCalculator.getMissingMaterials(materials);
                    return new UpgradeResult(false, "Missing materials: " + formatMissing(missing));
                }

                boolean extracted = MaterialCalculator.extractMaterials(
                        materials, player, level, true, true
                );

                if (!extracted) {
                    return new UpgradeResult(false, "Failed to extract materials");
                }
                extractionSource = " §7(Inventory)";
            }
        }

        BlockPos pos = component.getPosition();
        BlockState oldState = component.getState();
        Block oldBlock = oldState.getBlock();

        Item oldItem = oldBlock.asItem();
        ItemStack oldStack = null;
        if (!isCreative && oldItem != null && oldItem != net.minecraft.world.item.Items.AIR) {
            oldStack = new ItemStack(oldItem, 1);
        }

        Item upgradeItem = required.keySet().iterator().next();
        Block newBlock = Block.byItem(upgradeItem);

        if (newBlock == null || newBlock == net.minecraft.world.level.block.Blocks.AIR) {
            if (consumeMaterials && oldStack != null) {
                for (var entry : required.entrySet()) {
                    ItemStack refund = new ItemStack(entry.getKey(), entry.getValue());
                    if (!player.getInventory().add(refund)) {
                        player.drop(refund, false);
                    }
                }
            }
            return new UpgradeResult(false, "Invalid upgrade block");
        }

        BlockState newState = newBlock.defaultBlockState();

        try {
            newState = copyBlockStateProperties(oldState, newState);
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Could not copy properties: {}", e.getMessage());
        }

        boolean placed = level.setBlock(pos, newState, 11);

        if (!placed) {
            if (consumeMaterials) {
                for (var entry : required.entrySet()) {
                    ItemStack refund = new ItemStack(entry.getKey(), entry.getValue());
                    if (!player.getInventory().add(refund)) {
                        player.drop(refund, false);
                    }
                }
            }
            return new UpgradeResult(false, "Failed to place upgraded block");
        }

        level.sendBlockUpdated(pos, oldState, newState, 3);
        level.blockUpdated(pos, newBlock);

        updateNeighborBlocks(level, pos, newBlock);

        level.sendBlockUpdated(pos, oldState, newState, 11);

        level.getChunkAt(pos).setUnsaved(true);

        if (oldStack != null) {
            if (!player.getInventory().add(oldStack)) {
                player.drop(oldStack, false);
            }
            GTCEUTerminalMod.LOGGER.info("Returned old block {} to player", oldItem);
        }

        GTCEUTerminalMod.LOGGER.info("Upgraded {} at {} from {} to {} (Block: {} -> {}){}",
                component.getType(), pos, component.getTierName(),
                com.gregtechceu.gtceu.api.GTValues.VN[targetTier],
                oldBlock.getDescriptionId(), newBlock.getDescriptionId(),
                extractionSource.replace("§a", "").replace("§7", ""));

        return new UpgradeResult(true, "Successfully upgraded to " +
                com.gregtechceu.gtceu.api.GTValues.VN[targetTier] + extractionSource);
    }


    // Backward compatibility - upgrade without wireless terminal
    public static UpgradeResult upgradeComponent(
            ComponentInfo component,
            int targetTier,
            Player player,
            Level level,
            boolean consumeMaterials
    ) {
        return upgradeComponent(component, targetTier, player, level, consumeMaterials, ItemStack.EMPTY);
    }

    // Bulk upgrade with ME Network support
    public static BulkUpgradeResult upgradeMultipleComponents(
            List<ComponentInfo> components,
            int targetTier,
            Player player,
            Level level,
            ItemStack wirelessTerminal
    ) {
        BulkUpgradeResult result = new BulkUpgradeResult();

        Map<Item, Integer> totalRequired = new HashMap<>();
        boolean upgradingCoils = false;

        for (ComponentInfo component : components) {
            if (!ComponentUpgradeHelper.canUpgrade(component, targetTier)) {
                continue;
            }

            if (component.getType() == ComponentType.COIL) {
                upgradingCoils = true;
            }

            Map<Item, Integer> required = ComponentUpgradeHelper.getUpgradeItems(component, targetTier);
            required.forEach((item, count) ->
                    totalRequired.merge(item, count, Integer::sum)
            );
        }

        String extractionSource = "";

        // Try ME Network first if wireless terminal is provided
        if (wirelessTerminal != null && !wirelessTerminal.isEmpty()) {
            MENetworkItemExtractor.ExtractResult meResult =
                    MENetworkItemExtractor.tryExtractFromMEOrInventory(
                            wirelessTerminal, level, player, totalRequired
                    );

            if (meResult.success) {
                if (meResult.source == MENetworkItemExtractor.ExtractionSource.ME_NETWORK) {
                    extractionSource = " §a(ME Network)";
                } else if (meResult.source == MENetworkItemExtractor.ExtractionSource.PLAYER_INVENTORY) {
                    extractionSource = " §7(Inventory)";
                }
            } else {
                // Fallback to traditional method
                List<MaterialAvailability> materials = MaterialCalculator.checkMaterialsAvailability(
                        totalRequired, player, level
                );

                if (!MaterialCalculator.hasEnoughMaterials(materials)) {
                    Map<Item, Integer> missing = MaterialCalculator.getMissingMaterials(materials);
                    result.success = false;
                    result.message = "Missing materials: " + formatMissing(missing);
                    return result;
                }

                boolean extracted = MaterialCalculator.extractMaterials(
                        materials, player, level, true, true
                );

                if (!extracted) {
                    result.success = false;
                    result.message = "Failed to extract materials";
                    return result;
                }
                extractionSource = " §7(Inventory)";
            }
        } else {
            // No wireless terminal, use traditional method
            List<MaterialAvailability> materials = MaterialCalculator.checkMaterialsAvailability(
                    totalRequired, player, level
            );

            if (!MaterialCalculator.hasEnoughMaterials(materials)) {
                Map<Item, Integer> missing = MaterialCalculator.getMissingMaterials(materials);
                result.success = false;
                result.message = "Missing materials: " + formatMissing(missing);
                return result;
            }

            boolean extracted = MaterialCalculator.extractMaterials(
                    materials, player, level, true, true
            );

            if (!extracted) {
                result.success = false;
                result.message = "Failed to extract materials";
                return result;
            }
            extractionSource = " §7(Inventory)";
        }

        for (ComponentInfo component : components) {
            if (!ComponentUpgradeHelper.canUpgrade(component, targetTier)) {
                result.skipped++;
                continue;
            }

            UpgradeResult componentResult = upgradeComponent(
                    component, targetTier, player, level, false, ItemStack.EMPTY
            );

            if (componentResult.success) {
                result.successful++;
            } else {
                result.failed++;
                result.errors.add(componentResult.message);
            }
        }

        result.success = result.successful > 0;
        result.message = String.format("Upgraded %d/%d components%s",
                result.successful, components.size(), extractionSource);

        if (upgradingCoils && result.successful > 0) {
            String coilName = "Unknown Coil";
            try {
                ComponentInfo firstCoil = components.stream()
                        .filter(c -> c.getType() == ComponentType.COIL)
                        .findFirst()
                        .orElse(null);
                if (firstCoil != null) {
                    String fullName = ComponentUpgradeHelper.getUpgradeName(firstCoil, targetTier);
                    if (fullName != null && !fullName.isEmpty()) {
                        coilName = fullName;
                    }
                }
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error getting coil name for upgrade message", e);
            }

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            String.format("§e⚠ Upgraded %d coils to §6%s%s", result.successful, coilName, extractionSource)
                    ).withStyle(net.minecraft.ChatFormatting.YELLOW),
                    false
            );

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§6⚠ Reset multiblock to apply new temperature"
                    ).withStyle(net.minecraft.ChatFormatting.GOLD),
                    true
            );

            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.ANVIL_LAND,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F,
                    1.2F
            );

            GTCEUTerminalMod.LOGGER.info("Player {} upgraded {} coils to {} (tier {}){}",
                    player.getName().getString(), result.successful, coilName, targetTier,
                    extractionSource.replace("§a", "").replace("§7", ""));
        }

        return result;
    }


    // Backward compatibility - bulk upgrade without wireless terminal
    public static BulkUpgradeResult upgradeMultipleComponents(
            List<ComponentInfo> components,
            int targetTier,
            Player player,
            Level level
    ) {
        return upgradeMultipleComponents(components, targetTier, player, level, ItemStack.EMPTY);
    }

    private static BlockState copyBlockStateProperties(BlockState oldState, BlockState newState) {
        try {
            if (oldState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) &&
                    newState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                var facing = oldState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                newState = newState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
            }

            if (oldState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) &&
                    newState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                var facing = oldState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                newState = newState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, facing);
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Failed to copy block state properties", e);
        }

        return newState;
    }

    private static void updateNeighborBlocks(Level level, BlockPos pos, Block newBlock) {
        BlockPos[] neighbors = {
                pos,
                pos.above(),
                pos.below(),
                pos.north(),
                pos.south(),
                pos.east(),
                pos.west()
        };

        for (BlockPos neighborPos : neighbors) {
            BlockState neighborState = level.getBlockState(neighborPos);

            level.neighborChanged(neighborPos, newBlock, pos);

            level.sendBlockUpdated(neighborPos, neighborState, neighborState, 3);
        }

        if (!level.isClientSide) {
            var chunk = level.getChunkAt(pos);
            chunk.setUnsaved(true);

            level.getChunkSource().getLightEngine().checkBlock(pos);
        }

        GTCEUTerminalMod.LOGGER.info("Updated CTM for block at {} and 6 neighbors", pos);
    }

    private static String formatMissing(Map<Item, Integer> missing) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey().getDescription().getString())
                    .append(" x")
                    .append(entry.getValue());
        }
        return sb.toString();
    }

    public static boolean upgradeComponent(BlockPos position, int targetTier, Player player, BlockPos controllerPos, boolean wirelessTerminal)   {
        return wirelessTerminal;
    }

    public static boolean upgradeComponent(BlockPos position, int targetTier, Player player, BlockPos controllerPos, ItemStack wirelessTerminal) {
        return false;
    }

    public static class UpgradeResult {
        public final boolean success;
        public final String message;

        public UpgradeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static class BulkUpgradeResult {
        public boolean success;
        public String message;
        public int successful = 0;
        public int failed = 0;
        public int skipped = 0;
        public List<String> errors = new java.util.ArrayList<>();
    }
}