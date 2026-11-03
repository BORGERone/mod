package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 354: the "Видимость" row of a "В радиусе" node - now for bodies as well as for blocks.
 *
 * <p>Build 320 gave this toggle a small box painted on the node card. The box is still there and
 * still the same flag ({@code Node#timerRepeat}), but a box has no name: this row is the named
 * control, in the inspector, next to the radius and the cooldown it belongs to -
 * "Видимость радиуса: да/нет". Both write the same flag, so they can never disagree, and the ring
 * itself is drawn by {@link RadiusPass354}, which reads the same flag for a block and for a body.</p>
 *
 * <p>Build 352 showed the row in the block editor only; the author asked for a body's radius to be
 * visible the same way, so the restriction is gone - the row appears for any radius node in any of
 * the three tabs.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcRadiusRow354Mixin {

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private int selected;

    @Shadow
    private int inspectorX;

    @Shadow
    private int inspectorY;

    @Shadow
    private int inspectorW;

    @Shadow
    private int inspectorH;

    @Shadow
    private int sidebarScroll;

    @Shadow
    private int sidebarHeight;

    @Shadow
    private boolean buildingInspector;

    @Shadow
    private void remember() {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$visibleRow(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!RadiusPass354.isRadiusNode(node)) return;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            Button toggle = button("gui.settledlands.block.visible", x, y, width, 16, () -> {
                remember();
                RadiusPass354.setVisible(node, !RadiusPass354.visible(node));
                changed();
                widgets();
            });
            if (toggle == null) return;
            toggle.setMessage(Component.translatable(RadiusPass354.visible(node)
                    ? "gui.settledlands.block.visible_on" : "gui.settledlands.block.visible_off"));
            toggle.visible = y >= inspectorY + 22
                    && y + toggle.getHeight() <= inspectorY + inspectorH - 4;
        } finally {
            buildingInspector = false;
        }
        sidebarHeight += 22;
    }
}
