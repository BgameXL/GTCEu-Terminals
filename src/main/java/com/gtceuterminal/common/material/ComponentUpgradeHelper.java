package com.gtceuterminal.common.material;

import com.gregtechceu.gtceu.api.GTValues;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.config.*;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.config.SubstationHatchConfig;
import com.gtceuterminal.common.config.DualHatchConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for component upgrades with complete support for:
 * - Standard components (buses, hatches, energy)
 * - Laser hatches (INPUT_LASER, OUTPUT_LASER)
 * - Substation hatches (SUBSTATION_INPUT_ENERGY, SUBSTATION_OUTPUT_ENERGY)
 * - Dynamo hatches (DYNAMO_HATCH)
 * - Wireless components (if ComponentType enum includes them)
 * - Maintenance hatches (only 4 specific types)
 */
public class ComponentUpgradeHelper {

    /**
     * Build a minimal "required items" map from a concrete target blockId.
     * This is used for component types where a tier alone is not enough to uniquely identify an upgrade
     * (e.g. Maintenance hatches where multiple variants can share the same tier).
     */
    public static Map<Item, Integer> getUpgradeItemsForBlockId(String targetBlockId) {
        Map<Item, Integer> items = new HashMap<>();
        if (targetBlockId == null || targetBlockId.isBlank()) return items;

        try {
            ResourceLocation id = ResourceLocation.parse(targetBlockId);
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != net.minecraft.world.level.block.Blocks.AIR && block.asItem() != null) {
                items.put(block.asItem(), 1);
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Invalid block ID: {}", targetBlockId, e);
        }

        return items;
    }

    /**
     * Validate that a given blockId is an allowed upgrade option for the given component type.
     * For now this is primarily used by MAINTENANCE upgrades.
     */
    public static boolean isUpgradeIdAllowed(ComponentType type, String targetBlockId) {
        if (type == null) return false;
        if (targetBlockId == null || targetBlockId.isBlank()) return false;

        if (type == ComponentType.MAINTENANCE) {
            return MaintenanceHatchConfig.getAllMaintenanceHatches().stream()
                    .anyMatch(e -> targetBlockId.equals(e.blockId));
        }

        // For other types, we currently use tier-based upgrades.
        return true;
    }

    public static Map<Item, Integer> getUpgradeItems(ComponentInfo component, int targetTier) {
        Map<Item, Integer> items = new HashMap<>();

        Block targetBlock = getComponentBlock(component.getType(), targetTier);
        if (targetBlock != null && targetBlock.asItem() != null) {
            items.put(targetBlock.asItem(), 1);
        }

        return items;
    }

    private static Block getComponentBlock(ComponentType type, int tier) {
        String blockId = getBlockIdFromConfig(type, tier, null);
        if (blockId == null) return null;

        try {
            ResourceLocation id = ResourceLocation.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != net.minecraft.world.level.block.Blocks.AIR) {
                return block;
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Invalid block ID: {}", blockId, e);
        }

        return null;
    }

    /**
     * Get block ID from config based on component type and tier
     * Updated to support all component types including lasers, substations, dynamos
     */
    private static String getBlockIdFromConfig(ComponentType type, int tier, ComponentInfo component) {
        return switch (type) {
            // ========== FLUID HATCHES ==========
            case INPUT_HATCH, INPUT_HATCH_1X, QUAD_INPUT_HATCH, NONUPLE_INPUT_HATCH -> {
                for (HatchConfig.FluidHatchEntry hatch : HatchConfig.getInputHatches()) {
                    if (hatch.tier == tier) {
                        // Match based on capacity
                        boolean matches = switch (type) {
                            case INPUT_HATCH -> hatch.capacity.equals("1x") && hatch.blockId.matches(".*:.*_input_hatch$");
                            case INPUT_HATCH_1X -> hatch.capacity.equals("1x") && hatch.blockId.contains("_1x");
                            case QUAD_INPUT_HATCH -> hatch.capacity.equals("4x");
                            case NONUPLE_INPUT_HATCH -> hatch.capacity.equals("9x");
                            default -> false;
                        };
                        if (matches) yield hatch.blockId;
                    }
                }
                yield null;
            }

            case OUTPUT_HATCH, OUTPUT_HATCH_1X, QUAD_OUTPUT_HATCH, NONUPLE_OUTPUT_HATCH -> {
                for (HatchConfig.FluidHatchEntry hatch : HatchConfig.getOutputHatches()) {
                    if (hatch.tier == tier) {
                        boolean matches = switch (type) {
                            case OUTPUT_HATCH -> hatch.capacity.equals("1x") && hatch.blockId.matches(".*:.*_output_hatch$");
                            case OUTPUT_HATCH_1X -> hatch.capacity.equals("1x") && hatch.blockId.contains("_1x");
                            case QUAD_OUTPUT_HATCH -> hatch.capacity.equals("4x");
                            case NONUPLE_OUTPUT_HATCH -> hatch.capacity.equals("9x");
                            default -> false;
                        };
                        if (matches) yield hatch.blockId;
                    }
                }
                yield null;
            }

            // ========== ITEM BUSES ==========
            case INPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getInputBuses()) {
                    if (bus.tier == tier) yield bus.blockId;
                }
                yield null;
            }

