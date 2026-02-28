package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.ClientProxy;
import com.gtceuterminal.common.data.SchematicData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import net.minecraft.world.item.Items;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.ae2.MENetworkItemExtractor;
import com.gtceuterminal.common.ae2.MENetworkFluidHandlerWrapper;
import com.gtceuterminal.common.ae2.WirelessTerminalHandler;
import com.gtceuterminal.common.pattern.FluidPlacementHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.me.helpers.PlayerSource;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluid;

public class SchematicInterfaceBehavior {

    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();

        boolean shiftDown = player.isShiftKeyDown();

        if (shiftDown) {
            // Try to copy multiblock
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);
            if (machine instanceof IMultiController) {
                IMultiController controller = (IMultiController) machine;
                if (controller.isFormed()) {
                    if (!level.isClientSide) {
                        copyMultiblock(controller, itemStack, player, level, blockPos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            // Open GUI if not clicking on a multiblock
            if (level.isClientSide) {
                // GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Schematic UI");
                ClientProxy.openSchematicGUI(itemStack, player, Collections.emptyList());
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.CONSUME;
        }

        // Right click normal (paste)
        if (!level.isClientSide) {
            // Match vanilla placement behavior:
            // - If the clicked block is replaceable (tall grass, snow, etc.), place "into" it.
            // - Otherwise place on the adjacent position of the clicked face.
            // This prevents the controller anchor from ending up inside solid blocks when aiming at the ground.
            BlockPos anchor = blockPos;
            try {
                BlockState clicked = level.getBlockState(blockPos);
                if (clicked != null && !clicked.isAir() && !clicked.canBeReplaced()) {
                    anchor = blockPos.relative(context.getClickedFace());
                }
            } catch (Exception ignored) {
                anchor = blockPos.relative(context.getClickedFace());
            }

            pasteSchematic(itemStack, player, level, anchor, context.getClickedFace());
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }


    // Handle right-click in air to paste at EXACT ghost preview position
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (player.isShiftKeyDown()) {
            // Shift + Right-click in air: Open GUI
            if (level.isClientSide) {
                // GTCEUTerminalMod.LOGGER.info("Client: Requesting server to open Schematic UI");
                ClientProxy.openSchematicGUI(itemStack, player, Collections.emptyList());
                return InteractionResultHolder.success(itemStack);
            }
            return InteractionResultHolder.consume(itemStack);
        }

        // Right-click in air (no shift): Paste at EXACT ghost preview position
        if (hasClipboard(itemStack)) {
            if (!level.isClientSide) {
                // Get schematic to calculate optimal distance (same as renderer)
                CompoundTag itemTag = itemStack.getTag();
                SchematicData clipboard = SchematicData.fromNBT(
                        itemTag.getCompound("Clipboard"),
                        level.registryAccess()
                );

                // Calculate same distance as SchematicPreviewRenderer
                double distance = calculateOptimalDistance(clipboard);

                // Use EXACT same logic as SchematicPreviewRenderer.getTargetPlacementPos()
                BlockPos targetPos = getTargetPlacementPos(player, distance);
                Direction facing = Direction.UP;

                pasteSchematic(itemStack, player, level, targetPos, facing);
                return InteractionResultHolder.success(itemStack);
            }
            return InteractionResultHolder.consume(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    private double calculateOptimalDistance(SchematicData schematic) {
        BlockPos size = schematic.getSize();
        int maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

        double distance = 4.0 + (maxDimension / 2.0);
        return Math.min(15.0, Math.max(4.0, distance));
    }

    private BlockPos getTargetPlacementPos(Player player, double distance) {
        double raycastDistance = Math.max(10.0, distance + 5.0);
        HitResult hitResult = player.pick(raycastDistance, 0.0f, false);

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 targetVec = eyePos.add(lookVec.scale(distance));

        return new BlockPos(
                (int) Math.floor(targetVec.x),
                (int) Math.floor(targetVec.y),
                (int) Math.floor(targetVec.z)
        );
    }


    // Check if item has clipboard data
    private boolean hasClipboard(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        if (tag == null || !tag.contains("Clipboard")) {
            return false;
        }
        CompoundTag clipboardTag = tag.getCompound("Clipboard");
        return clipboardTag.contains("Blocks") && !clipboardTag.getList("Blocks", 10).isEmpty();
    }

    private void copyMultiblock(IMultiController controller, ItemStack itemStack, Player player, Level level, BlockPos blockPos) {
        // GTCEUTerminalMod.LOGGER.info("=== COPYING MULTIBLOCK ===");

        Set<BlockPos> positions = scanMultiblockArea(controller, level);

        if (positions.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§cFailed to scan multiblock!"),
                    true
            );
            GTCEUTerminalMod.LOGGER.warn("Scanned multiblock but got 0 positions");
            return;
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();

        BlockPos controllerPos = controller.self().getPos();

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            BlockPos relativePos = pos.subtract(controllerPos);
            blocks.put(relativePos, state);

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    CompoundTag tag = be.saveWithFullMetadata();
                    CompoundTag cleanTag = cleanNBTForSchematic(tag);
                    blockEntities.put(relativePos, cleanTag);
                } catch (Exception e) {
                    GTCEUTerminalMod.LOGGER.error("Failed to save block entity at {}", pos, e);
                }
            }
        }

        Direction facing = controller.self().getFrontFacing();
        String multiblockType = controller.self().getDefinition().getId().toString();

        SchematicData clipboard = new SchematicData(
                "Clipboard",
                multiblockType,
                blocks,
                blockEntities,
                facing.getName()
        );

        CompoundTag itemTag = itemStack.getOrCreateTag();
        itemTag.put("Clipboard", clipboard.toNBT());
        itemStack.setTag(itemTag);

        player.displayClientMessage(
                Component.literal("§aMultiblock copied! " + blocks.size() + " blocks"),
                true
        );

        // GTCEUTerminalMod.LOGGER.info("Multiblock copied: {} blocks", blocks.size());
    }

    private CompoundTag cleanNBTForSchematic(CompoundTag originalTag) {
        if (originalTag == null || originalTag.isEmpty()) {
            return new CompoundTag();
        }

        CompoundTag cleanTag = new CompoundTag();

        // 1. BlockID
        if (originalTag.contains("id")) {
            cleanTag.putString("id", originalTag.getString("id"));
        }

        // 2. Covers
        if (originalTag.contains("CoverContainer")) {
            cleanTag.put("CoverContainer", originalTag.get("CoverContainer").copy());
        }
        if (originalTag.contains("Covers")) {
            cleanTag.put("Covers", originalTag.get("Covers").copy());
        }

        // 3. Upgrades
        if (originalTag.contains("Upgrades")) {
            cleanTag.put("Upgrades", originalTag.get("Upgrades").copy());
        }

        // 4. Tier/Material
        if (originalTag.contains("Tier")) {
            cleanTag.putInt("Tier", originalTag.getInt("Tier"));
        }
        if (originalTag.contains("Material")) {
            cleanTag.putString("Material", originalTag.getString("Material"));
        }

        // 5. Facing/Rotation
        if (originalTag.contains("Facing")) {
            cleanTag.putString("Facing", originalTag.getString("Facing"));
        }
        if (originalTag.contains("FrontFacing")) {
            cleanTag.putString("FrontFacing", originalTag.getString("FrontFacing"));
        }

        // 6. User's Configuration (WorkingEnabled, AllowInputFromOutputSide, etc.)
        if (originalTag.contains("WorkingEnabled")) {
            cleanTag.putBoolean("WorkingEnabled", originalTag.getBoolean("WorkingEnabled"));
        }
        if (originalTag.contains("AllowInputFromOutputSide")) {
            cleanTag.putBoolean("AllowInputFromOutputSide", originalTag.getBoolean("AllowInputFromOutputSide"));
        }

        // 7. Custom name
        if (originalTag.contains("CustomName")) {
            cleanTag.putString("CustomName", originalTag.getString("CustomName"));
        }

        // 8. Multiblock-specific data (part index, etc.)
        if (originalTag.contains("PartIndex")) {
            cleanTag.putInt("PartIndex", originalTag.getInt("PartIndex"));
        }

        // 8b. Machine owner UUID — needed for wireless hatches (GTMThings) and any GTCEu machine
        // that restricts GUI/break access to the owner. This is the @SaveField "ownerUUID" from
        // MetaMachine, serialized by SyncDataHolder with the field name as the NBT key.
        // Note: during paste we REPLACE this with the pasting player's UUID so each player
        // owns their own wireless hatches (see the paste loop below).
        if (originalTag.contains("ownerUUID")) {
            cleanTag.put("ownerUUID", originalTag.get("ownerUUID").copy());
        }

        // 8c. Painting color (@SaveField "paintingColor" in MetaMachine)
        if (originalTag.contains("paintingColor")) {
            cleanTag.putInt("paintingColor", originalTag.getInt("paintingColor"));
        }

        // 9. GTCEu-AE2 integrated machine configuration (ME Input Bus, ME Output Bus, ME Stocking Bus, etc.)
        // These are GTCEu MetaMachine block entities (NOT ae2:cable_bus) that integrate with AE2.
        // Their fields are serialized by SyncDataHolder using the Java field name as the NBT key.
        //
        // Key fields (from ItemBusPartMachine + MEInputBusPartMachine source):
        //   "inventory"        → ExportOnlyAEItemList (array of slots, each with "config" and "stock")
        //   "circuitInventory" → NotifiableItemStackHandler (ghost circuit slot)
        //   "circuitSlotEnabled" → boolean
        //   "isDistinct"       → boolean
        //   "filterHandler"    → item filter config
        //   "autoPull"         → boolean (ME Stocking Bus only)
        //
        // DUPE RISK: each slot in "inventory" has both "config" (what item to request — safe)
        // and "stock" (items currently pulled from ME network — REAL items, must be stripped).
        // We copy "inventory" but sanitize each slot to keep only "config", dropping "stock".
        String blockId = originalTag.getString("id").toLowerCase();
        // 9-10. AE2 configuration — only if enabled in config
        if (com.gtceuterminal.common.config.ItemsConfig.isSchAllowAE2ConfigCopy()) {
            if (isGTCEuAE2Machine(blockId)) {
                copyGTCEuAE2MachineConfig(originalTag, cleanTag);
            }
            if (isAE2CableBus(blockId)) {
                copyAE2CableBusConfig(originalTag, cleanTag);
            }
        }

        GTCEUTerminalMod.LOGGER.debug("Cleaned NBT - Original keys: {}, Clean keys: {}",
                originalTag.getAllKeys().size(), cleanTag.getAllKeys().size());

        return cleanTag;
    }

    /**
     * Returns true for GTCEu machines that integrate with AE2 internally:
     * ME Input Bus, ME Output Bus, ME Stocking Bus, ME Input Hatch, ME Output Hatch,
     * ME Stocking Hatch, ME Pattern Buffer.
     *
     * These are GTCEu MetaMachine block entities (registry namespace "gtceu"),
     * NOT ae2:cable_bus. Their block IDs look like "gtceu:luv_me_item_input_bus".
     */
    private boolean isGTCEuAE2Machine(String blockId) {
        // Only match GTCEu machines that integrate with AE2 — i.e. the ME-prefixed buses/hatches.
        // Must start with "gtceu:" and contain "me_" to avoid false matches on plain buses like
        // "gtceu:lv_output_bus" or maintenance/muffler hatches that also contain "_hatch"/"_bus".
        if (!blockId.startsWith("gtceu:")) return false;
        String path = blockId.substring("gtceu:".length()); // e.g. "me_stocking_input_bus"
        return path.startsWith("me_")
                || path.contains("_me_");
    }

    /**
     * Copies GTCEu AE2-integrated machine configuration, sanitizing real-item state.
     *
     * Source analysis (GTCEu 1.20.1):
     *
     * ItemBusPartMachine @SaveField fields (serialized by field name via SyncDataHolder):
     *   "inventory"           → ExportOnlyAEItemList — each slot is a CompoundTag with:
     *                             "config" → GenericStack (what item to request — SAFE)
     *                             "stock"  → GenericStack (items currently in the bus — DUPE RISK, excluded)
     *   "circuitInventory"    → NotifiableItemStackHandler (ghost circuit config — SAFE)
     *   "circuitSlotEnabled"  → boolean
     *   "isDistinct"          → boolean
     *   "filterHandler"       → item filter configuration — SAFE
     *
     * MEStockingBusPartMachine additionally:
     *   "autoPull"            → boolean (auto-pull from ME)
     *
     * The "inventory" field serialization chain:
     *   SyncDataHolder → field name "inventory" → ExportOnlyAEItemList (ISyncManaged)
     *   → each ExportOnlyAEItemSlot.serializeNBT() → {config: ..., stock: ...}
     *   The slot array is stored as a CompoundTag with integer string keys: "0", "1", ...
     *   (this is how GTCEu's array transformers work — see ArrayTransformer in the sync system)
     */
    /**
     * Copies GTCEu AE2-integrated machine configuration, sanitizing real-item state.
     *
     * ACTUAL NBT structure (confirmed via live log dump):
     *
     *   machineBETag {
     *     "inventory": {                    ← ExportOnlyAEItemList (ISyncManaged)
     *       "isDistinct": 0b,
     *       "storage":    { Size, Items },  ← the actual item buffer (DUPE RISK if non-empty)
     *       "inventory":  [                 ← ExportOnlyAEItemSlot[] — only present on ME buses
     *         { "p": { "config": { "#": 1L, "#c": "ae2:i", "id": "..." } }, "t": 11b },
     *         { "p": {}, "t": 11b },        ← empty slot — MUST keep "t" or LDLib crashes
     *       ]
     *     }
     *   }
     *
     * The "p" key is LDLib's payload wrapper (NbtTagPayload). The "t" key is the type byte (11 = CompoundTag).
     * Empty slots MUST have { "p": {}, "t": 11b } — omitting "t" causes NullPayload crash on paste.
     * "stock" is inside "p" alongside "config" — we strip it to avoid item duplication.
     * "storage" inside the outer inventory holds actual pulled items — we strip it too.
     */
    private void copyGTCEuAE2MachineConfig(CompoundTag originalTag, CompoundTag cleanTag) {
        // ── Slot configuration ("inventory") ─────────────────────────────────────
        if (originalTag.contains("inventory")) {
            net.minecraft.nbt.Tag outerRaw = originalTag.get("inventory");
            if (outerRaw instanceof CompoundTag outerTag) {
                CompoundTag safeOuter = new CompoundTag();

                // Preserve isDistinct inside the outer inventory tag
                if (outerTag.contains("isDistinct")) {
                    safeOuter.putBoolean("isDistinct", outerTag.getBoolean("isDistinct"));
                }

                // Preserve "storage" structure but clear the Items list to avoid duplication.
                // The storage holds items currently pulled from ME — real items, DUPE RISK.
                // We keep the Size so LDLib initializes the handler with the right slot count.
                if (outerTag.contains("storage")) {
                    net.minecraft.nbt.Tag stRaw = outerTag.get("storage");
                    if (stRaw instanceof CompoundTag stTag) {
                        CompoundTag safeSt = new CompoundTag();
                        if (stTag.contains("Size")) safeSt.putInt("Size", stTag.getInt("Size"));
                        safeSt.put("Items", new net.minecraft.nbt.ListTag()); // empty — no real items
                        safeOuter.put("storage", safeSt);
                    }
                }

                // The AE slot config array — only present on ME buses, not plain item buses.
                // Each element is { "p": { "config": <GenericStack>, ["stock": <GenericStack>] }, "t": 11b }
                // "config" = what item to request from ME (safe to copy)
                // "stock"  = items currently stocked (DUPE RISK — excluded)
                // CRITICAL: must keep "t": 11b on every element or LDLib throws NullPayload on paste.
                if (outerTag.contains("inventory")) {
                    net.minecraft.nbt.Tag innerRaw = outerTag.get("inventory");
                    if (innerRaw instanceof net.minecraft.nbt.ListTag slotList) {
                        net.minecraft.nbt.ListTag safeList = new net.minecraft.nbt.ListTag();
                        for (net.minecraft.nbt.Tag slotRaw : slotList) {
                            if (slotRaw instanceof CompoundTag slotTag) {
                                CompoundTag safeSlot = new CompoundTag();

                                // Preserve the type tag — LDLib requires this
                                if (slotTag.contains("t")) {
                                    safeSlot.put("t", slotTag.get("t").copy());
                                }

                                // Rebuild payload: keep only "config", drop "stock"
                                if (slotTag.contains("p")) {
                                    net.minecraft.nbt.Tag pRaw = slotTag.get("p");
                                    if (pRaw instanceof CompoundTag pTag) {
                                        CompoundTag safeP = new CompoundTag();
                                        if (pTag.contains("config")) {
                                            safeP.put("config", pTag.get("config").copy());
                                        }
                                        // "stock" excluded — real items (DUPE RISK)
                                        safeSlot.put("p", safeP);
                                    }
                                } else {
                                    // No "p" wrapper — older format, try direct config key
                                    CompoundTag safeP = new CompoundTag();
                                    if (slotTag.contains("config")) {
                                        safeP.put("config", slotTag.get("config").copy());
                                    }
                                    safeSlot.put("p", safeP);
                                }

                                safeList.add(safeSlot);
                            }
                        }
                        safeOuter.put("inventory", safeList);
                    }
                }

                cleanTag.put("inventory", safeOuter);
            }
        }

        // ── Circuit slot ──────────────────────────────────────────────────────────
        if (originalTag.contains("circuitInventory")) {
            cleanTag.put("circuitInventory", originalTag.get("circuitInventory").copy());
        }
        if (originalTag.contains("circuitSlotEnabled")) {
            cleanTag.putBoolean("circuitSlotEnabled", originalTag.getBoolean("circuitSlotEnabled"));
        }

        // ── Bus behavior settings ─────────────────────────────────────────────────
        if (originalTag.contains("isDistinct")) {
            cleanTag.putBoolean("isDistinct", originalTag.getBoolean("isDistinct"));
        }
        if (originalTag.contains("filterHandler")) {
            cleanTag.put("filterHandler", originalTag.get("filterHandler").copy());
        }

        // ── Stocking bus extras ───────────────────────────────────────────────────
        if (originalTag.contains("autoPull")) {
            cleanTag.putBoolean("autoPull", originalTag.getBoolean("autoPull"));
        }
        if (originalTag.contains("minStackSize")) {
            cleanTag.putInt("minStackSize", originalTag.getInt("minStackSize"));
        }
        if (originalTag.contains("ticksPerCycle")) {
            cleanTag.putInt("ticksPerCycle", originalTag.getInt("ticksPerCycle"));
        }

        GTCEUTerminalMod.LOGGER.debug("Copied GTCEu AE2 machine config — keys: {}", cleanTag.getAllKeys());
    }


    /**
     * AE2's CableBusBlockEntity stores parts under the serialized direction name as the key.
     * Each face key ("down","up","north","south","east","west") or "cable" (center) maps to
     * a CompoundTag containing:
     *   - "id"                → the part's registry ID (e.g. "ae2:import_bus")
     *   - everything the part's writeToNBT() writes (flat in the same tag, not nested)
     *
     * This method checks if the block entity belongs to AE2's cable bus system.
     * The block entity itself has id "ae2:cable_bus" in its saveWithFullMetadata() output.
     */
    private boolean isAE2CableBus(String blockId) {
        return blockId.contains("ae2:cable_bus")
                || blockId.contains("appliedenergistics2:cable_bus");
    }

    /**
     * Copies the full AE2 CableBusBlockEntity configuration into the schematic tag.
     *
     * Structure (from CableBusContainer.writeToNBT + appeng source):
     *
     *   Root tag (the block entity tag):
     *     "down" / "up" / "north" / "south" / "east" / "west" / "cable"  → CompoundTag per face
     *
     *   Each face CompoundTag (written by CableBusContainer, then part.writeToNBT):
     *     "id"                  → String  — part registry ID (e.g. "ae2:import_bus")
     *     "customName"          → String  — optional custom name (AEBasePart)
     *
     *     From UpgradeablePart (all buses that support upgrades):
     *       "upgrades"          → CompoundTag — installed speed/capacity/etc. cards
     *       IConfigManager settings written FLAT (from ConfigManager.writeToNBT):
     *         "redstone_controlled" → String  — RedstoneMode enum name
     *         "fuzzy_mode"          → String  — FuzzyMode enum name
     *         "access"              → String  — AccessRestriction (StorageBus only)
     *         "storage_filter"      → String  — StorageFilter (StorageBus only)
     *         "scheduling_mode"     → String  — SchedulingMode (Export/Import bus)
     *         "craft_only"          → String  — YesNo (Export bus with crafting card)
     *
     *     From IOBusPart (Import Bus + Export Bus):
     *       "config"            → CompoundTag — filter slots (written by ConfigInventory.writeToChildTag)
     *
     *     From StorageBusPart:
     *       "config"            → CompoundTag — filter slots
     *       "priority"          → int
     *
     *     From AbstractLevelEmitterPart:
     *       "config"            → CompoundTag — single item being watched
     *       "reportingValue"    → long  — threshold amount
     *
     *     From InterfaceLogic (ME Interface / Pattern Provider):
     *       "config"            → CompoundTag — what to keep stocked
     *       "storage"           → CompoundTag — current stored items
     *       "upgrades"          → CompoundTag
     *       "cm"                — IConfigManager flat keys (priority_mode etc.)
     *       "priority"          → int
     */
    private void copyAE2CableBusConfig(CompoundTag originalTag, CompoundTag cleanTag) {
        // The cable bus structure is flat at the root: one sub-tag per Direction.
        // Direction.getSerializedName() returns the lower-case name; "cable" is used for null (center).
        String[] sideKeys = { "down", "up", "north", "south", "east", "west", "cable" };

        for (String sideKey : sideKeys) {
            if (!originalTag.contains(sideKey)) continue;

            net.minecraft.nbt.Tag rawSideTag = originalTag.get(sideKey);
            if (!(rawSideTag instanceof CompoundTag partData)) continue;

            // Strip transient/dupe-risk data from each part before copying.
            CompoundTag safePartData = sanitizeAE2PartData(partData);
            cleanTag.put(sideKey, safePartData);

            GTCEUTerminalMod.LOGGER.debug("AE2 cable bus: copied part on side '{}' (id={})",
                    sideKey, safePartData.getString("id"));
        }

        // Also preserve the cable's own data (hasRedstone state, facade info)
        if (originalTag.contains("hasRedstone")) {
            cleanTag.putInt("hasRedstone", originalTag.getInt("hasRedstone"));
        }
        // Facades (stored as "facade_up", "facade_down", etc. by FacadeContainer)
        for (String sideKey : sideKeys) {
            String facadeKey = "facade_" + sideKey;
            if (originalTag.contains(facadeKey)) {
                cleanTag.put(facadeKey, originalTag.get(facadeKey).copy());
            }
        }
    }

    /**
     * Sanitizes a single AE2 part's NBT data for safe use in schematics.
     *
     * AE2 parts serialize two conceptually different things into the same tag:
     *
     *   CONFIGURATION (safe to copy — describes how the part should behave):
     *     "id"                  → part registry ID
     *     "config"              → ConfigInventory with filter keys (AEKey references, no real items)
     *     "upgrades"            → installed upgrade cards
     *     "priority"            → storage priority
     *     "redstone_controlled" → RedstoneMode setting
     *     "fuzzy_mode"          → FuzzyMode setting
     *     "scheduling_mode"     → SchedulingMode (export/import bus)
     *     "craft_only"          → YesNo (export bus)
     *     "access"              → AccessRestriction (storage bus)
     *     "storage_filter"      → StorageFilter (storage bus)
     *     "reportingValue"      → level emitter threshold
     *     "customName"          → optional custom name
     *
     *   STATE (NOT safe to copy — contains actual items that would be duplicated):
     *     "storage"             → ME Interface physical item buffer (real items with counts)
     *
     *   TRANSIENT (not written by writeToNBT, but strip defensively):
     *     grid node data written by mainNode.saveToNBT() — safe, but position-specific
     *
     * Note: "config" uses ConfigInventory.Mode.CONFIG_STACKS (for Interface) or
     * CONFIG_TYPES (for buses) — neither contains real extractable items, only AEKey
     * references used as filters. Only "storage" (Mode.STORAGE) contains real items.
     */
    private CompoundTag sanitizeAE2PartData(CompoundTag partData) {
        CompoundTag safe = new CompoundTag();

        // Always copy the part ID — needed to reconstruct the part on paste
        if (partData.contains("id")) {
            safe.putString("id", partData.getString("id"));
        }

        // Filter configuration (AEKey references only, not real items — safe)
        if (partData.contains("config")) {
            safe.put("config", partData.get("config").copy());
        }

        // Installed upgrade cards
        if (partData.contains("upgrades")) {
            safe.put("upgrades", partData.get("upgrades").copy());
        }

        // Storage channel priority
        if (partData.contains("priority")) {
            safe.putInt("priority", partData.getInt("priority"));
        }

        // IConfigManager settings — all written as String keys by ConfigManager.writeToNBT()
        // These are the exact Setting.getName() values from appeng.api.config.Settings
        for (String settingKey : new String[]{
                "redstone_controlled",  // RedstoneMode  (all buses)
                "fuzzy_mode",           // FuzzyMode     (storage/import/export bus)
                "scheduling_mode",      // SchedulingMode (import/export bus)
                "craft_only",           // YesNo         (export bus with crafting card)
                "access",               // AccessRestriction (storage bus)
                "storage_filter",       // StorageFilter (storage bus)
                "lock_crafting_mode",   // LockCraftingMode (pattern provider)
                "pattern_access_terminal" // YesNo (pattern provider terminal visibility)
        }) {
            if (partData.contains(settingKey)) {
                safe.putString(settingKey, partData.getString(settingKey));
            }
        }

        // Level emitter threshold amount
        if (partData.contains("reportingValue")) {
            safe.putLong("reportingValue", partData.getLong("reportingValue"));
        }

        // Custom name
        if (partData.contains("customName")) {
            safe.putString("customName", partData.getString("customName"));
        }

        // ME Interface / Pattern Provider: patterns are configuration, not items — safe
        // "cm" is the IConfigManager for the interface (priority_mode etc.)
        if (partData.contains("cm")) {
            safe.put("cm", partData.get("cm").copy());
        }

        // INTENTIONALLY EXCLUDED: "storage" — this is the ME Interface's physical item buffer.
        // Copying it would duplicate the items that were inside the original interface.
        // The pasted interface will start empty, which is the correct behavior.

        return safe;
    }

    private Set<BlockPos> scanMultiblockArea(IMultiController controller, Level level) {
        Set<BlockPos> positions = new HashSet<>();

        try {
            Collection<BlockPos> cachePos = controller.getMultiblockState().getCache();
            if (cachePos != null && !cachePos.isEmpty()) {
                positions.addAll(cachePos);
                // GTCEUTerminalMod.LOGGER.info("Got {} positions from multiblock cache", positions.size());
                return positions;
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.warn("Failed to get positions from cache, using fallback method");
        }

        BlockPos controllerPos = controller.self().getPos();
        List<IMultiPart> parts = controller.getParts();

        // Always seed bounding box with the controller itself so it's never excluded
        int minX = controllerPos.getX(), minY = controllerPos.getY(), minZ = controllerPos.getZ();
        int maxX = controllerPos.getX(), maxY = controllerPos.getY(), maxZ = controllerPos.getZ();

        // Expand to include all parts (hatches, buses, etc.)
        for (IMultiPart part : parts) {
            BlockPos pos = part.self().getPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        minX -= 3; minY -= 3; minZ -= 3;
        maxX += 3; maxY += 3; maxZ += 3;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir()) {
                        positions.add(pos);
                    }
                }
            }
        }

        // GTCEUTerminalMod.LOGGER.info("Scanned area and found {} blocks", positions.size());
        return positions;
    }

    // Paste schematic at any position with proper rotation
    private void pasteSchematic(ItemStack itemStack, Player player, Level level, BlockPos targetPos, Direction facing) {
        CompoundTag itemTag = itemStack.getTag();
        if (itemTag == null || !itemTag.contains("Clipboard")) {
            player.displayClientMessage(
                    Component.literal("§cNo schematic in clipboard!"),
                    true
            );
            return;
        }

        SchematicData clipboard = SchematicData.fromNBT(
                itemTag.getCompound("Clipboard"),
                level.registryAccess()
        );

        // Get original facing from schematic
        Direction originalFacing = Direction.SOUTH;
        try {
            String facingStr = clipboard.getOriginalFacing();
            if (facingStr != null && !facingStr.isEmpty()) {
                Direction byName = Direction.byName(facingStr);
                if (byName != null) originalFacing = byName;
            }
        } catch (Exception ignored) {
        }

        // Calculate player's horizontal facing
        Direction playerFacing = getPlayerHorizontalFacing(player);
        Direction targetFacing = playerFacing.getOpposite();

        // Calculate rotation steps needed
        int rotationSteps = getRotationSteps(originalFacing, targetFacing);

        int userRot = 0;
        try {
            CompoundTag clipTag = itemTag.getCompound("Clipboard");
            if (clipTag.contains("UserRot")) {
                userRot = clipTag.getInt("UserRot") & 3;
            }
        } catch (Exception ignored) {}

        rotationSteps = (rotationSteps + userRot) & 3;


        // GTCEUTerminalMod.LOGGER.info("Pasting schematic at {} - Original facing: {}, Player facing: {}, Rotation steps: {}", targetPos, originalFacing, playerFacing, rotationSteps);

        // === FIRST PASS: compute placements + required materials ===
        Map<Item, Integer> required = new HashMap<>();
        List<Placement> placements = new ArrayList<>();

        int skippedCount = 0;

        for (Map.Entry<BlockPos, BlockState> entry : clipboard.getBlocks().entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            // Apply rotation to position
            BlockPos rotatedPos = rotatePositionSteps(relativePos, rotationSteps);
            BlockPos worldPos = targetPos.offset(rotatedPos);

            // Apply rotation to block state
            BlockState rotatedState = rotateBlockStateSteps(state, rotationSteps);

            // Bounds check
            if (!level.isInWorldBounds(worldPos)) {
                skippedCount++;
                continue;
            }

            // Skip if can't be placed here
            BlockState currentState = level.getBlockState(worldPos);
            boolean canReplace = currentState.isAir() || currentState.canBeReplaced();
            if (!canReplace) {
                skippedCount++;
                continue;
            }

            // If it's already exactly the same state, don't charge / place again
            if (currentState == rotatedState || currentState.equals(rotatedState)) {
                skippedCount++;
                continue;
            }

            // Check if this block is a fluid source (water, lava, GT fluids, etc.)
            if (rotatedState.getFluidState().isSource()) {
                // Fluid blocks have no item form - handle separately via FluidPlacementHelper
                placements.add(new Placement(relativePos, worldPos, rotatedState));
                continue;
            }

            // Determine item cost for this block
            Item item = rotatedState.getBlock().asItem();
            if (item == Items.AIR) {
                // Unplaceable via inventory (no item form) - skip it to avoid dupes
                skippedCount++;
                continue;
            }

            required.merge(item, 1, Integer::sum);
            placements.add(new Placement(relativePos, worldPos, rotatedState));
        }

        // Nothing to place
        if (placements.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§eNothing to paste here. Area may be occupied (or identical)."),
                    true
            );
            return;
        }

        // === MATERIAL CHECK / EXTRACTION (SURVIVAL ONLY) ===
        if (!player.getAbilities().instabuild) {
            MENetworkItemExtractor.ExtractResult result =
                    MENetworkItemExtractor.tryExtractFromMEOrInventory(itemStack, level, player, required);

            if (!result.success) {
                // Build missing list (Inventory + ME only)
                Map<Item, Integer> inv = MaterialCalculator.scanPlayerInventory(player);

                StringBuilder sb = new StringBuilder("§cMissing materials: ");
                int shown = 0;

                for (Map.Entry<Item, Integer> req : required.entrySet()) {
                    Item it = req.getKey();
                    int need = req.getValue();

                    int haveInv = inv.getOrDefault(it, 0);
                    long haveME = MENetworkItemExtractor.checkItemAvailability(itemStack, level, player, it);

                    long have = (long) haveInv + haveME;
                    long miss = need - have;

                    if (miss > 0) {
                        if (shown > 0) sb.append("§7, ");
                        sb.append("§f").append(it.getDescription().getString()).append("§7 x").append(miss);
                        shown++;
                        if (shown >= 6) {
                            sb.append("§7 ...");
                            break;
                        }
                    }
                }

                player.displayClientMessage(Component.literal(sb.toString()), true);
                return;
            }
        }

        // === SECOND PASS: place blocks (now that we paid) ===
        int placedCount = 0;

        // Get ME Network fluid storage once for the whole paste operation
        IFluidHandler fluidStorage = null;
        if (!player.getAbilities().instabuild) {
            try {
                fluidStorage = getMENetworkFluidStorage(itemStack, player);
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.debug("No ME Network fluid storage available for schematic paste");
            }
        }

        // Get player item handler for bucket detection
        net.minecraftforge.items.IItemHandler playerInventory = null;
        if (!player.getAbilities().instabuild) {
            var cap = player.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
            playerInventory = cap.resolve().orElse(null);
        }

        for (Placement p : placements) {
            // Handle fluid blocks separately
            if (p.state.getFluidState().isSource()) {
                Fluid fluid = p.state.getFluidState().getType();
                boolean placed = FluidPlacementHelper.tryPlaceFluid(
                        level, p.worldPos, player, fluid, playerInventory, fluidStorage
                );
                if (placed) placedCount++;
                else skippedCount++;
                continue;
            }

            // Place regular block
            level.setBlock(p.worldPos, p.state, 3);
            placedCount++;

            // Copy block entity data if exists (use original relative key)
            if (clipboard.getBlockEntities().containsKey(p.relativeKey)) {
                CompoundTag beTag = clipboard.getBlockEntities().get(p.relativeKey).copy();
                BlockEntity be = level.getBlockEntity(p.worldPos);
                if (be != null) {
                    try {
                        // BlockEntity.load() reads x/y/z from the tag to set its own position.
                        // Overwrite them with the actual paste position so the BE is not mislocated.
                        // This is especially important for AE2 CableBusBlockEntity.
                        beTag.putInt("x", p.worldPos.getX());
                        beTag.putInt("y", p.worldPos.getY());
                        beTag.putInt("z", p.worldPos.getZ());
                        be.load(beTag);

                        // Re-assign ownership to the player doing the paste.
                        //
                        // Why: the schematic was copied from someone's multiblock, so beTag contains
                        // their ownerUUID. If we left it as-is, the pasted wireless hatch (and any other
                        // owner-restricted machine) would be bound to the original owner, not the
                        // player who is pasting. By calling setPlacedBy() we let each mod's block
                        // run its own on-place binding logic (GTMThings uses this to bind to the player's
                        // wireless network UUID, GTCEu uses it to set ownerUUID via MetaMachine.onBlockEntityRegister).
                        //
                        // setPlacedBy is the canonical way mods bind ownership — same approach used
                        // by ComponentUpgrader.postPlaceInitialize() in this project.
                        try {
                            p.state.getBlock().setPlacedBy(
                                    (net.minecraft.server.level.ServerLevel) level,
                                    p.worldPos, p.state,
                                    player,
                                    net.minecraft.world.item.ItemStack.EMPTY
                            );
                        } catch (Exception ignored) {
                            // Some blocks throw on setPlacedBy with an empty stack — safe to ignore
                        }

                        // Force the BE to notify the level (AE2 cable bus needs this to reconnect grid nodes).
                        be.setChanged();
                    } catch (Exception e) {
                        GTCEUTerminalMod.LOGGER.error("Failed to load block entity at {}", p.worldPos, e);
                    }
                }
            }
        }

        player.displayClientMessage(
                Component.literal(String.format("§aSchematic pasted! §f%d §ablocks placed", placedCount) +
                        (skippedCount > 0 ? String.format(" §7(%d skipped)", skippedCount) : "")),
                true
        );

        // GTCEUTerminalMod.LOGGER.info("Schematic pasted: {} blocks placed, {} skipped", placedCount, skippedCount);
    }

