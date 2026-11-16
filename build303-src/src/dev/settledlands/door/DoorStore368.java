package dev.settledlands.door;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Build 368: the doors the author locked, kept in world data.
 *
 * <p>A lock belongs to a place, not to an item and not to a chunk that may be unloaded, so it is
 * stored the same way every other piece of this mod's world data is (see
 * {@code BlockTriggerStore351}): one {@code SavedData} file in the overworld, keyed by the dimension
 * and the block position - {@code minecraft:overworld@12,64,-30}. A door is two blocks, and the upper
 * half answers the same as the lower one, so both halves are written and both are read: a player who
 * clicks the top half must be stopped exactly like the one who clicks the bottom half.</p>
 *
 * <p>Nothing here knows about players or mobs - this is only "is this place locked". The two rules
 * that use it are {@link DoorEvents368} (who may open and who may break) and {@link NpcDoors368}
 * (who may always pass).</p>
 */
public final class DoorStore368 extends SavedData {

    /** More locked doors than this and the file is a mistake, not a build. */
    public static final int MAX_LOCKS = 8192;

    private static final String FILE = "settledlands_doors";
    private static final String LOCKS = "Locks";

    private final Set<String> locked = new HashSet<>();

    public static DoorStore368 get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    public static DoorStore368 get(ServerLevel level) {
        if (level == null) return null;
        MinecraftServer server = level.getServer();
        return server == null ? null : get(server);
    }

    private static SavedData.Factory<DoorStore368> factory() {
        return new SavedData.Factory<>(DoorStore368::new, DoorStore368::load, DataFixTypes.LEVEL);
    }

    private static DoorStore368 load(CompoundTag tag, HolderLookup.Provider registries) {
        DoorStore368 data = new DoorStore368();
        data.locked.addAll(decode(tag.getString(LOCKS)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(LOCKS, encode(locked));
        return tag;
    }

    /**
     * The locks are written as one string, one key per line. A list of tags would say the same thing in
     * four times the bytes, and a key is plain ASCII - dimension, at, three numbers. Encoding and
     * decoding are static and pure, so the format can be checked without a world.
     */
    public static String encode(java.util.Collection<String> keys) {
        StringBuilder all = new StringBuilder();
        if (keys == null) return "";
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            if (all.length() > 0) all.append('\n');
            all.append(key);
        }
        return all.toString();
    }

    public static Set<String> decode(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isEmpty()) return out;
        String[] parts = text.split("\n");
        for (int i = 0; i < parts.length && out.size() < MAX_LOCKS; i++) {
            String key = parts[i].trim();
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }

    /** {@code minecraft:overworld@12,64,-30}. */
    public static String key(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        String dimension = level.dimension() == null || level.dimension().location() == null
                ? "?" : String.valueOf(level.dimension().location());
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public boolean isLocked(ServerLevel level, BlockPos pos) {
        String key = key(level, pos);
        return !key.isEmpty() && locked.contains(key);
    }

    /**
     * Locks the place the click landed on. Both halves of a door are written, so the answer does not
     * depend on which half the player manages to hit.
     */
    public boolean lock(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        for (BlockPos part : Doors368.parts(level, pos)) {
            String key = key(level, part);
            if (key.isEmpty() || locked.size() >= MAX_LOCKS) continue;
            changed |= locked.add(key);
        }
        if (changed) setDirty();
        return changed;
    }

    public boolean unlock(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        for (BlockPos part : Doors368.parts(level, pos)) {
            changed |= locked.remove(key(level, part));
        }
        if (changed) setDirty();
        return changed;
    }

    /** How many places are locked - a probe and a log line, not a game rule. */
    public int size() {
        return locked.size();
    }

    /**
     * Build 371: the locks as they stand, as a copy.
     *
     * <p>{@link DoorGuard371} walks this list a slice at a time to put a locked door back the way it
     * must be if anything at all manages to open it. A copy, because the guard must never be able to
     * trip over a click that unlocks a door in the middle of its walk.</p>
     */
    public java.util.Set<String> snapshot() {
        return new java.util.HashSet<>(locked);
    }

    /** The dimension part of a key, {@code minecraft:overworld} in {@code minecraft:overworld@1,2,3}. */
    public static String dimensionOf(String key) {
        if (key == null) return "";
        int at = key.indexOf('@');
        return at < 0 ? "" : key.substring(0, at);
    }

    /** The same name {@link #key(ServerLevel, BlockPos)} writes, so the two can be compared. */
    public static String dimensionOf(ServerLevel level) {
        if (level == null || level.dimension() == null || level.dimension().location() == null) {
            return "?";
        }
        return String.valueOf(level.dimension().location());
    }

    /** The position part of a key, or null when the key is not one of ours. */
    public static BlockPos positionOf(String key) {
        if (key == null) return null;
        int at = key.indexOf('@');
        if (at < 0) return null;
        String[] parts = key.substring(at + 1).split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
