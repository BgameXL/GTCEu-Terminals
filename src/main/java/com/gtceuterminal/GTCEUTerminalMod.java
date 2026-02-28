package com.gtceuterminal;

import com.gtceuterminal.common.ae2.AE2Integration;
import com.gtceuterminal.common.config.*;
import com.gtceuterminal.common.data.GTCEUTerminalItems;
import com.gtceuterminal.common.data.GTCEUTerminalTabs;
import com.gtceuterminal.common.network.TerminalNetwork;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;

@Mod(GTCEUTerminalMod.MOD_ID)
public class GTCEUTerminalMod {
    public static final String MOD_ID = "gtceuterminal";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public GTCEUTerminalMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GTCEUTerminalItems.ITEMS.register(modEventBus);
        GTCEUTerminalTabs.CREATIVE_TABS.register(modEventBus);

        // Register network IMMEDIATELY
        TerminalNetwork.registerPackets();
        LOGGER.info("Terminal Network packets registered");

        // UI Factories will be registered in UIFactoryInitializer during client setup
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        // Register server configuration
        // Guard: if the file exists but is empty/truncated, delete it so Forge regenerates it cleanly
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve(ServerConfig.FILE_NAME);
            if (Files.exists(configPath) && Files.size(configPath) == 0) {
                LOGGER.warn("[GTCEuTerminal] Config file {} is empty/corrupted, deleting for regeneration", ServerConfig.FILE_NAME);
                Files.delete(configPath);
            }
        } catch (Exception e) {
            LOGGER.warn("[GTCEuTerminal] Could not check config file integrity: {}", e.getMessage());
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,
                ServerConfig.SPEC,
                ServerConfig.FILE_NAME);

        LOGGER.info("GTCEu Terminal Addon initialized");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Common setup started");

        event.enqueueWork(() -> {
            LOGGER.info("Initializing component configurations...");

            CoilConfig.initialize();

            HatchConfig.initialize();

            BusConfig.initialize();

            EnergyHatchConfig.initialize();

            MufflerHatchConfig.initialize();

            ParallelHatchConfig.initialize();

            MaintenanceHatchConfig.initialize();

            ComponentRegistry.init();

            LaserHatchConfig.initialize();

            WirelessHatchConfig.initialize();

            SubstationHatchConfig.initialize();

            DualHatchConfig.initialize();

            LOGGER.info("All component configurations initialized successfully");

            LOGGER.info("Initializing AE2 integration...");
            AE2Integration.init();

        });

        LOGGER.info("Common setup complete");
    }

    private void clientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Client setup started");

        event.enqueueWork(() -> {
            com.gtceuterminal.client.UIFactoryInitializer.init(event);
        });

        LOGGER.info("Client setup complete");
    }
}