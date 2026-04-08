package com.bgame.multiblockdesigner.command;

import com.bgame.multiblockdesigner.MultiblockDesignerMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MultiblockDesignerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DesignerCommands.register(event.getDispatcher());
    }
}