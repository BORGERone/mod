package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcStatsGraph;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Interactive "Автономный ИИ" card on the main tab of NpcDialogueGraphScreen,
 * positioned directly below the "Характеристики" card (-360, 186 by default).
 *
 * Uses zero-shadow text rendering (dropShadow = false, Z = 0.0) so overlapping
 * inspector panels, dropdown popups and context menus cleanly cover it.
 * Also relies solely on the 246 screen bridges in mouseClicked/history so legacy
 * subset harnesses (GraphUndoTest) run unchanged.
 */
public final class NpcAutoAiGraphUi255 {
    public static final int CARD_W = NpcAutoAi255.CARD_W;
    public static final int CARD_H = NpcAutoAi255.CARD_H;

    public static final int TOGGLE_X = 184;
    public static final int TOGGLE_W = 94;
    public static final int MINUS_X = 184;
    public static final int PLUS_X = 252;
    public static final int BTN_W = 18;
    public static final int CTRL_H = 18;

    public static final int ROW_WANDER_Y = 30;
    public static final int ROW_WANDER_R_Y = 62;
    public static final int ROW_ATTACK_Y = 94;
    public static final int ROW_AGGRO_R_Y = 126;

    private static final class DragState {
        NpcProfile profile;
        boolean dragging;
        boolean rememberedMove;
        boolean selected;
        double offsetWorldX;
        double offsetWorldY;
    }

    private static final Map<NpcDialogueGraphScreen, DragState> DRAGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcAutoAiGraphUi255() {}

    private static DragState dragState(NpcDialogueGraphScreen screen) {
        synchronized (DRAGS) {
            DragState d = DRAGS.get(screen);
            if (d == null) {
                d = new DragState();
                DRAGS.put(screen, d);
            }
            return d;
        }
    }

