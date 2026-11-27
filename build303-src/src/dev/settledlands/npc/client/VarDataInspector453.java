package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.VarDataNode453;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Build 453/456: inspector panels for the Variable node ({@link VarDataNode453}, "Переменная")
 * and enhanced {@link NpcDialogueGraph#VARIABLE} ("Мат выражение") / {@link NpcDialogueGraph#IF}
 * nodes (all 14 math operations + orange variable/numeric input status + blue entity/target input status).
 */
public final class VarDataInspector453 {

    public interface Host {
        void settledlands$remember453();
        void settledlands$changed453();
        void settledlands$widgets453();
        void settledlands$propertyChange453(Runnable action);
        void settledlands$label453(String key, int x, int y);
        EditBox settledlands$textBox453(String initial, int maxLen, int x, int y,
                int width, int height, Consumer<String> onChange);
        void settledlands$numberBox453(int value, int min, int max, int x, int y,
                int width, int height, Consumer<Integer> setter);
        Button settledlands$button453(String key, int x, int y, int width, int height,
                Runnable action);
        AbstractWidget settledlands$addDropdown453(NpcUi.NpcDropdown dropdown);
        void settledlands$deleteNode453(int nodeId);
        void settledlands$unlinkNode453(int nodeId);
        void settledlands$addInspectorWidget453(AbstractWidget w);
        void settledlands$setBuildingInspector453(boolean building);
    }

    private static Field fLabelKey;
    private static Field fLabelY;
    private static boolean labelFieldsProbed;

    private VarDataInspector453() {}

    // ---- Inspector for the Variable data node ("Переменная") ----

    public static int buildVarDataInspector(
            NpcDialogueGraph graph,
            Node node,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            int sidebarScroll,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        if (graph == null || node == null || host == null) return 0;

        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        host.settledlands$setBuildingInspector453(true);
        try {
            // 1. Variable name
            host.settledlands$label453("gui.settledlands.var453.name", x, y);
            y += 11;
            host.settledlands$textBox453(
                    node.pointsName == null ? "" : node.pointsName, 32,
                    x, y, width, 16,
                    val -> host.settledlands$propertyChange453(() -> {
                        node.pointsName = val == null ? "" : val.trim();
                        host.settledlands$changed453();
                    }));
            y += 22;

            // 2. Numeric value (integers only)
            host.settledlands$label453("gui.settledlands.var453.value", x, y);
            y += 11;
            host.settledlands$numberBox453(
                    node.varValue, -1_000_000, 1_000_000,
                    x, y, width, 16,
                    val -> host.settledlands$propertyChange453(() -> {
                        node.varValue = val == null ? 0 : val;
                        host.settledlands$changed453();
                    }));
            y += 22;

            // 3. Scope selector (Personal / Shared / Local) - clean 3 mutually-exclusive toggles
            host.settledlands$label453("gui.settledlands.var453.scope", x, y);
            y += 11;
            int scope = VarDataNode453.scopeOf(node);
            y = addScopeBtn(host, node, VarDataNode453.SCOPE_PERSONAL,
                    scope == VarDataNode453.SCOPE_PERSONAL,
                    "gui.settledlands.var453.btn_personal", x, y, width);
            y = addScopeBtn(host, node, VarDataNode453.SCOPE_SHARED,
                    scope == VarDataNode453.SCOPE_SHARED,
                    "gui.settledlands.var453.btn_shared", x, y, width);
            y = addScopeBtn(host, node, VarDataNode453.SCOPE_LOCAL,
                    scope == VarDataNode453.SCOPE_LOCAL,
                    "gui.settledlands.var453.btn_local", x, y, width);
            y += 4;

            // 4. Incoming orange & blue inputs + outgoing connections
            Node orangeIn = VarDataNode453.resolveOrangeSource(graph, node);
            if (orangeIn != null) {
                String srcName = (orangeIn.pointsName != null && !orangeIn.pointsName.isBlank())
                        ? orangeIn.pointsName : String.valueOf(orangeIn.varValue);
                String txt = Component.translatable("gui.settledlands.var453.orange_linked_in",
                        orangeIn.id, srcName).getString();
                host.settledlands$label453(txt, x, y);
                y += 12;
                host.settledlands$button453("gui.settledlands.var453.btn_unlink_orange",
                        x, y, width, 16, () -> {
                            host.settledlands$remember453();
                            node.varInputNode = -1;
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                y += 20;
            } else {
                host.settledlands$label453("gui.settledlands.var453.orange_none", x, y);
                y += 14;
            }

            int blueInId = VarDataCard453.findBlueSourceId(graph, node);
            if (blueInId >= 0) {
                String txt = Component.translatable("gui.settledlands.var453.blue_linked",
                        blueInId).getString();
                host.settledlands$label453(txt, x, y);
                y += 12;
                host.settledlands$button453("gui.settledlands.var453.btn_unlink_blue",
                        x, y, width, 16, () -> {
                            host.settledlands$remember453();
                            VarDataNode453.setEntityInput(node, -1);
                            if (graph.nodes != null) {
                                for (Node cand : graph.nodes.values()) {
                                    if (PursuitGraphNode272.isPursuitNode(cand)
                                            && PursuitGraphNode272.consumers(cand).contains(node.id)) {
                                        PursuitGraphNode272.toggleConsumer(cand, node.id);
                                    }
                                }
                            }
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                y += 20;
            } else {
                host.settledlands$label453("gui.settledlands.var453.blue_none", x, y);
                y += 14;
            }

            int wiredCount = VarDataNode453.wiredOrangeConsumers(graph, node.id);
            String linkLine = wiredCount > 0
                    ? Component.translatable("gui.settledlands.var453.wired_count", wiredCount).getString()
                    : Component.translatable("gui.settledlands.var453.wired_none").getString();
            host.settledlands$label453(linkLine, x, y);
            y += 12;

            if (wiredCount > 0) {
                host.settledlands$button453(
                        "gui.settledlands.var453.btn_unlink", x, y, width, 16,
                        () -> {
                            host.settledlands$remember453();
                            VarDataNode453.unplugOrangeFrom(graph, node.id);
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                y += 20;
            }

            host.settledlands$button453(
                    "gui.settledlands.graph.delete", x, y, width, 16,
                    () -> host.settledlands$deleteNode453(node.id));
            y += 20;

            for (int i = startIndex; i < inspectorWidgets.size(); i++) {
                AbstractWidget w = inspectorWidgets.get(i);
                w.visible = w.getY() >= inspectorY + 22
                        && w.getY() + w.getHeight() <= inspectorY + inspectorH - 4;
            }
        } finally {
            host.settledlands$setBuildingInspector453(false);
        }
        return (y - (inspectorY + 24 - sidebarScroll)) + 4;
    }

    private static int addScopeBtn(Host host, Node node, int scopeValue, boolean active,
            String key, int x, int y, int width) {
        Button btn = host.settledlands$button453(key, x, y, width, 16, () -> {
            host.settledlands$remember453();
            VarDataNode453.setScope(node, scopeValue);
            host.settledlands$changed453();
            host.settledlands$widgets453();
        });
        if (btn != null) {
            String prefix = active ? "\u2713 " : "  ";
            btn.setMessage(Component.literal(prefix + Component.translatable(key).getString()));
        }
        return y + 18;
    }

    // ---- Enhances stock VARIABLE (4) & IF (3) inspector rows ----

    public static int enhanceStockVariableAndIf(
            NpcDialogueGraph graph,
            Node node,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            int sidebarScroll,
            int sidebarHeight,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        if (graph == null || node == null || host == null) return sidebarHeight;

        if (node.type == NpcDialogueGraph.VARIABLE) {
            return enhanceVariable(graph, node, inspectorX, inspectorY, inspectorW, inspectorH,
                    sidebarHeight, labels, inspectorWidgets, host);
        }
        if (node.type == NpcDialogueGraph.IF) {
            return enhanceIf(graph, node, inspectorX, inspectorY, inspectorW, inspectorH,
                    sidebarScroll, sidebarHeight, labels, inspectorWidgets, host);
        }
        return sidebarHeight;
    }

    private static int enhanceVariable(
            NpcDialogueGraph graph,
            Node node,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            int sidebarHeight,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        // Locate the var_delta label created by stock widgets(), then shift all subsequent rows down.
        Object deltaLbl = findLabel(labels, "gui.settledlands.graph.var_delta");
        int deltaLblY = deltaLbl == null ? Integer.MIN_VALUE : labelY(deltaLbl);
        int insertY = deltaLblY != Integer.MIN_VALUE ? deltaLblY + 31 : (inspectorY + 24 + 52);

        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);

        // Compute inserted height: MathOp (11 + 20 = 31) + Orange input (11 + optional 20 or 14) + Blue input (11 + optional 20 or 14)
        Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        int blueId = VarDataNode453.entityInput(graph, node);
        Node blueSrc = blueId >= 0 ? graph.get(blueId) : null;

        int insertH = 31; // Math Operation label + dropdown
        insertH += (orangeSrc != null ? 33 : 22);
        insertH += (blueSrc != null ? 33 : 22);

        // Shift existing inspector widgets & labels that sit at or below insertY
        for (AbstractWidget w : inspectorWidgets) {
            if (w != null && w.getY() >= insertY - 2) {
                w.setY(w.getY() + insertH);
            }
        }
        shiftLabelsFrom(labels, insertY - 2, insertH);

        host.settledlands$setBuildingInspector453(true);
        try {
            int cy = insertY;
            // 1. Math operation dropdown (14 operations)
            host.settledlands$label453("gui.settledlands.var453.op_label", x, cy);
            cy += 11;
            List<Component> opItems = new ArrayList<>(VarDataNode453.OP_COUNT);
            for (int op = 0; op < VarDataNode453.OP_COUNT; op++) {
                opItems.add(Component.translatable(VarDataNode453.opLabelKey(op)));
            }
            NpcUi.NpcDropdown dd = NpcUi.dropdown(x, cy, width, opItems,
                    VarDataNode453.normalizeOp(node.varOp),
                    picked -> {
                        host.settledlands$remember453();
                        node.varOp = VarDataNode453.normalizeOp(picked);
                        host.settledlands$changed453();
                        host.settledlands$widgets453();
                    });
            host.settledlands$addDropdown453(dd);
            cy += 20;

            // 2. Orange input status & unlink
            if (orangeSrc != null) {
                String srcName = (orangeSrc.pointsName != null && !orangeSrc.pointsName.isBlank())
                        ? orangeSrc.pointsName : String.valueOf(orangeSrc.varValue);
                boolean hasOwn = node.pointsName != null && !node.pointsName.isBlank();
                String modeKey = hasOwn
                        ? "gui.settledlands.var453.orange_operand"
                        : "gui.settledlands.var453.orange_target";
                String txt = Component.translatable(modeKey, orangeSrc.id, srcName).getString();
                host.settledlands$label453(txt, x, cy);
                cy += 12;
                host.settledlands$button453("gui.settledlands.var453.btn_unlink_orange",
                        x, cy, width, 16, () -> {
                            host.settledlands$remember453();
                            node.varInputNode = -1;
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                cy += 21;
            } else {
                host.settledlands$label453("gui.settledlands.var453.orange_none", x, cy);
                cy += 22;
            }

            // 3. Blue input status & unlink
            if (blueSrc != null) {
                String txt = Component.translatable("gui.settledlands.var453.blue_linked",
                        blueSrc.id).getString();
                host.settledlands$label453(txt, x, cy);
                cy += 12;
                host.settledlands$button453("gui.settledlands.var453.btn_unlink_blue",
                        x, cy, width, 16, () -> {
                            host.settledlands$remember453();
                            VarDataNode453.setEntityInput(node, -1);
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                cy += 21;
            } else {
                host.settledlands$label453("gui.settledlands.var453.blue_none", x, cy);
                cy += 22;
            }
        } finally {
            host.settledlands$setBuildingInspector453(false);
        }

        // Recompute visibility for all inspector widgets against inspectorH
        for (AbstractWidget w : inspectorWidgets) {
            if (w == null) continue;
            w.visible = w.getY() >= inspectorY + 22
                    && w.getY() + w.getHeight() <= inspectorY + inspectorH - 4;
        }
        return sidebarHeight + insertH;
    }

    private static int enhanceIf(
            NpcDialogueGraph graph,
            Node node,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            int sidebarScroll,
            int sidebarHeight,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        int blueId = VarDataNode453.entityInput(graph, node);
        Node blueSrc = blueId >= 0 ? graph.get(blueId) : null;
        if (blueSrc == null) return sidebarHeight;

        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        host.settledlands$setBuildingInspector453(true);
        try {
            String txt = Component.translatable("gui.settledlands.var453.blue_linked",
                    blueSrc.id).getString();
            host.settledlands$label453(txt, x, y);
            y += 12;
            Button unlink = host.settledlands$button453("gui.settledlands.var453.btn_unlink_blue",
                    x, y, width, 16, () -> {
                        host.settledlands$remember453();
                        VarDataNode453.setEntityInput(node, -1);
                        host.settledlands$changed453();
                        host.settledlands$widgets453();
                    });
            if (unlink != null) {
                unlink.visible = y >= inspectorY + 22
                        && y + unlink.getHeight() <= inspectorY + inspectorH - 4;
            }
        } finally {
            host.settledlands$setBuildingInspector453(false);
        }
        return sidebarHeight + 34;
    }

    private static Object findLabel(List<?> labels, String key) {
        if (labels == null || key == null) return null;
        probeLabelFields(labels);
        if (fLabelKey == null) return null;
        for (Object lbl : labels) {
            if (lbl == null) continue;
            try {
                if (key.equals(fLabelKey.get(lbl))) return lbl;
            } catch (IllegalAccessException ignored) {}
        }
        return null;
    }

    private static int labelY(Object lbl) {
        if (lbl == null || fLabelY == null) return Integer.MIN_VALUE;
        try {
            return fLabelY.getInt(lbl);
        } catch (IllegalAccessException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static void shiftLabelsFrom(List<?> labels, int minY, int delta) {
        if (labels == null || delta == 0) return;
        probeLabelFields(labels);
        if (fLabelY == null) return;
        for (Object lbl : labels) {
            if (lbl == null) continue;
            try {
                int ly = fLabelY.getInt(lbl);
                if (ly >= minY) {
                    fLabelY.setInt(lbl, ly + delta);
                }
            } catch (IllegalAccessException ignored) {}
        }
    }

    private static void probeLabelFields(List<?> labels) {
        if (labelFieldsProbed) return;
        for (Object lbl : labels) {
            if (lbl == null) continue;
            try {
                fLabelKey = lbl.getClass().getDeclaredField("key");
                fLabelKey.setAccessible(true);
                fLabelY = lbl.getClass().getDeclaredField("y");
                fLabelY.setAccessible(true);
                labelFieldsProbed = true;
                return;
            } catch (NoSuchFieldException ignored) {}
        }
    }
}
