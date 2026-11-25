package dev.settledlands.chest;

import dev.settledlands.npc.NpcDialogueGraph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Build 436: pure rule, parser, serialization and weighted loot-roll engine for the «Пул предметов»
 * («Item Pool») chest graph node.
 *
 * <p>Stored inside stock {@link NpcDialogueGraph#TRIGGER} ({@code type == 2, special == 0}) with
 * {@code action = "trigger:loot_pool:<poolFile>|<maxRarity>|<maxWeight>|<rolls>"} so world/anchor
 * serialization requires zero schema changes. Keeps zero Minecraft/NeoForge runtime dependencies so
 * JVM probes can exercise all parsing and weighted-roll rules directly.</p>
 */
public final class LootPool436 {

    public static final String ACTION_PREFIX = "trigger:loot_pool:";
    public static final String STRIPPED_PREFIX = "loot_pool:";
    public static final String MENU_LABEL_KEY = "gui.settledlands.loot_pool.menu";
    public static final String TITLE_KEY = "gui.settledlands.loot_pool.title";
    public static final String DEFAULT_ROLLS = "3";
    public static final String DEFAULT_ACTION = ACTION_PREFIX + "|||" + DEFAULT_ROLLS;

    /** Warm amber-gold accent distinct from Teleport blue (#34A0EB) and stock block rows. */
    public static final int ACCENT_COLOR = -673727; // 0xFFF5B841

    public static final int MAX_FIELD_LEN = 24;
    public static final int MAX_CAP_LIMIT = 1_000_000;
    public static final int MAX_ROLLS_LIMIT = 54;

    private static volatile List<String> serverPools = Collections.emptyList();
    private static volatile Runnable clientPoolsListener;
    private static volatile Consumer<String> clientPoolDataListener;

    private LootPool436() {}

    public static void setClientListeners(Runnable poolsListener, Consumer<String> poolDataListener) {
        clientPoolsListener = poolsListener;
        clientPoolDataListener = poolDataListener;
    }

    public static void notifyClientPoolsUpdated() {
        Runnable r = clientPoolsListener;
        if (r != null) r.run();
    }

    public static void notifyClientPoolData(String payload) {
        Consumer<String> c = clientPoolDataListener;
        if (c != null) c.accept(payload);
    }

    public static void setServerPools(List<String> pools) {
        if (pools == null || pools.isEmpty()) {
            serverPools = Collections.emptyList();
            return;
        }
        List<String> clean = new ArrayList<>(pools.size());
        for (String p : pools) {
            String s = sanitizeFileName(p);
            if (!s.isEmpty() && !clean.contains(s)) clean.add(s);
        }
        Collections.sort(clean);
        serverPools = Collections.unmodifiableList(clean);
    }

    public static List<String> getServerPools() {
        return serverPools;
    }

    public static final class Parsed {
        public final String poolFile;
        public final String rawRarity;
        public final String rawWeight;
        public final String rawRolls;

        public Parsed(String poolFile, String rawRarity, String rawWeight, String rawRolls) {
            this.poolFile = poolFile == null ? "" : poolFile.trim();
            this.rawRarity = rawRarity == null ? "" : rawRarity.trim();
            this.rawWeight = rawWeight == null ? "" : rawWeight.trim();
            this.rawRolls = rawRolls == null ? "" : rawRolls.trim();
        }
    }

    public static final class Range {
        public final boolean unbounded;
        public final int min;
        public final int max;

        public Range(boolean unbounded, int min, int max) {
            this.unbounded = unbounded;
            this.min = min;
            this.max = max;
        }

        public static Range unbounded() {
            return new Range(true, 0, Integer.MAX_VALUE);
        }

        public int pick(Random rnd) {
            if (unbounded) return Integer.MAX_VALUE;
            if (min >= max || rnd == null) return min;
            return min + rnd.nextInt(max - min + 1);
        }
    }

    public static final class PoolEntry {
        public final String itemId;
        public final double rarity;
        public final double weight;
        public final int minCount;
        public final int maxCount;

        public PoolEntry(String itemId, double rarity, double weight, int minCount, int maxCount) {
            this.itemId = itemId;
            this.rarity = rarity;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        public PoolEntry(String itemId, int rarity, double weight, int minCount, int maxCount) {
            this(itemId, (double) rarity, weight, minCount, maxCount);
        }
    }

    public static final class Drop {
        public final String itemId;
        public final int count;

        public Drop(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    // ---- node identity ------------------------------------------------------

    public static boolean isLootPoolNode(NpcDialogueGraph.Node node) {
        return node != null
                && node.type == NpcDialogueGraph.TRIGGER
                && node.special == 0
                && isLootPoolAction(node.action);
    }

    public static boolean isLootPoolAction(String action) {
        return action != null && action.startsWith(ACTION_PREFIX);
    }

    public static boolean isStrippedLootPoolAction(String action) {
        return action != null && action.startsWith(STRIPPED_PREFIX);
    }

    public static void applyDefaults(NpcDialogueGraph.Node node) {
        if (node == null) return;
        node.type = NpcDialogueGraph.TRIGGER;
        if (!isLootPoolAction(node.action)) {
            node.action = DEFAULT_ACTION;
        }
    }

    // ---- serialization -----------------------------------------------------

    public static Parsed parse(String action) {
        if (action == null) return null;
        String payload;
        if (action.startsWith(ACTION_PREFIX)) {
            payload = action.substring(ACTION_PREFIX.length());
        } else if (action.startsWith(STRIPPED_PREFIX)) {
            payload = action.substring(STRIPPED_PREFIX.length());
        } else {
            return null;
        }
        String[] parts = payload.split("\\|", -1);
        String pool = parts.length >= 1 ? sanitizeFileName(parts[0]) : "";
        String rarity = parts.length >= 2 ? parts[1].trim() : "";
        String weight = parts.length >= 3 ? parts[2].trim() : "";
        String rolls = parts.length >= 4 ? parts[3].trim() : DEFAULT_ROLLS;
        return new Parsed(pool, rarity, weight, rolls);
    }

    public static String formatAction(String poolFile, String rawRarity, String rawWeight,
            String rawRolls) {
        return ACTION_PREFIX
                + sanitizeFileName(poolFile) + "|"
                + cleanField(rawRarity) + "|"
                + cleanField(rawWeight) + "|"
                + cleanField(rawRolls);
    }

    public static String poolFileOf(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? "" : p.poolFile;
    }

    public static String rawRarity(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? "" : p.rawRarity;
    }

    public static String rawWeight(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? "" : p.rawWeight;
    }

    public static String rawRolls(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? DEFAULT_ROLLS : p.rawRolls;
    }

    public static void setPoolFile(NpcDialogueGraph.Node node, String poolFile) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String rarity = p == null ? "" : p.rawRarity;
        String weight = p == null ? "" : p.rawWeight;
        String rolls = p == null ? DEFAULT_ROLLS : p.rawRolls;
        node.action = formatAction(poolFile, rarity, weight, rolls);
    }

    public static void setRarity(NpcDialogueGraph.Node node, String raw) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String pool = p == null ? "" : p.poolFile;
        String weight = p == null ? "" : p.rawWeight;
        String rolls = p == null ? DEFAULT_ROLLS : p.rawRolls;
        node.action = formatAction(pool, raw, weight, rolls);
    }

    public static void setWeight(NpcDialogueGraph.Node node, String raw) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String pool = p == null ? "" : p.poolFile;
        String rarity = p == null ? "" : p.rawRarity;
        String rolls = p == null ? DEFAULT_ROLLS : p.rawRolls;
        node.action = formatAction(pool, rarity, raw, rolls);
    }

    public static void setRolls(NpcDialogueGraph.Node node, String raw) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String pool = p == null ? "" : p.poolFile;
        String rarity = p == null ? "" : p.rawRarity;
        String weight = p == null ? "" : p.rawWeight;
        node.action = formatAction(pool, rarity, weight, raw);
    }

    public static boolean isRangeInput(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.length() > MAX_FIELD_LEN) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && c != '-' && c != '.' && c != ' ') {
                return false;
            }
        }
        return true;
    }

    public static String sanitizeFileName(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replace('\\', '/');
        if (t.contains("..")) return "";
        int slash = t.lastIndexOf('/');
        if (slash >= 0) t = t.substring(slash + 1);
        t = t.replace("|", "");
        if (".".equals(t)) return "";
        return t;
    }

    private static String cleanField(String raw) {
        return raw == null ? "" : raw.trim().replace("|", "");
    }

    // ---- range & file parsing ----------------------------------------------

    public static Range parseRange(String raw, int maxLimit) {
        if (raw == null) return Range.unbounded();
        String s = raw.trim();
        if (s.isEmpty()) return Range.unbounded();
        int sepIdx = s.indexOf("..");
        String left;
        String right;
        if (sepIdx >= 0) {
            left = s.substring(0, sepIdx).trim();
            right = s.substring(sepIdx + 2).trim();
        } else {
            int dash = s.indexOf('-');
            if (dash >= 0) {
                left = s.substring(0, dash).trim();
                right = s.substring(dash + 1).trim();
            } else {
                left = s;
                right = s;
            }
        }
        Integer a = tryParseInt(left);
        Integer b = tryParseInt(right);
        if (a == null && b == null) return Range.unbounded();
        if (a == null) a = 0;
        if (b == null) b = a;
        int ca = Math.max(0, Math.min(maxLimit, a));
        int cb = Math.max(0, Math.min(maxLimit, b));
        return ca <= cb ? new Range(false, ca, cb) : new Range(false, cb, ca);
    }

    private static Integer tryParseInt(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.valueOf(Integer.parseInt(t));
        } catch (NumberFormatException ignored) {
            try {
                double d = Double.parseDouble(t);
                if (!Double.isFinite(d)) return null;
                return Integer.valueOf((int) Math.round(d));
            } catch (NumberFormatException ignored2) {
                return null;
            }
        }
    }

    private static Double tryParseDouble(String s) {
        if (s == null) return null;
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return null;
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? Double.valueOf(d) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parses lines from {@code config/settledlands/loot_pools/*.txt}.
     * Format per line: {@code item_id | rarity_chance | weight | [count_or_range]}.
     */
    public static List<PoolEntry> parseLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Collections.emptyList();
        List<PoolEntry> out = new ArrayList<>();
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = stripComments(rawLine).trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split("\\|");
            if (cols.length < 3) continue;
            String itemId = cols[0].trim();
            if (itemId.isEmpty() || !itemId.contains(":")) continue;
            Double rarityObj = tryParseDouble(cols[1]);
            Double weightObj = tryParseDouble(cols[2]);
            if (rarityObj == null || weightObj == null) continue;
            double rarity = rarityObj;
            double weight = weightObj;
            if (!Double.isFinite(rarity) || rarity < 0.0D
                    || !Double.isFinite(weight) || weight <= 0.0D) {
                continue;
            }
            rarity = Math.min((double) MAX_CAP_LIMIT, rarity);

            int minCount = 1;
            int maxCount = 1;
            if (cols.length >= 4 && !cols[3].trim().isEmpty()) {
                Range cnt = parseRange(cols[3], 64);
                if (!cnt.unbounded) {
                    minCount = Math.max(1, cnt.min);
                    maxCount = Math.max(minCount, cnt.max);
                }
            }
            out.add(new PoolEntry(itemId, rarity, weight, minCount, maxCount));
        }
        return out;
    }

    private static String stripComments(String line) {
        if (line == null) return "";
        String t = line.trim();
        if (t.startsWith("#") || t.startsWith("//")) return "";
        int hash = line.indexOf(" #");
        int slashes = line.indexOf(" //");
        int cut = -1;
        if (hash >= 0 && slashes >= 0) cut = Math.min(hash, slashes);
        else if (hash >= 0) cut = hash;
        else if (slashes >= 0) cut = slashes;
        return cut >= 0 ? line.substring(0, cut) : line;
    }

    // ---- rolling -----------------------------------------------------------

    /**
     * Build 442: CS:GO-style weighted case rolling by {@code entry.rarity}, bounded by the crate's
     * total weight capacity ({@code spec.rawWeight}) and optional item/roll limit ({@code spec.rawRolls}).
     *
     * <p>The roulette always draws against all entries that fit inside the crate's <em>initial</em>
     * capacity ({@code initialWeight}) rather than shrinking the candidate set to {@code remainingWeight};
     * otherwise a tiny filler item (e.g. rarity 1, weight 1) would artificially jump to 100% chance on
     * later rolls whenever a heavier item leaves a small weight remainder.</p>
     */
    public static List<Drop> rollDrops(List<PoolEntry> entries, Parsed spec, Random rnd) {
        if (entries == null || entries.isEmpty() || spec == null || rnd == null) {
            return Collections.emptyList();
        }
        Range rarityCapRange = parseRange(spec.rawRarity, MAX_CAP_LIMIT);
        Range weightBudgetRange = parseRange(spec.rawWeight, MAX_CAP_LIMIT);
        Range rollsRange = parseRange(spec.rawRolls, MAX_ROLLS_LIMIT);

        // Crate total weight budget is picked once per container (e.g. 40 -> fits 2x weight 20 or 4x weight 10).
        double initialWeight = weightBudgetRange.unbounded
                ? Double.POSITIVE_INFINITY
                : (double) weightBudgetRange.pick(rnd);
        double remainingWeight = initialWeight;

        int maxRolls;
        if (!rollsRange.unbounded) {
            maxRolls = rollsRange.pick(rnd);
        } else if (!weightBudgetRange.unbounded) {
            maxRolls = MAX_ROLLS_LIMIT;
        } else {
            maxRolls = 3;
        }
        if (maxRolls <= 0) return Collections.emptyList();
        if (Double.isFinite(initialWeight) && initialWeight <= 1e-6D) {
            return Collections.emptyList();
        }

        List<Drop> out = new ArrayList<>(Math.min(maxRolls, 16));
        List<PoolEntry> eligible = new ArrayList<>(entries.size());

        for (int r = 0; r < maxRolls; r++) {
            if (Double.isFinite(remainingWeight) && remainingWeight <= 1e-6D) {
                break;
            }
            double maxRarity = rarityCapRange.unbounded
                    ? Double.POSITIVE_INFINITY
                    : (double) rarityCapRange.pick(rnd);

            eligible.clear();
            double totalRarityChance = 0.0D;
            for (PoolEntry e : entries) {
                if (e == null || !Double.isFinite(e.rarity) || e.rarity <= 0.0D) continue;
                if (!Double.isFinite(e.weight) || e.weight <= 0.0D) continue;
                if (e.rarity <= maxRarity + 1e-6D && e.weight <= initialWeight + 1e-6D) {
                    eligible.add(e);
                    totalRarityChance += e.rarity;
                }
            }
            if (eligible.isEmpty() || totalRarityChance <= 0.0D) {
                if (rarityCapRange.unbounded) {
                    break;
                }
                continue;
            }

            double pick = rnd.nextDouble() * totalRarityChance;
            double acc = 0.0D;
            PoolEntry chosen = eligible.get(eligible.size() - 1);
            for (PoolEntry e : eligible) {
                acc += e.rarity;
                if (acc >= pick) {
                    chosen = e;
                    break;
                }
            }

            if (Double.isFinite(remainingWeight) && chosen.weight > remainingWeight + 1e-6D) {
                // Item exceeded the remaining crate budget: stop in by-weight mode or skip this slot
                // without distorting the drop ratios of tiny low-rarity filler items.
                if (rollsRange.unbounded) {
                    break;
                }
                continue;
            }

            if (Double.isFinite(remainingWeight)) {
                remainingWeight = Math.max(0.0D, remainingWeight - chosen.weight);
            }

            int count = chosen.minCount >= chosen.maxCount
                    ? chosen.minCount
                    : chosen.minCount + rnd.nextInt(chosen.maxCount - chosen.minCount + 1);
            out.add(new Drop(chosen.itemId, Math.max(1, count)));
        }
        return out;
    }

    // ---- display helpers ---------------------------------------------------

    public static String summaryRange(String raw, String anyLabel) {
        String t = raw == null ? "" : raw.trim();
        return t.isEmpty() ? anyLabel : t;
    }

    // ---- Build 440: in-game pool editor normalization & wire codec ---------

    /**
     * Normalizes a user-supplied pool file name into a safe {@code <name>.txt} filename
     * inside {@code config/settledlands/loot_pools/}, returning {@code ""} if invalid.
     */
    public static String normalizeSaveName(String raw) {
        if (raw == null) return "";
        String t = sanitizeFileName(raw);
        if (t.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(t.length() + 4);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else if (Character.isWhitespace(c)) {
                sb.append('_');
            }
        }
        String clean = sb.toString();
        if (clean.isEmpty() || clean.startsWith(".") || clean.contains("..")) return "";
        if (clean.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            clean = clean.substring(0, clean.length() - 4) + ".txt";
        } else if (clean.toLowerCase(Locale.ROOT).endsWith(".disabled")) {
            return "";
        } else {
            clean = clean + ".txt";
        }
        if (clean.length() <= 4 || "README_FORMAT.txt".equalsIgnoreCase(clean)) {
            return "";
        }
        if (clean.length() > 64) {
            String stem = clean.substring(0, 60);
            clean = stem + ".txt";
        }
        return clean;
    }

    public static String suggestNextPoolName(List<String> existing) {
        for (int i = 1; i <= 999; i++) {
            String cand = "loot_pool_" + i + ".txt";
            if (existing == null || !existing.contains(cand)) {
                return cand;
            }
        }
        return "loot_pool_custom.txt";
    }

    public static String cleanItemId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace("|", "").replace("\n", "").replace("\r", "");
        return s.contains(":") ? s : (s.isEmpty() ? "" : "minecraft:" + s);
    }

    public static String formatWeight(double weight) {
        if (!Double.isFinite(weight) || weight <= 0.0D) return "1";
        return formatDecimal(weight);
    }

    public static String formatRarity(double rarity) {
        if (!Double.isFinite(rarity) || rarity <= 0.0D) return "0";
        return formatDecimal(rarity);
    }

    private static String formatDecimal(double val) {
        double rounded = Math.rint(val);
        if (Math.abs(val - rounded) < 1e-6D && rounded >= 0.0D && rounded <= 1_000_000_000.0D) {
            return Long.toString((long) rounded);
        }
        String s = String.format(Locale.ROOT, "%.3f", val);
        int dot = s.indexOf('.');
        if (dot >= 0) {
            int end = s.length();
            while (end > dot + 1 && s.charAt(end - 1) == '0') end--;
            if (end == dot + 1) end = dot;
            s = s.substring(0, end);
        }
        return s;
    }

    public static String formatCountRange(int minCount, int maxCount) {
        int min = Math.max(1, Math.min(64, minCount));
        int max = Math.max(min, Math.min(64, maxCount));
        return min == max ? Integer.toString(min) : (min + "-" + max);
    }

    /**
     * Parses a single item count or {@code min-max} range in {@code [1..64]} from an editor field.
     * Returns {@code null} when empty, incomplete (e.g. {@code "2-"}), or out of bounds.
     */
    public static int[] parseItemCountRange(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int dash = s.indexOf('-');
        if (dash < 0) {
            Integer single = tryParseIntStrict(s);
            if (single == null || single < 1 || single > 64) return null;
            return new int[] {single, single};
        }
        if (s.indexOf('-', dash + 1) >= 0) return null;
        String left = s.substring(0, dash).trim();
        String right = s.substring(dash + 1).trim();
        if (left.isEmpty() || right.isEmpty()) return null;
        Integer a = tryParseIntStrict(left);
        Integer b = tryParseIntStrict(right);
        if (a == null || b == null || a < 1 || b < 1 || a > 64 || b > 64) return null;
        return a <= b ? new int[] {a, b} : new int[] {b, a};
    }

    private static Integer tryParseIntStrict(String s) {
        if (s == null || s.isEmpty()) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(s));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String formatPoolFile(String poolFileName, List<PoolEntry> entries) {
        String safe = normalizeSaveName(poolFileName);
        String headerName = safe.isEmpty() ? "pool.txt" : safe;
        StringBuilder sb = new StringBuilder(256);
        sb.append("# SettledLands Loot Pool: ").append(headerName).append('\n');
        sb.append("# item_id | rarity_chance | weight | count\n");
        if (entries != null) {
            for (PoolEntry e : entries) {
                if (e == null) continue;
                String itemId = cleanItemId(e.itemId);
                if (itemId.isEmpty()
                        || !Double.isFinite(e.rarity) || e.rarity < 0.0D
                        || !Double.isFinite(e.weight) || e.weight <= 0.0D) {
                    continue;
                }
                double rarity = Math.max(0.0D, Math.min((double) MAX_CAP_LIMIT, e.rarity));
                sb.append(itemId)
                        .append(" | ").append(formatRarity(rarity))
                        .append(" | ").append(formatWeight(e.weight))
                        .append(" | ").append(formatCountRange(e.minCount, e.maxCount))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    public static String encodeEntriesWire(List<PoolEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PoolEntry e : entries) {
            if (e == null) continue;
            String id = cleanItemId(e.itemId);
            if (id.isEmpty()
                    || !Double.isFinite(e.rarity) || e.rarity < 0.0D
                    || !Double.isFinite(e.weight) || e.weight <= 0.0D) {
                continue;
            }
            int min = Math.max(1, Math.min(64, e.minCount));
            int max = Math.max(min, Math.min(64, e.maxCount));
            double rar = Math.max(0.0D, Math.min((double) MAX_CAP_LIMIT, e.rarity));
            if (sb.length() > 0) sb.append('\n');
            sb.append(id).append('|')
                    .append(formatRarity(rar)).append('|')
                    .append(formatWeight(e.weight)).append('|')
                    .append(min).append('|')
                    .append(max);
        }
        return sb.toString();
    }

    public static List<PoolEntry> decodeEntriesWire(String[] lines, int startIdx) {
        if (lines == null || startIdx >= lines.length) return Collections.emptyList();
        List<PoolEntry> out = new ArrayList<>();
        for (int i = Math.max(0, startIdx); i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isEmpty()) continue;
            String[] cols = line.split("\\|", -1);
            if (cols.length < 3) continue;
            if (!cols[0].contains(":")) continue;
            String id = cleanItemId(cols[0]);
            if (id.isEmpty() || !id.contains(":")) continue;
            Double rarObj = tryParseDouble(cols[1]);
            Double wgtObj = tryParseDouble(cols[2]);
            if (rarObj == null || wgtObj == null
                    || !Double.isFinite(rarObj) || rarObj < 0.0D
                    || !Double.isFinite(wgtObj) || wgtObj <= 0.0D) {
                continue;
            }
            double rar = Math.max(0.0D, Math.min((double) MAX_CAP_LIMIT, rarObj));
            int min = 1;
            int max = 1;
            if (cols.length >= 4) {
                Integer minObj = tryParseInt(cols[3]);
                if (minObj != null) min = Math.max(1, Math.min(64, minObj));
                max = min;
            }
            if (cols.length >= 5) {
                Integer maxObj = tryParseInt(cols[4]);
                if (maxObj != null) max = Math.max(min, Math.min(64, maxObj));
            }
            out.add(new PoolEntry(id, rar, wgtObj, min, max));
        }
        return out;
    }

    public static Integer parseIntInput(String raw) {
        return tryParseInt(raw);
    }

    public static Double parseWeightInput(String raw) {
        return tryParseDouble(raw);
    }

    public static Double parseRarityInput(String raw) {
        return tryParseDouble(raw);
    }

    /** Returns true when {@code entry} can drop and passes the node's rarity and crate-weight limits. */
    public static boolean passesSpec(PoolEntry entry, Parsed spec) {
        return blockedReason(entry, spec) == 0;
    }

    /**
     * Returns why {@code entry} cannot drop under {@code spec}:
     * {@code 0 = passes}, {@code 1 = exceeds rarity cap}, {@code 2 = exceeds crate weight limit},
     * {@code 3 = invalid rarity/weight}.
     */
    public static int blockedReason(PoolEntry entry, Parsed spec) {
        if (entry == null
                || !Double.isFinite(entry.rarity) || entry.rarity <= 0.0D
                || !Double.isFinite(entry.weight) || entry.weight <= 0.0D) {
            return 3;
        }
        if (spec == null) return 0;
        Range rarityCap = parseRange(spec.rawRarity, MAX_CAP_LIMIT);
        Range weightCap = parseRange(spec.rawWeight, MAX_CAP_LIMIT);
        if (!rarityCap.unbounded && entry.rarity > (double) rarityCap.max + 1e-6D) return 1;
        if (!weightCap.unbounded && entry.weight > (double) weightCap.max + 1e-6D) return 2;
        return 0;
    }

    /** Counts how many non-null entries are blocked by {@code spec}'s rarity or crate-weight caps. */
    public static int countBlockedBySpec(List<PoolEntry> entries, Parsed spec) {
        if (entries == null || entries.isEmpty() || spec == null) return 0;
        int blocked = 0;
        for (PoolEntry e : entries) {
            if (e != null && blockedReason(e, spec) != 0) {
                blocked++;
            }
        }
        return blocked;
    }

    /**
     * Build 442: computes the exact expected first-roll drop percentage {@code [0..100]} for each
     * entry in {@code entries} under {@code spec}'s rarity cap and crate weight budget ranges.
     */
    public static double[] computeEffectiveChancesPct(List<PoolEntry> entries, Parsed spec) {
        if (entries == null || entries.isEmpty()) return new double[0];
        int n = entries.size();
        double[] pct = new double[n];

        Range rRange = spec == null ? Range.unbounded() : parseRange(spec.rawRarity, MAX_CAP_LIMIT);
        Range wRange = spec == null ? Range.unbounded() : parseRange(spec.rawWeight, MAX_CAP_LIMIT);

        if (rRange.unbounded && wRange.unbounded) {
            double totalR = 0.0D;
            for (PoolEntry e : entries) {
                if (e != null && Double.isFinite(e.rarity) && e.rarity > 0.0D
                        && Double.isFinite(e.weight) && e.weight > 0.0D) {
                    totalR += e.rarity;
                }
            }
            if (totalR > 0.0D) {
                for (int i = 0; i < n; i++) {
                    PoolEntry e = entries.get(i);
                    if (e != null && Double.isFinite(e.rarity) && e.rarity > 0.0D
                            && Double.isFinite(e.weight) && e.weight > 0.0D) {
                        pct[i] = (e.rarity / totalR) * 100.0D;
                    }
                }
            }
            return pct;
        }

        int[] wBounds;
        long wSpan;
        if (wRange.unbounded) {
            wBounds = new int[] {Integer.MAX_VALUE};
            wSpan = 1L;
        } else {
            TreeSet<Integer> wCuts = new TreeSet<>();
            wCuts.add(Integer.valueOf(wRange.min));
            for (PoolEntry e : entries) {
                if (e != null && Double.isFinite(e.weight) && e.weight > 0.0D) {
                    int need = Math.max(0, (int) Math.ceil(e.weight - 1e-6D));
                    if (need > wRange.min && need <= wRange.max) {
                        wCuts.add(Integer.valueOf(need));
                    }
                }
            }
            wBounds = new int[wCuts.size() + 1];
            int idx = 0;
            for (Integer c : wCuts) wBounds[idx++] = c.intValue();
            wBounds[idx] = wRange.max + 1;
            wSpan = Math.max(1L, (long) wRange.max - (long) wRange.min + 1L);
        }

        int[] rBounds;
        long rSpan;
        if (rRange.unbounded) {
            rBounds = new int[] {Integer.MAX_VALUE};
            rSpan = 1L;
        } else {
            TreeSet<Integer> rCuts = new TreeSet<>();
            rCuts.add(Integer.valueOf(rRange.min));
            for (PoolEntry e : entries) {
                if (e != null && Double.isFinite(e.rarity) && e.rarity > 0.0D) {
                    int need = Math.max(0, (int) Math.ceil(e.rarity - 1e-6D));
                    if (need > rRange.min && need <= rRange.max) {
                        rCuts.add(Integer.valueOf(need));
                    }
                }
            }
            rBounds = new int[rCuts.size() + 1];
            int idx = 0;
            for (Integer c : rCuts) rBounds[idx++] = c.intValue();
            rBounds[idx] = rRange.max + 1;
            rSpan = Math.max(1L, (long) rRange.max - (long) rRange.min + 1L);
        }

        int wIntervalCount = wRange.unbounded ? 1 : (wBounds.length - 1);
        int rIntervalCount = rRange.unbounded ? 1 : (rBounds.length - 1);

        for (int wi = 0; wi < wIntervalCount; wi++) {
            double wCap = wRange.unbounded ? Double.POSITIVE_INFINITY : (double) wBounds[wi];
            double wProb = wRange.unbounded
                    ? 1.0D
                    : ((double) (wBounds[wi + 1] - wBounds[wi])) / (double) wSpan;
            if (wProb <= 0.0D) continue;

            for (int ri = 0; ri < rIntervalCount; ri++) {
                double rCap = rRange.unbounded ? Double.POSITIVE_INFINITY : (double) rBounds[ri];
                double rProb = rRange.unbounded
                        ? 1.0D
                        : ((double) (rBounds[ri + 1] - rBounds[ri])) / (double) rSpan;
                if (rProb <= 0.0D) continue;

                double pairProb = wProb * rProb;
                double totalR = 0.0D;
                for (int i = 0; i < n; i++) {
                    PoolEntry e = entries.get(i);
                    if (e != null && Double.isFinite(e.rarity) && e.rarity > 0.0D
                            && Double.isFinite(e.weight) && e.weight > 0.0D
                            && e.rarity <= rCap + 1e-6D && e.weight <= wCap + 1e-6D) {
                        totalR += e.rarity;
                    }
                }
                if (totalR <= 0.0D) continue;
                for (int i = 0; i < n; i++) {
                    PoolEntry e = entries.get(i);
                    if (e != null && Double.isFinite(e.rarity) && e.rarity > 0.0D
                            && Double.isFinite(e.weight) && e.weight > 0.0D
                            && e.rarity <= rCap + 1e-6D && e.weight <= wCap + 1e-6D) {
                        pct[i] += pairProb * (e.rarity / totalR) * 100.0D;
                    }
                }
            }
        }
        return pct;
    }
}
