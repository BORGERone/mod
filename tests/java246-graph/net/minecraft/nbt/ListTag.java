package net.minecraft.nbt;
import java.util.ArrayList;
public class ListTag extends ArrayList<Tag> implements Tag {public CompoundTag getCompound(int i){return (CompoundTag)get(i);}}
