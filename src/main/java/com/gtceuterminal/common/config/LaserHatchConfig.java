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

// Laser Hatch configuration
public class LaserHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(LaserHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "laser_hatches.json";
    
    private static List<LaserHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class LaserHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String hatchType;     // "INPUT" (target) or "OUTPUT" (source)
        public boolean wireless;
        public String amperage;

        public LaserHatchEntry(String blockId, String displayName, String tierName, int tier, String hatchType, boolean wireless, String amperage) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.hatchType = hatchType;
            this.wireless = wireless;
            this.amperage = amperage;
        }
    }

    public static class LaserHatchPattern {
        public String blockIdPattern;
        public String componentType;
        public Integer minTier;      // Usually 5 (IV) for laser hatches
        public Integer maxTier;

        public LaserHatchPattern(String blockIdPattern, String componentType, Integer minTier) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
            this.minTier = minTier;
        }
    }

    private static class LaserHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Laser Hatch Configuration";
        public List<LaserHatchEntry> hatches = new ArrayList<>();
        public List<LaserHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing laser hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Laser hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        LOGGER.info("Laser hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        LaserHatchConfiguration config = new LaserHatchConfiguration();

        // Laser hatches have different amperage variants like energy hatches
        config.patterns.add(new LaserHatchPattern("gtceu:*_laser_target_hatch", "LASER_INPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_laser_source_hatch", "LASER_OUTPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_256a_laser_target_hatch", "LASER_INPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_256a_laser_source_hatch", "LASER_OUTPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_1024a_laser_target_hatch", "LASER_INPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_1024a_laser_source_hatch", "LASER_OUTPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_4096a_laser_target_hatch", "LASER_INPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_4096a_laser_source_hatch", "LASER_OUTPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_16384a_laser_target_hatch", "LASER_INPUT", 5));
        config.patterns.add(new LaserHatchPattern("gtceu:*_16384a_laser_source_hatch", "LASER_OUTPUT", 5));
        
        // Wireless laser hatches (GTMThings)
        config.patterns.add(new LaserHatchPattern("gtmthings:*_wireless_laser_target_hatch", "WIRELESS_LASER_INPUT", null));
        config.patterns.add(new LaserHatchPattern("gtmthings:*_wireless_laser_source_hatch", "WIRELESS_LASER_OUTPUT", null));
        
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default laser hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            LaserHatchConfiguration config = GSON.fromJson(json, LaserHatchConfiguration.class);
            
            allHatches.clear();
            
            if (config.patterns != null) {
                for (LaserHatchPattern pattern : config.patterns) {
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

    private static List<LaserHatchEntry> expandPattern(LaserHatchPattern pattern) {
        List<LaserHatchEntry> entries = new ArrayList<>();

        String hatchType = pattern.componentType.contains("OUTPUT") ||
                pattern.componentType.contains("SOURCE") ? "OUTPUT" : "INPUT";
        boolean wireless = pattern.componentType.contains("WIRELESS");

        // Detected amperage from pattern (if any)
        String amperage = detectAmperageFromPattern(pattern.blockIdPattern);

        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;

        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();

            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            String displayName = generateDisplayName(tierName, pattern.componentType, wireless, amperage);

            entries.add(new LaserHatchEntry(blockId, displayName, tierName, tier, hatchType, wireless, amperage));
        }

        return entries;
    }

    private static String detectAmperageFromPattern(String pattern) {
        if (pattern.contains("_16384a_")) return "16384A";
        if (pattern.contains("_4096a_")) return "4096A";
        if (pattern.contains("_1024a_")) return "1024A";
        if (pattern.contains("_256a_")) return "256A";
        return null;
    }

    private static String generateDisplayName(String tierName, String componentType, boolean wireless, String amperage) {
        StringBuilder name = new StringBuilder(tierName);

        // Add amperage to name if present
        if (amperage != null) {
            name.append(" ").append(amperage);
        }

        // Add hatch type to name
        if (wireless) {
            if (componentType.contains("INPUT") || componentType.contains("TARGET")) {
                name.append(" Wireless Laser Target Hatch");
            } else {
                name.append(" Wireless Laser Source Hatch");
            }
        } else {
            if (componentType.contains("INPUT") || componentType.contains("TARGET")) {
                name.append(" Laser Target Hatch");
            } else {
                name.append(" Laser Source Hatch");
            }
        }

        return name.toString();
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();
        LaserHatchPattern inputPattern = new LaserHatchPattern("gtceu:*_laser_target_hatch", "LASER_INPUT", 5);
        allHatches.addAll(expandPattern(inputPattern));
    }

    public static List<LaserHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

   public static LaserHatchEntry getHatchForTier(String hatchType, int tier) {
       if (!initialized) initialize();

       // Prioritize hatches with specified amperage
       LaserHatchEntry withAmperage = allHatches.stream()
               .filter(h -> h.tier == tier)
               .filter(h -> h.hatchType.equalsIgnoreCase(hatchType))
               .filter(h -> h.amperage != null && !h.amperage.isEmpty())
               .findFirst()
               .orElse(null);

       if (withAmperage != null) {
           return withAmperage;
       }

       return allHatches.stream()
               .filter(h -> h.tier == tier)
               .filter(h -> h.hatchType.equalsIgnoreCase(hatchType))
               .filter(h -> h.amperage == null || h.amperage.isEmpty())
               .findFirst()
               .orElse(null);
    }
    public static LaserHatchEntry getHatchForTierAndAmperage(String hatchType, int tier, String amperage) {
        if (!initialized) initialize();

        if (amperage != null && !amperage.isEmpty()) {
            LaserHatchEntry withAmperage = allHatches.stream()
                    .filter(h -> h.tier == tier)
                    .filter(h -> h.hatchType.equalsIgnoreCase(hatchType))
                    .filter(h -> amperage.equals(h.amperage))
                    .findFirst()
                    .orElse(null);

            if (withAmperage != null) {
                return withAmperage;
            }
        }

        // Fallback
        return getHatchForTier(hatchType, tier);
    }

    public static void reload() {
        LOGGER.info("Reloading laser hatch configuration");
        initialized = false;
        initialize();
    }
}