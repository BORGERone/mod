import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.dungeon.DungeonNbt431;
import dev.settledlands.dungeon.DungeonTemplate425;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.BlockTriggerStore351;
import dev.settledlands.npc.NpcContent;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphAnchor420;
import dev.settledlands.npc.NpcGraphAnchorCore420;
import dev.settledlands.npc.RadiusPass354;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Build 438 End-to-End JVM verification against the real compiled classes in the JAR.
 *
 * <ol>
 *   <li><b>Shelf &amp; container wand highlight</b> ({@link RadiusPass354}): holding the NPC wand emits
 *       8 {@code ELECTRIC_SPARK} corners around furniture shelves/cabinets/crates AND blocks, keeps
 *       doors unhighlighted, raises highlight limit to 64, and sorts closest-first;</li>
 *   <li><b>Embedded {@code BlockEntity} chest graph persistence &amp; in-world Axiom paste</b>
 *       ({@link ChestGraph396}, {@link NpcGraphAnchor420}): graphs + locks + loot pool save into
 *       {@code SettledLandsChestGraph}, clear cleanly when emptied, restore automatically from loaded
 *       chunks when pasted without entities, and trigger anchors &amp; highlights immediately;</li>
 *   <li><b>In-room {@code freeSpot} enclosure scoring</b> ({@link NpcGraphAnchor420#freeSpot}): wall-mounted
 *       shelves adjacent to an exterior wall spawn markers inside the room under the ceiling rather than
 *       outside exterior walls;</li>
 *   <li><b>Axiom dungeon rooms with NO entities</b> ({@link DungeonTemplate425}, {@link DungeonManager425}):
 *       detects {@link DungeonTemplate425#hasEmbeddedChestGraphs}, restores shelf graphs + locks, rolls
 *       loot pools once (no double-roll when both anchor &amp; BE carry it), seals existing empty
 *       {@code guide_trigger} or spawns/seals a fresh live anchor in-room;</li>
 *   <li><b>Start-room tick-0 anchor rescue</b> ({@link NpcGraphAnchor420#ensureForPlayer}): if initial
 *       entity spawn before teleport is rejected, entering the dungeon immediately creates the live
 *       anchor on tick 0.</li>
 * </ol>
 */
public final class Build438Probe {

    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) throw new AssertionError("Build438Probe FAILED: " + what);
    }

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

    private static final class MockChunk extends LevelChunk {
        final Map<BlockPos, BlockEntity> bes = new HashMap<>();

        @Override
        public Map<BlockPos, BlockEntity> getBlockEntities() {
            return bes;
        }
    }

    private static final class ParticleEvent {
        final ParticleOptions type;
        final double x, y, z;

        ParticleEvent(ParticleOptions type, double x, double y, double z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class MockLevel extends ServerLevel {
        final ResourceKey<Level> dimKey;
        final MinecraftServer server;
        final DimensionDataStorage storage = new DimensionDataStorage();
        final Map<Long, BlockState> blocks = new HashMap<>();
        final Map<Long, BlockEntity> bes = new HashMap<>();
        final Map<Long, MockChunk> chunks = new HashMap<>();
        final List<Entity> entities = new ArrayList<>();
        final List<ParticleEvent> particles = new ArrayList<>();
        final BlockState airState;
        long gameTime = 2L;
        boolean rejectEntityAdd = false;

        final ServerChunkCache chunkCache = new ServerChunkCache() {
            @Override
            public LevelChunk getChunkNow(int chunkX, int chunkZ) {
                return chunks.get(chunkKey(chunkX, chunkZ));
            }
        };

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

        private static long chunkKey(int cx, int cz) {
            return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
        }

        void putBlock(BlockPos pos, BlockState state) {
            blocks.put(pack(pos), state);
        }

        MockChestBE putChest(BlockPos pos, BlockState state) {
            putBlock(pos, state);
            MockChestBE be = new MockChestBE();
            bes.put(pack(pos), be);
            MockChunk chunk = chunks.computeIfAbsent(
                    chunkKey(pos.getX() >> 4, pos.getZ() >> 4),
                    k -> new MockChunk());
            chunk.bes.put(pos, be);
            return be;
        }

        @Override public ResourceKey<Level> dimension() { return dimKey; }
        @Override public MinecraftServer getServer() { return server; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public boolean hasChunkAt(BlockPos pos) { return true; }
        @Override public long getGameTime() { return gameTime; }
        @Override public ServerChunkCache getChunkSource() { return chunkCache; }

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
            if (rejectEntityAdd) return false;
            if (entity != null && !entities.contains(entity)) {
                entities.add(entity);
            }
            return true;
        }

        @Override
        public int sendParticles(ParticleOptions particle, double x, double y, double z,
                int count, double dx, double dy, double dz, double speed) {
            particles.add(new ParticleEvent(particle, x, y, z));
            return count;
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
        private double px, py, pz;
        private ItemStack mainHand = ItemStack.EMPTY;

        MockPlayer(ServerLevel lvl, double x, double y, double z) {
            this.lvl = lvl;
            this.px = x;
            this.py = y;
            this.pz = z;
        }

        void move(double x, double y, double z) {
            this.px = x;
            this.py = y;
            this.pz = z;
        }

        void hold(ItemStack stack) {
            this.mainHand = stack == null ? ItemStack.EMPTY : stack;
        }

        @Override public ItemStack getItemInHand(InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND ? mainHand : ItemStack.EMPTY;
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
        Path poolDir = LootPoolManager436.ensureDirectory();
        Files.writeString(poolDir.resolve("build438_pool.txt"),
                "minecraft:diamond | 10 | 100 | 4\n", StandardCharsets.UTF_8);

        SimpleReg<Block> blockReg = new SimpleReg<>();
        SimpleReg<EntityType> entityReg = new SimpleReg<>();
        SimpleReg<Item> itemReg = new SimpleReg<>();

        MockBlock air = new MockBlock(true);
        MockBlock stone = new MockBlock(false);
        MockBlock wallBlock = new MockBlock(false);
        MockBlock netherBricks = new MockBlock(false);
        MockBlock shelfBlock = new MockBlock(false);
        MockBlock kitchenShelf = new MockBlock(false);
        MockBlock crateBlock = new MockBlock(false);
        MockBlock doorBlock = new MockBlock(false);

        blockReg.put("minecraft:air", air);
        blockReg.put("minecraft:stone", stone);
        blockReg.put("minecraft:oak_planks", wallBlock);
        blockReg.put("minecraft:nether_bricks", netherBricks);
        blockReg.put("refurbished_furniture:oak_storage_cabinet", shelfBlock);
        blockReg.put("refurbished_furniture:oak_kitchen_storage_cabinet", kitchenShelf);
        blockReg.put("refurbished_furniture:oak_crate", crateBlock);
        blockReg.put("minecraft:oak_door", doorBlock);

        EntityType guideTriggerType = new EntityType("settledlands:guide_trigger", () -> new Entity());
        entityReg.put("settledlands:guide_trigger", guideTriggerType);

        Item diamond = new Item();
        Item wandItem = new Item();
        itemReg.put("minecraft:diamond", diamond);
        itemReg.put("settledlands:npc_wand", wandItem);

        java.lang.reflect.Field wandField = NpcContent.class.getDeclaredField("wand");
        wandField.setAccessible(true);
        wandField.set(null, wandItem);

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

        testShelfWandHighlightAndOrdering(server, overworld, stone, shelfBlock, kitchenShelf,
                crateBlock, doorBlock, wandItem);
        testEmbeddedChestSavePasteAndIndoorFreeSpot(server, overworld, stone, wallBlock, shelfBlock);
        testDungeonNoEntitiesEmbeddedShelfSpawnsAnchor(server, dungeon, netherBricks, wallBlock,
                shelfBlock, diamond);
        testDungeonEmptyTriggerReusedAndNoDoubleRoll(server, dungeon, netherBricks, shelfBlock,
                kitchenShelf, diamond);
        testDungeonStartRoomRejectedEntityRescuedOnEntry(server, dungeon, netherBricks, wallBlock,
                shelfBlock, diamond);

        System.out.println("Build438Probe: " + checks + " checks passed");
    }

    private static NpcDialogueGraph makeShelfGraph(String poolFile, boolean locked, int keys) {
        NpcDialogueGraph g = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node chest = ChestGraph396.chestNode(g);
        if (locked) {
            ChestLock397.setLocked(chest, true);
            ChestLock397.setKeys(chest, keys);
            ChestLock397.setSeconds(chest, 9);
            ChestLock397.setDifficulty(chest, 2);
        }
        NpcDialogueGraph.Node pool = g.add(NpcDialogueGraph.TRIGGER, 260, 120);
        pool.action = "trigger:loot_pool:" + poolFile + "|50|100|1";
        chest.next = pool.id;
        return g;
    }

    private static Set<String> sparkCornersByBlock(List<ParticleEvent> events) {
        Set<String> blocks = new HashSet<>();
        for (ParticleEvent ev : events) {
            if (ev.type != ParticleTypes.ELECTRIC_SPARK) continue;
            int bx = (int) Math.floor(ev.x - 0.1D);
            // Sparks are emitted at bx, bx+1; only lower corners tell exact block pos reliably,
            // so group bottom-south-west corner (ev.x, ev.y == by+0.05, ev.z) where fractional y ~ 0.05.
            double fracY = ev.y - Math.floor(ev.y);
            if (fracY > 0.01D && fracY < 0.2D) {
                int x = (int) Math.round(ev.x);
                int y = (int) Math.floor(ev.y);
                int z = (int) Math.round(ev.z);
                // Any bottom corner exists at (bx,by,bz), (bx+1,by,bz), (bx,by,bz+1), (bx+1,by,bz+1)
                blocks.add(x + "," + y + "," + z);
            }
        }
        return blocks;
    }

    private static boolean hasAllEightSparksAt(List<ParticleEvent> events, BlockPos pos) {
        boolean[] seen = new boolean[8];
        for (ParticleEvent ev : events) {
            if (ev.type != ParticleTypes.ELECTRIC_SPARK) continue;
            for (int corner = 0; corner < 8; corner++) {
                double ex = pos.getX() + ((corner & 1) != 0 ? 1.0D : 0.0D);
                double ey = pos.getY() + ((corner & 2) != 0 ? 1.0D : 0.05D);
                double ez = pos.getZ() + ((corner & 4) != 0 ? 1.0D : 0.0D);
                if (Math.abs(ev.x - ex) < 1e-6 && Math.abs(ev.y - ey) < 1e-6 && Math.abs(ev.z - ez) < 1e-6) {
                    seen[corner] = true;
                }
            }
        }
        for (boolean b : seen) if (!b) return false;
        return true;
    }

    private static ServerTickEvent.Post tickEvent(MinecraftServer server) {
        return new ServerTickEvent.Post() {
            @Override public MinecraftServer getServer() { return server; }
        };
    }

    // ---- Test 1: Wand highlights shelves/cabinets/crates AND blocks, cap=64, nearest-first ----

    private static void testShelfWandHighlightAndOrdering(
            MockServer server, MockLevel ow, MockBlock stone, MockBlock shelf,
            MockBlock kitchenShelf, MockBlock crate, MockBlock door, Item wandItem) {
        MockPlayer player = new MockPlayer(ow, 10.5D, 64.0D, 10.5D);
        server.players.clear();
        server.players.add(player);

        BlockPos shelfPos = new BlockPos(11, 64, 10);
        BlockPos blockPos = new BlockPos(14, 64, 10);
        BlockPos kitchenPos = new BlockPos(17, 64, 10);
        BlockPos doorPos = new BlockPos(20, 64, 10);
        BlockPos cratePos = new BlockPos(23, 64, 10);

        ow.putChest(shelfPos, shelf.defaultBlockState());
        ow.putChest(kitchenPos, kitchenShelf.defaultBlockState());
        ow.putChest(cratePos, crate.defaultBlockState());
        ow.putBlock(blockPos, stone.defaultBlockState());
        ow.putBlock(doorPos, door.defaultBlockState());

        ChestGraph396Store chestStore = ChestGraph396Store.get(server);
        chestStore.put(ChestGraph396Store.key(ow, shelfPos), makeShelfGraph("build438_pool.txt", false, 6));
        chestStore.put(ChestGraph396Store.key(ow, kitchenPos), ChestGraph396.defaultGraph());
        chestStore.put(ChestGraph396Store.key(ow, cratePos), ChestGraph396.defaultGraph());

        BlockTriggerStore351 blockStore = BlockTriggerStore351.get(server);
        blockStore.put(BlockTriggerStore351.key(ow, blockPos), new NpcDialogueGraph());

        DoorGraph382Store doorStore = DoorGraph382Store.get(server);
        doorStore.put(ChestGraph396Store.key(ow, doorPos), new NpcDialogueGraph());

        // Not holding wand -> zero sparks
        ow.particles.clear();
        player.hold(ItemStack.EMPTY);
        ow.gameTime = 2L;
        RadiusPass354.onServerTick(tickEvent(server));
        check(ow.particles.isEmpty(), "holding empty hand emits zero wand sparks");

        // Holding wand -> 8 sparks on all 3 furniture containers + 1 block = 32 sparks, 0 on door
        ow.particles.clear();
        player.hold(new ItemStack(wandItem, 1));
        ow.gameTime = 4L;
        RadiusPass354.onServerTick(tickEvent(server));
        check(hasAllEightSparksAt(ow.particles, shelfPos), "oak_storage_cabinet shelf highlighted with 8 sparks");
        check(hasAllEightSparksAt(ow.particles, kitchenPos), "kitchen_storage_cabinet shelf highlighted with 8 sparks");
        check(hasAllEightSparksAt(ow.particles, cratePos), "oak_crate highlighted with 8 sparks");
        check(hasAllEightSparksAt(ow.particles, blockPos), "BlockTrigger351 block highlighted with 8 sparks");
        check(!hasAllEightSparksAt(ow.particles, doorPos), "doors remain unhighlighted per Build 382 rule");
        check(ow.particles.size() == 32, "exact 4*8=32 sparks emitted, got " + ow.particles.size());

        // Add 68 more shelves within 22 blocks; verify cap=64 (512 sparks) and farthest ones dropped, nearest kept
        for (int i = 0; i < 68; i++) {
            int sx = 10 + (i % 10);
            int sz = 12 + (i / 10) * 2;
            BlockPos p = new BlockPos(sx, 64, sz);
            ow.putChest(p, shelf.defaultBlockState());
            chestStore.put(ChestGraph396Store.key(ow, p), ChestGraph396.defaultGraph());
        }
        ow.particles.clear();
        ow.gameTime = 6L;
        RadiusPass354.onServerTick(tickEvent(server));
        check(ow.particles.size() == 64 * 8, "MAX_HIGHLIGHT=64 caps sparks at 512, got " + ow.particles.size());
        check(hasAllEightSparksAt(ow.particles, shelfPos), "nearest shelf stays highlighted under 64-cap");
        check(!hasAllEightSparksAt(ow.particles, new BlockPos(19, 64, 24)),
                "farthest shelf beyond 64 closest slots is omitted while closer ones shine");

        // Clean up Test 1 entities/stores so later tests start fresh
        for (int i = 0; i < 68; i++) {
            BlockPos p = new BlockPos(10 + (i % 10), 64, 12 + (i / 10) * 2);
            chestStore.put(ChestGraph396Store.key(ow, p), null);
        }
        chestStore.put(ChestGraph396Store.key(ow, shelfPos), null);
        chestStore.put(ChestGraph396Store.key(ow, kitchenPos), null);
        chestStore.put(ChestGraph396Store.key(ow, cratePos), null);
        blockStore.put(BlockTriggerStore351.key(ow, blockPos), null);
        doorStore.put(ChestGraph396Store.key(ow, doorPos), null);
    }

    // ---- Test 2: Embedded BlockEntity graph persistence, Axiom paste (no entities), indoor freeSpot ----

    private static void buildWalledRoomWithExteriorShelf(MockLevel lvl, int ox, int oy, int oz,
            MockBlock floor, MockBlock wall, MockBlock shelf) {
        // Room spans x in [ox..ox+6], z in [oz..oz+6], floor at y=oy, ceiling at y=oy+4
        // Exterior air outside x < ox has NO ceiling and ONLY outdoor floor at y=oy!
        for (int dx = -2; dx <= 6; dx++) {
            for (int dz = 0; dz <= 6; dz++) {
                lvl.putBlock(new BlockPos(ox + dx, oy, oz + dz), floor.defaultBlockState());
                if (dx >= 0) {
                    lvl.putBlock(new BlockPos(ox + dx, oy + 4, oz + dz), wall.defaultBlockState());
                    if (dx == 0 || dx == 6 || dz == 0 || dz == 6) {
                        for (int dy = 1; dy <= 3; dy++) {
                            lvl.putBlock(new BlockPos(ox + dx, oy + dy, oz + dz), wall.defaultBlockState());
                        }
                    }
                }
            }
        }
        // Mount shelf at x = ox + 1 (flush inside west wall dx=0; cell dx=1,dz=2 is inside wall/shelf,
        // wait: put shelf AT dx=0? No, put shelf in front of dx=0 at (ox+1, oy+1, oz+3), AND put a gap in
        // west wall or shelf ON the wall at dx=0 with exterior air at dx=-1!).
        // Putting shelf ON the exterior wall at dx=0 guarantees exterior air at dx=-1 is a direct neighbour!
        lvl.putChest(new BlockPos(ox, oy + 1, oz + 3), shelf.defaultBlockState());
    }

    private static void testEmbeddedChestSavePasteAndIndoorFreeSpot(
            MockServer server, MockLevel ow, MockBlock stone, MockBlock wall, MockBlock shelf) {
        int ox = 50, oy = 64, oz = 50;
        buildWalledRoomWithExteriorShelf(ow, ox, oy, oz, stone, wall, shelf);
        BlockPos shelfPos = new BlockPos(ox, oy + 1, oz + 3);
        MockPlayer player = new MockPlayer(ow, ox + 2.5D, oy + 1.0D, oz + 3.5D);
        server.players.clear();
        server.players.add(player);

        // Save shelf graph + lock via ChestGraph396.save -> writes BE NeoForgeData + spawns indoor anchor
        NpcDialogueGraph g = makeShelfGraph("build438_pool.txt", true, 8);
        String key = ChestGraph396Store.key(ow, shelfPos);
        int id = ChestGraph396.idFor(key);
        CompoundTag pkt = new CompoundTag();
        pkt.put("DialogueGraph", g.save());
        ChestGraph396.save(player, id, pkt);

        MockChestBE be = (MockChestBE) ow.getBlockEntity(shelfPos);
        CompoundTag pd = ((IBlockEntityExtension) be).getPersistentData();
        check(pd.contains(ChestGraph396.BE_GRAPH_TAG, 10),
                "ChestGraph396.save persists SettledLandsChestGraph on BlockEntity NeoForgeData");

        NpcDialogueGraph decoded = ChestGraph396.readBlockEntityGraph(be);
        check(decoded != null && decoded.nodes.size() >= 2
                        && ChestLock397.locked(ChestGraph396.chestNode(decoded))
                        && ChestLock397.keys(ChestGraph396.chestNode(decoded)) == 8,
                "readBlockEntityGraph preserves nodes & lock settings");

        // Verify anchor spawned strictly INSIDE the room (x > ox), never outside west wall (x < ox)
        Entity anchor = NpcGraphAnchor420.markerNear(ow, shelfPos);
        check(anchor != null, "anchor created immediately on ChestGraph396.save");
        int ax = (int) Math.floor(anchor.getX());
        int ay = (int) Math.floor(anchor.getY());
        int az = (int) Math.floor(anchor.getZ());
        check(ax >= ox + 1 && ax <= ox + 5 && ay >= oy + 1 && ay <= oy + 3 && az >= oz + 1 && az <= oz + 5,
                "freeSpot placed anchor indoors at (" + ax + "," + ay + "," + az + ") instead of exterior");

        // Simulate Axiom Copy (with copyWithEntities=false) -> Paste into (200, 70, 200)
        int px = 200, py = 70, pz = 200;
        buildWalledRoomWithExteriorShelf(ow, px, py, pz, stone, wall, shelf);
        BlockPos pastedShelf = new BlockPos(px, py + 1, pz + 3);
        MockChestBE pastedBe = (MockChestBE) ow.getBlockEntity(pastedShelf);
        ((IBlockEntityExtension) pastedBe).getPersistentData()
                .put(ChestGraph396.BE_GRAPH_TAG, pd.getCompound(ChestGraph396.BE_GRAPH_TAG).copy());

        ChestGraph396Store store = ChestGraph396Store.get(server);
        check(store.graph(ChestGraph396Store.key(ow, pastedShelf)) == null,
                "pasted shelf has no ChestGraph396Store entry before anchor pass");

        player.move(px + 2.5D, py + 1.0D, pz + 3.5D);
        NpcGraphAnchor420.pass(server);

        NpcDialogueGraph restored = store.graph(ChestGraph396Store.key(ow, pastedShelf));
        check(restored != null && ChestLock397.locked(ChestGraph396.chestNode(restored))
                        && ChestLock397.keys(ChestGraph396.chestNode(restored)) == 8,
                "Axiom paste without entities restores shelf graph & lock from BlockEntity NeoForgeData");

        Entity pastedAnchor = NpcGraphAnchor420.markerNear(ow, pastedShelf);
        check(pastedAnchor != null
                        && (int) Math.floor(pastedAnchor.getX()) >= px + 1
                        && NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(pastedAnchor)),
                "pasted shelf receives indoor live guide_trigger anchor automatically");

        // Clearing graph clears SettledLandsChestGraph from BlockEntity as well
        CompoundTag clearPkt = new CompoundTag();
        clearPkt.put("DialogueGraph", new CompoundTag());
        player.move(ox + 2.5D, oy + 1.0D, oz + 3.5D);
        ChestGraph396.save(player, id, clearPkt);
        check(!((IBlockEntityExtension) be).getPersistentData().contains(ChestGraph396.BE_GRAPH_TAG),
                "clearing chest graph removes SettledLandsChestGraph from BlockEntity");
    }

    // ---- Test 3: Axiom .bp room in hell_dungeon with ZERO entities restores graph, loot & anchor ----

    private static CompoundTag makeBeNbtWithChestGraph(String id, int x, int y, int z, NpcDialogueGraph g) {
        CompoundTag root = new CompoundTag();
        root.putString("id", id);
        root.putInt("x", x);
        root.putInt("y", y);
        root.putInt("z", z);
        CompoundTag nf = new CompoundTag();
        nf.put(ChestGraph396.BE_GRAPH_TAG, g.save());
        root.put("NeoForgeData", nf);
        return root;
    }

    private static DungeonTemplate425.StartTemplate makeDungeonRoomTemplate(
            NpcDialogueGraph shelfGraph, List<DungeonTemplate425.EntitySpec> entities) {
        List<DungeonTemplate425.PaletteEntry> palette = new ArrayList<>();
        palette.add(new DungeonTemplate425.PaletteEntry("minecraft:nether_bricks", Collections.emptyMap()));
        palette.add(new DungeonTemplate425.PaletteEntry("refurbished_furniture:oak_storage_cabinet",
                Collections.emptyMap()));

        List<Integer> rawBlocks = new ArrayList<>();
        // 5x5x5 closed room [0..4] x [0..4] x [0..4], shelf at (1, 1, 2)
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    if (x == 1 && y == 1 && z == 2) {
                        rawBlocks.add(x); rawBlocks.add(y); rawBlocks.add(z); rawBlocks.add(1);
                    } else if (y == 0 || y == 4 || x == 0 || x == 4 || z == 0 || z == 4) {
                        rawBlocks.add(x); rawBlocks.add(y); rawBlocks.add(z); rawBlocks.add(0);
                    }
                }
            }
        }
        int[] blocks = new int[rawBlocks.size()];
        for (int i = 0; i < rawBlocks.size(); i++) blocks[i] = rawBlocks.get(i);

        CompoundTag beNbt = makeBeNbtWithChestGraph("refurbished_furniture:oak_storage_cabinet", 1, 1, 2, shelfGraph);
        List<DungeonTemplate425.BlockEntitySpec> beSpecs = Collections.singletonList(
                new DungeonTemplate425.BlockEntitySpec("refurbished_furniture:oak_storage_cabinet",
                        1, 1, 2, beNbt));

        return new DungeonTemplate425.StartTemplate(palette, blocks, beSpecs, entities, null);
    }

    private static void testDungeonNoEntitiesEmbeddedShelfSpawnsAnchor(
            MockServer server, MockLevel dungeon, MockBlock netherBricks, MockBlock wall,
            MockBlock shelf, Item diamond) {
        int ox = 500, oy = 64, oz = 500, turns = 1;
        NpcDialogueGraph g = makeShelfGraph("build438_pool.txt", true, 7);
        DungeonTemplate425.StartTemplate tpl = makeDungeonRoomTemplate(g, Collections.emptyList());

        check(DungeonTemplate425.hasEmbeddedChestGraphs(tpl),
                "hasEmbeddedChestGraphs detects SettledLandsChestGraph inside BlockEntitySpec");

        // Pre-populate MockLevel BE map for rotated shelf pos so dungeon.getBlockEntity finds MockChestBE
        // For turns=1: rotX(1, 2, 1) = -2, rotZ(1, 2, 1) = 1 -> (ox - 2, oy + 1, oz + 1)
        BlockPos shelfPos = new BlockPos(ox - 2, oy + 1, oz + 1);
        MockChestBE chestBe = dungeon.putChest(shelfPos, shelf.defaultBlockState());
        dungeon.entities.clear();

        DungeonTemplate425.PlacementStats stats = new DungeonTemplate425.PlacementStats();
        DungeonTemplate425.placeSlice(dungeon, tpl, ox, oy, oz, turns,
                "minecraft:nether_bricks", 0, tpl.blockCount(), stats);

        ChestGraph396Store store = ChestGraph396Store.get(server);
        NpcDialogueGraph restored = store.graph(ChestGraph396Store.key(dungeon, shelfPos));
        check(restored != null && ChestLock397.locked(ChestGraph396.chestNode(restored))
                        && ChestLock397.keys(ChestGraph396.chestNode(restored)) == 7,
                "dungeon room with ZERO entities restores embedded shelf graph & lock at rotated pos");
        check(countItems(chestBe, diamond) == 4,
                "embedded dungeon shelf rolled loot pool once (4 diamonds), got " + countItems(chestBe, diamond));
        check(dungeon.entities.size() == 1,
                "dungeon room with ZERO schematic entities spawned 1 live guide_trigger anchor, got "
                        + dungeon.entities.size());

        Entity spawned = dungeon.entities.get(0);
        check(NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(spawned)),
                "auto-spawned dungeon anchor carries sealed graphs NBT");
        int ax = (int) Math.floor(spawned.getX());
        int az = (int) Math.floor(spawned.getZ());
        // Interior rotated bounds for x in [1..3], z in [1..3] with turns=1 -> x in [ox-3..ox-1], z in [oz+1..oz+3]
        check(ax >= ox - 3 && ax <= ox - 1 && az >= oz + 1 && az <= oz + 3,
                "auto-spawned dungeon anchor sits indoors at (" + ax + "," + az + ")");

        // Wand highlight works immediately in hell_dungeon for the restored shelf
        MockPlayer dp = new MockPlayer(dungeon, shelfPos.getX() + 0.5D, oy + 1.0D, shelfPos.getZ() + 0.5D);
        dp.hold(new ItemStack(NpcContent.wand(), 1));
        server.players.clear();
        server.players.add(dp);
        dungeon.particles.clear();
        server.overworld.gameTime = 8L;
        RadiusPass354.onServerTick(tickEvent(server));
        check(hasAllEightSparksAt(dungeon.particles, shelfPos),
                "restored dungeon shelf emits 8 wand highlight sparks");
    }

    // ---- Test 4: Empty guide_trigger sealed & anchor+BE never double-roll loot ----

    private static void testDungeonEmptyTriggerReusedAndNoDoubleRoll(
            MockServer server, MockLevel dungeon, MockBlock netherBricks,
            MockBlock shelf, MockBlock kitchenShelf, Item diamond) {
        // Case A: empty guide_trigger in Entities + embedded shelf BE
        int ox = 700, oy = 64, oz = 700;
        NpcDialogueGraph g = makeShelfGraph("build438_pool.txt", false, 6);
        List<DungeonTemplate425.EntitySpec> emptyEnts = Collections.singletonList(
                new DungeonTemplate425.EntitySpec("settledlands:guide_trigger",
                        2.5D, 1.0D, 2.5D, 0.0F, 0.0F, new CompoundTag()));
        DungeonTemplate425.StartTemplate tplEmpty = makeDungeonRoomTemplate(g, emptyEnts);

        BlockPos shelfPosA = new BlockPos(ox + 1, oy + 1, oz + 2);
        MockChestBE beA = dungeon.putChest(shelfPosA, shelf.defaultBlockState());
        dungeon.entities.clear();

        DungeonTemplate425.placeSlice(dungeon, tplEmpty, ox, oy, oz, 0,
                "minecraft:nether_bricks", 0, tplEmpty.blockCount(), null);
        check(dungeon.entities.size() == 1
                        && NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(dungeon.entities.get(0))),
                "empty guide_trigger in schematic is reused and sealed instead of spawning a duplicate");
        check(countItems(beA, diamond) == 4, "Case A container populated with 4 diamonds");

        // Case B: guide_trigger WITH Graphs for same shelf + embedded BE on the shelf -> NO double roll!
        int bx = 800, by = 64, bz = 800;
        BlockPos shelfPosB = new BlockPos(bx + 1, by + 1, bz + 2);
        MockChestBE beB = dungeon.putChest(shelfPosB, kitchenShelf.defaultBlockState());
        dungeon.entities.clear();

        // Build carried Graphs CompoundTag referencing relative shelf (1 - 2, 1 - 1, 2 - 2) = (-1, 0, 0)
        NpcGraphAnchorCore420.Entry entry = new NpcGraphAnchorCore420.Entry(
                NpcGraphAnchorCore420.CHEST_KIND, -1, 0, 0, g.save(), null);
        CompoundTag markerData = NpcGraphAnchorCore420.write(
                Collections.singletonList(entry), Collections.emptyList(), Collections.emptyList());
        CompoundTag fullMarkerTag = new CompoundTag();
        fullMarkerTag.put("NeoForgeData", markerData);
        // Wait: Entity.load in test shim wouldn't unpack NeoForgeData automatically unless we copy into persistentData,
        // or wait - in TestShims Entity.load can mirror NeoForgeData -> persistentData! Let's ensure both work!
        List<DungeonTemplate425.EntitySpec> fullEnts = Collections.singletonList(
                new DungeonTemplate425.EntitySpec("settledlands:guide_trigger",
                        2.5D, 1.0D, 2.5D, 0.0F, 0.0F, fullMarkerTag));
        DungeonTemplate425.StartTemplate tplBoth = makeDungeonRoomTemplate(g, fullEnts);
        DungeonTemplate425.placeSlice(dungeon, tplBoth, bx, by, bz, 0,
                "minecraft:nether_bricks", 0, tplBoth.blockCount(), null);

        check(dungeon.entities.size() == 1, "exactly 1 anchor entity exists when anchor+BE both present");
        check(countItems(beB, diamond) == 4,
                "anchor + embedded BE never double-roll loot (expected 4 diamonds, got "
                        + countItems(beB, diamond) + ")");
    }

    // ---- Test 5: Start room entity spawn rejected before teleport -> ensureForPlayer creates anchor ----

    private static void testDungeonStartRoomRejectedEntityRescuedOnEntry(
            MockServer server, MockLevel dungeon, MockBlock netherBricks,
            MockBlock wall, MockBlock shelf, Item diamond) {
        int sx = 950, sy = 64, sz = 950;
        NpcDialogueGraph g = makeShelfGraph("build438_pool.txt", false, 6);
        DungeonTemplate425.StartTemplate tpl = makeDungeonRoomTemplate(g, Collections.emptyList());

        BlockPos shelfPos = new BlockPos(sx + 1, sy + 1, sz + 2);
        MockChestBE be = dungeon.putChest(shelfPos, shelf.defaultBlockState());
        dungeon.entities.clear();
        dungeon.rejectEntityAdd = true; // simulate non-ticking chunk during i==0 placement before teleport

        DungeonTemplate425.placeSlice(dungeon, tpl, sx, sy, sz, 0,
                "minecraft:nether_bricks", 0, tpl.blockCount(), null);
        check(dungeon.entities.isEmpty(), "rejected entity add left zero markers before teleport");
        check(ChestGraph396Store.get(server).graph(ChestGraph396Store.key(dungeon, shelfPos)) != null
                        && countItems(be, diamond) == 4,
                "start room shelf graph & loot still restored even when entity add failed pre-teleport");

        // Player teleports in; chunk is now ticking -> ensureForPlayer spawns anchor on tick 0
        dungeon.rejectEntityAdd = false;
        MockPlayer p = new MockPlayer(dungeon, sx + 2.5D, sy + 1.0D, sz + 2.5D);
        NpcGraphAnchor420.ensureForPlayer(p);

        check(dungeon.entities.size() == 1
                        && NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(dungeon.entities.get(0))),
                "NpcGraphAnchor420.ensureForPlayer creates & seals missing start-room anchor immediately on entry");
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
