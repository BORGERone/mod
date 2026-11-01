package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side graph pursuit command and row-formation controller.
 *
 * <p>Build 280 is the escort controller applied for real:</p>
 *
 * <ul>
 *   <li>the standoff is measured to the target itself and is a node setting
 *       (default 1.6 blocks);</li>
 *   <li>a stop clears the navigation, the move control and the leftover velocity
 *       ({@code NpcEscort#stopMotion});</li>
 *   <li>a path is kept until it is done. Build 279 repathed every 4 ticks while
 *       catching up, and every repath restarts the path, so the NPC never got
 *       moving - this is why it did not accelerate. Now it repaths like the
 *       escort does: when the path is finished, when the target moved 2 blocks
 *       away from the snapshot, or when the progress watchdog gives up;</li>
 *   <li>the escort moves at the catch-up base speed
 *       ({@code GuideNpc#walkingNavigationSpeed(true)}) - Build 302;</li>
 *   <li>catch-up is that same base speed times {@link #CATCH_UP_EXTRA} plus the node's
 *       "catch-up boost", ramped in over the distance above the catch-up threshold,
 *       released at 3 blocks;</li>
 *   <li>when the target is more than 24 blocks away and is not looking at the
 *       NPC, the NPC is moved to a stable spot 8 blocks from it, so it can never
 *       be lost for good;</li>
 *   <li>the node also outputs its target: {@code %pursuit%},
 *       {@code %pursuit_kind%} and {@code %pursuit_uuid%} are resolved in the
 *       text and in the actions of the nodes that run after it.</li>
 * </ul>
 *
 * <p>Settings travel with the node action, so they are saved with the graph and
 * need no extra node field:</p>
 *
 * <pre>trigger:pursuit:start:npc:&lt;target&gt;|&lt;keep&gt;|&lt;catch-up&gt;|&lt;boost&gt;</pre>
 *
 * <p>Every number is optional - actions created by older builds keep the
 * defaults.</p>
 */
public final class NpcPursuit269 {
    public static final int TARGET_NPC = 0;
    public static final int TARGET_PLAYER = 1;

    public static final double DEFAULT_KEEP_DISTANCE = 1.6D;
    public static final double MIN_KEEP_DISTANCE = 1.0D;
    public static final double MAX_KEEP_DISTANCE = 8.0D;
    public static final double DEFAULT_CATCH_UP_DISTANCE = 8.0D;
    public static final double MIN_CATCH_UP_DISTANCE = 2.0D;
    public static final double MAX_CATCH_UP_DISTANCE = 48.0D;
    public static final double DEFAULT_CATCH_UP_BOOST = 1.5D;
    public static final double MIN_CATCH_UP_BOOST = 1.0D;
    public static final double MAX_CATCH_UP_BOOST = 3.0D;

    /**
     * Build 312: the pursuit walks a little faster than the NPC's ordinary walk - 1.15x
     * against the 0.10 walking constant, so 0.115 blocks a tick. Build 311 had it at the
     * catch-up base (1.6x) and that read as a sprint; this is a purposeful walk.
     */
    public static final double PURSUIT_WALK = 1.15D;

    /**
     * Build 312: the catch-up floor, relative to the same walk. The node's own catch-up boost
     * adds {@link #CATCH_UP_BOOST_SHARE} of its excess on top, so the default 1.5 boost gives
     * 1.55x and the maximum 3.0 gives 2.45x. Build 311 reached 3.04x, which is what made the
     * chase look like a teleport between frames.
     */
    public static final double CATCH_UP_EXTRA = 1.25D;

    /** Share of the node's excess catch-up boost that is turned into speed. */
    private static final double CATCH_UP_BOOST_SHARE = 0.6D;

    /** Output of the node, available to the nodes that run after it. */
    public static final String TOKEN_TARGET = "%pursuit%";
    public static final String TOKEN_KIND = "%pursuit_kind%";
    public static final String TOKEN_UUID = "%pursuit_uuid%";

    private static final String TAG_ACTIVE = "PursuitActive269";
    private static final String TAG_KIND = "PursuitKind269";
    private static final String TAG_TARGET = "PursuitTarget269";
    private static final String TAG_KEEP = "PursuitKeep279";
    private static final String TAG_CATCH_UP = "PursuitCatchUp279";
    private static final String TAG_BOOST = "PursuitBoost280";
    /** Build 289: id of the node whose blue output supplies the target, or -1. */
    private static final String TAG_SOURCE = "PursuitSource289";

    /** Separates the target from its numbers; identifiers and names cannot contain it. */
    private static final char PARAM_SEPARATOR = '|';

    /** Build 289: marks the source node id in the parameters, written by the editor. */
    private static final char SOURCE_MARK = '#';

    private static final int TARGET_RETRY_TICKS = 20;
    private static final int FORMATION_REFRESH_TICKS = 10;
    private static final int FORMATION_WIDTH = 3;
    /** Escort release distance (EscortRules.CATCH_UP_END_DISTANCE_SQ). */
    private static final double CATCH_UP_END_DISTANCE_SQ = 9.0D;
    /** Escort repath trigger: the target moved this far since the path was made. */
    private static final double CATCH_UP_TARGET_MOVED_SQ = 4.0D;
    /** Escort progress watchdog: no movement of half a block in five seconds is a failure. */
    private static final int PROGRESS_WINDOW_TICKS = 100;
    private static final double PROGRESS_MIN_SQ = 0.25D;
    /** Escort retry backoff: 20, then 40 ticks - never long enough to look like standing. */
    private static final int RETRY_BASE_TICKS = 20;
    private static final int RETRY_MAX_SHIFT = 1;

    /**
     * Build 312: a walking path is rebuilt only once the target has moved this far from the
     * point the path leads to, and never more often than {@link #REPATH_SUCCESS_TICKS}. Every
     * rebuild throws the current path away, and the mob has to stop, turn and set off again -
     * which is exactly the stutter this constant exists to remove.
     */
    private static final double REPATH_DISTANCE_SQ = 9.0D;

    /** Minimum ticks between two rebuilds of a path that is still being walked. */
    private static final int REPATH_SUCCESS_TICKS = 10;
    /** How far the target has to move before a stopped pursuer starts again. */
    private static final double RESUME_MARGIN = 0.75D;

    /**
     * Build 313: per-tick movement of the target, squared, that counts as walking. A player on
     * foot moves about 0.21 blocks a tick, so this is well below a walk and well above the
     * jitter of a body standing still. While the target walks, the pursuer never counts itself
     * as "arrived" - that was the stop-and-wait look of an escort.
     */
    private static final double TARGET_MOVING_SQ = 0.01D;

    /**
     * Build 313: how far the target has to move after a path ran out before the next one is
     * built. Short, because a follower whose path ended at a stale spot has to set off again
     * at once; the long threshold above is only for paths that are still being walked.
     */
    private static final double FOLLOW_STEP_SQ = 0.25D;
    /** Catch-up relocation: gap, cooldown and the distance the NPC lands at. */
    private static final double RELOCATE_GAP = 24.0D;
    private static final int RELOCATE_COOLDOWN_TICKS = 20;
    private static final double RELOCATE_ARRIVAL_DISTANCE = 8.0D;
    /** Half-angle of the target's view cone; outside it the NPC may be moved. */
    private static final double RELOCATE_HIDDEN_CONE = 70.0D;

    private static final Map<GuideNpc, State> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, TargetCache> TARGET_CACHE = new HashMap<>();
    private static final Map<String, SlotCache> SLOT_CACHE = new HashMap<>();
    /** Last target published by a pursuit node, for the %pursuit% placeholders. */
    private static final Map<UUID, Published> PUBLISHED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcPursuit269() {}

    public static boolean isPursuitAction(String action) {
        if (action == null) return false;
        String command = commandBody(action);
        return command.regionMatches(true, 0, "pursuit:", 0, 8)
                || command.regionMatches(true, 0, "pursuit ", 0, 8);
    }

    /** Called by the graph action hook. Returns true for every recognized pursuit command. */
    public static boolean run(ServerPlayer player, GuideNpc npc, String action) {
        if (!isPursuitAction(action)) return false;
        if (npc == null || npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc
                || npc.level() == null || npc.level().isClientSide) return true;

        String body = commandBody(action).trim();
        String[] parts = body.indexOf(':') >= 0 ? body.split(":", 4) : body.split("\\s+", 4);
        if (parts.length >= 2 && "stop".equalsIgnoreCase(parts[1])) {
            stop(npc);
            return true;
        }
        if (parts.length != 4 || !"start".equalsIgnoreCase(parts[1])) return true;
        int kind;
        if ("npc".equalsIgnoreCase(parts[2])) kind = TARGET_NPC;
        else if ("player".equalsIgnoreCase(parts[2])) kind = TARGET_PLAYER;
        else return true;

        String tail = parts[3].trim();
        double keepDistance = DEFAULT_KEEP_DISTANCE;
        double catchUpDistance = DEFAULT_CATCH_UP_DISTANCE;
        double catchUpBoost = DEFAULT_CATCH_UP_BOOST;
        int sourceNode = -1;
        int separator = tail.indexOf(PARAM_SEPARATOR);
        if (separator >= 0) {
            String[] params = tail.substring(separator + 1).split("\\|");
            tail = tail.substring(0, separator).trim();
            keepDistance = setting(params, 0, DEFAULT_KEEP_DISTANCE, MIN_KEEP_DISTANCE, MAX_KEEP_DISTANCE);
            catchUpDistance = setting(params, 1, DEFAULT_CATCH_UP_DISTANCE,
                    MIN_CATCH_UP_DISTANCE, MAX_CATCH_UP_DISTANCE);
            catchUpBoost = setting(params, 2, DEFAULT_CATCH_UP_BOOST,
                    MIN_CATCH_UP_BOOST, MAX_CATCH_UP_BOOST);
            sourceNode = sourceNode(params);
        }
        if (tail.isEmpty() && sourceNode < 0) return true;
        start(npc, kind, tail, keepDistance, catchUpDistance, catchUpBoost, sourceNode, player);
        return true;
    }

    /**
     * Build 289: the source node is stored by the editor as {@code |#<id>} - the blue
     * output of another node, for example the "In Radius" node. Returns -1 when the
     * node owns its target text itself.
     */
    private static int sourceNode(String[] params) {
        if (params == null) return -1;
        for (String param : params) {
            if (param == null) continue;
            String value = param.trim();
            if (value.length() < 2 || value.charAt(0) != SOURCE_MARK) continue;
            try {
                int id = Integer.parseInt(value.substring(1).trim());
                return id >= 0 ? id : -1;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String commandBody(String action) {
        String result = action == null ? "" : action.trim();
        if (result.regionMatches(true, 0, "trigger:", 0, 8)) result = result.substring(8).trim();
        else if (result.regionMatches(true, 0, "trigger ", 0, 8)) result = result.substring(8).trim();
        return result;
    }

    private static double setting(String[] values, int index, double fallback, double min, double max) {
        if (values == null || index >= values.length) return fallback;
        String raw = values[index];
        if (raw == null) return fallback;
        raw = raw.trim();
        if (raw.isEmpty()) return fallback;
        try {
            return clamp(Double.parseDouble(raw), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return value < min ? min : (value > max ? max : value);
    }

    private static void start(GuideNpc npc, int kind, String target, double keepDistance,
            double catchUpDistance, double catchUpBoost, int sourceNode, ServerPlayer player) {
        Published published = publish(player, kind, target);
        State state = new State(kind, target, keepDistance, catchUpDistance, catchUpBoost,
                sourceNode, NpcDamageValue290.isDamageSource(npc, sourceNode), published);
        synchronized (ACTIVE) {
            ACTIVE.put(npc, state);
        }
        synchronized (TARGET_CACHE) {
            TARGET_CACHE.remove(groupKey(kind, target));
        }
        synchronized (SLOT_CACHE) {
            SLOT_CACHE.clear();
        }
        stopMotion(npc);
        npc.setTarget(null);
    }

    /** Publishes the node output for the %pursuit% placeholders. */
    public static Published publish(ServerPlayer player, int kind, String target) {
        Published published = new Published();
        published.kind = kind == TARGET_PLAYER ? "player" : "npc";
        published.target = target == null ? "" : target;
        if (player != null) {
            PUBLISHED.put(player.getUUID(), published);
        }
        return published;
    }

    /**
     * Replaces the node output placeholders with the target the last pursuit node
     * published for this player: {@code %pursuit%}, {@code %pursuit_kind%} and
     * {@code %pursuit_uuid%}. Unknown players keep the text untouched.
     */
    public static String fillPursuit(String text, Player player) {
        if (text == null || text.isEmpty() || player == null) return text;
        if (text.indexOf("%pursuit") < 0) return text;
        Published published = PUBLISHED.get(player.getUUID());
        if (published == null) return text;
        String uuid = published.uuid == null ? "" : published.uuid.toString();
        return text.replace(TOKEN_UUID, uuid)
                .replace(TOKEN_KIND, published.kind)
                .replace(TOKEN_TARGET, published.target);
    }

    public static void stop(GuideNpc npc) {
        if (npc == null) return;
        State state;
        synchronized (ACTIVE) {
            state = ACTIVE.remove(npc);
        }
        if (state != null) {
            synchronized (TARGET_CACHE) {
                TARGET_CACHE.remove(groupKey(state.kind, state.target));
            }
            synchronized (SLOT_CACHE) {
                SLOT_CACHE.clear();
            }
        }
        stopMotion(npc);
    }

    /** Returns true while pursuit owns this NPC's movement tick, including when waiting for a target. */
    public static boolean tick(GuideNpc npc) {
        if (npc == null || npc.level() == null || npc.level().isClientSide) return false;
        State state;
        synchronized (ACTIVE) {
            state = ACTIVE.get(npc);
        }
        if (state == null) return false;
        if (!npc.isAlive() || npc.isRemoved()) {
            stop(npc);
            return false;
        }
        npc.setTarget(null);
        if (npc.isTalking() || npc.isLeashed()) {
            stopMotion(npc);
            return true;
        }

        Entity target = resolveTarget(npc, state);
        if (target != null) {
            // A fresh answer from the source wins and becomes the one to keep.
            if (state.damageSource) state.latched = target;
        } else if (state.damageSource) {
            // The damage node has already forgotten its attacker: keep chasing the body it
            // named, as long as that body is still here.
            target = latchedBody(npc, state);
        }
        if (target == null || !target.isAlive() || target.isRemoved()
                || target.level() != npc.level()) {
            state.arrived = false;
            stopMotion(npc);
            return true; // retain the command and retry after the target is available
        }
        if (state.published != null) {
            state.published.uuid = target.getUUID();
            if (state.sourceNode >= 0) {
                // The wire carries a body, not a name: report the one that was found.
                state.published.kind = NpcValueSource290.kindOf(target);
                Component name = target.getName();
                state.published.target = name == null ? "" : name.getString();
            }
        }

        double distanceSqr = npc.distanceToSqr(target);
        boolean wasCatchingUp = state.catchingUp;
        // Escort catch-up: engaged above the configured distance, released at 3 blocks.
        state.catchingUp = state.catchingUp
                ? distanceSqr > CATCH_UP_END_DISTANCE_SQ
                : distanceSqr > state.catchUpDistance * state.catchUpDistance;
        if (state.catchingUp != wasCatchingUp) {
            // Build 312: the mode change alone must not brake the body - zeroing the motion
            // here is the other half of the stutter. The speed is re-applied below.
            state.resetPath();
        }

        // Build 313: is the target walking right now? A follower must not stop while it is.
        double seenSqr = state.hasSeen
                ? target.distanceToSqr(state.seenX, state.seenY, state.seenZ)
                : 0.0D;
        state.seenX = target.getX();
        state.seenY = target.getY();
        state.seenZ = target.getZ();
        state.hasSeen = true;
        boolean targetMoving = seenSqr > TARGET_MOVING_SQ;

        double keepSqr = state.keepDistance * state.keepDistance;
        double resumeSqr = (state.keepDistance + RESUME_MARGIN) * (state.keepDistance + RESUME_MARGIN);
        if (state.arrived && distanceSqr < resumeSqr && !targetMoving) {
            stopMotion(npc);
            return true;
        }
        state.arrived = false;
        if (distanceSqr <= keepSqr && !targetMoving) {
            state.arrived = true;
            state.resetPath();
            stopMotion(npc);
            return true;
        }

        PathNavigation navigation = npc.getNavigation();
        if (navigation == null) return true;

        double gap = Math.sqrt(distanceSqr);
        if (state.catchingUp && gap > RELOCATE_GAP
                && npc.tickCount - state.lastRelocateTick >= RELOCATE_COOLDOWN_TICKS
                && behindTarget(target, npc, RELOCATE_HIDDEN_CONE)
                && relocate(npc, target, state)) {
            state.lastRelocateTick = npc.tickCount;
            state.resetPath();
            stopMotion(npc);
            return true;
        }

        // Build 311: GuideNpc#walkingNavigationSpeed carries the auto-AI peace-time damping
        // (x0.55 when the NPC is not fighting), which made a pursuit slower than ordinary
        // walking. Divide it back out to get the NPC's real walking speed.
        double damping = NpcAutoAiEngine255.navigationSpeedFactor(npc);
        if (!(damping > 0.05D) || damping > 1.0D) damping = 1.0D;
        double walk = npc.walkingNavigationSpeed(false) / damping;

        // Build 312: a purposeful walk while closing in (1.15x the ordinary walk) and a clear
        // push while catching up (1.25x plus part of the node's own boost). Build 311 ran at
        // 1.6x to 3.04x, which is what made the movement look too fast.
        double factor = PURSUIT_WALK;
        if (state.catchingUp) {
            double span = Math.max(state.catchUpDistance, 1.0D);
            double ramp = clamp((gap - state.catchUpDistance) / span, 0.0D, 1.0D);
            factor = CATCH_UP_EXTRA + (state.catchUpBoost - 1.0D) * CATCH_UP_BOOST_SHARE * ramp;
        }
        double speed = walk * factor;
        navigation.setSpeedModifier(speed);

        // Escort progress watchdog: a path that does not move the body is a failure.
        if (!state.progressInit) {
            state.progressX = npc.getX();
            state.progressY = npc.getY();
            state.progressZ = npc.getZ();
            state.progressInit = true;
        }
        state.progressTicks++;
        if (state.progressTicks >= PROGRESS_WINDOW_TICKS) {
            if (state.attemptedPath
                    && npc.distanceToSqr(state.progressX, state.progressY, state.progressZ) < PROGRESS_MIN_SQ) {
                state.failures = Math.min(state.failures + 1, 10);
                state.repathTicks = retryTicks(state.failures);
                // Build 312: no stopMotion here. A body that could not move is already still;
                // zeroing the motion as well only added a visible jolt once the path came back.
            }
            state.rememberProgress(npc);
        }
        if (state.repathTicks > 0) state.repathTicks--;

        // Build 312: keep the path that is being walked. It is rebuilt while catching up, once
        // the target left the point it leads to and the cooldown has passed. Rebuilding it on
        // every two blocks of target movement restarted the walk from a standstill, which is
        // what the repeated stop-turn-go stutter was.
        if (!navigation.isDone()) {
            if (!state.catchingUp || state.repathTicks > 0 || !targetMoved(target, state)) {
                return true;
            }
        } else if (state.repathTicks > 0
                || (distanceSqr <= resumeSqr && !movedBy(target, state, FOLLOW_STEP_SQ))) {
            // Build 313: the path ran out. Close to the target and the target has not moved:
            // nothing left to do, stand. Otherwise the next path is built right away - waiting
            // here is what turned a follower into somebody who walks, stops and sets off again,
            // and a body stuck far from a target that stands still now keeps trying.
            return true;
        }

        Vec3 destination = chasePoint(npc, target, state);
        if (navigation.moveTo(destination.x, destination.y, destination.z, 0, speed)) {
            state.attemptedPath = true;
            if (npc.distanceToSqr(state.progressX, state.progressY, state.progressZ) >= 1.0D) {
                state.failures = 0;
            }
            state.repathTicks = REPATH_SUCCESS_TICKS;
            state.snapshotTarget(target);
        } else {
            state.failures = Math.min(state.failures + 1, 10);
            state.repathTicks = retryTicks(state.failures);
        }
        return true;
    }

    private static int retryTicks(int failures) {
        return RETRY_BASE_TICKS << Math.min(Math.max(failures, 0), RETRY_MAX_SHIFT);
    }

    /** True when the target has moved at least {@code sq} from the point the path leads to. */
    private static boolean movedBy(Entity target, State state, double sq) {
        if (!state.hasTargetSnapshot) return true;
        return target.distanceToSqr(state.targetX, state.targetY, state.targetZ) > sq;
    }

    private static boolean targetMoved(Entity target, State state) {
        if (!state.hasTargetSnapshot) return true;
        return target.distanceToSqr(state.targetX, state.targetY, state.targetZ) > REPATH_DISTANCE_SQ;
    }

    /**
     * True when the NPC is outside the target's view cone, i.e. the target is not
     * looking at it. Only the yaw is read - the same data the formation geometry
     * uses - so no new Minecraft API is involved (Build 280 called
     * Entity#hasLineOfSight, which does not exist on Entity and crashed the server
     * the first time a target ran far enough away).
     */
    private static boolean behindTarget(Entity target, GuideNpc npc, double coneDegrees) {
        double dx = npc.getX() - target.getX();
        double dz = npc.getZ() - target.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-3D) return false;
        double yaw = Math.toRadians(target.getYRot());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        double dot = (dx * lookX + dz * lookZ) / horizontal;
        return dot < Math.cos(Math.toRadians(coneDegrees));
    }

    /**
     * Moves the NPC to a stable spot near a target that is not looking at it, so a
     * target that outran the path never leaves the NPC behind for good.
     */
    private static boolean relocate(GuideNpc npc, Entity target, State state) {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        double dx = npc.getX() - target.getX();
        double dz = npc.getZ() - target.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) {
            dx = 0.0D;
            dz = 1.0D;
            horizontal = 1.0D;
        }
        double arrival = Math.max(state.keepDistance, RELOCATE_ARRIVAL_DISTANCE);
        BlockPos base = BlockPos.containing(target.getX() + dx / horizontal * arrival,
                target.getY(), target.getZ() + dz / horizontal * arrival);
        BlockPos spot = npc.findStablePosition(level, base);
        if (spot == null) spot = base;
        if (!level.hasChunkAt(spot)) return false;
        npc.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        return true;
    }

    /**
     * Point this pursuer heads for: on a ring {@code keep} blocks from the target,
     * on the pursuer's own bearing, so it walks straight at the target and stops on
     * the ring instead of having to cross the target to reach a point in front of
     * it. Extra pursuers of the same target take the neighbouring ring slots.
     */
    private static Vec3 chasePoint(GuideNpc npc, Entity target, State state) {
        int slot = formationSlot(npc, state);
        int row = slot / FORMATION_WIDTH;
        int column = slot % FORMATION_WIDTH;
        double radius = state.keepDistance + row * 1.5D;
        double dx = npc.getX() - target.getX();
        double dz = npc.getZ() - target.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double bearing = horizontal < 1.0E-4D
                ? Math.toRadians(target.getYRot())
                : Math.atan2(dz, dx);
        double angle = bearing + (column - 1) * Math.toRadians(30.0D);
        return new Vec3(target.getX() + Math.cos(angle) * radius,
                target.getY(), target.getZ() + Math.sin(angle) * radius);
    }

    /**
     * Stops the NPC the way NpcEscort#stopMotion does: no leftover path, no wanted
     * position in the move control and no residual horizontal velocity. Without
     * this the body keeps sliding into the target for a few ticks after the
     * navigation stops, which is what made the pursuit NPC stand point-blank.
     */
    private static void stopMotion(GuideNpc npc) {
        if (npc == null) return;
        PathNavigation navigation = npc.getNavigation();
        if (navigation != null && !navigation.isDone()) navigation.stop();
        MoveControl moveControl = npc.getMoveControl();
        if (moveControl != null) {
            moveControl.setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 0.0D);
        }
        npc.xxa = 0.0F;
        npc.yya = 0.0F;
        npc.zza = 0.0F;
        Vec3 motion = npc.getDeltaMovement();
        if (motion != null && (motion.x != 0.0D || motion.z != 0.0D)) {
            npc.setDeltaMovement(0.0D, motion.y, 0.0D);
        }
    }

    /**
     * Build 311: the body a damage-driven pursuit keeps after the damage window closed.
     * Cleared as soon as the body is gone, so the retry path is not kept alive by a corpse.
     */
    private static Entity latchedBody(GuideNpc npc, State state) {
        Entity latched = state.latched;
        if (latched == null) return null;
        if (!latched.isAlive() || latched.isRemoved() || latched.level() != npc.level()) {
            state.latched = null;
            return null;
        }
        return latched;
    }

    private static Entity resolveTarget(GuideNpc npc, State state) {
        if (state.sourceNode >= 0 && NpcValueSource290.isSource(npc, state.sourceNode)) {
            // Build 289/290: the target comes from the blue output of another node - either
            // the "In Radius" node (whichever body it sees) or the "Урон" node (whoever hit
            // the NPC last). Null means "nothing there yet" or "nothing hit me"; the
            // command is kept and retried.
            return NpcValueSource290.resolve(npc, state.sourceNode);
        }
        // A pursuit-to-pursuit wire also stores a source id, but it copies the resolved
        // text into the action, so the text path below is what it uses.
        ServerLevel current = (ServerLevel) npc.level();
        MinecraftServer server = current.getServer();
        if (server == null) return null;
        String key = groupKey(state.kind, state.target);
        long now = current.getGameTime();
        synchronized (TARGET_CACHE) {
            TargetCache cached = TARGET_CACHE.get(key);
            if (cached != null && now < cached.retryAt) {
                Entity entity = cached.entity;
                if (entity != null && entity.isAlive() && !entity.isRemoved()) return entity;
                return null;
            }
            Entity found = findTarget(server, current, npc, state);
            TARGET_CACHE.put(key, new TargetCache(found, now + TARGET_RETRY_TICKS));
            return found;
        }
    }

    private static Entity findTarget(MinecraftServer server, ServerLevel current, GuideNpc requester, State state) {
        Entity sameDimension = null;
        Entity otherDimension = null;
        double nearest = Double.MAX_VALUE;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == requester || !matches(entity, state) || !entity.isAlive() || entity.isRemoved()) continue;
                if (entity.level() == current) {
                    double distance = entity.distanceToSqr(requester);
                    if (sameDimension == null || distance < nearest) {
                        sameDimension = entity;
                        nearest = distance;
                    }
                } else if (otherDimension == null) {
                    otherDimension = entity;
                }
            }
        }
        return sameDimension != null ? sameDimension : otherDimension;
    }

    /**
     * Build 288: a corpse is a valid pursuit target. It carries a profile like every
     * other GuideNpc - an identifier can be given to it in the editor - but corpses were
     * filtered out here together with the trigger NPCs, so a pursuit node aimed at a
     * corpse never found anything and the NPC simply stood still. Trigger NPCs stay
     * excluded: they are markers, not bodies. A corpse is also still excluded as a
     * pursuer in {@link #run}.
     */
    private static boolean matches(Entity entity, State state) {
        if (state.kind == TARGET_PLAYER) {
            return entity instanceof ServerPlayer player
                    && player.getName().getString().equalsIgnoreCase(state.target);
        }
        if (!(entity instanceof GuideNpc npc) || npc instanceof GuideTriggerNpc) return false;
        NpcProfile profile = npc.profile();
        return profile != null && profile.identifier != null && profile.identifier.equals(state.target);
    }

    private static int formationSlot(GuideNpc npc, State state) {
        ServerLevel level = (ServerLevel) npc.level();
        String key = groupKey(state.kind, state.target) + "|" + level.dimension().location();
        long now = level.getGameTime();
        synchronized (SLOT_CACHE) {
            SlotCache cache = SLOT_CACHE.get(key);
            if (cache == null || now >= cache.refreshAt) {
                List<GuideNpc> members = new ArrayList<>();
                synchronized (ACTIVE) {
                    for (Map.Entry<GuideNpc, State> entry : ACTIVE.entrySet()) {
                        GuideNpc candidate = entry.getKey();
                        State candidateState = entry.getValue();
                        if (candidate != null && candidateState != null && candidate.isAlive()
                                && candidate.level() == level && candidateState.kind == state.kind
                                && candidateState.target.equals(state.target)) members.add(candidate);
                    }
                }
                members.sort((left, right) -> left.getUUID().toString().compareTo(right.getUUID().toString()));
                Map<UUID, Integer> slots = new HashMap<>();
                for (int i = 0; i < members.size(); i++) slots.put(members.get(i).getUUID(), i);
                cache = new SlotCache(slots, now + FORMATION_REFRESH_TICKS);
                SLOT_CACHE.put(key, cache);
            }
            Integer slot = cache.slots.get(npc.getUUID());
            return slot == null ? 0 : slot;
        }
    }

    /** Pure formation geometry (Build 271 API, unchanged); the controller uses chasePoint. */
    public static Vec3 slotPosition(Entity target, int slot) {
        int safeSlot = Math.max(0, slot);
        int row = safeSlot / FORMATION_WIDTH;
        int column = safeSlot % FORMATION_WIDTH;
        double lateral = (column - 1) * 1.4D;
        double forward = 1.4D + row * 1.5D;
        double yaw = Math.toRadians(target.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        return new Vec3(target.getX() + forwardX * forward + rightX * lateral,
                target.getY(), target.getZ() + forwardZ * forward + rightZ * lateral);
    }

    public static void saveNpcData(GuideNpc npc, CompoundTag guideTag) {
        if (npc == null || guideTag == null) return;
        State state;
        synchronized (ACTIVE) {
            state = ACTIVE.get(npc);
        }
        if (state == null) {
            // CompoundTag.remove has a mapped return type (Tag), so avoid binding to an Object stub.
            // A false marker is sufficient; loadNpcData ignores the stale kind/target fields.
            guideTag.putBoolean(TAG_ACTIVE, false);
            guideTag.putInt(TAG_KIND, 0);
            guideTag.putString(TAG_TARGET, "");
            guideTag.putDouble(TAG_KEEP, DEFAULT_KEEP_DISTANCE);
            guideTag.putDouble(TAG_CATCH_UP, DEFAULT_CATCH_UP_DISTANCE);
            guideTag.putDouble(TAG_BOOST, DEFAULT_CATCH_UP_BOOST);
            guideTag.putInt(TAG_SOURCE, -1);
            return;
        }
        guideTag.putBoolean(TAG_ACTIVE, true);
        guideTag.putInt(TAG_KIND, state.kind);
        guideTag.putString(TAG_TARGET, state.target);
        guideTag.putDouble(TAG_KEEP, state.keepDistance);
        guideTag.putDouble(TAG_CATCH_UP, state.catchUpDistance);
        guideTag.putDouble(TAG_BOOST, state.catchUpBoost);
        guideTag.putInt(TAG_SOURCE, state.sourceNode);
    }

    public static void loadNpcData(GuideNpc npc, CompoundTag rootTag) {
        if (npc == null || rootTag == null) return;
        CompoundTag guideTag = rootTag.contains("Guide", 10) ? rootTag.getCompound("Guide") : rootTag;
        if (!guideTag.getBoolean(TAG_ACTIVE)) {
            synchronized (ACTIVE) {
                ACTIVE.remove(npc);
            }
            synchronized (SLOT_CACHE) {
                SLOT_CACHE.clear();
            }
            return;
        }
        int kind = guideTag.getInt(TAG_KIND);
        String target = guideTag.getString(TAG_TARGET);
        if (target == null) target = "";
        int sourceNode = guideTag.contains(TAG_SOURCE, 3) ? guideTag.getInt(TAG_SOURCE) : -1;
        if (sourceNode < 0 && ((kind != TARGET_NPC && kind != TARGET_PLAYER) || target.isBlank())) return;
        double keepDistance = guideTag.contains(TAG_KEEP, 6)
                ? clamp(guideTag.getDouble(TAG_KEEP), MIN_KEEP_DISTANCE, MAX_KEEP_DISTANCE)
                : DEFAULT_KEEP_DISTANCE;
        double catchUpDistance = guideTag.contains(TAG_CATCH_UP, 6)
                ? clamp(guideTag.getDouble(TAG_CATCH_UP), MIN_CATCH_UP_DISTANCE, MAX_CATCH_UP_DISTANCE)
                : DEFAULT_CATCH_UP_DISTANCE;
        double catchUpBoost = guideTag.contains(TAG_BOOST, 6)
                ? clamp(guideTag.getDouble(TAG_BOOST), MIN_CATCH_UP_BOOST, MAX_CATCH_UP_BOOST)
                : DEFAULT_CATCH_UP_BOOST;
        synchronized (ACTIVE) {
            ACTIVE.put(npc, new State(kind, target, keepDistance, catchUpDistance, catchUpBoost,
                    sourceNode, NpcDamageValue290.isDamageSource(npc, sourceNode), null));
        }
        synchronized (TARGET_CACHE) {
            TARGET_CACHE.remove(groupKey(kind, target));
        }
        synchronized (SLOT_CACHE) {
            SLOT_CACHE.clear();
        }
    }

    private static String groupKey(int kind, String target) {
        return kind + "|" + target;
    }

    /** Value the node outputs to the nodes after it. */
    public static final class Published {
        String kind = "npc";
        String target = "";
        UUID uuid;
    }

    private static final class State {
        final int kind;
        final String target;
        final double keepDistance;
        final double catchUpDistance;
        final double catchUpBoost;
        /** Build 289: node whose blue output supplies the target, or -1 when the text does. */
        final int sourceNode;
        /** Build 311: true when that node is the "Урон" node - its body is latched. */
        final boolean damageSource;
        final Published published;
        /**
         * Build 311: the body this pursuit is after. The damage node only names its attacker
         * for ten seconds; a pursuit outlives that window, so the body found once is kept
         * until it dies or leaves, and the chase no longer freezes mid-way.
         */
        Entity latched;
        boolean catchingUp;
        boolean arrived;
        int failures;
        int repathTicks;
        int progressTicks;
        boolean progressInit;
        boolean attemptedPath;
        double progressX;
        double progressY;
        double progressZ;
        boolean hasTargetSnapshot;
        double targetX;
        double targetY;
        double targetZ;
        int lastRelocateTick;
        /** Build 313: the target's position one tick ago, to tell a walk from a standstill. */
        double seenX;
        double seenY;
        double seenZ;
        boolean hasSeen;

        State(int kind, String target, double keepDistance, double catchUpDistance,
                double catchUpBoost, int sourceNode, boolean damageSource, Published published) {
            this.kind = kind;
            this.target = target;
            this.keepDistance = keepDistance;
            this.catchUpDistance = catchUpDistance;
            this.catchUpBoost = catchUpBoost;
            this.sourceNode = sourceNode;
            this.damageSource = damageSource;
            this.published = published;
        }

        void resetPath() {
            failures = 0;
            repathTicks = 0;
            progressTicks = 0;
            progressInit = false;
            attemptedPath = false;
            hasTargetSnapshot = false;
        }

        void rememberProgress(GuideNpc npc) {
            progressX = npc.getX();
            progressY = npc.getY();
            progressZ = npc.getZ();
            progressTicks = 0;
            attemptedPath = false;
        }

        void snapshotTarget(Entity target) {
            targetX = target.getX();
            targetY = target.getY();
            targetZ = target.getZ();
            hasTargetSnapshot = true;
        }
    }

    private static final class TargetCache {
        final Entity entity;
        final long retryAt;
        TargetCache(Entity entity, long retryAt) { this.entity = entity; this.retryAt = retryAt; }
    }

    private static final class SlotCache {
        final Map<UUID, Integer> slots;
        final long refreshAt;
        SlotCache(Map<UUID, Integer> slots, long refreshAt) {
            this.slots = slots;
            this.refreshAt = refreshAt;
        }
    }
}
