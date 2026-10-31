package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
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

    private static final String START_NPC = "trigger:pursuit:start:npc:";
    private static final String START_PLAYER = "trigger:pursuit:start:player:";
    private static final String STOP = "trigger:pursuit:stop";

    /** Last "start" action of a node, kept while the node is switched to "stop". */
    private static final Map<Node, String> LAST_START = new WeakHashMap<>();

    private PursuitGraphNode272() {}

    // ---- action helpers ----

    public static String actionForMenuRow(int row) {
        return row == PURSUIT_ROW ? START_NPC : null;
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
        if (action.startsWith(START_NPC)) return action.substring(START_NPC.length());
        if (action.startsWith(START_PLAYER)) return action.substring(START_PLAYER.length());
        return "";
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

    private static String buildAction(int targetType, String targetValue, boolean stopFlag) {
        if (stopFlag) return STOP;
        String kind = targetType == TARGET_PLAYER ? "player" : "npc";
        String value = targetValue == null ? "" : targetValue.trim();
        return "trigger:pursuit:start:" + kind + ":" + value;
    }

    public static void setTargetType(Node node, int targetType) {
        if (node == null) return;
        boolean stop = stopFlag(node);
        String start = buildAction(targetType, targetValue(node), false);
        LAST_START.put(node, start);
        node.action = stop ? STOP : start;
    }

    public static void setTargetValue(Node node, String value) {
        if (node == null) return;
        boolean stop = stopFlag(node);
        String start = buildAction(targetTypeIndex(node), value, false);
        LAST_START.put(node, start);
        node.action = stop ? STOP : start;
    }

    public static void setStop(Node node, boolean stop) {
        if (node == null) return;
        if (stop) {
            if (!stopFlag(node)) LAST_START.put(node, node.action);
            node.action = STOP;
            return;
        }
        String previous = LAST_START.get(node);
        node.action = previous != null ? previous : buildAction(targetTypeIndex(node), "", false);
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

        // Line 3 - stop switch state.
        if (stop) {
            graphics.drawString(font, clip(font,
                    Component.translatable("gui.settledlands.pursuit.stop_toggle").getString() + " \u2713",
                    CARD_WIDTH - 16), 8, 54, COL_STOP, false);
        }

        // Stock port dots: input on the left edge, output on the right edge.
        graphics.fill(-5, mid - 5, 5, mid + 5, COL_TITLE);
        graphics.fill(CARD_WIDTH - 5, mid - 5, CARD_WIDTH + 5, mid + 5, COL_PORT_OUT);

        graphics.pose().popPose();
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
