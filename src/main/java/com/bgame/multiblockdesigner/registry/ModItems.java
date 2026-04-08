package com.bgame.multiblockdesigner.registry;

import com.bgame.multiblockdesigner.MultiblockDesignerMod;
import com.bgame.multiblockdesigner.item.DesignerWandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MultiblockDesignerMod.MOD_ID);

    public static final RegistryObject<DesignerWandItem> DESIGNER_WAND =
            ITEMS.register("designer_wand", DesignerWandItem::new);

}