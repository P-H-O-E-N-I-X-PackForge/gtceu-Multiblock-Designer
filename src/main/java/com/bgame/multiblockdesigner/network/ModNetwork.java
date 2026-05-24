package com.bgame.multiblockdesigner.network;

import com.bgame.multiblockdesigner.MultiblockDesignerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MultiblockDesignerMod.MOD_ID + ":main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SPacketOpenClassifyGui.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SPacketOpenClassifyGui::encode)
                .decoder(SPacketOpenClassifyGui::decode)
                .consumerMainThread(SPacketOpenClassifyGui::handle)
                .add();
        CHANNEL.messageBuilder(CPacketResetScan.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketResetScan::encode)
                .decoder(CPacketResetScan::decode)
                .consumerMainThread(CPacketResetScan::handle)
                .add();
        CHANNEL.messageBuilder(CPacketSaveDefinition.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CPacketSaveDefinition::encode)
                .decoder(CPacketSaveDefinition::decode)
                .consumerMainThread(CPacketSaveDefinition::handle)
                .add();
        CHANNEL.messageBuilder(SPacketClipboard.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SPacketClipboard::encode)
                .decoder(SPacketClipboard::decode)
                .consumerMainThread(SPacketClipboard::handle)
                .add();
    }
}