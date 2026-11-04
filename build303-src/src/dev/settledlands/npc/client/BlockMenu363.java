package dev.settledlands.npc.client;

import dev.settledlands.npc.BlockTrigger351;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 363: the right-click menu of a block's graph offers only the nodes a block can run.
 *
 * <p>The stock menu is one panel with sixteen rows, one per node type of the editor. Build 351 made a
 * block's editor <i>refuse</i> the rows it cannot run - the click closed the menu and wrote a line into
 * the status bar - but the rows were still there, so the only way to learn what a block can do was to
 * try every row and read the answers. The author asked for the list itself to be the answer.</p>
 *
 * <p>What a block can run is not decided here: {@link BlockTrigger351#typeForRow(int)} already is that
 * decision, and build 351's palette and the in-game note both come from it. This class asks it once,
 * caches the answer, and the menu becomes those rows in the stock order - <b>Реплика, Триггер, Условие
 * If, Переменная, В радиусе, Случайность</b>. Nothing is added to the map here, so a node type that
 * becomes available later appears in the menu without a second edit.</p>
 *
 * <p>The panel is drawn by this class because the stock one is a hard-coded switch over sixteen row
 * numbers: there is no list to cut, and a row index cannot be re-pointed from outside. The colours,
 * the header, the row height and the hover fill are the stock ones, so a block's menu looks like the
 * menu of an NPC - it is the same panel with the rows a block cannot use simply absent.</p>
 *
 * <p>One menu is open at a time, so the geometry of the open one is kept in fields: the panel is
 * measured once when the menu appears ({@link #fit}) and painted from those numbers, which keeps the
 * frame path free of lookups. A menu that was never measured is not painted by this class at all -
 * the stock panel is the fallback, so an unforeseen path can cost the filtering, never the menu.</p>
 */
public final class BlockMenu363 {

    /** The header of the stock create menu, drawn the same way. */
    private static final String HEADER_KEY = "gui.settledlands.graph.create";

    /** One label per stock row, in the order the stock panel draws them. */
    private static final String[] LABEL_KEYS = {
        "gui.settledlands.graph.add_line",
        "gui.settledlands.graph.add_button",
        "gui.settledlands.graph.add_trigger",
        "gui.settledlands.graph.add_if",
        "gui.settledlands.graph.add_variable",
        "gui.settledlands.graph.add_check",
        "gui.settledlands.graph.add_damage",
        "gui.settledlands.graph.add_time",
        "gui.settledlands.graph.add_timer",
        "gui.settledlands.graph.add_anim",
        "gui.settledlands.graph.add_quest",
        "gui.settledlands.graph.add_move",
        "gui.settledlands.graph.add_mobility",
        "gui.settledlands.graph.add_random",
        "gui.settledlands.graph.add_cutscene",
        "gui.settledlands.graph.add_radius",
    };

    /** Geometry of the stock panel: header, row, and the two pixels of padding at the bottom. */
    private static final int HEADER_H = 20;
    private static final int ROW_H = 22;
    private static final int PAD = 4;

    /** Colours of the stock panel, so a block's menu is the same panel with fewer rows. */
    private static final int BODY = -15656669;
    private static final int EDGE = -8415050;
    private static final int HEADER = -5588790;
    private static final int LABEL = -1852043;
    private static final int HOVER_BG = -13351331;
    private static final int HOVER_TEXT = -1;

    /** The stock rows a block may create, computed once from the block's own palette. */
    private static int[] rows;

    /** Where the open panel is, measured when it appeared. */
    private static int left;
    private static int top;
    private static int width;
    private static int height;
    private static boolean ready;

    /** Build 365: the rows of the panel that fit, and the first one painted. */
    private static int visible;
    private static int firstRow;

    private BlockMenu363() {}

    /** Stock row numbers a block can create - the rows whose type {@link BlockTrigger351} allows. */
    public static int[] rows() {
        if (rows == null) {
            int[] kept = new int[LABEL_KEYS.length];
            int count = 0;
            for (int row = 0; row < LABEL_KEYS.length; row++) {
                if (BlockTrigger351.typeForRow(row) >= 0) kept[count++] = row;
            }
            rows = java.util.Arrays.copyOf(kept, count);
        }
        return rows;
    }

    /** How many rows a block's menu has. */
    public static int count() {
        return rows().length;
    }

    /** Row of this panel -> the stock row behind it, or -1 when there is none. */
    public static int stockRow(int compact) {
        int[] kept = rows();
        return compact >= 0 && compact < kept.length ? kept[compact] : -1;
    }

    /** The stock label of a row of this panel. */
    public static String label(int compact) {
        int stock = stockRow(compact);
        return stock < 0 ? "" : Component.translatable(LABEL_KEYS[stock]).getString();
    }

    /** True once the open panel has been measured and may be painted. */
    public static boolean ready() {
        return ready;
    }

    /** Forget the measurement: the next menu measures itself. */
    public static void reset() {
        ready = false;
        height = 0;
    }

    /**
     * Measure the menu that has just opened for a block: the panel is made as tall as its rows and no
     * taller than the canvas, the scroll is put back to the first row, and the geometry is kept for
     * the frame path. A copy/delete menu of an existing node is not touched - that one is stock.
     */
    public static void fit(NpcGraphContextMenu menu, NpcGraphCanvas canvas) {
        if (menu == null || canvas == null || !menu.open || menu.targetNodeId >= 0) {
            reset();
            return;
        }
        int available = Math.max(HEADER_H + ROW_H, canvas.bottom - canvas.top);
        int wanted = Math.min(HEADER_H + count() * ROW_H + PAD, available);
        menu.height = wanted;
        menu.scrollRow = 0;
        visible = Math.max(1, (wanted - HEADER_H) / ROW_H);
        firstRow = 0;
        if (menu.top + wanted > canvas.bottom) {
            menu.top = Math.max(canvas.top, canvas.bottom - wanted);
        }
        left = menu.left;
        top = menu.top;
        width = menu.width;
        height = wanted;
        ready = true;
    }

    /** Paint the open panel; {@code hoverRow} is the row of this panel under the cursor, or -1. */
    public static void draw(GuiGraphics graphics, Font font, int hoverRow) {
        if (!ready || graphics == null || font == null) return;
        int x0 = left;
        int y0 = top;
        int x1 = left + width;
        int y1 = top + height;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        try {
            graphics.fill(x0, y0, x1, y1, BODY);
            graphics.fill(x0, y0, x1, y0 + 1, EDGE);
            graphics.fill(x0, y1 - 1, x1, y1, EDGE);
            graphics.fill(x0, y0, x0 + 1, y1, EDGE);
            graphics.fill(x1 - 1, y0, x1, y1, EDGE);
            graphics.drawString(font, Component.translatable(HEADER_KEY).getString(), x0 + 8, y0 + 6,
                    HEADER, false);
            int first = Math.max(0, firstRow);
            for (int drawn = 0; drawn < visible; drawn++) {
                int row = first + drawn;
                if (row >= count()) break;
                int rowY = y0 + HEADER_H + drawn * ROW_H;
                if (rowY + ROW_H > y1) break;
                if (row == hoverRow) {
                    graphics.fill(x0 + 2, rowY, x1 - 2, rowY + ROW_H, HOVER_BG);
                }
                graphics.drawString(font, label(row), x0 + 8, rowY + 7,
                        row == hoverRow ? HOVER_TEXT : LABEL, false);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    /** How many rows fit in the measured panel - build 365 has more rows than fit. */
    public static int visible() {
        return visible;
    }

    /** True when every row of the panel fits: nothing to scroll, so the wheel stays cancelled. */
    public static boolean fits() {
        return ready && visible >= count();
    }

    /** The first painted row of the open panel. */
    public static int scrollRow() {
        return firstRow;
    }

    /**
     * One wheel notch over an open block panel. Before build 365 six rows always fit and the wheel was
     * simply cancelled; now the panel may hold eleven, so the notch moves the window and the stock
     * scroller never sees it - the paint and the cursor stay on the same row.
     *
     * @return true when the wheel was used by the panel
     */
    public static boolean wheel(NpcGraphContextMenu menu, double scrollY) {
        if (menu == null || !menu.open || menu.targetNodeId >= 0 || !ready) return false;
        if (fits()) return true;
        int max = Math.max(0, count() - visible);
        int step = scrollY > 0.0D ? 1 : scrollY < 0.0D ? -1 : 0;
        menu.scrollRow = Math.max(0, Math.min(max, menu.scrollRow + step));
        firstRow = menu.scrollRow;
        return true;
    }

    /** The row of this panel under the cursor, or -1. */
    public static int hoverRow(NpcGraphContextMenu menu, double mouseX, double mouseY) {
        if (menu == null || !ready) return -1;
        int row = menu.rowAt(mouseX, mouseY);
        return row >= 0 && row < count() ? row : -1;
    }
}
