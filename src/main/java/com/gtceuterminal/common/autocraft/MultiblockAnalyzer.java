package com.gtceuterminal.common.autocraft;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.ae2.WirelessTerminalHandler;
import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.pattern.AdvancedAutoBuilder;
import com.gtceuterminal.common.config.ManagerSettings;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gtceuterminal.common.compat.GTCEuCompat;
import com.gtceuterminal.common.compat.GTCEuCompat.PredicateCountMap;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Server-side analysis of what a multiblock needs to be built or upgraded.
 *
 * <p>Does NOT place any blocks — it only reads the pattern and compares
 * against the ME network to produce an {@link AnalysisResult}.</p>
 */
public final class MultiblockAnalyzer {

    private MultiblockAnalyzer() {}

    // ── Reflection fields (same as AdvancedAutoBuilder) ───────────────────────
    private static Field F_BLOCK_MATCHES;
    private static Field F_AISLE_REP;
    private static Field F_STRUCTURE_DIR;
    private static Field F_CENTER_OFFSET;
    private static boolean REFLECTION_READY = false;

    private static void ensureReflection() {
        if (REFLECTION_READY) return;
        try {
            F_BLOCK_MATCHES = BlockPattern.class.getDeclaredField("blockMatches");
            F_BLOCK_MATCHES.setAccessible(true);
            F_AISLE_REP = BlockPattern.class.getDeclaredField("aisleRepetitions");
            F_AISLE_REP.setAccessible(true);
            F_STRUCTURE_DIR = BlockPattern.class.getDeclaredField("structureDir");
            F_STRUCTURE_DIR.setAccessible(true);
            F_CENTER_OFFSET = BlockPattern.class.getDeclaredField("centerOffset");
            F_CENTER_OFFSET.setAccessible(true);
            REFLECTION_READY = true;
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("MultiblockAnalyzer: reflection init failed", t);
        }
    }

    // ── BUILD analysis ────────────────────────────────────────────────────────

    /**
     * Analyses what blocks are needed to build {@code controller} and how many
     * the player's linked ME network has in stock.
     *
     * @return analysis result, or null on failure
     */
    public static AnalysisResult analyzeForBuild(Player player,
                                                 IMultiController controller,
                                                 ManagerSettings.AutoBuildSettings settings) {
        ensureReflection();
        if (!REFLECTION_READY) return null;

        try {
            BlockPattern pattern        = controller.getPattern();
            MultiblockState worldState  = controller.getMultiblockState();

            TraceabilityPredicate[][][] blockMatches = (TraceabilityPredicate[][][]) F_BLOCK_MATCHES.get(pattern);
            int[][]                     aisleReps    = (int[][])                     F_AISLE_REP.get(pattern);
            RelativeDirection[]         structDir    = (RelativeDirection[])         F_STRUCTURE_DIR.get(pattern);
            int[]                       centerOff    = (int[])                       F_CENTER_OFFSET.get(pattern);

            if (blockMatches == null) return null;

            BlockPos  centerPos     = controller.self().getPos();
            var       facing        = controller.self().getFrontFacing();
            var       upFacing      = controller.self().getUpwardsFacing();
            boolean   isFlipped     = controller.self().isFlipped();
            int       minZ          = -centerOff[4];

            PredicateCountMap cacheGlobal = GTCEuCompat.getGlobalCount(worldState);
            PredicateCountMap cacheLayer = GTCEuCompat.getLayerCount(worldState);
            GTCEuCompat.clean(worldState);

            // item → total needed
            Map<Item, Integer> needed = new LinkedHashMap<>();

            for (int c = 0, z = minZ++; c < blockMatches.length; c++) {
                int reps = getRepetitions(c, aisleReps, settings.repeatCount);
                for (int r = 0; r < reps; r++, z++) {
                    cacheLayer.clear();
                    for (int b = 0, y = -centerOff[1]; b < blockMatches[c].length; b++, y++) {
                        for (int a = 0, x = -centerOff[0]; a < blockMatches[c][b].length; a++, x++) {
                            TraceabilityPredicate pred = blockMatches[c][b][a];
                            BlockPos pos = relativeOffset(structDir, x, y, z, facing, upFacing, isFlipped)
                                    .offset(centerPos);

                            if (!player.level().isEmptyBlock(pos)) {
                                // Already placed — count limits like AdvancedAutoBuilder does
                                GTCEuCompat.update(worldState, pos, pred);
                                for (SimplePredicate lim : pred.limited) lim.testLimited(worldState);
                                continue;
                            }

                            BlockInfo[] infos = pickInfos(pred, cacheGlobal, cacheLayer);
                            ItemStack candidate = firstBlockItem(infos);
                            if (candidate == null || candidate.isEmpty()) continue;

                            needed.merge(candidate.getItem(), 1, Integer::sum);
                        }
                    }
                }
            }

            return buildEntries(needed, player, controller.self().getPos());

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("MultiblockAnalyzer.analyzeForBuild failed", e);
            return null;
        }
    }

