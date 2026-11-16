package dev.settledlands.chest;

import dev.settledlands.npc.NpcDialogueGraph;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Build 397: the lock of a chest - the four numbers of the hack panel, the way a door has them.
 *
 * <p>The author, on build 396: «в графе сундука я не могу настраивать взлом». Build 396 gave the chest a
 * card that could only be <b>read</b> - how many times it was opened, who opened it last, when its chain
 * runs - and no way to say "this chest is locked". A door's card has four controls for exactly that
 * (how many keys the panel asks for, how fast one of them changes, which rows of the keyboard they come
 * from, and whether the door is locked at all), so the chest gets the very same four, and behind them the
 * very same panel: {@code HackPanel377}, opened for a chest, the solution of which is what opens the chest
 * instead of the door.</p>
 *
 * <p><b>Where the four values live.</b> On the chest's card, in node fields the stock serialiser writes
 * for <b>every</b> node - the rule of build 384, which exists because build 383 kept a door's values in
 * fields written only for other node types and every save wiped them:</p>
 *
 * <pre>
 *   varCompare   = keys        4..10   (0 = never configured, the default is used)
 *   varOp        = seconds     1..20   (0 = never configured)
 *   checkTarget  = difficulty  1..3    (0 = never configured)
 *   pointsShared = locked      true/false
 *   varValue     = how many times this chest was opened   (build 396)
 *   text         = the last player who opened it          (build 396)
 * </pre>
 *
 * <p>Keys, seconds and difficulty are stored in the three fields the door puts its own two sliders and its
 * difficulty in, and locked is a boolean the serialiser writes for every node. Nothing is kept in a second
 * place: the lock <b>is</b> the card's flag, so the moment the author saves the graph the chest is locked,
 * and the moment the graph is cleared the chest is an ordinary chest again. That is why this build needs no
 * lock store and no sync - one source of truth, nothing to drift.</p>
 *
 * <p>A locked chest does not open for a plain click (the click is refused where the chest would have been
 * opened, so it never opens for a single tick - the rule of builds 371/376), it cannot be broken open
 * either, and a solved panel asks the server to open <b>this</b> chest: the server re-checks the lock, the
 * distance and the block, and then opens the chest's own window
 * ({@code Player#openMenu(BlockState#getMenuProvider(...))}). The chain of the chest still runs when that
 * window closes, exactly as in build 396 - the hack is the door of a locked chest, not a second kind of
 * graph.</p>
 */
public final class ChestLock397 {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("SettledLands/ChestLock397");

    /** The card's lines, in the order the author reads them on the door card. */
    public static final String FIELD_NOTE =
            "chest node: varCompare = keys (4..10), varOp = seconds (1..20), checkTarget = difficulty (1..3),"
                    + " pointsShared = locked, varValue = opens, text = last opener";

    public static final int MIN_KEYS = 4;
    public static final int MAX_KEYS = 10;
    public static final int DEFAULT_KEYS = 6;

    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 20;
    public static final int DEFAULT_SPEED = 5;

    public static final int MIN_DIFFICULTY = 1;
    public static final int MAX_DIFFICULTY = 3;
    public static final int DEFAULT_DIFFICULTY = 1;

    /** How far the player may be from the chest when the panel is solved, in blocks - the door's reach. */
    private static final double REACH = 6.0D;

    private static final Map<String, Boolean> WARNED = new HashMap<String, Boolean>();

    private ChestLock397() {}

    private static boolean card(NpcDialogueGraph.Node node) {
        return ChestGraph396.isChestNode(node);
    }

    // ---- the four values ----------------------------------------------------

    /** How many keys the panel of this chest asks for at once - 4..10, the door's own range. */
    public static int keys(NpcDialogueGraph.Node node) {
        if (!card(node) || node.varCompare <= 0) return DEFAULT_KEYS;
        return clamp(node.varCompare, MIN_KEYS, MAX_KEYS);
    }

    public static void setKeys(NpcDialogueGraph.Node node, int value) {
        if (!card(node)) return;
        node.varCompare = clamp(value, MIN_KEYS, MAX_KEYS);
    }

    /** How often one key of the panel changes, in seconds - 1..20, the door's own range. */
    public static int seconds(NpcDialogueGraph.Node node) {
        if (!card(node) || node.varOp <= 0) return DEFAULT_SPEED;
        return clamp(node.varOp, MIN_SPEED, MAX_SPEED);
    }

    public static void setSeconds(NpcDialogueGraph.Node node, int value) {
        if (!card(node)) return;
        node.varOp = clamp(value, MIN_SPEED, MAX_SPEED);
    }

    /** Which rows of the keyboard the panel draws its keys from - 1, 2 or 3, the door's own scale. */
    public static int difficulty(NpcDialogueGraph.Node node) {
        if (!card(node) || node.checkTarget <= 0) return DEFAULT_DIFFICULTY;
        return clamp(node.checkTarget, MIN_DIFFICULTY, MAX_DIFFICULTY);
    }

    public static void setDifficulty(NpcDialogueGraph.Node node, int value) {
        if (!card(node)) return;
        node.checkTarget = clamp(value, MIN_DIFFICULTY, MAX_DIFFICULTY);
    }

    /** True when a plain click does not open this chest - the flag the author sets on the card. */
    public static boolean locked(NpcDialogueGraph.Node node) {
        return card(node) && node.pointsShared;
    }

    public static void setLocked(NpcDialogueGraph.Node node, boolean value) {
        if (!card(node)) return;
        node.pointsShared = value;
    }

    /** The two words of the card's state line: «Закрыта (только взлом)» / «Открыта (обычный клик)». */
    public static String stateKey(NpcDialogueGraph.Node node) {
        return locked(node) ? "gui.settledlands.chest.locked" : "gui.settledlands.chest.unlocked";
    }

    /**
     * The same state in the two words the <b>card</b> has room for. The toggle can explain itself - «Закрыта
     * (только взлом)» - but a card line is 14 characters wide before it is cut, so the card says «Замок:
     * закрыт» and the panel says the rest.
     */
    public static String stateKeyShort(NpcDialogueGraph.Node node) {
        return locked(node) ? "gui.settledlands.chest.state_locked"
                : "gui.settledlands.chest.state_open";
    }

    /** Build 417: true when picking this chest's lock is illegal (witnessed by live GuideNpc). */
    public static boolean illegal(NpcDialogueGraph.Node node) {
        return card(node) && node.varLocal;
    }

    public static void setIllegal(NpcDialogueGraph.Node node, boolean value) {
        if (card(node)) node.varLocal = value;
    }

    public static String illegalKey(NpcDialogueGraph.Node node) {
        return illegal(node)
                ? dev.settledlands.npc.NpcIllegalHack417.ILLEGAL_ON
                : dev.settledlands.npc.NpcIllegalHack417.ILLEGAL_OFF;
    }

    /**
     * A card that was never configured gets the door's defaults written into it - 6 keys, five seconds,
     * difficulty 1, unlocked - so the first save stores real numbers instead of a row of zeroes, and the
     * editor's sliders start where the panel does. Called on open and on save, like the door's
     * {@code DoorGraph382#adopt}: a graph written by build 396 opens as a chest with a working panel.
     */
    public static void applyDefaults(NpcDialogueGraph.Node node) {
        if (!card(node)) return;
        if (node.varCompare <= 0) node.varCompare = DEFAULT_KEYS;
        if (node.varOp <= 0) node.varOp = DEFAULT_SPEED;
        if (node.checkTarget <= 0) node.checkTarget = DEFAULT_DIFFICULTY;
        // The three values above are clamped to their ranges only when they are read; writing them back
        // here keeps a hand-edited file from carrying a 400-key panel into the game.
        node.varCompare = clamp(node.varCompare, MIN_KEYS, MAX_KEYS);
        node.varOp = clamp(node.varOp, MIN_SPEED, MAX_SPEED);
        node.checkTarget = clamp(node.checkTarget, MIN_DIFFICULTY, MAX_DIFFICULTY);
    }

    // ---- the world ----------------------------------------------------------

    /** The card of the chest at this position, or null when the chest has no graph. */
    public static NpcDialogueGraph.Node cardAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || pos == null) return null;
        try {
            String key = ChestGraph396Store.key(server, pos);
            if (key.isEmpty()) return null;
            ChestGraph396Store store = ChestGraph396Store.get(server);
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return null;
            return ChestGraph396.chestNode(graph);
        } catch (Throwable failure) {
            note(failure);
            return null;
        }
    }

    /** True when this block is a chest whose card says it is locked. */
    public static boolean locked(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        NpcDialogueGraph.Node card = cardAt(level, pos);
        return locked(card);
    }

    /** Build 417: true when this chest's card marks lockpicking as illegal. */
    public static boolean illegal(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return illegal(cardAt(level, pos));
    }

    /** True when this block is a chest whose card says it is locked, by the key the store uses. */
    public static boolean locked(ServerLevel level, String key) {
        if (level == null || key == null || key.isEmpty()) return false;
        try {
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            return locked(ChestGraph396.chestNode(graph));
        } catch (Throwable failure) {
            note(failure);
            return false;
        }
    }

    /** The numbers of this chest's panel, {@code {keys, seconds, difficulty}} - or null when it is free. */
    public static int[] panel(Level level, BlockPos pos) {
        NpcDialogueGraph.Node card = cardAt(level, pos);
        if (!locked(card)) return null;
        return new int[] {keys(card), seconds(card), difficulty(card)};
    }

    /** The numbers of this chest's panel by key - what the panel packet carries. */
    public static int[] panel(ServerLevel level, String key) {
        if (level == null || key == null) return null;
        try {
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            NpcDialogueGraph.Node card = ChestGraph396.chestNode(graph);
            if (!locked(card)) return null;
            return new int[] {keys(card), seconds(card), difficulty(card)};
        } catch (Throwable failure) {
            note(failure);
            return null;
        }
    }

    /**
     * Build 398: chests whose lock has been lifted for one player for a moment, and how long that lasts.
     *
     * <p>A solved panel opens the chest's own window on the server ({@code ChestNet397#solve}). On a client
     * where that window does not arrive - a load order, a competing screen, a packet the client drops - the
     * player is left standing at a chest that answered him with nothing, which is exactly the report «после
     * взлома сундука у нас не открывается инвентарь сундука». So the opening is not the only way out: when the
     * window does not open, the lock is lifted for that chest for a few seconds and the very next click opens
     * it through the game's own path, which is the path this mod has never had trouble with.</p>
     */
    /**
     * Build 401: how long a solved panel's crack waits for the click that spends it.
     *
     * <p>Ten seconds was right while the click came the instant the panel closed. It is not right any more: the
     * chest's «взлом» chain now runs <b>before</b> the chest opens («после успешного взлома проиграется
     * реплика, после чего откроется инвентарь сундука»), and a «Реплика» is a window the player reads at his own
     * pace. Ten seconds of reading would have turned the crack into an expired one and opened the lock panel
     * again. Ten minutes is longer than any reply of this mod and still not "for ever".</p>
     */
    private static final long CRACK_MS = 600_000L;

    /** {@code player@key} -> when the panel was solved. One crack belongs to one player and one chest. */
    private static final java.util.Map<String, Long> CRACKED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Build 401: this player has solved the panel of this chest - his next click on it is a <b>crack</b>.
     *
     * <p>Builds 398-400 kept a lift keyed by the <b>chest</b> alone and timed it at ten seconds: whoever clicked
     * the chest inside that window got in, and the window was shorter than a «Реплика». Keying it by the player
     * and the chest is both safer (nobody else's click can spend it) and enough time for the chain to play.</p>
     */
    public static void crack(String who, String key) {
        if (who == null || who.isEmpty() || key == null || key.isEmpty()) return;
        if (CRACKED.size() > 64) CRACKED.clear();
        CRACKED.put(who + "@" + key, System.currentTimeMillis());
    }

    /**
     * Was this click paid for by a solved panel of this chest? <b>Consumed here</b>: one crack, one click.
     *
     * <p>A second opening of the same chest is an ordinary opening and must run the «открытие» chain, and a
     * crack that was never spent must not leave the chest open to anybody who walks past it later.</p>
     */
    public static boolean takeCrack(String who, String key) {
        if (who == null || who.isEmpty() || key == null || key.isEmpty()) return false;
        Long at = CRACKED.remove(who + "@" + key);
        return at != null && System.currentTimeMillis() - at <= CRACK_MS;
    }

    /** Does this player still have an unspent crack on this chest? For the gate, never for the game. */
    public static boolean cracked(String who, String key) {
        if (who == null || who.isEmpty() || key == null || key.isEmpty()) return false;
        Long at = CRACKED.get(who + "@" + key);
        if (at == null) return false;
        if (System.currentTimeMillis() - at <= CRACK_MS) return true;
        CRACKED.remove(who + "@" + key);
        return false;
    }

    /** How many unspent cracks there are - the gate reads this to prove the click spends one. */
    public static int cracks() {
        return CRACKED.size();
    }

    /** Is the player still standing at the chest they solved the panel for? The door's own reach. */
    public static boolean near(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return false;
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    /** The state of the block, or null - a chest that was broken between the click and the packet. */
    public static BlockState state(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        return level.getBlockState(pos);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** One line per failure class, never a log flood (the rule of build 349). */
    private static void note(Throwable failure) {
        String message = String.valueOf(failure);
        if (WARNED.put(message, Boolean.TRUE) != null || WARNED.size() > 16) return;
        LOG.warn("chest lock: skipped: " + message);
    }
}
