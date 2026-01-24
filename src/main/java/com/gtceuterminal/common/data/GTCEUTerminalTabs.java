package com.gtceuterminal.common.data;

import com.gtceuterminal.GTCEUTerminalMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class GTCEUTerminalTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GTCEUTerminalMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> GTCEU_TERMINAL_TAB = CREATIVE_MODE_TABS.register(
            "gtceu_terminal",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gtceuterminal"))

                    .icon(() -> new ItemStack(GTCEUTerminalItems.MULTI_STRUCTURE_MANAGER.get()))

                    .displayItems((parameters, output) -> {
                        output.accept(GTCEUTerminalItems.MULTI_STRUCTURE_MANAGER.get());

                        output.accept(GTCEUTerminalItems.SCHEMATIC_INTERFACE.get());

                        output.accept(GTCEUTerminalItems.DISMANTLER.get());
                    })
                    .build()
    );
    public static final GTCEUTerminalTabs CREATIVE_TABS =  new GTCEUTerminalTabs();

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
        GTCEUTerminalMod.LOGGER.info("Registering GTCEu Terminal creative tabs");
    }
}