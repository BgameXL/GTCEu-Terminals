package com.gtceuterminal.common.upgrade;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.world.level.block.Block;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;


// Utility class to introspect PartAbility instances and their registries via reflection.
public final class PartAbilityIntrospector {

    private PartAbilityIntrospector() {}

    private static volatile List<PartAbility> ALL_KNOWN_ABILITIES;
    private static volatile Map<PartAbility, Map<Integer, Set<Block>>> ABILITY_REGISTRY_CACHE;

    public static List<PartAbility> getAllKnownAbilities() {
        var local = ALL_KNOWN_ABILITIES;
        if (local != null) return local;

        List<PartAbility> out = new ArrayList<>();
        for (Field f : PartAbility.class.getDeclaredFields()) {
            if (f.getType() != PartAbility.class) continue;
            if (!Modifier.isStatic(f.getModifiers())) continue;

            try {
                f.setAccessible(true);
                PartAbility ability = (PartAbility) f.get(null);
                if (ability != null) out.add(ability);
            } catch (Throwable ignored) {}
        }

        out.sort(Comparator.comparing(PartAbility::getName));
        ALL_KNOWN_ABILITIES = Collections.unmodifiableList(out);
        return ALL_KNOWN_ABILITIES;
    }

    @SuppressWarnings("unchecked")
    public static Map<PartAbility, Map<Integer, Set<Block>>> getAbilityTierRegistry() {
        var local = ABILITY_REGISTRY_CACHE;
        if (local != null) return local;

        Map<PartAbility, Map<Integer, Set<Block>>> out = new HashMap<>();

        Field registryField;
        try {
            registryField = PartAbility.class.getDeclaredField("registry");
            registryField.setAccessible(true);
        } catch (Throwable t) {
            ABILITY_REGISTRY_CACHE = Collections.emptyMap();
            return ABILITY_REGISTRY_CACHE;
        }

        for (PartAbility ability : getAllKnownAbilities()) {
            try {
                Object raw = registryField.get(ability);
                if (!(raw instanceof Int2ObjectMap<?> intMap)) continue;

                Map<Integer, Set<Block>> tierMap = new HashMap<>();
                for (Int2ObjectMap.Entry<?> e : ((Int2ObjectMap<?>) intMap).int2ObjectEntrySet()) {
                    int tier = e.getIntKey();
                    Object v = e.getValue();
                    if (v instanceof Set<?> set) {
                        Set<Block> blocks = new HashSet<>();
                        for (Object o : set) if (o instanceof Block b) blocks.add(b);
                        if (!blocks.isEmpty()) tierMap.put(tier, blocks);
                    }
                }
                if (!tierMap.isEmpty()) out.put(ability, tierMap);

            } catch (Throwable ignored) {}
        }

        ABILITY_REGISTRY_CACHE = Collections.unmodifiableMap(out);
        return ABILITY_REGISTRY_CACHE;
    }

    public static Set<PartAbility> detectAbilitiesForBlock(Block block) {
        Set<PartAbility> abilities = new HashSet<>();
        for (PartAbility a : getAllKnownAbilities()) {
            try {
                if (a.isApplicable(block)) abilities.add(a);
            } catch (Throwable ignored) {}
        }
        return abilities;
    }
}