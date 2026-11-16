package dev.settledlands.chest;

import dev.settledlands.npc.NpcDialogueGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Build 396: the graphs of the chests, kept in the world - one entry per chest.
 *
 * <p>Written exactly the way {@code BlockTriggerStore351} and {@code DoorGraph382Store} are written: one
 * {@code SavedData} file in the overworld, one entry per place, keyed by the dimension and the block
 * position - {@code minecraft:overworld@12,64,-30}. A chest is one block, so its key is its own position;
 * the two halves of a double chest are two entries, each with its own graph, and the card of each says
 * where it is.</p>
 */
public final class ChestGraph396Store extends SavedData {

    /** More chests with a graph than this and the file is a mistake, not a build. */
    public static final int MAX_CHESTS = 4096;

    private static final String FILE = "settledlands_chest_graphs";
    private static final String CHESTS = "Chests";
    private static final String KEY = "K";
    private static final String GRAPH = "G";

    private final Map<String, NpcDialogueGraph> byKey = new LinkedHashMap<String, NpcDialogueGraph>();
    private final Map<String, String> byDimension = new HashMap<String, String>();

    public static ChestGraph396Store get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    public static ChestGraph396Store get(ServerLevel level) {
        if (level == null) return null;
        MinecraftServer server = level.getServer();
        return server == null ? null : get(server);
    }

    private static SavedData.Factory<ChestGraph396Store> factory() {
        return new SavedData.Factory<>(ChestGraph396Store::new, ChestGraph396Store::load,
                DataFixTypes.LEVEL);
    }

    private static ChestGraph396Store load(CompoundTag tag, HolderLookup.Provider registries) {
        ChestGraph396Store data = new ChestGraph396Store();
        if (!tag.contains(CHESTS, 9)) return data;
        ListTag list = tag.getList(CHESTS, 10);
        for (int i = 0; i < list.size() && data.byKey.size() < MAX_CHESTS; i++) {
            CompoundTag one = list.getCompound(i);
            String key = one.getString(KEY);
            if (key.isEmpty() || !one.contains(GRAPH, 10)) continue;
            data.remember(key, NpcDialogueGraph.load(one.getCompound(GRAPH)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, NpcDialogueGraph> entry : byKey.entrySet()) {
            if (entry.getValue() == null) continue;
            CompoundTag one = new CompoundTag();
            one.putString(KEY, entry.getKey());
            one.put(GRAPH, entry.getValue().save());
            list.add(one);
        }
        tag.put(CHESTS, list);
        return tag;
    }

    /** The graph of that chest, or null while the chest has none. */
    public NpcDialogueGraph graph(String key) {
        return key == null || key.isEmpty() ? null : byKey.get(key);
    }

    public int size() {
        return byKey.size();
    }

    public boolean full() {
        return byKey.size() >= MAX_CHESTS;
    }

    /** Every key this store knows, sorted. */
    public List<String> keys() {
        List<String> keys = new ArrayList<String>(byKey.keySet());
        java.util.Collections.sort(keys);
        return keys;
    }

    /** Build 396: the keys of the chests of one dimension - what the event pass walks. */
    public List<String> ofDimension(String dimension) {
        List<String> out = new ArrayList<String>();
        if (dimension == null || dimension.isEmpty()) return out;
        for (Map.Entry<String, NpcDialogueGraph> entry : byKey.entrySet()) {
            if (entry.getValue() == null || entry.getKey() == null) continue;
            if (!dimension.equals(byDimension.get(entry.getKey()))) continue;
            out.add(entry.getKey());
        }
        return out;
    }

    /**
     * Writes the graph of one chest. False means the store is full and a new chest was refused; an
     * existing chest can always be rewritten. A null graph forgets the chest.
     */
    public boolean put(String key, NpcDialogueGraph graph) {
        if (key == null || key.isEmpty()) return false;
        if (graph == null) {
            if (byKey.remove(key) != null) setDirty();
            return true;
        }
        if (!byKey.containsKey(key) && full()) return false;
        remember(key, graph);
        setDirty();
        return true;
    }

    private void remember(String key, NpcDialogueGraph graph) {
        byKey.put(key, graph);
        int at = key.lastIndexOf('@');
        byDimension.put(key, at < 0 ? "?" : key.substring(0, at));
    }

    /** The key of the chest at this position - the string the store, the editor and the log all use. */
    public static String key(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        if (level.dimension() == null || level.dimension().location() == null) return "";
        return String.valueOf(level.dimension().location()) + "@"
                + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** The dimension part of a key, {@code minecraft:overworld} in {@code minecraft:overworld@1,2,3}. */
    public static String dimensionOf(String key) {
        if (key == null) return "";
        int at = key.indexOf('@');
        return at < 0 ? "" : key.substring(0, at);
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
