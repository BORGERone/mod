package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRadiusZone320;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 320: the visibility toggle of a "В радиусе" node, on the canvas.
 *
 * <p>A small check box with a label sits just above the node's card - above, because the card's own
 * height depends on its text and a box drawn inside would sometimes land on a line of it. It is
 * painted in screen space at a fixed size, the way the pins are, so it stays readable at any zoom,
 * and it is clickable without selecting the node first.</p>
 *
 * <p>The tick itself is the server's job, see {@link NpcRadiusZone320}: the box only writes the flag
 * into the node, and the graph is saved the way every other edit is - Ctrl+S sends the whole graph.
 * The zone is in the world, so it becomes visible when the editor is closed.</p>
 */
public final class NpcRadiusZoneUi320 {

    /** Screen size of the box, and the gap between the box and its label. */
    public static final int BOX = 8;

    public static final int GAP = 4;

    /** How far above the top edge of the card the control sits. */
    public static final int LIFT = 15;

    private static final int COL_BOX = 0xFF8A8A8A;

    private static final int COL_BOX_ON = 0xFF6FD36F;

    private static final int COL_TEXT = 0xFFD0D0D0;

    private NpcRadiusZoneUi320() {
    }

    private static String label() {
        return Component.translatable("gui.settledlands.graph.radius_zone").getString();
    }

    /** Left edge of the control in screen space, or NaN when this node has no control. */
    private static double left(NpcGraphCanvas canvas, Node node) {
        if (canvas == null || !NpcRadiusZone320.isRadiusNode(node)) return Double.NaN;
        return canvas.screenX(node.x);
    }

    private static double top(NpcGraphCanvas canvas, Node node) {
        return canvas.screenY(node.y) - LIFT;
    }

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || !NpcRadiusZone320.isRadiusNode(node)) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        double x = left(canvas, node);
        double y = top(canvas, node);
        if (Double.isNaN(x)) return;
        int bx = (int) Math.round(x);
        int by = (int) Math.round(y);

        boolean on = NpcRadiusZone320.visible(node);
        int color = on ? COL_BOX_ON : COL_BOX;
        graphics.fill(bx, by, bx + BOX, by + BOX, color);
        graphics.fill(bx + 2, by + 2, bx + BOX - 2, by + BOX - 2,
                on ? 0xFF202020 : 0xFF303030);
        if (on) {
            // A filled centre reads as "on" from across the canvas.
            graphics.fill(bx + 3, by + 3, bx + BOX - 3, by + BOX - 3, COL_BOX_ON);
        }
        graphics.drawString(font, label(), bx + BOX + GAP, by, COL_TEXT, false);
    }

    /** True when the click landed on the box or on its label. */
    public static boolean hit(NpcDialogueGraphScreen screen, Node node, double mouseX, double mouseY) {
        if (screen == null || !NpcRadiusZone320.isRadiusNode(node)) return false;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return false;
        double x = left(canvas, node);
        if (Double.isNaN(x)) return false;
        double y = top(canvas, node);
        double right = x + BOX + GAP + font.width(label());
        return mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + BOX;
    }

    /** Finds the radius node whose control is under the cursor, or null. */
    public static Node at(NpcDialogueGraphScreen screen, NpcDialogueGraph graph, double mouseX,
            double mouseY) {
        if (screen == null || graph == null || graph.nodes == null) return null;
        for (Node node : graph.nodes.values()) {
            if (hit(screen, node, mouseX, mouseY)) return node;
        }
        return null;
    }
}
