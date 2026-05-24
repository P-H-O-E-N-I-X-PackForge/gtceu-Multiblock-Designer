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
    public final java.util.List<String> abilities = new java.util.ArrayList<>();
    /**
     * When true, the exporter always emits Predicates.blocks(...) for this block
     * even if its role would normally produce Predicates.abilities(...).
     * Useful when a modpack requires a specific hatch tier rather than accepting any.
     */
    public boolean pinSpecific = false;

    public ScannedBlock(BlockPos pos, BlockState state) {
        this.pos   = pos;
        this.state = state;
        this.role  = BlockRole.UNKNOWN;
    }

    public ScannedBlock(BlockPos pos, BlockState state, BlockRole role) {
        this.pos   = pos;
        this.state = state;
        this.role  = role;
    }

    public static ScannedBlock deserializeNBT(CompoundTag tag) {
        return fromNBT(tag);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        tag.putString("block", blockId != null ? blockId.toString() : "minecraft:air");

        tag.putString("role", role.name());

        if (!abilities.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (String a : abilities) list.add(net.minecraft.nbt.StringTag.valueOf(a));
            tag.put("abilities", list);
        }

        if (pinSpecific) tag.putBoolean("pinSpecific", true);

        return tag;
    }

    public static ScannedBlock fromNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));

        ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("block"));
        var block = ForgeRegistries.BLOCKS.getValue(blockId);
        BlockState state = block != null
                ? block.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        BlockRole role;
        try { role = BlockRole.valueOf(tag.getString("role")); }
        catch (IllegalArgumentException e) { role = BlockRole.UNKNOWN; }

        ScannedBlock sb = new ScannedBlock(pos, state, role);

        if (tag.contains("abilities")) {
            net.minecraft.nbt.ListTag list = tag.getList("abilities", 8);
            for (int i = 0; i < list.size(); i++) sb.abilities.add(list.getString(i));
        }

        if (tag.contains("pinSpecific")) sb.pinSpecific = tag.getBoolean("pinSpecific");

        return sb;
    }

    /** Convenience — mirrors the ability toggle used in the GUI. */
    public void setAbility(String ability, boolean enabled) {
        if (enabled) { if (!abilities.contains(ability)) abilities.add(ability); }
        else abilities.remove(ability);
    }
}