package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRandomLine336;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcRandomLineUi336;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 336: the "случайная реплика" toggle of a "Реплика" node.
 *
 * <p>Painted at the tail of {@code drawNode}, so it sits on top of the card, and the click is taken
 * at the head of {@code mouseClicked} - before the screen decides the press starts a drag or a
 * selection. The control is above the card, outside the area the stock hit test walks, but taking
 * the click first is still what keeps a toggle from also dragging the node it belongs to.</p>
 *
 * <p>Both halves live in {@link NpcRandomLine336} and {@link NpcRandomLineUi336}; this class only
 * puts them where the screen will call them.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcRandomLineUiMixin336 {

    @Shadow
    private NpcDialogueGraph graph;

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawRandomLine(GuiGraphics graphics, Node node, CallbackInfo ci) {
        NpcRandomLineUi336.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$clickRandomLine(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (screen.settledlands$contextOpen()) return;
        Node hit = NpcRandomLineUi336.at(screen, graph, mouseX, mouseY);
        if (hit == null) return;
        NpcRandomLine336.toggle(hit);
        screen.settledlands$remember();
        screen.settledlands$changed();
        cir.setReturnValue(true);
    }
}
