package com.gtceuterminal.common.compat;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class GTCEuCompat {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GTCEuCompat.class);
    private static final boolean IS_LEGACY;

    static {
        boolean legacy = false;
        try {
            Field f = MultiblockState.class.getDeclaredField("globalCount");
            f.setAccessible(true);
            legacy = f.getType().getName().equals("java.util.Map");
        } catch (Exception e) {
            LOGGER.warn("[GTCEuCompat] Could not detect GTCEu version, assuming 7.0.0+");
        }
        IS_LEGACY = legacy;
        LOGGER.info("[GTCEuCompat] Detected GTCEu API: {}", IS_LEGACY ? "1.6.4 (legacy)" : "7.0.0+");
    }

    public static boolean isLegacy() {
        return IS_LEGACY;
    }

    private static final Method METHOD_CLEAN;
    private static final Method METHOD_UPDATE;

    static {
        Method mClean = null, mUpdate = null;
        try {
            mClean = MultiblockState.class.getDeclaredMethod("clean");
            mClean.setAccessible(true);
            mUpdate = MultiblockState.class.getDeclaredMethod("update", BlockPos.class, TraceabilityPredicate.class);
            mUpdate.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] Failed to cache MultiblockState methods", e);
        }
        METHOD_CLEAN  = mClean;
        METHOD_UPDATE = mUpdate;
    }

    public static void clean(MultiblockState state) {
        try {
            METHOD_CLEAN.invoke(state);
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] clean() failed", e);
        }
    }

    public static boolean update(MultiblockState state, BlockPos pos, TraceabilityPredicate predicate) {
        try {
            return (boolean) METHOD_UPDATE.invoke(state, pos, predicate);
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] update() failed", e);
            return false;
        }
    }

    private static final Method METHOD_GET_ACTUAL_DIRECTION;

    static {
        Method m = null;
        try {
            if (IS_LEGACY) {
                m = RelativeDirection.class.getMethod("getActualFacing", Direction.class);
            } else {
                m = RelativeDirection.class.getMethod("getActualDirection", Direction.class);
            }
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] Failed to cache RelativeDirection method", e);
        }
        METHOD_GET_ACTUAL_DIRECTION = m;
    }

    public static Direction getActualDirection(RelativeDirection relDir, Direction facing) {
        try {
            return (Direction) METHOD_GET_ACTUAL_DIRECTION.invoke(relDir, facing);
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] getActualDirection() failed", e);
            return Direction.NORTH;
        }
    }

    public static class PredicateCountMap {

        private final Object inner;

        private static Method mapGet;
        private static Method mapPut;
        private static Method mapGetOrDefault;
        private static Method mapClear;

        private static Method o2iGetInt;
        private static Method o2iAddTo;
        private static Method o2iGetOrDefault;
        private static Method o2iClear;

        static {
            try {
                if (IS_LEGACY) {
                    mapGet          = Map.class.getMethod("get", Object.class);
                    mapPut          = Map.class.getMethod("put", Object.class, Object.class);
                    mapGetOrDefault = Map.class.getMethod("getOrDefault", Object.class, Object.class);
                    mapClear        = Map.class.getMethod("clear");
                } else {
                    Class<?> o2i = Class.forName("it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap");
                    o2iGetInt       = o2i.getMethod("getInt", Object.class);
                    o2iAddTo        = o2i.getMethod("addTo", Object.class, int.class);
                    o2iGetOrDefault = o2i.getMethod("getOrDefault", Object.class, int.class);
                    o2iClear        = o2i.getMethod("clear");
                }
            } catch (Exception e) {
                LOGGER.error("[GTCEuCompat] Failed to cache PredicateCountMap methods", e);
            }
        }

        private PredicateCountMap(Object inner) {
            this.inner = inner;
        }

        public int getInt(SimplePredicate key) {
            try {
                if (IS_LEGACY) {
                    Object v = mapGet.invoke(inner, key);
                    return v == null ? 0 : (int) v;
                } else {
                    return (int) o2iGetInt.invoke(inner, key);
                }
            } catch (Exception e) { return 0; }
        }

        public int getOrDefault(SimplePredicate key, int defaultValue) {
            try {
                if (IS_LEGACY) {
                    return (int) mapGetOrDefault.invoke(inner, key, defaultValue);
                } else {
                    return (int) o2iGetOrDefault.invoke(inner, key, defaultValue);
                }
            } catch (Exception e) { return defaultValue; }
        }

        public void addTo(SimplePredicate key, int delta) {
            try {
                if (IS_LEGACY) {
                    Object cur = mapGet.invoke(inner, key);
                    int current = cur == null ? 0 : (int) cur;
                    mapPut.invoke(inner, key, current + delta);
                } else {
                    o2iAddTo.invoke(inner, key, delta);
                }
            } catch (Exception ignored) {}
        }

        public void clear() {
            try {
                if (IS_LEGACY) mapClear.invoke(inner);
                else           o2iClear.invoke(inner);
            } catch (Exception ignored) {}
        }
    }

    private static final Field FIELD_GLOBAL_COUNT;
    private static final Field FIELD_LAYER_COUNT;

    static {
        Field fg = null, fl = null;
        try {
            fg = MultiblockState.class.getDeclaredField("globalCount");
            fg.setAccessible(true);
            fl = MultiblockState.class.getDeclaredField("layerCount");
            fl.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] Could not access MultiblockState count fields", e);
        }
        FIELD_GLOBAL_COUNT = fg;
        FIELD_LAYER_COUNT  = fl;
    }

    public static PredicateCountMap getGlobalCount(MultiblockState state) {
        try {
            return new PredicateCountMap(FIELD_GLOBAL_COUNT.get(state));
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] getGlobalCount failed", e);
            return new PredicateCountMap(new java.util.HashMap<>());
        }
    }

    public static PredicateCountMap getLayerCount(MultiblockState state) {
        try {
            return new PredicateCountMap(FIELD_LAYER_COUNT.get(state));
        } catch (Exception e) {
            LOGGER.error("[GTCEuCompat] getLayerCount failed", e);
            return new PredicateCountMap(new java.util.HashMap<>());
        }
    }
}