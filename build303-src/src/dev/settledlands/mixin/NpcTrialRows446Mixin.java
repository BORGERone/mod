package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.TrialGraphClient446;
import dev.settledlands.npc.client.TrialModuleClient447;
import dev.settledlands.trial.TrialGraph446;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: inspector sidebar for the Trial Spawner card ({@code TRIAL_TYPE = 60}).
 * Zero synthetic lambdas are declared in this mixin class.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcTrialRows446Mixin {

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

    private NpcDialogueGraph.Node settledlands$trialCard446;
    private int settledlands$trialSelected446 = -1;

    @Shadow
    private void label(String key, int x, int y) {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$skipStockForTrialCard446(CallbackInfo ci) {
        settledlands$trialCard446 = null;
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!TrialGraph446.isTrialNode(node)) return;
        settledlands$trialCard446 = node;
        settledlands$trialSelected446 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildTrialInspector446(CallbackInfo ci) {
        NpcDialogueGraph.Node node = settledlands$trialCard446;
        settledlands$trialCard446 = null;
        if (node == null) return;

        selected = settledlands$trialSelected446;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        buildingInspector = true;
        try {
            label(TrialGraph446.TITLE_KEY, x, y);
            y += 14;

            String[] summary = TrialGraphClient446.lines(node);
            for (int i = 0; i < summary.length; i++) {
                label(summary[i], x, y);
                y += 12;
            }
            y += 4;

            String[] modSummary = TrialModuleClient447.hubModuleStatusLines(graph, node);
            for (int i = 0; i < modSummary.length; i++) {
                label(modSummary[i], x, y);
                y += 11;
            }
            y += 4;

            button("gui.settledlands.trial.btn_restore_modules", x, y, width, 18,
                    TrialModuleClient447.restoreModulesAction(screen, node));
            y += 22;

            label("gui.settledlands.trial.hint_start", x, y);
            y += 12;
            label("gui.settledlands.trial.hint_clear", x, y);
            y += 12;
            label("gui.settledlands.trial.hint_destroy", x, y);
            y += 12;
            label("gui.settledlands.trial.hint_player", x, y);
            y += 16;

            button("gui.settledlands.trial.btn_clear_sockets", x, y, width, 16,
                    TrialGraphClient446.clearSocketsAction(screen, node));
            y += 20;
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
}
