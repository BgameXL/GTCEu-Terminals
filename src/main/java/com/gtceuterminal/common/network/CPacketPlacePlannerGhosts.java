package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.util.SchematicUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// This is just dead code, but I'll keep it around in case I want to re-enable the "Build All" feature later.
public class CPacketPlacePlannerGhosts {

    public static final class GhostEntry {
        public final CompoundTag schematicNbt;
        public final BlockPos    origin;
        public final int         rotSteps;

        public GhostEntry(CompoundTag nbt, BlockPos origin, int rotSteps) {
            this.schematicNbt = nbt;
            this.origin       = origin;
            this.rotSteps     = rotSteps;
        }
    }

    private final List<GhostEntry> ghosts;

    public CPacketPlacePlannerGhosts(List<GhostEntry> ghosts) {
        this.ghosts = ghosts;
    }

    // ── Encode / Decode ───────────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(ghosts.size());
        for (GhostEntry g : ghosts) {
            buf.writeNbt(g.schematicNbt);
            buf.writeBlockPos(g.origin);
            buf.writeInt(g.rotSteps);
        }
    }

    public CPacketPlacePlannerGhosts(FriendlyByteBuf buf) {
        int count = buf.readInt();
        this.ghosts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ghosts.add(new GhostEntry(buf.readNbt(), buf.readBlockPos(), buf.readInt()));
        }
    }

    // ── Handler ───────────────────────────────────────────────────────────────
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!com.gtceuterminal.common.config.ItemsConfig.isSchPlannerBuildAllEnabled()) {
                player.displayClientMessage(
                        Component.translatable("item.gtceuterminal.planner_build_all.message.disabled"),
                        false
                );
                return;
            }

            ServerLevel level   = player.serverLevel();
            int totalPlaced     = 0;
            int totalSkipped    = 0;

            for (GhostEntry entry : ghosts) {
                try {
                    SchematicData schematic = SchematicData.fromNBT(
                            entry.schematicNbt, level.registryAccess());
                    int[] result = placeSchematic(player, level, schematic,
                            entry.origin, entry.rotSteps);
                    totalPlaced  += result[0];
                    totalSkipped += result[1];
                } catch (Exception e) {
                    GTCEUTerminalMod.LOGGER.error(
                            "CPacketPlacePlannerGhosts: failed to place ghost at {}", entry.origin, e);
                }
            }

            Component msg;
            if (totalSkipped == 0) {
                msg = Component.translatable(
                        "item.gtceuterminal.planner_build_all.message.success_no_skipped",
                        totalPlaced, ghosts.size()
                );
            } else {
                msg = Component.translatable(
                        "item.gtceuterminal.planner_build_all.message.success_with_skipped",
                        totalPlaced, totalSkipped
                );
            }
            player.displayClientMessage(msg, false);
        });
        ctx.get().setPacketHandled(true);
    }

    // ── Placement logic ───────────────────────────────────────────────────────
    private static int[] placeSchematic(ServerPlayer player, ServerLevel level,
                                        SchematicData schematic,
                                        BlockPos origin, int rotSteps) {
        int placed  = 0;
        int skipped = 0;

        for (Map.Entry<BlockPos, BlockState> e : schematic.getBlocks().entrySet()) {
            BlockState state = e.getValue();
            if (state.isAir()) continue;

            BlockPos   relRotated   = SchematicUtils.rotatePositionSteps(e.getKey(), rotSteps);
            BlockState stateRotated = SchematicUtils.rotateBlockStateSteps(state, rotSteps);
            BlockPos   worldPos     = origin.offset(relRotated);

            if (!level.isEmptyBlock(worldPos)) { skipped++; continue; }

            boolean didPlace = false;

            if (player.isCreative()) {
                level.setBlock(worldPos, stateRotated, 3);
                didPlace = true;
            } else {
                ItemStack found = findAndConsumeItem(player, stateRotated);
                if (found != null) {
                    level.setBlock(worldPos, stateRotated, 3);
                    didPlace = true;
                } else {
                    skipped++;
                }
            }

            if (didPlace) placed++;
        }

        return new int[]{ placed, skipped };
    }

    private static ItemStack findAndConsumeItem(ServerPlayer player, BlockState targetState) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) continue;
            if (bi.getBlock().defaultBlockState().getBlock() == targetState.getBlock()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                stack.shrink(1);
                return copy;
            }
        }
        return null;
    }
}