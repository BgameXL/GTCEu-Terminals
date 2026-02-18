/** package com.gtceuterminal.client;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.renderer.PowerMonitorRenderer;
import com.gtceuterminal.common.data.GTCEUTerminalBlockEntities;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = GTCEUTerminalMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RendererRegistry {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        GTCEUTerminalMod.LOGGER.info("Registering BlockEntity Renderers...");
        
        event.registerBlockEntityRenderer(
            GTCEUTerminalBlockEntities.POWER_MONITOR.get(),
            PowerMonitorRenderer::new
        );
        
        GTCEUTerminalMod.LOGGER.info("✓ Registered PowerMonitorRenderer");
    }
} **/