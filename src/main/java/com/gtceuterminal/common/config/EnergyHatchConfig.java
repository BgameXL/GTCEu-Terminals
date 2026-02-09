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

// Energy Hatch configuration
public class EnergyHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnergyHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "energy_hatches.json";

    private static List<EnergyHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class EnergyHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String amperage;      // "2A", "4A", "16A", etc.
        public String hatchType;     // "INPUT" or "OUTPUT"

        public EnergyHatchEntry() {}

        public EnergyHatchEntry(String blockId, String displayName, String tierName,
                                int tier, String amperage, String hatchType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.amperage = amperage;
            this.hatchType = hatchType;
        }
    }

    public static class EnergyHatchPattern {
        public String blockIdPattern;
        public String componentType;
        public String amperage;       // Optional: override amperage
        public Integer minTier;
        public Integer maxTier;

        public EnergyHatchPattern() {}

        public EnergyHatchPattern(String blockIdPattern, String componentType) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
        }
    }

    private static class EnergyHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Energy Hatch Configuration";
        public List<EnergyHatchEntry> hatches = new ArrayList<>();
        public List<EnergyHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing energy hatch configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Energy hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        LOGGER.info("Energy hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        EnergyHatchConfiguration config = new EnergyHatchConfiguration();

        // Energy Input Hatches
        config.patterns.add(createPattern("gtceu:*_energy_input_hatch", "ENERGY_HATCH", "2A"));
        config.patterns.add(createPattern("gtceu:*_energy_input_hatch_4a", "ENERGY_HATCH", "4A"));
        config.patterns.add(createPattern("gtceu:*_energy_input_hatch_16a", "ENERGY_HATCH", "16A"));
        config.patterns.add(createPattern("gtceu:*_energy_input_hatch_64a", "ENERGY_HATCH", "64A"));
        config.patterns.add(createPattern("gtceu:*_energy_input_hatch_256a", "ENERGY_HATCH", "256A"));

        // Dynamo Hatches (Energy Output)
        config.patterns.add(createPattern("gtceu:*_energy_output_hatch", "DYNAMO_HATCH", "2A"));
        config.patterns.add(createPattern("gtceu:*_energy_output_hatch_4a", "DYNAMO_HATCH", "4A"));
        config.patterns.add(createPattern("gtceu:*_energy_output_hatch_16a", "DYNAMO_HATCH", "16A"));
        config.patterns.add(createPattern("gtceu:*_energy_output_hatch_64a", "DYNAMO_HATCH", "64A"));

        // Substation Hatches (high amperage)
        config.patterns.add(createPattern("gtceu:*_substation_input_hatch_*", "SUBSTATION_INPUT", "64A+"));
        config.patterns.add(createPattern("gtceu:*_substation_output_hatch_*", "SUBSTATION_OUTPUT", "64A+"));

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default energy hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static EnergyHatchPattern createPattern(String pattern, String type, String amperage) {
        EnergyHatchPattern p = new EnergyHatchPattern(pattern, type);
        p.amperage = amperage;
        return p;
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            EnergyHatchConfiguration config = GSON.fromJson(json, EnergyHatchConfiguration.class);

            allHatches.clear();

            // Process patterns
            if (config.patterns != null) {
                for (EnergyHatchPattern pattern : config.patterns) {
                    allHatches.addAll(expandPattern(pattern));
                }
            }

            // Process individual entries (backward compatibility)
            if (config.hatches != null) {
                allHatches.addAll(config.hatches);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
            createHardcodedDefaults();
        }
    }

    // Expands a pattern into individual hatch entries based on tier and amperage
    private static List<EnergyHatchEntry> expandPattern(EnergyHatchPattern pattern) {
        List<EnergyHatchEntry> entries = new ArrayList<>();

        String hatchType = pattern.componentType.contains("OUTPUT") ||
                pattern.componentType.contains("DYNAMO") ? "OUTPUT" : "INPUT";

        String amperage = pattern.amperage != null ? pattern.amperage : extractAmperageFromPattern(pattern.blockIdPattern);

        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;

        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();

            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            String displayName = generateDisplayName(tierName, pattern.componentType, amperage);

            entries.add(new EnergyHatchEntry(blockId, displayName, tierName, tier, amperage, hatchType));
        }

        return entries;
    }

    private static String extractAmperageFromPattern(String pattern) {
        if (pattern.contains("_4a")) return "4A";
        if (pattern.contains("_16a")) return "16A";
        if (pattern.contains("_64a")) return "64A";
        if (pattern.contains("_256a")) return "256A";
        return "2A"; // Default
    }

    private static String generateDisplayName(String tierName, String componentType, String amperage) {
        String baseName = componentType.replace("_", " ").toLowerCase();
        String readableType = capitalizeWords(baseName);
        if (amperage != null && !amperage.equals("2A")) {
            return tierName + " " + amperage + " " + readableType;
        }
        return tierName + " " + readableType;
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();
        EnergyHatchPattern inputPattern = new EnergyHatchPattern("gtceu:*_energy_input_hatch", "ENERGY_HATCH");
        inputPattern.amperage = "2A";
        allHatches.addAll(expandPattern(inputPattern));
    }

    public static List<EnergyHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static EnergyHatchEntry getHatchForTier(String hatchType, int tier, String amperage) {
        if (!initialized) initialize();

        return allHatches.stream()
                .filter(h -> h.tier == tier)
                .filter(h -> h.hatchType.equalsIgnoreCase(hatchType))
                .filter(h -> amperage == null || h.amperage.equalsIgnoreCase(amperage))
                .findFirst()
                .orElse(null);
    }

    public static EnergyHatchEntry getHatchForTierAndAmperage(String hatchType, int tier, String amperage) {
        if (!initialized) initialize();

        if (amperage != null && !amperage.isEmpty()) {
            EnergyHatchEntry withAmperage = allHatches.stream()
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
        return allHatches.stream()
                .filter(h -> h.tier == tier)
                .filter(h -> h.hatchType.equalsIgnoreCase(hatchType))
                .findFirst()
                .orElse(null);
    }

    private static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;

        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }
    public static List<EnergyHatchEntry> getAllEnergyHatches() {
        return getAllHatches();
    }
}