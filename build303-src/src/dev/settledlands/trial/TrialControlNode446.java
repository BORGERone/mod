package dev.settledlands.trial;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import java.util.Locale;

/**
 * Build 446: pure rule, parser and formatter for the cross-graph «Спавнер» control node.
 *
 * <p>Stored inside the stock graph as {@link NpcDialogueGraph#TRIGGER} ({@code 2}, {@code special == 0})
 * with {@code action} carrying
 * {@code "trigger:spawner:<op>|<dim>|<rx>|<ry>|<rz>|<flags>|<cfgJson>"}.
 * Works cleanly across all six graph editors (NPC, common, block, door, chest, trial spawner)
 * with absolute coordinates ({@code 120}) or relative tilde offsets ({@code ~}, {@code ~5}, {@code ~-3}).</p>
 */
public final class TrialControlNode446 {

    public static final String ACTION_PREFIX = "trigger:spawner:";
    public static final String STRIPPED_PREFIX = "spawner:";

    public static final String OP_CONFIG = "CONFIG";
    public static final String OP_START = "START";
    public static final String OP_CONFIG_START = "CONFIG_START";
    public static final String OP_STOP = "STOP";
    public static final String OP_RESET_CD = "RESET_CD";
    public static final String OP_DESTROY = "DESTROY";

    public static final String[] OPS = {
        OP_START,
        OP_CONFIG,
        OP_CONFIG_START,
        OP_STOP,
        OP_RESET_CD,
        OP_DESTROY,
    };

    public static final String DEFAULT_DIMENSION = ""; // blank = current dimension of the running graph
    public static final int MAX_COORD_LEN = 24;
    public static final int MAX_ACTION_LEN = 24000;

    /** Row index in the NPC/common context menu (stock 0..15, Pursuit 16, Teleport 17, Spawner 18). */
    public static final int SPAWNER_ROW = 18;
    public static final int NPC_MENU_ITEMS = 19;
    public static final int NPC_VISIBLE_ROWS = 5;
    public static final int NPC_MAX_SCROLL = NPC_MENU_ITEMS - NPC_VISIBLE_ROWS;

    public static final int ACCENT_COLOR = -14108774; // 0xFF28B79A bright emerald-copper
    public static final String MENU_LABEL_KEY = "gui.settledlands.trial_ctl.menu";
    public static final String TITLE_KEY = "gui.settledlands.trial_ctl.title";

    public static final String DEFAULT_ACTION =
            ACTION_PREFIX + OP_START + "|" + DEFAULT_DIMENSION + "||||0|";

    public static final class Parsed {
        public final String op;
        public final String dimension;
        public final String rawX;
        public final String rawY;
        public final String rawZ;
        public final boolean killMobs;
        public final String configJson;

        Parsed(String op, String dimension, String rawX, String rawY, String rawZ,
                boolean killMobs, String configJson) {
            this.op = op;
            this.dimension = dimension;
            this.rawX = rawX;
            this.rawY = rawY;
            this.rawZ = rawZ;
            this.killMobs = killMobs;
            this.configJson = configJson;
        }

        public TrialConfig446 config() {
            if (configJson == null || configJson.isBlank()) return TrialConfig446.defaults();
            return TrialConfig446.fromJson(configJson);
        }

        public int resolveX(int originX) {
            return resolveAxis(rawX, originX);
        }

        public int resolveY(int originY) {
            return resolveAxis(rawY, originY);
        }

        public int resolveZ(int originZ) {
            return resolveAxis(rawZ, originZ);
        }
    }

    private TrialControlNode446() {}

    public static boolean isControlAction(String action) {
        if (action == null) return false;
        String lower = action.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith(ACTION_PREFIX) || lower.equals("trigger:spawner");
    }

    public static boolean isStrippedControlAction(String body) {
        if (body == null) return false;
        String lower = body.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith(STRIPPED_PREFIX) || lower.equals("spawner");
    }

