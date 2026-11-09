package dev.settledlands.door;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 368: a guide opens the door it walks into and closes it behind itself.
 *
 * <p>Vanilla mobs do this with an AI goal, which only runs when the mob's own goal selector decides to
 * walk somewhere. A guide is not driven that way: it is walked by the mod itself - a mobility point, an
 * escort, a scene - so the pass below is what opens the guide's doors, on the same server tick the rest
 * of the mod already uses.</p>
 *
 * <h2>Build 404: «доходит до двери, открывает, но дальше не проходит»</h2>
 *
 * <p>The pass now opens the door, and the guide still stopped at it. Two things were wrong, and both are
 * about <b>when</b> a door is open, never about whether the pass runs:</p>
 *
 * <ul>
 *   <li>a door was opened when the guide was already at it ({@value #REACH} blocks). By then the path the
 *       guide is following was planned at a closed door and it <b>ends</b> at the door - the door opening
 *       changes nothing about a path that is already finished. The doors are now opened <b>ahead</b>
 *       ({@value #REACH_AHEAD} blocks, in the direction the navigator is taking the guide,
 *       {@link #openAhead}), so the next leg is planned against an open doorway;</li>
 *   <li>and when a guide does stop at a door it has just opened, it is now walked through it: a short
 *       path to the point just past the doorway, and, if the pathfinder still refuses, the step ordered
 *       straight on the move control - a mob is moved by its move control, not by its opinion. (Build 405
 *       replaced that one step with the latch of {@link #crossStep}, because one order is one order and a
 *       guide that cannot walk needs to be taken through, not asked once.)</li>
 * </ul>
 *
 * <p>The stuck line now says what the navigator thinks ({@link #pathState}): how many nodes the path has,
 * which node is next, where it was taking the guide, and whether the guide is against a block. That is the
 * difference between "the path ended at the door" and "the path goes through and something else holds it",
 * and it is in the log rather than in another guess.</p>
 *
 * <h2>Build 405: «стоит перед открытой дверью, открывает и закрывает её»</h2>
 *
 * <p>The author's report is not "the door does not open" any more. It is a door that opens and shuts
 * over and over while the guide stands in it, and a guide that skips the point afterwards. Read as one
 * thing, that is this pass's own ten-second rule of build 404:</p>
 *
 * <ul>
 *   <li>the guide walks into the doorway <b>while it is open</b> - an open door has no collision, so the
 *       doorway is a normal block to stand in;</li>
 *   <li>ten seconds later the record of the open door went <b>stale</b> and
 *       {@link #closeLeftBehind} closed it - <b>with the guide inside it</b>. A living body inside a solid
 *       door block cannot walk out: every horizontal move is a collision, the game only slowly pushes it
 *       towards the closest free space. So the guide stands in a shut door;</li>
 *   <li>the pass sees a guide standing at a door and opens it again ({@link #openAround}), the guide takes
 *       a step, the record goes stale again ten seconds later, the door shuts on it again - the author's
 *       «открывает и закрывает»; and every attempt counts as a failed step of the guide's own walk, so
 *       after ten of them the engine drops the point - the author's «а потом пропускает точку».</li>
 * </ul>
 *
 * <p>Build 405 removes the cause and adds the two things that make it impossible to come back:</p>
 *
 * <ol>
 *   <li><b>a door is never closed while a guide is at it.</b> The ten-second rule is gone: a door opened
 *       for a guide is closed when no guide is inside {@link #GUIDE_SAFE} any more, and not one tick
 *       earlier, however long that takes. A guide that died in a doorway no longer counts as a guide, so
 *       the door still does not stay open for ever;</li>
 *   <li><b>the door a guide is standing in is held open</b> ({@link #holdOccupied}) - the feet block and
 *       the head block of every guide, every pass. Even a door somebody else shuts between two passes is
 *       open again before the guide can be trapped by it;</li>
 *   <li><b>and a guide that does not walk out of a doorway is walked out of it</b> ({@link #crossStep}).
 *       A latch per guide: the step is ordered again on every pass, in the direction the guide was
 *       going.</li>
 * </ol>
 *
 * <h2>Build 406: «телепортируется между дверьми, то возвращается назад»</h2>
 *
 * <p>The author's video of build 405 shows a patrol guide that walks, stands in a doorway, <b>jumps</b>,
 * walks back, and now and then arrives. The jumps were build 405's own doing: when ordering the step did
 * not move the guide, it moved the body 0.85 blocks.</p>
 *
 * <ul>
 *   <li><b>there is no teleport any more.</b> {@code nudge}, its free-cell test and the {@code isAir} call
 *       that only existed for it are gone from this class. The latch only ever <b>orders</b> a step, and a
 *       step that does not take leaves the guide where it is;</li>
 *   <li><b>the real cause was that the door was opened too late, again.</b> Every build so far opened it
 *       when the guide was already close: 1.9 blocks (403), 4 inside a half-circle (404), 2.5 (405). A leg
 *       of the walk is planned when the leg starts, up to a dozen blocks before the door, and a path
 *       planned at a closed door either goes round the house or ends at the door. That is why it «через
 *       раз и доходит»: it depends on whether the door happened to be open at planning time. The door is
 *       now opened {@value #AHEAD} blocks ahead of the guide, on the line to the point its navigator is
 *       taking it ({@link #openAhead} / {@link #inCorridor});</li>
 *   <li>and such a door is <b>not shut</b> on the next pass just because the guide is still far from it
 *       ({@link #needed}): a door is closed when no guide is at it <b>and</b> it stands on nobody's line
 *       any more. Build 405 would have shut the twelve-block door immediately - it was outside
 *       {@link #GUIDE_SAFE} - and the guide would have walked up to a closed door again.</li>
 * </ul>
 *
 * <p>The latch that remains is the answer to "the door is open and the guide is not moving"; the answer to
 * "the guide arrives half the time" is the twelve blocks.</p>
 *
 * <h2>Build 403: «они не проходят даже через открытые»</h2>
 *
 * <p>The author is right, and the pass of 368 had three holes that each look exactly like "the NPC cannot
 * use a door":</p>
 *
 * <ol>
 *   <li><b>the path finder was never told that a guide may open doors.</b> Build 368 answered the
 *       question on the <b>mob</b> ({@code GuideNpcDoor368Mixin} adds {@code canOpenDoors()}), but in
 *       1.21 that question does not live on a mob at all: {@code Mob} has no {@code canOpenDoors}, and the
 *       flag the path finder reads is {@code NodeEvaluator#canOpenDoors}, reached through
 *       {@code PathNavigation#getNodeEvaluator()}. The method the mixin adds is never called by anything,
 *       so every path a guide computed went <b>round</b> a closed door - or stopped at it -
 *       and the door the pass opened was never on the way. Fixed by
 *       {@link #allowDoorNodes}: the flag is now set on the guide's own node maker, on every pass, so a
 *       closed wooden door is a doorway in the next path it computes;</li>
 *   <li><b>the pass needed the guide to be moving.</b> A guide standing against a closed door does not
 *       move, so {@code moved < 0.05} skipped it every single time: the one mob the pass was written for
 *       was the one mob it never looked at. The movement gate is gone - every guide is looked at, and a
 *       guide with an unfinished path that has not moved is looked at <b>further</b>
 *       ({@value #STUCK_REACH} blocks);</li>
 *   <li><b>a path computed at a closed door stays ended at the closed door.</b> Opening a door does not
 *       extend a path that was computed while it was shut, so the guide stands at the open doorway with a
 *       finished path and goes nowhere. Now, when a guide has stood still with an unfinished path and a
 *       door in reach, the pass opens the door <b>and recomputes the guide's path to the same target</b>
 *       ({@code PathNavigation#recomputePath}) - which is the step that turns "the door is open" into
 *       "the guide is walking through it".</li>
 * </ol>
 *
 * <p>And the pass opens the doors <b>on the guide's path</b>, not only the ones within arm's reach: the
 * next {@value #LOOKAHEAD} nodes are checked, so a door is open before the guide reaches it instead of
 * the tick after it is already pressed against it ({@link #openOnPath}).</p>
 *
 * <p>The door is still closed behind the guide ({@value #CLOSE_RANGE} blocks, and only there), a door a
 * player closed stays closed, and {@link DoorGuard371} no longer
 * slams a locked one while a guide is walking through it.</p>
 *
 * <p>Cost: a list walk of the guides four times a second, one block lookup per closed door candidate,
 * and no block write unless a door is actually in the way.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class NpcDoors368 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/NpcDoors368");

    /**
     * Four times a second. Build 368 looked twice a second and only at guides that had moved; a guide
     * pressed against a door moves a hundredth of a block, and a guide that has to be seen to be helped
     * cannot wait for a fifth of a second.
     */
    private static final int PERIOD = 5;

    /** How close a door has to be to be opened for a guide that is walking. */
    private static final double REACH = 1.9D;

    /**
     * Build 403: how close a door has to be for a guide that is <b>stuck</b> - a guide with an
     * unfinished path that has not moved. It stands in the middle of the block in front of the door, so
     * the door can be up to a block and a half away; this reaches the door diagonally too.
     */
    private static final double STUCK_REACH = 3.0D;

    /** How many nodes of the guide's own path are checked for a door. */
    private static final int LOOKAHEAD = 4;

    /**
     * Build 404: how far ahead a door is opened for a guide that is walking, in the direction the
     * navigator is taking it. A door has to be open <b>before</b> the pathfinder plans the next leg -
     * opening it when the guide is already pressed against it is one plan too late, because the path that
     * ended at the closed door is the path it is following.
     */
    /**
     * Build 406: how far ahead a door is opened for a guide, <b>along the line its navigator is taking
     * it</b>. Build 404 opened at four blocks in a half-circle and build 405 narrowed that to 2.5; both
     * were still too late. The author's video of build 405 shows a patrol guide that walks, then stands in
     * a doorway, then jumps, then walks back - and every second crossing this pass had to carry it, because
     * by the time the door was open the guide had already planned its path at a closed one (or its path
     * ended at the door). A per-leg path is computed when the leg starts, which can be a dozen blocks
     * before the door: so the door is now opened while the guide is still that far away, as long as it
     * stands on the line the guide is walking. Twelve blocks is more than any leg this mod plans.
     */
    private static final double AHEAD = 12.0D;

    /**
     * Build 406: how far off that line a door may stand and still be opened. A doorway is on the line; a
     * door in the wall beside it is not. This replaces the cone of builds 404-405, which opened doors the
     * guide was merely walking past - the author's «открывает и закрывает» had that in it too.
     */
    private static final double CORRIDOR = 1.6D;

    /** Build 404: an open door this close to a guide that is stuck is a doorway it should be walking through. */
    private static final double PUSH_RANGE = 2.0D;

    /**
     * Build 405: how long one attempt to walk a guide out of one doorway may take (7 s). Bounded on
     * purpose: a guide is not to be dragged across the world by a latch that never expires.
     */
    private static final int CROSS_TICKS = 140;

    /** Build 405: how far past the doorway counts as through it. */
    private static final double THROUGH = 0.7D;

    /**
     * Build 405: a door opened by a guide and left behind is closed once no guide is near it - and only
     * then. Build 404 also had a ten-second rule, and that rule is what the author saw: a guide standing
     * inside an open doorway for longer than ten seconds had the door shut <b>on it</b>, and a body inside
     * a solid door block cannot walk out. The clock is gone; the distance is the whole rule now.
     */
    private static final double CLOSE_RANGE = 3.0D;

    /**
     * Build 403: while any guide is inside this radius of a door, nothing closes it - not this pass, not
     * {@link DoorGuard371}. This is the answer to «не проходят даже через открытые»: a door that stands
     * open for a player and is slammed the moment the player turns away is a closed door again for the
     * guide walking up to it, and no amount of opening on approach helps a mob whose path was computed
     * before the door appeared. It is public because the guard reads it.
     */
    public static final double GUIDE_SAFE = 4.0D;

    /** How long a guide has to stand still with an unfinished path before the pass steps in. */
    private static final int STUCK_TICKS = 20;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Watch> WATCH = new HashMap<>();
    private static final Map<UUID, long[]> PUSHED = new HashMap<>();
    private static final Map<UUID, int[]> STUCK = new HashMap<>();
    private static final Map<UUID, Cross> CROSS = new HashMap<>();
    private static final List<Open> OPENED = new ArrayList<>();

    /**
     * Build 404: the last place a guide's navigator was taking it, and how long ago that was. A guide that
     * stops for ever at an open door is one that was walking at a point the door stands between - without
     * remembering that point there is nothing to compare the door with.
     */
    private static final class Watch {
        BlockPos target;
        int age;
    }

    /** One door a guide opened, and when. */
    private static final class Open {
        final ServerLevel level;
        final BlockPos pos;
        long at;

        Open(ServerLevel level, BlockPos pos, long at) {
            this.level = level;
            this.pos = pos;
            this.at = at;
        }
    }

    /**
     * Build 405: one attempt to get one guide out of one doorway.
     *
     * <p>{@code fx}/{@code fz} is a unit vector: the way the guide was going when it walked into the
     * doorway. Every pass either sees the guide move by itself (which is what should happen now that the
     * door is opened a dozen blocks ahead and cannot be shut under it) or orders the next step. The latch
     * ends when the guide is past the doorway, or when {@value #CROSS_TICKS} ticks have passed. Build 405
     * also <b>moved</b> the body here; build 406 never does - the author saw those jumps.</p>
     */
    private static final class Cross {
        final ServerLevel level;
        final BlockPos door;
        final double fx;
        final double fz;
        final long startedAt;
        double lastX;
        double lastZ;
        int steps;

        Cross(ServerLevel level, BlockPos door, double fx, double fz, long startedAt, double x, double z) {
            this.level = level;
            this.door = door;
            this.fx = fx;
            this.fz = fz;
            this.startedAt = startedAt;
            this.lastX = x;
            this.lastZ = z;
        }
    }

    private static boolean failed;
    private static boolean saidAwake;
    private static boolean saidFlag;
    private static int saidOpen;
    private static int saidStuck;
    private static int saidPath;
    private static int saidCross;
    private static int saidThrough;

    private NpcDoors368() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (failed) return;
        MinecraftServer server = event == null ? null : event.getServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null || overworld.getGameTime() % PERIOD != 0L) return;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                walk(level, overworld.getGameTime());
            }
            closeLeftBehind(overworld.getGameTime());
        } catch (Throwable failure) {
            failed = true;
            if (failure != null) {
                LOG.warn("the guide door pass switched off for this session", failure);
            }
        }
    }

    private static void walk(ServerLevel level, long now) {
        Iterable<Entity> bodies = level.getAllEntities();
        if (bodies == null) return;
        List<UUID> seen = new ArrayList<>();
        int guides = 0;
        for (Entity body : bodies) {
            if (!(body instanceof dev.settledlands.npc.GuideNpc npc)) continue;
            UUID id = npc.getUUID();
            if (id == null) continue;
            seen.add(id);
            guides++;
            allowDoorNodes(npc);
            double x = npc.getX();
            double y = npc.getY();
            double z = npc.getZ();
            double[] last = LAST_POS.get(id);
            LAST_POS.put(id, new double[] {x, y, z});
            double moved = last == null
                    ? 1.0D
                    : Math.abs(x - last[0]) + Math.abs(y - last[1]) + Math.abs(z - last[2]);
            boolean walking = moved >= 0.05D;
            boolean pinned = !walking && hasPath(npc);
            int[] stood = STUCK.get(id);
            int still = walking ? 0 : (stood == null ? PERIOD : stood[0] + PERIOD);
            STUCK.put(id, new int[] {still});
            rememberTarget(npc, id);

            // Build 405: the door a guide is standing in is held open before anything else happens. A
            // body inside a shut door block cannot walk out, so this is the one state that must never
            // exist for a single tick.
            BlockPos underfoot = holdOccupied(level, npc, now);

            // Build 405: a latch that is already carrying this guide out of a doorway does the work of
            // this pass. It ends by itself - past the doorway, or after CROSS_TICKS.
            Cross live = CROSS.get(id);
            if (live != null && !crossStep(live, npc, now)) {
                CROSS.remove(id);
                live = null;
            }

            // Build 404: the doors ahead, in the direction the navigator is taking it - so the next path
            // it plans meets an open doorway instead of a wall.
            openAhead(level, npc, now);
            // Build 403: the doors on the guide's own path first - they are the ones it is going through.
            BlockPos onPath = openOnPath(level, npc, now);
            // Build 403: and then the doors around it, without asking whether it has moved. The movement
            // gate of build 368 is what froze every guide that stood still against a closed door.
            BlockPos around = openAround(level, npc, x, y, z, now, pinned ? STUCK_REACH : REACH,
                    walking);
            BlockPos door = onPath != null ? onPath : around;
            if (door == null) door = underfoot;

            BlockPos through = openDoorInReach(level, npc, x, y, z);
            if (through == null) through = underfoot;

            // Build 404: a guide that stops at a door it has just opened is the author's report of that
            // build («доходит до двери, теперь открывает, но дальше открытой двери не проходит»), and
            // the honest reading is that its path ended at the door and the next plan never reaches past
            // it. Build 405 found the second half of that report - «открывает и закрывает» - in this
            // pass's own ten-second rule, and answers both the same way: plan again, then start the latch
            // that walks the guide out of the doorway and keeps walking it until it is past.
            boolean stoodAtDoor = !walking && still >= STUCK_TICKS && still % STUCK_TICKS == 0
                    && (pinned || doorBetween(npc, id, through) || underfoot != null);
            // A guide standing inside a doorway is not waited for: that is the state the door may shut on.
            boolean inDoorway = underfoot != null && !walking;
            // Build 406: and a guide that is merely standing still next to a door it is not walking at is
            // not latched either - that was the other half of "то возвращается назад": a latch pushed it
            // wherever a stale target pointed. Build 405 could start one on `pinned` alone, and a guide
            // turning round at the far end of a patrol is pinned. Now the latch needs the doorway itself
            // (the guide is inside it) or a door that stands on the line to its target.
            boolean crossingMatters = inDoorway || (stoodAtDoor && doorBetween(npc, id, through));
            String push = through == null ? "no open door at hand"
                    : (live != null ? "still crossing" : "already tried");
            if (live == null && through != null && crossingMatters && mayPush(id, through, now)) {
                boolean planned = recompute(npc);
                double[] dir = travelDir(npc, id, through);
                CROSS.put(id, new Cross(level, through, dir[0], dir[1], now, x, z));
                push = planned ? "path replanned and a cross started" : "a cross started";
                if (saidCross < 6) {
                    saidCross++;
                    LOG.info("door: the guide " + id + " is being walked out of the doorway at " + through
                            + " - " + String.format(java.util.Locale.ROOT, "direction %.2f %.2f",
                                    dir[0], dir[1]) + ", door open=" + Doors368.anyOpen(level, through));
                }
            }
            if (crossingMatters || (stoodAtDoor && through != null)) {
                traceStuck(level, npc, x, y, z, door, push);
            }
        }
        // Build 403: one line to say the pass is alive, once there is a guide to look after. A pass that
        // never ran and a pass that ran and found nothing looked identical in every log the author sent.
        if (!saidAwake && guides > 0) {
            saidAwake = true;
            LOG.info("door: the guide door pass is armed - " + guides + " guide(s) in level "
                    + level.dimension() + ", a door is opened for whoever stands at it, moving or not");
        }
        if (LAST_POS.size() > 256) {
            LAST_POS.keySet().removeIf(id -> !seen.contains(id));
            STUCK.keySet().removeIf(id -> !seen.contains(id));
            WATCH.keySet().removeIf(id -> !seen.contains(id));
            PUSHED.keySet().removeIf(id -> !seen.contains(id));
        }
    }

    /**
     * Build 403: tells the guide's node maker that a closed wooden door is a doorway.
     *
     * <p>This is the switch build 368 tried to throw on the mob and could not: in 1.21 {@code Mob} has no
     * {@code canOpenDoors} (see the class javadoc), the flag lives on {@code NodeEvaluator} and the public
     * way to it is {@code PathNavigation#getNodeEvaluator()}. It is set on every pass rather than once,
     * because a navigator that has not been asked for a path yet may hand out a fresh node maker
     * whenever it likes; one virtual call four times a second is nothing.</p>
     */
    private static void allowDoorNodes(dev.settledlands.npc.GuideNpc npc) {
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation == null) return;
            NodeEvaluator evaluator = navigation.getNodeEvaluator();
            if (evaluator == null) return;
            if (evaluator.canOpenDoors()) return;
            evaluator.setCanOpenDoors(true);
            if (!saidFlag) {
                saidFlag = true;
                LOG.info("door: guides may open doors in their own path finding now (the flag lives on "
                        + "the node evaluator in this game version, not on the mob)");
            }
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not allow door nodes for a guide", failure);
            }
        }
    }

    /** True when the guide's navigator still has somewhere to go. */
    private static boolean hasPath(dev.settledlands.npc.GuideNpc npc) {
        try {
            PathNavigation navigation = npc.getNavigation();
            return navigation != null && !navigation.isDone();
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Build 403: opens the doors that sit on the next nodes of the guide's own path.
     *
     * <p>A path that goes through a doorway puts a node on the door block, so this is the exact set of
     * doors the guide has decided to walk through - no guessing by distance, and the door is open before
     * the guide is pressed against it. Returns the door it opened, if any.</p>
     */
    private static BlockPos openOnPath(ServerLevel level, dev.settledlands.npc.GuideNpc npc, long now) {
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation == null || navigation.isDone()) return null;
            Path path = navigation.getPath();
            if (path == null) return null;
            int count = path.getNodeCount();
            if (count <= 0) return null;
            int from = Math.max(0, path.getNextNodeIndex() - 1);
            int to = Math.min(count - 1, path.getNextNodeIndex() + LOOKAHEAD);
            BlockPos opened = null;
            for (int i = from; i <= to; i++) {
                BlockPos node = path.getNodePos(i);
                if (node == null) continue;
                BlockPos hit = openDoorAt(level, node, now, true);
                if (hit != null && opened == null) opened = hit;
            }
            return opened;
        } catch (Throwable failure) {
            return null;
        }
    }

    /** Opens the doors in the three blocks around a guide. */
    private static BlockPos openAround(ServerLevel level, Entity npc, double x, double y, double z,
            long now, double reach, boolean walking) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        BlockPos nearest = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!Doors368.isDoor(level.getBlockState(pos))) continue;
                    double distance = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                            pos.getZ() + 0.5D);
                    if (distance > reach * reach) continue;
                    if (nearest == null) nearest = pos;
                    if (openDoor(level, pos, now, walking)) {
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    /** Build 404: remembers where the guide's navigator was taking it, and how long ago that was. */
    private static void rememberTarget(dev.settledlands.npc.GuideNpc npc, UUID id) {
        try {
            Watch watch = WATCH.get(id);
            if (watch == null) {
                watch = new Watch();
                WATCH.put(id, watch);
            }
            PathNavigation navigation = npc.getNavigation();
            BlockPos target = navigation == null ? null : navigation.getTargetPos();
            if (target != null) {
                watch.target = target;
                watch.age = 0;
            } else if (watch.age < 10000) {
                watch.age += PERIOD;
            }
        } catch (Throwable ignored) {
            // a guide without a navigator is simply a guide this pass knows nothing about
        }
    }

    /**
     * Build 404: does the door stand between the guide and the place it was walking to? That is the test
     * for "it stopped at a door it was trying to pass" - as opposed to "it stopped at a door it lives
     * beside", which nothing should push it through.
     */
    private static boolean doorBetween(dev.settledlands.npc.GuideNpc npc, UUID id, BlockPos door) {
        if (door == null) return false;
        Watch watch = WATCH.get(id);
        if (watch == null || watch.target == null || watch.age > 60) return false;
        double dx = watch.target.getX() + 0.5D - npc.getX();
        double dz = watch.target.getZ() + 0.5D - npc.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) return false;
        double gx = door.getX() + 0.5D - npc.getX();
        double gz = door.getZ() + 0.5D - npc.getZ();
        double along = (gx * dx + gz * dz) / length;             // how far along the way the door is
        if (along < 0.2D || along > length + 1.0D) return false;  // behind the guide, or past the target
        double across = Math.abs(gx * dz - gz * dx) / length;      // how far off that line the door is
        return across <= 1.4D;
    }

    /**
     * Build 404: a guide is walked through a doorway at most once per second and three times per door -
     * enough to get it through, not enough to turn a wrong direction into a fight.
     */
    private static boolean mayPush(UUID id, BlockPos door, long now) {
        long[] last = PUSHED.get(id);
        long key = ((long) door.getX() & 0x3FFFFFFL) << 38
                | ((long) door.getZ() & 0x3FFFFFFL) << 12 | (door.getY() & 0xFFFL);
        if (last != null && last[0] == key) {
            if (now - last[1] < 20L) return false;
            if (last[2] >= 3L && now - last[1] < 200L) return false;
            if (now - last[1] >= 200L) last[2] = 0L;
        }
        long[] next = last != null && last[0] == key ? last : new long[] {key, now, 0L};
        next[1] = now;
        next[2] = next[2] + 1L;
        PUSHED.put(id, next);
        return true;
    }

    /**
     * Build 406: opens the closed doors that stand <b>on the line the guide is walking</b>, up to
     * {@value #AHEAD} blocks ahead.
     *
     * <p>Builds 403-405 all opened a door <b>late</b>: 403 at the guide's arm's length and on the nodes of
     * a path it had already planned, 404 four blocks ahead inside a half-circle, 405 two and a half. The
     * author's video of 405 is what "too late" looks like from the outside - a patrol guide that walks,
     * stops in a doorway, and is then carried through it (or not) depending on whether the door happened
     * to be open when that leg of the walk was planned. That is why it «через раз и доходит».</p>
     *
     * <p>A leg is planned when the leg starts, which can be a dozen blocks before the door, so the door has
     * to be open a dozen blocks before the door. The line is the navigator's own target
     * ({@code PathNavigation#getTargetPos}), and a door counts as "on it" at up to {@value #CORRIDOR}
     * blocks to the side - a doorway is on the line, the door in the wall beside it is not. Nothing is
     * opened behind the guide, nothing past its target, and nothing off the line: a door that opens and
     * shuts as a guide walks past was half of the author's complaint in build 405.</p>
     *
     * <p>The walk is sampled along the line instead of scanning a cube, so the cost of a twelve-block
     * look-ahead is about seventy block lookups, not three thousand.</p>
     */
    private static void openAhead(ServerLevel level, dev.settledlands.npc.GuideNpc npc, long now) {
        try {
            PathNavigation navigation = npc.getNavigation();
            BlockPos target = navigation == null ? null : navigation.getTargetPos();
            if (target == null) return;
            double x = npc.getX();
            double y = npc.getY();
            double z = npc.getZ();
            double tx = target.getX() + 0.5D - x;
            double tz = target.getZ() + 0.5D - z;
            double length = Math.sqrt(tx * tx + tz * tz);
            if (length < 1.0D) return;
            double reach = Math.min(length, AHEAD);
            int by = (int) Math.floor(y);
            for (double step = 0.5D; step <= reach; step += 0.5D) {
                int bx = (int) Math.floor(x + tx / length * step);
                int bz = (int) Math.floor(z + tz / length * step);
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = new BlockPos(bx, by + dy, bz);
                    BlockState state = level.getBlockState(pos);
                    if (state == null || !Doors368.isDoor(state)) continue;
                    if (Doors368.isOpen(state)) continue;
                    if (!inCorridor(x, z, target, pos)) continue;
                    openDoor(level, pos, now, true);
                }
            }
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not open the doors ahead of a guide", failure);
            }
        }
    }

    /**
     * Build 406: does this door stand on the straight line from the guide to the point its navigator is
     * taking it? The same reading as {@link #doorBetween}, without the remembered target: this one is asked
     * of the <b>live</b> target, because the door has to be open before that target is planned at all.
     */
    private static boolean inCorridor(double x, double z, BlockPos target, BlockPos door) {
        if (target == null || door == null) return false;
        double dx = target.getX() + 0.5D - x;
        double dz = target.getZ() + 0.5D - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) return false;
        double gx = door.getX() + 0.5D - x;
        double gz = door.getZ() + 0.5D - z;
        double along = (gx * dx + gz * dz) / length;
        if (along < -1.0D || along > length + 1.0D) return false;
        double across = Math.abs(gx * dz - gz * dx) / length;
        return across <= CORRIDOR;
    }

    /**
     * Build 406: is this door still <b>somebody's</b> doorway - a guide within arm's length of it, or a
     * guide walking at a point with this door on the line? A door opened twelve blocks ahead is outside
     * {@link #GUIDE_SAFE} for a while, and build 405 would have shut it again in the very next pass, which
     * is the "late again" the author keeps seeing. This is the question {@link #closeLeftBehind} asks
     * instead of "how long has it been open".
     */
    private static boolean needed(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        try {
            Iterable<Entity> bodies = level.getAllEntities();
            if (bodies == null) return false;
            // Build 406: the radius has to cover the twelve blocks a door is opened from, or this method
            // would answer "nobody needs it" for the very door openAhead just opened - and it would be shut
            // on the next pass, which is the build-403 bug with extra steps.
            double wide = AHEAD + 2.0D;
            for (Entity body : bodies) {
                if (!(body instanceof dev.settledlands.npc.GuideNpc npc)) continue;
                double distance = body.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D);
                if (distance > wide * wide) continue;
                if (distance <= CLOSE_RANGE * CLOSE_RANGE) return true;
                PathNavigation navigation = npc.getNavigation();
                BlockPos target = navigation == null ? null : navigation.getTargetPos();
                if (inCorridor(npc.getX(), npc.getZ(), target, pos)) return true;
            }
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not ask whether a door is still needed", failure);
            }
        }
        return false;
    }

    /** The open door a stuck guide is standing at, if any. */
    private static BlockPos openDoorInReach(ServerLevel level, Entity npc, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!Doors368.isDoor(level.getBlockState(pos))) continue;
                    if (!Doors368.isOpen(level.getBlockState(pos))) continue;
                    if (npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                            > PUSH_RANGE * PUSH_RANGE) {
                        continue;
                    }
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * Build 405: the door a guide is standing <b>in</b>, opened if it is shut, and kept in the ledger so
     * nothing shuts it while the guide is there.
     *
     * <p>This is the state the author's report is made of: a body whose feet block is a door block. An
     * open door has no collision, so a guide walking through a doorway spends a few ticks with its feet
     * inside the door block - and if the door shuts in that moment, the body is inside a solid block, every
     * horizontal move is a collision, and the game can only push it out along the shortest axis. The guide
     * stands there, the pass opens the door, the guide takes half a step, and it happens again. Opening
     * the door under the guide on every pass ends that picture before it can form.</p>
     */
    private static BlockPos holdOccupied(ServerLevel level, dev.settledlands.npc.GuideNpc npc, long now) {
        if (level == null || npc == null) return null;
        try {
            int bx = (int) Math.floor(npc.getX());
            int by = (int) Math.floor(npc.getY());
            int bz = (int) Math.floor(npc.getZ());
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(bx, by + dy, bz);
                BlockState state = level.getBlockState(pos);
                if (state == null || !Doors368.isDoor(state)) continue;
                if (Doors368.isOpen(state)) {
                    note(level, pos, now);
                    return pos;
                }
                if (Doors368.setOpen(level, pos, true)) {
                    note(level, pos, now);
                    if (saidThrough < 4) {
                        saidThrough++;
                        LOG.info("door: the door at " + pos + " was opened under a guide - a body may not "
                                + "be left inside a shut door");
                    }
                }
                return pos;
            }
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not hold the door a guide is standing in", failure);
            }
        }
        return null;
    }

    /**
     * Build 405: remembers that this door is open on a guide's account, refreshing the record instead of
     * piling up a second one for the same door.
     */
    private static void note(ServerLevel level, BlockPos pos, long now) {
        if (level == null || pos == null) return;
        for (int i = 0; i < OPENED.size(); i++) {
            Open open = OPENED.get(i);
            if (open.level == level && pos.equals(open.pos)) {
                open.at = now;
                return;
            }
        }
        OPENED.add(new Open(level, pos, now));
    }

    /**
     * Build 405: one pass of the latch that gets a guide out of a doorway.
     *
     * <p>Three cases, in this order: the guide is past the doorway (the latch is done), the guide is
     * walking by itself (nothing to do - an open door on the line of the walk was the whole problem), or
     * the guide has not moved since the last pass (order the next step again). Build 406 removed the move
     * of the body that build 405 did here: the author saw those jumps and they were not a fix.</p>
     *
     * @return true when the latch should live another pass
     */
    private static boolean crossStep(Cross cross, dev.settledlands.npc.GuideNpc npc, long now) {
        if (cross == null || npc == null) return false;
        try {
            if (now - cross.startedAt > CROSS_TICKS) {
                return report(cross, npc, "gave up after " + CROSS_TICKS + " ticks");
            }
            if (through(npc, cross)) {
                return report(cross, npc, "through the doorway after " + cross.steps + " step(s)");
            }
            double x = npc.getX();
            double z = npc.getZ();
            if (Math.abs(x - cross.lastX) + Math.abs(z - cross.lastZ) >= 0.05D) {
                // The guide is walking: the door being held open was all it needed.
                cross.lastX = x;
                cross.lastZ = z;
                return true;
            }
            cross.steps++;
            // Build 406: the step is <b>ordered</b> again on every pass, and that is all. Build 405 moved
            // the body 0.85 blocks when the order did not take, and the author's video shows exactly that:
            // a guide that jumps between doorways. A door problem is not worth a teleport, and a teleport
            // is not a fix - it is a different bug that hides this one.
            steer(cross, npc);
            cross.lastX = npc.getX();
            cross.lastZ = npc.getZ();
            return true;
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("the walk out of a doorway failed", failure);
            }
            return false;
        }
    }

    /** Build 405: is the guide on the far side of the doorway, and out of the door block? */
    private static boolean through(dev.settledlands.npc.GuideNpc npc, Cross cross) {
        if (npc == null || cross == null || cross.door == null) return false;
        double dx = npc.getX() - (cross.door.getX() + 0.5D);
        double dz = npc.getZ() - (cross.door.getZ() + 0.5D);
        if (dx * cross.fx + dz * cross.fz < THROUGH) return false;
        BlockPos feet = new BlockPos((int) Math.floor(npc.getX()), (int) Math.floor(npc.getY()),
                (int) Math.floor(npc.getZ()));
        BlockState state = cross.level == null ? null : cross.level.getBlockState(feet);
        return state == null || !Doors368.isDoor(state);
    }

    /** Ends a latch and says once what it cost. Returns false - the latch is over. */
    private static boolean report(Cross cross, dev.settledlands.npc.GuideNpc npc, String what) {
        if (saidThrough < 8) {
            saidThrough++;
            LOG.info("door: the guide " + (npc == null ? "?" : npc.getUUID()) + " is " + what
                    + " at " + (cross == null ? "?" : cross.door));
        }
        return false;
    }

    /**
     * Build 405: orders the step itself, 1.6 blocks along the way the guide was going. The navigator first
     * (a mob follows a path), and the move control when the navigator will not plan one - which is what
     * actually moves a mob.
     */
    private static boolean steer(Cross cross, dev.settledlands.npc.GuideNpc npc) {
        if (npc == null || cross == null) return false;
        double fx = cross.fx;
        double fz = cross.fz;
        try {
            double tx = npc.getX() + fx * 1.6D;
            double ty = npc.getY();
            double tz = npc.getZ() + fz * 1.6D;
            PathNavigation navigation = npc.getNavigation();
            // Build 406: the first order of a latch asks the navigator. From the second pass on it does
            // not: a path that ends at the doorway is exactly what the navigator keeps producing here, and
            // while a path is active it overwrites the move control every tick. So the path is cleared
            // first, and the step is ordered on the move control - the object that actually moves a mob.
            if (cross.steps <= 1 && navigation != null && navigation.moveTo(tx, ty, tz, 1.0D)) return true;
            if (navigation != null) navigation.stop();
            MoveControl control = ((Mob) npc).getMoveControl();
            if (control == null) return false;
            control.setWantedPosition(tx, ty, tz, 0.9D);
            return true;
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not order a guide a step out of a doorway", failure);
            }
            return false;
        }
    }

    /**
     * Build 405: the way the guide was going when it walked into the doorway.
     *
     * <p>Build 404 aimed at "1.1 blocks past the door centre, away from the guide" - and a guide standing
     * <b>in</b> the doorway has no "away from" worth speaking of: the direction was the half block between
     * its feet and the middle of the door, so the step it ordered pointed sideways along the door. This one
     * asks the navigator where it was taking the guide, then the door, then the guide's own facing.</p>
     */
    private static double[] travelDir(dev.settledlands.npc.GuideNpc npc, UUID id, BlockPos door) {
        double x = npc.getX();
        double z = npc.getZ();
        Watch watch = WATCH.get(id);
        if (watch != null && watch.target != null && watch.age <= 80) {
            double dx = watch.target.getX() + 0.5D - x;
            double dz = watch.target.getZ() + 0.5D - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length >= 0.5D) return new double[] {dx / length, dz / length};
        }
        if (door != null) {
            double dx = door.getX() + 0.5D - x;
            double dz = door.getZ() + 0.5D - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length >= 0.35D) return new double[] {dx / length, dz / length};
        }
        double yaw = Math.toRadians(npc.getYRot());
        return new double[] {-Math.sin(yaw), Math.cos(yaw)};
    }

    /** A door block on a path node, its doorstep or the block above it. */
    private static BlockPos openDoorAt(ServerLevel level, BlockPos node, long now, boolean onPath) {
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos at = new BlockPos(node.getX(), node.getY() + dy, node.getZ());
            if (!Doors368.isDoor(level.getBlockState(at))) continue;
            if (openDoor(level, at, now, onPath)) return at;
            return at;
        }
        return null;
    }

    private static boolean openDoor(ServerLevel level, BlockPos pos, long now, boolean onPath) {
        try {
            if (!Doors368.setOpen(level, pos, true)) return false;
            note(level, pos, now);
            if (saidOpen < 6) {
                saidOpen++;
                LOG.info("door: the guide pass opened " + pos + " in level " + level.dimension()
                        + (onPath ? " on a guide's path" : " next to a guide"));
            }
            return true;
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not open a door for a guide", failure);
            }
            return false;
        }
    }

    /**
     * Build 403: recomputes the guide's path to the target it already had.
     *
     * <p>A path computed while the door was closed ends at the closed door. Opening the door does not
     * lengthen that path, and a guide with a finished path stands still - which is the other half of
     * "the NPC cannot use a door". {@code PathNavigation#recomputePath()} asks the navigator to plan again
     * to the same target, and the target has not changed: only the door did.</p>
     */
    private static boolean recompute(dev.settledlands.npc.GuideNpc npc) {
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation == null) return false;
            navigation.recomputePath();
            if (saidPath < 4) {
                saidPath++;
                LOG.info("door: a guide stood at a door, so its path was planned again now that the "
                        + "door is open");
            }
            return true;
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not recompute a guide's path at a door", failure);
            }
            return false;
        }
    }

    /**
     * Build 403: one line for a guide that has stood still with an unfinished path - the case the author
     * has now reported twice. It names the door in reach and whether that door is open, so a log can tell
     * "the door was closed and we did not open it" from "the door was open and the guide still would not
     * walk", which are different bugs.
     */
    private static void traceStuck(ServerLevel level, dev.settledlands.npc.GuideNpc npc, double x,
            double y, double z, BlockPos door, String push) {
        if (saidStuck >= 6) return;
        saidStuck++;
        String where = door == null ? "no door within reach"
                : "door at " + door + " open=" + Doors368.anyOpen(level, door);
        LOG.info("door: the guide " + npc.getUUID() + " has stood still at "
                + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", x, y, z) + " - " + where
                + "; " + pathState(npc) + "; against a block=" + npc.horizontalCollision
                + "; walk-through=" + push);
    }

    /**
     * Build 404: what the guide's navigator thinks it is doing - the part of the report the author cannot
     * see. "no path" and "a path of five nodes, next node nought" are different bugs, and until this
     * build every log line looked the same for both.
     */
    private static String pathState(dev.settledlands.npc.GuideNpc npc) {
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation == null) return "no navigator";
            Path path = navigation.getPath();
            String done = navigation.isDone() ? "done" : "walking";
            if (path == null) return "no path, " + done;
            return "path " + path.getNodeCount() + " node(s), next " + path.getNextNodeIndex()
                    + ", target " + path.getTarget() + ", nav target " + navigation.getTargetPos()
                    + ", " + done;
        } catch (Throwable failure) {
            return "path unknown";
        }
    }

    /**
     * Closes the doors the guides opened and left.
     *
     * <p>Build 405: <b>the clock is gone</b>. Build 368-404 also closed a door that had been open for ten
     * seconds, "so a guide that died in a doorway does not leave the door open for ever" - but a guide
     * that died is not a guide, and {@link #guideNear} already answers for it, while a guide that is
     * <b>alive and standing in the doorway</b> was shut in: a body inside a solid door block cannot walk
     * out, so the door was opened again on the next pass, the guide took half a step, and ten seconds
     * later it was shut in again. That is the author's «стоит перед открытой дверью, открывает и
     * закрывает её». A door opened for a guide is now closed exactly when there is no guide near it, and
     * at no other moment.</p>
     *
     * <p>The rest is unchanged: a door somebody else closed in the meantime is not touched twice, and a
     * door the author locked is closed again just like any other - the lock is not a state of the door,
     * it is a rule about who may open it.</p>
     */
    private static void closeLeftBehind(long now) {
        if (OPENED.isEmpty()) return;
        int kept = 0;
        for (int i = OPENED.size() - 1; i >= 0; i--) {
            Open open = OPENED.get(i);
            // Build 405: nothing closes a door a guide is at - not an age, not a counter, not a budget.
            // Build 406: and nothing closes a door a guide is walking at either, however far it opened it
            // from. A door opened twelve blocks ahead is outside GUIDE_SAFE for the whole walk up to it,
            // and shutting it there would put the guide back exactly where builds 403-405 left it.
            if (needed(open.level, open.pos)) {
                kept++;
                continue;
            }
            OPENED.remove(i);
            if (Doors368.anyOpen(open.level, open.pos)) {
                Doors368.setOpen(open.level, open.pos, false);
            }
        }
        becauseUntouched(kept);
        if (OPENED.size() > 512) OPENED.clear();
    }

    private static int saidKept;

    /** One line for the case build 404 got wrong: doors that were left open only because a guide is there. */
    private static void becauseUntouched(int kept) {
        if (kept <= 0 || saidKept >= 4) return;
        saidKept++;
        LOG.info("door: " + kept + " door(s) left open - a guide is still at them (build 405 no longer "
                + "closes a door a guide stands in)");
    }

    /**
     * Build 403: is a guide inside {@code range} of this door block? Public because the locked-door guard
     * asks it before closing anything: a locked door standing open while a guide walks through it is not
     * a door that should be shut.
     */
    public static boolean guideNear(net.minecraft.world.level.Level level, BlockPos pos, double range) {
        if (level == null || pos == null || range <= 0D) return false;
        ServerLevel server = level instanceof ServerLevel sl ? sl : null;
        if (server == null) return false;
        Iterable<Entity> bodies = server.getAllEntities();
        if (bodies == null) return false;
        double limit = range * range;
        for (Entity body : bodies) {
            if (!(body instanceof dev.settledlands.npc.GuideNpc)) continue;
            if (body.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= limit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build 371: did a guide open this very door? The guard asks, so a locked door a guide is walking
     * through is not slammed in its face a tick later. Build 403 adds {@link #guideNear} beside it.
     */
    public static boolean guideOpened(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        for (int i = 0; i < OPENED.size(); i++) {
            Open open = OPENED.get(i);
            if (open.level == level && open.pos != null && open.pos.equals(pos)) return true;
        }
        return false;
    }

    /** How many doors a guide has open right now - a probe, not a rule. */
    public static int openDoors() {
        return OPENED.size();
    }
}
