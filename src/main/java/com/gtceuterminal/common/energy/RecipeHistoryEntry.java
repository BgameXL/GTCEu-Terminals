package com.gtceuterminal.common.energy;

import net.minecraft.network.FriendlyByteBuf;

// Data class representing a single entry in the recipe history of a machine.
public class RecipeHistoryEntry {

    public final String outputName;     // e.g. "Hot Nichrome Ingot"
    public final int    durationTicks;  // actual duration after OC
    public final long   timestamp;      // System.currentTimeMillis() when started
    public final boolean completed;     // true = finished, false = interrupted

    public RecipeHistoryEntry(String outputName, int durationTicks,
                               long timestamp, boolean completed) {
        this.outputName    = outputName;
        this.durationTicks = durationTicks;
        this.timestamp     = timestamp;
        this.completed     = completed;
    }

    // ─── Network ─────────────────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(outputName);
        buf.writeInt(durationTicks);
        buf.writeLong(timestamp);
        buf.writeBoolean(completed);
    }

    public static RecipeHistoryEntry decode(FriendlyByteBuf buf) {
        return new RecipeHistoryEntry(
                buf.readUtf(),
                buf.readInt(),
                buf.readLong(),
                buf.readBoolean()
        );
    }

    // ─── Display helpers ──────────────────────────────────────────────────────
    public int secondsAgo() {
        return (int) ((System.currentTimeMillis() - timestamp) / 1000);
    }

    public String durationStr() {
        int secs = durationTicks / 20;
        if (secs < 60) return secs + "s";
        return (secs / 60) + "m " + (secs % 60) + "s";
    }
}