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

// Fluid Hatch configuration
public class HatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "fluid_hatches.json";

    private static List<FluidHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class FluidHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String capacity;      // "1x", "4x", "9x"
        public String hatchType;     // "INPUT" or "OUTPUT"

        public FluidHatchEntry() {}

        public FluidHatchEntry(String blockId, String displayName, String tierName,
                               int tier, String capacity, String hatchType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.capacity = capacity;
            this.hatchType = hatchType;
        }
    }

    public static class FluidHatchPattern {
        public String blockIdPattern;
        public String componentType;
        public String capacity;
        public Integer minTier;
        public Integer maxTier;

        public FluidHatchPattern() {}

        public FluidHatchPattern(String blockIdPattern, String componentType, String capacity) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
            this.capacity = capacity;
        }
    }

    private static class FluidHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Fluid Hatch Configuration";
        public List<FluidHatchEntry> hatches = new ArrayList<>();
        public List<FluidHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing fluid hatch configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Fluid hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        LOGGER.info("Fluid hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        FluidHatchConfiguration config = new FluidHatchConfiguration();

        // Input Hatches
        config.patterns.add(new FluidHatchPattern("gtceu:*_input_hatch", "INPUT_HATCH", "1x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_input_hatch_1x", "INPUT_HATCH_1X", "1x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_input_hatch_4x", "QUAD_INPUT_HATCH", "4x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_input_hatch_9x", "NONUPLE_INPUT_HATCH", "9x"));

        // Output Hatches
        config.patterns.add(new FluidHatchPattern("gtceu:*_output_hatch", "OUTPUT_HATCH", "1x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_output_hatch_1x", "OUTPUT_HATCH_1X", "1x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_output_hatch_4x", "QUAD_OUTPUT_HATCH", "4x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_output_hatch_9x", "NONUPLE_OUTPUT_HATCH", "9x"));

        // Special fluid hatches (It's gonna be removed eventually but for now we can add it here so it shows up in the config)
        config.patterns.add(new FluidHatchPattern("gtceu:*_pump_fluid_hatch", "PUMP_FLUID_HATCH", "1x"));
        config.patterns.add(new FluidHatchPattern("gtceu:*_tank_valve", "TANK_VALVE", "1x"));

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default fluid hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            FluidHatchConfiguration config = GSON.fromJson(json, FluidHatchConfiguration.class);

            allHatches.clear();

            if (config.patterns != null) {
                for (FluidHatchPattern pattern : config.patterns) {
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

    // Expands a pattern into individual hatch entries based on tier and capacity
    private static List<FluidHatchEntry> expandPattern(FluidHatchPattern pattern) {
        List<FluidHatchEntry> entries = new ArrayList<>();

        String hatchType = pattern.componentType.contains("OUTPUT") ? "OUTPUT" : "INPUT";
        String capacity = pattern.capacity != null ? pattern.capacity : extractCapacityFromPattern(pattern.blockIdPattern);

        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;

        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();

            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            String displayName = generateDisplayName(tierName, pattern.componentType, capacity);

            entries.add(new FluidHatchEntry(blockId, displayName, tierName, tier, capacity, hatchType));
        }

        return entries;
    }

    private static String extractCapacityFromPattern(String pattern) {
        if (pattern.contains("_9x")) return "9x";
        if (pattern.contains("_4x")) return "4x";
        return "1x";
    }

    private static String generateDisplayName(String tierName, String componentType, String capacity) {
        String baseName = componentType.replace("_", " ").toLowerCase();
        String readableType = capitalizeWords(baseName);
        if (capacity != null && !capacity.equals("1x")) {
            return tierName + " " + readableType + " (" + capacity + ")";
        }
        return tierName + " " + readableType;
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();
        FluidHatchPattern inputPattern = new FluidHatchPattern("gtceu:*_input_hatch", "INPUT_HATCH", "1x");
        allHatches.addAll(expandPattern(inputPattern));
    }

    public static List<FluidHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static FluidHatchEntry getHatchForTier(String hatchType, int tier) {
        if (!initialized) initialize();

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

    public static List<FluidHatchEntry> getInputHatches() {
        return getAllHatches().stream()
                .filter(h -> "INPUT".equalsIgnoreCase(h.hatchType))
                .toList();
    }

    public static List<FluidHatchEntry> getOutputHatches() {
        return getAllHatches().stream()
                .filter(h -> "OUTPUT".equalsIgnoreCase(h.hatchType))
                .toList();
    }
}