            case OUTPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getOutputBuses()) {
                    if (bus.tier == tier) yield bus.blockId;
                }
                yield null;
            }

            // ========== ENERGY HATCHES (Input) ==========
            case ENERGY_HATCH -> {
                String amperage = component != null ? component.getAmperage() : null;
                EnergyHatchConfig.EnergyHatchEntry energy = EnergyHatchConfig.getHatchForTierAndAmperage("INPUT", tier, amperage);
                yield energy != null ? energy.blockId : null;
            }

            // ========== DYNAMO HATCHES (Energy Output) ==========
            case DYNAMO_HATCH -> {
                String amperage = component != null ? component.getAmperage() : null;
                EnergyHatchConfig.EnergyHatchEntry dynamo = EnergyHatchConfig.getHatchForTierAndAmperage("OUTPUT", tier, amperage);
                yield dynamo != null ? dynamo.blockId : null;
            }

            // ========== SUBSTATION HATCHES ==========
            case SUBSTATION_INPUT_ENERGY -> {
                SubstationHatchConfig.SubstationHatchEntry substation =
                        SubstationHatchConfig.getHatchForTier("INPUT", tier);
                yield substation != null ? substation.blockId : null;
            }

            case SUBSTATION_OUTPUT_ENERGY -> {
                SubstationHatchConfig.SubstationHatchEntry substation =
                        SubstationHatchConfig.getHatchForTier("OUTPUT", tier);
                yield substation != null ? substation.blockId : null;
            }

            // ========== LASER HATCHES ==========
            case INPUT_LASER -> {
                String amperage = component != null ? component.getAmperage() : null;
                LaserHatchConfig.LaserHatchEntry laser = LaserHatchConfig.getHatchForTierAndAmperage("INPUT", tier, amperage);
                yield laser != null ? laser.blockId : null;
            }

            case OUTPUT_LASER -> {
                String amperage = component != null ? component.getAmperage() : null;
                LaserHatchConfig.LaserHatchEntry laser = LaserHatchConfig.getHatchForTierAndAmperage("OUTPUT", tier, amperage);
                yield laser != null ? laser.blockId : null;
            }

            // ========== SPECIAL HATCHES ==========
            case PARALLEL_HATCH -> {
                for (ParallelHatchConfig.ParallelHatchEntry parallel : ParallelHatchConfig.getAllParallelHatches()) {
                    if (parallel.tier == tier && parallel.variant.equals("STANDARD")) {
                        yield parallel.blockId;
                    }
                }
                yield null;
            }

            case MUFFLER -> {
                MufflerHatchConfig.MufflerHatchEntry muffler = MufflerHatchConfig.getHatchForTier(tier);
                yield muffler != null ? muffler.blockId : null;
            }

            case COIL -> {
                CoilConfig.CoilEntry coil = CoilConfig.getCoilByTier(tier);
                yield coil != null ? coil.blockId : null;
            }

            case MAINTENANCE -> {
                // Permitir cualquier tipo de maintenance hatch
                for (MaintenanceHatchConfig.MaintenanceHatchEntry maintenance : MaintenanceHatchConfig.getAllMaintenanceHatches()) {
                    if (maintenance.tier == tier) {
                        yield maintenance.blockId;
                    }
                }
                yield null;
            }

            case DUAL_HATCH -> {
                for (DualHatchConfig.DualHatchEntry hatch : DualHatchConfig.getInputDualHatches()) {
                    if (hatch.tier == tier) {
                        yield hatch.blockId;
                    }
                }

                for (DualHatchConfig.DualHatchEntry hatch : DualHatchConfig.getOutputDualHatches()) {
                    if (hatch.tier == tier) {
                        yield hatch.blockId;
                    }
                }

                yield null;
            }

            default -> {
                // Try to handle wireless types if they exist in ComponentType enum
                String typeName = type.name();
                if (typeName.startsWith("WIRELESS_")) {
                    yield getWirelessBlockId(type, tier);
                }
                yield null;
            }
        };
    }

    /**
     * Handle wireless components (if they exist in ComponentType enum)
     */
    private static String getWirelessBlockId(ComponentType type, int tier) {
        String tierName = getTierNameForWireless(tier);
        if (tierName == null) return null;

        return switch (type.name()) {
            case "WIRELESS_ENERGY_INPUT" -> "gtmthings:" + tierName + "_2a_wireless_energy_input_hatch";
            case "WIRELESS_ENERGY_OUTPUT" -> "gtmthings:" + tierName + "_2a_wireless_energy_output_hatch";
            case "WIRELESS_LASER_INPUT" -> "gtmthings:" + tierName + "_256a_wireless_laser_target_hatch";
            case "WIRELESS_LASER_OUTPUT" -> "gtmthings:" + tierName + "_256a_wireless_laser_source_hatch";
            default -> null;
        };
    }

    /**
     * Check if component can be upgraded to target tier
     */
    public static boolean canUpgrade(ComponentInfo component, int targetTier) {
        if (!component.getType().isUpgradeable()) {
            return false;
        }

        // Don't allow "upgrading" to same tier
        if (targetTier == component.getTier()) {
            return false;
        }

        // Check if block exists for target tier
        String blockId = getBlockIdFromConfig(component.getType(), targetTier, component);
        if (blockId == null) {
            return false;
        }

        // Check if block actually exists in game
        return getComponentBlock(component.getType(), targetTier) != null;
    }

    /**
     * Get upgrade display name for target tier
     * Updated to support all component types
     */
    public static String getUpgradeName(ComponentInfo component, int targetTier) {
        ComponentType type = component.getType();
        String tierDisplayName = getTierDisplayName(targetTier);

        return switch (type) {
            // ========== FLUID HATCHES ==========
            case INPUT_HATCH, INPUT_HATCH_1X, QUAD_INPUT_HATCH, NONUPLE_INPUT_HATCH -> {
                for (HatchConfig.FluidHatchEntry hatch : HatchConfig.getInputHatches()) {
                    if (hatch.tier == targetTier) {
                        boolean matches = switch (type) {
                            case INPUT_HATCH -> hatch.capacity.equals("1x") && hatch.blockId.matches(".*:.*_input_hatch$");
                            case INPUT_HATCH_1X -> hatch.capacity.equals("1x") && hatch.blockId.contains("_1x");
                            case QUAD_INPUT_HATCH -> hatch.capacity.equals("4x");
                            case NONUPLE_INPUT_HATCH -> hatch.capacity.equals("9x");
                            default -> false;
                        };
                        if (matches) yield hatch.displayName + " →";
                    }
                }
                yield null;
            }

            case OUTPUT_HATCH, OUTPUT_HATCH_1X, QUAD_OUTPUT_HATCH, NONUPLE_OUTPUT_HATCH -> {
                for (HatchConfig.FluidHatchEntry hatch : HatchConfig.getOutputHatches()) {
                    if (hatch.tier == targetTier) {
                        boolean matches = switch (type) {
                            case OUTPUT_HATCH -> hatch.capacity.equals("1x") && hatch.blockId.matches(".*:.*_output_hatch$");
                            case OUTPUT_HATCH_1X -> hatch.capacity.equals("1x") && hatch.blockId.contains("_1x");
                            case QUAD_OUTPUT_HATCH -> hatch.capacity.equals("4x");
                            case NONUPLE_OUTPUT_HATCH -> hatch.capacity.equals("9x");
                            default -> false;
                        };
                        if (matches) yield hatch.displayName + " →";
                    }
                }
                yield null;
            }

            // ========== ITEM BUSES ==========
            case INPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getInputBuses()) {
                    if (bus.tier == targetTier) yield bus.displayName + " →";
                }
                yield null;
            }

            case OUTPUT_BUS -> {
                for (BusConfig.BusEntry bus : BusConfig.getOutputBuses()) {
                    if (bus.tier == targetTier) yield bus.displayName + " →";
                }
                yield null;
            }

            // ========== ENERGY & DYNAMOS ==========
            case ENERGY_HATCH -> {
                for (EnergyHatchConfig.EnergyHatchEntry energy : EnergyHatchConfig.getAllEnergyHatches()) {
                    if (energy.tier == targetTier &&
                            energy.hatchType.equalsIgnoreCase("INPUT") &&
                            energy.blockId.endsWith("_energy_input_hatch")) {
                        yield energy.displayName + " →";
                    }
                }
                yield null;
            }

            case DYNAMO_HATCH -> {
                for (EnergyHatchConfig.EnergyHatchEntry energy : EnergyHatchConfig.getAllEnergyHatches()) {
                    if (energy.tier == targetTier &&
                            energy.hatchType.equalsIgnoreCase("OUTPUT") &&
                            energy.blockId.endsWith("_energy_output_hatch")) {
                        yield energy.displayName + " →";
                    }
                }
                yield null;
            }

            // ========== SUBSTATION ==========
            case SUBSTATION_INPUT_ENERGY -> {
                SubstationHatchConfig.SubstationHatchEntry substation =
                        SubstationHatchConfig.getHatchForTier("INPUT", targetTier);
                yield substation != null ? substation.displayName + " →" : null;
            }

            case SUBSTATION_OUTPUT_ENERGY -> {
                SubstationHatchConfig.SubstationHatchEntry substation =
                        SubstationHatchConfig.getHatchForTier("OUTPUT", targetTier);
                yield substation != null ? substation.displayName + " →" : null;
            }

            // ========== LASERS ==========
            case INPUT_LASER -> {
                LaserHatchConfig.LaserHatchEntry laser =
                        LaserHatchConfig.getHatchForTier("INPUT", targetTier);
                yield laser != null ? laser.displayName + " →" : null;
            }

            case OUTPUT_LASER -> {
                LaserHatchConfig.LaserHatchEntry laser =
                        LaserHatchConfig.getHatchForTier("OUTPUT", targetTier);
                yield laser != null ? laser.displayName + " →" : null;
            }
            // ========== SPECIAL HATCHES ==========
            case PARALLEL_HATCH -> {
                for (ParallelHatchConfig.ParallelHatchEntry parallel : ParallelHatchConfig.getAllParallelHatches()) {
                    if (parallel.tier == targetTier && parallel.variant.equals("STANDARD")) {
                        yield parallel.displayName + " →";
                    }
                }
                yield null;
            }

            case MUFFLER ->
                    tierDisplayName + " Muffler Hatch →";

            case COIL -> {
                CoilConfig.CoilEntry coil = CoilConfig.getCoilByTier(targetTier);
                yield coil != null ? coil.displayName : null;
            }

            case MAINTENANCE -> {
                for (MaintenanceHatchConfig.MaintenanceHatchEntry maintenance : MaintenanceHatchConfig.getAllMaintenanceHatches()) {
                    if (maintenance.tier == targetTier) {
                        yield maintenance.displayName + " →";
                    }
                }
                yield null;
            }

            default -> {
                // Handle wireless types
                String typeName = type.name();
                if (typeName.contains("WIRELESS_ENERGY_INPUT")) {
                    yield tierDisplayName + " Wireless Energy Input Hatch →";
                } else if (typeName.contains("WIRELESS_ENERGY_OUTPUT")) {
                    yield tierDisplayName + " Wireless Energy Output Hatch →";
                } else if (typeName.contains("WIRELESS_LASER_INPUT")) {
                    yield tierDisplayName + " Wireless Laser Target Hatch →";
                } else if (typeName.contains("WIRELESS_LASER_OUTPUT")) {
                    yield tierDisplayName + " Wireless Laser Source Hatch →";
                }
                yield null;
            }
        };
    }

    /**
     * Get available tiers for a component type
     * Updated to handle all types correctly
     */
    public static List<Integer> getAvailableTiers(ComponentType type) {
        return switch (type) {
            // Fluid hatches
            case INPUT_HATCH, INPUT_HATCH_1X, QUAD_INPUT_HATCH, NONUPLE_INPUT_HATCH ->
                    HatchConfig.getInputHatches().stream().map(h -> h.tier).distinct().sorted().toList();

            case OUTPUT_HATCH, OUTPUT_HATCH_1X, QUAD_OUTPUT_HATCH, NONUPLE_OUTPUT_HATCH ->
                    HatchConfig.getOutputHatches().stream().map(h -> h.tier).distinct().sorted().toList();

            // Item buses
            case INPUT_BUS -> BusConfig.getInputBuses().stream().map(b -> b.tier).sorted().toList();
            case OUTPUT_BUS -> BusConfig.getOutputBuses().stream().map(b -> b.tier).sorted().toList();

            // Energy Hatches
            case ENERGY_HATCH, DYNAMO_HATCH ->
                    EnergyHatchConfig.getAllEnergyHatches().stream()
                            .filter(h -> !h.blockId.contains("substation"))  // Exclude substation
                            .map(h -> h.tier)
                            .distinct()
                            .sorted()
                            .toList();

            // Substation hatches
            case SUBSTATION_INPUT_ENERGY, SUBSTATION_OUTPUT_ENERGY ->
                    SubstationHatchConfig.getAllHatches().stream()
                            .map(h -> h.tier)
                            .distinct()
                            .sorted()
                            .toList();

            // Lasers (IV+ only, tier 5+)
            case INPUT_LASER, OUTPUT_LASER ->
                    LaserHatchConfig.getAllHatches().stream()
                            .map(l -> l.tier)
                            .filter(t -> t >= 5)
                            .distinct()
                            .sorted()
                            .toList();

            // Special hatches
            case PARALLEL_HATCH ->
                    ParallelHatchConfig.getAllParallelHatches().stream()
                            .filter(p -> p.variant.equals("STANDARD"))
                            .map(p -> p.tier)
                            .sorted()
                            .toList();

            case MUFFLER ->
                    MufflerHatchConfig.getAllMufflerHatches().stream().map(m -> m.tier).sorted().toList();

            case COIL ->
                    java.util.stream.IntStream.range(0, CoilConfig.getAllCoils().size()).boxed().toList();

            case MAINTENANCE ->
                    MaintenanceHatchConfig.getAllMaintenanceHatches().stream()
                            .map(m -> m.tier)
                            .distinct()
                            .sorted()
                            .toList();

            case DUAL_HATCH ->
                    DualHatchConfig.getInputDualHatches().stream()
                            .map(h -> h.tier)
                            .distinct()
                            .sorted()
                            .toList();

            default -> {
                // Handle wireless types
                String typeName = type.name();
                if (typeName.startsWith("WIRELESS_ENERGY_")) {
                    yield EnergyHatchConfig.getAllEnergyHatches().stream()
                            .map(e -> e.tier)
                            .distinct()
                            .sorted()
                            .toList();
                } else if (typeName.startsWith("WIRELESS_LASER_")) {
                    yield EnergyHatchConfig.getAllEnergyHatches().stream()
                            .map(e -> e.tier)
                            .filter(t -> t >= 5)
                            .distinct()
                            .sorted()
                            .toList();
                }
                yield List.of();
            }
        };
    }

    // ========== HELPER METHODS ==========

    private static String getTierNameForWireless(int tier) {
        if (tier < 0 || tier >= GTValues.VN.length) {
            return null;
        }
        return GTValues.VN[tier].toLowerCase();
    }

    private static String getTierDisplayName(int tier) {
        if (tier < 0 || tier >= GTValues.VN.length) {
            return "Unknown";
        }
        return GTValues.VN[tier];
    }
}