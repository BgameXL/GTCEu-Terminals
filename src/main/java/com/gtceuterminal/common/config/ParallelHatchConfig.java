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

// Parallel Hatch configuration
public class ParallelHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "parallel_hatches.json";

    private static List<ParallelHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class ParallelHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String variant;       // "STANDARD" or "MK"

        public ParallelHatchEntry() {}

        public ParallelHatchEntry(String blockId, String displayName, String tierName,
                                  int tier, String variant) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.variant = variant;
        }
    }

    public static class ParallelHatchPattern {
        public String blockIdPattern;
        public String componentType;
        public Integer minTier;
        public Integer maxTier;

        public ParallelHatchPattern() {}

        public ParallelHatchPattern(String blockIdPattern, String componentType) {
            this.blockIdPattern = blockIdPattern;
            this.componentType = componentType;
        }
    }

    private static class ParallelHatchConfiguration {
        public String version = "2.0";
        public String description = "GTCEu Terminal - Parallel Hatch Configuration";
        public List<ParallelHatchEntry> hatches = new ArrayList<>();
        public List<ParallelHatchPattern> patterns = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing parallel hatch configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Parallel hatch config not found, creating default with patterns...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        LOGGER.info("Parallel hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        ParallelHatchConfiguration config = new ParallelHatchConfiguration();

        // Standard parallel hatches (all tiers)
        config.patterns.add(new ParallelHatchPattern("gtceu:*_parallel_hatch", "PARALLEL_HATCH"));

        // MK variant parallel hatches
        config.patterns.add(new ParallelHatchPattern("gtceu:*_parallel_hatch_mk*", "PARALLEL_HATCH_MK"));

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default parallel hatch config with patterns");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            ParallelHatchConfiguration config = GSON.fromJson(json, ParallelHatchConfiguration.class);

            allHatches.clear();

            if (config.patterns != null) {
                for (ParallelHatchPattern pattern : config.patterns) {
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

    private static List<ParallelHatchEntry> expandPattern(ParallelHatchPattern pattern) {
        List<ParallelHatchEntry> entries = new ArrayList<>();

        String variant = pattern.componentType.contains("MK") ? "MK" : "STANDARD";

        int minTier = pattern.minTier != null ? pattern.minTier : 0;
        int maxTier = pattern.maxTier != null ? pattern.maxTier : GTValues.VN.length - 1;

        for (int tier = minTier; tier <= maxTier && tier < GTValues.VN.length; tier++) {
            String tierName = GTValues.VN[tier];
            String tierLower = tierName.toLowerCase();

            String blockId = pattern.blockIdPattern.replace("*", tierLower);
            String displayName = generateDisplayName(tierName, pattern.componentType);

            entries.add(new ParallelHatchEntry(blockId, displayName, tierName, tier, variant));
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
        ParallelHatchPattern pattern = new ParallelHatchPattern("gtceu:*_parallel_hatch", "PARALLEL_HATCH");
        allHatches.addAll(expandPattern(pattern));
    }

    public static List<ParallelHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static ParallelHatchEntry getHatchForTier(int tier) {
        if (!initialized) initialize();

        return allHatches.stream()
                .filter(h -> h.tier == tier)
                .filter(h -> "STANDARD".equals(h.variant))
                .findFirst()
                .orElse(null);
    }

    public static void reload() {
        LOGGER.info("Reloading parallel hatch configuration");
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

    public static List<ParallelHatchEntry> getAllParallelHatches() {
        return getAllHatches();
    }

}