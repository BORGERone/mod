package dev.settledlands.door;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
     * Runs the door's «открытие» chain for the player who opened it.
     *
     * @return true when a chain was started - false for an unwired socket, a door with no graph, or a
     *         repeated call in the same quarter of a second
     */
    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        try {
            DoorGraph382Store store = DoorGraph382Store.get(level.getServer());
            if (store == null) return false;
            String key = DoorGraph382Store.lowerKey(level, pos);
            if (key == null || key.isEmpty()) return false;
            NpcDialogueGraph graph = store.graph(key);
            if (graph == null) return false;

            NpcDialogueGraph.Node start = DoorGraph382.openChainStart(graph);
            if (start == null) return false;

            String stamp = key + "@" + player.getName().getString();
            long now = System.currentTimeMillis();
            Long last = RECENT.get(stamp);
            if (last != null && now - last < GUARD_MS) return false;
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
