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

/**
 * Build 453/455: appends the node description block ({@link GraphInspectorDesc453}) as the
 * bottom-most scrollable item inside the general inspector sidebar list instead of docking a
 * permanent box over the bottom of the screen. Zero synthetic lambdas.
 */
@Mixin(value = NpcDialogueGraphScreen.class, remap = false)
public abstract class GraphInspectorDescMixin453 implements GraphInspectorDesc453.ScrollDescHost {

    @Shadow private NpcDialogueGraph graph;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int inspectorH;
    @Shadow private int sidebarScroll;
    @Shadow private int sidebarHeight;
    @Shadow private List<?> labels;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Unique private int settledlands$descOffset455;
    @Unique private int settledlands$descHeight455;

    @Override
    public void settledlands$layoutScrollDesc455() {
        settledlands$descOffset455 = 0;
        settledlands$descHeight455 = 0;
        if (graph == null || selected < 0 || graph.get(selected) == null) {
            return;
        }
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        int descH = GraphInspectorDesc453.contentHeight(
                screen.settledlands$font(), graph, selected, inspectorW);
        if (descH <= 0) {
            return;
        }
        int offset = GraphInspectorDesc453.computeContentEndOffset(
                inspectorY, sidebarScroll, sidebarHeight, labels, inspectorWidgets);
        settledlands$descOffset455 = offset;
        settledlands$descHeight455 = descH;
        sidebarHeight = offset + descH + 6;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;disableScissor()V",
                    shift = At.Shift.BEFORE),
            remap = false)
    private void settledlands$renderDescInList455(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (settledlands$descHeight455 <= 0 || graph == null || selected < 0) {
            return;
        }
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        int boxTop = inspectorY + 24 - sidebarScroll + settledlands$descOffset455;
        GraphInspectorDesc453.renderInList(
                graphics, screen.settledlands$font(), graph, selected,
                inspectorX, inspectorY, inspectorW, inspectorH,
                boxTop, settledlands$descHeight455);
    }
}
