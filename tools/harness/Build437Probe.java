import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.door.DoorStore368;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.BlockTriggerStore351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphAnchor420;
import dev.settledlands.npc.NpcGraphAnchorCore420;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;

/**
 * Build 437 End-to-End JVM regression & fix verification against the real compiled classes in the JAR.
 *
 * <p>Covers every failure mode discovered after Build 436:</p>
 * <ol>
 *   <li>Furniture-only buildings (no {@code BlockTrigger351}) auto-create a {@code settledlands:guide_trigger}
 *       anchor immediately on {@link ChestGraph396#save} and on {@link NpcGraphAnchor420#pass};</li>
 *   <li>Wall-mounted furniture under a room ceiling picks an in-room spot in {@link NpcGraphAnchor420#freeSpot},
 *       never above the roof;</li>
 *   <li>Shelves 25..32 blocks away from an existing anchor (the old 24..32 dead zone) update the covering
 *       anchor without spawning duplicates or dropping out;</li>
 *   <li>Clearing all graphs clears stale anchor tags, and {@link NpcGraphAnchor420#sealHere} reuses an
 *       existing anchor;</li>
 *   <li>Door locks/graphs without block triggers also auto-create anchors;</li>
 *   <li>Overworld builds near {@code (0,0,0)} stamp {@code SettledLandsAnchorDim}, and dungeon room
 *       generation at {@code (0,0,0)} (both fresh and legacy markers without dim) and at rotated
 *       coordinates with &gt;50% modified fingerprint blocks materialises every furniture graph and
 *       populates item pool loot via {@link LootPoolManager436#onDungeonRoomPlaced(ServerLevel, List, int)}.</li>
 * </ol>
 */
