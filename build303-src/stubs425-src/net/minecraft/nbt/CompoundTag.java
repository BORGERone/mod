package net.minecraft.nbt;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compile-time mirror (Build 425): preserves all {@code api-src} {@code CompoundTag} methods and
 * adds {@link #getAllKeys()}, {@link #copy()}, and {@link #getLong(String)}/{@link #putLong(String, long)}
 * with full working in-memory behavior for JVM harness tests.
 */
public class CompoundTag implements Tag {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public boolean isEmpty() { return values.isEmpty(); }
    public boolean contains(String key) { return values.containsKey(key); }
    public boolean contains(String key, int type) { return values.containsKey(key); }
    public Set<String> getAllKeys() { return values.keySet(); }

    public ListTag getList(String key, int elementType) {
        Object value = values.get(key);
        return value instanceof ListTag list ? list : new ListTag();
    }

    public int getInt(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public long getLong(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public CompoundTag getCompound(String key) {
        Object value = values.get(key);
        return value instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    public String getString(String key) {
        Object value = values.get(key);
        return value instanceof String text ? text : "";
    }

    public void putString(String key, String value) { values.put(key, value); }
    public void remove(String key) { values.remove(key); }

    public boolean getBoolean(String key) {
        Object v = values.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.byteValue() != 0;
        return false;
    }

    public void putBoolean(String key, boolean value) { values.put(key, value); }
    public void putByte(String key, byte value) { values.put(key, value); }
    public void putShort(String key, short value) { values.put(key, value); }
    public void putInt(String key, int value) { values.put(key, value); }
    public void putLong(String key, long value) { values.put(key, value); }
    public void putIntArray(String key, int[] value) { values.put(key, value); }
    public int[] getIntArray(String key) { Object v = values.get(key); return v instanceof int[] a ? a : new int[0]; }
    public void putByteArray(String key, byte[] value) { values.put(key, value); }
    public void putLongArray(String key, long[] value) { values.put(key, value); }
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

    public void putUUID(String key, UUID value) { values.put(key, value); }
    public UUID getUUID(String key) { Object v = values.get(key); return v instanceof UUID u ? u : new UUID(0L, 0L); }

    public Tag put(String key, Tag value) {
        values.put(key, value);
        return value;
    }

    public CompoundTag copy() {
        CompoundTag clone = new CompoundTag();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            clone.values.put(e.getKey(), copyVal(e.getValue()));
        }
        return clone;
    }

    private static Object copyVal(Object v) {
        if (v instanceof CompoundTag ct) return ct.copy();
        if (v instanceof ListTag lt) {
            ListTag nl = new ListTag();
            for (int i = 0; i < lt.size(); i++) {
                CompoundTag item = lt.get(i);
                nl.add(item == null ? null : item.copy());
            }
            return nl;
        }
        if (v instanceof int[] a) return a.clone();
        if (v instanceof byte[] a) return a.clone();
        if (v instanceof long[] a) return a.clone();
        return v;
    }
}
