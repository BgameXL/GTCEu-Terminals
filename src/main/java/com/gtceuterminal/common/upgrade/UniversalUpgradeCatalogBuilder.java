package com.gtceuterminal.common.upgrade;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

// Builder for UniversalUpgradeCatalog, which scans a multiblock machine's parts and detects possible upgrades based on their abilities.
public final class UniversalUpgradeCatalogBuilder {

    private UniversalUpgradeCatalogBuilder() {
    }

    // Scans the given multiblock controller's parts
    public static UniversalUpgradeCatalog build(MultiblockControllerMachine controller, Level level) {
        UniversalUpgradeCatalog catalog = new UniversalUpgradeCatalog();

        Map<PartAbility, Map<Integer, Set<Block>>> registry =
                PartAbilityIntrospector.getAbilityTierRegistry();

        var parts = controller.getParts();
        if (parts == null) return catalog;

        for (var partRef : parts) {
            if (partRef == null || partRef.self() == null) continue;

            MetaMachine mm = partRef.self();
            if (!(mm instanceof MultiblockPartMachine)) continue;

            BlockPos pos = mm.getPos();
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

            int tier = 0;
            var def = mm.getDefinition();
            if (def != null) tier = def.getTier();

            Set<PartAbility> abilities = PartAbilityIntrospector.detectAbilitiesForBlock(block);
            if (abilities.isEmpty()) continue;

            var installed = new UniversalUpgradeCatalog.InstalledPart(pos, blockId, tier, abilities);

            List<UniversalUpgradeCatalog.CandidatePart> candidates = new ArrayList<>();
            for (PartAbility ability : abilities) {
                Map<Integer, Set<Block>> tierMap = registry.get(ability);
                if (tierMap == null) continue;

                for (var e : tierMap.entrySet()) {
                    int t = e.getKey();
                    for (Block b : e.getValue()) {
                        String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                        candidates.add(new UniversalUpgradeCatalog.CandidatePart(id, t, ability));
                    }
                }
            }

            candidates = dedupeAndSortCandidates(candidates);
            catalog.put(pos, new UniversalUpgradeCatalog.UpgradeOptions(installed, candidates));
        }

        return catalog;
    }

    // Dedupe candidates by ability name + block ID, keeping only the highest tier for each unique combination.
    private static List<UniversalUpgradeCatalog.CandidatePart> dedupeAndSortCandidates(
            List<UniversalUpgradeCatalog.CandidatePart> in
    ) {
        Map<String, UniversalUpgradeCatalog.CandidatePart> map = new HashMap<>();
        for (var c : in) {
            String key = c.ability().getName() + "|" + c.blockId();
            var prev = map.get(key);
            if (prev == null || c.tier() > prev.tier()) map.put(key, c);
        }

        List<UniversalUpgradeCatalog.CandidatePart> out = new ArrayList<>(map.values());
        out.sort(Comparator
                .comparing((UniversalUpgradeCatalog.CandidatePart c) -> c.ability().getName())
                .thenComparingInt(UniversalUpgradeCatalog.CandidatePart::tier)
                .thenComparing(UniversalUpgradeCatalog.CandidatePart::blockId));
        return out;
    }
}