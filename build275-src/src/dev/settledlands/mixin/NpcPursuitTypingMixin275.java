package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.PursuitGraphNode272;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 275: keystrokes for the pursuit target field.
 *
 * NpcDialogueGraphScreen does not declare charTyped(char, int), so there is
 * nothing to inject into - the method is merged into the screen instead. It
 * mirrors Screen#charTyped (widgets first) and gives the pursuit field priority
 * while it is being edited.
 *
 * keyPressed is also handled here, because the Build 272 injector only fires
 * when the selected node is a pursuit node and clicking the field does not
 * select the card.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitTypingMixin275 {

    @Unique
    public boolean charTyped(char ch, int modifiers) {
        NpcDialogueGraphScreen self = (NpcDialogueGraphScreen) (Object) this;
        Node editing = PursuitGraphNode272.editingNode(self);
        if (editing != null && PursuitGraphNode272.charTyped(self, ch, modifiers, editing)) {
            return true;
        }
        for (GuiEventListener listener : self.children()) {
            if (listener.charTyped(ch, modifiers)) return true;
        }
        return false;
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pursuitFieldKeys(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraphScreen self = (NpcDialogueGraphScreen) (Object) this;
        Node editing = PursuitGraphNode272.editingNode(self);
        if (editing != null && PursuitGraphNode272.keyPressed(self, keyCode, scanCode, modifiers, editing)) {
            cir.setReturnValue(true);
        }
    }
}
