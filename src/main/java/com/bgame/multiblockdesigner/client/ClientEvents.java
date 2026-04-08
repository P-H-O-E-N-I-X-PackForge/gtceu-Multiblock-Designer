package com.bgame.multiblockdesigner.client;

import com.bgame.multiblockdesigner.MultiblockDesignerMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MultiblockDesignerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    private static final SelectionRenderer SELECTION_RENDERER = new SelectionRenderer();

    static {
        MinecraftForge.EVENT_BUS.register(SELECTION_RENDERER);
    }
}