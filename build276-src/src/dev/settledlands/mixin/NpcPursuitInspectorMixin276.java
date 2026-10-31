package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.PursuitGraphNode272;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 276: the pursuit node gets its own inspector and its own typing.
 *
 * 1. widgets() - a pursuit node is a TRIGGER node, so the stock inspector builds
 *    the trigger preset dropdown plus a free-form command box. Both are hidden
 *    and replaced with the pursuit controls: target type switch, target value
 *    box and the stop switch. The standard unlink/delete buttons stay, moved
 *    under the pursuit controls.
 * 2. charTyped(char, int) - NpcDialogueGraphScreen does not declare it, so the
 *    method is merged into the screen (mirrors Screen#charTyped, with the card
 *    field first). Build 275 marked it @Unique, which makes Mixin rename the
 *    method, so the override never ran and the card field stayed dead.
 * 3. keyPressed - forwards backspace/enter to the card field while it is edited.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitInspectorMixin276 {
    @Shadow private NpcDialogueGraph graph;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int sidebarScroll;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Shadow private void label(String key, int x, int y) { throw new AssertionError(); }

    @Shadow private EditBox textBox(String value, int maxLength, int x, int y, int width, int height,
            java.util.function.Consumer<String> responder) { throw new AssertionError(); }

    @Shadow private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("TAIL"), remap = false)
    private void settledlands$pursuitInspector(CallbackInfo ci) {
        NpcDialogueGraphScreen self = (NpcDialogueGraphScreen) (Object) this;
        if (selected < 0 || graph == null) return;
        Node node = graph.get(selected);
        if (!PursuitGraphNode272.isPursuitNode(node)) return;

        // Drop the stock TRIGGER editor (preset dropdown + command box), keep the buttons.
        List<AbstractWidget> buttons = new ArrayList<>();
        for (Object entry : new ArrayList<>(inspectorWidgets)) {
            if (!(entry instanceof AbstractWidget)) continue;
            AbstractWidget widget = (AbstractWidget) entry;
            if (widget instanceof Button) {
                buttons.add(widget);
                continue;
            }
            widget.visible = false;
            widget.setFocused(false);
            inspectorWidgets.remove(entry);
        }

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;

        label("gui.settledlands.pursuit.target_type", x, y);
        int typeY = y + 11;
        Button[] typeRef = new Button[1];
        Button typeButton = button(PursuitGraphNode272.targetTypeButtonKey(node), x, typeY, width, 20, () -> {
            self.settledlands$remember();
            PursuitGraphNode272.setTargetType(node, PursuitGraphNode272.targetTypeIndex(node) == PursuitGraphNode272.TARGET_NPC
                    ? PursuitGraphNode272.TARGET_PLAYER
                    : PursuitGraphNode272.TARGET_NPC);
            self.settledlands$changed();
            if (typeRef[0] != null) {
                typeRef[0].setMessage(Component.translatable(PursuitGraphNode272.targetTypeButtonKey(node)));
            }
        });
        typeRef[0] = typeButton;

        label("gui.settledlands.pursuit.target_value", x, y + 38);
        int fieldY = y + 49;
        textBox(PursuitGraphNode272.targetValue(node), PursuitGraphNode272.MAX_VALUE_LENGTH,
                x, fieldY, width, 20, value -> {
                    if (value.equals(PursuitGraphNode272.targetValue(node))) return;
                    self.settledlands$remember();
                    PursuitGraphNode272.setTargetValue(node, value);
                    self.settledlands$changed();
                });

        int switchY = y + 73;
        Button[] stopRef = new Button[1];
        Button stopButton = button(PursuitGraphNode272.stopLabelKey(node), x, switchY, width, 20, () -> {
            self.settledlands$remember();
            PursuitGraphNode272.setStop(node, !PursuitGraphNode272.stopFlag(node));
            self.settledlands$changed();
            if (stopRef[0] != null) {
                stopRef[0].setMessage(Component.translatable(PursuitGraphNode272.stopLabelKey(node)));
            }
        });
        stopRef[0] = stopButton;

        // Keep the standard unlink/delete pair right under the pursuit controls.
        int buttonsY = switchY + 24;
        for (AbstractWidget button : buttons) {
            button.setY(buttonsY);
        }
    }

    public boolean charTyped(char ch, int modifiers) {
        NpcDialogueGraphScreen self = (NpcDialogueGraphScreen) (Object) this;
        Node editing = PursuitGraphNode272.editingNode(self);
        if (editing != null && PursuitGraphNode272.charTyped(self, ch, modifiers, editing)) {
            return true;
        }
        for (GuiEventListener listener : self.children()) {
            if (listener.charTyped(ch, modifiers)) return true;
        }
        return false;
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pursuitFieldKeys(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraphScreen self = (NpcDialogueGraphScreen) (Object) this;
        Node editing = PursuitGraphNode272.editingNode(self);
        if (editing != null && PursuitGraphNode272.keyPressed(self, keyCode, scanCode, modifiers, editing)) {
            cir.setReturnValue(true);
        }
    }
}
