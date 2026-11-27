package dev.settledlands.trial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Build 446: pure-Java mirror of Trial Tuner's {@code SpawnerConfig} with exact defaults,
 * field names, clamp bounds, and a standalone JSON parser/writer (zero hard dependency on
 * {@code trialtuner} or external JSON classes).
 */
public final class TrialConfig446 {

    public static final int MAX_ENTRIES = 32;
    public static final int MAX_JSON_CHARS = 24000;
    public static final int MAX_TRIGGER_CHARS = 4000;

    public static final String MODE_TRIAL = "TRIAL";
    public static final String MODE_OMINOUS = "OMINOUS";
    public static final String MODE_SPECIAL = "SPECIAL";

    public static final class SpawnEntry {
        public String entityId;
        public int weight;
        public String weightRange = "";

        public SpawnEntry() {
            this("minecraft:zombie", 1);
        }

        public SpawnEntry(String entityId, int weight) {
            this(entityId, weight, "");
        }

        public SpawnEntry(String entityId, int weight, String weightRange) {
            this.entityId = entityId == null ? "minecraft:zombie" : entityId;
            this.weight = weight;
            this.weightRange = weightRange == null ? "" : weightRange;
        }

        public String displayWeight() {
            return (weightRange != null && !weightRange.isBlank())
                    ? weightRange : Integer.toString(weight);
        }

        public SpawnEntry copy() {
            return new SpawnEntry(entityId, weight, weightRange);
        }
    }

    public static final class RewardEntry {
        public String itemId;
        public int weight;
        public int minCount;
        public int maxCount;
        public int chancePercent;
        public String weightRange = "";
        public String chanceRange = "";

        public RewardEntry() {
            this("minecraft:bread", 1, 1, 1, 100);
        }

        public RewardEntry(String itemId, int weight, int minCount, int maxCount, int chancePercent) {
            this(itemId, weight, minCount, maxCount, chancePercent, "", "");
        }

        public RewardEntry(String itemId, int weight, int minCount, int maxCount, int chancePercent,
                String weightRange, String chanceRange) {
            this.itemId = itemId == null ? "minecraft:bread" : itemId;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.chancePercent = chancePercent;
            this.weightRange = weightRange == null ? "" : weightRange;
            this.chanceRange = chanceRange == null ? "" : chanceRange;
        }

        public String displayWeight() {
            return (weightRange != null && !weightRange.isBlank())
                    ? weightRange : Integer.toString(weight);
        }

        public String displayChance() {
            return (chanceRange != null && !chanceRange.isBlank())
                    ? chanceRange : Integer.toString(chancePercent);
        }

        public RewardEntry copy() {
            return new RewardEntry(itemId, weight, minCount, maxCount, chancePercent,
                    weightRange, chanceRange);
        }
    }

    public String mode = MODE_TRIAL;
    public int activationRange = 14;
    public int spawnRange = 4;
    public int totalMobs = 6;
    public int totalMobsAddedPerPlayer = 2;
    public int simultaneousMobs = 2;
    public int simultaneousMobsAddedPerPlayer = 1;
    public int spawnBatchSize = 1;
    public int minSpawnDelayTicks = 20;
    public int maxSpawnDelayTicks = 40;
    public int cooldownTicks = 36000;
    public boolean scaleWithPlayers = true;
    public boolean selfDestruct = false;
    public int selfDestructAfterWaves = 3;
    public int selfDestructChancePercent = 25;
    public float ominousMobMultiplier = 1.5f;
    public boolean ominousEquipment = true;
    public boolean ominousHazards = true;
    public boolean ominousKeyConversion = true;
    public int ominousHazardIntervalTicks = 160;
    public List<SpawnEntry> mobs = defaultMobs();
    public List<RewardEntry> rewards = defaultRewards();
    public String onWaveClearCommands = "";
    public String onDestroyCommands = "";
    public boolean noBreakEnabled = true;
    public int noBreakRadius = 24;
    public int noBreakSeconds = 60;
    public int noBreakAmplifier = 3;
    public final Map<String, String> ranges = new LinkedHashMap<>();

    public static TrialConfig446 defaults() {
        return new TrialConfig446();
    }

    public static List<SpawnEntry> defaultMobs() {
        List<SpawnEntry> out = new ArrayList<>();
        out.add(new SpawnEntry("minecraft:zombie", 1));
        return out;
    }

    public static List<RewardEntry> defaultRewards() {
        List<RewardEntry> out = new ArrayList<>();
        out.add(new RewardEntry("minecraft:trial_key", 10, 1, 1, 100));
        out.add(new RewardEntry("minecraft:bread", 3, 1, 3, 100));
        out.add(new RewardEntry("minecraft:cooked_chicken", 3, 1, 1, 100));
        out.add(new RewardEntry("minecraft:baked_potato", 2, 1, 3, 100));
        out.add(new RewardEntry("minecraft:emerald", 1, 1, 2, 100));
        return out;
    }

