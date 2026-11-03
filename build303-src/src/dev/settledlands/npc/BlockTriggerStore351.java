package dev.settledlands.npc;

import java.util.LinkedHashMap;
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
 * Build 351: the graphs of the blocks, kept in the world instead of in an entity.
 *
 * <p>A block is not an entity: it cannot carry a profile, it is not saved with anything of its own and
 * it cannot be found by an id. So the graph of a block lives here, in world data, next to the shared
 * "Общее" graph, and every entry is keyed by the dimension and the block position -
 * {@code minecraft:overworld@12,64,-30}. Two placed copies of the same block are two entries; a block
 * broken and placed again in another spot is a different entry (the old one stays until the same spot
 * is edited again, which is what the author asked for: the graph belongs to the place, not to the
 * item).</p>
 *
 * <p>Written the same way as {@code CutsceneStorage}, {@code NpcMobilityGroups} and
 * {@code NpcCommonGraphStorage}: the same {@code SavedData} factory, the same {@code computeIfAbsent}
 * call, the same world data folder. Nothing new is invented for storage.</p>
 *
 * <p>Cost: a right click on a block is one hash lookup; nothing is scanned and nothing is ticked, so a
 * world with a thousand block graphs costs the same as one with none.</p>
 */
public final class BlockTriggerStore351 extends SavedData {

    /** How many blocks may carry a graph. Reached only by hand; the save then answers "full". */
    public static final int MAX_BLOCKS = 4096;

    private static final String FILE = "settledlands_block_triggers";
    private static final String BLOCKS = "Blocks";
    private static final String KEY = "K";
    private static final String GRAPH = "G";

    private final Map<String, NpcDialogueGraph> graphs = new LinkedHashMap<>();

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
        for (int i = 0; i < list.size() && data.graphs.size() < MAX_BLOCKS; i++) {
            CompoundTag entry = list.getCompound(i);
            String key = entry.getString(KEY);
            if (key.isEmpty() || !entry.contains(GRAPH, 10)) continue;
            data.graphs.put(key, NpcDialogueGraph.load(entry.getCompound(GRAPH)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, NpcDialogueGraph> entry : graphs.entrySet()) {
            if (entry.getValue() == null) continue;
            CompoundTag one = new CompoundTag();
            one.putString(KEY, entry.getKey());
            one.put(GRAPH, entry.getValue().save());
            list.add(one);
        }
        tag.put(BLOCKS, list);
        return tag;
    }

    /** {@code minecraft:overworld@12,64,-30} - dimension plus position, the key of one block. */
    public static String key(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        String dimension = level.dimension() == null || level.dimension().location() == null
                ? "?" : String.valueOf(level.dimension().location());
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** {@code 12 64 -30} - for messages, without the dimension. */
    public static String coords(BlockPos pos) {
        if (pos == null) return "?";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** The graph of that block, or null while the block has none yet. */
    public NpcDialogueGraph graph(String key) {
        return key == null || key.isEmpty() ? null : graphs.get(key);
    }

    public int size() {
        return graphs.size();
    }

    public boolean full() {
        return graphs.size() >= MAX_BLOCKS;
    }

    /**
     * Writes the graph of one block. False means the store is full and a new block was refused; an
     * existing block can always be rewritten.
     */
    public boolean put(String key, NpcDialogueGraph graph) {
        if (key == null || key.isEmpty()) return false;
        if (graph == null) {
            if (graphs.remove(key) != null) setDirty();
            return true;
        }
        if (!graphs.containsKey(key) && full()) return false;
        graphs.put(key, graph);
        setDirty();
        return true;
    }
}
