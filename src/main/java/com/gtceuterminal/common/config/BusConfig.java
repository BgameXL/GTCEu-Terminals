package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.gregtechceu.gtceu.api.GTValues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Bus configuration (Item I/O)
public class BusConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(BusConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "buses.json";

    private static List<BusEntry> inputBuses = new ArrayList<>();
    private static List<BusEntry> outputBuses = new ArrayList<>();
    private static boolean initialized = false;

    public static class BusEntry {
        public String blockId;           // "gtceu:uev_input_bus"
        public String displayName;       // "UEV Input Bus"
        public String tierName;          // "UEV"
        public int tier;                 // 10
        public String busType;           // "INPUT" or "OUTPUT"

        public BusEntry() {}

        public BusEntry(String blockId, String displayName, String tierName, int tier, String busType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.busType = busType;
        }

        @Override
        public String toString() {
            return String.format("BusEntry{type=%s, tier=%d (%s), block=%s}",
                    busType, tier, tierName, blockId);
        }
    }

    // NEW: Pattern-based configuration for easier maintenance
    public static class BusPattern {
        public String blockIdPattern;     // "gtceu:*_input_bus"
        public String componentType;      // "INPUT_BUS"
        public Integer minTier;           // Optional: minimum tier (0-14)
        public Integer maxTier;           // Optional: maximum tier (0-14)

        public BusPattern() {}

        public BusPattern(String blockIdPattern, String componentType) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
        }
    }

    private static class BusConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Bus Configuration (Item I/O)";

        // OLD FORMAT: Individual entries (backward compatible)
        public List<BusEntry> buses = new ArrayList<>();

        // NEW FORMAT: Wildcard patterns (preferred)
        public List<BusPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Bus config already initialized");
            return;
        }

        LOGGER.info("Initializing bus configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Bus config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        organizeBuses();
        // LOGGER.info("Bus configuration initialized: {} input, {} output",
               // inputBuses.size(), outputBuses.size());

        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        BusConfiguration config = new BusConfiguration();

        // Define patterns for all tiers of input and output buses
        config.patterns.add(new BusPattern("gtceu:*_input_bus", "INPUT_BUS"));
        config.patterns.add(new BusPattern("gtceu:*_output_bus", "OUTPUT_BUS"));
        config.patterns.add(new BusPattern("gtceu:*steam*input_bus*", "STEAM_INPUT_BUS"));
        config.patterns.add(new BusPattern("gtceu:*steam*output_bus*", "STEAM_OUTPUT_BUS"));

        // Write to file
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Created default bus config with patterns at {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create default bus config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            BusConfiguration config = GSON.fromJson(json, BusConfiguration.class);

            List<BusEntry> allBuses = new ArrayList<>();

            // Process patterns (if present)
            if (config.patterns != null && !config.patterns.isEmpty()) {
                LOGGER.info("Loading {} bus patterns", config.patterns.size());
                for (BusPattern pattern : config.patterns) {
                    List<BusEntry> generated = expandPattern(pattern);
                    allBuses.addAll(generated);
                    LOGGER.debug("Pattern '{}' generated {} buses",
                            pattern.blockIdPattern, generated.size());
                }
            }

            // OLD: Process individual entries (backward compatibility)
            if (config.buses != null && !config.buses.isEmpty()) {
                LOGGER.info("Loading {} individual bus entries", config.buses.size());
                allBuses.addAll(config.buses);
            }

            // Store all buses temporarily
            inputBuses.clear();
            outputBuses.clear();
            for (BusEntry bus : allBuses) {
                if ("INPUT".equalsIgnoreCase(bus.busType)) {
                    inputBuses.add(bus);
                } else if ("OUTPUT".equalsIgnoreCase(bus.busType)) {
                    outputBuses.add(bus);
                }
            }

            LOGGER.info("Loaded bus config: {} entries from patterns, {} from individual entries",
                    config.patterns != null ? config.patterns.size() : 0,
                    config.buses != null ? config.buses.size() : 0);

        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load bus config from {}", configPath, e);
            LOGGER.warn("Using hardcoded defaults");
            createHardcodedDefaults();
        }
    }

    // Expands a pattern into individual bus entries based on tier and type
    private static List<BusEntry> expandPattern(BusPattern pattern) {
        List<BusEntry> entries = new ArrayList<>();

        // Determine component type and bus type
        String busType = determineBusType(pattern.componentType);

        // Get tier range
        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;

        // Generate entry for each tier
        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();

            // Replace * with tier name
            String blockId = pattern.blockIdPattern.replace("*", tierLower);

            // Generate display name
            String displayName = generateDisplayName(tierName, pattern.componentType);

            entries.add(new BusEntry(blockId, displayName, tierName, tier, busType));
        }

        return entries;
    }

    private static String determineBusType(String componentType) {
        if (componentType == null) return "INPUT";

        String upper = componentType.toUpperCase();
        if (upper.contains("OUTPUT") || upper.contains("EXPORT")) {
            return "OUTPUT";
        }
        return "INPUT";
    }

    private static String generateDisplayName(String tierName, String componentType) {
        String baseName = componentType.replace("_", " ").toLowerCase();
        String readableType = capitalizeWords(baseName);
        return tierName + " " + readableType;
    }

    private static void createHardcodedDefaults() {
        inputBuses.clear();
        outputBuses.clear();

        // Generate using patterns
        BusPattern inputPattern = new BusPattern("gtceu:*_input_bus", "INPUT_BUS");
        BusPattern outputPattern = new BusPattern("gtceu:*_output_bus", "OUTPUT_BUS");

        inputBuses.addAll(expandPattern(inputPattern));
        outputBuses.addAll(expandPattern(outputPattern));
    }

    private static void organizeBuses() {
        // Already organized during loading
        LOGGER.debug("Buses organized: {} input, {} output",
                inputBuses.size(), outputBuses.size());
    }

    // Public getters (unchanged)
    public static List<BusEntry> getInputBuses() {
        if (!initialized) initialize();
        return new ArrayList<>(inputBuses);
    }

    public static List<BusEntry> getOutputBuses() {
        if (!initialized) initialize();
        return new ArrayList<>(outputBuses);
    }

    public static List<BusEntry> getAllBuses() {
        if (!initialized) initialize();
        List<BusEntry> all = new ArrayList<>();
        all.addAll(inputBuses);
        all.addAll(outputBuses);
        return all;
    }

    public static BusEntry getBusForTier(String busType, int tier) {
        if (!initialized) initialize();

        List<BusEntry> buses = "INPUT".equalsIgnoreCase(busType) ? inputBuses : outputBuses;

        return buses.stream()
                .filter(b -> b.tier == tier)
                .findFirst()
                .orElse(null);
    }

    public static void reload() {
        LOGGER.info("Reloading bus configuration");
        initialized = false;
        initialize();
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
}