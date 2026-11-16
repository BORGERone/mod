package dev.settledlands.npc;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedList;

/**
 * Build 318: copy and paste of graph nodes, as text on the clipboard.
 *
 * <p>Selecting several nodes and pressing Ctrl+C writes them to the system clipboard as plain
 * text; Ctrl+V in any graph of any NPC reads that text back and creates the nodes. The format is
 * deliberately readable, so a copied branch can be pasted into a chat, a text file or another
 * world, edited by hand, and pasted back:</p>
 *
 * <pre>
 * SLGRAPH318 1
 * node id=5 type=0 x=120 y=40 text="Привет" action="" next=7 decisions=5,9
 * node id=7 type=3 x=320 y=40 text="" varValue=3 falseNext=5
 * </pre>
 *
 * <p>Every value field of the node is written, so a copy keeps the text, the action, the check
 * radius, the animation, the cutscene, the variants and everything else. Links are written as the
 * old ids and are translated on the way back: {@code next}, {@code falseNext},
 * {@code varInputNode}, {@code choices}, {@code randomBranches} and {@code triggers}. A link
 * survives only when both of its ends were in the copied set - the same rule the editor in
 * Unreal Engine uses - so pasting a branch into another graph never points at a node that does not
 * exist there. The entry node is never copied: a graph has one entrance and a second one would
 * make the whole dialogue unreachable.</p>
 *
 * <p>Nothing here touches Minecraft: it is strings, integers and the graph model, which is also
 * what makes the round trip testable outside the game.</p>
 */
public final class NpcGraphClipboard318 {

    /** First line of every copied block. The number is the field layout, not the build. */
    public static final String HEADER = "SLGRAPH318 1";

    /** How far the pasted copy is moved from the original, in graph units. */
    public static final int PASTE_OFFSET = 24;

    /** The canvas moves a node in steps of eight units; a pasted block follows the same grid. */
    public static final double GRID = 8.0D;

    private static String lastError = "";

    private NpcGraphClipboard318() {
    }

    /** Why the last {@link #paste} failed, or an empty string. */
    public static String lastError() {
        return lastError;
    }

    /**
     * Writes the selected nodes as text.
     *
     * @return the block, or an empty string when there is nothing to copy (the entry node alone
     *         does not count).
     */
    public static String copy(NpcDialogueGraph graph, Collection<Integer> ids) {
        lastError = "";
        if (graph == null || ids == null || ids.isEmpty()) return "";
        Set<Integer> wanted = new LinkedHashSet<>(ids);
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        int written = 0;
        for (Node node : graph.nodes.values()) {
            if (node == null || !wanted.contains(node.id)) continue;
            if (node.id == graph.entry) continue;
            writeNode(out, node);
            written++;
        }
        return written == 0 ? "" : out.toString();
    }

    /**
     * How many nodes a block would create, without touching the graph.
     *
     * @return the count, or -1 when the text is not a node block.
     */
    public static int preview(String text) {
        List<Map<String, String>> rows = parseRows(text);
        return rows == null ? -1 : rows.size();
    }

    /**
     * The box the block would occupy at its original coordinates.
     *
     * @return {minX, minY, maxX, maxY}, or null when the text is not a node block.
     */
    public static int[] bounds(String text) {
        List<Map<String, String>> rows = parseRows(text);
        if (rows == null || rows.isEmpty()) return null;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Map<String, String> row : rows) {
            int x = number(row, "x", 0);
            int y = number(row, "y", 0);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        return new int[] { minX, minY, maxX, maxY };
    }

    /**
     * Pastes a block so that the middle of it lands on the given point of the graph.
     *
     * <p>This is what Ctrl+V uses: the block is put where the editor is looking, not where it was
     * copied from, so a copy taken in another graph - or from another world through the clipboard -
     * does not end up off screen behind the current view.</p>
     */
    public static List<Integer> pasteCentered(NpcDialogueGraph graph, String text, double centerX,
            double centerY) {
        lastError = "";
        int[] box = bounds(text);
        if (box == null) {
            if (lastError.isEmpty()) lastError = text == null || text.isBlank()
                    ? "empty" : "not a node block";
            return null;
        }
        // The canvas snaps a dragged node to a grid of eight units; a pasted block is kept on the
        // same grid, or it would sit off it by a few units after the first drag of any node in it.
        int dx = (int) Math.round((centerX - (box[0] + box[2]) / 2.0D) / GRID) * (int) GRID;
        int dy = (int) Math.round((centerY - (box[1] + box[3]) / 2.0D) / GRID) * (int) GRID;
        return paste(graph, text, dx, dy);
    }

