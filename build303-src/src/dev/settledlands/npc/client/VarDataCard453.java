package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.VarDataNode453;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 453/456: renders the Variable card ({@link VarDataNode453}) with orange data sockets and
 * blue target pin (without white flow ports), draws orange/white/blue sockets on
 * {@link NpcDialogueGraph#VARIABLE} ("Мат выражение"), and handles orange pin geometry/hit-tests
 * plus the NPC create-menu row.
 */
public final class VarDataCard453 {

    public static final int CARD_W = 220;
    public static final int CARD_H = 100;
    private static final int HEADER_H = 22;

    /** Y offset on the left/right edges of a Variable node's orange data pins (centered vertically; no white flow ports). */
    public static final double VAR_DATA_PIN_Y = 50.0D;
    /** Y offset on the left/right edges of a Math Expression (VARIABLE) node's orange data pins (below white exec at 50, above blue pin at 92). */
    public static final double VARIABLE_ORANGE_Y = 74.0D;
    /** Y offset on the left edge of an IF node's orange input pin. */
    public static final double IF_ORANGE_Y = 65.0D;

    private static final int COL_BORDER_IDLE = 0xFF8A4B16;
    private static final int COL_BORDER_SELECTED = 0xFFE9BD67;
    private static final int COL_BODY = 0xFF211A14;
    private static final int COL_HEADER = 0xFFC2640E;
    private static final int COL_TITLE = 0xFFFFF7ED;
    private static final int COL_TEXT = 0xFFFDE6CC;
    private static final int COL_MUTED = 0xFF9C846E;
    private static final int COL_ORANGE = 0xFFFB923C;
    private static final int COL_ORANGE_DARK = 0xFF42220B;
    private static final int COL_WHITE_PORT = 0xFFF2F2F2;
    private static final int COL_BLUE = 0xFF5FB4FF;

    private VarDataCard453() {}

    // ---- Menu row in NPC/common graph context menu ----

    public static void drawMenuRow(NpcGraphContextMenu menu, GuiGraphics graphics, Font font,
            int mouseX, int mouseY) {
        if (menu == null || graphics == null || font == null
                || !menu.open || menu.targetNodeId >= 0) {
            return;
        }
        int row = VarDataNode453.MENU_ROW;
        int first = menu.scrollRow;
        if (row < first || row >= first + VarDataNode453.NPC_VISIBLE_ROWS) return;

        int local = row - first;
        int ry = menu.top + 20 + local * 22;
        int hover = menu.rowAt(mouseX, mouseY);
        if (hover == row) {
            graphics.fill(menu.left + 2, ry, menu.left + menu.width - 10, ry + 22, -13351331);
        }
        graphics.fill(menu.left + 8, ry + 6, menu.left + 18, ry + 16, VarDataNode453.PIN_COLOR);
        String label = Component.translatable(VarDataNode453.MENU_LABEL_KEY).getString();
        graphics.drawString(font, clip(font, label, menu.width - 36),
                menu.left + 24, ry + 7, hover == row ? -1 : -1852043, false);
    }

    // ---- Drawing the Variable node card ("Переменная" / "Variable") ----

    public static void drawVarDataCard(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            Node node) {
        if (screen == null || graphics == null || node == null || !VarDataNode453.isVarDataNode(node)) {
            return;
        }
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (canvas == null || font == null) return;

        boolean selected = screen.settledlands$isSelected(node.id);
        int border = selected ? COL_BORDER_SELECTED : COL_BORDER_IDLE;
        Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        int blueId = findBlueSourceId(graph, node);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, CARD_W + 2, CARD_H + 2, border);
            graphics.fill(0, 0, CARD_W, CARD_H, COL_BODY);
            graphics.fill(0, 0, CARD_W, HEADER_H, COL_HEADER);
            graphics.fill(0, HEADER_H, CARD_W, HEADER_H + 1, COL_ORANGE);

            String title = "\u25C6 " + Component.translatable(VarDataNode453.TITLE_KEY).getString()
                    + " #" + node.id;
            graphics.drawString(font, clip(font, title, CARD_W - 14), 7, 7, COL_TITLE, false);

            // Variable name
            boolean hasName = node.pointsName != null && !node.pointsName.isBlank();
            String nameLabel = Component.translatable("gui.settledlands.var453.card_name").getString()
                    + " ";
            String shownName = hasName
                    ? node.pointsName
                    : Component.translatable("gui.settledlands.var453.const_mode").getString();
            graphics.drawString(font, clip(font, nameLabel + shownName, CARD_W - 16),
                    8, 28, hasName ? COL_TEXT : COL_MUTED, false);

            // Numeric value or wired orange input source badge
            String valShown;
            if (orangeSrc != null) {
                String srcLabel = (orangeSrc.pointsName != null && !orangeSrc.pointsName.isBlank())
                        ? orangeSrc.pointsName
                        : "#" + orangeSrc.id + " (" + orangeSrc.varValue + ")";
                valShown = "\u2190 " + srcLabel;
            } else {
                valShown = String.valueOf(node.varValue);
            }
            String valBadge = Component.translatable("gui.settledlands.var453.card_val").getString()
                    + " " + valShown;
            int badgeW = Math.min(CARD_W - 22, font.width(valBadge) + 12);
            graphics.fill(10, 42, 10 + badgeW, 57, COL_ORANGE_DARK);
            graphics.fill(10, 42, 12, 57, COL_ORANGE);
            graphics.drawString(font, clip(font, valBadge, badgeW - 6), 15, 46, COL_ORANGE, false);

            // Scope pill (Personal / Shared / Local)
            String scopeTxt = Component.translatable(VarDataNode453.scopeBadgeKey(node)).getString();
            int scopeCol = VarDataNode453.scopeColor(node);
            int pillW = Math.min(CARD_W - 76, font.width(scopeTxt) + 10);
            graphics.fill(10, 62, 10 + pillW, 76, 0xFF151922);
            graphics.fill(10, 62, 12, 76, scopeCol);
            graphics.drawString(font, clip(font, scopeTxt, pillW - 6), 15, 65, scopeCol, false);

            // Output hint near right edge
            int consumers = VarDataNode453.wiredOrangeConsumers(graph, node.id);
            if (consumers > 0) {
                String outHint = consumers + " \u2192";
                int hintW = font.width(outHint);
                graphics.drawString(font, outHint, CARD_W - 12 - hintW, 65, COL_ORANGE, false);
            }

            // Bottom line: blue target status, outgoing blue count, or integer/placeholder hint
            int blueOut = PursuitGraphNode272.wiredTo(graph, node.id);
            if (blueId >= 0) {
                String blueTxt = Component.translatable("gui.settledlands.var453.blue_linked",
                        blueId).getString();
                graphics.drawString(font, clip(font, blueTxt, CARD_W - 28), 10, 84, COL_BLUE, false);
            } else if (blueOut > 0) {
                String blueTxt = Component.translatable("gui.settledlands.var453.blue_out_card",
                        blueOut).getString();
                graphics.drawString(font, clip(font, blueTxt, CARD_W - 28), 10, 84, COL_BLUE, false);
            } else {
                String typeTag = Component.translatable("gui.settledlands.var453.numbers_only").getString();
                graphics.drawString(font, clip(font, typeTag, CARD_W - 28), 10, 84, COL_MUTED, false);
            }
        } finally {
            pose.popPose();
        }

        // Right-edge orange output pin (y=50, screen space)
        double px = Math.round(orangeOutScreenX(canvas, node));
        double py = Math.round(orangeOutScreenY(canvas, node));
        double r = Math.max(2.5D, 3.5D * canvas.zoom);
        PursuitGraphNode272.drawPin(graphics, px, py, r + 1.2D, 0xFF2B1605);
        PursuitGraphNode272.drawPin(graphics, px, py, r, COL_ORANGE);
        PursuitGraphNode272.drawPin(graphics, px, py, Math.max(1.2D, r * 0.42D), 0xFFFFF4E6);

        // Left-edge orange input pin (y=50, screen space)
        double ix = Math.round(orangeInScreenX(canvas, node));
        double iy = Math.round(orangeInScreenY(canvas, node));
        PursuitGraphNode272.drawPin(graphics, ix, iy, r + 1.2D, 0xFF2B1605);
        PursuitGraphNode272.drawPin(graphics, ix, iy, r, COL_ORANGE);
        PursuitGraphNode272.drawPin(graphics, ix, iy, Math.max(1.2D, r * 0.42D), 0xFFFFF4E6);

        // Left-edge blue input pin (0, 92) & right-edge blue output pin (220, 92)
        PursuitGraphNode272.drawValuePins(screen, graphics, node);
    }

    /** Returns the connected blue source id (direct {@code entityInput453} or upstream {@code Pursuit} node), or {@code -1}. */
    public static int findBlueSourceId(NpcDialogueGraph graph, Node node) {
        if (graph == null || node == null) return -1;
        int direct = VarDataNode453.entityInput(graph, node);
        if (direct >= 0) return direct;
        if (graph.nodes != null) {
            for (Node cand : graph.nodes.values()) {
                if (PursuitGraphNode272.isPursuitNode(cand)
                        && PursuitGraphNode272.consumers(cand).contains(node.id)) {
                    return cand.id;
                }
            }
        }
        return -1;
    }

    // ---- Drawing upgraded VARIABLE (4) card face + orange pins ----

    public static void drawVariableCard(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            Node node) {
        if (screen == null || graphics == null || node == null
                || node.type != NpcDialogueGraph.VARIABLE) {
            return;
        }
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (canvas == null || font == null) return;

        boolean selected = screen.settledlands$isSelected(node.id);
        int border = selected ? COL_BORDER_SELECTED : 0xFF46576B;

        Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        Node linkedTarget = (orangeSrc == null && (node.pointsName == null || node.pointsName.isBlank()))
                ? VarDataNode453.findLinkedOrangeTarget(graph, node.id) : null;
        boolean hasOwnName = node.pointsName != null && !node.pointsName.isBlank();
        String varTitle;
        if (hasOwnName) {
            varTitle = node.pointsName;
        } else if (orangeSrc != null) {
            varTitle = (orangeSrc.pointsName != null && !orangeSrc.pointsName.isBlank())
                    ? "\u2190 " + orangeSrc.pointsName
                    : "\u2190 #" + orangeSrc.id;
        } else if (linkedTarget != null) {
            varTitle = (linkedTarget.pointsName != null && !linkedTarget.pointsName.isBlank())
                    ? "\u2192 " + linkedTarget.pointsName
                    : "\u2192 #" + linkedTarget.id;
        } else {
            varTitle = Component.translatable("gui.settledlands.graph.no_var").getString();
        }

        String valOverride = null;
        if (orangeSrc != null && hasOwnName) {
            valOverride = (orangeSrc.pointsName != null && !orangeSrc.pointsName.isBlank())
                    ? "[" + orangeSrc.pointsName + "]"
                    : "[" + orangeSrc.varValue + "]";
        } else if (GraphRanges450.hasRange(node, GraphRanges450.KEY_VAR_VALUE)) {
            valOverride = GraphRanges450.getRangeText(node, GraphRanges450.KEY_VAR_VALUE, node.varValue);
        }
        String opLine = VarDataNode453.formatCardOp(node.varOp, node.varValue, valOverride);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, CARD_W + 2, CARD_H + 2, border);
            graphics.fill(0, 0, CARD_W, CARD_H, 0xFF232833);
            graphics.fill(0, 0, CARD_W, HEADER_H, -4046580);

            String hdr = Component.translatable("gui.settledlands.graph.variable").getString()
                    + " #" + node.id;
            graphics.drawString(font, clip(font, hdr, CARD_W - 16), 8, 7, 0xFFFDF8F2, false);

            Node scopeNode = (orangeSrc != null && !hasOwnName) ? orangeSrc
                    : (linkedTarget != null ? linkedTarget : node);
            String scopeLabel = Component.translatable(VarDataNode453.scopeBadgeKey(scopeNode)).getString();
            int scopeCol = VarDataNode453.scopeColor(scopeNode);

            graphics.drawString(font, clip(font, varTitle, CARD_W - 18), 8, 30, COL_ORANGE, false);
            graphics.drawString(font, clip(font, opLine, CARD_W - 18), 8, 46, 0xFFFFE4C4, false);
            graphics.drawString(font, clip(font, scopeLabel, CARD_W - 18), 10, 62, scopeCol, false);

            if (node.action != null && !node.action.isBlank()) {
                graphics.drawString(font, clip(font,
                        Component.translatable("gui.settledlands.graph.trigger").getString(),
                        CARD_W - 24), 10, 77, -1852043, false);
            }

            // Left white exec input (y=50)
            graphics.fill(-5, 45, 5, 55, COL_WHITE_PORT);
            // Left orange data input (y=74)
            graphics.fill(-5, (int) VARIABLE_ORANGE_Y - 5, 5, (int) VARIABLE_ORANGE_Y + 5, COL_ORANGE);
            graphics.fill(-3, (int) VARIABLE_ORANGE_Y - 3, 3, (int) VARIABLE_ORANGE_Y + 3, 0xFFFFEBD2);
            // Right execution output (y=50)
            graphics.fill(CARD_W - 5, 45, CARD_W + 5, 55, COL_ORANGE);
        } finally {
            pose.popPose();
        }

        // Right-edge orange data pin (screen space, y=74)
        double px = Math.round(orangeOutScreenX(canvas, node));
        double py = Math.round(orangeOutScreenY(canvas, node));
        double r = Math.max(1.8D, 2.5D * canvas.zoom);
        PursuitGraphNode272.drawPin(graphics, px, py, r + 1.0D, 0xFF2B1605);
        PursuitGraphNode272.drawPin(graphics, px, py, r, COL_ORANGE);

        // Preserve blue value input pin at y=92 (since drawNode was cancelled for VARIABLE)
        PursuitGraphNode272.drawValuePins(screen, graphics, node);
    }

    public static void drawIfOrangeOverlay(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            Node node) {
        if (screen == null || graphics == null || node == null) return;
        if (node.type != NpcDialogueGraph.IF) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null) return;
        double px = Math.round(orangeInScreenX(canvas, node));
        double py = Math.round(orangeInScreenY(canvas, node));
        if (!Double.isFinite(px) || !Double.isFinite(py)) return;
        double r = Math.max(1.8D, 2.2D * canvas.zoom);
        PursuitGraphNode272.drawPin(graphics, px, py, r, COL_ORANGE);
    }

    // ---- Orange pin screen coordinates & hit tests ----

    public static boolean hasOrangeOutPin(Node node) {
        return VarDataNode453.isOrangeSourceNode(node);
    }

    public static boolean hasOrangeInPin(Node node) {
        return VarDataNode453.isOrangeInputNode(node);
    }

    public static double orangeOutPinCardY(Node node) {
        if (node == null) return VAR_DATA_PIN_Y;
        return node.type == NpcDialogueGraph.VARIABLE ? VARIABLE_ORANGE_Y : VAR_DATA_PIN_Y;
    }

    public static double orangeInPinCardY(Node node) {
        if (node == null) return IF_ORANGE_Y;
        if (VarDataNode453.isVarDataNode(node)) return VAR_DATA_PIN_Y;
        if (node.type == NpcDialogueGraph.VARIABLE) return VARIABLE_ORANGE_Y;
        return IF_ORANGE_Y;
    }

    public static double orangeOutScreenX(NpcGraphCanvas canvas, Node node) {
        return canvas.screenX(node.x + CARD_W);
    }

    public static double orangeOutScreenY(NpcGraphCanvas canvas, Node node) {
        return canvas.screenY(node.y + orangeOutPinCardY(node));
    }

    public static double orangeInScreenX(NpcGraphCanvas canvas, Node node) {
        return canvas.screenX(node.x);
    }

    public static double orangeInScreenY(NpcGraphCanvas canvas, Node node) {
        return canvas.screenY(node.y + orangeInPinCardY(node));
    }

    public static boolean hitOrangeOut(NpcGraphCanvas canvas, Node node, double mouseX, double mouseY) {
        if (canvas == null || !hasOrangeOutPin(node)) return false;
        double dx = mouseX - orangeOutScreenX(canvas, node);
        double dy = mouseY - orangeOutScreenY(canvas, node);
        double hit = Math.max(6.0D, Math.min(9.0D, 6.0D * canvas.zoom + 2.0D));
        return dx * dx + dy * dy <= hit * hit;
    }

    public static boolean hitOrangeIn(NpcGraphCanvas canvas, Node node, double mouseX, double mouseY) {
        if (canvas == null || !hasOrangeInPin(node)) return false;
        double dx = mouseX - orangeInScreenX(canvas, node);
        double dy = mouseY - orangeInScreenY(canvas, node);
        double hit = Math.max(6.0D, Math.min(9.0D, 6.0D * canvas.zoom + 2.0D));
        return dx * dx + dy * dy <= hit * hit;
    }

    private static String clip(Font font, String text, int maxW) {
        if (font == null || text == null) return "";
        if (font.width(text) <= maxW) return text;
        String ell = "\u2026";
        int ew = font.width(ell);
        String out = font.plainSubstrByWidth(text, Math.max(0, maxW - ew));
        while (!out.isEmpty() && font.width(out) + ew > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out + ell;
    }
}
