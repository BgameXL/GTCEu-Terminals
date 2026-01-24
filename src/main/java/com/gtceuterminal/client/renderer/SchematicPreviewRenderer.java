package com.gtceuterminal.client.renderer;

import com.gtceuterminal.common.data.SchematicData;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


// Renders a schematic preview as ghost blocks in the world
public class SchematicPreviewRenderer {

    public static void renderGhostBlocks(PoseStack poseStack,
                                         MultiBufferSource bufferSource,
                                         SchematicData schematic,
                                         Minecraft minecraft,
                                         CompoundTag clipboardTag) {
        if (schematic == null || schematic.getBlocks().isEmpty() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        Direction originalFacing = Direction.SOUTH;
        try {
            String facingStr = schematic.getOriginalFacing();
            if (facingStr != null && !facingStr.isEmpty()) {
                Direction byName = Direction.byName(facingStr);
                if (byName != null) originalFacing = byName;
            }
        } catch (Exception ignored) {
        }

        double distance = calculateOptimalDistance(schematic);

        BlockPos targetPos = getTargetPlacementPos(minecraft, distance);
        if (targetPos == null) {
            return;
        }

        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        if (cameraPos.distanceTo(Vec3.atCenterOf(targetPos)) > 20.0) {
            return;
        }

        Direction playerFacing = getPlayerHorizontalFacing(minecraft.player);
        Direction targetFacing = playerFacing.getOpposite();

        int rotationSteps = getRotationSteps(originalFacing, targetFacing);

        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();

        // Render each block in the schematic
        for (var entry : schematic.getBlocks().entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            BlockPos rotatedPos = rotatePositionSteps(relativePos, rotationSteps);
            BlockPos worldPos = targetPos.offset(rotatedPos);

            BlockState rotatedState = rotateBlockStateSteps(state, rotationSteps);

            poseStack.pushPose();
            poseStack.translate(
                    worldPos.getX() - cameraPos.x,
                    worldPos.getY() - cameraPos.y,
                    worldPos.getZ() - cameraPos.z
            );

            try {
                blockRenderer.renderSingleBlock(
                        rotatedState,
                        poseStack,
                        bufferSource,
                        15728880,  // full bright
                        OverlayTexture.NO_OVERLAY
                );
            } catch (Exception ignored) {
                // Skip rendering this block if there's an error
            }

            poseStack.popPose();
        }
    }

    private static int getRotationSteps(Direction from, Direction to) {
        return (to.get2DDataValue() - from.get2DDataValue() + 4) % 4;
    }

    private static BlockPos rotatePositionSteps(BlockPos pos, int steps) {
        BlockPos result = pos;
        for (int i = 0; i < steps; i++) {
            // (x, z) -> (-z, x)
            result = new BlockPos(-result.getZ(), result.getY(), result.getX());
        }
        return result;
    }

    private static BlockState rotateBlockStateSteps(BlockState state, int steps) {
        BlockState result = state;
        for (int i = 0; i < steps; i++) {
            result = rotateBlockStateOnce(result);
        }
        return result;
    }

    private static BlockState rotateBlockStateOnce(BlockState state) {
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

    private static double calculateOptimalDistance(SchematicData schematic) {
        BlockPos size = schematic.getSize();
        int maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

        double distance = 4.0 + (maxDimension / 2.0);
        return Math.min(15.0, Math.max(4.0, distance));
    }

    public static BlockPos getTargetPlacementPos(Minecraft minecraft, double distance) {
        double raycastDistance = Math.max(10.0, distance + 5.0);
        HitResult hitResult = minecraft.player.pick(raycastDistance, minecraft.getFrameTime(), false);

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }

        Vec3 eyePos = minecraft.player.getEyePosition();
        Vec3 lookVec = minecraft.player.getLookAngle();
        Vec3 targetVec = eyePos.add(lookVec.scale(distance));

        return new BlockPos(
                (int) Math.floor(targetVec.x),
                (int) Math.floor(targetVec.y),
                (int) Math.floor(targetVec.z)
        );
    }

    private static Direction getPlayerHorizontalFacing(net.minecraft.world.entity.player.Player player) {
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
}