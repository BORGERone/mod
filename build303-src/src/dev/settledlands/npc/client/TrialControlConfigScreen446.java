package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcItems;
import dev.settledlands.trial.RangeSpec450;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Build 446: self-contained 4-tab configuration screen for {@code trialtuner:trial_spawner}
 * parameters — used both from the Trial Spawner card's inspector (2a) and from the cross-graph
 * {@code Триал-спавнер} control node's {@code CONFIGURE} operation (2b).
 */
public final class TrialControlConfigScreen446 extends Screen {

    private static final int TAB_GENERAL = 0;
    private static final int TAB_OMINOUS = 1;
    private static final int TAB_MOBS = 2;
    private static final int TAB_REWARDS = 3;

    private static final int VIEW_MAIN = 0;
    private static final int VIEW_MOB_PICKER = 1;
    private static final int VIEW_ITEM_PICKER = 2;

    private static final int MOB_ROW_H = 24;
    private static final int REWARD_ROW_H = 24;
    private static final int PICK_CELL_H = 22;

    private final NpcDialogueGraphScreen parentScreen;
    private final NpcDialogueGraph.Node targetNode;
    private final boolean controlNodeMode;
    private final TrialConfig446 cfg;

    private final List<GuiEventListener> dynamicWidgets = new ArrayList<>();
    private final List<GraphTooltips448.ConfigArea> tipAreas448 = new ArrayList<>();

    private int activeTab = TAB_GENERAL;
    private int viewMode = VIEW_MAIN;
    private int pickerRow = -1;
    private int listScroll;
    private int pickerScroll;
    private String pickerQuery = "";
    private String statusText = "";
    private int statusColor = 0xFF9AA5B8;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyTop;
    private int bodyBottom;

    // Buffered text fields for numeric values on General / Ominous so edits survive tab switches.
    private String totalMobsStr;
    private String simMobsStr;
    private String batchStr;
    private String actRangeStr;
    private String spawnRangeStr;
    private String delayStr;
    private String cooldownStr;
    private String mobsPerPlayerStr;
    private String simPerPlayerStr;
    private String selfWavesStr;
    private String selfChanceStr;
    private String ominousMultStr;
    private String ominousIntervalStr;

    private List<MobOption> allMobs;
    private final List<MobOption> filteredMobs = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private int pickerCols = 4;
    private int pickerVisibleRows = 5;
    private int pickerGridX;
    private int pickerGridY;
    private int pickerGridW;

    private static final class MobOption {
        final String id;
        final String displayName;
        final String searchLower;

        MobOption(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            this.searchLower = (id + " " + displayName).toLowerCase(Locale.ROOT);
        }
    }

    public TrialControlConfigScreen446(NpcDialogueGraphScreen parentScreen,
            NpcDialogueGraph.Node targetNode, boolean controlNodeMode) {
        super(Component.translatable("gui.settledlands.trial.cfg.title"));
        this.parentScreen = parentScreen;
        this.targetNode = targetNode;
        this.controlNodeMode = controlNodeMode;
        TrialConfig446 base = controlNodeMode
                ? TrialControlNode446.parsedOrDefault(targetNode).config()
                : TrialGraph446.configOf(targetNode);
        this.cfg = base == null ? TrialConfig446.defaults() : base.copy();
        syncBuffersFromConfig();
    }

    private void syncBuffersFromConfig() {
        totalMobsStr = cfg.rangeOrIntText("totalMobs", cfg.totalMobs);
        simMobsStr = cfg.rangeOrIntText("simultaneousMobs", cfg.simultaneousMobs);
        batchStr = cfg.rangeOrIntText("spawnBatchSize", cfg.spawnBatchSize);
        actRangeStr = cfg.rangeOrIntText("activationRange", cfg.activationRange);
        spawnRangeStr = cfg.rangeOrIntText("spawnRange", cfg.spawnRange);
        delayStr = RangeSpec450.formatInt(cfg.minSpawnDelayTicks, cfg.maxSpawnDelayTicks);
        cooldownStr = cfg.rangeOrIntText("cooldownTicks", cfg.cooldownTicks);
        mobsPerPlayerStr = cfg.rangeOrIntText("totalMobsAddedPerPlayer", cfg.totalMobsAddedPerPlayer);
        simPerPlayerStr = cfg.rangeOrIntText("simultaneousMobsAddedPerPlayer", cfg.simultaneousMobsAddedPerPlayer);
        selfWavesStr = cfg.rangeOrIntText("selfDestructAfterWaves", cfg.selfDestructAfterWaves);
        selfChanceStr = cfg.rangeOrIntText("selfDestructChancePercent", cfg.selfDestructChancePercent);
        ominousMultStr = cfg.rangeOrFloatText("ominousMobMultiplier", cfg.ominousMobMultiplier);
        ominousIntervalStr = cfg.rangeOrIntText("ominousHazardIntervalTicks", cfg.ominousHazardIntervalTicks);
    }

