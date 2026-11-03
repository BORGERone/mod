package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 359/361: a block has no «Автономный ИИ» card.
 *
 * <p>That card is not a node at all - {@code NpcAutoAi255} keeps a per-graph state in a side map and
 * the editor marks it as synced with the graph, which is what makes the panel appear. Build 359 took
 * the call that does it when the window is built ({@code ensure}) out of the way; build 360 came back
 * from the game with the card still on the canvas of a block, so the sync has a second door - the
 * snapshot of the profile that arrives right after the window opens - and skipping one of them was
 * not enough.</p>
 *
 * <p>Build 361 therefore stops asking which door was used: the card of a block graph is not drawn at
 * all ({@link #settledlands$blockWithoutAiDraw}), and a click where it would have been is not taken
 * ({@link #settledlands$blockWithoutAiClick}). Whatever the state of the side map, a block's canvas
 * has one card that means something - its name - and nothing else. {@code ensure} stays cancelled so
 * the state is never written in the first place; the two new guards are what makes the answer true
 * even when some other path writes it.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcAutoAiGraphUi255", remap = false)
public abstract class BlockNoAi359Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockWithoutAi(NpcDialogueGraphScreen screen, NpcProfile profile,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        ci.cancel();
    }

    /**
     * The panel itself: a block's canvas never paints it. This is the hook build 359 was missing -
     * the card was on screen while every editor call for it had been cancelled, so the drawing is
     * where the answer has to be given.
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockWithoutAiDraw(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        ci.cancel();
    }

    /** And it cannot be clicked either, so an invisible panel never swallows a click on the canvas. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockWithoutAiClick(NpcDialogueGraphScreen screen, double mouseX,
            double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        cir.setReturnValue(Boolean.FALSE);
    }
}
