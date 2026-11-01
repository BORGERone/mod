package dev.settledlands.npc.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Build 304: the node workspace, painted the way the Advanced Control Computer in
 * "Create Aeronautics: Gadgets &amp; Gizmos" paints its graph.
 *
 * <p>The colours and the measurements are taken from that editor's own default theme: a very
 * dark canvas with a dot grid, plain card bodies, a one pixel frame, a warm amber accent and
 * round ports with a dark rim. Nothing here touches the graph, the hit boxes or the text - it
 * only paints over what the screen has already drawn:</p>
 *
 * <ul>
 *   <li>the canvas background and its dots replace the old full-screen line grid
 *       ({@code grid});</li>
 *   <li>every card gets a frame, an accent bar along its top edge and round ports instead of
 *       the coloured 10x10 squares, drawn after the stock card so the text stays readable;</li>
 *   <li>cables take the theme's cable colour.</li>
 * </ul>
 */
public final class WorkspaceSkin304 {

    /* ---- palette: the default Graph V2 theme of Gadgets & Gizmos ---- */
    public static final int CANVAS = 0xff0d1215;
    public static final int CANVAS_DOT = 0xff1b2429;
    public static final int TITLE_BAR = 0xff192126;
    public static final int PANEL = 0xff11181b;
    public static final int PANEL_RAISED = 0xff202a2f;
    public static final int PANEL_HOVERED = 0xff2b353a;
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

    /** The stock card body, painted behind a port when the square is replaced by a dot. */
    private static final int CARD_BODY = 0xff26313f;

    /** Dot grid pitch in graph units, so the dots travel with the canvas. */
    private static final double GRID_PITCH = 24.0D;

    private WorkspaceSkin304() {
    }

    /** The canvas: flat dark background and a dot grid that scrolls with the pan. */
    public static void drawCanvas(GuiGraphics graphics, NpcGraphCanvas canvas) {
        if (graphics == null || canvas == null) return;
        int left = canvas.left;
        int top = canvas.top;
        int right = canvas.right;
        int bottom = canvas.bottom;
        if (right <= left || bottom <= top) return;

        graphics.fill(left, top, right, bottom, CANVAS);

        double pitch = GRID_PITCH * canvas.zoom;
        while (pitch < 8.0D) pitch *= 2.0D;
        double phaseX = ((canvas.panX % pitch) + pitch) % pitch;
        double phaseY = ((canvas.panY % pitch) + pitch) % pitch;
        int dot = canvas.zoom >= 1.35D ? 2 : 1;

        for (double y = top + phaseY - pitch; y < bottom; y += pitch) {
            int py = (int) Math.round(y);
            if (py < top) continue;
            for (double x = left + phaseX - pitch; x < right; x += pitch) {
                int px = (int) Math.round(x);
                if (px < left) continue;
                graphics.fill(px, py, px + dot, py + dot, CANVAS_DOT);
            }
        }
    }

    /**
     * Frame, accent bar and round ports of one card. {@code cardX}/{@code cardY} are the card's
     * graph coordinates, exactly the ones the screen uses for its own drawing.
     */
    public static void drawCard(GuiGraphics graphics, NpcGraphCanvas canvas, double cardX,
            double cardY, boolean selected) {
        if (graphics == null || canvas == null) return;
        double zoom = canvas.zoom;
        int x = (int) Math.round(canvas.screenX(cardX));
        int y = (int) Math.round(canvas.screenY(cardY));
        int w = (int) Math.round(NpcGraphCanvas.NODE_W * zoom);
        int h = (int) Math.round(NpcGraphCanvas.NODE_H * zoom);
        if (w < 12 || h < 12) return;

        int frame = Math.max(1, (int) Math.round(zoom));
        int midY = y + h / 2;
        int radius = Math.max(3, (int) Math.round(5.0D * zoom));

        // Ports: the stock card paints a 10x10 square on each edge. Cover it - inside the
        // card with the body colour, outside with the canvas - then draw a round port.
        int half = Math.max(radius + 1, (int) Math.round(NpcGraphCanvas.PORT * zoom / 2.0D) + 1);
        graphics.fill(x - half, midY - half, x + half, midY + half, CANVAS);
        graphics.fill(x + w - half, midY - half, x + w + half, midY + half, CANVAS);
        graphics.fill(x, midY - half, x + half, midY + half, CARD_BODY);
        graphics.fill(x + w - half, midY - half, x + w, midY + half, CARD_BODY);
        fillCircle(graphics, x, midY, radius, BORDER_SOFT);
        fillCircle(graphics, x, midY, radius - Math.max(1, frame), PRIMARY);
        fillCircle(graphics, x + w, midY, radius, BORDER_SOFT);
        fillCircle(graphics, x + w, midY, radius - Math.max(1, frame), PRIMARY);

        // Frame and the accent bar along the top edge.
        int line = selected ? ACCENT : BORDER;
        if (selected) {
            graphics.fill(x - frame - 1, y - frame - 1, x + w + frame + 1, y, ACCENT_OVERLAY);
        }
        graphics.fill(x, y, x + w, y + frame, line);
        graphics.fill(x, y + h - frame, x + w, y + h, line);
        graphics.fill(x, y, x + frame, y + h, line);
        graphics.fill(x + w - frame, y, x + w, y + h, line);

        int bar = Math.max(2, (int) Math.round(2.0D * zoom));
        int inset = Math.max(3, (int) Math.round(4.0D * zoom));
        graphics.fill(x + inset, y + frame, x + w - inset, y + frame + bar,
                selected ? ACCENT_LIGHT : ACCENT);
    }

    /** A filled circle, one horizontal span per row. */
    public static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (graphics == null || radius <= 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt((double) (radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** Rounds a colour towards white by {@code amount}, for the port highlight. */
    public static int lighten(int color, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        int a = (color >>> 24) & 0xff;
        int r = (int) Math.round(((color >>> 16) & 0xff) * (1.0D - clamped) + 255 * clamped);
        int g = (int) Math.round(((color >>> 8) & 0xff) * (1.0D - clamped) + 255 * clamped);
        int b = (int) Math.round((color & 0xff) * (1.0D - clamped) + 255 * clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
