package dev.settledlands.chest;

import dev.settledlands.npc.NpcDialogueGraph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
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
        public final int rarity;
        public final double weight;
        public final int minCount;
        public final int maxCount;

        public PoolEntry(String itemId, int rarity, double weight, int minCount, int maxCount) {
            this.itemId = itemId;
            this.rarity = rarity;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
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
     * Format per line: {@code item_id | rarity | weight | [count_or_range]}.
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
            Integer rarityObj = tryParseInt(cols[1]);
            Double weightObj = tryParseDouble(cols[2]);
            if (rarityObj == null || weightObj == null) continue;
            int rarity = Math.max(0, rarityObj);
            double weight = weightObj;
            if (weight <= 0.0D) continue;

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

    public static List<Drop> rollDrops(List<PoolEntry> entries, Parsed spec, Random rnd) {
        if (entries == null || entries.isEmpty() || spec == null || rnd == null) {
            return Collections.emptyList();
        }
        Range rarityCapRange = parseRange(spec.rawRarity, MAX_CAP_LIMIT);
        Range weightCapRange = parseRange(spec.rawWeight, MAX_CAP_LIMIT);
        Range rollsRange = parseRange(spec.rawRolls, MAX_ROLLS_LIMIT);
        int rolls = rollsRange.unbounded ? 3 : rollsRange.pick(rnd);
        if (rolls <= 0) return Collections.emptyList();

        List<Drop> out = new ArrayList<>(rolls);
        List<PoolEntry> eligible = new ArrayList<>(entries.size());

        for (int r = 0; r < rolls; r++) {
            int maxRarity = rarityCapRange.pick(rnd);
            double maxWeight = weightCapRange.unbounded
                    ? Double.POSITIVE_INFINITY
                    : (double) weightCapRange.pick(rnd);

            eligible.clear();
            double totalWeight = 0.0D;
            for (PoolEntry e : entries) {
                if (e.rarity <= maxRarity && e.weight <= maxWeight && e.weight > 0.0D) {
                    eligible.add(e);
                    totalWeight += e.weight;
                }
            }
            if (eligible.isEmpty() || totalWeight <= 0.0D) continue;

            double pick = rnd.nextDouble() * totalWeight;
            double acc = 0.0D;
            PoolEntry chosen = eligible.get(eligible.size() - 1);
            for (PoolEntry e : eligible) {
                acc += e.weight;
                if (acc >= pick) {
                    chosen = e;
                    break;
                }
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
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
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
        double rounded = Math.rint(weight);
        if (Math.abs(weight - rounded) < 1e-6D && rounded >= 1.0D && rounded <= 1_000_000_000.0D) {
            return Long.toString((long) rounded);
        }
        String s = String.format(Locale.ROOT, "%.3f", weight);
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

    public static String formatPoolFile(String poolFileName, List<PoolEntry> entries) {
        String safe = normalizeSaveName(poolFileName);
        String headerName = safe.isEmpty() ? "pool.txt" : safe;
        StringBuilder sb = new StringBuilder(256);
        sb.append("# SettledLands Loot Pool: ").append(headerName).append('\n');
        sb.append("# item_id | rarity | weight | count\n");
        if (entries != null) {
            for (PoolEntry e : entries) {
                if (e == null) continue;
                String itemId = cleanItemId(e.itemId);
                if (itemId.isEmpty() || !Double.isFinite(e.weight) || e.weight <= 0.0D) continue;
                int rarity = Math.max(0, Math.min(MAX_CAP_LIMIT, e.rarity));
                sb.append(itemId)
                        .append(" | ").append(rarity)
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
            if (id.isEmpty() || !Double.isFinite(e.weight) || e.weight <= 0.0D) continue;
            int min = Math.max(1, Math.min(64, e.minCount));
            int max = Math.max(min, Math.min(64, e.maxCount));
            int rar = Math.max(0, Math.min(MAX_CAP_LIMIT, e.rarity));
            if (sb.length() > 0) sb.append('\n');
            sb.append(id).append('|')
                    .append(rar).append('|')
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
            Integer rarObj = tryParseInt(cols[1]);
            Double wgtObj = tryParseDouble(cols[2]);
            if (rarObj == null || wgtObj == null || !Double.isFinite(wgtObj) || wgtObj <= 0.0D) continue;
            int rar = Math.max(0, Math.min(MAX_CAP_LIMIT, rarObj));
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

    /** Returns true when {@code entry} passes the node's rarity and weight ceiling filters. */
    public static boolean passesSpec(PoolEntry entry, Parsed spec) {
        if (entry == null || entry.weight <= 0.0D || !Double.isFinite(entry.weight)) return false;
        if (spec == null) return true;
        Range rarityCap = parseRange(spec.rawRarity, MAX_CAP_LIMIT);
        Range weightCap = parseRange(spec.rawWeight, MAX_CAP_LIMIT);
        if (!rarityCap.unbounded && entry.rarity > rarityCap.max) return false;
        if (!weightCap.unbounded && entry.weight > (double) weightCap.max) return false;
        return true;
    }
}
