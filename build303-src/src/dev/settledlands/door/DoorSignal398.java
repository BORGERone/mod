package dev.settledlands.door;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 398: the door's «открытие» chain - what runs when a door is opened by hand.
 *
 * <p>The author's words: «добавь ... главной ноде двери узел \"открытие\"». Until now a door's graph had
 * exactly one way in - it was <b>hacked</b> ({@link DoorSignal388#hack}) - and a door that is simply opened was
 * silent, so the author could not say anything at the moment his door opened for an ordinary reason.</p>
 *
 * <p>This class is the second way in, and it is deliberately the same shape as the first: read the graph of
 * that door, take the node the card's second socket points at ({@code Node#falseNext}), hand it to
 * {@link BlockTrigger351#start} - the entry of build 393, which runs the node itself and then walks on - and
 * let the stock interpreter do the rest. Nothing here knows what a «Реплика» is: a chain is a chain.</p>
 *
 * <p><b>When it runs.</b> The hook is the door's own {@code useWithoutItem} ({@code DoorUse373Mixin}), on the
 * server, in the tick the door really changed state - not on the click and not on a guess: a refused click
 * (the door is locked) returns {@code FAIL} and never gets here, and a click on the wrong block never reaches
 * the door at all. The state change is compared before and after, so a click that opened nothing cannot fire a
 * chain.</p>
 *
 * <p>A door that is <b>hacked</b> does not fire this chain - that is the first socket's job and the author can
 * wire the two differently, which is the whole point of having both.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class DoorSignal398 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorSignal398");

    /** One key per player and door, held for a moment: a door used twice in a tick fires once. */
    private static final long GUARD_MS = 250L;

    private static final Map<String, Long> RECENT = new ConcurrentHashMap<>();

    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    /** The last time this was logged, so the log cannot flood (the rule of build 349). */
    private static int said;

    private DoorSignal398() {}

    /**
     * Build 401, the watchdog of report #3: the door mixin says it is alive, once.
     *
     * <p>Called from the {@code HEAD} of the same {@code useWithoutItem} injection that carries the chain. A
     * mixin whose selector stops matching does not fail the launch ({@code require = 0}), it just never runs -
     * and then a wired socket is silent with nothing anywhere to explain it. One line, once, is the cheapest
     * thing that can tell the two apart in the author's log.</p>
     */
    public static void sawUse(BlockPos pos, Object state, boolean wasOpen) {
        if (SAW_USE) return;
        SAW_USE = true;
        note("door: DoorUse373Mixin is LIVE - the first use of a door reached the injection at " + pos
                + " (open before this click: " + wasOpen + "), so the «открытие» chain of build 398 has its"
                + " trigger");
    }

    /** Build 401: the door the mixin saw open - the chain is about to run from the mixin leg. */
    public static void sawOpened(BlockPos pos) {
        note("door: DoorUse373Mixin saw the door at " + pos + " go from shut to open - the «открытие» leg of"
                + " the mixin runs the chain (the tick watcher of build 401 will find it already run)");
    }

    private static boolean SAW_USE;

    /**
     * Build 401: one door a player has just clicked while it was shut - look at it again on the next tick.
     *
     * <p>The author's report on the second screenshot: «при открытии двери проиграется реплика, но сейчас
     * ничего не происходит». The chain has exactly one trigger, an {@code @Inject} at the {@code RETURN} of
     * {@code DoorBlock#useWithoutItem} ({@code DoorUse373Mixin}), and that trigger has one failure mode this
     * mod cannot see from the outside: {@code require = 0} means "if this selector does not match the game's
     * method, the game still launches" - and a mixin that never bound looks exactly like a chain that was never
     * wired. Both builds that added the socket (398) and its label (398) could only be as reliable as that one
     * injection.</p>
     *
     * <p>So the trigger gets a second leg that depends on nothing but the world: the click on a shut door is
     * recorded here (a door that is already open is a close, and a locked one never gets this far), and on the
     * next server tick the door is read again. Closed before, open now - the door was opened by that click, and
     * the chain runs. {@link #RECENT} makes the two legs one: a mixin that <b>did</b> bind has already run the
     * chain, and the watcher's call half a tick later is dropped by the same quarter-second guard.</p>
     *
     * <p>Cost: one map entry and one map walk per tick, and only while somebody has just clicked a shut door.</p>
     */
    public static void watch(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return;
        try {
            if (WATCHING.size() > 32) WATCHING.clear();
            WATCHING.put(player.getUUID() + "@" + level.dimension().location(),
                    new Watching(player, level, pos.immutable(), System.currentTimeMillis()));
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    /** One click on a shut door, waiting for the next tick to find out whether it opened it. */
    private static final class Watching {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos pos;
        final long at;

        Watching(ServerPlayer player, ServerLevel level, BlockPos pos, long at) {
            this.player = player;
            this.level = level;
            this.pos = pos;
            this.at = at;
        }
    }

    private static final Map<String, Watching> WATCHING = new ConcurrentHashMap<>();

    /**
     * The second leg: a tick later, did the door the player clicked on open?
     *
     * <p>One entry per player and dimension; the door is read with the mod's own {@code Doors368.isOpen}, the
     * same reader every door write of this mod trusts. A door that is still shut, or a player who left, is
     * dropped without a word - the click simply did not open anything.</p>
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (WATCHING.isEmpty()) return;
        try {
            Iterator<Map.Entry<String, Watching>> walk = WATCHING.entrySet().iterator();
            while (walk.hasNext()) {
                Map.Entry<String, Watching> entry = walk.next();
                Watching w = entry.getValue();
                if (System.currentTimeMillis() - w.at > 200L || w.player.isRemoved()
                        || w.player.level() != w.level) {
                    walk.remove();
                    continue;
                }
                walk.remove();
                if (!Doors368.isOpen(w.level.getBlockState(w.pos))) {
                    note("door: the click on the shut door at " + w.pos + " did not open it - nothing to run");
                    continue;
                }
                note("door: the door at " + w.pos + " opened on a click - the «открытие» leg of build 401 runs"
                        + " the chain (the use-mixin leg has already run it if it is live: the guard of"
                        + " " + GUARD_MS + " ms decides)");
                open(w.player, w.level, w.pos);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    /**
     * Runs the door's «открытие» chain for the player who opened it.
     *
     * @return true when a chain was started - false for an unwired socket, a door with no graph, or a
     *         repeated call in the same quarter of a second
     */
    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        try {
            DoorGraph382Store store = DoorGraph382Store.get(level.getServer());
            if (store == null) {
                why("the door store of this world is not there");
                return false;
            }
            String key = DoorGraph382Store.lowerKey(level, pos);
            if (key == null || key.isEmpty()) {
                why("the door at " + pos + " has no key");
                return false;
            }
            NpcDialogueGraph graph = store.graph(key);
            if (graph == null) {
                why("the door at " + key + " has no graph - the wand has never opened it");
                return false;
            }

            NpcDialogueGraph.Node start = DoorGraph382.openChainStart(graph);
            if (start == null) {
                // This is the answer to the second screenshot if the wire is not on the server's copy: the card
                // of this door has no node under its «открытие» socket, so there is nothing to run.
                why("the card of " + key + " has nothing wired to its «открытие» socket");
                return false;
            }

            String stamp = key + "@" + player.getName().getString();
            long now = System.currentTimeMillis();
            Long last = RECENT.get(stamp);
            if (last != null && now - last < GUARD_MS) return false;   // the other leg already ran it
            if (RECENT.size() > 256) RECENT.clear();
            RECENT.put(stamp, now);

            // The door stands where a block stands in a block's graph: the camera of a «Реплика» aims here
            // and the store key names the door in the log.
            BlockTalk358.Where where = new BlockTalk358.Where(level, key,
                    pos.getX(), pos.getY(), pos.getZ());

            // Build 393's rule: the node the socket points at is the FIRST step of the chain, not the one
            // before it - start() runs it, fire() would skip it.
            int stepped = BlockTrigger351.start(player, graph, start.id, where);
            note("door: " + key + " was opened by " + player.getName().getString()
                    + " - " + stepped + " node(s) stepped from the card's «открытие» socket");
            return stepped > 0;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /** One line per failure class, written once: a chain that does not run must say why, not be silent. */
    private static void why(String reason) {
        String line = "door: the «открытие» chain did not run for this click - " + reason;
        if (WAITED.add(line) && WAITED.size() <= 16) LOG.info(line);
    }

    private static final java.util.Set<String> WAITED = new java.util.HashSet<>();

    private static void note(String line) {
        if (said >= 12) return;
        said++;
        LOG.info(line);
    }

    /** One line per failure class, never a log flood (the rule of build 349). */
    private static void fail(Throwable failure) {
        String message = String.valueOf(failure);
        if (!WARNED.add(message) || WARNED.size() > 16) return;
        LOG.warn("door: the «открытие» chain of a door could not run: " + message);
    }
}