    private void commitBuffersToConfig() {
        cfg.setIntFieldWithRange("totalMobs", totalMobsStr, cfg.totalMobs, 1, 256);
        cfg.setIntFieldWithRange("simultaneousMobs", simMobsStr, cfg.simultaneousMobs, 1, 64);
        cfg.setIntFieldWithRange("spawnBatchSize", batchStr, cfg.spawnBatchSize, 1, 8);
        cfg.setIntFieldWithRange("activationRange", actRangeStr, cfg.activationRange, 1, 64);
        cfg.setIntFieldWithRange("spawnRange", spawnRangeStr, cfg.spawnRange, 1, 16);
        RangeSpec450.IntRange dr = RangeSpec450.parseInt(
                delayStr, cfg.minSpawnDelayTicks, cfg.maxSpawnDelayTicks, 1, 12000);
        cfg.minSpawnDelayTicks = dr.min;
        cfg.maxSpawnDelayTicks = dr.max;
        cfg.setIntFieldWithRange("cooldownTicks", cooldownStr, cfg.cooldownTicks, 0, 720000);
        cfg.setIntFieldWithRange("totalMobsAddedPerPlayer", mobsPerPlayerStr, cfg.totalMobsAddedPerPlayer, 0, 64);
        cfg.setIntFieldWithRange("simultaneousMobsAddedPerPlayer", simPerPlayerStr,
                cfg.simultaneousMobsAddedPerPlayer, 0, 32);
        cfg.setIntFieldWithRange("selfDestructAfterWaves", selfWavesStr, cfg.selfDestructAfterWaves, 1, 100);
        cfg.setIntFieldWithRange("selfDestructChancePercent", selfChanceStr, cfg.selfDestructChancePercent, 0, 100);
        cfg.setFloatFieldWithRange("ominousMobMultiplier", ominousMultStr, cfg.ominousMobMultiplier, 1.0F, 4.0F);
        cfg.setIntFieldWithRange("ominousHazardIntervalTicks", ominousIntervalStr,
                cfg.ominousHazardIntervalTicks, 20, 12000);
        syncBuffersFromConfig();
    }

    @Override
    protected void init() {
        panelW = Math.max(Math.min(width - 16, 620), Math.min(width - 8, 360));
        panelH = Math.max(Math.min(height - 16, 360), Math.min(height - 8, 220));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        bodyTop = panelY + 52;
        bodyBottom = panelY + panelH - 36;
        rebuildView();
    }

    private void clearDynamic() {
        for (int i = 0; i < dynamicWidgets.size(); i++) {
            removeWidget(dynamicWidgets.get(i));
        }
        dynamicWidgets.clear();
        tipAreas448.clear();
    }

    private void addTip(String slotId, Object widget, int x, int y, int w, int h,
            String tag, String arg) {
        tipAreas448.add(new GraphTooltips448.ConfigArea(slotId, widget, x, y, w, h, tag, arg));
    }

    private <T extends GuiEventListener> T addDyn(T w) {
        addRenderableWidget(w);
        dynamicWidgets.add(w);
        return w;
    }

    private void rebuildView() {
        clearDynamic();
        if (viewMode != VIEW_MAIN) {
            buildPickerView();
            return;
        }
        buildMainHeaderAndFooter();
        if (activeTab == TAB_GENERAL) buildGeneralTab();
        else if (activeTab == TAB_OMINOUS) buildOminousTab();
        else if (activeTab == TAB_MOBS) buildMobsTab();
        else buildRewardsTab();
    }

    private void buildMainHeaderAndFooter() {
        int tabX = panelX + 10;
        int tabY = panelY + 26;
        int tabW = (panelW - 20 - 12) / 4;
        String[] keys = {
            "gui.settledlands.trial.cfg.tab_general",
            "gui.settledlands.trial.cfg.tab_ominous",
            "gui.settledlands.trial.cfg.tab_mobs",
            "gui.settledlands.trial.cfg.tab_rewards",
        };
        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            String lbl = Component.translatable(keys[i]).getString();
            if (i == activeTab) lbl = "\u25B8 " + lbl;
            Button tabBtn = Button.builder(Component.literal(lbl), b -> {
                commitBuffersToConfig();
                activeTab = tabIdx;
                listScroll = 0;
                rebuildView();
            }).bounds(tabX + i * (tabW + 4), tabY, tabW, 18).build();
            tabBtn.active = i != activeTab;
            addDyn(tabBtn);
            addTip("cfg:tab_" + i, tabBtn, tabX + i * (tabW + 4), tabY, tabW, 18, "tab", keys[i]);
        }

        int btnY = panelY + panelH - 28;
        int right = panelX + panelW - 10;
        int btnW = 104;

