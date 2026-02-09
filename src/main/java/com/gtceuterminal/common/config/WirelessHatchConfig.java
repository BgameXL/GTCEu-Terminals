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
 * Wireless Hatch configuration for GTMThings addon
 * Supports wireless energy and wireless laser hatches
 */
public class WirelessHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(WirelessHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "wireless_hatches.json";
    
    private static List<WirelessHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class WirelessHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String hatchType;     // "ENERGY_INPUT", "ENERGY_OUTPUT", "LASER_INPUT", "LASER_OUTPUT"
        public String amperage;      // "2A", "256A", etc.
        
        public WirelessHatchEntry() {}
        
        public WirelessHatchEntry(String blockId, String displayName, String tierName, 
                                 int tier, String hatchType, String amperage) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.hatchType = hatchType;
            this.amperage = amperage;
        }
    }

    public static class WirelessHatchPattern {
        public String blockIdPattern;
        public String componentType;
        public String amperage;
        public Integer minTier;      // Laser starts at IV (tier 5)
        public Integer maxTier;
        
        public WirelessHatchPattern() {}
        
        public WirelessHatchPattern(String blockIdPattern, String componentType, String amperage) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
            this.amperage = amperage;
        }
    }

    private static class WirelessHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Wireless Hatch Configuration (GTMThings)";
        public List<WirelessHatchEntry> hatches = new ArrayList<>();
        public List<WirelessHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing wireless hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Wireless hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        LOGGER.info("Wireless hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        WirelessHatchConfiguration config = new WirelessHatchConfiguration();
        
        // Wireless Energy Hatches (all tiers)
        WirelessHatchPattern energyInput = new WirelessHatchPattern(
            "gtmthings:*_2a_wireless_energy_input_hatch", 
            "WIRELESS_ENERGY_INPUT", 
            "2A"
        );
        config.patterns.add(energyInput);
        
        WirelessHatchPattern energyOutput = new WirelessHatchPattern(
            "gtmthings:*_2a_wireless_energy_output_hatch", 
            "WIRELESS_ENERGY_OUTPUT", 
            "2A"
        );
        config.patterns.add(energyOutput);
        
        // Wireless Laser Hatches (IV+ only, tier 5+)
        WirelessHatchPattern laserInput = new WirelessHatchPattern(
            "gtmthings:*_256a_wireless_laser_target_hatch", 
            "WIRELESS_LASER_INPUT", 
            "256A"
        );
        laserInput.minTier = 5; // IV and above
        config.patterns.add(laserInput);
        
        WirelessHatchPattern laserOutput = new WirelessHatchPattern(
            "gtmthings:*_256a_wireless_laser_source_hatch", 
            "WIRELESS_LASER_OUTPUT", 
            "256A"
        );
        laserOutput.minTier = 5; // IV and above
        config.patterns.add(laserOutput);
        
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default wireless hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            WirelessHatchConfiguration config = GSON.fromJson(json, WirelessHatchConfiguration.class);
            
            allHatches.clear();
            
            if (config.patterns != null) {
                for (WirelessHatchPattern pattern : config.patterns) {
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

    private static List<WirelessHatchEntry> expandPattern(WirelessHatchPattern pattern) {
        List<WirelessHatchEntry> entries = new ArrayList<>();
        
        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;
        
        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();
            
            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            String displayName = generateDisplayName(tierName, pattern.componentType, pattern.amperage);
            
            entries.add(new WirelessHatchEntry(
                blockId, 
                displayName, 
                tierName, 
                tier, 
                pattern.componentType, 
                pattern.amperage
            ));
        }
        
        return entries;
    }

    private static String generateDisplayName(String tierName, String componentType, String amperage) {
        String prefix = tierName;
        String suffix = "";
        
        if (amperage != null && !amperage.isEmpty()) {
            suffix = " " + amperage;
        }
        
        return switch (componentType) {
            case "WIRELESS_ENERGY_INPUT" -> prefix + suffix + " Wireless Energy Input Hatch";
            case "WIRELESS_ENERGY_OUTPUT" -> prefix + suffix + " Wireless Energy Output Hatch";
            case "WIRELESS_LASER_INPUT" -> prefix + suffix + " Wireless Laser Target Hatch";
            case "WIRELESS_LASER_OUTPUT" -> prefix + suffix + " Wireless Laser Source Hatch";
            default -> prefix + " Wireless Hatch";
        };
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();
        
        // Fallback: Generate wireless energy input hatches for all tiers
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();
            
            allHatches.add(new WirelessHatchEntry(
                "gtmthings:" + tierLower + "_2a_wireless_energy_input_hatch",
                tierName + " 2A Wireless Energy Input Hatch",
                tierName,
                tier,
                "WIRELESS_ENERGY_INPUT",
                "2A"
            ));
        }
    }

    public static List<WirelessHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static WirelessHatchEntry getHatchForTier(String hatchType, int tier) {
        if (!initialized) initialize();
        
        return allHatches.stream()
            .filter(h -> h.tier == tier)
            .filter(h -> h.hatchType.equals(hatchType))
            .findFirst()
            .orElse(null);
    }

    public static List<WirelessHatchEntry> getHatchesByType(String hatchType) {
        if (!initialized) initialize();
        
        return allHatches.stream()
            .filter(h -> h.hatchType.equals(hatchType))
            .toList();
    }

    public static void reload() {
        LOGGER.info("Reloading wireless hatch configuration");
        initialized = false;
        initialize();
    }
}