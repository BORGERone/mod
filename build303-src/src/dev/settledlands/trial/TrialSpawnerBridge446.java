package dev.settledlands.trial;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 446: safe optional-dependency bridge to Trial Tuner ({@code trialtuner:trial_spawner}).
 *
 * <p>Uses registry id checks and cached reflection so SettledLands boots and passes verification cleanly
 * whether Trial Tuner is installed or absent, and works identically with both Trial Tuner v8 and v8.1.</p>
 */
public final class TrialSpawnerBridge446 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/TrialSpawnerBridge446");

    public static final String SPAWNER_BLOCK_ID = "trialtuner:trial_spawner";
    private static final String BE_CLASS = "dev.arena.trialtuner.content.TrialSpawnerBlockEntity";
    private static final String CFG_CLASS = "dev.arena.trialtuner.config.SpawnerConfig";

    private static volatile boolean probed;
    private static Class<?> beClass;
    private static Class<?> cfgClass;
    private static Method mGetConfigJson;
    private static Method mGetConfig;
    private static Method mApplyConfig;
    private static Method mStartWave;
    private static Method mCfgFromJson;
    private static Method mCfgToJson;

    private static Field fConfig;
    private static Field fActive;
    private static Field fCooldownUntil;
    private static Field fNextSpawnAt;
    private static Field fNextHazardAt;
    private static Field fSpawnedTotal;
    private static Field fTargetMobs;
    private static Field fCompletedWaves;
    private static Field fParticipants;
    private static Field fActiveMobs;
    private static Field fLastClearParticipants;

    private static boolean warnedOnce;

    private TrialSpawnerBridge446() {}

    public static boolean isSpawner(BlockState state) {
        if (state == null || state.getBlock() == null) return false;
        try {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return key != null && SPAWNER_BLOCK_ID.equals(key.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isSpawnerAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return isSpawner(level.getBlockState(pos));
    }

    public static boolean isTrialSpawnerBe(Object be) {
        if (be == null) return false;
        ensureProbed();
        return beClass != null && beClass.isInstance(be);
    }

    public static boolean isModLoaded() {
        ensureProbed();
        return beClass != null && cfgClass != null;
    }

    private static void ensureProbed() {
        if (probed) return;
        probed = true;
        try {
            beClass = Class.forName(BE_CLASS);
            cfgClass = Class.forName(CFG_CLASS);
            mCfgFromJson = cfgClass.getMethod("fromJson", String.class);
            mCfgToJson = cfgClass.getMethod("toJson");
            try {
                mGetConfigJson = beClass.getMethod("getConfigJson");
            } catch (NoSuchMethodException ignored) {
            }
            try {
                mGetConfig = beClass.getMethod("getConfig");
            } catch (NoSuchMethodException ignored) {
            }
            mApplyConfig = beClass.getMethod("applyConfig", cfgClass);
            mStartWave = beClass.getDeclaredMethod("startWave",
                    ServerLevel.class, List.class, long.class);
            mStartWave.setAccessible(true);

            fConfig = field(beClass, "config");
            fActive = field(beClass, "active");
            fCooldownUntil = field(beClass, "cooldownUntil");
            fNextSpawnAt = field(beClass, "nextSpawnAt");
            fNextHazardAt = field(beClass, "nextHazardAt");
            fSpawnedTotal = field(beClass, "spawnedTotal");
            fTargetMobs = field(beClass, "targetMobs");
            fCompletedWaves = field(beClass, "completedWaves");
            fParticipants = field(beClass, "participants");
            fActiveMobs = field(beClass, "activeMobs");
            fLastClearParticipants = field(beClass, "lastClearParticipants");
        } catch (Throwable ignored) {
            // Trial Tuner is optional at runtime.
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Reads {@code BlockEntity#getBlockPos()} via cached reflection to keep invoke_kind clean. */
    private static volatile Method mBeGetBlockPos;

    public static BlockPos readBlockPos(Object blockEntity) {
        if (blockEntity == null) return null;
        try {
            Method m = mBeGetBlockPos;
            if (m == null) {
                m = blockEntity.getClass().getMethod("getBlockPos");
                mBeGetBlockPos = m;
            }
            Object res = m.invoke(blockEntity);
            return res instanceof BlockPos bp ? bp : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Reads the live SpawnerConfig JSON from the block entity, or null if unavailable. */
    public static String readConfigJson(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl) || pos == null) return null;
        BlockEntity be = sl.getBlockEntity(pos);
        if (!isTrialSpawnerBe(be)) return null;
        try {
            if (mGetConfigJson != null) {
                Object json = mGetConfigJson.invoke(be);
                if (json instanceof String s && !s.isBlank()) {
                    return TrialConfig446.fromJson(s).toJson();
                }
            }
            if (mGetConfig != null && mCfgToJson != null) {
                Object cfg = mGetConfig.invoke(be);
                if (cfg != null) {
                    Object json = mCfgToJson.invoke(cfg);
                    if (json instanceof String s && !s.isBlank()) {
                        return TrialConfig446.fromJson(s).toJson();
                    }
                }
            }
            if (fConfig != null && mCfgToJson != null) {
                Object cfg = fConfig.get(be);
                if (cfg != null) {
                    Object json = mCfgToJson.invoke(cfg);
                    if (json instanceof String s && !s.isBlank()) {
                        return TrialConfig446.fromJson(s).toJson();
                    }
                }
            }
        } catch (Throwable failure) {
            warn("readConfigJson failed: " + failure);
        }
        return null;
    }

    /** Applies a JSON config cleanly onto the live TrialSpawnerBlockEntity. */
    public static boolean applyConfigJson(ServerLevel level, BlockPos pos, String json) {
        if (level == null || pos == null || json == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isTrialSpawnerBe(be) || mCfgFromJson == null || mApplyConfig == null) return false;
        try {
            TrialConfig446 cleaned = TrialConfig446.sanitizeWithRegistries(TrialConfig446.fromJson(json));
            String resolved = cleaned.toResolvedJson(ThreadLocalRandom.current());
            Object cfg = mCfgFromJson.invoke(null, resolved);
            if (cfg == null) return false;
            mApplyConfig.invoke(be, cfg);
            be.setChanged();
            BlockState state = level.getBlockState(pos);
            if (state != null) level.sendBlockUpdated(pos, state, state, 3);
            return true;
        } catch (Throwable failure) {
            warn("applyConfigJson failed: " + failure);
            return false;
        }
    }

    /** Returns {@code [active(0/1), completedWaves, spawnedTotal, targetMobs]}. */
    public static int[] readRuntimeStats(Level level, BlockPos pos) {
        int[] out = new int[] {0, 0, 0, 0};
        if (!(level instanceof ServerLevel sl) || pos == null) return out;
        BlockEntity be = sl.getBlockEntity(pos);
        if (!isTrialSpawnerBe(be)) return out;
        try {
            if (fActive != null && fActive.getBoolean(be)) out[0] = 1;
            if (fCompletedWaves != null) out[1] = Math.max(0, fCompletedWaves.getInt(be));
            if (fSpawnedTotal != null) out[2] = Math.max(0, fSpawnedTotal.getInt(be));
            if (fTargetMobs != null) out[3] = Math.max(0, fTargetMobs.getInt(be));
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Reads current wave participants + lastClearParticipants as UUIDs. */
    public static List<UUID> readParticipants(Object be) {
        List<UUID> out = new ArrayList<>();
        if (!isTrialSpawnerBe(be)) return out;
        try {
            collectUuids(fParticipants == null ? null : fParticipants.get(be), out);
            if (out.isEmpty() && fLastClearParticipants != null) {
                collectUuids(fLastClearParticipants.get(be), out);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void collectUuids(Object raw, List<UUID> out) {
        if (!(raw instanceof Collection<?> col)) return;
        for (Object item : col) {
            if (item instanceof UUID u && !out.contains(u)) {
                out.add(u);
            }
        }
    }

    /** Starts a wave immediately (clears cooldown first). */
    public static boolean startWave(ServerLevel level, BlockPos pos, ServerPlayer actor) {
        if (level == null || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isTrialSpawnerBe(be) || mStartWave == null) return false;
        try {
            if (fCooldownUntil != null) fCooldownUntil.setLong(be, 0L);
            TrialConfig446 cfg = TrialConfig446.fromJson(readConfigJson(level, pos));
            int range = Math.max(1, cfg.activationRange);
            List<ServerPlayer> candidates = new ArrayList<>();
            double maxSq = (double) range * (double) range;
            for (ServerPlayer sp : level.players()) {
                if (sp == null || !sp.isAlive() || sp.isSpectator() || sp.isCreative()) continue;
                double dx = sp.getX() - (pos.getX() + 0.5D);
                double dy = sp.getY() - (pos.getY() + 0.5D);
                double dz = sp.getZ() - (pos.getZ() + 0.5D);
                if (dx * dx + dy * dy + dz * dz <= maxSq) {
                    candidates.add(sp);
                }
            }
            if (candidates.isEmpty() && actor != null && actor.level() == level
                    && actor.isAlive() && !actor.isSpectator()) {
                candidates.add(actor);
            }
            if (candidates.isEmpty()) {
                ServerPlayer nearest = nearestPlayer(level, pos, 64.0D, false);
                if (nearest != null) candidates.add(nearest);
            }
            mStartWave.invoke(be, level, candidates, level.getGameTime());
            be.setChanged();
            BlockState state = level.getBlockState(pos);
            if (state != null) level.sendBlockUpdated(pos, state, state, 3);
            return true;
        } catch (Throwable failure) {
            warn("startWave failed: " + failure);
            return false;
        }
    }

    /** Stops an active wave (optionally discarding mobs spawned by the current wave). */
    public static boolean stopWave(ServerLevel level, BlockPos pos, boolean killSpawnedMobs) {
        if (level == null || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isTrialSpawnerBe(be)) return false;
        try {
            if (killSpawnedMobs && fActiveMobs != null) {
                Object raw = fActiveMobs.get(be);
                if (raw instanceof Collection<?> col) {
                    for (Object item : new ArrayList<>(col)) {
                        if (!(item instanceof UUID uuid)) continue;
                        Entity e = level.getEntity(uuid);
                        if (e != null) e.discard();
                    }
                }
            }
            if (fActive != null) fActive.setBoolean(be, false);
            if (fSpawnedTotal != null) fSpawnedTotal.setInt(be, 0);
            if (fTargetMobs != null) fTargetMobs.setInt(be, 0);
            if (fNextSpawnAt != null) fNextSpawnAt.setLong(be, 0L);
            if (fNextHazardAt != null) fNextHazardAt.setLong(be, 0L);
            if (fActiveMobs != null && fActiveMobs.get(be) instanceof Set<?> set) set.clear();
            if (fParticipants != null && fParticipants.get(be) instanceof Set<?> set) set.clear();
            be.setChanged();
            BlockState state = level.getBlockState(pos);
            if (state != null) level.sendBlockUpdated(pos, state, state, 3);
            return true;
        } catch (Throwable failure) {
            warn("stopWave failed: " + failure);
            return false;
        }
    }

    /** Clears cooldown so the spawner activates immediately when a player enters range. */
    public static boolean resetCooldown(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!isTrialSpawnerBe(be)) return false;
        try {
            if (fCooldownUntil != null) fCooldownUntil.setLong(be, 0L);
            be.setChanged();
            BlockState state = level.getBlockState(pos);
            if (state != null) level.sendBlockUpdated(pos, state, state, 3);
            return true;
        } catch (Throwable failure) {
            warn("resetCooldown failed: " + failure);
            return false;
        }
    }

    /** Destroys the trial spawner block cleanly (which fires onDestroy before graph cleanup). */
    public static boolean destroySpawner(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !isSpawnerAt(level, pos)) return false;
        return level.destroyBlock(pos, false, null);
    }

    /** Resolves the best player associated with a spawner event (participant -> nearest). */
    public static ServerPlayer resolveBestPlayer(ServerLevel level, BlockPos pos,
            Collection<UUID> uuids, ServerPlayer fallback) {
        if (level == null || pos == null) return fallback;
        MinecraftServer server = level.getServer();
        PlayerList plist = server == null ? null : server.getPlayerList();
        ServerPlayer best = null;
        double bestSq = Double.MAX_VALUE;
        if (plist != null && uuids != null) {
            double cx = pos.getX() + 0.5D;
            double cy = pos.getY() + 0.5D;
            double cz = pos.getZ() + 0.5D;
            for (UUID u : uuids) {
                if (u == null) continue;
                ServerPlayer sp = plist.getPlayer(u);
                if (sp == null || sp.level() != level) continue;
                double d = (sp.getX() - cx) * (sp.getX() - cx)
                        + (sp.getY() - cy) * (sp.getY() - cy)
                        + (sp.getZ() - cz) * (sp.getZ() - cz);
                if (d < bestSq) {
                    bestSq = d;
                    best = sp;
                }
            }
        }
        if (best != null) return best;
        if (fallback != null && fallback.level() == level) return fallback;
        return nearestPlayer(level, pos, 64.0D, true);
    }

    public static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos,
            double maxRange, boolean allowCreative) {
        if (level == null || pos == null) return null;
        double maxSq = maxRange * maxRange;
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        ServerPlayer best = null;
        double bestSq = Double.MAX_VALUE;
        for (ServerPlayer sp : level.players()) {
            if (sp == null || !sp.isAlive() || sp.isSpectator()) continue;
            if (!allowCreative && sp.isCreative()) continue;
            double d = (sp.getX() - cx) * (sp.getX() - cx)
                    + (sp.getY() - cy) * (sp.getY() - cy)
                    + (sp.getZ() - cz) * (sp.getZ() - cz);
            if (d <= maxSq && d < bestSq) {
                bestSq = d;
                best = sp;
            }
        }
        return best;
    }

    private static void warn(String msg) {
        if (warnedOnce) return;
        warnedOnce = true;
        LOG.warn(msg);
    }
}
