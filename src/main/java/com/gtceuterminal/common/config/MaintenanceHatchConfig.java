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

// Maintenance Hatch configuration
public class MaintenanceHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "maintenance_hatches.json";

    private static List<MaintenanceHatchEntry> allHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class MaintenanceHatchEntry {
        public String blockId;
        public String displayName;
        public String tierName;
        public int tier;
        public String maintenanceType;  // "STANDARD", "AUTO", "CONFIGURABLE", "CLEANING"

        public MaintenanceHatchEntry() {}

        public MaintenanceHatchEntry(String blockId, String displayName, String tierName,
                                     int tier, String maintenanceType) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.maintenanceType = maintenanceType;
        }
    }

    private static class MaintenanceHatchConfiguration {
        public String version = "2.1";
        public String description = "GTCEu Terminal - Maintenance Hatch Configuration";
        public List<MaintenanceHatchEntry> hatches = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) return;

        LOGGER.info("Initializing maintenance hatch configuration...");

        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);

        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Maintenance hatch config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }

        LOGGER.info("Maintenance hatch configuration initialized: {} entries", allHatches.size());
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        MaintenanceHatchConfiguration config = new MaintenanceHatchConfiguration();

        // ALL 4 maintenance hatches
        config.hatches.add(new MaintenanceHatchEntry(
                "gtceu:maintenance_hatch",
                "Maintenance Hatch",
                "LV",
                1,  // tier 1 (LV)
                "STANDARD"
        ));

        config.hatches.add(new MaintenanceHatchEntry(
                "gtceu:auto_maintenance_hatch",
                "Auto-Maintenance Hatch",
                "HV",
                3,  // tier 3 (HV)
                "AUTO"
        ));

        config.hatches.add(new MaintenanceHatchEntry(
                "gtceu:configurable_maintenance_hatch",
                "Configurable Maintenance Hatch",
                "HV",
                3,  // tier 3 (HV)
                "CONFIGURABLE"
        ));

        config.hatches.add(new MaintenanceHatchEntry(
                "gtceu:cleaning_maintenance_hatch",
                "Cleaning Maintenance Hatch",
                "HV",
                3,  // tier 3 (HV)
                "CLEANING"
        ));

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
            LOGGER.info("Created default maintenance hatch config (standard LV + HV variants)");
        } catch (IOException e) {
            LOGGER.error("Failed to create default config", e);
        }
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            MaintenanceHatchConfiguration config = GSON.fromJson(json, MaintenanceHatchConfiguration.class);

            allHatches.clear();

            if (config.hatches != null) {
                allHatches.addAll(config.hatches);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to load config", e);
            createHardcodedDefaults();
        }
    }

    private static void createHardcodedDefaults() {
        allHatches.clear();

        // Fallback: 4 LV maintenance hatches only
        allHatches.add(new MaintenanceHatchEntry("gtceu:maintenance_hatch", "Maintenance Hatch", "LV", 1, "STANDARD"));
        // In GTCEu, Auto/Configurable/Cleaning are HV-tier variants.
        allHatches.add(new MaintenanceHatchEntry("gtceu:auto_maintenance_hatch", "Auto-Maintenance Hatch", "HV", 3, "AUTO"));
        allHatches.add(new MaintenanceHatchEntry("gtceu:configurable_maintenance_hatch", "Configurable Maintenance Hatch", "HV", 3, "CONFIGURABLE"));
        allHatches.add(new MaintenanceHatchEntry("gtceu:cleaning_maintenance_hatch", "Cleaning Maintenance Hatch", "HV", 3, "CLEANING"));
    }

    public static List<MaintenanceHatchEntry> getAllHatches() {
        if (!initialized) initialize();
        return new ArrayList<>(allHatches);
    }

    public static MaintenanceHatchEntry getHatchForTier(int tier) {
        if (!initialized) initialize();

        // All maintenance hatches
        return allHatches.stream()
                .filter(h -> h.tier == tier)
                .filter(h -> "STANDARD".equals(h.maintenanceType))
                .findFirst()
                .orElse(null);
    }

    public static List<MaintenanceHatchEntry> getHatchesForTier(int tier) {
        if (!initialized) initialize();

        return allHatches.stream()
                .filter(h -> h.tier == tier)
                .toList();
    }

    // Get all hatches of a specific maintenance type (e.g. "AUTO", "CONFIGURABLE", "CLEANING")
    public static List<MaintenanceHatchEntry> getAllMaintenanceHatches() {
        return getAllHatches();
    }

    public static void reload() {
        LOGGER.info("Reloading maintenance hatch configuration");
        initialized = false;
        initialize();
    }
}