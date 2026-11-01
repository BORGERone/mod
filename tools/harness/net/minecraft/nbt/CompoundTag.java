package net.minecraft.nbt;

/** Harness stub: records nothing, accepts everything the model writes. */
public class CompoundTag implements Tag {
    public CompoundTag() {
    }

    public boolean contains(String key) { return false; }
    public boolean contains(String key, int type) { return false; }
    public int getInt(String key) { return 0; }
    public long getLong(String key) { return 0L; }
    public double getDouble(String key) { return 0.0D; }
    public float getFloat(String key) { return 0.0F; }
    public byte getByte(String key) { return 0; }
    public String getString(String key) { return ""; }
    public CompoundTag getCompound(String key) { return new CompoundTag(); }
    public ListTag getList(String key, int type) { return new ListTag(); }
    public java.util.Set<String> getAllKeys() { return new java.util.LinkedHashSet<>(); }
    public byte getTagType() { return 10; }
    public void putBoolean(String key, boolean value) { }
    public void putByte(String key, byte value) { }
    public void putInt(String key, int value) { }
    public void putLong(String key, long value) { }
    public void putFloat(String key, float value) { }
    public void putDouble(String key, double value) { }
    public void putString(String key, String value) { }
    public Tag put(String key, Tag value) { return value; }
    public void putIntArray(String key, int[] value) { }
    public void putLongArray(String key, long[] value) { }
}
