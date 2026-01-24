package com.gtceuterminal.common.ae2;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Handles wireless terminal connection to ME Network via Wireless Access Point
 * Works exactly like AE2's Wireless Terminal
 */
public class WirelessTerminalHandler {

    private static final String TAG_ACCESS_POINT_POS = "accessPoint";

    // Check if the item is linked to a Wireless Access Point
    public static boolean isLinked(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG_ACCESS_POINT_POS);
    }

    // Get the linked grid if available and in range
    @Nullable
    public static IGrid getLinkedGrid(ItemStack stack, Level level, Player player) {
        if (!isLinked(stack)) {
            return null;
        }

        // Get the linked position
        GlobalPos globalPos = getLinkedPosition(stack);
        if (globalPos == null) {
            return null;
        }

        // Check if we're in the right dimension
        if (!level.dimension().equals(globalPos.dimension())) {
            return null;
        }

        // Get the Access Point
        BlockEntity be = level.getBlockEntity(globalPos.pos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            return null;
        }

        // Check if it's active
        if (!accessPoint.isActive()) {
            return null;
        }

        // Check range
        double range = accessPoint.getRange();
        BlockPos playerPos = player.blockPosition();
        double distanceSq = globalPos.pos().distSqr(playerPos);

        if (distanceSq > (range * range)) {
            return null;
        }

        // Get the grid (security is handled by AE2 internally)
        IGrid grid = accessPoint.getGrid();

        return grid;
    }

    // Check if player is in range of the linked Access Point
    public static boolean isInRange(ItemStack stack, Level level, Player player) {
        if (!isLinked(stack)) {
            return false;
        }

        GlobalPos globalPos = getLinkedPosition(stack);
        if (globalPos == null) {
            return false;
        }

        if (!level.dimension().equals(globalPos.dimension())) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(globalPos.pos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            return false;
        }

        if (!accessPoint.isActive()) {
            return false;
        }

        double range = accessPoint.getRange();
        BlockPos playerPos = player.blockPosition();
        double distanceSq = globalPos.pos().distSqr(playerPos);

        return distanceSq <= (range * range);
    }

    // Get the linked position from NBT
    @Nullable
    private static GlobalPos getLinkedPosition(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ACCESS_POINT_POS)) {
            return null;
        }

        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ACCESS_POINT_POS))
                .result()
                .orElse(null);
    }
}