package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.data.SchematicData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Handles copying a formed GTCEu multiblock into an {@link ItemStack}'s NBT clipboard.
 *
 * Responsibilities:
 *  - Scanning the multiblock area to collect block positions
 *  - Cleaning block-entity NBT (strips real items, keeps config)
 *  - Handling AE2 machine and cable-bus config copying
 *  - Writing the resulting {@link SchematicData} to the item tag
 */
public final class SchematicCopier {

    private SchematicCopier() {}

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void copyMultiblock(IMultiController controller, ItemStack itemStack,
                                      Player player, Level level) {
        GTCEUTerminalMod.LOGGER.info("=== COPYING MULTIBLOCK ===");

        Set<BlockPos> positions = scanMultiblockArea(controller, level);

        if (positions.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("item.gtceuterminal.schematic_copier.failed_to_scan_multiblock"),
                    true);
            GTCEUTerminalMod.LOGGER.warn("Scanned multiblock but got 0 positions");
            return;
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        BlockPos controllerPos = controller.self().getPos();

        // Skip the upper half of double-block structures (doors, etc.).
        // The cache contains both BlockPos for every door, but we only store
        // the lower half so paste and material-count see exactly one entry per door.
        java.util.Set<BlockPos> upperHalves = new java.util.HashSet<>();
        for (BlockPos pos : positions) {
            BlockState st = level.getBlockState(pos);
            if (st.getBlock() instanceof DoorBlock
                    && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                upperHalves.add(pos);
            }
        }

        for (BlockPos pos : positions) {
            if (upperHalves.contains(pos)) continue; // upper half — lower half carries the item
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            BlockPos relativePos = pos.subtract(controllerPos);
            blocks.put(relativePos, state);

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    CompoundTag tag = be.saveWithFullMetadata();
                    blockEntities.put(relativePos, cleanNBTForSchematic(tag));
                } catch (Exception e) {
                    GTCEUTerminalMod.LOGGER.error("Failed to save block entity at {}", pos, e);
                }
            }
        }

        Direction facing = controller.self().getFrontFacing();
        String multiblockType = controller.self().getDefinition().getId().toString();

        SchematicData clipboard = new SchematicData(
                "Clipboard", multiblockType, blocks, blockEntities, facing.getName());

        CompoundTag itemTag = itemStack.getOrCreateTag();
        itemTag.put("Clipboard", clipboard.toNBT());
        itemStack.setTag(itemTag);

        player.displayClientMessage(
                Component.translatable("item.gtceuterminal.schematic_copier.multiblock_copied", blocks.size()),
                true);

        GTCEUTerminalMod.LOGGER.info("Multiblock copied: {} blocks", blocks.size());
    }

    // ── Area scanning ─────────────────────────────────────────────────────────

    /**
     * Returns the set of block positions that belong to the multiblock.
     *
     * Primary: uses the multiblock state cache (exact parts only).
     * Fallback: computes the bounding box of all parts and scans only the part
     *           positions — no extra padding — so terrain blocks are not included.
     */
    private static Set<BlockPos> scanMultiblockArea(IMultiController controller, Level level) {
        Set<BlockPos> positions = new HashSet<>();

        // Primary: use the cache, which contains only the actual multiblock blocks
        try {
            Collection<BlockPos> cached = controller.getMultiblockState().getCache();
            if (cached != null && !cached.isEmpty()) {
                positions.addAll(cached);
                GTCEUTerminalMod.LOGGER.info("Got {} positions from multiblock cache", positions.size());
                return positions;
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Multiblock cache unavailable, falling back to part positions");
        }

        // Fallback: collect positions directly from the part list instead of
        // scanning the bounding box. This avoids capturing terrain blocks.
        List<IMultiPart> parts = controller.getParts();
        if (parts.isEmpty()) {
            GTCEUTerminalMod.LOGGER.warn("No parts found in multiblock");
            return positions;
        }

        positions.add(controller.self().getPos());
        for (IMultiPart part : parts) {
            positions.add(part.self().getPos());
        }

        GTCEUTerminalMod.LOGGER.info("Fallback scan: collected {} part positions", positions.size());
        return positions;
    }

    // ── NBT cleaning ──────────────────────────────────────────────────────────

    /**
     * Strips transient state and real-item data from a block-entity tag,
     * keeping only the configuration needed to reconstruct the block on paste.
     */
    private static CompoundTag cleanNBTForSchematic(CompoundTag originalTag) {
        if (originalTag == null || originalTag.isEmpty()) return new CompoundTag();

        CompoundTag clean = new CompoundTag();

        copyString(originalTag, clean, "id");

        // Covers / upgrades
        copyTag(originalTag, clean, "CoverContainer");
        copyTag(originalTag, clean, "Covers");
        copyTag(originalTag, clean, "Upgrades");

        // Tier / material
        if (originalTag.contains("Tier"))     clean.putInt("Tier", originalTag.getInt("Tier"));
        copyString(originalTag, clean, "Material");

        // Facing / rotation
        copyString(originalTag, clean, "Facing");
        copyString(originalTag, clean, "FrontFacing");

        // User configuration
        if (originalTag.contains("WorkingEnabled"))
            clean.putBoolean("WorkingEnabled", originalTag.getBoolean("WorkingEnabled"));
        if (originalTag.contains("AllowInputFromOutputSide"))
            clean.putBoolean("AllowInputFromOutputSide", originalTag.getBoolean("AllowInputFromOutputSide"));

        // Identity / cosmetics
        copyString(originalTag, clean, "CustomName");
        if (originalTag.contains("PartIndex"))
            clean.putInt("PartIndex", originalTag.getInt("PartIndex"));
        copyTag(originalTag, clean, "ownerUUID");
        if (originalTag.contains("paintingColor"))
            clean.putInt("paintingColor", originalTag.getInt("paintingColor"));

        // AE2 config (opt-in via server config)
        if (com.gtceuterminal.common.config.ItemsConfig.isSchAllowAE2ConfigCopy()) {
            String blockId = originalTag.getString("id").toLowerCase();
            if (isGTCEuAE2Machine(blockId))  copyGTCEuAE2MachineConfig(originalTag, clean);
            if (isAE2CableBus(blockId))       copyAE2CableBusConfig(originalTag, clean);
        }

        GTCEUTerminalMod.LOGGER.debug("Cleaned NBT — original keys: {}, clean keys: {}",
                originalTag.getAllKeys().size(), clean.getAllKeys().size());
        return clean;
    }

    // ── AE2 detection ─────────────────────────────────────────────────────────

    private static boolean isGTCEuAE2Machine(String blockId) {
        if (!blockId.startsWith("gtceu:")) return false;
        String path = blockId.substring("gtceu:".length());
        return path.startsWith("me_") || path.contains("_me_");
    }

    private static boolean isAE2CableBus(String blockId) {
        return blockId.contains("ae2:cable_bus")
                || blockId.contains("appliedenergistics2:cable_bus");
    }

    // ── AE2 config copying ────────────────────────────────────────────────────

    /**
     * Copies AE2-related config from a GTCEu ME bus/hatch NBT tag,
     * stripping real-item stock to prevent duplication on paste.
     */
    private static void copyGTCEuAE2MachineConfig(CompoundTag src, CompoundTag dst) {
        if (src.contains("inventory")) {
            net.minecraft.nbt.Tag outerRaw = src.get("inventory");
            if (outerRaw instanceof CompoundTag outerTag) {
                CompoundTag safeOuter = new CompoundTag();

                if (outerTag.contains("isDistinct"))
                    safeOuter.putBoolean("isDistinct", outerTag.getBoolean("isDistinct"));

                // Keep slot count but wipe real items from storage
                if (outerTag.contains("storage")) {
                    net.minecraft.nbt.Tag stRaw = outerTag.get("storage");
                    if (stRaw instanceof CompoundTag stTag) {
                        CompoundTag safeSt = new CompoundTag();
                        if (stTag.contains("Size")) safeSt.putInt("Size", stTag.getInt("Size"));
                        safeSt.put("Items", new net.minecraft.nbt.ListTag());
                        safeOuter.put("storage", safeSt);
                    }
                }

                // Keep slot config, drop stock
                if (outerTag.contains("inventory")) {
                    net.minecraft.nbt.Tag innerRaw = outerTag.get("inventory");
                    if (innerRaw instanceof net.minecraft.nbt.ListTag slotList) {
                        net.minecraft.nbt.ListTag safeList = new net.minecraft.nbt.ListTag();
                        for (net.minecraft.nbt.Tag slotRaw : slotList) {
                            if (slotRaw instanceof CompoundTag slotTag) {
                                CompoundTag safeSlot = new CompoundTag();
                                if (slotTag.contains("t")) safeSlot.put("t", slotTag.get("t").copy());
                                CompoundTag safeP = new CompoundTag();
                                if (slotTag.contains("p")) {
                                    net.minecraft.nbt.Tag pRaw = slotTag.get("p");
                                    if (pRaw instanceof CompoundTag pTag && pTag.contains("config"))
                                        safeP.put("config", pTag.get("config").copy());
                                } else if (slotTag.contains("config")) {
                                    safeP.put("config", slotTag.get("config").copy());
                                }
                                safeSlot.put("p", safeP);
                                safeList.add(safeSlot);
                            }
                        }
                        safeOuter.put("inventory", safeList);
                    }
                }
                dst.put("inventory", safeOuter);
            }
        }

        if (src.contains("circuitInventory"))  dst.put("circuitInventory", src.get("circuitInventory").copy());
        if (src.contains("circuitSlotEnabled")) dst.putBoolean("circuitSlotEnabled", src.getBoolean("circuitSlotEnabled"));
        if (src.contains("isDistinct"))        dst.putBoolean("isDistinct", src.getBoolean("isDistinct"));
        if (src.contains("filterHandler"))     dst.put("filterHandler", src.get("filterHandler").copy());
        if (src.contains("autoPull"))          dst.putBoolean("autoPull", src.getBoolean("autoPull"));
        if (src.contains("minStackSize"))      dst.putInt("minStackSize", src.getInt("minStackSize"));
        if (src.contains("ticksPerCycle"))     dst.putInt("ticksPerCycle", src.getInt("ticksPerCycle"));

        GTCEUTerminalMod.LOGGER.debug("Copied GTCEu AE2 machine config — keys: {}", dst.getAllKeys());
    }

    /** Copies AE2 cable-bus part configuration, stripping real-item state. */
    private static void copyAE2CableBusConfig(CompoundTag src, CompoundTag dst) {
        String[] sideKeys = { "down", "up", "north", "south", "east", "west", "cable" };

        for (String side : sideKeys) {
            if (!src.contains(side)) continue;
            net.minecraft.nbt.Tag rawSide = src.get(side);
            if (!(rawSide instanceof CompoundTag partData)) continue;
            dst.put(side, sanitizeAE2PartData(partData));
            GTCEUTerminalMod.LOGGER.debug("AE2 cable bus: copied part on side '{}'", side);
        }

        if (src.contains("hasRedstone")) dst.putInt("hasRedstone", src.getInt("hasRedstone"));
        for (String side : sideKeys) {
            String facadeKey = "facade_" + side;
            if (src.contains(facadeKey)) dst.put(facadeKey, src.get(facadeKey).copy());
        }
    }

    private static CompoundTag sanitizeAE2PartData(CompoundTag partData) {
        CompoundTag safe = new CompoundTag();

        copyString(partData, safe, "id");
        copyTag(partData, safe, "config");
        copyTag(partData, safe, "upgrades");
        if (partData.contains("priority")) safe.putInt("priority", partData.getInt("priority"));

        for (String key : new String[]{
                "redstone_controlled", "fuzzy_mode", "scheduling_mode", "craft_only",
                "access", "storage_filter", "lock_crafting_mode", "pattern_access_terminal"
        }) {
            if (partData.contains(key)) safe.putString(key, partData.getString(key));
        }

        if (partData.contains("reportingValue")) safe.putLong("reportingValue", partData.getLong("reportingValue"));
        copyString(partData, safe, "customName");
        copyTag(partData, safe, "cm");

        return safe;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void copyString(CompoundTag src, CompoundTag dst, String key) {
        if (src.contains(key)) dst.putString(key, src.getString(key));
    }

    private static void copyTag(CompoundTag src, CompoundTag dst, String key) {
        if (src.contains(key)) dst.put(key, src.get(key).copy());
    }
}