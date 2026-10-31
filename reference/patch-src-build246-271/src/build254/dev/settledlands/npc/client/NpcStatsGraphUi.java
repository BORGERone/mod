package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcStatsGraph;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Large graph node with three canvas-scaled spinners and bottom Randomize button (Build 254). */
public final class NpcStatsGraphUi {
    public static final int WIDTH = 300, HEIGHT = 170, LEFT = 186, RIGHT = 256, BUTTON_W = 22, BUTTON_H = 20;
    public static final int RAND_X = 12, RAND_Y = 134, RAND_W = 266, RAND_H = 22;
    private static final int[] ROWS = {32, 66, 100};
    private static final String[] LABELS = {
        "gui.settledlands.stats.health",
        "gui.settledlands.stats.damage",
        "gui.settledlands.stats.speed"
    };

    private NpcStatsGraphUi() {}

    private static int width(NpcDialogueGraph.Node n) {
        return n.type == NpcStatsGraph.TYPE ? WIDTH : 220;
    }

    private static int height(NpcDialogueGraph.Node n) {
        return n.type == NpcStatsGraph.TYPE ? HEIGHT : 100;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static void drawCenteredNoShadow(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        int w = font == null ? text.length() * 6 : font.width(text);
        g.drawString(font, text, cx - w / 2, y, color, false);
    }

    public static void ensure(NpcDialogueGraphScreen s) {
        if (s == null) return;
        if (NpcStatsGraph.ensure(s.settledlands$graph())) {
            s.settledlands$changed();
        }
    }

    public static void draw(NpcDialogueGraphScreen s, GuiGraphics g, NpcDialogueGraph.Node n) {
        if (n.type != NpcStatsGraph.TYPE) {
            s.settledlands$drawOriginal(g, n);
            return;
        }
        NpcGraphCanvas c = s.settledlands$canvas();
        Font font = s.settledlands$font();
        g.pose().pushPose();
        g.pose().translate(c.screenX(n.x), c.screenY(n.y), 0.0);
        g.pose().scale((float) c.zoom, (float) c.zoom, 1.0f);
        int border = s.settledlands$isSelected(n.id) ? 0xffe9be67 : 0xff465267;
        g.fill(-2, -2, WIDTH + 2, HEIGHT + 2, border);
        g.fill(0, 0, WIDTH, HEIGHT, 0xff262f3c);
        g.fill(0, 0, WIDTH, 24, 0xff375870);
        g.drawString(font, tr("gui.settledlands.stats.title") + " #" + n.id, 10, 8, 0xfff4f5f8, false);
        int[] values = {n.varValue, n.checkRadius, n.checkCooldown};
        for (int i = 0; i < 3; i++) {
            int y = ROWS[i];
            g.drawString(font, tr(LABELS[i]), 12, y + 6, 0xffe7edf4, false);
            g.fill(LEFT, y, LEFT + BUTTON_W, y + BUTTON_H, 0xff56697a);
            g.fill(RIGHT, y, RIGHT + BUTTON_W, y + BUTTON_H, 0xff56697a);
            g.fill(LEFT + BUTTON_W + 3, y, RIGHT - 3, y + BUTTON_H, 0xff172330);
            drawCenteredNoShadow(g, font, "−", LEFT + BUTTON_W / 2, y + 6, 0xffffffff);
            drawCenteredNoShadow(g, font, Integer.toString(NpcStatsGraph.clamp(values[i])), (LEFT + BUTTON_W + RIGHT) / 2, y + 6, 0xffffffff);
            drawCenteredNoShadow(g, font, "+", RIGHT + BUTTON_W / 2, y + 6, 0xffffffff);
        }
        g.fill(RAND_X, RAND_Y, RAND_X + RAND_W, RAND_Y + RAND_H, 0xff56697a);
        drawCenteredNoShadow(g, font, tr("gui.settledlands.stats.randomize"), RAND_X + RAND_W / 2, RAND_Y + 7, 0xffffffff);
        g.pose().popPose();
    }

    public static boolean randomize(NpcDialogueGraphScreen s, NpcDialogueGraph.Node n, Random rng) {
        if (s == null || n == null || n.type != NpcStatsGraph.TYPE || rng == null) return false;
        int oh = NpcStatsGraph.clamp(n.varValue);
        int od = NpcStatsGraph.clamp(n.checkRadius);
        int os = NpcStatsGraph.clamp(n.checkCooldown);
        int nh = NpcStatsGraph.randomStep(oh, rng);
        int nd = NpcStatsGraph.randomStep(od, rng);
        int ns = NpcStatsGraph.randomStep(os, rng);
        if (nh != oh || nd != od || ns != os) {
            s.settledlands$remember();
            n.varValue = nh;
            n.checkRadius = nd;
            n.checkCooldown = ns;
            s.settledlands$changed();
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(NpcDialogueGraphScreen s, double x, double y, int button) {
        NpcGraphCanvas c = s.settledlands$canvas();
        NpcDialogueGraph graph = s.settledlands$graph();
        if (s.activeDropdown() == null && !s.settledlands$contextOpen() && !s.settledlands$insideInspector(x, y) && c.contains(x, y)) {
            NpcDialogueGraph.Node n = graph.get(c.hit(graph, x, y));
            if (n != null && n.type == NpcStatsGraph.TYPE) {
                if (button == 1) return true;
                if (button == 0) {
                    double nx = c.worldX(x) - n.x, ny = c.worldY(y) - n.y;
                    for (int i = 0; i < 3; i++) {
                        int delta = nx >= LEFT && nx < LEFT + BUTTON_W ? -1 : nx >= RIGHT && nx < RIGHT + BUTTON_W ? 1 : 0;
                        if (delta == 0 || ny < ROWS[i] || ny >= ROWS[i] + BUTTON_H) continue;
                        int old = i == 0 ? n.varValue : i == 1 ? n.checkRadius : n.checkCooldown;
                        int value = NpcStatsGraph.clamp(old + delta);
                        if (value != old) {
                            s.settledlands$remember();
                            if (i == 0) n.varValue = value;
                            else if (i == 1) n.checkRadius = value;
                            else n.checkCooldown = value;
                            s.settledlands$changed();
                        }
                        return true;
                    }
                    if (nx >= RAND_X && nx < RAND_X + RAND_W && ny >= RAND_Y && ny < RAND_Y + RAND_H) {
                        randomize(s, n, ThreadLocalRandom.current());
                        return true;
                    }
                    boolean handled = s.settledlands$mouseClickedOriginal(x, y, button);
                    s.settledlands$hideStatsInspector();
                    return handled;
                }
            }
        }
        return s.settledlands$mouseClickedOriginal(x, y, button);
    }

    public static boolean visible(NpcGraphCanvas c, NpcDialogueGraph.Node n) {
        double x = c.screenX(n.x), y = c.screenY(n.y);
        return x + width(n) * c.zoom >= c.left && x <= c.right && y + height(n) * c.zoom >= c.top && y <= c.bottom;
    }

    public static int hit(NpcGraphCanvas c, NpcDialogueGraph graph, double sx, double sy) {
        if (!c.contains(sx, sy)) return -1;
        double x = c.worldX(sx), y = c.worldY(sy);
        int found = -1;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (n.x <= x && x <= n.x + width(n) && n.y <= y && y <= n.y + height(n)) found = n.id;
        }
        return found;
    }

    public static int hitPort(NpcGraphCanvas c, NpcDialogueGraph graph, double x, double y, boolean output) {
        int id = c.settledlands$hitPortOriginal(graph, x, y, output);
        NpcDialogueGraph.Node n = graph.get(id);
        return n != null && n.type == NpcStatsGraph.TYPE ? -1 : id;
    }

    public static void focus(NpcGraphCanvas c, NpcDialogueGraph.Node n) {
        if (n == null) return;
        c.panX = (c.right - c.left) / 2.0 - (n.x + width(n) / 2.0) * c.zoom;
        c.panY = (c.bottom - c.top) / 2.0 - (n.y + height(n) / 2.0) * c.zoom;
    }

    public static void frameAll(NpcGraphCanvas c, NpcDialogueGraph graph) {
        if (graph.nodes.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + width(n));
            maxY = Math.max(maxY, n.y + height(n));
        }
        c.zoom = Math.max(0.05, Math.min(1.0, Math.min((c.right - c.left - 60.0) / Math.max(1, maxX - minX), (c.bottom - c.top - 60.0) / Math.max(1, maxY - minY))));
        c.panX = (c.right - c.left) / 2.0 - (minX + maxX) / 2.0 * c.zoom;
        c.panY = (c.bottom - c.top) / 2.0 - (minY + maxY) / 2.0 * c.zoom;
    }
}
