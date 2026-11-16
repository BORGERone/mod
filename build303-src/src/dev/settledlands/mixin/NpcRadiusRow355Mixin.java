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
 * Build 355: the "Видимость" row of a radius node, and it takes effect on the click.
 *
 * <p>The row itself is build 354's: one named control in the inspector, next to the radius and the
 * cooldown, writing the same flag build 320's card box writes ({@code Node#timerRepeat}). What was
 * wrong is that the ring is drawn by the <b>server</b> from the graph it has stored, while the flag
 * lived only in the editor until the graph was saved - so a toggle that looked switched off kept the
 * ring where it was, and there was no way to tell the two apart. The row now saves the graph right
 * after flipping the flag: the click is what the server sees, not the click plus a save the author
 * has to remember.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcRadiusRow355Mixin {

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
    private boolean save() {
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
                save();     // the ring is drawn by the server: hand it the new flag now
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
