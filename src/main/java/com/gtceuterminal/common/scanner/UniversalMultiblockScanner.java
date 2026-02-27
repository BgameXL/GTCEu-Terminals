package com.gtceuterminal.common.scanner;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.config.ComponentPattern;
import com.gtceuterminal.common.config.ComponentPatternRegistry;
import com.gtceuterminal.common.multiblock.ComponentType;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Universal Multiblock Detection System
 * Detects ANY multiblock that uses the GTCEu API, regardless of the mod or modpack that added it.
 * Wors with:
 * - GTCEu (NO WAY)
 * - PFT (Phoenix Forge Technologies)
 * - Monifactory
 * - AGE (AstroGreg:Exisilium)
 * - TFG (TerraFirmaGreg)
 */
public class UniversalMultiblockScanner {

    private static final int DEFAULT_SCAN_RADIUS = 32;

    // Flood fill constants
    private static final int MAX_SCAN_SIZE_XZ = 48;
    private static final int MAX_SCAN_SIZE_Y  = 48;
    private static final int BOUNDS_PADDING = 2;

    // Scan the area looking for ALL the multiblocks formed
    public static List<DetectedMultiblock> scanForAllMultiblocks(Level level, BlockPos center, int radius) {
        List<DetectedMultiblock> found = new ArrayList<>();
        Set<BlockPos> scannedControllers = new HashSet<>();

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight(), center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        // GTCEUTerminalMod.LOGGER.info("Scanning for universal multiblocks in radius {} from {}", radius, center);

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (scannedControllers.contains(pos)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMachineBlockEntity machineBlockEntity) {
                MetaMachine metaMachine = machineBlockEntity.getMetaMachine();

                // Detect if it is a MultiblockController
                if (metaMachine instanceof MultiblockControllerMachine controller) {
                    BlockPos immutablePos = pos.immutable();
                    scannedControllers.add(immutablePos);

                    // Check if it is formed
                    if (isMultiblockFormed(controller)) {
                        DetectedMultiblock detected = analyzeMultiblock(controller, immutablePos, level);
                        if (detected != null) {
                            found.add(detected);
                            GTCEUTerminalMod.LOGGER.info("Found multiblock: {} at {}",
                                    detected.getName(), immutablePos);
                        }
                    }
                }
            }
        }

