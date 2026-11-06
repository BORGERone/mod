package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 383/388: the card of the door itself - the one node of a door's graph this mod draws.
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
 * <p>The card is the block's «Имя» card ({@code BlockNameCard359}) with the door's settings in it, and
 * deliberately so: a block's graph and a door's graph are the same editor, and the one card each of them
 * owns is the only thing that should look different. Same header, same accent line, same body, same
 * colours, same box.</p>
 *
 * <p>What it shows is the whole settings of the door: how many keys the hack panel asks for, how often
 * one of them changes, the difficulty (build 385 - which rows of the keyboard those keys come from),
 * whether the door is closed to a plain click, and one line that says the panel owns the keyboard but for
 * ESC. The values are read from the node, so the card repaints on the next frame after a slider moves -
 * nothing is cached and nothing is drawn per tick.</p>
 *
 * <p><b>Build 388: every other node of a door's graph is a stock node</b>, drawn by the stock painter,
 * because a door runs the stock interpreter a block does ({@code BlockTrigger351}).</p>
 *
 * <p><b>Build 389: three cards are ours now</b> - the door (brown), its output «Импульс» (amber) and the
 * blue «Цель» that carries the reference to the player who hacked the door. They are the two nodes the
 * author asked for, and they exist as types of this mod only because the stock graph has no node for
 * "this door was hacked" and none that holds a player reference. Everything about them - the box, the
 * hit test, the selection, the drag, the wires - is the stock canvas; only the words are ours.</p>
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
     * Paints the door card. Never throws: a card that cannot be drawn costs the card, not the frame.
     */
    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        boolean selected = screen.settledlands$isSelected(node.id);
        boolean sealed = DoorGraph382.sealed(node);
        int accent = sealed ? SEALED : OPEN;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            int type = node.type;
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, type == DoorGraph382.DOOR_TYPE ? accent
                    : DoorGraph382.colour(type));
            graphics.drawString(font, tr(DoorGraph382.labelKey(type)), 8, 6,
                    type == DoorGraph382.DOOR_TYPE ? accent : DoorGraph382.colour(type), false);

            if (type == DoorGraph382.SIGNAL_TYPE) {
                // The output of the door card: what a pulse is and when it comes.
                graphics.drawString(font, clip(font, tr("gui.settledlands.door.signal_line1"),
                        width - 16), 8, 28, LABEL, false);
                graphics.drawString(font, clip(font, tr("gui.settledlands.door.signal_line2"),
                        width - 16), 8, 46, LABEL, false);
                graphics.drawString(font, clip(font, tr("gui.settledlands.door.signal_line3"),
                        width - 16), 8, 64, EDGE, false);
                graphics.drawString(font, clip(font, tr("gui.settledlands.door.signal_line4"),
                        width - 16), 8, 82, EDGE, false);
            } else if (type == DoorGraph382.TARGET_TYPE) {
                // The blue output: the reference to the player who hacked the door, as the graph keeps it.
                graphics.drawString(font, clip(font, tr("gui.settledlands.door.target_line1"),
                        width - 16), 8, 28, LABEL, false);
                String who = DoorGraph382.opener(node);
                graphics.drawString(font, clip(font, who.isEmpty()
                                ? tr("gui.settledlands.door.target_none")
                                : line("gui.settledlands.door.target_last", who), width - 16),
                        8, 46, who.isEmpty() ? EDGE : accent, false);
                graphics.drawString(font, clip(font, tr("gui.settledlands.door.target_line3"),
                        width - 16), 8, 64, EDGE, false);
            } else {
                // Five lines in the same 100 px box - a 14 px pitch, the last one ends at 93.
                graphics.drawString(font, clip(font, line("gui.settledlands.door.count",
                        Integer.toString(DoorGraph382.keys(node))), width - 16), 8, 28, LABEL, false);
                graphics.drawString(font, clip(font, line("gui.settledlands.door.speed",
                        Integer.toString(DoorGraph382.speed(node))), width - 16), 8, 42, LABEL, false);
                // «Сложность: 2 (qwerty + asdf)» - the number and the rows it buys, on one line.
                graphics.drawString(font, clip(font, difficulty(node), width - 16), 8, 56, LABEL, false);
                graphics.drawString(font, state(sealed), 8, 70, sealed ? SEALED : OPEN, false);
                // The panel swallows the keyboard but for ESC; the card says so, because a player who
                // cannot close a panel with anything else deserves to read why.
                graphics.drawString(font, tr("gui.settledlands.door.hint"), 8, 84, EDGE, false);
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

    /** The name of the door card, in the language the client runs in. */
    public static String title() {
        return tr(DoorGraph382.TITLE_KEY);
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

    /** Cut a line that would run off the card, so a long one never paints over its neighbour. */
    private static String clip(Font font, String text, int width) {
        if (font == null || text == null || font.width(text) <= width) return text == null ? "" : text;
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.isEmpty() ? "" : cut + "…";
    }
}
