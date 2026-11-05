package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
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
 * Build 383: a door's graph has no «Автономный ИИ» card either, and still answers the mouse.
 *
 * <p>The third of the stock panels a body's editor puts on every graph, and the second one the author's
 * build-382 screenshot showed on the door's canvas. {@code NpcAutoAi255} is not a node: it keeps a
 * per-graph state in a side map, and the editor's sync - the call when the window is built and the
 * profile snapshot that arrives right after it - is what makes the panel appear. Both are taken out of
 * the way for a door, as build 359/361 did for a block, and the panel is not painted.</p>
 *
 * <p>The click is the part that must not be broken twice. The graph screen's own {@code mouseClicked} is
 * a one-line delegation to this class, which hands the click on to the stats UI when it has nothing to
 * do with the panel, and that is the only path a click on a node takes. So the click is <b>handed on</b>
 * here - the panel is skipped and the next handler in the stock chain gets the same arguments - never
 * dropped; dropping it made a block's canvas unclickable in build 361, and a door's canvas would be no
 * different.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcAutoAiGraphUi255", remap = false)
public abstract class NpcDoorNoAi383Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$doorWithoutAi(NpcDialogueGraphScreen screen, NpcProfile profile,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!DoorGraph382.isDoorEditorId(screen.entityId())) return;
        ci.cancel();
    }

    /** The panel itself: a door's canvas never paints it. */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$doorWithoutAiDraw(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!DoorGraph382.isDoorEditorId(screen.entityId())) return;
        ci.cancel();
    }

    /**
     * The click goes through to the rest of the editor. Only the panel is skipped - a door's canvas must
     * keep selecting and dragging its node, which is the whole point of opening the graph.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$doorWithoutAiClick(NpcDialogueGraphScreen screen, double mouseX,
            double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (screen == null) return;
        if (!DoorGraph382.isDoorEditorId(screen.entityId())) return;
        cir.setReturnValue(NpcStatsGraphUi.mouseClicked(screen, mouseX, mouseY, button));
    }
}
