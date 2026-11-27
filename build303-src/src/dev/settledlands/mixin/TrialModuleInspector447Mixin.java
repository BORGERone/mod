package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.LootPoolCard436;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.TrialGraphClient446;
import dev.settledlands.npc.client.TrialModuleClient447;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
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
 * Build 447: dedicated per-module inspector sidebar for the 8 {@link TrialModule447} cards.
 * Zero synthetic lambdas are declared in this mixin class (all callbacks live in
 * {@link TrialModuleClient447}).
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class TrialModuleInspector447Mixin {

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

    private NpcDialogueGraph.Node settledlands$modNode447;
    private int settledlands$modSelected447 = -1;

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

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$skipStockForModule447(CallbackInfo ci) {
        settledlands$modNode447 = null;
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!TrialModule447.isModuleNode(node)) return;
        settledlands$modNode447 = node;
        settledlands$modSelected447 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildModuleInspector447(CallbackInfo ci) {
        NpcDialogueGraph.Node node = settledlands$modNode447;
        settledlands$modNode447 = null;
        if (node == null) return;

        selected = settledlands$modSelected447;
        int slot = TrialModule447.slotOf(node);
        if (slot < 0) return;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        buildingInspector = true;
        try {
            label(TrialModule447.titleKey(slot), x, y);
            y += 14;

            label(TrialModuleClient447.linkStatusText(graph, node), x, y);
            y += 12;

            switch (slot) {
                case TrialModule447.SLOT_MODE -> y = settledlands$buildModeRows447(screen, node, x, y, width);
                case TrialModule447.SLOT_RADIUS -> y = settledlands$buildRadiusRows447(node, x, y, width);
                case TrialModule447.SLOT_WAVE -> y = settledlands$buildWaveRows447(node, x, y, width);
                case TrialModule447.SLOT_TIMING -> y = settledlands$buildTimingRows447(node, x, y, width);
                case TrialModule447.SLOT_MOBS -> y = settledlands$buildMobsRows447(screen, node, x, y, width);
                case TrialModule447.SLOT_REWARDS -> y = settledlands$buildRewardsRows447(screen, node, x, y, width);
                case TrialModule447.SLOT_OMINOUS -> y = settledlands$buildOminousRows447(screen, node, x, y, width);
                case TrialModule447.SLOT_PROTECT -> y = settledlands$buildProtectRows447(screen, node, x, y, width);
                default -> {
                }
            }

            y += 6;
            boolean linked = node.next >= 0 && TrialGraph446.isTrialNode(graph.get(node.next));
            String attachKey = linked
                    ? "gui.settledlands.trial.mod.btn_unlink"
                    : "gui.settledlands.trial.mod.btn_attach";
            int half = (width - 4) / 2;
            button(attachKey, x, y, half, 16,
                    TrialModuleClient447.toggleAttachHubAction(screen, graph, node));
            button("gui.settledlands.graph.delete", x + half + 4, y, width - half - 4, 16,
                    TrialModuleClient447.deleteModuleAction(screen, graph, node));
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

    private int settledlands$intRow447(NpcDialogueGraph.Node node, String labelKey, String value,
            int fieldId, int x, int y, int width) {
        label(labelKey, x, y);
        y += 11;
        EditBox box = textBox(value, 10, x, y, width, 18,
                TrialModuleClient447.scalarSetter(graph, node, fieldId));
        box.setFilter(TrialModuleClient447.intFilter());
        return y + 22;
    }

    private int settledlands$buildModeRows447(NpcDialogueGraphScreen screen, NpcDialogueGraph.Node node,
            int x, int y, int width) {
        button(TrialModuleClient447.modeButtonText(node), x, y, width, 18,
                TrialModuleClient447.cycleModeAction(screen, graph, node));
        y += 22;
        button(TrialModuleClient447.scaleButtonText(node), x, y, width, 18,
                TrialModuleClient447.toggleScaleAction(screen, graph, node));
        return y + 22;
    }

    private int settledlands$buildRadiusRows447(NpcDialogueGraph.Node node, int x, int y, int width) {
        TrialModule447.RadiusData d = TrialModule447.readRadius(node);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.act_range",
                Integer.toString(d.activationRange), TrialModuleClient447.FIELD_RADIUS_ACT, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.spawn_range",
                Integer.toString(d.spawnRange), TrialModuleClient447.FIELD_RADIUS_SPAWN, x, y, width);
        return y;
    }

    private int settledlands$buildWaveRows447(NpcDialogueGraph.Node node, int x, int y, int width) {
        TrialModule447.WaveData d = TrialModule447.readWave(node);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.total_mobs",
                Integer.toString(d.totalMobs), TrialModuleClient447.FIELD_WAVE_TOTAL, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.per_player_total",
                Integer.toString(d.totalMobsAddedPerPlayer),
                TrialModuleClient447.FIELD_WAVE_PER_PLAYER, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.sim_mobs",
                Integer.toString(d.simultaneousMobs), TrialModuleClient447.FIELD_WAVE_SIM, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.per_player_sim",
                Integer.toString(d.simultaneousMobsAddedPerPlayer),
                TrialModuleClient447.FIELD_WAVE_SIM_PER_PLAYER, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.batch",
                Integer.toString(d.spawnBatchSize), TrialModuleClient447.FIELD_WAVE_BATCH, x, y, width);
        return y;
    }

    private int settledlands$buildTimingRows447(NpcDialogueGraph.Node node, int x, int y, int width) {
        TrialModule447.TimingData d = TrialModule447.readTiming(node);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.min_delay",
                Integer.toString(d.minSpawnDelayTicks), TrialModuleClient447.FIELD_TIMING_MIN, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.max_delay",
                Integer.toString(d.maxSpawnDelayTicks), TrialModuleClient447.FIELD_TIMING_MAX, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.cooldown",
                Integer.toString(d.cooldownTicks), TrialModuleClient447.FIELD_TIMING_CD, x, y, width);
        return y;
    }

    private int settledlands$buildMobsRows447(NpcDialogueGraphScreen screen, NpcDialogueGraph.Node node,
            int x, int y, int width) {
        LootPoolCard436.ensureClientListenersRegistered();

        label("gui.settledlands.trial.mob_pool.file", x, y);
        y += 11;

        String currentFile = TrialModule447.mobPoolFile(node);
        List<String> options = TrialModuleClient447.mobPoolOptions(currentFile);
        int dropWidth = Math.max(60, width - 24);
        Consumer<Runnable> commit = TrialModuleClient447.commitAction(screen);

        NpcUi.NpcDropdown dropdown = NpcUi.dropdown(
                x, y, dropWidth,
                TrialModuleClient447.mobPoolDropdownEntries(options),
                TrialModuleClient447.mobPoolDropdownIndex(options, currentFile),
                TrialModuleClient447.mobPoolPicker(commit, screen, node, options));
        LootPoolCard436.trackTrialMobDropdown(dropdown, options, screen, node, commit);
        screen.addRenderableWidget(dropdown);
        inspectorWidgets.add(dropdown);

        AbstractWidget gearBtn = TrialModuleClient447.mobPoolGearButton(
                x + width - 20, y, screen, node);
        screen.addRenderableWidget(gearBtn);
        inspectorWidgets.add(gearBtn);
        y += 24;

        List<String> summary = TrialModuleClient447.mobPoolSummaryLines(node);
        for (int i = 0; i < summary.size(); i++) {
            label(summary.get(i), x, y);
            y += 11;
        }
        y += 2;
        label("gui.settledlands.trial.mob_pool.folder_hint", x, y);
        return y + 12;
    }

    private int settledlands$buildRewardsRows447(NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node, int x, int y, int width) {
        LootPoolCard436.ensureClientListenersRegistered();

        label("gui.settledlands.trial.reward_pool.file", x, y);
        y += 11;

        String currentFile = TrialModule447.rewardPoolFile(node);
        List<String> options = TrialModuleClient447.rewardPoolOptions(currentFile);
        int dropWidth = Math.max(60, width - 24);
        Consumer<Runnable> commit = TrialModuleClient447.commitAction(screen);

        NpcUi.NpcDropdown dropdown = NpcUi.dropdown(
                x, y, dropWidth,
                TrialModuleClient447.rewardPoolDropdownEntries(options),
                TrialModuleClient447.rewardPoolDropdownIndex(options, currentFile),
                TrialModuleClient447.rewardPoolPicker(commit, screen, node, options));
        LootPoolCard436.trackTrialRewardDropdown(dropdown, options, screen, node, commit);
        screen.addRenderableWidget(dropdown);
        inspectorWidgets.add(dropdown);

        AbstractWidget gearBtn = TrialModuleClient447.rewardPoolGearButton(
                x + width - 20, y, screen, node);
        screen.addRenderableWidget(gearBtn);
        inspectorWidgets.add(gearBtn);
        y += 24;

        List<String> summary = TrialModuleClient447.rewardPoolSummaryLines(node);
        for (int i = 0; i < summary.size(); i++) {
            label(summary.get(i), x, y);
            y += 11;
        }
        y += 2;
        label("gui.settledlands.trial.reward_pool.folder_hint", x, y);
        return y + 12;
    }

    private int settledlands$buildOminousRows447(NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node, int x, int y, int width) {
        TrialModule447.OminousData d = TrialModule447.readOminous(node);
        label("gui.settledlands.trial.cfg.ominous_mult", x, y);
        y += 11;
        EditBox multBox = textBox(TrialModuleClient447.formatFloat(d.mobMultiplier), 8,
                x, y, width, 18,
                TrialModuleClient447.scalarSetter(graph, node, TrialModuleClient447.FIELD_OMINOUS_MULT));
        multBox.setFilter(TrialModuleClient447.floatFilter());
        y += 22;

        button(TrialModuleClient447.ominousToggleLabel(node, 0), x, y, width, 18,
                TrialModuleClient447.toggleOminousAction(screen, graph, node, 0));
        y += 22;
        button(TrialModuleClient447.ominousToggleLabel(node, 1), x, y, width, 18,
                TrialModuleClient447.toggleOminousAction(screen, graph, node, 1));
        y += 22;
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.ominous_interval",
                Integer.toString(d.hazardIntervalTicks),
                TrialModuleClient447.FIELD_OMINOUS_INTERVAL, x, y, width);
        button(TrialModuleClient447.ominousToggleLabel(node, 2), x, y, width, 18,
                TrialModuleClient447.toggleOminousAction(screen, graph, node, 2));
        return y + 22;
    }

    private int settledlands$buildProtectRows447(NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node, int x, int y, int width) {
        TrialModule447.ProtectData d = TrialModule447.readProtect(node);
        button(TrialModuleClient447.protectToggleLabel(node, 0), x, y, width, 18,
                TrialModuleClient447.toggleProtectAction(screen, graph, node, 0));
        y += 22;
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.nobreak_r",
                Integer.toString(d.noBreakRadius), TrialModuleClient447.FIELD_PROTECT_RADIUS, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.nobreak_s",
                Integer.toString(d.noBreakSeconds), TrialModuleClient447.FIELD_PROTECT_SECS, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.nobreak_amp",
                Integer.toString(d.noBreakAmplifier), TrialModuleClient447.FIELD_PROTECT_AMP, x, y, width);
        button(TrialModuleClient447.protectToggleLabel(node, 1), x, y, width, 18,
                TrialModuleClient447.toggleProtectAction(screen, graph, node, 1));
        y += 22;
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.destruct_waves",
                Integer.toString(d.selfDestructAfterWaves),
                TrialModuleClient447.FIELD_PROTECT_WAVES, x, y, width);
        y = settledlands$intRow447(node, "gui.settledlands.trial.cfg.destruct_chance",
                Integer.toString(d.selfDestructChancePercent),
                TrialModuleClient447.FIELD_PROTECT_CHANCE, x, y, width);
        return y;
    }
}
