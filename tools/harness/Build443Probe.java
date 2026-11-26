import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Events;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.chest.ChestOpen401;
import dev.settledlands.chest.CustomBook443;
import dev.settledlands.chest.CustomBookManager443;
import dev.settledlands.chest.CustomBookNet443;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.npc.NpcDialogueGraph;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Headless JVM verification harness for Build 443:
 *   1. CustomBook443 ID normalization, transliteration, color/hex span parser, file & Base64 codecs
 *   2. CustomBookManager443 disk CRUD, WrittenBookContent / CustomName building, inventory extraction
 *   3. CustomBookNet443 query / save / give / delete payloads + OP gating + full-inv drop fallback
 *   4. Lectern & Chiseled Bookshelf ChestGraph396 recognition, mixed-pool book filtering, block state sync
 *   5. ChestGraph396Events windowless vs menu-wait tick timings & ChestOpen401 hit capture
 */
public final class Build443Probe {

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

    private static final class MockPlayer extends ServerPlayer {
        final String pname;
        final UUID pid;
        final Level lvl;
        final boolean op;
        final List<ItemStack> droppedItems = new ArrayList<>();

        MockPlayer(String name, UUID id, Level level, boolean op) {
            this.pname = name;
            this.pid = id;
            this.lvl = level;
            this.op = op;
        }

        @Override public UUID getUUID() { return pid; }
        @Override public Level level() { return lvl; }
        @Override public Component getName() { return Component.literal(pname); }
        @Override public boolean hasPermissions(int level) { return op; }
        @Override public net.minecraft.world.entity.item.ItemEntity drop(ItemStack stack, boolean dropAround) {
            if (stack != null && !stack.isEmpty()) droppedItems.add(stack.copy());
            return null;
        }
    }

    private static final class MockPlayerList extends PlayerList {
        final Map<UUID, ServerPlayer> map = new HashMap<>();

        void add(ServerPlayer p) { map.put(p.getUUID(), p); }

        @Override public List<ServerPlayer> getPlayers() {
            return new ArrayList<>(map.values());
        }

        @Override public ServerPlayer getPlayer(UUID id) {
            return map.get(id);
        }
    }

    private static final class MockLevel extends ServerLevel {
        final MinecraftServer srv;
        final ResourceKey<Level> dim;
        final DimensionDataStorage storage = new DimensionDataStorage();
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, BlockEntity> bes = new HashMap<>();
        long time;
        int sendBlockUpdatedCalls;

        MockLevel(MinecraftServer srv, ResourceKey<Level> dim) {
            this.srv = srv;
            this.dim = dim;
        }

        @Override public MinecraftServer getServer() { return srv; }
        @Override public ResourceKey<Level> dimension() { return dim; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public long getGameTime() { return time; }

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

        @Override public void sendBlockUpdated(BlockPos pos, BlockState o, BlockState n, int flags) {
            sendBlockUpdatedCalls++;
        }
    }

    private static final class MockServer extends MinecraftServer {
        final MockPlayerList plist = new MockPlayerList();
        MockLevel overworld;

        @Override public PlayerList getPlayerList() { return plist; }
        @Override public ServerLevel overworld() { return overworld; }
        @Override public ServerLevel getLevel(ResourceKey<Level> key) {
            return DungeonManager425.HELL_DUNGEON_LEVEL.equals(key) ? overworld : null;
        }
    }

    public static void main(String[] args) throws Exception {
        initRegistries();
        testIdAndCleanItemId();
        testColorSpanParser();
        testFileAndWireCodecs();
        testBookManagerDiskAndComponents();
        testBookNetRoundTrip();
        testLecternAndChiseledBookshelfGraphAndLoot();
        testEventTimings();
        System.out.println("Build443Probe: PASS (" + checks + " checks passed)");
    }

    private static void initRegistries() {
        SimpleReg<Item> itemReg = new SimpleReg<>();
        itemReg.put("minecraft:air", Items.AIR);
        itemReg.put("minecraft:diamond", new Item());
        itemReg.put("minecraft:book", Items.BOOK);
        itemReg.put("minecraft:enchanted_book", Items.ENCHANTED_BOOK);
        itemReg.put("minecraft:written_book", Items.WRITTEN_BOOK);
        itemReg.put("minecraft:writable_book", Items.WRITABLE_BOOK);
        BuiltInRegistries.ITEM = itemReg;
    }

