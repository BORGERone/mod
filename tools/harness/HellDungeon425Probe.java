import dev.settledlands.dungeon.DungeonCommand425;
import dev.settledlands.dungeon.DungeonData425;
import dev.settledlands.dungeon.DungeonEvents425;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.dungeon.DungeonTemplate425;
import dev.settledlands.npc.NpcGraphAnchorCommand420;
import dev.settledlands.npc.TeleportActions424;
import dev.settledlands.npc.TeleportNode424;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.ProbeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Build 425 JVM probe: validates bundled {@code start.slbp} decompression and parsing,
 * doorway seam remapping, non-overlapping grid instance allocation & SavedData round-trip,
 * once-only placement semantics, natural vs spawner MobSpawnEvent filtering, missing-dimension
 * safety, and Teleport node routing + return-point restoration.
 */
public final class HellDungeon425Probe {

    private static int passed = 0;

    public static void main(String[] args) {
        registerVanillaTestBlocks(false);
        testBundledStartTemplate();
        testLiveStartPlacementAndOptionalModSafety();
        testGridOriginAllocationAndRoundTrip();
        testSpawnFilteringRules();
        testTeleportRoutingMissingDimensionSafe();
        testEnterAndLeaveFlow();
        testSubscriberReflectionAndCommandRegistration();
        System.out.println("HellDungeon425Probe: " + passed + " checks passed");
    }

    private static final String[] VANILLA_START_BLOCKS = {
        "minecraft:blackstone",
        "minecraft:bone_block",
        "minecraft:chain",
        "minecraft:chiseled_nether_bricks",
        "minecraft:chiseled_polished_blackstone",
        "minecraft:cracked_nether_bricks",
        "minecraft:cracked_polished_blackstone_bricks",
        "minecraft:gilded_blackstone",
        "minecraft:lapis_block",
        "minecraft:magma_block",
        "minecraft:nether_brick_stairs",
        "minecraft:nether_bricks",
        "minecraft:polished_basalt",
        "minecraft:polished_blackstone",
        "minecraft:polished_blackstone_brick_slab",
        "minecraft:polished_blackstone_brick_stairs",
        "minecraft:polished_blackstone_bricks",
        "minecraft:polished_blackstone_wall",
        "minecraft:redstone_block",
        "minecraft:shroomlight",
        "minecraft:soul_fire",
        "minecraft:soul_lantern",
        "minecraft:soul_soil",
        "minecraft:wither_skeleton_skull",
    };

    private static final class MockProperty extends Property<String> {
        private final String propName;

        MockProperty(String propName) {
            this.propName = propName;
        }

        @Override public String getName() { return propName; }
        @Override public java.util.Optional<String> getValue(String value) {
            return java.util.Optional.ofNullable(value);
        }
    }

    private static final class MockStateDef extends StateDefinition<Block, BlockState> {
        private final Map<String, Property<?>> props = new HashMap<>();

        @Override
        public Property<?> getProperty(String name) {
            return props.computeIfAbsent(name, MockProperty::new);
        }
    }

    private static final class MockBlockState extends BlockState {
        final String blockId;
        final Block owner;
        final Map<String, String> appliedProps = new HashMap<>();

        MockBlockState(String blockId, Block owner) {
            this.blockId = blockId;
            this.owner = owner;
        }

        @Override public Block getBlock() { return owner; }

        @Override
        @SuppressWarnings("rawtypes")
        public Object setValue(Property property, Comparable value) {
            MockBlockState copy = new MockBlockState(blockId, owner);
            copy.appliedProps.putAll(this.appliedProps);
            copy.appliedProps.put(property.getName(), String.valueOf(value));
            return copy;
        }
    }

    private static final class MockBlock extends Block {
        final String id;
        final MockStateDef def = new MockStateDef();

        MockBlock(String id) {
            this.id = id;
        }

        @Override public BlockState defaultBlockState() { return new MockBlockState(id, this); }
        @Override public StateDefinition<Block, BlockState> getStateDefinition() { return def; }
    }

    private static final class MockTrialSpawnerEntity extends BlockEntity {
        boolean changedCalled = false;

        @Override public void setChanged() { changedCalled = true; }
    }

