package dev.settledlands.door;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 388/389: what runs when a door is hacked - the door's graph, through the interpreter every other
 * graph uses.
 *
 * <p>Build 388 had made a door's graph a stock graph: the chain starts at «Начало» and
 * {@link BlockTrigger351#fire} runs it, so every stock node behaves the way it does in a block's graph or
 * an NPC's. Build 389 gives that chain the start the author drew in words: «один выходной узел сигнала и
 * синий узел цели ... даёт импульс если дверь взломать ... передаёт ссылку на того кто взломал дверь».</p>
 *
 * <ul>
 *   <li><b>the pulse</b> - every «Импульс» node ({@link DoorGraph382#SIGNAL_TYPE}) of the graph is fired,
 *       once, and <b>only</b> when the door is hacked: the lock panel of the door was solved. An ordinary
 *       click on a door that is not sealed does not pulse - that is not a hack;</li>
 *   <li><b>the reference</b> - the player is the source of the whole chain, so {@code %player%} in a
 *       «Триггер» or a «Реплика» after the pulse is the hacker himself; and every blue «Цель» node
 *       ({@link DoorGraph382#TARGET_TYPE}) of the graph is stamped with his name, which is the reference
 *       the author can see on the card and that the graph keeps in the world file.</li>
 * </ul>
 *
 * <p>A graph built by build 388 has no «Импульс» node - it starts at «Начало». Such a graph is still run
 * on a hack, from that start node, so nothing that worked yesterday stops working today.</p>
 *
 * <p>Cost, because this runs inside a door click: one store lookup, one stamp per target node, one walk
 * per signal node, and nothing per tick. The walk itself is bounded by the interpreter (256 nodes, a
 * visited set), so a loop drawn by hand cannot hang a tick, and the same hack cannot run the chain twice -
 * the panel sends once.</p>
 */
public final class DoorSignal388 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorSignal388");

    /** One click arrives once per hand, and one hack must run the chain once. */
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final long GUARD_MS = 400L;

    private static final Set<String> WARNED = new HashSet<>();

    private DoorSignal388() {}

    /**
     * Runs the door's graph for the player who hacked it: stamps him into every blue target node and
     * pulses every signal node.
     *
     * @return true when at least one chain was started
     */
    public static boolean hack(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        try {
            DoorGraph382Store store = DoorGraph382Store.get(level.getServer());
            if (store == null) return false;
            String key = DoorGraph382Store.lowerKey(level, pos);
            if (key == null || key.isEmpty()) return false;
            NpcDialogueGraph graph = store.graph(key);
            if (graph == null) return false;

            String name = player.getName().getString();
            String stamp = key + "@" + name;
            long now = System.currentTimeMillis();
            Long last = RECENT.get(stamp);
            if (last != null && now - last < GUARD_MS) return false;
            if (RECENT.size() > 256) RECENT.clear();
            RECENT.put(stamp, now);

            // The reference: the player who hacked this door, written into every blue target node of the
            // graph. Written before the chain runs, so a node of the chain can already read it back.
            boolean recorded = false;
            if (graph.nodes != null) {
                for (NpcDialogueGraph.Node node : graph.nodes.values()) {
                    if (DoorGraph382.isTargetNode(node)) {
                        DoorGraph382.setOpener(node, name);
                        recorded = true;
                    }
                }
            }

            // The door stands where a block stands in a block's graph: the window of a «Реплика» aims
            // its camera at these coordinates, and the store key names the door in the log.
            BlockTalk358.Where where = new BlockTalk358.Where(level, key,
                    pos.getX(), pos.getY(), pos.getZ());

            List<NpcDialogueGraph.Node> starts = DoorGraph382.signalNodes(graph);
            boolean legacy = starts.isEmpty();
            if (legacy) {
                NpcDialogueGraph.Node start = startNode(graph);
                if (start != null) starts.add(start);
            }

            boolean ran = false;
            for (NpcDialogueGraph.Node start : starts) {
                if (start == null) continue;
                ran |= BlockTrigger351.fire(player, graph, start, where);
            }

            if (recorded) {
                // The name has to survive the session: the graph is the store's own, saved by put().
                store.put(key, graph);
            }
            LOG.info("door: " + key + " was hacked by " + name + " - "
                    + (starts.isEmpty() ? "no signal node, nothing to pulse"
                            : starts.size() + " pulse(s) from " + (legacy ? "«Начало» (a graph of build 388 "
                                    + "or older)" : "the signal node(s)"))
                    + (recorded ? ", and the target holds his name" : ", and there is no target node"));
            return ran;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * The node a graph of build 388 starts at when it has no signal node: the «Начало» of the graph,
     * never the door card (the card is the door's settings, not a step of any chain).
     */
    public static NpcDialogueGraph.Node startNode(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) return null;
        NpcDialogueGraph.Node entry = graph.get(graph.entry);
        if (entry != null && !DoorGraph382.isDoorNode(entry)) return entry;
        List<NpcDialogueGraph.Node> rest = new ArrayList<>();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && !DoorGraph382.isDoorNode(node)) rest.add(node);
        }
        if (rest.isEmpty()) return null;
        rest.sort((left, right) -> Integer.compare(left.id, right.id));
        return rest.get(0);
    }

    /** One line per failure class, never a log flood (the rule of build 349). */
    private static void fail(Throwable failure) {
        String message = String.valueOf(failure);
        if (!WARNED.add(message) || WARNED.size() > 16) return;
        LOG.warn("door: the graph of a door could not run: " + message);
    }
}
