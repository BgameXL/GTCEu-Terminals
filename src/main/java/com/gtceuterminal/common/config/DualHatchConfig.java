package com.gtceuterminal.common.config;

import com.google.gson.*;
import com.gtceuterminal.GTCEUTerminalMod;

import java.io.*;
import java.util.*;

// Dual Hatch configuration
public class DualHatchConfig {

    private static final File CONFIG_FILE = new File("config/gtceuterminal/dual_hatches.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<DualHatchEntry> INPUT_DUAL_HATCHES = new ArrayList<>();
    private static final List<DualHatchEntry> OUTPUT_DUAL_HATCHES = new ArrayList<>();

    public static void initialize() {
    }

    public static class DualHatchEntry {
        public int tier;
        public String blockId;
        public String displayName;
        public String source; // "gtceu" or "gtmthings"

        public DualHatchEntry(int tier, String blockId, String displayName, String source) {
            this.tier = tier;
            this.blockId = blockId;
            this.displayName = displayName;
            this.source = source;
        }
    }

    public static void init() {
        if (CONFIG_FILE.exists()) {
            load();
        } else {
            createDefault();
            save();
        }
    }

    private static void createDefault() {
        GTCEUTerminalMod.LOGGER.info("Creating default dual hatch configuration...");

        // GTCeu Dual Input Hatches (LV-UV)
        String[] tierNames = {"lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv"};
        String[] tierDisplayNames = {"LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV"};

        for (int i = 0; i < tierNames.length; i++) {
            int tier = i + 1; // LV = tier 1
            String tierName = tierNames[i];
            String displayName = tierDisplayNames[i];

            // GTCeu Dual Input Hatch
            INPUT_DUAL_HATCHES.add(new DualHatchEntry(
                    tier,
                    "gtceu:" + tierName + "_dual_input_hatch",
                    displayName + " Dual Input Hatch",
                    "gtceu"
            ));

            // GTMThings Huge Dual Hatch
            INPUT_DUAL_HATCHES.add(new DualHatchEntry(
                    tier,
                    "gtmthings:" + tierName + "_huge_dual_hatch",
                    displayName + " Huge Dual Hatch",
                    "gtmthings"
            ));
        }

        // Add UHV+ tiers if GTMThings is installed
        String[] highTierNames = {"uhv", "uev", "uiv", "uxv", "opv", "max"};
        String[] highTierDisplayNames = {"UHV", "UEV", "UIV", "UXV", "OpV", "MAX"};

        for (int i = 0; i < highTierNames.length; i++) {
            int tier = i + 9; // UHV = tier 9
            String tierName = highTierNames[i];
            String displayName = highTierDisplayNames[i];

            // GTMThings Huge Dual Hatch
            INPUT_DUAL_HATCHES.add(new DualHatchEntry(
                    tier,
                    "gtmthings:" + tierName + "_huge_dual_hatch",
                    displayName + " Huge Dual Hatch",
                    "gtmthings"
            ));
        }

        GTCEUTerminalMod.LOGGER.info("Created {} dual hatch entries", INPUT_DUAL_HATCHES.size());
    }

    private static void save() {
    }

    private static void load() {
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            INPUT_DUAL_HATCHES.clear();
            OUTPUT_DUAL_HATCHES.clear();

            if (root.has("inputDualHatches")) {
                JsonArray inputArray = root.getAsJsonArray("inputDualHatches");
                for (JsonElement element : inputArray) {
                    JsonObject obj = element.getAsJsonObject();
                    INPUT_DUAL_HATCHES.add(new DualHatchEntry(
                            obj.get("tier").getAsInt(),
                            obj.get("blockId").getAsString(),
                            obj.get("displayName").getAsString(),
                            obj.has("source") ? obj.get("source").getAsString() : "gtceu"
                    ));
                }
            }

            if (root.has("outputDualHatches")) {
                JsonArray outputArray = root.getAsJsonArray("outputDualHatches");
                for (JsonElement element : outputArray) {
                    JsonObject obj = element.getAsJsonObject();
                    OUTPUT_DUAL_HATCHES.add(new DualHatchEntry(
                            obj.get("tier").getAsInt(),
                            obj.get("blockId").getAsString(),
                            obj.get("displayName").getAsString(),
                            obj.has("source") ? obj.get("source").getAsString() : "gtceu"
                    ));
                }
            }

            GTCEUTerminalMod.LOGGER.info("Loaded {} input and {} output dual hatches from config",
                    INPUT_DUAL_HATCHES.size(), OUTPUT_DUAL_HATCHES.size());

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Failed to load dual hatch configuration", e);
            createDefault();
            save();
        }
    }

    public static List<DualHatchEntry> getInputDualHatches() {
        return Collections.unmodifiableList(INPUT_DUAL_HATCHES);
    }

    public static List<DualHatchEntry> getOutputDualHatches() {
        return Collections.unmodifiableList(OUTPUT_DUAL_HATCHES);
    }

    public static void reload() {
        load();
    }
}