package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.math.BigInteger;

// Infers machine state and energy usage by analyzing traits, capabilities, and recipe logic.
public class MachineInferencer {

    public static class MachineSnapshot {
        public final long euPerTick;
        public final MachineState state;
        public final long inputEuPerTick;
        public final long outputEuPerTick;
        public final long storedEu;
        public final long capacityEu;

        public MachineSnapshot(long euPerTick, MachineState state) {
            this(euPerTick, state, 0, 0, 0, 0);
        }

        public MachineSnapshot(long euPerTick, MachineState state, long inputEuPerTick, long outputEuPerTick) {
            this(euPerTick, state, inputEuPerTick, outputEuPerTick, 0, 0);
        }

        public MachineSnapshot(long euPerTick,
                               MachineState state,
                               long inputEuPerTick,
                               long outputEuPerTick,
                               long storedEu,
                               long capacityEu) {
            this.euPerTick = euPerTick;
            this.state = state;
            this.inputEuPerTick = inputEuPerTick;
            this.outputEuPerTick = outputEuPerTick;
            this.storedEu = storedEu;
            this.capacityEu = capacityEu;
        }

        public boolean hasInputOutputData() {
            return inputEuPerTick > 0 || outputEuPerTick > 0;
        }

        public boolean hasStorageData() {
            return capacityEu > 0 || storedEu > 0;
        }
    }

    private static class EnergyTracker {
        long lastEnergy = -1;
        long lastTime = -1;
        int calculatedEuPerTick = 0;
    }

