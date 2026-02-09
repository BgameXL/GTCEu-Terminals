package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.GTValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Substation Hatch configuration
 * Substation hatches are high-amperage energy hatches (64A, 256A, etc.)
 */
public class SubstationHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubstationHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "substation_hatches.json";
    
    private static List<SubstationHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class SubstationHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String hatchType;     // "INPUT" or "OUTPUT"
        public String amperage;      // "64A", "256A"
        
        public SubstationHatchEntry() {}
        
        public SubstationHatchEntry(String blockId, String displayName, String tierName, 
                                    int tier, String hatchType, String amperage) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.hatchType = hatchType;
            this.amperage = amperage;
        }
    }

    public static class SubstationHatchPattern {
        public String blockIdPattern;
        public String hatchType;     // "INPUT" or "OUTPUT"
        public String amperage;
        public Integer minTier;
        public Integer maxTier;
        
        public SubstationHatchPattern() {}
        
        public SubstationHatchPattern(String blockIdPattern, String hatchType, String amperage) {
            this.blockIdPattern = blockIdPattern;
            this.hatchType = hatchType;
            this.amperage = amperage;
        }
    }

    private static class SubstationHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Substation Hatch Configuration";
        public List<SubstationHatchEntry> hatches = new ArrayList<>();
        public List<SubstationHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing substation hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Substation hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        LOGGER.info("Substation hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        SubstationHatchConfiguration config = new SubstationHatchConfiguration();
        
        // Substation Input Hatches (64A, 256A)
        SubstationHatchPattern input64a = new SubstationHatchPattern(
            "gtceu:*_substation_input_hatch_64a", 
            "INPUT", 
            "64A"
        );
        config.patterns.add(input64a);
        
        SubstationHatchPattern input256a = new SubstationHatchPattern(
            "gtceu:*_substation_input_hatch_256a", 
            "INPUT", 
            "256A"
        );
        config.patterns.add(input256a);
        
        // Substation Output Hatches (64A, 256A)
        SubstationHatchPattern output64a = new SubstationHatchPattern(
            "gtceu:*_substation_output_hatch_64a", 
            "OUTPUT", 
            "64A"
        );
        config.patterns.add(output64a);
        
        SubstationHatchPattern output256a = new SubstationHatchPattern(
            "gtceu:*_substation_output_hatch_256a", 
            "OUTPUT", 
            "256A"
        );
        config.patterns.add(output256a);
        
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default substation hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            SubstationHatchConfiguration config = GSON.fromJson(json, SubstationHatchConfiguration.class);
            
            allHatches.clear();
            
            if (config.patterns != null) {
                for (SubstationHatchPattern pattern : config.patterns) {
                    allHatches.addAll(expandPattern(pattern));
                }
            }
            
            if (config.hatches != null) {
                allHatches.addAll(config.hatches);
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
            createHardcodedDefaults();
        }
    }

    private static List<SubstationHatchEntry> expandPattern(SubstationHatchPattern pattern) {
        List<SubstationHatchEntry> entries = new ArrayList<>();
        
        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;
        
        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();
            
            // Replace wildcard with tier
            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            
            // Generate display name
            String displayName = tierName + " " + pattern.amperage + " Substation " + 
                               (pattern.hatchType.equals("INPUT") ? "Input" : "Output") + " Energy";
            
            entries.add(new SubstationHatchEntry(
                blockId, 
                displayName, 
                tierName, 
                tier, 
                pattern.hatchType, 
                pattern.amperage
            ));
        }
        
        return entries;
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();
        
        // Fallback: Generate 64A input hatches for all tiers
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();
            
            allHatches.add(new SubstationHatchEntry(
                "gtceu:" + tierLower + "_substation_input_hatch_64a",
                tierName + " 64A Substation Input Energy",
                tierName,
                tier,
                "INPUT",
                "64A"
            ));
        }
    }

    public static List<SubstationHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static SubstationHatchEntry getHatchForTier(String hatchType, int tier) {
        if (!initialized) initialize();
        
        return allHatches.stream()
            .filter(h -> h.tier == tier)
            .filter(h -> h.hatchType.equals(hatchType))
            .findFirst()
            .orElse(null);
    }

    public static List<SubstationHatchEntry> getHatchesByType(String hatchType) {
        if (!initialized) initialize();
        
        return allHatches.stream()
            .filter(h -> h.hatchType.equals(hatchType))
            .toList();
    }

    public static void reload() {
        LOGGER.info("Reloading substation hatch configuration");
        initialized = false;
        initialize();
    }
}