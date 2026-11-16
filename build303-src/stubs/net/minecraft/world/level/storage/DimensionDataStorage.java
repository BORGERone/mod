package net.minecraft.world.level.storage;

import net.minecraft.world.level.saveddata.SavedData;

/** Compile-time mirror of the one call the base jar makes: computeIfAbsent(factory, name). */
public class DimensionDataStorage {
    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
        return null;
    }
}
