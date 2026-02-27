package com.gtceuterminal.common.energy;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.config.ItemsConfig;
import com.gtceuterminal.common.multiblock.MachineInferencer;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Collects energy data from GTCEu machines at a given position and time, including current state and historical input/output for graphing.
public class EnergyDataCollector {

    private static final Map<String, Deque<Long>> inputHistoryMap  = new HashMap<>();
    private static final Map<String, Deque<Long>> outputHistoryMap = new HashMap<>();

    // ─── Main entry point ────────────────────────────────────────────────────
    public static EnergySnapshot collect(ServerLevel level, BlockPos pos,
                                         String customName, String machineType) {
        EnergySnapshot snap = new EnergySnapshot();
        snap.machineName = customName.isBlank() ? machineType : customName;
        snap.machineType = machineType;
        snap.mode = EnergySnapshot.MachineMode.UNKNOWN;
        snap.isFormed = false;

        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine == null) return snap;

            // Power Substation — use MachineInferencer (reflection-based)
            if (machine instanceof PowerSubstationMachine substation) {
                snap.isFormed = substation.isFormed();
                snap.mode = EnergySnapshot.MachineMode.STORAGE;

                // Read BigInteger stored/capacity from IEnergyInfoProvider
                IEnergyInfoProvider.EnergyInfo info = substation.getEnergyInfo();
                snap.usesBigInt   = info.capacity().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0;
                snap.bigStored    = info.stored();
                snap.bigCapacity  = info.capacity();
                snap.energyStored   = snap.bigStored.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                snap.energyCapacity = snap.bigCapacity.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();

                // Use MachineInferencer to get actual input/output flow via reflection
                MachineInferencer.MachineSnapshot ms = MachineInferencer.inferMachine(level, pos);
                if (ms != null) {
                    snap.inputPerSec  = ms.inputEuPerTick * 20;
                    snap.outputPerSec = ms.outputEuPerTick * 20;
                }

                // Also try capability for voltage info
                IEnergyContainer ec = GTCapabilityHelper.getEnergyContainer(level, pos, null);
                if (ec != null) {
                    snap.inputVoltage  = ec.getInputVoltage();
                    snap.inputAmperage = ec.getInputAmperage();
                    snap.outputVoltage  = ec.getOutputVoltage();
                    snap.outputAmperage = ec.getOutputAmperage();
                    // Prefer per-sec from capability if inferencer gave 0
                    if (snap.inputPerSec == 0)  snap.inputPerSec  = ec.getInputPerSec();
                    if (snap.outputPerSec == 0) snap.outputPerSec = ec.getOutputPerSec();
                }
            }

            // ─── WorkableElectricMultiblock (consumer / generator) ────────────
            else if (machine instanceof WorkableElectricMultiblockMachine electric) {
                snap.isFormed = electric.isFormed();
                snap.mode = electric.isGenerator()
                        ? EnergySnapshot.MachineMode.GENERATOR
                        : EnergySnapshot.MachineMode.CONSUMER;

                EnergyContainerList ec = electric.getEnergyContainer();
                if (ec != null) {
                    snap.energyStored   = ec.getEnergyStored();
                    snap.energyCapacity = ec.getEnergyCapacity();
                    snap.inputPerSec    = ec.getInputPerSec();
                    snap.outputPerSec   = ec.getOutputPerSec();
                    snap.inputVoltage   = ec.getInputVoltage();
                    snap.inputAmperage  = ec.getInputAmperage();
                    snap.outputVoltage  = ec.getOutputVoltage();
                    snap.outputAmperage = ec.getOutputAmperage();
                }

                if (ItemsConfig.getEAShowPerHatchDetails() && snap.isFormed) {
                    collectHatchInfo(electric, snap);
                }

                // Active recipe info + history tracking
                if (snap.isFormed && snap.mode == EnergySnapshot.MachineMode.CONSUMER
                        && electric instanceof IRecipeLogicMachine) {
                    IRecipeLogicMachine rlm = (IRecipeLogicMachine) electric;
                    collectRecipeInfo(rlm, snap);
                    RecipeHistoryTracker.poll(pos, rlm);
                    snap.recipeHistory = new java.util.ArrayList<>(RecipeHistoryTracker.getHistory(pos));
                }
            }

            // Any other GTCEu machine with energy capability
            else {
                IEnergyContainer ec = GTCapabilityHelper.getEnergyContainer(level, pos, null);
                if (ec != null) {
                    snap.isFormed = true;
                    snap.mode = ec.getOutputVoltage() > 0
                            ? EnergySnapshot.MachineMode.GENERATOR
                            : EnergySnapshot.MachineMode.CONSUMER;
                    snap.energyStored   = ec.getEnergyStored();
                    snap.energyCapacity = ec.getEnergyCapacity();
                    snap.inputPerSec    = ec.getInputPerSec();
                    snap.outputPerSec   = ec.getOutputPerSec();
                    snap.inputVoltage   = ec.getInputVoltage();
                    snap.inputAmperage  = ec.getInputAmperage();
                    snap.outputVoltage  = ec.getOutputVoltage();
                    snap.outputAmperage = ec.getOutputAmperage();
                }
            }