    private static void testIdAndCleanItemId() {
        req("dnevnik_rytsarya_1".equals(CustomBook443.normalizeBookId("Дневник Рыцаря #1")),
                "cyrillic transliteration");
        req("moi_log".equals(CustomBook443.normalizeBookId("settledlands:book/Мой Лог.txt")),
                "strip prefix + ext");
        req("taina".equals(CustomBook443.normalizeBookId("book:&6Тайна")),
                "strip formatting in id");
        req("passwd".equals(CustomBook443.normalizeBookId("../etc/passwd")),
                "path traversal sanitized");
        req("".equals(CustomBook443.normalizeBookId("README_BOOKS")),
                "readme reserved");
        req("".equals(CustomBook443.normalizeBookId("con")),
                "windows reserved");
        String longId = CustomBook443.normalizeBookId(
                "очень_длинное_название_книги_которое_превышает_сорок_восемь_символов");
        req(longId.length() > 0 && longId.length() <= CustomBook443.MAX_ID_LEN,
                "max id length clamp");

        req("settledlands:book/dnevnik_1".equals(LootPool436.cleanItemId("book:Дневник 1")),
                "LootPool436.cleanItemId short book:");
        req("settledlands:book/lore_1".equals(LootPool436.cleanItemId("settledlands:book/Lore 1")),
                "LootPool436.cleanItemId full prefix");
        req("minecraft:diamond".equals(LootPool436.cleanItemId("diamond")),
                "LootPool436.cleanItemId regular item unchanged");
    }

    private static void testColorSpanParser() {
        List<CustomBook443.Span> spans = CustomBook443.parseSpans(
                "&6&lЗолотой &rобычный &#123456Хекс \\&6экран {#ABCDEF}Фигурный");
        req(spans.size() == 4, "expected 4 spans, got " + spans.size());
        req("Золотой ".equals(spans.get(0).text) && spans.get(0).rgb == 0xFFAA00 && spans.get(0).bold,
                "span 0 gold+bold");
        req("обычный ".equals(spans.get(1).text) && spans.get(1).rgb == -1 && !spans.get(1).bold,
                "span 1 reset");
        req("Хекс &6экран ".equals(spans.get(2).text) && spans.get(2).rgb == 0x123456,
                "span 2 hex + escaped ampersand");
        req("Фигурный".equals(spans.get(3).text) && spans.get(3).rgb == 0xABCDEF,
                "span 3 brace hex");

        List<CustomBook443.Span> reordered = CustomBook443.parseSpans("&l&o&eЗаголовок");
        req(reordered.size() == 1 && reordered.get(0).bold && reordered.get(0).italic
                && reordered.get(0).rgb == 0xFFFF55, "style before color preserves style");

        req("Привет мир".equals(CustomBook443.stripFormatting("&a&lПривет &#FF00FFмир")),
                "stripFormatting");
    }

    private static void testFileAndWireCodecs() {
        CustomBook443.BookDef orig = new CustomBook443.BookDef(
                "chronicle_1",
                "&6&lХроника | Войны",
                "Маг §7Эльдар",
                Arrays.asList(
                        "&4Первая страница\nС новой строки &oкурсив|палка",
                        "&#33CC99Вторая страница\nСмайл ☺ &rконец"));
        String fileText = CustomBook443.formatBookFile(orig);
        CustomBook443.BookDef reparsed = CustomBook443.parseBookFile(
                orig.id, fileText.lines().toList());
        req(reparsed != null && orig.id.equals(reparsed.id), "file roundtrip id");
        req(orig.title.equals(reparsed.title), "file roundtrip title");
        req(orig.author.equals(reparsed.author), "file roundtrip author");
        req(orig.pages.equals(reparsed.pages), "file roundtrip pages");

        String wire = CustomBook443.encodeBookWire(orig);
        CustomBook443.BookDef fromWire = CustomBook443.decodeBookWire(wire);
        req(fromWire != null && orig.title.equals(fromWire.title) && orig.pages.equals(fromWire.pages),
                "Base64 book wire roundtrip");

        String sumWire = CustomBook443.encodeSummariesWire(Collections.singletonList(orig));
        List<CustomBook443.BookDef> sums = CustomBook443.decodeSummariesWire(sumWire);
        req(sums.size() == 1 && orig.title.equals(sums.get(0).title)
                && sums.get(0).summaryPageCount == 2, "summary wire roundtrip");
    }

