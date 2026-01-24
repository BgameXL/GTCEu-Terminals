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

public class MaintenanceHatchConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceHatchConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String CONFIG_DIR = "config/gtceuterminal";
    private static final String CONFIG_FILE = "maintenance_hatches.json";
    
    private static final List<MaintenanceHatchEntry> maintenanceHatches = new ArrayList<>();
    private static boolean initialized = false;

    public static class MaintenanceHatchEntry {
        public String blockId;           // "gtceu:maintenance_hatch"
        public String displayName;       // "Maintenance Hatch"
        public String tierName;          // "LV"
        public int tier;                 // 1 (LV)
        public String description;       // "Basic maintenance hatch"
        
        public MaintenanceHatchEntry() {}
        
        public MaintenanceHatchEntry(String blockId, String displayName, String tierName, int tier, String description) {
            this.blockId = blockId;
            this.displayName = displayName;
            this.tierName = tierName;
            this.tier = tier;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("MaintenanceHatchEntry{tier=%d (%s), block=%s}", 
                tier, tierName, blockId);
        }
    }

    private static class MaintenanceHatchConfiguration {
        public String version = "1.0";
        public String description = "GTCEu Terminal - Maintenance Hatch Configuration";
        public String note = "Maintenance hatches handle automatic maintenance for multiblocks. Higher tiers provide more features.";
        public List<MaintenanceHatchEntry> maintenanceHatches = new ArrayList<>();
    }

    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Maintenance hatch config already initialized");
            return;
        }

        LOGGER.info("Initializing maintenance hatch configuration...");
        
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            loadConfig(configPath);
        } else {
            LOGGER.info("Maintenance hatch config not found, creating default...");
            createDefaultConfig(configPath);
            loadConfig(configPath);
        }
        
        organizeHatches();
        LOGGER.info("Maintenance hatch configuration initialized: {} hatches", maintenanceHatches.size());
        
        initialized = true;
    }

    private static void createDefaultConfig(Path configPath) {
        MaintenanceHatchConfiguration config = new MaintenanceHatchConfiguration();
        
        // Default GTCEu maintenance hatches
        config.maintenanceHatches.add(new MaintenanceHatchEntry(
            "gtceu:maintenance_hatch",
            "Maintenance Hatch",
            "LV",
            GTValues.LV,
            "Basic maintenance - requires manual maintenance"
        ));
        
        config.maintenanceHatches.add(new MaintenanceHatchEntry(
            "gtceu:configurable_maintenance_hatch",
            "Configurable Maintenance Hatch",
            "MV",
            GTValues.MV,
            "Allows configuration of maintenance requirements"
        ));
        
        config.maintenanceHatches.add(new MaintenanceHatchEntry(
            "gtceu:cleaning_maintenance_hatch",
            "Cleaning Maintenance Hatch",
            "HV",
            GTValues.HV,
            "Automatically cleans the multiblock"
        ));
        
        config.maintenanceHatches.add(new MaintenanceHatchEntry(
            "gtceu:auto_maintenance_hatch",
            "Auto Maintenance Hatch",
            "EV",
            GTValues.EV,
            "Fully automatic maintenance"
        ));
        
        saveConfig(configPath, config);
    }

    private static void loadConfig(Path configPath) {
        try {
            String json = Files.readString(configPath);
            MaintenanceHatchConfiguration config = GSON.fromJson(json, MaintenanceHatchConfiguration.class);
            
            if (config != null && config.maintenanceHatches != null) {
                maintenanceHatches.clear();
                maintenanceHatches.addAll(config.maintenanceHatches);
                
                LOGGER.info("Loaded {} maintenance hatches from config", config.maintenanceHatches.size());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load maintenance hatch config: {}", e.getMessage());
        }
    }

    private static void saveConfig(Path configPath, MaintenanceHatchConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("Saved maintenance hatch config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save maintenance hatch config: {}", e.getMessage());
        }
    }

    private static void organizeHatches() {
        maintenanceHatches.sort(Comparator.comparingInt(h -> h.tier));
    }

    // Public API
    public static List<MaintenanceHatchEntry> getAllMaintenanceHatches() {
        return new ArrayList<>(maintenanceHatches);
    }

    public static MaintenanceHatchEntry getMaintenanceHatchByBlock(String blockId) {
        for (MaintenanceHatchEntry hatch : maintenanceHatches) {
            if (hatch.blockId.equals(blockId)) return hatch;
        }
        return null;
    }

    public static MaintenanceHatchEntry getMaintenanceHatchByTier(int tier) {
        for (MaintenanceHatchEntry hatch : maintenanceHatches) {
            if (hatch.tier == tier) return hatch;
        }
        return null;
    }

    public static List<Integer> getAvailableTiers() {
        List<Integer> tiers = new ArrayList<>();
        for (MaintenanceHatchEntry hatch : maintenanceHatches) {
            if (!tiers.contains(hatch.tier)) {
                tiers.add(hatch.tier);
            }
        }
        Collections.sort(tiers);
        return tiers;
    }
}