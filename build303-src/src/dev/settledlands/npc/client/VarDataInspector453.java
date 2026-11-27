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
 * Build 453/456/457: inspector panels for the Variable node ({@link VarDataNode453}, "Переменная"),
 * enhanced {@link NpcDialogueGraph#VARIABLE} ("Мат выражение") / {@link NpcDialogueGraph#IF}
 * ("Условие") with integrated World Variable Catalog picker &amp; inline gear button, and blue
 * variable status/unlink controls on {@code LINE}/{@code BUTTON}/{@code TRIGGER} nodes.
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
        void settledlands$addRenderable453(AbstractWidget widget);
        int settledlands$entityId453();
        NpcDialogueGraphScreen settledlands$screen453();
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
        ensureCatalogSynced(host);

        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        host.settledlands$setBuildingInspector453(true);
        try {
            // 1. Variable name + catalog gear button
            host.settledlands$label453("gui.settledlands.var453.name", x, y);
            y += 11;
            int boxW = Math.max(20, width - 19);
            host.settledlands$textBox453(
                    node.pointsName == null ? "" : node.pointsName, 32,
                    x, y, boxW, 16,
                    val -> host.settledlands$propertyChange453(() -> {
                        node.pointsName = val == null ? "" : val.trim();
                        host.settledlands$changed453();
                    }));
            Consumer<VarCatalogClient457.Entry> onPick = picked -> applyPickedCatalogEntry(host, node, picked);
            LootPoolGearButton440 gear = new LootPoolGearButton440(
                    x + width - 17, y, 17, 16,
                    Component.translatable("gui.settledlands.var_catalog.gear_tip"),
                    VarCatalogClient457.ACCENT_COLOR,
                    () -> VarCatalogClient457.openCatalogEditor(
                            host.settledlands$screen453(), node,
                            node.pointsName == null ? "" : node.pointsName,
                            VarDataNode453.scopeOf(node), false, onPick));
            host.settledlands$addRenderable453(gear);
            y += 18;

            // 1b. World variable catalog dropdown
            addCatalogDropdown(host, node, x, y, width, onPick);
            y += 20;

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

            // 3. Scope selector (Local / Personal / Shared)
            host.settledlands$label453("gui.settledlands.var453.scope", x, y);
            y += 11;
            int scope = VarDataNode453.scopeOf(node);
            y = addScopeBtn(host, node, VarDataNode453.SCOPE_LOCAL,
                    scope == VarDataNode453.SCOPE_LOCAL,
                    "gui.settledlands.var453.btn_local", x, y, width);
            y = addScopeBtn(host, node, VarDataNode453.SCOPE_PERSONAL,
                    scope == VarDataNode453.SCOPE_PERSONAL,
                    "gui.settledlands.var453.btn_personal", x, y, width);
            y = addScopeBtn(host, node, VarDataNode453.SCOPE_SHARED,
                    scope == VarDataNode453.SCOPE_SHARED,
                    "gui.settledlands.var453.btn_shared", x, y, width);
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
                            WireHover453.invalidateCache();
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
                            WireHover453.invalidateCache();
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
                            WireHover453.invalidateCache();
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                y += 20;
            }

            int blueOutCount = PursuitGraphNode272.wiredTo(graph, node.id);
            if (blueOutCount > 0) {
                String blueOutLine = Component.translatable("gui.settledlands.var453.blue_out_count",
                        blueOutCount).getString();
                host.settledlands$label453(blueOutLine, x, y);
                y += 12;
                host.settledlands$button453(
                        "gui.settledlands.var453.btn_unlink_blue_out", x, y, width, 16,
                        () -> {
                            host.settledlands$remember453();
                            PursuitGraphNode272.unplugFrom(graph, node.id);
                            WireHover453.invalidateCache();
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

    // ---- Enhances stock VARIABLE (4), IF (3), and blue-input nodes ----

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
                    sidebarScroll, sidebarHeight, labels, inspectorWidgets, host);
        }
        if (node.type == NpcDialogueGraph.IF) {
            return enhanceIf(graph, node, inspectorX, inspectorY, inspectorW, inspectorH,
                    sidebarScroll, sidebarHeight, labels, inspectorWidgets, host);
        }
        if (node.type == NpcDialogueGraph.LINE || node.type == NpcDialogueGraph.BUTTON
                || node.type == NpcDialogueGraph.TRIGGER) {
            return enhanceBlueInputNode(graph, node, inspectorX, inspectorY, inspectorW, inspectorH,
                    sidebarScroll, sidebarHeight, inspectorWidgets, host);
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
            int sidebarScroll,
            int sidebarHeight,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        ensureCatalogSynced(host);
        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);

        // 1. Attach catalog gear button + dropdown under the var_name EditBox
        sidebarHeight = injectCatalogVarPicker(node, x, width, sidebarHeight, labels, inspectorWidgets, host);

        // 2. Locate the var_delta label created by stock widgets(), then shift all subsequent rows down.
        Object deltaLbl = findLabel(labels, "gui.settledlands.graph.var_delta");
        int deltaLblY = deltaLbl == null ? Integer.MIN_VALUE : labelY(deltaLbl);
        int insertY = deltaLblY != Integer.MIN_VALUE ? deltaLblY + 31 : (inspectorY + 24 + 72);

        Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        int blueId = VarDataNode453.entityInput(graph, node);
        Node blueSrc = blueId >= 0 ? graph.get(blueId) : null;

        int insertH = 51; // Local-scope toggle (20) + Math Operation label & dropdown (31)
        insertH += (orangeSrc != null ? 33 : 22);
        insertH += (blueSrc != null ? 33 : 22);

        for (AbstractWidget w : inspectorWidgets) {
            if (w != null && w.getY() >= insertY - 2) {
                w.setY(w.getY() + insertH);
            }
        }
        shiftLabelsFrom(labels, insertY - 2, insertH);

        host.settledlands$setBuildingInspector453(true);
        try {
            int cy = insertY;
            // Scope quick-toggle (Local vs Shared/Personal)
            cy = addLocalScopeToggle(host, node, x, cy, width);

            // Math operation dropdown (14 operations)
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

            // Orange input status & unlink
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
                            WireHover453.invalidateCache();
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                cy += 21;
            } else {
                host.settledlands$label453("gui.settledlands.var453.orange_none", x, cy);
                cy += 22;
            }

            // Blue input status & unlink
            if (blueSrc != null) {
                String txt = Component.translatable("gui.settledlands.var453.blue_linked",
                        blueSrc.id).getString();
                host.settledlands$label453(txt, x, cy);
                cy += 12;
                host.settledlands$button453("gui.settledlands.var453.btn_unlink_blue",
                        x, cy, width, 16, () -> {
                            host.settledlands$remember453();
                            VarDataNode453.setEntityInput(node, -1);
                            WireHover453.invalidateCache();
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

        updateWidgetVisibility(inspectorWidgets, inspectorY, inspectorH);
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
        ensureCatalogSynced(host);
        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);

        // 1. Attach catalog gear button + dropdown under the var_name EditBox
        sidebarHeight = injectCatalogVarPicker(node, x, width, sidebarHeight, labels, inspectorWidgets, host);

        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;
        int addedH = 0;

        host.settledlands$setBuildingInspector453(true);
        try {
            // 2. Local-scope quick toggle for IF condition
            int afterLocal = addLocalScopeToggle(host, node, x, y, width);
            addedH += (afterLocal - y);
            y = afterLocal;

            // 3. Blue input status (Entity or dynamic Variable operand)
            int blueId = VarDataNode453.entityInput(graph, node);
            Node blueSrc = blueId >= 0 ? graph.get(blueId) : null;
            if (blueSrc != null) {
                String txt;
                if (VarDataNode453.isVarDataNode(blueSrc)) {
                    String vn = (blueSrc.pointsName != null && !blueSrc.pointsName.isBlank())
                            ? blueSrc.pointsName : String.valueOf(blueSrc.varValue);
                    txt = Component.translatable("gui.settledlands.var453.blue_if_var_linked",
                            blueSrc.id, vn).getString();
                } else {
                    txt = Component.translatable("gui.settledlands.var453.blue_linked",
                            blueSrc.id).getString();
                }
                host.settledlands$label453(txt, x, y);
                y += 12;
                Button unlink = host.settledlands$button453("gui.settledlands.var453.btn_unlink_blue",
                        x, y, width, 16, () -> {
                            host.settledlands$remember453();
                            VarDataNode453.setEntityInput(node, -1);
                            WireHover453.invalidateCache();
                            host.settledlands$changed453();
                            host.settledlands$widgets453();
                        });
                if (unlink != null) {
                    unlink.visible = y >= inspectorY + 22
                            && y + unlink.getHeight() <= inspectorY + inspectorH - 4;
                }
                addedH += 34;
            }
        } finally {
            host.settledlands$setBuildingInspector453(false);
        }

        updateWidgetVisibility(inspectorWidgets, inspectorY, inspectorH);
        return sidebarHeight + addedH;
    }

    private static int enhanceBlueInputNode(
            NpcDialogueGraph graph,
            Node node,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            int sidebarScroll,
            int sidebarHeight,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        if (node.varInputNode < 0) return sidebarHeight;
        Node blueSrc = graph.get(node.varInputNode);
        if (!VarDataNode453.isVarDataNode(blueSrc)) return sidebarHeight;

        int x = inspectorX + 8;
        int width = Math.max(40, inspectorW - 16);
        int baseTop = inspectorY + 24 - sidebarScroll;
        int y = baseTop + sidebarHeight + 4;
        if (inspectorWidgets != null) {
            for (AbstractWidget w : inspectorWidgets) {
                if (w != null && w.visible) {
                    y = Math.max(y, w.getY() + w.getHeight() + 4);
                }
            }
        }
        String vn = (blueSrc.pointsName != null && !blueSrc.pointsName.isBlank())
                ? blueSrc.pointsName : String.valueOf(blueSrc.varValue);

        host.settledlands$setBuildingInspector453(true);
        try {
            String txt = Component.translatable("gui.settledlands.var453.blue_text_linked",
                    blueSrc.id, vn).getString();
            host.settledlands$label453(txt, x, y);
            y += 12;
            String tokenHint = (blueSrc.pointsName != null && !blueSrc.pointsName.isBlank())
                    ? ("%" + blueSrc.pointsName + "%, %var%")
                    : "%var%";
            host.settledlands$label453(tokenHint, x, y);
            y += 12;
            Button unlink = host.settledlands$button453("gui.settledlands.var453.btn_unlink_blue",
                    x, y, width, 16, () -> {
                        host.settledlands$remember453();
                        node.varInputNode = -1;
                        WireHover453.invalidateCache();
                        host.settledlands$changed453();
                        host.settledlands$widgets453();
                    });
            if (unlink != null) {
                unlink.visible = y >= inspectorY + 22
                        && y + unlink.getHeight() <= inspectorY + inspectorH - 4;
            }
            y += 20;
        } finally {
            host.settledlands$setBuildingInspector453(false);
        }
        return Math.max(sidebarHeight + 48, y - baseTop + 4);
    }

    // ---- Shared catalog picker & local-scope helpers ----

    private static int injectCatalogVarPicker(
            Node node,
            int x,
            int width,
            int sidebarHeight,
            List<?> labels,
            List<AbstractWidget> inspectorWidgets,
            Host host) {
        Object nameLbl = findLabel(labels, "gui.settledlands.graph.var_name");
        if (nameLbl == null) return sidebarHeight;
        int lblY = labelY(nameLbl);
        if (lblY == Integer.MIN_VALUE) return sidebarHeight;

        AbstractWidget nameBox = null;
        for (AbstractWidget w : inspectorWidgets) {
            if (w instanceof EditBox
                    && w.getY() >= lblY + 6
                    && w.getY() <= lblY + 20) {
                nameBox = w;
                break;
            }
        }
        if (nameBox == null) return sidebarHeight;

        int boxY = nameBox.getY();
        int shiftAfter = boxY + nameBox.getHeight() + 1;
        int delta = 20;

        // Shift all subsequent widgets & labels down by 20px for the catalog picker row
        for (AbstractWidget w : inspectorWidgets) {
            if (w != null && w != nameBox && w.getY() >= shiftAfter) {
                w.setY(w.getY() + delta);
            }
        }
        shiftLabelsFrom(labels, shiftAfter, delta);

        host.settledlands$setBuildingInspector453(true);
        try {
            Consumer<VarCatalogClient457.Entry> onPick = picked -> applyPickedCatalogEntry(host, node, picked);
            int rowY = boxY + 18;
            int dropW = Math.max(24, width - 19);
            addCatalogDropdown(host, node, x, rowY, dropW, onPick);

            LootPoolGearButton440 gear = new LootPoolGearButton440(
                    x + width - 17, rowY, 17, 16,
                    Component.translatable("gui.settledlands.var_catalog.gear_tip"),
                    VarCatalogClient457.ACCENT_COLOR,
                    () -> VarCatalogClient457.openCatalogEditor(
                            host.settledlands$screen453(), node,
                            node.pointsName == null ? "" : node.pointsName,
                            VarDataNode453.scopeOf(node), false, onPick));
            host.settledlands$addRenderable453(gear);
        } finally {
            host.settledlands$setBuildingInspector453(false);
        }
        return sidebarHeight + delta;
    }

    private static void addCatalogDropdown(
            Host host,
            Node node,
            int x,
            int y,
            int width,
            Consumer<VarCatalogClient457.Entry> onPick) {
        List<Component> labelsList = new ArrayList<>();
        List<VarCatalogClient457.DropdownRowMeta> metaList = new ArrayList<>();
        int selectedIdx = VarCatalogClient457.buildDropdownOptions(
                node.pointsName, VarDataNode453.scopeOf(node), labelsList, metaList);
        NpcUi.NpcDropdown dd = NpcUi.dropdown(x, y, width, labelsList, selectedIdx, idx -> {
            if (idx < 0 || idx >= metaList.size()) return;
            VarCatalogClient457.DropdownRowMeta meta = metaList.get(idx);
            if (meta == null || meta.isCreateRow || meta.entry == null || !meta.entry.available) {
                return;
            }
            onPick.accept(meta.entry);
        });
        VarCatalogClient457.trackDropdown(dd, metaList, host.settledlands$screen453(), node, onPick);
        host.settledlands$addDropdown453(dd);
    }

    private static void applyPickedCatalogEntry(Host host, Node node, VarCatalogClient457.Entry picked) {
        if (host == null || node == null || picked == null) return;
        host.settledlands$remember453();
        node.pointsName = picked.name != null ? picked.name : "";
        VarDataNode453.setScope(node, picked.scope);
        if (VarDataNode453.isVarDataNode(node) && node.varValue == 0 && picked.value != 0) {
            node.varValue = picked.value;
        }
        host.settledlands$changed453();
        host.settledlands$widgets453();
    }

    private static int addLocalScopeToggle(Host host, Node node, int x, int y, int width) {
        boolean isLocal = node.varLocal;
        Button btn = host.settledlands$button453(
                "gui.settledlands.var453.btn_local_toggle", x, y, width, 16,
                () -> {
                    host.settledlands$remember453();
                    node.varLocal = !node.varLocal;
                    if (node.varLocal) {
                        node.pointsShared = false;
                    }
                    host.settledlands$changed453();
                    host.settledlands$widgets453();
                });
        if (btn != null) {
            String key = isLocal
                    ? "gui.settledlands.var453.btn_local_on"
                    : "gui.settledlands.var453.btn_local_off";
            btn.setMessage(Component.translatable(key));
        }
        return y + 20;
    }

    private static void ensureCatalogSynced(Host host) {
        int eid = host.settledlands$entityId453();
        if (eid != 0) {
            VarCatalogClient457.setActiveEntityId(eid);
            if (VarCatalogClient457.currentContextId() != eid) {
                VarCatalogClient457.requestSync(eid);
            }
        }
    }

    private static void updateWidgetVisibility(List<AbstractWidget> widgets, int inspectorY, int inspectorH) {
        for (AbstractWidget w : widgets) {
            if (w == null) continue;
            w.visible = w.getY() >= inspectorY + 22
                    && w.getY() + w.getHeight() <= inspectorY + inspectorH - 4;
        }
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
