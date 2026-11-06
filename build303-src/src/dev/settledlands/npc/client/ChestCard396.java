package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 396: the card of the chest itself - the one node of a chest's graph this mod draws.
 *
 * <p>The author's words: «Все прекрасно, теперь я хочу сделать такой-же граф для сундуков». So this is the
 * door's card ({@code DoorCard382}) with the chest's facts in it, in the same box, with the same header,
 * the same accent line, the same muted grey body and the same two sockets on the right edge. A door's card
 * shows the hack panel's settings; a chest has no settings, so the card shows what a chest knows: how many
 * times it was opened, who opened it last, and the moment its chain runs.</p>
 *
 * <p><b>The two outputs are sockets of this card, not nodes beside it</b> - the rule of build 392, which
 * applies to the chest by the same words, and which the door has kept since:</p>
 *
 * <ul>
 *   <li><b>the execution socket</b> - the round amber dot on the right edge, on the row the canvas gives
 *       every node's output port ({@code NODE_H / 2}). The chest's chain starts here, and it starts when the
 *       chest is <b>closed</b> again - a chest has a window of its own, and a line spoken under it would be a
 *       line nobody reads;</li>
 *   <li><b>the blue socket</b> - the blue diamond one row under it, the value output every card of this mod
 *       has ({@code PursuitGraphNode272}). It hands over the player who opened the chest.</li>
 * </ul>
 *
 * <p>Both sockets are drawn where their wires really start, and the labels («открытие», «кто открыл») sit in
 * the strip on the right that is kept free of text for them.</p>
 */
public final class ChestCard396 {

    /** Border of a selected and of an idle card - the colours the stock card uses for the same states. */
    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int EDGE = -5723992;
    private static final int LABEL = -5592406;

    /** The brown of the chest card - the accent the door card has used since build 382. */
    private static final int ACCENT = -3725286;

    /** The amber of a signal, the colour a pulse has had in this mod since build 386. */
    private static final int AMBER = -3177856;

    /** The row of the execution port: the middle of the card, where the canvas draws every output. */
    private static final int OUT_ROW = NpcGraphCanvas.NODE_H / 2;

    /** The row of the blue socket: the row every value pin of this mod sits on. */
    private static final int VALUE_ROW = NpcGraphCanvas.NODE_H - 8;

    /** The strip on the right that belongs to the two socket labels; the text stops before it. */
    private static final int LABEL_STRIP = 74;

    private ChestCard396() {}

    /** Paints the chest card. Never throws: a card that cannot be drawn costs the card, not the frame. */
    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        boolean selected = screen.settledlands$isSelected(node.id);
        int textWidth = width - 16 - LABEL_STRIP;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, ACCENT);
            graphics.drawString(font, tr(ChestGraph396.TITLE_KEY), 8, 6, ACCENT, false);

            String[] lines = ChestGraphClient396.lines(node);
            graphics.drawString(font, clip(font, lines[0], textWidth), 8, 30, LABEL, false);
            // Who opened the chest last - the value the blue socket hands out. Empty until somebody does,
            // and the card says so in the same muted grey the rest of the graph uses for "nothing here yet".
            boolean nobody = ChestGraph396.opener(node).isEmpty();
            graphics.drawString(font, clip(font, lines[1], textWidth), 8, 48, nobody ? EDGE : LABEL, false);
            // The moment: a chest's chain runs when the chest is closed, and the card says it plainly so
            // that nothing about it is a surprise.
            graphics.drawString(font, clip(font, lines[2], textWidth), 8, 66, AMBER, false);

            // The two labels of the sockets, right-aligned in the strip that was kept free.
            String outLabel = tr("gui.settledlands.chest.pin_out");
            graphics.drawString(font, outLabel, width - 8 - font.width(outLabel), OUT_ROW - 4, AMBER, false);
            String whoLabel = tr("gui.settledlands.chest.pin_who");
            graphics.drawString(font, whoLabel, width - 8 - font.width(whoLabel), VALUE_ROW - 4,
                    PursuitGraphNode272.COL_VALUE, false);

            // The sockets themselves, where their wires start. Drawn last, so a label can never paint over
            // them. Same shapes as the door's: round is a step of the chain, a diamond is a value.
            DoorCard382.socket(graphics, width, OUT_ROW, AMBER);
            DoorCard382.diamond(graphics, width, VALUE_ROW, 5, PursuitGraphNode272.COL_VALUE);
        } catch (Throwable failure) {
            ChestGraph396.fail(failure);
        } finally {
            pose.popPose();
        }
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
