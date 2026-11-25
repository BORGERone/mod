package dev.settledlands.chest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Build 443: pure data model, color/formatting parser, file serializer, wire codec, and client
 * summary cache for custom colored loot-pool books ({@code config/settledlands/books/<id>.txt},
 * referenced in loot pools as {@code settledlands:book/<id>}).
 *
 * <p>Zero Minecraft/client class dependencies so it is safe on dedicated servers and testable in
 * standalone JVM probes.</p>
 */
public final class CustomBook443 {

    public static final String BOOK_ITEM_PREFIX = "settledlands:book/";
    public static final String SHORT_BOOK_PREFIX = "book:";
    public static final int MAX_ID_LEN = 48;
    public static final int MAX_TITLE_LEN = 96;
    public static final int MAX_WRITTEN_TITLE_LEN = 32;
    public static final int MAX_AUTHOR_LEN = 64;
    public static final int MAX_PAGES = 100;
    public static final int MAX_PAGE_CHARS = 1024;
    public static final String DEFAULT_AUTHOR = "Неизвестен";
    public static final String PAGE_MARKER = "=== PAGE ===";
    public static final String ALT_PAGE_MARKER = "--- PAGE ---";

    private static final String README_STEM = "readme_books";

    public static final class ColorDef {
        public final char code;
        public final int rgb;
        public final String key;

        ColorDef(char code, int rgb, String key) {
            this.code = code;
            this.rgb = rgb;
            this.key = key;
        }
    }

    /** All 16 standard Minecraft formatting colors in palette order. */
    public static final ColorDef[] COLORS = {
        new ColorDef('0', 0x000000, "black"),
        new ColorDef('1', 0x0000AA, "dark_blue"),
        new ColorDef('2', 0x00AA00, "dark_green"),
        new ColorDef('3', 0x00AAAA, "dark_aqua"),
        new ColorDef('4', 0xAA0000, "dark_red"),
        new ColorDef('5', 0xAA00AA, "dark_purple"),
        new ColorDef('6', 0xFFAA00, "gold"),
        new ColorDef('7', 0xAAAAAA, "gray"),
        new ColorDef('8', 0x555555, "dark_gray"),
        new ColorDef('9', 0x5555FF, "blue"),
        new ColorDef('a', 0x55FF55, "green"),
        new ColorDef('b', 0x55FFFF, "aqua"),
        new ColorDef('c', 0xFF5555, "red"),
        new ColorDef('d', 0xFF55FF, "light_purple"),
        new ColorDef('e', 0xFFFF55, "yellow"),
        new ColorDef('f', 0xFFFFFF, "white"),
    };

    private static final Map<Character, Integer> CODE_TO_RGB = new HashMap<>();
    private static final Map<Integer, Character> RGB_TO_CODE = new HashMap<>();

    static {
        for (ColorDef c : COLORS) {
            CODE_TO_RGB.put(Character.valueOf(c.code), Integer.valueOf(c.rgb));
            RGB_TO_CODE.put(Integer.valueOf(c.rgb), Character.valueOf(c.code));
        }
    }

    public static final class Span {
        public final String text;
        public final int rgb;
        public final char colorCode;
        public final boolean bold;
        public final boolean italic;
        public final boolean underlined;
        public final boolean strikethrough;
        public final boolean obfuscated;

        public Span(String text, int rgb, char colorCode,
                boolean bold, boolean italic, boolean underlined,
                boolean strikethrough, boolean obfuscated) {
            this.text = text == null ? "" : text;
            this.rgb = rgb;
            this.colorCode = colorCode;
            this.bold = bold;
            this.italic = italic;
            this.underlined = underlined;
            this.strikethrough = strikethrough;
            this.obfuscated = obfuscated;
        }
    }

    public static final class BookDef {
        public final String id;
        public final String title;
        public final String author;
        public final List<String> pages;
        public final int summaryPageCount;

