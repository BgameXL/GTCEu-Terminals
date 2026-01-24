package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.ClientProxy;
import com.gtceuterminal.common.data.SchematicData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import net.minecraft.world.item.Items;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.ae2.MENetworkItemExtractor;

public class SchematicInterfaceBehavior {

    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();

        boolean shiftDown = player.isShiftKeyDown();

        if (shiftDown) {
            // Try to copy multiblock
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);
            if (machine instanceof IMultiController) {
                IMultiController controller = (IMultiController) machine;
                if (controller.isFormed()) {
                    if (!level.isClientSide) {
                        copyMultiblock(controller, itemStack, player, level, blockPos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            // Open GUI if not clicking on a multiblock
            if (level.isClientSide) {
                GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Schematic UI");
                ClientProxy.openSchematicGUI(itemStack, player, Collections.emptyList());
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.CONSUME;
        }

        // Right click normal (paste)
        if (!level.isClientSide) {
            // Match vanilla placement behavior:
            // - If the clicked block is replaceable (tall grass, snow, etc.), place "into" it.
            // - Otherwise place on the adjacent position of the clicked face.
            // This prevents the controller anchor from ending up inside solid blocks when aiming at the ground.
            BlockPos anchor = blockPos;
            try {
                BlockState clicked = level.getBlockState(blockPos);
                if (clicked != null && !clicked.isAir() && !clicked.canBeReplaced()) {
                    anchor = blockPos.relative(context.getClickedFace());
                }
            } catch (Exception ignored) {
                anchor = blockPos.relative(context.getClickedFace());
            }

            pasteSchematic(itemStack, player, level, anchor, context.getClickedFace());
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }


    // Handle right-click in air to paste at EXACT ghost preview position
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (player.isShiftKeyDown()) {
            // Shift + Right-click in air: Open GUI
            if (level.isClientSide) {
                GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Schematic UI");
                ClientProxy.openSchematicGUI(itemStack, player, Collections.emptyList());
                return InteractionResultHolder.success(itemStack);
            }
            return InteractionResultHolder.consume(itemStack);
        }

        // Right-click in air (no shift): Paste at EXACT ghost preview position
        if (hasClipboard(itemStack)) {
            if (!level.isClientSide) {
                // Get schematic to calculate optimal distance (same as renderer)
                CompoundTag itemTag = itemStack.getTag();
                SchematicData clipboard = SchematicData.fromNBT(
                        itemTag.getCompound("Clipboard"),
                        level.registryAccess()
                );

                // Calculate same distance as SchematicPreviewRenderer
                double distance = calculateOptimalDistance(clipboard);

                // Use EXACT same logic as SchematicPreviewRenderer.getTargetPlacementPos()
                BlockPos targetPos = getTargetPlacementPos(player, distance);
                Direction facing = Direction.UP;

                pasteSchematic(itemStack, player, level, targetPos, facing);
                return InteractionResultHolder.success(itemStack);
            }
            return InteractionResultHolder.consume(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    private double calculateOptimalDistance(SchematicData schematic) {
        BlockPos size = schematic.getSize();
        int maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

        double distance = 4.0 + (maxDimension / 2.0);
        return Math.min(15.0, Math.max(4.0, distance));
    }

    private BlockPos getTargetPlacementPos(Player player, double distance) {
        double raycastDistance = Math.max(10.0, distance + 5.0);
        HitResult hitResult = player.pick(raycastDistance, 0.0f, false);

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 targetVec = eyePos.add(lookVec.scale(distance));

        return new BlockPos(
                (int) Math.floor(targetVec.x),
                (int) Math.floor(targetVec.y),
                (int) Math.floor(targetVec.z)
        );
    }


    // Check if item has clipboard data
    private boolean hasClipboard(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag == null || !tag.contains("Clipboard")) {
            return false;
        }
        CompoundTag clipboardTag = tag.getCompound("Clipboard");
        return clipboardTag.contains("Blocks") && !clipboardTag.getList("Blocks", 10).isEmpty();
    }

    private void copyMultiblock(IMultiController controller, ItemStack itemStack, Player player, Level level, BlockPos blockPos) {
        GTCEUTerminalMod.LOGGER.info("=== COPYING MULTIBLOCK ===");

        Set<BlockPos> positions = scanMultiblockArea(controller, level);

        if (positions.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§cFailed to scan multiblock!"),
                    true
            );
            GTCEUTerminalMod.LOGGER.warn("Scanned multiblock but got 0 positions");
            return;
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();

        BlockPos controllerPos = controller.self().getPos();

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            BlockPos relativePos = pos.subtract(controllerPos);
            blocks.put(relativePos, state);

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    CompoundTag tag = be.saveWithFullMetadata();
                    blockEntities.put(relativePos, tag);
                } catch (Exception e) {
                    GTCEUTerminalMod.LOGGER.error("Failed to save block entity at {}", pos, e);
                }
            }
        }

        Direction facing = controller.self().getFrontFacing();
        String multiblockType = controller.self().getDefinition().getId().toString();

        SchematicData clipboard = new SchematicData(
                "Clipboard",
                multiblockType,
                blocks,
                blockEntities,
                facing.getName()
        );

        CompoundTag itemTag = itemStack.getOrCreateTag();
        itemTag.put("Clipboard", clipboard.toNBT());
        itemStack.setTag(itemTag);

        player.displayClientMessage(
                Component.literal("§aMultiblock copied! " + blocks.size() + " blocks"),
                true
        );

        GTCEUTerminalMod.LOGGER.info("Multiblock copied: {} blocks", blocks.size());
    }

    private Set<BlockPos> scanMultiblockArea(IMultiController controller, Level level) {
        Set<BlockPos> positions = new HashSet<>();

        try {
            Collection<BlockPos> cachePos = controller.getMultiblockState().getCache();
            if (cachePos != null && !cachePos.isEmpty()) {
                positions.addAll(cachePos);
                GTCEUTerminalMod.LOGGER.info("Got {} positions from multiblock cache", positions.size());
                return positions;
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Failed to get positions from cache, using fallback method");
        }

        BlockPos controllerPos = controller.self().getPos();
        List<IMultiPart> parts = controller.getParts();

        if (parts.isEmpty()) {
            GTCEUTerminalMod.LOGGER.warn("No parts found in multiblock");
            return positions;
        }

        GTCEUTerminalMod.LOGGER.info("Scanning area around {} parts", parts.size());

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (IMultiPart part : parts) {
            BlockPos pos = part.self().getPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        minX -= 3; minY -= 3; minZ -= 3;
        maxX += 3; maxY += 3; maxZ += 3;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir()) {
                        positions.add(pos);
                    }
                }
            }
        }

        GTCEUTerminalMod.LOGGER.info("Scanned area and found {} blocks", positions.size());
        return positions;
    }

