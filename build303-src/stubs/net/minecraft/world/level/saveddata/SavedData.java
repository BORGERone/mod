package net.minecraft.world.level.saveddata;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;

/** Compile-time signature only; the shape mirrors 1.21.1 and the calls CutsceneStorage already makes. */
public abstract class SavedData {
    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    public void setDirty() {
    }

    public boolean isDirty() {
        return false;
    }

    public static class Factory<T extends SavedData> {
        public Factory(Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
                DataFixTypes type) {
        }
    }
}
