package com.gtceuterminal.common.ae2;

import appeng.api.features.GridLinkables;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.data.GTCEUTerminalItems;

/**
 * Registers GTCEu Terminal items with AE2's GridLinkables system
 * This allows them to be linked in the ME Wireless Access Point GUI
 */
public class AE2Integration {

    private static boolean initialized = false;

    /**
     * Initialize AE2 integration
     * Should be called during mod initialization (FMLCommonSetupEvent)
     */
    public static void init() {
        if (initialized) {
            return;
        }

        try {
            // Create the handler
            TerminalGridLinkableHandler handler = new TerminalGridLinkableHandler();

            // Register Multi-Structure Manager
            GridLinkables.register(GTCEUTerminalItems.MULTI_STRUCTURE_MANAGER.get(), handler);
            GTCEUTerminalMod.LOGGER.info("Registered Multi-Structure Manager with AE2 GridLinkables");

            // Register Schematic Interface
            GridLinkables.register(GTCEUTerminalItems.SCHEMATIC_INTERFACE.get(), handler);
            GTCEUTerminalMod.LOGGER.info("Registered Schematic Interface with AE2 GridLinkables");

            initialized = true;
            GTCEUTerminalMod.LOGGER.info("AE2 Integration initialized successfully");

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Failed to initialize AE2 Integration", e);
        }
    }

    // Check if AE2 integration is initialized
    public static boolean isInitialized() {
        return initialized;
    }
}