    // Paste schematic at any position with proper rotation
    private void pasteSchematic(ItemStack itemStack, Player player, Level level, BlockPos targetPos, Direction facing) {
        CompoundTag itemTag = itemStack.getTag();
        if (itemTag == null || !itemTag.contains("Clipboard")) {
            player.displayClientMessage(
                    Component.literal("§cNo schematic in clipboard!"),
                    true
            );
            return;
        }

        SchematicData clipboard = SchematicData.fromNBT(
                itemTag.getCompound("Clipboard"),
                level.registryAccess()
        );

        // Get original facing from schematic
        Direction originalFacing = Direction.SOUTH;
        try {
            String facingStr = clipboard.getOriginalFacing();
            if (facingStr != null && !facingStr.isEmpty()) {
                Direction byName = Direction.byName(facingStr);
                if (byName != null) originalFacing = byName;
            }
        } catch (Exception ignored) {
        }

        // Calculate player's horizontal facing
        Direction playerFacing = getPlayerHorizontalFacing(player);
        Direction targetFacing = playerFacing.getOpposite();

        // Calculate rotation steps needed
        int rotationSteps = getRotationSteps(originalFacing, targetFacing);

        GTCEUTerminalMod.LOGGER.info("Pasting schematic at {} - Original facing: {}, Player facing: {}, Rotation steps: {}",
                targetPos, originalFacing, playerFacing, rotationSteps);

        // === FIRST PASS: compute placements + required materials ===
        Map<Item, Integer> required = new HashMap<>();
        List<Placement> placements = new ArrayList<>();

        int skippedCount = 0;

        for (Map.Entry<BlockPos, BlockState> entry : clipboard.getBlocks().entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            // Apply rotation to position
            BlockPos rotatedPos = rotatePositionSteps(relativePos, rotationSteps);
            BlockPos worldPos = targetPos.offset(rotatedPos);

            // Apply rotation to block state
            BlockState rotatedState = rotateBlockStateSteps(state, rotationSteps);

            // Bounds check
            if (!level.isInWorldBounds(worldPos)) {
                skippedCount++;
                continue;
            }

            // Skip if can't be placed here
            BlockState currentState = level.getBlockState(worldPos);
            boolean canReplace = currentState.isAir() || currentState.canBeReplaced();
            if (!canReplace) {
                skippedCount++;
                continue;
            }

            // If it's already exactly the same state, don't charge / place again
            if (currentState == rotatedState || currentState.equals(rotatedState)) {
                skippedCount++;
                continue;
            }

            // Determine item cost for this block
            Item item = rotatedState.getBlock().asItem();
            if (item == Items.AIR) {
                // Unplaceable via inventory (no item form) - skip it to avoid dupes
                skippedCount++;
                continue;
            }

            required.merge(item, 1, Integer::sum);
            placements.add(new Placement(relativePos, worldPos, rotatedState));
        }

        // Nothing to place
        if (placements.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§eNothing to paste here. Area may be occupied (or identical)."),
                    true
            );
            return;
        }