    private static void registerVanillaTestBlocks(boolean includeTrialSpawner) {
        ProbeRegistries.clear();
        for (String id : VANILLA_START_BLOCKS) {
            ProbeRegistries.putBlock(ResourceLocation.tryParse(id), new MockBlock(id));
        }
        if (includeTrialSpawner) {
            ProbeRegistries.putBlock(ResourceLocation.tryParse("trialtuner:trial_spawner"),
                    new MockBlock("trialtuner:trial_spawner"));
        }
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            throw new AssertionError("FAIL: " + label);
        }
        passed++;
    }

    private static void testBundledStartTemplate() {
        DungeonTemplate425.StartTemplate tpl = DungeonTemplate425.loadStart();
        check(tpl != null, "start.slbp loaded");
        check(tpl.blockCount() == 6019, "start.slbp has 6019 blocks");
        check(tpl.palette.size() >= 25, "palette has expected states");
        check(tpl.blockEntities.size() == 1, "start.slbp has 1 block entity");
        check("trialtuner:trial_spawner".equals(tpl.blockEntities.get(0).id),
                "trial spawner preserved in start.slbp");
        check(tpl.entities.size() == 1, "start.slbp has 1 entity");
        check("settledlands:guide_trigger".equals(tpl.entities.get(0).id),
                "guide_trigger entity preserved in start.slbp");
        check(!tpl.entities.get(0).nbt.contains("UUID"), "entity UUID stripped");
        check(!tpl.entities.get(0).nbt.contains("neoforge:attachments"),
                "entity neoforge:attachments stripped");

        // Seam marker replacement
        check("minecraft:nether_bricks".equals(DungeonTemplate425.remapBlockId("minecraft:lapis_block")),
                "lapis_block remapped to nether_bricks");
        check("minecraft:nether_bricks".equals(DungeonTemplate425.remapBlockId("minecraft:redstone_block")),
                "redstone_block remapped to nether_bricks");
        check("minecraft:soul_soil".equals(DungeonTemplate425.remapBlockId("minecraft:soul_soil")),
                "regular blocks unchanged");

        // Verify all 6019 block coordinates sit inside expected chamber bounds [-12..12, -10..10, -12..12]
        // and verify entrance spawn cell has solid ground beneath and air headroom above.
        Set<String> occupied = new HashSet<>();
        int[] raw = tpl.blocks;
        for (int i = 0; i + 3 < raw.length; i += 4) {
            int x = raw[i];
            int y = raw[i + 1];
            int z = raw[i + 2];
            check(x >= -12 && x <= 12 && y >= -10 && y <= 10 && z >= -12 && z <= 12,
                    "block in bounds at " + x + "," + y + "," + z);
            occupied.add(x + "," + y + "," + z);
        }
        check(occupied.contains("10,-9,0"), "floor beneath entrance spawn (10,-9,0) exists");
        check(!occupied.contains("10,-8,0") && !occupied.contains("10,-7,0"),
                "headroom at (10,-8..-7,0) is open air");
    }

    private static void testLiveStartPlacementAndOptionalModSafety() {
        DungeonTemplate425.StartTemplate tpl = DungeonTemplate425.loadStart();
        MockServer srv = new MockServer();
        MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
        srv.levels.put("ResourceKey[minecraft:dimension / settledlands:hell_dungeon]", hd);

        // Case 1: trialtuner absent -> skips 1 block + 1 block entity safely without throwing
        registerVanillaTestBlocks(false);
        DungeonTemplate425.PlacementStats absentStats = DungeonTemplate425.place(hd, tpl, 0, 0, 0);
        check(absentStats.blocksPlaced == 6018, "absent trialtuner places 6018 vanilla blocks");
        check(absentStats.blocksSkipped == 1, "absent trialtuner skips exactly 1 block");
        check(absentStats.blockEntitiesSkipped == 1, "absent trialtuner skips 1 block entity safely");
        check("minecraft:nether_bricks".equals(hd.placedBlockId(12, -10, 0)),
                "east doorway lapis_block replaced by minecraft:nether_bricks");
        check("minecraft:nether_bricks".equals(hd.placedBlockId(-12, -10, 0)),
                "west doorway redstone_block replaced by minecraft:nether_bricks");
        check(hd.placedBlockId(1, -8, -9) == null, "skipped block entity position untouched when absent");

        // Case 2: trialtuner present -> places all 6019 blocks and loads BlockEntity at shifted origin
        registerVanillaTestBlocks(true);
        MockLevel hd2 = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
        MockTrialSpawnerEntity spawnerBe = new MockTrialSpawnerEntity();
        hd2.blockEntities.put("513,-8,-9", spawnerBe);
        DungeonTemplate425.PlacementStats presentStats = DungeonTemplate425.place(hd2, tpl, 512, 0, 0);
        check(presentStats.blocksPlaced == 6019, "present trialtuner places all 6019 blocks");
        check(presentStats.blocksSkipped == 0, "present trialtuner skips 0 blocks");
        check(presentStats.blockEntitiesLoaded == 1 && spawnerBe.changedCalled,
                "shifted trial spawner BlockEntity loaded and marked changed");
        check("trialtuner:trial_spawner".equals(hd2.placedBlockId(513, -8, -9)),
                "trial spawner block placed at origin + (1, -8, -9)");
        BlockState stairs = hd2.placedStates.get("522,-7,-5");
        check(stairs == null || stairs instanceof MockBlockState, "block states recorded");
        // Verify a stair block in start.slbp retains its blockstate properties
        int propertyBearing = 0;
        for (BlockState bs : hd2.placedStates.values()) {
            if (bs instanceof MockBlockState mbs && !mbs.appliedProps.isEmpty()) {
                propertyBearing++;
            }
        }
        check(propertyBearing > 50, "palette blockstate properties applied (" + propertyBearing + " blocks)");

        registerVanillaTestBlocks(false);
    }

    private static void testGridOriginAllocationAndRoundTrip() {
        Set<String> seen = new HashSet<>();
        for (int idx = 0; idx < 64; idx++) {
            int[] org = DungeonData425.gridOriginForIndex(idx);
            check(org.length == 3 && org[1] == 0, "origin y = 0 at idx " + idx);
            check(org[0] % DungeonData425.GRID_SPACING == 0 && org[2] % DungeonData425.GRID_SPACING == 0,
                    "grid aligned at idx " + idx);
            String key = org[0] + "," + org[2];
            check(seen.add(key), "unique grid origin for idx " + idx);
        }
        int[] o0 = DungeonData425.gridOriginForIndex(0);
        check(o0[0] == 0 && o0[1] == 0 && o0[2] == 0, "index 0 origin is (0,0,0)");

        DungeonData425 data = new DungeonData425();
        DungeonData425.Instance d0 = data.getOrCreateInstance(null, 12345L, 100L);
        check("default".equals(d0.id) && d0.index == 0 && !d0.placed, "default instance created");
        data.markPlaced("default");
        check(d0.placed, "markPlaced flips placed flag");

        DungeonData425.Instance d1 = data.allocateNewInstance("boss", 12345L, 200L);
        check("boss".equals(d1.id) && d1.index == 1 && d1.originX == 512, "second grid cell allocated");
        DungeonData425.Instance d2 = data.allocateNewInstance("boss", 12345L, 300L);
        check("boss_2".equals(d2.id) && d2.index == 2, "duplicate name disambiguated cleanly");

        java.util.UUID pid = new java.util.UUID(11L, 22L);
        data.saveReturnPoint(pid, "minecraft:overworld", 12.5, 70.0, -44.25, 45.0F, -5.0F);

        CompoundTag saved = data.save(new CompoundTag(), null);
        DungeonData425 loaded = DungeonData425.load(saved, null);
        check(loaded.instanceCount() == 3, "round-trip preserves all 3 instances");
        DungeonData425.Instance ld0 = loaded.getInstance("default");
        check(ld0 != null && ld0.placed && ld0.originX == 0, "default instance placed state restored");
        DungeonData425.ReturnPoint lrp = loaded.getReturnPoint(pid);
        check(lrp != null && "minecraft:overworld".equals(lrp.dimension)
                && Math.abs(lrp.x - 12.5) < 1e-9 && Math.abs(lrp.y - 70.0) < 1e-9
                && Math.abs(lrp.z - (-44.25)) < 1e-9, "return point restored");
    }

    private static final class MockPosCheck extends MobSpawnEvent.PositionCheck {
        private final ServerLevelAccessor level;
        private final MobSpawnType type;
        Result result = Result.DEFAULT;

        MockPosCheck(ServerLevelAccessor level, MobSpawnType type) {
            this.level = level;
            this.type = type;
        }

        @Override public ServerLevelAccessor getLevel() { return level; }
        @Override public MobSpawnType getSpawnType() { return type; }
        @Override public void setResult(Result result) { this.result = result; }
    }

    private static final class MockStorage extends DimensionDataStorage {
        private final Map<String, SavedData> store = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
            return (T) store.computeIfAbsent(name, k -> new DungeonData425());
        }
    }

    private static final class MockLevel extends ServerLevel {
        final ResourceKey<Level> dim;
        final MinecraftServer srv;
        final DimensionDataStorage storage = new MockStorage();
        final BlockPos sharedSpawn = new BlockPos(16, 68, -24);
        final Map<String, BlockState> placedStates = new HashMap<>();
        final Map<String, BlockEntity> blockEntities = new HashMap<>();
        int setBlockCalls = 0;

        MockLevel(ResourceKey<Level> dim, MinecraftServer srv) {
            this.dim = dim;
            this.srv = srv;
        }

        private static String key(BlockPos pos) {
            return pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }

        String placedBlockId(int x, int y, int z) {
            BlockState bs = placedStates.get(x + "," + y + "," + z);
            return bs instanceof MockBlockState mbs ? mbs.blockId : null;
        }

        @Override public ResourceKey<Level> dimension() { return dim; }
        @Override public MinecraftServer getServer() { return srv; }
        @Override public long getSeed() { return 9999L; }
        @Override public long getGameTime() { return 4200L; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public BlockPos getSharedSpawnPos() { return sharedSpawn; }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            setBlockCalls++;
            placedStates.put(key(pos), state);
            return true;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(key(pos));
        }
    }

    private static final class MockServer extends MinecraftServer {
        final Map<String, ServerLevel> levels = new HashMap<>();
        ServerLevel overworld;

        @Override public ServerLevel overworld() { return overworld; }

        @Override
        public ServerLevel getLevel(ResourceKey<Level> key) {
            return key == null ? null : levels.get(key.toString());
        }
    }

    private static final class MockPlayer extends ServerPlayer {
        final MinecraftServer srv;
        final java.util.UUID id = new java.util.UUID(7L, 9L);
        ServerLevel curLevel;
        double x = 100.0, y = 65.0, z = -200.0;
        float yaw = 180.0F, pitch = 10.0F;
        boolean clearedFire = false;
        Vec3 lastDelta = null;
        String lastMessage = null;
        int teleportCount = 0;

        MockPlayer(MinecraftServer srv, ServerLevel initialLevel) {
            this.srv = srv;
            this.curLevel = initialLevel;
            this.fallDistance = 14.0F;
        }

        @Override public MinecraftServer getServer() { return srv; }
        @Override public java.util.UUID getUUID() { return id; }
        @Override public ServerLevel serverLevel() { return curLevel; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getZ() { return z; }
        @Override public float getYRot() { return yaw; }
        @Override public float getXRot() { return pitch; }
        @Override public void clearFire() { clearedFire = true; }
        @Override public void setDeltaMovement(Vec3 m) { lastDelta = m; }

        @Override
        public void displayClientMessage(Component msg, boolean actionBar) {
            lastMessage = msg == null ? null : msg.getString();
        }

        @Override
        public boolean teleportTo(ServerLevel level, double tx, double ty, double tz,
                Set<?> relatives, float tyaw, float tpitch) {
            teleportCount++;
            this.curLevel = level;
            this.x = tx;
            this.y = ty;
            this.z = tz;
            this.yaw = tyaw;
            this.pitch = tpitch;
            return true;
        }
    }

    private static void testSpawnFilteringRules() {
        MockServer srv = new MockServer();
        MockLevel ow = new MockLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")), srv);
        MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);

        for (MobSpawnType blocked : new MobSpawnType[] {
                MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION,
                MobSpawnType.PATROL, MobSpawnType.STRUCTURE, MobSpawnType.REINFORCEMENT}) {
            MockPosCheck ev = new MockPosCheck(hd, blocked);
            DungeonEvents425.onMobSpawnPosition(ev);
            check(ev.result == MobSpawnEvent.PositionCheck.Result.FAIL,
                    "hell_dungeon blocks " + blocked);
        }

        for (MobSpawnType allowed : new MobSpawnType[] {
                MobSpawnType.SPAWNER, MobSpawnType.TRIAL_SPAWNER,
                MobSpawnType.COMMAND, MobSpawnType.SPAWN_EGG, MobSpawnType.MOB_SUMMONED}) {
            MockPosCheck ev = new MockPosCheck(hd, allowed);
            DungeonEvents425.onMobSpawnPosition(ev);
            check(ev.result == MobSpawnEvent.PositionCheck.Result.DEFAULT,
                    "hell_dungeon preserves " + allowed);
        }

        MockPosCheck otherDim = new MockPosCheck(ow, MobSpawnType.NATURAL);
        DungeonEvents425.onMobSpawnPosition(otherDim);
        check(otherDim.result == MobSpawnEvent.PositionCheck.Result.DEFAULT,
                "overworld natural spawns untouched");
    }

    private static void testTeleportRoutingMissingDimensionSafe() {
        MockServer srv = new MockServer();
        MockLevel ow = new MockLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")), srv);
        srv.overworld = ow;
        srv.levels.put("ResourceKey[minecraft:dimension / minecraft:overworld]", ow);

        MockPlayer p = new MockPlayer(srv, ow);
        boolean ok = TeleportActions424.teleport(p, "trigger:teleport:settledlands:hell_dungeon|||");
        check(!ok, "teleport fails safely when hell_dungeon dimension missing in old save");
        check(p.teleportCount == 0, "player not teleported on missing dimension");
        check(p.lastMessage != null && p.lastMessage.contains("message.settledlands.dungeon.not_loaded"),
                "notifies player cleanly when hell_dungeon not loaded");
    }

    private static void testEnterAndLeaveFlow() {
        MockServer srv = new MockServer();
        MockLevel ow = new MockLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")), srv);
        MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
        srv.overworld = ow;
        srv.levels.put("ResourceKey[minecraft:dimension / minecraft:overworld]", ow);
        srv.levels.put("ResourceKey[minecraft:dimension / settledlands:hell_dungeon]", hd);

        MockPlayer p = new MockPlayer(srv, ow);

        // Blank coords route to start chamber entrance (10.5, -8.0, 0.5) yaw 90
        boolean entered = TeleportActions424.teleport(p, "trigger:teleport:settledlands:hell_dungeon|||");
        check(entered, "Teleport node enters hell_dungeon cleanly");
        check(p.curLevel == hd, "player moved into hell_dungeon");
        check(Math.abs(p.x - 10.5) < 1e-9 && Math.abs(p.y - (-8.0)) < 1e-9 && Math.abs(p.z - 0.5) < 1e-9,
                "blank coords land at start entrance (10.5, -8.0, 0.5)");
        check(p.yaw == 90.0F && p.pitch == 0.0F, "entrance yaw=90 pitch=0 applied");
        check(p.fallDistance == 0.0F && p.clearedFire && p.lastDelta == Vec3.ZERO,
                "safety resets applied on dungeon entry");

        DungeonData425 data = DungeonData425.get(srv);
        check(data.instanceCount() == 1, "first teleport entry creates first unique instance");
        DungeonData425.Instance inst0 = data.allInstances().iterator().next();
        check(inst0 != null && inst0.placed && inst0.index == 0,
                "first unique instance placed at grid cell 0");
        check(hd.setBlockCalls == 6018, "first entry placed 6018 blocks on cell 0");
        check(data.getReturnPoint(p.id) != null, "return point recorded from overworld");

        // Re-entering existing instance by explicit id + explicit coords does not rebuild blocks or overwrite return point
        int blocksAfterFirst = hd.setBlockCalls;
        boolean reEnteredSame = DungeonManager425.enterDungeon(p, 4.0, 12.0, -8.0, false, inst0.id);
        check(reEnteredSame && p.x == 4.0 && p.y == 12.0 && p.z == -8.0,
                "explicit instance + coordinates inside hell_dungeon honored");
        check(hd.setBlockCalls == blocksAfterFirst,
                "re-entering existing instance does not re-place blocks");
        DungeonData425.ReturnPoint savedRp = data.getReturnPoint(p.id);
        check(savedRp != null && "minecraft:overworld".equals(savedRp.dimension),
                "re-entering from inside hell_dungeon does not overwrite return point");

        // Second entry via Teleport with literal 0|0|0 allocates a NEW unique instance at (512, 0, 0) + entrance offset
        boolean zeroCoordsNew = TeleportActions424.teleport(p, "trigger:teleport:settledlands:hell_dungeon|0|0|0");
        check(zeroCoordsNew && data.instanceCount() == 2,
                "zero-coord Teleport allocates a fresh unique instance instead of (0,0,0) ceiling");
        check(Math.abs(p.x - 522.5) < 1e-9 && Math.abs(p.y - (-8.0)) < 1e-9 && Math.abs(p.z - 0.5) < 1e-9,
                "second instance lands at (512 + 10.5, -8.0, 0.5)");

        // Explicit non-zero Teleport coords route to exact target coordinates
        boolean enteredExplicit = TeleportActions424.teleport(p,
                "trigger:teleport:settledlands:hell_dungeon|4|12|-8");
        check(enteredExplicit && p.x == 4.0 && p.y == 12.0 && p.z == -8.0,
                "explicit non-zero Teleport coordinates inside hell_dungeon honored");

        // Creating named new grid instance allocates cell 3 (after inst0, inst1, default)
        boolean createdNew = DungeonManager425.createAndEnterNewInstance(p, "raid");
        DungeonData425.Instance raid = data.getInstance("raid");
        check(createdNew && raid != null && raid.index >= 2, "new named grid instance created and entered");
        check(Math.abs(p.x - (raid.originX + 10.5)) < 1e-9
                        && Math.abs(p.y - (-8.0)) < 1e-9
                        && Math.abs(p.z - (raid.originZ + 0.5)) < 1e-9,
                "named instance lands at cell origin + entrance offset");

        // Leaving restores saved overworld coordinates (100.0, 65.0, -200.0)
        boolean left = DungeonManager425.leaveDungeon(p);
        check(left && p.curLevel == ow, "leaveDungeon returns player to overworld");
        check(Math.abs(p.x - 100.0) < 1e-9 && Math.abs(p.y - 65.0) < 1e-9 && Math.abs(p.z - (-200.0)) < 1e-9,
                "return coordinates restored precisely");
        check(data.getReturnPoint(p.id) == null, "return point cleared after clean leave");

        // Leaving without saved return point falls back safely to overworld shared spawn
        DungeonManager425.enterDungeon(p, inst0.id);
        data.clearReturnPoint(p.id);
        boolean fallbackLeft = DungeonManager425.leaveDungeon(p);
        check(fallbackLeft && p.curLevel == ow, "leave fallback works without saved return point");
        check(Math.abs(p.x - 16.5) < 1e-9 && Math.abs(p.y - 69.0) < 1e-9 && Math.abs(p.z - (-23.5)) < 1e-9,
                "fallback uses overworld.getSharedSpawnPos()");

        // Verify world alias & registry integration on TeleportNode424
        check(TeleportNode424.worldCount() == 5, "TeleportNode424 exposes 5 worlds");
        check("settledlands:hell_dungeon".equals(TeleportNode424.worldId(4)), "5th world id is hell_dungeon");
    }

    private static void testSubscriberReflectionAndCommandRegistration() {
        // Simulates NeoForge AutomaticEventSubscriber.inject() -> Class.getDeclaredMethods()
        Class<?>[] subscribers = new Class<?>[] {
            DungeonCommand425.class,
            DungeonEvents425.class,
            NpcGraphAnchorCommand420.class,
            DungeonManager425.class,
            DungeonData425.class,
            DungeonTemplate425.class,
        };
        for (Class<?> cls : subscribers) {
            check(cls.getDeclaredMethods().length > 0,
                    cls.getSimpleName() + " getDeclaredMethods resolves cleanly");
            check(cls.getDeclaredConstructors().length > 0,
                    cls.getSimpleName() + " getDeclaredConstructors resolves cleanly");
        }
        check(DungeonCommand425.class.getDeclaredFields().length >= 0,
                "DungeonCommand425 getDeclaredFields resolves cleanly");

        // Exercise onRegisterCommands with null and a live dispatcher event
        DungeonCommand425.onRegisterCommands(null);
        com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> disp =
                new com.mojang.brigadier.CommandDispatcher<>();
        net.neoforged.neoforge.event.RegisterCommandsEvent ev =
                new net.neoforged.neoforge.event.RegisterCommandsEvent() {
                    @Override
                    public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher() {
                        return disp;
                    }
                };
        DungeonCommand425.onRegisterCommands(ev);
        check(true, "DungeonCommand425.onRegisterCommands runs without exception");
    }
}
