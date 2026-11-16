package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.DoorCard382;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 382/383: the card of a door node is drawn by this mod, and only by this mod.
 *
 * <p>Build 382 let the stock painter draw the box and wrote three lines on top of it. That shipped the
 * mess the author photographed: a pink box, the title of the last row of the stock table, and the
 * relation and points lines of a «Реплика» under our text - the stock switch has no branch for the
 * door's type and falls through to the topic rows. Build 359 hit exactly this with the name card of a
 * block and answered it the same way: the stock painter is cancelled for that one type and the card is
 * painted in full by {@link DoorCard382}, in the box the canvas hit-tests a node with.</p>
 *
 * <p>It is a door graph only: an NPC or block graph never holds these types, and if one ever arrived
 * with one (a copied file) it would draw as a door card rather than as a random node - which is the
 * honest answer, the type says door.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcDoorCard382Mixin {

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$doorCard(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        // Build 386: all four node types of a door's graph are painted by DoorCard382 - the door, the
        // opener signal, the blue «Кто открыл» output and the radius gate.
        if (!DoorGraph382.isDoorGraphNode(node) || graphics == null) return;
        ci.cancel();
        DoorCard382.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }
}
