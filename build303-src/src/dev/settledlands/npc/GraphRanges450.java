package dev.settledlands.npc;

import dev.settledlands.trial.RangeSpec450;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Build 450: optional integer range specs ({@code "a-b"}) on stock {@link NpcDialogueGraph.Node}
 * numeric inputs.
 *
 * <p>For nodes whose {@code text} field is unused by gameplay ({@code IF}, {@code VARIABLE},
 * {@code CHECK}, {@code DAMAGE}, {@code TIME}, {@code TIMER}, {@code RADIUS}), ranges are stored
 * compactly inside {@code node.text} as {@code "rs450:k1=a-b;k2=c-d"} and cleared back to {@code ""}
 * when every input returns to a single scalar, preserving full wire/NBT compatibility and
 * zero extra fields on {@code NpcDialogueGraph.Node}.</p>
 */
public final class GraphRanges450 {

    public static final String PREFIX = "rs450:";

    public static final String KEY_IF_COMPARE = "cmp";
    public static final String KEY_VAR_VALUE = "val";
    public static final String KEY_CHECK_RADIUS = "rad";
    public static final String KEY_CHECK_COOLDOWN = "cd";
    public static final String KEY_MIN_DAMAGE = "dmg";
    public static final String KEY_DAMAGE_COOLDOWN = "cd";
    public static final String KEY_TIME_TICKS = "t";
    public static final String KEY_TIMER_INTERVAL = "iv";
    public static final String KEY_RADIUS = "r";

    private GraphRanges450() {}

    public static boolean supportsTextRanges(NpcDialogueGraph.Node node) {
        if (node == null) return false;
        int t = node.type;
        return t == NpcDialogueGraph.IF
                || t == NpcDialogueGraph.VARIABLE
                || t == NpcDialogueGraph.CHECK
                || t == NpcDialogueGraph.DAMAGE
                || t == NpcDialogueGraph.TIME
                || t == NpcDialogueGraph.TIMER
                || t == NpcDialogueGraph.RADIUS;
    }

