package com.bgame.multiblockdesigner.definition;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

// A block in a MultiblockDefinition, with position relative to the controller and a role
public class RelativeBlock {

    public final BlockPos relPos;   // offset from controller
    public final BlockState state;
    public final BlockRole role;
    public final java.util.List<String> abilities = new java.util.ArrayList<>();

    public RelativeBlock(BlockPos relPos, BlockState state, BlockRole role) {
        this.relPos  = relPos;
        this.state   = state;
        this.role    = role;
    }

    public RelativeBlock(BlockPos relPos, BlockState state, BlockRole role, java.util.List<String> abilities) {
        this.relPos  = relPos;
        this.state   = state;
        this.role    = role;
        this.abilities.addAll(abilities);
    }

    // NBT
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rx", relPos.getX());
        tag.putInt("ry", relPos.getY());
        tag.putInt("rz", relPos.getZ());
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        tag.putString("block", key != null ? key.toString() : "minecraft:air");
        tag.putString("role", role.name());

        if (!abilities.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (String a : abilities) list.add(net.minecraft.nbt.StringTag.valueOf(a));
            tag.put("abilities", list);
        }

        return tag;
    }

    public static RelativeBlock fromNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("rx"), tag.getInt("ry"), tag.getInt("rz"));
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("block"));
        var block = id != null ? ForgeRegistries.BLOCKS.getValue(id) : null;
        BlockState state = block != null
            ? block.defaultBlockState()
            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockRole role;
        try { role = BlockRole.valueOf(tag.getString("role")); }
        catch (IllegalArgumentException e) { role = BlockRole.UNKNOWN; }

        RelativeBlock rb = new RelativeBlock(pos, state, role);
        if (tag.contains("abilities")) {
            net.minecraft.nbt.ListTag list = tag.getList("abilities", 8);
            for (int i = 0; i < list.size(); i++) {
                rb.abilities.add(list.getString(i));
            }
        }
        return rb;
    }
}