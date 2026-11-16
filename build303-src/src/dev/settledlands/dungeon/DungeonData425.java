package dev.settledlands.dungeon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Build 425 / 427: world-persisted registry of Hell Dungeon grid instances, procedural piece
 * placement plans/progress, and player return points.
 *
 * <p>Each instance owns a non-overlapping {@code 512 x 512} grid cell on the XZ plane
 * ({@link #gridOriginForIndex(int)}), generates its layout once per instance, and persists
 * piece-by-piece progress so interrupted builds resume cleanly after restart.</p>
 */
public final class DungeonData425 extends SavedData {

    public static final String DATA_NAME = "settledlands_hell_dungeon";
    public static final String DEFAULT_INSTANCE_ID = "default";
    public static final int GRID_SPACING = 512;

    public static final SavedData.Factory<DungeonData425> FACTORY =
            new SavedData.Factory<>(DungeonData425::new, DungeonData425::load, null);

    public static final class PieceProgress {
        public final String pieceKey;
        public final int ox;
        public final int oy;
        public final int oz;
        public final int turns;
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;
        public int blockCursor;
        public boolean done;

        public PieceProgress(String pieceKey, int ox, int oy, int oz, int turns,
                int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                int blockCursor, boolean done) {
            this.pieceKey = pieceKey;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.turns = turns & 3;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.blockCursor = Math.max(0, blockCursor);
            this.done = done;
        }

        public double distSqTo(double px, double py, double pz) {
            double cx = clamp(px, minX, maxX) - px;
            double cy = clamp(py, minY, maxY) - py;
            double cz = clamp(pz, minZ, maxZ) - pz;
            return cx * cx + cy * cy + cz * cz;
        }

        private static double clamp(double v, int lo, int hi) {
            if (v < lo) return lo;
            if (v > hi) return hi;
            return v;
        }
    }

    public static final class SealProgress {
        public final int x;
        public final int y;
        public final int z;
        public final int outDir;
        public boolean done;

        public SealProgress(int x, int y, int z, int outDir, boolean done) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.outDir = outDir & 3;
            this.done = done;
        }
    }

    public static final class Instance {
        public final String id;
        public final int index;
        public final int originX;
        public final int originY;
        public final int originZ;
        public boolean placed;
        public long seed;
        public long createdTick;

        // Build 427 procedural layout state
        public boolean planGenerated;
        public double spawnX;
        public double spawnY;
        public double spawnZ;
        public float spawnYaw;
        public float spawnPitch;
        public String doorwayBlock;
        public String blueprintSource;
        public final List<PieceProgress> pieces = new ArrayList<>();
        public final List<SealProgress> seals = new ArrayList<>();

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
            this.planGenerated = placed;
            this.spawnX = originX + DungeonTemplate425.SPAWN_OFFSET_X;
            this.spawnY = originY + DungeonTemplate425.SPAWN_OFFSET_Y;
            this.spawnZ = originZ + DungeonTemplate425.SPAWN_OFFSET_Z;
            this.spawnYaw = DungeonTemplate425.SPAWN_YAW;
            this.spawnPitch = DungeonTemplate425.SPAWN_PITCH;
            this.doorwayBlock = DungeonTemplate425.DOORWAY_REPLACEMENT;
            this.blueprintSource = "";
        }

        public int donePieceCount() {
            int c = 0;
            for (PieceProgress p : pieces) {
                if (p.done) c++;
            }
            return c;
        }

        public boolean hasPendingWork() {
            if (!planGenerated || placed) return false;
            for (PieceProgress p : pieces) {
                if (!p.done) return true;
            }
            for (SealProgress s : seals) {
                if (!s.done) return true;
            }
            return false;
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
        long instSeed = mixSeed(seed, idx, id);
        Instance created = new Instance(id, idx, origin[0], origin[1], origin[2], false, instSeed, tick);
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
        long instSeed = mixSeed(seed, idx, id);
        Instance created = new Instance(id, idx, origin[0], origin[1], origin[2], false, instSeed, tick);
        instances.put(id, created);
        setDirty();
        return created;
    }

    private static long mixSeed(long worldSeed, int idx, String id) {
        long h = worldSeed ^ ((long) idx * 0x9E3779B97F4A7C15L);
        if (id != null) {
            for (int i = 0; i < id.length(); i++) {
                h = h * 31L + id.charAt(i);
            }
        }
        return h;
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

    public void setPlan(Instance inst, DungeonGenerator427.Plan plan,
            String doorwayBlock, String blueprintSource) {
        if (inst == null || plan == null) return;
        inst.pieces.clear();
        for (DungeonGenerator427.PlannedPiece pp : plan.pieces) {
            inst.pieces.add(new PieceProgress(
                    pp.pieceKey, pp.ox, pp.oy, pp.oz, pp.turns,
                    pp.minX, pp.minY, pp.minZ, pp.maxX, pp.maxY, pp.maxZ,
                    0, false));
        }
        inst.seals.clear();
        for (DungeonGenerator427.Seal s : plan.seals) {
            inst.seals.add(new SealProgress(s.x, s.y, s.z, s.outDir, false));
        }
        inst.spawnX = plan.spawnX;
        inst.spawnY = plan.spawnY;
        inst.spawnZ = plan.spawnZ;
        inst.spawnYaw = plan.spawnYaw;
        inst.spawnPitch = plan.spawnPitch;
        inst.doorwayBlock = (doorwayBlock == null || doorwayBlock.isEmpty())
                ? DungeonTemplate425.DOORWAY_REPLACEMENT : doorwayBlock;
        inst.blueprintSource = blueprintSource == null ? "" : blueprintSource;
        inst.planGenerated = true;
        setDirty();
    }

    public void markPlaced(String rawId) {
        Instance inst = instances.get(normalizeInstanceId(rawId));
        if (inst != null) {
            inst.planGenerated = true;
            if (!inst.placed) {
                inst.placed = true;
            }
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

            item.putBoolean("PlanGenerated", inst.planGenerated);
            item.putDouble("SpawnX", inst.spawnX);
            item.putDouble("SpawnY", inst.spawnY);
            item.putDouble("SpawnZ", inst.spawnZ);
            item.putFloat("SpawnYaw", inst.spawnYaw);
            item.putFloat("SpawnPitch", inst.spawnPitch);
            if (inst.doorwayBlock != null && !inst.doorwayBlock.isEmpty()) {
                item.putString("DoorwayBlock", inst.doorwayBlock);
            }
            if (inst.blueprintSource != null && !inst.blueprintSource.isEmpty()) {
                item.putString("BlueprintSource", inst.blueprintSource);
            }

            if (!inst.pieces.isEmpty()) {
                ListTag pList = new ListTag();
                for (PieceProgress p : inst.pieces) {
                    CompoundTag pt = new CompoundTag();
                    pt.putString("Piece", p.pieceKey);
                    pt.putInt("Ox", p.ox);
                    pt.putInt("Oy", p.oy);
                    pt.putInt("Oz", p.oz);
                    pt.putInt("Turns", p.turns);
                    pt.putInt("MinX", p.minX);
                    pt.putInt("MinY", p.minY);
                    pt.putInt("MinZ", p.minZ);
                    pt.putInt("MaxX", p.maxX);
                    pt.putInt("MaxY", p.maxY);
                    pt.putInt("MaxZ", p.maxZ);
                    pt.putInt("Cursor", p.blockCursor);
                    pt.putBoolean("Done", p.done);
                    pList.add(pt);
                }
                item.put("Pieces", pList);
            }
            if (!inst.seals.isEmpty()) {
                ListTag sList = new ListTag();
                for (SealProgress s : inst.seals) {
                    CompoundTag st = new CompoundTag();
                    st.putInt("X", s.x);
                    st.putInt("Y", s.y);
                    st.putInt("Z", s.z);
                    st.putInt("Dir", s.outDir);
                    st.putBoolean("Done", s.done);
                    sList.add(st);
                }
                item.put("Seals", sList);
            }

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
            Instance inst = new Instance(id, idx, ox, oy, oz, placed, seed, created);

            if (item.contains("PlanGenerated")) {
                inst.planGenerated = item.getBoolean("PlanGenerated");
            }
            if (item.contains("SpawnX")) {
                inst.spawnX = item.getDouble("SpawnX");
                inst.spawnY = item.getDouble("SpawnY");
                inst.spawnZ = item.getDouble("SpawnZ");
                inst.spawnYaw = item.getFloat("SpawnYaw");
                inst.spawnPitch = item.getFloat("SpawnPitch");
            }
            String db = item.getString("DoorwayBlock");
            if (!db.isEmpty()) inst.doorwayBlock = db;
            inst.blueprintSource = item.getString("BlueprintSource");

            ListTag pList = item.getList("Pieces", 10);
            for (int j = 0; j < pList.size(); j++) {
                CompoundTag pt = pList.getCompound(j);
                inst.pieces.add(new PieceProgress(
                        pt.getString("Piece"),
                        pt.getInt("Ox"),
                        pt.getInt("Oy"),
                        pt.getInt("Oz"),
                        pt.getInt("Turns"),
                        pt.getInt("MinX"),
                        pt.getInt("MinY"),
                        pt.getInt("MinZ"),
                        pt.getInt("MaxX"),
                        pt.getInt("MaxY"),
                        pt.getInt("MaxZ"),
                        pt.getInt("Cursor"),
                        pt.getBoolean("Done")));
            }

            ListTag sList = item.getList("Seals", 10);
            for (int j = 0; j < sList.size(); j++) {
                CompoundTag st = sList.getCompound(j);
                inst.seals.add(new SealProgress(
                        st.getInt("X"),
                        st.getInt("Y"),
                        st.getInt("Z"),
                        st.getInt("Dir"),
                        st.getBoolean("Done")));
            }

            data.instances.put(id, inst);
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
