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
 * Build 453: renders the UE5-style variable data card ({@link VarDataNode453}), draws the orange
 * data input socket on {@link NpcDialogueGraph#VARIABLE}, upgrades the {@code VARIABLE} card face
 * with its real math operation and wired input indicators, and handles orange pin geometry/hit-tests
 * plus the NPC create-menu row.
 */
public final class VarDataCard453 {

    public static final int CARD_W = 220;
    public static final int CARD_H = 100;
    private static final int HEADER_H = 22;

    /** Y offset on the right edge of a UE5 VarData node's orange output pin (aligns with IF's orange pin). */
    public static final double VAR_DATA_PIN_Y = 65.0D;
    /** Y offset on the left/right edges of a VARIABLE node's orange data pins (below white exec at 50, above blue pin at 92). */
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
    private static final int COL_EXEC_PORT = -3876630;
    private static final int COL_WHITE_PORT = 0xFFF2F2F2;

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

    // ---- Drawing the new UE5 variable node card ----

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

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, CARD_W + 2, CARD_H + 2, border);
            graphics.fill(0, 0, CARD_W, CARD_H, COL_BODY);
            graphics.fill(0, 0, CARD_W, HEADER_H, COL_HEADER);
            graphics.fill(0, HEADER_H, CARD_W, HEADER_H + 1, 0xFFFB923C);

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
                    8, 30, hasName ? COL_TEXT : COL_MUTED, false);

            // Numeric value badge
            String valNum = String.valueOf(node.varValue);
            String valBadge = Component.translatable("gui.settledlands.var453.card_val").getString()
                    + " " + valNum;
            int badgeW = Math.min(CARD_W - 16, font.width(valBadge) + 12);
            graphics.fill(8, 44, 8 + badgeW, 59, COL_ORANGE_DARK);
            graphics.fill(8, 44, 10, 59, COL_ORANGE);
            graphics.drawString(font, clip(font, valBadge, badgeW - 6), 13, 48, COL_ORANGE, false);

            // Scope pill (Personal / Shared / Local)
            String scopeTxt = Component.translatable(VarDataNode453.scopeBadgeKey(node)).getString();
            int scopeCol = VarDataNode453.scopeColor(node);
            int pillW = Math.min(CARD_W - 50, font.width(scopeTxt) + 10);
            graphics.fill(8, 66, 8 + pillW, 80, 0xFF151922);
            graphics.fill(8, 66, 10, 80, scopeCol);
            graphics.drawString(font, clip(font, scopeTxt, pillW - 6), 13, 69, scopeCol, false);

            // Link count / output hint near right edge
            int consumers = VarDataNode453.wiredOrangeConsumers(graph, node.id);
            String outHint = consumers > 0
                    ? consumers + " \u2192"
                    : "\u2192 Set/Get";
            int hintW = font.width(outHint);
            graphics.drawString(font, outHint, CARD_W - 12 - hintW, 61,
                    consumers > 0 ? COL_ORANGE : COL_MUTED, false);

            // Bottom subtle hint
            String typeTag = Component.translatable("gui.settledlands.var453.numbers_only").getString();
            graphics.drawString(font, clip(font, typeTag, CARD_W - 16), 8, 86, COL_MUTED, false);
        } finally {
            pose.popPose();
        }

        // Draw screen-space orange output pin on the right edge (zoom-consistent circle)
        double px = Math.round(orangeOutScreenX(canvas, node));
        double py = Math.round(orangeOutScreenY(canvas, node));
        double r = Math.max(2.0D, 3.0D * canvas.zoom);
        PursuitGraphNode272.drawPin(graphics, px, py, r + 1.0D, 0xFF2B1605);
        PursuitGraphNode272.drawPin(graphics, px, py, r, COL_ORANGE);
        PursuitGraphNode272.drawPin(graphics, px, py, Math.max(1.0D, r * 0.4D), 0xFFFFF4E6);
    }

    // ---- Drawing upgraded VARIABLE (4) card face + orange pins ----

    /**
     * Replaces stock {@code VARIABLE (4)} card drawing so the author sees the selected math
     * operation, range or orange-linked operand/target, scope badge, white exec ports at y=50,
     * orange data input at y=74, and orange data output at y=74 (plus Pursuit's blue pin at y=92).
     */
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
        boolean hasOwnName = node.pointsName != null && !node.pointsName.isBlank();
        String varTitle = hasOwnName
                ? node.pointsName
                : (orangeSrc != null
                        ? (orangeSrc.pointsName != null && !orangeSrc.pointsName.isBlank()
                                ? "\u2190 " + orangeSrc.pointsName
                                : "\u2190 #" + orangeSrc.id)
                        : Component.translatable("gui.settledlands.graph.no_var").getString());

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
            graphics.fill(0, 0, CARD_W, HEADER_H, -4046580); // stock VARIABLE warm amber header

            String hdr = Component.translatable("gui.settledlands.graph.variable").getString()
                    + " #" + node.id;
            graphics.drawString(font, clip(font, hdr, CARD_W - 16), 8, 7, 0xFFFDF8F2, false);

            // Scope badge on right of line 1 or line 3
            String scopeLabel = orangeSrc != null && !hasOwnName
                    ? Component.translatable(VarDataNode453.scopeBadgeKey(orangeSrc)).getString()
                    : Component.translatable(VarDataNode453.scopeBadgeKey(node)).getString();
            int scopeCol = orangeSrc != null && !hasOwnName
                    ? VarDataNode453.scopeColor(orangeSrc)
                    : VarDataNode453.scopeColor(node);

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

        // Right-edge orange data pin (screen space, y=74) so VARIABLE can also pass value onward
        double px = Math.round(orangeOutScreenX(canvas, node));
        double py = Math.round(orangeOutScreenY(canvas, node));
        double r = Math.max(1.8D, 2.5D * canvas.zoom);
        PursuitGraphNode272.drawPin(graphics, px, py, r + 1.0D, 0xFF2B1605);
        PursuitGraphNode272.drawPin(graphics, px, py, r, COL_ORANGE);

        // Preserve blue value input pin at y=92 (since drawNode was cancelled for VARIABLE)
        PursuitGraphNode272.drawValuePins(screen, graphics, node);
    }

    /**
     * Draws an extra highlight/ring around {@code IF}'s orange input socket when wired or hovered,
     * and draws the orange pin dot on {@code VARIABLE} if stock drawNode ran.
     */
    public static void drawIfOrangeOverlay(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            Node node) {
        if (screen == null || graphics == null || node == null) return;
        if (node.type != NpcDialogueGraph.IF) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null) return;
        // Make sure IF's orange socket at y=65 stands out cleanly
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
        return node.type == NpcDialogueGraph.VARIABLE ? VARIABLE_ORANGE_Y : IF_ORANGE_Y;
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
