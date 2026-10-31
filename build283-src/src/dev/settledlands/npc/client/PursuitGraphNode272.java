package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 277: the pursuit node card plus the action helpers.
 *
 * The card is drawn here so that pursuit nodes keep their own purple header and
 * their own two text lines instead of the generic trigger box. Everything it
 * shows is read from node.action, and the inspector writes to the same field, so
 * the card and the inspector cannot drift apart. Nothing on the card is edited
 * in place: the node is edited in the inspector panel
 * (NpcPursuitInspectorMixin277), which uses ordinary widgets.
 *
 * A pursuit node is stored as a TRIGGER node (the server-side trigger path runs
 * the "trigger:pursuit:..." action), but the screen is told to build no stock
 * inspector for it - see NpcPursuitInspectorMixin277.
 *
 * Build 279 adds the movement settings the pursuit node was missing: how close
 * the NPC is allowed to come, from what distance it starts catching up and, in
 * Build 280, how hard it accelerates. They live in the action itself, after a
 * separator the target cannot contain, so they are saved with the graph and read
 * by NpcPursuit269 on the server:
 *
 * <pre>trigger:pursuit:start:npc:&lt;target&gt;|&lt;keep&gt;|&lt;catch-up&gt;|&lt;boost&gt;</pre>
 *
 * The node also has an output, the way a blueprint node has an output pin: the
 * target it is bound to, printed on the card next to the output port and
 * available to every node that runs after it as {@code %pursuit%}.
 *
 * Build 283 gives the node the second output a blueprint node has: next to the
 * execution output there is now a value output - "Target" - that can be wired to
 * any node. The wire is a variable reference: it pulls the text value of what
 * this NPC pursues (the same text {@code %pursuit%} resolves to), and it is saved
 * with the graph. Consumers are stored as an extra parameter of the action,
 * after the three numbers, so no new field and no format change is needed:
 *
 * <pre>trigger:pursuit:start:npc:&lt;target&gt;|&lt;keep&gt;|&lt;catch-up&gt;|&lt;boost&gt;|@&lt;ids&gt;</pre>
 *
 * The server reads the three numbers by index and ignores what follows them.
 */
public final class PursuitGraphNode272 {
    public static final int PURSUIT_ROW = 16;
    public static final int MENU_ITEM_COUNT = 17;

    /** Stock node geometry (NpcGraphCanvas.NODE_W / NODE_H). */
    public static final int CARD_WIDTH = 220;
    public static final int CARD_HEIGHT = 100;

    private static final int HEADER_H = 22;

    private static final int COL_BORDER_IDLE = 0xff46576b;
    private static final int COL_BORDER_SELECTED = 0xffe9bd67;
    private static final int COL_BODY = 0xff26313f;
    private static final int COL_HEADER = 0xffc77dff;
    private static final int COL_TITLE = 0xfff0f3f7;
    private static final int COL_TEXT = 0xffe3ebf3;
    private static final int COL_MUTED = 0xff8a9ba8;
    private static final int COL_STOP = 0xffa7f2ca;
    private static final int COL_PORT_OUT = 0xffc77dff;

    public static final int TARGET_NPC = 0;
    public static final int TARGET_PLAYER = 1;

    /** Longest target the inspector field accepts. */
    public static final int MAX_VALUE_LENGTH = 64;

    /** Standoff: 1-2 blocks is the requested range, 1.6 is the default. */
    public static final double MIN_KEEP_DISTANCE = 1.0D;
    public static final double MAX_KEEP_DISTANCE = 8.0D;
    public static final double DEFAULT_KEEP_DISTANCE = 1.6D;

    /** Distance at which the NPC switches to the escort catch-up mode. */
    public static final double MIN_CATCH_UP_DISTANCE = 2.0D;
    public static final double MAX_CATCH_UP_DISTANCE = 48.0D;
    public static final double DEFAULT_CATCH_UP_DISTANCE = 8.0D;

    /** How much faster than the walking speed the NPC may run while catching up. */
    public static final double MIN_CATCH_UP_BOOST = 1.0D;
    public static final double MAX_CATCH_UP_BOOST = 3.0D;
    public static final double DEFAULT_CATCH_UP_BOOST = 1.5D;