    private static final int MAX_TRACKED_MACHINES = 1000; // ~36 KB max
    private static final java.util.Map<BlockPos, EnergyTracker> energyTrackers =
            new java.util.LinkedHashMap<BlockPos, EnergyTracker>(MAX_TRACKED_MACHINES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<BlockPos, EnergyTracker> eldest) {
                    return size() > MAX_TRACKED_MACHINES;
                }
            };

    public static MachineSnapshot inferMachine(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return new MachineSnapshot(0, MachineState.IDLE_CHUNK_UNLOADED);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return new MachineSnapshot(0, MachineState.UNKNOWN);
        }

        // Get MetaMachine if it's a GTCEu machine
        MetaMachine metaMachine = null;
        if (be instanceof IMachineBlockEntity machineEntity) {
            metaMachine = machineEntity.getMetaMachine();
        }

        if (metaMachine == null) {
            return new MachineSnapshot(0, MachineState.UNKNOWN);
        }

        System.out.println("=== MachineInferencer for " + pos + " ===");

        boolean isMultiblock = metaMachine instanceof IMultiController;
        boolean hasRecipeLogic = metaMachine instanceof IRecipeLogicMachine;
        System.out.println("  Is Multiblock: " + isMultiblock);
        System.out.println("  Has Recipe Logic: " + hasRecipeLogic);

        MachineSnapshot energySnapshot = inferFromEnergyContainerWithDelta(metaMachine, pos);
        if (energySnapshot != null && energySnapshot.euPerTick > 0) {
            System.out.println("  -> Used energy container delta: " + energySnapshot.euPerTick + " EU/t");
            return energySnapshot;
        }

        if (metaMachine instanceof IMultiController controller) {
            MachineSnapshot result = inferMultiblockController(controller);
            if (result.euPerTick > 0 || energySnapshot == null) {
                System.out.println("  -> Multiblock: " + result.euPerTick + " EU/t, state=" + result.state);
                return result;
            }
        }

        if (energySnapshot != null) {
            // System.out.println("  -> Using energy container (0 EU/t): " + energySnapshot.state);
            return energySnapshot;
        }

        if (metaMachine instanceof IRecipeLogicMachine machine) {
            MachineSnapshot result = inferRecipeLogicMachine(machine);
            System.out.println("  -> Recipe machine: " + result.euPerTick + " EU/t");
            return result;
        }

        System.out.println("  -> Fallback: RUNNING with 0 EU/t");
        return new MachineSnapshot(0, MachineState.RUNNING);
    }

    private static MachineSnapshot inferFromEnergyContainerWithDelta(MetaMachine metaMachine, BlockPos pos) {
        try {
            System.out.println("  [DEBUG] Checking for Energy Container trait...");

            var traits = metaMachine.getTraits();
            System.out.println("  [DEBUG] Total traits: " + traits.size());
            for (var trait : traits) {
                System.out.println("    [DEBUG] Trait: " + trait.getClass().getSimpleName());
            }

            var energyContainerOpt = metaMachine.getTraits().stream()
                    .filter(IEnergyContainer.class::isInstance)
                    .map(IEnergyContainer.class::cast)
                    .findFirst();

            System.out.println("  [DEBUG] IEnergyContainer present: " + energyContainerOpt.isPresent());

            if (!energyContainerOpt.isPresent()) {
                var powerStationOpt = metaMachine.getTraits().stream()
                        .filter(trait -> trait.getClass().getSimpleName().equals("PowerStationEnergyBank"))
                        .findFirst();

                System.out.println("  [DEBUG] PowerStationEnergyBank present: " + powerStationOpt.isPresent());

                if (powerStationOpt.isPresent()) {
                    System.out.println("  [DEBUG] Detected Power Substation - attempting reflection access");
                    return inferFromPowerStationBank(powerStationOpt.get(), pos);
                }
            }

            if (energyContainerOpt.isPresent()) {
                IEnergyContainer energyContainer = energyContainerOpt.get();

                long storedEnergy = energyContainer.getEnergyStored();
                long maxEnergy = energyContainer.getEnergyCapacity();
                long outputVoltage = energyContainer.getOutputVoltage();
                long outputAmperage = energyContainer.getOutputAmperage();
                long inputVoltage = energyContainer.getInputVoltage();
                long inputAmperage = energyContainer.getInputAmperage();

                System.out.println("  Energy Container found:");
                System.out.println("    Stored: " + storedEnergy + " / " + maxEnergy);
                System.out.println("    Output: " + outputVoltage + "V × " + outputAmperage + "A = " + (outputVoltage * outputAmperage) + " EU/t");
                System.out.println("    Input: " + inputVoltage + "V × " + inputAmperage + "A = " + (inputVoltage * inputAmperage) + " EU/t");

                long theoreticalOutput = outputVoltage * outputAmperage;

                long theoreticalInput = inputVoltage * inputAmperage;

                EnergyTracker tracker = energyTrackers.computeIfAbsent(pos, k -> new EnergyTracker());
                long currentTime = System.currentTimeMillis();

                if (tracker.lastEnergy >= 0 && tracker.lastTime >= 0) {
                    long timeDelta = currentTime - tracker.lastTime;
                    if (timeDelta >= 50 && timeDelta < 2000) {
                        long energyDelta = Math.abs(storedEnergy - tracker.lastEnergy);

                        tracker.calculatedEuPerTick = (int) ((energyDelta * 1000) / (timeDelta * 20));
                        System.out.println("    Delta calculated: " + tracker.calculatedEuPerTick + " EU/t (from " + energyDelta + " EU over " + timeDelta + "ms)");
                    }
                }

                tracker.lastEnergy = storedEnergy;
                tracker.lastTime = currentTime;

                int eut = 0;

                if (tracker.calculatedEuPerTick > 0) {
                    eut = tracker.calculatedEuPerTick;
                    System.out.println("    Using delta EU/t: " + eut);
                }
                else if (theoreticalOutput > 0 || theoreticalInput > 0) {
                    eut = (int) Math.min(Math.max(theoreticalOutput, theoreticalInput), Integer.MAX_VALUE);
                    System.out.println("    Using theoretical capacity: " + eut + " EU/t (buffer/battery)");
                }

                // Determine state
                MachineState state;
                if (tracker.calculatedEuPerTick > 0) {
                    state = MachineState.RUNNING;
                } else if (storedEnergy <= 0) {
                    state = MachineState.IDLE_NO_POWER;
                } else if (storedEnergy >= maxEnergy * 0.99) {
                    state = MachineState.RUNNING;
                } else {
                    state = MachineState.RUNNING;
                }

                System.out.println("    Final EU/t: " + eut + ", State: " + state);
                return new MachineSnapshot(
                        eut,
                        state,
                        theoreticalInput,
                        theoreticalOutput,
                        storedEnergy,
                        maxEnergy
                );

            }
        } catch (Exception e) {
            System.err.println("Error in inferFromEnergyContainer: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private static MachineSnapshot inferFromPowerStationBank(MachineTrait trait, BlockPos pos) {
        try {
            // Get the PowerSubstationMachine instance (parent of the trait)
            MetaMachine machine = trait.getMachine();

            // BigInteger getStored()
            // BigInteger getCapacity()

            // long getInputPerSec()
            // long getOutputPerSec()

            Class<?> traitClazz = trait.getClass();
            Class<?> machineClazz = machine.getClass();

            long storedEnergy = 0;
            java.math.BigInteger bigStored = java.math.BigInteger.ZERO;
            java.math.BigInteger bigCapacity = java.math.BigInteger.ZERO;

            try {
                var getStoredMethod = traitClazz.getMethod("getStored");
                Object storedObj = getStoredMethod.invoke(trait);
                if (storedObj instanceof java.math.BigInteger) {
                    bigStored = (java.math.BigInteger) storedObj;
                    storedEnergy = bigStored.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0
                            ? Long.MAX_VALUE
                            : bigStored.longValue();
                }
            } catch (Exception e) {
            }

            try {
                var getCapacityMethod = traitClazz.getMethod("getCapacity");
                Object capacityObj = getCapacityMethod.invoke(trait);
                if (capacityObj instanceof java.math.BigInteger) {
                    bigCapacity = (java.math.BigInteger) capacityObj;
                }
            } catch (Exception e) {
            }

            int fillPercent = 0;
            if (!bigCapacity.equals(java.math.BigInteger.ZERO)) {
                fillPercent = bigStored.multiply(java.math.BigInteger.valueOf(100))
                        .divide(bigCapacity).intValue();
            }

            long inputPerSec = 0;
            long outputPerSec = 0;

            try {
                var getInputMethod = machineClazz.getMethod("getInputPerSec");
                inputPerSec = (long) getInputMethod.invoke(machine);
            } catch (Exception e) {
                // System.out.println("    [DEBUG] Could not get inputPerSec: " + e.getMessage());
            }

            try {
                var getOutputMethod = machineClazz.getMethod("getOutputPerSec");
                outputPerSec = (long) getOutputMethod.invoke(machine);
            } catch (Exception e) {
                // System.out.println("    [DEBUG] Could not get outputPerSec: " + e.getMessage());
            }

            // System.out.println("  PowerStationEnergyBank:");
            System.out.println("    Stored: " + storedEnergy + " EU (" + fillPercent + "% full)");

            long inputPerTick = inputPerSec / 20;
            long outputPerTick = Math.abs(outputPerSec) / 20;

            if (inputPerTick > 0 || outputPerTick > 0) {
                System.out.println("    Input: " + inputPerTick + " EU/t (" + inputPerSec + " EU/s)");
                System.out.println("    Output: " + outputPerTick + " EU/t (" + outputPerSec + " EU/s)");
            }

            long flowRate = Math.max(inputPerTick, outputPerTick);

            if (flowRate > 0) {
                System.out.println("    → Showing REAL flow rate: " + flowRate + " EU/t");
            } else {
                long maxEnergy = bigCapacity.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0
                        ? Long.MAX_VALUE
                        : bigCapacity.longValue();

                long eut;
                if (maxEnergy == Long.MAX_VALUE) {
                    eut = 10_000_000;
                } else if (maxEnergy >= 50_000_000_000L) {
                    eut = 5_000_000;
                } else if (maxEnergy >= 5_000_000_000L) {
                    eut = 1_000_000;
                } else if (maxEnergy >= 500_000_000) {
                    eut = 262_144;
                } else if (maxEnergy >= 50_000_000) {
                    eut = 65_536;
                } else if (maxEnergy >= 5_000_000) {
                    eut = 16_384;
                } else {
                    eut = 4_096;
                }

                flowRate = eut;
                System.out.println("    → Showing capacity estimate: " + flowRate + " EU/t (idle)");
            }

            MachineState state;
            if (storedEnergy == 0) {
                state = MachineState.IDLE_NO_POWER;
            } else {
                state = MachineState.RUNNING;
            }

            long capacityLong = bigCapacity.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0
                    ? Long.MAX_VALUE
                    : bigCapacity.longValue();

            return new MachineSnapshot(flowRate, state, inputPerTick, outputPerTick, storedEnergy, capacityLong);


        } catch (Exception e) {
            System.err.println("Error in inferFromPowerStationBank: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

private static MachineSnapshot inferMultiblockController(IMultiController controller) {
    if (!controller.isFormed()) {
        return new MachineSnapshot(0, MachineState.IDLE_NOT_FORMED);
    }

    if (!(controller instanceof IRecipeLogicMachine machine)) {
        return new MachineSnapshot(0, MachineState.UNKNOWN);
    }

    RecipeLogic logic = machine.getRecipeLogic();
    if (logic == null) {
        return new MachineSnapshot(0, MachineState.UNKNOWN);
    }

    if (!logic.isWorkingEnabled()) {
        return new MachineSnapshot(0, MachineState.IDLE_MACHINE_OFF);
    }

    int maxVoltageCapacity = getMaxEnergyHatchVoltage(controller);

    if (logic.isActive()) {
        int eut = getEuPerTick(logic);

        if (maxVoltageCapacity > eut) {
            System.out.println("  Multiblock running: using " + eut + " EU/t, capacity " + maxVoltageCapacity + " EU/t");
            return new MachineSnapshot(maxVoltageCapacity, MachineState.RUNNING);
        }

        return new MachineSnapshot(eut, MachineState.RUNNING);
    }

    // When idle, show the max capacity
    if (maxVoltageCapacity > 0) {
        return new MachineSnapshot(maxVoltageCapacity, MachineState.IDLE_NO_RECIPE);
    }

    return new MachineSnapshot(0, MachineState.IDLE_NO_RECIPE);
}


private static int getMaxEnergyHatchVoltage(IMultiController controller) {
    try {
        long maxVoltage = 0;

        // Get parts from the controller
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof MetaMachine partMachine) {
                for (var trait : partMachine.getTraits()) {
                    if (trait instanceof IEnergyContainer energyContainer) {
                        long inputVoltage = energyContainer.getInputVoltage();
                        if (inputVoltage > maxVoltage) {
                            maxVoltage = inputVoltage;
                        }
                    }
                }
            }
        }

        if (maxVoltage > 0) {
            System.out.println("  [DEBUG] Max energy hatch voltage: " + maxVoltage + "V");
        }

        return (int) Math.min(maxVoltage, Integer.MAX_VALUE);

    } catch (Exception e) {
        System.err.println("  [DEBUG] Error getting max voltage: " + e.getMessage());
        return 0;
    }
}

private static MachineSnapshot inferRecipeLogicMachine(IRecipeLogicMachine machine) {
    RecipeLogic logic = machine.getRecipeLogic();
    if (logic == null) {
        return new MachineSnapshot(0, MachineState.UNKNOWN);
    }

    if (!logic.isWorkingEnabled()) {
        return new MachineSnapshot(0, MachineState.IDLE_MACHINE_OFF);
    }

    if (logic.isActive()) {
        int eut = getEuPerTick(logic);
        return new MachineSnapshot(eut, MachineState.RUNNING);
    }

    return new MachineSnapshot(0, MachineState.IDLE_NO_RECIPE);
}

private static int getEuPerTick(RecipeLogic logic) {
    try {
        System.out.println("  Getting EU/t from RecipeLogic...");

        var recipeField = logic.getClass().getDeclaredField("lastRecipe");
        recipeField.setAccessible(true);
        GTRecipe recipe = (GTRecipe) recipeField.get(logic);

        if (recipe == null) {
            System.out.println("    No recipe found");
            return 0;
        }

        System.out.println("    Recipe found: " + recipe.id);

        var tickInputs = recipe.tickInputs;
        if (tickInputs != null && !tickInputs.isEmpty()) {
            System.out.println("    Checking tickInputs...");
            for (var entry : tickInputs.entrySet()) {
                var capability = entry.getKey();
                System.out.println("      Capability: " + capability.getClass().getSimpleName());

                if (capability.getClass().getSimpleName().contains("EU") ||
                        capability.getClass().getName().contains("Energy")) {
                    var contents = entry.getValue();
                    if (!contents.isEmpty()) {
                        var content = contents.get(0);
                        System.out.println("      Found EU content: " + content);

                        try {
                            var contentObj = content.content;
                            var voltageMethod = contentObj.getClass().getMethod("voltage");
                            var amperageMethod = contentObj.getClass().getMethod("amperage");
                            long voltage = (long) voltageMethod.invoke(contentObj);
                            long amperage = (long) amperageMethod.invoke(contentObj);
                            long euPerTick = voltage * amperage;
                            System.out.println("      EU/t from energy stack: " + voltage + "V × " + amperage + "A = " + euPerTick);
                            return (int) Math.min(euPerTick, Integer.MAX_VALUE);
                        } catch (Exception e) {
                            System.out.println("      Could not extract voltage/amperage: " + e.getMessage());
                        }
                    }
                }
            }
        }

        var data = recipe.data;
        if (data != null && data.contains("eut")) {
            int eut = data.getInt("eut");
            System.out.println("    EU/t from NBT data: " + eut);
            return eut;
        }

        System.out.println("    Could not determine EU/t from recipe");
        return 0;

    } catch (Exception e) {
        System.err.println("  Error getting EU/t: " + e.getMessage());
        e.printStackTrace();
        return 0;
    }
}

    public static void clearTracker(BlockPos pos) {
        energyTrackers.remove(pos);
    }
}