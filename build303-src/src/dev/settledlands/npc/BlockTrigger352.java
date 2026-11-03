package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 352: the periodic half of block graphs - the "В радиусе" node, the radius ring, and the wand
 * highlight.
 *
 * <p>Three jobs, one pass, twice a second ({@link #PERIOD} ticks), and only for the blocks that need
 * it: a graph with a check node that can fire, a check node with its visibility toggle on, and the
 * blocks near a player who holds the wand. A world where none of that is true walks one list and does
 * nothing.</p>
 *
 * <ul>
 *   <li><b>В радиусе</b> - the node's own rules, copied from the NPC tick: the box is the block
 *       inflated by the radius, the target is what the node asks for (player, npc, monster, any), the
 *       cooldown is per body, and when it fires the node's action runs and the chain continues from
 *       it. The command source is the body that matched when it is a player, otherwise the nearest
 *       player within the radius plus a margin - a block has no body of its own to be the source.</li>
 *   <li><b>Видимость</b> - the toggle of build 320, same node flag ({@code Node#timerRepeat}), same
 *       idea: a ring of particles exactly at the radius, drawn around the block, only while a player
 *       is close enough to see it.</li>
 *   <li><b>Подсветка</b> - a block that carries a graph sparks on its edges while a player holds the
 *       wand of NPC settings, so the author can see which blocks are already wired up.</li>
 * </ul>
 *
 * <p>Particles go through the eight-argument {@code sendParticles} the base jar already calls, so the
 * cost is one packet per point per period and nothing is sent when nobody is near. A failure anywhere
 * is reported once and switches the pass off for the session instead of taking the server down (build
 * 349 rule).</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class BlockTrigger352 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/BlockTrigger352");

    /** Twice a second: fast enough to look alive, slow enough to be free. */
    private static final int PERIOD = 8;

    /** How far a wand holder sees a block's spark. */
    private static final double HIGHLIGHT_RANGE = 24.0D;

    /** How far the ring of a "В радиусе" node is worth sending. */
    private static final double RING_RANGE = 48.0D;

    /** Highlighted blocks per player per pass - a wall of spark is not a hint. */
    private static final int MAX_HIGHLIGHT = 48;

    /** Points on the ring: the same density build 320 uses for an NPC's zone. */
    private static final int RING_POINTS = 24;

    /** Cooldowns per block and node, keyed by body; pruned so a busy world cannot grow it forever. */
    private static final Map<String, Map<Integer, Map<UUID, Long>>> COOLDOWNS = new HashMap<>();

    private static boolean disabled;
    private static boolean reported;

    private BlockTrigger352() {}

    // ---- the node's flag ----------------------------------------------------

    /** The "В радиусе" node of either name. */
    public static boolean isRadiusNode(NpcDialogueGraph.Node node) {
        return node != null && (node.type == NpcDialogueGraph.CHECK
                || node.type == NpcDialogueGraph.RADIUS);
    }

    /** The visibility toggle. Build 320 already stores it in the node's {@code timerRepeat}. */
    public static boolean visible(NpcDialogueGraph.Node node) {
        return node != null && node.timerRepeat;
    }

    public static void setVisible(NpcDialogueGraph.Node node, boolean on) {
        if (node != null) node.timerRepeat = on;
    }

    // ---- the tick -----------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (disabled) return;
        MinecraftServer server = event == null ? null : event.getServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        if (overworld.getGameTime() % PERIOD != 0L) return;
        try {
            BlockTriggerStore351 store = BlockTriggerStore351.get(server);
            if (store == null || store.size() == 0) return;
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            for (ServerLevel level : server.getAllLevels()) {
                List<BlockTriggerStore351.Entry> blocks =
                        store.ofDimension(BlockTriggerStore351.dimensionOf(level));
                if (blocks.isEmpty()) continue;
                pass(level, blocks, players);
            }
        } catch (Throwable failure) {
            fail(failure, true);
        }
    }

    private static void pass(ServerLevel level, List<BlockTriggerStore351.Entry> blocks,
            List<ServerPlayer> players) {
        List<ServerPlayer> here = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player != null && player.level() == level) here.add(player);
        }
        if (here.isEmpty()) return;
        long now = level.getGameTime();
        int lit = 0;

        for (int i = 0; i < blocks.size(); i++) {
            BlockTriggerStore351.Entry block = blocks.get(i);
            NpcDialogueGraph graph = block.graph;
            if (graph == null) continue;

            if (block.ring) ring(level, block, graph, here);
            if (block.check) check(level, block, graph, here, now);
            if (lit >= MAX_HIGHLIGHT) continue;
            for (int p = 0; p < here.size(); p++) {
                ServerPlayer player = here.get(p);
                if (holdingWand(player) && block.distanceSqr(player.getX(), player.getY(), player.getZ())
                        <= HIGHLIGHT_RANGE * HIGHLIGHT_RANGE) {
                    highlight(level, block);
                    lit++;
                    break;
                }
            }
        }
    }

    /** True when the wand of NPC settings is in either hand - the author's own "show me the blocks". */
    private static boolean holdingWand(ServerPlayer player) {
        if (player == null || NpcContent.wand() == null) return false;
        return player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(NpcContent.wand())
                || player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).is(NpcContent.wand());
    }

    // ---- the highlight ------------------------------------------------------

    private static void highlight(ServerLevel level, BlockTriggerStore351.Entry block) {
        double x = block.x;
        double y = block.y;
        double z = block.z;
        // Four bottom and four top corners, one spark each: reads as an outline, costs eight points.
        spark(level, x, y + 0.05D, z);
        spark(level, x + 1.0D, y + 0.05D, z);
        spark(level, x, y + 0.05D, z + 1.0D);
        spark(level, x + 1.0D, y + 0.05D, z + 1.0D);
        spark(level, x, y + 1.0D, z);
        spark(level, x + 1.0D, y + 1.0D, z);
        spark(level, x, y + 1.0D, z + 1.0D);
        spark(level, x + 1.0D, y + 1.0D, z + 1.0D);
    }

    private static void spark(ServerLevel level, double x, double y, double z) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, y, z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
    }

    // ---- the ring -----------------------------------------------------------

    private static void ring(ServerLevel level, BlockTriggerStore351.Entry block,
            NpcDialogueGraph graph, List<ServerPlayer> players) {
        boolean near = false;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (block.distanceSqr(player.getX(), player.getY(), player.getZ())
                    <= RING_RANGE * RING_RANGE) {
                near = true;
                break;
            }
        }
        if (!near) return;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!isRadiusNode(node) || !visible(node)) continue;
            int radius = Math.max(1, Math.min(64, node.checkRadius));
            double oy = block.y + 0.15D;
            for (int i = 0; i < RING_POINTS; i++) {
                double angle = (Math.PI * 2.0D * i) / RING_POINTS;
                double x = block.centerX() + Math.cos(angle) * radius;
                double z = block.centerZ() + Math.sin(angle) * radius;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, x, oy, z, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ---- the check node -----------------------------------------------------

    private static void check(ServerLevel level, BlockTriggerStore351.Entry block,
            NpcDialogueGraph graph, List<ServerPlayer> players, long now) {
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!isRadiusNode(node)) continue;
            if (node.next < 0 && node.triggers.isEmpty()
                    && (node.action == null || node.action.isBlank())) {
                continue;
            }
            int radius = Math.max(1, Math.min(64, node.checkRadius));
            long cooldownTicks = Math.max(0, node.checkCooldown) * 20L;
            Map<UUID, Long> seen = cooldowns(block.key, node.id, now, cooldownTicks);
            Entity matched = findTarget(level, block, node, radius, seen, now, cooldownTicks);
            if (matched == null) continue;
            seen.put(matched.getUUID(), now);

            ServerPlayer source = matched instanceof ServerPlayer player ? player
                    : nearestPlayer(level, block, radius + 32.0D, players);
            BlockTrigger351.fire(source, graph, node);
        }
    }

    private static Map<UUID, Long> cooldowns(String key, int nodeId, long now, long cooldownTicks) {
        Map<Integer, Map<UUID, Long>> perNode = COOLDOWNS.get(key);
        if (perNode == null) {
            if (COOLDOWNS.size() > BlockTriggerStore351.MAX_BLOCKS) COOLDOWNS.clear();
            perNode = new HashMap<>();
            COOLDOWNS.put(key, perNode);
        }
        Map<UUID, Long> seen = perNode.get(nodeId);
        if (seen == null) {
            seen = new HashMap<>();
            perNode.put(nodeId, seen);
        }
        if (seen.size() > 64) {
            final long limit = Math.max(cooldownTicks, 200L);
            seen.entrySet().removeIf(entry -> now - entry.getValue() > limit);
        }
        return seen;
    }

    /** The node's own rules, copied from {@code GuideDialogue#findCheckTarget}. */
    private static Entity findTarget(ServerLevel level, BlockTriggerStore351.Entry block,
            NpcDialogueGraph.Node node, int radius, Map<UUID, Long> seen, long now,
            long cooldownTicks) {
        double r = radius;
        AABB box = new AABB(block.x, block.y, block.z, block.x + 1.0D, block.y + 1.0D,
                block.z + 1.0D).inflate(r);

        if (node.checkTarget == NpcDialogueGraph.TARGET_MONSTER) {
            return first(level, box, r, block, seen, now, cooldownTicks, body -> body instanceof Enemy);
        }
        if (node.checkTarget == NpcDialogueGraph.TARGET_ANY) {
            return first(level, box, r, block, seen, now, cooldownTicks,
                    body -> !(body instanceof Player player && player.isSpectator()));
        }
        if (node.checkTarget == NpcDialogueGraph.TARGET_NPC) {
            return first(level, box, r, block, seen, now, cooldownTicks,
                    body -> !(body instanceof Player));
        }
        return first(level, box, r, block, seen, now, cooldownTicks, body -> body instanceof ServerPlayer);
    }

    private static Entity first(ServerLevel level, AABB box, double radius,
            BlockTriggerStore351.Entry block, Map<UUID, Long> seen, long now, long cooldownTicks,
            java.util.function.Predicate<Entity> kind) {
        List<Entity> bodies = level.getEntitiesOfClass(Entity.class, box,
                body -> body != null && body.isAlive() && !body.isRemoved() && kind.test(body)
                        && block.distanceSqr(body.getX(), body.getY(), body.getZ()) <= radius * radius);
        for (int i = 0; i < bodies.size(); i++) {
            Entity body = bodies.get(i);
            Long last = seen.get(body.getUUID());
            if (last != null && now - last < cooldownTicks) continue;
            return body;
        }
        return null;
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockTriggerStore351.Entry block,
            double range, List<ServerPlayer> players) {
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == null || player.level() != level) continue;
            double distance = block.distanceSqr(player.getX(), player.getY(), player.getZ());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static void fail(Throwable failure, boolean fatal) {
        if (fatal) disabled = true;
        if (reported) return;
        reported = true;
        LOG.warn("block graph tick switched off for this session: {}", String.valueOf(failure));
    }
}
