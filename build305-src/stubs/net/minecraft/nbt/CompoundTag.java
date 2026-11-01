package net.minecraft.nbt;

/** Compile-time signature only; never bundled into the mod JAR. */
public class CompoundTag implements Tag {
    public CompoundTag() {
    }

    public boolean contains(String key) {
        return false;
    }

    public int getInt(String key) {
        return 0;
    }

    public String getString(String key) {
        return "";
    }

    public void putBoolean(String key, boolean value) {
    }

    public void putInt(String key, int value) {
    }

    public void putString(String key, String value) {
    }

    public Tag put(String key, Tag value) {
        return value;
    }
}