    /** Separator between the target and its numbers; identifiers and names cannot contain it. */
    private static final char PARAM_SEPARATOR = '|';

    /**
     * Second output of the node: the target value. Drawn on the right edge below
     * the execution pin, hit the same way a blueprint data pin is hit.
     */
    public static final int TARGET_PIN_Y = CARD_HEIGHT - 8;

    /** Extra radius (in card pixels) around the value pin that still counts as a hit. */
    public static final double TARGET_PIN_RADIUS = 7.0D;

    /** Marks the consumer list inside the action; identifiers and names never contain it. */
    private static final char CONSUMER_MARK = '@';

    /** A node cannot feed more consumers than this. */
    public static final int MAX_CONSUMERS = 12;

    private static final String START_NPC = "trigger:pursuit:start:npc:";
    private static final String START_PLAYER = "trigger:pursuit:start:player:";
    private static final String STOP = "trigger:pursuit:stop";

    /** Last "start" action of a node, kept while the node is switched to "stop". */
    private static final Map<Node, String> LAST_START = new WeakHashMap<>();

    private PursuitGraphNode272() {}

    // ---- action helpers ----

    public static String actionForMenuRow(int row) {
        if (row != PURSUIT_ROW) return null;
        return START_NPC + PARAM_SEPARATOR + number(DEFAULT_KEEP_DISTANCE)
                + PARAM_SEPARATOR + number(DEFAULT_CATCH_UP_DISTANCE)
                + PARAM_SEPARATOR + number(DEFAULT_CATCH_UP_BOOST);
    }