        GTCEUTerminalMod.LOGGER.info("Total multiblocks found: {}", found.size());
        return found;
    }

    private static boolean isMultiblockFormed(MultiblockControllerMachine controller) {
        try {
            /// Check if it is formed
            if (controller.isFormed()) {
                return true;
            }

            // Check pattern state
            MultiblockState state = controller.getMultiblockState();
            if (state != null && state.isNeededFlip()) {
                return true;
            }

            // Check parts count (if it has parts, it's probably formed)
            var parts = controller.getParts();
            if (parts != null && !parts.isEmpty()) {
                return true;
            }

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Error checking if multiblock is formed: {}", e.getMessage());
        }

        return false;
    }

    // Analyze a multiblock and extract ALL its information
    private static DetectedMultiblock analyzeMultiblock(
            MultiblockControllerMachine controller,
            BlockPos pos,
            Level level
    ) {
        try {
            String name = getMultiblockName(controller);
            String modId = getMultiblockModId(controller);
            int tier = getMultiblockTier(controller);

            // Extract all components from the multiblock
            Map<String, List<ComponentData>> components = extractAllComponents(controller, level);

            return new DetectedMultiblock(
                    name,
                    modId,
                    pos,
                    tier,
                    components,
                    controller
            );

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error analyzing multiblock at {}: {}", pos, e.getMessage());
            return null;
        }
    }

    // Gets the name of the multiblock
    private static String getMultiblockName(MultiblockControllerMachine controller) {
        try {
            var definition = controller.getDefinition();
            if (definition != null) {
                return definition.getDescriptionId();
            }

            String machineName = controller.toString();
            if (machineName != null && !machineName.isEmpty()) {
                return machineName;
            }

            return "Unknown Multiblock";

        } catch (Exception e) {
            return "Unknown Multiblock";
        }
    }

    // Detects which mod the multiblock comes from
    private static String getMultiblockModId(MultiblockControllerMachine controller) {
        try {
            var definition = controller.getDefinition();
            if (definition != null) {
                var registryName = definition.getId();
                if (registryName != null) {
                    return registryName.getNamespace();
                }
            }

            return "gtceu"; // Default

        } catch (Exception e) {
            return "unknown";
        }
    }

    // Get the multiblock tier
    private static int getMultiblockTier(MultiblockControllerMachine controller) {
        try {
            // If getTier() is in MachineDefinition
            var definition = controller.getDefinition();
            if (definition != null) {
                return definition.getTier();
            }

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.debug("Could not get tier from definition: {}", e.getMessage());

            // Calculate by components (fallback)
            try {
                var parts = controller.getParts();
                if (parts != null && !parts.isEmpty()) {
                    return parts.stream()
                            .filter(part -> part != null && part.self() != null)
                            .map(part -> part.self())
                            .filter(machine -> machine.getDefinition() != null)
                            .mapToInt(machine -> machine.getDefinition().getTier())
                            .max()
                            .orElse(0);
                }
            } catch (Exception e2) {
                GTCEUTerminalMod.LOGGER.debug("Could not calculate tier from parts: {}", e2.getMessage());
            }
        }

        return 0; // Fallback
    }

    // Remove ALL components from the multiblock
    private static Map<String, List<ComponentData>> extractAllComponents(
            MultiblockControllerMachine controller,
            Level level
    ) {
        Map<String, List<ComponentData>> components = new HashMap<>();
        Set<BlockPos> alreadyScanned = new HashSet<>();  // ⭐ NUEVO: Track escaneados

        try {
            var parts = controller.getParts();
            if (parts == null || parts.isEmpty()) {
                GTCEUTerminalMod.LOGGER.warn("Multiblock has no parts, scanning for wireless components...");
                detectWirelessAndAddonComponents(controller, level, components, alreadyScanned);
                return components;
            }

            for (var part : parts) {
                if (part == null || part.self() == null) continue;

                MetaMachine machine = part.self();
                ComponentData data = analyzeComponent(machine, level);

                if (data != null) {
                    components.computeIfAbsent(data.getCategory(), k -> new ArrayList<>())
                            .add(data);
                    alreadyScanned.add(machine.getPos());
                }
            }

            // Also extract structural components (coils, casings)
            extractStructureComponents(controller, level, components);

            detectWirelessAndAddonComponents(controller, level, components, alreadyScanned);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error extracting components: {}", e.getMessage());
        }

        return components;
    }

    private static void detectWirelessAndAddonComponents(
            MultiblockControllerMachine controller,
            Level level,
            Map<String, List<ComponentData>> components,
            Set<BlockPos> alreadyScanned) {

        try {
            BlockPos controllerPos = controller.getPos();
            int scanRadius = 5;

            for (BlockPos pos : BlockPos.betweenClosed(
                    controllerPos.offset(-scanRadius, -scanRadius, -scanRadius),
                    controllerPos.offset(scanRadius, scanRadius, scanRadius))) {

                if (alreadyScanned.contains(pos)) continue;

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IMachineBlockEntity machineBlockEntity) {
                    MetaMachine machine = machineBlockEntity.getMetaMachine();

                    if (machine == null) continue;

                    var definition = machine.getDefinition();
                    if (definition == null) continue;

                    String blockId = definition.getId().toString().toLowerCase();

                    if (machine instanceof MultiblockPartMachine part) {
                        BlockPos wanted = controller.getPos();
                        BlockPos got = null;

                        // A) getControllerPos()
                        try {
                            var m = part.getClass().getMethod("getControllerPos");
                            Object p = m.invoke(part);
                            if (p instanceof BlockPos bp) got = bp;
                        } catch (Throwable ignored) {}

                        // B) getController() -> getPos()
                        if (got == null) {
                            try {
                                var m = part.getClass().getMethod("getController");
                                Object ctrl = m.invoke(part);
                                if (ctrl != null) {
                                    try {
                                        var getPos = ctrl.getClass().getMethod("getPos");
                                        Object p = getPos.invoke(ctrl);
                                        if (p instanceof BlockPos bp) got = bp;
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable ignored) {}
                        }

                        if (got == null || !got.equals(wanted)) {
                            continue;
                        }
                    } else {
                        continue;
                    }

                    // Detect wireless energy components
                    if (blockId.contains("wireless")) {
                        if (blockId.contains("energy")) {
                            ComponentData data = analyzeComponent(machine, level);
                            if (data != null) {
                                components.computeIfAbsent(data.getCategory(), k -> new ArrayList<>())
                                        .add(data);
                                alreadyScanned.add(pos);

                                GTCEUTerminalMod.LOGGER.info("Detected wireless component: {} at {}",
                                        blockId, pos);
                            }
                        }
                    }

                    if (blockId.contains("laser") ||
                            blockId.contains("data") ||
                            blockId.contains("optical") ||
                            blockId.contains("computation")) {

                        ComponentData data = analyzeComponent(machine, level);
                        if (data != null) {
                            components.computeIfAbsent(data.getCategory(), k -> new ArrayList<>())
                                    .add(data);
                            alreadyScanned.add(pos);

                            GTCEUTerminalMod.LOGGER.info("Detected addon component: {} at {}",
                                    blockId, pos);
                        }
                    }
                }
            }

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error detecting wireless/addon components: {}", e.getMessage());
        }
    }

    // Analyze an individual component and categorize it
    private static ComponentData analyzeComponent(MetaMachine machine, Level level) {
        try {
            String type = detectComponentType(machine);

            // getTier() is in MachineDefinition
            int tier = 0;
            var definition = machine.getDefinition();
            if (definition != null) {
                tier = definition.getTier();
            }

            BlockPos pos = machine.getPos();
            String name = definition != null ? definition.getDescriptionId() : "Unknown";

            return new ComponentData(type, name, tier, pos);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.debug("Error analyzing component: {}", e.getMessage());
            return null;
        }
    }


    // Detects component type automatically
    private static String detectComponentType(MetaMachine machine) {
        try {
            var definition = machine.getDefinition();
            if (definition == null) return "Unknown Component";

            String id = definition.getId().toString().toLowerCase();

            ComponentPattern pattern = ComponentPatternRegistry.findMatch(id);
            if (pattern != null) {
                GTCEUTerminalMod.LOGGER.debug("Matched pattern '{}' for block '{}'",
                        pattern.getPattern(), id);

                String displayName = pattern.getDisplayName();

                String amperage = detectAmperage(id);
                if (amperage != null && !displayName.contains(amperage)) {
                    displayName = amperage + " " + displayName;
                }

                return displayName;
            }

            if (machine instanceof MultiblockPartMachine) {
                var block = machine.getBlockState().getBlock();
                ComponentType type = PartAbilityMapper.detectFromBlock(block);

                if (type != null) {
                    GTCEUTerminalMod.LOGGER.debug("Detected by PartAbility registry: {}",
                            type.getDisplayName());

                    String amperage = detectAmperage(id);
                    if (amperage != null) {
                        return amperage + " " + type.getDisplayName();
                    }

                    if (type == null || type == ComponentType.UNKNOWN) {
                        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

                        if (blockId.contains("wireless_energy_input")) {
                            type = ComponentType.WIRELESS_ENERGY_INPUT;
                        } else if (blockId.contains("wireless_energy_output")) {
                            type = ComponentType.WIRELESS_ENERGY_OUTPUT;
                        } else if (blockId.contains("wireless_laser_target")) {
                            type = ComponentType.WIRELESS_LASER_INPUT;
                        } else if (blockId.contains("wireless_laser_source")) {
                            type = ComponentType.WIRELESS_LASER_OUTPUT;
                        }
                    }

                    return type.getDisplayName();
                }
            }

            String detected = detectByImprovedAnalysis(id);
            if (detected != null) {
                return detected;
            }

            return definition.getDescriptionId();

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.debug("Could not detect component type: {}", e.getMessage());
            return "Unknown Component";
        }
    }

    private static String detectAmperage(String id) {
        if (id.contains("_65536a")) return "65536A";
        if (id.contains("_16384a")) return "16384A";
        if (id.contains("_4096a")) return "4096A";
        if (id.contains("_1024a")) return "1024A";
        if (id.contains("_256a")) return "256A";
        if (id.contains("_64a")) return "64A";
        if (id.contains("_16a")) return "16A";
        if (id.contains("_4a")) return "4A";
        return null;
    }

    private static String detectByImprovedAnalysis(String id) {
        // Wireless components (no tienen PartAbility estándar)
        if (id.contains("wireless")) {
            if (id.contains("energy")) {
                if (id.contains("input")) return "Wireless Energy Hatch (Input)";
                if (id.contains("output")) return "Wireless Energy Hatch (Output)";
                return "Wireless Energy Hatch";
            }
            if (id.contains("laser")) {
                if (id.contains("target")) return "Wireless Laser Target Hatch";
                if (id.contains("source")) return "Wireless Laser Source Hatch";
                return "Wireless Laser Hatch";
            }
        }

        // Substation hatches
        if (id.contains("substation")) {
            if (id.contains("input")) return "Substation Input Energy Hatch";
            if (id.contains("output")) return "Substation Output Energy Hatch";
        }

        // Energy hatches
        if (id.contains("energy")) {
            String amperage = detectAmperage(id);
            String prefix = amperage != null ? amperage + " " : "";

            if (id.contains("output") || id.contains("dynamo")) {
                return prefix + "Dynamo Hatch";
            }
            if (id.contains("input")) {
                return prefix + "Energy Hatch";
            }
        }

        if (id.contains("dynamo")) {
            String amperage = detectAmperage(id);
            return (amperage != null ? amperage + " " : "") + "Dynamo Hatch";
        }

        // Coils
        if (id.contains("coil")) return "Heating Coil";

        // Casings
        if (id.contains("casing")) return "Casing";

        return null;
    }

    // Helper method
    private static String getBaseComponentType(String id) {
        if (id.contains("energy") && id.contains("input")) return "Energy Hatch";
        if (id.contains("energy") && id.contains("output")) return "Dynamo Hatch";
        if (id.contains("dynamo")) return "Dynamo Hatch";
        return "Component";
    }


    // Extracts components from the structure, scans the multiblock pattern
    private static boolean isCandidate(BlockState state) {
        if (state == null || state.isAir()) return false;

        try {
            String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
            if ("gtceu".equals(namespace)) return true;
        } catch (Exception ignored) {}

        // Allow configured coil blocks
        return com.gtceuterminal.common.config.CoilConfig.getCoilTier(state) >= 0;
    }

    // Gets all blocks from the multiblock using flood fill — public wrapper for MSM highlight
    public static Set<BlockPos> getMultiblockBlocksPublic(MultiblockControllerMachine controller, Level level) {
        return getMultiblockBlocks(controller, level);
    }

    // Gets all blocks from the multiblock using flood fill
    private static Set<BlockPos> getMultiblockBlocks(MultiblockControllerMachine controller, Level level) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos controllerPos = controller.getPos();

        try {
            // 1. Get anchors
            List<BlockPos> anchors = new ArrayList<>();
            anchors.add(controllerPos.immutable());

            var parts = controller.getParts();
            if (parts != null && !parts.isEmpty()) {
                for (var part : parts) {
                    if (part != null && part.self() != null) {
                        BlockPos p = part.self().getPos().immutable();
                        anchors.add(p);
                        positions.add(p);
                    }
                }
            }

            // 2. Create bounds from anchors
            Bounds b = Bounds.fromAnchors(anchors, BOUNDS_PADDING);
            b = b.clampToMaxSize(controllerPos, MAX_SCAN_SIZE_XZ, MAX_SCAN_SIZE_Y);

            // Add controller if candidate
            if (isCandidate(level.getBlockState(controllerPos))) {
                positions.add(controllerPos.immutable());
            }

            // 3. Flood-fill connected blocks
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();

            for (BlockPos a : anchors) {
                if (a == null) continue;
                if (visited.add(a)) {
                    queue.add(a);
                }
            }

            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();

                for (Direction dir : Direction.values()) {
                    BlockPos next = cur.relative(dir);
                    if (!b.contains(next)) continue;
                    if (!visited.add(next)) continue;

                    BlockState s = level.getBlockState(next);
                    if (!isCandidate(s)) continue;

                    positions.add(next.immutable());
                    queue.add(next);
                }
            }

            GTCEUTerminalMod.LOGGER.debug("Found {} blocks via flood fill", positions.size());

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error in getMultiblockBlocks", e);
        }

        return positions;
    }

    // Identify if a block is a structural component (coil, casing, etc.)
    private static ComponentData identifyStructureBlock(BlockState blockState, BlockPos pos, Level level) {
        String blockId = blockState.getBlock().builtInRegistryHolder().key().location().toString();
        String blockIdLower = blockId.toLowerCase();

        // Detect coils
        if (blockIdLower.contains("coil")) {
            int tier = detectCoilTier(blockId);
            String coilName = blockState.getBlock().getName().getString();
            return new ComponentData("COIL", coilName, tier, pos);
        }

        // Detect casings
        if (blockIdLower.contains("casing")) {
            return new ComponentData("CASING", blockState.getBlock().getName().getString(), 0, pos);
        }

        return null;
    }

    private static int detectCoilTier(String blockId) {
        String lower = blockId.toLowerCase();

        if (lower.contains("cupronickel")) return 0;
        if (lower.contains("kanthal")) return 1;
        if (lower.contains("nichrome")) return 2;
        if (lower.contains("rtm_alloy") || lower.contains("rtmalloy")) return 3;
        if (lower.contains("hss_g") || lower.contains("hssg")) return 4;
        if (lower.contains("naquadah") && !lower.contains("enriched")) return 5;
        if (lower.contains("trinium")) return 6;
        if (lower.contains("tritanium")) return 7;

        return 0;
    }

    private static void extractStructureComponents(
            MultiblockControllerMachine controller,
            Level level,
            Map<String, List<ComponentData>> components
    ) {
        try {
            // Use flood fill to get all blocks of the multiblock
            Set<BlockPos> positions = getMultiblockBlocks(controller, level);

            int structureBlocksFound = 0;

            // Identify coils and casings
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                ComponentData structureComponent = identifyStructureBlock(state, pos, level);

                if (structureComponent != null) {
                    components.computeIfAbsent(structureComponent.getCategory(), k -> new ArrayList<>())
                            .add(structureComponent);
                    structureBlocksFound++;
                }
            }

            GTCEUTerminalMod.LOGGER.info("Found {} structure components from {} blocks via flood fill",
                    structureBlocksFound, positions.size());

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Could not extract structure components: {}", e.getMessage());
        }
    }

    // Class that represents an individual component
    private static final class Bounds {
        int minX, maxX, minY, maxY, minZ, maxZ;

        boolean contains(BlockPos pos) {
            if (pos == null) return false;
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        static Bounds fromAnchors(List<BlockPos> anchors, int padding) {
            Bounds b = new Bounds();
            b.minX = Integer.MAX_VALUE;
            b.minY = Integer.MAX_VALUE;
            b.minZ = Integer.MAX_VALUE;
            b.maxX = Integer.MIN_VALUE;
            b.maxY = Integer.MIN_VALUE;
            b.maxZ = Integer.MIN_VALUE;

            for (BlockPos p : anchors) {
                b.minX = Math.min(b.minX, p.getX());
                b.minY = Math.min(b.minY, p.getY());
                b.minZ = Math.min(b.minZ, p.getZ());
                b.maxX = Math.max(b.maxX, p.getX());
                b.maxY = Math.max(b.maxY, p.getY());
                b.maxZ = Math.max(b.maxZ, p.getZ());
            }

            b.minX -= padding; b.maxX += padding;
            b.minY -= padding; b.maxY += padding;
            b.minZ -= padding; b.maxZ += padding;

            return b;
        }

        Bounds clampToMaxSize(BlockPos center, int maxXZ, int maxY) {
            int sizeX = (maxX - minX) + 1;
            int sizeY = (maxY - minY) + 1;
            int sizeZ = (maxZ - minZ) + 1;

            if (sizeX > maxXZ) {
                int half = maxXZ / 2;
                minX = center.getX() - half;
                maxX = center.getX() + half;
            }
            if (sizeZ > maxXZ) {
                int half = maxXZ / 2;
                minZ = center.getZ() - half;
                maxZ = center.getZ() + half;
            }
            if (sizeY > maxY) {
                int half = maxY / 2;
                minY = center.getY() - half;
                maxY = center.getY() + half;
            }
            return this;
        }
    }

    public static class ComponentData {
        private final String category;
        private final String name;
        private final int tier;
        private final BlockPos position;

        public ComponentData(String category, String name, int tier, BlockPos position) {
            this.category = category;
            this.name = name;
            this.tier = tier;
            this.position = position;
        }

        public String getCategory() { return category; }
        public String getName() { return name; }
        public int getTier() { return tier; }
        public BlockPos getPosition() { return position; }

        @Override
        public String toString() {
            return String.format("%s (T%d) at %s", name, tier, position);
        }
    }

    // Class that represents a detected multiblock
    public static class DetectedMultiblock {
        private final String name;
        private final String modId;
        private final BlockPos position;
        private final int tier;
        private final Map<String, List<ComponentData>> components;
        private final MultiblockControllerMachine controller;

        public DetectedMultiblock(
                String name,
                String modId,
                BlockPos position,
                int tier,
                Map<String, List<ComponentData>> components,
                MultiblockControllerMachine controller
        ) {
            this.name = name;
            this.modId = modId;
            this.position = position;
            this.tier = tier;
            this.components = components;
            this.controller = controller;
        }

        public String getName() { return name; }
        public String getModId() { return modId; }
        public BlockPos getPosition() { return position; }
        public int getTier() { return tier; }
        public Map<String, List<ComponentData>> getComponents() { return components; }
        public MultiblockControllerMachine getController() { return controller; }

        public int getTotalComponentCount() {
            return components.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }

        public List<String> getComponentCategories() {
            return new ArrayList<>(components.keySet());
        }

        public boolean isFromMod(String modId) {
            return this.modId.equals(modId);
        }

        @Override
        public String toString() {
            return String.format("%s (%s) at %s - %d components",
                    name, modId, position, getTotalComponentCount());
        }
    }
}