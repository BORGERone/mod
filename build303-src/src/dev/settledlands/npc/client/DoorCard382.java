package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 383/388/392: the card of the door itself - the one node of a door's graph this mod draws.
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
 * <p>What it shows is the settings of the door: how many keys the hack panel asks for, how often one of
 * them changes, the difficulty (build 385 - which rows of the keyboard those keys come from), whether the
 * door is closed to a plain click, and who hacked it last. The values are read from the node, so the card
 * repaints on the next frame after a slider moves - nothing is cached and nothing is drawn per tick.</p>
 *
 * <p><b>Build 392: the two outputs of the door are sockets of this card, not nodes beside it.</b> The
 * author's words after two builds of separate cards: «я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде
 * \"дверь\"». So the card now carries:</p>
 *
 * <ul>
 *   <li><b>the execution socket</b> - a round amber dot on the right edge, on the row the canvas gives
 *       every node's output port ({@code NODE_H / 2}). It is where the wire to the rest of the chain
 *       starts, and it is the pulse of the door: the chain runs when the door is hacked. Round on
 *       purpose - the value socket next to it is a diamond, and the two must never be confused;</li>
 *   <li><b>the blue socket</b> - the blue diamond on the right edge one row under it, the value output
 *       every card of this mod has ({@code PursuitGraphNode272}). It hands over the reference to the
 *       player who hacked the door.</li>
 * </ul>
 *
 * <p>Both sockets are drawn where their wires really start - the row and the edge the canvas uses - so
 * what the author grabs is what the cable leaves from. The two labels («взлом», «кто взломал») sit on the
 * right of the settings, in the strip that is kept free of text for exactly that.</p>
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

    /** The amber of the door's signal, the colour a pulse has had in this mod since build 386. */
    private static final int AMBER = -3177856;

    /** The row of the execution port: the middle of the card, where the canvas draws every output. */
    private static final int OUT_ROW = NpcGraphCanvas.NODE_H / 2;

    /** The row of the blue socket: the row every value pin of this mod sits on. */
    private static final int VALUE_ROW = NpcGraphCanvas.NODE_H - 8;

    /** The strip on the right that belongs to the two socket labels; settings text stops before it. */
    private static final int LABEL_STRIP = 74;

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
        int textWidth = width - 16 - LABEL_STRIP;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, accent);
            graphics.drawString(font, tr(DoorGraph382.TITLE_KEY), 8, 6, accent, false);

            // The settings, five lines in the same 100 px box - a 14 px pitch, the last one ends at 93.
            // Everything stops before the label strip, so no line can ever run under a socket label.
            graphics.drawString(font, clip(font, line("gui.settledlands.door.count",
                    Integer.toString(DoorGraph382.keys(node))), textWidth), 8, 28, LABEL, false);
            graphics.drawString(font, clip(font, line("gui.settledlands.door.speed",
                    Integer.toString(DoorGraph382.speed(node))), textWidth), 8, 42, LABEL, false);
            // «Сложность: 2 (qwerty + asdf)» - the number and the rows it buys, on one line.
            graphics.drawString(font, clip(font, difficulty(node), textWidth), 8, 56, LABEL, false);
            graphics.drawString(font, clip(font, state(sealed), textWidth), 8, 70,
                    sealed ? SEALED : OPEN, false);
            // The reference the blue socket hands out, as the card keeps it: the last player who hacked
            // this door. Empty until somebody does, and the card says so in the same muted grey the rest
            // of the graph uses for "nothing here yet".
            String who = DoorGraph382.opener(node);
            graphics.drawString(font, clip(font, who.isEmpty()
                            ? tr("gui.settledlands.door.target_none")
                            : line("gui.settledlands.door.hacked_last", who), textWidth),
                    8, 84, who.isEmpty() ? EDGE : accent, false);

            // Build 392: the two labels of the sockets, right-aligned in the strip that was kept free.
            String outLabel = tr("gui.settledlands.door.pin_out");
            graphics.drawString(font, outLabel, width - 8 - font.width(outLabel),
                    OUT_ROW - 4, AMBER, false);
            // Build 398: the second execution socket - «открытие» - two rows under «взлом»: the chain
            // that runs when this door is simply opened, which a door that is only hacked never had.
            String openLabel = tr(CardEventPin398.labelKey(node));
            graphics.drawString(font, openLabel, width - 8 - font.width(openLabel),
                    CardEventPin398.SECOND_ROW - 4, AMBER, false);
            String whoLabel = tr("gui.settledlands.door.pin_who");
            graphics.drawString(font, whoLabel, width - 8 - font.width(whoLabel),
                    VALUE_ROW - 4, PursuitGraphNode272.COL_VALUE, false);

            // The sockets themselves, where their wires start: the round execution port of the canvas,
            // and the blue diamond of every value output of this mod. Drawn last, so a label can never
            // paint over them.
            socket(graphics, width, OUT_ROW, AMBER);
            // Build 398: «открытие» - the socket that starts the chain when this door is opened by hand.
            socket(graphics, width, CardEventPin398.SECOND_ROW, AMBER);
            diamond(graphics, width, VALUE_ROW, 5, PursuitGraphNode272.COL_VALUE);
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

    /**
     * The round execution socket: a dark ring and an amber dot, centred on the card's right edge at
     * {@code row}. Round on purpose - a value socket is a diamond, and the author must never have to guess
     * which of the two he is grabbing.
     */
    public static void socket(GuiGraphics graphics, int x, int row, int colour) {
        if (graphics == null) return;
        graphics.fill(x - 6, row - 6, x + 6, row + 6, EDGE);
        graphics.fill(x - 4, row - 4, x + 4, row + 4, colour);
    }

    /**
     * A diamond in card units - the shape of a value pin. The same size the wire pass uses as a start,
     * a little larger than the 2 px stock dot so a socket on the door card is easy to find and to grab.
     */
    public static void diamond(GuiGraphics graphics, int x, int row, int radius, int colour) {
        if (graphics == null || radius <= 0) return;
        for (int dy = -radius - 1; dy <= radius + 1; dy++) {
            int half = radius + 1 - Math.abs(dy);
            if (half < 0) continue;
            graphics.fill(x - half, row + dy, x + half + 1, row + dy + 1, EDGE);
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(x - half, row + dy, x + half + 1, row + dy + 1, colour);
        }
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
