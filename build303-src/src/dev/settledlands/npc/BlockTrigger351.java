package dev.settledlands.npc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 351/352: a block can carry a graph, right clicking it with an empty hand runs that graph, and
 * a "В радиусе" node in it fires by itself while somebody stands in its radius.
 *
 * <p>The block is opened in the very same editor the NPCs use - the stock screen is handed a profile
 * whose main graph is the block's graph, under an id of its own
 * ({@code BLOCK_ID_MAX..BLOCK_ID_MIN}). The screen already speaks one language with the server
 * ({@code CfgView} in, {@code CfgSave} out), so nothing new is invented on the wire: the id tells the
 * server which block the save belongs to, and {@code GuideNpc#applyConfig} is intercepted for those
 * ids before it ever looks for an entity.</p>
 *
 * <p>What runs from a block is the chain that the block does not need a body for - the palette of the
 * block graph holds exactly those nodes:</p>
 *
 * <ul>
 *   <li><b>Реплика</b> - build 357: the block says a line to the player who clicked it, in the chat -
 *       the node text with the stock {@code %player%} / {@code %relation%} placeholders filled, and
 *       the chain continues from it at once. There is no body to show a dialogue window for, so the
 *       chat is the block's voice;</li>
 *   <li><b>Триггер</b> - runs its action/command with the clicking player as the command source, so
 *       {@code @p} is that player;</li>
 *   <li><b>Условие If</b> - compares a variable (or the variable wired into it) and takes the true or
 *       the false branch, exactly like the NPC graph does;</li>
 *   <li><b>Переменная</b> - adds its delta to a named variable, stored in the same {@code NpcPoints}
 *       the NPC graphs use, so a block and an NPC can share one counter;</li>
 *   <li><b>Случайность</b> - picks one of its branches;</li>
 *   <li><b>В радиусе</b> - build 352: an event node, ticked by {@code RadiusPass354} - when a body of
 *       the chosen kind stands within its radius, its action runs and the chain continues from it.</li>
 * </ul>
 *
 * <p>The author's rule for the click trigger: an empty hand and a graph with a start node. No start
 * node, no graph, anything in the hand - nothing happens and nothing is cancelled, the block keeps
 * doing whatever it did before.</p>
 *
 * <p>Cost: one hash lookup and one chain walk per click, both bounded (256 nodes, a visited set). The
 * radius nodes are the only periodic work and live in {@code RadiusPass354}, not here.</p>
 */
public final class BlockTrigger351 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/BlockTrigger351");

    /** Ids the block editor answers to. Far away from real entity ids and from MOBILITY_EDITOR_ID. */
    public static final int ID_MAX = -2_000_100_000;
    public static final int ID_MIN = -2_000_199_999;

    /** One chain walk follows at most this many nodes; a self link cannot loop the server. */
    private static final int MAX_STEPS = 256;

    /** Outer bound on remembered ids; the editor session map is reset when it grows past this. */
    private static final int MAX_IDS = 8192;

    private static final Map<String, Integer> BY_KEY = new HashMap<>();
    private static final Map<Integer, String> BY_ID = new HashMap<>();
    private static int nextSlot;

    /** Node types the block graph offers; everything else is not in its palette. */
    public static final int[] PALETTE = {
        NpcDialogueGraph.LINE, NpcDialogueGraph.TRIGGER, NpcDialogueGraph.IF,
        NpcDialogueGraph.VARIABLE, NpcDialogueGraph.CHECK, NpcDialogueGraph.RANDOM,
    };

    /**
     * The rows of the stock palette that a block may create, one per type in {@link #PALETTE}.
     * The editor asks {@link #typeForRow} about a click, so this is the one place that decides what
     * a block can build - and {@code BlockPaletteProbe} keeps the two arrays in step.
     */
    public static final int[] PALETTE_ROWS = {0, 2, 3, 4, 5, 13};

    /**
     * Stock palette row -> node type, or -1 when a block cannot run that row. Row 0 is «Реплика»,
     * the node build 357 adds; the rest is the palette of build 351.
     */
    public static int typeForRow(int row) {
        return switch (row) {
            case 0 -> NpcDialogueGraph.LINE;
            case 2 -> NpcDialogueGraph.TRIGGER;
            case 3 -> NpcDialogueGraph.IF;
            case 4 -> NpcDialogueGraph.VARIABLE;
            case 5 -> NpcDialogueGraph.CHECK;
            case 13 -> NpcDialogueGraph.RANDOM;
            default -> -1;
        };
    }

    private static final Set<String> WARNED = new HashSet<>();

    private BlockTrigger351() {}

    public static boolean isBlockEditorId(int id) {
        return id >= ID_MIN && id <= ID_MAX;
    }

    /** True when this node type may live in a block graph. */
    public static boolean allowed(int type) {
        for (int candidate : PALETTE) {
            if (candidate == type) return true;
        }
        return false;
    }

    /** The id the editor uses for this block; stable for the session, so a re-open keeps the same one. */
    public static synchronized int idFor(String key) {
        if (key == null || key.isEmpty()) return ID_MAX;
        Integer known = BY_KEY.get(key);
        if (known != null) return known;
        if (BY_KEY.size() >= MAX_IDS) {
            BY_KEY.clear();
            BY_ID.clear();
            nextSlot = 0;
        }
        int id = ID_MAX - nextSlot++;
        BY_KEY.put(key, id);
        BY_ID.put(id, key);
        return id;
    }

    /** The block behind an editor id, or null when that id was never handed out in this session. */
    public static String keyOf(int id) {
        return BY_ID.get(id);
    }

    // ---- opening the editor -------------------------------------------------

    /**
     * Hand the block's graph to the stock graph editor. Runs on the server, once per wand click.
     * Returns false when the player may not edit or the graph cannot be sent.
     */
    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
            return false;
        }
        String key = BlockTriggerStore351.key(level, pos);
        try {
            BlockTriggerStore351 store = BlockTriggerStore351.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            CompoundTag data = new CompoundTag();
            data.put("BlockGraph", graph == null ? new NpcDialogueGraph().save() : graph.save());
            NpcNet.sendRaw(player, new NpcNet.CfgView(idFor(key), data));
            player.displayClientMessage(
                    Component.translatable("message.settledlands.block.open",
                            Component.literal(BlockTriggerStore351.coords(pos))),
                    true);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    // ---- saving -------------------------------------------------------------

    /**
     * A save that arrived for a block id. Called from the {@code GuideNpc#applyConfig} hook, which
     * cancels the stock body for these ids - there is no entity to write to.
     */
    public static void save(ServerPlayer player, int id, CompoundTag data) {
        if (player == null || data == null) return;
        String key = keyOf(id);
        if (key == null || key.isEmpty()) return;
        try {
            CompoundTag tag = data.getCompound("DialogueGraph");
            NpcDialogueGraph graph = tag.isEmpty() ? null : NpcDialogueGraph.load(tag);
            // An empty graph is no graph: the entry is dropped, so the block stops sparkling and a
            // right click on it does nothing - "cleared" and "never configured" are the same state.
            if (graph != null && graph.nodes.isEmpty()) graph = null;
            BlockTriggerStore351 store = BlockTriggerStore351.get(player.getServer());
            if (store == null) return;
            if (!store.put(key, graph)) {
                player.displayClientMessage(
                        Component.translatable("message.settledlands.block.full",
                                Component.literal(String.valueOf(BlockTriggerStore351.MAX_BLOCKS))),
                        false);
                return;
            }
            String where = key.substring(key.indexOf('@') + 1);
            if (graph == null) {
                player.displayClientMessage(
                        Component.translatable("message.settledlands.block.deleted",
                                Component.literal(where)),
                        true);
                return;
            }
            player.displayClientMessage(
                    Component.translatable("message.settledlands.block.saved",
                            Component.literal(where),
                            Component.literal(Integer.toString(graph.nodes.size()))),
                    true);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    // ---- running the chain --------------------------------------------------

    /**
     * Right click with an empty hand: run the block's chain from its start node. Returns true when the
     * graph fired (which only means the walk started - a chain may legitimately end at once).
     */
    public static boolean run(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null) return false;
        try {
            BlockTriggerStore351 store = BlockTriggerStore351.get(level.getServer());
            if (store == null) return false;
            NpcDialogueGraph graph = store.graph(BlockTriggerStore351.key(level, pos));
            if (graph == null) return false;
            int start = graph.entry;
            if (start < 0 || graph.get(start) == null) return false;  // no start node: nothing happens
            walk(player, graph, start);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * Fire one node of a block graph and continue from it - what a "В радиусе" node does when its
     * radius has somebody in it. Build 352, called from the tick pass.
     */
    public static boolean fire(ServerPlayer source, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        if (graph == null || node == null) return false;
        try {
            Set<Integer> visited = new HashSet<>();
            visited.add(node.id);
            act(source, node);
            fireTriggers(source, graph, node, visited);
            if (node.next >= 0) walk(source, graph, node.next, visited);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * The chain itself, node for node the semantics of the NPC graph: a trigger action is run with the
     * player as source, a variable moves its points, an If compares and picks a branch, a random node
     * picks one of its ports. Trigger lists attached to a node fire too, like they do for an NPC.
     */
    private static void walk(ServerPlayer player, NpcDialogueGraph graph, int start) {
        walk(player, graph, start, new HashSet<>());
    }

    private static void walk(ServerPlayer player, NpcDialogueGraph graph, int start, Set<Integer> visited) {
        int curId = start;
        while (curId >= 0 && visited.size() < MAX_STEPS && visited.add(curId)) {
            NpcDialogueGraph.Node cur = graph.get(curId);
            if (cur == null) return;

            if (cur.type == NpcDialogueGraph.LINE) {
                // The block's voice: the line goes to the player in the chat, and the chain walks
                // on at once - there is no dialogue window to wait for a click in.
                say(player, cur);
                act(player, cur);
                fireTriggers(player, graph, cur, visited);
                curId = cur.next;
                continue;
            }
            if (cur.type == NpcDialogueGraph.TRIGGER) {
                act(player, cur);
                fireTriggers(player, graph, cur, visited);
                curId = cur.next;
                continue;
            }
            if (cur.type == NpcDialogueGraph.VARIABLE) {
                if (!cur.pointsName.isBlank() && cur.varValue != 0 && player != null
                        && player.getServer() != null) {
                    NpcPoints.get(player.getServer())
                            .add(player.getUUID(), cur.pointsName, cur.pointsShared, cur.varValue);
                }
                act(player, cur);
                fireTriggers(player, graph, cur, visited);
                curId = cur.next;
                continue;
            }
            if (cur.type == NpcDialogueGraph.IF) {
                boolean pass = GuideDialogue.evalCondition(value(player, graph, cur), cur.varOp,
                        cur.varCompare);
                act(player, cur);
                fireTriggers(player, graph, cur, visited);
                curId = pass ? cur.next : cur.falseNext;
                continue;
            }
            if (cur.type == NpcDialogueGraph.RANDOM) {
                int min = Math.max(1, cur.randomMin);
                int max = Math.max(min, cur.randomMax);
                int port = min + java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1) - 1;
                act(player, cur);
                fireTriggers(player, graph, cur, visited);
                curId = port >= 0 && port < cur.randomBranches.size()
                        ? cur.randomBranches.get(port) : -1;
                continue;
            }
            if (RadiusPass354.isRadiusNode(cur)) {
                // An event node: the tick pass fires it, a walk only steps over it.
                curId = cur.next;
                continue;
            }
            // A node the block palette cannot create (an older file, a hand-edited graph): step over
            // it instead of stalling, and say it once per session so it is never a silent surprise.
            warnOnce(cur.type);
            curId = cur.next;
        }
    }

    /** The variable an If node reads: its own name, or the name of the variable node wired into it. */
    private static int value(ServerPlayer player, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        String name = node.pointsName;
        boolean shared = node.pointsShared;
        if (node.varInputNode >= 0) {
            NpcDialogueGraph.Node source = graph.get(node.varInputNode);
            if (source != null && source.type == NpcDialogueGraph.VARIABLE && !source.pointsName.isBlank()) {
                name = source.pointsName;
                shared = source.pointsShared;
            }
        }
        if (name.isBlank() || player == null || player.getServer() == null) return 0;
        return NpcPoints.get(player.getServer()).get(player.getUUID(), name, shared);
    }

    /**
     * A "Реплика" node: the text the author wrote, said to one player in the chat.
     *
     * <p>The placeholders are the stock ones - {@code NpcProfile#fill} replaces {@code %player%} with
     * the name and {@code %relation%} with the relation of the talker; a block has no relation of its
     * own, so that one is always 0. Nothing is sent when the text is blank or there is no player (a
     * radius event may fire for a body that is not a player) - the chain still goes on, because a
     * silent line is a line, not a stop.</p>
     */
    private static void say(ServerPlayer player, NpcDialogueGraph.Node node) {
        if (player == null || node == null || node.text == null || node.text.isBlank()) return;
        try {
            String line = NpcProfile.fill(node.text, player, 0);
            if (line == null || line.isBlank()) return;
            player.displayClientMessage(Component.literal(line), false);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void act(ServerPlayer player, NpcDialogueGraph.Node node) {
        if (node.action == null || node.action.isBlank()) return;
        try {
            // No NPC: the block has no body, and NpcActions handles that by using the player as the
            // command source, so @p in the action is the player who clicked.
            NpcActions.run(player, null, 0, node.action);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void fireTriggers(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, Set<Integer> visited) {
        List<Integer> ids = node.triggers;
        if (ids == null) return;
        for (int i = 0; i < ids.size() && visited.size() < MAX_STEPS; i++) {
            int triggerId = ids.get(i);
            if (!visited.add(triggerId)) continue;
            NpcDialogueGraph.Node trigger = graph.get(triggerId);
            if (trigger == null || trigger.type != NpcDialogueGraph.TRIGGER) continue;
            act(player, trigger);
            fireTriggers(player, graph, trigger, visited);
        }
    }

    private static void warnOnce(int type) {
        String key = "type:" + type;
        if (!WARNED.add(key) || WARNED.size() > 8) return;
        LOG.warn("block graph holds node type {} which a block cannot run - stepped over", type);
    }

    /** One line per failure class, never a log flood: same rule as the armour pass of build 349. */
    public static void fail(Throwable failure) {
        String message = String.valueOf(failure);
        if (!WARNED.add(message) || WARNED.size() > 16) return;
        LOG.warn("block trigger pass skipped: {}", message);
    }
}
