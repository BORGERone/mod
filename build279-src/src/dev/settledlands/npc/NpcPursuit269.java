package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side graph pursuit command and row-formation controller.
 *
 * <p>Build 279 moves the NPC with the escort node's controller instead of the
 * old "walk to the slot and hope" loop:</p>
 *
 * <ul>
 *   <li>the standoff is measured to the target, not to a formation point, and it
 *       is configurable per node (default 1.6 blocks, the request was 1-2);</li>
 *   <li>a stop clears the navigation, the move control and the leftover velocity
 *       (NpcEscort#stopMotion) - Build 278 only called navigation.stop(), so the
 *       last path kept pushing the NPC into the player;</li>
 *   <li>a target that runs away is chased in the escort catch-up mode: the fast
 *       navigation speed (GuideNpc#walkingNavigationSpeed(true)) and a shorter
 *       repath interval, with the EscortRules thresholds (start at the configured
 *       distance, default 8 blocks, release at 3 blocks);</li>
 *   <li>a pursuer heads for a point of its own - on the ring at the standoff, on
 *       its own bearing from the target - instead of at a formation point in
 *       front of the target, which the NPC could only reach by walking into it;
 *       extra pursuers of the same target spread over that ring by slot.</li>
 * </ul>
 *
 * <p>Settings travel with the node action, so they are saved with the graph and
 * need no extra node field:</p>
 *
 * <pre>trigger:pursuit:start:npc:&lt;target&gt;|&lt;keep&gt;|&lt;catch-up&gt;</pre>
 *
 * <p>Both numbers are optional - actions created by older builds keep the
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

    private static final String TAG_ACTIVE = "PursuitActive269";
    private static final String TAG_KIND = "PursuitKind269";
    private static final String TAG_TARGET = "PursuitTarget269";
    private static final String TAG_KEEP = "PursuitKeep279";
    private static final String TAG_CATCH_UP = "PursuitCatchUp279";

    /** Separates the target from its two numbers; identifiers and names cannot contain it. */
    private static final char PARAM_SEPARATOR = '|';

    private static final int TARGET_RETRY_TICKS = 20;
    private static final int FORMATION_REFRESH_TICKS = 10;
    private static final int FORMATION_WIDTH = 3;
    private static final int REPATH_TICKS = 10;
    private static final int CATCH_UP_REPATH_TICKS = 4;
    /** Escort release distance (EscortRules.CATCH_UP_END_DISTANCE_SQ). */
    private static final double CATCH_UP_END_DISTANCE_SQ = 9.0D;
    /** How far the target has to move before a stopped pursuer starts again. */
    private static final double RESUME_MARGIN = 0.75D;

    private static final Map<GuideNpc, State> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, TargetCache> TARGET_CACHE = new HashMap<>();
    private static final Map<String, SlotCache> SLOT_CACHE = new HashMap<>();

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
        int separator = tail.indexOf(PARAM_SEPARATOR);
        if (separator >= 0) {
            String[] params = tail.substring(separator + 1).split("\\|");
            tail = tail.substring(0, separator).trim();
            keepDistance = setting(params, 0, DEFAULT_KEEP_DISTANCE, MIN_KEEP_DISTANCE, MAX_KEEP_DISTANCE);
            catchUpDistance = setting(params, 1, DEFAULT_CATCH_UP_DISTANCE,
                    MIN_CATCH_UP_DISTANCE, MAX_CATCH_UP_DISTANCE);
        }
        if (tail.isEmpty()) return true;
        start(npc, kind, tail, keepDistance, catchUpDistance);
        return true;
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
            double parsed = Double.parseDouble(raw);
            return clamp(parsed, min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return value < min ? min : (value > max ? max : value);
    }

    private static void start(GuideNpc npc, int kind, String target, double keepDistance, double catchUpDistance) {
        State state = new State(kind, target, keepDistance, catchUpDistance);
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
        if (target == null || !target.isAlive() || target.isRemoved()
                || target.level() != npc.level()) {
            state.arrived = false;
            stopMotion(npc);
            return true; // retain the command and retry after the target is available
        }

        double distanceSqr = npc.distanceToSqr(target);
        // Escort catch-up: engaged above the configured distance, released at 3 blocks.
        state.catchingUp = state.catchingUp
                ? distanceSqr > CATCH_UP_END_DISTANCE_SQ
                : distanceSqr > state.catchUpDistance * state.catchUpDistance;

        double keepSqr = state.keepDistance * state.keepDistance;
        double resumeSqr = (state.keepDistance + RESUME_MARGIN) * (state.keepDistance + RESUME_MARGIN);
        if (state.arrived && distanceSqr < resumeSqr) {
            stopMotion(npc);
            return true;
        }
        state.arrived = false;
        if (distanceSqr <= keepSqr) {
            state.arrived = true;
            stopMotion(npc);
            return true;
        }

        PathNavigation navigation = npc.getNavigation();
        if (navigation == null) return true;

        Vec3 destination = chasePoint(npc, target, state);

        double speed = npc.walkingNavigationSpeed(state.catchingUp);
        navigation.setSpeedModifier(speed);
        int interval = state.catchingUp ? CATCH_UP_REPATH_TICKS : REPATH_TICKS;
        if (navigation.isDone() || npc.tickCount % interval == 0) {
            navigation.moveTo(destination.x, destination.y, destination.z, 0, speed);
        }
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

    private static Entity resolveTarget(GuideNpc npc, State state) {
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

    private static boolean matches(Entity entity, State state) {
        if (state.kind == TARGET_PLAYER) {
            return entity instanceof ServerPlayer player
                    && player.getName().getString().equalsIgnoreCase(state.target);
        }
        if (!(entity instanceof GuideNpc npc) || npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc)
            return false;
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
                Map<java.util.UUID, Integer> slots = new HashMap<>();
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
            return;
        }
        guideTag.putBoolean(TAG_ACTIVE, true);
        guideTag.putInt(TAG_KIND, state.kind);
        guideTag.putString(TAG_TARGET, state.target);
        guideTag.putDouble(TAG_KEEP, state.keepDistance);
        guideTag.putDouble(TAG_CATCH_UP, state.catchUpDistance);
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
        if ((kind != TARGET_NPC && kind != TARGET_PLAYER) || target == null || target.isBlank()) return;
        double keepDistance = guideTag.contains(TAG_KEEP, 6)
                ? clamp(guideTag.getDouble(TAG_KEEP), MIN_KEEP_DISTANCE, MAX_KEEP_DISTANCE)
                : DEFAULT_KEEP_DISTANCE;
        double catchUpDistance = guideTag.contains(TAG_CATCH_UP, 6)
                ? clamp(guideTag.getDouble(TAG_CATCH_UP), MIN_CATCH_UP_DISTANCE, MAX_CATCH_UP_DISTANCE)
                : DEFAULT_CATCH_UP_DISTANCE;
        synchronized (ACTIVE) {
            ACTIVE.put(npc, new State(kind, target, keepDistance, catchUpDistance));
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

    private static final class State {
        final int kind;
        final String target;
        final double keepDistance;
        final double catchUpDistance;
        boolean catchingUp;
        boolean arrived;

        State(int kind, String target, double keepDistance, double catchUpDistance) {
            this.kind = kind;
            this.target = target;
            this.keepDistance = keepDistance;
            this.catchUpDistance = catchUpDistance;
        }
    }

    private static final class TargetCache {
        final Entity entity;
        final long retryAt;
        TargetCache(Entity entity, long retryAt) { this.entity = entity; this.retryAt = retryAt; }
    }

    private static final class SlotCache {
        final Map<java.util.UUID, Integer> slots;
        final long refreshAt;
        SlotCache(Map<java.util.UUID, Integer> slots, long refreshAt) {
            this.slots = slots;
            this.refreshAt = refreshAt;
        }
    }
}
