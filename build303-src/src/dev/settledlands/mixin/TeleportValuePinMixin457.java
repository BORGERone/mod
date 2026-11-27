package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.PursuitGraphNode272;
import dev.settledlands.npc.client.TeleportCard424;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 457: ensures the blue input pin (0, 92) is also rendered on {@link TeleportCard424} cards,
 * since {@link TeleportGraphUiMixin424} cancels stock {@code drawNode} before {@code NpcPursuitValuePinMixin290}.
 * Zero synthetic lambdas.
 */
@Mixin(value = TeleportCard424.class, remap = false)
public final class TeleportValuePinMixin457 {

    @Inject(method = "draw(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private static void settledlands$drawTeleportBlueInputPin457(
            NpcDialogueGraphScreen screen, GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (screen != null && graphics != null && node != null) {
            PursuitGraphNode272.drawValuePins(screen, graphics, node);
        }
    }
}