    /** "1.6" - one decimal, Locale.ROOT, the same shape the server parses. */
    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static double setting(String raw, double fallback, double min, double max) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (Double.isNaN(parsed)) return fallback;
            return parsed < min ? min : (parsed > max ? max : parsed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static boolean isPursuitNode(Node node) {
        if (node == null || node.type != NpcDialogueGraph.TRIGGER) return false;
        String action = node.action;
        return action != null && (action.startsWith("trigger:pursuit:") || action.startsWith("trigger:pursuit "));
    }

    public static int targetTypeIndex(Node node) {
        String action = node == null ? null : actionOf(node);
        if (action != null && action.startsWith(START_PLAYER)) return TARGET_PLAYER;
        return TARGET_NPC;
    }

    public static String targetValue(Node node) {
        String action = node == null ? null : actionOf(node);
        if (action == null) return "";
        String value;
        if (action.startsWith(START_NPC)) value = action.substring(START_NPC.length());
        else if (action.startsWith(START_PLAYER)) value = action.substring(START_PLAYER.length());
        else return "";
        int separator = value.indexOf(PARAM_SEPARATOR);
        return (separator >= 0 ? value.substring(0, separator) : value).trim();
    }

    /** Numbers written after the target, or null when the node has none. */
    private static String[] params(Node node) {
        String action = node == null ? null : actionOf(node);
        if (action == null) return new String[0];
        int separator = action.indexOf(PARAM_SEPARATOR);
        if (separator < 0) return new String[0];
        return action.substring(separator + 1).split("\\|");
    }

    private static String param(Node node, int index) {
        String[] values = params(node);
        return index < values.length ? values[index] : null;
    }

    /** Configured standoff, 1.6 blocks by default. */
    public static double keepDistance(Node node) {
        return setting(param(node, 0), DEFAULT_KEEP_DISTANCE, MIN_KEEP_DISTANCE, MAX_KEEP_DISTANCE);
    }

    /** Configured catch-up distance, 8 blocks by default. */
    public static double catchUpDistance(Node node) {
        return setting(param(node, 1), DEFAULT_CATCH_UP_DISTANCE,
                MIN_CATCH_UP_DISTANCE, MAX_CATCH_UP_DISTANCE);
    }

    /** Configured catch-up speed multiplier, 1.5 by default. */
    public static double catchUpBoost(Node node) {
        return setting(param(node, 2), DEFAULT_CATCH_UP_BOOST, MIN_CATCH_UP_BOOST, MAX_CATCH_UP_BOOST);
    }

    public static String keepDistanceText(Node node) {
        return number(keepDistance(node));
    }

    public static String catchUpDistanceText(Node node) {
        return number(catchUpDistance(node));
    }

    public static String catchUpBoostText(Node node) {
        return number(catchUpBoost(node));
    }

    public static boolean stopFlag(Node node) {
        String action = node == null ? null : node.action;
        return action != null && action.equals(STOP);
    }

    /** node.action, or the remembered start action while the node is set to stop. */
    private static String actionOf(Node node) {
        if (node == null) return null;
        if (stopFlag(node)) {
            String previous = LAST_START.get(node);
            return previous != null ? previous : node.action;
        }
        return node.action;
    }

    public static String targetTypeLabelKey(Node node) {
        return targetTypeIndex(node) == TARGET_PLAYER
                ? "gui.settledlands.pursuit.target_player"
                : "gui.settledlands.pursuit.target_npc";
    }

    /** Inspector button caption, e.g. "Target: NPC". */
    public static String targetTypeButtonKey(Node node) {
        return targetTypeIndex(node) == TARGET_PLAYER
                ? "gui.settledlands.pursuit.target_player_button"
                : "gui.settledlands.pursuit.target_npc_button";
    }

    public static String stopLabelKey(Node node) {
        return stopFlag(node)
                ? "gui.settledlands.pursuit.stop_toggle_on"
                : "gui.settledlands.pursuit.stop_toggle";
    }

    private static String buildAction(int targetType, String targetValue, double keepDistance,
            double catchUpDistance, double catchUpBoost, boolean stopFlag, List<Integer> consumers) {
        if (stopFlag) return STOP;
        String kind = targetType == TARGET_PLAYER ? "player" : "npc";
        String value = targetValue == null ? "" : targetValue.trim();
        value = stripParams(value);
        StringBuilder action = new StringBuilder("trigger:pursuit:start:").append(kind).append(':')
                .append(value).append(PARAM_SEPARATOR)
                .append(number(keepDistance)).append(PARAM_SEPARATOR).append(number(catchUpDistance))
                .append(PARAM_SEPARATOR).append(number(catchUpBoost));
        if (consumers != null && !consumers.isEmpty()) {
            action.append(PARAM_SEPARATOR).append(CONSUMER_MARK);
            for (int i = 0; i < consumers.size(); i++) {
                if (i > 0) action.append(',');
                action.append(consumers.get(i).intValue());
            }
        }
        return action.toString();
    }

    // ---- value output: the target as a variable ----

    /** Nodes wired to this pursuit node's target output, in the order they were linked. */
    public static List<Integer> consumers(Node node) {
        String[] values = params(node);
        if (values.length <= 3) return List.of();
        String token = values[3];
        if (token == null || token.isEmpty() || token.charAt(0) != CONSUMER_MARK) return List.of();
        List<Integer> ids = new ArrayList<>();
        for (String part : token.substring(1).split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                int id = Integer.parseInt(trimmed);
                if (id >= 0 && !ids.contains(id) && ids.size() < MAX_CONSUMERS) ids.add(id);
            } catch (NumberFormatException ignored) {
                // a broken entry must never take the node down with it
            }
        }
        return ids;
    }

    public static boolean hasConsumers(Node node) {
        return !consumers(node).isEmpty();
    }

    /** Rewrites the action with exactly these consumers, keeping every setting. */
    public static void setConsumers(Node node, List<Integer> ids) {
        if (node == null) return;
        String start = buildAction(targetTypeIndex(node), targetValue(node), keepDistance(node),
                catchUpDistance(node), catchUpBoost(node), false, ids);
        LAST_START.put(node, start);
        node.action = stopFlag(node) ? STOP : start;
    }

    /** Adds or removes one consumer; {@code -1} clears the whole list. */
    public static void toggleConsumer(Node node, int consumerId) {
        if (node == null) return;
        List<Integer> ids = new ArrayList<>(consumers(node));
        if (consumerId < 0) {
            ids.clear();
        } else if (!ids.remove(Integer.valueOf(consumerId)) && ids.size() < MAX_CONSUMERS) {
            ids.add(consumerId);
        }
        setConsumers(node, ids);
    }

    // ---- value output: geometry ----

    /** Screen X of the value pin: the right edge of the card, like the execution port. */
    public static double targetPinScreenX(NpcGraphCanvas canvas, Node node) {
        return canvas.portScreenX(node, true);
    }

