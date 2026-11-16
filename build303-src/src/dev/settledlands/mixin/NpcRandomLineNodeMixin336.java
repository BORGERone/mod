package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcRandomLine345;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 336, as of 345: a "Реплика" node says the phrase its own NPC rolled.
 *
 * <p>Every place that shows a line asks the node for its text through {@code lineFor}, so this is the
 * one point that has to know about the roll. Since 345 the dice are thrown per NPC
 * ({@link NpcRandomLine345}): while a dialogue view is being built the NPC is the one being spoken to,
 * and everywhere else the node's own stored roll answers, exactly as before.</p>
 *
 * <p>The phrase is returned untouched, so {@code {player}}, relation and points placeholders are
 * still filled by the caller, per phrase.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph$Node", remap = false)
public abstract class NpcRandomLineNodeMixin336 {

    @Inject(method = "lineFor", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$randomLine(int relation, CallbackInfoReturnable<String> cir) {
        String phrase = NpcRandomLine345.pick((NpcDialogueGraph.Node) (Object) this);
        if (phrase != null) cir.setReturnValue(phrase);
    }
}
