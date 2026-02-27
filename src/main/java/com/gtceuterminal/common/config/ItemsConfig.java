package com.gtceuterminal.common.config;

import com.gtceuterminal.GTCEUTerminalMod;

import java.util.List;

// Configuration for items the Energy Analyzer, Multi-Structure Manager, and Schematic Interface.
public class ItemsConfig {

    private static final YamlConfigLoader LOADER = new YamlConfigLoader("", "gtceuterminals.yaml");

    // ─── Energy Analyzer ─────────────────────────────────────────────────────
    private static int     ea_maxLinkedMachines    = 16;
    private static int     ea_refreshIntervalTicks = 20;
    private static boolean ea_showPerHatchDetails  = true;
    private static int     ea_historySeconds       = 30;

    // ─── Multi Structure Manager ──────────────────────────────────────────────
    private static int     mgr_maxDetectedMultiblocks = 32;
    private static boolean mgr_allowMENetworkUpgrade  = true;
    private static int     mgr_highlightDurationSec   = 10;
    private static int     mgr_highlightColor         = 0x00AAFF;

    // ─── Schematic Interface ──────────────────────────────────────────────────
    private static boolean sch_allowAE2ConfigCopy = true;

    // ─── Load ─────────────────────────────────────────────────────────────────
    public static void load() {
        LOADER.writeDefaults(List.of(

                // Energy Analyzer
                YamlConfigLoader.ConfigEntry.of("energy_analyzer.max_linked_machines", 16,
                        "[Energy Analyzer] Maximum machines linkable per item (1-64)"),
                YamlConfigLoader.ConfigEntry.of("energy_analyzer.refresh_interval_ticks", 20,
                        "[Energy Analyzer] How often energy data is refreshed in ticks (20 = 1s, min 5)"),
                YamlConfigLoader.ConfigEntry.of("energy_analyzer.show_per_hatch_details", true,
                        "[Energy Analyzer] Show per-hatch energy breakdown in the UI"),
                YamlConfigLoader.ConfigEntry.of("energy_analyzer.history_seconds", 30,
                        "[Energy Analyzer] Seconds of energy history to graph (5-120)"),

                // Multi Structure Manager
                YamlConfigLoader.ConfigEntry.of("manager.max_detected_multiblocks", 32,
                        "[Manager] Maximum number of multiblocks detected and shown in the UI (1-128)"),
                YamlConfigLoader.ConfigEntry.of("manager.allow_me_network_upgrade", true,
                        "[Manager] Allow upgrading components using items from a linked ME network (requires AE2)"),
                YamlConfigLoader.ConfigEntry.of("manager.highlight_duration_seconds", 10,
                        "[Manager] How long the multiblock highlight lasts in the world after clicking (seconds, 1-60)"),
                YamlConfigLoader.ConfigEntry.of("manager.highlight_color", "00AAFF",
                        "[Manager] Highlight color as hex RGB, e.g. 00AAFF = blue, FFAA00 = orange, 00FF88 = green"),

                // Schematic Interface
                YamlConfigLoader.ConfigEntry.of("schematic.allow_ae2_config_copy", true,
                        "[Schematic] Preserve AE2/ME bus slot configurations when copying and pasting multiblocks (requires AE2)")
        ));

        // ── Read Energy Analyzer ──────────────────────────────────────────────
        ea_maxLinkedMachines    = clamp(LOADER.getInt("energy_analyzer.max_linked_machines", 16), 1, 64);
        ea_refreshIntervalTicks = clamp(LOADER.getInt("energy_analyzer.refresh_interval_ticks", 20), 5, 200);
        ea_showPerHatchDetails  = LOADER.getBoolean("energy_analyzer.show_per_hatch_details", true);
        ea_historySeconds       = clamp(LOADER.getInt("energy_analyzer.history_seconds", 30), 5, 120);

        // ── Read Multi Structure Manager ──────────────────────────────────────
        mgr_maxDetectedMultiblocks = clamp(LOADER.getInt("manager.max_detected_multiblocks", 32), 1, 128);
        mgr_allowMENetworkUpgrade  = LOADER.getBoolean("manager.allow_me_network_upgrade", true);
        mgr_highlightDurationSec   = clamp(LOADER.getInt("manager.highlight_duration_seconds", 10), 1, 60);
        mgr_highlightColor         = parseHexColor(LOADER.getString("manager.highlight_color", "00AAFF"), 0x00AAFF);

        // ── Read Schematic Interface ──────────────────────────────────────────
        sch_allowAE2ConfigCopy = LOADER.getBoolean("schematic.allow_ae2_config_copy", true);

        GTCEUTerminalMod.LOGGER.info(
                "ItemsConfig loaded — EnergyAnalyzer: maxMachines={}, refresh={}t, history={}s | Manager: maxMultiblocks={}, allowME={} | Schematic: ae2Copy={}",
                ea_maxLinkedMachines, ea_refreshIntervalTicks, ea_historySeconds,
                mgr_maxDetectedMultiblocks, mgr_allowMENetworkUpgrade,
                sch_allowAE2ConfigCopy
        );
    }

    // ─── Energy Analyzer Getters ──────────────────────────────────────────────
    public static int     getEAMaxLinkedMachines()    { return ea_maxLinkedMachines; }
    public static int     getEARefreshIntervalTicks() { return ea_refreshIntervalTicks; }
    public static boolean getEAShowPerHatchDetails()  { return ea_showPerHatchDetails; }
    public static int     getEAHistorySeconds()       { return ea_historySeconds; }
    // Kept for backward compatibility — dimension filtering removed but callers still compile
    public static boolean isEADimensionAllowed(String dimensionId) { return true; }

    // ─── Multi Structure Manager Getters ──────────────────────────────────────
    public static int     getMgrMaxDetectedMultiblocks()  { return mgr_maxDetectedMultiblocks; }
    public static boolean isMgrAllowMENetworkUpgrade()    { return mgr_allowMENetworkUpgrade; }
    public static int     getMgrHighlightDurationMs()     { return mgr_highlightDurationSec * 1000; }
    public static int     getMgrHighlightColor()          { return mgr_highlightColor; }

    // ─── Schematic Interface Getters ──────────────────────────────────────────
    public static boolean isSchAllowAE2ConfigCopy() { return sch_allowAE2ConfigCopy; }

    // ─── Util ─────────────────────────────────────────────────────────────────
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int parseHexColor(String hex, int fallback) {
        try {
            String clean = hex.trim().replace("#", "").replace("0x", "").replace("0X", "");
            return (int) Long.parseLong(clean, 16) & 0xFFFFFF;
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Invalid highlight_color '{}', using default", hex);
            return fallback;
        }
    }
}