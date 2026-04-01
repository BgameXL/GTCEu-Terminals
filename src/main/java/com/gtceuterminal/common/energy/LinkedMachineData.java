package com.gtceuterminal.common.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import net.minecraftforge.registries.ForgeRegistries;

// Data class representing a linked machine for energy monitoring.
public class LinkedMachineData {

    private BlockPos pos;
    private String dimensionId;
    private String customName;
    /** Registry key of the controller block, e.g. {@code gtceu:electric_blast_furnace}. */
    private String controllerBlockKey;
    /** Legacy English display from old saves when only {@code Type} was stored. */
    private String legacyTypeDisplay;

    public LinkedMachineData(BlockPos pos, String dimensionId, String customName, String controllerBlockKey) {
        this(pos, dimensionId, customName, controllerBlockKey, "");
    }

    public LinkedMachineData(BlockPos pos, String dimensionId, String customName,
                             String controllerBlockKey, String legacyTypeDisplay) {
        this.pos = pos;
        this.dimensionId = dimensionId;
        this.customName = customName != null ? customName : "";
        this.controllerBlockKey = controllerBlockKey != null ? controllerBlockKey : "";
        this.legacyTypeDisplay = legacyTypeDisplay != null ? legacyTypeDisplay : "";
    }

    // ─── NBT ─────────────────────────────────────────────────────────────────
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putString("Dim", dimensionId);
        tag.putString("Name", customName);
        tag.putString("BlockKey", controllerBlockKey);
        if (!legacyTypeDisplay.isEmpty()) {
            tag.putString("Type", legacyTypeDisplay);
        }
        return tag;
    }

    public static LinkedMachineData fromNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(
                tag.getInt("X"),
                tag.getInt("Y"),
                tag.getInt("Z")
        );
        String blockKey = tag.contains("BlockKey") ? tag.getString("BlockKey") : "";
        String legacyType = tag.getString("Type");
        if (blockKey.isEmpty() && !legacyType.isEmpty()) {
            return new LinkedMachineData(
                    pos,
                    tag.getString("Dim"),
                    tag.getString("Name"),
                    "",
                    legacyType
            );
        }
        return new LinkedMachineData(
                pos,
                tag.getString("Dim"),
                tag.getString("Name"),
                blockKey,
                ""
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    public boolean matches(BlockPos otherPos, String otherDim) {
        return pos.equals(otherPos) && dimensionId.equals(otherDim);
    }

    /**
     * Localized controller name when no custom name is set; otherwise the custom name.
     * Safe on dedicated server (uses block registry + translation keys).
     */
    public Component getDisplayNameComponent() {
        if (customName != null && !customName.isBlank()) {
            return Component.literal(customName);
        }
        if (controllerBlockKey != null && !controllerBlockKey.isBlank()) {
            Block b = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(controllerBlockKey));
            if (b != null) {
                return b.getName();
            }
        }
        return Component.literal(legacyTypeDisplay != null ? legacyTypeDisplay : "");
    }

    /** For logging / plain string; prefer {@link #getDisplayNameComponent()} in UI. */
    public String getDisplayName() {
        return getDisplayNameComponent().getString();
    }

    /**
     * Offline UI: custom name, block translation key, or legacy English from old saves.
     */
    public void applyToSnapshotIdentity(EnergySnapshot snap) {
        snap.machineCustomName = customName != null ? customName : "";
        snap.machineTypeKey = "";
        if (controllerBlockKey != null && !controllerBlockKey.isBlank()) {
            Block b = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(controllerBlockKey));
            if (b != null) {
                snap.machineTypeKey = b.getDescriptionId();
            }
        }
        if (snap.machineTypeKey.isEmpty()
                && (snap.machineCustomName == null || snap.machineCustomName.isBlank())
                && legacyTypeDisplay != null && !legacyTypeDisplay.isBlank()) {
            snap.machineCustomName = legacyTypeDisplay;
        }
    }

    public static String dimId(Level level) {
        return level.dimension().location().toString();
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────
    public BlockPos getPos()          { return pos; }
    public String getDimensionId()    { return dimensionId; }
    public String getCustomName()     { return customName; }
    public String getControllerBlockKey() { return controllerBlockKey; }
    public String getLegacyTypeDisplay() { return legacyTypeDisplay; }
    public void setCustomName(String n) { this.customName = n != null ? n : ""; }
}
