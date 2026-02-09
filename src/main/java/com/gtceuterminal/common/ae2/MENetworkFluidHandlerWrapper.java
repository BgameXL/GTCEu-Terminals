package com.gtceuterminal.common.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.gtceuterminal.GTCEUTerminalMod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper that adapts AE2's ME Network fluid storage to Forge's IFluidHandler interface
 * This allows the auto-builder to extract fluids directly from the ME Network
 */
public class MENetworkFluidHandlerWrapper implements IFluidHandler {

    private final MEStorage inventory;
    private final IActionSource actionSource;
    private List<FluidStack> cachedFluids;
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 1000; // 1 second cache

    public MENetworkFluidHandlerWrapper(MEStorage inventory, IActionSource actionSource) {
        this.inventory = inventory;
        this.actionSource = actionSource;
        this.cachedFluids = new ArrayList<>();
        updateCache();
    }

    // Factory method to create the wrapper from an AE2 grid
    public static MENetworkFluidHandlerWrapper fromGrid(IGrid grid, IActionSource actionSource) {
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return null;
        }
        return new MENetworkFluidHandlerWrapper(storage.getInventory(), actionSource);
    }

    private void updateCache() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < CACHE_DURATION) {
            return;
        }

        cachedFluids.clear();

        try {
            // Get all available stacks from the ME Network
            KeyCounter availableStacks = new KeyCounter();
            inventory.getAvailableStacks(availableStacks);

            for (var entry : availableStacks) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();

                if (key instanceof AEFluidKey fluidKey && amount > 0) {
                    // Convert from AE2's unit (1 droplet) to Forge's unit (1mb)
                    // AE2 uses droplets where 81000 droplets = 1 bucket = 1000mb
                    // So 81 droplets = 1mb
                    int amountMB = (int) (amount / 81);

                    if (amountMB > 0) {
                        FluidStack stack = new FluidStack(fluidKey.getFluid(), amountMB);
                        cachedFluids.add(stack);
                    }
                }
            }

            lastCacheUpdate = now;

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error updating ME Network fluid cache", e);
        }
    }

    @Override
    public int getTanks() {
        updateCache();
        return cachedFluids.size();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        updateCache();
        if (tank < 0 || tank >= cachedFluids.size()) {
            return FluidStack.EMPTY;
        }
        return cachedFluids.get(tank).copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        // ME Network has "infinite" capacity from this perspective
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        // ME Network can store any fluid
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // We don't support inserting fluids into ME Network from auto-builder
        // (only extracting)
        return 0;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        try {
            // Convert from mb (Forge) to droplets (AE2)
            // 1000mb = 81000 droplets
            long dropletsRequested = resource.getAmount() * 81L;

            AEFluidKey fluidKey = AEFluidKey.of(resource.getFluid());

            long extracted;
            if (action == FluidAction.SIMULATE) {
                // Just check if we can extract
                extracted = inventory.extract(
                        fluidKey,
                        dropletsRequested,
                        Actionable.SIMULATE,
                        actionSource
                );
            } else {
                // Actually extract
                extracted = inventory.extract(
                        fluidKey,
                        dropletsRequested,
                        Actionable.MODULATE,
                        actionSource
                );

                // Invalidate cache since we modified the network
                lastCacheUpdate = 0;
            }

            // Convert back from droplets to mb
            int extractedMB = (int) (extracted / 81);

            if (extractedMB > 0) {
                return new FluidStack(resource.getFluid(), extractedMB);
            }

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error draining fluid from ME Network", e);
        }

        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        updateCache();

        // Drain from the first available fluid
        if (cachedFluids.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidStack first = cachedFluids.get(0);
        FluidStack toDrain = new FluidStack(first.getFluid(), Math.min(maxDrain, first.getAmount()));

        return drain(toDrain, action);
    }

    public boolean hasFluid(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return false;
        }

        updateCache();

        for (FluidStack stored : cachedFluids) {
            if (stored.getFluid() == fluid.getFluid() && stored.getAmount() >= fluid.getAmount()) {
                return true;
            }
        }

        return false;
    }

    // Helper method to get the total amount of a specific fluid in the ME Network
    public int getFluidAmount(net.minecraft.world.level.material.Fluid fluid) {
        updateCache();

        for (FluidStack stored : cachedFluids) {
            if (stored.getFluid() == fluid) {
                return stored.getAmount();
            }
        }

        return 0;
    }
}