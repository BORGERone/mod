package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 359: the card of a block's «Имя» node, and the only place its text is read on the canvas.
 *
 * <p>A block graph keeps three cards a body's graph keeps too - the stats card, the «Автономный ИИ»
 * card and, from this build, the name card - and only the third one means anything to a block. The
 * first two are gone for a block ({@code BlockNoStats359Mixin}, {@code BlockNoAi359Mixin}); this one
 * is drawn here, in the block's own colour, because the stock card has no branch for a node type it
 * does not know and would label it «Случайность».</p>
 *
 * <p>The card occupies exactly the box the canvas already hit-tests a node with ({@code NODE_W} x
 * {@code NODE_H}), so a click selects it and a drag moves it without a single line of our own hit
 * code: the screen draws the box and the canvas finds it, both from the same geometry.</p>
 *
 * <p>What is written on it is what the dialogue will show: the name, the colour it is written in and
 * a line that says so. The name is edited in the inspector ({@code BlockNameInspector359Mixin}), the
 * way every other property of a node is edited - the card only shows it.</p>
 */
public final class BlockNameCard359 {

    /** Border of a selected node, the colour the stock card uses for the same state. */
    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int LABEL = -5592406;
    private static final int HINT = -9868951;

    private BlockNameCard359() {}

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        int color = BlockName359.rgb(BlockName359.colorOf(node));
        boolean selected = screen.settledlands$isSelected(node.id);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, color);
            graphics.drawString(font, tr(BlockName359.TITLE_KEY), 8, 6, color, false);

            String name = BlockName359.nameOf(node);
            boolean named = !name.isEmpty();
            graphics.drawString(font, named ? clip(font, name, width - 16) : tr(BlockName359.EMPTY_KEY),
                    8, 30, named ? color : LABEL, false);
            graphics.drawString(font,
                    tr(BlockName359.COLOR_KEY) + ": " + tr(BlockName359.COLOR_KEYS[BlockName359.colorOf(node)]),
                    8, 52, LABEL, false);
            graphics.drawString(font, tr("gui.settledlands.name.hint"), 8, 74, HINT, false);
        } finally {
            pose.popPose();
        }
    }

    /** The translated text of a key, in the language the client runs in. */
    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** Cut a name that would run off the card, so a long one never paints over its neighbour. */
    private static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.isEmpty() ? "" : cut + "…";
    }
}
