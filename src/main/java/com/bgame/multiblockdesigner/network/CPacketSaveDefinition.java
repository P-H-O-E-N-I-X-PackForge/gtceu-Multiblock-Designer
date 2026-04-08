package com.bgame.multiblockdesigner.network;

import com.bgame.multiblockdesigner.data.DefinitionSavedData;
import com.bgame.multiblockdesigner.definition.MultiblockDefinition;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import com.bgame.multiblockdesigner.item.DesignerWandItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

// Sent client → server when the player clicks "Confirm & Save" in the classify GUI.
public class CPacketSaveDefinition {

    private final CompoundTag wandNbt;
    private final String      displayName;

    public CPacketSaveDefinition(CompoundTag wandNbt, String displayName) {
        this.wandNbt     = wandNbt;
        this.displayName = displayName;
    }

    public static void encode(CPacketSaveDefinition pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.wandNbt);
        buf.writeUtf(pkt.displayName, 64);
    }

    public static CPacketSaveDefinition decode(FriendlyByteBuf buf) {
        return new CPacketSaveDefinition(buf.readNbt(), buf.readUtf(64));
    }

    public static void handle(CPacketSaveDefinition pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Reconstruct the scanned block list from the NBT the client sent
            ItemStack dummy = new ItemStack(net.minecraft.world.item.Items.STICK);
            dummy.setTag(pkt.wandNbt);
            List<ScannedBlock> classified = DesignerWandItem.loadScannedBlocks(dummy);

            if (classified.isEmpty()) return;

            // Build the definition
            MultiblockDefinition def;
            try {
                def = MultiblockDefinition.fromClassified(classified, pkt.displayName);
            } catch (IllegalArgumentException e) {
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "[Designer] Error: " + e.getMessage())
                );
                return;
            }

            // Persist in the world's SavedData
            ServerLevel level = player.serverLevel();
            DefinitionSavedData.get(level).save(def);

            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "[Designer] Saved definition '%s' (id: %s, %d blocks)"
                    .formatted(def.displayName, def.id, def.blocks.size()))
            );

            // Clear the wand scan state so the player can start fresh
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof com.bgame.multiblockdesigner.item.DesignerWandItem) {
                    DesignerWandItem.clearScanPublic(stack);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}