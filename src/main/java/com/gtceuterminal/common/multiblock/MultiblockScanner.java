package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.scanner.UniversalMultiblockScanner;
import com.gtceuterminal.common.scanner.UniversalMultiblockScanner.DetectedMultiblock;
import com.gtceuterminal.common.scanner.UniversalMultiblockScanner.ComponentData;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Multiblock Scanner
 * Detects multiblocks from ANY mod/modpack that uses the GTCEu API:
 * Examples:
 * - GTCEu
 * - AGE (AstroGreg:Exsilium)
 * - Monifactory
 * - TFG (TerraFirmaGreg)
 * - Any other modpack using GTCEu 7.0.0 and newer versions
 * Maintains the existing API for compatibility with the rest of the code.
 */
public class MultiblockScanner {

    // Scans multiblocks near the player
    public static List<MultiblockInfo> scanNearbyMultiblocks(Player player, Level level, int radius) {
        List<MultiblockInfo> multiblocks = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();
        Vec3 playerVec = player.position();

        GTCEUTerminalMod.LOGGER.info("=== Universal Multiblock Scan Started ===");
        GTCEUTerminalMod.LOGGER.info("Position: {}, Radius: {}", playerPos, radius);

        // Detects multiblocks
        List<DetectedMultiblock> detected = UniversalMultiblockScanner.scanForAllMultiblocks(
                level,
                playerPos,
                radius
        );

        GTCEUTerminalMod.LOGGER.info("Universal scanner found {} multiblocks", detected.size());

        // Convert DetectedMultiblock to MultiblockInfo for compatibility
        for (DetectedMultiblock mb : detected) {
            try {
                MultiblockInfo info = convertToMultiblockInfo(mb, playerVec, level);
                multiblocks.add(info);

                GTCEUTerminalMod.LOGGER.info("  - {} ({}) from mod '{}' with {} components",
                        info.getName(),
                        info.getTier(),
                        mb.getModId(),
                        mb.getTotalComponentCount()
                );

            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error converting multiblock {}: {}",
                        mb.getName(), e.getMessage());
            }
        }

        // Order by distance to the player
        multiblocks.sort((a, b) -> Double.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));

        // Log
        long uniqueMods = detected.stream()
                .map(DetectedMultiblock::getModId)
                .distinct()
                .count();
        // GTCEUTerminalMod.LOGGER.info("=== Scan Complete ===");
        // GTCEUTerminalMod.LOGGER.info("Total multiblocks: {}", multiblocks.size());
        // GTCEUTerminalMod.LOGGER.info("From {} different mods", uniqueMods);
        return multiblocks;
    }

    private static MultiblockInfo convertToMultiblockInfo(
            DetectedMultiblock detected,
            Vec3 playerPos,
            Level level
    ) {
        BlockPos controllerPos = detected.getPosition();
        double distance = playerPos.distanceTo(Vec3.atCenterOf(controllerPos));

        /**
         * DEBUG
        GTCEUTerminalMod.LOGGER.info("=== Distance Calculation ===");
        GTCEUTerminalMod.LOGGER.info("Multiblock: {}", detected.getName());
        GTCEUTerminalMod.LOGGER.info("Player Position: {}", playerPos);
        GTCEUTerminalMod.LOGGER.info("Controller Position: {}", controllerPos);
        GTCEUTerminalMod.LOGGER.info("Controller Center: {}", Vec3.atCenterOf(controllerPos));
        GTCEUTerminalMod.LOGGER.info("Calculated Distance: {}m", String.format("%.2f", distance));
         */

        // Create MultiblockInfo
        MultiblockInfo info = new MultiblockInfo(
                detected.getController(),
                detected.getName(),
                controllerPos,
                detected.getTier(),
                distance,
                true
        );

        // Add source mod metadata
        info.setSourceMod(detected.getModId());

        // Convert and add components
        for (var entry : detected.getComponents().entrySet()) {
            String category = entry.getKey();
            List<ComponentData> components = entry.getValue();

            for (ComponentData comp : components) {
                ComponentInfo componentInfo = createComponentInfo(category, comp, level);
                if (componentInfo != null) {
                    info.addComponent(componentInfo);
                }
            }
        }

        return info;
    }


    // Create a ComponentInfo from the universal scanner data
    private static ComponentInfo createComponentInfo(String category, ComponentData comp, Level level) {
        try {
            ComponentType type = parseComponentType(category);
            BlockPos pos = comp.getPosition();

            BlockState state = level.getBlockState(pos);

            return new ComponentInfo(
                    type,
                    comp.getTier(),
                    pos,
                    state
            );
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Could not create component info for {}: {}",
                    category, e.getMessage());
            return null;
        }
    }

    // Map the universal scanner categories to ComponentType
    private static ComponentType parseComponentType(String category) {
        String lower = category.toLowerCase();

        // WIRELESS COMPONENTS
        if (lower.contains("wireless")) {
            if (lower.contains("energy")) {
                if (lower.contains("output")) {
                    return ComponentType.WIRELESS_ENERGY_OUTPUT;
                }
                return ComponentType.WIRELESS_ENERGY_INPUT;
            }
            if (lower.contains("laser")) {
                if (lower.contains("source") || lower.contains("output")) {
                    return ComponentType.WIRELESS_LASER_OUTPUT;
                }
                return ComponentType.WIRELESS_LASER_INPUT;
            }
        }

        // SUBSTATION HATCHES
        if (lower.contains("substation")) {
            if (lower.contains("input") || lower.contains("input energy")) {
                return ComponentType.SUBSTATION_INPUT_ENERGY;
            }
            if (lower.contains("output") || lower.contains("output energy")) {
                return ComponentType.SUBSTATION_OUTPUT_ENERGY;
            }
        }

        // LASER HATCHES
        if (lower.contains("laser")) {
            if (lower.contains("input") || lower.contains("target")) {
                return ComponentType.INPUT_LASER;
            }
            if (lower.contains("output") || lower.contains("source")) {
                return ComponentType.OUTPUT_LASER;
            }
        }

        // DIRECT MAPPING by display name
        // This works because ComponentType.getDisplayName() matches
        // what UniversalMultiblockScanner returns
        for (ComponentType type : ComponentType.values()) {
            // Try exact match first
            if (type.getDisplayName().equalsIgnoreCase(category)) {
                return type;
            }

            // Try with amperage removed
            // "4A Energy Hatch" -> "Energy Hatch"
            String categoryWithoutAmperage = category.replaceFirst("^\\d+A\\s+", "");
            if (type.getDisplayName().equalsIgnoreCase(categoryWithoutAmperage)) {
                return type;
            }
        }

        // ⭐ FALLBACK: Special cases
        if (lower.contains("coil")) return ComponentType.COIL;
        if (lower.contains("casing")) return ComponentType.CASING;

        GTCEUTerminalMod.LOGGER.warn("Unknown component type: {}", category);
        return ComponentType.UNKNOWN;
    }

    public static Set<BlockPos> getMultiblockBlocks(IMultiController controller) {
        return Set.of();
    }
}