    public TrialConfig446 copy() {
        TrialConfig446 c = new TrialConfig446();
        c.mode = mode;
        c.activationRange = activationRange;
        c.spawnRange = spawnRange;
        c.totalMobs = totalMobs;
        c.totalMobsAddedPerPlayer = totalMobsAddedPerPlayer;
        c.simultaneousMobs = simultaneousMobs;
        c.simultaneousMobsAddedPerPlayer = simultaneousMobsAddedPerPlayer;
        c.spawnBatchSize = spawnBatchSize;
        c.minSpawnDelayTicks = minSpawnDelayTicks;
        c.maxSpawnDelayTicks = maxSpawnDelayTicks;
        c.cooldownTicks = cooldownTicks;
        c.scaleWithPlayers = scaleWithPlayers;
        c.selfDestruct = selfDestruct;
        c.selfDestructAfterWaves = selfDestructAfterWaves;
        c.selfDestructChancePercent = selfDestructChancePercent;
        c.ominousMobMultiplier = ominousMobMultiplier;
        c.ominousEquipment = ominousEquipment;
        c.ominousHazards = ominousHazards;
        c.ominousKeyConversion = ominousKeyConversion;
        c.ominousHazardIntervalTicks = ominousHazardIntervalTicks;
        c.onWaveClearCommands = onWaveClearCommands;
        c.onDestroyCommands = onDestroyCommands;
        c.noBreakEnabled = noBreakEnabled;
        c.noBreakRadius = noBreakRadius;
        c.noBreakSeconds = noBreakSeconds;
        c.noBreakAmplifier = noBreakAmplifier;
        c.mobs = new ArrayList<>();
        if (mobs != null) {
            for (SpawnEntry e : mobs) {
                if (e != null) c.mobs.add(e.copy());
            }
        }
        c.rewards = new ArrayList<>();
        if (rewards != null) {
            for (RewardEntry r : rewards) {
                if (r != null) c.rewards.add(r.copy());
            }
        }
        c.ranges.putAll(ranges);
        return sanitize(c);
    }

    public void copyFrom(TrialConfig446 src) {
        TrialConfig446 c = src == null ? defaults() : src.copy();
        this.mode = c.mode;
        this.activationRange = c.activationRange;
        this.spawnRange = c.spawnRange;
        this.totalMobs = c.totalMobs;
        this.totalMobsAddedPerPlayer = c.totalMobsAddedPerPlayer;
        this.simultaneousMobs = c.simultaneousMobs;
        this.simultaneousMobsAddedPerPlayer = c.simultaneousMobsAddedPerPlayer;
        this.spawnBatchSize = c.spawnBatchSize;
        this.minSpawnDelayTicks = c.minSpawnDelayTicks;
        this.maxSpawnDelayTicks = c.maxSpawnDelayTicks;
        this.cooldownTicks = c.cooldownTicks;
        this.scaleWithPlayers = c.scaleWithPlayers;
        this.selfDestruct = c.selfDestruct;
        this.selfDestructAfterWaves = c.selfDestructAfterWaves;
        this.selfDestructChancePercent = c.selfDestructChancePercent;
        this.ominousMobMultiplier = c.ominousMobMultiplier;
        this.ominousEquipment = c.ominousEquipment;
        this.ominousHazards = c.ominousHazards;
        this.ominousKeyConversion = c.ominousKeyConversion;
        this.ominousHazardIntervalTicks = c.ominousHazardIntervalTicks;
        this.mobs = new ArrayList<>();
        for (SpawnEntry e : c.mobs) {
            if (e != null) this.mobs.add(e.copy());
        }
        this.rewards = new ArrayList<>();
        for (RewardEntry r : c.rewards) {
            if (r != null) this.rewards.add(r.copy());
        }
        this.onWaveClearCommands = c.onWaveClearCommands;
        this.onDestroyCommands = c.onDestroyCommands;
        this.noBreakEnabled = c.noBreakEnabled;
        this.noBreakRadius = c.noBreakRadius;
        this.noBreakSeconds = c.noBreakSeconds;
        this.noBreakAmplifier = c.noBreakAmplifier;
        this.ranges.clear();
        this.ranges.putAll(c.ranges);
    }