    /**
     * Creates the nodes of a copied block.
     *
     * @return the ids of the new nodes, in the order of the block, or null when the block was
     *         not understood or the graph is full; {@link #lastError()} then says why.
     */
    public static List<Integer> paste(NpcDialogueGraph graph, String text) {
        return paste(graph, text, PASTE_OFFSET, PASTE_OFFSET);
    }

    public static List<Integer> paste(NpcDialogueGraph graph, String text, int dx, int dy) {
        lastError = "";
        if (graph == null) {
            lastError = "no graph";
            return null;
        }
        List<Map<String, String>> rows = parseRows(text);
        if (rows == null) return null;
        if (rows.isEmpty()) {
            lastError = "empty";
            return null;
        }
        if (graph.nodes.size() + rows.size() > NpcDialogueGraph.MAX_NODES) {
            lastError = "limit";
            return null;
        }

        List<Integer> added = new ArrayList<>(rows.size());
        Map<Integer, Integer> moved = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            int type = number(row, "type", -1);
            if (type < 0 || type > NpcDialogueGraph.CUTSCENE) {
                lastError = "bad type " + type;
                rollback(graph, added);
                return null;
            }
            int x = clamp(number(row, "x", 0) + dx);
            int y = clamp(number(row, "y", 0) + dy);
            Node node = graph.add(type, x, y);
            if (node == null) {
                lastError = "limit";
                rollback(graph, added);
                return null;
            }
            added.add(node.id);
            moved.put(number(row, "id", -1), node.id);
            applyValues(node, row);
        }

