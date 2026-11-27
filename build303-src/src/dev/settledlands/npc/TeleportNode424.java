package dev.settledlands.npc;

/**
 * Build 424: pure rule, parser and coordinate formatter for the «Телепорт» graph node.
 *
 * <p>Stored inside the stock graph as {@link NpcDialogueGraph#TRIGGER} ({@code 2}, {@code special == 0})
 * with {@code action} carrying {@code "trigger:teleport:<dimension>|<x>|<y>|<z>"}. Using the stock
 * trigger node type preserves NBT/packet serialization, validation, clipboard copy/paste, undo/redo
 * and anchor placement across all five editors (NPC, common, block, door, chest) without changing any
 * wire protocol.</p>
 *
 * <p>Blank coordinates default to {@code 0, 0, 0}, matching the design rule («пустые координаты = 0,0,0»);
 * raw input strings are preserved so typing or erasing a box does not snap back under the cursor.</p>
 */
public final class TeleportNode424 {

    /** Canonical action prefix stored in {@link NpcDialogueGraph.Node#action}. */
    public static final String ACTION_PREFIX = "trigger:teleport:";

    /** Form seen if {@link NpcActions} already stripped {@code "trigger:"}. */
    public static final String STRIPPED_PREFIX = "teleport:";

    /** Default dimension when a Teleport node is created. */
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    /** Canonical default action: Overworld, blank coordinates (executed as 0, 0, 0). */
    public static final String DEFAULT_ACTION = ACTION_PREFIX + DEFAULT_DIMENSION + "|||";

    /** Row index in the NPC/common context menu (stock 0..15, Pursuit 16, Teleport 17). */
    public static final int TELEPORT_ROW = 17;

    /** Total rows in the NPC/common context menu once Teleport (17), Trial Control (18), and UE5 Var (19) join. */
    public static final int NPC_MENU_ITEMS = VarDataNode453.NPC_MENU_ITEMS;

    /** Visible rows in the NPC/common context menu window. */
    public static final int NPC_VISIBLE_ROWS = 5;

    /** Maximum {@code scrollRow} in the NPC/common context menu ({@code 20 - 5 = 15}). */
    public static final int NPC_MAX_SCROLL = NPC_MENU_ITEMS - NPC_VISIBLE_ROWS;

    /** Distinct cyan-blue accent for the Teleport menu row and canvas card. */
    public static final int ACCENT_COLOR = -11873793; // 0xFF4AD2FF

    /** Maximum character length permitted in each coordinate box. */
    public static final int MAX_COORD_LEN = 24;

    /** Reasonable world bounds clamp (Vanilla horizontal border +/- 30,000,000). */
    private static final double MAX_ABS_COORD = 30_000_000.0D;

    /** Maximum vertical coordinate clamp. */
    private static final double MAX_ABS_Y = 30_000_000.0D;

    public static final String MENU_LABEL_KEY = "gui.settledlands.teleport.menu";
    public static final String TITLE_KEY = "gui.settledlands.teleport.title";

    private static final String[] WORLD_IDS = {
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end",
        "settledlands:purgatory",
        "settledlands:hell_dungeon",
    };

    private static final String[] WORLD_KEYS = {
        "gui.settledlands.teleport.world.overworld",
        "gui.settledlands.teleport.world.nether",
        "gui.settledlands.teleport.world.end",
        "gui.settledlands.teleport.world.purgatory",
        "gui.settledlands.teleport.world.hell_dungeon",
    };

    public static final class Parsed {
        public final String dimension;
        public final String rawX;
        public final String rawY;
        public final String rawZ;
        public final double x;
        public final double y;
        public final double z;

        Parsed(String dimension, String rawX, String rawY, String rawZ,
                double x, double y, double z) {
            this.dimension = dimension;
            this.rawX = rawX;
            this.rawY = rawY;
            this.rawZ = rawZ;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private TeleportNode424() {}

    public static int worldCount() {
        return WORLD_IDS.length;
    }

    public static String worldId(int index) {
        if (index < 0 || index >= WORLD_IDS.length) return DEFAULT_DIMENSION;
        return WORLD_IDS[index];
    }

    public static String worldKey(int index) {
        if (index < 0 || index >= WORLD_KEYS.length) return WORLD_KEYS[0];
        return WORLD_KEYS[index];
    }

    public static int worldIndexForId(String dimension) {
        if (dimension == null || dimension.isEmpty()) return 0;
        String clean = dimension.trim().toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < WORLD_IDS.length; i++) {
            if (WORLD_IDS[i].equals(clean)) return i;
        }
        return -1;
    }

