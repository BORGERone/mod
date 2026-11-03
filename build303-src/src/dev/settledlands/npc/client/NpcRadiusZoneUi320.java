package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRadiusZone320;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Build 320: the visibility toggle of a radius node - retired in build 361.
 *
 * <p>Build 320 painted a small check box with the label «Зона» just above the card of a radius node,
 * in screen space, so the zone could be switched on and off without selecting the node first. It was
 * the only way to switch it for the node it was written for. Build 320 was also the last time it was
 * the only way: build 354 put the same flag into the inspector as a row («Видимость радиуса: да/нет»)
 * and build 355 made that row save the graph on the click, and the row is what the «В радиусе» node
 * has used since.</p>
 *
 * <p>Two controls for one flag, drawn in two different places and in two different shapes, is one
 * control too many - and it left the two nodes that share the flag looking different: a button in the
 * inspector for «В радиусе», a box on the canvas for «Цель». Build 361 therefore keeps only the row,
 * so a node that shows a zone shows it the same way wherever it is: the checkbox is no longer painted
 * ({@link #draw}) and it no longer takes clicks ({@link #hit}). The flag, the ring in the world and
 * the save are untouched - they live in {@link NpcRadiusZone320} and {@code RadiusPass354}.</p>
 */
public final class NpcRadiusZoneUi320 {

    /** Screen size of the box the retired control was painted with. Kept for the callers' geometry. */
    public static final int BOX = 8;

    public static final int GAP = 4;

    /** How far above the top edge of the card the control used to sit. */
    public static final int LIFT = 15;

    private NpcRadiusZoneUi320() {
    }

    /**
     * Build 361: nothing is painted above a radius node any more. The one control of this flag is the
     * inspector row of build 354/355, which both radius nodes already have.
     */
    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        // Retired: the inspector row is the toggle.
    }

    /** Build 361: and the retired control cannot be hit either, so it never eats a click. */
    public static boolean hit(NpcDialogueGraphScreen screen, Node node, double mouseX, double mouseY) {
        return false;
    }

    /** Finds the radius node whose control is under the cursor - never, since build 361. */
    public static Node at(NpcDialogueGraphScreen screen, NpcDialogueGraph graph, double mouseX,
            double mouseY) {
        return null;
    }
}
