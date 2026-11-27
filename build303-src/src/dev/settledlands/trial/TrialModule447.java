package dev.settledlands.trial;

import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Build 447: modular configuration nodes for the {@code trialtuner:trial_spawner} blueprint graph.
 *
 * <p>Each module node is stored inside stock {@link NpcDialogueGraph#TRIGGER} ({@code type == 2,
 * special == 0}) with {@code action = "trial_mod:<kind>:<payload>"} and connects its right output
 * ({@code node.next = hub.id}) to the matching typed left input slot ({@code 0..7}) on the central
 * {@link TrialGraph446#TRIAL_TYPE} hub card.</p>
 *
 * <p>Keeps zero client/render dependencies so both server logic and headless JVM probes run cleanly.</p>
 */
public final class TrialModule447 {

    public static final String PREFIX = "trial_mod:";

    public static final int SLOT_MODE = 0;
    public static final int SLOT_RADIUS = 1;
    public static final int SLOT_WAVE = 2;
    public static final int SLOT_TIMING = 3;
    public static final int SLOT_MOBS = 4;
    public static final int SLOT_REWARDS = 5;
    public static final int SLOT_OMINOUS = 6;
    public static final int SLOT_PROTECT = 7;
    public static final int MODULE_COUNT = 8;

    private static final String[] KINDS = {
        "mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect"
    };

    private static final String[] TITLE_KEYS = {
        "gui.settledlands.trial.mod.mode",
        "gui.settledlands.trial.mod.radius",
        "gui.settledlands.trial.mod.wave",
        "gui.settledlands.trial.mod.timing",
        "gui.settledlands.trial.mod.mobs",
        "gui.settledlands.trial.mod.rewards",
        "gui.settledlands.trial.mod.ominous",
        "gui.settledlands.trial.mod.protect"
    };

    private static final String[] PIN_KEYS = {
        "gui.settledlands.trial.mod.pin_mode",
        "gui.settledlands.trial.mod.pin_radius",
        "gui.settledlands.trial.mod.pin_wave",
        "gui.settledlands.trial.mod.pin_timing",
        "gui.settledlands.trial.mod.pin_mobs",
        "gui.settledlands.trial.mod.pin_rewards",
        "gui.settledlands.trial.mod.pin_ominous",
        "gui.settledlands.trial.mod.pin_protect"
    };

    private static final String[] MENU_KEYS = {
        "gui.settledlands.trial.mod.menu_mode",
        "gui.settledlands.trial.mod.menu_radius",
        "gui.settledlands.trial.mod.menu_wave",
        "gui.settledlands.trial.mod.menu_timing",
        "gui.settledlands.trial.mod.menu_mobs",
        "gui.settledlands.trial.mod.menu_rewards",
        "gui.settledlands.trial.mod.menu_ominous",
        "gui.settledlands.trial.mod.menu_protect"
    };

    private static final int[] COLORS = {
        0xFF35998B, // mode: copper-teal
        0xFF42A5F5, // radius: sky blue
        0xFFFF8A65, // wave: warm coral
        0xFFAB7DF2, // timing: lavender
        0xFFE56B6F, // mobs: crimson red
        0xFFF5B841, // rewards: loot gold
        0xFFD166A8, // ominous: magenta
        0xFF7FA6C9  // protect: steel blue
    };

    /** Staggered two-column layout left of the hub (660, 247) so all 8 wires flow cleanly without crossing cards. */
    private static final int[] REL_X = { -620, -320, -620, -320, -620, -320, -620, -320 };
    private static final int[] REL_Y = { -207, -137,  -67,    3,   73,  143,  213,  283 };

    private TrialModule447() {}

    // ---- metadata & identity ------------------------------------------------

    public static boolean isModuleAction(String action) {
        return action != null && action.startsWith(PREFIX);
    }

    public static boolean isModuleNode(NpcDialogueGraph.Node node) {
        return node != null
                && node.type == NpcDialogueGraph.TRIGGER
                && node.special == 0
                && slotOfAction(node.action) >= 0;
    }

    public static int slotOf(NpcDialogueGraph.Node node) {
        return isModuleNode(node) ? slotOfAction(node.action) : -1;
    }

    public static int slotOfAction(String action) {
        if (!isModuleAction(action)) return -1;
        String rest = action.substring(PREFIX.length());
        int colon = rest.indexOf(':');
        String kind = (colon >= 0 ? rest.substring(0, colon) : rest).trim();
        if ("mobs_pool".equals(kind)) return SLOT_MOBS;
        if ("rewards_pool".equals(kind)) return SLOT_REWARDS;
        for (int i = 0; i < KINDS.length; i++) {
            if (KINDS[i].equals(kind)) return i;
        }
        return -1;
    }

    public static String kindOf(int slot) {
        return slot >= 0 && slot < KINDS.length ? KINDS[slot] : KINDS[0];
    }

    public static String titleKey(int slot) {
        return slot >= 0 && slot < TITLE_KEYS.length ? TITLE_KEYS[slot] : TITLE_KEYS[0];
    }

    public static String pinKey(int slot) {
        return slot >= 0 && slot < PIN_KEYS.length ? PIN_KEYS[slot] : PIN_KEYS[0];
    }

    public static String menuKey(int slot) {
        return slot >= 0 && slot < MENU_KEYS.length ? MENU_KEYS[slot] : MENU_KEYS[0];
    }

    public static int color(int slot) {
        return slot >= 0 && slot < COLORS.length ? COLORS[slot] : COLORS[0];
    }

    public static int dimColor(int slot) {
        int c = color(slot);
        int r = ((c >> 16) & 0xFF) / 2;
        int g = ((c >> 8) & 0xFF) / 2;
        int b = (c & 0xFF) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int defaultXFor(NpcDialogueGraph.Node hub, int slot) {
        int hx = hub == null ? 660 : hub.x;
        return hx + (slot >= 0 && slot < REL_X.length ? REL_X[slot] : -320);
    }

    public static int defaultYFor(NpcDialogueGraph.Node hub, int slot) {
        int hy = hub == null ? 247 : hub.y;
        return hy + (slot >= 0 && slot < REL_Y.length ? REL_Y[slot] : 0);
    }

    public static void applyDefaults(NpcDialogueGraph.Node node, int slot) {
        if (node == null) return;
        node.type = NpcDialogueGraph.TRIGGER;
        node.special = 0;
        node.next = -1;
        writeFromConfig(node, slot, TrialConfig446.defaults());
    }

    // ---- typed data views ---------------------------------------------------

    public static final class ModeData {
        public String mode = TrialConfig446.MODE_TRIAL;
        public boolean scaleWithPlayers = true;
    }

    public static final class RadiusData {
        public int activationRange = 14;
        public int spawnRange = 4;
    }

    public static final class WaveData {
        public int totalMobs = 6;
        public int totalMobsAddedPerPlayer = 2;
        public int simultaneousMobs = 2;
        public int simultaneousMobsAddedPerPlayer = 1;
        public int spawnBatchSize = 1;
    }

    public static final class TimingData {
        public int minSpawnDelayTicks = 20;
        public int maxSpawnDelayTicks = 40;
        public int cooldownTicks = 36000;
    }

    public static final class OminousData {
        public float mobMultiplier = 1.5f;
        public boolean equipment = true;
        public boolean hazards = true;
        public int hazardIntervalTicks = 160;
        public boolean keyConversion = true;
    }

    public static final class ProtectData {
        public boolean noBreakEnabled = true;
        public int noBreakRadius = 24;
        public int noBreakSeconds = 60;
        public int noBreakAmplifier = 3;
        public boolean selfDestruct = false;
        public int selfDestructAfterWaves = 3;
        public int selfDestructChancePercent = 25;
    }

    private static String payloadOf(NpcDialogueGraph.Node node, int expectedSlot) {
        if (node == null || node.action == null) return "";
        String prefix = PREFIX + kindOf(expectedSlot) + ":";
        if (!node.action.startsWith(prefix)) return "";
        return node.action.substring(prefix.length());
    }

    private static String[] splitPipes(String payload) {
        return payload == null || payload.isEmpty() ? new String[0] : payload.split("\\|", -1);
    }

    public static ModeData readMode(NpcDialogueGraph.Node node) {
        ModeData d = new ModeData();
        String[] p = splitPipes(payloadOf(node, SLOT_MODE));
        if (p.length >= 1 && !p[0].isEmpty()) d.mode = TrialConfig446.normalizeMode(p[0]);
        if (p.length >= 2 && !p[1].isEmpty()) d.scaleWithPlayers = parseBool(p[1], true);
        return d;
    }

    public static void writeMode(NpcDialogueGraph.Node node, ModeData d) {
        if (node == null) return;
        ModeData v = d == null ? new ModeData() : d;
        node.action = PREFIX + "mode:" + TrialConfig446.normalizeMode(v.mode)
                + "|" + (v.scaleWithPlayers ? "1" : "0");
    }

    public static RadiusData readRadius(NpcDialogueGraph.Node node) {
        RadiusData d = new RadiusData();
        String[] p = splitPipes(payloadOf(node, SLOT_RADIUS));
        if (p.length >= 1) d.activationRange = clamp(parseInt(p[0], d.activationRange), 1, 64);
        if (p.length >= 2) d.spawnRange = clamp(parseInt(p[1], d.spawnRange), 1, 16);
        return d;
    }

    public static void writeRadius(NpcDialogueGraph.Node node, RadiusData d) {
        if (node == null) return;
        RadiusData v = d == null ? new RadiusData() : d;
        node.action = PREFIX + "radius:"
                + clamp(v.activationRange, 1, 64) + "|"
                + clamp(v.spawnRange, 1, 16);
    }

    public static WaveData readWave(NpcDialogueGraph.Node node) {
        WaveData d = new WaveData();
        String[] p = splitPipes(payloadOf(node, SLOT_WAVE));
        if (p.length >= 1) d.totalMobs = clamp(parseInt(p[0], d.totalMobs), 1, 256);
        if (p.length >= 2) d.totalMobsAddedPerPlayer = clamp(parseInt(p[1], d.totalMobsAddedPerPlayer), 0, 64);
        if (p.length >= 3) d.simultaneousMobs = clamp(parseInt(p[2], d.simultaneousMobs), 1, 64);
        if (p.length >= 4) d.simultaneousMobsAddedPerPlayer = clamp(parseInt(p[3], d.simultaneousMobsAddedPerPlayer), 0, 32);
        if (p.length >= 5) d.spawnBatchSize = clamp(parseInt(p[4], d.spawnBatchSize), 1, 8);
        return d;
    }

    public static void writeWave(NpcDialogueGraph.Node node, WaveData d) {
        if (node == null) return;
        WaveData v = d == null ? new WaveData() : d;
        node.action = PREFIX + "wave:"
                + clamp(v.totalMobs, 1, 256) + "|"
                + clamp(v.totalMobsAddedPerPlayer, 0, 64) + "|"
                + clamp(v.simultaneousMobs, 1, 64) + "|"
                + clamp(v.simultaneousMobsAddedPerPlayer, 0, 32) + "|"
                + clamp(v.spawnBatchSize, 1, 8);
    }

    public static TimingData readTiming(NpcDialogueGraph.Node node) {
        TimingData d = new TimingData();
        String[] p = splitPipes(payloadOf(node, SLOT_TIMING));
        if (p.length >= 1) d.minSpawnDelayTicks = clamp(parseInt(p[0], d.minSpawnDelayTicks), 1, 12000);
        if (p.length >= 2) d.maxSpawnDelayTicks = clamp(parseInt(p[1], d.maxSpawnDelayTicks), d.minSpawnDelayTicks, 12000);
        if (p.length >= 3) d.cooldownTicks = clamp(parseInt(p[2], d.cooldownTicks), 0, 720000);
        if (d.maxSpawnDelayTicks < d.minSpawnDelayTicks) d.maxSpawnDelayTicks = d.minSpawnDelayTicks;
        return d;
    }

    public static void writeTiming(NpcDialogueGraph.Node node, TimingData d) {
        if (node == null) return;
        TimingData v = d == null ? new TimingData() : d;
        int minD = clamp(v.minSpawnDelayTicks, 1, 12000);
        int maxD = clamp(v.maxSpawnDelayTicks, minD, 12000);
        int cd = clamp(v.cooldownTicks, 0, 720000);
        node.action = PREFIX + "timing:" + minD + "|" + maxD + "|" + cd;
    }

    public static String mobPoolFile(NpcDialogueGraph.Node node) {
        if (node == null || node.action == null) return "";
        String prefix = PREFIX + "mobs_pool:";
        if (!node.action.startsWith(prefix)) return "";
        String rest = node.action.substring(prefix.length());
        int bar = rest.indexOf('|');
        String rawFile = bar >= 0 ? rest.substring(0, bar) : rest;
        return TrialMobPool449.sanitizeFileName(rawFile);
    }

    public static List<TrialConfig446.SpawnEntry> readMobs(NpcDialogueGraph.Node node) {
        String raw = "";
        if (node != null && node.action != null) {
            String poolPrefix = PREFIX + "mobs_pool:";
            if (node.action.startsWith(poolPrefix)) {
                String rest = node.action.substring(poolPrefix.length());
                int bar = rest.indexOf('|');
                raw = bar >= 0 ? rest.substring(bar + 1) : "";
            } else {
                raw = payloadOf(node, SLOT_MOBS);
            }
        }
        List<TrialConfig446.SpawnEntry> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            String[] items = raw.split(";");
            for (int i = 0; i < items.length && out.size() < TrialConfig446.MAX_ENTRIES; i++) {
                String row = items[i].trim();
                if (row.isEmpty()) continue;
                int star = row.indexOf('*');
                String eid = star >= 0 ? row.substring(0, star) : row;
                int w = star >= 0 ? parseInt(row.substring(star + 1), 1) : 1;
                String norm = TrialConfig446.normalizeId(eid);
                if (norm != null) {
                    out.add(new TrialConfig446.SpawnEntry(norm, clamp(w, 1, 100000)));
                }
            }
        }
        if (out.isEmpty()) return TrialConfig446.defaultMobs();
        return out;
    }

    public static void writeMobs(NpcDialogueGraph.Node node, List<TrialConfig446.SpawnEntry> mobs) {
        writeMobPool(node, mobPoolFile(node), mobs);
    }

    public static void writeMobPool(NpcDialogueGraph.Node node, String poolFile,
            List<TrialConfig446.SpawnEntry> mobs) {
        if (node == null) return;
        String cleanFile = TrialMobPool449.sanitizeFileName(poolFile);
        List<TrialConfig446.SpawnEntry> clean = new ArrayList<>();
        if (mobs != null) {
            for (int i = 0; i < mobs.size() && clean.size() < TrialConfig446.MAX_ENTRIES; i++) {
                TrialConfig446.SpawnEntry e = mobs.get(i);
                if (e == null) continue;
                String id = TrialConfig446.normalizeId(e.entityId);
                if (id != null) clean.add(new TrialConfig446.SpawnEntry(id, clamp(e.weight, 1, 100000)));
            }
        }
        if (clean.isEmpty()) clean = TrialConfig446.defaultMobs();
        StringBuilder sb = new StringBuilder(PREFIX);
        if (!cleanFile.isEmpty()) {
            sb.append("mobs_pool:").append(cleanFile).append('|');
        } else {
            sb.append("mobs:");
        }
        for (int i = 0; i < clean.size(); i++) {
            if (i > 0) sb.append(';');
            TrialConfig446.SpawnEntry e = clean.get(i);
            sb.append(e.entityId).append('*').append(e.weight);
        }
        node.action = sb.toString();
    }

    public static String rewardPoolFile(NpcDialogueGraph.Node node) {
        if (node == null || node.action == null) return "";
        String prefix = PREFIX + "rewards_pool:";
        if (!node.action.startsWith(prefix)) return "";
        String rest = node.action.substring(prefix.length());
        int bar = rest.indexOf('|');
        String rawFile = bar >= 0 ? rest.substring(0, bar) : rest;
        return LootPool436.sanitizeFileName(rawFile);
    }

    public static List<TrialConfig446.RewardEntry> readRewards(NpcDialogueGraph.Node node) {
        if (node == null || node.action == null) {
            return TrialConfig446.defaultRewards();
        }
        String raw;
        String poolPrefix = PREFIX + "rewards_pool:";
        String plainPrefix = PREFIX + "rewards:";
        if (node.action.startsWith(poolPrefix)) {
            String rest = node.action.substring(poolPrefix.length());
            int bar = rest.indexOf('|');
            raw = bar >= 0 ? rest.substring(bar + 1) : "";
        } else if (node.action.startsWith(plainPrefix)) {
            raw = node.action.substring(plainPrefix.length());
        } else {
            return TrialConfig446.defaultRewards();
        }
        List<TrialConfig446.RewardEntry> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        String[] items = raw.split(";");
        for (int i = 0; i < items.length && out.size() < TrialConfig446.MAX_ENTRIES; i++) {
            String row = items[i].trim();
            if (row.isEmpty()) continue;
            String[] p = row.split("\\*", -1);
            String id = p.length >= 1 ? TrialConfig446.normalizeId(p[0]) : null;
            if (id == null) continue;
            int w = p.length >= 2 ? clamp(parseInt(p[1], 1), 1, 100000) : 1;
            int minC = p.length >= 3 ? clamp(parseInt(p[2], 1), 1, 64) : 1;
            int maxC = p.length >= 4 ? clamp(parseInt(p[3], minC), minC, 64) : minC;
            int chance = p.length >= 5 ? clamp(parseInt(p[4], 100), 0, 100) : 100;
            out.add(new TrialConfig446.RewardEntry(id, w, minC, maxC, chance));
        }
        return out;
    }

    public static void writeRewards(NpcDialogueGraph.Node node, List<TrialConfig446.RewardEntry> rewards) {
        writeRewardPool(node, rewardPoolFile(node), rewards);
    }

    public static void writeRewardPool(NpcDialogueGraph.Node node, String poolFile,
            List<TrialConfig446.RewardEntry> rewards) {
        if (node == null) return;
        String cleanFile = LootPool436.sanitizeFileName(poolFile);
        StringBuilder sb = new StringBuilder(PREFIX);
        if (!cleanFile.isEmpty()) {
            sb.append("rewards_pool:").append(cleanFile).append('|');
        } else {
            sb.append("rewards:");
        }
        if (rewards != null) {
            int count = 0;
            for (int i = 0; i < rewards.size() && count < TrialConfig446.MAX_ENTRIES; i++) {
                TrialConfig446.RewardEntry r = rewards.get(i);
                if (r == null) continue;
                String id = TrialConfig446.normalizeId(r.itemId);
                if (id == null) continue;
                int minC = clamp(r.minCount, 1, 64);
                int maxC = clamp(r.maxCount, minC, 64);
                int w = clamp(r.weight, 1, 100000);
                int ch = clamp(r.chancePercent, 0, 100);
                if (count > 0) sb.append(';');
                sb.append(id).append('*').append(w).append('*')
                        .append(minC).append('*').append(maxC).append('*').append(ch);
                count++;
            }
        }
        node.action = sb.toString();
    }

    public static List<TrialConfig446.RewardEntry> fromLootEntries(List<LootPool436.PoolEntry> list) {
        List<TrialConfig446.RewardEntry> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.size() && out.size() < TrialConfig446.MAX_ENTRIES; i++) {
            LootPool436.PoolEntry e = list.get(i);
            if (e == null) continue;
            String id = TrialConfig446.normalizeId(e.itemId);
            if (id == null) continue;
            int w = clamp((int) Math.round(e.weight), 1, 100000);
            int minC = clamp(e.minCount, 1, 64);
            int maxC = clamp(e.maxCount, minC, 64);
            int ch = clamp((int) Math.round(e.rarity), 0, 100);
            out.add(new TrialConfig446.RewardEntry(id, w, minC, maxC, ch));
        }
        return out;
    }

    public static List<LootPool436.PoolEntry> toLootEntries(List<TrialConfig446.RewardEntry> list) {
        List<LootPool436.PoolEntry> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.size() && out.size() < TrialConfig446.MAX_ENTRIES; i++) {
            TrialConfig446.RewardEntry r = list.get(i);
            if (r == null) continue;
            String id = LootPool436.cleanItemId(r.itemId);
            if (id.isEmpty()) continue;
            int minC = clamp(r.minCount, 1, 64);
            int maxC = clamp(r.maxCount, minC, 64);
            double rar = r.chancePercent > 0 ? (double) clamp(r.chancePercent, 0, 100) : 50.0D;
            double wgt = Math.max(1.0D, (double) r.weight);
            out.add(new LootPool436.PoolEntry(id, rar, wgt, minC, maxC));
        }
        return out;
    }

    /**
     * Server-side pass: refreshes cached entries inside any mob/reward pool modules from the
     * matching {@code *.txt} files under {@code config/settledlands/}, then re-syncs the hub JSON.
     */
    public static boolean refreshPoolsFromServerDisk(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return false;
        boolean updated = false;
        try {
            for (NpcDialogueGraph.Node node : graph.nodes.values()) {
                int slot = slotOf(node);
                if (slot == SLOT_MOBS) {
                    String file = mobPoolFile(node);
                    if (!file.isEmpty() && TrialMobPoolManager449.poolExists(file)) {
                        List<TrialConfig446.SpawnEntry> loaded = TrialMobPoolManager449.loadPoolQuiet(file);
                        if (!loaded.isEmpty()) {
                            writeMobPool(node, file, loaded);
                            updated = true;
                        }
                    }
                } else if (slot == SLOT_REWARDS) {
                    String file = rewardPoolFile(node);
                    if (!file.isEmpty() && LootPoolManager436.poolExists(file)) {
                        List<LootPool436.PoolEntry> loaded = LootPoolManager436.loadPoolQuiet(file);
                        writeRewardPool(node, file, fromLootEntries(loaded));
                        updated = true;
                    }
                }
            }
            if (updated) syncCardConfig(graph);
        } catch (Throwable ignored) {
        }
        return updated;
    }

    public static OminousData readOminous(NpcDialogueGraph.Node node) {
        OminousData d = new OminousData();
        String[] p = splitPipes(payloadOf(node, SLOT_OMINOUS));
        if (p.length >= 1) d.mobMultiplier = clampFloat(parseFloat(p[0], d.mobMultiplier), 1.0f, 4.0f);
        if (p.length >= 2) d.equipment = parseBool(p[1], d.equipment);
        if (p.length >= 3) d.hazards = parseBool(p[2], d.hazards);
        if (p.length >= 4) d.hazardIntervalTicks = clamp(parseInt(p[3], d.hazardIntervalTicks), 20, 12000);
        if (p.length >= 5) d.keyConversion = parseBool(p[4], d.keyConversion);
        return d;
    }

    public static void writeOminous(NpcDialogueGraph.Node node, OminousData d) {
        if (node == null) return;
        OminousData v = d == null ? new OminousData() : d;
        float mult = clampFloat(v.mobMultiplier, 1.0f, 4.0f);
        int interval = clamp(v.hazardIntervalTicks, 20, 12000);
        node.action = PREFIX + "ominous:"
                + String.format(Locale.ROOT, "%.2f", mult) + "|"
                + (v.equipment ? "1" : "0") + "|"
                + (v.hazards ? "1" : "0") + "|"
                + interval + "|"
                + (v.keyConversion ? "1" : "0");
    }

    public static ProtectData readProtect(NpcDialogueGraph.Node node) {
        ProtectData d = new ProtectData();
        String[] p = splitPipes(payloadOf(node, SLOT_PROTECT));
        if (p.length >= 1) d.noBreakEnabled = parseBool(p[0], d.noBreakEnabled);
        if (p.length >= 2) d.noBreakRadius = clamp(parseInt(p[1], d.noBreakRadius), 1, 64);
        if (p.length >= 3) d.noBreakSeconds = clamp(parseInt(p[2], d.noBreakSeconds), 1, 600);
        if (p.length >= 4) d.noBreakAmplifier = clamp(parseInt(p[3], d.noBreakAmplifier), 0, 3);
        if (p.length >= 5) d.selfDestruct = parseBool(p[4], d.selfDestruct);
        if (p.length >= 6) d.selfDestructAfterWaves = clamp(parseInt(p[5], d.selfDestructAfterWaves), 1, 100);
        if (p.length >= 7) d.selfDestructChancePercent = clamp(parseInt(p[6], d.selfDestructChancePercent), 0, 100);
        return d;
    }

    public static void writeProtect(NpcDialogueGraph.Node node, ProtectData d) {
        if (node == null) return;
        ProtectData v = d == null ? new ProtectData() : d;
        node.action = PREFIX + "protect:"
                + (v.noBreakEnabled ? "1" : "0") + "|"
                + clamp(v.noBreakRadius, 1, 64) + "|"
                + clamp(v.noBreakSeconds, 1, 600) + "|"
                + clamp(v.noBreakAmplifier, 0, 3) + "|"
                + (v.selfDestruct ? "1" : "0") + "|"
                + clamp(v.selfDestructAfterWaves, 1, 100) + "|"
                + clamp(v.selfDestructChancePercent, 0, 100);
    }

    // ---- config <-> modules bridging ----------------------------------------

    public static void writeFromConfig(NpcDialogueGraph.Node node, int slot, TrialConfig446 src) {
        if (node == null) return;
        TrialConfig446 c = TrialConfig446.sanitize(src == null ? TrialConfig446.defaults() : src.copy());
        switch (slot) {
            case SLOT_MODE -> {
                ModeData d = new ModeData();
                d.mode = c.mode;
                d.scaleWithPlayers = c.scaleWithPlayers;
                writeMode(node, d);
            }
            case SLOT_RADIUS -> {
                RadiusData d = new RadiusData();
                d.activationRange = c.activationRange;
                d.spawnRange = c.spawnRange;
                writeRadius(node, d);
            }
            case SLOT_WAVE -> {
                WaveData d = new WaveData();
                d.totalMobs = c.totalMobs;
                d.totalMobsAddedPerPlayer = c.totalMobsAddedPerPlayer;
                d.simultaneousMobs = c.simultaneousMobs;
                d.simultaneousMobsAddedPerPlayer = c.simultaneousMobsAddedPerPlayer;
                d.spawnBatchSize = c.spawnBatchSize;
                writeWave(node, d);
            }
            case SLOT_TIMING -> {
                TimingData d = new TimingData();
                d.minSpawnDelayTicks = c.minSpawnDelayTicks;
                d.maxSpawnDelayTicks = c.maxSpawnDelayTicks;
                d.cooldownTicks = c.cooldownTicks;
                writeTiming(node, d);
            }
            case SLOT_MOBS -> writeMobs(node, c.mobs);
            case SLOT_REWARDS -> writeRewards(node, c.rewards);
            case SLOT_OMINOUS -> {
                OminousData d = new OminousData();
                d.mobMultiplier = c.ominousMobMultiplier;
                d.equipment = c.ominousEquipment;
                d.hazards = c.ominousHazards;
                d.hazardIntervalTicks = c.ominousHazardIntervalTicks;
                d.keyConversion = c.ominousKeyConversion;
                writeOminous(node, d);
            }
            case SLOT_PROTECT -> {
                ProtectData d = new ProtectData();
                d.noBreakEnabled = c.noBreakEnabled;
                d.noBreakRadius = c.noBreakRadius;
                d.noBreakSeconds = c.noBreakSeconds;
                d.noBreakAmplifier = c.noBreakAmplifier;
                d.selfDestruct = c.selfDestruct;
                d.selfDestructAfterWaves = c.selfDestructAfterWaves;
                d.selfDestructChancePercent = c.selfDestructChancePercent;
                writeProtect(node, d);
            }
            default -> {
            }
        }
    }

    public static void applyToConfig(NpcDialogueGraph.Node node, TrialConfig446 dst) {
        if (node == null || dst == null) return;
        int slot = slotOf(node);
        switch (slot) {
            case SLOT_MODE -> {
                ModeData d = readMode(node);
                dst.mode = d.mode;
                dst.scaleWithPlayers = d.scaleWithPlayers;
            }
            case SLOT_RADIUS -> {
                RadiusData d = readRadius(node);
                dst.activationRange = d.activationRange;
                dst.spawnRange = d.spawnRange;
            }
            case SLOT_WAVE -> {
                WaveData d = readWave(node);
                dst.totalMobs = d.totalMobs;
                dst.totalMobsAddedPerPlayer = d.totalMobsAddedPerPlayer;
                dst.simultaneousMobs = d.simultaneousMobs;
                dst.simultaneousMobsAddedPerPlayer = d.simultaneousMobsAddedPerPlayer;
                dst.spawnBatchSize = d.spawnBatchSize;
            }
            case SLOT_TIMING -> {
                TimingData d = readTiming(node);
                dst.minSpawnDelayTicks = d.minSpawnDelayTicks;
                dst.maxSpawnDelayTicks = d.maxSpawnDelayTicks;
                dst.cooldownTicks = d.cooldownTicks;
            }
            case SLOT_MOBS -> dst.mobs = readMobs(node);
            case SLOT_REWARDS -> dst.rewards = readRewards(node);
            case SLOT_OMINOUS -> {
                OminousData d = readOminous(node);
                dst.ominousMobMultiplier = d.mobMultiplier;
                dst.ominousEquipment = d.equipment;
                dst.ominousHazards = d.hazards;
                dst.ominousHazardIntervalTicks = d.hazardIntervalTicks;
                dst.ominousKeyConversion = d.keyConversion;
            }
            case SLOT_PROTECT -> {
                ProtectData d = readProtect(node);
                dst.noBreakEnabled = d.noBreakEnabled;
                dst.noBreakRadius = d.noBreakRadius;
                dst.noBreakSeconds = d.noBreakSeconds;
                dst.noBreakAmplifier = d.noBreakAmplifier;
                dst.selfDestruct = d.selfDestruct;
                dst.selfDestructAfterWaves = d.selfDestructAfterWaves;
                dst.selfDestructChancePercent = d.selfDestructChancePercent;
            }
            default -> {
            }
        }
    }

    // ---- graph assembly & wiring --------------------------------------------

    public static boolean hasAnyModule(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return false;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (isModuleNode(n)) return true;
        }
        return false;
    }

    public static boolean isConnectedToHub(NpcDialogueGraph graph, NpcDialogueGraph.Node mod,
            NpcDialogueGraph.Node hub) {
        return graph != null && hub != null && isModuleNode(mod) && mod.next == hub.id;
    }

    public static NpcDialogueGraph.Node connectedModule(NpcDialogueGraph graph,
            NpcDialogueGraph.Node hub, int slot) {
        if (graph == null || graph.nodes == null || hub == null || slot < 0 || slot >= MODULE_COUNT) {
            return null;
        }
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (slotOf(n) == slot && n.next == hub.id) return n;
        }
        return null;
    }

    public static int connectedCount(NpcDialogueGraph graph, NpcDialogueGraph.Node hub) {
        int count = 0;
        for (int s = 0; s < MODULE_COUNT; s++) {
            if (connectedModule(graph, hub, s) != null) count++;
        }
        return count;
    }

    public static TrialConfig446 compileConfig(NpcDialogueGraph graph) {
        TrialConfig446 cfg = TrialConfig446.defaults();
        NpcDialogueGraph.Node hub = TrialGraph446.trialNode(graph);
        if (hub == null) return cfg;

        // Preserve any legacy command strings attached to the hub if present.
        if (hub.action != null && !hub.action.isBlank()) {
            TrialConfig446 legacy = TrialConfig446.fromJson(hub.action);
            cfg.onWaveClearCommands = legacy.onWaveClearCommands;
            cfg.onDestroyCommands = legacy.onDestroyCommands;
        }
        if (hub.varOp == 0 && !hasAnyModule(graph) && hub.action != null && !hub.action.isBlank()) {
            // Un-migrated single-node Build 446 graph
            return TrialConfig446.fromJson(hub.action);
        }
        for (int s = 0; s < MODULE_COUNT; s++) {
            NpcDialogueGraph.Node mod = connectedModule(graph, hub, s);
            if (mod != null) {
                applyToConfig(mod, cfg);
            }
        }
        return TrialConfig446.sanitize(cfg);
    }

    public static void syncCardConfig(NpcDialogueGraph graph) {
        NpcDialogueGraph.Node hub = TrialGraph446.trialNode(graph);
        if (hub == null) return;
        TrialConfig446 compiled = compileConfig(graph);
        hub.action = compiled.toJson();
    }

    public static void applyConfigToConnectedModules(NpcDialogueGraph graph, TrialConfig446 cfg) {
        if (graph == null || cfg == null) return;
        NpcDialogueGraph.Node hub = TrialGraph446.trialNode(graph);
        if (hub == null) return;
        TrialConfig446 clean = TrialConfig446.sanitize(cfg.copy());
        for (int s = 0; s < MODULE_COUNT; s++) {
            NpcDialogueGraph.Node mod = connectedModule(graph, hub, s);
            if (mod != null) {
                writeFromConfig(mod, s, clean);
            }
        }
        hub.action = clean.toJson();
    }

    /** Creates and connects any missing module slots (0..7) around {@code hub}. */
    public static int createMissingModules(NpcDialogueGraph graph, NpcDialogueGraph.Node hub,
            TrialConfig446 cfg) {
        if (graph == null || hub == null) return 0;
        TrialConfig446 src = TrialConfig446.sanitize(cfg == null ? TrialConfig446.defaults() : cfg.copy());
        int created = 0;
        for (int s = 0; s < MODULE_COUNT; s++) {
            if (connectedModule(graph, hub, s) != null) continue;
            int x = defaultXFor(hub, s);
            int y = defaultYFor(hub, s);
            NpcDialogueGraph.Node mod = graph.add(NpcDialogueGraph.TRIGGER, x, y);
            if (mod == null) continue;
            mod.special = 0;
            writeFromConfig(mod, s, src);
            mod.next = hub.id;
            created++;
        }
        hub.varOp = 1;
        syncCardConfig(graph);
        return created;
    }

    /** Connects module {@code modId} to {@code hubId}, replacing any existing module wired to the same slot. */
    public static boolean connectModule(NpcDialogueGraph graph, int modId, int hubId) {
        if (graph == null || graph.nodes == null) return false;
        NpcDialogueGraph.Node mod = graph.get(modId);
        NpcDialogueGraph.Node hub = graph.get(hubId);
        int slot = slotOf(mod);
        if (slot < 0 || !TrialGraph446.isTrialNode(hub)) return false;
        for (NpcDialogueGraph.Node other : graph.nodes.values()) {
            if (other != null && other.id != mod.id && slotOf(other) == slot && other.next == hub.id) {
                other.next = -1;
            }
        }
        mod.next = hub.id;
        hub.varOp = 1;
        syncCardConfig(graph);
        return true;
    }

    public static boolean disconnectModule(NpcDialogueGraph graph, int modId) {
        if (graph == null) return false;
        NpcDialogueGraph.Node mod = graph.get(modId);
        if (!isModuleNode(mod) || mod.next < 0) return false;
        mod.next = -1;
        syncCardConfig(graph);
        return true;
    }

    public static boolean disconnectSlot(NpcDialogueGraph graph, int hubId, int slot) {
        if (graph == null || graph.nodes == null || slot < 0 || slot >= MODULE_COUNT) return false;
        NpcDialogueGraph.Node hub = graph.get(hubId);
        if (!TrialGraph446.isTrialNode(hub)) return false;
        boolean changed = false;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (slotOf(n) == slot && n.next == hub.id) {
                n.next = -1;
                changed = true;
            }
        }
        if (changed) syncCardConfig(graph);
        return changed;
    }

    // ---- helpers ------------------------------------------------------------

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            float v = Float.parseFloat(raw.trim().replace(',', '.'));
            return Float.isFinite(v) ? v : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null) return fallback;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s) || "true".equals(s)) return true;
        if ("0".equals(s) || "false".equals(s)) return false;
        return fallback;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampFloat(float v, float min, float max) {
        if (!Float.isFinite(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
