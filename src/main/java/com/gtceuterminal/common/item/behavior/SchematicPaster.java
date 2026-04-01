package com.gtceuterminal.common.item.behavior;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.ae2.MENetworkFluidHandlerWrapper;
import com.gtceuterminal.common.ae2.MENetworkItemExtractor;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.pattern.FluidPlacementHelper;
import com.gtceuterminal.common.util.SchematicUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles pasting a {@link SchematicData} from an {@link ItemStack}'s NBT clipboard
 * into the world at a given position.
 *
 * Responsibilities:
 *  - Computing block rotation based on the player's facing
 *  - Two-pass placement: material check first, then placement
 *  - Extracting materials from player inventory or ME network
 *  - Handling fluid blocks via FluidPlacementHelper
 *  - Restoring block-entity data after placement
 */
public final class SchematicPaster {

    private SchematicPaster() {}

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void pasteSchematic(ItemStack itemStack, Player player,
                                      Level level, BlockPos targetPos) {
        CompoundTag itemTag = itemStack.getTag();
        if (itemTag == null || !itemTag.contains("Clipboard")) {
            player.displayClientMessage(
                    Component.translatable("item.gtceuterminal.schematic_paster.no_schematic_in_clipboard"),
                    true);
            return;
        }

        SchematicData clipboard = SchematicData.fromNBT(
                itemTag.getCompound("Clipboard"), level.registryAccess());

        // Resolve rotation
        Direction originalFacing = Direction.SOUTH;
        try {
            String facingStr = clipboard.getOriginalFacing();
            if (facingStr != null && !facingStr.isEmpty()) {
                Direction byName = Direction.byName(facingStr);
                if (byName != null) originalFacing = byName;
            }
        } catch (Exception ignored) {}

        Direction targetFacing = player.getDirection().getOpposite();
        int rotationSteps = SchematicUtils.getRotationSteps(originalFacing, targetFacing);

        // Apply user rotation stored on the item
        try {
            CompoundTag clipTag = itemTag.getCompound("Clipboard");
            if (clipTag.contains("UserRot"))
                rotationSteps = (rotationSteps + (clipTag.getInt("UserRot") & 3)) & 3;
        } catch (Exception ignored) {}

        // Capture as final so it can be used inside the lambda below.
        final int finalRotationSteps = rotationSteps;

        // Compute the lowest Y among all rotated relative positions.
        // The schematic origin is the controller block, which may sit above the floor.
        // Without this adjustment, blocks below the controller end up buried in the ground.
        int minRelY = clipboard.getBlocks().keySet().stream()
                .mapToInt(pos -> SchematicUtils.rotatePositionSteps(pos, finalRotationSteps).getY())
                .min()
                .orElse(0);

        // Shift the anchor so the lowest schematic block sits exactly at targetPos.Y.
        final BlockPos adjustedTarget = targetPos.above(-minRelY);

        GTCEUTerminalMod.LOGGER.info(
                "Pasting at {} (adjusted from {}, minRelY={}) — rotation: {}",
                adjustedTarget, targetPos, minRelY, rotationSteps);

        // ── PASS 1: compute placements + required materials ─────────────────────
        Map<Item, Integer> required = new HashMap<>();
        List<Placement> placements = new ArrayList<>();
        int skippedCount = 0;

        for (Map.Entry<BlockPos, BlockState> entry : clipboard.getBlocks().entrySet()) {
            BlockPos   relativePos  = entry.getKey();
            BlockState state        = entry.getValue();

            BlockPos   rotatedPos   = SchematicUtils.rotatePositionSteps(relativePos, rotationSteps);
            BlockState rotatedState = SchematicUtils.rotateBlockStateSteps(state, rotationSteps);
            BlockPos   worldPos     = adjustedTarget.offset(rotatedPos);

            if (!level.isInWorldBounds(worldPos)) { skippedCount++; continue; }

            BlockState current = level.getBlockState(worldPos);
            if (!current.isAir() && !current.canBeReplaced()) { skippedCount++; continue; }
            if (current.equals(rotatedState))                  { skippedCount++; continue; }

            // Fluid blocks: no item form, handled separately
            if (rotatedState.getFluidState().isSource()) {
                placements.add(new Placement(relativePos, worldPos, rotatedState));
                continue;
            }

            // Skip the UPPER half of doors — the item is already counted from LOWER.
            // When we place the LOWER half via BlockItem.place() Minecraft will
            // auto-create the UPPER half, so we never need to place or count it.
            if (rotatedState.getBlock() instanceof DoorBlock
                    && rotatedState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                skippedCount++; continue;
            }

            Item item = rotatedState.getBlock().asItem();
            if (item == Items.AIR) { skippedCount++; continue; }

            required.merge(item, 1, Integer::sum);
            placements.add(new Placement(relativePos, worldPos, rotatedState));
        }

        if (placements.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("item.gtceuterminal.schematic_paster.nothing_to_paste"),
                    true);
            return;
        }

