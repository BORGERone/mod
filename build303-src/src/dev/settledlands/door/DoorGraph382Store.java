package dev.settledlands.door;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Build 382: the graphs of the doors, kept in the world - a door is two blocks, not an entity.
 *
 * <p>Written exactly the way {@code DoorStore368} (locks) and {@code BlockTriggerStore351} (block
 * graphs) are written: one {@code SavedData} file in the overworld, one entry per place, keyed by the
 * dimension and the block position - {@code minecraft:overworld@12,64,-30}. The key of a door is
 * always the <b>lower half</b>: both halves of one door are one door, so a click on the top half and
 * a click on the bottom half reach the same graph ({@link DoorGraph382#lowerKey}).</p>
 *
 * <p>What is stored is a whole {@code NpcDialogueGraph}, because the door is edited in the stock graph
 * editor of the NPCs. What this build puts in it is one node, {@link DoorGraph382#DOOR_TYPE}, whose
 * three saved fields are the three controls the author asked for: the number of keys the hack panel
 * asks for, how fast one of them changes, and whether the door is «закрыта» (only a solved panel opens
 * it) or «открыта» (an ordinary door).</p>
 */
public final class DoorGraph382Store extends SavedData {

    /** More doors with a graph than this and the file is a mistake, not a build. */
    public static final int MAX_DOORS = 4096;

    private static final String FILE = "settledlands_door_graphs";
    private static final String DOORS = "Doors";
    private static final String KEY = "K";
    private static final String GRAPH = "G";

    /**
     * Build 390: the doors whose starter shape - card, pulse, blue target - has already been drawn. A
     * door is wired once, so a node the author deletes stays deleted instead of coming back on the next
     * open. Persisted with the graphs, because it has to survive a restart exactly as long.
     */
    private static final String STARTER = "S";

    private final Map<String, NpcDialogueGraph> byKey = new LinkedHashMap<>();
    private final Map<String, String> byDimension = new HashMap<>();
    private final java.util.Set<String> starter = new java.util.HashSet<>();

    public static DoorGraph382Store get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    public static DoorGraph382Store get(ServerLevel level) {
        if (level == null) return null;
        MinecraftServer server = level.getServer();
        return server == null ? null : get(server);
    }

    private static SavedData.Factory<DoorGraph382Store> factory() {
        return new SavedData.Factory<>(DoorGraph382Store::new, DoorGraph382Store::load,
                DataFixTypes.LEVEL);
    }

    private static DoorGraph382Store load(CompoundTag tag, HolderLookup.Provider registries) {
        DoorGraph382Store data = new DoorGraph382Store();
        if (!tag.contains(DOORS, 9)) return data;
        ListTag list = tag.getList(DOORS, 10);
        for (int i = 0; i < list.size() && data.byKey.size() < MAX_DOORS; i++) {
            CompoundTag one = list.getCompound(i);
            String key = one.getString(KEY);
            if (key.isEmpty() || !one.contains(GRAPH, 10)) continue;
            data.remember(key, NpcDialogueGraph.load(one.getCompound(GRAPH)));
        }
        if (tag.contains(STARTER, 9)) {
            ListTag done = tag.getList(STARTER, 10);
            for (int i = 0; i < done.size() && data.starter.size() < MAX_DOORS; i++) {
                String key = done.getCompound(i).getString(KEY);
                if (!key.isEmpty()) data.starter.add(key);
            }
        }
        return data;
    }

    /** True when the starter shape of this door has already been wired once. */
    public boolean starterDone(String key) {
        return key != null && !key.isEmpty() && starter.contains(key);
    }

    /** Remembers that the starter shape of this door is drawn - and saves the file. */
    public void markStarter(String key) {
        if (key == null || key.isEmpty()) return;
        if (starter.add(key)) setDirty();
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
        tag.put(DOORS, list);
        ListTag done = new ListTag();
        for (String key : starter) {
            if (key == null || key.isEmpty()) continue;
            CompoundTag one = new CompoundTag();
            one.putString(KEY, key);
            done.add(one);
        }
        tag.put(STARTER, done);
        return tag;
    }

    /** The graph of that door, or null while the door has none yet. */
    public NpcDialogueGraph graph(String key) {
        return key == null || key.isEmpty() ? null : byKey.get(key);
    }

    public int size() {
        return byKey.size();
    }

    public boolean full() {
        return byKey.size() >= MAX_DOORS;
    }

    /**
     * Build 394: the keys of the doors of one dimension, unsorted and unfiltered - what the event pass
     * walks. {@link #keys()} sorts a copy for the sync, which is fine twice on login and wrong ten times a
     * second, so the pass asks this instead: one linear read of the index the store already keeps.
     */
    public List<String> ofDimension(String dimension) {
        List<String> out = new ArrayList<>();
        if (dimension == null || dimension.isEmpty()) return out;
        for (Map.Entry<String, NpcDialogueGraph> entry : byKey.entrySet()) {
            if (entry.getValue() == null || entry.getKey() == null) continue;
            if (!dimension.equals(byDimension.get(entry.getKey()))) continue;
            out.add(entry.getKey());
        }
        return out;
    }

    /** Every key this store knows, sorted - the sync of build 382 walks it. */
    public List<String> keys() {
        List<String> keys = new ArrayList<>(byKey.keySet());
        java.util.Collections.sort(keys);
        return keys;
    }

    /**
     * Writes the graph of one door. False means the store is full and a new door was refused; an
     * existing door can always be rewritten. A null graph forgets the door.
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

    /**
     * The key of the door under this position, always the lower half.
     *
     * <p>A door is two blocks. The graph of one door must be one entry, so the position is lowered
     * while there is a door below it. A position that is not a door at all keeps its own key, so a
     * caller that asks about a wall gets a key that matches nothing.</p>
     */
    public static String lowerKey(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        BlockPos low = DoorGraph382.lower(level, pos);
        return DoorStore368.key(level, low);
    }

    /** True when this position is either half of a door. */
    public static boolean isDoorAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        return Doors368.isDoor(state);
    }
}
