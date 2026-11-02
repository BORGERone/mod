package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcRandomLine336;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 336: a "Реплика" node says the phrase it rolled.
 *
 * <p>Every place that shows a line - {@code sendGraph} in the dialogue, the same call for a chain
 * started from a check or a cutscene - asks the node for its text through {@code lineFor}, so this
 * is the one point that has to know about the roll. When the toggle is off, or there is nothing to
 * choose from, the call is left alone and the node behaves exactly as before: relation variants and
 * plain text stay where they were.</p>
 *
 * <p>The phrase is returned untouched, so {@code {player}}, relation and points placeholders are
 * still filled by the caller, per phrase.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph$Node", remap = false)
public abstract class NpcRandomLineNodeMixin336 {

    @Inject(method = "lineFor", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$randomLine(int relation, CallbackInfoReturnable<String> cir) {
        String phrase = NpcRandomLine336.pick((NpcDialogueGraph.Node) (Object) this);
        if (phrase != null) cir.setReturnValue(phrase);
    }
}
