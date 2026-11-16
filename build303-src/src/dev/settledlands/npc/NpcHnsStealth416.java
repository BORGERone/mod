package dev.settledlands.npc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Build 416: Hackers 'n Slashers ({@code hackersandslashers}) stealth compatibility for NPC gaze
 * and the player's Stealth'o Meter eye.
 *
 * <h2>Root causes in Build 415</h2>
 * <ol>
 *   <li>{@code GuideNpc#customServerAiStep()} scanned {@code level().getNearestPlayer(this, 12.0D)}
 *       in 360 degrees through walls and regardless of sneak/light/invisibility, then called
 *       {@code NpcAutoAiEngine255#lookHeadAtPlayer} every tick to rotate {@code yHeadRot} directly
 *       onto the player. That bypassed Hackers 'n Slashers' {@code LookControlMixin} and immediately
 *       dragged {@code yHeadRot} onto a sneaking player behind the NPC, putting the player inside
 *       {@code StealthHelper.isOnFov(npc, player)} and spiking {@code sightLevel} (and the HUD
 *       Stealth'o Meter eye) to 100%.</li>
 *   <li>{@code NpcAutoAiEngine255#tickIdleLookAround(GuideNpc)} likewise checked
 *       {@code level().getNearestPlayer(npc, 12.0D)} without visibility gating, freezing idle head
 *       movement whenever a hidden player approached from behind or behind a wall.</li>
 *   <li>{@code GuideTriggerNpc} (invisible world marker) and {@code GuideCorpseNpc} (corpse/coffin)
 *       both extend {@code GuideNpc -> PathfinderMob -> Mob}, so Hackers 'n Slashers'
 *       {@code StealthManager#mobVisualPerception} and {@code updatePlayerVisibility} treated invisible
 *       triggers and corpses as sighted mobs and opened the player's stealth eye near them.</li>
 * </ol>
 *
 * <p>Scope chosen by the author: gate only automatic NPC head-tracking and Stealth'o Meter perception
 * on non-sighted entities; leave the «В радиусе» ({@code CHECK}) graph node untouched and do not abort
 * an already-running «Преследование» ({@code NpcPursuit269}).</p>
 */
public final class NpcHnsStealth416 {

    /** Stock gaze distance used by {@code GuideNpc#customServerAiStep} (12 blocks). */
    public static final double LOOK_RANGE = 12.0D;
    public static final double LOOK_RANGE_SQR = LOOK_RANGE * LOOK_RANGE;

    /**
     * HnS detection threshold ({@code StealthManager#stealthBehaviorByTargeting} uses 90.0) required
     * before an idle NPC begins snapping its head toward a non-conversing player.
     */
    public static final double ACQUIRE_SIGHT_THRESHOLD = 90.0D;

    /**
     * HnS gaze threshold ({@code LookControlMixin#HnS$shouldCancelLook} cancels below 40.0) used to
     * keep head tracking smooth once the NPC has already spotted the player, until line of sight breaks
     * or alert decays back to hidden.
     */
    public static final double RETAIN_SIGHT_THRESHOLD = 40.0D;

    private static final Map<GuideNpc, UUID> TRACKED_LOOK =
            Collections.synchronizedMap(new WeakHashMap<GuideNpc, UUID>());

    private static volatile boolean reflectionResolved;
    private static volatile boolean hnsPresent;
    private static Field fServerConfig;
    private static Field fEnableStealthSystem;
    private static Method mGetSightLevel;
    private static Method mSetSightLevel;
    private static Method mGetTrackedPlayers;
    private static Method mHasSuspicionEntries;
    private static Method mClearSuspicionEntries;
    private static Method mHasMobTrueVision;

    private NpcHnsStealth416() {}

    private static void resolveReflection() {
        if (reflectionResolved) {
            return;
        }
        synchronized (NpcHnsStealth416.class) {
            if (reflectionResolved) {
                return;
            }
            Field serverCfg = null;
            Field enableStealth = null;
            Method getSight = null;
            Method setSight = null;
            Method trackedPlayers = null;
            Method hasSuspicion = null;
            Method clearSuspicion = null;
            Method trueVision = null;
            boolean present = false;
            try {
                Class<?> entityHelper = Class.forName(
                        "net.dndats.hackersandslashers.utils.helper.EntityHelper");
                getSight = entityHelper.getMethod("getSightLevel", Player.class, Mob.class);
                present = true;
                try {
                    setSight = entityHelper.getMethod(
                            "setSightLevel", Player.class, Mob.class, double.class);
                } catch (Throwable ignored) {
                }
                try {
                    trackedPlayers = entityHelper.getMethod("getTrackedPlayers", Mob.class);
                } catch (Throwable ignored) {
                }
                try {
                    hasSuspicion = entityHelper.getMethod("hasSuspicionEntries", Mob.class);
                    clearSuspicion = entityHelper.getMethod("clearSuspicionEntries", Mob.class);
                } catch (Throwable ignored) {
                    hasSuspicion = null;
                    clearSuspicion = null;
                }
                try {
                    Class<?> datapackHelper = Class.forName(
                            "net.dndats.hackersandslashers.utils.helper.DatapackHelper");
                    trueVision = datapackHelper.getMethod("hasMobTrueVision", Mob.class);
                } catch (Throwable ignored) {
                }
                try {
                    Class<?> mainCls = Class.forName(
                            "net.dndats.hackersandslashers.HackersAndSlashers");
                    serverCfg = mainCls.getField("serverConfig");
                    enableStealth = serverCfg.getType().getField("enable_stealth_system");
                } catch (Throwable ignored) {
                    serverCfg = null;
                    enableStealth = null;
                }
            } catch (Throwable ignored) {
                present = false;
            }
            fServerConfig = serverCfg;
            fEnableStealthSystem = enableStealth;
            mGetSightLevel = getSight;
            mSetSightLevel = setSight;
            mGetTrackedPlayers = trackedPlayers;
            mHasSuspicionEntries = hasSuspicion;
            mClearSuspicionEntries = clearSuspicion;
            mHasMobTrueVision = trueVision;
            hnsPresent = present;
            reflectionResolved = true;
        }
    }

    /** Package/test hook to re-run reflection discovery in offline JVM probes. */
    public static void resetReflectionForTests() {
        synchronized (NpcHnsStealth416.class) {
            reflectionResolved = false;
            hnsPresent = false;
            fServerConfig = null;
            fEnableStealthSystem = null;
            mGetSightLevel = null;
            mSetSightLevel = null;
            mGetTrackedPlayers = null;
            mHasSuspicionEntries = null;
            mClearSuspicionEntries = null;
            mHasMobTrueVision = null;
            TRACKED_LOOK.clear();
        }
    }

    /** True when Hackers 'n Slashers is loaded and its stealth system is enabled. */
    public static boolean isHnsStealthActive() {
        resolveReflection();
        if (!hnsPresent || mGetSightLevel == null) {
            return false;
        }
        Field cfgField = fServerConfig;
        Field flagField = fEnableStealthSystem;
        if (cfgField == null || flagField == null) {
            return true;
        }
        try {
            Object cfg = cfgField.get(null);
            if (cfg == null) {
                return true;
            }
            return flagField.getBoolean(cfg);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Reads {@code EntityHelper.getSightLevel(player, npc)} (0..100), or 100 if unavailable. */
    public static double getSightLevel(Player player, GuideNpc npc) {
        resolveReflection();
        Method m = mGetSightLevel;
        if (m == null || player == null || npc == null) {
            return 100.0D;
        }
        try {
            Object res = m.invoke(null, player, npc);
            if (res instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 100.0D;
    }

    /** Reads {@code DatapackHelper.hasMobTrueVision(npc)}, or false if unavailable. */
    public static boolean hasTrueVision(GuideNpc npc) {
        resolveReflection();
        Method m = mHasMobTrueVision;
        if (m == null || npc == null) {
            return false;
        }
        try {
            Object res = m.invoke(null, npc);
            if (res instanceof Boolean b) {
                return b.booleanValue();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * True for non-perceiving technical subclasses ({@code GuideTriggerNpc}, {@code GuideCorpseNpc}).
     */
    public static boolean isBlindNpc(GuideNpc npc) {
        if (npc == null) {
            return true;
        }
        return npc instanceof GuideTriggerNpc || npc instanceof GuideCorpseNpc;
    }

    private static boolean safeIsBodiless(Player player) {
        try {
            return NpcStatsInteraction.isBodiless(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Build 417: true when {@code player} is currently a bodiless spectator/spirit in SettledLands. */
    public static boolean isPlayerBodiless(Player player) {
        return safeIsBodiless(player);
    }

    /** Build 417: true when {@code npc} is already visually tracking {@code player}'s head position. */
    public static boolean isTrackingPlayer(GuideNpc npc, Player player) {
        if (npc == null || player == null) {
            return false;
        }
        UUID tracked = TRACKED_LOOK.get(npc);
        return tracked != null && tracked.equals(player.getUUID());
    }

    private static boolean safeIsInDialogue(ServerPlayer player) {
        try {
            return GuideDialogue.isInDialogue(player);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Clears any Hackers 'n Slashers sight/suspicion entries accumulated on invisible triggers,
     * corpses/coffins, or player-possessed NPC bodies so they never open the player's Stealth'o Meter.
     */
    public static void clearBlindEntitySight(GuideNpc npc) {
        if (npc == null || !isBlindNpc(npc)) {
            return;
        }
        TRACKED_LOOK.remove(npc);
        Level level = npc.level();
        if (level == null || level.isClientSide) {
            return;
        }
        resolveReflection();
        if (!hnsPresent) {
            return;
        }
        try {
            if (mGetTrackedPlayers != null) {
                Object rawSet = mGetTrackedPlayers.invoke(null, npc);
                if (rawSet instanceof Set<?> tracked && !tracked.isEmpty()) {
                    try {
                        tracked.clear();
                    } catch (UnsupportedOperationException uoe) {
                        clearViaPlayerLoop(level, npc);
                    }
                }
            } else {
                clearViaPlayerLoop(level, npc);
            }
            if (mHasSuspicionEntries != null && mClearSuspicionEntries != null) {
                Object has = mHasSuspicionEntries.invoke(null, npc);
                if (Boolean.TRUE.equals(has)) {
                    mClearSuspicionEntries.invoke(null, npc);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearViaPlayerLoop(Level level, GuideNpc npc) {
        Method setSight = mSetSightLevel;
        if (setSight == null) {
            return;
        }
        List<? extends Player> players = level.players();
        if (players == null || players.isEmpty()) {
            return;
        }
        for (int i = 0, n = players.size(); i < n; i++) {
            Player p = players.get(i);
            if (p != null) {
                try {
                    setSight.invoke(null, p, npc, Double.valueOf(0.0D));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Replacement for {@code NpcAutoAiEngine255.shouldLookAtPlayer(GuideNpc)} inside
     * {@code GuideNpc#customServerAiStep()}.
     */
    public static boolean shouldLookAtPlayer(GuideNpc npc) {
        if (isBlindNpc(npc)) {
            if (npc != null) {
                TRACKED_LOOK.remove(npc);
            }
            return false;
        }
        return NpcAutoAiEngine255.shouldLookAtPlayer(npc);
    }

    /**
     * Decides whether {@code npc} may orient its head toward {@code player} on this tick.
     *
     * @param retain true when {@code npc} was already visually tracking {@code player}
     */
    public static boolean canLookAtPlayer(GuideNpc npc, Player player, boolean retain) {
        if (npc == null || player == null) {
            return false;
        }
        if (isBlindNpc(npc)) {
            return false;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (safeIsBodiless(player)) {
            return false;
        }
        if (npc.distanceToSqr(player) > LOOK_RANGE_SQR) {
            return false;
        }

        // Active dialogue partner: keep facing the player talking to this NPC regardless of light/crouch.
        if (npc.isTalking()) {
            if (!(player instanceof ServerPlayer sp) || safeIsInDialogue(sp)) {
                return true;
            }
        }

        // Active combat target with unobstructed line of sight.
        if (npc.getTarget() == player && npc.hasLineOfSight(player)) {
            return true;
        }

        boolean hnsActive = isHnsStealthActive();
        boolean trueVision = hnsActive && hasTrueVision(npc);

        // Invisible players are not tracked unless the NPC has HnS True Vision.
        if (player.isInvisible() && !trueVision) {
            return false;
        }

        // Never track players through solid walls or closed doors.
        if (!npc.hasLineOfSight(player)) {
            return false;
        }

        // When Hackers 'n Slashers stealth is active, only turn the head once HnS says this NPC
        // actually perceives the player (acquire >= 90.0, retain >= 40.0).
        if (hnsActive && !trueVision) {
            double sight = getSightLevel(player, npc);
            double threshold = retain ? RETAIN_SIGHT_THRESHOLD : ACQUIRE_SIGHT_THRESHOLD;
            if (sight < threshold) {
                return false;
            }
        }

        return true;
    }

    /**
     * Replacement for {@code level.getNearestPlayer(entity, maxDist)} in both
     * {@code GuideNpc#customServerAiStep()} and {@code NpcAutoAiEngine255#tickIdleLookAround(GuideNpc)}.
     */
    public static Player pickVisibleLookPlayer(Level level, Entity self, double maxDist) {
        if (level == null || self == null) {
            return null;
        }
        if (!(self instanceof GuideNpc npc)) {
            return level.getNearestPlayer(self, maxDist);
        }
        if (isBlindNpc(npc)) {
            TRACKED_LOOK.remove(npc);
            return null;
        }
        List<? extends Player> players = level.players();
        if (players == null || players.isEmpty()) {
            TRACKED_LOOK.remove(npc);
            return null;
        }
        UUID prevUuid = TRACKED_LOOK.get(npc);
        double limitSq = maxDist < 0.0D ? Double.MAX_VALUE : maxDist * maxDist;
        Player best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0, n = players.size(); i < n; i++) {
            Player candidate = players.get(i);
            if (candidate == null || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            double distSq = npc.distanceToSqr(candidate);
            if (distSq > limitSq || distSq >= bestDistSq) {
                continue;
            }
            UUID candUuid = candidate.getUUID();
            boolean retain = prevUuid != null && prevUuid.equals(candUuid);
            if (!canLookAtPlayer(npc, candidate, retain)) {
                continue;
            }
            best = candidate;
            bestDistSq = distSq;
        }
        if (best != null) {
            UUID bestUuid = best.getUUID();
            if (bestUuid != null) {
                TRACKED_LOOK.put(npc, bestUuid);
            }
        } else {
            TRACKED_LOOK.remove(npc);
        }
        return best;
    }

    /**
     * Per-tick guard around {@code NpcAutoAiEngine255#lookHeadAtPlayer(GuideNpc, Player)} so the NPC
     * stops turning its head immediately on the tick a tracked player ducks behind a wall or drops into
     * stealth between 10-tick scans.
     *
     * @return true if head rotation was applied, false if tracking was dropped
     */
    public static boolean lookHeadAtVisiblePlayer(GuideNpc npc, Player player) {
        if (!canLookAtPlayer(npc, player, true)) {
            if (npc != null) {
                TRACKED_LOOK.remove(npc);
            }
            return false;
        }
        UUID uuid = player.getUUID();
        if (uuid != null) {
            TRACKED_LOOK.put(npc, uuid);
        }
        NpcAutoAiEngine255.lookHeadAtPlayer(npc, player);
        return true;
    }
}
