package com.gtceuterminal.common.network;

import com.gtceuterminal.common.item.MultiStructureManagerItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Client → Server: set or reset a custom name for a multiblock structure in the Multi-Structure Manager item.
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

            // Search both hands first, then full inventory (rename can be triggered from MSM UI without holding it)
            ItemStack stack = null;
            for (InteractionHand h : InteractionHand.values()) {
                ItemStack s = player.getItemInHand(h);
                if (!s.isEmpty() && s.getItem() instanceof MultiStructureManagerItem) { stack = s; break; }
            }
            if (stack == null) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (!s.isEmpty() && s.getItem() instanceof MultiStructureManagerItem) { stack = s; break; }
                }
            }
            if (stack == null || stack.isEmpty()) {
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