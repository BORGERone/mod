package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.GraphInspectorDesc453;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: reserves the bottom area of the right-hand inspector sidebar for the node description
 * footer ({@link GraphInspectorDesc453}) across {@code widgets()}, {@code mouseScrolled()},
 * {@code renderBackground()}, and {@code render()} using a re-entrant depth counter so nested
 * calls (e.g., {@code mouseScrolled} calling {@code widgets}) never double-shrink {@code inspectorH}.
 * Zero synthetic lambdas.
 */
@Mixin(value = NpcDialogueGraphScreen.class, remap = false)
public abstract class GraphInspectorDescMixin453 {

    @Shadow private NpcDialogueGraph graph;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int inspectorH;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Unique private int settledlands$fullH453;
    @Unique private int settledlands$reserveH453;
    @Unique private int settledlands$shrinkDepth453;

    @Unique
    private void settledlands$pushReserve453() {
        if (settledlands$shrinkDepth453 == 0) {
            NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
            int reserve = GraphInspectorDesc453.reserveHeight(
                    screen.settledlands$font(), graph, selected, inspectorW, inspectorH);
            settledlands$fullH453 = inspectorH;
            settledlands$reserveH453 = reserve;
            if (reserve > 0) {
                inspectorH = Math.max(60, inspectorH - reserve);
            }
        }
        settledlands$shrinkDepth453++;
    }

    @Unique
    private void settledlands$popReserve453() {
        if (settledlands$shrinkDepth453 <= 0) return;
        settledlands$shrinkDepth453--;
        if (settledlands$shrinkDepth453 == 0) {
            inspectorH = settledlands$fullH453;
        }
    }

    // ---- widgets() ----

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$reserveBeforeWidgets453(CallbackInfo ci) {
        settledlands$pushReserve453();
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$restoreAfterWidgets453(CallbackInfo ci) {
        if (settledlands$reserveH453 > 0 && inspectorWidgets != null) {
            int topBound = inspectorY + 22;
            int bottomBound = inspectorY + Math.max(60, settledlands$fullH453 - settledlands$reserveH453) - 4;
            for (AbstractWidget w : inspectorWidgets) {
                if (w != null && w.visible) {
                    w.visible = w.getY() >= topBound && w.getY() + w.getHeight() <= bottomBound;
                }
            }
        }
        settledlands$popReserve453();
    }

    // ---- mouseScrolled() ----

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), remap = false)
    private void settledlands$reserveBeforeScroll453(double mouseX, double mouseY,
            double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        settledlands$pushReserve453();
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("RETURN"), remap = false)
    private void settledlands$restoreAfterScroll453(double mouseX, double mouseY,
            double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        settledlands$popReserve453();
    }

    // ---- renderBackground() ----

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), remap = false)
    private void settledlands$reserveBeforeBg453(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        settledlands$pushReserve453();
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), remap = false)
    private void settledlands$drawFooterAfterBg453(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        int reserve = settledlands$reserveH453;
        int fullH = settledlands$fullH453;
        settledlands$popReserve453();
        if (reserve > 0) {
            NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
            GraphInspectorDesc453.renderFooter(
                    graphics, screen.settledlands$font(), graph, selected,
                    inspectorX, inspectorY, inspectorW, fullH, reserve);
        }
    }

    // ---- render() ----

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"), remap = false)
    private void settledlands$reserveBeforeRender453(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        settledlands$pushReserve453();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), remap = false)
    private void settledlands$restoreAfterRender453(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        settledlands$popReserve453();
    }
}
