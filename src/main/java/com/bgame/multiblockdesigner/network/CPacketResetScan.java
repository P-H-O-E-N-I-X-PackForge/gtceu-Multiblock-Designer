package com.bgame.multiblockdesigner.network;

import com.bgame.multiblockdesigner.item.DesignerWandItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client → server when the player clicks "↺ Reset" in the classify GUI.
 * Clears the scan NBT from the wand so the player can select a new structure.
 */
public class CPacketResetScan {

    public static CPacketResetScan decode(FriendlyByteBuf buf) {
        return new CPacketResetScan();
    }

    public static void encode(CPacketResetScan pkt, FriendlyByteBuf buf) {
        // no payload needed
    }

    public static void handle(CPacketResetScan pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof DesignerWandItem) {
                    DesignerWandItem.clearScanPublic(stack);
                    // Force inventory sync so the client sees the updated NBT
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    return;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}