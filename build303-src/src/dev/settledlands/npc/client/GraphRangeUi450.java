package dev.settledlands.npc.client;

import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.trial.RangeSpec450;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Build 450: adapts stock {@link NpcDialogueGraphScreen} inspector rows after {@code widgets()}
 * so single numeric inputs accept {@code "a-b"} ranges and min/max pairs ({@code RANDOM},
 * {@code BUTTON}) collapse into one unified range field without duplicating UI.
 */
public final class GraphRangeUi450 {

    public interface Host {
        EditBox settledlands$createRangeBox450(String initial, int maxLen, int x, int y,
                int width, int height, Consumer<String> setter);

        void settledlands$runPropertyChange450(Runnable action);
    }

    private static final Predicate<String> SIGNED_INT_RANGE =
            s -> RangeSpec450.isIntRangeInput(s, true);
    private static final Predicate<String> UNSIGNED_INT_RANGE =
            s -> RangeSpec450.isIntRangeInput(s, false);

    private static Field fLabelKey;
    private static Field fLabelY;
    private static boolean labelFieldsProbed;

    private GraphRangeUi450() {}

    public static void apply(
            NpcDialogueGraphScreen screen,
            NpcDialogueGraph graph,
            int selected,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        if (screen == null || graph == null || selected < 0
                || labels == null || inspectorWidgets == null || host == null) {
            return;
        }
        NpcDialogueGraph.Node node = graph.get(selected);
        if (node == null) return;

        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);

        if (node.type == NpcDialogueGraph.RANDOM) {
            collapseRandomRange(screen, node, inspectorY, inspectorH,
                    x, width, labels, inspectorWidgets, host);
            return;
        }
        if (node.type == NpcDialogueGraph.BUTTON) {
            collapseButtonRanges(screen, node, inspectorY, inspectorH,
                    x, width, labels, inspectorWidgets, host);
            return;
        }

