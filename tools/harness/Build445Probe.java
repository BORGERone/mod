import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.CustomBook443;
import dev.settledlands.chest.CustomBookManager443;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.npc.NpcDialogueGraph;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * Headless JVM verification harness for Build 445:
 *   1. Exact user scenario from image-9.png (2.txt: Бедрок 2-6 | Редк. 5 | Вес 80,
 *      book_3 1 | Редк. 50 | Вес 10, node Шт: 3):
 *        - Displayed chances: 5% and 50%
 *        - Chests: book_3 drops ~50% (strictly 1 copy, never 2-3), bedrock ~5% (2-6 count)
 *        - Chiseled Bookshelves: book_3 places 1 copy ~50% of the time and empty ~50% (never 3!)
 *        - Lecterns: book_3 spawns ~50% of the time and empty ~50%
 *   2. Multi-book shelf pools, unbiased shuffle, non-book isolation, and stacked-entry Шт caps
 *   3. Exact agreement between computeEffectiveChancesPct and Monte-Carlo under node caps
 */
public final class Build445Probe {

    private static int checks;

    private static void req(boolean cond, String msg) {
        if (!cond) throw new AssertionError("FAIL: " + msg);
        checks++;
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
        @Override public java.util.Iterator<T> iterator() { return byId.values().iterator(); }
    }

    private static final class MockChestBE extends BlockEntity implements Container {
        final ItemStack[] slots = new ItemStack[27];

        MockChestBE() {
            Arrays.fill(slots, ItemStack.EMPTY);
        }

        @Override public int getContainerSize() { return slots.length; }
        @Override public ItemStack getItem(int slot) { return slots[slot]; }
        @Override public void setItem(int slot, ItemStack stack) {
            slots[slot] = stack == null ? ItemStack.EMPTY : stack;
        }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
    }

    private static final class MockLevel extends ServerLevel {
        final MinecraftServer srv;
        final ResourceKey<Level> dim;
        final DimensionDataStorage storage = new DimensionDataStorage();
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, BlockEntity> bes = new HashMap<>();

        MockLevel(MinecraftServer srv, ResourceKey<Level> dim) {
            this.srv = srv;
            this.dim = dim;
        }

        @Override public MinecraftServer getServer() { return srv; }
        @Override public ResourceKey<Level> dimension() { return dim; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }

        @Override public BlockState getBlockState(BlockPos pos) {
            BlockState s = states.get(pos);
            return s != null ? s : new BlockState(null, true);
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            states.put(pos.immutable(), state);
            return true;
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return bes.get(pos);
        }

        void putBlockEntity(BlockPos pos, BlockEntity be) {
            bes.put(pos.immutable(), be);
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState o, BlockState n, int flags) {}
    }

    private static final class MockServer extends MinecraftServer {
        MockLevel overworld;
        @Override public ServerLevel overworld() { return overworld; }
        @Override public ServerLevel getLevel(ResourceKey<Level> key) {
            return DungeonManager425.HELL_DUNGEON_LEVEL.equals(key) ? overworld : null;
        }
    }

    public static void main(String[] args) throws Exception {
        initRegistries();
        testExactImage9Scenario();
        testMultiBookShelfShuffleAndCaps();
        testNodeLimitsAndEffectiveChancesConsistency();
        System.out.println("Build445Probe: PASS (" + checks + " checks passed)");
    }

    private static void initRegistries() {
        SimpleReg<Item> itemReg = new SimpleReg<>();
        itemReg.put("minecraft:air", Items.AIR);
        itemReg.put("minecraft:bedrock", new Item());
        itemReg.put("minecraft:iron_sword", new Item());
        itemReg.put("minecraft:golden_sword", new Item());
        itemReg.put("minecraft:diamond_sword", new Item());
        itemReg.put("minecraft:stone_sword", new Item());
        itemReg.put("minecraft:book", Items.BOOK);
        itemReg.put("minecraft:writable_book", Items.WRITABLE_BOOK);
        itemReg.put("minecraft:written_book", Items.WRITTEN_BOOK);
        BuiltInRegistries.ITEM = itemReg;
    }

