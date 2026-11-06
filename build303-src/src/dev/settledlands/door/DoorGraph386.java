package dev.settledlands.door;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 386: what happens when a door is opened - the signal of the door's own graph.
 *
 * <p>The author asked for a node that «подаёт сигнал, когда кто-то откроет дверь», and for a blue output
 * node «с ссылкой на того, кто конкретно открыл дверь». This class is the half that runs: every time a
 * player opens a door that has one, the chain is walked from the «Открытие» node through the stock
 * {@code next} links the editor already draws.</p>
 *
 * <ul>
 *   <li>{@link DoorGraph382#WHO_TYPE} «Кто открыл» - the blue output: the name of the opener is written
 *       into the node, so the author sees in the editor that the chain reached it (and every later node
 *       that wants the opener reads it from there);</li>
 *   <li>{@link DoorGraph382#RADIUS_TYPE} «В радиусе» - the gate: if the opener is further from the door
 *       than the node's radius, the chain stops there and nothing after it runs.</li>
 * </ul>
 *
 * <p>Safety, because this runs inside a door click: the walk is bounded ({@link #MAX_STEPS}), a node
 * visited twice ends it (a loop in a hand-made graph must not lock a server tick), and every failure is
 * swallowed with one log line per failure class - the door itself is already open by then and nothing
 * here may undo that.</p>
 *
 * <p>Cost: one store lookup and a bounded walk per door opening, and nothing at all per tick.</p>
 */
public final class DoorGraph386 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorGraph386");

    /** A graph is a picture, not a program: a chain longer than this is a mistake, not work. */
    private static final int MAX_STEPS = 32;

    private static final Set<String> WARNED = new HashSet<>();

    /**
     * One click arrives once per hand, and the same opening must run the chain once: a stamp per door
     * and player, good for a fraction of a second. Cheap, and it keeps a later node from doing its work
     * twice when the chain grows side effects.
     */
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final long GUARD_MS = 400L;

    private DoorGraph386() {}

    /**
     * Runs the door's signal for one player, on the server, once per opening.
     *
     * @return true when the graph had an «Открытие» node and the chain ran - the caller uses it only
     *         for its log line
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
            NpcDialogueGraph.Node event = DoorGraph382.eventNode(graph);
            if (event == null) return false;

            String name = player.getName().getString();
            String stamp = key + "@" + name;
            long now = System.currentTimeMillis();
            Long last = RECENT.get(stamp);
            if (last != null && now - last < GUARD_MS) return false;
            if (RECENT.size() > 256) RECENT.clear();
            RECENT.put(stamp, now);
            boolean recorded = false;
            boolean ran = false;
            Set<Integer> seen = new HashSet<>();
            NpcDialogueGraph.Node node = event;
            int steps = 0;
            while (node != null && steps++ < MAX_STEPS) {
                // A node reached twice is a loop: stop, do not spin. The author can make one by wiring
                // two nodes to each other, and a door click must never hang a tick because of it.
                if (!seen.add(node.id)) break;
                if (DoorGraph382.isWhoNode(node)) {
                    DoorGraph382.setOpener(node, name);
                    recorded = true;
                    ran = true;
                } else if (DoorGraph382.isRadiusNode(node)) {
                    ran = true;
                    if (!within(player, pos, DoorGraph382.radius(node))) {
                        LOG.info("door: the signal of " + key + " stopped at the radius node #" + node.id
                                + " - " + name + " is further than "
                                + DoorGraph382.radius(node) + " blocks");
                        break;
                    }
                }
                int next = node.next;
                if (next < 0) break;
                node = graph.get(next);
            }
            if (recorded) {
                // The name travels back to the author through the graph itself - no new packet, and the
                // save path of build 384 already stores every field the serialiser writes.
                store.put(key, graph);
            }
            if (ran) {
                LOG.info("door: the signal of " + key + " ran for " + name
                        + (recorded ? " and the opener was written into the «Кто открыл» node" : ""));
            }
            return ran;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /** True when the player stands within {@code radius} blocks of the door. */
    private static boolean within(ServerPlayer player, BlockPos pos, int radius) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        double limit = (double) radius * (double) radius;
        return dx * dx + dy * dy + dz * dz <= limit;
    }

    /** One line per failure class, never a log flood (the rule of build 349). */
    private static void fail(Throwable failure) {
        String message = String.valueOf(failure);
        if (!WARNED.add(message) || WARNED.size() > 16) return;
        LOG.warn("door: the signal of a door could not run: " + message);
    }
}
