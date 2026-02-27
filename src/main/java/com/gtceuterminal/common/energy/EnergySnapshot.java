package com.gtceuterminal.common.energy;

import net.minecraft.network.FriendlyByteBuf;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

// Snapshot of a machine's energy state at a specific moment in time, sent from server to client for display in the Energy Analyzer UI.
public class EnergySnapshot {

    public enum MachineMode { CONSUMER, GENERATOR, STORAGE, UNKNOWN }

    // ─── Identity ─────────────────────────────────────────────────────────────
    public String machineName;
    public String machineType;
    public MachineMode mode;
    public boolean isFormed;

    // ─── Current energy state ────────────────────────────────────────────────
    public long energyStored;       // EU stored (long for normal machines)
    public long energyCapacity;
    public BigInteger bigStored;    // Only for BigInt storage (Power Substation)
    public BigInteger bigCapacity;
    public boolean usesBigInt;

    // ─── Flow rates ───────────────────────────────────────────────────────────
    public long inputPerSec;        // EU/s incoming
    public long outputPerSec;       // EU/s outgoing
    public long inputVoltage;       // highest input voltage tier
    public long inputAmperage;
    public long outputVoltage;
    public long outputAmperage;

    // ─── Per-hatch breakdown ──────────────────────────────────────────────────
    public List<HatchInfo> hatches = new ArrayList<>();

    // ─── Active recipe info (CONSUMER mode only) ──────────────────────────
    public boolean isRecipeActive   = false;
    public String  recipeId         = "";   // e.g. "gtceu:ebf/iron_ingot"
    public float   recipeProgress   = 0f;   // 0.0 – 1.0
    public int     recipeProgressTicks = 0; // progress in ticks (for accurate time display)
    public int     recipeDuration   = 0;    // total ticks
    public String  recipeTypeName   = "";   // e.g. "Electric Blast Furnace"
    public List<RecipeHistoryEntry> recipeHistory = new ArrayList<>();

    // ─── History (ring buffer sent as array) ─────────────────────────────────
    public long[] inputHistory  = new long[0];  // EU/s per second, oldest to newest
    public long[] outputHistory = new long[0];

    // ─── Derived ──────────────────────────────────────────────────────────────
    public long netPerSec() { return inputPerSec - outputPerSec; }

    public float chargePercent() {
        if (usesBigInt) {
            if (bigCapacity == null || bigCapacity.equals(BigInteger.ZERO)) return 0f;
            return bigStored.multiply(BigInteger.valueOf(100)).divide(bigCapacity).floatValue() / 100f;
        }
        if (energyCapacity == 0) return 0f;
        return (float) energyStored / energyCapacity;
    }

    public long secondsUntilChange() {
        long net = netPerSec();
        if (net == 0) return -1;
        if (net > 0) {
            // Filling
            long remaining = usesBigInt
                    ? bigCapacity.subtract(bigStored).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue()
                    : (energyCapacity - energyStored);
            return remaining <= 0 ? -1 : remaining / net;
        } else {
            // Draining
            long stored = usesBigInt
                    ? bigStored.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue()
                    : energyStored;
            return stored <= 0 ? -1 : stored / (-net);
        }
    }

    // ─── Hatch info ───────────────────────────────────────────────────────────
    public record HatchInfo(String name, long voltage, long amperage, boolean isInput) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name);
            buf.writeLong(voltage);
            buf.writeLong(amperage);
            buf.writeBoolean(isInput);
        }

        public static HatchInfo decode(FriendlyByteBuf buf) {
            return new HatchInfo(buf.readUtf(), buf.readLong(), buf.readLong(), buf.readBoolean());
        }
    }

    // ─── Network serialization ────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(machineName);
        buf.writeUtf(machineType);
        buf.writeEnum(mode);
        buf.writeBoolean(isFormed);
        buf.writeLong(energyStored);
        buf.writeLong(energyCapacity);
        buf.writeBoolean(usesBigInt);
        if (usesBigInt) {
            byte[] bs = bigStored.toByteArray();
            buf.writeInt(bs.length);
            buf.writeBytes(bs);
            byte[] bc = bigCapacity.toByteArray();
            buf.writeInt(bc.length);
            buf.writeBytes(bc);
        }
        buf.writeLong(inputPerSec);
        buf.writeLong(outputPerSec);
        buf.writeLong(inputVoltage);
        buf.writeLong(inputAmperage);
        buf.writeLong(outputVoltage);
        buf.writeLong(outputAmperage);

        buf.writeInt(hatches.size());
        for (HatchInfo h : hatches) h.encode(buf);

        buf.writeInt(recipeHistory.size());
        for (RecipeHistoryEntry e : recipeHistory) e.encode(buf);
        buf.writeBoolean(isRecipeActive);
        buf.writeUtf(recipeId);
        buf.writeFloat(recipeProgress);
        buf.writeInt(recipeProgressTicks);
        buf.writeInt(recipeDuration);
        buf.writeUtf(recipeTypeName);

        buf.writeInt(inputHistory.length);
        for (long v : inputHistory) buf.writeLong(v);
        buf.writeInt(outputHistory.length);
        for (long v : outputHistory) buf.writeLong(v);
    }

    // Must be in same order as encode()
    public static EnergySnapshot decode(FriendlyByteBuf buf) {
        EnergySnapshot s = new EnergySnapshot();
        s.machineName    = buf.readUtf();
        s.machineType    = buf.readUtf();
        s.mode           = buf.readEnum(MachineMode.class);
        s.isFormed       = buf.readBoolean();
        s.energyStored   = buf.readLong();
        s.energyCapacity = buf.readLong();
        s.usesBigInt     = buf.readBoolean();
        if (s.usesBigInt) {
            byte[] bs = new byte[buf.readInt()]; buf.readBytes(bs); s.bigStored   = new BigInteger(bs);
            byte[] bc = new byte[buf.readInt()]; buf.readBytes(bc); s.bigCapacity = new BigInteger(bc);
        }
        s.inputPerSec    = buf.readLong();
        s.outputPerSec   = buf.readLong();
        s.inputVoltage   = buf.readLong();
        s.inputAmperage  = buf.readLong();
        s.outputVoltage  = buf.readLong();
        s.outputAmperage = buf.readLong();

        int hc = buf.readInt();
        for (int i = 0; i < hc; i++) s.hatches.add(HatchInfo.decode(buf));

        int rhc = buf.readInt();
        for (int i = 0; i < rhc; i++) s.recipeHistory.add(RecipeHistoryEntry.decode(buf));
        s.isRecipeActive      = buf.readBoolean();
        s.recipeId            = buf.readUtf();
        s.recipeProgress      = buf.readFloat();
        s.recipeProgressTicks = buf.readInt();
        s.recipeDuration      = buf.readInt();
        s.recipeTypeName  = buf.readUtf();

        int ih = buf.readInt();
        s.inputHistory = new long[ih];
        for (int i = 0; i < ih; i++) s.inputHistory[i] = buf.readLong();

        int oh = buf.readInt();
        s.outputHistory = new long[oh];
        for (int i = 0; i < oh; i++) s.outputHistory[i] = buf.readLong();

        return s;
    }
}