package com.gtceuterminal.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// Data class representing a linked machine for energy monitoring.
public class LinkedMachineData {

    private BlockPos pos;
    private String dimensionId;
    private String customName;
    private String machineType; // auto-detected display name

    public LinkedMachineData(BlockPos pos, String dimensionId, String customName, String machineType) {
        this.pos = pos;
        this.dimensionId = dimensionId;
        this.customName = customName;
        this.machineType = machineType;
    }

    // ─── NBT ─────────────────────────────────────────────────────────────────
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putString("Dim", dimensionId);
        tag.putString("Name", customName);
        tag.putString("Type", machineType);
        return tag;
    }

    public static LinkedMachineData fromNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(
                tag.getInt("X"),
                tag.getInt("Y"),
                tag.getInt("Z")
        );
        return new LinkedMachineData(
                pos,
                tag.getString("Dim"),
                tag.getString("Name"),
                tag.getString("Type")
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    public boolean matches(BlockPos otherPos, String otherDim) {
        return pos.equals(otherPos) && dimensionId.equals(otherDim);
    }

    public String getDisplayName() {
        return customName.isBlank() ? machineType : customName;
    }

    public static String dimId(Level level) {
        return level.dimension().location().toString();
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────
    public BlockPos getPos()          { return pos; }
    public String getDimensionId()    { return dimensionId; }
    public String getCustomName()     { return customName; }
    public String getMachineType()    { return machineType; }
    public void setCustomName(String n) { this.customName = n; }
    public void setMachineType(String t) { this.machineType = t; }
}