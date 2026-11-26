package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.GraphTooltips448;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 448: captures created top-bar and inspector widgets, labels, and renders the &gt;2-second
 * state-aware hover tooltip overlay on {@link NpcDialogueGraphScreen}.
 */
@Mixin(value = NpcDialogueGraphScreen.class, remap = false)
public abstract class GraphHover448Mixin {

    @Shadow private NpcDialogueGraph graph;
    @Shadow private NpcGraphCanvas canvas;
    @Shadow private NpcGraphContextMenu contextMenu;
    @Shadow private NpcUi.NpcDropdown openDropdown;
    @Shadow private int dragging;
    @Shadow private int linkFrom;
    @Shadow private boolean panning;
    @Shadow private boolean selectingBox;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int inspectorH;
    @Shadow private int tab;
    @Shadow private int selected;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$beginWidgets448(CallbackInfo ci) {
        GraphTooltips448.beginScreenWidgets((NpcDialogueGraphScreen) (Object) this);
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$finishWidgets448(CallbackInfo ci) {
        GraphTooltips448.finishScreenWidgets((NpcDialogueGraphScreen) (Object) this,
                inspectorWidgets, graph, selected);
    }

    @Inject(method = "label(Ljava/lang/String;II)V", at = @At("HEAD"), remap = false)
    private void settledlands$trackLabel448(String key, int x, int y, CallbackInfo ci) {
        GraphTooltips448.recordLabel((NpcDialogueGraphScreen) (Object) this, key, x, y);
    }

    @Inject(method = "button(Ljava/lang/String;IIIILjava/lang/Runnable;)Lnet/minecraft/client/gui/components/Button;",
            at = @At("RETURN"), remap = false)
    private void settledlands$trackButton448(String key, int x, int y, int width, int height,
            Runnable action, CallbackInfoReturnable<Button> cir) {
        GraphTooltips448.recordButton((NpcDialogueGraphScreen) (Object) this,
                cir.getReturnValue(), key, x, y, width, height);
    }

    @Inject(method = "textBox(Ljava/lang/String;IIIIILjava/util/function/Consumer;)Lnet/minecraft/client/gui/components/EditBox;",
            at = @At("RETURN"), remap = false)
    private void settledlands$trackTextBox448(String initial, int maxLen, int x, int y,
            int width, int height, Consumer<String> setter, CallbackInfoReturnable<EditBox> cir) {
        GraphTooltips448.recordBox((NpcDialogueGraphScreen) (Object) this,
                cir.getReturnValue(), x, y, width, height);
    }

    @Inject(method = "multiLineTextBox(Ljava/lang/String;IIIIIILnet/minecraft/network/chat/Component;Ljava/util/function/Consumer;)Lnet/minecraft/client/gui/components/MultiLineEditBox;",
            at = @At("RETURN"), remap = false)
    private void settledlands$trackMultiLine448(String initial, int maxLen, int x, int y,
            int width, int minLines, int maxLines, Component hint, Consumer<String> setter,
            CallbackInfoReturnable<MultiLineEditBox> cir) {
        GraphTooltips448.recordMultiBox((NpcDialogueGraphScreen) (Object) this,
                cir.getReturnValue(), x, y, width);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), remap = false)
    private void settledlands$renderTooltip448(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        int sw = inspectorX + inspectorW + 10;
        int sh = inspectorH + 20;
        GraphTooltips448.renderGraphTooltip((NpcDialogueGraphScreen) (Object) this, graphics,
                sw, sh, mouseX, mouseY, graph, canvas,
                contextMenu, openDropdown, dragging, linkFrom, panning, selectingBox,
                inspectorX, inspectorY, inspectorW, inspectorH, tab, selected);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), remap = false)
    private void settledlands$onClick448(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        GraphTooltips448.onMouseClick();
    }
}
