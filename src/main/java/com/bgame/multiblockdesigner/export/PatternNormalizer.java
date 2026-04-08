package com.bgame.multiblockdesigner.export;

import com.bgame.multiblockdesigner.definition.BlockRole;
import com.bgame.multiblockdesigner.definition.MultiblockDefinition;
import com.bgame.multiblockdesigner.definition.RelativeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class PatternNormalizer {

    // Result of normalization — everything needed to generate the export
    public static class NormalizedPattern {
        public final String[][] aisles;
        public final Map<Character, List<RelativeBlock>> keyMap;
        public final Map<Character, BlockRole> keyRoles;
        // Bounding box
        public final int sizeX, sizeY, sizeZ;
        public final int minX, minY, minZ;

        NormalizedPattern(String[][] aisles, Map<Character, List<RelativeBlock>> keyMap,
                          Map<Character, BlockRole> keyRoles,
                          int sizeX, int sizeY, int sizeZ,
                          int minX, int minY, int minZ) {
            this.aisles   = aisles;
            this.keyMap   = keyMap;
            this.keyRoles = keyRoles;
            this.sizeX    = sizeX;
            this.sizeY    = sizeY;
            this.sizeZ    = sizeZ;
            this.minX     = minX;
            this.minY     = minY;
            this.minZ     = minZ;
        }
    }

    // Char pool for block types (skip C which is reserved for controller)
    private static final String CHAR_POOL = "ABDEFGHIJKLMNOPQRSTUVWXYZ0123456789abdefghijklmnopqrstuvwxyz";

    public static NormalizedPattern normalize(MultiblockDefinition def) {
        List<RelativeBlock> blocks = def.blocks;

        // Find bounding box
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 0, maxY = 0, maxZ = 0;
        for (RelativeBlock rb : blocks) {
            minX = Math.min(minX, rb.relPos.getX()); minY = Math.min(minY, rb.relPos.getY()); minZ = Math.min(minZ, rb.relPos.getZ());
            maxX = Math.max(maxX, rb.relPos.getX()); maxY = Math.max(maxY, rb.relPos.getY()); maxZ = Math.max(maxZ, rb.relPos.getZ());
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        // Build spatial lookup
        Map<BlockPos, RelativeBlock> byPos = new HashMap<>();
        for (RelativeBlock rb : blocks) byPos.put(rb.relPos, rb);

        // Controller always gets 'C', air gets ' '
        Map<String, Character> pairToChar = new LinkedHashMap<>();
        Map<Character, List<RelativeBlock>> keyMap  = new LinkedHashMap<>();
        Map<Character, BlockRole>           keyRoles = new LinkedHashMap<>();

        pairToChar.put("CONTROLLER", 'C');
        keyMap.put('C', new ArrayList<>());
        keyRoles.put('C', BlockRole.CONTROLLER);

        int charIdx = 0;

        for (RelativeBlock rb : blocks) {
            if (rb.role == BlockRole.CONTROLLER) {
                keyMap.get('C').add(rb);
                continue;
            }

            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(rb.state.getBlock());
            String pairKey = (blockId != null ? blockId.toString() : "unknown") + "|" + rb.role.name();

            if (!pairToChar.containsKey(pairKey)) {
                // Find next available char
                char ch;
                do {
                    if (charIdx >= CHAR_POOL.length())
                        throw new IllegalStateException("Too many unique block types in definition (max " + CHAR_POOL.length() + ")");
                    ch = CHAR_POOL.charAt(charIdx++);
                } while (ch == 'C');

                pairToChar.put(pairKey, ch);
                keyMap.put(ch, new ArrayList<>());
                keyRoles.put(ch, rb.role);
            }

            char ch = pairToChar.get(pairKey);
            keyMap.get(ch).add(rb);
        }

        // Build aisles[y][z] = String (chars along X)
        String[][] aisles = new String[sizeY][sizeZ];

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < sizeX; x++) {
                    BlockPos rel = new BlockPos(minX + x, minY + y, minZ + z);
                    RelativeBlock rb = byPos.get(rel);
                    if (rb == null) {
                        row.append(' ');
                    } else if (rb.role == BlockRole.CONTROLLER) {
                        row.append('C');
                    } else {
                        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(rb.state.getBlock());
                        String pairKey = (blockId != null ? blockId.toString() : "unknown") + "|" + rb.role.name();
                        row.append(pairToChar.get(pairKey));
                    }
                }
                aisles[y][z] = row.toString();
            }
        }

        return new NormalizedPattern(aisles, keyMap, keyRoles, sizeX, sizeY, sizeZ, minX, minY, minZ);
    }
}