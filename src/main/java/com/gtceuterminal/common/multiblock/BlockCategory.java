package com.gtceuterminal.common.multiblock;

import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class BlockCategory {
    
    public enum Category {
        COILS("Heating Coils"),
        HATCHES("Hatches"),
        BUSES("Buses"),
        MAINTENANCE("Maintenance Hatches"),
        MUFFLERS("Mufflers"),
        ENERGY("Energy Hatches"),
        LASER_ENERGY("Laser Hatches"),
        SUBSTATION_ENERGY("Substation Hatches"),
        DYNAMO_ENERGY("Dynamo Hatches"),
        PIPES("Pipes");
        
        private final String displayName;
        
        Category(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static Category categorize(BlockState state) {
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        
        // Check for coil_block or just coil
        if ((path.contains("coil_block") || path.contains("coil")) && !path.contains("fusion")) {
            return Category.COILS;
        }
        
        if (path.contains("maintenance_hatch")) {
            return Category.MAINTENANCE;
        }
        
        if (path.contains("muffler")) {
            return Category.MUFFLERS;
        }
        
        if (path.contains("energy") && path.contains("hatch")) {
            if (path.contains("laser")) {
                return Category.LASER_ENERGY;
            } else if (path.contains("substation")) {
                return Category.SUBSTATION_ENERGY;
            } else if (path.contains("dynamo")) {
                return Category.DYNAMO_ENERGY;
            }
            return Category.ENERGY;
        }
        
        if (path.contains("hatch")) {
            return Category.HATCHES;
        }
        
        if (path.contains("bus")) {
            return Category.BUSES;
        }
        
        if (path.contains("pipe") || path.contains("cable")) {
            return Category.PIPES;
        }
        
        return Category.HATCHES;
    }
    
    public static Map<Category, List<BlockState>> groupByCategory(Set<BlockState> blocks) {
        Map<Category, List<BlockState>> grouped = new EnumMap<>(Category.class);
        
        for (BlockState block : blocks) {
            Category category = categorize(block);
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(block);
        }
        
        for (List<BlockState> blockList : grouped.values()) {
            blockList.sort((a, b) -> {
                String pathA = a.getBlock().builtInRegistryHolder().key().location().getPath();
                String pathB = b.getBlock().builtInRegistryHolder().key().location().getPath();
                return Integer.compare(TierProgression.getTierIndex(pathA), 
                                      TierProgression.getTierIndex(pathB));
            });
        }
        
        return grouped;
    }
    
    public static boolean isUpgradeable(BlockState state) {
        String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
        if (!namespace.equals("gtceu")) {
            return false;
        }
        
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        
        if ((path.contains("coil_block") || path.contains("coil")) && !path.contains("fusion")) {
            return true;
        }
        
        if (path.contains("maintenance_hatch")) {
            return true;
        }
        
        if (path.contains("muffler")) {
            return true;
        }
        
        if (path.contains("energy") && path.contains("hatch")) {
            return true;
        }
        
        if (path.contains("fluid_hatch") || path.contains("fluid_bus")) {
            return true;
        }
        
        if (path.contains("input_hatch") || path.contains("output_hatch") || 
            path.contains("input_bus") || path.contains("output_bus")) {
            return !path.contains("me_") && !path.contains("optical_");
        }
        
        return false;
    }
    
    public static List<BlockState> getBlocksInCategory(Category category, BlockState example, int maxTierIndex) {
        List<BlockState> blocks = new ArrayList<>();
        
        String examplePath = example.getBlock().builtInRegistryHolder().key().location().getPath();
        List<String> tierPaths = TierProgression.getAllTiers(examplePath);
        
        if (!tierPaths.isEmpty()) {
            for (String tierPath : tierPaths) {
                if (tierPath.equals(examplePath)) continue;
                
                // Skip tiers above max
                if (category != Category.COILS && TierProgression.getTierIndex(tierPath) > maxTierIndex) continue;
                
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(block -> {
                        String namespace = block.builtInRegistryHolder().key().location().getNamespace();
                        String path = block.builtInRegistryHolder().key().location().getPath();
                        return namespace.equals("gtceu") && path.contains(tierPath);
                    })
                    .findFirst()
                    .ifPresent(block -> blocks.add(block.defaultBlockState()));
            }
        } else {
            boolean isInput = examplePath.contains("input");
            boolean isFluid = examplePath.contains("fluid");
            
            net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> {
                    String namespace = block.builtInRegistryHolder().key().location().getNamespace();
                    if (!namespace.equals("gtceu")) return false;
                    
                    String path = block.builtInRegistryHolder().key().location().getPath();
                    
                    if (path.equals(examplePath)) return false;
                    
                    // Check tier limit
                    if (category != Category.COILS && TierProgression.getTierIndex(path) > maxTierIndex) return false;
                    
                    switch (category) {
                        case COILS:
                            return (path.contains("coil_block") || path.contains("coil")) && !path.contains("fusion");
                        case MAINTENANCE:
                            return path.contains("maintenance_hatch");
                        case HATCHES:
                            return path.contains("hatch") && !path.contains("energy") && !path.contains("maintenance")
                                && (path.contains("input") == isInput)
                                && (path.contains("fluid") == isFluid);
                        case BUSES:
                            return path.contains("bus") && (path.contains("input") == isInput);
                        case MUFFLERS:
                            return path.contains("muffler");
                        case ENERGY:
                            return path.contains("energy") && path.contains("hatch") 
                                && !path.contains("laser") && !path.contains("substation") && !path.contains("dynamo");
                        case LASER_ENERGY:
                            return path.contains("laser") && path.contains("energy");
                        case SUBSTATION_ENERGY:
                            return path.contains("substation") && path.contains("energy");
                        case DYNAMO_ENERGY:
                            return path.contains("dynamo") && path.contains("energy");
                        default:
                            return false;
                    }
                })
                .limit(20)
                .forEach(block -> blocks.add(block.defaultBlockState()));
        }
        
        // Sort by tier index
        blocks.sort((a, b) -> {
            String pathA = a.getBlock().builtInRegistryHolder().key().location().getPath();
            String pathB = b.getBlock().builtInRegistryHolder().key().location().getPath();
            return Integer.compare(TierProgression.getTierIndex(pathA), 
                                  TierProgression.getTierIndex(pathB));
        });
        
        return blocks;
    }
    
    public static String getDisplayName(BlockState state) {
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        
        String[] parts = path.split("_");
        StringBuilder name = new StringBuilder();
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (isTierName(part)) {
                    name.append(part.toUpperCase());
                } else {
                    name.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
                }
                name.append(" ");
            }
        }
        
        return name.toString().trim();
    }
    
    private static boolean isTierName(String part) {
        return part.equals("ulv") || part.equals("lv") || part.equals("mv") || 
               part.equals("hv") || part.equals("ev") || part.equals("iv") || 
               part.equals("luv") || part.equals("zpm") || part.equals("uv") || 
               part.equals("uhv");
    }
    
    public static String getTierFromPath(String path) {
        if (path.contains("ulv")) return "ulv";
        if (path.contains("lv")) return "lv";
        if (path.contains("mv")) return "mv";
        if (path.contains("hv")) return "hv";
        if (path.contains("ev")) return "ev";
        if (path.contains("iv")) return "iv";
        if (path.contains("luv")) return "luv";
        if (path.contains("zpm")) return "zpm";
        if (path.contains("uv")) return "uv";
        if (path.contains("uhv")) return "uhv";
        return "";
    }
}