    /** Screen Y of the value pin. */
    public static double targetPinScreenY(NpcGraphCanvas canvas, Node node) {
        return canvas.screenY(node.y) + TARGET_PIN_Y * canvas.zoom;
    }

    public static boolean hitTargetPin(NpcGraphCanvas canvas, Node node, double mouseX, double mouseY) {
        if (canvas == null || node == null) return false;
        double dx = mouseX - targetPinScreenX(canvas, node);
        double dy = mouseY - targetPinScreenY(canvas, node);
        double radius = Math.max(TARGET_PIN_RADIUS, TARGET_PIN_RADIUS * canvas.zoom);
        return dx * dx + dy * dy <= radius * radius;
    }

    private static String stripParams(String value) {
        int separator = value.indexOf(PARAM_SEPARATOR);
        return (separator >= 0 ? value.substring(0, separator) : value).trim();
    }

    public static void setTargetType(Node node, int targetType) {
        if (node == null) return;
        boolean stop = stopFlag(node);
        String start = buildAction(targetType, targetValue(node), keepDistance(node),
                catchUpDistance(node), catchUpBoost(node), false, consumers(node));
        LAST_START.put(node, start);
        node.action = stop ? STOP : start;
    }

    public static void setTargetValue(Node node, String value) {
        if (node == null) return;
        boolean stop = stopFlag(node);
        String start = buildAction(targetTypeIndex(node), value, keepDistance(node),
                catchUpDistance(node), catchUpBoost(node), false, consumers(node));
        LAST_START.put(node, start);
        node.action = stop ? STOP : start;
    }

    public static void setKeepDistance(Node node, double value) {
        applySettings(node, value, catchUpDistance(node), catchUpBoost(node));
    }

    public static void setCatchUpDistance(Node node, double value) {
        applySettings(node, keepDistance(node), value, catchUpBoost(node));
    }

    public static void setCatchUpBoost(Node node, double value) {
        applySettings(node, keepDistance(node), catchUpDistance(node), value);
    }

    private static void applySettings(Node node, double keepDistance, double catchUpDistance,
            double catchUpBoost) {
        String start = buildAction(targetTypeIndex(node), targetValue(node), keepDistance,
                catchUpDistance, catchUpBoost, false, consumers(node));
        if (start.equals(actionOf(node))) return;
        LAST_START.put(node, start);
        node.action = stopFlag(node) ? STOP : start;
    }

    public static void setStop(Node node, boolean stop) {
        if (node == null) return;
        if (stop) {
            if (!stopFlag(node)) LAST_START.put(node, node.action);
            node.action = STOP;
            return;
        }
        String previous = LAST_START.get(node);
        node.action = previous != null ? previous
                : buildAction(targetTypeIndex(node), "", keepDistance(node), catchUpDistance(node),
                        catchUpBoost(node), false, consumers(node));
    }

    // ---- context menu ----

    public static boolean isOpenForCreation(NpcGraphContextMenu menu) {
        return menu != null && menu.open && menu.targetNodeId < 0;
    }

    public static int rowAt(NpcGraphContextMenu menu, double x, double y) {
        return menu == null ? -1 : menu.rowAt(x, y);
    }

    public record MenuPoint(int row, double x, double y, int wireFrom, int wirePort) {}

    public static MenuPoint capture(NpcGraphContextMenu menu, int row) {
        if (menu == null) return null;
        return new MenuPoint(row, menu.worldX, menu.worldY, menu.pendingWireFrom, menu.pendingWirePort);
    }

