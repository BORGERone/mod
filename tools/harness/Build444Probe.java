import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.CustomBook443;
import dev.settledlands.chest.CustomBookManager443;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.LootPoolBookEditorScreen443;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Container;
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

/**
 * Headless JVM verification harness for Build 444:
 *   1. Default book author ("Неизвестен") on new, authorless-file, and imported books
 *   2. Exact vanilla book page fit enforcement (114px width x 14 lines + bold width + paste clamping)
 *   3. Unbiased full-pool odds on Lecterns & Chiseled Bookshelves (no non-book pre-stripping -> 100% bug)
 *   4. Strict Chiseled Bookshelf Шт cap enforcement even when pool entries have count > 1 (e.g. 2-6)
 */
public final class Build444Probe {

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

    /**
     * Faithful vanilla-like line wrapper used to verify {@link LootPoolBookEditorScreen443}
     * line counting and {@link CustomBook443#fitPageText} without LWJGL/client startup.
     * Supports explicit {@code '\n'}, word wrapping at {@code maxWidth}, and bold (+1px) width.
     */
    private static final class MockBookFont extends Font {
        @Override
        public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
            if (text == null || maxWidth <= 0) return Collections.emptyList();
            List<Integer> widths = new ArrayList<>();
            List<Character> chars = new ArrayList<>();
            text.visit((style, str) -> {
                if (str == null) return Optional.empty();
                int adv = (style != null && style.isBold()) ? 7 : 6;
                for (int i = 0; i < str.length(); i++) {
                    char ch = str.charAt(i);
                    chars.add(Character.valueOf(ch));
                    widths.add(Integer.valueOf(ch == ' ' ? 4 : adv));
                }
                return Optional.empty();
            }, Style.EMPTY);

