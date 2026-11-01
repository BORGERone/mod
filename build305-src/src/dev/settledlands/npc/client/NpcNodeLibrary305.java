package dev.settledlands.npc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 305: the node library - the list of node types with a search field, down the left side
 * of the canvas.
 *
 * <p>This is the part of the Gadgets &amp; Gizmos workspace that changes the way the editor is
 * used: a node is created from a searchable library instead of a fixed row of buttons. The
 * panel is our own code on top of the ported drawing layer, because the nodes behind it are
 * ours - an entry knows an {@code NpcDialogueGraph} type and nothing else.</p>
 *
 * <p>The panel only paints and keeps the search text. The screen keeps the authority: a click
 * that picks an entry only reports the type, and the mixin calls the screen's own
 * {@code add(int)}, so a node is created exactly the way the stock buttons create it - undo
 * snapshot, selection, dirty flag and all.</p>
 */
public final class NpcNodeLibrary305 {

    /** Node type, translation key, latin alias for searching. */
    private static final Object[][] ENTRIES = {
            {0, "gui.settledlands.graph.line", "line reply"},
            {1, "gui.settledlands.graph.button", "button answer"},
            {2, "gui.settledlands.graph.trigger", "trigger action"},
            {3, "gui.settledlands.graph.if", "if condition"},
            {4, "gui.settledlands.graph.variable", "variable"},
            {5, "gui.settledlands.graph.check", "radius check area"},
            {6, "gui.settledlands.graph.damage", "damage"},
            {7, "gui.settledlands.graph.time", "time day"},
            {8, "gui.settledlands.graph.timer", "timer"},
            {9, "gui.settledlands.graph.anim", "animation anim"},
            {10, "gui.settledlands.graph.quest", "quest"},
            {11, "gui.settledlands.graph.move", "move"},
            {12, "gui.settledlands.graph.mobility", "mobility"},
            {13, "gui.settledlands.graph.random", "random"},
            {14, "gui.settledlands.graph.cutscene", "cutscene"},
    };

    private static final int WIDTH = 132;
    private static final int ROW = 14;
    private static final int PAD = 5;
    private static final int HEADER = 14;
    private static final int SEARCH = 14;

    private static boolean open = true;
    private static String query = "";
    private static int scroll;
    private static int hovered = -1;
    private static boolean searchFocused;
    private static int mouseX = Integer.MIN_VALUE;
    private static int mouseY = Integer.MIN_VALUE;

    private NpcNodeLibrary305() {
    }

    /* ---------------- state ---------------- */

    public static boolean isOpen() {
        return open;
    }

    public static void toggle() {
        open = !open;
        if (!open) searchFocused = false;
    }

    public static String query() {
        return query;
    }

    public static void setQuery(String value) {
        query = value == null ? "" : value;
        scroll = 0;
    }

    public static boolean isSearchFocused() {
        return searchFocused;
    }

    public static void setSearchFocused(boolean focused) {
        searchFocused = focused;
    }

    public static void setMouse(double x, double y) {
        mouseX = (int) x;
        mouseY = (int) y;
    }

