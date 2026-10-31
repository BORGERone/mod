package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 275: pursuit node rendered as a regular graph node.
 *
 * The card is the stock node box - 220x100, same border, background, 22 px
 * header, header colour of the TRIGGER type and the stock port dots - so it is
 * indistinguishable from the other nodes and NpcGraphCanvas#portScreenX/Y
 * (input at x, output at x + 220, both at y + 50) describe its ports exactly.
 * The three pursuit controls are drawn inside that box.
 */
public final class PursuitGraphNode272 {
    public static final int PURSUIT_ROW = 16;
    public static final int MENU_ITEM_COUNT = 17;

    /** Stock node geometry (NpcGraphCanvas.NODE_W / NODE_H). */
    public static final int CARD_WIDTH = 220;
    public static final int CARD_HEIGHT = 100;

    private static final int HEADER_H = 22;
    private static final int ROW_X = 8;
    private static final int ROW_W = CARD_WIDTH - 16;
    private static final int ROW_H = 18;
    private static final int DROPDOWN_X = ROW_X, DROPDOWN_Y = 27, DROPDOWN_W = ROW_W, DROPDOWN_H = ROW_H;
    private static final int FIELD_X = ROW_X, FIELD_Y = 48, FIELD_W = ROW_W, FIELD_H = ROW_H;
    private static final int TOGGLE_X = ROW_X, TOGGLE_Y = 69, TOGGLE_W = ROW_W, TOGGLE_H = ROW_H;

    /** Palette copied from NpcDialogueGraphScreen#drawNode. */
    private static final int COL_BORDER_IDLE = 0xff46576b;
    private static final int COL_BORDER_SELECTED = 0xffe9bd67;
    private static final int COL_BODY = 0xff26313f;
    private static final int COL_HEADER = 0xffc77dff;
    private static final int COL_TITLE = 0xfff0f3f7;
    private static final int COL_PORT_IN = 0xffc4d8ea;

    private static final int COL_ROW_BG = 0xff344452;
    private static final int COL_FIELD_BG = 0xff172330;
    private static final int COL_FIELD_EDIT = 0xff56697a;
    private static final int COL_TOGGLE_OFF = 0xff37424d;
    private static final int COL_TOGGLE_ON = 0xff285b49;
    private static final int COL_ROW_TEXT = 0xfff1d68a;
    private static final int COL_FIELD_TEXT = 0xffffffff;
    private static final int COL_FIELD_EMPTY = 0xff8a9ba8;
    private static final int COL_TOGGLE_TEXT = 0xffd9e3ec;
    private static final int COL_TOGGLE_TEXT_ON = 0xffa7f2ca;

    private static final int TARGET_NPC = 0;
    private static final int TARGET_PLAYER = 1;

    private static final Map<NpcDialogueGraphScreen, NpcUi.NpcDropdown> DROPDOWN_POPUPS = new WeakHashMap<>();
    private static final Map<NpcDialogueGraphScreen, String> FIELD_TEXT = new WeakHashMap<>();
    private static final Map<NpcDialogueGraphScreen, Boolean> FIELD_EDITING = new WeakHashMap<>();
    private static final Map<NpcDialogueGraphScreen, Node> EDITING_NODE = new WeakHashMap<>();

    private PursuitGraphNode272() {}

    public static String actionForMenuRow(int row) {
        return row == PURSUIT_ROW ? "trigger:pursuit:start:npc:" : null;
    }

    public static boolean isPursuitNode(Node node) {
        if (node == null || node.type != NpcDialogueGraph.TRIGGER) return false;
        String action = node.action;
        return action != null && (action.startsWith("trigger:pursuit:") || action.startsWith("trigger:pursuit "));
    }

    /** Node whose target field currently takes keystrokes, or null. */
    public static Node editingNode(NpcDialogueGraphScreen screen) {
        return screen == null ? null : EDITING_NODE.get(screen);
    }