            // History
            String key = historyKey(level, pos);
            int maxH = ItemsConfig.getEAHistorySeconds();
            snap.inputHistory  = updateHistory(inputHistoryMap,  key, snap.inputPerSec,  maxH);
            snap.outputHistory = updateHistory(outputHistoryMap, key, snap.outputPerSec, maxH);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error collecting energy snapshot at {}", pos, e);
        }

        return snap;
    }

    // ─── Active recipe info ──────────────────────────────────────────────────
    private static void collectRecipeInfo(IRecipeLogicMachine rlm, EnergySnapshot snap) {
        try {
            RecipeLogic logic = rlm.getRecipeLogic();
            if (logic == null) return;

            snap.isRecipeActive = logic.isWorking();
            snap.recipeDuration = logic.getMaxProgress();
            // Read progress ticks directly for accuracy (field is protected)
            try {
                var progressField = RecipeLogic.class.getDeclaredField("progress");
                progressField.setAccessible(true);
                int progressTicks = (int) progressField.get(logic);
                snap.recipeProgress = snap.recipeDuration > 0
                        ? (float) progressTicks / snap.recipeDuration : 0f;
                snap.recipeProgressTicks = progressTicks;
            } catch (Exception e2) {
                snap.recipeProgress = (float) logic.getProgressPercent();
                snap.recipeProgressTicks = (int)(snap.recipeProgress * snap.recipeDuration);
            }

            if (logic.isWorking() || logic.isWaiting()) {
                // lastRecipe is protected — use reflection
                try {
                    var field = RecipeLogic.class.getDeclaredField("lastRecipe");
                    field.setAccessible(true);
                    Object recipeObj = field.get(logic);
                    if (recipeObj instanceof com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
                        // Prefer output item display name over recipe path
                        snap.recipeId = getOutputName(recipe);

                        // Recipe type name (the machine type)
                        if (recipe.recipeType != null) {
                            snap.recipeTypeName = toReadableName(
                                    recipe.recipeType.registryName.getPath());
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.debug("Error collecting recipe info", e);
        }
    }

    // Try to get a user-friendly name for the active recipe, preferring output item name if available
    private static String getOutputName(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        try {
            // Try item outputs first
            var itemCap = com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP;
            var outputs = recipe.outputs.get(itemCap);
            if (outputs != null && !outputs.isEmpty()) {
                Object raw = outputs.get(0).content;
                if (raw instanceof net.minecraft.world.item.crafting.Ingredient ing) {
                    net.minecraft.world.item.ItemStack[] items = ing.getItems();
                    if (items.length > 0 && !items[0].isEmpty()) {
                        return items[0].getHoverName().getString();
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback: use recipe path suffix
        String fullId = recipe.id != null ? recipe.id.getPath() : "";
        int slash = fullId.lastIndexOf('/');
        return toReadableName(slash >= 0 ? fullId.substring(slash + 1) : fullId);
    }

    static String toReadableName(String id) {
        if (id == null || id.isBlank()) return "";
        // Replace underscores with spaces, capitalize each word
        String[] words = id.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                sb.append(w.substring(1).toLowerCase());
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    // ─── Hatch breakdown ─────────────────────────────────────────────────────
    private static void collectHatchInfo(WorkableElectricMultiblockMachine machine, EnergySnapshot snap) {
        try {
            IMultiController ctrl = (IMultiController) machine;

            for (IMultiPart part : ctrl.getParts()) {
                BlockPos partPos = part.self().getPos();
                Level level = part.self().getLevel();
                if (level == null) continue;

                IEnergyContainer ec = GTCapabilityHelper.getEnergyContainer(level, partPos, null);
                if (ec == null) continue;

                // Standard GTCEu hatch detection via PartAbility
                boolean isInput = PartAbility.INPUT_ENERGY.isApplicable(part.self().getBlockState().getBlock())
                        || PartAbility.SUBSTATION_INPUT_ENERGY.isApplicable(part.self().getBlockState().getBlock());

                // GTMThings wireless hatch detection via block registry name
                var blockKey = ForgeRegistries.BLOCKS.getKey(part.self().getBlockState().getBlock());
                if (blockKey != null) {
                    String blockId = blockKey.toString().toLowerCase();
                    if (blockId.contains("wireless")) {
                        if (blockId.contains("input") || blockId.contains("target")) {
                            isInput = true;
                        } else if (blockId.contains("output") || blockId.contains("source")) {
                            isInput = false;
                        }
                        long vol = isInput ? ec.getInputVoltage() : ec.getOutputVoltage();
                        long amp = isInput ? ec.getInputAmperage() : ec.getOutputAmperage();
                        if (vol > 0) {
                            String tier = GTValues.VN[com.gregtechceu.gtceu.utils.GTUtil.getTierByVoltage(vol)];
                            String name = tier + " Wireless " + (isInput ? "Energy Input" : "Energy Output") + " Hatch";
                            if (amp > 1) name += " " + amp + "A";
                            snap.hatches.add(new EnergySnapshot.HatchInfo(name, vol, amp, isInput));
                            continue;
                        }
                    }
                }

                long voltage  = isInput ? ec.getInputVoltage()  : ec.getOutputVoltage();
                long amperage = isInput ? ec.getInputAmperage() : ec.getOutputAmperage();
                if (voltage <= 0) continue;

                String tier = GTValues.VN[com.gregtechceu.gtceu.utils.GTUtil.getTierByVoltage(voltage)];
                String name = tier + " " + (isInput ? "Energy Hatch" : "Dynamo Hatch");
                if (amperage > 1) name += " " + amperage + "A";
                snap.hatches.add(new EnergySnapshot.HatchInfo(name, voltage, amperage, isInput));
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.debug("Error collecting hatch info", e);
        }
    }

    // ─── History helpers ──────────────────────────────────────────────────────
    private static long[] updateHistory(Map<String, Deque<Long>> map, String key,
                                        long value, int maxSize) {
        Deque<Long> deque = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        deque.addLast(value);
        while (deque.size() > maxSize) deque.pollFirst();
        return deque.stream().mapToLong(Long::longValue).toArray();
    }

    private static String historyKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void clearHistory(Level level, BlockPos pos) {
        String key = historyKey(level, pos);
        inputHistoryMap.remove(key);
        outputHistoryMap.remove(key);
    }
}