    private static void testBookManagerDiskAndComponents() throws Exception {
        Path booksDir = CustomBookManager443.ensureDirectory();
        req(Files.isDirectory(booksDir), "books dir created");
        req(Files.isRegularFile(booksDir.resolve("README_BOOKS.txt.disabled")), "readme created");

        CustomBook443.BookDef book = new CustomBook443.BookDef(
                "my_lore",
                "&6&lДневник Стража Подземелья — Очень Длинное Название",
                "Архивариус",
                Arrays.asList("&cПредупреждение\nНе открывать!", "&#12AB34Вторая глава &oтайна"));
        String savedId = CustomBookManager443.saveBook(book);
        req("my_lore".equals(savedId), "saveBook returned id");
        req(CustomBookManager443.listBookIds().contains("my_lore"), "listBookIds includes saved book");

        ItemStack stack = CustomBookManager443.buildBookStack(book, 1);
        req(!stack.isEmpty() && stack.is(Items.WRITTEN_BOOK), "buildBookStack is WRITTEN_BOOK");
        Component cname = stack.get(DataComponents.CUSTOM_NAME);
        req(cname != null && CustomBookManager443.componentToFormatted(cname).contains("&6&lДневник"),
                "custom name retains &6&l");
        WrittenBookContent wbc = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        req(wbc != null && wbc.title().raw().length() <= CustomBook443.MAX_WRITTEN_TITLE_LEN,
                "written title <= 32 chars");
        req(wbc.pages().size() == 2 && "Архивариус".equals(wbc.author()) && wbc.resolved(),
                "written pages/author/resolved");

        CustomBook443.BookDef extracted = CustomBookManager443.extractBookFromStack(stack, "");
        req(extracted != null, "extractBookFromStack non-null");
        req(extracted.title.contains("&6&lДневник"), "extracted title keeps formatting");
        req(extracted.pages.size() == 2 && extracted.pages.get(0).contains("&cПредупреждение")
                && extracted.pages.get(1).contains("&#12AB34"), "extracted pages retain colors/hex");

        ItemStack writable = new ItemStack(Items.WRITABLE_BOOK, 1);
        writable.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(
                Arrays.asList(Filterable.passThrough("Запись 1"), Filterable.passThrough("Запись 2"))));
        CustomBook443.BookDef extWritable = CustomBookManager443.extractBookFromStack(writable, "zametka");
        req(extWritable != null && "zametka".equals(extWritable.id) && extWritable.pages.size() == 2,
                "extractBookFromStack handles WRITABLE_BOOK");

        req(!CustomBookManager443.resolveItemStack("settledlands:book/my_lore", 1, true).isEmpty(),
                "resolveItemStack server finds custom book");
        req(CustomBookManager443.resolveItemStack("settledlands:book/no_such_book", 1, true).isEmpty(),
                "resolveItemStack missing book returns EMPTY");
        req(CustomBookManager443.isValidForLectern("settledlands:book/my_lore", true),
                "custom book valid for lectern");
        req(CustomBookManager443.isValidForLectern("minecraft:written_book", true),
                "written_book valid for lectern");
        req(CustomBookManager443.isValidForLectern("minecraft:writable_book", true),
                "writable_book valid for lectern");
        req(!CustomBookManager443.isValidForLectern("minecraft:book", true),
                "plain minecraft:book NOT valid for lectern");
        req(!CustomBookManager443.isValidForLectern("minecraft:diamond", true),
                "diamond NOT valid for lectern");
        req(CustomBookManager443.isValidForChiseledBookshelf("settledlands:book/my_lore", true),
                "custom book valid for chiseled bookshelf");
        req(CustomBookManager443.isValidForChiseledBookshelf("minecraft:book", true),
                "plain book valid for chiseled bookshelf");
        req(CustomBookManager443.isValidForChiseledBookshelf("minecraft:enchanted_book", true),
                "enchanted_book valid for chiseled bookshelf");
        req(!CustomBookManager443.isValidForChiseledBookshelf("minecraft:diamond", true),
                "diamond NOT valid for chiseled bookshelf");
    }