    private static boolean isShiftDownSafe() {
        try {
            return Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static void drawNoShadowCentered(GuiGraphics g, Font f, String s, int cx, int y, int color) {
        g.drawString(f, s, cx - f.width(s) / 2, y, color, false);
    }

    public static void ensure(NpcDialogueGraphScreen screen, NpcProfile profile) {
        if (screen == null) return;
        DragState ds = dragState(screen);
        ds.profile = profile;
        NpcDialogueGraph main = screen.settledlands$graph();
        if (main == null) return;
        NpcAutoAi255.ensureCardOnMainGraph(main, profile);
    }

    public static void onHistoryApplied(NpcDialogueGraphScreen screen) {
        if (screen == null) return;
        NpcDialogueGraph g = screen.settledlands$graph();
        if (g != null && NpcAutoAi255.graphState(g).synced) {
            DragState ds = DRAGS.get(screen);
            if (ds != null && ds.profile != null) {
                NpcAutoAi255.mirrorGraphToProfile(g, ds.profile);
            }
        }
    }

    public static void onSnapshotApplied(NpcDialogueGraphScreen screen, NpcDialogueGraph main, NpcProfile profile) {
        if (screen == null) return;
        DragState ds = dragState(screen);
        ds.profile = profile;
        if (main != null && profile != null) {
            NpcAutoAi255.ensureCardOnMainGraph(main, profile);
        }
    }

    public static boolean visible(NpcGraphCanvas canvas, NpcAutoAi255.GraphState gs) {
        if (canvas == null || gs == null || !gs.synced) return false;
        int x = cardX(gs);
        int y = cardY(gs);
        double x1 = canvas.screenX(x);
        double y1 = canvas.screenY(y);
        double z = canvas.zoom;
        return x1 + CARD_W * z >= canvas.left
                && x1 <= canvas.right
                && y1 + CARD_H * z >= canvas.top
                && y1 <= canvas.bottom;
    }

    public static int cardX(NpcAutoAi255.GraphState gs) {
        return gs.cardX == NpcAutoAi255.UNSET_COORD ? NpcAutoAi255.DEFAULT_CARD_X : gs.cardX;
    }

    public static int cardY(NpcAutoAi255.GraphState gs) {
        return gs.cardY == NpcAutoAi255.UNSET_COORD ? NpcAutoAi255.DEFAULT_CARD_Y : gs.cardY;
    }

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics g) {
        if (screen == null || g == null) return;
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        NpcAutoAi255.GraphState gs = NpcAutoAi255.graphState(graph);
        if (!gs.synced) return;
        NpcGraphCanvas c = screen.settledlands$canvas();
        if (!visible(c, gs)) return;

        DragState ds = dragState(screen);
        Font f = screen.settledlands$font();
        int border = ds.selected ? 0xFFF3D47A : 0xFF4BA896;

        int x = cardX(gs);
        int y = cardY(gs);

        g.pose().pushPose();
        g.pose().translate(c.screenX(x), c.screenY(y), 0.0D);
        g.pose().scale((float) c.zoom, (float) c.zoom, 1.0F);

        g.fill(0, 0, CARD_W, CARD_H, 0xEE141A20);
        g.fill(0, 0, CARD_W, 24, 0xFF1F4741);
        g.fill(0, 0, CARD_W, 1, border);
        g.fill(0, CARD_H - 1, CARD_W, CARD_H, border);
        g.fill(0, 0, 1, CARD_H, border);
        g.fill(CARD_W - 1, 0, CARD_W, CARD_H, border);

        g.drawString(f, tr("gui.settledlands.auto_ai.title"), 10, 8, 0xFFF2F6FA, false);

        drawToggleRow(g, f, ROW_WANDER_Y, tr("gui.settledlands.auto_ai.wander"), gs.autoWander);
        drawSpinnerRow(g, f, ROW_WANDER_R_Y, tr("gui.settledlands.auto_ai.wander_radius"),
                NpcAutoAi255.clampRadiusOrDefault(gs.wanderRadius, NpcAutoAi255.DEFAULT_WANDER_RADIUS));
        drawToggleRow(g, f, ROW_ATTACK_Y, tr("gui.settledlands.auto_ai.attack"), gs.autoAttackHostiles);
        drawSpinnerRow(g, f, ROW_AGGRO_R_Y, tr("gui.settledlands.auto_ai.aggro_radius"),
                NpcAutoAi255.clampRadiusOrDefault(gs.aggroRadius, NpcAutoAi255.DEFAULT_AGGRO_RADIUS));

        drawNoShadowCentered(g, f, tr("gui.settledlands.auto_ai.hint"), CARD_W / 2, 152, 0xFF8CA4B5);

        g.pose().popPose();
    }

    private static void drawToggleRow(GuiGraphics g, Font f, int ry, String label, boolean on) {
        g.drawString(f, label, 12, ry + 5, 0xFFD9E3EC, false);
        int bg = on ? 0xFF255B4A : 0xFF28323B;
        int fg = on ? 0xFF9AF5CE : 0xFFAAB8C5;
        g.fill(TOGGLE_X, ry, TOGGLE_X + TOGGLE_W, ry + CTRL_H, bg);
        String txt = tr(on ? "gui.settledlands.auto_ai.on" : "gui.settledlands.auto_ai.off");
        drawNoShadowCentered(g, f, txt, TOGGLE_X + TOGGLE_W / 2, ry + 5, fg);
    }

    private static void drawSpinnerRow(GuiGraphics g, Font f, int ry, String label, int val) {
        g.drawString(f, label, 12, ry + 5, 0xFFD9E3EC, false);
        g.fill(MINUS_X, ry, MINUS_X + BTN_W, ry + CTRL_H, val > NpcAutoAi255.MIN_RADIUS ? 0xFF2B3945 : 0xFF1C232A);
        g.fill(PLUS_X, ry, PLUS_X + BTN_W, ry + CTRL_H, val < NpcAutoAi255.MAX_RADIUS ? 0xFF2B3945 : 0xFF1C232A);
        drawNoShadowCentered(g, f, "<", MINUS_X + BTN_W / 2, ry + 5, 0xFFF4F7FA);
        drawNoShadowCentered(g, f, Integer.toString(val), (MINUS_X + BTN_W + PLUS_X) / 2, ry + 5, 0xFFEAD28A);
        drawNoShadowCentered(g, f, ">", PLUS_X + BTN_W / 2, ry + 5, 0xFFF4F7FA);
    }

    public static boolean mouseClicked(NpcDialogueGraphScreen screen, double mx, double my, int button) {
        if (screen != null
                && !screen.settledlands$contextOpen()
                && !screen.settledlands$insideInspector(mx, my)) {
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas c = screen.settledlands$canvas();
            if (graph != null && c != null && c.contains(mx, my)) {
                NpcAutoAi255.GraphState gs = NpcAutoAi255.graphState(graph);
                if (gs.synced) {
                    int cx = cardX(gs);
                    int cy = cardY(gs);
                    double nx = c.worldX(mx) - cx;
                    double ny = c.worldY(my) - cy;
                    DragState ds = dragState(screen);
                    if (nx >= 0.0D && nx <= CARD_W && ny >= 0.0D && ny <= CARD_H) {
                        if (button != 0) {
                            return true; // Protect against right-click delete context menu
                        }
                        ds.selected = true;
                        int step = isShiftDownSafe() ? 5 : 1;
                        if (ny >= ROW_WANDER_Y && ny < ROW_WANDER_Y + CTRL_H
                                && nx >= TOGGLE_X && nx < TOGGLE_X + TOGGLE_W) {
                            screen.settledlands$remember();
                            gs.autoWander = !gs.autoWander;
                            if (ds.profile != null) {
                                NpcAutoAi255.mirrorGraphToProfile(graph, ds.profile);
                            }
                            screen.settledlands$changed();
                            return true;
                        }
                        if (ny >= ROW_WANDER_R_Y && ny < ROW_WANDER_R_Y + CTRL_H) {
                            int delta = hitSpinner(nx, step);
                            if (delta != 0) {
                                applyRadiusDelta(screen, graph, gs, ds.profile, true, delta);
                                return true;
                            }
                        }
                        if (ny >= ROW_ATTACK_Y && ny < ROW_ATTACK_Y + CTRL_H
                                && nx >= TOGGLE_X && nx < TOGGLE_X + TOGGLE_W) {
                            screen.settledlands$remember();
                            gs.autoAttackHostiles = !gs.autoAttackHostiles;
                            if (ds.profile != null) {
                                NpcAutoAi255.mirrorGraphToProfile(graph, ds.profile);
                            }
                            screen.settledlands$changed();
                            return true;
                        }
                        if (ny >= ROW_AGGRO_R_Y && ny < ROW_AGGRO_R_Y + CTRL_H) {
                            int delta = hitSpinner(nx, step);
                            if (delta != 0) {
                                applyRadiusDelta(screen, graph, gs, ds.profile, false, delta);
                                return true;
                            }
                        }

                        // Start dragging the card
                        screen.settledlands$hideStatsInspector();
                        ds.dragging = true;
                        ds.rememberedMove = false;
                        ds.offsetWorldX = nx;
                        ds.offsetWorldY = ny;
                        return true;
                    } else {
                        ds.selected = false;
                    }
                }
            }
        }
        return NpcStatsGraphUi.mouseClicked(screen, mx, my, button);
    }

    public static boolean handleMouseDragged(NpcDialogueGraphScreen screen, double mx, double my,
                                             int button, double dx, double dy) {
        if (mouseDragged(screen, mx, my, button)) {
            return true;
        }
        return screen != null && screen.settledlands$mouseDraggedOriginal(mx, my, button, dx, dy);
    }

    public static boolean handleMouseReleased(NpcDialogueGraphScreen screen, double mx, double my, int button) {
        if (mouseReleased(screen, mx, my, button)) {
            return true;
        }
        return screen != null && screen.settledlands$mouseReleasedOriginal(mx, my, button);
    }

    public static boolean mouseDragged(NpcDialogueGraphScreen screen, double mx, double my, int button) {
        if (screen == null || button != 0) return false;
        DragState ds = DRAGS.get(screen);
        if (ds == null || !ds.dragging) return false;
        NpcDialogueGraph graph = screen.settledlands$graph();
        NpcGraphCanvas c = screen.settledlands$canvas();
        if (graph == null || c == null) {
            ds.dragging = false;
            return false;
        }
        NpcAutoAi255.GraphState gs = NpcAutoAi255.graphState(graph);
        if (!gs.synced) {
            ds.dragging = false;
            return false;
        }
        int nextX = NpcAutoAi255.clampCoord((int) Math.round(c.worldX(mx) - ds.offsetWorldX));
        int nextY = NpcAutoAi255.clampCoord((int) Math.round(c.worldY(my) - ds.offsetWorldY));
        if (nextX != cardX(gs) || nextY != cardY(gs)) {
            if (!ds.rememberedMove) {
                screen.settledlands$remember();
                ds.rememberedMove = true;
            }
            gs.cardX = nextX;
            gs.cardY = nextY;
            screen.settledlands$changed();
        }
        return true;
    }

    public static boolean mouseReleased(NpcDialogueGraphScreen screen, double mx, double my, int button) {
        if (screen == null || button != 0) return false;
        DragState ds = DRAGS.get(screen);
        if (ds != null && ds.dragging) {
            ds.dragging = false;
            ds.rememberedMove = false;
            return true;
        }
        return false;
    }

    private static int hitSpinner(double nx, int step) {
        if (nx >= MINUS_X && nx < MINUS_X + BTN_W) return -step;
        if (nx >= PLUS_X && nx < PLUS_X + BTN_W) return step;
        return 0;
    }

    private static void applyRadiusDelta(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
                                         NpcAutoAi255.GraphState gs, NpcProfile profile, boolean wander, int delta) {
        int cur = wander
                ? NpcAutoAi255.clampRadiusOrDefault(gs.wanderRadius, NpcAutoAi255.DEFAULT_WANDER_RADIUS)
                : NpcAutoAi255.clampRadiusOrDefault(gs.aggroRadius, NpcAutoAi255.DEFAULT_AGGRO_RADIUS);
        int next = NpcAutoAi255.clampRadius(cur + delta);
        if (next != cur) {
            screen.settledlands$remember();
            if (wander) {
                gs.wanderRadius = next;
            } else {
                gs.aggroRadius = next;
            }
            if (profile != null) {
                NpcAutoAi255.mirrorGraphToProfile(graph, profile);
            }
            screen.settledlands$changed();
        }
    }

    public static void frameAll(NpcGraphCanvas c, NpcDialogueGraph graph) {
        if (graph == null) {
            NpcStatsGraphUi.frameAll(c, null);
            return;
        }
        NpcAutoAi255.GraphState gs = NpcAutoAi255.graphState(graph);
        if (!gs.synced) {
            NpcStatsGraphUi.frameAll(c, graph);
            return;
        }
        int minX = cardX(gs);
        int minY = cardY(gs);
        int maxX = minX + CARD_W;
        int maxY = minY + CARD_H;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (n == null) continue;
            boolean st = n.type == NpcStatsGraph.TYPE;
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + (st ? 300 : 220));
            maxY = Math.max(maxY, n.y + (st ? 170 : 104));
        }
        double w = Math.max(1, maxX - minX + 80);
        double h = Math.max(1, maxY - minY + 80);
        int vw = Math.max(1, c.right - c.left);
        int vh = Math.max(1, c.bottom - c.top);
        c.zoom = Math.max(0.2D, Math.min(1.0D, Math.min(vw / w, vh / h)));
        c.panX = vw / 2.0D - (minX + maxX) / 2.0D * c.zoom;
        c.panY = vh / 2.0D - (minY + maxY) / 2.0D * c.zoom;
    }
}
