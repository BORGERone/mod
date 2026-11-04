package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 367: every node that carries a radius starts with its zone switched off.
 *
 * <p>The zoom flag of a radius node is {@code Node#timerRepeat}, a saved boolean of the stock node
 * whose own default is {@code true}. Every "В радиусе" and every "Цель" therefore came into the world
 * with a particle ring already drawn, whether the author wanted to see it or not - and the inspector
 * row that switches it shows a timer's flag, so the ring looked like a bug of the editor. The author
 * asked for the toggle to be off by default.</p>
 *
 * <p>{@code NpcDialogueGraph#add} is the one place a node is born, and it is shared: the NPC editor,
 * the block editor of build 351 and a paste all go through it, so one hook covers every node of every
 * graph. The rule itself lives in {@link RadiusPass354#hideByDefault}, next to {@code visible} and
 * {@code setVisible} that read and write the same flag, and it touches only the two node types that
 * have a radius at all - a timer, a line, a button and the rest are left exactly as the stock made
 * them.</p>
 *
 * <p>Cost: one boolean test per node created - not per frame, not per tick. A graph saved with the
 * flag on keeps it: this is a default for new nodes, not an eraser for old ones.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class RadiusDefault367Mixin {

    @Inject(method = "add(III)Ldev/settledlands/npc/NpcDialogueGraph$Node;",
            at = @At("RETURN"), remap = false)
    private void settledlands$radiusZoneOffByDefault(int type, int x, int y,
            CallbackInfoReturnable<NpcDialogueGraph.Node> cir) {
        RadiusPass354.hideByDefault(cir.getReturnValue());
    }
}
