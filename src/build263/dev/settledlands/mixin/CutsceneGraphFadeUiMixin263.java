package dev.settledlands.mixin;

import dev.settledlands.npc.CutsceneFadeGraphData263;
import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Appends fade switches without rewriting visibility on the screen's existing node widgets. */
@Mixin(targets = {"dev.settledlands.npc.client.NpcDialogueGraphScreen"}, remap = false)
public abstract class CutsceneGraphFadeUiMixin263 {
    @Shadow(remap = false) private NpcDialogueGraph graph;
    @Shadow(remap = false) private int selected;
    @Shadow(remap = false) private List<AbstractWidget> inspectorWidgets;
    @Shadow(remap = false) private int inspectorX;
    @Shadow(remap = false) private int inspectorY;
    @Shadow(remap = false) private int inspectorW;
    @Shadow(remap = false) private int inspectorH;
    @Shadow(remap = false) private int sidebarHeight;
    @Shadow(remap = false) private int sidebarScroll;
    @Shadow(remap = false) private void remember() {}
    @Shadow(remap = false) private void changed() {}
    @Shadow(remap = false) private void widgets() {}

    @Invoker(value = "button", remap = false)
    protected abstract Button settledlands$addInspectorButton(String key, int x, int y, int width,
                                                               int height, Runnable action);

    @Inject(method = {"widgets()V"}, at = {@At(value = "RETURN")}, remap = false)
    private void settledlands$appendCutsceneFadeSwitches(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (node == null || node.type != NpcDialogueGraph.CUTSCENE) return;

        int x = inspectorX + 8;
        int width = Math.max(0, inspectorW - 16);
        int y = inspectorY + 24 + sidebarHeight - sidebarScroll;
        Button fadeOut = settledlands$addInspectorButton(CutsceneFadeGraphData263.fadeOut(node)
                        ? "gui.settledlands.graph.cutscene_fade_out_on"
                        : "gui.settledlands.graph.cutscene_fade_out_off",
                x, y, width, 16, () -> {
                    remember();
                    CutsceneFadeGraphData263.setFadeOut(node, !CutsceneFadeGraphData263.fadeOut(node));
                    changed();
                    widgets();
                });
        Button fadeIn = settledlands$addInspectorButton(CutsceneFadeGraphData263.fadeIn(node)
                        ? "gui.settledlands.graph.cutscene_fade_in_on"
                        : "gui.settledlands.graph.cutscene_fade_in_off",
                x, y + 20, width, 16, () -> {
                    remember();
                    CutsceneFadeGraphData263.setFadeIn(node, !CutsceneFadeGraphData263.fadeIn(node));
                    changed();
                    widgets();
                });

        // The base widgets() has already reset buildingInspector and applied its own
        // clipping. Register only these two additions explicitly; never toggle the
        // screen's shared inspector-building flag after the base pass has finished.
        // NOTE: sidebarHeight is NOT increased here — it accumulates across repeated
        // widgets() calls and corrupts layout of the inspector body on subsequent
        // selections. The base method recomputes sidebarHeight on every pass.
        inspectorWidgets.add(fadeOut);
        inspectorWidgets.add(fadeIn);
        fadeOut.visible = fitsInspector(fadeOut);
        fadeIn.visible = fitsInspector(fadeIn);
    }

    private boolean fitsInspector(AbstractWidget button) {
        return button.getY() >= inspectorY + 22
                && button.getY() + button.getHeight() <= inspectorY + inspectorH - 4;
    }
}