        int index = 0;
        for (Map<String, String> row : rows) {
            Node node = graph.get(added.get(index++));
            if (node == null) continue;
            applyLinks(node, row, moved);
        }
        return added;
    }

    /** Header check and one map per node line, or null with {@link #lastError} set. */
    private static List<Map<String, String>> parseRows(String text) {
        if (text == null || text.isBlank()) {
            lastError = "empty";
            return null;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int start = 0;
        while (start < lines.length && lines[start].isBlank()) start++;
        if (start >= lines.length || !lines[start].trim().equals(HEADER)) {
            lastError = "not a node block";
            return null;
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = start + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            Map<String, String> row = parseLine(line);
            if (row == null || !row.containsKey("type")) {
                lastError = "line " + (i + 1);
                return null;
            }
            rows.add(row);
        }
        return rows;
    }

    private static void rollback(NpcDialogueGraph graph, List<Integer> added) {
        for (int id : added) graph.remove(id);
        added.clear();
    }

    private static int clamp(int value) {
        int max = NpcDialogueGraph.MAX_COORD;
        return Math.max(-max, Math.min(max, value));
    }

    private static void writeNode(StringBuilder out, Node node) {
        out.append("node");
        out.append(" id=").append(node.id);
        out.append(" type=").append(node.type);
        out.append(" x=").append(node.x);
        out.append(" y=").append(node.y);
        text(out, "text", node.text);
        text(out, "action", node.action);
        num(out, "special", node.special);
        num(out, "builtinTopic", node.builtinTopic);
        num(out, "originTopic", node.originTopic);
        num(out, "originStep", node.originStep);
        num(out, "originAnswer", node.originAnswer);
        num(out, "relation", node.relation);
        num(out, "minRelation", node.minRelation);
        num(out, "maxRelation", node.maxRelation);
        text(out, "pointsName", node.pointsName);
        num(out, "minPoints", node.minPoints);
        num(out, "maxPoints", node.maxPoints);
        flag(out, "pointsShared", node.pointsShared);
        flag(out, "varLocal", node.varLocal);
        num(out, "varValue", node.varValue);
        num(out, "varCompare", node.varCompare);
        num(out, "varOp", node.varOp);
        num(out, "checkTarget", node.checkTarget);
        num(out, "checkRadius", node.checkRadius);
        num(out, "checkCooldown", node.checkCooldown);
        num(out, "damageFilter", node.damageFilter);
        num(out, "damageCooldown", node.damageCooldown);
        num(out, "minDamage", node.minDamage);
        num(out, "timeMode", node.timeMode);
        num(out, "timeTicks", node.timeTicks);
        num(out, "timerInterval", node.timerInterval);
        flag(out, "timerRepeat", node.timerRepeat);
        num(out, "radius", node.radius);
        num(out, "radiusTarget", node.radiusTarget);
        text(out, "moveCoords", node.moveCoords);
        out.append(" moveSpeed=").append(String.format(Locale.ROOT, "%.3f", node.moveSpeed));
        flag(out, "moveEscort", node.moveEscort);
        text(out, "mobilityGroupId", node.mobilityGroupId);
        text(out, "mobilityGroupName", node.mobilityGroupName);
        text(out, "animName", node.animName);
        text(out, "animBone", node.animBone);
        flag(out, "animLoop", node.animLoop);
        flag(out, "cutsceneLine", node.cutsceneLine);
        text(out, "cutsceneName", node.cutsceneName);
        num(out, "randomMin", node.randomMin);
        num(out, "randomMax", node.randomMax);
        num(out, "next", node.next);
        num(out, "falseNext", node.falseNext);
        num(out, "varInputNode", node.varInputNode);
        ints(out, "choices", node.choices);
        ints(out, "randomBranches", node.randomBranches);
        ints(out, "triggers", node.triggers);
        if (node.variants != null) {
            int i = 0;
            for (NpcProfile.Variant variant : node.variants) {
                if (variant == null) continue;
                out.append(" variant").append(i).append('=')
                        .append(quote(variant.minRelation + ":" + safe(variant.text)));
                i++;
            }
        }
        out.append('\n');
    }

    private static void applyValues(Node node, Map<String, String> row) {
        node.text = string(row, "text", node.text);
        node.action = string(row, "action", node.action);
        node.special = number(row, "special", node.special);
        node.builtinTopic = number(row, "builtinTopic", node.builtinTopic);
        node.originTopic = number(row, "originTopic", node.originTopic);
        node.originStep = number(row, "originStep", node.originStep);
        node.originAnswer = number(row, "originAnswer", node.originAnswer);
        node.relation = number(row, "relation", node.relation);
        node.minRelation = number(row, "minRelation", node.minRelation);
        node.maxRelation = number(row, "maxRelation", node.maxRelation);
        node.pointsName = string(row, "pointsName", node.pointsName);
        node.minPoints = number(row, "minPoints", node.minPoints);
        node.maxPoints = number(row, "maxPoints", node.maxPoints);
        node.pointsShared = flag(row, "pointsShared", node.pointsShared);
        node.varLocal = flag(row, "varLocal", node.varLocal);
        node.varValue = number(row, "varValue", node.varValue);
        node.varCompare = number(row, "varCompare", node.varCompare);
        node.varOp = number(row, "varOp", node.varOp);
        node.checkTarget = number(row, "checkTarget", node.checkTarget);
        node.checkRadius = number(row, "checkRadius", node.checkRadius);
        node.checkCooldown = number(row, "checkCooldown", node.checkCooldown);
        node.damageFilter = number(row, "damageFilter", node.damageFilter);
        node.damageCooldown = number(row, "damageCooldown", node.damageCooldown);
        node.minDamage = number(row, "minDamage", node.minDamage);
        node.timeMode = number(row, "timeMode", node.timeMode);
        node.timeTicks = number(row, "timeTicks", node.timeTicks);
        node.timerInterval = number(row, "timerInterval", node.timerInterval);
        node.timerRepeat = flag(row, "timerRepeat", node.timerRepeat);
        node.radius = number(row, "radius", node.radius);
        node.radiusTarget = number(row, "radiusTarget", node.radiusTarget);
        node.moveCoords = string(row, "moveCoords", node.moveCoords);
        node.moveSpeed = decimal(row, "moveSpeed", node.moveSpeed);
        node.moveEscort = flag(row, "moveEscort", node.moveEscort);
        node.mobilityGroupId = string(row, "mobilityGroupId", node.mobilityGroupId);
        node.mobilityGroupName = string(row, "mobilityGroupName", node.mobilityGroupName);
        node.animName = string(row, "animName", node.animName);
        node.animBone = string(row, "animBone", node.animBone);
        node.animLoop = flag(row, "animLoop", node.animLoop);
        node.cutsceneLine = flag(row, "cutsceneLine", node.cutsceneLine);
        node.cutsceneName = string(row, "cutsceneName", node.cutsceneName);
        node.randomMin = number(row, "randomMin", node.randomMin);
        node.randomMax = number(row, "randomMax", node.randomMax);
        if (row.containsKey("variant0")) {
            List<NpcProfile.Variant> variants = new LinkedList<>();
            for (int i = 0; row.containsKey("variant" + i); i++) {
                String value = row.get("variant" + i);
                int split = value.indexOf(':');
                int min = split < 0 ? 0 : (int) parseLong(value.substring(0, split), 0);
                String text = split < 0 ? value : value.substring(split + 1);
                variants.add(new NpcProfile.Variant(min, text));
            }
            node.variants.clear();
            node.variants.addAll(variants);
        }
    }

    private static void applyLinks(Node node, Map<String, String> row, Map<Integer, Integer> moved) {
        node.next = link(row, "next", moved, -1);
        node.falseNext = link(row, "falseNext", moved, -1);
        node.varInputNode = link(row, "varInputNode", moved, -1);
        // The three link lists are final on the node, so they are refilled in place.
        refill(row, "choices", moved, node.choices);
        refill(row, "randomBranches", moved, node.randomBranches);
        refill(row, "triggers", moved, node.triggers);
    }

    private static int link(Map<String, String> row, String key, Map<Integer, Integer> moved,
            int fallback) {
        String raw = row.get(key);
        if (raw == null) return fallback;
        int old = (int) parseLong(raw, -1);
        Integer now = moved.get(old);
        return now == null ? -1 : now;
    }

    private static void refill(Map<String, String> row, String key, Map<Integer, Integer> moved,
            List<Integer> target) {
        String raw = row.get(key);
        if (raw == null) return;
        List<Integer> now = links(raw, moved);
        target.clear();
        target.addAll(now);
    }

    private static List<Integer> links(String raw, Map<Integer, Integer> moved) {
        List<Integer> out = new LinkedList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.equals("-")) continue;
            Integer now = moved.get((int) parseLong(trimmed, -1));
            if (now != null) out.add(now);
        }
        return out;
    }

    // -- text ----------------------------------------------------------------

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void text(StringBuilder out, String key, String value) {
        if (value == null || value.isEmpty()) return;
        out.append(' ').append(key).append('=').append(quote(value));
    }

    private static void num(StringBuilder out, String key, int value) {
        out.append(' ').append(key).append('=').append(value);
    }

    private static void flag(StringBuilder out, String key, boolean value) {
        out.append(' ').append(key).append('=').append(value ? '1' : '0');
    }

    private static void ints(StringBuilder out, String key, List<Integer> values) {
        out.append(' ').append(key).append('=');
        if (values == null || values.isEmpty()) {
            out.append('-');
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(values.get(i));
        }
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static Map<String, String> parseLine(String line) {
        Map<String, String> row = new LinkedHashMap<>();
        int i = 0;
        int length = line.length();
        if (!line.startsWith("node")) return null;
        i = 4;
        while (i < length) {
            while (i < length && line.charAt(i) == ' ') i++;
            int start = i;
            while (i < length && line.charAt(i) != '=' && line.charAt(i) != ' ') i++;
            if (i >= length || line.charAt(i) != '=') return null;
            String key = line.substring(start, i);
            i++;
            String value;
            if (i < length && line.charAt(i) == '"') {
                StringBuilder out = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < length) {
                    char c = line.charAt(i++);
                    if (c == '\\' && i < length) {
                        char next = line.charAt(i++);
                        switch (next) {
                            case 'n' -> out.append('\n');
                            case 'r' -> out.append('\r');
                            case 't' -> out.append('\t');
                            default -> out.append(next);
                        }
                    } else if (c == '"') {
                        closed = true;
                        break;
                    } else {
                        out.append(c);
                    }
                }
                if (!closed) return null;
                value = out.toString();
            } else {
                int from = i;
                while (i < length && line.charAt(i) != ' ') i++;
                value = line.substring(from, i);
            }
            row.put(key, value);
        }
        return row;
    }

    private static int number(Map<String, String> row, String key, int fallback) {
        String raw = row.get(key);
        return raw == null ? fallback : (int) parseLong(raw, fallback);
    }

    private static boolean flag(Map<String, String> row, String key, boolean fallback) {
        String raw = row.get(key);
        if (raw == null) return fallback;
        return raw.equals("1") || raw.equalsIgnoreCase("true");
    }

    private static String string(Map<String, String> row, String key, String fallback) {
        String raw = row.get(key);
        return raw == null ? fallback : raw;
    }

    private static double decimal(Map<String, String> row, String key, double fallback) {
        String raw = row.get(key);
        if (raw == null) return fallback;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
