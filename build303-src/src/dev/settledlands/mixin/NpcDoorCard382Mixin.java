package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.DoorGraphClient382;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 382: what is written inside the card of a door node.
 *
 * <p>The stock painter is a chain of per-type branches over the sixteen node types of the mod. The
 * door node is a type of its own ({@link DoorGraph382#DOOR_TYPE}), so the stock code draws the card
 * box, its colour, its selection and its ports - and inside it, nothing. This hook writes the four
 * lines the author asked for, in the card's own coordinates:</p>
 *
 * <pre>
 *   Дверь
 *   Кол-во чисел: 6
 *   Скорость: 5 с
 *   Закрыта (только взлом)
 * </pre>
 *
 * <p>The transform is the stock one - the canvas position of the node, then the canvas zoom - so the
 * text moves and scales with the card. Nothing is added for any other node type, and a canvas that is
 * not there costs the text, never the frame (the rule of build 349: no visual pass may take the game
 * down).</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcDoorCard382Mixin {

    private static final int TITLE = 0xFFF0E0B8;
    private static final int LINE = 0xFFBFD4BF;
    private static final int STATE = 0xFFE8B060;

    @Inject(method = "drawNode", at = @At("RETURN"), remap = false)
    private void settledlands$doorCard(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (!DoorGraph382.isDoorNode(node) || graphics == null) return;
        try {
            NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            Font font = screen.settledlands$font();
            if (canvas == null || font == null) return;
            double left = canvas.screenX(node.x);
            double top = canvas.screenY(node.y);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(left, top, 0.0D);
                graphics.pose().scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
                graphics.drawString(font, DoorGraphClient382.title(), 8, 8, TITLE, false);
                String[] lines = DoorGraphClient382.lines(node);
                for (int i = 0; i < lines.length && i < 3; i++) {
                    if (lines[i] == null || lines[i].isBlank()) continue;
                    int colour = i == 2 ? STATE : LINE;
                    graphics.drawString(font, lines[i], 8, 30 + i * 18, colour, false);
                }
            } finally {
                graphics.pose().popPose();
            }
        } catch (Throwable failure) {
            DoorGraph382.fail(failure);
        }
    }
}
