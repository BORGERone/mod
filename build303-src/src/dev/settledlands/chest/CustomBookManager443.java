package dev.settledlands.chest;

import dev.settledlands.npc.NpcItems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 443: server disk persistence ({@code config/settledlands/books/<id>.txt}),
 * Minecraft {@link ItemStack} ({@code minecraft:written_book} + {@code DataComponents}),
 * inventory book extraction, and lectern/chiseled-bookshelf item compatibility checks.
 *
 * <p>Dedicated-server safe: never references any client GUI class.</p>
 */
public final class CustomBookManager443 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/CustomBook443");
    private static final Set<String> WARNED = new HashSet<>();

    private static final String README_BOOKS_DISABLED =
            "# SettledLands Custom Loot-Pool Books\n"
            + "# Put <id>.txt book files here (or edit via '+ Книга' inside the in-game Loot Pool Editor).\n"
            + "# Reference a custom book inside loot_pools/*.txt as:  settledlands:book/<id>\n"
            + "#\n"
            + "# Format:\n"
            + "#   title: &6&lDragon Chronicle\n"
            + "#   author: Archmage\n"
            + "#   === PAGE ===\n"
            + "#   First page text with &4colors &rand lines...\n"
            + "#   === PAGE ===\n"
            + "#   Second page text (hex colors like &#FF8800 work too).\n";

    private CustomBookManager443() {}

    public static Path booksDir() {
        try {
            Path cfg = FMLPaths.CONFIGDIR.get();
            if (cfg != null) {
                return cfg.resolve("settledlands").resolve("books");
            }
        } catch (Throwable ignored) {
        }
        return Paths.get("config", "settledlands", "books");
    }

    public static Path ensureDirectory() {
        Path dir = booksDir();
        try {
            boolean fresh = !Files.exists(dir);
            Files.createDirectories(dir);
            if (fresh) {
                Path readme = dir.resolve("README_BOOKS.txt.disabled");
                if (!Files.exists(readme)) {
                    Files.writeString(readme, README_BOOKS_DISABLED, StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ex) {
            warnOnce("mkdir:" + dir, "custom_book: failed to create directory " + dir + ": " + ex.getMessage());
        }
        return dir;
    }

    public static Path resolveBookPath(String rawBookId) {
        String id = CustomBook443.normalizeBookId(rawBookId);
        if (id.isEmpty()) return null;
        return ensureDirectory().resolve(id + ".txt");
    }

    public static List<String> listBookIds() {
        Path dir = ensureDirectory();
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> {
                if (p == null || !Files.isRegularFile(p)) return;
                String fileName = p.getFileName().toString();
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".txt")) return;
                String id = CustomBook443.normalizeBookId(fileName);
                if (!id.isEmpty()) out.add(id);
            });
        } catch (Throwable ex) {
            warnOnce("list:" + dir, "custom_book: failed to list " + dir + ": " + ex.getMessage());
        }
        Collections.sort(out);
        return out;
    }

    public static List<CustomBook443.BookDef> listBooks() {
        List<String> ids = listBookIds();
        List<CustomBook443.BookDef> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            CustomBook443.BookDef def = loadBookQuiet(id);
            if (def != null) out.add(def);
        }
        return out;
    }

    public static boolean bookExists(String rawId) {
        Path p = resolveBookPath(rawId);
        return p != null && Files.isRegularFile(p);
    }

    public static CustomBook443.BookDef loadBookQuiet(String rawId) {
        String id = CustomBook443.normalizeBookId(rawId);
        if (id.isEmpty()) return null;
        Path path = booksDir().resolve(id + ".txt");
        if (!Files.isRegularFile(path)) return null;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return CustomBook443.parseBookFile(id, lines);
        } catch (Throwable ex) {
            warnOnce("read:" + id, "custom_book: failed reading " + path + ": " + ex.getMessage());
            return null;
        }
    }

    public static String saveBook(CustomBook443.BookDef book) {
        if (book == null || book.id.isEmpty()) return "";
        Path path = resolveBookPath(book.id);
        if (path == null) return "";
        try {
            String text = CustomBook443.formatBookFile(book);
            Files.writeString(path, text, StandardCharsets.UTF_8);
            return book.id;
        } catch (Throwable ex) {
            warnOnce("save:" + book.id, "custom_book: failed saving " + path + ": " + ex.getMessage());
            return "";
        }
    }

    public static boolean deleteBook(String rawId) {
        Path path = resolveBookPath(rawId);
        if (path == null) return false;
        try {
            return Files.deleteIfExists(path);
        } catch (Throwable ex) {
            warnOnce("del:" + rawId, "custom_book: failed deleting " + path + ": " + ex.getMessage());
            return false;
        }
    }

    // ---- Minecraft Component / ItemStack builders ---------------------------

    public static Component toFormattedComponent(String raw, boolean forceNoItalicDefault) {
        if (raw == null || raw.isEmpty()) {
            return forceNoItalicDefault
                    ? Component.empty().withStyle(st -> st.withItalic(Boolean.FALSE))
                    : Component.empty();
        }
        List<CustomBook443.Span> spans = CustomBook443.parseSpans(raw);
        if (spans.isEmpty()) {
            return forceNoItalicDefault
                    ? Component.empty().withStyle(st -> st.withItalic(Boolean.FALSE))
                    : Component.empty();
        }
        MutableComponent root = Component.empty();
        if (forceNoItalicDefault) {
            root = root.withStyle(st -> st.withItalic(Boolean.FALSE));
        }
        for (CustomBook443.Span sp : spans) {
            final int rgb = sp.rgb;
            final boolean bold = sp.bold;
            final boolean italic = sp.italic;
            final boolean italicOff = !sp.italic && forceNoItalicDefault;
            final boolean underlined = sp.underlined;
            final boolean strikethrough = sp.strikethrough;
            final boolean obfuscated = sp.obfuscated;
            MutableComponent part = Component.literal(sp.text).withStyle(st -> {
                Style s = st;
                if (rgb >= 0) s = s.withColor(rgb);
                if (bold) s = s.withBold(Boolean.TRUE);
                if (italic) s = s.withItalic(Boolean.TRUE);
                else if (italicOff) s = s.withItalic(Boolean.FALSE);
                if (underlined) s = s.withUnderlined(Boolean.TRUE);
                if (strikethrough) s = s.withStrikethrough(Boolean.TRUE);
                if (obfuscated) s = s.withObfuscated(Boolean.TRUE);
                return s;
            });
            root.append((Component) part);
        }
        return root;
    }

    public static String componentToFormatted(Component component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();
        final int[] prevRgb = new int[] { -1 };
        final boolean[] prevFlags = new boolean[5]; // bold, italic, underline, strike, obf
        try {
            component.visit((style, text) -> {
                if (text == null || text.isEmpty()) return Optional.empty();
                TextColor tc = style == null ? null : style.getColor();
                int rgb = tc == null ? -1 : (tc.getValue() & 0xFFFFFF);
                boolean b = style != null && style.isBold();
                boolean it = style != null && style.isItalic();
                boolean un = style != null && style.isUnderlined();
                boolean st = style != null && style.isStrikethrough();
                boolean ob = style != null && style.isObfuscated();

                boolean turnedOffFlag = (prevFlags[0] && !b)
                        || (prevFlags[1] && !it)
                        || (prevFlags[2] && !un)
                        || (prevFlags[3] && !st)
                        || (prevFlags[4] && !ob)
                        || (prevRgb[0] >= 0 && rgb < 0);
                boolean colorChanged = rgb != prevRgb[0];
                if (turnedOffFlag) {
                    out.append("&r");
                    prevRgb[0] = -1;
                    for (int k = 0; k < 5; k++) prevFlags[k] = false;
                    colorChanged = rgb >= 0;
                }
                if (colorChanged && rgb >= 0) {
                    char code = CustomBook443.codeForRgb(rgb);
                    if (code != 0) {
                        out.append('&').append(code);
                    } else {
                        out.append(String.format(Locale.ROOT, "&#%06X", Integer.valueOf(rgb)));
                    }
                    prevRgb[0] = rgb;
                }
                if (b && !prevFlags[0]) { out.append("&l"); prevFlags[0] = true; }
                if (it && !prevFlags[1]) { out.append("&o"); prevFlags[1] = true; }
                if (un && !prevFlags[2]) { out.append("&n"); prevFlags[2] = true; }
                if (st && !prevFlags[3]) { out.append("&m"); prevFlags[3] = true; }
                if (ob && !prevFlags[4]) { out.append("&k"); prevFlags[4] = true; }

                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    if (ch == '§') out.append('&');
                    else out.append(ch);
                }
                return Optional.empty();
            }, Style.EMPTY);
        } catch (Throwable ignored) {
            return component.getString();
        }
        return out.toString();
    }

    public static ItemStack buildBookStack(CustomBook443.BookDef book, int count) {
        if (book == null) return ItemStack.EMPTY;
        int safeCount = Math.max(1, Math.min(16, count));
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK, safeCount);
        if (!book.title.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, toFormattedComponent(book.title, true));
        }
        String plainTitle = book.plainTitle();
        if (plainTitle.isEmpty()) plainTitle = "Book";
        if (plainTitle.length() > CustomBook443.MAX_WRITTEN_TITLE_LEN) {
            plainTitle = plainTitle.substring(0, CustomBook443.MAX_WRITTEN_TITLE_LEN);
        }

        List<Filterable<Component>> pages = new ArrayList<>(Math.max(1, book.pages.size()));
        for (String pageText : book.pages) {
            pages.add(Filterable.passThrough(toFormattedComponent(pageText, false)));
        }
        if (pages.isEmpty()) {
            pages.add(Filterable.passThrough((Component) Component.empty()));
        }
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(plainTitle),
                book.author == null ? "" : book.author,
                0,
                pages,
                true);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return stack;
    }

    public static boolean hasCustomBookData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK)) return true;
        return stack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null
                || stack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null;
    }

    public static CustomBook443.BookDef extractBookFromStack(ItemStack stack, String suggestedId) {
        if (stack == null || stack.isEmpty()) return null;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        String formattedCustomName = customName == null ? "" : componentToFormatted(customName);

        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            String rawTitle = !formattedCustomName.isEmpty()
                    ? formattedCustomName
                    : (written.title() == null || written.title().raw() == null ? "" : written.title().raw());
            if (rawTitle.isEmpty()) rawTitle = stack.getHoverName().getString();
            String author = written.author() == null ? "" : written.author();
            List<String> pages = new ArrayList<>();
            if (written.pages() != null) {
                for (Filterable<Component> fc : written.pages()) {
                    if (pages.size() >= CustomBook443.MAX_PAGES) break;
                    Component comp = fc == null ? null : fc.raw();
                    pages.add(comp == null ? "" : componentToFormatted(comp));
                }
            }
            if (pages.isEmpty()) pages.add("");
            String id = CustomBook443.normalizeBookId(suggestedId);
            if (id.isEmpty()) id = CustomBook443.normalizeBookId(rawTitle);
            if (id.isEmpty()) id = "imported_book";
            return new CustomBook443.BookDef(id, rawTitle, author, pages);
        }

        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK)) {
            String rawTitle = !formattedCustomName.isEmpty()
                    ? formattedCustomName
                    : stack.getHoverName().getString();
            List<String> pages = new ArrayList<>();
            if (writable != null && writable.pages() != null) {
                for (Filterable<String> fs : writable.pages()) {
                    if (pages.size() >= CustomBook443.MAX_PAGES) break;
                    String s = fs == null || fs.raw() == null ? "" : fs.raw().replace('§', '&');
                    pages.add(s);
                }
            }
            if (pages.isEmpty()) pages.add("");
            String id = CustomBook443.normalizeBookId(suggestedId);
            if (id.isEmpty()) id = CustomBook443.normalizeBookId(rawTitle);
            if (id.isEmpty()) id = "imported_book";
            return new CustomBook443.BookDef(id, rawTitle, "", pages);
        }
        return null;
    }

    public static ItemStack resolveItemStack(String itemId, int count, boolean preferServerDisk) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        if (CustomBook443.isBookItemId(itemId)) {
            String bookId = CustomBook443.bookIdFromItemId(itemId);
            if (bookId.isEmpty()) return ItemStack.EMPTY;
            CustomBook443.BookDef def = preferServerDisk
                    ? loadBookQuiet(bookId)
                    : CustomBook443.getClientBook(bookId);
            if (def == null && !preferServerDisk) {
                def = loadBookQuiet(bookId);
            }
            return def == null ? ItemStack.EMPTY : buildBookStack(def, count);
        }
        return NpcItems.stack(itemId, count);
    }

    public static boolean isValidForLectern(String itemId, boolean preferServerDisk) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (CustomBook443.isBookItemId(itemId)) {
            return !resolveItemStack(itemId, 1, preferServerDisk).isEmpty();
        }
        ItemStack probe = NpcItems.stack(itemId, 1);
        return !probe.isEmpty() && probe.is(ItemTags.LECTERN_BOOKS);
    }

    public static boolean isValidForChiseledBookshelf(String itemId, boolean preferServerDisk) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (CustomBook443.isBookItemId(itemId)) {
            return !resolveItemStack(itemId, 1, preferServerDisk).isEmpty();
        }
        ItemStack probe = NpcItems.stack(itemId, 1);
        return !probe.isEmpty() && probe.is(ItemTags.BOOKSHELF_BOOKS);
    }

    private static void warnOnce(String key, String msg) {
        if (WARNED.add(key)) {
            LOG.warn(msg);
        }
    }
}
