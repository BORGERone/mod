package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.TeleportCard424;
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
 * Build 424: inspector sidebar for the «Телепорт» node (world selector dropdown + X/Y/Z coordinate
 * inputs with blank = 0 + incoming-If helper + unlink/delete).
 *
 * <p>Uses named methods + {@link TeleportCard424} callback factories so zero synthetic lambdas are
 * merged into {@code NpcDialogueGraphScreen} (clean against {@code mixin_clash_check.py}).</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class TeleportInspectorMixin424 {

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
    private List inspectorWidgets;

    private NpcDialogueGraph.Node settledlands$teleportNode424;
    private int settledlands$teleportSelected424 = -1;
    private int settledlands$teleportDeleteId424 = -1;

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
    private void propertyChange(Runnable action) {
        throw new AssertionError();
    }

    @Shadow
    private void label(String key, int x, int y) {
        throw new AssertionError();
    }

    @Shadow
    private EditBox textBox(String value, int maxLength, int x, int y, int width, int height,
            Consumer<String> onChange) {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Shadow
    private int incomingIfSection(NpcDialogueGraph.Node node, int x, int y, int width) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$skipStockForTeleport424(CallbackInfo ci) {
        settledlands$teleportNode424 = null;
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!TeleportNode424.isTeleportNode(node)) return;
        settledlands$teleportNode424 = node;
        settledlands$teleportSelected424 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildTeleportInspector424(CallbackInfo ci) {
        NpcDialogueGraph.Node node = settledlands$teleportNode424;
        settledlands$teleportNode424 = null;
        if (node == null) return;

        selected = settledlands$teleportSelected424;
        settledlands$teleportDeleteId424 = node.id;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        buildingInspector = true;
        try {
            label(TeleportNode424.TITLE_KEY, x, y);
            y += 14;

            label("gui.settledlands.teleport.world", x, y);
            y += 11;

            String currentDim = TeleportNode424.dimensionOf(node);
            String customDim = TeleportNode424.worldIndexForId(currentDim) < 0 ? currentDim : null;
            NpcUi.NpcDropdown dropdown = NpcUi.dropdown(
                    x, y, width,
                    TeleportCard424.worldDropdownEntries(currentDim),
                    TeleportCard424.worldDropdownIndex(currentDim),
                    TeleportCard424.worldPicker(this::settledlands$tpCommit424, node, customDim));
            screen.addRenderableWidget(dropdown);
            inspectorWidgets.add(dropdown);
            y += 24;

            label("gui.settledlands.teleport.coords_hint", x, y);
            y += 12;

            int labelW = 16;
            int boxX = x + labelW;
            int boxW = Math.max(30, width - labelW);

            label("gui.settledlands.teleport.x", x, y + 6);
            EditBox xBox = textBox(TeleportNode424.rawX(node), TeleportNode424.MAX_COORD_LEN,
                    boxX, y, boxW, 20, TeleportCard424.xSetter(node));
            xBox.setHint(Component.literal("0"));
            xBox.setFilter(TeleportNode424::isCoordInput);
            y += 24;

            label("gui.settledlands.teleport.y", x, y + 6);
            EditBox yBox = textBox(TeleportNode424.rawY(node), TeleportNode424.MAX_COORD_LEN,
                    boxX, y, boxW, 20, TeleportCard424.ySetter(node));
            yBox.setHint(Component.literal("0"));
            yBox.setFilter(TeleportNode424::isCoordInput);
            y += 24;

            label("gui.settledlands.teleport.z", x, y + 6);
            EditBox zBox = textBox(TeleportNode424.rawZ(node), TeleportNode424.MAX_COORD_LEN,
                    boxX, y, boxW, 20, TeleportCard424.zSetter(node));
            zBox.setHint(Component.literal("0"));
            zBox.setFilter(TeleportNode424::isCoordInput);
            y += 26;

            y = incomingIfSection(node, x, y, width);
            y += 4;

            int half = (width - 4) / 2;
            button("gui.settledlands.graph.unlink", x, y, half, 16,
                    TeleportCard424.unlinkAction(screen, graph, node.id));
            button("gui.settledlands.graph.delete", x + half + 4, y, width - half - 4, 16,
                    this::settledlands$tpDelete424);
            y += 22;
        } finally {
            buildingInspector = false;
        }

        sidebarHeight = y + sidebarScroll - (inspectorY + 24);
        int topBound = inspectorY + 22;
        int bottomBound = inspectorY + inspectorH - 4;
        for (int i = startIndex; i < inspectorWidgets.size(); i++) {
            AbstractWidget w = (AbstractWidget) inspectorWidgets.get(i);
            w.visible = w.getY() >= topBound && w.getY() + w.getHeight() <= bottomBound;
        }
    }

    private void settledlands$tpCommit424(Runnable action) {
        propertyChange(action);
    }

    private void settledlands$tpDelete424() {
        if (graph == null || settledlands$teleportDeleteId424 < 0) return;
        remember();
        graph.remove(settledlands$teleportDeleteId424);
        selected = -1;
        changed();
        widgets();
    }
}
