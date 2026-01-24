package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class MultiblockInfo {
    private final IMultiController controller;
    private final String name;
    private final BlockPos controllerPos;
    private final int tier;
    private final double distanceFromPlayer;
    private final boolean isFormed;
    private final List<ComponentInfo> components;
    private MultiblockStatus status;

    // Universal Scanner Support
    private String sourceMod = "gtceu";

    public MultiblockInfo(
            IMultiController controller,
            String name,
            BlockPos controllerPos,
            int tier,
            double distanceFromPlayer,
            boolean isFormed
    ) {
        this.controller = controller;
        this.name = name;
        this.controllerPos = controllerPos;
        this.tier = tier;
        this.distanceFromPlayer = distanceFromPlayer;
        this.isFormed = isFormed;
        this.components = new ArrayList<>();
        this.status = MultiblockStatus.IDLE;
    }

    public IMultiController getController() {
        return controller;
    }

    public String getName() {
        return name;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public int getTier() {
        return tier;
    }

    public double getDistanceFromPlayer() {
        return distanceFromPlayer;
    }

    public boolean isFormed() {
        return isFormed;
    }

    public List<ComponentInfo> getComponents() {
        return components;
    }

    public List<ComponentGroup> getGroupedComponents() {
        java.util.Map<String, ComponentGroup> groups = new java.util.HashMap<>();

        for (ComponentInfo comp : components) {
            String blockName = comp.getBlockName();
            String key = ComponentGroup.getGroupKey(comp.getType(), comp.getTier(), blockName);

            ComponentGroup group = groups.get(key);
            if (group == null) {
                group = new ComponentGroup(comp.getType(), comp.getTier(), blockName);
                groups.put(key, group);
            }

            group.addComponent(comp);
        }

        // Convert to list and sort by type name, then block name, then tier
        List<ComponentGroup> result = new ArrayList<>(groups.values());
        result.sort((a, b) -> {
            // Sort by type first
            int typeCompare = a.getType().getDisplayName().compareTo(b.getType().getDisplayName());
            if (typeCompare != 0) return typeCompare;
            // Then by block name
            int nameCompare = a.getBlockName().compareTo(b.getBlockName());
            if (nameCompare != 0) return nameCompare;
            // Then by tier
            return Integer.compare(a.getTier(), b.getTier());
        });

        return result;
    }

    public void addComponent(ComponentInfo component) {
        components.add(component);
    }

    public MultiblockStatus getStatus() {
        return status;
    }

    public void setStatus(MultiblockStatus status) {
        this.status = status;
    }

    public String getTierName() {
        if (tier < 0 || tier >= com.gregtechceu.gtceu.api.GTValues.VN.length) {
            return "Unknown";
        }
        return com.gregtechceu.gtceu.api.GTValues.VN[tier].toUpperCase(java.util.Locale.ROOT);
    }

    public String getDistanceString() {
        // Displaying a decimal point prevents many different distances from appearing identical due to rounding.
        // (e.g., 55.6m, 56.1m, 56.4m -> "56m"), which also obscures the actual order.
        if (Double.isNaN(distanceFromPlayer) || Double.isInfinite(distanceFromPlayer)) {
            return "?m";
        }

        // 1 decimal place up to 999.9m, then no decimal places to avoid taking up too much space.
        if (distanceFromPlayer < 1000.0) {
            return String.format(java.util.Locale.ROOT, "%.1fm", distanceFromPlayer);
        }
        return String.format(java.util.Locale.ROOT, "%.0fm", distanceFromPlayer);
    }

    public List<ComponentInfo> getComponentsByType(ComponentType type) {
        return components.stream()
                .filter(c -> c.getType() == type)
                .toList();
    }

    public List<ComponentInfo> getUpgradeableComponents() {
        return components.stream()
                .filter(c -> c.getType().isUpgradeable())
                .toList();
    }

    public int countComponentsOfType(ComponentType type) {
        return (int) components.stream()
                .filter(c -> c.getType() == type)
                .count();
    }

    // ============================================
    // MOD SOURCE TRACKING
    // ============================================

    public void setSourceMod(String modId) {
        this.sourceMod = modId != null ? modId : "unknown";
    }

    public String getSourceMod() {
        return sourceMod;
    }

    public boolean isFromMod(String modId) {
        return this.sourceMod.equals(modId);
    }

    public boolean isVanillaGTCEu() {
        return "gtceu".equals(sourceMod);
    }

    /**
     * Gets a display name that includes the source mod, useful for UI when there are multiblocks of multiple mods
     * Examples:
     * - GTCEu: "Electric Blast Furnace"
     * - PFT: "Tesla Tower"
     * - AGE: "Solar Boiler Array"
     */
    public String getDisplayNameWithMod() {
        if ("gtceu".equals(sourceMod)) {
            return getName();
        }
        return "[" + sourceMod.toUpperCase() + "] " + getName();
    }

    public int getModColor() {
        return switch (sourceMod) {
            case "gtceu" -> 0xFFFFFF;              // White
            case "monifactory" -> 0x00FF00;               // Green
            case "terrafirmagreg" -> 0x00FFFF;            // Cyan
            case "phoenix's technologies" -> 0xFFAA00; // Orange
            case "astrogreg:exsilium" -> 0xFF00FF;    // Purple
            default -> 0xAAAAAA;                   // Gray
        };
    }

    @Override
    public String toString() {
        return "MultiblockInfo{" +
                "name='" + name + '\'' +
                ", tier=" + getTierName() +
                ", distance=" + getDistanceString() +
                ", formed=" + isFormed +
                ", components=" + components.size() +
                ", status=" + status +
                ", sourceMod='" + sourceMod + '\'' +
                '}';
    }

    public String getType() {
        return "";
    }
}