package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ComponentInfo {
    private final ComponentType type;
    private final int tier;
    private final BlockPos position;
    private final BlockState state;
    private final String blockName;

    public ComponentInfo(ComponentType type, int tier, BlockPos position, BlockState state) {
        this.type = type;
        this.tier = tier;
        this.position = position;
        this.state = state;
        // Use registry ID for more specific identification
        this.blockName = state.getBlock().builtInRegistryHolder().key().location().getPath();
    }

    public ComponentInfo(ComponentType type, int tier, BlockPos position, ComponentType type1, int tier1, BlockPos position1, BlockState state, String blockName) {
        this.type = type1;
        this.tier = tier1;
        this.position = position1;
        this.state = state;
        this.blockName = blockName;
    }

    public ComponentType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public BlockPos getPosition() {
        return position;
    }

    public BlockState getState() {
        return state;
    }

    public String getBlockName() {
        return blockName;
    }

    /**
     * It achieves the maximum tier for this type of component.
     * Coils use a different tier system (0-7) vs. voltage tiers
     */
    private int getMaxTierForType() {
        if (type == ComponentType.COIL) {
            // Coils tienen 8 tipos (0-7): Cupronickel hasta Tritanium
            // Usar CoilConfig para obtener el máximo dinámicamente
            return 7;
        }

        // Standard components use voltage tiers
        return GTValues.VN.length - 1;
    }

    public String getTierName() {
        if (type == ComponentType.COIL) {
            String coilName = com.gtceuterminal.common.config.CoilConfig.getCoilDisplayName(tier);
            if (coilName != null && !coilName.isEmpty()) {
                // Return only the coil name without "Coil"
                return coilName.replace(" Coil", "").trim();
            }
            return "Unknown Coil";
        }

        // Standard components use voltage names
        if (tier < 0 || tier >= GTValues.VN.length) {
            return "Unknown";
        }
        return GTValues.VN[tier].toUpperCase(java.util.Locale.ROOT);
    }

    public List<Integer> getPossibleUpgradeTiers() {
        List<Integer> tiers = new ArrayList<>();
        int maxTier = getMaxTierForType();

        // Generate a list of ALL possible tiers (except the current one)
        for (int i = 0; i <= maxTier; i++) {
            if (i != tier) {  // Exclude current tier
                tiers.add(i);
            }
        }

        return tiers;
    }

    // Check if it's possible to switch to a specific tier
    public boolean canUpgradeTo(int targetTier) {
        int maxTier = getMaxTierForType();
        return targetTier != tier && targetTier >= 0 && targetTier <= maxTier;
    }

    // Get display name for GUI, Formats registry ID to readable name
    public String getDisplayName() {
        if (blockName.contains("rtm_alloy")) {
            return blockName.replace("rtm_alloy", "RTM Alloy")
                    .replace("_coil_block", " Coil Block")
                    .replace("_", " ");
        }
        if (blockName.contains("hss_g")) {
            return blockName.replace("hss_g", "HSS-G")
                    .replace("_coil_block", " Coil Block")
                    .replace("_", " ");
        }

        String[] parts = blockName.split("_");
        StringBuilder display = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (display.length() > 0) {
                display.append(" ");
            }

            if (!part.isEmpty()) {
                // Check if this part is a tier name
                if (isTierName(part)) {
                    display.append(part.toUpperCase(java.util.Locale.ROOT));
                } else {
                    display.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        display.append(part.substring(1));
                    }
                }
            }
        }

        return display.toString();
    }

    private boolean isTierName(String s) {
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("ulv") || lower.equals("lv") || lower.equals("mv") ||
                lower.equals("hv") || lower.equals("ev") || lower.equals("iv") ||
                lower.equals("luv") || lower.equals("zpm") || lower.equals("uv") ||
                lower.equals("uhv") || lower.equals("uev") || lower.equals("uiv") ||
                lower.equals("uxv") || lower.equals("opv") || lower.equals("max");
    }

    @Override
    public String toString() {
        return "ComponentInfo{" +
                "type=" + type +
                ", tier=" + getTierName() +
                ", position=" + position +
                '}';
    }

    public Object getPos() {
        return null;
    }
}