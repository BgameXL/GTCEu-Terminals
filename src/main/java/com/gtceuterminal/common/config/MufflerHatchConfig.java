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

// Muffler Hatch configuration
public class MufflerHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MufflerHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "muffler_hatches.json";

    private static List<MufflerHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class MufflerHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;

        public MufflerHatchEntry() {}

        public MufflerHatchEntry(String blockId, String displayName, String tierName, int tier) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
        }
    }

    public static class MufflerHatchPattern {
        public String blockIdPattern;
        public String componentType;
        public Integer minTier;
        public Integer maxTier;

        public MufflerHatchPattern() {}

        public MufflerHatchPattern(String blockIdPattern, String componentType) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
        }
    }

    private static class MufflerHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Muffler Hatch Configuration";
        public List<MufflerHatchEntry> hatches = new ArrayList<>();
        public List<MufflerHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing muffler hatch configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Muffler hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        LOGGER.info("Muffler hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        MufflerHatchConfiguration config = new MufflerHatchConfiguration();

        // Standard muffler hatches (all tiers)
        config.patterns.add(new MufflerHatchPattern("gtceu:*_muffler_hatch", "MUFFLER"));

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default muffler hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            MufflerHatchConfiguration config = GSON.fromJson(json, MufflerHatchConfiguration.class);

            allHatches.clear();

            if (config.patterns != null) {
                for (MufflerHatchPattern pattern : config.patterns) {
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

    private static List<MufflerHatchEntry> expandPattern(MufflerHatchPattern pattern) {
        List<MufflerHatchEntry> entries = new ArrayList<>();

        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;

        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();

            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            String displayName = generateDisplayName(tierName, pattern.componentType);

            entries.add(new MufflerHatchEntry(blockId, displayName, tierName, tier));
        }

        return entries;
    }

    private static String generateDisplayName(String tierName, String componentType) {
        String baseName = componentType
                .replace("_", " ")
                .toLowerCase();
        String readableType = capitalizeWords(baseName);

        return tierName + " " + baseName;
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();
        MufflerHatchPattern pattern = new MufflerHatchPattern("gtceu:*_muffler_hatch", "MUFFLER");
        allHatches.addAll(expandPattern(pattern));
    }

    public static List<MufflerHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static MufflerHatchEntry getHatchForTier(int tier) {
        if (!initialized) initialize();

        return allHatches.stream()
                .filter(h -> h.tier == tier)
                .findFirst()
                .orElse(null);
    }

    public static void reload() {
        LOGGER.info("Reloading muffler hatch configuration");
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

    public static List<MufflerHatchEntry> getAllMufflerHatches() {
        return getAllHatches();
    }
}