    public static boolean isControlNode(NpcDialogueGraph.Node node) {
        return node != null
                && node.special == 0
                && node.type == NpcDialogueGraph.TRIGGER
                && isControlAction(node.action);
    }

    public static void applyDefaults(NpcDialogueGraph.Node node) {
        if (node == null) return;
        node.type = NpcDialogueGraph.TRIGGER;
        node.text = "";
        node.action = DEFAULT_ACTION;
    }

    public static String normalizeOp(String raw) {
        if (raw == null) return OP_START;
        String u = raw.trim().toUpperCase(Locale.ROOT);
        for (String op : OPS) {
            if (op.equals(u)) return op;
        }
        return OP_START;
    }

    public static int opIndex(String op) {
        String norm = normalizeOp(op);
        for (int i = 0; i < OPS.length; i++) {
            if (OPS[i].equals(norm)) return i;
        }
        return 0;
    }

    public static String opKey(String op) {
        return "gui.settledlands.trial_ctl.op." + normalizeOp(op).toLowerCase(Locale.ROOT);
    }

    public static boolean opUsesConfig(String op) {
        String norm = normalizeOp(op);
        return OP_CONFIG.equals(norm) || OP_CONFIG_START.equals(norm);
    }

    public static Parsed parse(String action) {
        if (action == null) return null;
        String trimmed = action.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String body;
        if (lower.startsWith(ACTION_PREFIX)) {
            body = trimmed.substring(ACTION_PREFIX.length());
        } else if (lower.equals("trigger:spawner")) {
            body = "";
        } else if (lower.startsWith(STRIPPED_PREFIX)) {
            body = trimmed.substring(STRIPPED_PREFIX.length());
        } else if (lower.equals("spawner")) {
            body = "";
        } else {
            return null;
        }

        String op = OP_START;
        String dim = DEFAULT_DIMENSION;
        String rx = "";
        String ry = "";
        String rz = "";
        boolean killMobs = false;
        String cfgJson = "";

        if (!body.isEmpty()) {
            String[] parts = splitN(body, '|', 7);
            if (parts.length > 0 && !parts[0].isBlank()) op = normalizeOp(parts[0]);
            if (parts.length > 1) dim = normalizeDim(parts[1]);
            if (parts.length > 2) rx = sanitizeCoordInput(parts[2]);
            if (parts.length > 3) ry = sanitizeCoordInput(parts[3]);
            if (parts.length > 4) rz = sanitizeCoordInput(parts[4]);
            if (parts.length > 5) killMobs = "1".equals(parts[5].trim())
                    || "true".equalsIgnoreCase(parts[5].trim());
            if (parts.length > 6) cfgJson = parts[6] == null ? "" : parts[6].trim();
        }
        return new Parsed(op, dim, rx, ry, rz, killMobs, cfgJson);
    }

    /** Splits at most {@code limit - 1} pipes so JSON payload in slot 6 is never truncated. */
    private static String[] splitN(String s, char sep, int limit) {
        String[] out = new String[limit];
        int idx = 0;
        int start = 0;
        while (idx < limit - 1) {
            int next = s.indexOf(sep, start);
            if (next < 0) break;
            out[idx++] = s.substring(start, next);
            start = next + 1;
        }
        out[idx++] = start <= s.length() ? s.substring(start) : "";
        while (idx < limit) out[idx++] = "";
        return out;
    }

    public static String encode(String op, String dim, String rx, String ry, String rz,
            boolean killMobs, String cfgJson) {
        String cleanOp = normalizeOp(op);
        String cleanDim = normalizeDim(dim);
        String cleanX = sanitizeCoordInput(rx);
        String cleanY = sanitizeCoordInput(ry);
        String cleanZ = sanitizeCoordInput(rz);
        String cleanFlags = killMobs ? "1" : "0";
        String cleanJson = "";
        if (cfgJson != null && !cfgJson.isBlank()) {
            cleanJson = cfgJson.replace('|', '/').trim();
        }
        return ACTION_PREFIX + cleanOp + "|" + cleanDim + "|"
                + cleanX + "|" + cleanY + "|" + cleanZ + "|"
                + cleanFlags + "|" + cleanJson;
    }

