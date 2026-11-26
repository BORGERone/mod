package dev.settledlands.trial;

import dev.settledlands.npc.BlockTriggerStore351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import java.util.ArrayList;
import java.util.Collections;
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
 * Build 446: persistent world storage for {@code trialtuner:trial_spawner} graphs
 * ({@code settledlands_trial_graphs.dat} in overworld data storage).
 */
public final class TrialGraph446Store extends SavedData {

    public static final int MAX_SPAWNERS = 4096;

    private static final String FILE = "settledlands_trial_graphs";
    private static final String SPAWNERS = "Spawners";
    private static final String KEY = "K";
    private static final String GRAPH = "G";

    public static final class Entry implements RadiusPass354.Spot {
        public final String key;
        public final String dimension;
        public final int x;
        public final int y;
        public final int z;
        public NpcDialogueGraph graph;
        public boolean check;
        public boolean ring;

        Entry(String key, String dimension, int x, int y, int z, NpcDialogueGraph graph) {
            this.key = key;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.graph = graph;
            this.check = RadiusPass354.hasFiringEvent(graph);
            this.ring = RadiusPass354.hasVisibleRadius(graph);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int z() {
            return z;
        }

        @Override
        public NpcDialogueGraph graph() {
            return graph;
        }

        @Override
        public boolean check() {
            return check;
        }

        @Override
        public boolean ring() {
            return ring;
        }
    }

    private final Map<String, Entry> byKey = new LinkedHashMap<>();
    private final Map<String, List<Entry>> byDimension = new HashMap<>();

    public static TrialGraph446Store get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    public static TrialGraph446Store get(ServerLevel level) {
        if (level == null) return null;
        return get(level.getServer());
    }

    private static SavedData.Factory<TrialGraph446Store> factory() {
        return new SavedData.Factory<>(TrialGraph446Store::new, TrialGraph446Store::load,
                DataFixTypes.LEVEL);
    }

    private static TrialGraph446Store load(CompoundTag tag, HolderLookup.Provider registries) {
        TrialGraph446Store data = new TrialGraph446Store();
        if (!tag.contains(SPAWNERS, 9)) return data;
        ListTag list = tag.getList(SPAWNERS, 10);
        for (int i = 0; i < list.size() && data.byKey.size() < MAX_SPAWNERS; i++) {
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
        for (Entry entry : byKey.values()) {
            if (entry == null || entry.graph == null) continue;
            CompoundTag one = new CompoundTag();
            one.putString(KEY, entry.key);
            one.put(GRAPH, entry.graph.save());
            list.add(one);
        }
        tag.put(SPAWNERS, list);
        return tag;
    }

    public static String key(ServerLevel level, BlockPos pos) {
        return BlockTriggerStore351.key(level, pos);
    }

    public static String dimensionOf(String key) {
        if (key == null) return "";
        int at = key.indexOf('@');
        return at < 0 ? "" : key.substring(0, at);
    }

    public static BlockPos positionOf(String key) {
        if (key == null) return null;
        int at = key.indexOf('@');
        if (at < 0) return null;
        String[] parts = key.substring(at + 1).split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public NpcDialogueGraph graph(String key) {
        if (key == null || key.isEmpty()) return null;
        Entry entry = byKey.get(key);
        return entry == null ? null : entry.graph;
    }

    public boolean contains(String key) {
        return key != null && !key.isEmpty() && byKey.containsKey(key);
    }

    public int size() {
        return byKey.size();
    }

    public List<Entry> ofDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) return Collections.emptyList();
        List<Entry> list = byDimension.get(dimension);
        return list == null ? Collections.emptyList() : list;
    }

    public List<String> keysOfDimension(String dimension) {
        List<String> out = new ArrayList<>();
        for (Entry e : ofDimension(dimension)) {
            if (e != null && e.key != null) out.add(e.key);
        }
        return out;
    }

    public boolean put(String key, NpcDialogueGraph graph) {
        if (key == null || key.isEmpty()) return false;
        if (graph == null) {
            boolean removed = removeInternal(key);
            if (removed) setDirty();
            return true;
        }
        if (!byKey.containsKey(key) && byKey.size() >= MAX_SPAWNERS) return false;
        remember(key, graph);
        setDirty();
        return true;
    }

    public boolean remove(String key) {
        if (key == null || key.isEmpty()) return false;
        boolean removed = removeInternal(key);
        if (removed) setDirty();
        return removed;
    }

    private void remember(String key, NpcDialogueGraph graph) {
        BlockPos pos = positionOf(key);
        if (pos == null) return;
        String dim = dimensionOf(key);
        removeInternal(key);
        Entry entry = new Entry(key, dim, pos.getX(), pos.getY(), pos.getZ(), graph);
        byKey.put(key, entry);
        byDimension.computeIfAbsent(dim, k -> new ArrayList<>()).add(entry);
    }

    private boolean removeInternal(String key) {
        Entry prev = byKey.remove(key);
        if (prev == null) return false;
        List<Entry> list = byDimension.get(prev.dimension);
        if (list != null) {
            list.remove(prev);
            if (list.isEmpty()) byDimension.remove(prev.dimension);
        }
        return true;
    }
}
