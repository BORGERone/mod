package net.minecraft.nbt;

import java.util.ArrayList;

public class ListTag extends ArrayList<CompoundTag> {
    public CompoundTag getCompound(int index) { return get(index); }
}
