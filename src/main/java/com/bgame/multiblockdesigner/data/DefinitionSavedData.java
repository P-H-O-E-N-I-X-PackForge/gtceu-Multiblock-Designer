package com.bgame.multiblockdesigner.data;

import com.bgame.multiblockdesigner.MultiblockDesignerMod;
import com.bgame.multiblockdesigner.definition.MultiblockDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class DefinitionSavedData extends SavedData {

    private static final String DATA_KEY = MultiblockDesignerMod.MOD_ID + "_definitions";

    private final Map<String, MultiblockDefinition> definitions = new LinkedHashMap<>();

    // Access
    // Gets or creates the SavedData instance for the given server level
    public static DefinitionSavedData get(ServerLevel level) {
        return level.getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(DefinitionSavedData::load, DefinitionSavedData::new, DATA_KEY);
    }

    // CRUD
    public void save(MultiblockDefinition def) {
        definitions.put(def.id, def);
        setDirty();
    }

    public Optional<MultiblockDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public void remove(String id) {
        if (definitions.remove(id) != null) setDirty();
    }

    public Collection<MultiblockDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int count() {
        return definitions.size();
    }

    // NBT serialization
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MultiblockDefinition def : definitions.values()) {
            list.add(def.toNBT());
        }
        tag.put("definitions", list);
        return tag;
    }

    public static DefinitionSavedData load(CompoundTag tag) {
        DefinitionSavedData data = new DefinitionSavedData();
        ListTag list = tag.getList("definitions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MultiblockDefinition def = MultiblockDefinition.fromNBT(list.getCompound(i));
            data.definitions.put(def.id, def);
        }
        return data;
    }
}