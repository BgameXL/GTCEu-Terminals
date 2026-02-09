package com.gtceuterminal.common.config;

import com.gtceuterminal.GTCEUTerminalMod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Registry for component patterns
public class ComponentPatternRegistry {
    
    private static final List<ComponentPattern> patterns = new ArrayList<>();
    private static boolean initialized = false;
    
    // Initialize the registry (load patterns from config)
    public static void initialize() {
        if (initialized) return;
        
        GTCEUTerminalMod.LOGGER.info("Initializing Component Pattern Registry");
        loadPatterns();
        initialized = true;
    }
    
    // Load patterns from config files
    public static void loadPatterns() {
        patterns.clear();
        
        try {
            // Load default patterns
            PatternConfigLoader.loadDefaultPatterns(patterns);
            
            // Load custom patterns (if exists)
            PatternConfigLoader.loadCustomPatterns(patterns);
            
            // Sort by priority (highest first)
            patterns.sort(Comparator.comparingInt(ComponentPattern::getPriority).reversed());
            
            GTCEUTerminalMod.LOGGER.info("Loaded {} component patterns", patterns.size());
            
            // Log patterns for debugging
            if (GTCEUTerminalMod.LOGGER.isDebugEnabled()) {
                for (ComponentPattern pattern : patterns) {
                    GTCEUTerminalMod.LOGGER.debug("Pattern: {} -> {} (priority: {})", 
                        pattern.getPattern(), 
                        pattern.getComponentType(), 
                        pattern.getPriority());
                }
            }
            
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Failed to load component patterns", e);
        }
    }

    public static void reload() {
        GTCEUTerminalMod.LOGGER.info("Reloading component patterns");
        loadPatterns();
    }

    public static ComponentPattern findMatch(String blockId) {
        if (blockId == null) return null;
        
        for (ComponentPattern pattern : patterns) {
            if (pattern.matches(blockId)) {
                return pattern;
            }
        }
        
        return null;
    }

    public static List<ComponentPattern> findAllMatches(String blockId) {
        List<ComponentPattern> matches = new ArrayList<>();
        
        if (blockId == null) return matches;
        
        for (ComponentPattern pattern : patterns) {
            if (pattern.matches(blockId)) {
                matches.add(pattern);
            }
        }
        
        return matches;
    }

    public static void registerPattern(ComponentPattern pattern) {
        patterns.add(pattern);
        patterns.sort(Comparator.comparingInt(ComponentPattern::getPriority).reversed());
    }

    public static List<ComponentPattern> getAllPatterns() {
        return new ArrayList<>(patterns);
    }

    public static int getPatternCount() {
        return patterns.size();
    }

    public static void clear() {
        patterns.clear();
    }
}