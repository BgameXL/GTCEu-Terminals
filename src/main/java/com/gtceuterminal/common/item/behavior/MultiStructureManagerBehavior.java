package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.ClientProxy;
import com.gtceuterminal.client.gui.multiblock.ManagerSettingsUI;
import com.gtceuterminal.common.pattern.AdvancedAutoBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
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
import net.minecraft.ChatFormatting;

import org.jetbrains.annotations.NotNull;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MultiStructureManagerBehavior {

    private final int cooldownTicks;
    private final boolean enableSounds;

    public MultiStructureManagerBehavior(int cooldownTicks, boolean enableSounds) {
        this.cooldownTicks = cooldownTicks;
        this.enableSounds = enableSounds;
    }

    public MultiStructureManagerBehavior() {
        this(20, true);
    }

    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();

        // Check if clicking on Wireless Access Point to link
        BlockEntity be = level.getBlockEntity(blockPos);
        if (be != null) {
            try {
                Class<?> wapClass = Class.forName("appeng.api.implementations.blockentities.IWirelessAccessPoint");
                if (wapClass.isInstance(be)) {
                    if (!level.isClientSide) {
                        // Link the item to this Access Point
                        GlobalPos globalPos = GlobalPos.of(level.dimension(), blockPos);
                        CompoundTag tag = itemStack.getOrCreateTag();
                        tag.put("accessPoint", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, globalPos)
                                .getOrThrow(false, err -> {}));

                        player.displayClientMessage(
                                Component.literal("✓ Linked to ME Network").withStyle(ChatFormatting.GREEN),
                                true
                        );

                        playSound(level, blockPos, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            } catch (ClassNotFoundException ignored) {
                // AE2 not installed, skip ME Network linking
            }
        }

        // Shift+Click handling with settings
        if (player.isShiftKeyDown()) {
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);

            // Case 1: Clicking on multiblock controller → Auto-build
            if (machine instanceof IMultiController controller) {
                // Auto-build if not formed
                if (!controller.isFormed()) {
                    if (!level.isClientSide) {
                        if (cooldownTicks > 0 && player.getCooldowns().isOnCooldown(itemStack.getItem())) {
                            sendMessage(player, "§cCooldown active!", false);
                            return InteractionResult.FAIL;
                        }

                        // Read settings correctly
                        ManagerSettingsUI.Settings settings = new ManagerSettingsUI.Settings(itemStack);
                        ManagerSettingsUI.AutoBuildSettings buildSettings = settings.toAutoBuildSettings();

                        // GTCEUTerminalMod.LOGGER.info("Auto-building with settings: RepeatCount={}, NoHatchMode={}, TierMode={}, IsUseAE{}",
                                // buildSettings.repeatCount, buildSettings.noHatchMode, buildSettings.tierMode, buildSettings.isUseAE);

                        try {
                            // Use AdvancedAutoBuilder with settings support
                            boolean success = AdvancedAutoBuilder.autoBuild(player, controller, buildSettings);

                            if (success) {
                                if (cooldownTicks > 0) {
                                    player.getCooldowns().addCooldown(itemStack.getItem(), cooldownTicks);
                                }

                                sendMessage(player, "§aMultiblock built!", true);
                                playSound(level, blockPos, SoundEvents.ANVIL_USE, 1.0f, 1.2f);
                            } else {
                                sendMessage(player, "§cFailed to build! Check materials.", false);
                                playSound(level, blockPos, SoundEvents.ANVIL_LAND, 0.5f, 0.8f);
                            }

                        } catch (Exception e) {
                            GTCEUTerminalMod.LOGGER.error("Error during auto-build", e);
                            sendMessage(player, "§cFailed to build!", false);
                            playSound(level, blockPos, SoundEvents.ANVIL_LAND, 0.5f, 0.8f);
                            return InteractionResult.FAIL;
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                // If already formed, show message
                if (!level.isClientSide) {
                    sendMessage(player, "§aMultiblock is already formed!", true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            // Case 2: Shift+Click on non-controller → Open Multi-Structure Manager
            if (level.isClientSide) {
                GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Multi-Structure Manager");
                ClientProxy.openMultiStructureGUI(itemStack, player);
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.CONSUME;
        }

        // Normal click (no shift) → Open Settings UI
        if (level.isClientSide) {
            GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Manager Settings");
            ClientProxy.openManagerSettingsGUI(itemStack, player);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.CONSUME;
    }

    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Item item, @NotNull Level level,
                                                           @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        // Shift + Right-click in air: Open Multi-Structure Manager
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Multi-Structure Manager");
                ClientProxy.openMultiStructureGUI(itemStack, player);
                return InteractionResultHolder.success(itemStack);
            }
            return InteractionResultHolder.consume(itemStack);
        }

        // Normal right-click in air: Open Settings UI
        if (level.isClientSide) {
            GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Manager Settings");
            ClientProxy.openManagerSettingsGUI(itemStack, player);
            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.consume(itemStack);
    }

    private void sendMessage(@NotNull Player player, @NotNull String message, boolean isSuccess) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private void playSound(@NotNull Level level, @NotNull BlockPos pos,
                           @NotNull net.minecraft.sounds.SoundEvent sound,
                           float volume, float pitch) {
        if (enableSounds) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}