            if (chars.isEmpty()) return Collections.emptyList();
            List<FormattedCharSequence> lines = new ArrayList<>();
            int n = chars.size();
            int pos = 0;
            while (pos < n) {
                int w = 0;
                int lastSpace = -1;
                int cur = pos;
                boolean hitNewline = false;
                while (cur < n) {
                    char ch = chars.get(cur).charValue();
                    if (ch == '\n') {
                        hitNewline = true;
                        break;
                    }
                    int cw = widths.get(cur).intValue();
                    if (w + cw > maxWidth && cur > pos) {
                        break;
                    }
                    w += cw;
                    if (ch == ' ') {
                        lastSpace = cur;
                    }
                    cur++;
                }
                if (hitNewline) {
                    lines.add(new FormattedCharSequence() {});
                    pos = cur + 1;
                } else if (cur >= n) {
                    lines.add(new FormattedCharSequence() {});
                    pos = cur;
                } else if (lastSpace >= pos) {
                    lines.add(new FormattedCharSequence() {});
                    pos = lastSpace + 1;
                } else {
                    lines.add(new FormattedCharSequence() {});
                    pos = cur;
                }
            }
            return lines;
        }
    }

    public static void main(String[] args) throws Exception {
        initRegistries();
        testDefaultAuthorUnknown();
        testExactBookPageLimits();
        testLecternAndBookshelfFairOddsAndCaps();
        System.out.println("Build444Probe: PASS (" + checks + " checks passed)");
    }

    private static void initRegistries() {
        SimpleReg<Item> itemReg = new SimpleReg<>();
        itemReg.put("minecraft:air", Items.AIR);
        itemReg.put("minecraft:glass", new Item());
        itemReg.put("minecraft:iron_block", new Item());
        itemReg.put("minecraft:diamond", new Item());
        itemReg.put("minecraft:book", Items.BOOK);
        itemReg.put("minecraft:enchanted_book", Items.ENCHANTED_BOOK);
        itemReg.put("minecraft:written_book", Items.WRITTEN_BOOK);
        itemReg.put("minecraft:writable_book", Items.WRITABLE_BOOK);
        BuiltInRegistries.ITEM = itemReg;
    }

    private static void testDefaultAuthorUnknown() throws Exception {
        req("Неизвестен".equals(CustomBook443.DEFAULT_AUTHOR), "DEFAULT_AUTHOR constant is Неизвестен");

        CustomBook443.BookDef noAuthorHeader = CustomBook443.parseBookFile(
                "old_file", Arrays.asList("title: Забытый том", "=== PAGE ===", "Текст"));
        req(noAuthorHeader != null && "Неизвестен".equals(noAuthorHeader.author),
                "parseBookFile defaults missing author line to Неизвестен");

        CustomBook443.BookDef emptyFile = CustomBook443.parseBookFile("empty", Collections.emptyList());
        req(emptyFile != null && "Неизвестен".equals(emptyFile.author),
                "parseBookFile empty file defaults author to Неизвестен");

        CustomBook443.BookDef explicitBlank = CustomBook443.parseBookFile(
                "blank_auth", Arrays.asList("title: Без имени", "author: ", "=== PAGE ===", "Текст"));
        req(explicitBlank != null && "".equals(explicitBlank.author),
                "parseBookFile respects explicit empty 'author:' line");

        String roundTripBlank = CustomBook443.formatBookFile(explicitBlank);
        req(roundTripBlank.contains("author: \n"), "formatBookFile preserves explicit blank author line");
        CustomBook443.BookDef reparsedBlank = CustomBook443.parseBookFile(
                "blank_auth", roundTripBlank.lines().toList());
        req(reparsedBlank != null && "".equals(reparsedBlank.author),
                "format+parse roundtrip retains explicit empty author");

        ItemStack writable = new ItemStack(Items.WRITABLE_BOOK, 1);
        writable.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(
                Collections.singletonList(Filterable.passThrough("Запись"))));
        CustomBook443.BookDef fromWritable = CustomBookManager443.extractBookFromStack(writable, "note");
        req(fromWritable != null && "Неизвестен".equals(fromWritable.author),
                "extractBookFromStack(WRITABLE_BOOK) defaults author to Неизвестен");

        ItemStack blankWritten = new ItemStack(Items.WRITTEN_BOOK, 1);
        blankWritten.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Том"), "", 0,
                Collections.singletonList(Filterable.passThrough(net.minecraft.network.chat.Component.literal("А"))),
                true));
        CustomBook443.BookDef fromBlankWritten = CustomBookManager443.extractBookFromStack(blankWritten, "tom");
        req(fromBlankWritten != null && "Неизвестен".equals(fromBlankWritten.author),
                "extractBookFromStack(WRITTEN_BOOK with empty author) defaults to Неизвестен");

        ItemStack authoredWritten = new ItemStack(Items.WRITTEN_BOOK, 1);
        authoredWritten.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Том 2"), "Маг Эльдар", 0,
                Collections.singletonList(Filterable.passThrough(net.minecraft.network.chat.Component.literal("Б"))),
                true));
        CustomBook443.BookDef fromAuthored = CustomBookManager443.extractBookFromStack(authoredWritten, "tom2");
        req(fromAuthored != null && "Маг Эльдар".equals(fromAuthored.author),
                "extractBookFromStack keeps explicit non-empty author");
    }

    private static void testExactBookPageLimits() {
        MockBookFont font = new MockBookFont();

        // 14 lines of 17 chars (102px <= 114px normal, 119px > 114px when bold!) fit cleanly;
        // a 15th line or trailing newline is rejected.
        StringBuilder sb14 = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            if (i > 0) sb14.append('\n');
            sb14.append("Строка_длинной_").append(i < 10 ? "0" + i : Integer.toString(i));
        }
        String page14 = sb14.toString();
        req(LootPoolBookEditorScreen443.countVanillaPageLines(font, page14) == 14,
                "14 lines fit countVanillaPageLines");
        req(LootPoolBookEditorScreen443.fitsVanillaPage(font, page14), "14 lines fit page");

        String withExtraNewline = page14 + "\n";
        req(LootPoolBookEditorScreen443.countVanillaPageLines(font, withExtraNewline) == 15,
                "trailing newline after line 14 triggers line 15 cursor overflow");
        req(!LootPoolBookEditorScreen443.fitsVanillaPage(font, withExtraNewline),
                "trailing newline after line 14 does not fit");

        String fittedNewline = CustomBook443.fitPageText(
                page14, withExtraNewline, s -> LootPoolBookEditorScreen443.fitsVanillaPage(font, s));
        req(page14.equals(fittedNewline), "fitPageText blocks Enter on line 14");

        // Line 13 + Enter is allowed (opens line 14)
        StringBuilder sb13 = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            if (i > 0) sb13.append('\n');
            sb13.append("Линия_").append(i);
        }
        String page13Enter = sb13.toString() + "\n";
        req(LootPoolBookEditorScreen443.fitsVanillaPage(font, page13Enter),
                "Enter at end of line 13 opens line 14 cleanly");

        // Bold formatting (&l) expands character advance (6px -> 7px) and reduces capacity when overflowing
        String wide14 = page14;
        String bolded = "&l" + wide14;
        // Bold turns 15 chars x 7px = 105px (fits), 17 chars x 7px = 119px > 114px (wraps to 28 lines!)
        req(!LootPoolBookEditorScreen443.fitsVanillaPage(font, bolded),
                "bold expansion pushes 14-line wide page past 14 lines");

        // Zero-width color code on full 14-line page still fits
        String colored14 = "&6" + page14;
        req(LootPoolBookEditorScreen443.fitsVanillaPage(font, colored14),
                "zero-width color code still fits on 14-line page");

        // Huge paste onto 12-line page clamps cleanly at the fitted prefix without breaking &#RRGGBB
        StringBuilder sb12 = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb12.append("Линия ").append(i).append('\n');
        }
        String base12 = sb12.toString();
        String bigPaste = "Первая влезшая строка текста &#FF8800цветного текста и ещё много лишних слов чтобы переполнить страницу целиком и полностью";
        String pasted = CustomBook443.fitPageText(
                base12, base12 + bigPaste, s -> LootPoolBookEditorScreen443.fitsVanillaPage(font, s));
        req(LootPoolBookEditorScreen443.fitsVanillaPage(font, pasted),
                "pasted result guaranteed to fit <= 14 lines");
        req(pasted.length() > base12.length() && pasted.length() < (base12 + bigPaste).length(),
                "large paste keeps maximal fitting slice and drops excess");

        // Formatting-code cut rollback check
        req(CustomBook443.rollbackIncompleteFormatting("abc&#12", 7, 0) == 3,
                "rollback incomplete &#12 hex");
        req(CustomBook443.rollbackIncompleteFormatting("abc&", 4, 0) == 3,
                "rollback dangling &");
        req(CustomBook443.rollbackIncompleteFormatting("abc\\&", 5, 0) == 5,
                "escaped \\& is NOT rolled back");
        req(CustomBook443.rollbackIncompleteFormatting("abc{#12AB", 9, 0) == 3,
                "rollback incomplete {#12AB");
        String codeBoundaryPaste = "АБВГД&#123456ЕЖЗИКЛМН";
        String clampedAt5 = CustomBook443.fitPageText(
                "", codeBoundaryPaste, s -> s.length() <= 9); // "АБВГД&#12" would cut inside &#123456
        req("АБВГД".equals(clampedAt5), "paste clamp rolls back incomplete &# hex code, got: " + clampedAt5);

        // Pre-existing oversized text: typing more is blocked, backspace/deletion is always allowed
        String oversized = page14 + "\nЛишняя 15\nЛишняя 16";
        req(oversized.equals(CustomBook443.fitPageText(
                oversized, oversized + "x", s -> LootPoolBookEditorScreen443.fitsVanillaPage(font, s))),
                "adding text to already-oversized page is rejected");
        String shorter = oversized.substring(0, oversized.length() - 1);
        req(shorter.equals(CustomBook443.fitPageText(
                oversized, shorter, s -> LootPoolBookEditorScreen443.fitsVanillaPage(font, s))),
                "deleting from already-oversized page is always permitted");

        // Pure formatting overflow above MAX_PAGE_CHARS (1024) blocked even if 0px visual width
        StringBuilder zeroWidthFlood = new StringBuilder();
        for (int i = 0; i < 600; i++) zeroWidthFlood.append("&6"); // 1200 chars, 0px
        String clampedChars = CustomBook443.fitPageText(
                "", zeroWidthFlood.toString(), s -> LootPoolBookEditorScreen443.fitsVanillaPage(font, s));
        req(clampedChars.length() <= CustomBook443.MAX_PAGE_CHARS,
                "MAX_PAGE_CHARS still enforced for 0-width formatting floods");
    }

    private static void testLecternAndBookshelfFairOddsAndCaps() throws Exception {
        CustomBookManager443.ensureDirectory();
        CustomBook443.BookDef necronomicon = new CustomBook443.BookDef(
                "necronomicon", "&4Некрономикон", "Неизвестен", Collections.singletonList("Запретные знания"));
        CustomBookManager443.saveBook(necronomicon);

        // 1) Pure LootPool436 Monte-Carlo matching user screenshot (image-8.png):
        //    Стекло (2-6, R=5, W=80), Железный блок (1-4, R=20, W=50), Некрономикон (1, R=50, W=10), Шт=3
        List<LootPool436.PoolEntry> screenshotPool = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:glass", 5.0D, 80.0D, 2, 6),
                new LootPool436.PoolEntry("minecraft:iron_block", 20.0D, 50.0D, 1, 4),
                new LootPool436.PoolEntry("settledlands:book/necronomicon", 50.0D, 10.0D, 1, 1));
        LootPool436.Parsed nodeRolls3 = new LootPool436.Parsed("test.txt", "", "", "3");
        Random rnd = new Random(444001L);

        int lecternHits = 0;
        final int trials = 10000;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> d = LootPool436.rollLecternDrops(
                    screenshotPool, nodeRolls3, id -> CustomBookManager443.isValidForLectern(id, true), rnd);
            req(d.size() <= 1, "lectern drop never > 1");
            if (d.size() == 1) {
                req("settledlands:book/necronomicon".equals(d.get(0).itemId), "lectern only accepts book");
                lecternHits++;
            }
        }
        double lecRate = lecternHits / (double) trials;
        // Direct %: necronomicon has rarity 50 -> exact 50% chance on lectern
        req(lecRate >= 0.475D && lecRate <= 0.525D,
                "lectern rate matches direct 50% pool chance, got " + lecRate);

        int shelfTotal = 0;
        int shelfMaxSeen = 0;
        int shelfZeroSeen = 0;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> d = LootPool436.rollBookshelfDrops(
                    screenshotPool, nodeRolls3, 6,
                    id -> CustomBookManager443.isValidForChiseledBookshelf(id, true), rnd);
            req(d.size() <= 1, "single book entry with count=1 NEVER duplicates on shelf, got " + d.size());
            for (LootPool436.Drop drop : d) {
                req("settledlands:book/necronomicon".equals(drop.itemId) && drop.count == 1,
                        "bookshelf drop is strictly 1 book");
            }
            shelfTotal += d.size();
            if (d.size() > shelfMaxSeen) shelfMaxSeen = d.size();
            if (d.isEmpty()) shelfZeroSeen++;
        }
        double shelfAvg = shelfTotal / (double) trials;
        req(shelfMaxSeen == 1 && shelfZeroSeen > 4500 && shelfAvg >= 0.475D && shelfAvg <= 0.525D,
                "bookshelf spawns 1 book ~50% of the time and 0 otherwise (avg=" + shelfAvg + ")");

        // 2) 50/50 mixed pool on lectern -> ~50% direct book chance
        List<LootPool436.PoolEntry> fiftyFifty = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:iron_block", 50.0D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("settledlands:book/necronomicon", 50.0D, 10.0D, 1, 1));
        int ffHits = 0;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> d = LootPool436.rollLecternDrops(
                    fiftyFifty, nodeRolls3, id -> CustomBookManager443.isValidForLectern(id, true), rnd);
            if (!d.isEmpty()) ffHits++;
        }
        double ffRate = ffHits / (double) trials;
        req(ffRate >= 0.475D && ffRate <= 0.525D,
                "50/50 pool on lectern with default Шт=3 spawns ~50%, got " + ffRate);

        // 3) Single-book pool (50%) with node Редк.<= 0-100 on lectern -> (51/101)*50% ~= 25.25%
        List<LootPool436.PoolEntry> soloBook50 = Collections.singletonList(
                new LootPool436.PoolEntry("settledlands:book/necronomicon", 50.0D, 10.0D, 1, 1));
        LootPool436.Parsed rarity0to100 = new LootPool436.Parsed("solo.txt", "0-100", "", "3");
        int rarHits = 0;
        for (int i = 0; i < trials; i++) {
            if (!LootPool436.rollLecternDrops(
                    soloBook50, rarity0to100, id -> CustomBookManager443.isValidForLectern(id, true), rnd).isEmpty()) {
                rarHits++;
            }
        }
        double rarRate = rarHits / (double) trials;
        req(rarRate >= 0.23D && rarRate <= 0.275D,
                "Редк.<= 0-100 with 50% book on lectern gives ~25.25%, got " + rarRate);

        // 4) Edge roll ranges: Шт=0, Шт=0-1
        LootPool436.Parsed zeroRolls = new LootPool436.Parsed("solo.txt", "", "", "0");
        req(LootPool436.rollLecternDrops(soloBook50, zeroRolls, id -> true, rnd).isEmpty(),
                "Шт=0 produces empty lectern");
        req(LootPool436.rollBookshelfDrops(soloBook50, zeroRolls, 6, id -> true, rnd).isEmpty(),
                "Шт=0 produces empty bookshelf");

        LootPool436.Parsed zeroOneRolls = new LootPool436.Parsed("solo.txt", "", "", "0-1");
        int z1Hits = 0;
        for (int i = 0; i < 4000; i++) {
            if (!LootPool436.rollLecternDrops(soloBook50, zeroOneRolls, id -> true, rnd).isEmpty()) z1Hits++;
        }
        double z1Rate = z1Hits / 4000.0D;
        req(z1Rate >= 0.22D && z1Rate <= 0.28D, "Шт=0-1 gives ~25% on 50% lectern book, got " + z1Rate);

        // 5) Full world/BE integration: stacked book count 2-6 with Шт=3 NEVER exceeds 3 slots!
        MockServer server = new MockServer();
        MockLevel level = new MockLevel(server, DungeonManager425.HELL_DUNGEON_LEVEL);
        server.overworld = level;
        Path poolsDir = LootPoolManager436.ensureDirectory();
        Files.createDirectories(poolsDir);
        Files.writeString(poolsDir.resolve("stacked_books_444.txt"),
                "settledlands:book/necronomicon | 100 | 10 | 2-6\n");
        Files.writeString(poolsDir.resolve("full6_books_444.txt"),
                "settledlands:book/necronomicon | 100 | 10 | 6\n");
        Files.writeString(poolsDir.resolve("screenshot_444.txt"),
                "minecraft:glass | 5 | 80 | 2-6\n"
                + "minecraft:iron_block | 20 | 50 | 1-4\n"
                + "settledlands:book/necronomicon | 50 | 10 | 1\n");

        ChiseledBookShelfBlock shelfBlock = new ChiseledBookShelfBlock();
        LecternBlock lecternBlock = new LecternBlock();
        ChestBlock chestBlock = new ChestBlock();

        NpcDialogueGraph gStackedShelf = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node stackedNode = gStackedShelf.add(NpcDialogueGraph.TRIGGER, 0, 0);
        LootPool436.applyDefaults(stackedNode);
        LootPool436.setPoolFile(stackedNode, "stacked_books_444.txt");
        LootPool436.setRolls(stackedNode, "3");
        ChestGraph396.chestNode(gStackedShelf).next = stackedNode.id;

        for (int run = 0; run < 200; run++) {
            BlockPos pos = new BlockPos(run, 64, 0);
            level.setBlock(pos, new BlockState(shelfBlock, false), 3);
            ChiseledBookShelfBlockEntity be = new ChiseledBookShelfBlockEntity();
            level.putBlockEntity(pos, be);
            int placed = LootPoolManager436.populateContainer(level, pos, gStackedShelf);
            req(placed >= 2 && placed <= 3, "stacked count 2-6 with Шт=3 places 2..3 books (never >3!), got " + placed);
            int nonEmpty = 0;
            BlockState st = level.getBlockState(pos);
            for (int s = 0; s < 6; s++) {
                boolean has = be.getItem(s) != null && !be.getItem(s).isEmpty();
                if (has) nonEmpty++;
                req(Boolean.valueOf(has).equals(
                        st.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(s))),
                        "shelf slot property synced");
            }
            req(nonEmpty == placed, "occupied slots match placed count (" + nonEmpty + ")");
        }

        // Шт=6 on shelf with count=6 allows all 6 slots
        LootPool436.setPoolFile(stackedNode, "full6_books_444.txt");
        LootPool436.setRolls(stackedNode, "6");
        BlockPos fullShelfPos = new BlockPos(999, 64, 0);
        level.setBlock(fullShelfPos, new BlockState(shelfBlock, false), 3);
        ChiseledBookShelfBlockEntity fullShelf = new ChiseledBookShelfBlockEntity();
        level.putBlockEntity(fullShelfPos, fullShelf);
        int placed6 = LootPoolManager436.populateContainer(level, fullShelfPos, gStackedShelf);
        req(placed6 == 6, "Шт=6 with count=6 fills all 6 bookshelf slots, got " + placed6);

        // Mixed screenshot pool on Lectern (300 containers): has both populated (~50%) AND empty (~50%) lecterns!
        NpcDialogueGraph gLecMix = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node mixNode = gLecMix.add(NpcDialogueGraph.TRIGGER, 0, 0);
        LootPool436.applyDefaults(mixNode);
        LootPool436.setPoolFile(mixNode, "screenshot_444.txt");
        LootPool436.setRolls(mixNode, "3");
        ChestGraph396.chestNode(gLecMix).next = mixNode.id;

        int filledLecs = 0;
        int emptyLecs = 0;
        for (int i = 0; i < 300; i++) {
            BlockPos pos = new BlockPos(i, 70, 5);
            level.setBlock(pos, new BlockState(lecternBlock, false), 3);
            LecternBlockEntity lec = new LecternBlockEntity();
            level.putBlockEntity(pos, lec);
            int p = LootPoolManager436.populateContainer(level, pos, gLecMix);
            if (p == 1) {
                req(lec.hasBook() && lec.getBook().is(Items.WRITTEN_BOOK), "lectern has written book");
                filledLecs++;
            } else {
                req(p == 0 && !lec.hasBook(), "missed lectern stays empty");
                emptyLecs++;
            }
        }
        req(filledLecs >= 115 && filledLecs <= 185 && emptyLecs >= 115 && emptyLecs <= 185,
                "mixed pool on lecterns produces ~50% filled (" + filledLecs + ") and ~50% empty (" + emptyLecs + ")");

        // Regular chest with screenshot pool still gets non-books (Glass & Iron Block) as normal
        boolean sawGlassOrIron = false;
        for (int i = 0; i < 30 && !sawGlassOrIron; i++) {
            BlockPos cPos = new BlockPos(i, 80, 5);
            level.setBlock(cPos, new BlockState(chestBlock, false), 3);
            MockChestBE chest = new MockChestBE();
            level.putBlockEntity(cPos, chest);
            LootPoolManager436.populateContainer(level, cPos, gLecMix);
            for (int s = 0; s < chest.getContainerSize(); s++) {
                ItemStack st = chest.getItem(s);
                if (st != null && !st.isEmpty() && !st.is(Items.WRITTEN_BOOK)) {
                    sawGlassOrIron = true;
                    break;
                }
            }
        }
        req(sawGlassOrIron, "regular chest with mixed pool still receives Glass / Iron Block");
    }
}