    // ── UPGRADE analysis ──────────────────────────────────────────────────────

    /**
     * Analyses what items are needed to upgrade {@code components} and how many
     * the ME network has.
     */
    public static AnalysisResult analyzeForUpgrade(Player player,
                                                   List<ComponentInfo> components,
                                                   int targetTier,
                                                   String upgradeId,
                                                   BlockPos controllerPos) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        List<BlockPos>     positions = new ArrayList<>();

        for (ComponentInfo comp : components) {
            positions.add(comp.getPosition());
            Map<Item, Integer> perComp = (upgradeId != null && !upgradeId.isBlank())
                    ? ComponentUpgradeHelper.getUpgradeItemsForBlockId(upgradeId)
                    : ComponentUpgradeHelper.getUpgradeItems(comp, targetTier);
            perComp.forEach((item, count) -> needed.merge(item, count, Integer::sum));
        }

        try {
            AnalysisResult base = buildEntries(needed, player, controllerPos);
            if (base == null) return null;
            return new AnalysisResult(base.entries, controllerPos, targetTier, upgradeId, positions);
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("MultiblockAnalyzer.analyzeForUpgrade failed", e);
            return null;
        }
    }

    // ── Shared: build entry list from needed map + ME query ───────────────────

    private static AnalysisResult buildEntries(Map<Item, Integer> needed,
                                               Player player,
                                               BlockPos controllerPos) {
        IGrid grid = getGrid(player);
        IStorageService   storage  = grid != null ? grid.getStorageService()   : null;
        ICraftingService  crafting = grid != null ? grid.getCraftingService()   : null;

        List<AnalysisResult.Entry> entries = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            ItemStack stack = new ItemStack(e.getKey(), e.getValue());
            long   inME     = 0;
            boolean craftable = false;

            if (storage != null) {
                AEItemKey key = AEItemKey.of(stack);
                inME      = storage.getInventory().getAvailableStacks().get(key);
                if (crafting != null) {
                    try {
                        // isCraftable signature varies across AE2 15.x patch versions
                        craftable = crafting.isCraftable(key);
                    } catch (Exception ex) {
                        GTCEUTerminalMod.LOGGER.debug(
                                "MultiblockAnalyzer: isCraftable failed for {}: {}",
                                stack.getItem().getDescriptionId(), ex.getMessage());
                        craftable = false;
                    }
                }
            }
            entries.add(new AnalysisResult.Entry(stack, inME, craftable));
        }

        return new AnalysisResult(entries, controllerPos);
    }

    // ── ME grid helper ────────────────────────────────────────────────────────

    private static IGrid getGrid(Player player) {
        try {
            // Find a linked wireless terminal in hands or inventory
            for (ItemStack s : allStacks(player)) {
                if (WirelessTerminalHandler.isWirelessTerminal(s)
                        && WirelessTerminalHandler.isLinked(s)) {
                    IGrid g = WirelessTerminalHandler.getLinkedGrid(s, player.level(), player);
                    if (g != null) return g;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Iterable<ItemStack> allStacks(Player player) {
        List<ItemStack> all = new ArrayList<>();
        all.add(player.getMainHandItem());
        all.add(player.getOffhandItem());
        all.addAll(player.getInventory().items);
        return all;
    }

    // ── Pattern helpers (mirrors AdvancedAutoBuilder) ─────────────────────────

    private static int getRepetitions(int slice, int[][] aisleReps, int repeatCount) {
        if (aisleReps == null || slice >= aisleReps.length) return 1;
        int min = aisleReps[slice][0];
        int max = aisleReps[slice][1];
        if (repeatCount == 0) return Math.max(1, min);
        int d = Math.max(repeatCount, min);
        if (max >= min && max > 0) d = Math.min(d, max);
        return Math.max(1, d);
    }

    private static BlockInfo[] pickInfos(TraceabilityPredicate pred,
                                         PredicateCountMap global,
                                         PredicateCountMap layer) {
        // Mirrors AdvancedAutoBuilder.pickInfosForPredicate exactly
        for (SimplePredicate lim : pred.limited) {
            if (lim.minLayerCount > 0) {
                int cur = layer.getInt(lim);
                if (cur < lim.minLayerCount && (lim.maxLayerCount == -1 || cur < lim.maxLayerCount)) {
                    layer.addTo(lim, 1);
                    return lim.candidates == null ? null : lim.candidates.get();
                }
            }
        }
        for (SimplePredicate lim : pred.limited) {
            if (lim.minCount > 0) {
                int cur = global.getInt(lim);
                if (cur < lim.minCount && (lim.maxCount == -1 || cur < lim.maxCount)) {
                    global.addTo(lim, 1);
                    return lim.candidates == null ? null : lim.candidates.get();
                }
            }
        }
        BlockInfo[] infos = new BlockInfo[0];
        for (SimplePredicate lim : pred.limited) {
            if (lim.maxLayerCount != -1 && layer.getOrDefault(lim, Integer.MAX_VALUE) == lim.maxLayerCount) continue;
            if (lim.maxCount     != -1 && global.getOrDefault(lim, Integer.MAX_VALUE) == lim.maxCount)     continue;
            layer.addTo(lim, 1);
            global.addTo(lim, 1);
            if (lim.candidates != null) infos = merge(infos, lim.candidates.get());
        }
        for (SimplePredicate com : pred.common) {
            if (com.candidates != null) infos = merge(infos, com.candidates.get());
        }
        return infos;
    }

    private static BlockInfo[] merge(BlockInfo[] a, BlockInfo[] b) {
        if (b == null) return a;
        BlockInfo[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static ItemStack firstBlockItem(BlockInfo[] infos) {
        if (infos == null) return null;
        for (BlockInfo info : infos) {
            if (info == null || info.getBlockState().isAir()) continue;
            ItemStack s = info.getItemStackForm();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    // Mirrors AdvancedAutoBuilder.setActualRelativeOffset
    private static BlockPos relativeOffset(RelativeDirection[] dir,
                                           int x, int y, int z,
                                           net.minecraft.core.Direction facing,
                                           net.minecraft.core.Direction upFacing,
                                           boolean flipped) {
        int[] c0 = {x, y, z}, c1 = new int[3];
        var up = net.minecraft.core.Direction.UP;
        var down = net.minecraft.core.Direction.DOWN;

        if (facing == up || facing == down) {
            net.minecraft.core.Direction of = facing == down
                    ? upFacing : upFacing.getOpposite();
            for (int i = 0; i < 3; i++) {
                switch (GTCEuCompat.getActualDirection(dir[i], of)) {
                    case UP    -> c1[1] =  c0[i];
                    case DOWN  -> c1[1] = -c0[i];
                    case WEST  -> c1[0] = -c0[i];
                    case EAST  -> c1[0] =  c0[i];
                    case NORTH -> c1[2] = -c0[i];
                    case SOUTH -> c1[2] =  c0[i];
                }
            }
            int xOff = upFacing.getStepX(), zOff = upFacing.getStepZ(), tmp;
            if (xOff == 0) { tmp=c1[2]; c1[2]=zOff>0?c1[1]:-c1[1]; c1[1]=zOff>0?-tmp:tmp; }
            else           { tmp=c1[0]; c1[0]=xOff>0?c1[1]:-c1[1]; c1[1]=xOff>0?-tmp:tmp; }
            if (flipped) { if (upFacing==net.minecraft.core.Direction.NORTH||upFacing==net.minecraft.core.Direction.SOUTH) c1[0]=-c1[0]; else c1[2]=-c1[2]; }
        } else {
            for (int i = 0; i < 3; i++) {
                switch (GTCEuCompat.getActualDirection(dir[i], facing)) {
                    case UP    -> c1[1] =  c0[i];
                    case DOWN  -> c1[1] = -c0[i];
                    case WEST  -> c1[0] = -c0[i];
                    case EAST  -> c1[0] =  c0[i];
                    case NORTH -> c1[2] = -c0[i];
                    case SOUTH -> c1[2] =  c0[i];
                }
            }
            if (upFacing==net.minecraft.core.Direction.WEST||upFacing==net.minecraft.core.Direction.EAST) {
                int xOff=upFacing==net.minecraft.core.Direction.EAST?facing.getClockWise().getStepX():facing.getClockWise().getOpposite().getStepX();
                int zOff=upFacing==net.minecraft.core.Direction.EAST?facing.getClockWise().getStepZ():facing.getClockWise().getOpposite().getStepZ();
                int tmp;
                if (xOff==0){tmp=c1[2];c1[2]=zOff>0?-c1[1]:c1[1];c1[1]=zOff>0?tmp:-tmp;}
                else        {tmp=c1[0];c1[0]=xOff>0?-c1[1]:c1[1];c1[1]=xOff>0?tmp:-tmp;}
            } else if (upFacing==net.minecraft.core.Direction.SOUTH) {
                c1[1]=-c1[1];
                if (facing.getStepX()==0) c1[0]=-c1[0]; else c1[2]=-c1[2];
            }
            if (flipped) {
                if (upFacing==net.minecraft.core.Direction.NORTH||upFacing==net.minecraft.core.Direction.SOUTH) {
                    if (facing==net.minecraft.core.Direction.NORTH||facing==net.minecraft.core.Direction.SOUTH) c1[0]=-c1[0]; else c1[2]=-c1[2];
                } else c1[1]=-c1[1];
            }
        }
        return new BlockPos(c1[0], c1[1], c1[2]);
    }
}