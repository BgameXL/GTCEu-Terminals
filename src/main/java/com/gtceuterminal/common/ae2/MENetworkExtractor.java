package com.gtceuterminal.common.ae2;

import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Extractor for removing items from Applied Energistics 2
 * uses reflection to avoid hard dependency on AE2
 * It works now!
 */
public class MENetworkExtractor {

    public static long extractFromMENetwork(Player player, Level level, BlockPos nodePos, Item item, long amount) {
        if (!MENetworkScanner.isAE2Available()) {
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
            
            ItemStack minecraftStack = new ItemStack(item, (int) Math.min(amount, Integer.MAX_VALUE));
            Object aeStack = aeItemStackFactoryClass.getMethod("fromItemStack", ItemStack.class)
                .invoke(null, minecraftStack);
            
            if (aeStack == null) return 0;
            
            aeItemStackClass.getMethod("setStackSize", long.class).invoke(aeStack, amount);
            
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Object modulateAction = actionableClass.getField("MODULATE").get(null);
            
            Class<?> baseActionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Class<?> machineSourceClass = Class.forName("appeng.me.helpers.MachineSource");
            Object actionSource = machineSourceClass.getConstructor(Object.class).newInstance(be);
            
            Class<?> inventoryClass = Class.forName("appeng.api.storage.IMEInventory");
            
            Object result = inventoryClass.getMethod("extractItems", aeItemStackClass, actionableClass, baseActionSourceClass)
                .invoke(inventory, aeStack, modulateAction, actionSource);
            
            if (result == null) {
                GTCEUTerminalMod.LOGGER.warn("Failed to extract {} x{} from ME network", item, amount);
                return 0;
            }
            
            long extracted = (long) aeItemStackClass.getMethod("getStackSize").invoke(result);
            
            if (extracted > 0) {
                ItemStack extractedStack = new ItemStack(item, (int) Math.min(extracted, 64));
                if (!player.getInventory().add(extractedStack)) {
                    player.drop(extractedStack, false);
                }
                
                long remainder = extracted - 64;
                while (remainder > 0) {
                    int stackSize = (int) Math.min(remainder, 64);
                    ItemStack remainderStack = new ItemStack(item, stackSize);
                    if (!player.getInventory().add(remainderStack)) {
                        player.drop(remainderStack, false);
                    }
                    remainder -= stackSize;
                }
            }
            
            // GTCEUTerminalMod.LOGGER.info("Extracted {} x{} from ME network at {}", item, extracted, nodePos);
            return extracted;
            
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error extracting items from ME network", e);
            return 0;
        }
    }

     // Extract items from any nearby ME network
    public static long extractFromNearbyMENetwork(Player player, Level level, Item item, long amount, int radius) {
        if (!MENetworkScanner.isAE2Available()) {
            return 0;
        }
        
        var nodes = MENetworkScanner.findNearbyMENodes(player, level, radius);
        
        for (BlockPos nodePos : nodes) {
            long extracted = extractFromMENetwork(player, level, nodePos, item, amount);
            if (extracted > 0) {
                return extracted;
            }
        }
        
        return 0;
    }
}