public final class Build437Probe {

    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) throw new AssertionError("Build437Probe FAILED: " + what);
    }

    // ---- Test registries & world mocks ------------------------------------

    private static final class SimpleReg<T> implements DefaultedRegistry<T> {
        private final Map<ResourceLocation, T> byId = new HashMap<>();
        private final Map<T, ResourceLocation> byVal = new HashMap<>();

        void put(String id, T val) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            byId.put(rl, val);
            byVal.put(val, rl);
        }

        @Override public ResourceLocation getKey(T value) { return byVal.get(value); }
        @Override public T get(ResourceLocation id) { return byId.get(id); }
        @Override public boolean containsKey(ResourceLocation id) { return byId.containsKey(id); }
    }

    private static final class MockBlock extends Block {
        final boolean air;
        final BlockState state;

        MockBlock(boolean air) {
            this.air = air;
            this.state = new BlockState(this, air);
        }

        @Override public BlockState defaultBlockState() { return state; }
    }

    private static final class MockChestBE extends BlockEntity implements Container {
        final ItemStack[] slots = new ItemStack[27];

        MockChestBE() {
            for (int i = 0; i < slots.length; i++) slots[i] = ItemStack.EMPTY;
        }

        @Override public ItemStack getItem(int slot) { return slots[slot]; }
        @Override public int getContainerSize() { return slots.length; }
        @Override public void setItem(int slot, ItemStack stack) {
            slots[slot] = stack == null ? ItemStack.EMPTY : stack;
        }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
    }

    private static final class MockLevel extends ServerLevel {
        final ResourceKey<Level> dimKey;
        final MinecraftServer server;
        final DimensionDataStorage storage = new DimensionDataStorage();
        final Map<Long, BlockState> blocks = new HashMap<>();
        final Map<Long, BlockEntity> bes = new HashMap<>();
        final List<Entity> entities = new ArrayList<>();
        final BlockState airState;

        MockLevel(ResourceKey<Level> dimKey, MinecraftServer server, BlockState airState) {
            this.dimKey = dimKey;
            this.server = server;
            this.airState = airState;
        }

        private static long pack(BlockPos pos) {
            return (((long) pos.getX() & 0x3FFFFFFL) << 38)
                    | (((long) pos.getZ() & 0x3FFFFFFL) << 12)
                    | ((long) pos.getY() & 0xFFFL);
        }

        void putBlock(BlockPos pos, BlockState state) {
            blocks.put(pack(pos), state);
        }

        void putChest(BlockPos pos, BlockState state) {
            putBlock(pos, state);
            bes.put(pack(pos), new MockChestBE());
        }

        @Override public ResourceKey<Level> dimension() { return dimKey; }
        @Override public MinecraftServer getServer() { return server; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public boolean hasChunkAt(BlockPos pos) { return true; }

        @Override public BlockState getBlockState(BlockPos pos) {
            BlockState s = blocks.get(pack(pos));
            return s != null ? s : airState;
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            putBlock(pos, state);
            return true;
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return bes.get(pack(pos));
        }

        @Override public boolean addFreshEntity(Entity entity) {
            if (entity != null && !entities.contains(entity)) {
                entities.add(entity);
            }
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box, Predicate<? super T> test) {
            List<T> out = new ArrayList<>();
            for (Entity e : entities) {
                if (e == null || !type.isInstance(e)) continue;
                if (box != null && (e.getX() < box.minX || e.getX() > box.maxX
                        || e.getY() < box.minY || e.getY() > box.maxY
                        || e.getZ() < box.minZ || e.getZ() > box.maxZ)) {
                    continue;
                }
                T cast = (T) e;
                if (test == null || test.test(cast)) out.add(cast);
            }
            return out;
        }
    }

    private static final class MockServer extends MinecraftServer {
        MockLevel overworld;
        MockLevel dungeon;
        final List<ServerPlayer> players = new ArrayList<>();
        final PlayerList playerList = new PlayerList() {
            @Override public List<ServerPlayer> getPlayers() { return players; }
        };

        @Override public ServerLevel overworld() { return overworld; }
        @Override public Iterable<ServerLevel> getAllLevels() {
            List<ServerLevel> all = new ArrayList<>();
            if (overworld != null) all.add(overworld);
            if (dungeon != null) all.add(dungeon);
            return all;
        }
        @Override public PlayerList getPlayerList() { return playerList; }
    }

    private static final class MockPlayer extends ServerPlayer {
        private final ServerLevel lvl;
        private final double px, py, pz;

        MockPlayer(ServerLevel lvl, double x, double y, double z) {
            this.lvl = lvl;
            this.px = x;
            this.py = y;
            this.pz = z;
        }

        @Override public MinecraftServer getServer() { return lvl.getServer(); }
        @Override public ServerLevel serverLevel() { return lvl; }
        @Override public Level level() { return lvl; }
        @Override public double getX() { return px; }
        @Override public double getY() { return py; }
        @Override public double getZ() { return pz; }
        @Override public boolean hasPermissions(int level) { return true; }
        @Override public void displayClientMessage(Component msg, boolean actionBar) {}
    }

    public static void main(String[] args) throws Exception {
        // Initialize LootPoolManager436 pool file inside the isolated probe workdir
        Path poolDir = LootPoolManager436.ensureDirectory();
        Files.writeString(poolDir.resolve("shelf_pool.txt"),
                "minecraft:diamond | 100 | 100 | 3\n", StandardCharsets.UTF_8);

        SimpleReg<Block> blockReg = new SimpleReg<>();
        SimpleReg<EntityType> entityReg = new SimpleReg<>();
        SimpleReg<Item> itemReg = new SimpleReg<>();

        MockBlock air = new MockBlock(true);
        MockBlock stone = new MockBlock(false);
        MockBlock netherBricks = new MockBlock(false);
        MockBlock shelfBlock = new MockBlock(false);
        MockBlock kitchenShelfBlock = new MockBlock(false);
        MockBlock wallBlock = new MockBlock(false);

        blockReg.put("minecraft:air", air);
        blockReg.put("minecraft:stone", stone);
        blockReg.put("minecraft:nether_bricks", netherBricks);
        blockReg.put("refurbished_furniture:oak_storage_cabinet", shelfBlock);
        blockReg.put("refurbished_furniture:oak_kitchen_storage_cabinet", kitchenShelfBlock);
        blockReg.put("minecraft:oak_planks", wallBlock);

        EntityType guideTriggerType = new EntityType("settledlands:guide_trigger", () -> new Entity());
        entityReg.put("settledlands:guide_trigger", guideTriggerType);

        Item diamond = new Item();
        itemReg.put("minecraft:diamond", diamond);

        BuiltInRegistries.BLOCK = blockReg;
        BuiltInRegistries.ENTITY_TYPE = entityReg;
        BuiltInRegistries.ITEM = itemReg;

        MockServer server = new MockServer();
        MockLevel overworld = new MockLevel(
                ResourceKey.create(null, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")),
                server, air.defaultBlockState());
        MockLevel dungeon = new MockLevel(
                ResourceKey.create(null, ResourceLocation.fromNamespaceAndPath("settledlands", "hell_dungeon")),
                server, air.defaultBlockState());
        server.overworld = overworld;
        server.dungeon = dungeon;

        testRoomBuildAndImmediateSave(server, overworld, stone, wallBlock, shelfBlock, kitchenShelfBlock);
        testDeadZone25To32AndClearAndSealHere(server, overworld, shelfBlock, kitchenShelfBlock);
        testDoorOnlyStructureAutoAnchor(server, overworld);
        testDungeonOriginCollisionAndFallback(server, overworld, dungeon, stone, wallBlock, netherBricks,
                shelfBlock, kitchenShelfBlock, diamond);

        System.out.println("Build437Probe: " + checks + " checks passed");
    }

    private static NpcDialogueGraph makeShelfLootGraph(String poolFile) {
        NpcDialogueGraph g = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node chest = ChestGraph396.chestNode(g);
        NpcDialogueGraph.Node pool = g.add(NpcDialogueGraph.TRIGGER, 260, 120);
        pool.action = "trigger:loot_pool:" + poolFile + "|100|100|1";
        chest.next = pool.id;
        return g;
    }

    private static void buildRoom(MockLevel lvl, int ox, int oy, int oz, int turns,
            BlockState floor, BlockState wall, BlockState shelf1, BlockState shelf2) {
        // 9x5x9 room centered near origin: floor at y=oy, walls around x/z in [-4..4], roof at y=oy+4
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                setTurned(lvl, ox, oy, oz, dx, 0, dz, turns, floor, false);
                setTurned(lvl, ox, oy, oz, dx, 4, dz, turns, wall, false);
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                    for (int dy = 1; dy <= 3; dy++) {
                        setTurned(lvl, ox, oy, oz, dx, dy, dz, turns, wall, false);
                    }
                }
            }
        }
        // Wall-mounted shelf directly under the ceiling at (3, 3, -2) -> adjacent inside room at (2, 3, -2)
        // Roof is solid at (3, 4, -2), above roof (3, 5, -2) is air!
        setTurned(lvl, ox, oy, oz, 3, 3, -2, turns, shelf1, true);
        // Second shelf on another wall at (-2, 2, 3)
        setTurned(lvl, ox, oy, oz, -2, 2, 3, turns, shelf2, true);
    }

    private static BlockPos turnPos(int ox, int oy, int oz, int dx, int dy, int dz, int turns) {
        int[] t = NpcGraphAnchorCore420.turn(new int[] {dx, dy, dz}, turns);
        return new BlockPos(ox + t[0], oy + t[1], oz + t[2]);
    }

    private static void setTurned(MockLevel lvl, int ox, int oy, int oz, int dx, int dy, int dz,
            int turns, BlockState st, boolean isChest) {
        BlockPos p = turnPos(ox, oy, oz, dx, dy, dz, turns);
        if (isChest) lvl.putChest(p, st);
        else lvl.putBlock(p, st);
    }

    private static void testRoomBuildAndImmediateSave(MockServer server, MockLevel overworld,
            MockBlock stone, MockBlock wallBlock, MockBlock shelfBlock, MockBlock kitchenShelfBlock) {
        // Build room around (0, 0, 0) in overworld with NO BlockTrigger351 at all
        buildRoom(overworld, 0, 0, 0, 0, stone.defaultBlockState(), wallBlock.defaultBlockState(),
                shelfBlock.defaultBlockState(), kitchenShelfBlock.defaultBlockState());
        MockPlayer player = new MockPlayer(overworld, 0.5, 1.0, 0.5);
        server.players.clear();
        server.players.add(player);

        BlockPos shelf1 = new BlockPos(3, 3, -2);
        String key1 = ChestGraph396Store.key(overworld, shelf1);
        CompoundTag saveTag = new CompoundTag();
        saveTag.put("DialogueGraph", makeShelfLootGraph("shelf_pool.txt").save());

        ChestGraph396.save(player, ChestGraph396.idFor(key1), saveTag);

        check(overworld.entities.size() == 1,
                "saving first furniture shelf graph must immediately spawn 1 anchor marker");
        Entity marker = overworld.entities.get(0);
        CompoundTag mData = NpcGraphAnchor420.data(marker);
        check(NpcGraphAnchorCore420.hasGraphs(mData), "marker carries anchor graphs immediately");
        check("minecraft:overworld".equals(mData.getString("SettledLandsAnchorDim")),
                "marker records SettledLandsAnchorDim=minecraft:overworld");
        // Marker must NOT climb above the room roof (y=4 is roof, y>=5 is outside!)
        int markerY = (int) Math.floor(marker.getY());
        check(markerY >= 1 && markerY <= 3,
                "freeSpot placed marker inside room (y=" + markerY + "), not above roof (y>=5)");
        List<NpcGraphAnchorCore420.Entry> entries = NpcGraphAnchorCore420.entries(mData);
        check(entries.size() == 1 && "chest".equals(entries.get(0).kind),
                "marker contains 1 chest/furniture entry");

        // Save second shelf inside the same room -> updates existing marker, no duplicate
        BlockPos shelf2 = new BlockPos(-2, 2, 3);
        String key2 = ChestGraph396Store.key(overworld, shelf2);
        ChestGraph396.save(player, ChestGraph396.idFor(key2), saveTag);
        check(overworld.entities.size() == 1, "second shelf reuses existing anchor marker");
        entries = NpcGraphAnchorCore420.entries(NpcGraphAnchor420.data(marker));
        check(entries.size() == 2, "existing anchor immediately refreshed with both shelves");
    }

    private static void testDeadZone25To32AndClearAndSealHere(MockServer server, MockLevel overworld,
            MockBlock shelfBlock, MockBlock kitchenShelfBlock) {
        MockPlayer player = new MockPlayer(overworld, 15.0, 1.0, 0.0);
        server.players.clear();
        server.players.add(player);

        Entity marker = overworld.entities.get(0);
        BlockPos anchorHome = NpcGraphAnchor420.home(overworld, marker);
        // Place shelf 28 blocks from marker (old code had 24..32 dead zone!)
        BlockPos farShelf = anchorHome.offset(28, 0, 0);
        overworld.putChest(farShelf, kitchenShelfBlock.defaultBlockState());
        String farKey = ChestGraph396Store.key(overworld, farShelf);
        CompoundTag saveTag = new CompoundTag();
        saveTag.put("DialogueGraph", makeShelfLootGraph("shelf_pool.txt").save());
        ChestGraph396.save(player, ChestGraph396.idFor(farKey), saveTag);

        check(overworld.entities.size() == 1,
                "shelf at distance 28 must be covered by existing marker (no 24..32 dead zone duplicate)");
        check(NpcGraphAnchorCore420.entries(NpcGraphAnchor420.data(marker)).size() == 3,
                "shelf at distance 28 was gathered into the marker (size=3)");

        // Clear all three shelves -> marker tags must be cleared cleanly
        CompoundTag emptySave = new CompoundTag();
        emptySave.put("DialogueGraph", new NpcDialogueGraph().save());
        ChestGraph396.save(player, ChestGraph396.idFor(ChestGraph396Store.key(overworld, new BlockPos(3, 3, -2))),
                emptySave);
        ChestGraph396.save(player, ChestGraph396.idFor(ChestGraph396Store.key(overworld, new BlockPos(-2, 2, 3))),
                emptySave);
        ChestGraph396.save(player, ChestGraph396.idFor(farKey), emptySave);
        check(!NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(marker)),
                "clearing all graphs clears stale anchor tags");

        // Re-add one shelf via ChestGraph396Store + periodic pass(server) (no immediate save call)
        ChestGraph396Store.get(server).put(ChestGraph396Store.key(overworld, new BlockPos(3, 3, -2)),
                makeShelfLootGraph("shelf_pool.txt"));
        player = new MockPlayer(overworld, 3.0, 2.0, -2.0);
        server.players.clear();
        server.players.add(player);
        int work = NpcGraphAnchor420.pass(server);
        check(work > 0, "periodic NpcGraphAnchor420.pass picks up ChestGraph396Store-only structures");

        int countBefore = overworld.entities.size();
        check(NpcGraphAnchor420.sealHere(overworld, new BlockPos(3, 3, -2)),
                "sealHere succeeds");
        check(overworld.entities.size() == countBefore,
                "sealHere reuses existing covering marker instead of spawning duplicate");
    }

    private static void testDoorOnlyStructureAutoAnchor(MockServer server, MockLevel overworld) {
        // Place a door graph / lock far away at (200, 10, 200) with no block triggers
        BlockPos doorPos = new BlockPos(200, 10, 200);
        DoorStore368 locks = DoorStore368.get(server);
        locks.lock(overworld, doorPos);
        DoorGraph382Store doors = DoorGraph382Store.get(server);
        doors.put(DoorStore368.key(overworld, doorPos), DoorGraph382.defaultGraph(true));

        MockPlayer player = new MockPlayer(overworld, 202.0, 10.0, 202.0);
        server.players.clear();
        server.players.add(player);
        int before = overworld.entities.size();
        NpcGraphAnchor420.pass(server);
        check(overworld.entities.size() > before,
                "door-only structure receives an automatic anchor marker on periodic pass");
        Entity doorMarker = NpcGraphAnchor420.markerNear(overworld, doorPos);
        check(doorMarker != null && NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(doorMarker)),
                "door marker carries graphs and locks");
    }

    private static void testDungeonOriginCollisionAndFallback(MockServer server, MockLevel overworld,
            MockLevel dungeon, MockBlock stone, MockBlock wallBlock, MockBlock netherBricks,
            MockBlock shelfBlock, MockBlock kitchenShelfBlock, Item diamond) {
        // Find the sealed marker from overworld (0,0,0) with shelves
        overworld.entities.clear();
        ChestGraph396Store store = ChestGraph396Store.get(server);
        // Clear overworld chest entries cleanly
        for (String k : new ArrayList<>(store.keys())) store.put(k, null);

        BlockPos shelf1 = new BlockPos(3, 3, -2);
        BlockPos shelf2 = new BlockPos(-2, 2, 3);
        MockPlayer owPlayer = new MockPlayer(overworld, 0.5, 1.0, 0.5);
        server.players.clear();
        server.players.add(owPlayer);
        CompoundTag saveTag = new CompoundTag();
        saveTag.put("DialogueGraph", makeShelfLootGraph("shelf_pool.txt").save());
        ChestGraph396.save(owPlayer, ChestGraph396.idFor(ChestGraph396Store.key(overworld, shelf1)), saveTag);
        ChestGraph396.save(owPlayer, ChestGraph396.idFor(ChestGraph396Store.key(overworld, shelf2)), saveTag);

        Entity owMarker = overworld.entities.get(0);
        CompoundTag carriedNbt = NpcGraphAnchor420.data(owMarker).copy();

        // Case A: hell_dungeon instance #0 at (0, 0, 0) - IDENTICAL coordinates to overworld build!
        buildRoom(dungeon, 0, 0, 0, 0, stone.defaultBlockState(), wallBlock.defaultBlockState(),
                shelfBlock.defaultBlockState(), kitchenShelfBlock.defaultBlockState());
        Entity dMarker0 = new Entity();
        dMarker0.moveTo(owMarker.getX(), owMarker.getY(), owMarker.getZ());
        NpcGraphAnchor420.data(dMarker0).put("Graphs", carriedNbt.getList("Graphs", 10));
        NpcGraphAnchor420.data(dMarker0).put("FP", carriedNbt.getList("FP", 10));
        NpcGraphAnchor420.data(dMarker0).putInt("SettledLandsAnchor", carriedNbt.getInt("SettledLandsAnchor"));
        NpcGraphAnchor420.data(dMarker0).putIntArray("SettledLandsAnchorAt",
                carriedNbt.getIntArray("SettledLandsAnchorAt"));
        // Copy dimension tag (Overworld) -> same (x,y,z) in hell_dungeon must NOT skip!
        NpcGraphAnchor420.data(dMarker0).putString("SettledLandsAnchorDim",
                carriedNbt.getString("SettledLandsAnchorDim"));

        int placed0 = LootPoolManager436.onDungeonRoomPlaced(dungeon, Collections.singletonList(dMarker0), 0);
        check(placed0 > 0, "dungeon room at (0,0,0) matching Overworld coordinates must materialise and populate loot");
        check(store.graph(ChestGraph396Store.key(dungeon, shelf1)) != null
                && store.graph(ChestGraph396Store.key(dungeon, shelf2)) != null,
                "both furniture shelves have graphs in ChestGraph396Store in hell_dungeon at (0,0,0)");
        MockChestBE be1 = (MockChestBE) dungeon.getBlockEntity(shelf1);
        check(be1 != null && countItems(be1, diamond) == 3,
                "shelf #1 in dungeon at (0,0,0) populated with 3 diamonds from shelf_pool.txt");

        // Case B: legacy marker WITHOUT SettledLandsAnchorDim at (0,0,0) (e.g. from Build 436)
        // Clear dungeon keys & re-run on fresh room at (0,0,0) with legacy marker
        store.put(ChestGraph396Store.key(dungeon, shelf1), null);
        store.put(ChestGraph396Store.key(dungeon, shelf2), null);
        ((MockChestBE) dungeon.getBlockEntity(shelf1)).slots[0] = ItemStack.EMPTY;
        Entity legacyMarker = new Entity();
        legacyMarker.moveTo(owMarker.getX(), owMarker.getY(), owMarker.getZ());
        NpcGraphAnchor420.data(legacyMarker).put("Graphs", carriedNbt.getList("Graphs", 10));
        NpcGraphAnchor420.data(legacyMarker).put("FP", carriedNbt.getList("FP", 10));
        NpcGraphAnchor420.data(legacyMarker).putInt("SettledLandsAnchor", carriedNbt.getInt("SettledLandsAnchor"));
        NpcGraphAnchor420.data(legacyMarker).putIntArray("SettledLandsAnchorAt",
                carriedNbt.getIntArray("SettledLandsAnchorAt"));
        // Intentionally NO SettledLandsAnchorDim!
        int placedLegacy = LootPoolManager436.onDungeonRoomPlaced(
                dungeon, Collections.singletonList(legacyMarker), 0);
        check(placedLegacy > 0
                && store.graph(ChestGraph396Store.key(dungeon, shelf1)) != null,
                "legacy marker without SettledLandsAnchorDim at (0,0,0) still materialises in hell_dungeon");

        // Case C: rotated room at (512, 0, 512) with turns=1 AND >60% of fingerprint blocks replaced
        int rx = 512, ry = 0, rz = 512, turns = 1;
        buildRoom(dungeon, rx, ry, rz, turns, netherBricks.defaultBlockState(), netherBricks.defaultBlockState(),
                shelfBlock.defaultBlockState(), kitchenShelfBlock.defaultBlockState());
        int[] turnedAnchorOffset = NpcGraphAnchorCore420.turn(
                new int[] {(int) Math.floor(owMarker.getX()), (int) Math.floor(owMarker.getY()),
                        (int) Math.floor(owMarker.getZ())},
                turns);
        Entity turnedMarker = new Entity();
        turnedMarker.moveTo(rx + turnedAnchorOffset[0] + 0.5, ry + turnedAnchorOffset[1],
                rz + turnedAnchorOffset[2] + 0.5);
        NpcGraphAnchor420.data(turnedMarker).put("Graphs", carriedNbt.getList("Graphs", 10));
        NpcGraphAnchor420.data(turnedMarker).put("FP", carriedNbt.getList("FP", 10));
        NpcGraphAnchor420.data(turnedMarker).putInt("SettledLandsAnchor", carriedNbt.getInt("SettledLandsAnchor"));
        NpcGraphAnchor420.data(turnedMarker).putIntArray("SettledLandsAnchorAt",
                carriedNbt.getIntArray("SettledLandsAnchorAt"));
        NpcGraphAnchor420.data(turnedMarker).putString("SettledLandsAnchorDim", "minecraft:overworld");

        int placedTurned = LootPoolManager436.onDungeonRoomPlaced(
                dungeon, Collections.singletonList(turnedMarker), turns);
        check(placedTurned > 0,
                "rotated dungeon room with altered fingerprint falls back to knownTurns and populates loot");
        BlockPos rotShelf1 = turnPos(rx, ry, rz, 3, 3, -2, turns);
        BlockPos rotShelf2 = turnPos(rx, ry, rz, -2, 2, 3, turns);
        check(store.graph(ChestGraph396Store.key(dungeon, rotShelf1)) != null
                && store.graph(ChestGraph396Store.key(dungeon, rotShelf2)) != null,
                "rotated furniture shelves restored at exact turned world coordinates");
        MockChestBE rotBe2 = (MockChestBE) dungeon.getBlockEntity(rotShelf2);
        check(rotBe2 != null && countItems(rotBe2, diamond) == 3,
                "rotated shelf #2 populated with 3 diamonds");
    }

    private static int countItems(Container c, Item item) {
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s != null && !s.isEmpty() && s.getItem() == item) total += s.getCount();
        }
        return total;
    }
}
