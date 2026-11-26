package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.TrialModuleCard447;
import dev.settledlands.npc.client.TrialModuleClient447;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 447: draws specialized {@link TrialModule447} cards, frames the entire modular graph left
 * of the inspector sidebar on first open, keeps {@code hub.action} continuously synced from
 * connected modules on every {@code changed()}, and runs deferred inspector rebuilds cleanly.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class TrialModuleUi447Mixin {

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private NpcGraphCanvas canvas;

    @Shadow
    private int selected;

    @Shadow
    private int focus;

    @Shadow
    private boolean focusOnInit;

    @Shadow
    private int inspectorX;

    private boolean settledlands$wasFocusOnInit447;

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawTrialModuleCard447(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (node == null || graphics == null || !TrialModule447.isModuleNode(node)) return;
        ci.cancel();
        TrialModuleCard447.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }

    @Inject(method = "init()V", at = @At("HEAD"), remap = false)
    private void settledlands$captureFocusFlag447(CallbackInfo ci) {
        settledlands$wasFocusOnInit447 = focusOnInit;
    }

    @Inject(method = "init()V", at = @At("RETURN"), remap = false)
    private void settledlands$frameModularTrialGraph447(CallbackInfo ci) {
        if (!settledlands$wasFocusOnInit447) return;
        settledlands$wasFocusOnInit447 = false;
        try {
            NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
            if (!TrialGraph446.isTrialEditorId(screen.entityId())) return;
            if (graph == null || canvas == null || graph.hasCamera || focus >= 0) return;
            int savedRight = canvas.right;
            if (inspectorX > canvas.left + 120) {
                canvas.right = inspectorX - 10;
            }
            try {
                canvas.frameAll(graph);
            } finally {
                canvas.right = savedRight;
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "changed()V", at = @At("HEAD"), remap = false)
    private void settledlands$syncModulesBeforeSave447(CallbackInfo ci) {
        if (graph == null) return;
        if (TrialGraph446.trialNode(graph) != null) {
            TrialModule447.syncCardConfig(graph);
        }
    }

    @Inject(method = "changed()V", at = @At("RETURN"), remap = false)
    private void settledlands$rebuildInspectorIfRequested447(CallbackInfo ci) {
        if (!TrialModuleClient447.consumeInspectorRebuild()) return;
        if (graph != null && selected >= 0 && graph.get(selected) == null) {
            selected = -1;
        }
        widgets();
    }
}
