package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.door.DoorGraph382;
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
 * for, how often one of them changes, and whether the door is closed to a plain click. The values are
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

    /** Paints one door node. Never throws: a card that cannot be drawn costs the card, not the frame. */
    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        boolean selected = screen.settledlands$isSelected(node.id);
        boolean sealed = DoorGraph382.sealed(node);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, sealed ? SEALED : OPEN);
            graphics.drawString(font, title(), 8, 6, sealed ? SEALED : OPEN, false);

            graphics.drawString(font, line("gui.settledlands.door.count",
                    Integer.toString(DoorGraph382.keys(node))), 8, 30, LABEL, false);
            graphics.drawString(font, line("gui.settledlands.door.speed",
                    Integer.toString(DoorGraph382.speed(node))), 8, 48, LABEL, false);
            graphics.drawString(font, state(sealed), 8, 66, sealed ? SEALED : OPEN, false);
            // The one line the stock card never needed: what these two numbers are for.
            graphics.drawString(font, tr("gui.settledlands.door.hint"), 8, 84, EDGE, false);
        } catch (Throwable failure) {
            DoorGraph382.fail(failure);
        } finally {
            pose.popPose();
        }
    }

    /** The name of the node, in the language the client runs in. */
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