    public static TrialConfig446 sanitize(TrialConfig446 cfg) {
        if (cfg == null) cfg = defaults();
        cfg.mode = normalizeMode(cfg.mode);
        cfg.onWaveClearCommands = cleanTrigger(cfg.onWaveClearCommands);
        cfg.onDestroyCommands = cleanTrigger(cfg.onDestroyCommands);
        cfg.activationRange = clamp(cfg.activationRange, 1, 64);
        cfg.spawnRange = clamp(cfg.spawnRange, 1, 16);
        cfg.totalMobs = clamp(cfg.totalMobs, 1, 256);
        cfg.totalMobsAddedPerPlayer = clamp(cfg.totalMobsAddedPerPlayer, 0, 64);
        cfg.simultaneousMobs = clamp(cfg.simultaneousMobs, 1, 64);
        cfg.simultaneousMobsAddedPerPlayer = clamp(cfg.simultaneousMobsAddedPerPlayer, 0, 32);
        cfg.spawnBatchSize = clamp(cfg.spawnBatchSize, 1, 8);
        cfg.minSpawnDelayTicks = clamp(cfg.minSpawnDelayTicks, 1, 12000);
        cfg.maxSpawnDelayTicks = clamp(cfg.maxSpawnDelayTicks, cfg.minSpawnDelayTicks, 12000);
        cfg.cooldownTicks = clamp(cfg.cooldownTicks, 0, 720000);
        cfg.selfDestructAfterWaves = clamp(cfg.selfDestructAfterWaves, 1, 100);
        cfg.selfDestructChancePercent = clamp(cfg.selfDestructChancePercent, 0, 100);
        if (!Float.isFinite(cfg.ominousMobMultiplier)) cfg.ominousMobMultiplier = 1.5f;
        cfg.ominousMobMultiplier = Math.max(1.0f, Math.min(4.0f, cfg.ominousMobMultiplier));
        cfg.ominousHazardIntervalTicks = clamp(cfg.ominousHazardIntervalTicks, 20, 12000);
        cfg.noBreakRadius = clamp(cfg.noBreakRadius, 1, 64);
        cfg.noBreakSeconds = clamp(cfg.noBreakSeconds, 1, 600);
        cfg.noBreakAmplifier = clamp(cfg.noBreakAmplifier, 0, 3);

        List<SpawnEntry> cleanMobs = new ArrayList<>();
        if (cfg.mobs != null) {
            for (SpawnEntry e : cfg.mobs) {
                if (e == null || cleanMobs.size() >= MAX_ENTRIES) continue;
                String id = normalizeId(e.entityId);
                if (id == null) continue;
                String wr = "";
                int w = clamp(e.weight, 1, 100000);
                if (RangeSpec450.hasRangeSeparator(e.weightRange)) {
                    RangeSpec450.IntRange rr = RangeSpec450.parseInt(e.weightRange, w, w, 1, 100000);
                    if (rr.isRange()) {
                        wr = rr.format();
                        w = rr.min;
                    } else {
                        w = rr.min;
                    }
                }
                cleanMobs.add(new SpawnEntry(id, w, wr));
            }
        }
        if (cleanMobs.isEmpty()) cleanMobs = defaultMobs();
        cfg.mobs = cleanMobs;

        List<RewardEntry> cleanRewards = new ArrayList<>();
        if (cfg.rewards != null) {
            for (RewardEntry r : cfg.rewards) {
                if (r == null || cleanRewards.size() >= MAX_ENTRIES) continue;
                String id = normalizeId(r.itemId);
                if (id == null) continue;
                int minC = clamp(r.minCount, 1, 64);
                int maxC = clamp(r.maxCount, minC, 64);
                int w = clamp(r.weight, 1, 100000);
                String wr = "";
                if (RangeSpec450.hasRangeSeparator(r.weightRange)) {
                    RangeSpec450.IntRange rr = RangeSpec450.parseInt(r.weightRange, w, w, 1, 100000);
                    if (rr.isRange()) {
                        wr = rr.format();
                        w = rr.min;
                    } else {
                        w = rr.min;
                    }
                }
                int ch = clamp(r.chancePercent, 0, 100);
                String cr = "";
                if (RangeSpec450.hasRangeSeparator(r.chanceRange)) {
                    RangeSpec450.IntRange crR = RangeSpec450.parseInt(r.chanceRange, ch, ch, 0, 100);
                    if (crR.isRange()) {
                        cr = crR.format();
                        ch = crR.min;
                    } else {
                        ch = crR.min;
                    }
                }
                cleanRewards.add(new RewardEntry(id, w, minC, maxC, ch, wr, cr));
            }
        }
        cfg.rewards = cleanRewards;
        sanitizeRangesMap(cfg);
        return cfg;
    }