        // ── PASS 2: material check / extraction ───────────────────────────────
        if (!player.getAbilities().instabuild) {
            MENetworkItemExtractor.ExtractResult result =
                    MENetworkItemExtractor.tryExtractFromMEOrInventory(itemStack, level, player, required);

            if (!result.success) {
                player.displayClientMessage(buildMissingMessage(itemStack, level, player, required), true);
                return;
            }
        }

        // ── PASS 3: place blocks ───────────────────────────────────────────────
        IFluidHandler fluidStorage = null;
        net.minecraftforge.items.IItemHandler playerInventory = null;

        if (!player.getAbilities().instabuild) {
            try { fluidStorage = MENetworkFluidHandlerWrapper.getFromPlayer(player); }
            catch (Exception ignored) {}

            var cap = player.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
            playerInventory = cap.resolve().orElse(null);
        }

        int placedCount = 0;

        for (Placement p : placements) {
            if (p.state.getFluidState().isSource()) {
                Fluid fluid = p.state.getFluidState().getType();
                if (FluidPlacementHelper.tryPlaceFluid(level, p.worldPos, player, fluid, playerInventory, fluidStorage))
                    placedCount++;
                else
                    skippedCount++;
                continue;
            }

            // Doors are two-block-tall structures.
            if (p.state.getBlock() instanceof DoorBlock
                    && p.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                BlockPos upperPos = p.worldPos.above();
                // Only place if there is room for the upper half
                BlockState upperCurrent = level.getBlockState(upperPos);
                if (upperCurrent.isAir() || upperCurrent.canBeReplaced()) {
                    BlockState upperState = p.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                    level.setBlock(p.worldPos, p.state, 3);
                    level.setBlock(upperPos,   upperState, 3);
                    placedCount++;
                } else {
                    skippedCount++;
                }
                continue;
            }
            level.setBlock(p.worldPos, p.state, 3);
            placedCount++;

            // Restore block-entity data
            if (clipboard.getBlockEntities().containsKey(p.relativeKey)) {
                CompoundTag beTag = clipboard.getBlockEntities().get(p.relativeKey).copy();
                BlockEntity be = level.getBlockEntity(p.worldPos);
                if (be != null) {
                    try {
                        beTag.putInt("x", p.worldPos.getX());
                        beTag.putInt("y", p.worldPos.getY());
                        beTag.putInt("z", p.worldPos.getZ());
                        be.load(beTag);
                        try {
                            p.state.getBlock().setPlacedBy(
                                    (net.minecraft.server.level.ServerLevel) level,
                                    p.worldPos, p.state, player,
                                    net.minecraft.world.item.ItemStack.EMPTY);
                        } catch (Exception ignored) {}
                        be.setChanged();
                    } catch (Exception e) {
                        GTCEUTerminalMod.LOGGER.error("Failed to load block entity at {}", p.worldPos, e);
                    }
                }
            }
        }

        Component msg = Component.translatable(
                "item.gtceuterminal.schematic_paster.paste_success.no_skipped",
                placedCount
        );
        if (skippedCount > 0) {
            msg = Component.translatable(
                    "item.gtceuterminal.schematic_paster.paste_success.with_skipped",
                    placedCount,
                    skippedCount
            );
        }
        player.displayClientMessage(msg, true);

        GTCEUTerminalMod.LOGGER.info("Schematic pasted: {} placed, {} skipped", placedCount, skippedCount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Component buildMissingMessage(ItemStack itemStack, Level level,
                                                 Player player, Map<Item, Integer> required) {
        Map<Item, Integer> inv = MaterialCalculator.scanPlayerInventory(player);
        StringBuilder sb = new StringBuilder();
        int shown = 0;

        for (Map.Entry<Item, Integer> req : required.entrySet()) {
            Item it   = req.getKey();
            int  need = req.getValue();
            int  have = (int) Math.min(Integer.MAX_VALUE,
                    (long) inv.getOrDefault(it, 0)
                            + MENetworkItemExtractor.checkItemAvailability(itemStack, level, player, it));
            long miss = need - have;

            if (miss > 0) {
                if (shown > 0) sb.append(", ");
                sb.append(it.getDescription().getString()).append(" x").append(miss);
                if (++shown >= 6) { sb.append(" ..."); break; }
            }
        }

        return Component.translatable(
                "item.gtceuterminal.schematic_paster.missing_materials",
                sb.toString()
        );
    }

    // ── Internal record ───────────────────────────────────────────────────────

    private record Placement(BlockPos relativeKey, BlockPos worldPos, BlockState state) {}
}