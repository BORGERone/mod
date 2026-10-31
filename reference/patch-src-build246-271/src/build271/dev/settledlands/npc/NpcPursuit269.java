package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server-side graph pursuit command and row-formation controller. */
public final class NpcPursuit269 {
    public static final int TARGET_NPC = 0;
    public static final int TARGET_PLAYER = 1;
    private static final String TAG_ACTIVE = "PursuitActive269";
    private static final String TAG_KIND = "PursuitKind269";
    private static final String TAG_TARGET = "PursuitTarget269";
    private static final int TARGET_RETRY_TICKS = 20;
    private static final int FORMATION_REFRESH_TICKS = 10;
    private static final int FORMATION_WIDTH = 3;

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
        String target = parts[3].trim();
        if (target.isEmpty()) return true;
        start(npc, kind, target);
        return true;
    }

    private static String commandBody(String action) {
        String result = action == null ? "" : action.trim();
        if (result.regionMatches(true, 0, "trigger:", 0, 8)) result = result.substring(8).trim();
        else if (result.regionMatches(true, 0, "trigger ", 0, 8)) result = result.substring(8).trim();
        return result;
    }

    private static void start(GuideNpc npc, int kind, String target) {
        State state = new State(kind, target);
        synchronized (ACTIVE) {
            ACTIVE.put(npc, state);
        }
        synchronized (TARGET_CACHE) {
            TARGET_CACHE.remove(groupKey(kind, target));
        }
        synchronized (SLOT_CACHE) {
            SLOT_CACHE.clear();
        }
        if (npc.getNavigation() != null) npc.getNavigation().stop();
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
        if (npc.getNavigation() != null) npc.getNavigation().stop();
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
            if (npc.getNavigation() != null) npc.getNavigation().stop();
            return true;
        }

        Entity target = resolveTarget(npc, state);
        if (target == null || !target.isAlive() || target.isRemoved()
                || target.level() != npc.level()) {
            if (npc.getNavigation() != null) npc.getNavigation().stop();
            return true; // retain the command and retry after the target is available
        }

        int slot = formationSlot(npc, state);
        Vec3 destination = slotPosition(target, slot);
        double dx = npc.getX() - destination.x;
        double dy = npc.getY() - destination.y;
        double dz = npc.getZ() - destination.z;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        if (distanceSqr <= 1.25D * 1.25D) {
            if (npc.getNavigation() != null) npc.getNavigation().stop();
        } else if (npc.getNavigation() != null && npc.tickCount % 10 == 0) {
            npc.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.10D);
        }
        return true;
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

    /** Pure formation geometry used by the movement controller and JVM tests. */
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
            return;
        }
        guideTag.putBoolean(TAG_ACTIVE, true);
        guideTag.putInt(TAG_KIND, state.kind);
        guideTag.putString(TAG_TARGET, state.target);
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
        synchronized (ACTIVE) {
            ACTIVE.put(npc, new State(kind, target));
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
        State(int kind, String target) { this.kind = kind; this.target = target; }
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
