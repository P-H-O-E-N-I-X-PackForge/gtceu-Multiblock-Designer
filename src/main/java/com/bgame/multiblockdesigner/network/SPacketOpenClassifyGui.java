package com.bgame.multiblockdesigner.network;

import com.bgame.multiblockdesigner.gui.WandClassifyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent server → client to open the classify GUI.
 * Carries the full wand NBT so the client has the scanned block list.
 */
public class SPacketOpenClassifyGui {

    private final CompoundTag wandNbt;

    public SPacketOpenClassifyGui(CompoundTag wandNbt) {
        this.wandNbt = wandNbt;
    }

    public static SPacketOpenClassifyGui decode(FriendlyByteBuf buf) {
        return new SPacketOpenClassifyGui(buf.readNbt());
    }

    public static void encode(SPacketOpenClassifyGui pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.wandNbt);
    }

    public static void handle(SPacketOpenClassifyGui pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Reconstruct a dummy ItemStack just to carry the NBT into the screen
            ItemStack dummy = new ItemStack(Items.STICK);
            dummy.setTag(pkt.wandNbt);
            Minecraft.getInstance().setScreen(new WandClassifyScreen(dummy));
        });
        ctx.get().setPacketHandled(true);
    }
}