        public BookDef(String id, String title, String author, List<String> pages) {
            this(id, title, author, pages, pages == null ? 0 : pages.size());
        }

        public BookDef(String id, String title, String author, List<String> pages, int summaryPageCount) {
            this.id = normalizeBookId(id);
            this.title = clipText(title == null ? "" : title.replace('\r', ' ').replace('\n', ' '),
                    MAX_TITLE_LEN);
            this.author = clipText(author == null ? "" : author.replace('\r', ' ').replace('\n', ' ').trim(),
                    MAX_AUTHOR_LEN);
            List<String> cleanPages = new ArrayList<>();
            if (pages != null) {
                for (String p : pages) {
                    if (cleanPages.size() >= MAX_PAGES) break;
                    cleanPages.add(normalizePageText(p));
                }
            }
            if (cleanPages.isEmpty()) {
                cleanPages.add("");
            }
            this.pages = Collections.unmodifiableList(cleanPages);
            this.summaryPageCount = Math.max(this.pages.size(), summaryPageCount);
        }

        public String itemId() {
            return id.isEmpty() ? "" : BOOK_ITEM_PREFIX + id;
        }

        public String plainTitle() {
            String plain = stripFormatting(title).trim();
            return plain.isEmpty() ? id : plain;
        }
    }

    private static volatile List<String> clientBookIds = Collections.emptyList();
    private static final Map<String, BookDef> CLIENT_BOOKS = new ConcurrentHashMap<>();
    private static volatile Runnable clientListListener;
    private static volatile Consumer<String> clientDataListener;

    private CustomBook443() {}

    // ---- client cache & listeners -------------------------------------------

    public static void setClientListeners(Runnable onListUpdated, Consumer<String> onDataReceived) {
        clientListListener = onListUpdated;
        clientDataListener = onDataReceived;
    }

    public static void setClientBookSummaries(List<BookDef> summaries) {
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (summaries != null) {
            for (BookDef b : summaries) {
                if (b == null || b.id.isEmpty() || !seen.add(b.id)) continue;
                ids.add(b.id);
                BookDef existing = CLIENT_BOOKS.get(b.id);
                if (existing != null && existing.title.equals(b.title)
                        && existing.author.equals(b.author)
                        && existing.pages.size() == b.summaryPageCount) {
                    continue;
                }
                CLIENT_BOOKS.put(b.id, b);
            }
        }
        CLIENT_BOOKS.keySet().retainAll(seen);
        Collections.sort(ids);
        clientBookIds = Collections.unmodifiableList(ids);
    }

    public static void putClientBook(BookDef book) {
        if (book == null || book.id.isEmpty()) return;
        CLIENT_BOOKS.put(book.id, book);
        if (!clientBookIds.contains(book.id)) {
            List<String> updated = new ArrayList<>(clientBookIds);
            updated.add(book.id);
            Collections.sort(updated);
            clientBookIds = Collections.unmodifiableList(updated);
        }
    }

    public static void removeClientBook(String bookId) {
        String id = normalizeBookId(bookId);
        if (id.isEmpty()) return;
        CLIENT_BOOKS.remove(id);
        if (clientBookIds.contains(id)) {
            List<String> updated = new ArrayList<>(clientBookIds);
            updated.remove(id);
            clientBookIds = Collections.unmodifiableList(updated);
        }
    }

    public static BookDef getClientBook(String bookId) {
        String id = normalizeBookId(bookId);
        return id.isEmpty() ? null : CLIENT_BOOKS.get(id);
    }

    public static List<String> getClientBookIds() {
        return clientBookIds;
    }

