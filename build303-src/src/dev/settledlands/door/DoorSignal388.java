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
 * Build 388: what runs when a door is opened - the door's graph, through the interpreter every other
 * graph uses.
 *
 * <p>Build 386 had written its own three-node walk for the door («Открытие» -> «Кто open» -> «В радиусе»)
 * and the author's answer was «сделай реализацию как в других графах». This class is that answer: it
 * starts the chain and hands it to {@link BlockTrigger351#fire}, exactly as the block's own click does
 * (build 351), so every stock node in a door's graph behaves the way it behaves in a block's graph or an
 * NPC's:</p>
 *
 * <ul>
 *   <li>a <b>«Триггер»</b> runs its action with {@code %player%} filled in - and that player is the one
 *       who opened the door, which is the «кто открыл» the author asked for, in the stock placeholder
 *       instead of in a type of our own;</li>
 *   <li><b>«Реплика»</b> and <b>«Кнопка»</b> speak through the stock dialogue window with the door as the
 *       speaker, and the chain waits for the window;</li>
 *   <li><b>«Условие If»</b>, <b>«Переменная»</b> and <b>«Случайность»</b> do what they do everywhere
 *       else, against the same {@code NpcPoints} the NPCs use;</li>
 *   <li><b>«Катсцена»</b> plays its scene for the opener;</li>
 *   <li>the event nodes («В радиусе», «Время», «Таймер», «Цель») are the stock ones; a walk steps over
 *       them, as it does in a block's graph - their tick pass is the stock one, not ours.</li>
 * </ul>
 *
 * <p>The chain starts where every other graph starts: at the node marked <b>«Начало»</b>. If the door's
 * graph has no start node, the chain starts at the lowest-numbered stock node; a graph with nothing but
 * the door card does nothing at all, which is what a door that was never edited should do.</p>
 *
 * <p>Cost, because this runs inside a door click: one store lookup, one walk, and nothing per tick. The
 * walk itself is bounded by the interpreter (256 nodes, a visited set), so a loop drawn by hand cannot
 * hang a tick, and the same opening cannot run the chain twice - one click arrives once per hand.</p>
 */
public final class DoorSignal388 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorSignal388");

    /** One click arrives once per hand, and one opening must run the chain once. */
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final long GUARD_MS = 400L;

    private static final Set<String> WARNED = new HashSet<>();

    private DoorSignal388() {}

    /**
     * Runs the door's graph for the player who opened it.
     *
     * @return true when a chain was started
     */
    public static boolean fire(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        try {
            DoorGraph382Store store = DoorGraph382Store.get(level.getServer());
            if (store == null) return false;
            String key = DoorGraph382Store.lowerKey(level, pos);
            if (key == null || key.isEmpty()) return false;
            NpcDialogueGraph graph = store.graph(key);
            if (graph == null) return false;
            NpcDialogueGraph.Node start = startNode(graph);
            if (start == null) return false;

            String name = player.getName().getString();
            String stamp = key + "@" + name;
            long now = System.currentTimeMillis();
            Long last = RECENT.get(stamp);
            if (last != null && now - last < GUARD_MS) return false;
            if (RECENT.size() > 256) RECENT.clear();
            RECENT.put(stamp, now);

            // The door stands where the block stands in a block's graph: the window of a «Реплика»
            // aims its camera at these coordinates, and the store key names the door in the log.
            BlockTalk358.Where where = new BlockTalk358.Where(level, key,
                    pos.getX(), pos.getY(), pos.getZ());
            boolean ran = BlockTrigger351.fire(player, graph, start, where);
            LOG.info("door: the graph of " + key + " ran for " + name + " from node #" + start.id
                    + (ran ? "" : " - the interpreter declined it"));
            return ran;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * The node the chain starts at: the «Начало» of the graph, like every other graph.
     *
     * <p>A door's graph created by build 382 holds the door card and nothing else, and the card is not
     * the start of anything - it is the door's settings. So the card is never the start node, and a graph
     * with nothing but the card does nothing when the door opens.</p>
     */
    public static NpcDialogueGraph.Node startNode(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) return null;
        NpcDialogueGraph.Node entry = graph.get(graph.entry);
        if (entry != null && entry.type != DoorGraph382.DOOR_TYPE) return entry;
        List<NpcDialogueGraph.Node> rest = new ArrayList<>();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && node.type != DoorGraph382.DOOR_TYPE) rest.add(node);
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
