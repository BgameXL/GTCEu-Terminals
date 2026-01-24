package com.gtceuterminal.client;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.network.CPacketOpenMultiStructureUI;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

    // Handles keybindings for the mod
@Mod.EventBusSubscriber(modid = GTCEUTerminalMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeybindHandler {

    public static final KeyMapping OPEN_MULTI_MANAGER = new KeyMapping(
            "key.gtceuterminal.open_multi_manager",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_M,
            "key.categories.gtceuterminal"
    );

    @SubscribeEvent
    public static void registerKeybinds(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MULTI_MANAGER);
        GTCEUTerminalMod.LOGGER.info("Registered keybinds");
    }

    @Mod.EventBusSubscriber(modid = GTCEUTerminalMod.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft mc = Minecraft.getInstance();

                if (OPEN_MULTI_MANAGER.consumeClick()) {
                    if (mc.player != null && mc.screen == null) {
                        GTCEUTerminalMod.LOGGER.info("Opening Multi-Structure Manager via keybind");
                        InteractionHand hand = mc.player.getOffhandItem().getItem() instanceof com.gtceuterminal.common.item.MultiStructureManagerItem
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND;
                        TerminalNetwork.CHANNEL.sendToServer(new CPacketOpenMultiStructureUI(hand));
                    }
                }
            }
        }
    }
}