    private static int parseTargetType(String action) {
        if (action == null) return TARGET_NPC;
        if (action.startsWith("trigger:pursuit:start:player:")) return TARGET_PLAYER;
        return TARGET_NPC;
    }

    private static String parseTargetValue(String action) {
        if (action == null) return "";
        if (action.startsWith("trigger:pursuit:start:npc:")) return action.substring(26);
        if (action.startsWith("trigger:pursuit:start:player:")) return action.substring(29);
        return "";
    }

    private static boolean parseStopFlag(String action) {
        return action != null && action.equals("trigger:pursuit:stop");
    }

    private static String buildAction(int targetType, String targetValue, boolean stopFlag) {
        if (stopFlag) return "trigger:pursuit:stop";
        String kind = targetType == TARGET_PLAYER ? "player" : "npc";
        String value = targetValue == null ? "" : targetValue.trim();
        return "trigger:pursuit:start:" + kind + ":" + value;
    }

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
        int y = menu.top + 20 + visible * 22;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 600.0);
        graphics.fill(menu.left + 2, y, menu.left + menu.width - 2, y + 22,
                hovered == PURSUIT_ROW ? 0xff33485a : 0xff1a2027);
        String text = Component.translatable("gui.settledlands.graph.pursuit_menu").getString();
        while (!text.isEmpty() && font.width(text) > menu.width - 18)
            text = text.substring(0, text.length() - 1);
        graphics.drawString(font, text, menu.left + 8, y + 6, 0xfff1d68a, false);
        graphics.pose().popPose();
    }

    public static void drawNode(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || !isPursuitNode(node)) {
            screen.settledlands$drawOriginal(graphics, node);
            return;
        }

        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int targetType = parseTargetType(node.action);
        String targetValue = FIELD_TEXT.getOrDefault(screen, parseTargetValue(node.action));
        boolean stopFlag = parseStopFlag(node.action);
        boolean editing = FIELD_EDITING.getOrDefault(screen, false);

        graphics.pose().pushPose();
        graphics.pose().translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0);
        graphics.pose().scale((float) canvas.zoom, (float) canvas.zoom, 1.0f);

        // Stock node frame.
        graphics.fill(-2, -2, CARD_WIDTH + 2, CARD_HEIGHT + 2,
                screen.settledlands$isSelected(node.id) ? COL_BORDER_SELECTED : COL_BORDER_IDLE);
        graphics.fill(0, 0, CARD_WIDTH, CARD_HEIGHT, COL_BODY);
        graphics.fill(0, 0, CARD_WIDTH, HEADER_H, COL_HEADER);

        String title = Component.translatable("gui.settledlands.graph.pursuit_node").getString();
        while (!title.isEmpty() && font.width(title) > CARD_WIDTH - 16)
            title = title.substring(0, title.length() - 1);
        graphics.drawString(font, title, 8, 7, COL_TITLE, false);

        // Row 1 - target type.
        graphics.fill(DROPDOWN_X, DROPDOWN_Y, DROPDOWN_X + DROPDOWN_W, DROPDOWN_Y + DROPDOWN_H, COL_ROW_BG);
        String dropdownLabel = targetType == TARGET_PLAYER
                ? Component.translatable("gui.settledlands.pursuit.target_player").getString()
                : Component.translatable("gui.settledlands.pursuit.target_npc").getString();
        drawCentered(font, graphics, dropdownLabel + " \u25be",
                DROPDOWN_X + DROPDOWN_W / 2, DROPDOWN_Y + 5, COL_ROW_TEXT);

        // Row 2 - target value.
        graphics.fill(FIELD_X, FIELD_Y, FIELD_X + FIELD_W, FIELD_Y + FIELD_H,
                editing ? COL_FIELD_EDIT : COL_FIELD_BG);
        String displayValue = targetValue.isEmpty()
                ? Component.translatable("gui.settledlands.pursuit.field_empty").getString()
                : targetValue;
        graphics.drawString(font, displayValue, FIELD_X + 5, FIELD_Y + 5,
                targetValue.isEmpty() ? COL_FIELD_EMPTY : COL_FIELD_TEXT, false);

        // Row 3 - stop toggle.
        graphics.fill(TOGGLE_X, TOGGLE_Y, TOGGLE_X + TOGGLE_W, TOGGLE_Y + TOGGLE_H,
                stopFlag ? COL_TOGGLE_ON : COL_TOGGLE_OFF);
        String toggleLabel = Component.translatable("gui.settledlands.pursuit.stop_toggle").getString();
        drawCentered(font, graphics, toggleLabel + (stopFlag ? " \u2713" : ""),
                TOGGLE_X + TOGGLE_W / 2, TOGGLE_Y + 5,
                stopFlag ? COL_TOGGLE_TEXT_ON : COL_TOGGLE_TEXT);

        // Stock port dots.
        int mid = CARD_HEIGHT / 2;
        graphics.fill(-5, mid - 5, 5, mid + 5, COL_PORT_IN);
        graphics.fill(CARD_WIDTH - 5, mid - 5, CARD_WIDTH + 5, mid + 5, COL_HEADER);

        graphics.pose().popPose();
    }

    private static void drawCentered(Font font, GuiGraphics g, String text, int cx, int y, int color) {
        int w = font == null ? text.length() * 6 : font.width(text);
        g.drawString(font, text, cx - w / 2, y, color, false);
    }

    private static boolean handleDropdownPopup(NpcDialogueGraphScreen screen, double x, double y, int button) {
        NpcUi.NpcDropdown popup = DROPDOWN_POPUPS.get(screen);
        if (popup == null || screen.activeDropdown() != popup) return false;
        boolean handled;
        if (button == 0) handled = popup.popupClicked(x, y);
        else {
            popup.closePopup();
            handled = false;
        }
        if (!popup.popupOpen()) {
            screen.openDropdown(null);
            DROPDOWN_POPUPS.remove(screen);
        }
        return handled;
    }

    private static void openDropdownPopup(NpcDialogueGraphScreen screen, Node node, double mouseX, double mouseY) {
        ArrayList<Component> options = new ArrayList<>();
        options.add(Component.translatable("gui.settledlands.pursuit.target_npc"));
        options.add(Component.translatable("gui.settledlands.pursuit.target_player"));
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        int screenX = (int) Math.round(canvas.screenX(node.x + DROPDOWN_X));
        int screenBottom = (int) Math.round(canvas.screenY(node.y + DROPDOWN_Y + DROPDOWN_H));
        int width = (int) Math.round(DROPDOWN_W * canvas.zoom);
        WeakReference<NpcDialogueGraphScreen> screenRef = new WeakReference<>(screen);
        WeakReference<Node> nodeRef = new WeakReference<>(node);
        NpcUi.NpcDropdown popup = NpcUi.dropdown(screenX, screenBottom - 20, width, options,
                parseTargetType(node.action), selected -> {
                    NpcDialogueGraphScreen s = screenRef.get();
                    Node n = nodeRef.get();
                    if (s == null || n == null) return;
                    int oldType = parseTargetType(n.action);
                    if (selected != oldType) {
                        s.settledlands$remember();
                        String value = FIELD_TEXT.getOrDefault(s, parseTargetValue(n.action));
                        boolean stop = parseStopFlag(n.action);
                        n.action = buildAction(selected, value, stop);
                        s.settledlands$changed();
                    }
                });
        DROPDOWN_POPUPS.put(screen, popup);
        popup.onClick(mouseX, mouseY);
        screen.openDropdown(popup);
    }

    public static boolean mouseClicked(NpcDialogueGraphScreen screen, double x, double y, int button, Node node) {
        if (screen == null || node == null || !isPursuitNode(node)) return false;
        if (handleDropdownPopup(screen, x, y, button)) return true;

        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null) return false;
        double nx = canvas.worldX(x) - node.x;
        double ny = canvas.worldY(y) - node.y;

        // Dropdown click
        if (nx >= DROPDOWN_X && nx < DROPDOWN_X + DROPDOWN_W
                && ny >= DROPDOWN_Y && ny < DROPDOWN_Y + DROPDOWN_H && button == 0) {
            FIELD_EDITING.remove(screen);
            EDITING_NODE.remove(screen);
            openDropdownPopup(screen, node, x, y);
            return true;
        }

        // Field click - start editing
        if (nx >= FIELD_X && nx < FIELD_X + FIELD_W
                && ny >= FIELD_Y && ny < FIELD_Y + FIELD_H && button == 0) {
            FIELD_EDITING.put(screen, true);
            EDITING_NODE.put(screen, node);
            return true;
        }

        // Toggle click
        if (nx >= TOGGLE_X && nx < TOGGLE_X + TOGGLE_W
                && ny >= TOGGLE_Y && ny < TOGGLE_Y + TOGGLE_H && button == 0) {
            FIELD_EDITING.remove(screen);
            EDITING_NODE.remove(screen);
            screen.settledlands$remember();
            int type = parseTargetType(node.action);
            String value = FIELD_TEXT.getOrDefault(screen, parseTargetValue(node.action));
            boolean stop = !parseStopFlag(node.action);
            node.action = buildAction(type, value, stop);
            screen.settledlands$changed();
            return true;
        }

        // Anywhere else on the card ends editing.
        FIELD_EDITING.remove(screen);
        EDITING_NODE.remove(screen);
        return false;
    }

    private static Node editingTarget(NpcDialogueGraphScreen screen, Node fallback) {
        Node editing = EDITING_NODE.get(screen);
        return editing != null ? editing : fallback;
    }

    public static boolean charTyped(NpcDialogueGraphScreen screen, char ch, int modifiers, Node node) {
        if (screen == null) return false;
        Node target = editingTarget(screen, node);
        if (!isPursuitNode(target)) return false;
        if (!FIELD_EDITING.getOrDefault(screen, false)) return false;
        String current = FIELD_TEXT.getOrDefault(screen, parseTargetValue(target.action));
        if (ch >= 32 && ch < 127) {
            screen.settledlands$remember();
            String updated = current + ch;
            FIELD_TEXT.put(screen, updated);
            int type = parseTargetType(target.action);
            boolean stop = parseStopFlag(target.action);
            target.action = buildAction(type, updated, stop);
            screen.settledlands$changed();
            return true;
        }
        return false;
    }

    public static boolean keyPressed(NpcDialogueGraphScreen screen, int keyCode, int scanCode, int modifiers, Node node) {
        if (screen == null) return false;
        Node target = editingTarget(screen, node);
        if (!isPursuitNode(target)) return false;
        if (!FIELD_EDITING.getOrDefault(screen, false)) return false;
        String current = FIELD_TEXT.getOrDefault(screen, parseTargetValue(target.action));
        if (keyCode == 259 && !current.isEmpty()) { // Backspace
            screen.settledlands$remember();
            String updated = current.substring(0, current.length() - 1);
            FIELD_TEXT.put(screen, updated);
            int type = parseTargetType(target.action);
            boolean stop = parseStopFlag(target.action);
            target.action = buildAction(type, updated, stop);
            screen.settledlands$changed();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            FIELD_EDITING.remove(screen);
            EDITING_NODE.remove(screen);
            return true;
        }
        return false;
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

    /** Kept for compatibility; build 275 lets NpcGraphCanvas#hitPort report pursuit ports. */
    public static int hitPort(NpcGraphCanvas c, NpcDialogueGraph graph, double x, double y, boolean output) {
        return c.settledlands$hitPortOriginal(graph, x, y, output);
    }

    public static void focus(NpcGraphCanvas c, Node n) {
        if (n == null || !isPursuitNode(n)) return;
        c.panX = (c.right - c.left) / 2.0 - (n.x + CARD_WIDTH / 2.0) * c.zoom;
        c.panY = (c.bottom - c.top) / 2.0 - (n.y + CARD_HEIGHT / 2.0) * c.zoom;
    }
}