    private static void testExactImage9Scenario() throws Exception {
        CustomBookManager443.ensureDirectory();
        for (int i = 1; i <= 5; i++) {
            CustomBookManager443.saveBook(new CustomBook443.BookDef(
                    "book_" + i, "&6Книга #" + i, "Неизвестен",
                    Collections.singletonList("Текст книги #" + i)));
        }

        // Exact user pool from image-9.png (2.txt):
        //   minecraft:bedrock | 5 | 80 | 2-6
        //   settledlands:book/book_3 | 50 | 10 | 1
        // Node: Шт = 3, rarityCap = "", crateWeight = ""
        List<LootPool436.PoolEntry> pool = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:bedrock", 5.0D, 80.0D, 2, 6),
                new LootPool436.PoolEntry("settledlands:book/book_3", 50.0D, 10.0D, 1, 1));
        LootPool436.Parsed spec = new LootPool436.Parsed("2.txt", "", "", "3");

        double[] base = LootPool436.computeEffectiveChancesPct(pool, null);
        double[] eff = LootPool436.computeEffectiveChancesPct(pool, spec);
        req(Math.abs(base[0] - 5.0D) < 1e-6D && Math.abs(base[1] - 50.0D) < 1e-6D,
                "base chances match exact 5% and 50%: " + Arrays.toString(base));
        req(Math.abs(eff[0] - 5.0D) < 1e-6D && Math.abs(eff[1] - 50.0D) < 1e-6D,
                "effective chances with Шт=3 match exact 5% and 50%: " + Arrays.toString(eff));

