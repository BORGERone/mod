package dev.settledlands.npc.client;

import dev.settledlands.chest.ChestLootNet436;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialMobPool449;
import dev.settledlands.trial.TrialMobPoolNet449;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Build 447: zero-lambda callback & formatting helpers for {@link TrialModule447} cards and
 * inspectors. All lambdas live here (not in mixin classes) so {@code mixin_clash_check.py}
 * stays completely clean.
 */
public final class TrialModuleClient447 {

    public static final int FIELD_RADIUS_ACT = 1;
    public static final int FIELD_RADIUS_SPAWN = 2;

    public static final int FIELD_WAVE_TOTAL = 10;
    public static final int FIELD_WAVE_PER_PLAYER = 11;
    public static final int FIELD_WAVE_SIM = 12;
    public static final int FIELD_WAVE_SIM_PER_PLAYER = 13;
    public static final int FIELD_WAVE_BATCH = 14;

    public static final int FIELD_TIMING_MIN = 20;
    public static final int FIELD_TIMING_MAX = 21;
    public static final int FIELD_TIMING_CD = 22;

    public static final int FIELD_OMINOUS_MULT = 30;
    public static final int FIELD_OMINOUS_INTERVAL = 31;

    public static final int FIELD_PROTECT_RADIUS = 40;
    public static final int FIELD_PROTECT_SECS = 41;
    public static final int FIELD_PROTECT_AMP = 42;
    public static final int FIELD_PROTECT_WAVES = 43;
    public static final int FIELD_PROTECT_CHANCE = 44;

    private static boolean pendingInspectorRebuild;
    private static NpcDialogueGraphScreen pendingPoolSyncScreen;
    private static int pendingPoolSyncNodeId = -1;
    private static int pendingPoolSyncSlot = -1;
    private static String pendingPoolSyncFile = "";

    private TrialModuleClient447() {}

    public static void requestInspectorRebuild() {
        pendingInspectorRebuild = true;
    }

    public static boolean consumeInspectorRebuild() {
        if (!pendingInspectorRebuild) return false;
        pendingInspectorRebuild = false;
        return true;
    }

