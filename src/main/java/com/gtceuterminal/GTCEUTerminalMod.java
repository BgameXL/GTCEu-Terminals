package com.gtceuterminal;

import com.gtceuterminal.common.ae2.AE2Integration;
import com.gtceuterminal.common.config.*;
import com.gtceuterminal.common.data.GTCEUTerminalItems;
import com.gtceuterminal.common.data.GTCEUTerminalTabs;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.gtceuterminal.client.gui.factory.MultiStructureUIFactory;
import com.gtceuterminal.client.gui.factory.DismantlerUIFactory;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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