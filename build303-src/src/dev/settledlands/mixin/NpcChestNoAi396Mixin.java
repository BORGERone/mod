package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcStatsGraphUi;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 396: a chest's graph has no «Автономный ИИ» card either, and still answers the mouse.
 *
 * <p>The third of the stock panels a body's editor puts on every graph, and the same one build 383 took
 * away from the door. {@code NpcAutoAi255} is not a node - it keeps per-graph state in a side map, and the
 * editor's sync is what makes the panel appear - so the call is skipped for a chest and the panel is not
 * painted.</p>
 *
 * <p>The click is <b>handed on</b>, never dropped: the screen's own {@code mouseClicked} delegates to this
 * class, which passes the click to the stats UI when the panel has nothing to do with it. Dropping it made a
 * block's canvas unclickable in build 361, and a chest's canvas would be no different.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcAutoAiGraphUi255", remap = false)
public abstract class NpcChestNoAi396Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$chestWithoutAi(NpcDialogueGraphScreen screen, NpcProfile profile,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!ChestGraph396.isChestEditorId(screen.entityId())) return;
        ci.cancel();
    }

    /** The panel itself: a chest's canvas never paints it. */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$chestWithoutAiDraw(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!ChestGraph396.isChestEditorId(screen.entityId())) return;
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$chestWithoutAiClick(NpcDialogueGraphScreen screen, double mouseX,
            double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (screen == null) return;
        if (!ChestGraph396.isChestEditorId(screen.entityId())) return;
        cir.setReturnValue(NpcStatsGraphUi.mouseClicked(screen, mouseX, mouseY, button));
    }
}
