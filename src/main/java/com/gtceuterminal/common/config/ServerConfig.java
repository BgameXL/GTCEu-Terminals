package com.gtceuterminal.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public class ServerConfig {
    public static final String FILE_NAME = "gtceuterminal-server.toml";
    public static final ForgeConfigSpec SPEC;

    // Configuration options
    public static final ForgeConfigSpec.IntValue MULTIBLOCK_SCAN_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AE2_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("GTCEu Terminal Addon Server Configuration")
                .push("general");

        MULTIBLOCK_SCAN_RADIUS = builder
                .comment("Radius in blocks for multiblock scanning (default: 32)")
                .defineInRange("multiblockScanRadius", 32, 8, 128);

        ENABLE_AE2_INTEGRATION = builder
                .comment("Enable Applied Energistics 2 integration (default: true)")
                .define("enableAE2Integration", true);

        ENABLE_DEBUG_LOGGING = builder
                .comment("Enable debug logging for troubleshooting (default: false)")
                .define("enableDebugLogging", false);

        builder.pop();

        SPEC = builder.build();
    }

    public static int getMultiblockScanRadius() {
        return MULTIBLOCK_SCAN_RADIUS.get();
    }

    public static boolean isAE2IntegrationEnabled() {
        return ENABLE_AE2_INTEGRATION.get();
    }

    public static boolean isDebugLoggingEnabled() {
        return ENABLE_DEBUG_LOGGING.get();
    }
}