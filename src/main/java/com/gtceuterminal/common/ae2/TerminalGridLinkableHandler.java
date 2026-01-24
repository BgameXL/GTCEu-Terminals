package com.gtceuterminal.common.ae2;

import appeng.api.features.IGridLinkableHandler;

import com.gtceuterminal.common.item.MultiStructureManagerItem;
import com.gtceuterminal.common.item.SchematicInterfaceItem;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/**
 * Handler for linking GTCEu Terminal items to ME Wireless Access Points
 * Registers with AE2's GridLinkable system
 */
public class TerminalGridLinkableHandler implements IGridLinkableHandler {

    private static final String TAG_ACCESS_POINT_POS = "accessPoint";

    @Override
    public boolean canLink(ItemStack stack) {
        return stack.getItem() instanceof MultiStructureManagerItem
                || stack.getItem() instanceof SchematicInterfaceItem;
    }

    @Override
    public void link(ItemStack itemStack, GlobalPos pos) {
        // Save the wireless access point position using AE2's format
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                .result()
                .ifPresent(tag -> itemStack.getOrCreateTag().put(TAG_ACCESS_POINT_POS, tag));
    }

    @Override
    public void unlink(ItemStack itemStack) {
        // Remove the link (called by AE2 when needed)
        if (itemStack.hasTag()) {
            itemStack.getTag().remove(TAG_ACCESS_POINT_POS);
        }
    }
}