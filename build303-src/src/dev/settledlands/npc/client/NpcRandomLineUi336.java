package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRandomLine336;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 336: the "случайная реплика" toggle, drawn on the canvas above a "Реплика" node.
 *
 * <p>A small check box with a label, the same control the "В радиусе" node got in build 320: it is
 * painted in screen space at a fixed size, so it stays readable at any zoom, it sits just above the
 * card because the card's own height depends on its text, and it takes the click before the screen
 * decides the press was a node drag.</p>
 *
 * <p>The label shows what the node settled on - "строка 2 из 3" - so the author can see the roll
 * without opening anything. Flipping it while it is on turns it off; flipping it on again throws the
 * dice anew with the phrases the node has at that moment.</p>
 */
public final class NpcRandomLineUi336 {

    /** Screen size of the box, and the gap between the box and its label. */
    public static final int BOX = 8;

    public static final int GAP = 4;

    /** How far above the top edge of the card the control sits. */
    public static final int LIFT = 15;

    private static final int COL_BOX = 0xFF8A8A8A;

    private static final int COL_BOX_ON = 0xFFE3BD75;

    private static final int COL_TEXT = 0xFFD0D0D0;

    private NpcRandomLineUi336() {
    }

    private static String label(Node node) {
        if (node == null) return "";
        if (!NpcRandomLine336.random(node)) {
            return Component.translatable(NpcRandomLine336.OFF_KEY).getString();
        }
        List<String> lines = NpcRandomLine336.phrases(node.text);
        int count = Math.max(1, lines.size());
        int rolled = NpcRandomLine336.index(node, count) + 1;
        return Component.translatable(NpcRandomLine336.ON_KEY, rolled, count).getString();
    }

    private static double left(NpcGraphCanvas canvas, Node node) {
        if (canvas == null || !NpcRandomLine336.isLine(node)) return Double.NaN;
        return canvas.screenX(node.x);
    }

    private static double top(NpcGraphCanvas canvas, Node node) {
        return canvas.screenY(node.y) - LIFT;
    }

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || !NpcRandomLine336.isLine(node)) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        double x = left(canvas, node);
        if (Double.isNaN(x)) return;
        int bx = (int) Math.round(x);
        int by = (int) Math.round(top(canvas, node));

        boolean on = NpcRandomLine336.random(node);
        graphics.fill(bx, by, bx + BOX, by + BOX, on ? COL_BOX_ON : COL_BOX);
        graphics.fill(bx + 2, by + 2, bx + BOX - 2, by + BOX - 2, on ? 0xFF202020 : 0xFF303030);
        if (on) graphics.fill(bx + 3, by + 3, bx + BOX - 3, by + BOX - 3, COL_BOX_ON);
        graphics.drawString(font, label(node), bx + BOX + GAP, by, COL_TEXT, false);
    }

    /** True when the click landed on the box or on its label. */
    public static boolean hit(NpcDialogueGraphScreen screen, Node node, double mouseX, double mouseY) {
        if (screen == null || !NpcRandomLine336.isLine(node)) return false;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return false;
        double x = left(canvas, node);
        if (Double.isNaN(x)) return false;
        double y = top(canvas, node);
        double right = x + BOX + GAP + font.width(label(node));
        return mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + BOX;
    }

    /** Finds the line node whose toggle is under the cursor, or null. */
    public static Node at(NpcDialogueGraphScreen screen, NpcDialogueGraph graph, double mouseX,
            double mouseY) {
        if (screen == null || graph == null || graph.nodes == null) return null;
        for (Node node : graph.nodes.values()) {
            if (hit(screen, node, mouseX, mouseY)) return node;
        }
        return null;
    }
}
