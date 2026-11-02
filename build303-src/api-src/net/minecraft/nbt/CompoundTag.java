package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/**
 * Compile-time mirror. Build 334 corrected two members to the real descriptors of 1.21.1, which the
 * base jar already calls and the old mirror got wrong: {@code put(String,Tag)} returns the value it
 * stored, and {@code remove(String)} returns nothing. Compiling against the old shapes produced
 * {@code put(String,Object)V} / {@code remove(String)Tag} in our bytecode - a NoSuchMethodError
 * waiting for the first call in game.
 */
public class CompoundTag implements Tag {
    private final Map<String, Object> values = new HashMap<>();
    public boolean contains(String key) { return values.containsKey(key); }
    public boolean contains(String key, int type) { return values.containsKey(key); }
    public ListTag getList(String key, int elementType) {
        Object value = values.get(key);
        return value instanceof ListTag list ? list : new ListTag();
    }
    public int getInt(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
    public CompoundTag getCompound(String key) {
        Object value = values.get(key);
        return value instanceof CompoundTag tag ? tag : new CompoundTag();
    }
    public String getString(String key) { Object value = values.get(key); return value instanceof String text ? text : ""; }
    public void putString(String key, String value) { values.put(key, value); }
    /** Real descriptor: {@code void remove(String)}. */
    public void remove(String key) { values.remove(key); }
    public boolean getBoolean(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public void putBoolean(String key, boolean value) { values.put(key, value); }
    public void putInt(String key, int value) { values.put(key, value); }
    public void putFloat(String key, float value) { values.put(key, value); }
    public double getDouble(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }
    public float getFloat(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }
    public void putDouble(String key, double value) { values.put(key, value); }
    /** Real descriptor: {@code Tag put(String, Tag)} - it returns what it stored. */
    public Tag put(String key, Tag value) { values.put(key, value); return value; }
}