    private static void testBookNetRoundTrip() throws Exception {
        Method mQuery = CustomBookNet443.class.getDeclaredMethod(
                "onBookQuery", CustomBookNet443.BookQuery.class, IPayloadContext.class);
        mQuery.setAccessible(true);
        Method mSave = CustomBookNet443.class.getDeclaredMethod(
                "onBookSave", CustomBookNet443.BookSave.class, IPayloadContext.class);
        mSave.setAccessible(true);

        MockPlayer op = new MockPlayer("Tester", UUID.randomUUID(), null, true);
        MockPlayer nonOp = new MockPlayer("Guest", UUID.randomUUID(), null, false);
        IPayloadContext opCtx = new IPayloadContext() {
            @Override public CompletableFuture<?> enqueueWork(Runnable task) {
                if (task != null) task.run();
                return CompletableFuture.completedFuture(null);
            }
            @Override public Player player() { return op; }
        };
        IPayloadContext nonOpCtx = new IPayloadContext() {
            @Override public CompletableFuture<?> enqueueWork(Runnable task) {
                if (task != null) task.run();
                return CompletableFuture.completedFuture(null);
            }
            @Override public Player player() { return nonOp; }
        };

        CustomBook443.BookDef netBook = new CustomBook443.BookDef(
                "net_test", "&eСетевая книга", "Автор", Collections.singletonList("&aТекст"));

        PacketDistributor.SENT_TO_PLAYER.clear();
        mSave.invoke(null, new CustomBookNet443.BookSave("SAVE\n" + CustomBook443.encodeBookWire(netBook)), nonOpCtx);
        req(!CustomBookManager443.bookExists("net_test"), "non-OP BookSave SAVE denied");

        PacketDistributor.SENT_TO_PLAYER.clear();
        mQuery.invoke(null, new CustomBookNet443.BookQuery(""), opCtx);
        boolean sawBooks = false;
        boolean sawNew = false;
        for (Object p : PacketDistributor.SENT_TO_PLAYER) {
            if (p instanceof CustomBookNet443.Books) sawBooks = true;
            if (p instanceof CustomBookNet443.BookData d && d.payload().startsWith("NEW\n")) sawNew = true;
        }
        req(sawBooks && sawNew, "BookQuery empty sends Books + NEW");

        PacketDistributor.SENT_TO_PLAYER.clear();
        mSave.invoke(null, new CustomBookNet443.BookSave("SAVE\n" + CustomBook443.encodeBookWire(netBook)), opCtx);
        req(CustomBookManager443.bookExists("net_test"), "BookSave SAVE writes disk file");

        mSave.invoke(null, new CustomBookNet443.BookSave("GIVE\n" + CustomBook443.encodeBookWire(netBook)), opCtx);
        int nonEmpty = 0;
        for (int i = 0; i < op.getInventory().getContainerSize(); i++) {
            if (!op.getInventory().getItem(i).isEmpty()) nonEmpty++;
        }
        req(nonEmpty == 1, "BookSave GIVE puts book in inventory");
        Item filler = new Item();
        while (op.getInventory().add(new ItemStack(filler, 1))) {}
        mSave.invoke(null, new CustomBookNet443.BookSave("GIVE\n" + CustomBook443.encodeBookWire(netBook)), opCtx);
        req(op.droppedItems.size() == 1, "BookSave GIVE drops book when inventory is full");

        mSave.invoke(null, new CustomBookNet443.BookSave("DELETE\nnet_test"), opCtx);
        req(!CustomBookManager443.bookExists("net_test"), "BookSave DELETE removes disk file");
    }

