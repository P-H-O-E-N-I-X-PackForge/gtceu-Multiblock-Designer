package com.bgame.multiblockdesigner.definition;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MultiblockDefinition {

    public final String id;
    public final String displayName;
    public final List<RelativeBlock> blocks;

    public final BlockPos minRel;
    public final BlockPos maxRel;

    private MultiblockDefinition(String id, String displayName, List<RelativeBlock> blocks,
                                  BlockPos minRel, BlockPos maxRel) {
        this.id          = id;
        this.displayName = displayName;
        this.blocks      = blocks;
        this.minRel      = minRel;
        this.maxRel      = maxRel;
    }

    // Factory method to create a MultiblockDefinition from a list of classified scanned blocks.
    public static MultiblockDefinition fromClassified(List<ScannedBlock> classified, String displayName) {
        // Find the controller
        ScannedBlock controllerBlock = classified.stream()
            .filter(b -> b.role == BlockRole.CONTROLLER)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No CONTROLLER block found in classified list"));

        BlockPos origin = controllerBlock.pos;

        // Normalize all positions relative to controller
        List<RelativeBlock> relBlocks = new ArrayList<>();
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 0, maxY = 0, maxZ = 0;

        for (ScannedBlock sb : classified) {
            if (sb.state.isAir()) continue;

            BlockPos rel = sb.pos.subtract(origin);
            relBlocks.add(new RelativeBlock(rel, sb.state, sb.role));

            minX = Math.min(minX, rel.getX()); minY = Math.min(minY, rel.getY()); minZ = Math.min(minZ, rel.getZ());
            maxX = Math.max(maxX, rel.getX()); maxY = Math.max(maxY, rel.getY()); maxZ = Math.max(maxZ, rel.getZ());
        }

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new MultiblockDefinition(id, displayName,
            relBlocks, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    // Queries
    public List<RelativeBlock> getByRole(BlockRole role) {
        return blocks.stream().filter(b -> b.role == role).collect(Collectors.toList());
    }

    public boolean hasRole(BlockRole role) {
        return blocks.stream().anyMatch(b -> b.role == role);
    }

    // Width (X), Height (Y), Depth (Z) of the structure.
    public BlockPos size() {
        return new BlockPos(
            maxRel.getX() - minRel.getX() + 1,
            maxRel.getY() - minRel.getY() + 1,
            maxRel.getZ() - minRel.getZ() + 1
        );
    }

    // NBT — for SavedData world persistence
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id",   id);
        tag.putString("name", displayName);

        ListTag list = new ListTag();
        for (RelativeBlock rb : blocks) list.add(rb.toNBT());
        tag.put("blocks", list);

        return tag;
    }

    public static MultiblockDefinition fromNBT(CompoundTag tag) {
        String id          = tag.getString("id");
        String displayName = tag.getString("name");

        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        List<RelativeBlock> blocks = new ArrayList<>();

        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        boolean first = true;

        for (int i = 0; i < list.size(); i++) {
            RelativeBlock rb = RelativeBlock.fromNBT(list.getCompound(i));
            blocks.add(rb);

            int x = rb.relPos.getX(), y = rb.relPos.getY(), z = rb.relPos.getZ();
            if (first) {
                minX = maxX = x; minY = maxY = y; minZ = maxZ = z;
                first = false;
            } else {
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
        }

        return new MultiblockDefinition(id, displayName, blocks,
            new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }


    @Override
    public String toString() {
        return "MultiblockDefinition{id='%s', name='%s', blocks=%d, size=%s}"
            .formatted(id, displayName, blocks.size(), size());
    }
}