package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 354: everything the "В радиусе" node shows and does, in one pass.
 *
 * <p>Build 352 had one pass for everything at eight ticks, which made two things wrong. The wand
 * highlight is sparks, and a spark lives a few ticks: refreshed twice a second it blinked on and off
 * between every refresh. And the ring was a still picture of the circle, because the same points were
 * put back in the same places. So the pass now has two speeds - the picture runs every
 * {@link #VISUAL_PERIOD} ticks (ten times a second, faster than a spark dies, so nothing blinks and
 * the ring can travel), the radius events stay at {@link #CHECK_PERIOD} ticks (twice a second, as
 * before, because that is the expensive half).</p>
 *
 * <ul>
 *   <li><b>Подсветка</b> - a block that carries a graph sparks on its eight corners while the wand of
 *       NPC settings is in a player's hand, refreshed every picture tick.</li>
 *   <li><b>Кольцо блока</b> - the radius of a "В радиусе" node whose visibility is on, as particles
 *       that travel around the circle: each picture tick the whole ring steps {@link #RING_STEP}
 *       further, and the particles that are still alive sketch the way they came.</li>
 *   <li><b>Кольцо НПС</b> - the same ring around a body. Build 320 drew one from the NPC tick;
 *       {@code NpcRadiusRingMixin354} takes that drawing over so both rings are the same picture and
 *       both travel. The node lookup stays 320's, so a body and a block agree on what a radius node
 *       is.</li>
 *   <li><b>Событие</b> - the node fires with its own rules (target, radius, per-body cooldown); when
 *       it fires the node's action runs and the chain continues from it.</li>
 * </ul>
 *
 * <p>Nothing runs when the store is empty or nobody is online, the block walk stays per dimension,
 * and a failure is reported once and disables the pass for the session instead of taking the server
 * down (build 349 rule).</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class RadiusPass354 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/RadiusPass354");

    /** Ten pictures a second: faster than a spark dies, so the highlight is steady, not blinking. */
    private static final int VISUAL_PERIOD = 2;

    /** The radius event itself stays twice a second - it is the half that asks the world questions. */
    private static final int CHECK_PERIOD = 8;

    /** How far a wand holder sees a block's spark. */
    private static final double HIGHLIGHT_RANGE = 24.0D;

    /** How far a ring is worth sending. */
    private static final double RING_RANGE = 48.0D;

    /** Highlighted blocks per pass - a wall of spark is not a hint. */
    private static final int MAX_HIGHLIGHT = 24;

    /** Points on the ring: thirty degrees apart, enough to read as a circle. */
    private static final int RING_POINTS = 12;

    /** How far the ring travels per picture tick: a full turn in about four seconds. */
    private static final double RING_STEP = Math.PI / 20.0D;

    /** The ring sits just above the block's or the body's feet, not inside it. */
    private static final double RING_LIFT = 0.15D;

    private static final double TAU = Math.PI * 2.0D;

    /** Cooldowns per block and node, keyed by body; pruned so a busy world cannot grow it forever. */
    private static final Map<String, Map<Integer, Map<UUID, Long>>> COOLDOWNS = new HashMap<>();

    private static boolean disabled;
    private static boolean reported;

    private RadiusPass354() {}

    // ---- the node's flag ----------------------------------------------------

    /**
     * A node that carries a radius, and therefore a ring: "В радиусе" (the check node, the one the
     * palette adds under that name) and "Цель" (the radius node build 320 draws for a body).
     */
    public static boolean isRadiusNode(NpcDialogueGraph.Node node) {
        return node != null && (node.type == NpcDialogueGraph.CHECK
                || node.type == NpcDialogueGraph.RADIUS);
    }

    /**
     * Build 365: every node this pass fires. The radius pair came in 354; Время and Таймер joined in
     * 365, together with the five new palette rows of a block. A walk steps over all four.
     */
    public static boolean isEventNode(NpcDialogueGraph.Node node) {
        return isRadiusNode(node) || (node != null && (node.type == NpcDialogueGraph.TIME
                || node.type == NpcDialogueGraph.TIMER));
    }

    /** The radius the node actually fires with, and the one its ring must show. */
    public static int radius(NpcDialogueGraph.Node node) {
        if (node == null) return 1;
        int value = node.type == NpcDialogueGraph.RADIUS ? node.radius : node.checkRadius;
        return Math.max(1, Math.min(64, value));
    }

    /** The visibility toggle: build 320's flag, the one the card box writes. */
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
        long now = overworld.getGameTime();
        boolean visual = now % VISUAL_PERIOD == 0L;
        boolean check = now % CHECK_PERIOD == 0L;
        if (!visual && !check) return;
        try {
            BlockTriggerStore351 store = BlockTriggerStore351.get(server);
            if (store == null || store.size() == 0) return;
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            for (ServerLevel level : server.getAllLevels()) {
                List<BlockTriggerStore351.Entry> blocks =
                        store.ofDimension(BlockTriggerStore351.dimensionOf(level));
                if (blocks.isEmpty()) continue;
                pass(level, blocks, players, now, visual, check);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void pass(ServerLevel level, List<BlockTriggerStore351.Entry> blocks,
            List<ServerPlayer> players, long now, boolean visual, boolean check) {
        List<ServerPlayer> here = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player != null && player.level() == level) here.add(player);
        }
        if (here.isEmpty()) return;
        // Build 365: a scene started by a block is resumed from here too, before the blocks walk on.
        if (check) resumeCutscenes(here);
        int lit = 0;

        for (int i = 0; i < blocks.size(); i++) {
            BlockTriggerStore351.Entry block = blocks.get(i);
            NpcDialogueGraph graph = block.graph;
            if (graph == null) continue;

            if (visual) {
                // One walk over the nearby players answers both questions: is a ring worth sending,
                // and is a wand holder close enough to light this block up.
                boolean near = false;
                boolean wand = false;
                for (int p = 0; p < here.size(); p++) {
                    ServerPlayer player = here.get(p);
                    double distance = block.distanceSqr(player.getX(), player.getY(), player.getZ());
                    if (distance <= RING_RANGE * RING_RANGE) near = true;
                    if (distance <= HIGHLIGHT_RANGE * HIGHLIGHT_RANGE && holdingWand(player)) wand = true;
                    if (near && wand) break;
                }
                if (near && block.ring) rings(level, block, graph, now);
                if (wand && lit < MAX_HIGHLIGHT) {
                    highlight(level, block);
                    lit++;
                }
            }
            if (check && block.check) {
                check(level, block, graph, here, now);
                timeEvents(level, block, graph, here);
                timerEvents(level, block, graph, here);
            }
        }
    }

    /** True when the wand of NPC settings is in either hand - the author's own "show me the blocks". */
    private static boolean holdingWand(ServerPlayer player) {
        if (player == null || NpcContent.wand() == null) return false;
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(NpcContent.wand())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(NpcContent.wand());
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
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    // ---- the rings ----------------------------------------------------------

    /** Every visible radius node of a block graph. */
    private static void rings(ServerLevel level, BlockTriggerStore351.Entry block,
            NpcDialogueGraph graph, long now) {
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!isRadiusNode(node) || !visible(node)) continue;
            ring(level, block.centerX(), block.y, block.centerZ(), radius(node), now);
        }
    }

    /**
     * Every ring of a body: its "В радиусе" node and its "Цель" node, whichever carry the visibility
     * flag. Called by {@code NpcRadiusRingMixin354} in place of build 320's own drawing, which is why
     * a body and a block now show the same travelling ring and a body gets one for either node.
     */
    public static void npcTick(GuideNpc npc) {
        if (disabled || npc == null) return;
        Level level = npc.level();
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (now % VISUAL_PERIOD != 0L) return;
        try {
            NpcProfile profile = npc.profile();
            NpcDialogueGraph graph = profile == null ? null : profile.dialogueGraph;
            if (graph == null || graph.nodes.isEmpty()) return;
            MinecraftServer owner = server.getServer();
            if (owner == null) return;
            List<ServerPlayer> players = owner.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            double x = npc.getX();
            double y = npc.getY();
            double z = npc.getZ();
            boolean near = false;
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer player = players.get(i);
                if (player.level() != level) continue;
                if (player.distanceToSqr(x, y, z) <= RING_RANGE * RING_RANGE) {
                    near = true;
                    break;
                }
            }
            if (!near) return;
            for (NpcDialogueGraph.Node node : graph.nodes.values()) {
                if (!isRadiusNode(node) || !visible(node)) continue;
                ring(server, x, y, z, radius(node), now);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    /**
     * Points that travel around the circle: each picture tick the whole ring steps a little further,
     * and the particles that are still alive draw the trail behind every point.
     */
    private static void ring(ServerLevel level, double centerX, double centerY, double centerZ,
            int radius, long now) {
        int r = Math.max(1, Math.min(64, radius));
        double phase = ((now / VISUAL_PERIOD) * RING_STEP) % TAU;
        double y = centerY + RING_LIFT;
        double step = TAU / RING_POINTS;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = phase + i * step;
            level.sendParticles(ParticleTypes.ENCHANT,
                    centerX + Math.cos(angle) * r, y, centerZ + Math.sin(angle) * r,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
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
            int radius = radius(node);
            // A radius node has no cooldown control of its own in every tab; never let it fire more
            // than once a second, or a body standing in the zone would run the chain non-stop.
            long cooldownTicks = Math.max(1, node.checkCooldown) * 20L;
            Map<UUID, Long> seen = cooldowns(block.key, node.id, now, cooldownTicks);
            Entity matched = findTarget(level, block, kind(node), radius, seen, now, cooldownTicks);
            if (matched == null) continue;
            seen.put(matched.getUUID(), now);

            ServerPlayer source = matched instanceof ServerPlayer player ? player
                    : nearestPlayer(level, block, radius + 32.0D, players);
            BlockTrigger351.fire(source, graph, node,
                    new BlockTalk358.Where(level, block.key, block.x, block.y, block.z));
        }
    }

    /**
     * The kind «Цель» watches. The check node keeps its own field; a radius node carries its kind in
     * {@code radiusTarget}, and anything the palette does not know means "any body" - a zone that
     * fires on nobody is a zone the author cannot see working.
     */
    private static int kind(NpcDialogueGraph.Node node) {
        int value = node.type == NpcDialogueGraph.RADIUS ? node.radiusTarget : node.checkTarget;
        return value >= 0 && value <= NpcDialogueGraph.TARGET_PASSIVE ? value
                : NpcDialogueGraph.TARGET_ANY;
    }

    // ---- build 365: Время, Таймер, Катсцена ----------------------------------

    /** The world day a «Время» node last fired on, per block and node. */
    private static final Map<String, Long> TIME_DAY = new HashMap<>();

    /** When a «Таймер» node last fired, per block and node; {@link #SPENT} means it is done. */
    private static final Map<String, Long> TIMER_AT = new HashMap<>();

    /** A one-shot timer that has fired and must never fire again this session. */
    private static final long SPENT = -1L;

    /** The stock window a time event may fire in, and the stock reach of a scene-less event. */
    private static final long TIME_WINDOW = 400L;

    private static final double EVENT_RANGE = 32.0D;

    /** A scene nobody finishes within this long stops holding its chain. */
    private static final long SCENE_MS = 300_000L;

    /** A scene is never considered finished this soon, or {@code play} would be undone at once. */
    private static final long SCENE_GRACE_MS = 500L;

    private static final List<After> AFTER = new ArrayList<>();

    private static final class After {
        final UUID player;
        final NpcDialogueGraph graph;
        final int next;
        final BlockTalk358.Where where;
        final long at;

        After(UUID player, NpcDialogueGraph graph, int next, BlockTalk358.Where where, long at) {
            this.player = player;
            this.graph = graph;
            this.next = next;
            this.where = where;
            this.at = at;
        }
    }

    /**
     * Build 365: a «Катсцена» node of a block started a scene and the chain waits for it. The player
     * is polled here, and when the playback is over the chain walks on from the node after the scene.
     */
    public static void afterCutscene(ServerPlayer player, NpcDialogueGraph graph, int next,
            BlockTalk358.Where where) {
        if (player == null || graph == null) return;
        synchronized (AFTER) {
            if (AFTER.size() > 64) AFTER.clear();
            AFTER.add(new After(player.getUUID(), graph, next, where, System.currentTimeMillis()));
        }
    }

    private static void resumeCutscenes(List<ServerPlayer> players) {
        synchronized (AFTER) {
            if (AFTER.isEmpty()) return;
            long now = System.currentTimeMillis();
            for (Iterator<After> it = AFTER.iterator(); it.hasNext();) {
                After after = it.next();
                if (now - after.at > SCENE_MS) {
                    it.remove();
                    continue;
                }
                if (now - after.at < SCENE_GRACE_MS) continue;
                ServerPlayer player = null;
                for (int i = 0; i < players.size(); i++) {
                    ServerPlayer candidate = players.get(i);
                    if (candidate != null && candidate.getUUID().equals(after.player)) {
                        player = candidate;
                        break;
                    }
                }
                if (player == null) {
                    it.remove();
                    continue;
                }
                if (CutsceneCamera.isPlaying(player)) continue;
                it.remove();
                if (after.next >= 0) {
                    BlockTrigger351.resume(player, after.graph, after.next, after.where);
                }
            }
        }
    }

    /**
     * Build 365: the «Время» node - the stock rule of {@code GuideDialogue#tickGraphTimeNodes}. A mode
     * picks the hour (0 dawn, 6000 noon, 12000 sunset, 18000 midnight, 4 the author's own ticks); the
     * node fires once a day, inside a 400 tick window after that hour, for the nearest player within
     * 32 blocks.
     */
    private static void timeEvents(ServerLevel level, BlockTriggerStore351.Entry block,
            NpcDialogueGraph graph, List<ServerPlayer> players) {
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long tod = ((dayTime % 24000L) + 24000L) % 24000L;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node.type != NpcDialogueGraph.TIME) continue;
            if (node.next < 0 && node.triggers.isEmpty()
                    && (node.action == null || node.action.isBlank())) {
                continue;
            }
            long start;
            if (node.timeMode == 1) start = 6000L;
            else if (node.timeMode == 2) start = 12000L;
            else if (node.timeMode == 3) start = 18000L;
            else if (node.timeMode == 4) start = Math.max(0L, Math.min(23999L, node.timeTicks));
            else start = 0L;
            if (tod < start || tod >= start + TIME_WINDOW) continue;
            String key = block.key + "#" + node.id;
            Long seen = TIME_DAY.get(key);
            if (seen != null && seen.longValue() == day) continue;
            ServerPlayer source = nearestPlayer(level, block, EVENT_RANGE, players);
            if (source == null || source.isSpectator()) continue;
            TIME_DAY.put(key, day);
            BlockTrigger351.fire(source, graph, node,
                    new BlockTalk358.Where(level, block.key, block.x, block.y, block.z));
        }
    }

    /**
     * Build 365: the «Таймер» node - the stock rule of {@code GuideDialogue#tickGraphTimerNodes}. The
     * interval is in seconds and counts from the moment the node was first seen; a repeating node
     * restarts from the moment it fired, a one-shot node is spent and never fires again.
     */
    private static void timerEvents(ServerLevel level, BlockTriggerStore351.Entry block,
            NpcDialogueGraph graph, List<ServerPlayer> players) {
        long now = level.getGameTime();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node.type != NpcDialogueGraph.TIMER) continue;
            if (node.next < 0 && node.triggers.isEmpty()
                    && (node.action == null || node.action.isBlank())) {
                continue;
            }
            long period = Math.max(1, node.timerInterval) * 20L;
            String key = block.key + "#" + node.id;
            Long last = TIMER_AT.get(key);
            if (last == null) {
                if (TIMER_AT.size() > 4096) TIMER_AT.clear();
                TIMER_AT.put(key, now);
                continue;
            }
            if (last.longValue() == SPENT) continue;
            if (now - last.longValue() < period) continue;
            ServerPlayer source = nearestPlayer(level, block, EVENT_RANGE, players);
            if (source == null || source.isSpectator()) {
                TIMER_AT.put(key, now);
                continue;
            }
            TIMER_AT.put(key, node.timerRepeat ? now : SPENT);
            BlockTrigger351.fire(source, graph, node,
                    new BlockTalk358.Where(level, block.key, block.x, block.y, block.z));
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
            int kind, int radius, Map<UUID, Long> seen, long now, long cooldownTicks) {
        double r = radius;
        AABB box = new AABB(block.x, block.y, block.z, block.x + 1.0D, block.y + 1.0D,
                block.z + 1.0D).inflate(r);

        if (kind == NpcDialogueGraph.TARGET_MONSTER) {
            return first(level, box, r, block, seen, now, cooldownTicks, body -> body instanceof Enemy);
        }
        if (kind == NpcDialogueGraph.TARGET_ANY) {
            return first(level, box, r, block, seen, now, cooldownTicks,
                    body -> !(body instanceof Player player && player.isSpectator()));
        }
        if (kind == NpcDialogueGraph.TARGET_NPC) {
            return first(level, box, r, block, seen, now, cooldownTicks,
                    body -> !(body instanceof Player));
        }
        if (kind == NpcDialogueGraph.TARGET_PASSIVE) {
            return first(level, box, r, block, seen, now, cooldownTicks,
                    body -> !(body instanceof Player) && !(body instanceof Enemy));
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

    private static void fail(Throwable failure) {
        disabled = true;
        if (reported) return;
        reported = true;
        LOG.warn("radius pass switched off for this session: {}", String.valueOf(failure));
    }
}
