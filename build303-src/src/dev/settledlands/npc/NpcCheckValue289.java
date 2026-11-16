package dev.settledlands.npc;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Build 289: what the blue output of an "In Radius" node carries.
 *
 * <p>The node already knows how to find a body in its radius - it does that in
 * {@code GuideDialogue#findCheckTarget} to fire its chain. This class answers the same
 * question for the value wire: given the NPC and the id of a check node, which entity
 * does that node see right now?</p>
 *
 * <p>The rules are the node's own, copied from {@code findCheckTarget} so the wire can
 * never point at something the node itself would not pick:</p>
 *
 * <ul>
 *   <li>the box is the NPC's bounding box inflated by the node radius (1..64 blocks);</li>
 *   <li>{@code Цель проверки} = NPC - any other living NPC, never the node's own owner;</li>
 *   <li>{@code Монстр} - anything that implements {@code Enemy};</li>
 *   <li>{@code Любая сущность} - any living entity except the owner and spectators;</li>
 *   <li>{@code Игрок} - a living, non-spectator player.</li>
 * </ul>
 *
 * <p>Two deliberate differences: the trigger cooldown is ignored (a value wire has to
 * stay connected to a target that is still in the radius, not blink out for the node's
 * cooldown), and the nearest match wins instead of the first one the list happens to
 * return, so a pursuer does not hop between two bodies.</p>
 *
 * <p>A node id in a pursuit action is not always a check node - a pursuit-to-pursuit
 * wire stores one too and copies the resolved text into the action. {@link #isCheckSource}
 * tells the two apart, so the older wire keeps working exactly as before.</p>
 *
 * <p>Cost: one box query per pursuing NPC per 10 ticks, cached per NPC and node - the
 * same order as the check node's own scan and far below a per-tick path search.</p>
 */
public final class NpcCheckValue289 {

    /** Same cap as the node scan uses (Math.max(1, Math.min(64, checkRadius))). */
    public static final int MAX_RADIUS = 64;

    /** How long a scan result is reused; the check node itself scans every 10 ticks. */
    private static final int RETRY_TICKS = 10;

    private static final Map<GuideNpc, Cache> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcCheckValue289() {}

    /** True when this node id is an "In Radius" node of this NPC. Cached like the scan. */
    public static boolean isCheckSource(GuideNpc npc, int nodeId) {
        return lookup(npc, nodeId) != null;
    }

    /**
     * Entity the check node {@code nodeId} of this NPC sees, or null when the node is not
     * a check node, nothing matches, or the body left the radius.
     */
    public static Entity detected(GuideNpc npc, int nodeId) {
        Hit hit = lookup(npc, nodeId);
        if (hit == null || hit.entity == null) return null;
        Entity entity = hit.entity;
        if (!entity.isAlive() || entity.isRemoved() || entity.level() != npc.level()) {
            // The body went away between two scans: rescan now instead of waiting.
            ServerLevel level = level(npc);
            if (level == null) return null;
            Hit fresh = scanAndStore(npc, level, nodeId);
            if (fresh == null || fresh.entity == null) return null;
            entity = fresh.entity;
        }
        return entity;
    }

    private static ServerLevel level(GuideNpc npc) {
        if (npc == null || npc.level() == null || npc.level().isClientSide) return null;
        return npc.level() instanceof ServerLevel server ? server : null;
    }

    /** Cached lookup; null means "this node is not a check node". */
    private static Hit lookup(GuideNpc npc, int nodeId) {
        if (npc == null || nodeId < 0) return null;
        ServerLevel level = level(npc);
        if (level == null) return null;
        long now = level.getGameTime();
        synchronized (CACHE) {
            Cache cache = CACHE.get(npc);
            if (cache == null) {
                cache = new Cache();
                CACHE.put(npc, cache);
            }
            Hit known = cache.hits.get(nodeId);
            if (known != null && now < known.expiresAt) {
                return known.check ? known : null;
            }
        }
        return scanAndStore(npc, level, nodeId);
    }

    private static Hit scanAndStore(GuideNpc npc, ServerLevel level, int nodeId) {
        NpcDialogueGraph.Node node = checkNode(npc, nodeId);
        Entity found = node == null ? null : scan(npc, level, node);
        Hit hit = new Hit(found, node != null, level.getGameTime() + RETRY_TICKS);
        synchronized (CACHE) {
            Cache cache = CACHE.get(npc);
            if (cache == null) {
                cache = new Cache();
                CACHE.put(npc, cache);
            }
            cache.hits.put(nodeId, hit);
        }
        return hit.check ? hit : null;
    }

    /** The node with this id in any of the NPC's three graphs, if it is a check node. */
    private static NpcDialogueGraph.Node checkNode(GuideNpc npc, int nodeId) {
        NpcProfile profile;
        try {
            profile = npc.profile();
        } catch (Throwable ignored) {
            return null;
        }
        if (profile == null) return null;
        NpcDialogueGraph.Node node = find(profile.dialogueGraph, nodeId);
        if (node == null) node = find(profile.walkGraph, nodeId);
        if (node == null) node = find(profile.animGraph, nodeId);
        if (node == null || node.type != NpcDialogueGraph.CHECK) return null;
        return node;
    }

    private static NpcDialogueGraph.Node find(NpcDialogueGraph graph, int nodeId) {
        if (graph == null || graph.nodes == null) return null;
        return graph.get(nodeId);
    }

    private static Entity scan(GuideNpc npc, ServerLevel level, NpcDialogueGraph.Node node) {
        double radius = Math.max(1, Math.min(MAX_RADIUS, node.checkRadius));
        AABB box = npc.getBoundingBox().inflate(radius);
        double limit = radius * radius;

        if (node.checkTarget == NpcDialogueGraph.TARGET_NPC) {
            List<GuideNpc> found = level.getEntitiesOfClass(GuideNpc.class, box,
                    (GuideNpc other) -> other != npc && other.isAlive()
                            && npc.distanceToSqr(other) <= limit);
            return nearest(npc, found);
        }
        if (node.checkTarget == NpcDialogueGraph.TARGET_MONSTER) {
            List<Entity> found = level.getEntitiesOfClass(Entity.class, box,
                    (Entity other) -> other instanceof Enemy && other.isAlive()
                            && npc.distanceToSqr(other) <= limit);
            return nearest(npc, found);
        }
        if (node.checkTarget == NpcDialogueGraph.TARGET_ANY) {
            List<Entity> found = level.getEntitiesOfClass(Entity.class, box,
                    (Entity other) -> other != npc && other.isAlive() && !isSpectator(other)
                            && npc.distanceToSqr(other) <= limit);
            return nearest(npc, found);
        }
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box,
                (ServerPlayer player) -> !player.isSpectator() && player.isAlive()
                        && npc.distanceToSqr(player) <= limit);
        return nearest(npc, players);
    }

    private static boolean isSpectator(Entity entity) {
        return entity instanceof Player player && player.isSpectator();
    }

    private static Entity nearest(GuideNpc npc, List<? extends Entity> found) {
        if (found == null || found.isEmpty()) return null;
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : found) {
            if (entity == null || !entity.isAlive() || entity.isRemoved()) continue;
            double distance = npc.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private static final class Cache {
        final Map<Integer, Hit> hits = new HashMap<>();
    }

    private static final class Hit {
        final Entity entity;
        /** False when the node id is not a check node at all. */
        final boolean check;
        final long expiresAt;

        Hit(Entity entity, boolean check, long expiresAt) {
            this.entity = entity;
            this.check = check;
            this.expiresAt = expiresAt;
        }
    }
}
