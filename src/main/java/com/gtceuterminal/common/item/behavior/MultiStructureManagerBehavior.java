package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.config.ManagerSettings;
import com.gtceuterminal.common.pattern.AdvancedAutoBuilder;

import com.gtceuterminal.client.gui.factory.MultiStructureManagerUIFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.NotNull;

public class MultiStructureManagerBehavior {

    private final int cooldownTicks;
    private final boolean enableSounds;

    public MultiStructureManagerBehavior(int cooldownTicks, boolean enableSounds) {
        this.cooldownTicks = cooldownTicks;
        this.enableSounds  = enableSounds;
    }

    public MultiStructureManagerBehavior() {
        this(20, true);
    }

    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player    player    = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level     level     = context.getLevel();
        BlockPos  blockPos  = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();
        InteractionHand hand = context.getHand();

        // Check if clicking on Wireless Access Point to link
        BlockEntity be = level.getBlockEntity(blockPos);
        if (be != null) {
            try {
                Class<?> wapClass = Class.forName("appeng.api.implementations.blockentities.IWirelessAccessPoint");
                if (wapClass.isInstance(be)) {
                    if (!level.isClientSide) {
                        GlobalPos globalPos = GlobalPos.of(level.dimension(), blockPos);
                        CompoundTag tag = itemStack.getOrCreateTag();
                        tag.put("accessPoint", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, globalPos)
                                .getOrThrow(false, err -> {}));
                        player.displayClientMessage(
                                Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.linked_to_me_network"),
                                true);
                        playSound(level, blockPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            } catch (ClassNotFoundException ignored) {}
        }

        if (player.isShiftKeyDown()) {
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);

            // Case 1: Shift+Click on unformed controller → Auto-build
            if (machine instanceof IMultiController controller) {
                if (!controller.isFormed()) {
                    if (!level.isClientSide) {
                        if (cooldownTicks > 0 && player.getCooldowns().isOnCooldown(itemStack.getItem())) {
                            sendMessage(player,
                                    Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.cooldown_active"),
                                    false);
                            return InteractionResult.FAIL;
                        }
                        ManagerSettings.Settings settings = new ManagerSettings.Settings(itemStack);
                        ManagerSettings.AutoBuildSettings buildSettings = settings.toAutoBuildSettings();
                        try {
                            boolean success = AdvancedAutoBuilder.autoBuild(player, controller, buildSettings);
                            if (success) {
                                if (cooldownTicks > 0)
                                    player.getCooldowns().addCooldown(itemStack.getItem(), cooldownTicks);
                                sendMessage(player,
                                        Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.multiblock_built"),
                                        true);
                                playSound(level, blockPos, SoundEvents.ANVIL_USE, 1.0f, 1.2f);
                            } else {
                                sendMessage(player,
                                        Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.failed_to_build_check_materials"),
                                        false);
                                playSound(level, blockPos, SoundEvents.ANVIL_LAND, 0.5f, 0.8f);
                            }
                        } catch (Exception e) {
                            GTCEUTerminalMod.LOGGER.error("Error during auto-build", e);
                            sendMessage(player,
                                    Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.failed_to_build"),
                                    false);
                            playSound(level, blockPos, SoundEvents.ANVIL_LAND, 0.5f, 0.8f);
                            return InteractionResult.FAIL;
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (!level.isClientSide) {
                    sendMessage(player,
                            Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.multiblock_already_formed"),
                            true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            // Case 2: Shift+Click on non-controller → Open Multi-Structure Manager
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                GTCEUTerminalMod.LOGGER.info("Server: opening Multi-Structure Manager UI");
                MultiStructureManagerUIFactory.INSTANCE.openMultiStructure(sp, itemStack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Normal click (no shift) → if on unformed controller, analyze for autocraft
        {
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);
            if (machine instanceof IMultiController controller && !controller.isFormed()) {
                if (!level.isClientSide) {
                    if (cooldownTicks > 0 && player.getCooldowns().isOnCooldown(itemStack.getItem())) {
                        sendMessage(player,
                                Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.cooldown_active"),
                                false);
                        return InteractionResult.FAIL;
                    }
                    try {
                        ManagerSettings.Settings raw = new ManagerSettings.Settings(itemStack);
                        ManagerSettings.AutoBuildSettings buildSettings = raw.toAutoBuildSettings();

                        com.gtceuterminal.common.autocraft.AnalysisResult analysis =
                                com.gtceuterminal.common.autocraft.MultiblockAnalyzer.analyzeForBuild(
                                        player, controller, buildSettings);

                        if (analysis == null || analysis.entries.isEmpty()) {
                            sendMessage(player,
                                    Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.nothing_to_build"),
                                    true);
                            return InteractionResult.sidedSuccess(false);
                        }

                        com.gtceuterminal.common.network.TerminalNetwork.sendToPlayer(
                                new com.gtceuterminal.common.network.SPacketAnalysisResult(analysis),
                                (ServerPlayer) player);

                    } catch (Exception e) {
                        GTCEUTerminalMod.LOGGER.error("Error during autocraft analysis", e);
                        sendMessage(player,
                                Component.translatable("item.gtceuterminal.multi_structure_manager.behavior.analysis_failed"),
                                false);
                        return InteractionResult.FAIL;
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Normal click (no shift) on non-controller → Open Manager Settings
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            GTCEUTerminalMod.LOGGER.info("Server: opening Manager Settings UI");
            MultiStructureManagerUIFactory.INSTANCE.openManagerSettings(sp, itemStack);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Item item, @NotNull Level level,
                                                           @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Shift + Right-click in air → Open Multi-Structure Manager
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                GTCEUTerminalMod.LOGGER.info("Server: opening Multi-Structure Manager UI");
                MultiStructureManagerUIFactory.INSTANCE.openMultiStructure(sp, itemStack);
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
        }

        // Normal right-click in air → Open Manager Settings
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            GTCEUTerminalMod.LOGGER.info("Server: opening Manager Settings UI");
            MultiStructureManagerUIFactory.INSTANCE.openManagerSettings(sp, itemStack);
        }
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }

    private void sendMessage(@NotNull Player player, @NotNull Component message, boolean actionBar) {
        player.displayClientMessage(message, actionBar);
    }

    private void playSound(@NotNull Level level, @NotNull BlockPos pos,
                           @NotNull net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (enableSounds) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}