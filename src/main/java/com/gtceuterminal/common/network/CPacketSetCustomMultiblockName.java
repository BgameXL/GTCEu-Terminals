package com.gtceuterminal.common.network;

import com.gtceuterminal.common.item.MultiStructureManagerItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Saves/clears a custom multiblock name on the Multi-Structure Manager item.
public class CPacketSetCustomMultiblockName {

    private final int handOrdinal;
    private final String key;
    private final String name;
    private final boolean reset;

    public CPacketSetCustomMultiblockName(InteractionHand hand, String key, String name, boolean reset) {
        this.handOrdinal = hand == null ? 0 : hand.ordinal();
        this.key = key == null ? "" : key;
        this.name = name == null ? "" : name;
        this.reset = reset;
    }

    public CPacketSetCustomMultiblockName(FriendlyByteBuf buf) {
        this.handOrdinal = buf.readVarInt();
        this.key = buf.readUtf(256);
        this.name = buf.readUtf(64);
        this.reset = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeUtf(key, 256);
        buf.writeUtf(name, 64);
        buf.writeBoolean(reset);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            InteractionHand hand = InteractionHand.MAIN_HAND;
            InteractionHand[] values = InteractionHand.values();
            if (handOrdinal >= 0 && handOrdinal < values.length) {
                hand = values[handOrdinal];
            }

            ItemStack stack = player.getItemInHand(hand);

            // Fallback: if the player moved the item, try the other hand
            if (stack.isEmpty() || !(stack.getItem() instanceof MultiStructureManagerItem)) {
                InteractionHand other = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack otherStack = player.getItemInHand(other);
                if (!otherStack.isEmpty() && otherStack.getItem() instanceof MultiStructureManagerItem) {
                    stack = otherStack;
                }
            }

            if (stack.isEmpty() || !(stack.getItem() instanceof MultiStructureManagerItem)) {
                return;
            }

            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag names = tag.getCompound("CustomMultiblockNames");

            if (reset || key.isEmpty()) {
                if (!key.isEmpty()) {
                    names.remove(key);
                }
            } else {
                String clean = name.trim();
                if (clean.length() > 32) clean = clean.substring(0, 32);

                if (clean.isEmpty()) {
                    names.remove(key);
                } else {
                    names.putString(key, clean);
                }
            }

            tag.put("CustomMultiblockNames", names);
            stack.setTag(tag);

            // Ensure inventory sync
            player.getInventory().setChanged();
        });

        ctx.get().setPacketHandled(true);
    }
}