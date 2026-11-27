package dev.settledlands.trial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Build 449: pure rule, parser, serialization and weighted-wave test roller for named
 * Trial Spawner Mob Pools ({@code config/settledlands/mob_pools/*.txt}).
 *
 * <p>Keeps zero client/render dependencies so both server logic and headless JVM probes
 * can exercise all parsing and rolling rules cleanly.</p>
 */
public final class TrialMobPool449 {

    public static final int ACCENT_COLOR = TrialModule447.color(TrialModule447.SLOT_MOBS);
    public static final int MAX_ENTRIES = TrialConfig446.MAX_ENTRIES;
    public static final int MAX_WEIGHT = 100_000;

    private static volatile List<String> serverPools = Collections.emptyList();
    private static volatile Runnable clientPoolsListener;
    private static volatile Consumer<String> clientPoolDataListener;

    private TrialMobPool449() {}

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

    public static final class WavePick {
        public final String entityId;
        public final int count;

        public WavePick(String entityId, int count) {
            this.entityId = entityId;
            this.count = count;
        }
    }

    // ---- filename normalization --------------------------------------------

    public static String sanitizeFileName(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replace('\\', '/');
        if (t.contains("..")) return "";
        int slash = t.lastIndexOf('/');
        if (slash >= 0) t = t.substring(slash + 1);
        t = t.replace("|", "").replace(";", "");
        if (".".equals(t)) return "";
        return t;
    }

    /** Normalizes a pool filename for saving as {@code <safe>.txt}; returns "" if invalid. */
    public static String normalizeSaveName(String raw) {
        String base = sanitizeFileName(raw);
        if (base.isEmpty()) return "";
        if (base.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            base = base.substring(0, base.length() - 4).trim();
        }
        if (base.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        String clean = sb.toString();
        while (clean.startsWith(".")) clean = clean.substring(1);
        if (clean.isEmpty()) return "";
        if (clean.length() > 48) clean = clean.substring(0, 48);
        return clean + ".txt";
    }

    public static String suggestNextPoolName(List<String> existing) {
        TreeSet<String> lower = new TreeSet<>();
        if (existing != null) {
            for (String s : existing) {
                if (s != null) lower.add(s.toLowerCase(Locale.ROOT));
            }
        }
        for (int n = 1; n <= 999; n++) {
            String candidate = "mob_pool_" + n + ".txt";
            if (!lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return "mob_pool_custom.txt";
    }

    // ---- file text & wire encoding -----------------------------------------

    public static List<TrialConfig446.SpawnEntry> parseLines(List<String> lines) {
        List<TrialConfig446.SpawnEntry> out = new ArrayList<>();
        if (lines == null) return out;
        for (int i = 0; i < lines.size() && out.size() < MAX_ENTRIES; i++) {
            String line = lines.get(i);
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
            int hash = trimmed.indexOf(" #");
            if (hash > 0) trimmed = trimmed.substring(0, hash).trim();

            String idPart;
            String weightPart = null;
            int pipe = trimmed.indexOf('|');
            int star = trimmed.indexOf('*');
            if (pipe >= 0) {
                idPart = trimmed.substring(0, pipe).trim();
                weightPart = trimmed.substring(pipe + 1).trim();
            } else if (star >= 0) {
                idPart = trimmed.substring(0, star).trim();
                weightPart = trimmed.substring(star + 1).trim();
            } else {
                idPart = trimmed;
            }
            if (idPart.isEmpty() || idPart.indexOf(' ') >= 0) continue;
            String normId = TrialConfig446.normalizeId(idPart);
            if (normId == null || !normId.contains(":")) continue;
            int w = 1;
            String wr = "";
            if (weightPart != null && !weightPart.isEmpty()) {
                RangeSpec450.IntRange rr = parseWeightRangeInput(weightPart);
                if (rr == null || rr.max <= 0) continue;
                w = rr.min;
                if (rr.isRange()) wr = rr.format();
            }
            out.add(new TrialConfig446.SpawnEntry(normId, w, wr));
        }
        return out;
    }

    public static String formatPoolFile(String fileName, List<TrialConfig446.SpawnEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SettledLands Mob Pool: ").append(fileName == null ? "" : fileName).append('\n');
        sb.append("# Format: entity_id | weight_or_range\n");
        if (entries != null) {
            int count = 0;
            for (TrialConfig446.SpawnEntry e : entries) {
                if (e == null || count >= MAX_ENTRIES) continue;
                String id = TrialConfig446.normalizeId(e.entityId);
                if (id == null || !id.contains(":")) continue;
                sb.append(id).append(" | ").append(formatWeightToken(e)).append('\n');
                count++;
            }
        }
        return sb.toString();
    }

    public static String encodeEntriesWire(List<TrialConfig446.SpawnEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (TrialConfig446.SpawnEntry e : entries) {
            if (e == null || count >= MAX_ENTRIES) continue;
            String id = TrialConfig446.normalizeId(e.entityId);
            if (id == null || !id.contains(":")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(id).append('|').append(formatWeightToken(e));
            count++;
        }
        return sb.toString();
    }

    public static List<TrialConfig446.SpawnEntry> decodeEntriesWire(String[] lines, int startIdx) {
        List<TrialConfig446.SpawnEntry> out = new ArrayList<>();
        if (lines == null) return out;
        for (int i = Math.max(0, startIdx); i < lines.length && out.size() < MAX_ENTRIES; i++) {
            String row = lines[i];
            if (row == null || row.isBlank()) continue;
            String[] p = row.split("\\|", -1);
            String id = p.length >= 1 ? TrialConfig446.normalizeId(p[0]) : null;
            if (id == null || !id.contains(":")) continue;
            RangeSpec450.IntRange rr = p.length >= 2 ? parseWeightRangeInput(p[1]) : null;
            if (rr != null) {
                out.add(new TrialConfig446.SpawnEntry(id, rr.min, rr.isRange() ? rr.format() : ""));
            } else {
                out.add(new TrialConfig446.SpawnEntry(id, parseWeight(p.length >= 2 ? p[1] : "", 1)));
            }
        }
        return out;
    }

    // ---- odds & test-wave preview ------------------------------------------

    public static double[] computeEffectiveChancesPct(List<TrialConfig446.SpawnEntry> entries) {
        if (entries == null || entries.isEmpty()) return new double[0];
        double[] out = new double[entries.size()];
        double[] eff = new double[entries.size()];
        double total = 0.0D;
        for (int i = 0; i < entries.size(); i++) {
            TrialConfig446.SpawnEntry e = entries.get(i);
            if (e == null || e.weight <= 0) continue;
            double w = e.weight;
            if (e.weightRange != null && !e.weightRange.isBlank()) {
                RangeSpec450.IntRange rr = parseWeightRangeInput(e.weightRange);
                if (rr != null) w = (rr.min + rr.max) * 0.5D;
            }
            eff[i] = w;
            total += w;
        }
        if (total <= 0.0D) return out;
        for (int i = 0; i < entries.size(); i++) {
            if (eff[i] > 0.0D) {
                out[i] = (eff[i] / total) * 100.0D;
            }
        }
        return out;
    }

    public static List<WavePick> rollWavePreview(List<TrialConfig446.SpawnEntry> entries,
            int totalToSpawn, Random rnd) {
        if (entries == null || entries.isEmpty() || totalToSpawn <= 0 || rnd == null) {
            return Collections.emptyList();
        }
        List<TrialConfig446.SpawnEntry> valid = new ArrayList<>(entries.size());
        long totalW = 0L;
        for (TrialConfig446.SpawnEntry e : entries) {
            if (e == null) continue;
            String id = TrialConfig446.normalizeId(e.entityId);
            if (id == null) continue;
            int w = clampWeight(e.weight);
            if (e.weightRange != null && !e.weightRange.isBlank()) {
                RangeSpec450.IntRange rr = parseWeightRangeInput(e.weightRange);
                if (rr != null) w = rr.pick(rnd);
            }
            valid.add(new TrialConfig446.SpawnEntry(id, w));
            totalW += w;
        }
        if (valid.isEmpty() || totalW <= 0L) return Collections.emptyList();

        Map<String, Integer> counts = new LinkedHashMap<>();
        int rolls = Math.max(1, Math.min(128, totalToSpawn));
        for (int r = 0; r < rolls; r++) {
            long pick = Math.abs(rnd.nextLong()) % totalW;
            long acc = 0L;
            String chosen = valid.get(0).entityId;
            for (TrialConfig446.SpawnEntry e : valid) {
                acc += e.weight;
                if (pick < acc) {
                    chosen = e.entityId;
                    break;
                }
            }
            counts.merge(chosen, 1, Integer::sum);
        }
        List<WavePick> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> en : counts.entrySet()) {
            out.add(new WavePick(en.getKey(), en.getValue()));
        }
        return out;
    }

    private static String formatWeightToken(TrialConfig446.SpawnEntry e) {
        if (e.weightRange != null && !e.weightRange.isBlank()) {
            RangeSpec450.IntRange rr = parseWeightRangeInput(e.weightRange);
            if (rr != null && rr.isRange()) return rr.format();
        }
        return Integer.toString(clampWeight(e.weight));
    }

    public static RangeSpec450.IntRange parseWeightRangeInput(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (!RangeSpec450.hasRangeSeparator(raw)) {
            Integer single = parseWeightInput(raw);
            return single == null ? null : new RangeSpec450.IntRange(single, single);
        }
        RangeSpec450.IntRange rawUnclamped = RangeSpec450.tryParseInt(raw, 0, MAX_WEIGHT);
        if (rawUnclamped == null || rawUnclamped.max <= 0) return null;
        return new RangeSpec450.IntRange(
                clampWeight(rawUnclamped.min), clampWeight(rawUnclamped.max));
    }

    public static Integer parseWeightInput(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < 1 || v > MAX_WEIGHT) return null;
            return v;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static int parseWeight(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return clampWeight(fallback);
        try {
            return clampWeight(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return clampWeight(fallback);
        }
    }

    public static int clampWeight(int w) {
        return Math.max(1, Math.min(MAX_WEIGHT, w));
    }
}