    public static String worldKeyForId(String dimension) {
        int idx = worldIndexForId(dimension);
        return idx >= 0 ? WORLD_KEYS[idx] : null;
    }

    public static boolean isTeleportAction(String action) {
        if (action == null) return false;
        String trimmed = action.trim();
        if (trimmed.isEmpty()) return false;
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith(ACTION_PREFIX)
                || lower.equals("trigger:teleport")
                || lower.startsWith("trigger:teleport ");
    }

    public static boolean isStrippedTeleportAction(String body) {
        if (body == null) return false;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return false;
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith(STRIPPED_PREFIX)
                || lower.equals("teleport")
                || lower.startsWith("teleport ");
    }

    public static boolean isTeleportNode(NpcDialogueGraph.Node node) {
        return node != null
                && node.special == 0
                && node.type == NpcDialogueGraph.TRIGGER
                && isTeleportAction(node.action);
    }

    public static void applyDefaults(NpcDialogueGraph.Node node) {
        if (node == null) return;
        node.type = NpcDialogueGraph.TRIGGER;
        node.text = "";
        node.action = DEFAULT_ACTION;
    }

    /**
     * Parses either {@code "trigger:teleport:<dim>|<x>|<y>|<z>"} or {@code "teleport:<dim>|<x>|<y>|<z>"}
     * (and space-separated fallback {@code "trigger:teleport <dim> <x> <y> <z>"}).
     * Missing or blank coordinates always resolve to {@code 0.0D}.
     */
    public static Parsed parse(String action) {
        if (action == null) return null;
        String trimmed = action.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        String body;
        if (lower.startsWith(ACTION_PREFIX)) {
            body = trimmed.substring(ACTION_PREFIX.length());
        } else if (lower.equals("trigger:teleport")) {
            body = "";
        } else if (lower.startsWith("trigger:teleport ")) {
            body = trimmed.substring("trigger:teleport ".length()).trim();
        } else if (lower.startsWith(STRIPPED_PREFIX)) {
            body = trimmed.substring(STRIPPED_PREFIX.length());
        } else if (lower.equals("teleport")) {
            body = "";
        } else if (lower.startsWith("teleport ")) {
            body = trimmed.substring("teleport ".length()).trim();
        } else {
            return null;
        }

        String dim = DEFAULT_DIMENSION;
        String rx = "";
        String ry = "";
        String rz = "";

        if (!body.isEmpty()) {
            if (body.indexOf('|') >= 0) {
                String[] parts = body.split("\\|", -1);
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    dim = normalizeDimension(parts[0]);
                }
                if (parts.length > 1) rx = sanitizeRawCoord(parts[1]);
                if (parts.length > 2) ry = sanitizeRawCoord(parts[2]);
                if (parts.length > 3) rz = sanitizeRawCoord(parts[3]);
            } else {
                String[] parts = body.trim().split("\\s+");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    dim = normalizeDimension(parts[0]);
                }
                if (parts.length > 1) rx = sanitizeRawCoord(parts[1]);
                if (parts.length > 2) ry = sanitizeRawCoord(parts[2]);
                if (parts.length > 3) rz = sanitizeRawCoord(parts[3]);
            }
        }

