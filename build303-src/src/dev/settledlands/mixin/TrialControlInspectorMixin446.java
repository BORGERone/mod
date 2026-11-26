package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.TrialControlCard446;
import dev.settledlands.trial.TrialControlNode446;
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
 * Build 446: inspector sidebar for the cross-graph {@link TrialControlNode446} node.
 * Zero synthetic lambdas are declared in this mixin class.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class TrialControlInspectorMixin446 {

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

    private NpcDialogueGraph.Node settledlands$trialCtrlNode446;
    private int settledlands$trialCtrlSelected446 = -1;
    private int settledlands$trialCtrlDeleteId446 = -1;

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
    private void settledlands$skipStockForTrialControl446(CallbackInfo ci) {
        settledlands$trialCtrlNode446 = null;
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!TrialControlNode446.isControlNode(node)) return;
        settledlands$trialCtrlNode446 = node;
        settledlands$trialCtrlSelected446 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildTrialControlInspector446(CallbackInfo ci) {
        NpcDialogueGraph.Node node = settledlands$trialCtrlNode446;
        settledlands$trialCtrlNode446 = null;
        if (node == null) return;

        selected = settledlands$trialCtrlSelected446;
        settledlands$trialCtrlDeleteId446 = node.id;

        TrialControlNode446.Parsed parsed = TrialControlNode446.parsedOrDefault(node);
        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        buildingInspector = true;
        try {
            label(TrialControlNode446.TITLE_KEY, x, y);
            y += 14;

            label("gui.settledlands.trial_ctl.op_prefix", x, y);
            y += 11;
            NpcUi.NpcDropdown opDrop = NpcUi.dropdown(
                    x, y, width,
                    TrialControlCard446.opDropdownEntries(),
                    TrialControlNode446.opIndex(parsed.op),
                    TrialControlCard446.opPicker(
                            this::settledlands$ctrlCommit446,
                            this::settledlands$ctrlRebuild446,
                            node));
            screen.addRenderableWidget(opDrop);
            inspectorWidgets.add(opDrop);
            y += 24;

            if (TrialControlNode446.opUsesConfig(parsed.op)) {
                button("gui.settledlands.trial.btn_configure", x, y, width, 18,
                        TrialControlCard446.openConfigAction(screen, node));
                y += 22;
            } else if (TrialControlNode446.OP_STOP.equals(parsed.op)) {
                button(parsed.killMobs
                                ? "gui.settledlands.trial_ctl.kill_mobs_yes"
                                : "gui.settledlands.trial_ctl.kill_mobs_no",
                        x, y, width, 18,
                        TrialControlCard446.toggleKillMobsAction(
                                this::settledlands$ctrlCommit446,
                                this::settledlands$ctrlRebuild446,
                                node));
                y += 22;
            }

            label("gui.settledlands.teleport.world", x, y);
            y += 11;
            String customDim = (parsed.dimension != null && !parsed.dimension.isBlank()
                    && TeleportNode424.worldIndexForId(parsed.dimension) < 0)
                    ? parsed.dimension : null;
            NpcUi.NpcDropdown dimDrop = NpcUi.dropdown(
                    x, y, width,
                    TrialControlCard446.worldDropdownEntries(parsed.dimension),
                    TrialControlCard446.worldDropdownIndex(parsed.dimension),
                    TrialControlCard446.worldPicker(
                            this::settledlands$ctrlCommit446, node, customDim));
            screen.addRenderableWidget(dimDrop);
            inspectorWidgets.add(dimDrop);
            y += 24;

            label("gui.settledlands.trial_ctl.coords_hint", x, y);
            y += 12;

            int labelW = 16;
            int boxX = x + labelW;
            int boxW = Math.max(30, width - labelW);

            label("gui.settledlands.teleport.x", x, y + 6);
            EditBox xBox = textBox(parsed.rawX, TrialControlNode446.MAX_COORD_LEN,
                    boxX, y, boxW, 20, TrialControlCard446.xSetter(node));
            xBox.setHint(Component.literal("0 / ~"));
            xBox.setFilter(TrialControlCard446.coordFilter());
            y += 24;

            label("gui.settledlands.teleport.y", x, y + 6);
            EditBox yBox = textBox(parsed.rawY, TrialControlNode446.MAX_COORD_LEN,
                    boxX, y, boxW, 20, TrialControlCard446.ySetter(node));
            yBox.setHint(Component.literal("0 / ~"));
            yBox.setFilter(TrialControlCard446.coordFilter());
            y += 24;

            label("gui.settledlands.teleport.z", x, y + 6);
            EditBox zBox = textBox(parsed.rawZ, TrialControlNode446.MAX_COORD_LEN,
                    boxX, y, boxW, 20, TrialControlCard446.zSetter(node));
            zBox.setHint(Component.literal("0 / ~"));
            zBox.setFilter(TrialControlCard446.coordFilter());
            y += 24;

            button("gui.settledlands.trial_ctl.btn_here", x, y, width, 16,
                    TrialControlCard446.setTildeCoordsAction(
                            this::settledlands$ctrlCommit446,
                            this::settledlands$ctrlRebuild446,
                            node));
            y += 20;

            y = incomingIfSection(node, x, y, width);
            y += 4;

            int half = (width - 4) / 2;
            button("gui.settledlands.graph.unlink", x, y, half, 16,
                    TrialControlCard446.unlinkAction(screen, graph, node.id));
            button("gui.settledlands.graph.delete", x + half + 4, y, width - half - 4, 16,
                    this::settledlands$ctrlDelete446);
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

    private void settledlands$ctrlCommit446(Runnable action) {
        propertyChange(action);
    }

    private void settledlands$ctrlRebuild446() {
        widgets();
    }

    private void settledlands$ctrlDelete446() {
        if (graph == null || settledlands$trialCtrlDeleteId446 < 0) return;
        remember();
        graph.remove(settledlands$trialCtrlDeleteId446);
        selected = -1;
        changed();
        widgets();
    }
}