    private static void sanitizeRangesMap(TrialConfig446 cfg) {
        if (cfg.ranges.isEmpty()) return;
        Map<String, String> cleaned = new LinkedHashMap<>();
        cleanIntRange(cfg, cleaned, "activationRange", cfg.activationRange, 1, 64);
        cleanIntRange(cfg, cleaned, "spawnRange", cfg.spawnRange, 1, 16);
        cleanIntRange(cfg, cleaned, "totalMobs", cfg.totalMobs, 1, 256);
        cleanIntRange(cfg, cleaned, "totalMobsAddedPerPlayer", cfg.totalMobsAddedPerPlayer, 0, 64);
        cleanIntRange(cfg, cleaned, "simultaneousMobs", cfg.simultaneousMobs, 1, 64);
        cleanIntRange(cfg, cleaned, "simultaneousMobsAddedPerPlayer", cfg.simultaneousMobsAddedPerPlayer, 0, 32);
        cleanIntRange(cfg, cleaned, "spawnBatchSize", cfg.spawnBatchSize, 1, 8);
        cleanIntRange(cfg, cleaned, "cooldownTicks", cfg.cooldownTicks, 0, 720000);
        cleanIntRange(cfg, cleaned, "selfDestructAfterWaves", cfg.selfDestructAfterWaves, 1, 100);
        cleanIntRange(cfg, cleaned, "selfDestructChancePercent", cfg.selfDestructChancePercent, 0, 100);
        cleanFloatRange(cfg, cleaned, "ominousMobMultiplier", cfg.ominousMobMultiplier, 1.0F, 4.0F);
        cleanIntRange(cfg, cleaned, "ominousHazardIntervalTicks", cfg.ominousHazardIntervalTicks, 20, 12000);
        cleanIntRange(cfg, cleaned, "noBreakRadius", cfg.noBreakRadius, 1, 64);
        cleanIntRange(cfg, cleaned, "noBreakSeconds", cfg.noBreakSeconds, 1, 600);
        cleanIntRange(cfg, cleaned, "noBreakAmplifier", cfg.noBreakAmplifier, 0, 3);
        cfg.ranges.clear();
        cfg.ranges.putAll(cleaned);
    }

    private static void cleanIntRange(TrialConfig446 cfg, Map<String, String> dst, String key,
            int curVal, int min, int max) {
        String raw = cfg.ranges.get(key);
        if (raw == null || !RangeSpec450.hasRangeSeparator(raw)) return;
        RangeSpec450.IntRange r = RangeSpec450.parseInt(raw, curVal, curVal, min, max);
        if (r.isRange()) {
            dst.put(key, r.format());
        }
    }

    private static void cleanFloatRange(TrialConfig446 cfg, Map<String, String> dst, String key,
            float curVal, float min, float max) {
        String raw = cfg.ranges.get(key);
        if (raw == null || !RangeSpec450.hasRangeSeparator(raw)) return;
        RangeSpec450.FloatRange r = RangeSpec450.parseFloat(raw, curVal, curVal, min, max);
        if (r.isRange()) {
            dst.put(key, r.format());
        }
    }