        double x = clampCoord(parseCoord(rx), MAX_ABS_COORD);
        double y = clampCoord(parseCoord(ry), MAX_ABS_Y);
        double z = clampCoord(parseCoord(rz), MAX_ABS_COORD);
        return new Parsed(dim, rx, ry, rz, x, y, z);
    }

    private static String normalizeDimension(String raw) {
        if (raw == null) return DEFAULT_DIMENSION;
        String clean = raw.trim().replace('|', '_').replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.isEmpty()) return DEFAULT_DIMENSION;
        if (clean.indexOf(':') < 0) {
            String lower = clean.toLowerCase(java.util.Locale.ROOT);
            if (lower.equals("overworld")) return "minecraft:overworld";
            if (lower.equals("nether") || lower.equals("the_nether")) return "minecraft:the_nether";
            if (lower.equals("end") || lower.equals("the_end")) return "minecraft:the_end";
            if (lower.equals("purgatory")) return "settledlands:purgatory";
            if (lower.equals("hell_dungeon") || lower.equals("dungeon")) return "settledlands:hell_dungeon";
            return "minecraft:" + lower;
        }
        return clean.toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean isCoordInput(String text) {
        if (text == null || text.isEmpty()) return true;
        if (text.length() > MAX_COORD_LEN) return false;
        boolean seenDot = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-') {
                if (i != 0) return false;
            } else if (c == '.' || c == ',') {
                if (seenDot) return false;
                seenDot = true;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeRawCoord(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > MAX_COORD_LEN) s = s.substring(0, MAX_COORD_LEN);
        return isCoordInput(s) ? s : "";
    }

    public static double parseCoord(String raw) {
        if (raw == null) return 0.0D;
        String s = raw.trim().replace(',', '.');
        if (s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")) return 0.0D;
        try {
            double v = Double.parseDouble(s);
            if (!Double.isFinite(v)) return 0.0D;
            return v == 0.0D ? 0.0D : v;
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static double clampCoord(double v, double limit) {
        if (!Double.isFinite(v)) return 0.0D;
        if (v > limit) return limit;
        if (v < -limit) return -limit;
        return v == 0.0D ? 0.0D : v;
    }

    public static String encode(String dimension, String rawX, String rawY, String rawZ) {
        String dim = normalizeDimension(dimension);
        return ACTION_PREFIX + dim + "|"
                + sanitizeRawCoord(rawX) + "|"
                + sanitizeRawCoord(rawY) + "|"
                + sanitizeRawCoord(rawZ);
    }

    public static String dimensionOf(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? DEFAULT_DIMENSION : p.dimension;
    }

    public static int worldIndex(NpcDialogueGraph.Node node) {
        return worldIndexForId(dimensionOf(node));
    }

    public static String rawX(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? "" : p.rawX;
    }

    public static String rawY(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? "" : p.rawY;
    }

    public static String rawZ(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        return p == null ? "" : p.rawZ;
    }

    public static void setWorld(NpcDialogueGraph.Node node, int worldIndex) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String rx = p == null ? "" : p.rawX;
        String ry = p == null ? "" : p.rawY;
        String rz = p == null ? "" : p.rawZ;
        node.action = encode(worldId(worldIndex), rx, ry, rz);
    }

    public static void setDimensionId(NpcDialogueGraph.Node node, String dimension) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String rx = p == null ? "" : p.rawX;
        String ry = p == null ? "" : p.rawY;
        String rz = p == null ? "" : p.rawZ;
        node.action = encode(dimension, rx, ry, rz);
    }

    public static void setX(NpcDialogueGraph.Node node, String rawX) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String dim = p == null ? DEFAULT_DIMENSION : p.dimension;
        String ry = p == null ? "" : p.rawY;
        String rz = p == null ? "" : p.rawZ;
        node.action = encode(dim, rawX, ry, rz);
    }

    public static void setY(NpcDialogueGraph.Node node, String rawY) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String dim = p == null ? DEFAULT_DIMENSION : p.dimension;
        String rx = p == null ? "" : p.rawX;
        String rz = p == null ? "" : p.rawZ;
        node.action = encode(dim, rx, rawY, rz);
    }

    public static void setZ(NpcDialogueGraph.Node node, String rawZ) {
        if (node == null) return;
        Parsed p = parse(node.action);
        String dim = p == null ? DEFAULT_DIMENSION : p.dimension;
        String rx = p == null ? "" : p.rawX;
        String ry = p == null ? "" : p.rawY;
        node.action = encode(dim, rx, ry, rawZ);
    }

    public static String formatCoord(double v) {
        if (!Double.isFinite(v) || v == 0.0D) return "0";
        long rounded = (long) v;
        if ((double) rounded == v) return Long.toString(rounded);
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            s = s.substring(0, s.length() - 1);
        }
        return s.equals("-0") ? "0" : s;
    }

    public static String formatSummaryCoords(NpcDialogueGraph.Node node) {
        Parsed p = node == null ? null : parse(node.action);
        if (p == null) return "0, 0, 0";
        return formatCoord(p.x) + ", " + formatCoord(p.y) + ", " + formatCoord(p.z);
    }
}
