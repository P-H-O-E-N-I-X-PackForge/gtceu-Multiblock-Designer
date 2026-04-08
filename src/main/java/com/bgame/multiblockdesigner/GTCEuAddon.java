package com.bgame.multiblockdesigner;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@GTAddon
public class GTCEuAddon implements IGTAddon {

    public GTCEuAddon() {}

    @Override
    public GTRegistrate getRegistrate() {
        return GTRegistrate.create(MultiblockDesignerMod.MOD_ID);
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return MultiblockDesignerMod.MOD_ID;
    }
}