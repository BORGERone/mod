package dev.settledlands.npc.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Build 305: the drawing layer of the node workspace.
 *
 * <p>Adapted from the Advanced Contraption Controller of Gadgets &amp; Gizmos (drawing
 * primitives and the default palette), used here with the authors' permission. It is plain
 * painting only - rectangles, circles, diamonds, frames, panels - and it knows nothing about
 * either mod's graph.</p>
 *
 * <p>Everything is drawn in screen pixels; callers pass the rectangle they already computed.
 * The rounded shapes are built from one-pixel rows, so a frame costs a few dozen fills, not a
 * few hundred.</p>
 */
public final class WorkspaceTheme305 {

    /* ---- palette: the default theme of the Gadgets & Gizmos graph editor ---- */
    public static final int CANVAS = 0xff0d1215;
    public static final int CANVAS_DOT = 0xff1b2429;
    public static final int TITLE_BAR = 0xff192126;
    public static final int PANEL = 0xff11181b;
    public static final int PANEL_RAISED = 0xff202a2f;
    public static final int PANEL_HOVERED = 0xff2b353a;
    public static final int PANEL_SELECTED = 0xff28271f;
    public static final int BORDER = 0xff39464c;
    public static final int BORDER_STRONG = 0xff59666b;
    public static final int BORDER_SOFT = 0xff2c373d;
    public static final int PRIMARY = 0xffe8e0cd;
    public static final int SECONDARY = 0xffc7c0b2;
    public static final int MUTED = 0xff918c82;
    public static final int ACCENT = 0xffc6873f;
    public static final int ACCENT_LIGHT = 0xffe2aa63;
    public static final int ACCENT_DARK = 0xff704723;
    public static final int ACCENT_OVERLAY = 0x66704723;
    public static final int PRIMARY_ACTION_TEXT = 0xfffff3dd;
    public static final int DANGER = 0xffbd6559;

    /** Ordinary cables on the dark canvas. */
    public static final int CABLE = 0xffc7c0b2;

    private WorkspaceTheme305() {
    }

    /* ---------------- panels ---------------- */

    public static void drawTitleBar(GuiGraphics graphics, int x, int y, int width, int height) {
        fill(graphics, x, y, x + width, y + height, TITLE_BAR);
        fill(graphics, x, y + height - 1, x + width, y + height, BORDER);
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawRoundedRect(graphics, x, y, width, height, 5, BORDER_STRONG);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 4, PANEL_RAISED);
    }

    /* ---------------- nodes ---------------- */

    /**
     * A card frame: rounded border, raised body, a header tinted towards the node's accent and
     * an accent dot at the left of the header. {@code titleHeight} is the header's height.
     */
    public static void drawNodeFrame(GuiGraphics graphics, int x, int y, int width, int height,
            int titleHeight, int accentColor, boolean selected) {
        int border = selected ? ACCENT : BORDER;
        if (selected) {
            drawRoundedRect(graphics, x - 1, y - 1, width + 2, height + 2, 7,
                    withAlpha(ACCENT, 0x66));
        }
        drawRoundedRect(graphics, x, y, width, height, 6, border);

        int headerBottom = Math.min(y + height - 1, y + Math.max(1, titleHeight));
        int headerColor = mix(PANEL_RAISED, accentColor, 0.14D);
        drawTopRoundedRect(graphics, x + 1, y + 1, width - 2,
                Math.max(1, headerBottom - y - 1), 5, headerColor);
        if (headerBottom < y + height - 1) {
            fill(graphics, x + 1, headerBottom - 1, x + width - 1, headerBottom, BORDER_SOFT);
        }
        int indicator = Math.min(6, Math.max(3, titleHeight / 4));
        if (width > 24) {
            drawCircle(graphics, x + 12, y + Math.max(7, titleHeight / 2), indicator / 2,
                    (accentColor & 0x00ffffff) | 0x55000000);
            drawCircle(graphics, x + 12, y + Math.max(7, titleHeight / 2),
                    Math.max(1, indicator / 2 - 1), accentColor);
        }
    }

    /** A round data port: soft rim, solid body, a light core. */
    public static void drawPort(GuiGraphics graphics, int centerX, int centerY, int col) {
        drawCircle(graphics, centerX, centerY, 5, (col & 0x00ffffff) | 0x33000000);
        drawCircle(graphics, centerX, centerY, 4, col);
        drawCircle(graphics, centerX, centerY, 2, mix(col, 0xffffffff, 0.18D));
    }

    /** An execution port: a hollow diamond. */
    public static void drawExecPort(GuiGraphics graphics, int centerX, int centerY, int size) {
        int radius = Math.max(3, size / 2);
        drawDiamond(graphics, centerX, centerY, radius, PRIMARY);
        drawDiamond(graphics, centerX, centerY, Math.max(1, radius - 2), CANVAS);
    }

    /* ---------------- list rows ---------------- */

    public static void drawRow(GuiGraphics graphics, int x, int y, int width, int height,
            int accentColor, boolean hovered, boolean accent) {
        int border = hovered ? ACCENT : BORDER_SOFT;
        drawRoundedRect(graphics, x, y, width, height, 3, border);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 2,
                hovered ? PANEL_SELECTED : PANEL_RAISED);
        if (accent && height > 4) {
            fill(graphics, x + 1, y + 2, x + 3, y + height - 2, accentColor);
        }
    }

    /* ---------------- primitives ---------------- */

    public static void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (graphics == null || x2 <= x1 || y2 <= y1) return;
        graphics.fill(x1, y1, x2, y2, color);
    }

    /** A filled rounded rectangle, one row at a time. */
    public static void drawRoundedRect(GuiGraphics graphics, int x, int y, int width, int height,
            int radius, int color) {
        if (graphics == null || width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            fill(graphics, x, y, x + width, y + height, color);
            return;
        }
        for (int row = 0; row < height; row++) {
            int inset = cornerInset(row, height, r);
            fill(graphics, x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    /** A rounded rectangle whose bottom corners are square - a card header. */
    public static void drawTopRoundedRect(GuiGraphics graphics, int x, int y, int width,
            int height, int radius, int color) {
        if (graphics == null || width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            fill(graphics, x, y, x + width, y + height, color);
            return;
        }
        for (int row = 0; row < height; row++) {
            int inset = row < r ? cornerInset(row, r * 2, r) : 0;
            fill(graphics, x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int cornerInset(int row, int height, int radius) {
        if (row < radius) {
            int v = radius - row - 1;
            return radius - (int) Math.round(Math.sqrt((double) (radius * radius - v * v)));
        }
        if (row >= height - radius) {
            int v = row - (height - radius);
            return radius - (int) Math.round(Math.sqrt((double) (radius * radius - v * v)));
        }
        return 0;
    }

    public static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (graphics == null || radius <= 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt((double) (radius * radius - dy * dy));
            fill(graphics, cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    public static void drawDiamond(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (graphics == null || radius <= 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            fill(graphics, cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /* ---------------- colour ---------------- */

    public static int mix(int first, int second, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        int a = (int) Math.round(((first >>> 24) & 0xff) * (1.0D - clamped)
                + ((second >>> 24) & 0xff) * clamped);
        int r = (int) Math.round(((first >>> 16) & 0xff) * (1.0D - clamped)
                + ((second >>> 16) & 0xff) * clamped);
        int g = (int) Math.round(((first >>> 8) & 0xff) * (1.0D - clamped)
                + ((second >>> 8) & 0xff) * clamped);
        int b = (int) Math.round((first & 0xff) * (1.0D - clamped)
                + (second & 0xff) * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((alpha & 0xff) << 24);
    }
}
