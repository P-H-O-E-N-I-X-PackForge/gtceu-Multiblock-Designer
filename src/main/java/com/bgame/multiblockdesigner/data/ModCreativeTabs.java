package com.bgame.multiblockdesigner.data;

import com.bgame.multiblockdesigner.MultiblockDesignerMod;
import com.bgame.multiblockdesigner.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MultiblockDesignerMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> DESIGNER_TAB = CREATIVE_MODE_TABS.register("designer_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.DESIGNER_WAND.get())) // The tab icon
                    .title(Component.literal("Designer"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DESIGNER_WAND.get());
                        output.accept(ModItems.COPY_TOOL.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}