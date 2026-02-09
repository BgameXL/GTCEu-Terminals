package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

// Component Registry for GTCEu Terminal
public class ComponentRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/gtceuterminal/components.json");
    
    // Component categories
    public enum ComponentCategory {
        ENERGY_HATCH("Energy Hatches"),
        FLUID_HATCH("Fluid Hatches"),
        ITEM_HATCH("Item Hatches"),
        MAINTENANCE_HATCH("Maintenance Hatches"),
        MUFFLER_HATCH("Muffler Hatches"),
        LASER_HATCH("Laser Hatches"),
        ROTOR_HOLDER("Rotor Holders"),
        CASING("Casings"),
        COIL("Heating Coils"),
        OTHER("Other Components");

        private final String displayName;

        ComponentCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Configure component patterns for each category
    private static final Map<ComponentCategory, Set<String>> customComponents = new EnumMap<>(ComponentCategory.class);
    
    // Block matching cache for performance
    private static final Map<String, Set<ResourceLocation>> wildcardCache = new HashMap<>();
    
    // Flag for unsaved changes
    private static boolean isDirty = false;

    static {
        for (ComponentCategory category : ComponentCategory.values()) {
            customComponents.put(category, new HashSet<>());
        }
    }

    // Initialize registry, load config or create default if not exists
    public static void init() {
        if (CONFIG_FILE.exists()) {
            loadConfig();
        } else {
            createDefaultConfig();
            saveConfig();
        }
        
        GTCEUTerminalMod.LOGGER.info("Component Registry initialized with {} custom entries",
                customComponents.values().stream().mapToInt(Set::size).sum());
    }

    private static void createDefaultConfig() {
        GTCEUTerminalMod.LOGGER.info("Creating default component configuration...");

        // GTMThings wireless energy hatches
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:*_wireless_energy_input_hatch");
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:*_wireless_energy_output_hatch");
        
        // GTMThings high-tier components (UEV-MAX)
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:uev_*_energy_input_hatch");
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:uiv_*_energy_input_hatch");
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:uxv_*_energy_input_hatch");
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:opv_*_energy_input_hatch");
        addPattern(ComponentCategory.ENERGY_HATCH, "gtmthings:max_*_energy_input_hatch");
        
        // GT Community Additions
        addPattern(ComponentCategory.ENERGY_HATCH, "gtcommunityadd:*_energy_input_hatch");
        addPattern(ComponentCategory.FLUID_HATCH, "gtcommunityadd:*_fluid_input_hatch");

        // addPattern(ComponentCategory.ENERGY_HATCH, "kubejs:custom_energy_hatch");
        
        isDirty = true;
    }

   // Adds a new pattern to a category and marks config as dirty
    public static void addPattern(ComponentCategory category, String pattern) {
        customComponents.get(category).add(pattern);
        wildcardCache.clear(); // Invalidar cache
        isDirty = true;
    }

    public static void removePattern(ComponentCategory category, String pattern) {
        customComponents.get(category).remove(pattern);
        wildcardCache.clear();
        isDirty = true;
    }

    public static Set<String> getPatterns(ComponentCategory category) {
        return Collections.unmodifiableSet(customComponents.get(category));
    }

    // Verify if a block ID matches any pattern in a category
    public static boolean matches(ComponentCategory category, ResourceLocation blockId) {
        Set<String> patterns = customComponents.get(category);
        if (patterns.isEmpty()) return false;
        
        String blockIdStr = blockId.toString();
        
        for (String pattern : patterns) {
            if (matchesPattern(pattern, blockIdStr)) {
                return true;
            }
        }
        
        return false;
    }

    public static Set<ResourceLocation> getMatchingBlocks(ComponentCategory category) {
        Set<String> patterns = customComponents.get(category);
        if (patterns.isEmpty()) return Collections.emptySet();

        String cacheKey = category.name() + ":" + String.join(";", patterns);
        
        // Verify cache
        if (wildcardCache.containsKey(cacheKey)) {
            return wildcardCache.get(cacheKey);
        }

        Set<ResourceLocation> matches = new HashSet<>();
        
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
            if (blockId != null && matches(category, blockId)) {
                matches.add(blockId);
            }
        }

        wildcardCache.put(cacheKey, matches);
        
        GTCEUTerminalMod.LOGGER.debug("Found {} blocks matching patterns for category {}",
                matches.size(), category.name());
        
        return matches;
    }

    public static List<ItemStack> getMatchingItemStacks(ComponentCategory category) {
        Set<ResourceLocation> blockIds = getMatchingBlocks(category);
        List<ItemStack> stacks = new ArrayList<>();
        
        for (ResourceLocation blockId : blockIds) {
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block != null) {
                Item item = block.asItem();
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    stacks.add(new ItemStack(item));
                }
            }
        }
        
        return stacks;
    }

    private static boolean matchesPattern(String pattern, String text) {
        // Sin wildcards, comparación directa
        if (!pattern.contains("*")) {
            return pattern.equals(text);
        }
        
        // Convertir wildcards a regex
        // Escapar caracteres especiales de regex excepto *
        String regex = pattern
                .replace(".", "\\.")
                .replace(":", "\\:")
                .replace("*", ".*");
        
        // ^ y $ para matchear toda la string
        regex = "^" + regex + "$";
        
        try {
            return Pattern.matches(regex, text);
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Invalid pattern: {}", pattern, e);
            return false;
        }
    }

    public static void saveConfig() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            
            JsonObject root = new JsonObject();
            root.addProperty("_comment", "GTCEu Terminal - Custom Component Configuration");
            root.addProperty("_description", "Add custom blocks from addons and KubeJS here. Supports wildcards (*).");
            root.addProperty("_examples", "gtmthings:*_wireless_energy_input_hatch matches all wireless energy hatches");
            
            JsonObject categories = new JsonObject();
            
            for (Map.Entry<ComponentCategory, Set<String>> entry : customComponents.entrySet()) {
                ComponentCategory category = entry.getKey();
                Set<String> patterns = entry.getValue();
                
                JsonArray array = new JsonArray();
                for (String pattern : patterns) {
                    array.add(pattern);
                }
                
                categories.add(category.name().toLowerCase(), array);
            }
            
            root.add("components", categories);
            
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(root, writer);
            }
            
            isDirty = false;
            GTCEUTerminalMod.LOGGER.info("Component configuration saved to {}", CONFIG_FILE.getPath());
            
        } catch (IOException e) {
            GTCEUTerminalMod.LOGGER.error("Failed to save component configuration", e);
        }
    }

    public static void loadConfig() {
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            
            if (!root.has("components")) {
                GTCEUTerminalMod.LOGGER.warn("Invalid config file format, creating new one");
                createDefaultConfig();
                saveConfig();
                return;
            }
            
            JsonObject components = root.getAsJsonObject("components");
            
            // Limpiar configuración actual
            for (Set<String> patterns : customComponents.values()) {
                patterns.clear();
            }
            
            // Cargar patrones
            for (ComponentCategory category : ComponentCategory.values()) {
                String key = category.name().toLowerCase();
                if (components.has(key)) {
                    JsonArray array = components.getAsJsonArray(key);
                    for (int i = 0; i < array.size(); i++) {
                        String pattern = array.get(i).getAsString();
                        customComponents.get(category).add(pattern);
                    }
                }
            }
            
            wildcardCache.clear();
            isDirty = false;
            
            GTCEUTerminalMod.LOGGER.info("Component configuration loaded from {}", CONFIG_FILE.getPath());
            
        } catch (IOException e) {
            GTCEUTerminalMod.LOGGER.error("Failed to load component configuration, using defaults", e);
            createDefaultConfig();
            saveConfig();
        }
    }

    public static void reload() {
        GTCEUTerminalMod.LOGGER.info("Reloading component configuration...");
        loadConfig();
    }

    public static boolean isDirty() {
        return isDirty;
    }

    public static String getStats() {
        int totalPatterns = customComponents.values().stream().mapToInt(Set::size).sum();
        int totalBlocks = customComponents.keySet().stream()
                .mapToInt(cat -> getMatchingBlocks(cat).size())
                .sum();
        
        return String.format("Patterns: %d, Matched Blocks: %d", totalPatterns, totalBlocks);
    }

    public static Map<ComponentCategory, Integer> getSummary() {
        Map<ComponentCategory, Integer> summary = new EnumMap<>(ComponentCategory.class);
        
        for (ComponentCategory category : ComponentCategory.values()) {
            int count = getMatchingBlocks(category).size();
            summary.put(category, count);
        }
        
        return summary;
    }
}