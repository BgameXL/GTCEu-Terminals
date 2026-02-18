package com.gtceuterminal.client;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.SchematicUIFactory;
import com.gtceuterminal.client.gui.factory.MultiStructureUIFactory;
import com.gtceuterminal.client.gui.factory.DismantlerUIFactory;
import com.gtceuterminal.client.gui.factory.ManagerSettingsUIFactory;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// Registers UI Factories on client side
@OnlyIn(Dist.CLIENT)
public class UIFactoryInitializer {

    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            GTCEUTerminalMod.LOGGER.info("Registering UI Factories...");

            // Register Schematic Interface Factory
            UIFactory.register(SchematicUIFactory.INSTANCE);
            GTCEUTerminalMod.LOGGER.info("✓ Registered SchematicUIFactory with ID: {}",
                    SchematicUIFactory.UI_ID);

            // Register Multi-Structure Manager Factory
            UIFactory.register(MultiStructureUIFactory.INSTANCE);
            GTCEUTerminalMod.LOGGER.info("✓ Registered MultiStructureUIFactory with ID: {}",
                    MultiStructureUIFactory.UI_ID);

            // Register Dismantler Factory
            UIFactory.register(DismantlerUIFactory.INSTANCE);
            GTCEUTerminalMod.LOGGER.info("✓ Registered DismantlerUIFactory with ID: {}",
                    DismantlerUIFactory.UI_ID);

            // Register Manager Settings Factory
            UIFactory.register(ManagerSettingsUIFactory.INSTANCE);
            GTCEUTerminalMod.LOGGER.info("✓ Registered ManagerSettingsUIFactory");

            /** Register Power Logger Factory
            UIFactory.register(PowerLoggerUIFactory.INSTANCE);
            GTCEUTerminalMod.LOGGER.info("✓ Registered PowerLoggerUIFactory");

            // Register Power Monitor Factory
            UIFactory.register(PowerMonitorUIFactory.INSTANCE);
            GTCEUTerminalMod.LOGGER.info("✓ Registered PowerMonitorUIFactory");
             **/

            GTCEUTerminalMod.LOGGER.info("All UI Factories registered successfully!");
        });
    }
}