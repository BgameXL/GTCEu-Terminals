package com.gtceuterminal.client;

import com.gtceuterminal.client.renderer.SchematicPreviewRenderer;
import com.gtceuterminal.common.data.SchematicData;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        // Check if player is holding schematic Terminal
        ItemStack heldItem = minecraft.player.getMainHandItem();
        if (!heldItem.getItem().toString().contains("schematic_interface")) {
            heldItem = minecraft.player.getOffhandItem();
            if (!heldItem.getItem().toString().contains("schematic_interface")) {
                return;
            }
        }

        CompoundTag tag = heldItem.getTag();
        if (tag == null || !tag.contains("Clipboard")) {
            return;
        }

        try {
            SchematicData clipboard = SchematicData.fromNBT(
                    tag.getCompound("Clipboard"),
                    minecraft.level.registryAccess()
            );

            if (clipboard.getBlocks().isEmpty()) {
                return;
            }

            // Setup rendering
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

            poseStack.pushPose();

            // Enable transparency
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // 50% transparent

            // Render ghost blocks with clipboard tag for original facing
            SchematicPreviewRenderer.renderGhostBlocks(poseStack, bufferSource, clipboard, minecraft, tag.getCompound("Clipboard"));

            bufferSource.endBatch();

            // Reset rendering state
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            poseStack.popPose();

        } catch (Exception e) {
            
        }
    }
}