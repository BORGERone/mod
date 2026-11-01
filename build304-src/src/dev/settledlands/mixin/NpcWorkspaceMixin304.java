package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.WorkspaceSkin304;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 304: the graph screen paints its canvas and its cards in the workspace style.
 *
 * <p>Both hooks sit on the screen's own methods, so nothing here depends on a Minecraft
 * method name resolving at runtime - the lesson from Build 301/302.</p>
 *
 * <ul>
 *   <li>{@code grid} - the old full-screen line grid is replaced by the dark canvas and its dot
 *       grid. The method only draws, so cancelling it changes nothing else.</li>
 *   <li>{@code drawNode} - after the stock card has drawn itself, its frame, accent bar and
 *       round ports are painted on top. The card's own text is already there and is not
 *       touched.</li>
 * </ul>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcWorkspaceMixin304 {

    @Shadow
    private int selected;

    @Shadow
    public abstract NpcGraphCanvas settledlands$canvas();

    @Inject(method = "grid(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void settledlands$workspaceCanvas(GuiGraphics graphics, CallbackInfo ci) {
        NpcGraphCanvas canvas = settledlands$canvas();
        if (canvas == null) return;
        WorkspaceSkin304.drawCanvas(graphics, canvas);
        ci.cancel();
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$cardSkin(GuiGraphics graphics, Node node, CallbackInfo ci) {
        NpcGraphCanvas canvas = settledlands$canvas();
        if (canvas == null || node == null) return;
        WorkspaceSkin304.drawCard(graphics, canvas, node.x, node.y, selected == node.id);
    }
}
