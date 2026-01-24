package com.gtceuterminal.common.data;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.item.DismantlerItem;
import com.gtceuterminal.common.item.MultiStructureManagerItem;
import com.gtceuterminal.common.item.SchematicInterfaceItem;

import net.minecraft.world.item.Item;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class GTCEUTerminalItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GTCEUTerminalMod.MOD_ID);

    public static final RegistryObject<Item> MULTI_STRUCTURE_MANAGER = ITEMS.register("multi_structure_manager",
            () -> new MultiStructureManagerItem(new Item.Properties().stacksTo(1), 20, true));

    public static final RegistryObject<Item> SCHEMATIC_INTERFACE = ITEMS.register("schematic_interface",
            () -> new SchematicInterfaceItem());

    public static final RegistryObject<Item> DISMANTLER = ITEMS.register("dismantler",
            () -> new DismantlerItem(new Item.Properties().stacksTo(1)));
}