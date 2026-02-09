package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Heating Coil configuration
public class CoilConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoilConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "coils.json";

    private static List<CoilEntry> allCoils = new ArrayList<>();
    private static boolean initialized = false;

    public static class CoilEntry {
        public String blockId;
        public String displayName;
        public String coilType;      // Material name: "Cupronickel", "Kanthal", etc.
        public String heatCapacity;  // "1800K", "2700K", etc.
        public int tier;             // Relative tier (0-7)

        public CoilEntry() {}

        public CoilEntry(String blockId, String displayName, String coilType,
                         String heatCapacity, int tier) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.coilType = coilType;
            this.heatCapacity = heatCapacity;
            this.tier = tier;
        }
    }

    public static class CoilPattern {
        public String blockIdPattern;
        public String componentType;
        public String heatCapacity;

        public CoilPattern() {}

        public CoilPattern(String blockIdPattern, String componentType, String heatCapacity) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
            this.heatCapacity = heatCapacity;
        }
    }

    private static class CoilConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Heating Coil Configuration";
        public List<CoilEntry> coils = new ArrayList<>();
        public List<CoilPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing coil configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Coil config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        LOGGER.info("Coil configuration initialized: {} entries", allCoils.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        CoilConfiguration config = new CoilConfiguration();

        // All heating coils in order of heat capacity
        config.patterns.add(new CoilPattern("gtceu:cupronickel_coil_block", "COIL", "1800K"));
        config.patterns.add(new CoilPattern("gtceu:kanthal_coil_block", "COIL", "2700K"));
        config.patterns.add(new CoilPattern("gtceu:nichrome_coil_block", "COIL", "3600K"));
        config.patterns.add(new CoilPattern("gtceu:rtm_alloy_coil_block", "COIL", "4500K"));
        config.patterns.add(new CoilPattern("gtceu:hssg_coil_block", "COIL", "5400K"));
        config.patterns.add(new CoilPattern("gtceu:naquadah_coil_block", "COIL", "7200K"));
        config.patterns.add(new CoilPattern("gtceu:trinium_coil_block", "COIL", "9000K"));
        config.patterns.add(new CoilPattern("gtceu:tritanium_coil_block", "COIL", "10800K"));

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default coil config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            CoilConfiguration config = GSON.fromJson(json, CoilConfiguration.class);

            allCoils.clear();

            if (config.patterns != null) {
                int tier = 0;
                for (CoilPattern pattern : config.patterns) {
                    CoilEntry entry = createCoilEntry(pattern, tier);
                    if (entry != null) {
                        allCoils.add(entry);
                        tier++;
                    }
                }
            }

            if (config.coils != null) {
                allCoils.addAll(config.coils);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
            createHardcodedDefaults();
        }
    }

    private static CoilEntry createCoilEntry(CoilPattern pattern, int tier) {
        String blockId = pattern.blockIdPattern;

        // Extract coil material name from block ID
        // "gtceu:cupronickel_coil_block" -> "Cupronickel"
        String coilType = extractCoilType(blockId);
        String displayName = coilType + " Coil";

        return new CoilEntry(blockId, displayName, coilType, pattern.heatCapacity, tier);
    }

    private static String extractCoilType(String blockId) {
        // Extract material name: "gtceu:cupronickel_coil_block" -> "cupronickel"
        String material = blockId
                .replace("gtceu:", "")
                .replace("_coil_block", "")
                .replace("_", " ");

        // Capitalize: "cupronickel" -> "Cupronickel"
        return material.substring(0, 1).toUpperCase() + material.substring(1);
    }

    private static void createHardcodedDefaults() {
        allCoils.clear();

        // Fallback hardcoded coils
        allCoils.add(new CoilEntry("gtceu:cupronickel_coil_block", "Cupronickel Coil", "Cupronickel", "1800K", 0));
        allCoils.add(new CoilEntry("gtceu:kanthal_coil_block", "Kanthal Coil", "Kanthal", "2700K", 1));
        allCoils.add(new CoilEntry("gtceu:nichrome_coil_block", "Nichrome Coil", "Nichrome", "3600K", 2));
        allCoils.add(new CoilEntry("gtceu:rtm_alloy_coil_block", "RTM Alloy Coil", "RTM Alloy", "4500K", 3));
        allCoils.add(new CoilEntry("gtceu:hssg_coil_block", "HSS-G Coil", "HSS-G", "5400K", 4));
        allCoils.add(new CoilEntry("gtceu:naquadah_coil_block", "Naquadah Coil", "Naquadah", "7200K", 5));
        allCoils.add(new CoilEntry("gtceu:trinium_coil_block", "Trinium Coil", "Trinium", "9000K", 6));
        allCoils.add(new CoilEntry("gtceu:tritanium_coil_block", "Tritanium Coil", "Tritanium", "10800K", 7));
    }

    public static List<CoilEntry> getAllCoils() {
        if (!initialized) initialize();
        return new ArrayList<>(allCoils);
    }

    public static CoilEntry getCoilByType(String coilType) {
        if (!initialized) initialize();

        return allCoils.stream()
                .filter(c -> c.coilType.equalsIgnoreCase(coilType))
                .findFirst()
                .orElse(null);
    }

    public static CoilEntry getCoilByTier(int tier) {
        if (!initialized) initialize();

        return allCoils.stream()
                .filter(c -> c.tier == tier)
                .findFirst()
                .orElse(null);
    }

    public static void reload() {
        LOGGER.info("Reloading coil configuration");
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

    public static String getCoilDisplayName(int tier) {
        CoilEntry coil = getCoilByTier(tier);
        return coil != null ? coil.displayName : "Unknown Coil";
    }

    public static int getCoilTier(net.minecraft.world.level.block.state.BlockState state) {
        if (state == null) return -1;

        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString();

        for (CoilEntry coil : getAllCoils()) {
            if (coil.blockId.equals(blockId)) {
                return coil.tier;
            }
        }

        return -1;
    }
}