package com.gtceuterminal.common.ae2;

import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Scanner for Applied Energistics 2 ME Networks
 * Uses reflection to avoid hard dependency on AE2
 * it works now!
 */
public class MENetworkScanner {
    
    private static boolean ae2Available = false;
    private static boolean ae2Checked = false;

    public static boolean isAE2Available() {
        if (!ae2Checked) {
            try {
                Class.forName("appeng.api.networking.IGrid");
                ae2Available = true;
                GTCEUTerminalMod.LOGGER.info("AE2 detected - ME Network integration enabled");
            } catch (ClassNotFoundException e) {
                ae2Available = false;
                GTCEUTerminalMod.LOGGER.info("AE2 not detected - ME Network integration disabled");
            }
            ae2Checked = true;
        }
        return ae2Available;
    }

    public static List<BlockPos> findNearbyMENodes(Player player, Level level, int radius) {
        List<BlockPos> nodes = new ArrayList<>();
        
        if (!isAE2Available()) {
            return nodes;
        }
        
        BlockPos playerPos = player.blockPosition();
        
        try {

            // Scan area for ME network blocks
            for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-radius, -radius, -radius),
                playerPos.offset(radius, radius, radius)
            )) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null && isMENetworkBlock(be)) {
                    nodes.add(pos.immutable());
                }
            }
            
            GTCEUTerminalMod.LOGGER.debug("Found {} ME network nodes near player", nodes.size());
            
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error scanning for ME networks", e);
        }
        
        return nodes;
    }

    private static boolean isMENetworkBlock(BlockEntity be) {
        try {
            Class<?> gridHostClass = Class.forName("appeng.api.networking.IGridHost");
            return gridHostClass.isInstance(be);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static long countItemInMENetwork(Player player, Level level, BlockPos nodePos, Item item) {
        if (!isAE2Available()) {
            return 0;
        }
        
        try {
            BlockEntity be = level.getBlockEntity(nodePos);
            if (be == null) return 0;
            
            Class<?> gridHostClass = Class.forName("appeng.api.networking.IGridHost");
            if (!gridHostClass.isInstance(be)) {
                return 0;
            }
            
            Object gridHost = gridHostClass.cast(be);
            Object gridNode = gridHostClass.getMethod("getGridNode").invoke(gridHost);
            
            if (gridNode == null) return 0;
            
            Class<?> gridNodeClass = Class.forName("appeng.api.networking.IGridNode");
            Object grid = gridNodeClass.getMethod("getGrid").invoke(gridNode);
            
            if (grid == null) return 0;
            
            Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
            Class<?> storageGridClass = Class.forName("appeng.api.networking.storage.IStorageGrid");
            Object storageGrid = gridClass.getMethod("getService", Class.class)
                .invoke(grid, storageGridClass);
            
            if (storageGrid == null) return 0;
            
            Object inventory = storageGridClass.getMethod("getInventory").invoke(storageGrid);
            
            if (inventory == null) return 0;
            
            Class<?> aeItemStackClass = Class.forName("appeng.api.storage.data.IAEItemStack");
            Class<?> aeItemStackFactoryClass = Class.forName("appeng.util.item.AEItemStack");
            
            ItemStack minecraftStack = new ItemStack(item);
            Object aeStack = aeItemStackFactoryClass.getMethod("fromItemStack", ItemStack.class)
                .invoke(null, minecraftStack);
            
            if (aeStack == null) return 0;
            
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Object simulateAction = actionableClass.getField("SIMULATE").get(null);
            
            Class<?> baseActionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Class<?> machineSourceClass = Class.forName("appeng.me.helpers.MachineSource");
            Object actionSource = machineSourceClass.getConstructor(Object.class).newInstance(be);
            
            Class<?> inventoryClass = Class.forName("appeng.api.storage.IMEInventory");
            
            Object result = inventoryClass.getMethod("extractItems", aeItemStackClass, actionableClass, baseActionSourceClass)
                .invoke(inventory, aeStack, simulateAction, actionSource);
            
            if (result == null) return 0;
            
            long count = (long) aeItemStackClass.getMethod("getStackSize").invoke(result);
            
            GTCEUTerminalMod.LOGGER.debug("Found {} of {} in ME network at {}", count, item, nodePos);
            
            return count;
            
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error counting items in ME network", e);
            return 0;
        }
    }

    public static long getTotalInMENetworks(Player player, Level level, Item item, int radius) {
        if (!isAE2Available()) {
            return 0;
        }
        
        List<BlockPos> nodes = findNearbyMENodes(player, level, radius);
        long total = 0;
        
        for (BlockPos nodePos : nodes) {
            long count = countItemInMENetwork(player, level, nodePos, item);
            if (count > 0) {
                total += count;
                break;
            }
        }
        
        return total;
    }
}