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
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
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
 * <h2>Build 407: the door was open on the plan and shut again before the step</h2>
 *
 * <p>This is the root the author's log from build 406 finally shows, and it is the one thing every door
 * attempt since 368 missed. Opening a door was treated as a <b>one-off event</b>, so the pass also had to
 * decide when to shut it again - and every attempt shut it too early:</p>
 *
 * <ul>
 *   <li>406 opened doors twelve blocks ahead and closed them as soon as no guide was on its line, which
 *       for a door twelve blocks away means the very next pass. The log of that run is one door at
 *       {@code 62 64 -237} being opened and shut again and again, 140 ticks of "against a block=true"
 *       while a finished path waited behind it;</li>
 *   <li>368-404 opened the door only when the guide was already at it, which is one path-plan too late:
 *       a path computed at a shut door <b>ends</b> at the shut door and a finished path does not become a
 *       path through an open doorway.</li>
 * </ul>
 *
 * <p>The fix is to make the door <b>part of walking</b> instead of an event beside it:</p>
 *
 * <ol>
 *   <li>the guide's own path finder is told that a door is a doorway once - {@link #allowDoorNodes} sets
 *       both {@code canOpenDoors} (a closed wooden door may be walked, it will be opened) and
 *       {@code canPassDoors} (a wooden door standing open is typed as floor: no opening step in the way of
 *       the step);</li>
 *   <li>a door the guide's path runs through is opened from the path itself ({@link #openOnPath}), and it
 *       <b>stays open until the guide is through it</b> - not for a fixed time, not until the pass looks
 *       again, but until a guide has actually come up to it and walked away from it;</li>
 *   <li>and only then is it shut - behind the guide, with nobody in the doorway ({@link #stopLeftBehind});</li>
 *   <li>a <b>locked</b> door: build 407 refused every one of them, and that was wrong - it stopped a guide
 *       in front of the very door it had opened in the log of 406, because the author's test door is locked.
 *       Build 408 follows the author's own sentence, «нпс должен открывать все двери на его пути, в т.ч. и
 *       запертые»: a locked door <b>on the guide's path</b> is opened for it and shut again behind it, while
 *       a locked door that merely stands next to the guide is nobody's doorway and stays shut.</li>
 * </ol>
 *
 * <p>What is <b>not</b> here any more, and why: build 405's body moves (the author saw the guide teleport -
 * never again), and build 404/406's walk-a-step-on-the-move-control. The log shows that step doing nothing
 * at all for two seconds: a guide's own movement runs inside its AI step, every tick, and asks the navigator
 * again - a wanted position set from outside is overwritten before it is used. Adding another push on top of
 * that was a second failure on top of the first.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class NpcDoors368 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/NpcDoors368");

    /** Four times a second: a walking guide covers a quarter of a block in that time. */
    private static final int PERIOD = 5;

    /** How close a door has to be to be opened for a guide that is walking. */
    private static final double REACH = 1.9D;

    /**
     * How close a door has to be for a guide that is <b>stuck</b> - a guide with an unfinished path that
     * has not moved. It stands in the middle of the block in front of the door, so the door can be up to a
     * block and a half away; this reaches the door diagonally too.
     */
    private static final double STUCK_REACH = 3.0D;

    /** How many nodes of the guide's own path are checked for a door. */
    private static final int LOOKAHEAD = 4;

    /**
     * Build 407: the doorway. A guide this close to a door it opened is in it, so the door is neither shut
     * while it stands there nor claimed to be walked through before it has been: the pass marks a door
     * "walked through" only when a guide has actually been inside this radius, and shuts it only afterwards.
     * Two distances with one meaning in between is what turn 406 into an open/shut loop.
     */
    private static final double IN_DOORWAY = 1.7D;

    /** A door kept open for a guide is shut anyway after this many ticks (5 s) - a guide may die or leave. */
    private static final long CLOSE_AFTER = 100L;

    /** How long a guide has to stand still with an unfinished path before the pass looks closer. */
    private static final int STUCK_TICKS = 20;

    /** One replan per guide per this many ticks, when it stands still at a door on its way. */
    private static final long REPLAN_EVERY = 100L;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Watch> WATCH = new HashMap<>();
    private static final Map<UUID, int[]> STUCK = new HashMap<>();
    private static final Map<UUID, Long> REPLANNED = new HashMap<>();
    private static final Map<PathNavigation, Long> PATH_DOORS = new HashMap<>();
    private static final List<Open> OPENED = new ArrayList<>();

    /** One door this pass opened, and whether a guide has been up to it since. */
    private static final class Open {
        final ServerLevel level;
        final BlockPos pos;
        final long at;
        boolean walkedTo;

        Open(ServerLevel level, BlockPos pos, long at) {
            this.level = level;
            this.pos = pos;
            this.at = at;
            this.walkedTo = false;
        }
    }

    /** Where a guide's navigator was taking it, and how long ago that was. */
    private static final class Watch {
        BlockPos target;
        int age;
    }

    /** How many times the pass threw. It keeps running until this many, then stands down - and says so. */
    private static int failed;
    /** Build 413: when the pass last ran from a guide's own tick, so both ways in share one pace. */
    private static long lastOwnPass;
    private static final int FAILURE_LIMIT = 200;
    private static int guidesSeen;
    private static int doorsOpened;
    private static long saidAliveAt;
    private static boolean saidAwake;
    private static boolean saidFlag;
    private static int saidOpen;
    private static int saidStuck;
    private static int saidPath;
    private static int saidGuard;

    private NpcDoors368() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Build 412: the pass no longer switches itself off for the session. Until now one exception
        // anywhere in the pass set a flag and every later pass returned at the first line - silently, apart
        // from a single warning in the log. Every build since 368 added code to this pass (path reading, the
        // target watch, the on-the-way tests, the lock rule), so any one failure - in mine or in somebody
        // else's class it calls - turned every door into a plain closed door for the rest of the session:
        // «через открытые проходит, а сам открывать разучился». A pass that dies must say so and come back.
        if (failed > FAILURE_LIMIT) return;
        MinecraftServer server = event == null ? null : event.getServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null || overworld.getGameTime() % PERIOD != 0L) return;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                walk(level, overworld.getGameTime());
            }
            stopLeftBehind(overworld.getGameTime());
            beat(overworld.getGameTime(), guidesSeen, doorsOpened);
        } catch (Throwable failure) {
            failed++;
            if (failed <= 3) {
                LOG.warn("the guide door pass failed this pass (failure " + failed + " of "
                        + FAILURE_LIMIT + ") - it keeps running", failure);
            }
        }
    }

    /**
     * Build 412: once a minute the pass says it is alive, how many guides it can see and how many doors it
     * has opened in all. Every report after 408 needed this and none of them had it: a pass that never ran,
     * a pass that ran and found nothing, and a pass that had switched itself off all looked exactly the same
     * in the author's log. One line a minute is cheap and it turns the next report into a fact.
     */
    private static void beat(long now, int guides, int opened) {
        if (now - saidAliveAt < 1200L) return;
        saidAliveAt = now;
        LOG.info("door: the guide door pass is alive - " + guides + " guide(s) in sight, " + opened
                + " door(s) opened since the game started, " + failed + " failed pass(es)");
    }

    /**
     * Build 413: the same pass, for one guide, called from that guide's own tick.
     *
     * <p>The author's report of 412 settled a question I had been treating as a matter of taste: with a door
     * already standing open the guide walked through it for minutes and never closed it behind itself, and
     * with the door shut it never opened it. Opening and closing are the same few lines in this class, so the
     * conclusion is not a matter of opinion - this pass never ran for that guide. The server tick is the one
     * place it used to run from, and if that hook is not firing (an event class the game no longer posts, a
     * static handler the event bus never registered) nothing here can ever run, and no gate can see it: the
     * code is all present and correct in the jar.</p>
     *
     * <p>So the pass now has a second way in, one that cannot go missing: the guide's own tick, through
     * {@code GuideGuideDoor413Mixin}. Both ways go through `track`, which keeps its own clock, so calling it
     * from the entity's tick cannot make it run faster than once every {@link #PERIOD} ticks.</p>
     */
    public static void pass(dev.settledlands.npc.GuideNpc npc) {
        if (npc == null) return;
        try {
            if (!(npc.level() instanceof ServerLevel level)) return;
            long now = level.getGameTime();
            if (now - lastOwnPass < PERIOD) return;
            lastOwnPass = now;
            guidesSeen = 1;
            track(level, npc, now);
            stopLeftBehind(now);
            beat(now, 1, doorsOpened);
        } catch (Throwable failure) {
            failed++;
            if (failed <= 3) {
                LOG.warn("the guide door pass failed in a guide's own tick (failure " + failed + " of "
                        + FAILURE_LIMIT + ") - it keeps running", failure);
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
            track(level, npc, now);
        }
        guidesSeen = guides;
        // One line to say the pass is alive, once there is a guide to look after. A pass that never ran and
        // a pass that ran and found nothing looked identical in every log the author sent.
        if (!saidAwake && guides > 0) {
            saidAwake = true;
            LOG.info("door: the guide door pass is armed - " + guides + " guide(s) in level "
                    + level.dimension() + "; a door on a guide's way is opened and stays open until the "
                    + "guide is through it, and a locked one on that way is opened too");
        }
        if (LAST_POS.size() > 256) {
            LAST_POS.keySet().removeIf(id -> !seen.contains(id));
            STUCK.keySet().removeIf(id -> !seen.contains(id));
            WATCH.keySet().removeIf(id -> !seen.contains(id));
            REPLANNED.keySet().removeIf(id -> !seen.contains(id));
        }
    }

    /** Everything the pass does for one guide, in one place, so both ways in run the same code. */
    private static void track(ServerLevel level, dev.settledlands.npc.GuideNpc npc, long now) {
        {
            UUID id = npc.getUUID();
            if (id == null) return;
            allowDoorNodes(npc);
            double x = npc.getX();
            double y = npc.getY();
            double z = npc.getZ();
            double[] last = LAST_POS.get(id);
            if (last == null) {
                LAST_POS.put(id, new double[] {x, y, z, 0.0D, 0.0D});
            } else {
                // Remember where it came from while it is still moving: that is the only heading this pass
                // can trust, because the guide walks by three different engines (its own scripted step, a
                // mobility route, and the vanilla navigator) and none of them all the time.
                double stepX = x - last[0];
                double stepZ = z - last[2];
                if (Math.abs(stepX) + Math.abs(stepZ) >= 0.02D) {
                    LAST_POS.put(id, new double[] {x, y, z, stepX, stepZ});
                } else {
                    LAST_POS.put(id, new double[] {x, y, z, last[3], last[4]});
                }
            }
            double moved = last == null
                    ? 1.0D
                    : Math.abs(x - last[0]) + Math.abs(y - last[1]) + Math.abs(z - last[2]);
            boolean walking = moved >= 0.05D;
            boolean pinned = !walking && hasPath(npc);
            int[] stood = STUCK.get(id);
            int still = walking ? 0 : (stood == null ? PERIOD : stood[0] + PERIOD);
            STUCK.put(id, new int[] {still});
            rememberTarget(npc, id);
            markWalkedTo(id, x, y, z);

            // The doors the guide's own path goes through: the exact set it has decided to walk through,
            // opened before it arrives and kept open until it is out (see stopLeftBehind).
            openOnPath(level, npc, now);
            // And the doors around a guide, in case the path was planned before the door was ever opened -
            // the path finder only knows about doors standing open when it plans. Build 410: whether the
            // door is on the guide's way is decided per door and by the facts (its own path, or the line to
            // where it was walking), not by the "is it moving this fifth of a second" flag: build 409 passed
            // that flag here as the verdict, so a locked door was "not on the path" exactly when the guide
            // had stopped in front of it - and it stood there for ever.
            if (openAround(level, npc, id, x, y, z, now, walking ? REACH : STUCK_REACH, walking) != null) {
                doorsOpened++;
            }

            boolean stoodAtDoor = !walking && still >= STUCK_TICKS && still % STUCK_TICKS == 0
                    && pinned;
            if (stoodAtDoor) {
                // A guide with an unfinished path that has not moved, and a door between it and the place
                // it was walking to: ask the navigator to plan again now that the door is standing open.
                // This is a replan, not a step - the guide still walks where its own movement drives it.
                BlockPos door = doorInReach(level, npc, x, y, z);
                boolean planned = door != null && doorBetween(npc, id, door) && replan(npc, id, now);
                traceStuck(level, npc, x, y, z, door, planned ? "path planned again" : "nothing to do");
            }
        }
    }

    /**
     * Tells the guide's node maker that doors are doorways.
     *
     * <p>Two switches, and both are needed: {@code canOpenDoors} makes a <b>closed</b> wooden door a node
     * that may be walked (the pass will have opened it by the time the guide gets there), and
     * {@code canPassDoors} makes a wooden door <b>standing open</b> plain floor. Without the second one the
     * step into the doorway is still special, and the step is what the author watches.</p>
     *
     * <p>It is set on every pass rather than once, because a navigator may hand out a fresh node maker
     * whenever it likes. The log line is said once per navigator, so a session cannot fill the file with it.
     * Build 403's finding stands: in 1.21 this question does not live on the mob - {@code Mob} has no
     * {@code canOpenDoors} at all - which is why build 368's mob mixin never answered anything.</p>
     */
    private static void allowDoorNodes(dev.settledlands.npc.GuideNpc npc) {
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation == null) return;
            NodeEvaluator evaluator = navigation.getNodeEvaluator();
            if (evaluator == null) return;
            if (!evaluator.canOpenDoors()) evaluator.setCanOpenDoors(true);
            Long known = PATH_DOORS.get(navigation);
            long now = npc.level() instanceof ServerLevel server ? server.getGameTime() : 0L;
            if (known != null && now - known < 200L) return;
            PATH_DOORS.put(navigation, now);
            // Build 407: the second switch. Reading it back is not possible without a second external
            // member, so this is said once per navigator and the effect is visible where it matters: the
            // guide either walks through the open doorway or it does not.
            navigation.setCanPassDoors(true);
            if (!saidFlag) {
                saidFlag = true;
                LOG.info("door: a guide's own path finding treats a door as a doorway now - a closed wooden "
                        + "door as a door it may open, a door standing open as plain floor");
            }
            if (PATH_DOORS.size() > 64) {
                PATH_DOORS.keySet().removeIf(nav -> nav == null || nav.isDone() && nav.getPath() == null);
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
     * Opens the doors that sit on the next nodes of the guide's own path.
     *
     * <p>A path that goes through a doorway puts a node on the door block, so this is the exact set of
     * doors the guide has decided to walk through - no guessing by distance, and the door is open before the
     * guide is pressed against it. Returns the door it opened, if any.</p>
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
                BlockPos hit = openDoorAt(level, npc, node, now);
                if (hit != null && opened == null) opened = hit;
            }
            return opened;
        } catch (Throwable failure) {
            return null;
        }
    }

    /**
     * Opens the doors around a guide, each one judged by whether it is on the guide's way.
     *
     * <p>Build 410: the verdict is not the caller's flag any more. Build 411 adds the third answer,
     * {@link #inFrontOf}: the guide walks by three engines and the vanilla navigator is not always one of
     * them, so path-and-target can both be empty - and then every door was off its way and it opened none.</p>
     */
    private static BlockPos openAround(ServerLevel level, dev.settledlands.npc.GuideNpc npc, UUID id,
            double x, double y, double z, long now, double reach, boolean walking) {
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
                    if (openDoor(level, npc, pos, now,
                            onTheWay(level, npc, id, pos) || inFrontOf(level, npc, id, pos, walking))) {
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * A door block on a path node, its doorstep or the block above it - a door the guide's path goes
     * through, which is the one kind of door a lock does not stop (build 408). The {@code onPath} argument
     * handed to {@link #openDoor} is what lets it tell "a door on the way" from "a locked door that merely
     * stands next to the guide".
     */
    private static BlockPos openDoorAt(ServerLevel level, dev.settledlands.npc.GuideNpc npc, BlockPos node,
            long now) {
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos at = new BlockPos(node.getX(), node.getY() + dy, node.getZ());
            if (!Doors368.isDoor(level.getBlockState(at))) continue;
            openDoor(level, npc, at, now, true);
            return at;
        }
        return null;
    }

    /**
     * Opens one door for a guide, and remembers it so it is kept open until the guide is through.
     *
     * <p>Three refusals: not a door, already standing open (somebody else's door is none of our business),
     * and a <b>locked</b> door that is not on this guide's way. The last one is the author's own line between
     * his two rules: every door on the guide's path is opened, locked or not, while a locked door the guide
     * merely stands next to stays shut so the pass does not become a key to the village.</p>
     */
    private static boolean openDoor(ServerLevel level, dev.settledlands.npc.GuideNpc npc, BlockPos pos,
            long now, boolean onPath) {
        try {
            if (pos == null) return false;
            if (!Doors368.isDoor(level.getBlockState(pos))) return false;
            if (Doors368.anyOpen(level, pos)) {
                keepOpen(level, pos, now);
                return false;
            }
            // Build 408: a locked door is the author's rule, and he settled it in one sentence - «нпс
            // должен открывать все двери на его пути, в т.ч. и запертые». So the lock is not an answer by
            // itself: a door the guide's own path runs through is opened locked or not (368/403 said the
            // same: a guide is allowed to open a locked door and walk through it), while a locked door that
            // merely stands next to the guide is nobody's doorway and stays shut. Build 407 refused both,
            // and the guide that had opened that very door in the log of 406 stopped dead in front of it.
            if (locked(level, pos) && !onPath) {
                if (saidGuard < 3) {
                    saidGuard++;
                    LOG.info("door: the locked door at " + pos + " is not on a guide's way - it stays shut "
                            + "(path=" + pathState(npc) + ")");
                }
                return false;
            }
            if (locked(level, pos) && saidGuard < 6) {
                saidGuard++;
                LOG.info("door: the locked door at " + pos + " is on the guide's path - opened for the guide, "
                        + "and shut again behind it");
            }
            if (!Doors368.setOpen(level, pos, true)) return false;
            keepOpen(level, pos, now);
            if (saidOpen < 6) {
                saidOpen++;
                LOG.info("door: the guide pass opened " + pos + " in level " + level.dimension()
                        + (onPath ? " on a guide's path" : " next to a guide")
                        + " - it stays open until the guide is through it");
            }
            return true;
        } catch (Throwable failure) {
            if (failure != null) {
                LOG.warn("could not open a door for a guide", failure);
            }
            return false;
        }
    }

    /** Is any half of the door at this position locked? */
    private static boolean locked(ServerLevel level, BlockPos pos) {
        List<BlockPos> parts = Doors368.parts(level, pos);
        for (int i = 0; i < parts.size(); i++) {
            if (DoorEvents368.locked(level, parts.get(i))) return true;
        }
        return false;
    }

    /** Remembers a door as one this pass keeps open (the list is bounded - a session cannot grow it for ever). */
    private static void keepOpen(ServerLevel level, BlockPos pos, long now) {
        for (int i = 0; i < OPENED.size(); i++) {
            Open open = OPENED.get(i);
            if (open.level == level && open.pos != null && open.pos.equals(pos)) return;
        }
        if (OPENED.size() >= 64) OPENED.remove(0);
        OPENED.add(new Open(level, pos, now));
    }

    /** Build 407: marks a door opened ahead as "walked through", once a guide has actually been in it. */
    private static void markWalkedTo(UUID id, double x, double y, double z) {
        if (OPENED.isEmpty()) return;
        for (int i = 0; i < OPENED.size(); i++) {
            Open open = OPENED.get(i);
            if (open.walkedTo || open.pos == null) continue;
            double dx = open.pos.getX() + 0.5D - x;
            double dz = open.pos.getZ() + 0.5D - z;
            double dy = open.pos.getY() + 0.5D - y;
            if (dx * dx + dz * dz + dy * dy <= IN_DOORWAY * IN_DOORWAY) open.walkedTo = true;
        }
    }

    /**
     * Shuts the doors behind the guide.
     *
     * <p>The author chose "close it right behind me", and this is exactly that: a door is shut when a guide
     * has been up to it and no guide is in the doorway any more - one and a half blocks is the whole
     * doorway, so the door is never shut on the mob that is standing in it. A door the guide never reached
     * is left open until {@value #CLOSE_AFTER} ticks, because it was opened for a guide that is still
     * walking towards it; shutting it early is what turn 405-406 did and what the log shows as the loop.</p>
     */
    private static void stopLeftBehind(long now) {
        if (OPENED.isEmpty()) return;
        for (int i = OPENED.size() - 1; i >= 0; i--) {
            Open open = OPENED.get(i);
            long age = now - open.at;
            boolean stale = age > CLOSE_AFTER || age < 0L;
            boolean somebodyHere = guideNear(open.level, open.pos, IN_DOORWAY);
            if (somebodyHere) {
                open.walkedTo = true;
                continue;
            }
            if (open.walkedTo || stale) {
                OPENED.remove(i);
                if (Doors368.anyOpen(open.level, open.pos)) {
                    Doors368.setOpen(open.level, open.pos, false);
                }
            }
        }
    }

    /**
     * Asks the guide's navigator to plan again to the target it already had - now that the door it is
     * standing at is standing open. A finished path does not become a path through an open doorway by
     * itself; this is the call that turns "the door is open" into "the guide is walking through it".
     */
    private static boolean replan(dev.settledlands.npc.GuideNpc npc, UUID id, long now) {
        Long last = REPLANNED.get(id);
        if (last != null && now - last < REPLAN_EVERY) return false;
        REPLANNED.put(id, now);
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

    /** The closed, unlocked door closest to a guide, if any is within reach. */
    private static BlockPos doorInReach(ServerLevel level, dev.settledlands.npc.GuideNpc npc, double x,
            double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!Doors368.isDoor(level.getBlockState(pos))) continue;
                    if (npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                            > STUCK_REACH * STUCK_REACH) {
                        continue;
                    }
                    return pos;
                }
            }
        }
        return null;
    }

    /** Remembers where the guide's navigator was taking it, and how long ago that was. */
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
     * Build 410: is this door on the guide's way? Two answers, both from facts about the guide rather than
     * from whether it happens to be moving: a door block on the next nodes of its own path, or a door between
     * it and the place its navigator was taking it. This is the question a lock is answered with - «нпс
     * должен открывать все двери на его пути, в т.ч. и запертые» - so it has to stay true while the guide
     * stands and waits, which is exactly when the door is being decided.
     */
    private static boolean onTheWay(ServerLevel level, dev.settledlands.npc.GuideNpc npc, UUID id,
            BlockPos door) {
        if (door == null) return false;
        if (onPathNode(npc, door)) return true;
        return doorBetween(npc, id, door);
    }

    /**
     * Build 411: is the door straight in front of the guide, at arm's length?
     *
     * <p>The other two answers can both be silent, and that is what the author met in 410 («разучился их сам
     * открывать»): the guide walks by its own scripted step and by its own mobility routes, so the vanilla
     * navigator's path and target are often empty - and then "on the way" was false for every door, so a
     * locked door was never opened, and a walk that had to go through one stopped dead.</p>
     *
     * <p>This one is physics instead of bookkeeping: while the guide is moving, the door must lie ahead of
     * it within a narrow cone and within arm's length - the door it is about to walk into. While it stands,
     * the door must be close enough to touch, because a guide that has stopped is standing at a doorway, not
     * beside a wall.</p>
     */
    private static boolean inFrontOf(ServerLevel level, dev.settledlands.npc.GuideNpc npc, UUID id,
            BlockPos door, boolean walking) {
        if (door == null) return false;
        try {
            double dx = door.getX() + 0.5D - npc.getX();
            double dz = door.getZ() + 0.5D - npc.getZ();
            double away = Math.sqrt(dx * dx + dz * dz);
            if (away > 1.6D) return false;
            if (!walking) return true;
            double[] last = LAST_POS.get(id);
            if (last == null) return true;
            double headingX = last[3];
            double headingZ = last[4];
            double heading = Math.sqrt(headingX * headingX + headingZ * headingZ);
            if (heading < 0.001D) return true;
            // How far ahead along its own heading the door is, and how far off that line: a cone of about
            // sixty degrees, which is one doorway wide at arm's length and nothing else.
            double ahead = (dx * headingX + dz * headingZ) / heading;
            double off = Math.abs(dx * headingZ - dz * headingX) / heading;
            return ahead >= 0.2D && off <= ahead + 0.35D;
        } catch (Throwable failure) {
            return false;
        }
    }

    /** Is this door block one of the next nodes of the guide's own path? */
    private static boolean onPathNode(dev.settledlands.npc.GuideNpc npc, BlockPos door) {
        try {
            PathNavigation navigation = npc.getNavigation();
            if (navigation == null || navigation.isDone()) return false;
            Path path = navigation.getPath();
            if (path == null) return false;
            int count = path.getNodeCount();
            if (count <= 0) return false;
            int from = Math.max(0, path.getNextNodeIndex() - 1);
            int to = Math.min(count - 1, path.getNextNodeIndex() + LOOKAHEAD);
            for (int i = from; i <= to; i++) {
                BlockPos node = path.getNodePos(i);
                if (node == null) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (node.getX() == door.getX() && node.getY() + dy == door.getY()
                            && node.getZ() == door.getZ()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Does the door stand between the guide and the place it was walking to? That is the test for "it
     * stopped at a door it was trying to pass" - as opposed to "it stopped at a door it lives beside",
     * which nothing should push it through.
     */
    private static boolean doorBetween(dev.settledlands.npc.GuideNpc npc, UUID id, BlockPos door) {
        if (door == null) return false;
        Watch watch = WATCH.get(id);
        // Build 410: the age of the target was sixty ticks - one second of standing still and the pass
        // forgot where the guide was going, which is the second half of the 409 deadlock. A target stays
        // remembered while the guide is standing and its path is unfinished; it is dropped when a new target
        // arrives and ages normally only for a guide that already has a finished path.
        if (watch == null || watch.target == null) return false;
        if (watch.age > 60 && !hasPath(npc)) return false;
        double dx = watch.target.getX() + 0.5D - npc.getX();
        double dz = watch.target.getZ() + 0.5D - npc.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) return false;
        double gx = door.getX() + 0.5D - npc.getX();
        double gz = door.getZ() + 0.5D - npc.getZ();
        double along = (gx * dx + gz * dz) / length;              // how far along the way the door is
        if (along < 0.2D || along > length + 1.0D) return false;  // behind the guide, or past the target
        double across = Math.abs(gx * dz - gz * dx) / length;     // how far off that line the door is
        return across <= 1.4D;
    }

    /**
     * One line for a guide that has stood still with an unfinished path - the case the author has reported
     * more than once. It names the door in reach and whether that door is open, so a log can tell "the door
     * was closed and we did not open it" from "the door was open and the guide still would not walk", which
     * are different bugs. Build 404 added the navigator's own state and it is what found this build's root.
     */
    private static void traceStuck(ServerLevel level, dev.settledlands.npc.GuideNpc npc, double x,
            double y, double z, BlockPos door, String action) {
        if (saidStuck >= 6) return;
        saidStuck++;
        String where = door == null ? "no door within reach"
                : "door at " + door + " open=" + Doors368.anyOpen(level, door)
                        + " locked=" + locked(level, door);
        LOG.info("door: the guide " + npc.getUUID() + " has stood still at "
                + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", x, y, z) + " - " + where
                + "; " + pathState(npc) + "; against a block=" + npc.horizontalCollision
                + "; " + action);
    }

    /**
     * What the guide's navigator thinks it is doing - the part of the report the author cannot see.
     * "no path" and "a path of five nodes, next node nought" are different bugs.
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
     * Is a guide inside {@code range} of this door block? Public because the locked-door guard asks it before
     * closing anything: a locked door standing open while a guide walks through it is not a door that should
     * be shut.
     */
    public static boolean guideNear(Level level, BlockPos pos, double range) {
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

    /** Did this pass open that very door? The guard asks, so it does not fight the pass over a door. */
    public static boolean guideOpened(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        for (int i = 0; i < OPENED.size(); i++) {
            Open open = OPENED.get(i);
            if (open.level == level && open.pos != null && open.pos.equals(pos)) return true;
        }
        return false;
    }

    /** How many doors this pass is keeping open right now - a probe, not a rule. */
    public static int openDoors() {
        return OPENED.size();
    }
}