    public static Parsed parsedOrDefault(NpcDialogueGraph.Node node) {
        if (node == null) return parse(DEFAULT_ACTION);
        Parsed p = parse(node.action);
        return p != null ? p : parse(DEFAULT_ACTION);
    }

    public static void write(NpcDialogueGraph.Node node, String op, String dim,
            String rx, String ry, String rz, boolean killMobs, String cfgJson) {
        if (node == null) return;
        node.action = encode(op, dim, rx, ry, rz, killMobs, cfgJson);
    }

    public static void setOp(NpcDialogueGraph.Node node, String op) {
        Parsed p = parsedOrDefault(node);
        String nextJson = p.configJson;
        if (opUsesConfig(op) && (nextJson == null || nextJson.isBlank())) {
            nextJson = TrialConfig446.defaults().toJson();
        }
        write(node, op, p.dimension, p.rawX, p.rawY, p.rawZ, p.killMobs, nextJson);
    }

    public static void setDimension(NpcDialogueGraph.Node node, String dim) {
        Parsed p = parsedOrDefault(node);
        write(node, p.op, dim, p.rawX, p.rawY, p.rawZ, p.killMobs, p.configJson);
    }

    public static void setX(NpcDialogueGraph.Node node, String rx) {
        Parsed p = parsedOrDefault(node);
        write(node, p.op, p.dimension, rx, p.rawY, p.rawZ, p.killMobs, p.configJson);
    }

    public static void setY(NpcDialogueGraph.Node node, String ry) {
        Parsed p = parsedOrDefault(node);
        write(node, p.op, p.dimension, p.rawX, ry, p.rawZ, p.killMobs, p.configJson);
    }

    public static void setZ(NpcDialogueGraph.Node node, String rz) {
        Parsed p = parsedOrDefault(node);
        write(node, p.op, p.dimension, p.rawX, p.rawY, rz, p.killMobs, p.configJson);
    }

    public static void setKillMobs(NpcDialogueGraph.Node node, boolean killMobs) {
        Parsed p = parsedOrDefault(node);
        write(node, p.op, p.dimension, p.rawX, p.rawY, p.rawZ, killMobs, p.configJson);
    }

    public static void setConfig(NpcDialogueGraph.Node node, TrialConfig446 cfg) {
        Parsed p = parsedOrDefault(node);
        String json = (cfg == null ? TrialConfig446.defaults() : cfg).toJson();
        write(node, p.op, p.dimension, p.rawX, p.rawY, p.rawZ, p.killMobs, json);
    }

    public static boolean isCoordInputValid(String text) {
        if (text == null || text.isEmpty()) return true;
        if (text.length() > MAX_COORD_LEN) return false;
        String t = text;
        int start = 0;
        if (t.charAt(0) == '~') {
            start = 1;
            if (t.length() == 1) return true;
        }
        for (int i = start; i < t.length(); i++) {
            char ch = t.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '+') continue;
            return false;
        }
        return true;
    }

    public static String sanitizeCoordInput(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replace('|', ' ').replace('\r', ' ').replace('\n', ' ');
        if (t.length() > MAX_COORD_LEN) t = t.substring(0, MAX_COORD_LEN);
        return t;
    }

    public static int resolveAxis(String raw, int origin) {
        if (raw == null || raw.isBlank()) return 0;
        String t = raw.trim();
        if (t.startsWith("~")) {
            String rest = t.substring(1).trim();
            if (rest.isEmpty()) return origin;
            try {
                return origin + Integer.parseInt(rest);
            } catch (NumberFormatException ignored) {
                return origin;
            }
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalizeDim(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_DIMENSION;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('|', '_');
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    public static int dimensionIndex(String dim) {
        if (dim == null || dim.isBlank()) return 0; // 0 = Current world
        int tpIdx = TeleportNode424.worldIndexForId(dim);
        return tpIdx >= 0 ? tpIdx + 1 : -1;
    }
}
