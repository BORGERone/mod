package dev.settledlands.dungeon;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Build 425: world-persisted registry of Hell Dungeon grid instances and player return points.
 *
 * <p>Each instance owns a non-overlapping {@code 512 x 512} grid cell on the XZ plane
 * ({@link #gridOriginForIndex(int)}), and tracks {@code placed} so {@code start.slbp} is placed once
 * per instance and never overwrites subsequent edits or generation.</p>
 */
public final class DungeonData425 extends SavedData {

    public static final String DATA_NAME = "settledlands_hell_dungeon";
    public static final String DEFAULT_INSTANCE_ID = "default";
    public static final int GRID_SPACING = 512;

    public static final SavedData.Factory<DungeonData425> FACTORY =
            new SavedData.Factory<>(DungeonData425::new, DungeonData425::load, null);

    public static final class Instance {
        public final String id;
        public final int index;
        public final int originX;
        public final int originY;
        public final int originZ;
        public boolean placed;
        public long seed;
        public long createdTick;

        public Instance(String id, int index, int originX, int originY, int originZ,
                boolean placed, long seed, long createdTick) {
            this.id = id;
            this.index = index;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.placed = placed;
            this.seed = seed;
            this.createdTick = createdTick;
        }
    }

    public static final class ReturnPoint {
        public final String dimension;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private int nextIndex = 0;
    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();

    public DungeonData425() {}

    public static DungeonData425 get(ServerLevel overworld) {
        if (overworld == null) return new DungeonData425();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static DungeonData425 get(MinecraftServer server) {
        if (server == null) return new DungeonData425();
        return get(server.overworld());
    }

    public static String normalizeInstanceId(String raw) {
        if (raw == null) return DEFAULT_INSTANCE_ID;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        return clean.isEmpty() ? DEFAULT_INSTANCE_ID : clean;
    }

    /**
     * Deterministic non-overlapping XZ spiral: index 0 -> (0, 0, 0), 1 -> (512, 0, 0),
     * 2 -> (512, 0, 512), 3 -> (0, 0, 512), 4 -> (-512, 0, 512), etc.
     */
    public static int[] gridOriginForIndex(int index) {
        if (index <= 0) return new int[] {0, 0, 0};
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int stepLen = 1;
        int stepLeft = 1;
        int turns = 0;
        for (int i = 0; i < index; i++) {
            x += dx;
            z += dz;
            stepLeft--;
            if (stepLeft == 0) {
                int tmp = dx;
                dx = -dz;
                dz = tmp;
                turns++;
                if ((turns & 1) == 0) {
                    stepLen++;
                }
                stepLeft = stepLen;
            }
        }
        return new int[] {x * GRID_SPACING, 0, z * GRID_SPACING};
    }

    public Instance getInstance(String id) {
        return instances.get(normalizeInstanceId(id));
    }

    public Collection<Instance> allInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public int instanceCount() {
        return instances.size();
    }

    public Instance getOrCreateInstance(String rawId, long seed, long tick) {
        String id = normalizeInstanceId(rawId);
        Instance existing = instances.get(id);
        if (existing != null) return existing;
        int idx = allocateNextIndex();
        int[] origin = gridOriginForIndex(idx);
        Instance created = new Instance(id, idx, origin[0], origin[1], origin[2], false, seed, tick);
        instances.put(id, created);
        setDirty();
        return created;
    }

    public Instance allocateNewInstance(String requestedId, long seed, long tick) {
        String base = (requestedId == null || requestedId.trim().isEmpty())
                ? "dungeon_" + (nextIndex + 1)
                : normalizeInstanceId(requestedId);
        String id = base;
        int suffix = 2;
        while (instances.containsKey(id)) {
            id = base + "_" + suffix++;
        }
        int idx = allocateNextIndex();
        int[] origin = gridOriginForIndex(idx);
        Instance created = new Instance(id, idx, origin[0], origin[1], origin[2], false, seed, tick);
        instances.put(id, created);
        setDirty();
        return created;
    }

    private int allocateNextIndex() {
        int candidate = nextIndex;
        while (isIndexUsed(candidate)) {
            candidate++;
        }
        nextIndex = candidate + 1;
        return candidate;
    }

    private boolean isIndexUsed(int idx) {
        for (Instance inst : instances.values()) {
            if (inst.index == idx) return true;
        }
        return false;
    }

    public void markPlaced(String rawId) {
        Instance inst = instances.get(normalizeInstanceId(rawId));
        if (inst != null && !inst.placed) {
            inst.placed = true;
            setDirty();
        }
    }

    public void saveReturnPoint(UUID playerId, String dimension,
            double x, double y, double z, float yaw, float pitch) {
        if (playerId == null || dimension == null || dimension.isEmpty()) return;
        returnPoints.put(playerId, new ReturnPoint(dimension, x, y, z, yaw, pitch));
        setDirty();
    }

    public ReturnPoint getReturnPoint(UUID playerId) {
        return playerId == null ? null : returnPoints.get(playerId);
    }

    public void clearReturnPoint(UUID playerId) {
        if (playerId != null && returnPoints.remove(playerId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextIndex", nextIndex);

        ListTag instList = new ListTag();
        for (Instance inst : instances.values()) {
            CompoundTag item = new CompoundTag();
            item.putString("Id", inst.id);
            item.putInt("Index", inst.index);
            item.putInt("OriginX", inst.originX);
            item.putInt("OriginY", inst.originY);
            item.putInt("OriginZ", inst.originZ);
            item.putBoolean("Placed", inst.placed);
            item.putLong("Seed", inst.seed);
            item.putLong("CreatedTick", inst.createdTick);
            instList.add(item);
        }
        tag.put("Instances", instList);

        ListTag retList = new ListTag();
        for (Map.Entry<UUID, ReturnPoint> e : returnPoints.entrySet()) {
            ReturnPoint rp = e.getValue();
            CompoundTag item = new CompoundTag();
            item.putUUID("Player", e.getKey());
            item.putString("Dim", rp.dimension);
            item.putDouble("X", rp.x);
            item.putDouble("Y", rp.y);
            item.putDouble("Z", rp.z);
            item.putFloat("Yaw", rp.yaw);
            item.putFloat("Pitch", rp.pitch);
            retList.add(item);
        }
        tag.put("ReturnPoints", retList);
        return tag;
    }

    public static DungeonData425 load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonData425 data = new DungeonData425();
        if (tag == null) return data;
        data.nextIndex = Math.max(0, tag.getInt("NextIndex"));

        ListTag instList = tag.getList("Instances", 10);
        for (int i = 0; i < instList.size(); i++) {
            CompoundTag item = instList.getCompound(i);
            String id = normalizeInstanceId(item.getString("Id"));
            int idx = item.getInt("Index");
            int ox = item.getInt("OriginX");
            int oy = item.getInt("OriginY");
            int oz = item.getInt("OriginZ");
            boolean placed = item.getBoolean("Placed");
            long seed = item.getLong("Seed");
            long created = item.getLong("CreatedTick");
            data.instances.put(id, new Instance(id, idx, ox, oy, oz, placed, seed, created));
            if (idx >= data.nextIndex) {
                data.nextIndex = idx + 1;
            }
        }

        ListTag retList = tag.getList("ReturnPoints", 10);
        for (int i = 0; i < retList.size(); i++) {
            CompoundTag item = retList.getCompound(i);
            UUID uuid = item.getUUID("Player");
            String dim = item.getString("Dim");
            if (uuid != null && dim != null && !dim.isEmpty()) {
                data.returnPoints.put(uuid, new ReturnPoint(
                        dim,
                        item.getDouble("X"),
                        item.getDouble("Y"),
                        item.getDouble("Z"),
                        item.getFloat("Yaw"),
                        item.getFloat("Pitch")));
            }
        }
        return data;
    }
}
