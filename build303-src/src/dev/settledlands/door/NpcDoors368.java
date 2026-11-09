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
 *   <li>and when a guide does stop at a door it has just opened, it is now walked through it
 *       ({@link #pushThrough}): a short path to the point just past the doorway, and, if the pathfinder
 *       still refuses, the step ordered straight on the move control - a mob is moved by its move control,
 *       not by its opinion. It is a walk, not a teleport: the guide still collides with everything.</li>
 * </ul>
 *
 * <p>The stuck line now says what the navigator thinks ({@link #pathState}): how many nodes the path has,
 * which node is next, where it was taking the guide, and whether the guide is against a block. That is the
 * difference between "the path ended at the door" and "the path goes through and something else holds it",
 * and it is in the log rather than in another guess.</p>
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
 * <p>The door is still closed behind the guide ({@value #CLOSE_RANGE} blocks, or after
 * {@value #CLOSE_AFTER} ticks), a door a player closed stays closed, and {@link DoorGuard371} no longer
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
    private static final double REACH_AHEAD = 4.0D;

    /** Build 404: an open door this close to a guide that is stuck is a doorway it should be walking through. */
    private static final double PUSH_RANGE = 2.0D;

    /** How far a guide has to get from a door it opened before the door is closed again. */
    private static final double CLOSE_RANGE = 3.0D;

    /** A door opened by a guide and left behind is closed after this many ticks (10 s). */
    private static final long CLOSE_AFTER = 200L;

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
        final long at;

        Open(ServerLevel level, BlockPos pos, long at) {
            this.level = level;
            this.pos = pos;
            this.at = at;
        }
    }

    private static boolean failed;
    private static boolean saidAwake;
    private static boolean saidFlag;
    private static int saidOpen;
    private static int saidStuck;
    private static int saidPath;

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

            boolean stoodAtDoor = !walking && still >= STUCK_TICKS && still % STUCK_TICKS == 0
                    && (pinned || doorBetween(npc, id, openDoorInReach(level, npc, x, y, z)));
            if (stoodAtDoor) {
                // Build 404: a guide that stops at a door it has just opened is the author's report
                // exactly («доходит до двери, теперь открывает, но дальше открытой двери не проходит»),
                // and the honest reading is that its path ended at the door and the next plan never
                // reaches past it. So: plan again, and if the plan still leads nowhere, walk it through
                // the doorway step by step - the door is open, the guide is 1.5 blocks from it, and the
                // only thing missing is somebody telling its feet to keep going.
                boolean planned = recompute(npc);
                BlockPos through = openDoorInReach(level, npc, x, y, z);
                String push = "no open door at hand";
                if (through != null && mayPush(id, through, now)) {
                    push = pushThrough(level, npc, through)
                            ? (planned ? "path replanned and a step ordered" : "a step ordered")
                            : "failed";
                } else if (through != null) {
                    push = "already tried";
                }
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
     * Build 404: opens the closed doors <b>ahead</b> of a walking guide - within {@value #REACH_AHEAD}
     * blocks and inside a cone around the direction the navigator is taking it.
     *
     * <p>Why a cone and not a radius: a door ten blocks behind the guide or behind a wall is nobody's
     * business, while the door it is about to plan a path through is everybody's. Opening it a second
     * before the plan is what makes the plan go through the doorway instead of around it or up to it.</p>
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
            tx /= length;
            tz /= length;
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            int span = (int) Math.ceil(REACH_AHEAD);
            for (int dx = -span; dx <= span; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -span; dz <= span; dz++) {
                        BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                        if (!Doors368.isDoor(level.getBlockState(pos))) continue;
                        double vx = pos.getX() + 0.5D - x;
                        double vz = pos.getZ() + 0.5D - z;
                        double distance = Math.sqrt(vx * vx + vz * vz);
                        if (distance > REACH_AHEAD || distance < 0.01D) continue;
                        if ((vx * tx + vz * tz) / distance < 0.5D) continue;   // roughly ahead
                        if (Doors368.isOpen(level.getBlockState(pos))) continue;
                        openDoor(level, pos, now, true);
                    }
                }
            }
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not open the doors ahead of a guide", failure);
            }
        }
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
     * Build 404: walks a guide through an open doorway it is standing at.
     *
     * <p>The guide's own movement is the game's navigator ({@code GuideNpc#stepCustomMovement} calls
     * {@code PathNavigation#moveTo}), so a path that ends at the door means the guide stops at the door -
     * the door being open changes nothing once the path is done. This asks for a short path to the point
     * just <b>past</b> the doorway; if the pathfinder still refuses, the step itself is ordered on the
     * move control, which is what actually moves a mob. No teleport: the mob still walks, and still
     * collides with anything in the way.</p>
     */
    private static boolean pushThrough(ServerLevel level, dev.settledlands.npc.GuideNpc npc, BlockPos door) {
        if (level == null || npc == null || door == null) return false;
        double cx = door.getX() + 0.5D;
        double cz = door.getZ() + 0.5D;
        double dx = cx - npc.getX();
        double dz = cz - npc.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3D) return false;
        double fx = cx + dx / length * 1.1D;
        double fz = cz + dz / length * 1.1D;
        double fy = npc.getY();
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation != null && navigation.moveTo(fx, fy, fz, 1.0D)) return true;
            if (navigation != null) navigation.stop();
            MoveControl control = ((Mob) npc).getMoveControl();
            if (control == null) return false;
            control.setWantedPosition(fx, fy, fz, 0.9D);
            return true;
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not walk a guide through an open door", failure);
            }
            return false;
        }
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
            OPENED.add(new Open(level, pos, now));
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
     * Closes the doors the guides opened and left. A door is closed when no guide stands next to it any
     * more, or when it has been open longer than {@link #CLOSE_AFTER} ticks - a guide that died in a
     * doorway must not leave the door open for ever. A door somebody else closed in the meantime is not
     * touched twice, and a door the author locked is closed again just like any other: the lock is not
     * a state of the door, it is a rule about who may open it.
     */
    private static void closeLeftBehind(long now) {
        if (OPENED.isEmpty()) return;
        for (int i = OPENED.size() - 1; i >= 0; i--) {
            Open open = OPENED.get(i);
            long age = now - open.at;
            boolean stale = age > CLOSE_AFTER || age < 0L;
            if (!stale && (guideNear(open.level, open.pos, CLOSE_RANGE)
                    || guideNear(open.level, open.pos, GUIDE_SAFE))) {
                continue;
            }
            OPENED.remove(i);
            if (Doors368.anyOpen(open.level, open.pos)) {
                Doors368.setOpen(open.level, open.pos, false);
            }
        }
        if (OPENED.size() > 512) OPENED.clear();
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