        Button applyClose = Button.builder(
                Component.translatable("gui.settledlands.trial.cfg.save_close"),
                b -> applyAndClose())
                .bounds(right - btnW * 2 - 6, btnY, btnW, 20).build();
        Button cancelBtn = Button.builder(
                Component.translatable("gui.settledlands.trial.cfg.cancel"),
                b -> returnToParent())
                .bounds(right - btnW, btnY, btnW, 20).build();
        Button resetBtn = Button.builder(
                Component.translatable("gui.settledlands.trial.cfg.reset"),
                b -> {
                    TrialConfig446 fresh = TrialConfig446.defaults();
                    cfg.copyFrom(fresh);
                    syncBuffersFromConfig();
                    listScroll = 0;
                    statusText = Component.translatable("gui.settledlands.trial.cfg.reset_done").getString();
                    statusColor = 0xFF8BE0B0;
                    rebuildView();
                }).bounds(panelX + 10, btnY, 96, 20).build();
        addDyn(resetBtn);
        addDyn(applyClose);
        addDyn(cancelBtn);
        addTip("cfg:reset", resetBtn, panelX + 10, btnY, 96, 20, "reset", "");
        addTip("cfg:save", applyClose, right - btnW * 2 - 6, btnY, btnW, 20, "save_close", "");
        addTip("cfg:cancel", cancelBtn, right - btnW, btnY, btnW, 20, "cancel", "");
    }

    private void buildGeneralTab() {
        int leftX = panelX + 12;
        int colW = (panelW - 36) / 2;
        int rightX = leftX + colW + 12;
        int y = bodyTop + 4;

        Button modeBtn = Button.builder(modeLabel(), b -> {
            cfg.mode = TrialConfig446.nextMode(cfg.mode);
            b.setMessage(modeLabel());
        }).bounds(leftX, y, colW, 18).build();
        addDyn(modeBtn);
        addTip("cfg:mode", modeBtn, leftX, y, colW, 18, "mode", "");

        Button noBreakBtn = Button.builder(toggleLabel("gui.settledlands.trial.cfg.prevent_break",
                cfg.noBreakEnabled), b -> {
            cfg.noBreakEnabled = !cfg.noBreakEnabled;
            b.setMessage(toggleLabel("gui.settledlands.trial.cfg.prevent_break", cfg.noBreakEnabled));
        }).bounds(rightX, y, colW, 18).build();
        addDyn(noBreakBtn);
        addTip("cfg:nobreak", noBreakBtn, rightX, y, colW, 18, "prevent_break", "");
        y += 24;

        int fieldW = 64;
        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.total_mobs",
                totalMobsStr, v -> totalMobsStr = v);
        addLabeledBox(rightX, y, colW, fieldW, "gui.settledlands.trial.cfg.sim_mobs",
                simMobsStr, v -> simMobsStr = v);
        y += 22;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.batch",
                batchStr, v -> batchStr = v);
        addLabeledBox(rightX, y, colW, fieldW, "gui.settledlands.trial.cfg.act_range",
                actRangeStr, v -> actRangeStr = v);
        y += 22;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.spawn_range",
                spawnRangeStr, v -> spawnRangeStr = v);
        addLabeledBox(rightX, y, colW, fieldW, "gui.settledlands.trial.cfg.cooldown",
                cooldownStr, v -> cooldownStr = v);
        y += 22;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.delay",
                delayStr, v -> delayStr = v);
        y += 24;

        Button scaleBtn = Button.builder(toggleLabel("gui.settledlands.trial.cfg.scale_players",
                cfg.scaleWithPlayers), b -> {
            cfg.scaleWithPlayers = !cfg.scaleWithPlayers;
            b.setMessage(toggleLabel("gui.settledlands.trial.cfg.scale_players", cfg.scaleWithPlayers));
        }).bounds(leftX, y, colW, 18).build();
        addDyn(scaleBtn);
        addTip("cfg:scale", scaleBtn, leftX, y, colW, 18, "scale_players", "");

        Button destructBtn = Button.builder(toggleLabel("gui.settledlands.trial.cfg.self_destruct",
                cfg.selfDestruct), b -> {
            cfg.selfDestruct = !cfg.selfDestruct;
            b.setMessage(toggleLabel("gui.settledlands.trial.cfg.self_destruct", cfg.selfDestruct));
        }).bounds(rightX, y, colW, 18).build();
        addDyn(destructBtn);
        addTip("cfg:destruct", destructBtn, rightX, y, colW, 18, "self_destruct", "");
        y += 22;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.per_player_total",
                mobsPerPlayerStr, v -> mobsPerPlayerStr = v);
        addLabeledBox(rightX, y, colW, fieldW, "gui.settledlands.trial.cfg.destruct_waves",
                selfWavesStr, v -> selfWavesStr = v);
        y += 22;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.per_player_sim",
                simPerPlayerStr, v -> simPerPlayerStr = v);
        addLabeledBox(rightX, y, colW, fieldW, "gui.settledlands.trial.cfg.destruct_chance",
                selfChanceStr, v -> selfChanceStr = v);
    }

    private void buildOminousTab() {
        int leftX = panelX + 12;
        int colW = Math.min(340, panelW - 24);
        int fieldW = 72;
        int y = bodyTop + 10;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.ominous_mult",
                ominousMultStr, v -> ominousMultStr = v);
        y += 26;

        Button keyBtn = Button.builder(toggleLabel("gui.settledlands.trial.cfg.ominous_keys",
                cfg.ominousKeyConversion), b -> {
            cfg.ominousKeyConversion = !cfg.ominousKeyConversion;
            b.setMessage(toggleLabel("gui.settledlands.trial.cfg.ominous_keys",
                    cfg.ominousKeyConversion));
        }).bounds(leftX, y, colW, 20).build();
        addDyn(keyBtn);
        addTip("cfg:om_keys", keyBtn, leftX, y, colW, 20, "ominous_keys", "");
        y += 26;

        Button hazBtn = Button.builder(toggleLabel("gui.settledlands.trial.cfg.ominous_hazards",
                cfg.ominousHazards), b -> {
            cfg.ominousHazards = !cfg.ominousHazards;
            b.setMessage(toggleLabel("gui.settledlands.trial.cfg.ominous_hazards",
                    cfg.ominousHazards));
        }).bounds(leftX, y, colW, 20).build();
        addDyn(hazBtn);
        addTip("cfg:om_haz", hazBtn, leftX, y, colW, 20, "ominous_hazards", "");
        y += 26;

        addLabeledBox(leftX, y, colW, fieldW, "gui.settledlands.trial.cfg.ominous_interval",
                ominousIntervalStr, v -> ominousIntervalStr = v);
    }

    private void buildMobsTab() {
        int x = panelX + 12;
        int w = panelW - 24;
        int topBtnY = bodyTop + 2;

        Button addBtn = Button.builder(
                Component.translatable("gui.settledlands.trial.cfg.add_mob"), b -> {
            cfg.mobs.add(new TrialConfig446.SpawnEntry("minecraft:zombie", 1));
            int vis = visibleMobRows();
            listScroll = Math.max(0, cfg.mobs.size() - vis);
            rebuildView();
        }).bounds(x, topBtnY, 130, 18).build();
        addDyn(addBtn);
        addTip("cfg:add_mob", addBtn, x, topBtnY, 130, 18, "add_mob", "");

        int listTop = bodyTop + 26;
        int vis = visibleMobRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, cfg.mobs.size() - vis)));

        int pickW = 56;
        int delW = 20;
        int weightW = 64;
        int idW = Math.max(80, w - pickW - delW - weightW - 18);

        for (int s = 0; s < vis; s++) {
            final int idx = listScroll + s;
            if (idx >= cfg.mobs.size()) break;
            final TrialConfig446.SpawnEntry entry = cfg.mobs.get(idx);
            int ry = listTop + s * MOB_ROW_H;

            EditBox idBox = new EditBox(font, x, ry, idW, 18, Component.empty());
            idBox.setMaxLength(96);
            idBox.setValue(entry.entityId);
            idBox.setResponder(val -> entry.entityId = val == null ? "" : val.trim());
            addDyn(idBox);
            addTip("cfg:m_id_" + s, idBox, x, ry, idW, 18, "mob_id", "");

            Button pickBtn = Button.builder(
                    Component.translatable("gui.settledlands.trial.cfg.pick"), b -> {
                Set<String> present = new HashSet<>();
                for (TrialConfig446.SpawnEntry m : cfg.mobs) {
                    if (m != null && m.entityId != null) present.add(m.entityId);
                }
                Minecraft.getInstance().setScreen(new TrialMobCatalogScreen449(
                        this, idx, present, chosen -> {
                            entry.entityId = chosen;
                            rebuildView();
                        }));
            }).bounds(x + idW + 4, ry, pickW, 18).build();
            addDyn(pickBtn);
            addTip("cfg:m_pk_" + s, pickBtn, x + idW + 4, ry, pickW, 18, "mob_pick", "");

            EditBox wgtBox = new EditBox(font, x + idW + pickW + 8, ry, weightW, 18, Component.empty());
            wgtBox.setMaxLength(15);
            wgtBox.setFilter(TrialModuleClient447.intRangeFilter());
            wgtBox.setValue(entry.displayWeight());
            wgtBox.setResponder(val -> {
                RangeSpec450.IntRange rr = RangeSpec450.parseInt(val, entry.weight, entry.weight, 1, 100000);
                entry.weight = rr.min;
                entry.weightRange = rr.isRange() ? rr.format() : "";
            });
            addDyn(wgtBox);
            addTip("cfg:m_wg_" + s, wgtBox, x + idW + pickW + 8, ry, weightW, 18, "mob_weight", "");

            Button delBtn = Button.builder(Component.literal("-"), b -> {
                if (cfg.mobs.size() > 1) {
                    cfg.mobs.remove(idx);
                    rebuildView();
                } else {
                    statusText = Component.translatable("gui.settledlands.trial.cfg.keep_one_mob").getString();
                    statusColor = 0xFFFFB048;
                }
            }).bounds(x + idW + pickW + weightW + 12, ry, delW, 18).build();
            addDyn(delBtn);
            addTip("cfg:m_dl_" + s, delBtn, x + idW + pickW + weightW + 12, ry, delW, 18, "mob_del", "");
        }
    }

    private void buildRewardsTab() {
        int x = panelX + 12;
        int w = panelW - 24;
        int topBtnY = bodyTop + 2;

        Button addBtn = Button.builder(
                Component.translatable("gui.settledlands.trial.cfg.add_reward"), b -> {
            cfg.rewards.add(new TrialConfig446.RewardEntry("minecraft:emerald", 1, 1, 3, 100));
            int vis = visibleRewardRows();
            listScroll = Math.max(0, cfg.rewards.size() - vis);
            rebuildView();
        }).bounds(x, topBtnY, 140, 18).build();
        addDyn(addBtn);
        addTip("cfg:add_rew", addBtn, x, topBtnY, 140, 18, "add_reward", "");

        int listTop = bodyTop + 26;
        int vis = visibleRewardRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, cfg.rewards.size() - vis)));

        int pickW = 52;
        int cntW = 54;
        int wgtW = 48;
        int pctW = 48;
        int delW = 20;
        int idW = Math.max(70, w - pickW - cntW - wgtW - pctW - delW - 20);

        for (int s = 0; s < vis; s++) {
            final int idx = listScroll + s;
            if (idx >= cfg.rewards.size()) break;
            final TrialConfig446.RewardEntry entry = cfg.rewards.get(idx);
            int ry = listTop + s * REWARD_ROW_H;
            int cx = x;

            EditBox idBox = new EditBox(font, cx, ry, idW, 18, Component.empty());
            idBox.setMaxLength(96);
            idBox.setValue(entry.itemId);
            idBox.setResponder(val -> entry.itemId = val == null ? "" : val.trim());
            addDyn(idBox);
            addTip("cfg:r_id_" + s, idBox, cx, ry, idW, 18, "rew_id", "");
            cx += idW + 4;

            Button pickBtn = Button.builder(
                    Component.translatable("gui.settledlands.trial.cfg.pick"), b -> {
                pickerRow = idx;
                pickerQuery = "";
                pickerScroll = 0;
                viewMode = VIEW_ITEM_PICKER;
                rebuildView();
            }).bounds(cx, ry, pickW, 18).build();
            addDyn(pickBtn);
            addTip("cfg:r_pk_" + s, pickBtn, cx, ry, pickW, 18, "rew_pick", "");
            cx += pickW + 4;

            EditBox cntBox = new EditBox(font, cx, ry, cntW, 18, Component.empty());
            cntBox.setMaxLength(12);
            cntBox.setFilter(TrialModuleClient447.intRangeFilter());
            cntBox.setHint(Component.literal("1-3"));
            cntBox.setValue(RangeSpec450.formatInt(entry.minCount, entry.maxCount));
            cntBox.setResponder(val -> {
                RangeSpec450.IntRange rr = RangeSpec450.parseInt(val, entry.minCount, entry.maxCount, 1, 64);
                entry.minCount = rr.min;
                entry.maxCount = rr.max;
            });
            addDyn(cntBox);
            addTip("cfg:r_cnt_" + s, cntBox, cx, ry, cntW, 18, "rew_count", "");
            cx += cntW + 4;

            EditBox wgtBox = new EditBox(font, cx, ry, wgtW, 18, Component.empty());
            wgtBox.setMaxLength(15);
            wgtBox.setFilter(TrialModuleClient447.intRangeFilter());
            wgtBox.setValue(entry.displayWeight());
            wgtBox.setResponder(val -> {
                RangeSpec450.IntRange rr = RangeSpec450.parseInt(val, entry.weight, entry.weight, 1, 100000);
                entry.weight = rr.min;
                entry.weightRange = rr.isRange() ? rr.format() : "";
            });
            addDyn(wgtBox);
            addTip("cfg:r_wg_" + s, wgtBox, cx, ry, wgtW, 18, "rew_weight", "");
            cx += wgtW + 4;

            EditBox pctBox = new EditBox(font, cx, ry, pctW, 18, Component.empty());
            pctBox.setMaxLength(12);
            pctBox.setFilter(TrialModuleClient447.intRangeFilter());
            pctBox.setValue(entry.displayChance());
            pctBox.setResponder(val -> {
                RangeSpec450.IntRange rr = RangeSpec450.parseInt(val, entry.chancePercent, entry.chancePercent, 0, 100);
                entry.chancePercent = rr.min;
                entry.chanceRange = rr.isRange() ? rr.format() : "";
            });
            addDyn(pctBox);
            addTip("cfg:r_ch_" + s, pctBox, cx, ry, pctW, 18, "rew_chance", "");
            cx += pctW + 4;

            Button delBtn = Button.builder(Component.literal("-"), b -> {
                cfg.rewards.remove(idx);
                rebuildView();
            }).bounds(cx, ry, delW, 18).build();
            addDyn(delBtn);
            addTip("cfg:r_dl_" + s, delBtn, cx, ry, delW, 18, "rew_del", "");
        }
    }

    private void buildPickerView() {
        int x = panelX + 10;
        int w = panelW - 20;
        Button backBtn = Button.builder(
                Component.translatable("gui.settledlands.trial.cfg.back"), b -> {
            viewMode = VIEW_MAIN;
            rebuildView();
        }).bounds(panelX + panelW - 86, panelY + 6, 76, 18).build();
        addDyn(backBtn);
        addTip("cfg:pk_back", backBtn, panelX + panelW - 86, panelY + 6, 76, 18, "picker_back", "");

        EditBox searchBox = new EditBox(font, x, panelY + 28, w, 18,
                Component.translatable("gui.settledlands.trial.cfg.search"));
        searchBox.setMaxLength(80);
        searchBox.setHint(Component.translatable("gui.settledlands.trial.cfg.search"));
        searchBox.setValue(pickerQuery);
        searchBox.setResponder(text -> {
            pickerQuery = text == null ? "" : text;
            pickerScroll = 0;
            refreshPickerData();
        });
        addDyn(searchBox);
        addTip("cfg:pk_srch", searchBox, x, panelY + 28, w, 18, "picker_search", "");
        setInitialFocus(searchBox);

        pickerGridX = x;
        pickerGridY = panelY + 52;
        pickerGridW = w;
        int gridH = Math.max(44, panelY + panelH - 16 - pickerGridY);
        pickerCols = viewMode == VIEW_ITEM_PICKER ? Math.max(6, pickerGridW / 24) : 2;
        pickerVisibleRows = Math.max(2, gridH / PICK_CELL_H);
        refreshPickerData();
    }

    private void refreshPickerData() {
        String q = pickerQuery.trim().toLowerCase(Locale.ROOT);
        if (viewMode == VIEW_MOB_PICKER) {
            if (allMobs == null) {
                allMobs = loadMobOptions();
            }
            filteredMobs.clear();
            for (int i = 0; i < allMobs.size(); i++) {
                MobOption opt = allMobs.get(i);
                if (q.isEmpty() || opt.searchLower.contains(q)) {
                    filteredMobs.add(opt);
                }
            }
        } else if (viewMode == VIEW_ITEM_PICKER) {
            List<ItemStack> stacks = NpcItems.allStacks();
            filteredItems.clear();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (stack == null || stack.isEmpty()) continue;
                if (q.isEmpty()) {
                    filteredItems.add(stack);
                    continue;
                }
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
                String id = rl == null ? "" : rl.toString().toLowerCase(Locale.ROOT);
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (id.contains(q) || name.contains(q)) {
                    filteredItems.add(stack);
                }
            }
        }
    }

    private static List<MobOption> loadMobOptions() {
        List<MobOption> out = new ArrayList<>();
        try {
            for (EntityType type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == null) continue;
                ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (rl == null) continue;
                String id = rl.toString();
                if (isNonLivingNoise(id)) continue;
                String name = "";
                try {
                    name = type.getDescription().getString();
                } catch (Throwable ignored) {
                }
                out.add(new MobOption(id, name));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean isNonLivingNoise(String id) {
        return id.endsWith("arrow") || id.endsWith("boat") || id.endsWith("minecart")
                || id.endsWith("item") || id.endsWith("experience_orb")
                || id.endsWith("falling_block") || id.endsWith("area_effect_cloud")
                || id.endsWith("lightning_bolt") || id.endsWith("fireball")
                || id.endsWith("snowball") || id.endsWith("egg") || id.endsWith("pearl")
                || id.equals("minecraft:player") || id.equals("minecraft:marker");
    }

    private void addLabeledBox(int x, int y, int colW, int fieldW, String labelKey,
            String initialValue, java.util.function.Consumer<String> setter) {
        int boxX = x + colW - fieldW;
        EditBox box = new EditBox(font, boxX, y, fieldW, 18, Component.empty());
        box.setMaxLength(16);
        box.setFilter("gui.settledlands.trial.cfg.ominous_mult".equals(labelKey)
                ? TrialModuleClient447.floatRangeFilter()
                : TrialModuleClient447.intRangeFilter());
        if ("gui.settledlands.trial.cfg.delay".equals(labelKey)) {
            box.setHint(Component.literal("20-40"));
        }
        box.setValue(initialValue == null ? "" : initialValue);
        box.setResponder(val -> setter.accept(val == null ? "" : val));
        addDyn(box);
        addTip("cfg:fld_" + labelKey, box, x, y, colW, 18, "field", labelKey);
    }

    private int visibleMobRows() {
        return Math.max(2, (bodyBottom - (bodyTop + 28)) / MOB_ROW_H);
    }

    private int visibleRewardRows() {
        return Math.max(2, (bodyBottom - (bodyTop + 28)) / REWARD_ROW_H);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x990A0D13);
        int accent = 0xFF000000 | TrialGraph446.ACCENT_COLOR;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF080A0F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE141821);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, accent);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF2D3546);

        Component title = viewMode == VIEW_MOB_PICKER
                ? Component.translatable("gui.settledlands.trial.cfg.pick_mob_title")
                : viewMode == VIEW_ITEM_PICKER
                ? Component.translatable("gui.settledlands.trial.cfg.pick_item_title")
                : Component.translatable("gui.settledlands.trial.cfg.title");
        graphics.drawString(font, title, panelX + 12, panelY + 9, 0xFFF2F5FA, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (viewMode == VIEW_MAIN) {
            renderMainLabels(graphics);
            if (!statusText.isEmpty()) {
                graphics.drawString(font, clip(font, statusText, panelW - 220),
                        panelX + 112, panelY + panelH - 22, statusColor, false);
            }
        } else if (viewMode == VIEW_MOB_PICKER) {
            renderMobPicker(graphics, mouseX, mouseY);
        } else if (viewMode == VIEW_ITEM_PICKER) {
            renderItemPicker(graphics, mouseX, mouseY);
        }

        GraphTooltips448.renderConfigTooltip(this, graphics, this.font, this.width, this.height,
                mouseX, mouseY, this.tipAreas448, this.cfg, "");
    }

    private void renderMainLabels(GuiGraphics graphics) {
        int leftX = panelX + 12;
        int colW = (panelW - 36) / 2;
        int rightX = leftX + colW + 12;
        if (activeTab == TAB_GENERAL) {
            int y = bodyTop + 4 + 24;
            int lblMax = colW - 68;
            drawLbl(graphics, "gui.settledlands.trial.cfg.total_mobs", leftX, y + 5, lblMax);
            drawLbl(graphics, "gui.settledlands.trial.cfg.sim_mobs", rightX, y + 5, lblMax);
            y += 22;
            drawLbl(graphics, "gui.settledlands.trial.cfg.batch", leftX, y + 5, lblMax);
            drawLbl(graphics, "gui.settledlands.trial.cfg.act_range", rightX, y + 5, lblMax);
            y += 22;
            drawLbl(graphics, "gui.settledlands.trial.cfg.spawn_range", leftX, y + 5, lblMax);
            drawLbl(graphics, "gui.settledlands.trial.cfg.cooldown", rightX, y + 5, lblMax);
            y += 22;
            drawLbl(graphics, "gui.settledlands.trial.cfg.delay", leftX, y + 5, lblMax);
            y += 24 + 22;
            drawLbl(graphics, "gui.settledlands.trial.cfg.per_player_total", leftX, y + 5, lblMax);
            drawLbl(graphics, "gui.settledlands.trial.cfg.destruct_waves", rightX, y + 5, lblMax);
            y += 22;
            drawLbl(graphics, "gui.settledlands.trial.cfg.per_player_sim", leftX, y + 5, lblMax);
            drawLbl(graphics, "gui.settledlands.trial.cfg.destruct_chance", rightX, y + 5, lblMax);
        } else if (activeTab == TAB_OMINOUS) {
            int oColW = Math.min(340, panelW - 24);
            int lblMax = oColW - 76;
            drawLbl(graphics, "gui.settledlands.trial.cfg.ominous_mult", leftX, bodyTop + 15, lblMax);
            drawLbl(graphics, "gui.settledlands.trial.cfg.ominous_interval",
                    leftX, bodyTop + 10 + 26 + 26 + 26 + 5, lblMax);
        } else if (activeTab == TAB_MOBS) {
            graphics.drawString(font, Component.translatable("gui.settledlands.trial.cfg.mobs_hdr"),
                    leftX, bodyTop + 16, 0xFF9AA5B8, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.settledlands.trial.cfg.rewards_hdr"),
                    leftX, bodyTop + 16, 0xFF9AA5B8, false);
        }
    }

    private void renderMobPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int colW = (pickerGridW - 6) / 2;
        int start = pickerScroll * 2;
        for (int r = 0; r < pickerVisibleRows; r++) {
            for (int c = 0; c < 2; c++) {
                int idx = start + r * 2 + c;
                if (idx >= filteredMobs.size()) break;
                MobOption opt = filteredMobs.get(idx);
                int cx = pickerGridX + c * (colW + 6);
                int cy = pickerGridY + r * PICK_CELL_H;
                boolean over = mouseX >= cx && mouseX < cx + colW
                        && mouseY >= cy && mouseY < cy + PICK_CELL_H - 2;
                graphics.fill(cx, cy, cx + colW, cy + PICK_CELL_H - 2,
                        over ? 0xFF283A4E : 0xFF1A202B);
                graphics.renderOutline(cx, cy, colW, PICK_CELL_H - 2,
                        over ? (0xFF000000 | TrialGraph446.ACCENT_COLOR) : 0xFF2D3546);
                String text = opt.displayName.isEmpty() ? opt.id : opt.displayName + " (" + opt.id + ")";
                graphics.drawString(font, clip(font, text, colW - 8), cx + 4, cy + 6, 0xFFEAEFF8, false);
            }
        }
    }

    private void renderItemPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int cellW = 24;
        int start = pickerScroll * pickerCols;
        ItemStack hovered = ItemStack.EMPTY;
        for (int r = 0; r < pickerVisibleRows; r++) {
            for (int c = 0; c < pickerCols; c++) {
                int idx = start + r * pickerCols + c;
                if (idx >= filteredItems.size()) break;
                ItemStack stack = filteredItems.get(idx);
                int cx = pickerGridX + c * cellW;
                int cy = pickerGridY + r * PICK_CELL_H;
                boolean over = mouseX >= cx && mouseX < cx + 22 && mouseY >= cy && mouseY < cy + 20;
                graphics.fill(cx, cy, cx + 22, cy + 20, over ? 0xFF2B3E52 : 0xFF1A202B);
                graphics.renderOutline(cx, cy, 22, 20,
                        over ? (0xFF000000 | TrialGraph446.ACCENT_COLOR) : 0xFF2D3546);
                graphics.renderItem(stack, cx + 3, cy + 2);
                if (over) hovered = stack;
            }
        }
        if (!hovered.isEmpty()) {
            setTooltipForNextRenderPass(hovered.getHoverName());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GraphTooltips448.onMouseClick();
        if (button == 0 && viewMode == VIEW_MOB_PICKER) {
            int colW = (pickerGridW - 6) / 2;
            int start = pickerScroll * 2;
            for (int r = 0; r < pickerVisibleRows; r++) {
                for (int c = 0; c < 2; c++) {
                    int idx = start + r * 2 + c;
                    if (idx >= filteredMobs.size()) break;
                    int cx = pickerGridX + c * (colW + 6);
                    int cy = pickerGridY + r * PICK_CELL_H;
                    if (mouseX >= cx && mouseX < cx + colW
                            && mouseY >= cy && mouseY < cy + PICK_CELL_H - 2) {
                        if (pickerRow >= 0 && pickerRow < cfg.mobs.size()) {
                            cfg.mobs.get(pickerRow).entityId = filteredMobs.get(idx).id;
                        }
                        viewMode = VIEW_MAIN;
                        rebuildView();
                        return true;
                    }
                }
            }
        } else if (button == 0 && viewMode == VIEW_ITEM_PICKER) {
            int cellW = 24;
            int start = pickerScroll * pickerCols;
            for (int r = 0; r < pickerVisibleRows; r++) {
                for (int c = 0; c < pickerCols; c++) {
                    int idx = start + r * pickerCols + c;
                    if (idx >= filteredItems.size()) break;
                    int cx = pickerGridX + c * cellW;
                    int cy = pickerGridY + r * PICK_CELL_H;
                    if (mouseX >= cx && mouseX < cx + 22 && mouseY >= cy && mouseY < cy + 20) {
                        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(
                                filteredItems.get(idx).getItem());
                        if (rl != null && pickerRow >= 0 && pickerRow < cfg.rewards.size()) {
                            cfg.rewards.get(pickerRow).itemId = rl.toString();
                        }
                        viewMode = VIEW_MAIN;
                        rebuildView();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
        if (step != 0) {
            if (viewMode == VIEW_MOB_PICKER) {
                int totalRows = (filteredMobs.size() + 1) / 2;
                int maxScroll = Math.max(0, totalRows - pickerVisibleRows);
                pickerScroll = Math.max(0, Math.min(maxScroll, pickerScroll + step));
                return true;
            }
            if (viewMode == VIEW_ITEM_PICKER) {
                int totalRows = (filteredItems.size() + pickerCols - 1) / Math.max(1, pickerCols);
                int maxScroll = Math.max(0, totalRows - pickerVisibleRows);
                pickerScroll = Math.max(0, Math.min(maxScroll, pickerScroll + step));
                return true;
            }
            if (activeTab == TAB_MOBS) {
                int maxScroll = Math.max(0, cfg.mobs.size() - visibleMobRows());
                int next = Math.max(0, Math.min(maxScroll, listScroll + step));
                if (next != listScroll) {
                    listScroll = next;
                    rebuildView();
                }
                return true;
            }
            if (activeTab == TAB_REWARDS) {
                int maxScroll = Math.max(0, cfg.rewards.size() - visibleRewardRows());
                int next = Math.max(0, Math.min(maxScroll, listScroll + step));
                if (next != listScroll) {
                    listScroll = next;
                    rebuildView();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (viewMode != VIEW_MAIN) {
            viewMode = VIEW_MAIN;
            rebuildView();
            return;
        }
        returnToParent();
    }

    private void applyAndClose() {
        commitBuffersToConfig();
        TrialConfig446 sanitized = TrialConfig446.sanitize(cfg);
        if (targetNode != null) {
            if (parentScreen != null) parentScreen.settledlands$remember();
            if (controlNodeMode) {
                TrialControlNode446.setConfig(targetNode, sanitized);
            } else {
                TrialGraph446.setConfig(targetNode, sanitized);
            }
            if (parentScreen != null) parentScreen.settledlands$changed();
        }
        returnToParent();
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private Component modeLabel() {
        String key;
        if (TrialConfig446.MODE_OMINOUS.equals(cfg.mode)) key = "gui.settledlands.trial.mode.ominous";
        else if (TrialConfig446.MODE_SPECIAL.equals(cfg.mode)) key = "gui.settledlands.trial.mode.special";
        else key = "gui.settledlands.trial.mode.normal";
        return Component.translatable("gui.settledlands.trial.cfg.mode_btn",
                Component.translatable(key));
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.translatable(key,
                Component.translatable(on ? "gui.settledlands.trial.cfg.yes"
                        : "gui.settledlands.trial.cfg.no"));
    }

    private void drawLbl(GuiGraphics graphics, String key, int x, int y, int maxW) {
        graphics.drawString(font, clip(font, Component.translatable(key).getString(), maxW),
                x, y, 0xFFB8C4D8, false);
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback, float min, float max) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            float v = Float.parseFloat(raw.trim().replace(',', '.'));
            if (!Float.isFinite(v)) return fallback;
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String clip(Font font, String text, int maxW) {
        if (font == null || text == null || maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "\u2026") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "" : s + "\u2026";
    }
}
