package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.PursuitGraphNode272;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 277: a pursuit node gets its own inspector - and only its own.
 *
 * Why Build 276 failed: a pursuit node is stored as a TRIGGER node, so
 * NpcDialogueGraphScreen#widgets() built the whole trigger editor (preset
 * dropdown, command box, hint) and the pursuit panel was drawn on top of it.
 * Hiding those widgets afterwards does not work - a hidden widget still takes
 * clicks in this screen - so the labels overlapped and typing landed in the
 * trigger command box, which turned the node into a trigger node.
 *
 * Fix: for a pursuit node the stock inspector block is skipped entirely. While
 * widgets() runs the selection is temporarily cleared, so the screen builds its
 * top bar and nothing else; the selection is restored the moment widgets()
 * returns, and the pursuit panel is built: target type switch, target value
 * field (a real EditBox, so typing goes through the normal widget path), stop
 * switch, and the standard unlink/delete buttons at the bottom. Nothing of the
 * node is modified to get there - only the screen's own selection field, and
 * only for the duration of that call.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitInspectorMixin277 {
    @Shadow private NpcDialogueGraph graph;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int inspectorH;
    @Shadow private int sidebarScroll;
    @Shadow private boolean buildingInspector;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Shadow private void remember() { throw new AssertionError(); }

    @Shadow private void changed() { throw new AssertionError(); }

    @Shadow private void widgets() { throw new AssertionError(); }

    @Shadow private void propertyChange(Runnable action) { throw new AssertionError(); }

    @Shadow private void label(String key, int x, int y) { throw new AssertionError(); }

    @Shadow private EditBox textBox(String value, int maxLength, int x, int y, int width, int height,
            Consumer<String> responder) { throw new AssertionError(); }

    @Shadow private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    /** Pursuit node whose inspector is being built, null when the selection is not one. */
    private Node settledlands$pursuitNode277;
    private int settledlands$pursuitSelected277;

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$skipStockInspector(CallbackInfo ci) {
        settledlands$pursuitNode277 = null;
        if (graph == null || selected < 0) return;
        Node node = graph.get(selected);
        if (!PursuitGraphNode272.isPursuitNode(node)) return;
        settledlands$pursuitNode277 = node;
        settledlands$pursuitSelected277 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildPursuitInspector(CallbackInfo ci) {
        Node node = settledlands$pursuitNode277;
        settledlands$pursuitNode277 = null;
        if (node == null) return;
        selected = settledlands$pursuitSelected277;
        settledlands$panel(node);
    }

    private void settledlands$panel(Node node) {
        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;

        int firstMine = inspectorWidgets.size();
        buildingInspector = true;
        try {
            label("gui.settledlands.pursuit.target_type", x, y);

            Button[] typeRef = new Button[1];
            Button typeButton = button(PursuitGraphNode272.targetTypeButtonKey(node), x, y + 11, width, 20, () -> {
                remember();
                PursuitGraphNode272.setTargetType(node,
                        PursuitGraphNode272.targetTypeIndex(node) == PursuitGraphNode272.TARGET_NPC
                                ? PursuitGraphNode272.TARGET_PLAYER
                                : PursuitGraphNode272.TARGET_NPC);
                changed();
                if (typeRef[0] != null)
                    typeRef[0].setMessage(Component.translatable(PursuitGraphNode272.targetTypeButtonKey(node)));
            });
            typeRef[0] = typeButton;

            label("gui.settledlands.pursuit.target_value", x, y + 36);
            textBox(PursuitGraphNode272.targetValue(node), PursuitGraphNode272.MAX_VALUE_LENGTH,
                    x, y + 47, width, 20, value -> {
                        if (value.equals(PursuitGraphNode272.targetValue(node))) return;
                        propertyChange(() -> PursuitGraphNode272.setTargetValue(node, value));
                    });

            Button[] stopRef = new Button[1];
            Button stopButton = button(PursuitGraphNode272.stopLabelKey(node), x, y + 72, width, 20, () -> {
                remember();
                PursuitGraphNode272.setStop(node, !PursuitGraphNode272.stopFlag(node));
                changed();
                if (stopRef[0] != null)
                    stopRef[0].setMessage(Component.translatable(PursuitGraphNode272.stopLabelKey(node)));
            });
            stopRef[0] = stopButton;

            int split = (width - 4) / 2;
            button("gui.settledlands.graph.unlink", x, y + 96, split, 16, () -> {
                remember();
                graph.disconnectOutputs(node.id);
                changed();
            });
            button("gui.settledlands.graph.delete", x + split + 4, y + 96, width - split - 4, 16, () -> {
                remember();
                graph.remove(node.id);
                selected = -1;
                changed();
                widgets();
            });
        } finally {
            buildingInspector = false;
        }

        // Same visibility rule widgets() applies to its own inspector widgets.
        for (int i = firstMine; i < inspectorWidgets.size(); i++) {
            AbstractWidget widget = inspectorWidgets.get(i);
            widget.visible = widget.getY() >= inspectorY + 22
                    && widget.getY() + widget.getHeight() <= inspectorY + inspectorH - 4;
        }
    }
}
