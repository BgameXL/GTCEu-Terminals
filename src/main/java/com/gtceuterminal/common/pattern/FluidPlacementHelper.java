package com.gtceuterminal.common.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.fluids.GTFluid;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.item.GTBucketItem;
import com.gtceuterminal.GTCEUTerminalMod;

import java.util.Optional;

// Helper class to handle fluid placement logic, including checks for valid placement
// handling of special cases (like water in the nether), and integration with player inventory and ME Network fluid storage.
public class FluidPlacementHelper {

    /**
     * Attempts to place a fluid at the given position
     * @param world The world
     * @param pos The position to place the fluid
     * @param player The player (used for creative mode checks and sounds)
     * @param fluid The fluid to place
     * @param playerInventory Optional player inventory for bucket extraction
     * @param fluidStorage Optional ME Network fluid storage
     * @return true if the fluid was successfully placed
     */
    public static boolean tryPlaceFluid(
            @NotNull Level world,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull Fluid fluid,
            @Nullable IItemHandler playerInventory,
            @Nullable IFluidHandler fluidStorage
    ) {
        // Validate that it's a flowing fluid (most placeable fluids are)
        if (!(fluid instanceof FlowingFluid)) {
            return false;
        }

        BlockState currentState = world.getBlockState(pos);

        // Check if we can place the fluid here
        if (!canPlaceFluid(world, pos, currentState, fluid)) {
            return false;
        }

        // In creative mode, just place it
        if (player.isCreative()) {
            return placeFluidBlock(world, pos, currentState, fluid, player);
        }

        // Try to find a bucket in inventory
        boolean bucketFound = false;
        if (playerInventory != null) {
            for (int i = 0; i < playerInventory.getSlots(); i++) {
                ItemStack stack = playerInventory.getStackInSlot(i);
                if (stack.getItem() instanceof BucketItem bucketItem) {
                    if (bucketItem.getFluid() == fluid) {
                        // Extract the bucket and place the fluid
                        if (placeFluidBlock(world, pos, currentState, fluid, player)) {
                            playerInventory.extractItem(i, 1, false);
                            bucketFound = true;
                            break;
                        }
                    }
                }
            }
        }

        // If no bucket found, try ME Network fluid storage
        if (!bucketFound && fluidStorage != null) {
            // Calculate the amount needed (1000mb = 1 bucket = 1 block)
            FluidStack requiredFluid = new FluidStack(fluid, 1000);

            // Try to drain from ME Network
            FluidStack drained = fluidStorage.drain(requiredFluid, IFluidHandler.FluidAction.SIMULATE);
            if (drained.getAmount() >= 1000) {
                if (placeFluidBlock(world, pos, currentState, fluid, player)) {
                    // Actually drain the fluid from ME
                    fluidStorage.drain(requiredFluid, IFluidHandler.FluidAction.EXECUTE);
                    return true;
                }
            }
        }

        return bucketFound;
    }


    // Checks if a fluid can be placed at the given position
    private static boolean canPlaceFluid(Level world, BlockPos pos, BlockState state, Fluid fluid) {
        // Already the correct fluid? Skip
        if (state == fluid.defaultFluidState().createLegacyBlock()) {
            return false;
        }

        // Can place in air
        if (state.isAir()) {
            return true;
        }

        // Can replace with fluid
        if (state.canBeReplaced(fluid)) {
            return true;
        }

        // Special container blocks (like waterlogged blocks)
        if (state.getBlock() instanceof LiquidBlockContainer container) {
            return container.canPlaceLiquid(world, pos, state, fluid);
        }

        return false;
    }


    // Actually places the fluid block in the world with proper effects
    private static boolean placeFluidBlock(Level world, BlockPos pos, BlockState currentState,
                                           Fluid fluid, @Nullable Player player) {
        // Check for vaporization (water in nether, plasma fluids, etc.)
        if (shouldVaporize(world, pos, fluid)) {
            playVaporizationEffect(world, pos, player);
            return true; // Fluid was "placed" (vaporized)
        }

        // Handle special liquid containers
        if (currentState.getBlock() instanceof LiquidBlockContainer container && fluid == Fluids.WATER) {
            container.placeLiquid(world, pos, currentState, ((FlowingFluid) fluid).getSource(false));
            playEmptySound(world, pos, fluid, player);
            return true;
        }

        // Standard fluid placement
        if (currentState.canBeReplaced(fluid) && !currentState.liquid()) {
            world.destroyBlock(pos, true);
        }

        BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
        if (world.setBlock(pos, fluidState, Block.UPDATE_ALL_IMMEDIATE) &&
                fluidState.getFluidState().isSource()) {
            playEmptySound(world, pos, fluid, player);
            return true;
        }

        return false;
    }


    // Checks if the fluid should vaporize when placed
    private static boolean shouldVaporize(Level world, BlockPos pos, Fluid fluid) {
        // Water in nether
        if (world.dimensionType().ultraWarm() && fluid.is(FluidTags.WATER)) {
            return true;
        }

        // GTCeu specific: plasma and gas fluids
        // This requires checking if the fluid is a GTFluid with vaporization properties
        // For now, we'll just check the basic water in nether case
        return false;
    }


    // Plays vaporization effect (water in nether, plasma, etc.)
    private static void playVaporizationEffect(Level world, BlockPos pos, @Nullable Player player) {
        world.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);

        // Add smoke particles
        for (int i = 0; i < 8; i++) {
            double x = pos.getX() + world.random.nextDouble();
            double y = pos.getY() + world.random.nextDouble();
            double z = pos.getZ() + world.random.nextDouble();
            world.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }


    // Plays the bucket empty sound
    private static void playEmptySound(Level world, BlockPos pos, Fluid fluid, @Nullable Player player) {
        var soundEvent = fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        world.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
    }


    // Extracts a fluid bucket from the player's inventory or ME Network
    // Returns the fluid if found, null otherwise
    @Nullable
    public static Fluid findAvailableFluid(IItemHandler playerInventory, @Nullable IFluidHandler fluidStorage) {
        // Check player inventory for buckets
        if (playerInventory != null) {
            for (int i = 0; i < playerInventory.getSlots(); i++) {
                ItemStack stack = playerInventory.getStackInSlot(i);
                if (stack.getItem() instanceof BucketItem bucketItem) {
                    Fluid fluid = bucketItem.getFluid();
                    if (fluid != Fluids.EMPTY) {
                        return fluid;
                    }
                }
            }
        }

        // Check ME Network fluid storage
        if (fluidStorage != null) {
            // Get the first available fluid with at least 1000mb (1 bucket)
            for (int tank = 0; tank < fluidStorage.getTanks(); tank++) {
                FluidStack fluidInTank = fluidStorage.getFluidInTank(tank);
                if (!fluidInTank.isEmpty() && fluidInTank.getAmount() >= 1000) {
                    return fluidInTank.getFluid();
                }
            }
        }

        return null;
    }
}