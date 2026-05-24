package com.bgame.multiblockdesigner;

import com.bgame.multiblockdesigner.data.ModCreativeTabs;
import com.bgame.multiblockdesigner.network.ModNetwork;
import com.bgame.multiblockdesigner.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MultiblockDesignerMod.MOD_ID)
public class MultiblockDesignerMod {

    public static final String MOD_ID = "gtceu_multiblock_designer";

    public MultiblockDesignerMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModCreativeTabs.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModNetwork.register();
    }
}