package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

/**
 * Build 453: unified hover detection, Bezier bend-point routing, Shift+LMB / MMB cutting,
 * waiting-wire attachment, and point-to-target aiming across ALL graph wire types:
 * <ul>
 *   <li>Stock execution wires ({@code next}, {@code falseNext}, {@code choices}, {@code triggers}, {@code randomBranches}, white hangs)</li>
 *   <li>Blue value/target wires ({@code Pursuit}, {@code Check}, {@code Damage}, {@code Door}, {@code Chest}, {@code Trial})</li>
 *   <li>Orange variable/data wires ({@link VarDataNode453} / {@code VARIABLE} -&gt; {@code VARIABLE}/{@code IF})</li>
 *   <li>Amber secondary event wires ({@link CardEventPin398}: Door/Chest/Trial)</li>
 *   <li>Green Trial Spawner wave-start wires ({@link TrialCard446})</li>
 *   <li>Color-coded Trial Module to Trial Hub wires ({@link TrialModuleCard447})</li>
 * </ul>
 */
public final class WireHover453 {

    public static final char KIND_STOCK = 's';
    public static final char KIND_VALUE = NpcWirePoints321.KIND_VALUE;
    public static final char KIND_ORANGE = NpcWirePoints321.KIND_ORANGE;
    public static final char KIND_AMBER = NpcWirePoints321.KIND_AMBER;
    public static final char KIND_TRIAL_START = NpcWirePoints321.KIND_TRIAL_START;
    public static final char KIND_TRIAL_MOD = NpcWirePoints321.KIND_TRIAL_MOD;
    public static final char KIND_HANG_WHITE = 'w';
    public static final char KIND_HANG_COLOR = 'k';

    public static final class WireHit {
        public final char kind;
        public final Node from;
        public final int fromPort;
        public final int toId;
        public final int arg;
        public final int endX;
        public final int endY;
        public final int segment;
        public final double sx1;
        public final double sy1;
        public final double sx2;
        public final double sy2;
        public final double distSq;

        WireHit(char kind, Node from, int fromPort, int toId, int arg, int endX, int endY,
                int segment, double sx1, double sy1, double sx2, double sy2, double distSq) {
            this.kind = kind;
            this.from = from;
            this.fromPort = fromPort;
            this.toId = toId;
            this.arg = arg;
            this.endX = endX;
            this.endY = endY;
            this.segment = segment;
            this.sx1 = sx1;
            this.sy1 = sy1;
            this.sx2 = sx2;
            this.sy2 = sy2;
            this.distSq = distSq;
        }
    }

    @FunctionalInterface
    public interface CurveDrawer {
        int draw(BufferBuilder buffer, Matrix4f matrix, double x1, double y1,
                double x2, double y2, int color, boolean highlight);
    }

    /** Temporary link maps hidden during stock wires() pass so hover arbitration still sees true graph links. */
    private static Map<Integer, Integer> hiddenModuleNext = Collections.emptyMap();
    private static Map<Integer, Integer> hiddenOrangeVarIn = Collections.emptyMap();

    /** Per-frame cache keyed on graph + cursor coordinates. */
    private static NpcDialogueGraph cachedGraph;
    private static double cachedMouseX = Double.NaN;
    private static double cachedMouseY = Double.NaN;
    private static long cachedTimeMs = -1L;
    private static WireHit cachedHit;

    private WireHover453() {}

    public static void setHiddenModuleNext(Map<Integer, Integer> hidden) {
        hiddenModuleNext = hidden == null ? Collections.emptyMap() : hidden;
    }

    public static void setHiddenOrangeVarIn(Map<Integer, Integer> hidden) {
        hiddenOrangeVarIn = hidden == null ? Collections.emptyMap() : hidden;
    }

    public static void invalidateCache() {
        cachedGraph = null;
        cachedHit = null;
    }

    public static int moduleTargetId(Node mod) {
        if (mod == null) return -1;
        Integer hidden = hiddenModuleNext.get(mod.id);
        return hidden != null ? hidden : mod.next;
    }

    public static int orangeInputId(Node consumer) {
        if (consumer == null) return -1;
        Integer hidden = hiddenOrangeVarIn.get(consumer.id);
        return hidden != null ? hidden : consumer.varInputNode;
    }

    // ---- Point validation across ALL wire kinds ----

    public static boolean isBlueAlive(NpcDialogueGraph graph, Node from, int toId) {
        return PursuitGraphNode272.blueLinked(graph, from, toId);
    }

