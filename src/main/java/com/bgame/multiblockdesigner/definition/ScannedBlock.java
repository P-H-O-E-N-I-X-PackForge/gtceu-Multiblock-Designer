package com.bgame.multiblockdesigner.definition;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

public class ScannedBlock {

    public final BlockPos pos;
    public final BlockState state;
    public BlockRole role;

    public ScannedBlock(BlockPos pos, BlockState state) {
        this.pos = pos;
        this.state = state;
        this.role = BlockRole.UNKNOWN;
    }

    public ScannedBlock(BlockPos pos, BlockState state, BlockRole role) {
        this.pos = pos;
        this.state = state;
        this.role = role;
    }

    // NBT serialization — stored in the wand's ItemStack tag during workflow
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        tag.putString("block", blockId != null ? blockId.toString() : "minecraft:air");

        tag.putString("role", role.name());
        return tag;
    }

    public static ScannedBlock fromNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));

        ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("block"));
        var block = ForgeRegistries.BLOCKS.getValue(blockId);
        BlockState state = block != null ? block.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        BlockRole role;
        try {
            role = BlockRole.valueOf(tag.getString("role"));
        } catch (IllegalArgumentException e) {
            role = BlockRole.UNKNOWN;
        }

        return new ScannedBlock(pos, state, role);
    }
}