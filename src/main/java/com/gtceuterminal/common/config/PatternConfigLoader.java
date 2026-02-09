package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.multiblock.ComponentType;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


// Loader for component pattern configurations from JSON files.
public class PatternConfigLoader {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_DIR = "gtceuterminal";
    private static final String PATTERNS_FILE = "component_patterns.json";
    private static final String CUSTOM_PATTERNS_FILE = "component_patterns_custom.json";
    
    // Load default patterns from config file (creates default if not exists)
    public static void loadDefaultPatterns(List<ComponentPattern> patterns) {
        Path configPath = getConfigPath();
        File configFile = configPath.resolve(PATTERNS_FILE).toFile();
        
        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }
        
        // Load patterns from file
        loadPatternsFromFile(configFile, patterns);
    }
    
    // Load custom patterns from config file (if it exists)
    public static void loadCustomPatterns(List<ComponentPattern> patterns) {
        Path configPath = getConfigPath();
        File configFile = configPath.resolve(CUSTOM_PATTERNS_FILE).toFile();
        
        if (configFile.exists()) {
            loadPatternsFromFile(configFile, patterns);
        }
    }
    
    // Load patterns from a given file and add to the list
    private static void loadPatternsFromFile(File file, List<ComponentPattern> patterns) {
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonArray patternsArray = root.getAsJsonArray("patterns");
            
            if (patternsArray == null) {
                GTCEUTerminalMod.LOGGER.warn("No patterns found in {}", file.getName());
                return;
            }
            
            for (var element : patternsArray) {
                JsonObject patternObj = element.getAsJsonObject();
                
                try {
                    ComponentPattern pattern = parsePattern(patternObj);
                    if (pattern != null) {
                        patterns.add(pattern);
                    }
                } catch (Exception e) {
                    GTCEUTerminalMod.LOGGER.error("Failed to parse pattern: {}", patternObj, e);
                }
            }
            
            GTCEUTerminalMod.LOGGER.info("Loaded {} patterns from {}", patternsArray.size(), file.getName());
            
        } catch (IOException e) {
            GTCEUTerminalMod.LOGGER.error("Failed to load patterns from {}", file.getName(), e);
        }
    }
    
    // Parse a single pattern from JSON object
    private static ComponentPattern parsePattern(JsonObject obj) {
        String pattern = obj.get("pattern").getAsString();
        String componentTypeName = obj.get("componentType").getAsString();
        
        // Parse component type
        ComponentType componentType;
        try {
            componentType = ComponentType.valueOf(componentTypeName);
        } catch (IllegalArgumentException e) {
            GTCEUTerminalMod.LOGGER.error("Invalid component type: {}", componentTypeName);
            return null;
        }
        
        ComponentPattern result = new ComponentPattern(pattern, componentType);
        
        // Optional fields
        if (obj.has("priority")) {
            result.setPriority(obj.get("priority").getAsInt());
        }
        
        if (obj.has("displayPrefix")) {
            result.setDisplayPrefix(obj.get("displayPrefix").getAsString());
        }
        
        if (obj.has("description")) {
            result.setDescription(obj.get("description").getAsString());
        }
        
        return result;
    }
    
    // Create default config file with example patterns
    private static void createDefaultConfig(File configFile) {
        try {
            configFile.getParentFile().mkdirs();
            
            JsonObject root = new JsonObject();
            root.addProperty("version", "1.0");
            root.addProperty("_comment", "Component detection patterns - See documentation for more info");
            
            JsonArray patterns = new JsonArray();
            
            // Add default patterns
            addDefaultPatterns(patterns);
            
            root.add("patterns", patterns);
            
            // Write to file
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(root, writer);
            }
            
            GTCEUTerminalMod.LOGGER.info("Created default component patterns config");
            
        } catch (IOException e) {
            GTCEUTerminalMod.LOGGER.error("Failed to create default config", e);
        }
    }
    
    // Add hardcoded default patterns (for reference or fallback)
    private static void addDefaultPatterns(JsonArray patterns) {

        // Wireless Energy Hatches (GTMThings)
        addPattern(patterns, "gtmthings:*_wireless_energy_input_hatch", "WIRELESS_ENERGY_INPUT", 100, null, "Wireless Energy Input Hatch (all tiers)");
        addPattern(patterns, "gtmthings:*_wireless_energy_output_hatch", "WIRELESS_ENERGY_OUTPUT", 100, null, "Wireless Energy Output Hatch (all tiers)");
        
        // Wireless Laser Hatches (GTMThings)
        addPattern(patterns, "gtmthings:*_wireless_laser_target_hatch", "WIRELESS_LASER_INPUT", 100, null, "Wireless Laser Target Hatch (all tiers)");
        addPattern(patterns, "gtmthings:*_wireless_laser_source_hatch", "WIRELESS_LASER_OUTPUT", 100, null, "Wireless Laser Source Hatch (all tiers)");
        
        // Energy Hatches with Amperage
        addPattern(patterns, "*:*_energy_input_hatch_4a", "ENERGY_HATCH", 90, "4A", "4A Energy Input Hatch");
        addPattern(patterns, "*:*_energy_input_hatch_16a", "ENERGY_HATCH", 90, "16A", "16A Energy Input Hatch");
        addPattern(patterns, "*:*_energy_input_hatch_64a", "ENERGY_HATCH", 90, "64A", "64A Energy Input Hatch");
        addPattern(patterns, "*:*_energy_output_hatch_4a", "DYNAMO_HATCH", 90, "4A", "4A Dynamo Hatch");
        addPattern(patterns, "*:*_energy_output_hatch_16a", "DYNAMO_HATCH", 90, "16A", "16A Dynamo Hatch");
        addPattern(patterns, "*:*_energy_output_hatch_64a", "DYNAMO_HATCH", 90, "64A", "64A Dynamo Hatch");
        
        // Substation Hatches
        addPattern(patterns, "*:*_substation_input_hatch_*", "SUBSTATION_INPUT_ENERGY", 95, null, "Substation Input Energy");
        addPattern(patterns, "*:*_substation_output_hatch_*", "SUBSTATION_OUTPUT_ENERGY", 95, null, "Substation Output Energy");
        
        // Generic fallback patterns (low priority)
        addPattern(patterns, "*:*_energy_input_hatch", "ENERGY_HATCH", 10, null, "Generic energy input hatch");
        addPattern(patterns, "*:*_energy_output_hatch", "DYNAMO_HATCH", 10, null, "Generic dynamo hatch");
    }
    
    // Helper method to add a pattern to the JSON array
    private static void addPattern(JsonArray patterns, String pattern, String componentType, int priority, String displayPrefix, String description) {
        JsonObject obj = new JsonObject();
        obj.addProperty("pattern", pattern);
        obj.addProperty("componentType", componentType);
        obj.addProperty("priority", priority);
        
        if (displayPrefix != null) {
            obj.addProperty("displayPrefix", displayPrefix);
        }
        
        if (description != null) {
            obj.addProperty("description", description);
        }
        
        patterns.add(obj);
    }
    
    // Get the path to the config directory
    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR);
    }
}