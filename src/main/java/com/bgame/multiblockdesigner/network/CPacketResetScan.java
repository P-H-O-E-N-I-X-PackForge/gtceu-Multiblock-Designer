package com.bgame.multiblockdesigner.network;

import com.bgame.multiblockdesigner.item.CopyToolItem;
import com.bgame.multiblockdesigner.item.DesignerWandItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client → server when the player clicks "↺ Reset" in the classify GUI.
 * Clears the scan NBT from the wand/tool so the player can select a new structure.
 */
public class CPacketResetScan {

    public static CPacketResetScan decode(FriendlyByteBuf buf) {
        return new CPacketResetScan();
    }

    public static void encode(CPacketResetScan pkt, FriendlyByteBuf buf) {
        // no payload needed
    }

    public static void handle(CPacketResetScan pkt, Supplier<NetworkEvent.Context> ctxGetter) {
        NetworkEvent.Context ctx = ctxGetter.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof DesignerWandItem) {
                    DesignerWandItem.clearScanPublic(stack);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    return;
                } else if (stack.getItem() instanceof CopyToolItem) {
                    stack.getOrCreateTag().remove(CopyToolItem.NBT_POS1);
                    stack.getOrCreateTag().remove(CopyToolItem.NBT_POS2);
                    stack.getOrCreateTag().remove(CopyToolItem.TAG_SCANNED);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    return;
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}