    public static Predicate<String> intFilter() {
        return raw -> {
            if (raw == null || raw.isEmpty()) return true;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c < '0' || c > '9') return false;
            }
            return true;
        };
    }

    public static Predicate<String> floatFilter() {
        return raw -> {
            if (raw == null || raw.isEmpty()) return true;
            boolean dot = false;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c >= '0' && c <= '9') continue;
                if ((c == '.' || c == ',') && !dot) {
                    dot = true;
                    continue;
                }
                return false;
            }
            return true;
        };
    }

    // ---- Hub summary & restore ----------------------------------------------

    public static String[] hubModuleStatusLines(NpcDialogueGraph graph, Node hub) {
        if (graph == null || hub == null) return new String[0];
        int connected = TrialModule447.connectedCount(graph, hub);
        List<String> out = new ArrayList<>();
        out.add(TrialGraphClient446.safe("gui.settledlands.trial.hub_modules",
                connected + "/" + TrialModule447.MODULE_COUNT));
        for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
            Node mod = TrialModule447.connectedModule(graph, hub, s);
            String name = Component.translatable(TrialModule447.pinKey(s)).getString();
            String state = mod != null
                    ? "#" + mod.id
                    : Component.translatable("gui.settledlands.trial.mod.slot_default").getString();
            out.add(name + ": " + state);
        }
        return out.toArray(new String[0]);
    }

    public static Runnable restoreModulesAction(NpcDialogueGraphScreen screen, Node hub) {
        return () -> {
            if (screen == null) return;
            NpcDialogueGraph graph = screen.settledlands$graph();
            if (graph == null) return;
            Node targetHub = TrialGraph446.trialNode(graph);
            if (targetHub == null) targetHub = hub;
            if (targetHub == null) return;

            screen.settledlands$remember();
            // First re-attach any existing unlinked modules of missing slots.
            if (graph.nodes != null) {
                for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
                    if (TrialModule447.connectedModule(graph, targetHub, s) != null) continue;
                    for (Node candidate : graph.nodes.values()) {
                        if (TrialModule447.slotOf(candidate) == s && candidate.next < 0) {
                            candidate.next = targetHub.id;
                            break;
                        }
                    }
                }
            }
            // Then create any still-missing slots with current effective config.
            TrialConfig446 cfg = TrialModule447.compileConfig(graph);
            TrialModule447.createMissingModules(graph, targetHub, cfg);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Module lifecycle & linking -----------------------------------------

    public static String linkStatusText(NpcDialogueGraph graph, Node node) {
        if (graph != null && node != null && node.next >= 0) {
            Node hub = graph.get(node.next);
            if (TrialGraph446.isTrialNode(hub)) {
                return TrialGraphClient446.safe("gui.settledlands.trial.mod.linked_to",
                        "#" + hub.id);
            }
        }
        return Component.translatable("gui.settledlands.trial.mod.unlinked_short").getString();
    }

    public static Runnable toggleAttachHubAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node) {
        return () -> {
            if (screen == null || graph == null || node == null) return;
            screen.settledlands$remember();
            Node curHub = node.next >= 0 ? graph.get(node.next) : null;
            if (TrialGraph446.isTrialNode(curHub)) {
                TrialModule447.disconnectModule(graph, node.id);
            } else {
                Node hub = TrialGraph446.trialNode(graph);
                if (hub != null) {
                    TrialModule447.connectModule(graph, node.id, hub.id);
                }
            }
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    public static Runnable deleteModuleAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node) {
        return () -> {
            if (screen == null || graph == null || node == null) return;
            screen.settledlands$remember();
            graph.remove(node.id);
            TrialGraph446.onNodeRemoved(graph, node.id);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Slot 0: Mode -------------------------------------------------------

    public static String modeButtonText(Node node) {
        TrialModule447.ModeData d = TrialModule447.readMode(node);
        return Component.translatable("gui.settledlands.trial.cfg.mode_btn",
                Component.translatable(TrialModuleCard447.modeKey(d.mode))).getString();
    }

    public static String scaleButtonText(Node node) {
        TrialModule447.ModeData d = TrialModule447.readMode(node);
        return Component.translatable("gui.settledlands.trial.cfg.scale_players",
                TrialModuleCard447.yesNo(d.scaleWithPlayers)).getString();
    }

    public static Runnable cycleModeAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node) {
        return () -> {
            if (screen == null || node == null) return;
            screen.settledlands$remember();
            TrialModule447.ModeData d = TrialModule447.readMode(node);
            d.mode = TrialConfig446.nextMode(d.mode);
            TrialModule447.writeMode(node, d);
            TrialModule447.syncCardConfig(graph);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    public static Runnable toggleScaleAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node) {
        return () -> {
            if (screen == null || node == null) return;
            screen.settledlands$remember();
            TrialModule447.ModeData d = TrialModule447.readMode(node);
            d.scaleWithPlayers = !d.scaleWithPlayers;
            TrialModule447.writeMode(node, d);
            TrialModule447.syncCardConfig(graph);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Scalar integer & float fields (radius, wave, timing, ominous, protect) --

    public static Consumer<String> scalarSetter(NpcDialogueGraph graph, Node node, int fieldId) {
        return raw -> {
            if (node == null || raw == null || raw.isBlank()) return;
            switch (fieldId) {
                case FIELD_RADIUS_ACT -> {
                    TrialModule447.RadiusData d = TrialModule447.readRadius(node);
                    d.activationRange = parseInt(raw, d.activationRange, 1, 64);
                    TrialModule447.writeRadius(node, d);
                }
                case FIELD_RADIUS_SPAWN -> {
                    TrialModule447.RadiusData d = TrialModule447.readRadius(node);
                    d.spawnRange = parseInt(raw, d.spawnRange, 1, 16);
                    TrialModule447.writeRadius(node, d);
                }
                case FIELD_WAVE_TOTAL -> {
                    TrialModule447.WaveData d = TrialModule447.readWave(node);
                    d.totalMobs = parseInt(raw, d.totalMobs, 1, 256);
                    TrialModule447.writeWave(node, d);
                }
                case FIELD_WAVE_PER_PLAYER -> {
                    TrialModule447.WaveData d = TrialModule447.readWave(node);
                    d.totalMobsAddedPerPlayer = parseInt(raw, d.totalMobsAddedPerPlayer, 0, 64);
                    TrialModule447.writeWave(node, d);
                }
                case FIELD_WAVE_SIM -> {
                    TrialModule447.WaveData d = TrialModule447.readWave(node);
                    d.simultaneousMobs = parseInt(raw, d.simultaneousMobs, 1, 64);
                    TrialModule447.writeWave(node, d);
                }
                case FIELD_WAVE_SIM_PER_PLAYER -> {
                    TrialModule447.WaveData d = TrialModule447.readWave(node);
                    d.simultaneousMobsAddedPerPlayer = parseInt(raw, d.simultaneousMobsAddedPerPlayer, 0, 32);
                    TrialModule447.writeWave(node, d);
                }
                case FIELD_WAVE_BATCH -> {
                    TrialModule447.WaveData d = TrialModule447.readWave(node);
                    d.spawnBatchSize = parseInt(raw, d.spawnBatchSize, 1, 8);
                    TrialModule447.writeWave(node, d);
                }
                case FIELD_TIMING_MIN -> {
                    TrialModule447.TimingData d = TrialModule447.readTiming(node);
                    d.minSpawnDelayTicks = parseInt(raw, d.minSpawnDelayTicks, 1, 12000);
                    if (d.maxSpawnDelayTicks < d.minSpawnDelayTicks) {
                        d.maxSpawnDelayTicks = d.minSpawnDelayTicks;
                    }
                    TrialModule447.writeTiming(node, d);
                }
                case FIELD_TIMING_MAX -> {
                    TrialModule447.TimingData d = TrialModule447.readTiming(node);
                    d.maxSpawnDelayTicks = parseInt(raw, d.maxSpawnDelayTicks, d.minSpawnDelayTicks, 12000);
                    TrialModule447.writeTiming(node, d);
                }
                case FIELD_TIMING_CD -> {
                    TrialModule447.TimingData d = TrialModule447.readTiming(node);
                    d.cooldownTicks = parseInt(raw, d.cooldownTicks, 0, 720000);
                    TrialModule447.writeTiming(node, d);
                }
                case FIELD_OMINOUS_MULT -> {
                    TrialModule447.OminousData d = TrialModule447.readOminous(node);
                    d.mobMultiplier = parseFloat(raw, d.mobMultiplier, 1.0f, 4.0f);
                    TrialModule447.writeOminous(node, d);
                }
                case FIELD_OMINOUS_INTERVAL -> {
                    TrialModule447.OminousData d = TrialModule447.readOminous(node);
                    d.hazardIntervalTicks = parseInt(raw, d.hazardIntervalTicks, 20, 12000);
                    TrialModule447.writeOminous(node, d);
                }
                case FIELD_PROTECT_RADIUS -> {
                    TrialModule447.ProtectData d = TrialModule447.readProtect(node);
                    d.noBreakRadius = parseInt(raw, d.noBreakRadius, 1, 64);
                    TrialModule447.writeProtect(node, d);
                }
                case FIELD_PROTECT_SECS -> {
                    TrialModule447.ProtectData d = TrialModule447.readProtect(node);
                    d.noBreakSeconds = parseInt(raw, d.noBreakSeconds, 1, 600);
                    TrialModule447.writeProtect(node, d);
                }
                case FIELD_PROTECT_AMP -> {
                    TrialModule447.ProtectData d = TrialModule447.readProtect(node);
                    d.noBreakAmplifier = parseInt(raw, d.noBreakAmplifier, 0, 3);
                    TrialModule447.writeProtect(node, d);
                }
                case FIELD_PROTECT_WAVES -> {
                    TrialModule447.ProtectData d = TrialModule447.readProtect(node);
                    d.selfDestructAfterWaves = parseInt(raw, d.selfDestructAfterWaves, 1, 100);
                    TrialModule447.writeProtect(node, d);
                }
                case FIELD_PROTECT_CHANCE -> {
                    TrialModule447.ProtectData d = TrialModule447.readProtect(node);
                    d.selfDestructChancePercent = parseInt(raw, d.selfDestructChancePercent, 0, 100);
                    TrialModule447.writeProtect(node, d);
                }
                default -> {
                    return;
                }
            }
            if (graph != null) TrialModule447.syncCardConfig(graph);
        };
    }

    // ---- Slot 4 & 5: Named Mob & Reward Pool Selectors (Build 449) --------

    public static Consumer<Runnable> commitAction(NpcDialogueGraphScreen screen) {
        return action -> {
            if (screen == null || action == null) return;
            screen.settledlands$remember();
            action.run();
            if (screen.settledlands$graph() != null) {
                TrialModule447.syncCardConfig(screen.settledlands$graph());
            }
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    public static List<String> mobPoolOptions(String currentFile) {
        Set<String> unique = new LinkedHashSet<>(TrialMobPool449.getServerPools());
        String cleanCurrent = TrialMobPool449.sanitizeFileName(currentFile);
        if (!cleanCurrent.isEmpty()) unique.add(cleanCurrent);
        List<String> sorted = new ArrayList<>(unique);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        List<String> out = new ArrayList<>(sorted.size() + 1);
        out.add("");
        out.addAll(sorted);
        return out;
    }

    public static List<Component> mobPoolDropdownEntries(List<String> options) {
        List<Component> entries = new ArrayList<>(options.size());
        for (String opt : options) {
            if (opt == null || opt.isEmpty()) {
                entries.add(Component.translatable("gui.settledlands.trial.mob_pool.create_new"));
            } else {
                entries.add(Component.literal(opt));
            }
        }
        return entries;
    }

    public static int mobPoolDropdownIndex(List<String> options, String currentFile) {
        String clean = TrialMobPool449.sanitizeFileName(currentFile);
        if (clean.isEmpty()) return 0;
        for (int i = 0; i < options.size(); i++) {
            if (clean.equals(options.get(i))) return i;
        }
        return 0;
    }

    public static IntConsumer mobPoolPicker(Consumer<Runnable> commit,
            NpcDialogueGraphScreen screen, Node node, List<String> options) {
        return idx -> {
            if (node == null || options == null || idx < 0 || idx >= options.size()) return;
            String chosen = options.get(idx);
            if (chosen == null || chosen.isEmpty()) {
                LootPoolCard436.openMobPoolEditor(screen, node, "");
                return;
            }
            if (commit != null) {
                commit.accept(() -> TrialModule447.writeMobPool(
                        node, chosen, TrialModule447.readMobs(node)));
            }
            pendingPoolSyncScreen = screen;
            pendingPoolSyncNodeId = node.id;
            pendingPoolSyncSlot = TrialModule447.SLOT_MOBS;
            pendingPoolSyncFile = chosen;
            TrialMobPoolNet449.sendPoolQuery(chosen);
        };
    }

    public static AbstractWidget mobPoolGearButton(int x, int y, NpcDialogueGraphScreen screen,
            Node node) {
        String poolFile = TrialModule447.mobPoolFile(node);
        AbstractWidget btn = new LootPoolGearButton440(
                x, y, 20, 20,
                Component.translatable("gui.settledlands.trial.mob_pool.edit_btn_tip"),
                TrialMobPool449.ACCENT_COLOR,
                () -> LootPoolCard436.openMobPoolEditor(screen, node, poolFile));
        GraphTooltips448.recordGearButton(btn, x, y, 20, 20);
        return btn;
    }

    public static List<String> mobPoolSummaryLines(Node node) {
        List<String> lines = new ArrayList<>();
        if (node == null) return lines;
        String file = TrialModule447.mobPoolFile(node);
        List<TrialConfig446.SpawnEntry> mobs = TrialModule447.readMobs(node);
        String source = file.isEmpty()
                ? Component.translatable("gui.settledlands.trial.mod.pool_inline").getString()
                : file;
        lines.add(Component.translatable("gui.settledlands.trial.mob_pool.inspector_status",
                source, Integer.valueOf(mobs.size())).getString());
        int shown = Math.min(4, mobs.size());
        for (int i = 0; i < shown; i++) {
            TrialConfig446.SpawnEntry e = mobs.get(i);
            lines.add("• " + TrialMobPreview449.displayName(e.entityId) + " (w=" + e.weight + ")");
        }
        if (mobs.size() > shown) {
            lines.add(Component.translatable("gui.settledlands.trial.mod.more_entries",
                    Integer.valueOf(mobs.size() - shown)).getString());
        }
        return lines;
    }

    public static List<String> rewardPoolOptions(String currentFile) {
        return LootPoolCard436.poolOptions(currentFile);
    }

    public static List<Component> rewardPoolDropdownEntries(List<String> options) {
        return LootPoolCard436.poolDropdownEntries(options);
    }

    public static int rewardPoolDropdownIndex(List<String> options, String currentFile) {
        return LootPoolCard436.poolDropdownIndex(options, currentFile);
    }

    public static IntConsumer rewardPoolPicker(Consumer<Runnable> commit,
            NpcDialogueGraphScreen screen, Node node, List<String> options) {
        return idx -> {
            if (node == null || options == null || idx < 0 || idx >= options.size()) return;
            String chosen = options.get(idx);
            if (chosen == null || chosen.isEmpty()) {
                LootPoolCard436.openPoolEditor(screen, node, "");
                return;
            }
            if (commit != null) {
                commit.accept(() -> TrialModule447.writeRewardPool(
                        node, chosen, TrialModule447.readRewards(node)));
            }
            pendingPoolSyncScreen = screen;
            pendingPoolSyncNodeId = node.id;
            pendingPoolSyncSlot = TrialModule447.SLOT_REWARDS;
            pendingPoolSyncFile = chosen;
            ChestLootNet436.sendPoolQuery(chosen);
        };
    }

    public static AbstractWidget rewardPoolGearButton(int x, int y, NpcDialogueGraphScreen screen,
            Node node) {
        String poolFile = TrialModule447.rewardPoolFile(node);
        AbstractWidget btn = new LootPoolGearButton440(
                x, y, 20, 20,
                Component.translatable("gui.settledlands.trial.reward_pool.edit_btn_tip"),
                TrialModule447.color(TrialModule447.SLOT_REWARDS),
                () -> LootPoolCard436.openPoolEditor(screen, node, poolFile));
        GraphTooltips448.recordGearButton(btn, x, y, 20, 20);
        return btn;
    }

    public static List<String> rewardPoolSummaryLines(Node node) {
        List<String> lines = new ArrayList<>();
        if (node == null) return lines;
        String file = TrialModule447.rewardPoolFile(node);
        List<TrialConfig446.RewardEntry> rewards = TrialModule447.readRewards(node);
        String source = file.isEmpty()
                ? Component.translatable("gui.settledlands.trial.mod.pool_inline").getString()
                : file;
        lines.add(Component.translatable("gui.settledlands.trial.reward_pool.inspector_status",
                source, Integer.valueOf(rewards.size())).getString());
        int shown = Math.min(4, rewards.size());
        for (int i = 0; i < shown; i++) {
            TrialConfig446.RewardEntry r = rewards.get(i);
            lines.add("• " + TrialGraphClient446.shortId(r.itemId)
                    + " x" + r.minCount + "-" + r.maxCount + " (" + r.chancePercent + "%)");
        }
        if (rewards.size() > shown) {
            lines.add(Component.translatable("gui.settledlands.trial.mod.more_entries",
                    Integer.valueOf(rewards.size() - shown)).getString());
        }
        return lines;
    }

    public static void onMobPoolsUpdated() {
        requestInspectorRebuild();
    }

    public static void onLootPoolsUpdated() {
        requestInspectorRebuild();
    }

    public static void onMobPoolData(String rawPayload) {
        if (rawPayload == null || pendingPoolSyncSlot != TrialModule447.SLOT_MOBS) return;
        String[] lines = rawPayload.split("\n", -1);
        String status = lines.length >= 1 ? lines[0].trim() : "";
        String file = lines.length >= 2 ? lines[1].trim() : "";
        if (!"OK".equals(status) || !file.equals(pendingPoolSyncFile)) return;
        NpcDialogueGraphScreen screen = pendingPoolSyncScreen;
        int nodeId = pendingPoolSyncNodeId;
        pendingPoolSyncSlot = -1;
        pendingPoolSyncScreen = null;
        pendingPoolSyncFile = "";
        if (screen == null || screen.settledlands$graph() == null) return;
        Node node = screen.settledlands$graph().get(nodeId);
        if (TrialModule447.slotOf(node) != TrialModule447.SLOT_MOBS) return;
        List<TrialConfig446.SpawnEntry> entries = TrialMobPool449.decodeEntriesWire(lines, 2);
        if (!entries.isEmpty()) {
            TrialModule447.writeMobPool(node, file, entries);
            TrialModule447.syncCardConfig(screen.settledlands$graph());
            requestInspectorRebuild();
            screen.settledlands$changed();
        }
    }

    public static void onLootPoolData(String rawPayload) {
        if (rawPayload == null || pendingPoolSyncSlot != TrialModule447.SLOT_REWARDS) return;
        String[] lines = rawPayload.split("\n", -1);
        String status = lines.length >= 1 ? lines[0].trim() : "";
        String file = lines.length >= 2 ? lines[1].trim() : "";
        if (!"OK".equals(status) || !file.equals(pendingPoolSyncFile)) return;
        NpcDialogueGraphScreen screen = pendingPoolSyncScreen;
        int nodeId = pendingPoolSyncNodeId;
        pendingPoolSyncSlot = -1;
        pendingPoolSyncScreen = null;
        pendingPoolSyncFile = "";
        if (screen == null || screen.settledlands$graph() == null) return;
        Node node = screen.settledlands$graph().get(nodeId);
        if (TrialModule447.slotOf(node) != TrialModule447.SLOT_REWARDS) return;
        List<TrialConfig446.RewardEntry> rewards =
                TrialModule447.fromLootEntries(LootPool436.decodeEntriesWire(lines, 2));
        TrialModule447.writeRewardPool(node, file, rewards);
        TrialModule447.syncCardConfig(screen.settledlands$graph());
        requestInspectorRebuild();
        screen.settledlands$changed();
    }

    // ---- Slot 4: Mobs -------------------------------------------------------

    public static Runnable openAddMobPicker(NpcDialogueGraphScreen screen, Node node) {
        return () -> TrialModulePickerScreen447.openAddMob(screen, node);
    }

    public static Runnable openMobPoolAction(NpcDialogueGraphScreen screen, Node node) {
        return () -> TrialModulePickerScreen447.openMobPoolEditor(screen, node);
    }

    public static Runnable openPickMobRowAction(NpcDialogueGraphScreen screen, Node node, int row) {
        return () -> TrialModulePickerScreen447.openPickMobRow(screen, node, row);
    }

    public static Consumer<String> mobWeightSetter(NpcDialogueGraph graph, Node node, int row) {
        return raw -> {
            if (node == null || raw == null || raw.isBlank()) return;
            List<TrialConfig446.SpawnEntry> mobs = TrialModule447.readMobs(node);
            if (row < 0 || row >= mobs.size()) return;
            mobs.get(row).weight = parseInt(raw, mobs.get(row).weight, 1, 10000);
            TrialModule447.writeMobs(node, mobs);
            if (graph != null) TrialModule447.syncCardConfig(graph);
        };
    }

    public static Runnable removeMobRowAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node, int row) {
        return () -> {
            if (screen == null || node == null) return;
            List<TrialConfig446.SpawnEntry> mobs = TrialModule447.readMobs(node);
            if (mobs.size() <= 1 || row < 0 || row >= mobs.size()) return;
            screen.settledlands$remember();
            mobs.remove(row);
            TrialModule447.writeMobs(node, mobs);
            if (graph != null) TrialModule447.syncCardConfig(graph);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Slot 5: Rewards ----------------------------------------------------

    public static Runnable openAddRewardPicker(NpcDialogueGraphScreen screen, Node node) {
        return () -> TrialModulePickerScreen447.openAddReward(screen, node);
    }

    public static Runnable openRewardPoolAction(NpcDialogueGraphScreen screen, Node node) {
        return () -> TrialModulePickerScreen447.openRewardPoolEditor(screen, node);
    }

    public static Runnable openPickRewardRowAction(NpcDialogueGraphScreen screen, Node node, int row) {
        return () -> TrialModulePickerScreen447.openPickRewardRow(screen, node, row);
    }

    public static Consumer<String> rewardFieldSetter(NpcDialogueGraph graph, Node node, int row,
            int which) {
        // which: 0=weight, 1=min, 2=max, 3=chance%
        return raw -> {
            if (node == null || raw == null || raw.isBlank()) return;
            List<TrialConfig446.RewardEntry> rewards = TrialModule447.readRewards(node);
            if (row < 0 || row >= rewards.size()) return;
            TrialConfig446.RewardEntry r = rewards.get(row);
            switch (which) {
                case 0 -> r.weight = parseInt(raw, r.weight, 1, 10000);
                case 1 -> {
                    r.minCount = parseInt(raw, r.minCount, 1, 64);
                    if (r.maxCount < r.minCount) r.maxCount = r.minCount;
                }
                case 2 -> r.maxCount = parseInt(raw, r.maxCount, r.minCount, 64);
                case 3 -> r.chancePercent = parseInt(raw, r.chancePercent, 0, 100);
                default -> {
                }
            }
            TrialModule447.writeRewards(node, rewards);
            if (graph != null) TrialModule447.syncCardConfig(graph);
        };
    }

    public static Runnable removeRewardRowAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node, int row) {
        return () -> {
            if (screen == null || node == null) return;
            List<TrialConfig446.RewardEntry> rewards = TrialModule447.readRewards(node);
            if (row < 0 || row >= rewards.size()) return;
            screen.settledlands$remember();
            rewards.remove(row);
            TrialModule447.writeRewards(node, rewards);
            if (graph != null) TrialModule447.syncCardConfig(graph);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Slot 6: Ominous toggles --------------------------------------------

    public static String ominousToggleLabel(Node node, int which) {
        TrialModule447.OminousData d = TrialModule447.readOminous(node);
        return switch (which) {
            case 0 -> Component.translatable("gui.settledlands.trial.cfg.ominous_equip",
                    TrialModuleCard447.yesNo(d.equipment)).getString();
            case 1 -> Component.translatable("gui.settledlands.trial.cfg.ominous_hazards",
                    TrialModuleCard447.yesNo(d.hazards)).getString();
            default -> Component.translatable("gui.settledlands.trial.cfg.ominous_keys",
                    TrialModuleCard447.yesNo(d.keyConversion)).getString();
        };
    }

    public static Runnable toggleOminousAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node, int which) {
        return () -> {
            if (screen == null || node == null) return;
            screen.settledlands$remember();
            TrialModule447.OminousData d = TrialModule447.readOminous(node);
            if (which == 0) d.equipment = !d.equipment;
            else if (which == 1) d.hazards = !d.hazards;
            else d.keyConversion = !d.keyConversion;
            TrialModule447.writeOminous(node, d);
            if (graph != null) TrialModule447.syncCardConfig(graph);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Slot 7: Protect & self-destruct toggles ----------------------------

    public static String protectToggleLabel(Node node, int which) {
        TrialModule447.ProtectData d = TrialModule447.readProtect(node);
        return which == 0
                ? Component.translatable("gui.settledlands.trial.cfg.nobreak",
                        TrialModuleCard447.yesNo(d.noBreakEnabled)).getString()
                : Component.translatable("gui.settledlands.trial.cfg.destruct",
                        TrialModuleCard447.yesNo(d.selfDestruct)).getString();
    }

    public static Runnable toggleProtectAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            Node node, int which) {
        return () -> {
            if (screen == null || node == null) return;
            screen.settledlands$remember();
            TrialModule447.ProtectData d = TrialModule447.readProtect(node);
            if (which == 0) d.noBreakEnabled = !d.noBreakEnabled;
            else d.selfDestruct = !d.selfDestruct;
            TrialModule447.writeProtect(node, d);
            if (graph != null) TrialModule447.syncCardConfig(graph);
            requestInspectorRebuild();
            screen.settledlands$changed();
        };
    }

    // ---- Formatting & parsing -----------------------------------------------

    public static String formatFloat(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback, float min, float max) {
        try {
            float v = Float.parseFloat(raw.trim().replace(',', '.'));
            if (!Float.isFinite(v)) return fallback;
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
