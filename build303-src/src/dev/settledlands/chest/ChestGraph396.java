package dev.settledlands.chest;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 396: a chest gets the same graph a door has - one card of its own, the stock palette around it,
 * and the stock interpreter behind it.
 *
 * <p>The author, after the four door builds: «Все прекрасно, теперь я хочу сделать такой-же граф для
 * сундуков». So this is the door's design, applied to a chest and nothing else:</p>
 *
 * <ul>
 *   <li><b>a card of its own</b> - {@link #CHEST_TYPE} is one node type of this mod ({@code 50}, the door's
 *       is {@code 40}), titled «Сундук», carrying the chest's two outputs and its two lines of state;</li>
 *   <li><b>two sockets on the card</b>, exactly like the door's (build 392): the round amber socket on the
 *       execution row is where the chain of the chest starts, and the blue diamond under it hands out the
 *       player who opened the chest - {@code %target%} in a «Реплика» is his name, exactly as with a door;
 *   </li>
 *   <li><b>the stock palette</b> - {@link #paletteRows()} is the block's own ({@code BlockTrigger351}), so a
 *       «Реплика», a «Триггер», an «Условие» or a «В радиусе» in a chest's graph are the stock nodes with the
 *       stock semantics, run by the stock interpreter ({@link BlockTrigger351#start});</li>
 *   <li><b>the same editor</b> - the NPC wand opens the graph of the chest it is clicked on, in the very same
 *       screen the NPCs, the blocks and the doors use, and the save comes back through the same packet.</li>
 * </ul>
 *
 * <p><b>When the chain runs.</b> A door has no window of its own, so its chain runs the moment it is
 * cracked. A chest does: the moment it opens the game puts the chest's own window in front of the player,
 * and a dialogue window sent in the same tick is replaced by it - the author would see «ничего не
 * происходит» for the second time in five builds. So the chest's chain runs when that window <b>closes</b>:
 * he takes what he came for, the chest shuts, and the «Реплика» of the chest says its line with nothing in
 * front of it. {@link ChestGraph396Events} is that watch. The card says which moment it is
 * ({@code gui.settledlands.chest.trigger}), so nothing about it is a surprise.</p>
 *
 * <p>Cost: one world-data entry per chest that was given a graph, and a hash lookup per right-click.</p>
 */
public final class ChestGraph396 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestGraph396");

    /** The editor ids of the chests. Next block down from the doors ({@code -2_000_200_000}). */
    public static final int ID_MAX = -2_000_300_000;
    public static final int ID_MIN = -2_000_399_999;

    /** The one node type of this mod in a chest's graph - the card, exactly like the door's 40. */
    public static final int CHEST_TYPE = 50;

    /** The title of the card, in the language the client runs in. */
    public static final String TITLE_KEY = "gui.settledlands.chest.node";

    private static final int MAX_IDS = 8192;
    private static final Map<String, Integer> BY_KEY = new HashMap<>();
    private static final Map<Integer, String> BY_ID = new HashMap<>();
    private static int nextSlot;

    private static final Set<String> WARNED = new HashSet<String>();

    private ChestGraph396() {}

    // ---- ids ----------------------------------------------------------------

    public static boolean isChestEditorId(int id) {
        return id >= ID_MIN && id <= ID_MAX;
    }

    /** The id the editor uses for this chest; stable for the session, so a re-open keeps the same one. */
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

    /** The chest behind an editor id, or null when that id was never handed out in this session. */
    public static String keyOf(int id) {
        return BY_ID.get(id);
    }

    // ---- the chest in the world --------------------------------------------

    /**
     * True when this block is a chest the author can put a graph on.
     *
     * <p>A chest and a barrel are the two plain containers of the game; both are one block, both open a
     * window of their own and both are what the word «сундук» means in a build. A double chest is two
     * blocks and therefore two graphs - the one the author clicked is the one he edits, and the card says
     * where it is. A container from another mod is not covered, and the README says so.</p>
     */
    public static boolean isChest(BlockState state) {
        if (state == null) return false;
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
    }

    public static boolean isChestAt(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && isChest(level.getBlockState(pos));
    }

    // ---- the card -----------------------------------------------------------

    public static boolean isChestNode(NpcDialogueGraph.Node node) {
        return node != null && node.type == CHEST_TYPE;
    }

    /** The only card of this mod in a chest's graph: the two outputs are its sockets, not nodes. */
    public static boolean isChestGraphNode(NpcDialogueGraph.Node node) {
        return isChestNode(node);
    }

    /** Longest name the card keeps. */
    private static final int MAX_OPENER = 32;

    /** The player who opened this chest last, as the card keeps him - the value of the blue socket. */
    public static String opener(NpcDialogueGraph.Node node) {
        if (!isChestNode(node) || node.text == null) return "";
        return node.text.trim();
    }

    /** Records the opener on the card. One name, trimmed, bounded - {@code text} is saved for every node. */
    public static void setOpener(NpcDialogueGraph.Node node, String name) {
        if (!isChestNode(node) || name == null) return;
        String trimmed = name.trim();
        node.text = trimmed.length() <= MAX_OPENER ? trimmed : trimmed.substring(0, MAX_OPENER);
    }

    /** How many times this chest has been opened. {@code varValue} is a saved field, so it survives a save. */
    public static int count(NpcDialogueGraph.Node node) {
        if (!isChestNode(node)) return 0;
        return Math.max(0, node.varValue);
    }

    public static void setCount(NpcDialogueGraph.Node node, int value) {
        if (!isChestNode(node)) return;
        node.varValue = Math.max(0, Math.min(999_999, value));
    }

    /** The card of a graph: the entry node when it is one, else the first card found. */
    public static NpcDialogueGraph.Node chestNode(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return null;
        NpcDialogueGraph.Node entry = graph.get(graph.entry);
        if (isChestNode(entry)) return entry;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (isChestNode(node)) return node;
        }
        return null;
    }

    /**
     * The node the chest's chain starts at: the one the card's execution socket points at. Null when
     * nothing is wired to it - a chest whose socket is empty says nothing, which is honest: the author
     * drew nothing.
     */
    public static NpcDialogueGraph.Node chainStart(NpcDialogueGraph graph) {
        NpcDialogueGraph.Node chest = chestNode(graph);
        if (chest == null || chest.next < 0) return null;
        NpcDialogueGraph.Node start = graph.get(chest.next);
        if (start == null || start == chest) return null;
        return start;
    }

    /**
     * Build 398: the chain the card's <b>second</b> socket («взлом») points at - the one that runs when this
     * chest was cracked rather than simply unlocked and opened. The first socket («открытие») is
     * {@link #chainStart}.
     *
     * <p>It is kept in {@code Node#falseNext}, the second output every node already has: the stock serialiser
     * writes and reads it for every type (the lesson of build 384), so the second chain survives a save with no
     * new field and no change to the store. A card is not an «Условие If», and nothing in the stock
     * interpreter walks {@code falseNext} of anything else, so the field can only mean this.</p>
     */
    public static NpcDialogueGraph.Node hackChainStart(NpcDialogueGraph graph) {
        NpcDialogueGraph.Node chest = chestNode(graph);
        if (chest == null || chest.falseNext < 0) return null;
        NpcDialogueGraph.Node start = graph.get(chest.falseNext);
        if (start == null || start == chest) return null;
        return start;
    }

    /** True when the card's «взлом» socket is wired to a node that still exists. */
    public static boolean hasHackChain(NpcDialogueGraph graph) {
        return hackChainStart(graph) != null;
    }

    /** The rows of a chest's create menu: the block's rows, in the same order and with the same labels. */
    public static int[] paletteRows() {
        return BlockTrigger351.PALETTE_ROWS;
    }

    /** The type made by a row of a chest's create menu - the block's own mapping, not a second one. */
    public static int typeForRow(int row) {
        return BlockTrigger351.typeForRow(row);
    }

    /** The row of the create menu that makes this type, or -1 when a chest cannot run it. */
    public static int rowForType(int type) {
        for (int row = 0; row < 16; row++) {
            if (BlockTrigger351.typeForRow(row) == type) return row;
        }
        return -1;
    }

    /** True when this node type may live in a chest's graph: the card, or anything a block may run. */
    public static boolean allowed(int type) {
        return type == CHEST_TYPE || BlockTrigger351.allowed(type);
    }

    /** The language key of the card's title. */
    public static String labelKey(int type) {
        return TITLE_KEY;
    }

    /** The colour of the chest card: the same brown the door card has had since build 382. */
    public static int colour(int type) {
        return -3725286;
    }

    // ---- the graph ----------------------------------------------------------

    /** A fresh graph of a chest: one card, and «Начало» stands on it - its own output port is the start. */
    public static NpcDialogueGraph defaultGraph() {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node node = graph.add(CHEST_TYPE, 120, 120);
        if (node != null) {
            setCount(node, 0);
            // Build 397: the four numbers of the lock panel, at the door's defaults (6 / 5 s / 1, open).
            ChestLock397.applyDefaults(node);
            graph.entry = node.id;
        }
        return graph;
    }

    /** Makes sure the graph has a card - a graph that arrived empty or from a hand-edited file. */
    public static NpcDialogueGraph withChestNode(NpcDialogueGraph graph) {
        if (graph == null) return defaultGraph();
        if (graph.nodes == null || graph.nodes.isEmpty() || chestNode(graph) == null) {
            NpcDialogueGraph.Node made = graph.add(CHEST_TYPE, 120, 120);
            if (made != null) {
                ChestLock397.applyDefaults(made);
                if (graph.entry < 0 || graph.get(graph.entry) == null) graph.entry = made.id;
            }
            return graph;
        }
        // Build 397: a card written by build 396 has no panel numbers yet - it gets the door's defaults
        // here, so the sliders of the inspector start where the panel does.
        ChestLock397.applyDefaults(chestNode(graph));
        return graph;
    }

    /**
     * A chest's graph holds the chest and the nodes the author put in it - and never the two cards the
     * editor adds to every graph it opens ({@link BlockName359#STATS_TYPE} and {@link BlockName359#TYPE}),
     * which is the rule of build 383 for a door and now the same for a chest.
     */
    public static int stripCards(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return 0;
        int dropped = BlockName359.dropType(graph, BlockName359.STATS_TYPE)
                + BlockName359.dropType(graph, BlockName359.TYPE);
        if (graph.entry >= 0 && !graph.nodes.containsKey(graph.entry)) {
            NpcDialogueGraph.Node node = chestNode(graph);
            if (node != null) graph.entry = node.id;
        }
        return dropped;
    }

    // ---- opening the editor -------------------------------------------------

    /** Hand the chest's graph to the stock graph editor. Runs on the server, once per wand click. */
    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
            return false;
        }
        if (!isChestAt(level, pos)) return false;
        try {
            String key = ChestGraph396Store.key(level, pos);
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) {
                graph = defaultGraph();
                if (store != null) store.put(key, graph);
            } else {
                graph = withChestNode(graph);
                stripCards(graph);
            }
            CompoundTag data = new CompoundTag();
            data.put("DialogueGraph", graph.save());
            NpcNet.sendRaw(player, new NpcNet.CfgView(idFor(key), data));
            player.displayClientMessage(Component.translatable("message.settledlands.chest.graph_open",
                    Component.literal(coords(pos))), true);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    // ---- saving -------------------------------------------------------------

    /** A save that arrived for a chest id - the same seam the door's save uses. */
    public static void save(ServerPlayer player, int id, CompoundTag data) {
        if (player == null || data == null) return;
        String key = keyOf(id);
        if (key == null || key.isEmpty()) return;
        BlockPos pos = ChestGraph396Store.positionOf(key);
        try {
            CompoundTag tag = data.getCompound("DialogueGraph");
            NpcDialogueGraph graph = tag.isEmpty() ? null : NpcDialogueGraph.load(tag);
            if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) {
                // An empty graph is no graph: the chest is an ordinary chest again.
                ChestGraph396Store store = ChestGraph396Store.get(player.getServer());
                if (store != null) store.put(key, null);
                player.displayClientMessage(Component.translatable(
                        "message.settledlands.chest.graph_cleared", Component.literal(coords(pos))), true);
                return;
            }
            graph = withChestNode(graph);
            stripCards(graph);
            // Build 397: the panel numbers are written back before the graph is stored, so a card that
            // carried nothing but the build-396 fields saves as a chest with a working lock panel.
            ChestLock397.applyDefaults(chestNode(graph));
            ChestGraph396Store store = ChestGraph396Store.get(player.getServer());
            if (store == null) return;
            if (!store.put(key, graph)) {
                player.displayClientMessage(Component.translatable("message.settledlands.chest.graph_full",
                        Component.literal(String.valueOf(ChestGraph396Store.MAX_CHESTS))), false);
                return;
            }
            player.displayClientMessage(Component.translatable("message.settledlands.chest.graph_saved",
                    Component.literal(coords(pos))), true);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    // ---- the moment the chest is opened -------------------------------------

    /**
     * Build 396: records the opener on the card and counts the open - called the moment a player opens a
     * chest that has a graph.
     *
     * <p>Both facts are written into the card and the graph is stored at once, so the card shows «Открыл:
     * BORGER» the next time the graph is opened even if the world is restarted in between. The chain is not
     * started here: it starts when the chest's own window closes ({@link ChestGraph396Events}).</p>
     *
     * @return the node the card's socket points at, or null when the socket is not wired
     */
    public static NpcDialogueGraph.Node noteOpened(ServerPlayer player, ServerLevel level, BlockPos pos,
            String key) {
        if (player == null || level == null || pos == null || key == null || key.isEmpty()) return null;
        try {
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return null;
            graph = withChestNode(graph);
            NpcDialogueGraph.Node card = chestNode(graph);
            if (card == null) return null;
            setOpener(card, player.getName().getString());
            setCount(card, count(card) + 1);
            if (store != null) store.put(key, graph);
            return chainStart(graph);
        } catch (Throwable failure) {
            fail(failure);
            return null;
        }
    }

    /** Build 396: runs the chain of a chest from the node its socket points at. What a close does. */
    public static boolean run(ServerPlayer player, ServerLevel level, BlockPos pos, String key) {
        return run(player, level, pos, key, false);
    }

    /**
     * Build 398: the chain of a chest's close, from the socket the opening belongs to.
     *
     * <p>Two sockets, two events: a chest that was <b>cracked</b> runs the node under «взлом», a chest that was
     * simply opened runs the node under «открытие». A graph written by build 396/397 has only the one socket
     * wired - {@code next} - so a cracked chest falls back to it rather than going silent: an older graph keeps
     * working exactly as it did, and the author gains the second socket without losing the first.</p>
     */
    public static boolean run(ServerPlayer player, ServerLevel level, BlockPos pos, String key,
            boolean cracked) {
        if (player == null || level == null || key == null || key.isEmpty()) return false;
        try {
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return false;
            NpcDialogueGraph.Node start = cracked ? hackChainStart(graph) : chainStart(graph);
            boolean fellBack = false;
            if (start == null && cracked) {
                // A graph of 396/397 knows nothing of the second socket: its one chain is all there is.
                start = chainStart(graph);
                fellBack = start != null;
            }
            if (start == null) return false;
            BlockTalk358.Where where = new BlockTalk358.Where(level, key, pos.getX(), pos.getY(), pos.getZ());
            // Build 393's rule: the node the socket points at is the FIRST step of the chain, not the one
            // before it - start() runs it, fire() would skip it.
            int stepped = BlockTrigger351.start(player, graph, start.id, where);
            LOG.info("chest: " + key + " was " + (cracked ? "cracked" : "opened") + " by "
                    + player.getName().getString() + " and closed - " + stepped
                    + " node(s) stepped from the card's "
                    + (cracked && !fellBack ? "«взлом» socket" : "«открытие» socket")
                    + (fellBack ? " (a graph of 396/397: the only socket it has)" : ""));
            return stepped > 0 || start != null;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * Build 401: run the chest's <b>«взлом»</b> chain the moment the panel is solved - before the chest opens.
     *
     * <p>The author's words: «Ожидаемое выполнение - после успешного взлома проиграется репика, после чего
     * откроется инвентарь сундука. Но сейчас сначала открывается инвентарь а потом проигрывается реплика».
     * Build 396 ran every chest chain when the window <b>closed</b>, and for an ordinary opening that is still
     * right - he takes what he came for, the chest shuts, and the «Реплика» of «открытие» says its line with
     * nothing in front of it. For a <b>crack</b> it is exactly backwards: the flavour of cracking a lock comes
     * before the reward, and the author asked for that order in words.</p>
     *
     * <p>So a crack runs here, from the card's «взлом» socket, and the chest is opened afterwards - by the
     * player's own click, delivered by the client once this chain has had its say ({@code ChestNet397#solve}
     * sends {@code settledlands:chest_ready} with that answer). A graph of 396/397 has only the one socket: a
     * crack falls back to it, so an older graph keeps working and its chain now plays before the opening
     * instead of after it, which is the order its own author asked for.</p>
     *
     * @param stopped returned to the caller: {@code true} when the chain is waiting for the player to close a
     *                window, {@code false} when it ran to its end - the client waits only in the first case
     * @return how many nodes were stepped over, 0 when nothing is wired
     */
    public static int runCrack(ServerPlayer player, ServerLevel level, BlockPos pos, String key,
            boolean[] stopped) {
        if (stopped != null && stopped.length > 0) stopped[0] = false;
        if (player == null || level == null || pos == null || key == null || key.isEmpty()) return 0;
        try {
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return 0;
            NpcDialogueGraph.Node start = hackChainStart(graph);
            boolean fellBack = false;
            if (start == null) {
                start = chainStart(graph);
                fellBack = start != null;
            }
            if (start == null) {
                LOG.info("chest: " + key + " was cracked, but nothing is wired to the card's «взлом» socket -"
                        + " the chest opens straight away");
                return 0;
            }
            BlockTalk358.Where where = new BlockTalk358.Where(level, key,
                    pos.getX(), pos.getY(), pos.getZ());
            int stepped = BlockTrigger351.startReportingWindow(player, graph, start.id, where, stopped);
            LOG.info("chest: " + key + " was cracked by " + player.getName().getString() + " - " + stepped
                    + " node(s) stepped from the card's "
                    + (fellBack ? "«открытие» socket (a graph of 396/397: the only socket it has)"
                            : "«взлом» socket")
                    + (stopped != null && stopped.length > 0 && stopped[0]
                            ? ", the chain is waiting for the player before the chest opens"
                            : ", the chest opens now"));
            return stepped;
        } catch (Throwable failure) {
            fail(failure);
            return 0;
        }
    }

    // ---- helpers ------------------------------------------------------------

    /** {@code 12 64 -30} - for messages, without the dimension. */
    public static String coords(BlockPos pos) {
        if (pos == null) return "?";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** One line per failure class, never a log flood (the rule of build 349). */
    public static void fail(Throwable failure) {
        String message = String.valueOf(failure);
        if (!WARNED.add(message) || WARNED.size() > 16) return;
        LOG.warn("chest graph pass skipped: " + message);
    }
}
