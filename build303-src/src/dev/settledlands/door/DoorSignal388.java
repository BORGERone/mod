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
 * Build 388/389/392: what runs when a door is hacked - the door's graph, through the interpreter every
 * other graph uses.
 *
 * <p>Build 388 had made a door's graph a stock graph: {@link BlockTrigger351#fire} runs it, so every stock
 * node behaves the way it does in a block's graph or an NPC's. Build 389 gave the chain the start the
 * author drew in words («один выходной узел сигнала и синий узел цели ... даёт импульс если дверь
 * взломать»), and did it with two nodes. Build 392 keeps the meaning and drops the nodes: both outputs are
 * sockets of the door card now.</p>
 *
 * <ul>
 *   <li><b>the pulse</b> - the chain that leaves the card's execution socket, and it leaves it <b>only</b>
 *       when the door is hacked: the lock panel of the door was solved. An ordinary click on a door that
 *       is not sealed does not pulse - that is not a hack;</li>
 *   <li><b>the reference</b> - the player is the source of the whole chain, so {@code %player%} in a
 *       «Триггер» or a «Реплика» after the socket is the hacker himself; the card also keeps his name
 *       ({@link DoorGraph382#setOpener}), which is what its blue socket hands to every node wired to it -
 *       {@code %target%} in such a «Реплика» is the hacker.</li>
 * </ul>
 *
 * <p>A graph built by build 388 has its chain at «Начало» and no socket wired: such a graph is still run
 * on a hack, from that start node, so nothing that worked yesterday stops working today. A graph saved by
 * 389-391 still holds the two nodes of that build: they are folded into the card first
 * ({@link DoorGraph382#foldStarter}), and the folded graph is written back.</p>
 *
 * <p>Cost, because this runs inside a door click: one store lookup, one name on the card, one walk, and
 * nothing per tick. The walk itself is bounded by the interpreter (256 nodes, a visited set), so a loop
 * drawn by hand cannot hang a tick, and the same hack cannot run the chain twice - the panel sends
 * once.</p>
 */
public final class DoorSignal388 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorSignal388");

    /** One click arrives once per hand, and one hack must run the chain once. */
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final long GUARD_MS = 400L;

    private static final Set<String> WARNED = new HashSet<>();

    private DoorSignal388() {}

    /**
     * Runs the door's graph for the player who hacked it: writes his name onto the card and runs the chain
     * the card's output socket points at.
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

            // Build 392: a graph saved by 389-391 still has the pulse and the blue target as nodes. They
            // are folded into the card's two sockets here as well as in the editor, because a door may be
            // hacked before its graph is ever opened again.
            boolean folded = DoorGraph382.foldStarter(graph);

            // The reference: the card keeps the name of the player who hacked this door, and its blue
            // socket hands him to every node wired to it. Written before the chain runs, so a node of the
            // chain can already read him back.
            NpcDialogueGraph.Node door = DoorGraph382.doorNode(graph);
            boolean recorded = door != null && name != null && !name.isBlank();
            if (recorded) DoorGraph382.setOpener(door, name);

            // The door stands where a block stands in a block's graph: the window of a «Реплика» aims
            // its camera at these coordinates, and the store key names the door in the log.
            BlockTalk358.Where where = new BlockTalk358.Where(level, key,
                    pos.getX(), pos.getY(), pos.getZ());

            // Build 392: the chain starts where the card's execution socket points. A graph that has no
            // socket wired (build 388) still starts at «Начало», so nothing it does stops working.
            List<NpcDialogueGraph.Node> starts = new ArrayList<>();
            NpcDialogueGraph.Node chain = DoorGraph382.chainStart(graph);
            boolean legacy = chain == null;
            if (chain != null) {
                starts.add(chain);
            } else {
                NpcDialogueGraph.Node start = startNode(graph);
                if (start != null) starts.add(start);
            }

            boolean ran = false;
            for (NpcDialogueGraph.Node start : starts) {
                if (start == null) continue;
                ran |= BlockTrigger351.fire(player, graph, start, where);
            }

            if (recorded || folded) {
                // The name has to survive the session: the graph is the store's own, saved by put().
                store.put(key, graph);
            }
            LOG.info("door: " + key + " was hacked by " + name + " - "
                    + (starts.isEmpty() ? "nothing is wired to the card's output socket, nothing to pulse"
                            : "the chain runs from " + (legacy ? "«Начало» (a graph of build 388 or older)"
                                    : "the card's output socket"))
                    + (recorded ? ", and the card holds his name" : ", and the card did not take his name"));
            return ran;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * The node a graph of build 388 starts at when the card's output socket is not wired: the «Начало» of
     * the graph, never the door card (the card is the door's settings and its two sockets, not a step of
     * any chain).
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
