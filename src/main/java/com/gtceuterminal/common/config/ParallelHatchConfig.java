package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ParallelHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "parallel_hatches.json";

    private static List<ParallelHatchEntry> parallelHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class ParallelHatchEntry {
        public String blockId;           // "gtceu:iv_parallel_hatch"
        public String displayName;       // "IV Parallel Hatch"
        public String tierName;          // "IV"
        public int tier;                 // 5
        public int maxParallel;          // max parallel operations

        public ParallelHatchEntry() {}

        public ParallelHatchEntry(String blockId, String displayName, String tierName, int tier, int maxParallel) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.maxParallel = maxParallel;
        }

        @Override
        public String toString() {
            return String.format("ParallelHatchEntry{tier=%d (%s), maxParallel=%d, block=%s}",
                    tier, tierName, maxParallel, blockId);
        }
    }

    private static class ParallelHatchConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Parallel Hatch Configuration";
        public String note = "Parallel hatches enable parallel processing in multiblocks. Higher tiers = more parallels.";
        public List<ParallelHatchEntry> parallelHatches = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Parallel hatch config already initialized");
            return;
        }

        LOGGER.info("Initializing parallel hatch configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Parallel hatch config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        organizeHatches();
        LOGGER.info("Parallel hatch configuration initialized: {} hatches", parallelHatches.size());

        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        ParallelHatchConfiguration config = new ParallelHatchConfiguration();

        config.parallelHatches.add(new ParallelHatchEntry(
                "gtceu:iv_parallel_hatch",
                "Elite Parallel Control Hatch",
                "IV",
                5,  // IV
                4
        ));

        config.parallelHatches.add(new ParallelHatchEntry(
                "gtceu:luv_parallel_hatch",
                "Master Parallel Control Hatch",
                "LUV",
                6,  // LUV
                16
        ));

        config.parallelHatches.add(new ParallelHatchEntry(
                "gtceu:zpm_parallel_hatch",
                "Ultimate Parallel Control Hatch",
                "ZPM",
                7,  // ZPM
                64
        ));

        config.parallelHatches.add(new ParallelHatchEntry(
                "gtceu:uv_parallel_hatch",
                "Super Parallel Control Hatch",
                "UV",
                8,  // UV
                256
        ));

        saveConfig(configPath, config);
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            ParallelHatchConfiguration config = GSON.fromJson(json, ParallelHatchConfiguration.class);

            if (config != null && config.parallelHatches != null) {
                parallelHatches.clear();
                parallelHatches.addAll(config.parallelHatches);

                LOGGER.info("Loaded {} parallel hatches from config", config.parallelHatches.size());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load parallel hatch config: {}", e.getMessage());
        }
    }

    private static void saveConfig(Path configPath, ParallelHatchConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Saved parallel hatch config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save parallel hatch config: {}", e.getMessage());
        }
    }

    private static void organizeHatches() {
        parallelHatches.sort(Comparator.comparingInt(h -> h.tier));
    }

    // Public API
    public static List<ParallelHatchEntry> getAllParallelHatches() {
        return new ArrayList<>(parallelHatches);
    }

    public static ParallelHatchEntry getParallelHatchByBlock(String blockId) {
        for (ParallelHatchEntry hatch : parallelHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        return null;
    }

    public static ParallelHatchEntry getParallelHatchByTier(int tier) {
        for (ParallelHatchEntry hatch : parallelHatches) {
            if (hatch.tier == tier) return hatch;
        }
        return null;
    }

    public static int getMaxParallelForTier(int tier) {
        ParallelHatchEntry hatch = getParallelHatchByTier(tier);
        return hatch != null ? hatch.maxParallel : 1;
    }
}