        // === MATERIAL CHECK / EXTRACTION (SURVIVAL ONLY) ===
        if (!player.getAbilities().instabuild) {
            MENetworkItemExtractor.ExtractResult result =
                    MENetworkItemExtractor.tryExtractFromMEOrInventory(itemStack, level, player, required);

            if (!result.success) {
                // Build missing list (Inventory + ME only)
                Map<Item, Integer> inv = MaterialCalculator.scanPlayerInventory(player);

                StringBuilder sb = new StringBuilder("§cMissing materials: ");
                int shown = 0;

                for (Map.Entry<Item, Integer> req : required.entrySet()) {
                    Item it = req.getKey();
                    int need = req.getValue();

                    int haveInv = inv.getOrDefault(it, 0);
                    long haveME = MENetworkItemExtractor.checkItemAvailability(itemStack, level, player, it);

                    long have = (long) haveInv + haveME;
                    long miss = need - have;

                    if (miss > 0) {
                        if (shown > 0) sb.append("§7, ");
                        sb.append("§f").append(it.getDescription().getString()).append("§7 x").append(miss);
                        shown++;
                        if (shown >= 6) {
                            sb.append("§7 ...");
                            break;
                        }
                    }
                }

                player.displayClientMessage(Component.literal(sb.toString()), true);
                return;
            }
        }

        // === SECOND PASS: place blocks (now that we paid) ===
        int placedCount = 0;

        for (Placement p : placements) {
            // Place block
            level.setBlock(p.worldPos, p.state, 3);
            placedCount++;

            // Copy block entity data if exists (use original relative key)
            if (clipboard.getBlockEntities().containsKey(p.relativeKey)) {
                CompoundTag beTag = clipboard.getBlockEntities().get(p.relativeKey);
                BlockEntity be = level.getBlockEntity(p.worldPos);
                if (be != null) {
                    try {
                        be.load(beTag);
                    } catch (Exception e) {
                        GTCEUTerminalMod.LOGGER.error("Failed to load block entity at {}", p.worldPos, e);
                    }
                }
            }
        }

        player.displayClientMessage(
                Component.literal(String.format("§aSchematic pasted! §f%d §ablocks placed", placedCount) +
                        (skippedCount > 0 ? String.format(" §7(%d skipped)", skippedCount) : "")),
                true
        );

        GTCEUTerminalMod.LOGGER.info("Schematic pasted: {} blocks placed, {} skipped", placedCount, skippedCount);
    }

    private record Placement(BlockPos relativeKey, BlockPos worldPos, BlockState state) {}


    // Get player's horizontal facing direction
    private Direction getPlayerHorizontalFacing(Player player) {
        float yaw = (player.getYRot() % 360 + 360) % 360;

        if (yaw >= 315 || yaw < 45) {
            return Direction.SOUTH;
        } else if (yaw >= 45 && yaw < 135) {
            return Direction.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            return Direction.NORTH;
        } else {
            return Direction.EAST;
        }
    }


    // Calculate rotation steps between two directions
    private int getRotationSteps(Direction from, Direction to) {
        return (to.get2DDataValue() - from.get2DDataValue() + 4) % 4;
    }


    // Rotate position by given steps
    private BlockPos rotatePositionSteps(BlockPos pos, int steps) {
        BlockPos result = pos;
        for (int i = 0; i < steps; i++) {
            // (x, z) -> (-z, x)
            result = new BlockPos(-result.getZ(), result.getY(), result.getX());
        }
        return result;
    }


    // Rotate block state by given steps
    private BlockState rotateBlockStateSteps(BlockState state, int steps) {
        BlockState result = state;
        for (int i = 0; i < steps; i++) {
            result = rotateBlockStateOnce(result);
        }
        return result;
    }


    // Rotate block state once (90 degrees clockwise)
    private BlockState rotateBlockStateOnce(BlockState state) {
        try {
            // HORIZONTAL_FACING
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                Direction facing = state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                if (facing.getAxis().isHorizontal()) {
                    return state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                            facing.getClockWise()
                    );
                }
            }

            // FACING
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                Direction facing = state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                            facing.getClockWise()
                    );
                }
            }
        } catch (Exception ignored) {
        }

        return state;
    }
}