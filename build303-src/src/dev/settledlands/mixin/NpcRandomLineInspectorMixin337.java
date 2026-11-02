package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRandomLine336;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcRandomLineUi337;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 337: the "случайная реплика" toggle as a row of the "Реплика" node's inspector.
 *
 * <p>Build 336 drew its own check box over the node card. This build puts the control where every
 * other yes/no property of a node already lives - a stock on/off button in the inspector - and stops
 * drawing anything on the canvas at all.</p>
 *
 * <p>The row is added at the end of {@code widgets()}, after the screen has laid out its own rows and
 * clipped them, so the position is read from what the screen itself left behind: the content ends at
 * {@code sidebarHeight} past the top of the scroll area. The scroll range is then grown by the row's
 * height, otherwise the button would exist below the reachable part of the inspector and never be
 * seen, and it is clipped by the same rule the stock rows get.</p>
 *
 * <p>Flipping it goes through the screen's own {@code remember()}/{@code changed()}/{@code widgets()}
 * trio, so undo works and the label is rebuilt with the new roll.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcRandomLineInspectorMixin337 {

    @Shadow private NpcDialogueGraph graph;

    @Shadow private int selected;

    @Shadow private int inspectorX;

    @Shadow private int inspectorY;

    @Shadow private int inspectorW;

    @Shadow private int inspectorH;

    @Shadow private int sidebarScroll;

    @Shadow private int sidebarHeight;

    @Shadow private boolean buildingInspector;

    @Shadow private void remember() { throw new AssertionError(); }

    @Shadow private void changed() { throw new AssertionError(); }

    @Shadow private void widgets() { throw new AssertionError(); }

    @Shadow private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$randomLineRow(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        Node node = graph.get(selected);
        if (!NpcRandomLine336.isLine(node)) return;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            Button toggle = button(NpcRandomLine336.OFF_KEY, x, y, width, 16, () -> {
                remember();
                NpcRandomLine336.toggle(node);
                changed();
                widgets();
            });
            if (toggle == null) return;
            toggle.setMessage(NpcRandomLineUi337.label(node));
            toggle.visible = y >= inspectorY + 22
                    && y + toggle.getHeight() <= inspectorY + inspectorH - 4;
        } finally {
            buildingInspector = false;
        }
        sidebarHeight += 22;
    }
}
