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

        // Energy
        if (lower.contains("energy") && lower.contains("hatch")) return ComponentType.ENERGY_HATCH;
        if (lower.contains("dynamo")) return ComponentType.DYNAMO_HATCH;
        if (lower.contains("substation") && lower.contains("input")) return ComponentType.SUBSTATION_INPUT_ENERGY;
        if (lower.contains("substation") && lower.contains("output")) return ComponentType.SUBSTATION_OUTPUT_ENERGY;

        // Input/Output Buses (Items)
        if (lower.contains("input") && lower.contains("bus")) return ComponentType.INPUT_BUS;
        if (lower.contains("output") && lower.contains("bus")) return ComponentType.OUTPUT_BUS;
        if (lower.contains("steam") && lower.contains("input") && lower.contains("bus")) return ComponentType.STEAM_INPUT_BUS;
        if (lower.contains("steam") && lower.contains("export") && lower.contains("bus")) return ComponentType.STEAM_OUTPUT_BUS;

        // Input/Output Hatches (Fluids)
        if (lower.contains("input") && lower.contains("hatch")) {
            if (lower.contains("4x") || lower.contains("quad")) return ComponentType.QUAD_INPUT_HATCH;
            if (lower.contains("9x") || lower.contains("nonuple")) return ComponentType.NONUPLE_INPUT_HATCH;
            return ComponentType.INPUT_HATCH;
        }
        if (lower.contains("output") && lower.contains("hatch")) {
            if (lower.contains("4x") || lower.contains("quad")) return ComponentType.QUAD_OUTPUT_HATCH;
            if (lower.contains("9x") || lower.contains("nonuple")) return ComponentType.NONUPLE_OUTPUT_HATCH;
            return ComponentType.OUTPUT_HATCH;
        }

        // Special Hatches
        if (lower.contains("muffler")) return ComponentType.MUFFLER;
        if (lower.contains("maintenance")) return ComponentType.MAINTENANCE;
        if (lower.contains("rotor_holder") || lower.contains("rotor holder")) return ComponentType.ROTOR_HOLDER;
        if (lower.contains("pump_fluid") || lower.contains("pump fluid")) return ComponentType.PUMP_FLUID_HATCH;
        if (lower.contains("tank_valve") || lower.contains("tank valve")) return ComponentType.TANK_VALVE;
        if (lower.contains("passthrough")) return ComponentType.PASSTHROUGH_HATCH;
        if (lower.contains("parallel")) return ComponentType.PARALLEL_HATCH;

        // Laser Hatches
        if (lower.contains("input_laser") || (lower.contains("input") && lower.contains("laser"))) return ComponentType.INPUT_LASER;
        if (lower.contains("output_laser") || (lower.contains("output") && lower.contains("laser"))) return ComponentType.OUTPUT_LASER;

        // Data/Computation Hatches
        if (lower.contains("computation") && lower.contains("reception")) return ComponentType.COMPUTATION_DATA_RECEPTION;
        if (lower.contains("computation") && lower.contains("transmission")) return ComponentType.COMPUTATION_DATA_TRANSMISSION;
        if (lower.contains("optical") && lower.contains("reception")) return ComponentType.OPTICAL_DATA_RECEPTION;
        if (lower.contains("optical") && lower.contains("transmission")) return ComponentType.OPTICAL_DATA_TRANSMISSION;
        if (lower.contains("data_access") || lower.contains("data access")) return ComponentType.DATA_ACCESS;

        // HPCA (High Performance Computing Array)
        if (lower.contains("hpca")) return ComponentType.HPCA_COMPONENT;
        if (lower.contains("object_holder") || lower.contains("object holder")) return ComponentType.OBJECT_HOLDER;

        // Steam
        if (lower.contains("steam")) return ComponentType.STEAM;

        // Coils
        if (lower.contains("coil")) return ComponentType.COIL;

        // Casings
        if (lower.contains("casing")) return ComponentType.CASING;

        // Default: Unknown
        GTCEUTerminalMod.LOGGER.debug("Unknown component type: {}", category);
        return ComponentType.UNKNOWN;
    }

    public static Set<BlockPos> getMultiblockBlocks(IMultiController controller) {
        return Set.of();
    }
}