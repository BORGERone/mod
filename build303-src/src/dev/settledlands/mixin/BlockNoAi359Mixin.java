package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
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
 * Build 359/361/362: a block has no «Автономный ИИ» card.
 *
 * <p>That card is not a node at all - {@code NpcAutoAi255} keeps a per-graph state in a side map and
 * the editor marks it as synced with the graph, which is what makes the panel appear. Build 359 took
 * the call that does it when the window is built ({@code ensure}) out of the way; the card came back,
 * because the sync has a second door - the profile snapshot that arrives right after the window opens -
 * so build 361 also cancelled the drawing of the panel.</p>
 *
 * <p>Build 362 fixes what 361 broke. The click handler of this class is not only the panel's handler:
 * the graph screen's own {@code mouseClicked} is a one-line delegation to it, and when it has nothing
 * to do with the click it hands the click on to the «Характеристики» UI, which hands it on to the rest
 * of the editor. Cancelling that method for a block therefore cancelled the whole canvas - the graph
 * of a block stopped answering the mouse. The click is now <b>handed on</b> instead of dropped: the panel
 * is skipped and the next handler in the stock chain is called with the same arguments, which is exactly
 * the path a click that misses the panel takes.</p>
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

    /** The panel itself: a block's canvas never paints it. */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockWithoutAiDraw(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        ci.cancel();
    }

    /**
     * The click: the panel is skipped, the rest of the editor still gets the click. Dropping it here -
     * what build 361 did - made a block's graph unclickable, because this method is the screen's only
     * way into the click chain.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockWithoutAiClick(NpcDialogueGraphScreen screen, double mouseX,
            double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        cir.setReturnValue(NpcStatsGraphUi.mouseClicked(screen, mouseX, mouseY, button));
    }
}