    public static boolean isCustomAlive(NpcDialogueGraph graph, Node from, char kind, int toId) {
        if (graph == null || from == null || toId < 0 || from.id == toId) return false;
        Node to = graph.get(toId);
        if (to == null) return false;
        return switch (kind) {
            case KIND_VALUE -> isBlueAlive(graph, from, toId);
            case KIND_ORANGE -> VarDataNode453.isOrangeSourceNode(from)
                    && VarDataNode453.isOrangeInputNode(to)
                    && orangeInputId(to) == from.id;
            case KIND_AMBER -> CardEventPin398.isCard(from) && from.falseNext == toId;
            case KIND_TRIAL_START -> TrialGraph446.isTrialNode(from)
                    && TrialGraph446.startWaveTarget(from) == toId;
            case KIND_TRIAL_MOD -> TrialModule447.isModuleNode(from)
                    && TrialGraph446.isTrialNode(to)
                    && moduleTargetId(from) == toId;
            default -> false;
        };
    }

    public static List<int[]> livePoints(NpcDialogueGraph graph) {
        if (graph == null) return List.of();
        return NpcWirePoints321.points(graph,
                (node, id) -> isBlueAlive(graph, node, id),
                (node, kind, id) -> isCustomAlive(graph, node, kind, id));
    }

    // ---- Unified nearest-wire scan ----

    public static WireHit hoveredWire(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        if (screen == null || graph == null || graph.nodes == null) return null;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || !canvas.contains(mouseX, mouseY)) return null;
        if (screen.settledlands$contextOpen()) return null;

