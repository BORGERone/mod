package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 383: the whole card of a door node, drawn by this mod.
 *
 * <p>Build 382 only wrote the text of the card on top of the stock one, and the stock painter has no
 * branch for a node type it does not know: it drew the colour of the last row of its table (a pink
 * box), a title it made up, and the relation and points lines of a «Реплика» underneath our text -
 * exactly the mess build 359 hit with the name card of a block. This class is that lesson applied: the
 * stock painter is taken out of the way for this one type ({@code NpcDoorCard382Mixin} cancels it) and
 * the card is drawn here, in the box the canvas already hit-tests a node with
 * ({@code NpcGraphCanvas#NODE_W} x {@code NODE_H}), so selecting, dragging and multi-select stay the
 * stock ones.</p>
 *
 * <p>What it shows is the whole node: the title, the number of keys the hack panel of this door asks
 * for, how often one of them changes, the difficulty (which rows of the keyboard those keys come from,
 * build 385), whether the door is closed to a plain click, and one line that says the panel owns the
 * keyboard but for ESC. The values are
 * read from the node, so the card repaints on the next frame after a slider moves - nothing is cached
 * and nothing is drawn per tick.</p>
 */
public final class DoorCard382 {

    /** Border of a selected and of an idle card - the colours the stock card uses for the same states. */
    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int EDGE = -5723992;
    private static final int LABEL = -5592406;
    private static final int SEALED = -35328;
    private static final int OPEN = -1179650;

    private DoorCard382() {}

    /**
     * Paints one node of a door's graph. Never throws: a card that cannot be drawn costs the card, not
     * the frame.
     *
     * <p>Build 386: four types share this card - «Дверь» (brown), «Открытие» (amber), «Кто открыл»
     * (blue, the colour the author asked for) and «В радиусе». Each has its own title, its own accent
     * line under the header and its own two to four lines of text; the box, the hit test, the selection
     * and the drag stay the stock ones.</p>
     */
    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        boolean selected = screen.settledlands$isSelected(node.id);
        int type = node.type;
        boolean sealed = type == DoorGraph382.DOOR_TYPE && DoorGraph382.sealed(node);
        int accent = type == DoorGraph382.DOOR_TYPE
                ? (sealed ? SEALED : OPEN) : DoorGraph382.colour(type);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, accent);
            graphics.drawString(font, tr(DoorGraph382.labelKey(type)), 8, 6, accent, false);

            // Five lines in the same 100 px box - a 14 px pitch, the last one ends at 93.
            if (type == DoorGraph382.DOOR_TYPE) {
                graphics.drawString(font, line("gui.settledlands.door.count",
                        Integer.toString(DoorGraph382.keys(node))), 8, 28, LABEL, false);
                graphics.drawString(font, line("gui.settledlands.door.speed",
                        Integer.toString(DoorGraph382.speed(node))), 8, 42, LABEL, false);
                // «Сложность: 2 (qwerty + asdf)» - the number and the rows it buys, on one line.
                graphics.drawString(font, difficulty(node), 8, 56, LABEL, false);
                graphics.drawString(font, state(sealed), 8, 70, sealed ? SEALED : OPEN, false);
                // The panel swallows the keyboard but for ESC; the card says so, because a player who
                // cannot close a panel with anything else deserves to read why.
                graphics.drawString(font, tr("gui.settledlands.door.hint"), 8, 84, EDGE, false);
            } else if (type == DoorGraph382.EVENT_TYPE) {
                graphics.drawString(font, tr("gui.settledlands.door.event_line1"), 8, 28, LABEL, false);
                graphics.drawString(font, tr("gui.settledlands.door.event_line2"), 8, 42, LABEL, false);
                graphics.drawString(font, tr("gui.settledlands.door.event_line3"), 8, 56, EDGE, false);
                graphics.drawString(font, tr("gui.settledlands.door.event_line4"), 8, 70, EDGE, false);
            } else if (type == DoorGraph382.WHO_TYPE) {
                graphics.drawString(font, tr("gui.settledlands.door.who_line1"), 8, 28, LABEL, false);
                String last = DoorGraph382.opener(node);
                graphics.drawString(font,
                        last.isEmpty() ? tr("gui.settledlands.door.who_none")
                                : line("gui.settledlands.door.who_last", last),
                        8, 42, last.isEmpty() ? EDGE : accent, false);
                graphics.drawString(font, tr("gui.settledlands.door.who_line3"), 8, 56, EDGE, false);
            } else if (type == DoorGraph382.RADIUS_TYPE) {
                graphics.drawString(font, line("gui.settledlands.door.radius_line",
                        Integer.toString(DoorGraph382.radius(node))), 8, 28, LABEL, false);
                graphics.drawString(font, tr("gui.settledlands.door.radius_line2"), 8, 42, LABEL, false);
                graphics.drawString(font, tr("gui.settledlands.door.radius_line3"), 8, 56, EDGE, false);
            }
        } catch (Throwable failure) {
            DoorGraph382.fail(failure);
        } finally {
            pose.popPose();
        }
    }

    /** «Сложность: 2 (qwerty + asdf)» - the value and the rows of the keyboard it opens. */
    public static String difficulty(NpcDialogueGraph.Node node) {
        int level = DoorGraph382.difficulty(node);
        return line("gui.settledlands.door.difficulty", Integer.toString(level))
                + " (" + tr("gui.settledlands.door.rows" + level) + ")";
    }

    /** The name of the door node, in the language the client runs in. */
    public static String title() {
        return tr("gui.settledlands.door.node");
    }

    /** {@code Кол-во чисел: 6} - a key with one argument, never a throw. */
    private static String line(String key, String argument) {
        try {
            return Component.translatable(key, Component.literal(argument)).getString();
        } catch (Throwable failure) {
            return key + ": " + argument;
        }
    }

    private static String state(boolean sealed) {
        return tr(sealed ? "gui.settledlands.door.sealed" : "gui.settledlands.door.unsealed");
    }

    private static String tr(String key) {
        try {
            return Component.translatable(key).getString();
        } catch (Throwable failure) {
            return key;
        }
    }
}
