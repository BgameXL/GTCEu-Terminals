package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.multiblock.DismantleExecutor;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CPacketDismantle {

    private final BlockPos controllerPos;

    public CPacketDismantle(BlockPos controllerPos) {
        this.controllerPos = controllerPos;
    }

    public static void encode(CPacketDismantle msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.controllerPos);
    }

    public static CPacketDismantle decode(FriendlyByteBuf buffer) {
        return new CPacketDismantle(buffer.readBlockPos());
    }

    public static void handle(CPacketDismantle msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                
                GTCEUTerminalMod.LOGGER.info("Dismantling multiblock at {} for player {}",
                        msg.controllerPos, player.getName().getString());

                // Verify that it is a multiblock controller
                BlockEntity blockEntity = serverLevel.getBlockEntity(msg.controllerPos);
                
                if (blockEntity instanceof IMachineBlockEntity mbe) {
                    var metaMachine = mbe.getMetaMachine();
                    
                    if (metaMachine instanceof MultiblockControllerMachine controller) {
                        if (controller.isFormed()) {
                            // Perform dismantling
                            boolean success = DismantleExecutor.dismantleMultiblock(
                                    serverLevel, player, controller
                            );
                            
                            if (success) {
                                GTCEUTerminalMod.LOGGER.info("Successfully dismantled multiblock");
                            } else {
                                GTCEUTerminalMod.LOGGER.error("Failed to dismantle multiblock");
                            }
                        } else {
                            GTCEUTerminalMod.LOGGER.warn("Multiblock is not formed");
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}