    private static void testLecternAndChiseledBookshelfGraphAndLoot() throws Exception {
        MockServer server = new MockServer();
        MockLevel level = new MockLevel(server, DungeonManager425.HELL_DUNGEON_LEVEL);
        server.overworld = level;

        LecternBlock lecternBlock = new LecternBlock();
        ChiseledBookShelfBlock shelfBlock = new ChiseledBookShelfBlock();
        ChestBlock chestBlock = new ChestBlock();

        BlockPos lecPos = new BlockPos(10, 64, 10);
        BlockState lecState = new BlockState(lecternBlock, false);
        LecternBlockEntity lecBe = new LecternBlockEntity();
        level.setBlock(lecPos, lecState, 3);
        level.putBlockEntity(lecPos, lecBe);

        BlockPos shelfPos = new BlockPos(12, 64, 10);
        BlockState shelfState = new BlockState(shelfBlock, false);
        ChiseledBookShelfBlockEntity shelfBe = new ChiseledBookShelfBlockEntity();
        level.setBlock(shelfPos, shelfState, 3);
        level.putBlockEntity(shelfPos, shelfBe);

        BlockPos chestPos = new BlockPos(14, 64, 10);
        BlockState chestState = new BlockState(chestBlock, false);
        MockChestBE chestBe = new MockChestBE();
        level.setBlock(chestPos, chestState, 3);
        level.putBlockEntity(chestPos, chestBe);

        req(ChestGraph396.isChest(lecState), "isChest(LecternBlock)");
        req(ChestGraph396.isChest(shelfState), "isChest(ChiseledBookShelfBlock)");
        req(ChestGraph396.isChestAt(level, lecPos), "isChestAt lectern (non-Container!)");
        req(ChestGraph396.isChestAt(level, shelfPos), "isChestAt chiseled bookshelf");

        Path poolsDir = LootPoolManager436.ensureDirectory();
        Files.createDirectories(poolsDir);
        Files.writeString(poolsDir.resolve("book_only_443.txt"),
                "settledlands:book/my_lore | 100 | 1 | 4\n");
        Files.writeString(poolsDir.resolve("mixed_443.txt"),
                "minecraft:diamond | 90 | 1 | 1\n"
                + "settledlands:book/my_lore | 10 | 1 | 1\n");

        NpcDialogueGraph gBookOnly = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node pNode2 = gBookOnly.add(NpcDialogueGraph.TRIGGER, 0, 0);
        LootPool436.applyDefaults(pNode2);
        LootPool436.setPoolFile(pNode2, "book_only_443.txt");
        LootPool436.setRolls(pNode2, "4");
        ChestGraph396.chestNode(gBookOnly).next = pNode2.id;

        LecternBlock.RESET_CALLS = 0;
        int placedLec = LootPoolManager436.populateContainer(level, lecPos, gBookOnly);
        req(placedLec == 1 && lecBe.hasBook() && lecBe.getBook().is(Items.WRITTEN_BOOK),
                "lectern populated with custom book");
        req(LecternBlock.RESET_CALLS == 1, "LecternBlock.resetBookState invoked");
        req(LootPoolManager436.populateContainer(level, lecPos, gBookOnly) == 0,
                "occupied lectern skipped on second populateContainer");

        level.sendBlockUpdatedCalls = 0;
        int placedShelf = LootPoolManager436.populateContainer(level, shelfPos, gBookOnly);
        req(placedShelf == 4, "chiseled bookshelf placed 4 books from book-only pool, got " + placedShelf);
        int occupiedCount = 0;
        BlockState afterShelf = level.getBlockState(shelfPos);
        for (int i = 0; i < 6; i++) {
            ItemStack slotItem = shelfBe.getItem(i);
            boolean hasItem = slotItem != null && !slotItem.isEmpty();
            if (hasItem) {
                req(slotItem.is(Items.WRITTEN_BOOK), "bookshelf only got books");
                occupiedCount++;
            }
            Boolean propVal = afterShelf.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i));
            req(Boolean.valueOf(hasItem).equals(propVal), "SLOT_OCCUPIED_PROPERTIES synced slot " + i);
        }
        req(occupiedCount == 4 && level.sendBlockUpdatedCalls >= 1,
                "bookshelf visual properties + sendBlockUpdated synced");

        // Build 444 update: mixed pools roll honestly without pre-stripping non-books.
        NpcDialogueGraph gMixed = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node mixNode = gMixed.add(NpcDialogueGraph.TRIGGER, 0, 0);
        LootPool436.applyDefaults(mixNode);
        LootPool436.setPoolFile(mixNode, "mixed_443.txt");
        LootPool436.setRolls(mixNode, "3");
        ChestGraph396.chestNode(gMixed).next = mixNode.id;
        BlockPos mixShelfPos = new BlockPos(16, 64, 10);
        level.setBlock(mixShelfPos, new BlockState(shelfBlock, false), 3);
        ChiseledBookShelfBlockEntity mixShelfBe = new ChiseledBookShelfBlockEntity();
        level.putBlockEntity(mixShelfPos, mixShelfBe);
        int mixPlaced = LootPoolManager436.populateContainer(level, mixShelfPos, gMixed);
        req(mixPlaced >= 0 && mixPlaced <= 3, "mixed bookshelf respects Шт=3 ceiling");
        for (int i = 0; i < 6; i++) {
            ItemStack st = mixShelfBe.getItem(i);
            req(st == null || st.isEmpty() || st.is(Items.WRITTEN_BOOK),
                    "mixed bookshelf never stores non-book diamonds");
        }

        LootPool436.setRolls(pNode2, "1");
        int placedChest = LootPoolManager436.populateContainer(level, chestPos, gBookOnly);
        req(placedChest == 1, "chest receives custom book");
        ItemStack inChest = ItemStack.EMPTY;
        for (int i = 0; i < chestBe.getContainerSize(); i++) {
            if (!chestBe.getItem(i).isEmpty()) inChest = chestBe.getItem(i);
        }
        req(!inChest.isEmpty() && inChest.get(DataComponents.WRITTEN_BOOK_CONTENT) != null,
                "custom book in chest retains WrittenBookContent");
    }

    private static void testEventTimings() {
        MockServer server = new MockServer();
        MockLevel level = new MockLevel(server, DungeonManager425.HELL_DUNGEON_LEVEL);
        server.overworld = level;
        ServerPlayer player = new MockPlayer("Opener", UUID.randomUUID(), level, true);
        server.plist.add(player);

        LecternBlock lecternBlock = new LecternBlock();
        BlockPos emptyLecPos = new BlockPos(20, 64, 20);
        level.setBlock(emptyLecPos, new BlockState(lecternBlock, false), 3);
        level.putBlockEntity(emptyLecPos, new LecternBlockEntity());

        BlockPos fullLecPos = new BlockPos(21, 64, 20);
        level.setBlock(fullLecPos, new BlockState(lecternBlock, false), 3);
        LecternBlockEntity fullLec = new LecternBlockEntity();
        fullLec.setBook(new ItemStack(Items.WRITTEN_BOOK, 1));
        level.putBlockEntity(fullLecPos, fullLec);

        ServerTickEvent.Post tickEvent = new ServerTickEvent.Post() {
            @Override public MinecraftServer getServer() { return server; }
        };

        level.time = 100L;
        ChestGraph396Events.remember(player, level, emptyLecPos,
                ChestGraph396Store.key(level, emptyLecPos), false);
        req(ChestGraph396Events.pending() == 1, "pending added for empty lectern");
        level.time = 102L;
        ChestGraph396Events.onTick(tickEvent);
        req(ChestGraph396Events.pending() == 0, "empty lectern finished in 2 ticks (OPEN_GRACE)");

        level.time = 200L;
        ChestGraph396Events.remember(player, level, fullLecPos,
                ChestGraph396Store.key(level, fullLecPos), false);
        level.time = 202L;
        ChestGraph396Events.onTick(tickEvent);
        req(ChestGraph396Events.pending() == 1, "occupied lectern still waiting on LecternMenu at +2 ticks");
        level.time = 241L;
        ChestGraph396Events.onTick(tickEvent);
        req(ChestGraph396Events.pending() == 0, "occupied lectern expired cleanly after NO_WINDOW");

        ChestOpen401.captureClientHit(emptyLecPos);
        checks++;
    }
}
