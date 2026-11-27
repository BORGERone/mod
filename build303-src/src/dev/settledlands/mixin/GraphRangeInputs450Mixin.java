package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.GraphRangeUi450;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 450: zero-lambda mixin enabling {@code a-b} range inputs on stock graph inspector nodes
 * and unifying {@code RANDOM}/{@code BUTTON} min/max pairs into single inputs.
 */
@Mixin(value = NpcDialogueGraphScreen.class, remap = false)
public abstract class GraphRangeInputs450Mixin implements GraphRangeUi450.Host {

    @Shadow private NpcDialogueGraph graph;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int inspectorH;
    @Shadow private boolean buildingInspector;
    @Shadow private List<?> labels;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Shadow private EditBox textBox(String initial, int maxLen, int x, int y,
            int width, int height, Consumer<String> setter) {
        throw new AssertionError();
    }

    @Shadow private void propertyChange(Runnable action) {
        throw new AssertionError();
    }

    @Override
    public EditBox settledlands$createRangeBox450(String initial, int maxLen, int x, int y,
            int width, int height, Consumer<String> setter) {
        return textBox(initial, maxLen, x, y, width, height, setter);
    }

    @Override
    public void settledlands$runPropertyChange450(Runnable action) {
        propertyChange(action);
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$applyRangesUi450(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        buildingInspector = true;
        try {
            GraphRangeUi450.apply(
                    (NpcDialogueGraphScreen) (Object) this,
                    graph,
                    selected,
                    inspectorX,
                    inspectorY,
                    inspectorW,
                    inspectorH,
                    labels,
                    inspectorWidgets,
                    this);
        } finally {
            buildingInspector = false;
        }
    }
}