    public static boolean isRangeMarker(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    public static Map<String, RangeSpec450.IntRange> readMap(NpcDialogueGraph.Node node) {
        Map<String, RangeSpec450.IntRange> map = new LinkedHashMap<>();
        if (node == null || !supportsTextRanges(node) || !isRangeMarker(node.text)) {
            return map;
        }
        String body = node.text.substring(PREFIX.length());
        String[] parts = body.split(";");
        for (String p : parts) {
            int eq = p.indexOf('=');
            if (eq <= 0 || eq >= p.length() - 1) continue;
            String k = p.substring(0, eq).trim();
            String v = p.substring(eq + 1).trim();
            if (k.isEmpty() || v.isEmpty()) continue;
            RangeSpec450.IntRange r = RangeSpec450.tryParseInt(v, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (r != null && r.isRange()) {
                map.put(k, r);
            }
        }
        return map;
    }

    public static void writeMap(NpcDialogueGraph.Node node, Map<String, RangeSpec450.IntRange> map) {
        if (node == null || !supportsTextRanges(node)) return;
        if (map == null || map.isEmpty()) {
            if (isRangeMarker(node.text) || node.text == null) {
                node.text = "";
            }
            return;
        }
        StringBuilder sb = new StringBuilder(PREFIX);
        boolean first = true;
        for (Map.Entry<String, RangeSpec450.IntRange> e : map.entrySet()) {
            RangeSpec450.IntRange r = e.getValue();
            if (r == null || !r.isRange()) continue;
            if (!first) sb.append(';');
            first = false;
            sb.append(e.getKey()).append('=').append(r.format());
        }
        node.text = first ? "" : sb.toString();
    }

    public static boolean hasRange(NpcDialogueGraph.Node node, String key) {
        return readMap(node).containsKey(key);
    }

    public static boolean inRange(NpcDialogueGraph.Node node, String key, int value) {
        RangeSpec450.IntRange r = readMap(node).get(key);
        if (r == null) return false;
        return r.contains(value);
    }

    public static String getRangeText(NpcDialogueGraph.Node node, String key, int scalar) {
        RangeSpec450.IntRange r = readMap(node).get(key);
        if (r != null && r.isRange()) {
            return r.format();
        }
        return Integer.toString(scalar);
    }

    public static int setRangeText(NpcDialogueGraph.Node node, String key, String raw,
            int fallback, int minBound, int maxBound) {
        RangeSpec450.IntRange parsed = RangeSpec450.parseInt(raw, fallback, fallback, minBound, maxBound);
        Map<String, RangeSpec450.IntRange> map = readMap(node);
        if (parsed.isRange()) {
            map.put(key, parsed);
        } else {
            map.remove(key);
        }
        writeMap(node, map);
        return parsed.min;
    }

    public static boolean rerollNode(NpcDialogueGraph.Node node, Random rnd) {
        if (node == null || rnd == null || !supportsTextRanges(node)) return false;
        Map<String, RangeSpec450.IntRange> map = readMap(node);
        if (map.isEmpty()) return false;
        boolean changed = false;
        switch (node.type) {
            case NpcDialogueGraph.IF -> {
                RangeSpec450.IntRange r = map.get(KEY_IF_COMPARE);
                if (r != null && node.varOp != 0 && node.varOp != 1) {
                    node.varCompare = r.pick(rnd);
                    changed = true;
                }
            }
            case NpcDialogueGraph.VARIABLE -> {
                RangeSpec450.IntRange r = map.get(KEY_VAR_VALUE);
                if (r != null) {
                    node.varValue = r.pick(rnd);
                    changed = true;
                }
            }
            case NpcDialogueGraph.CHECK -> {
                RangeSpec450.IntRange r1 = map.get(KEY_CHECK_RADIUS);
                if (r1 != null) {
                    node.checkRadius = Math.max(1, Math.min(64, r1.pick(rnd)));
                    changed = true;
                }
                RangeSpec450.IntRange r2 = map.get(KEY_CHECK_COOLDOWN);
                if (r2 != null) {
                    node.checkCooldown = Math.max(0, Math.min(3600, r2.pick(rnd)));
                    changed = true;
                }
            }
            case NpcDialogueGraph.DAMAGE -> {
                RangeSpec450.IntRange r1 = map.get(KEY_MIN_DAMAGE);
                if (r1 != null) {
                    node.minDamage = Math.max(0, Math.min(1000, r1.pick(rnd)));
                    changed = true;
                }
                RangeSpec450.IntRange r2 = map.get(KEY_DAMAGE_COOLDOWN);
                if (r2 != null) {
                    node.damageCooldown = Math.max(0, Math.min(3600, r2.pick(rnd)));
                    changed = true;
                }
            }
            case NpcDialogueGraph.TIME -> {
                RangeSpec450.IntRange r = map.get(KEY_TIME_TICKS);
                if (r != null) {
                    node.timeTicks = Math.max(0, Math.min(23999, r.pick(rnd)));
                    changed = true;
                }
            }
            case NpcDialogueGraph.TIMER -> {
                RangeSpec450.IntRange r = map.get(KEY_TIMER_INTERVAL);
                if (r != null) {
                    node.timerInterval = Math.max(1, Math.min(86400, r.pick(rnd)));
                    changed = true;
                }
            }
            case NpcDialogueGraph.RADIUS -> {
                RangeSpec450.IntRange r = map.get(KEY_RADIUS);
                if (r != null) {
                    node.radius = Math.max(1, Math.min(64, r.pick(rnd)));
                    changed = true;
                }
            }
            default -> {
            }
        }
        return changed;
    }

    public static void rerollGraph(NpcDialogueGraph graph, Random rnd) {
        if (graph == null || graph.nodes == null || rnd == null) return;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            rerollNode(node, rnd);
        }
    }

    public static void rerollGraphOfType(NpcDialogueGraph graph, Random rnd, int nodeType) {
        if (graph == null || graph.nodes == null || rnd == null) return;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && node.type == nodeType) {
                rerollNode(node, rnd);
            }
        }
    }
}