        switch (node.type) {
            case NpcDialogueGraph.IF -> adaptScalarBox(node, GraphRanges450.KEY_IF_COMPARE,
                    "gui.settledlands.graph.var_compare", node.varCompare,
                    -1000000, 1000000, true, "5-10", labels, inspectorWidgets, host,
                    v -> node.varCompare = v);
            case NpcDialogueGraph.VARIABLE -> adaptScalarBox(node, GraphRanges450.KEY_VAR_VALUE,
                    "gui.settledlands.graph.var_delta", node.varValue,
                    -1000000, 1000000, true, "1-5", labels, inspectorWidgets, host,
                    v -> node.varValue = v);
            case NpcDialogueGraph.CHECK -> {
                adaptScalarBox(node, GraphRanges450.KEY_CHECK_RADIUS,
                        "gui.settledlands.graph.check_radius", node.checkRadius,
                        1, 64, false, "3-8", labels, inspectorWidgets, host,
                        v -> node.checkRadius = v);
                adaptScalarBox(node, GraphRanges450.KEY_CHECK_COOLDOWN,
                        "gui.settledlands.graph.check_cooldown", node.checkCooldown,
                        0, 3600, false, "5-15", labels, inspectorWidgets, host,
                        v -> node.checkCooldown = v);
            }
            case NpcDialogueGraph.DAMAGE -> {
                adaptScalarBox(node, GraphRanges450.KEY_MIN_DAMAGE,
                        "gui.settledlands.graph.min_damage", node.minDamage,
                        0, 1000, false, "1-5", labels, inspectorWidgets, host,
                        v -> node.minDamage = v);
                adaptScalarBox(node, GraphRanges450.KEY_DAMAGE_COOLDOWN,
                        "gui.settledlands.graph.check_cooldown", node.damageCooldown,
                        0, 3600, false, "5-15", labels, inspectorWidgets, host,
                        v -> node.damageCooldown = v);
            }
            case NpcDialogueGraph.TIME -> adaptScalarBox(node, GraphRanges450.KEY_TIME_TICKS,
                    "gui.settledlands.graph.time_ticks", node.timeTicks,
                    0, 23999, false, "1000-6000", labels, inspectorWidgets, host,
                    v -> node.timeTicks = v);
            case NpcDialogueGraph.TIMER -> adaptScalarBox(node, GraphRanges450.KEY_TIMER_INTERVAL,
                    "gui.settledlands.graph.timer_interval", node.timerInterval,
                    1, 86400, false, "5-15", labels, inspectorWidgets, host,
                    v -> node.timerInterval = v);
            case NpcDialogueGraph.RADIUS -> adaptScalarBox(node, GraphRanges450.KEY_RADIUS,
                    "gui.settledlands.graph.radius", node.radius,
                    1, 64, false, "4-10", labels, inspectorWidgets, host,
                    v -> node.radius = v);
            default -> {
            }
        }
    }

    private static void adaptScalarBox(
            NpcDialogueGraph.Node node,
            String rangeKey,
            String labelKey,
            int scalarValue,
            int minBound,
            int maxBound,
            boolean allowNegative,
            String hint,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host,
            IntConsumer fieldSetter) {
        Object lbl = findLabel(labels, labelKey);
        if (lbl == null) return;
        int lblY = labelY(lbl);
        if (lblY == Integer.MIN_VALUE) return;
        EditBox box = findEditBoxAtY(inspectorWidgets, lblY + 11, 0);
        if (box == null) return;

        String initial = GraphRanges450.getRangeText(node, rangeKey, scalarValue);
        box.setResponder(s -> {});
        box.setFilter(allowNegative ? SIGNED_INT_RANGE : UNSIGNED_INT_RANGE);
        box.setMaxLength(16);
        box.setHint(Component.literal(hint));
        box.setValue(initial);
        box.setResponder(raw -> host.settledlands$runPropertyChange450(() -> {
            int chosen = GraphRanges450.setRangeText(node, rangeKey, raw, scalarValue, minBound, maxBound);
            fieldSetter.accept(chosen);
        }));
    }

    private static void collapseRandomRange(
            NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node,
            int inspectorY,
            int inspectorH,
            int x,
            int width,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        Object lbl = findLabel(labels, "gui.settledlands.graph.random_range");
        if (lbl == null) return;
        int lblY = labelY(lbl);
        if (lblY == Integer.MIN_VALUE) return;
        int boxY = lblY + 11;
        EditBox first = findEditBoxAtY(inspectorWidgets, boxY, 0);
        EditBox second = findEditBoxAtY(inspectorWidgets, boxY, 1);
        if (first == null || second == null) return;

        parkBox(first, inspectorWidgets);
        parkBox(second, inspectorWidgets);

        Object outHeader = findLabel(labels, "gui.settledlands.graph.random_outputs");
        int outY = outHeader == null ? Integer.MIN_VALUE : labelY(outHeader);
        Object outputsLbl = outY == Integer.MIN_VALUE ? null : findLabelAtY(labels, outY + 11);

        String initial = RangeSpec450.formatInt(node.randomMin, node.randomMax);
        GraphTooltips448.recordLabel(screen, "gui.settledlands.graph.random_range", x, lblY);
        EditBox unified = host.settledlands$createRangeBox450(initial, 14, x, boxY, width, 16, val -> {
            RangeSpec450.IntRange r = RangeSpec450.parseInt(val, node.randomMin, node.randomMax, 1, 64);
            node.randomMin = r.min;
            node.randomMax = r.max;
            while (node.randomBranches.size() < node.randomMax) {
                node.randomBranches.add(-1);
            }
            while (node.randomBranches.size() > node.randomMax) {
                node.randomBranches.remove(node.randomBranches.size() - 1);
            }
            if (outputsLbl != null) {
                setLabelKey(outputsLbl, "1-" + node.randomMax + "  (" + node.randomMax + " "
                        + Component.translatable("gui.settledlands.graph.outputs").getString() + ")");
            }
        });
        if (unified != null) {
            unified.setFilter(UNSIGNED_INT_RANGE);
            unified.setHint(Component.literal("1-3"));
            unified.visible = boxY >= inspectorY + 22 && boxY + 16 <= inspectorY + inspectorH - 4;
        }
    }

    private static void collapseButtonRanges(
            NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node,
            int inspectorY,
            int inspectorH,
            int x,
            int width,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        collapsePair(screen, labels, inspectorWidgets, host, inspectorY, inspectorH, x, width,
                "gui.settledlands.graph.min_relation", "gui.settledlands.graph.max_relation",
                "gui.settledlands.graph.relation_range",
                RangeSpec450.formatInt(node.minRelation, node.maxRelation), 12, "0-100",
                val -> {
                    RangeSpec450.IntRange r = RangeSpec450.parseInt(val, node.minRelation, node.maxRelation, 0, 100);
                    node.minRelation = r.min;
                    node.maxRelation = r.max;
                });

        collapsePair(screen, labels, inspectorWidgets, host, inspectorY, inspectorH, x, width,
                "gui.settledlands.graph.min_points", "gui.settledlands.graph.max_points",
                "gui.settledlands.graph.points_range",
                RangeSpec450.formatInt(node.minPoints, node.maxPoints), 16, "0-100",
                val -> {
                    RangeSpec450.IntRange r = RangeSpec450.parseInt(val, node.minPoints, node.maxPoints, 0, 1000000);
                    node.minPoints = r.min;
                    node.maxPoints = r.max;
                });
    }

    private static void collapsePair(
            NpcDialogueGraphScreen screen,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host,
            int inspectorY,
            int inspectorH,
            int x,
            int width,
            String minLabelKey,
            String maxLabelKey,
            String unifiedLabelKey,
            String initialValue,
            int maxLen,
            String hint,
            Consumer<String> onValue) {
        Object minLbl = findLabel(labels, minLabelKey);
        if (minLbl == null) return;
        int lblY = labelY(minLbl);
        if (lblY == Integer.MIN_VALUE) return;
        int boxY = lblY + 11;

        EditBox first = findEditBoxAtY(inspectorWidgets, boxY, 0);
        EditBox second = findEditBoxAtY(inspectorWidgets, boxY, 1);
        if (first == null || second == null) return;

        parkBox(first, inspectorWidgets);
        parkBox(second, inspectorWidgets);

        setLabelKey(minLbl, unifiedLabelKey);
        Object maxLbl = findLabel(labels, maxLabelKey);
        if (maxLbl != null) {
            int maxY = labelY(maxLbl);
            labels.remove(maxLbl);
            GraphTooltips448.removeLabel(screen, maxLabelKey, maxY);
        }
        GraphTooltips448.replaceLabelKey(screen, minLabelKey, unifiedLabelKey, lblY);
        GraphTooltips448.recordLabel(screen, unifiedLabelKey, x, lblY);

        EditBox unified = host.settledlands$createRangeBox450(initialValue, maxLen, x, boxY, width, 16, onValue);
        if (unified != null) {
            unified.setFilter(UNSIGNED_INT_RANGE);
            unified.setHint(Component.literal(hint));
            unified.visible = boxY >= inspectorY + 22 && boxY + 16 <= inspectorY + inspectorH - 4;
        }
    }

    private static void parkBox(EditBox box, List<AbstractWidget> inspectorWidgets) {
        AbstractWidget aw = box;
        aw.visible = false;
        aw.setY(-2000);
        box.setEditable(false);
        inspectorWidgets.remove(box);
    }

    private static void ensureLabelFields(Object sample) {
        if (labelFieldsProbed || sample == null) return;
        labelFieldsProbed = true;
        try {
            Class<?> c = sample.getClass();
            fLabelKey = c.getDeclaredField("key");
            fLabelKey.setAccessible(true);
            fLabelY = c.getDeclaredField("y");
            fLabelY.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    private static String labelKey(Object lbl) {
        ensureLabelFields(lbl);
        if (lbl == null || fLabelKey == null) return null;
        try {
            Object v = fLabelKey.get(lbl);
            return v instanceof String s ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setLabelKey(Object lbl, String key) {
        ensureLabelFields(lbl);
        if (lbl == null || fLabelKey == null) return;
        try {
            fLabelKey.set(lbl, key);
        } catch (Throwable ignored) {
        }
    }

    private static int labelY(Object lbl) {
        ensureLabelFields(lbl);
        if (lbl == null || fLabelY == null) return Integer.MIN_VALUE;
        try {
            return fLabelY.getInt(lbl);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static Object findLabel(List<?> labels, String key) {
        for (int i = 0; i < labels.size(); i++) {
            Object l = labels.get(i);
            if (l != null && key.equals(labelKey(l))) {
                return l;
            }
        }
        return null;
    }

    private static Object findLabelAtY(List<?> labels, int y) {
        for (int i = 0; i < labels.size(); i++) {
            Object l = labels.get(i);
            if (l != null && labelY(l) == y) {
                return l;
            }
        }
        return null;
    }

    private static EditBox findEditBoxAtY(List<AbstractWidget> widgets, int y, int matchIndex) {
        int seen = 0;
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget w = widgets.get(i);
            if (w instanceof EditBox eb && eb.getY() == y) {
                if (seen == matchIndex) return eb;
                seen++;
            }
        }
        return null;
    }
}
