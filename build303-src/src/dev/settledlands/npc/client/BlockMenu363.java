package dev.settledlands.npc.client;

import dev.settledlands.npc.BlockTrigger351;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 363/364: the right-click menu of a block's graph carries the rows a block can run, painted as
 * the stock panel.
 *
 * <p>Build 366: the panel is the <b>stock panel again</b> - five rows and a scrollbar, not a taller
 * list. Build 365 grew the panel to eleven rows to fit the new nodes and broke four things at once:
 * the stock row hit test only knows the five rows of its own window, so every click below the fifth
 * row was dropped; the panel no longer fitted the screen; there was no scrollbar; and the rows lost
 * the colour each node type has in the stock menu. The fix is the one the author asked for - take the
 * existing dropdown list as the model:</p>
 *
 * <ul>
 *   <li><b>size</b> - the stock height, {@code 20 + 5 x 22 + 4 = 134}, cut to the canvas;</li>
 *   <li><b>colours</b> - the sixteen colours of the stock painter, one per node type, so a block's
 *       rows look exactly like the rows of the same nodes in an NPC graph;</li>
 *   <li><b>scroll</b> - a window of five rows and the dropdown's own arithmetic: the wheel moves
 *       {@code firstRow} between 0 and {@code count - 5}, and the scrollbar on the right shows where
 *       the window is and takes a click;</li>
 *   <li><b>hit test</b> - {@link #rowAt} counts from the window, not from the stock's five-row map, so
 *       every row of the block can be clicked once it is scrolled into view.</li>
 * </ul>
 *
 * <p>The copy/delete menu of an existing node is not touched: it never reaches this class.</p>
 */
public final class BlockMenu363 {

    /** Header «Создать узел». */
    private static final String HEADER_KEY = "gui.settledlands.graph.create";

    /** The sixteen stock rows, in the order the stock painter uses. */
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

    /**
     * The colour of each stock row, copied from the painter of the stock menu - the same colour the
     * node has when it stands in the graph. Build 365 painted every row grey, which is the "no colour
     * on the buttons" the author saw.
     */
    private static final int[] ROW_COLOUR = {
        -7550482,   // 0 Реплика
        -1852043,   // 1 Кнопка
        -2974723,   // 2 Триггер
        -13058568,  // 3 Условие If
        -290244,    // 4 Переменная
        -13315175,  // 5 В радиусе
        -770210,    // 6 Урон
        -278748,    // 7 Время
        -5796870,   // 8 Таймер
        -1292135,   // 9 Анимация
        -680437,    // 10 Задание
        -8286984,   // 11 Передвижение
        -13773633,  // 12 Подвижность
        -5745161,   // 13 Случайность
        -1004548,   // 14 Катсцена
        -8467457,   // 15 Цель
    };

    private static final int HEADER_H = 20;
    private static final int ROW_H = 22;
    private static final int PAD = 4;
    /** Rows that fit in the stock panel - the window the stock hit test knows. */
    private static final int VISIBLE = 5;
    /** The height of the stock panel, scrollbar included. */
    private static final int PANEL_H = HEADER_H + VISIBLE * ROW_H + PAD;

    private static final int BODY = -15656669;
    private static final int EDGE = -8415050;
    private static final int HEADER = -5588790;
    private static final int LABEL = -1852043;
    private static final int HOVER_BG = -13351331;
    private static final int HOVER_TEXT = -1;
    private static final int STRIP_BG = -14800581;
    private static final int THUMB = -10193781;
    private static final int THUMB_HOT = -7035976;

    /** The stock rows a block may create, computed once from the block's own palette. */
    private static int[] rows;

    /** Where the open panel is, measured when it appeared. */
    private static int left;
    private static int top;
    private static int width;
    private static int height;
    private static boolean ready;

    /** Build 366: the rows of the window that fit, and the first one painted. */
    private static int visible;
    private static int firstRow;
    /** The menu this geometry belongs to: a second measurement of the same open menu keeps the window. */
    private static NpcGraphContextMenu measured;

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

    /** The stock colour of a row of this panel. */
    public static int colour(int compact) {
        int stock = stockRow(compact);
        return stock >= 0 && stock < ROW_COLOUR.length ? ROW_COLOUR[stock] : LABEL;
    }

    /** True once the open panel has been measured and may be painted. */
    public static boolean ready() {
        return ready;
    }

    /** Forget the measurement: the next menu measures itself. */
    public static void reset() {
        ready = false;
        height = 0;
        firstRow = 0;
        measured = null;
    }

    /**
     * Measure the menu that has just opened for a block. The panel is the stock one - five rows and
     * the scrollbar, never taller - and the window starts at the first row. A copy/delete menu of an
     * existing node is not touched: that one is stock.
     */
    public static void fit(NpcGraphContextMenu menu, NpcGraphCanvas canvas) {
        if (menu == null || canvas == null || !menu.open || menu.targetNodeId >= 0) {
            reset();
            return;
        }
        int available = Math.max(HEADER_H + ROW_H, canvas.bottom - canvas.top);
        int wanted = Math.min(PANEL_H, available);
        boolean same = measured == menu;
        menu.height = wanted;
        visible = Math.max(1, (wanted - HEADER_H) / ROW_H);
        int max = Math.max(0, count() - visible);
        firstRow = same ? Math.max(0, Math.min(max, firstRow)) : 0;
        menu.scrollRow = firstRow;
        measured = menu;
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
        int textRight = x1 - 6 - 4;
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
            for (int drawn = 0; drawn < visible; drawn++) {
                int row = firstRow + drawn;
                if (row >= count()) break;
                int rowY = y0 + HEADER_H + drawn * ROW_H;
                if (rowY + ROW_H > y1) break;
                if (row == hoverRow) {
                    graphics.fill(x0 + 2, rowY, textRight, rowY + ROW_H, HOVER_BG);
                    graphics.fill(x0 + 2, rowY, x0 + 2, rowY, HOVER_BG);
                }
                String text = clip(font, label(row), textRight - (x0 + 8));
                graphics.drawString(font, text, x0 + 8, rowY + 7,
                        row == hoverRow ? HOVER_TEXT : colour(row), false);
            }
            strip(graphics, hoverRow >= 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    /** The scrollbar on the right, drawn exactly as the stock menu draws it. */
    private static void strip(GuiGraphics graphics, boolean hot) {
        if (count() <= visible) return;
        int sx = left + width - 6 - 2;
        int sy = top + HEADER_H + 2;
        int sh = height - HEADER_H - 4;
        if (sh <= 0) return;
        graphics.fill(sx, sy, sx + 6, sy + sh, STRIP_BG);
        int thumb = Math.max(16, sh * visible / count());
        int maxRow = Math.max(1, count() - visible);
        int ty = sy + (sh - thumb) * firstRow / maxRow;
        graphics.fill(sx, ty, sx + 6, ty + thumb, hot ? THUMB_HOT : THUMB);
    }

    /** How many rows fit in the measured panel. */
    public static int visible() {
        return visible;
    }

    /** True when every row fits: nothing to scroll. */
    public static boolean fits() {
        return ready && count() <= visible;
    }

    /** The first painted row of the open panel. */
    public static int scrollRow() {
        return firstRow;
    }

    /**
     * One wheel notch over an open block panel, the way the stock dropdown scrolls: one row, between
     * the first and the last window that still shows a row.
     *
     * @return true when the wheel belongs to the panel
     */
    public static boolean wheel(NpcGraphContextMenu menu, double scrollY) {
        if (menu == null || !menu.open || menu.targetNodeId >= 0 || !ready) return false;
        if (fits()) return true;
        scroll(menu, wheelStep(scrollY));
        return true;
    }

    /**
     * The direction one wheel notch moves the window, in rows. This is the <b>stock</b> convention -
     * the stock screen passes its wheel on as {@code scrollY > 0 ? -1 : +1} - and build 366 had it
     * the other way round, so the notch scrolled the list against the hand and the scrollbar read as
     * inverted. Pure and static, so a probe can check the sign without a screen.
     */
    public static int wheelStep(double scrollY) {
        return scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
    }

    /** Move the window by whole rows, the one place that clamps it. */
    public static void scroll(NpcGraphContextMenu menu, int step) {
        if (menu == null || !ready || step == 0) return;
        int max = Math.max(0, count() - visible);
        menu.scrollRow = Math.max(0, Math.min(max, menu.scrollRow + step));
        firstRow = menu.scrollRow;
    }

    /** True when the point is on the scrollbar of the open panel. */
    public static boolean stripHit(NpcGraphContextMenu menu, double mouseX, double mouseY) {
        if (menu == null || !menu.open || menu.targetNodeId >= 0 || !ready) return false;
        if (count() <= visible) return false;
        int sx = left + width - 6 - 2;
        return mouseX >= sx && mouseX <= sx + 6 && mouseY >= top + HEADER_H
                && mouseY <= top + height;
    }

    /**
     * A click on the scrollbar: the upper half turns the window back, the lower half forward. One row
     * per click - the same stride as a wheel notch, so the panel never jumps under the cursor.
     *
     * @return true when the click was the scrollbar
     */
    public static boolean stripClick(NpcGraphContextMenu menu, double mouseY) {
        if (menu == null || !ready) return false;
        // Above the middle of the strip is "towards the first row", below it "towards the last" -
        // the same direction as the thumb travels.
        int middle = top + HEADER_H + (height - HEADER_H) / 2;
        scroll(menu, mouseY < middle ? -1 : 1);
        return true;
    }

    /**
     * The row of this panel under the cursor, or -1. Counted from the window ({@code firstRow}) and
     * not from the stock hit test, which only knows the five rows of its own map and answered -1 for
     * everything below the fifth row - the click bug the author hit in build 365.
     */
    public static int rowAt(NpcGraphContextMenu menu, double mouseX, double mouseY) {
        if (menu == null || !ready) return -1;
        if (mouseX < left || mouseX > left + width) return -1;
        if (mouseY < top + HEADER_H || mouseY >= top + height) return -1;
        if (stripHit(menu, mouseX, mouseY)) return -1;
        int local = (int) ((mouseY - top - HEADER_H) / ROW_H);
        if (local < 0 || local >= visible) return -1;
        int row = firstRow + local;
        return row < count() ? row : -1;
    }

    /** The row of this panel under the cursor, or -1. */
    public static int hoverRow(NpcGraphContextMenu menu, double mouseX, double mouseY) {
        return rowAt(menu, mouseX, mouseY);
    }

    /** A label cut to the width of the panel, so a long one never runs over the edge. */
    private static String clip(Font font, String text, int room) {
        if (text == null) return "";
        if (room <= 0 || font.width(text) <= room) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
