package dev.settledlands.mixin;

import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.client.GraphCardDesc448;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 448: attaches the compact bottom description footer to the world-space Auto-AI panel
 * when it is rendered on NPC graphs.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcAutoAiGraphUi255", remap = false)
public abstract class AutoAiFooter448Mixin {

    @Inject(method = "draw(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$drawAutoAiFooter448(NpcDialogueGraphScreen screen,
            GuiGraphics graphics, CallbackInfo ci) {
        // Build 453: canvas footer disabled to keep the canvas uncluttered.
    }

    @Inject(method = "visible(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcAutoAi255$GraphState;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$visibleAutoAiFooter448(NpcGraphCanvas canvas,
            NpcAutoAi255.GraphState state, CallbackInfoReturnable<Boolean> cir) {
        // Build 453: canvas footer disabled.
    }
}
