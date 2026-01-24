package com.gtceuterminal.common.multiblock;

import java.util.*;

public class TierProgression {
    
    // Coil progression
    private static final List<String> COIL_TIERS = Arrays.asList(
        "cupronickel_coil_block",
        "kanthal_coil_block", 
        "nichrome_coil_block",
        "rtm_alloy_coil_block",
        "hssg_coil_block",
        "naquadah_coil_block",
        "trinium_coil_block",
        "tritanium_coil_block"
    );
    
    // Machine casing progression (it has a specific use, documentation and config files later)
    private static final List<String> CASING_TIERS = Arrays.asList(
        "lv_machine_casing",
        "mv_machine_casing",
        "hv_machine_casing",
        "ev_machine_casing",
        "iv_machine_casing",
        "luv_machine_casing",
        "zpm_machine_casing",
        "uv_machine_casing",
        "uhv_machine_casing"
    );
    
    // Energy hatch
    private static final List<String> VOLTAGE_TIERS = Arrays.asList(
        "ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"
    );

    public static String getNextTier(String currentPath) {
        // Check coils
        for (int i = 0; i < COIL_TIERS.size() - 1; i++) {
            if (currentPath.contains(COIL_TIERS.get(i))) {
                return COIL_TIERS.get(i + 1);
            }
        }
        
        // Check casings
        for (int i = 0; i < CASING_TIERS.size() - 1; i++) {
            if (currentPath.contains(CASING_TIERS.get(i))) {
                return CASING_TIERS.get(i + 1);
            }
        }
        
        // Check voltage tiers
        for (int i = 0; i < VOLTAGE_TIERS.size() - 1; i++) {
            if (currentPath.contains("." + VOLTAGE_TIERS.get(i) + ".") || 
                currentPath.endsWith("." + VOLTAGE_TIERS.get(i))) {
                String nextTier = VOLTAGE_TIERS.get(i + 1);
                return currentPath.replace(VOLTAGE_TIERS.get(i), nextTier);
            }
        }
        
        return null;
    }

    public static String getPreviousTier(String currentPath) {
        // Check coils
        for (int i = 1; i < COIL_TIERS.size(); i++) {
            if (currentPath.contains(COIL_TIERS.get(i))) {
                return COIL_TIERS.get(i - 1);
            }
        }
        
        // Check casings
        for (int i = 1; i < CASING_TIERS.size(); i++) {
            if (currentPath.contains(CASING_TIERS.get(i))) {
                return CASING_TIERS.get(i - 1);
            }
        }
        
        // Check voltage tiers
        for (int i = 1; i < VOLTAGE_TIERS.size(); i++) {
            if (currentPath.contains("." + VOLTAGE_TIERS.get(i) + ".") || 
                currentPath.endsWith("." + VOLTAGE_TIERS.get(i))) {
                String prevTier = VOLTAGE_TIERS.get(i - 1);
                return currentPath.replace(VOLTAGE_TIERS.get(i), prevTier);
            }
        }
        
        return null;
    }

    public static List<String> getAllTiers(String currentPath) {
        List<String> tiers = new ArrayList<>();
        
        // Check coils
        for (String coil : COIL_TIERS) {
            if (currentPath.contains(coil)) {
                return new ArrayList<>(COIL_TIERS);
            }
        }
        
        // Check casings
        for (String casing : CASING_TIERS) {
            if (currentPath.contains(casing)) {
                return new ArrayList<>(CASING_TIERS);
            }
        }
        
        // Check voltage tiers
        for (String tier : VOLTAGE_TIERS) {
            if (currentPath.contains("." + tier + ".") || currentPath.endsWith("." + tier)) {
                for (String newTier : VOLTAGE_TIERS) {
                    tiers.add(currentPath.replace(tier, newTier));
                }
                return tiers;
            }
        }
        
        return tiers;
    }

    public static int getTierIndex(String path) {
        // Coils
        for (int i = 0; i < COIL_TIERS.size(); i++) {
            if (path.contains(COIL_TIERS.get(i))) return i;
        }
        
        // Casings (needs work later, some projects uses casings as tiered blocks, but not gtceu by default)
        for (int i = 0; i < CASING_TIERS.size(); i++) {
            if (path.contains(CASING_TIERS.get(i))) return i;
        }
        
        // Voltage
        for (int i = 0; i < VOLTAGE_TIERS.size(); i++) {
            if (path.contains("." + VOLTAGE_TIERS.get(i) + ".") || 
                path.endsWith("." + VOLTAGE_TIERS.get(i))) return i;
        }
        
        return -1;
    }

    public static boolean canUpgrade(String path) {
        return getNextTier(path) != null;
    }

    public static boolean canDowngrade(String path) {
        return getPreviousTier(path) != null;
    }

    public static String getTierName(int index) {
        if (index >= 0 && index < VOLTAGE_TIERS.size()) {
            return VOLTAGE_TIERS.get(index).toUpperCase();
        }
        return "UNKNOWN";
    }
}