        long now = System.currentTimeMillis();
        if (graph == cachedGraph && mouseX == cachedMouseX && mouseY == cachedMouseY
                && now - cachedTimeMs <= 40L) {
            return cachedHit;
        }
        WireHit hit = scanNearestWire(screen, canvas, graph, mouseX, mouseY);
        cachedGraph = graph;
        cachedMouseX = mouseX;
        cachedMouseY = mouseY;
        cachedTimeMs = now;
        cachedHit = hit;
        return hit;
    }

    /**
     * Direct hit query used on mouse clicks (ignores short cache and checks cursor is not on a
     * card or port or bend dot).
     */
    public static WireHit scanNearestWire(NpcDialogueGraphScreen screen, NpcGraphCanvas canvas,
            NpcDialogueGraph graph, double mouseX, double mouseY) {
        if (canvas == null || graph == null || graph.nodes == null) return null;
        if (!canvas.contains(mouseX, mouseY)) return null;
        if (canvas.hit(graph, mouseX, mouseY) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, true) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, false) >= 0) return null;
        if (screen != null && (NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY) != null
                || NpcWirePointsUi321.portAt(screen, graph, mouseX, mouseY) != null)) {
            return null;
        }
        if (isOverAnyColoredPin(graph, canvas, mouseX, mouseY)) return null;

        double limit = Math.max(4.5D, Math.min(8.0D, 4.5D * canvas.zoom + 2.0D));
        double[] best = { limit * limit };
        WireHit found = null;

        // 1. Stock execution wires & white waiting hangs
        for (Node node : graph.nodes.values()) {
            if (node == null) continue;
            if (node.type == NpcDialogueGraph.IF && node.falseNext >= 0) {
                found = scanStock(canvas, graph, node, 1, node.falseNext, mouseX, mouseY, best, found);
            }
            if (node.next >= 0 && !(TrialModule447.isModuleNode(node)
                    && TrialGraph446.isTrialNode(graph.get(node.next)))) {
                // Module -> Hub is scanned under KIND_TRIAL_MOD below so hover/cut uses colored path
                found = scanStock(canvas, graph, node, 0, node.next, mouseX, mouseY, best, found);
            }
            for (int to : node.choices) {
                found = scanStock(canvas, graph, node, 0, to, mouseX, mouseY, best, found);
            }
            for (int i = 0; i < node.randomBranches.size(); i++) {
                int to = node.randomBranches.get(i);
                if (to >= 0) found = scanStock(canvas, graph, node, i, to, mouseX, mouseY, best, found);
            }
            for (int to : node.triggers) {
                found = scanStock(canvas, graph, node, 0, to, mouseX, mouseY, best, found);
            }
            // Orange value wires to VarDataNode453 / IF / VARIABLE are drawn in the orange pass regardless of node.next
            if (VarDataNode453.isOrangeInputNode(node)) {
                int vin = orangeInputId(node);
                if (vin >= 0) {
                    Node src = graph.get(vin);
                    if (src != null && src != node && VarDataNode453.isOrangeSourceNode(src)) {
                        found = scanOrange(canvas, src, node, mouseX, mouseY, best, found);
                    }
                }
            }
            for (int[] hang : NpcWirePoints321.hangs(node)) {
                found = scanWhiteHang(canvas, node, hang, mouseX, mouseY, best, found);
            }
            for (NpcWirePoints321.ColorHang ch : NpcWirePoints321.colorHangs(node)) {
                found = scanColorHang(canvas, ch, mouseX, mouseY, best, found);
            }

            // 2. Blue value wires (Pursuit / ValueSource -> Pursuit / ValueInput)
            if (PursuitGraphNode272.isPursuitNode(node)) {
                int source = PursuitGraphNode272.sourceId(node);
                if (source >= 0) {
                    found = scanBlue(canvas, graph.get(source), node, mouseX, mouseY, best, found);
                }
                for (Integer consumer : PursuitGraphNode272.consumers(node)) {
                    if (consumer != null) {
                        found = scanBlue(canvas, node, graph.get(consumer), mouseX, mouseY, best, found);
                    }
                }
            }
            if (PursuitGraphNode272.isValueInputNode(node)) {
                int source = PursuitGraphNode272.stockInputSource(node);
                if (source >= 0) {
                    found = scanBlue(canvas, graph.get(source), node, mouseX, mouseY, best, found);
                }
            }

            // 3. Amber second-socket wires (Door / Chest / Trial)
            if (CardEventPin398.isCard(node)) {
                int target = CardEventPin398.secondChain(node);
                if (target >= 0) {
                    Node to = graph.get(target);
                    if (to != null && to != node) {
                        double[] route = routeEnds(canvas,
                                CardEventPin398.pinScreenX(canvas, node),
                                CardEventPin398.pinScreenY(canvas, node),
                                canvas.portScreenX(to, false),
                                canvas.portScreenY(to, false, 0),
                                NpcWirePoints321.customBends(node, KIND_AMBER, to.id));
                        found = measure(canvas, KIND_AMBER, node, -1, to.id, 0, 0, 0,
                                route, mouseX, mouseY, best, found);
                    }
                }
            }

            // 4. Trial Spawner green Start-Wave wire
            if (TrialGraph446.isTrialNode(node)) {
                int target = TrialGraph446.startWaveTarget(node);
                if (target >= 0) {
                    Node to = graph.get(target);
                    if (to != null && to != node) {
                        double[] route = routeEnds(canvas,
                                TrialCard446.pinScreenX(canvas, node),
                                TrialCard446.pinScreenY(canvas, node),
                                canvas.portScreenX(to, false),
                                canvas.portScreenY(to, false, 0),
                                NpcWirePoints321.customBends(node, KIND_TRIAL_START, to.id));
                        found = measure(canvas, KIND_TRIAL_START, node, -1, to.id, 0, 0, 0,
                                route, mouseX, mouseY, best, found);
                    }
                }
            }

            // 5. Trial Module -> Trial Hub wire
            int modSlot = TrialModule447.slotOf(node);
            if (modSlot >= 0) {
                int hubId = moduleTargetId(node);
                Node hub = hubId >= 0 ? graph.get(hubId) : null;
                if (TrialGraph446.isTrialNode(hub)) {
                    double[] route = routeEnds(canvas,
                            TrialModuleCard447.outputPinScreenX(canvas, node),
                            TrialModuleCard447.outputPinScreenY(canvas, node),
                            TrialCard446.inputPinScreenX(canvas, hub),
                            TrialCard446.inputPinScreenY(canvas, hub, modSlot),
                            NpcWirePoints321.customBends(node, KIND_TRIAL_MOD, hub.id));
                    found = measure(canvas, KIND_TRIAL_MOD, node, -1, hub.id, modSlot, 0, 0,
                            route, mouseX, mouseY, best, found);
                }
            }
        }
        return found;
    }

    private static WireHit scanStock(NpcGraphCanvas canvas, NpcDialogueGraph graph, Node from,
            int fromPort, int toId, double mouseX, double mouseY, double[] best, WireHit found) {
        Node to = graph.get(toId);
        if (to == null || to == from) return found;
        double[] route = NpcWirePointsUi321.route(canvas, from, fromPort, to);
        return measure(canvas, KIND_STOCK, from, fromPort, toId, 0, 0, 0,
                route, mouseX, mouseY, best, found);
    }

    private static WireHit scanBlue(NpcGraphCanvas canvas, Node from, Node to,
            double mouseX, double mouseY, double[] best, WireHit found) {
        if (from == null || to == null || from == to) return found;
        double[] ends = PursuitGraphNode272.blueEnds(canvas, from, to);
        if (ends == null) return found;
        double[] route = routeEnds(canvas, ends[2], ends[3], ends[4], ends[5],
                NpcWirePoints321.valueBends(from, to.id));
        return measure(canvas, KIND_VALUE, from, NpcWirePoints321.VALUE_PORT, to.id, 0, 0, 0,
                route, mouseX, mouseY, best, found);
    }

    private static WireHit scanOrange(NpcGraphCanvas canvas, Node from, Node to,
            double mouseX, double mouseY, double[] best, WireHit found) {
        if (from == null || to == null || from == to) return found;
        double x1 = VarDataCard453.orangeOutScreenX(canvas, from);
        double y1 = VarDataCard453.orangeOutScreenY(canvas, from);
        double x2 = VarDataCard453.orangeInScreenX(canvas, to);
        double y2 = VarDataCard453.orangeInScreenY(canvas, to);
        double[] route = routeEnds(canvas, x1, y1, x2, y2,
                NpcWirePoints321.customBends(from, KIND_ORANGE, to.id));
        return measure(canvas, KIND_ORANGE, from, -1, to.id, 0, 0, 0,
                route, mouseX, mouseY, best, found);
    }

    private static WireHit scanWhiteHang(NpcGraphCanvas canvas, Node node, int[] hang,
            double mouseX, double mouseY, double[] best, WireHit found) {
        int size = NpcWirePoints321.routeSize(hang);
        if (size <= 0) return found;
        double[] route = new double[(size + 1) * 2];
        route[0] = canvas.portScreenX(node, true);
        route[1] = canvas.portScreenY(node, true, hang[0]);
        int at = 2;
        for (int i = 0; i < size; i++) {
            route[at++] = canvas.screenX(NpcWirePoints321.routeX(hang, i));
            route[at++] = canvas.screenY(NpcWirePoints321.routeY(hang, i));
        }
        return measure(canvas, KIND_HANG_WHITE, node, hang[0], -1, 0,
                NpcWirePoints321.routeX(hang, size - 1), NpcWirePoints321.routeY(hang, size - 1),
                route, mouseX, mouseY, best, found);
    }

    private static WireHit scanColorHang(NpcGraphCanvas canvas, NpcWirePoints321.ColorHang ch,
            double mouseX, double mouseY, double[] best, WireHit found) {
        if (ch == null || ch.route.isEmpty() || ch.from == null) return found;
        double[] start = colorHangStart(canvas, ch.from, ch.kind, ch.arg);
        if (start == null) return found;
        double[] route = new double[(ch.route.size() + 1) * 2];
        route[0] = start[0];
        route[1] = start[1];
        int at = 2;
        for (int[] pt : ch.route) {
            route[at++] = canvas.screenX(pt[0]);
            route[at++] = canvas.screenY(pt[1]);
        }
        return measure(canvas, KIND_HANG_COLOR, ch.from, (int) ch.kind, -1, ch.arg,
                ch.endX(), ch.endY(), route, mouseX, mouseY, best, found);
    }

    public static double[] routeEnds(NpcGraphCanvas canvas, double x1, double y1,
            double x2, double y2, List<int[]> points) {
        int n = points == null ? 0 : points.size();
        double[] route = new double[(n + 2) * 2];
        route[0] = x1;
        route[1] = y1;
        int at = 2;
        if (points != null) {
            for (int[] pt : points) {
                route[at++] = canvas.screenX(pt[0]);
                route[at++] = canvas.screenY(pt[1]);
            }
        }
        route[at++] = x2;
        route[at] = y2;
        return route;
    }

    private static WireHit measure(NpcGraphCanvas canvas, char kind, Node from, int fromPort,
            int toId, int arg, int endX, int endY, double[] route,
            double mouseX, double mouseY, double[] best, WireHit found) {
        int[] seg = new int[1];
        double[] ends = new double[4];
        double dist = NpcWirePointsUi321.nearestOnRoute(canvas, route, mouseX, mouseY, seg, ends);
        if (dist > best[0]) return found;
        best[0] = dist;
        return new WireHit(kind, from, fromPort, toId, arg, endX, endY,
                seg[0], ends[0], ends[1], ends[2], ends[3], dist);
    }

    private static boolean isOverAnyColoredPin(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mouseX, double mouseY) {
        for (Node n : graph.nodes.values()) {
            if (n == null) continue;
            if (PursuitGraphNode272.isValueSourceNode(n)
                    && PursuitGraphNode272.hitCheckPin(canvas, n, mouseX, mouseY)) return true;
            if (PursuitGraphNode272.isValueInputNode(n)
                    && PursuitGraphNode272.hitInputPin(canvas, n, mouseX, mouseY)) return true;
            if (PursuitGraphNode272.isPursuitNode(n)
                    && (PursuitGraphNode272.hitTargetPin(canvas, n, mouseX, mouseY)
                    || PursuitGraphNode272.hitTargetInPin(canvas, n, mouseX, mouseY))) return true;
            if (VarDataCard453.hitOrangeOut(canvas, n, mouseX, mouseY)
                    || VarDataCard453.hitOrangeIn(canvas, n, mouseX, mouseY)) return true;
        }
        if (CardEventPin398.cardAt(graph, canvas, mouseX, mouseY) != null) return true;
        if (TrialCard446.cardAt(graph, canvas, mouseX, mouseY) != null) return true;
        if (TrialModuleCard447.moduleAtPin(graph, canvas, mouseX, mouseY) != null) return true;
        if (TrialModuleCard447.hubSlotAt(graph, canvas, mouseX, mouseY) != null) return true;
        return false;
    }

    // ---- Hover highlight queries ----

    public static boolean isStockHovered(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            int fromId, int fromPort, int toId, double mouseX, double mouseY) {
        WireHit hit = hoveredWire(screen, graph, mouseX, mouseY);
        return hit != null && hit.kind == KIND_STOCK
                && hit.from != null && hit.from.id == fromId
                && hit.fromPort == fromPort && hit.toId == toId;
    }

    public static boolean isColoredHovered(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            char kind, int fromId, int toId, double mouseX, double mouseY) {
        WireHit hit = hoveredWire(screen, graph, mouseX, mouseY);
        return hit != null && hit.kind == kind
                && hit.from != null && hit.from.id == fromId && hit.toId == toId;
    }

    public static boolean isWhiteHangHovered(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node from, int fromPort, int endX, int endY, double mouseX, double mouseY) {
        WireHit hit = hoveredWire(screen, graph, mouseX, mouseY);
        return hit != null && hit.kind == KIND_HANG_WHITE && hit.from == from
                && hit.fromPort == fromPort && hit.endX == endX && hit.endY == endY;
    }

    public static boolean isColorHangHovered(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node from, char kind, int arg, int endX, int endY, double mouseX, double mouseY) {
        WireHit hit = hoveredWire(screen, graph, mouseX, mouseY);
        return hit != null && hit.kind == KIND_HANG_COLOR && hit.from == from
                && hit.fromPort == (int) kind && hit.arg == arg
                && hit.endX == endX && hit.endY == endY;
    }

    // ---- Drawing colored wires through bend points ----

    public static int drawBentWire(BufferBuilder buffer, Matrix4f matrix, NpcGraphCanvas canvas,
            double x1, double y1, double x2, double y2, List<int[]> points,
            int color, boolean highlight, CurveDrawer drawer) {
        if (points == null || points.isEmpty() || canvas == null) {
            return drawer.draw(buffer, matrix, x1, y1, x2, y2, color, highlight);
        }
        int drawn = 0;
        double x = x1;
        double y = y1;
        for (int[] pt : points) {
            double px = canvas.screenX(pt[0]);
            double py = canvas.screenY(pt[1]);
            drawn += drawer.draw(buffer, matrix, x, y, px, py, color, highlight);
            x = px;
            y = py;
        }
        drawn += drawer.draw(buffer, matrix, x, y, x2, y2, color, highlight);
        return drawn;
    }

    // ---- Cutting wires (Shift+LMB or MMB) ----

    /**
     * Cuts a non-stock (colored or waiting) wire under the cursor. Returns true if a non-stock
     * wire was cut (stock wires fall through to {@code NpcDialogueGraphScreen}'s own cut handler).
     */
    public static boolean cutNonStockWire(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        if (screen == null || graph == null) return false;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        WireHit hit = scanNearestWire(screen, canvas, graph, mouseX, mouseY);
        if (hit == null || hit.kind == KIND_STOCK) return false;

        screen.settledlands$remember();
        boolean changed = false;
        switch (hit.kind) {
            case KIND_VALUE -> {
                Node to = graph.get(hit.toId);
                if (to != null && hit.from != null) {
                    if (PursuitGraphNode272.isPursuitNode(to)
                            && PursuitGraphNode272.sourceId(to) == hit.from.id) {
                        PursuitGraphNode272.setSource(to, -1);
                        changed = true;
                    } else if (PursuitGraphNode272.isValueInputNode(to)
                            && PursuitGraphNode272.inputSource(to) == hit.from.id) {
                        PursuitGraphNode272.setInputSource(to, -1);
                        changed = true;
                    } else if (PursuitGraphNode272.isPursuitNode(hit.from)
                            && PursuitGraphNode272.consumers(hit.from).contains(to.id)) {
                        PursuitGraphNode272.toggleConsumer(hit.from, to.id);
                        changed = true;
                    }
                    if (changed) NpcWirePoints321.clearValueWire(hit.from, to.id);
                }
            }
            case KIND_ORANGE -> {
                Node to = graph.get(hit.toId);
                if (to != null && hit.from != null && to.varInputNode == hit.from.id) {
                    to.varInputNode = -1;
                    NpcWirePoints321.clearCustomWire(hit.from, KIND_ORANGE, to.id);
                    changed = true;
                }
            }
            case KIND_AMBER -> {
                if (hit.from != null && CardEventPin398.isCard(hit.from)) {
                    CardEventPin398.setSecondChain(hit.from, -1);
                    NpcWirePoints321.clearCustomWire(hit.from, KIND_AMBER, hit.toId);
                    changed = true;
                }
            }
            case KIND_TRIAL_START -> {
                if (hit.from != null && TrialGraph446.isTrialNode(hit.from)) {
                    TrialGraph446.setStartWaveTarget(hit.from, -1);
                    NpcWirePoints321.clearCustomWire(hit.from, KIND_TRIAL_START, hit.toId);
                    TrialModule447.syncCardConfig(graph);
                    changed = true;
                }
            }
            case KIND_TRIAL_MOD -> {
                if (hit.from != null && TrialModule447.isModuleNode(hit.from)) {
                    TrialModule447.disconnectModule(graph, hit.from.id);
                    NpcWirePoints321.clearCustomWire(hit.from, KIND_TRIAL_MOD, hit.toId);
                    TrialModuleClient447.requestInspectorRebuild();
                    changed = true;
                }
            }
            case KIND_HANG_WHITE -> {
                changed = NpcWirePoints321.removeHang(hit.from, hit.fromPort, hit.endX, hit.endY);
            }
            case KIND_HANG_COLOR -> {
                changed = NpcWirePoints321.removeColorHang(hit.from, (char) hit.fromPort,
                        hit.arg, hit.endX, hit.endY);
            }
            default -> {
            }
        }
        if (changed) {
            invalidateCache();
            screen.settledlands$changed();
        }
        return changed;
    }

    // ---- Color helpers for waiting hangs & point-port pulling ----

    public static double[] colorHangStart(NpcGraphCanvas canvas, Node from, char kind, int arg) {
        if (canvas == null || from == null) return null;
        return switch (kind) {
            case KIND_VALUE -> {
                if (PursuitGraphNode272.isPursuitNode(from)) {
                    yield new double[] {
                        PursuitGraphNode272.targetPinScreenX(canvas, from),
                        PursuitGraphNode272.targetPinScreenY(canvas, from)
                    };
                }
                yield new double[] {
                    PursuitGraphNode272.checkPinScreenX(canvas, from),
                    PursuitGraphNode272.checkPinScreenY(canvas, from)
                };
            }
            case KIND_ORANGE -> new double[] {
                VarDataCard453.orangeOutScreenX(canvas, from),
                VarDataCard453.orangeOutScreenY(canvas, from)
            };
            case KIND_AMBER -> new double[] {
                CardEventPin398.pinScreenX(canvas, from),
                CardEventPin398.pinScreenY(canvas, from)
            };
            case KIND_TRIAL_START -> new double[] {
                TrialCard446.pinScreenX(canvas, from),
                TrialCard446.pinScreenY(canvas, from)
            };
            case KIND_TRIAL_MOD -> new double[] {
                TrialModuleCard447.outputPinScreenX(canvas, from),
                TrialModuleCard447.outputPinScreenY(canvas, from)
            };
            default -> null;
        };
    }

    public static int colorForKind(char kind, int arg) {
        return switch (kind) {
            case KIND_VALUE -> PursuitGraphNode272.COL_VALUE_WIRE;
            case KIND_ORANGE -> VarDataNode453.PIN_COLOR;
            case KIND_AMBER -> CardEventPin398.COL_AMBER_WIRE;
            case KIND_TRIAL_START -> TrialCard446.COL_START_WIRE;
            case KIND_TRIAL_MOD -> TrialModule447.color(arg);
            default -> PursuitGraphNode272.COL_CABLE_WHITE;
        };
    }

    /** Picks the wire color when pulling from a bend point's right port. */
    public static int pullColorAtPoint(NpcDialogueGraph graph, int x, int y) {
        if (graph == null) return PursuitGraphNode272.COL_CABLE_WHITE;
        List<NpcWirePoints321.ColorHang> chs = NpcWirePoints321.colorHangsAt(graph, x, y);
        if (!chs.isEmpty()) {
            NpcWirePoints321.ColorHang first = chs.get(0);
            return colorForKind(first.kind, first.arg);
        }
        List<NpcWirePoints321.ColorBend> cbs = NpcWirePoints321.customBendsAt(graph, x, y);
        if (!cbs.isEmpty()) {
            NpcWirePoints321.ColorBend first = cbs.get(0);
            int arg = first.kind == KIND_TRIAL_MOD ? TrialModule447.slotOf(first.from) : 0;
            return colorForKind(first.kind, arg);
        }
        if (!NpcWirePoints321.valueBendsAt(graph, x, y).isEmpty()) {
            return PursuitGraphNode272.COL_VALUE_WIRE;
        }
        return PursuitGraphNode272.COL_CABLE_WHITE;
    }

    /** Promotes / retargets all colored wires sitting at bend point (x, y) toward {@code targetId}. */
    public static int aimColoredAtPoint(NpcDialogueGraph graph, int x, int y, int targetId) {
        if (graph == null || targetId < 0) return 0;
        Node target = graph.get(targetId);
        if (target == null || target.special != NpcDialogueGraph.NORMAL) return 0;
        int done = 0;

        // Snapshot existing bends BEFORE promoting hangs so promoted wires are not counted twice.
        List<NpcWirePoints321.ColorHang> hangs = new ArrayList<>(NpcWirePoints321.colorHangsAt(graph, x, y));
        List<NpcWirePoints321.Bend> valueBends = new ArrayList<>(NpcWirePoints321.valueBendsAt(graph, x, y));
        List<NpcWirePoints321.ColorBend> customBends = new ArrayList<>(NpcWirePoints321.customBendsAt(graph, x, y));

        for (NpcWirePoints321.ColorHang ch : hangs) {
            if (ch.from == null || ch.from.id == targetId) continue;
            if (connectColoredWire(graph, ch.from, ch.kind, target)) {
                if (NpcWirePoints321.promoteColor(ch.from, ch.kind, ch.arg, x, y, targetId)) {
                    done++;
                }
            }
        }
        for (NpcWirePoints321.Bend vb : valueBends) {
            if (vb.from == null || vb.from.id == targetId) continue;
            if (vb.toId == targetId) {
                done++;
                continue;
            }
            if (!isBlueAlive(graph, vb.from, vb.toId)) continue;
            if (connectColoredWire(graph, vb.from, KIND_VALUE, target)) {
                disconnectBlueSingle(vb.from, graph.get(vb.toId));
                NpcWirePoints321.retargetColor(vb.from, KIND_VALUE, vb.toId, targetId);
                done++;
            }
        }
        for (NpcWirePoints321.ColorBend cb : customBends) {
            if (cb.from == null || cb.from.id == targetId) continue;
            if (cb.toId == targetId) {
                done++;
                continue;
            }
            if (!isCustomAlive(graph, cb.from, cb.kind, cb.toId)) continue;
            if (connectColoredWire(graph, cb.from, cb.kind, target)) {
                disconnectCustomSingle(graph, cb.from, cb.kind, cb.toId);
                NpcWirePoints321.retargetColor(cb.from, cb.kind, cb.toId, targetId);
                done++;
            }
        }
        if (done > 0) invalidateCache();
        return done;
    }

    public static boolean connectColoredWire(NpcDialogueGraph graph, Node from, char kind, Node target) {
        if (graph == null || from == null || target == null || from.id == target.id) return false;
        return switch (kind) {
            case KIND_VALUE -> {
                if (PursuitGraphNode272.isValueSourceNode(from)) {
                    if (PursuitGraphNode272.isPursuitNode(target)) {
                        PursuitGraphNode272.setSource(target, from.id);
                        yield true;
                    }
                    if (PursuitGraphNode272.isValueInputNode(target)) {
                        PursuitGraphNode272.setInputSource(target, from.id);
                        yield true;
                    }
                    yield false;
                }
                if (PursuitGraphNode272.isPursuitNode(from)) {
                    if (PursuitGraphNode272.isPursuitNode(target)) {
                        PursuitGraphNode272.setSource(target, from.id);
                        yield true;
                    }
                    if (!PursuitGraphNode272.consumers(from).contains(target.id)) {
                        PursuitGraphNode272.toggleConsumer(from, target.id);
                    }
                    yield true;
                }
                yield false;
            }
            case KIND_ORANGE -> {
                if (VarDataNode453.isOrangeSourceNode(from) && VarDataNode453.isOrangeInputNode(target)) {
                    target.varInputNode = from.id;
                    yield true;
                }
                yield false;
            }
            case KIND_AMBER -> {
                if (CardEventPin398.isCard(from) && !TrialGraph446.isTrialNode(target)) {
                    if (!TrialModule447.isModuleNode(target)
                            || TrialModule447.supportsConditionalInput(target)) {
                        CardEventPin398.setSecondChain(from, target.id);
                        TrialModule447.syncCardConfig(graph);
                        yield true;
                    }
                }
                yield false;
            }
            case KIND_TRIAL_START -> {
                if (TrialGraph446.isTrialNode(from) && !TrialGraph446.isTrialNode(target)) {
                    if (!TrialModule447.isModuleNode(target)
                            || TrialModule447.supportsConditionalInput(target)) {
                        TrialGraph446.setStartWaveTarget(from, target.id);
                        TrialModule447.syncCardConfig(graph);
                        yield true;
                    }
                }
                yield false;
            }
            case KIND_TRIAL_MOD -> {
                if (TrialModule447.isModuleNode(from) && TrialGraph446.isTrialNode(target)) {
                    TrialModule447.connectModule(graph, from.id, target.id);
                    TrialModuleClient447.requestInspectorRebuild();
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private static void disconnectBlueSingle(Node from, Node oldTo) {
        if (from == null || oldTo == null) return;
        if (PursuitGraphNode272.isPursuitNode(oldTo) && PursuitGraphNode272.sourceId(oldTo) == from.id) {
            PursuitGraphNode272.setSource(oldTo, -1);
        } else if (PursuitGraphNode272.isValueInputNode(oldTo)
                && PursuitGraphNode272.inputSource(oldTo) == from.id) {
            PursuitGraphNode272.setInputSource(oldTo, -1);
        } else if (PursuitGraphNode272.isPursuitNode(from)
                && PursuitGraphNode272.consumers(from).contains(oldTo.id)) {
            PursuitGraphNode272.toggleConsumer(from, oldTo.id);
        }
    }

    private static void disconnectCustomSingle(NpcDialogueGraph graph, Node from, char kind, int oldToId) {
        if (from == null || oldToId < 0) return;
        switch (kind) {
            case KIND_ORANGE -> {
                Node oldTo = graph.get(oldToId);
                if (oldTo != null && oldTo.varInputNode == from.id) oldTo.varInputNode = -1;
            }
            case KIND_AMBER -> {
                if (CardEventPin398.isCard(from) && CardEventPin398.secondChain(from) == oldToId) {
                    CardEventPin398.setSecondChain(from, -1);
                }
            }
            case KIND_TRIAL_START -> {
                if (TrialGraph446.isTrialNode(from) && TrialGraph446.startWaveTarget(from) == oldToId) {
                    TrialGraph446.setStartWaveTarget(from, -1);
                }
            }
            case KIND_TRIAL_MOD -> {
                if (TrialModule447.isModuleNode(from) && from.next == oldToId) {
                    TrialModule447.disconnectModule(graph, from.id);
                }
            }
            default -> {
            }
        }
    }
}
