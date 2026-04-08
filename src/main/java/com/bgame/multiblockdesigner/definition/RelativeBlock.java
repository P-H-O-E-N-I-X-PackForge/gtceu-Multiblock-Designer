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

    public RelativeBlock(BlockPos relPos, BlockState state, BlockRole role) {
        this.relPos  = relPos;
        this.state   = state;
        this.role    = role;
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
        return new RelativeBlock(pos, state, role);
    }
}