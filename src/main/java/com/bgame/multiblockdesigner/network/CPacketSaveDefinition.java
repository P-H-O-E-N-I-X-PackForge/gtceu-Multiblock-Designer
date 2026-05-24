package com.bgame.multiblockdesigner.network;

import com.bgame.multiblockdesigner.data.DefinitionSavedData;
import com.bgame.multiblockdesigner.definition.MultiblockDefinition;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import com.bgame.multiblockdesigner.export.DefinitionExporter;
import com.bgame.multiblockdesigner.item.CopyToolItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Sent client → server when the player clicks "Confirm & Save" in the classify GUI.
public class CPacketSaveDefinition {

    private final CompoundTag wandNbt;
    private final String      displayName;
    private final boolean     exportJava;

    public CPacketSaveDefinition(CompoundTag wandNbt, String displayName) {
        this(wandNbt, displayName, false);
    }

    public CPacketSaveDefinition(CompoundTag wandNbt, String displayName, boolean exportJava) {
        this.wandNbt     = wandNbt;
        this.displayName = displayName;
        this.exportJava  = exportJava;
    }

    public static void encode(CPacketSaveDefinition pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.wandNbt);
        buf.writeUtf(pkt.displayName, 64);
        buf.writeBoolean(pkt.exportJava);
    }

    public static CPacketSaveDefinition decode(FriendlyByteBuf buf) {
        return new CPacketSaveDefinition(buf.readNbt(), buf.readUtf(64), buf.readBoolean());
    }

    public static void handle(CPacketSaveDefinition pkt, Supplier<NetworkEvent.Context> ctxGetter) {
        NetworkEvent.Context ctx = ctxGetter.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Reconstruct the scanned block list from the NBT the client sent
            List<ScannedBlock> classified = new ArrayList<>();
            String key = pkt.wandNbt.contains("scannedBlocks") ? "scannedBlocks" : CopyToolItem.TAG_SCANNED;
            if (pkt.wandNbt.contains(key)) {
                ListTag list = pkt.wandNbt.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    classified.add(ScannedBlock.fromNBT(list.getCompound(i)));
                }
            }

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

            // Export
            DefinitionExporter.ExportResult result = DefinitionExporter.export(player.getServer(), def, pkt.exportJava);

            if (result.success) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "[Designer] Saved definition '%s' (id: %s, %d blocks) and exported to: %s"
                                        .formatted(def.displayName, def.id, def.blocks.size(), result.jsPath))
                );
            } else {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "[Designer] Saved definition but export failed: " + result.error)
                );
            }

            // Clear the wand/tool scan state so the player can start fresh
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof DesignerWandItem) {
                    DesignerWandItem.clearScanPublic(stack);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    break;
                } else if (stack.getItem() instanceof CopyToolItem) {
                    stack.getOrCreateTag().remove(CopyToolItem.NBT_POS1);
                    stack.getOrCreateTag().remove(CopyToolItem.NBT_POS2);
                    stack.getOrCreateTag().remove(CopyToolItem.TAG_SCANNED);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    break;
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}