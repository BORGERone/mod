package dev.settledlands.npc;

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
 * Build 351/352: the graphs of the blocks, kept in the world instead of in an entity.
 *
 * <p>A block is not an entity: it cannot carry a profile, it is not saved with anything of its own and
 * it cannot be found by an id. So the graph of a block lives here, in world data, next to the shared
 * "Общее" graph, and every entry is keyed by the dimension and the block position -
 * {@code minecraft:overworld@12,64,-30}. Two placed copies of the same block are two entries; a block
 * broken and placed again in another spot is a different entry (the old one stays until the same spot
 * is edited again, which is what the author asked for: the graph belongs to the place, not to the
 * item).</p>
 *
 * <p>Build 352 gives every entry two flags that are worked out once, when the graph is written, and
 * not once per tick: {@link Entry#check} (the graph holds a "В радиусе" node that can fire) and
 * {@link Entry#ring} (one of those nodes has its visibility toggle on). The tick pass then walks only
 * the entries of one dimension and only does work for the flags that are set - a world with a thousand
 * block graphs where none has a check node costs a list walk twice a second and nothing else.</p>
 *
 * <p>Written the same way as {@code CutsceneStorage}, {@code NpcMobilityGroups} and
 * {@code NpcCommonGraphStorage}: the same {@code SavedData} factory, the same {@code computeIfAbsent}
 * call, the same world data folder. Nothing new is invented for storage.</p>
 */
public final class BlockTriggerStore351 extends SavedData {

    /** How many blocks may carry a graph. Reached only by hand; the save then answers "full". */
    public static final int MAX_BLOCKS = 4096;

    private static final String FILE = "settledlands_block_triggers";
    private static final String BLOCKS = "Blocks";
    private static final String KEY = "K";
    private static final String GRAPH = "G";

    /** One block: where it is, its graph, and the two flags the tick pass asks about. */
    public static final class Entry {
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
            this.check = hasFiringCheck(graph);
            this.ring = hasRing(graph);
        }

        public double centerX() {
            return x + 0.5D;
        }

        public double centerY() {
            return y;
        }

        public double centerZ() {
            return z + 0.5D;
        }

        /** Squared distance from a point to the middle of the block's face, in blocks. */
        public double distanceSqr(double px, double py, double pz) {
            double dx = centerX() - px;
            double dy = (y + 0.5D) - py;
            double dz = centerZ() - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final Map<String, Entry> byKey = new LinkedHashMap<>();
    private final Map<String, List<Entry>> byDimension = new HashMap<>();

    public static BlockTriggerStore351 get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    private static SavedData.Factory<BlockTriggerStore351> factory() {
        return new SavedData.Factory<>(BlockTriggerStore351::new, BlockTriggerStore351::load,
                DataFixTypes.LEVEL);
    }

    private static BlockTriggerStore351 load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockTriggerStore351 data = new BlockTriggerStore351();
        if (!tag.contains(BLOCKS, 9)) return data;
        ListTag list = tag.getList(BLOCKS, 10);
        for (int i = 0; i < list.size() && data.byKey.size() < MAX_BLOCKS; i++) {
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
            if (entry.graph == null) continue;
            CompoundTag one = new CompoundTag();
            one.putString(KEY, entry.key);
            one.put(GRAPH, entry.graph.save());
            list.add(one);
        }
        tag.put(BLOCKS, list);
        return tag;
    }

    /** {@code minecraft:overworld@12,64,-30} - dimension plus position, the key of one block. */
    public static String key(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        return dimensionOf(level) + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** The dimension id of a level, as it appears in a key. */
    public static String dimensionOf(ServerLevel level) {
        if (level == null) return "?";
        return level.dimension() == null || level.dimension().location() == null
                ? "?" : String.valueOf(level.dimension().location());
    }

    /** {@code 12 64 -30} - for messages, without the dimension. */
    public static String coords(BlockPos pos) {
        if (pos == null) return "?";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** True when a graph holds a "В радиусе" node that has somewhere to go. */
    private static boolean hasFiringCheck(NpcDialogueGraph graph) {
        if (graph == null) return false;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!BlockTrigger352.isRadiusNode(node)) continue;
            if (node.next >= 0 || !node.triggers.isEmpty() || (node.action != null
                    && !node.action.isBlank())) {
                return true;
            }
        }
        return false;
    }

    /** True when a "В радиусе" node has its visibility toggle on. */
    private static boolean hasRing(NpcDialogueGraph graph) {
        if (graph == null) return false;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (BlockTrigger352.isRadiusNode(node) && BlockTrigger352.visible(node)) return true;
        }
        return false;
    }

    /** The graph of that block, or null while the block has none yet. */
    public NpcDialogueGraph graph(String key) {
        Entry entry = key == null ? null : byKey.get(key);
        return entry == null ? null : entry.graph;
    }

    public Entry entry(String key) {
        return key == null ? null : byKey.get(key);
    }

    /** Every block of one dimension; empty when that dimension has no block graph. */
    public List<Entry> ofDimension(String dimension) {
        List<Entry> list = dimension == null ? null : byDimension.get(dimension);
        return list == null ? java.util.Collections.emptyList() : list;
    }

    public int size() {
        return byKey.size();
    }

    public boolean full() {
        return byKey.size() >= MAX_BLOCKS;
    }

    /**
     * Writes the graph of one block. False means the store is full and a new block was refused; an
     * existing block can always be rewritten.
     */
    public boolean put(String key, NpcDialogueGraph graph) {
        if (key == null || key.isEmpty()) return false;
        if (graph == null) {
            Entry removed = byKey.remove(key);
            if (removed != null) forget(removed);
            if (removed != null) setDirty();
            return true;
        }
        if (!byKey.containsKey(key) && full()) return false;
        Entry previous = byKey.get(key);
        if (previous != null) forget(previous);
        remember(key, graph);
        setDirty();
        return true;
    }

    private void remember(String key, NpcDialogueGraph graph) {
        int at = key.lastIndexOf('@');
        String dimension = at < 0 ? "?" : key.substring(0, at);
        int x = 0;
        int y = 0;
        int z = 0;
        if (at >= 0) {
            String[] parts = key.substring(at + 1).split(",");
            try {
                if (parts.length == 3) {
                    x = Integer.parseInt(parts[0].trim());
                    y = Integer.parseInt(parts[1].trim());
                    z = Integer.parseInt(parts[2].trim());
                }
            } catch (RuntimeException failure) {
                BlockTrigger351.fail(failure);
            }
        }
        Entry entry = new Entry(key, dimension, x, y, z, graph);
        byKey.put(key, entry);
        List<Entry> list = byDimension.get(dimension);
        if (list == null) {
            list = new ArrayList<>();
            byDimension.put(dimension, list);
        }
        list.add(entry);
    }

    private void forget(Entry entry) {
        List<Entry> list = byDimension.get(entry.dimension);
        if (list != null) list.remove(entry);
    }
}