    public static void drawMenuRow(NpcGraphContextMenu menu, GuiGraphics graphics, Font font,
            int mouseX, int mouseY) {
        if (!isOpenForCreation(menu) || graphics == null || font == null) return;
        int hovered = menu.rowAt(mouseX, mouseY);
        int visible = PURSUIT_ROW - menu.scrollRow;
        if (visible < 0 || visible >= 5) return;
        // Same geometry as the stock rows: the menu draws its scrollbar in the
        // rightmost 10 px, so the row stops before it.
        int y = menu.top + 20 + visible * 22;
        int right = menu.left + menu.width - 10;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 600.0);
        graphics.fill(menu.left + 2, y, right, y + 22,
                hovered == PURSUIT_ROW ? 0xff33485a : 0xff1a2027);
        String text = clip(font, Component.translatable("gui.settledlands.graph.pursuit_menu").getString(),
                right - menu.left - 12);
        graphics.drawString(font, text, menu.left + 8, y + 7, COL_HEADER, false);
        graphics.pose().popPose();
    }

    // ---- card ----

    public static void drawNode(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || !isPursuitNode(node)) {
            screen.settledlands$drawOriginal(graphics, node);
            return;
        }

        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        String targetValue = targetValue(node);
        boolean stop = stopFlag(node);
        int mid = CARD_HEIGHT / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0);
        graphics.pose().scale((float) canvas.zoom, (float) canvas.zoom, 1.0f);

        // Same frame, header height and port dots as the stock node.
        graphics.fill(-2, -2, CARD_WIDTH + 2, CARD_HEIGHT + 2,
                screen.settledlands$isSelected(node.id) ? COL_BORDER_SELECTED : COL_BORDER_IDLE);
        graphics.fill(0, 0, CARD_WIDTH, CARD_HEIGHT, COL_BODY);
        graphics.fill(0, 0, CARD_WIDTH, HEADER_H, COL_HEADER);

        graphics.drawString(font, clip(font,
                Component.translatable("gui.settledlands.graph.pursuit_node").getString(), CARD_WIDTH - 16),
                8, 7, COL_TITLE, false);

        // Line 1 - target type.
        String type = Component.translatable(targetTypeLabelKey(node)).getString();
        graphics.drawString(font, clip(font,
                Component.translatable("gui.settledlands.pursuit.target_value").getString() + " " + type,
                CARD_WIDTH - 16), 8, 30, COL_TEXT, false);

        // Line 2 - target value.
        String value = targetValue.isEmpty()
                ? Component.translatable("gui.settledlands.pursuit.field_empty").getString()
                : targetValue;
        graphics.drawString(font, clip(font, value, CARD_WIDTH - 16), 8, 42,
                targetValue.isEmpty() ? COL_MUTED : COL_TEXT, false);

        // Line 3 - movement settings the server reads from the action.
        String settings = Component.translatable("gui.settledlands.pursuit.keep_distance").getString() + " "
                + keepDistanceText(node) + "   "
                + Component.translatable("gui.settledlands.pursuit.catch_up").getString() + " "
                + catchUpDistanceText(node) + "   \u00d7" + catchUpBoostText(node);
        graphics.drawString(font, clip(font, settings, CARD_WIDTH - 16), 8, 54, COL_TEXT, false);

        // Line 4 - stop switch state.
        if (stop) {
            graphics.drawString(font, clip(font,
                    Component.translatable("gui.settledlands.pursuit.stop_toggle").getString() + " \u2713",
                    CARD_WIDTH - 16), 8, 66, COL_STOP, false);
        }

        // Outputs of the node, the way a blueprint node has them: the execution
        // output next to the stock port dot, and below it the value output - the
        // target this node is bound to, which is what %pursuit% resolves to.
        int consumed = consumers(node).size();
        String output = "\u2192 " + Component.translatable("gui.settledlands.pursuit.output").getString()
                + ": " + (targetValue.isEmpty()
                        ? Component.translatable("gui.settledlands.pursuit.field_empty").getString()
                        : targetValue);
        graphics.drawString(font, clip(font, output, CARD_WIDTH - 30), 8, 78,
                targetValue.isEmpty() ? COL_MUTED : COL_PORT_OUT, false);

        // Stock port dots: input on the left edge, execution output on the right edge.
        graphics.fill(-5, mid - 5, 5, mid + 5, COL_TITLE);
        graphics.fill(CARD_WIDTH - 5, mid - 5, CARD_WIDTH + 5, mid + 5, COL_PORT_OUT);

        // Value pin: a diamond, so it cannot be mistaken for the round execution port.
        int pinY = TARGET_PIN_Y;
        graphics.fill(CARD_WIDTH - 6, pinY - 2, CARD_WIDTH + 6, pinY + 2, COL_PORT_OUT);
        graphics.fill(CARD_WIDTH - 2, pinY - 6, CARD_WIDTH + 2, pinY + 6, COL_PORT_OUT);
        if (consumed > 0) {
            String count = String.valueOf(consumed);
            graphics.drawString(font, count, CARD_WIDTH - 8 - font.width(count) - 4, pinY - 4,
                    COL_PORT_OUT, false);
        }

        graphics.pose().popPose();
    }

    /**
     * Draws every saved target wire, plus the wire being dragged. Screen space is
     * correct here because the screen renders nodes with their own transform and
     * returns to GUI space before our tail hook runs.
     */
    public static void drawTargetWires(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            int mouseX, int mouseY, int draggingFrom) {
        if (screen == null || graphics == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (canvas == null || graph == null) return;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!isPursuitNode(node)) continue;
            double x1 = targetPinScreenX(canvas, node);
            double y1 = targetPinScreenY(canvas, node);
            if (node.id == draggingFrom) {
                dashedWire(graphics, x1, y1, mouseX, mouseY, COL_PORT_OUT);
                continue;
            }
            for (Integer id : consumers(node)) {
                NpcDialogueGraph.Node consumer = id == null ? null : graph.get(id.intValue());
                if (consumer == null) continue;
                // Land on the consumer's own input port, whatever kind of node it is.
                double x2 = canvas.portScreenX(consumer, false);
                double y2 = canvas.portScreenY(consumer, false, 0);
                dashedWire(graphics, x1, y1, x2, y2, COL_PORT_OUT);
            }
        }
    }

    /** A dashed cable: no buffers, no state, a couple of dozen small quads. */
    private static void dashedWire(GuiGraphics graphics, double x1, double y1, double x2, double y2,
            int color) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) {
            return;
        }
        if (Math.abs(x2 - x1) + Math.abs(y2 - y1) > 20000.0D) return;
        double controlX = (x1 + x2) * 0.5D;
        int steps = 26;
        for (int i = 0; i < steps; i++) {
            if ((i & 1) == 1) continue;
            double t = i / (double) steps;
            double inv = 1.0D - t;
            double px = inv * inv * x1 + 2.0D * inv * t * controlX + t * t * x2;
            double py = inv * inv * y1 + 2.0D * inv * t * y1 + t * t * y2;
            int sx = (int) Math.round(px);
            int sy = (int) Math.round(py);
            graphics.fill(sx - 1, sy - 1, sx + 1, sy + 1, color);
        }
    }

    private static String clip(Font font, String text, int width) {
        if (font == null || text == null) return "";
        String out = text;
        while (!out.isEmpty() && font.width(out) > width) out = out.substring(0, out.length() - 1);
        return out;
    }

    public static boolean visible(NpcGraphCanvas c, Node n) {
        if (!isPursuitNode(n)) return true;
        double x = c.screenX(n.x), y = c.screenY(n.y);
        return x + CARD_WIDTH * c.zoom >= c.left && x <= c.right && y + CARD_HEIGHT * c.zoom >= c.top && y <= c.bottom;
    }

    public static int hit(NpcGraphCanvas c, NpcDialogueGraph graph, double sx, double sy) {
        if (!c.contains(sx, sy)) return -1;
        double x = c.worldX(sx), y = c.worldY(sy);
        int found = -1;
        for (Node n : graph.nodes.values()) {
            if (isPursuitNode(n)) {
                if (n.x <= x && x <= n.x + CARD_WIDTH && n.y <= y && y <= n.y + CARD_HEIGHT) found = n.id;
            }
        }
        return found;
    }

    /** Pursuit ports are the stock generic ports: input at x, output at x + width. */
    public static int hitPort(NpcGraphCanvas c, NpcDialogueGraph graph, double x, double y, boolean output) {
        return c.settledlands$hitPortOriginal(graph, x, y, output);
    }

    public static void focus(NpcGraphCanvas c, Node n) {
        if (n == null || !isPursuitNode(n)) return;
        c.panX = (c.right - c.left) / 2.0 - (n.x + CARD_WIDTH / 2.0) * c.zoom;
        c.panY = (c.bottom - c.top) / 2.0 - (n.y + CARD_HEIGHT / 2.0) * c.zoom;
    }
}