    public static List<BookDef> getClientBooks() {
        List<String> ids = clientBookIds;
        List<BookDef> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            BookDef b = CLIENT_BOOKS.get(id);
            if (b != null) out.add(b);
        }
        return out;
    }

    public static void notifyClientBooksUpdated() {
        Runnable cb = clientListListener;
        if (cb != null) {
            try {
                cb.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void notifyClientBookData(String payload) {
        Consumer<String> cb = clientDataListener;
        if (cb != null && payload != null) {
            try {
                cb.accept(payload);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- ids & item references ----------------------------------------------

    public static boolean isBookItemId(String itemId) {
        if (itemId == null) return false;
        String s = itemId.trim().toLowerCase(Locale.ROOT);
        return s.startsWith(BOOK_ITEM_PREFIX) || s.startsWith(SHORT_BOOK_PREFIX);
    }

    public static String bookIdFromItemId(String itemId) {
        if (itemId == null) return "";
        String s = itemId.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith(BOOK_ITEM_PREFIX)) {
            return normalizeBookId(s.substring(BOOK_ITEM_PREFIX.length()));
        }
        if (lower.startsWith(SHORT_BOOK_PREFIX)) {
            return normalizeBookId(s.substring(SHORT_BOOK_PREFIX.length()));
        }
        return normalizeBookId(s);
    }

    public static String toBookItemId(String bookId) {
        String norm = normalizeBookId(bookId);
        return norm.isEmpty() ? "" : BOOK_ITEM_PREFIX + norm;
    }

    public static String normalizeBookId(String raw) {
        if (raw == null) return "";
        String s = stripFormatting(raw).trim();
        if (s.isEmpty()) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith(BOOK_ITEM_PREFIX)) {
            s = s.substring(BOOK_ITEM_PREFIX.length());
        } else if (lower.startsWith(SHORT_BOOK_PREFIX)) {
            s = s.substring(SHORT_BOOK_PREFIX.length());
        }
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        if (s.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            s = s.substring(0, s.length() - 4);
        }
        s = transliterateToAsciiId(s);
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                sb.append(c);
                lastUnderscore = false;
            } else if (c == '_' || c == ' ' || c == '.') {
                if (sb.length() > 0 && !lastUnderscore) {
                    sb.append('_');
                    lastUnderscore = true;
                }
            }
        }
        while (sb.length() > 0) {
            char last = sb.charAt(sb.length() - 1);
            if (last == '_' || last == '-') sb.setLength(sb.length() - 1);
            else break;
        }
        while (sb.length() > 0 && (sb.charAt(0) == '_' || sb.charAt(0) == '-')) {
            sb.deleteCharAt(0);
        }
        if (sb.length() > MAX_ID_LEN) {
            sb.setLength(MAX_ID_LEN);
            while (sb.length() > 0 && (sb.charAt(sb.length() - 1) == '_' || sb.charAt(sb.length() - 1) == '-')) {
                sb.setLength(sb.length() - 1);
            }
        }
        String res = sb.toString();
        if (res.isEmpty() || README_STEM.equals(res) || "readme".equals(res)
                || "con".equals(res) || "prn".equals(res) || "aux".equals(res) || "nul".equals(res)) {
            return "";
        }
        return res;
    }

    public static String suggestNextBookId(Collection<String> existingIds) {
        Set<String> lower = new HashSet<>();
        if (existingIds != null) {
            for (String id : existingIds) {
                String n = normalizeBookId(id);
                if (!n.isEmpty()) lower.add(n);
            }
        }
        for (int i = 1; i <= 9999; i++) {
            String cand = "book_" + i;
            if (!lower.contains(cand)) return cand;
        }
        return "custom_book";
    }

    private static String transliterateToAsciiId(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length() * 2);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'а' -> out.append('a');
                case 'б' -> out.append('b');
                case 'в' -> out.append('v');
                case 'г' -> out.append('g');
                case 'д' -> out.append('d');
                case 'е', 'ё', 'э' -> out.append('e');
                case 'ж' -> out.append("zh");
                case 'з' -> out.append('z');
                case 'и', 'й' -> out.append('i');
                case 'к' -> out.append('k');
                case 'л' -> out.append('l');
                case 'м' -> out.append('m');
                case 'н' -> out.append('n');
                case 'о' -> out.append('o');
                case 'п' -> out.append('p');
                case 'р' -> out.append('r');
                case 'с' -> out.append('s');
                case 'т' -> out.append('t');
                case 'у' -> out.append('u');
                case 'ф' -> out.append('f');
                case 'х' -> out.append("kh");
                case 'ц' -> out.append("ts");
                case 'ч' -> out.append("ch");
                case 'ш' -> out.append("sh");
                case 'щ' -> out.append("shch");
                case 'ы' -> out.append('y');
                case 'ю' -> out.append("yu");
                case 'я' -> out.append("ya");
                case 'ъ', 'ь' -> {}
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    // ---- formatting & color codes (&, §, &#RRGGBB, {#RRGGBB}) ---------------

    public static int rgbForCode(char code) {
        Integer v = CODE_TO_RGB.get(Character.valueOf(Character.toLowerCase(code)));
        return v == null ? -1 : v.intValue();
    }

    public static char codeForRgb(int rgb) {
        Character c = RGB_TO_CODE.get(Integer.valueOf(rgb & 0xFFFFFF));
        return c == null ? 0 : c.charValue();
    }

    public static String stripFormatting(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder out = new StringBuilder(input.length());
        for (Span sp : parseSpans(input)) {
            out.append(sp.text);
        }
        return out.toString();
    }

    /**
     * Parses {@code &0..&f}, {@code §0..§f}, {@code &l/&o/&n/&m/&k/&r}, {@code &#RRGGBB},
     * {@code §#RRGGBB}, and {@code {#RRGGBB}} into styled spans. Supports consecutive style/color
     * sequences in either order (e.g. {@code &l&6} and {@code &6&l}) and {@code \&} escapes.
     */
    public static List<Span> parseSpans(String input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        List<Span> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int rgb = -1;
        char colorCode = 0;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        int n = input.length();
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < n && (input.charAt(i + 1) == '&' || input.charAt(i + 1) == '§')) {
                buf.append(input.charAt(i + 1));
                i++;
                continue;
            }
            if ((c == '&' || c == '§') && i + 1 < n) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 8 <= n) {
                    int parsedRgb = parseHexRgb(input, i + 2);
                    if (parsedRgb >= 0) {
                        boolean hadText = buf.length() > 0;
                        if (hadText) {
                            out.add(new Span(buf.toString(), rgb, colorCode, bold, italic,
                                    underlined, strikethrough, obfuscated));
                            buf.setLength(0);
                            bold = false;
                            italic = false;
                            underlined = false;
                            strikethrough = false;
                            obfuscated = false;
                        }
                        rgb = parsedRgb;
                        colorCode = codeForRgb(parsedRgb);
                        i += 7;
                        continue;
                    }
                }
                char low = Character.toLowerCase(next);
                int colRgb = rgbForCode(low);
                if (colRgb >= 0) {
                    boolean hadText = buf.length() > 0;
                    if (hadText) {
                        out.add(new Span(buf.toString(), rgb, colorCode, bold, italic,
                                underlined, strikethrough, obfuscated));
                        buf.setLength(0);
                        bold = false;
                        italic = false;
                        underlined = false;
                        strikethrough = false;
                        obfuscated = false;
                    }
                    rgb = colRgb;
                    colorCode = low;
                    i++;
                    continue;
                }
                if (low == 'l' || low == 'o' || low == 'n' || low == 'm' || low == 'k' || low == 'r') {
                    if (buf.length() > 0) {
                        out.add(new Span(buf.toString(), rgb, colorCode, bold, italic,
                                underlined, strikethrough, obfuscated));
                        buf.setLength(0);
                    }
                    switch (low) {
                        case 'l' -> bold = true;
                        case 'o' -> italic = true;
                        case 'n' -> underlined = true;
                        case 'm' -> strikethrough = true;
                        case 'k' -> obfuscated = true;
                        case 'r' -> {
                            rgb = -1;
                            colorCode = 0;
                            bold = false;
                            italic = false;
                            underlined = false;
                            strikethrough = false;
                            obfuscated = false;
                        }
                        default -> {}
                    }
                    i++;
                    continue;
                }
            } else if (c == '{' && i + 9 <= n && input.charAt(i + 1) == '#' && input.charAt(i + 8) == '}') {
                int parsedRgb = parseHexRgb(input, i + 2);
                if (parsedRgb >= 0) {
                    boolean hadText = buf.length() > 0;
                    if (hadText) {
                        out.add(new Span(buf.toString(), rgb, colorCode, bold, italic,
                                underlined, strikethrough, obfuscated));
                        buf.setLength(0);
                        bold = false;
                        italic = false;
                        underlined = false;
                        strikethrough = false;
                        obfuscated = false;
                    }
                    rgb = parsedRgb;
                    colorCode = codeForRgb(parsedRgb);
                    i += 8;
                    continue;
                }
            }
            buf.append(c);
        }

        if (buf.length() > 0) {
            out.add(new Span(buf.toString(), rgb, colorCode, bold, italic,
                    underlined, strikethrough, obfuscated));
        }
        return out;
    }

    private static int parseHexRgb(String s, int offset) {
        if (offset < 0 || offset + 6 > s.length()) return -1;
        int val = 0;
        for (int k = 0; k < 6; k++) {
            int d = Character.digit(s.charAt(offset + k), 16);
            if (d < 0) return -1;
            val = (val << 4) | d;
        }
        return val;
    }

    // ---- disk file format (config/settledlands/books/<id>.txt) -------------

    public static String formatBookFile(BookDef book) {
        if (book == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("# SettledLands Custom Book: ").append(book.id).append('\n');
        sb.append("# Formatting: &0-&f colors, &#RRGGBB hex, &l bold, &o italic, &n underline, &m strike, &r reset\n");
        sb.append("title: ").append(book.title).append('\n');
        sb.append("author: ").append(book.author).append('\n');
        for (String page : book.pages) {
            sb.append(PAGE_MARKER).append('\n');
            if (!page.isEmpty()) {
                sb.append(page).append('\n');
            }
        }
        return sb.toString();
    }

    public static BookDef parseBookFile(String bookId, List<String> lines) {
        String normId = normalizeBookId(bookId);
        if (normId.isEmpty()) return null;
        if (lines == null || lines.isEmpty()) {
            return new BookDef(normId, normId, DEFAULT_AUTHOR, Collections.singletonList(""));
        }

        String title = "";
        String author = "";
        boolean sawAuthorLine = false;
        List<String> pages = new ArrayList<>();
        StringBuilder curPage = null;
        boolean curHasLine = false;
        boolean sawPageMarker = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            if (!sawPageMarker && line.startsWith("#")) {
                continue;
            }
            String trimmed = line.trim();
            if (PAGE_MARKER.equals(trimmed) || ALT_PAGE_MARKER.equals(trimmed)) {
                if (curPage != null) {
                    pages.add(trimTrailingNewline(curPage.toString()));
                }
                curPage = new StringBuilder();
                curHasLine = false;
                sawPageMarker = true;
                continue;
            }
            if (!sawPageMarker && trimmed.toLowerCase(Locale.ROOT).startsWith("title:")) {
                title = trimmed.substring(6).trim();
                continue;
            }
            if (!sawPageMarker && trimmed.toLowerCase(Locale.ROOT).startsWith("author:")) {
                author = trimmed.substring(7).trim();
                sawAuthorLine = true;
                continue;
            }
            if (!sawPageMarker && trimmed.isEmpty()) {
                continue;
            }
            if (curPage == null) {
                curPage = new StringBuilder();
                curHasLine = false;
            }
            if (curHasLine) {
                curPage.append('\n');
            }
            curPage.append(line);
            curHasLine = true;
        }

        if (curPage != null) {
            pages.add(trimTrailingNewline(curPage.toString()));
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        if (title.isEmpty()) {
            title = normId;
        }
        if (!sawAuthorLine) {
            author = DEFAULT_AUTHOR;
        }
        return new BookDef(normId, title, author, pages);
    }

    // ---- Base64 wire codecs (safe for arbitrary UTF-8, colors, | and \n) ----

    public static String encodeBookWire(BookDef book) {
        if (book == null || book.id.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(book.id).append('|')
                .append(b64Encode(book.title)).append('|')
                .append(b64Encode(book.author)).append('|')
                .append(book.pages.size());
        for (String p : book.pages) {
            sb.append('|').append(b64Encode(p));
        }
        return sb.toString();
    }

    public static BookDef decodeBookWire(String wire) {
        if (wire == null || wire.isEmpty()) return null;
        String[] parts = wire.split("\\|", -1);
        if (parts.length < 4) return null;
        String id = normalizeBookId(parts[0]);
        if (id.isEmpty()) return null;
        String title = b64Decode(parts[1]);
        String author = b64Decode(parts[2]);
        int count;
        try {
            count = Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (count < 0) count = 0;
        if (count > MAX_PAGES) count = MAX_PAGES;
        List<String> pages = new ArrayList<>(Math.max(1, count));
        for (int i = 0; i < count && (4 + i) < parts.length; i++) {
            pages.add(b64Decode(parts[4 + i]));
        }
        if (pages.isEmpty()) pages.add("");
        return new BookDef(id, title, author, pages);
    }

    public static String encodeSummariesWire(List<BookDef> books) {
        if (books == null || books.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (BookDef b : books) {
            if (b == null || b.id.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(b.id).append('|')
                    .append(b64Encode(b.title)).append('|')
                    .append(b64Encode(b.author)).append('|')
                    .append(b.summaryPageCount);
        }
        return sb.toString();
    }

    public static List<BookDef> decodeSummariesWire(String wire) {
        if (wire == null || wire.isEmpty()) return Collections.emptyList();
        List<BookDef> out = new ArrayList<>();
        for (String line : wire.split("\n")) {
            if (line == null || line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 4) continue;
            String id = normalizeBookId(parts[0]);
            if (id.isEmpty()) continue;
            String title = b64Decode(parts[1]);
            String author = b64Decode(parts[2]);
            int pageCount = 1;
            try {
                pageCount = Math.max(1, Integer.parseInt(parts[3].trim()));
            } catch (NumberFormatException ignored) {
            }
            out.add(new BookDef(id, title, author, Collections.singletonList(""), pageCount));
        }
        return out;
    }

    private static String b64Encode(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    private static String b64Decode(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String normalizePageText(String text) {
        if (text == null || text.isEmpty()) return "";
        String norm = text.replace("\r\n", "\n").replace('\r', '\n');
        return norm.length() > MAX_PAGE_CHARS ? norm.substring(0, MAX_PAGE_CHARS) : norm;
    }

    private static String clipText(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    // ---- Build 444: exact page-fit helper ----------------------------------

    /**
     * Ensures an edit to a book page never exceeds {@link #MAX_PAGE_CHARS} or the visual
     * {@code fitsPredicate} (vanilla 114px width x 14 lines), while always allowing deletions
     * and binary-searching multi-character pastes so as much text fits as possible without
     * splitting formatting codes or surrogate pairs.
     */
    public static String fitPageText(String prevText, String candidateText,
            Predicate<String> fitsPredicate) {
        String prev = prevText == null ? "" : prevText;
        String next = candidateText == null ? "" : candidateText;
        if (next.equals(prev)) return next;
        if (next.length() <= prev.length()) {
            return next;
        }
        if (next.length() <= MAX_PAGE_CHARS
                && (fitsPredicate == null || fitsPredicate.test(next))) {
            return next;
        }
        if (prev.length() > MAX_PAGE_CHARS
                || (fitsPredicate != null && !fitsPredicate.test(prev))) {
            return prev;
        }

        int minLen = Math.min(prev.length(), next.length());
        int prefixLen = 0;
        while (prefixLen < minLen && prev.charAt(prefixLen) == next.charAt(prefixLen)) {
            prefixLen++;
        }
        int suffixLen = 0;
        while (suffixLen < (minLen - prefixLen)
                && prev.charAt(prev.length() - 1 - suffixLen)
                        == next.charAt(next.length() - 1 - suffixLen)) {
            suffixLen++;
        }

        int insertedLen = next.length() - prefixLen - suffixLen;
        if (insertedLen <= 1) {
            return prev;
        }

        String after = suffixLen > 0 ? next.substring(next.length() - suffixLen) : "";
        int maxAllowed = Math.max(0, MAX_PAGE_CHARS - prefixLen - suffixLen);
        int lo = 0;
        int hi = Math.min(insertedLen, maxAllowed);
        int bestK = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            String trial = next.substring(0, prefixLen + mid) + after;
            if (fitsPredicate == null || fitsPredicate.test(trial)) {
                bestK = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (bestK <= 0) {
            return prev;
        }

        int cut = prefixLen + bestK;
        if (cut > prefixLen && Character.isHighSurrogate(next.charAt(cut - 1))) {
            cut--;
        }
        cut = rollbackIncompleteFormatting(next, cut, prefixLen);
        if (cut <= prefixLen) {
            return prev;
        }
        String res = next.substring(0, cut) + after;
        if (res.length() <= MAX_PAGE_CHARS && (fitsPredicate == null || fitsPredicate.test(res))) {
            return res;
        }
        return prev;
    }

    /**
     * Walks {@code cut} backward if truncating at {@code cut} sliced inside {@code &}, {@code &#RRGGBB},
     * or {@code {#RRGGBB}}. Never moves earlier than {@code minStart}.
     */
    public static int rollbackIncompleteFormatting(String s, int cut, int minStart) {
        if (s == null || cut <= minStart) return Math.max(0, cut);
        int floor = Math.max(0, minStart);
        int len = Math.min(cut, s.length());
        if (len <= floor) return len;

        // Trailing single '&' or '§' (when not escaped by '\\')
        char last = s.charAt(len - 1);
        if ((last == '&' || last == '§') && !isEscapedBackslash(s, len - 1)) {
            return len - 1;
        }

        // Look back up to 9 chars for &#RRGGBB / §#RRGGBB / {#RRGGBB
        int searchFrom = Math.max(floor, len - 9);
        for (int idx = len - 1; idx >= searchFrom; idx--) {
            char ch = s.charAt(idx);
            if ((ch == '&' || ch == '§') && idx + 1 < len && s.charAt(idx + 1) == '#'
                    && !isEscapedBackslash(s, idx)) {
                int hexLen = len - (idx + 2);
                if (hexLen >= 0 && hexLen < 6 && areHexChars(s, idx + 2, len)) {
                    return idx;
                }
                break;
            }
            if (ch == '{' && idx + 1 < len && s.charAt(idx + 1) == '#') {
                int hexLen = len - (idx + 2);
                if (hexLen >= 0 && hexLen <= 6 && areHexChars(s, idx + 2, len)) {
                    return idx;
                }
                break;
            }
        }
        return len;
    }

    private static boolean isEscapedBackslash(String s, int idx) {
        int slashes = 0;
        for (int i = idx - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            slashes++;
        }
        return (slashes & 1) == 1;
    }

    private static boolean areHexChars(String s, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static String trimTrailingNewline(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return end == s.length() ? s : s.substring(0, end);
    }
}
