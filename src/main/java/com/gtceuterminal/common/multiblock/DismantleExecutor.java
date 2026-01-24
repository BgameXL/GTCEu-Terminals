package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Execute the dismantling of the multiblock
public class DismantleExecutor {


    // Dismantle the multiblock and give items to the player
    public static boolean dismantleMultiblock(ServerLevel level,
                                              ServerPlayer player,
                                              MultiblockControllerMachine controller) {
        DismantleScanner.ScanResult scanResult = DismantleScanner.scanMultiblock(level, controller);

        // Build EXACT refunds (including NBT) BEFORE breaking blocks
        List<ItemStack> items = new ArrayList<>();
        BlockPos controllerPos = controller.getPos();

        // 1) First everything except the controller (avoids invalidating early state)
        for (BlockPos pos : scanResult.getAllBlocks()) {
            if (pos.equals(controllerPos)) continue;
            ItemStack refund = createRefundStack(level, pos);
            mergeInto(items, refund);
        }

        // 2) Then the controller
        ItemStack controllerRefund = createRefundStack(level, controllerPos);
        mergeInto(items, controllerRefund);

        // Break all blocks (without drops), controller at the end
        for (BlockPos pos : scanResult.getAllBlocks()) {
            if (pos.equals(controllerPos)) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(controllerPos, Blocks.AIR.defaultBlockState(), 3);

        // Give items to the player
        for (ItemStack stack : items) {
            if (!player.getInventory().add(stack)) {
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        stack.copy()
                );
                level.addFreshEntity(itemEntity);
            }
        }

        return true;
    }

    /**
     * Creates the ItemStack to be refunded for a specific position, attempting to preserve the BlockEntity's NBT (covers, upgrades, configs, etc.).
     * Note: If a block has no associated item (asItem() == AIR), it cannot be refunded as a stack.
     */
    private static ItemStack createRefundStack(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return ItemStack.EMPTY;

        if (state.getBlock().asItem() == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty()) return ItemStack.EMPTY;

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            try {
                // This saves BlockEntityTag to the item which makes it return with NBT when placed
                be.saveToItem(stack);
            } catch (Throwable ignored) {}
        }
        return stack;
    }


    // Combines identical stacks (same item + same NBT) respecting maxStackSize.
    private static void mergeInto(List<ItemStack> out, ItemStack in) {
        if (in == null || in.isEmpty()) return;

        while (!in.isEmpty()) {
            boolean merged = false;

            for (ItemStack existing : out) {
                if (!existing.isEmpty()
                        && sameItemAndTag(existing, in)
                        && existing.getCount() < existing.getMaxStackSize()) {

                    int canMove = existing.getMaxStackSize() - existing.getCount();
                    int toMove = Math.min(canMove, in.getCount());

                    existing.grow(toMove);
                    in.shrink(toMove);

                    merged = true;
                    if (in.isEmpty()) return;
                }
            }

            if (!merged) {
                int take = Math.min(in.getMaxStackSize(), in.getCount());
                ItemStack part = in.copy();
                part.setCount(take);
                out.add(part);
                in.shrink(take);
            }
        }
    }

    private static boolean sameItemAndTag(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        return Objects.equals(a.getTag(), b.getTag());
    }


    // Calculate available space in the player's inventory
    public static int getAvailableInventorySlots(ServerPlayer player) {
        int emptySlots = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }
}