        Random rnd = new Random(445001L);
        final int trials = 10_000;
        int chestBookHits = 0;
        int chestBedrockHits = 0;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(pool, spec, rnd);
            req(drops.size() <= 2, "at most 2 distinct entries can drop");
            int bCount = 0;
            int rCount = 0;
            for (LootPool436.Drop d : drops) {
                if ("settledlands:book/book_3".equals(d.itemId)) {
                    bCount++;
                    req(d.count == 1, "book_3 count is strictly 1, got " + d.count);
                    chestBookHits++;
                } else if ("minecraft:bedrock".equals(d.itemId)) {
                    rCount++;
                    req(d.count >= 2 && d.count <= 6, "bedrock count in [2..6], got " + d.count);
                    chestBedrockHits++;
                }
            }
            req(bCount <= 1 && rCount <= 1, "no entry duplicates itself within a chest");
        }
        double chestBookRate = chestBookHits / (double) trials;
        double chestBedrockRate = chestBedrockHits / (double) trials;
        req(chestBookRate >= 0.475D && chestBookRate <= 0.525D,
                "chest book_3 drop rate ~50%, got " + chestBookRate);
        req(chestBedrockRate >= 0.038D && chestBedrockRate <= 0.062D,
                "chest bedrock drop rate ~5%, got " + chestBedrockRate);

        int shelfHits = 0;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollBookshelfDrops(
                    pool, spec, 6, id -> CustomBookManager443.isValidForChiseledBookshelf(id, true), rnd);
            req(drops.size() <= 1,
                    "bookshelf NEVER spawns >1 copy of book_3 when Кол-во=1 (even with Шт=3), got "
                            + drops.size());
            if (drops.size() == 1) {
                req("settledlands:book/book_3".equals(drops.get(0).itemId) && drops.get(0).count == 1,
                        "shelf drop is 1 copy of book_3");
                shelfHits++;
            }
        }
        double shelfRate = shelfHits / (double) trials;
        req(shelfRate >= 0.475D && shelfRate <= 0.525D,
                "bookshelf book_3 spawn rate ~50% (and 50% empty), got " + shelfRate);

        int lecternHits = 0;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollLecternDrops(
                    pool, spec, id -> CustomBookManager443.isValidForLectern(id, true), rnd);
            req(drops.size() <= 1, "lectern drops at most 1 book");
            if (drops.size() == 1) {
                req("settledlands:book/book_3".equals(drops.get(0).itemId), "lectern book is book_3");
                lecternHits++;
            }
        }
        double lecRate = lecternHits / (double) trials;
        req(lecRate >= 0.475D && lecRate <= 0.525D,
                "lectern book_3 spawn rate ~50% (and 50% empty), got " + lecRate);

        // Full BE / MockLevel integration for 400 chests, 400 bookshelves, 400 lecterns with 2.txt
        MockServer server = new MockServer();
        MockLevel level = new MockLevel(server, DungeonManager425.HELL_DUNGEON_LEVEL);
        server.overworld = level;
        Path poolsDir = LootPoolManager436.ensureDirectory();
        Files.createDirectories(poolsDir);
        Files.writeString(poolsDir.resolve("2.txt"),
                "minecraft:bedrock | 5 | 80 | 2-6\n"
                + "settledlands:book/book_3 | 50 | 10 | 1\n",
                StandardCharsets.UTF_8);

        NpcDialogueGraph graph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node node = graph.add(NpcDialogueGraph.TRIGGER, 0, 0);
        LootPool436.applyDefaults(node);
        LootPool436.setPoolFile(node, "2.txt");
        LootPool436.setRolls(node, "3");
        ChestGraph396.chestNode(graph).next = node.id;

        ChiseledBookShelfBlock shelfBlock = new ChiseledBookShelfBlock();
        LecternBlock lecternBlock = new LecternBlock();
        ChestBlock chestBlock = new ChestBlock();

        int worldShelfBooks = 0;
        int worldShelfEmpty = 0;
        for (int i = 0; i < 400; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            level.setBlock(pos, new BlockState(shelfBlock, false), 3);
            ChiseledBookShelfBlockEntity be = new ChiseledBookShelfBlockEntity();
            level.putBlockEntity(pos, be);
            int placed = LootPoolManager436.populateContainer(level, pos, graph);
            req(placed == 0 || placed == 1,
                    "MockLevel shelf with 2.txt places strictly 0 or 1 book, got " + placed);
            int occupied = 0;
            BlockState st = level.getBlockState(pos);
            for (int s = 0; s < 6; s++) {
                ItemStack stack = be.getItem(s);
                boolean has = stack != null && !stack.isEmpty();
                if (has) {
                    req(stack.is(Items.WRITTEN_BOOK) && stack.getCount() == 1, "shelf holds 1 written book");
                    occupied++;
                }
                Boolean propVal = st.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(s));
                if (placed > 0) {
                    req(Boolean.valueOf(has).equals(propVal),
                            "shelf slot state property matches occupancy");
                } else {
                    req(propVal == null || Boolean.FALSE.equals(propVal),
                            "empty shelf keeps slot unoccupied");
                }
            }
            req(occupied == placed, "shelf occupied count matches placed");
            if (placed == 1) worldShelfBooks++;
            else worldShelfEmpty++;
        }
        req(worldShelfBooks >= 165 && worldShelfBooks <= 235
                && worldShelfEmpty >= 165 && worldShelfEmpty <= 235,
                "MockLevel shelves split ~50/50 (books=" + worldShelfBooks + ", empty=" + worldShelfEmpty + ")");

        int worldLecBooks = 0;
        int worldLecEmpty = 0;
        for (int i = 0; i < 400; i++) {
            BlockPos pos = new BlockPos(i, 68, 0);
            level.setBlock(pos, new BlockState(lecternBlock, false), 3);
            LecternBlockEntity lec = new LecternBlockEntity();
            level.putBlockEntity(pos, lec);
            int placed = LootPoolManager436.populateContainer(level, pos, graph);
            if (placed == 1) {
                req(lec.hasBook() && lec.getBook().is(Items.WRITTEN_BOOK), "lectern populated");
                worldLecBooks++;
            } else {
                req(placed == 0 && !lec.hasBook(), "lectern empty");
                worldLecEmpty++;
            }
        }
        req(worldLecBooks >= 165 && worldLecBooks <= 235
                && worldLecEmpty >= 165 && worldLecEmpty <= 235,
                "MockLevel lecterns split ~50/50 (books=" + worldLecBooks + ", empty=" + worldLecEmpty + ")");

        int worldChestBedrock = 0;
        int worldChestBook = 0;
        for (int i = 0; i < 400; i++) {
            BlockPos pos = new BlockPos(i, 72, 0);
            level.setBlock(pos, new BlockState(chestBlock, false), 3);
            MockChestBE chest = new MockChestBE();
            level.putBlockEntity(pos, chest);
            LootPoolManager436.populateContainer(level, pos, graph);
            int books = 0;
            int bedrockStacks = 0;
            for (int s = 0; s < chest.getContainerSize(); s++) {
                ItemStack st = chest.getItem(s);
                if (st == null || st.isEmpty()) continue;
                if (st.is(Items.WRITTEN_BOOK)) {
                    books += st.getCount();
                } else {
                    bedrockStacks++;
                    req(st.getCount() >= 2 && st.getCount() <= 6, "chest bedrock stack 2-6");
                }
            }
            req(books <= 1 && bedrockStacks <= 1, "chest never duplicates book_3 or bedrock");
            if (books == 1) worldChestBook++;
            if (bedrockStacks == 1) worldChestBedrock++;
        }
        req(worldChestBook >= 165 && worldChestBook <= 235
                && worldChestBedrock >= 8 && worldChestBedrock <= 40,
                "MockLevel chests match ~50% book (" + worldChestBook + ") & ~5% bedrock (" + worldChestBedrock + ")");
    }

    private static void testMultiBookShelfShuffleAndCaps() {
        Random rnd = new Random(445002L);
        // 5 distinct books + heavy non-book bedrock, all at 100%, node Шт=3:
        // Non-book must NOT steal Шт capacity from books, and all 5 books should appear uniformly (~60%).
        List<LootPool436.PoolEntry> mixedFiveBooks = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:bedrock", 100.0D, 80.0D, 2, 6),
                new LootPool436.PoolEntry("settledlands:book/book_1", 100.0D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("settledlands:book/book_2", 100.0D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("settledlands:book/book_3", 100.0D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("settledlands:book/book_4", 100.0D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("settledlands:book/book_5", 100.0D, 10.0D, 1, 1));
        LootPool436.Parsed rolls3 = new LootPool436.Parsed("five.txt", "", "", "3");

        Map<String, Integer> seenCounts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollBookshelfDrops(
                    mixedFiveBooks, rolls3, 6,
                    id -> CustomBookManager443.isValidForChiseledBookshelf(id, true), rnd);
            req(drops.size() == 3, "5 distinct 100% books + Шт=3 fills exactly 3 slots, got " + drops.size());
            Set<String> distinct = new HashSet<>();
            for (LootPool436.Drop d : drops) {
                req(distinct.add(d.itemId), "no duplicate book on multi-book shelf: " + d.itemId);
                seenCounts.merge(d.itemId, 1, Integer::sum);
            }
        }
        for (int b = 1; b <= 5; b++) {
            int cnt = seenCounts.getOrDefault("settledlands:book/book_" + b, 0);
            req(cnt >= 520 && cnt <= 680,
                    "shuffled fair selection gives ~600/1000 for book_" + b + ", got " + cnt);
        }

        // Book entry with explicit count 2-4 on shelf with Шт=3: places 2..3 copies, never 4.
        List<LootPool436.PoolEntry> stacked = Collections.singletonList(
                new LootPool436.PoolEntry("minecraft:book", 100.0D, 10.0D, 2, 4));
        boolean saw2 = false;
        boolean saw3 = false;
        for (int i = 0; i < 100; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollBookshelfDrops(
                    stacked, rolls3, 6,
                    id -> CustomBookManager443.isValidForChiseledBookshelf(id, true), rnd);
            req(drops.size() >= 2 && drops.size() <= 3,
                    "stacked book 2-4 with Шт=3 caps at 3 slots, got " + drops.size());
            if (drops.size() == 2) saw2 = true;
            if (drops.size() == 3) saw3 = true;
        }
        req(saw2 && saw3, "stacked book 2-4 produces both 2 and 3 copies under Шт=3");
    }

    private static void testNodeLimitsAndEffectiveChancesConsistency() {
        Random rnd = new Random(445003L);
        List<LootPool436.PoolEntry> pool = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:bedrock", 5.0D, 80.0D, 2, 6),
                new LootPool436.PoolEntry("settledlands:book/book_3", 50.0D, 10.0D, 1, 1));

        // 1. Weight cap 0-30 blocks bedrock (W=80) and allows book_3 when w >= 10 (21/31 * 50% = 33.871%)
        LootPool436.Parsed w0to30 = new LootPool436.Parsed("2.txt", "", "0-30", "3");
        double[] effW = LootPool436.computeEffectiveChancesPct(pool, w0to30);
        double expectedWBook = (21.0D / 31.0D) * 50.0D;
        req(Math.abs(effW[0]) < 1e-9D && Math.abs(effW[1] - expectedWBook) < 1e-4D,
                "weight 0-30 effective chances match exact 0% & 33.871%: " + Arrays.toString(effW));

        int wHits = 0;
        for (int i = 0; i < 10_000; i++) {
            List<LootPool436.Drop> d = LootPool436.rollDrops(pool, w0to30, rnd);
            for (LootPool436.Drop drop : d) {
                req(!"minecraft:bedrock".equals(drop.itemId), "weight 80 never drops inside 0-30");
                if ("settledlands:book/book_3".equals(drop.itemId)) wHits++;
            }
        }
        double mcWBook = (wHits * 100.0D) / 10_000.0D;
        req(Math.abs(mcWBook - expectedWBook) < 2.0D,
                "Monte-Carlo (" + mcWBook + "%) agrees with formula (" + expectedWBook + "%) for weight 0-30");

        // 2. Rarity cap 0-100: bedrock (96/101 * 5%), book_3 (51/101 * 50%)
        LootPool436.Parsed r0to100 = new LootPool436.Parsed("2.txt", "0-100", "", "3");
        double[] effR = LootPool436.computeEffectiveChancesPct(pool, r0to100);
        double expectedRBedrock = (96.0D / 101.0D) * 5.0D;
        double expectedRBook = (51.0D / 101.0D) * 50.0D;
        req(Math.abs(effR[0] - expectedRBedrock) < 1e-4D && Math.abs(effR[1] - expectedRBook) < 1e-4D,
                "rarity 0-100 effective chances match exact formula: " + Arrays.toString(effR));

        int rBookHits = 0;
        for (int i = 0; i < 10_000; i++) {
            for (LootPool436.Drop drop : LootPool436.rollDrops(pool, r0to100, rnd)) {
                if ("settledlands:book/book_3".equals(drop.itemId)) rBookHits++;
            }
        }
        double mcRBook = (rBookHits * 100.0D) / 10_000.0D;
        req(Math.abs(mcRBook - expectedRBook) < 2.0D,
                "Monte-Carlo (" + mcRBook + "%) agrees with formula (" + expectedRBook + "%) for rarity 0-100");

        // 3. Rolls 0-1 on solo 50% book -> 25%
        List<LootPool436.PoolEntry> soloBook = Collections.singletonList(pool.get(1));
        LootPool436.Parsed r0to1 = new LootPool436.Parsed("2.txt", "", "", "0-1");
        double[] eff01 = LootPool436.computeEffectiveChancesPct(soloBook, r0to1);
        req(Math.abs(eff01[0] - 25.0D) < 1e-6D, "Шт=0-1 halves 50% -> 25%, got " + eff01[0]);

        // 4. 6 entries at 100% with Шт=2 -> 33.333% each (formula & MC)
        List<LootPool436.PoolEntry> six100 = Arrays.asList(
                new LootPool436.PoolEntry("a:1", 100.0D, 1.0D, 1, 1),
                new LootPool436.PoolEntry("a:2", 100.0D, 1.0D, 1, 1),
                new LootPool436.PoolEntry("a:3", 100.0D, 1.0D, 1, 1),
                new LootPool436.PoolEntry("a:4", 100.0D, 1.0D, 1, 1),
                new LootPool436.PoolEntry("a:5", 100.0D, 1.0D, 1, 1),
                new LootPool436.PoolEntry("a:6", 100.0D, 1.0D, 1, 1));
        LootPool436.Parsed cap2 = new LootPool436.Parsed("six.txt", "", "", "2");
        double[] effSix = LootPool436.computeEffectiveChancesPct(six100, cap2);
        req(Math.abs(effSix[0] - (100.0D / 3.0D)) < 1e-4D,
                "6x100% with Шт=2 displays ~33.33%, got " + effSix[0]);

        // 5. Legacy >100 rarity on disk clamps cleanly to 100%
        List<LootPool436.PoolEntry> parsedLegacy = LootPool436.parseLines(
                Collections.singletonList("minecraft:bedrock | 250 | 10 | 1"));
        req(parsedLegacy.size() == 1 && Math.abs(parsedLegacy.get(0).rarity - 100.0D) < 1e-6D,
                "legacy >100 rarity in .txt clamps cleanly to 100%");
    }
}
