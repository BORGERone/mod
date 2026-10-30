package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

public class CompoundTag {
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
    public boolean getBoolean(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public void putBoolean(String key, boolean value) { values.put(key, value); }
    public void putInt(String key, int value) { values.put(key, value); }
    public void put(String key, Object value) { values.put(key, value); }
}