    public boolean hasRanges() {
        if (!ranges.isEmpty()) return true;
        if (mobs != null) {
            for (SpawnEntry e : mobs) {
                if (e != null && e.weightRange != null && !e.weightRange.isBlank()) return true;
            }
        }
        if (rewards != null) {
            for (RewardEntry r : rewards) {
                if (r != null && ((r.weightRange != null && !r.weightRange.isBlank())
                        || (r.chanceRange != null && !r.chanceRange.isBlank()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setIntFieldWithRange(String key, String raw, int fallback, int min, int max) {
        RangeSpec450.IntRange r = RangeSpec450.parseInt(raw, fallback, fallback, min, max);
        setIntByKey(key, r.min);
        if (r.isRange()) ranges.put(key, r.format());
        else ranges.remove(key);
    }

    public void setFloatFieldWithRange(String key, String raw, float fallback, float min, float max) {
        RangeSpec450.FloatRange r = RangeSpec450.parseFloat(raw, fallback, fallback, min, max);
        if ("ominousMobMultiplier".equals(key)) ominousMobMultiplier = r.min;
        if (r.isRange()) ranges.put(key, r.format());
        else ranges.remove(key);
    }

    public String rangeOrIntText(String key, int fallback) {
        String r = ranges.get(key);
        return (r != null && !r.isBlank()) ? r : Integer.toString(fallback);
    }

    public String rangeOrFloatText(String key, float fallback) {
        String r = ranges.get(key);
        return (r != null && !r.isBlank()) ? r : RangeSpec450.formatFloatCompact(fallback);
    }

    /** Resolves all dynamic ranges into concrete numbers for TrialSpawnerBlockEntity. */
    public TrialConfig446 resolveRanges(Random rnd) {
        TrialConfig446 out = copy();
        if (rnd == null) rnd = new Random();
        if (!out.ranges.isEmpty()) {
            out.activationRange = pickRangeInt(out.ranges.get("activationRange"), out.activationRange, 1, 64, rnd);
            out.spawnRange = pickRangeInt(out.ranges.get("spawnRange"), out.spawnRange, 1, 16, rnd);
            out.totalMobs = pickRangeInt(out.ranges.get("totalMobs"), out.totalMobs, 1, 256, rnd);
            out.totalMobsAddedPerPlayer = pickRangeInt(out.ranges.get("totalMobsAddedPerPlayer"),
                    out.totalMobsAddedPerPlayer, 0, 64, rnd);
            out.simultaneousMobs = pickRangeInt(out.ranges.get("simultaneousMobs"), out.simultaneousMobs, 1, 64, rnd);
            out.simultaneousMobsAddedPerPlayer = pickRangeInt(out.ranges.get("simultaneousMobsAddedPerPlayer"),
                    out.simultaneousMobsAddedPerPlayer, 0, 32, rnd);
            out.spawnBatchSize = pickRangeInt(out.ranges.get("spawnBatchSize"), out.spawnBatchSize, 1, 8, rnd);
            out.cooldownTicks = pickRangeInt(out.ranges.get("cooldownTicks"), out.cooldownTicks, 0, 720000, rnd);
            out.selfDestructAfterWaves = pickRangeInt(out.ranges.get("selfDestructAfterWaves"),
                    out.selfDestructAfterWaves, 1, 100, rnd);
            out.selfDestructChancePercent = pickRangeInt(out.ranges.get("selfDestructChancePercent"),
                    out.selfDestructChancePercent, 0, 100, rnd);
            String multRaw = out.ranges.get("ominousMobMultiplier");
            if (multRaw != null && !multRaw.isBlank()) {
                out.ominousMobMultiplier = RangeSpec450.parseFloat(
                        multRaw, out.ominousMobMultiplier, out.ominousMobMultiplier, 1.0F, 4.0F).pick(rnd);
            }
            out.ominousHazardIntervalTicks = pickRangeInt(out.ranges.get("ominousHazardIntervalTicks"),
                    out.ominousHazardIntervalTicks, 20, 12000, rnd);
            out.noBreakRadius = pickRangeInt(out.ranges.get("noBreakRadius"), out.noBreakRadius, 1, 64, rnd);
            out.noBreakSeconds = pickRangeInt(out.ranges.get("noBreakSeconds"), out.noBreakSeconds, 1, 600, rnd);
            out.noBreakAmplifier = pickRangeInt(out.ranges.get("noBreakAmplifier"), out.noBreakAmplifier, 0, 3, rnd);
            out.ranges.clear();
        }
        if (out.mobs != null) {
            for (SpawnEntry e : out.mobs) {
                if (e == null) continue;
                if (e.weightRange != null && !e.weightRange.isBlank()) {
                    e.weight = RangeSpec450.parseInt(e.weightRange, e.weight, e.weight, 1, 100000).pick(rnd);
                    e.weightRange = "";
                }
            }
        }
        if (out.rewards != null) {
            for (RewardEntry r : out.rewards) {
                if (r == null) continue;
                if (r.weightRange != null && !r.weightRange.isBlank()) {
                    r.weight = RangeSpec450.parseInt(r.weightRange, r.weight, r.weight, 1, 100000).pick(rnd);
                    r.weightRange = "";
                }
                if (r.chanceRange != null && !r.chanceRange.isBlank()) {
                    r.chancePercent = RangeSpec450.parseInt(
                            r.chanceRange, r.chancePercent, r.chancePercent, 0, 100).pick(rnd);
                    r.chanceRange = "";
                }
            }
        }
        return sanitize(out);
    }

    public String toResolvedJson(Random rnd) {
        if (!hasRanges()) return toJson();
        return resolveRanges(rnd).toJson();
    }

    private static int pickRangeInt(String raw, int fallback, int min, int max, Random rnd) {
        if (raw == null || raw.isBlank()) return fallback;
        return RangeSpec450.parseInt(raw, fallback, fallback, min, max).pick(rnd);
    }

    private void setIntByKey(String key, int val) {
        switch (key) {
            case "activationRange" -> activationRange = val;
            case "spawnRange" -> spawnRange = val;
            case "totalMobs" -> totalMobs = val;
            case "totalMobsAddedPerPlayer" -> totalMobsAddedPerPlayer = val;
            case "simultaneousMobs" -> simultaneousMobs = val;
            case "simultaneousMobsAddedPerPlayer" -> simultaneousMobsAddedPerPlayer = val;
            case "spawnBatchSize" -> spawnBatchSize = val;
            case "cooldownTicks" -> cooldownTicks = val;
            case "selfDestructAfterWaves" -> selfDestructAfterWaves = val;
            case "selfDestructChancePercent" -> selfDestructChancePercent = val;
            case "ominousHazardIntervalTicks" -> ominousHazardIntervalTicks = val;
            case "noBreakRadius" -> noBreakRadius = val;
            case "noBreakSeconds" -> noBreakSeconds = val;
            case "noBreakAmplifier" -> noBreakAmplifier = val;
            default -> { }
        }
    }

    /** Strips unknown entity/item ids only when registries are actually initialized (runtime). */
    public static TrialConfig446 sanitizeWithRegistries(TrialConfig446 cfg) {
        cfg = sanitize(cfg);
        try {
            List<SpawnEntry> validMobs = new ArrayList<>();
            for (SpawnEntry e : cfg.mobs) {
                ResourceLocation rl = ResourceLocation.tryParse(e.entityId);
                if (rl != null && (BuiltInRegistries.ENTITY_TYPE == null
                        || isKnownEntity(rl))) {
                    validMobs.add(e);
                }
            }
            if (!validMobs.isEmpty()) cfg.mobs = validMobs;
            List<RewardEntry> validRewards = new ArrayList<>();
            for (RewardEntry r : cfg.rewards) {
                ResourceLocation rl = ResourceLocation.tryParse(r.itemId);
                if (rl != null && (BuiltInRegistries.ITEM == null || isKnownItem(rl))) {
                    validRewards.add(r);
                }
            }
            cfg.rewards = validRewards;
        } catch (Throwable ignored) {
        }
        return sanitize(cfg);
    }

    private static boolean isKnownEntity(ResourceLocation rl) {
        try {
            if (BuiltInRegistries.ENTITY_TYPE == null) return true;
            Object got = BuiltInRegistries.ENTITY_TYPE.get(rl);
            return got != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isKnownItem(ResourceLocation rl) {
        try {
            if (BuiltInRegistries.ITEM == null) return true;
            Object got = BuiltInRegistries.ITEM.get(rl);
            return got != null;
        } catch (Throwable t) {
            return true;
        }
    }

    public static String normalizeMode(String raw) {
        if (raw == null) return MODE_TRIAL;
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (MODE_OMINOUS.equals(u)) return MODE_OMINOUS;
        if (MODE_SPECIAL.equals(u)) return MODE_SPECIAL;
        return MODE_TRIAL;
    }

    public static String nextMode(String raw) {
        String cur = normalizeMode(raw);
        if (MODE_TRIAL.equals(cur)) return MODE_OMINOUS;
        if (MODE_OMINOUS.equals(cur)) return MODE_SPECIAL;
        return MODE_TRIAL;
    }

    public static String normalizeId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 128) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (!s.contains(":")) s = "minecraft:" + s;
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl != null) return rl.toString();
        // Offline unit test fallback when ResourceLocation is stubbed or strict.
        return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? s : null;
    }

    public static String cleanTrigger(String raw) {
        if (raw == null) return "";
        String s = raw.replace('\r', ';').replace('\n', ';');
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_TRIGGER_CHARS));
        for (int i = 0; i < s.length() && sb.length() < MAX_TRIGGER_CHARS; i++) {
            char ch = s.charAt(i);
            if (!Character.isISOControl(ch)) sb.append(ch);
        }
        return sb.toString();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static TrialConfig446 fromJson(String json) {
        TrialConfig446 cfg = defaults();
        if (json == null || json.isBlank() || json.length() > MAX_JSON_CHARS) {
            return cfg;
        }
        try {
            Object parsed = new JsonParser(json).parseValue();
            if (!(parsed instanceof Map<?, ?> map)) return cfg;
            cfg.mode = normalizeMode(asString(map.get("mode"), MODE_TRIAL));
            cfg.activationRange = asInt(map.get("activationRange"), cfg.activationRange);
            cfg.spawnRange = asInt(map.get("spawnRange"), cfg.spawnRange);
            cfg.totalMobs = asInt(map.get("totalMobs"), cfg.totalMobs);
            cfg.totalMobsAddedPerPlayer = asInt(map.get("totalMobsAddedPerPlayer"), cfg.totalMobsAddedPerPlayer);
            cfg.simultaneousMobs = asInt(map.get("simultaneousMobs"), cfg.simultaneousMobs);
            cfg.simultaneousMobsAddedPerPlayer = asInt(map.get("simultaneousMobsAddedPerPlayer"),
                    cfg.simultaneousMobsAddedPerPlayer);
            cfg.spawnBatchSize = asInt(map.get("spawnBatchSize"), cfg.spawnBatchSize);
            cfg.minSpawnDelayTicks = asInt(map.get("minSpawnDelayTicks"), cfg.minSpawnDelayTicks);
            cfg.maxSpawnDelayTicks = asInt(map.get("maxSpawnDelayTicks"), cfg.maxSpawnDelayTicks);
            cfg.cooldownTicks = asInt(map.get("cooldownTicks"), cfg.cooldownTicks);
            cfg.scaleWithPlayers = asBool(map.get("scaleWithPlayers"), cfg.scaleWithPlayers);
            cfg.selfDestruct = asBool(map.get("selfDestruct"), cfg.selfDestruct);
            cfg.selfDestructAfterWaves = asInt(map.get("selfDestructAfterWaves"), cfg.selfDestructAfterWaves);
            cfg.selfDestructChancePercent = asInt(map.get("selfDestructChancePercent"),
                    cfg.selfDestructChancePercent);
            cfg.ominousMobMultiplier = asFloat(map.get("ominousMobMultiplier"), cfg.ominousMobMultiplier);
            cfg.ominousEquipment = asBool(map.get("ominousEquipment"), cfg.ominousEquipment);
            cfg.ominousHazards = asBool(map.get("ominousHazards"), cfg.ominousHazards);
            cfg.ominousKeyConversion = asBool(map.get("ominousKeyConversion"), cfg.ominousKeyConversion);
            cfg.ominousHazardIntervalTicks = asInt(map.get("ominousHazardIntervalTicks"),
                    cfg.ominousHazardIntervalTicks);
            cfg.onWaveClearCommands = asString(map.get("onWaveClearCommands"), cfg.onWaveClearCommands);
            cfg.onDestroyCommands = asString(map.get("onDestroyCommands"), cfg.onDestroyCommands);
            cfg.noBreakEnabled = asBool(map.get("noBreakEnabled"), cfg.noBreakEnabled);
            cfg.noBreakRadius = asInt(map.get("noBreakRadius"), cfg.noBreakRadius);
            cfg.noBreakSeconds = asInt(map.get("noBreakSeconds"), cfg.noBreakSeconds);
            cfg.noBreakAmplifier = asInt(map.get("noBreakAmplifier"), cfg.noBreakAmplifier);

            Object rawMobs = map.get("mobs");
            if (rawMobs instanceof List<?> list) {
                List<SpawnEntry> mobs = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> em)) continue;
                    String eid = asString(em.get("entityId"), "");
                    int w = asInt(em.get("weight"), 1);
                    String wr = asString(em.get("weightRange"), "");
                    if (!eid.isEmpty()) mobs.add(new SpawnEntry(eid, w, wr));
                }
                cfg.mobs = mobs;
            }
            Object rawRewards = map.get("rewards");
            if (rawRewards instanceof List<?> list) {
                List<RewardEntry> rewards = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> rm)) continue;
                    String iid = asString(rm.get("itemId"), "");
                    int w = asInt(rm.get("weight"), 1);
                    int minC = asInt(rm.get("minCount"), 1);
                    int maxC = asInt(rm.get("maxCount"), 1);
                    int chance = asInt(rm.get("chancePercent"), 100);
                    String wr = asString(rm.get("weightRange"), "");
                    String cr = asString(rm.get("chanceRange"), "");
                    if (!iid.isEmpty()) rewards.add(new RewardEntry(iid, w, minC, maxC, chance, wr, cr));
                }
                cfg.rewards = rewards;
            }
            Object rawRanges = map.get("ranges");
            if (rawRanges instanceof Map<?, ?> rm) {
                for (Map.Entry<?, ?> en : rm.entrySet()) {
                    if (en.getKey() == null || en.getValue() == null) continue;
                    cfg.ranges.put(String.valueOf(en.getKey()), String.valueOf(en.getValue()));
                }
            }
            return sanitize(cfg);
        } catch (Throwable ignored) {
            return defaults();
        }
    }

    public String toJson() {
        TrialConfig446 c = sanitize(copy());
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        appendStrField(sb, "mode", c.mode, true);
        appendIntField(sb, "activationRange", c.activationRange, false);
        appendIntField(sb, "spawnRange", c.spawnRange, false);
        appendIntField(sb, "totalMobs", c.totalMobs, false);
        appendIntField(sb, "totalMobsAddedPerPlayer", c.totalMobsAddedPerPlayer, false);
        appendIntField(sb, "simultaneousMobs", c.simultaneousMobs, false);
        appendIntField(sb, "simultaneousMobsAddedPerPlayer", c.simultaneousMobsAddedPerPlayer, false);
        appendIntField(sb, "spawnBatchSize", c.spawnBatchSize, false);
        appendIntField(sb, "minSpawnDelayTicks", c.minSpawnDelayTicks, false);
        appendIntField(sb, "maxSpawnDelayTicks", c.maxSpawnDelayTicks, false);
        appendIntField(sb, "cooldownTicks", c.cooldownTicks, false);
        appendBoolField(sb, "scaleWithPlayers", c.scaleWithPlayers, false);
        appendBoolField(sb, "selfDestruct", c.selfDestruct, false);
        appendIntField(sb, "selfDestructAfterWaves", c.selfDestructAfterWaves, false);
        appendIntField(sb, "selfDestructChancePercent", c.selfDestructChancePercent, false);
        appendFloatField(sb, "ominousMobMultiplier", c.ominousMobMultiplier, false);
        appendBoolField(sb, "ominousEquipment", c.ominousEquipment, false);
        appendBoolField(sb, "ominousHazards", c.ominousHazards, false);
        appendBoolField(sb, "ominousKeyConversion", c.ominousKeyConversion, false);
        appendIntField(sb, "ominousHazardIntervalTicks", c.ominousHazardIntervalTicks, false);

        sb.append(",\"mobs\":[");
        for (int i = 0; i < c.mobs.size(); i++) {
            SpawnEntry e = c.mobs.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            appendStrField(sb, "entityId", e.entityId, true);
            appendIntField(sb, "weight", e.weight, false);
            if (e.weightRange != null && !e.weightRange.isBlank()) {
                appendStrField(sb, "weightRange", e.weightRange, false);
            }
            sb.append('}');
        }
        sb.append(']');

        sb.append(",\"rewards\":[");
        for (int i = 0; i < c.rewards.size(); i++) {
            RewardEntry r = c.rewards.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            appendStrField(sb, "itemId", r.itemId, true);
            appendIntField(sb, "weight", r.weight, false);
            appendIntField(sb, "minCount", r.minCount, false);
            appendIntField(sb, "maxCount", r.maxCount, false);
            appendIntField(sb, "chancePercent", r.chancePercent, false);
            if (r.weightRange != null && !r.weightRange.isBlank()) {
                appendStrField(sb, "weightRange", r.weightRange, false);
            }
            if (r.chanceRange != null && !r.chanceRange.isBlank()) {
                appendStrField(sb, "chanceRange", r.chanceRange, false);
            }
            sb.append('}');
        }
        sb.append(']');

        appendStrField(sb, "onWaveClearCommands", c.onWaveClearCommands, false);
        appendStrField(sb, "onDestroyCommands", c.onDestroyCommands, false);
        appendBoolField(sb, "noBreakEnabled", c.noBreakEnabled, false);
        appendIntField(sb, "noBreakRadius", c.noBreakRadius, false);
        appendIntField(sb, "noBreakSeconds", c.noBreakSeconds, false);
        appendIntField(sb, "noBreakAmplifier", c.noBreakAmplifier, false);
        if (!c.ranges.isEmpty()) {
            sb.append(",\"ranges\":{");
            boolean first = true;
            for (Map.Entry<String, String> en : c.ranges.entrySet()) {
                appendStrField(sb, en.getKey(), en.getValue(), first);
                first = false;
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendStrField(StringBuilder sb, String k, String v, boolean first) {
        if (!first) sb.append(',');
        appendQuoted(sb, k);
        sb.append(':');
        appendQuoted(sb, v == null ? "" : v);
    }

    private static void appendIntField(StringBuilder sb, String k, int v, boolean first) {
        if (!first) sb.append(',');
        appendQuoted(sb, k);
        sb.append(':').append(v);
    }

    private static void appendBoolField(StringBuilder sb, String k, boolean v, boolean first) {
        if (!first) sb.append(',');
        appendQuoted(sb, k);
        sb.append(':').append(v);
    }

    private static void appendFloatField(StringBuilder sb, String k, float v, boolean first) {
        if (!first) sb.append(',');
        appendQuoted(sb, k);
        sb.append(':').append(Float.toString(v));
    }

    private static void appendQuoted(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static String asString(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return (int) Math.round(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static float asFloat(Object o, float def) {
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof String s) {
            try {
                return Float.parseFloat(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean b) return b.booleanValue();
        if (o instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(t)) return true;
            if ("false".equals(t)) return false;
        }
        return def;
    }

    /** Minimal strict recursive-descent JSON parser. */
    private static final class JsonParser {
        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWs();
            if (pos >= src.length()) return null;
            char ch = src.charAt(pos);
            if (ch == '{') return parseObject();
            if (ch == '[') return parseArray();
            if (ch == '"') return parseString();
            if (ch == 't' && src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (ch == 'f' && src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (ch == 'n' && src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> out = new LinkedHashMap<>();
            pos++; // '{'
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return out;
            }
            while (pos < src.length()) {
                skipWs();
                if (pos >= src.length() || src.charAt(pos) != '"') break;
                String key = parseString();
                skipWs();
                if (pos >= src.length() || src.charAt(pos) != ':') break;
                pos++;
                Object val = parseValue();
                out.put(key, val);
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                }
                break;
            }
            return out;
        }

        private List<Object> parseArray() {
            List<Object> out = new ArrayList<>();
            pos++; // '['
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return out;
            }
            while (pos < src.length()) {
                Object val = parseValue();
                out.add(val);
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                }
                break;
            }
            return out;
        }

        private String parseString() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char ch = src.charAt(pos++);
                if (ch == '"') return sb.toString();
                if (ch == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 <= src.length()) {
                                String hex = src.substring(pos, pos + 4);
                                pos += 4;
                                try {
                                    sb.append((char) Integer.parseInt(hex, 16));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = pos;
            while (pos < src.length()) {
                char ch = src.charAt(pos);
                if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '+'
                        || ch == '.' || ch == 'e' || ch == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                pos++;
                return 0;
            }
            String num = src.substring(start, pos);
            try {
                if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                    return Double.parseDouble(num);
                }
                long l = Long.parseLong(num);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                return l;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