    @org.jetbrains.annotations.Nullable
    private IFluidHandler getMENetworkFluidStorage(ItemStack terminalStack, Player player) {
        try {
            // First try the terminal itself if it's linked
            if (WirelessTerminalHandler.isLinked(terminalStack)) {
                IGrid grid = WirelessTerminalHandler.getLinkedGrid(terminalStack, player.level(), player);
                if (grid != null) {
                    IActionSource actionSource = new PlayerSource(player, null);
                    MENetworkFluidHandlerWrapper wrapper = MENetworkFluidHandlerWrapper.fromGrid(grid, actionSource);
                    if (wrapper != null) return wrapper;
                }
            }

            // Also check all inventory items in case the terminal is in a different slot
            java.util.List<ItemStack> toCheck = new java.util.ArrayList<>();
            toCheck.add(player.getMainHandItem());
            toCheck.add(player.getOffhandItem());
            player.getInventory().items.forEach(toCheck::add);

            for (ItemStack stack : toCheck) {
                if (stack.isEmpty() || !WirelessTerminalHandler.isLinked(stack)) continue;
                IGrid grid = WirelessTerminalHandler.getLinkedGrid(stack, player.level(), player);
                if (grid == null) continue;
                IActionSource actionSource = new PlayerSource(player, null);
                MENetworkFluidHandlerWrapper wrapper = MENetworkFluidHandlerWrapper.fromGrid(grid, actionSource);
                if (wrapper != null) return wrapper;
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error accessing ME Network fluid storage for schematic paste", e);
        }
        return null;
    }

    private record Placement(BlockPos relativeKey, BlockPos worldPos, BlockState state) {}


    // Get player's horizontal facing direction
    private Direction getPlayerHorizontalFacing(Player player) {
        float yaw = (player.getYRot() % 360 + 360) % 360;

        if (yaw >= 315 || yaw < 45) {
            return Direction.SOUTH;
        } else if (yaw >= 45 && yaw < 135) {
            return Direction.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            return Direction.NORTH;
        } else {
            return Direction.EAST;
        }
    }


    // Calculate rotation steps between two directions
    private int getRotationSteps(Direction from, Direction to) {
        return (to.get2DDataValue() - from.get2DDataValue() + 4) % 4;
    }


    // Rotate position by given steps
    private BlockPos rotatePositionSteps(BlockPos pos, int steps) {
        BlockPos result = pos;
        for (int i = 0; i < steps; i++) {
            // (x, z) -> (-z, x)
            result = new BlockPos(-result.getZ(), result.getY(), result.getX());
        }
        return result;
    }


    // Rotate block state by given steps
    private BlockState rotateBlockStateSteps(BlockState state, int steps) {
        BlockState result = state;
        for (int i = 0; i < steps; i++) {
            result = rotateBlockStateOnce(result);
        }
        return result;
    }


    // Rotate block state once (90 degrees clockwise)
    private BlockState rotateBlockStateOnce(BlockState state) {
        try {
            // HORIZONTAL_FACING
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                Direction facing = state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                if (facing.getAxis().isHorizontal()) {
                    return state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                            facing.getClockWise()
                    );
                }
            }

            // FACING
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                Direction facing = state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                            facing.getClockWise()
                    );
                }
            }

            // AXIS: X <-> Z, for GhostBlock rotation (Will gonna implemented in the future)
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
                var axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
                if (axis == net.minecraft.core.Direction.Axis.X) {
                    return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                            net.minecraft.core.Direction.Axis.Z);
                } else if (axis == net.minecraft.core.Direction.Axis.Z) {
                    return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                            net.minecraft.core.Direction.Axis.X);
                }
            }

        } catch (Exception ignored) {
        }

        return state;
    }
}