    private static List<Object[]> filtered() {
        List<Object[]> result = new ArrayList<>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (Object[] entry : ENTRIES) {
            String title = title(entry).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || title.contains(needle)
                    || ((String) entry[2]).contains(needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static String title(Object[] entry) {
        return Component.translatable((String) entry[1]).getString();
    }

    /* ---------------- geometry ---------------- */

    private static int panelX() {
        return 6;
    }

    private static int panelY(int canvasTop) {
        return canvasTop + 6;
    }

    private static int panelHeight(int bottom, int canvasTop) {
        return Math.max(70, bottom - panelY(canvasTop) - 8);
    }

    /** The search field's rectangle, so the screen can put a real text box there. */
    public static int searchX() {
        return panelX() + PAD;
    }

    public static int searchY(int canvasTop) {
        return panelY(canvasTop) + PAD + HEADER;
    }

    public static int searchWidth() {
        return WIDTH - PAD * 2;
    }

    public static int searchHeight() {
        return SEARCH;
    }

    private static int listTop(int canvasTop) {
        return searchY(canvasTop) + SEARCH + 4;
    }

    private static int visibleRows(int bottom, int canvasTop) {
        return Math.max(1, (panelHeight(bottom, canvasTop) - HEADER - SEARCH - PAD * 2 - 6)
                / ROW);
    }

    /* ---------------- drawing ---------------- */

    public static void draw(GuiGraphics graphics, Font font, int bottom, int canvasTop) {
        if (graphics == null || font == null || !open) return;
        int x = panelX();
        int y = panelY(canvasTop);
        int height = panelHeight(bottom, canvasTop);

        WorkspaceTheme305.drawPanel(graphics, x, y, WIDTH, height);

        graphics.drawString(font, Component.translatable("gui.settledlands.workspace.library")
                .getString(), x + PAD, y + PAD, WorkspaceTheme305.PRIMARY, false);
        List<Object[]> entries = filtered();
        String count = String.valueOf(entries.size());
        graphics.drawString(font, count, x + WIDTH - PAD - font.width(count), y + PAD,
                WorkspaceTheme305.MUTED, false);

        // The text of the search box; the box itself is a real EditBox placed on top of it.
        int searchY = searchY(canvasTop);
        if (!searchFocused || query.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.settledlands.workspace.search")
                            .getString(), searchX() + 4, searchY + 3, WorkspaceTheme305.MUTED,
                    false);
        }

        int top = listTop(canvasTop);
        int rows = visibleRows(bottom, canvasTop);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - rows)));
        hovered = -1;
        for (int row = 0; row < rows && scroll + row < entries.size(); row++) {
            int index = scroll + row;
            Object[] entry = entries.get(index);
            int rowY = top + row * ROW;
            boolean hover = rowAt(rowY);
            if (hover) hovered = index;
            WorkspaceTheme305.drawRow(graphics, x + PAD, rowY, WIDTH - PAD * 2, ROW - 1,
                    WorkspaceTheme305.ACCENT, hover, hover);
            graphics.drawString(font,
                    clip(font, title(entry), WIDTH - PAD * 2 - 12),
                    x + PAD + 6, rowY + 3,
                    hover ? WorkspaceTheme305.PRIMARY_ACTION_TEXT : WorkspaceTheme305.SECONDARY,
                    false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.settledlands.workspace.empty")
                    .getString(), x + PAD + 4, top + 3, WorkspaceTheme305.MUTED, false);
        }
    }

    /** The longest prefix of {@code text} that fits into {@code width} pixels. */
    private static String clip(Font font, String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        String result = text;
        while (!result.isEmpty() && font.width(result + "\u2026") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "\u2026";
    }

    private static boolean rowAt(int rowY) {
        return mouseX >= panelX() && mouseX <= panelX() + WIDTH
                && mouseY >= rowY && mouseY < rowY + ROW;
    }

    /* ---------------- input ---------------- */

    public static boolean contains(double mx, double my, int bottom, int canvasTop) {
        if (!open) return false;
        return mx >= panelX() && mx <= panelX() + WIDTH
                && my >= panelY(canvasTop)
                && my <= panelY(canvasTop) + panelHeight(bottom, canvasTop);
    }

    public static boolean inSearchField(double mx, double my, int canvasTop) {
        return mx >= searchX() && mx <= searchX() + searchWidth()
                && my >= searchY(canvasTop) && my <= searchY(canvasTop) + searchHeight();
    }

    /** The node type under the pointer, or -1. */
    public static int entryAt(double mx, double my, int bottom, int canvasTop) {
        if (!open) return -1;
        mouseX = (int) mx;
        mouseY = (int) my;
        List<Object[]> entries = filtered();
        int top = listTop(canvasTop);
        int rows = visibleRows(bottom, canvasTop);
        for (int row = 0; row < rows && scroll + row < entries.size(); row++) {
            int rowY = top + row * ROW;
            if (my >= rowY && my < rowY + ROW) {
                return (Integer) entries.get(scroll + row)[0];
            }
        }
        return -1;
    }

    public static boolean scroll(double mx, double my, double delta, int bottom,
            int canvasTop) {
        if (!contains(mx, my, bottom, canvasTop)) return false;
        scroll -= (int) Math.signum(delta);
        return true;
    }
}
