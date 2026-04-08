package com.bgame.multiblockdesigner.gui;

import com.bgame.multiblockdesigner.definition.BlockRole;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * All ScannedBlocks that share the same Block type, grouped together.
 * Role is assigned at the group level — all instances get the same role.
 */
public class BlockTypeGroup {

    public final BlockState representativeState;
    public final List<ScannedBlock> blocks = new ArrayList<>();
    public BlockRole role = BlockRole.UNKNOWN;

    public boolean expanded = false;

    public BlockTypeGroup(BlockState state) {
        this.representativeState = state;
    }

    public void add(ScannedBlock block) {
        blocks.add(block);
    }

    // Applies this group's role to all member ScannedBlocks
    public void applyRole(BlockRole newRole) {
        this.role = newRole;
        for (ScannedBlock b : blocks) {
            b.role = newRole;
        }
    }

    public int count() {
        return blocks.size();
    }

    /**
     * Builds a grouped list from a flat ScannedBlock list.
     * Groups by Block identity (ignores blockstate properties like facing).
     */
    public static List<BlockTypeGroup> groupFrom(List<ScannedBlock> scanned) {
        List<BlockTypeGroup> groups = new ArrayList<>();

        for (ScannedBlock sb : scanned) {
            BlockTypeGroup found = null;
            for (BlockTypeGroup g : groups) {
                if (g.representativeState.getBlock() == sb.state.getBlock()) {
                    found = g;
                    break;
                }
            }
            if (found == null) {
                found = new BlockTypeGroup(sb.state);
                groups.add(found);
            }
            found.add(sb);
        }

        return groups;
    }
}