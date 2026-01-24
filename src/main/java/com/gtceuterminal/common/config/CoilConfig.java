package com.gtceuterminal.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CoilConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoilConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "coils.json";

    private static List<CoilEntry> coilEntries = new ArrayList<>();
    private static boolean initialized = false;

    public static class CoilEntry {
        public String blockId;           // e.g., "gtceu:cupronickel_coil_block"
        public String displayName;       // e.g., "Cupronickel Coil"
        public int temperature;          // e.g., 1800
        public int tier;                 // e.g., 0

        // For JSON serialization
        public CoilEntry() {}

        public CoilEntry(String blockId, String displayName, int temperature, int tier) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.temperature = temperature;
            this.tier = tier;
        }

        @Override
        public String toString() {
            return String.format("CoilEntry{blockId='%s', name='%s', temp=%dK, tier=%d}",
                    blockId, displayName, temperature, tier);
        }
    }

    private static class CoilConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Coil Configuration";
        public List<CoilEntry> coils = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Coil config already initialized");
            return;
        }

        LOGGER.info("Initializing coil configuration system...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Config file not found, creating default configuration...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        coilEntries.sort(Comparator.comparingInt(entry -> entry.temperature));

        LOGGER.info("Coil configuration initialized with {} coil types", coilEntries.size());
        logCoilList();

        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        CoilConfiguration config = new CoilConfiguration();

        config.coils.add(new CoilEntry("gtceu:cupronickel_coil_block", "Cupronickel Coil", 1800, 0));
        config.coils.add(new CoilEntry("gtceu:kanthal_coil_block", "Kanthal Coil", 2700, 1));
        config.coils.add(new CoilEntry("gtceu:nichrome_coil_block", "Nichrome Coil", 3600, 2));
        config.coils.add(new CoilEntry("gtceu:rtm_alloy_coil_block", "RTM Alloy Coil", 4500, 3));
        config.coils.add(new CoilEntry("gtceu:hssg_coil_block", "HSS-G Coil", 5400, 4));
        config.coils.add(new CoilEntry("gtceu:naquadah_coil_block", "Naquadah Coil", 7200, 5));
        config.coils.add(new CoilEntry("gtceu:trinium_coil_block", "Trinium Coil", 9000, 6));
        config.coils.add(new CoilEntry("gtceu:tritanium_coil_block", "Tritanium Coil", 10800, 7));

        try {
            Files.createDirectories(configPath.getParent());

            // Write config file
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);

            LOGGER.info("Created default config file at: {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create default config file", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            CoilConfiguration config = GSON.fromJson(json, CoilConfiguration.class);

            if (config == null || config.coils == null) {
                LOGGER.error("Invalid config file structure, using defaults");
                createDefaultConfig(configPath);
                return;
            }

            coilEntries.clear();

            for (CoilEntry entry : config.coils) {
                if (validateEntry(entry)) {
                    coilEntries.add(entry);
                } else {
                    LOGGER.warn("Skipping invalid coil entry: {}", entry);
                }
            }

            if (coilEntries.isEmpty()) {
                LOGGER.warn("No valid coils found in config, creating defaults");
                createDefaultConfig(configPath);
                loadConfig(configPath);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to load config file", e);
            createDefaultConfig(configPath);
        } catch (JsonSyntaxException e) {
            LOGGER.error("Invalid JSON syntax in config file", e);
            createDefaultConfig(configPath);
        }
    }

    private static boolean validateEntry(CoilEntry entry) {
        if (entry.blockId == null || entry.blockId.isEmpty()) {
            LOGGER.error("Coil entry missing blockId");
            return false;
        }

        if (entry.displayName == null || entry.displayName.isEmpty()) {
            LOGGER.error("Coil entry missing displayName: {}", entry.blockId);
            return false;
        }

        if (entry.temperature <= 0) {
            LOGGER.error("Coil entry has invalid temperature: {}", entry.blockId);
            return false;
        }

        if (entry.tier < 0) {
            LOGGER.error("Coil entry has invalid tier: {}", entry.blockId);
            return false;
        }

        // Verify block exists
        try {
            ResourceLocation blockId = ResourceLocation.parse(entry.blockId);
            Block block = BuiltInRegistries.BLOCK.get(blockId);

            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                LOGGER.warn("Block not found in registry: {} - skipping", entry.blockId);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Invalid block ID format: {}", entry.blockId, e);
            return false;
        }

        return true;
    }

    public static List<CoilEntry> getAllCoils() {
        if (!initialized) {
            initialize();
        }
        return new ArrayList<>(coilEntries);
    }

    public static int getCoilTier(BlockState state) {
        if (!initialized) {
            initialize();
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        for (int i = 0; i < coilEntries.size(); i++) {
            if (coilEntries.get(i).blockId.equals(blockId)) {
                return i;
            }
        }

        return -1;
    }

    public static CoilEntry getCoilByTier(int tier) {
        if (!initialized) {
            initialize();
        }

        if (tier >= 0 && tier < coilEntries.size()) {
            return coilEntries.get(tier);
        }

        return null;
    }

    public static Block getCoilBlock(int tier) {
        CoilEntry entry = getCoilByTier(tier);
        if (entry == null) return null;

        try {
            ResourceLocation blockId = ResourceLocation.parse(entry.blockId);
            return BuiltInRegistries.BLOCK.get(blockId);
        } catch (Exception e) {
            LOGGER.error("Failed to get block for tier {}", tier, e);
            return null;
        }
    }

    public static String getCoilDisplayName(int tier) {
        CoilEntry entry = getCoilByTier(tier);
        if (entry == null) return "Unknown Coil";
        return String.format("%s (%dK)", entry.displayName, entry.temperature);
    }

    public static int getMaxCoilTier() {
        if (!initialized) {
            initialize();
        }
        if (coilEntries.isEmpty()) {
            return 0;
        }

        return coilEntries.size() - 1;
    }

    public static boolean isValidTier(int tier) {
        return tier >= 0 && tier <= getMaxCoilTier();
    }

    public static void reload() {
        LOGGER.info("Reloading coil configuration...");
        initialized = false;
        coilEntries.clear();
        initialize();
    }

    private static void logCoilList() {
        LOGGER.info("Configured coils (sorted by temperature):");
        for (int i = 0; i < coilEntries.size(); i++) {
            CoilEntry entry = coilEntries.get(i);
            LOGGER.info("  [{}] {} - {}K (Block: {})",
                    i, entry.displayName, entry.temperature, entry.blockId);
        }
    }

    public static Path getConfigPath() {
        return Paths.get(CONFIG_DIR, CONFIG_FILE);
    }
}
