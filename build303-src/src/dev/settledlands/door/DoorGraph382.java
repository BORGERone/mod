package dev.settledlands.door;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 382: a door can carry its own graph - right click it with the NPC wand and the stock graph
 * editor opens on that door.
 *
 * <p>The author's request: «при тапе ПКМом жезлом настройки нпс по двери - создастся собственный граф
 * двери, где по дефолту будет нода с 2 ползунками (Кол-во чисел, Скорость) и тумблером
 * открыта/закрыта». So the editor, the canvas, the wires, undo, copy and save are the stock ones (the
 * same door build 351 opened for a block), and the door's graph is created with one node in it the
 * first time the door is opened. That node is {@link #DOOR_TYPE} and it is the door:</p>
 *
 * <ul>
 *   <li><b>Кол-во чисел</b> - how many keys the hack panel asks for at this door (4..10);</li>
 *   <li><b>Скорость</b> - how often one of those keys changes, in seconds (1..10, the build-377
 *       panel always used 5);</li>
 *   <li><b>открыта / закрыта</b> - the author's own rule: «Закрыта» means the door is opened
 *       <b>only</b> by solving the panel, «Открыта» means it is an ordinary door again.</li>
 * </ul>
 *
 * <p>How the third one is done: it is the lock of build 368. «Закрыта» adds both halves of the door to
 * {@code DoorStore368}, «Открыта» takes them out - so the refusal of a plain click, the unbreakable
 * door, the 8 s guard of build 371, the panel of build 377 and the lock sync of build 376 all keep
 * working without one new rule. The two keys the panel reads are pushed to the clients through
 * {@code DoorSync375}, because the panel is opened locally.</p>
 *
 * <p>Where the three values live: a stock graph node has no free fields, so the door node uses three
 * saved fields of the node that nothing else reads for this type -
 * {@code Node#randomMin} (keys), {@code Node#timerInterval} (seconds) and {@code Node#timerRepeat}
 * (sealed). {@link #FIELD_NOTE} keeps that next to the code that depends on it. They survive save,
 * load, undo, copy and the trip to the server with the rest of the graph.</p>
 *
 * <p>Cost: one hash lookup per wand click, one per graph read in the panel's open, and nothing at all
 * per tick.</p>
 */
public final class DoorGraph382 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorGraph382");

    /** Ids the door editor answers to. Far away from entity ids and from the block range. */
    public static final int ID_MAX = -2_000_200_000;
    public static final int ID_MIN = -2_000_299_999;

    /**
     * The node type of a door. The stock graph knows the types 0..15 plus their sub-kinds, and none of
     * them is a door, so the type is an integer of our own: the canvas paints the stock card (size,
     * colour, selection, drag) and {@code NpcDoorCard382Mixin} writes what is inside it.
     */
    public static final int DOOR_TYPE = 40;

    /** Where the three controls are kept in a node. See the class comment. */
    public static final String FIELD_NOTE =
            "door node: randomMin = keys, timerInterval = seconds, timerRepeat = sealed";

    public static final int MIN_KEYS = 4;
    public static final int MAX_KEYS = 10;
    public static final int DEFAULT_KEYS = 6;

    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 10;
    public static final int DEFAULT_SPEED = 5;

    /** Outer bound on remembered editor ids; the session map is reset when it grows past this. */
    private static final int MAX_IDS = 8192;

    private static final Map<String, Integer> BY_KEY = new HashMap<>();
    private static final Map<Integer, String> BY_ID = new HashMap<>();
    private static int nextSlot;

    private static final Set<String> WARNED = new HashSet<>();

    private DoorGraph382() {}

    public static boolean isDoorEditorId(int id) {
        return id >= ID_MIN && id <= ID_MAX;
    }

    /** The id the editor uses for this door; stable for the session, so a re-open keeps the same one. */
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

    /** The door behind an editor id, or null when that id was never handed out in this session. */
    public static String keyOf(int id) {
        return BY_ID.get(id);
    }

    // ---- the door in the world ----------------------------------------------

    /**
     * The lower half of the door at this position.
     *
     * <p>A door is two blocks; the graph of one door must be one entry, so the position is lowered to
     * the lowest part {@link Doors368#parts} finds. That walk is the same one the lock store of build
     * 368 uses, so the two can never disagree about which blocks are one door - and it needs no
     * property of the state, which is the one thing this build would otherwise have had to guess.</p>
     */
    public static BlockPos lower(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        try {
            BlockPos low = pos;
            for (BlockPos part : Doors368.parts(level, pos)) {
                if (part != null && part.getY() < low.getY()) low = part;
            }
            return low;
        } catch (Throwable failure) {
            fail(failure);
            return pos;
        }
    }

    public static boolean isDoorAt(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && Doors368.isDoor(level.getBlockState(pos));
    }

    // ---- the node ------------------------------------------------------------

    public static boolean isDoorNode(NpcDialogueGraph.Node node) {
        return node != null && node.type == DOOR_TYPE;
    }

    /** The door node of a graph: the entry node when it is one, else the first door node. */
    public static NpcDialogueGraph.Node doorNode(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return null;
        NpcDialogueGraph.Node entry = graph.get(graph.entry);
        if (isDoorNode(entry)) return entry;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (isDoorNode(node)) return node;
        }
        return null;
    }

    public static int keys(NpcDialogueGraph.Node node) {
        if (!isDoorNode(node)) return DEFAULT_KEYS;
        return clamp(node.randomMin, MIN_KEYS, MAX_KEYS);
    }

    public static int speed(NpcDialogueGraph.Node node) {
        if (!isDoorNode(node)) return DEFAULT_SPEED;
        return clamp(node.timerInterval, MIN_SPEED, MAX_SPEED);
    }

    public static boolean sealed(NpcDialogueGraph.Node node) {
        return isDoorNode(node) && node.timerRepeat;
    }

    public static void setKeys(NpcDialogueGraph.Node node, int value) {
        if (isDoorNode(node)) node.randomMin = clamp(value, MIN_KEYS, MAX_KEYS);
    }

    public static void setSpeed(NpcDialogueGraph.Node node, int value) {
        if (isDoorNode(node)) node.timerInterval = clamp(value, MIN_SPEED, MAX_SPEED);
    }

    public static void setSealed(NpcDialogueGraph.Node node, boolean value) {
        if (isDoorNode(node)) node.timerRepeat = value;
    }

    /** The keys and the speed of a door graph, or null when the graph has no door node. */
    public static int[] settings(NpcDialogueGraph graph) {
        NpcDialogueGraph.Node node = doorNode(graph);
        return node == null ? null : new int[] {keys(node), speed(node), sealed(node) ? 1 : 0};
    }

    /**
     * The graph a door starts with: one node, the door itself, in the middle of the canvas.
     *
     * <p>{@code sealed} is the state the door is in right now, so opening the graph of a locked door
     * shows «Закрыта» rather than a lie the author would have to correct first.</p>
     */
    public static NpcDialogueGraph defaultGraph(boolean sealed) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node node = graph.add(DOOR_TYPE, 120, 120);
        if (node != null) {
            node.randomMin = DEFAULT_KEYS;
            node.timerInterval = DEFAULT_SPEED;
            node.timerRepeat = sealed;
            graph.entry = node.id;
        }
        return graph;
    }

    /** Makes sure the graph has a door node - a graph that arrived empty or from an older file. */
    public static NpcDialogueGraph withDoorNode(NpcDialogueGraph graph, boolean sealed) {
        if (graph == null) return defaultGraph(sealed);
        if (graph.nodes == null || graph.nodes.isEmpty() || doorNode(graph) == null) {
            NpcDialogueGraph fresh = defaultGraph(sealed);
            NpcDialogueGraph.Node source = doorNode(fresh);
            NpcDialogueGraph.Node made = graph.add(DOOR_TYPE, 120, 120);
            if (made != null && source != null) {
                made.randomMin = source.randomMin;
                made.timerInterval = source.timerInterval;
                made.timerRepeat = sealed;
                graph.entry = made.id;
            }
            return graph;
        }
        return graph;
    }

    // ---- opening the editor -------------------------------------------------

    /**
     * Hand the door's graph to the stock graph editor. Runs on the server, once per wand click.
     * Returns false when the player may not edit or the door is not a door.
     */
    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"),
                    true);
            return false;
        }
        if (!isDoorAt(level, pos)) return false;
        try {
            String key = DoorGraph382Store.lowerKey(level, pos);
            DoorGraph382Store store = DoorGraph382Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            boolean sealed = locked(level, pos);
            // A door that has never been edited opens with its own node in it, and that node is
            // stored at once - the author sees the door, not an empty canvas that would say "nothing
            // here" about a door that does have settings.
            if (graph == null) {
                graph = defaultGraph(sealed);
                if (store != null) store.put(key, graph);
            } else {
                graph = withDoorNode(graph, sealed);
            }
            CompoundTag data = new CompoundTag();
            data.put("DialogueGraph", graph.save());
            NpcNet.sendRaw(player, new NpcNet.CfgView(idFor(key), data));
            player.displayClientMessage(Component.translatable("message.settledlands.door.graph_open",
                    Component.literal(coords(pos))), true);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    // ---- saving -------------------------------------------------------------

    /**
     * A save that arrived for a door id. Called from the {@code GuideNpc#applyConfig} hook, which
     * cancels the stock body for these ids - there is no entity to write to.
     */
    public static void save(ServerPlayer player, int id, CompoundTag data) {
        if (player == null || data == null) return;
        String key = keyOf(id);
        if (key == null || key.isEmpty()) return;
        ServerLevel level = player.level() instanceof ServerLevel server ? server : null;
        BlockPos pos = DoorStore368.positionOf(key);
        try {
            CompoundTag tag = data.getCompound("DialogueGraph");
            NpcDialogueGraph graph = tag.isEmpty() ? null : NpcDialogueGraph.load(tag);
            if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) {
                // An empty graph is no graph: the entry is dropped and the door is an ordinary door
                // again, because a door with no graph cannot say it is closed.
                DoorGraph382Store store = DoorGraph382Store.get(player.getServer());
                if (store != null) store.put(key, null);
                applySeal(level, pos, false);
                DoorSync375.sendParams(level);
                player.displayClientMessage(Component.translatable(
                        "message.settledlands.door.graph_cleared", Component.literal(coords(pos))),
                        true);
                return;
            }
            graph = withDoorNode(graph, locked(level, pos));
            NpcDialogueGraph.Node node = doorNode(graph);
            if (node != null) {
                setKeys(node, keys(node));
                setSpeed(node, speed(node));
            }
            DoorGraph382Store store = DoorGraph382Store.get(player.getServer());
            if (store == null) return;
            if (!store.put(key, graph)) {
                player.displayClientMessage(Component.translatable(
                        "message.settledlands.door.graph_full",
                        Component.literal(String.valueOf(DoorGraph382Store.MAX_DOORS))), false);
                return;
            }
            boolean sealed = sealed(node);
            // The lock is written through the level the door is in: if the editor has been moved to
            // another dimension in the meantime the position is not a door here, and the flag waits
            // for the next save instead of locking a wall.
            if (isDoorAt(level, pos)) applySeal(level, pos, sealed);
            // The panel is opened locally, so the two numbers it shows have to be told to the clients
            // that may walk up to this door.
            DoorSync375.sendParams(level);
            player.displayClientMessage(Component.translatable("message.settledlands.door.graph_saved",
                    Component.literal(coords(pos)),
                    Component.literal(sealed ? tr("gui.settledlands.door.sealed")
                            : tr("gui.settledlands.door.unsealed"))), true);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    /**
     * Makes the door closed to a plain click or an ordinary door again - through the lock of build 368,
     * so every rule that already reads the lock follows without knowing about this build.
     */
    public static void applySeal(ServerLevel level, BlockPos pos, boolean sealed) {
        if (level == null || pos == null) return;
        try {
            DoorStore368 store = DoorStore368.get(level);
            if (store == null) return;
            boolean before = store.isLocked(level, pos);
            if (sealed) store.lock(level, pos);
            else store.unlock(level, pos);
            if (before != sealed) {
                // Whatever the client guessed, the answer arrives in the same tick (build 371/376).
                DoorSync375.sendDelta(level, pos, sealed);
                if (!sealed && Doors368.anyOpen(level, pos)) Doors368.setOpen(level, pos, false);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public static boolean locked(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        try {
            DoorStore368 store = DoorStore368.get(level);
            return store != null && store.isLocked(level, pos);
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    // ---- helpers ------------------------------------------------------------

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** {@code 12 64 -30} - for messages, without the dimension. */
    public static String coords(BlockPos pos) {
        if (pos == null) return "?";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static String tr(String key) {
        try {
            return Component.translatable(key).getString();
        } catch (Throwable failure) {
            return key;
        }
    }

    /** One line per failure class, never a log flood (the rule of build 349). */
    public static void fail(Throwable failure) {
        String message = String.valueOf(failure);
        if (!WARNED.add(message) || WARNED.size() > 16) return;
        LOG.warn("door graph pass skipped: " + message);
    }

    /** Unused but kept for the gate: the list of saved fields this class reads. */
    static List<String> fields() {
        return List.of("randomMin", "timerInterval", "timerRepeat");
    }
}
