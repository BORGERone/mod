package dev.settledlands.door;

import com.mojang.blaze3d.platform.InputConstants;
import dev.settledlands.chest.ChestNet397;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 378: the lock panel - "взлом" - of a locked door.
 *
 * <p><b>Build 397: a locked chest opens this very panel too.</b> The author's line was «в графе сундука я
 * не могу настраивать взлом» - and a setting needs something on the other side of it. So the chest card
 * carries the same four numbers as the door card, the server hands them to this panel when a locked chest is
 * clicked ({@link #openForChest}), and a solved panel answers with the chest's own packet instead of the
 * door's. There is still one lock panel in this mod, not two: a second board would be a second set of keys to
 * get wrong.</p>
 *
 * <p>A locked door used to answer a click with a line in the action bar and nothing else. It now opens
 * this panel: a row of four to ten letters, and the door opens only when <b>every</b> button is held
 * down at the same moment. Holding that many letters with two hands is the whole game; letting go of
 * any one of them puts that button out.</p>
 *
 * <p>Build 378 changed two things the author asked for. The keycaps are <b>letters</b> now, not digits
 * ({@code Q W E R T Y U I O P} - one row, the one directly under the number row, so the hands sit where
 * they already were). And the clock no longer throws the attempt away: every five seconds <b>one</b> of
 * the keys changes to a different letter. Whatever is held stays held, so nothing is lost - but the
 * button that just changed is no longer the key the player is pressing, and the set has to be completed
 * again around it. That is the pressure; there is no failure state at all, and the door is untouched by
 * anything the panel does or refuses to do.</p>
 *
 * <p>Why the keyboard and not the mouse: one cursor cannot hold four buttons. {@code
 * InputConstants.isKeyDown} answers for real hardware every frame - the same call the base mod's own
 * screens make for the space bar - so a key already held when the panel opened, or released while the
 * window lost focus, cannot lie about its state.</p>
 *
 * <p><b>Build 385: the panel owns the keyboard.</b> Build 384 let every key it did not use fall through
 * to the screen's super method, and a screen that answers {@code false} hands the key to the game's key
 * mappings - the author pressed R in the panel and reloaded his shaders. Only ESC does anything now: it
 * closes the panel. Everything else is swallowed, so no key mapping, no hotkey and no mod shortcut can
 * fire while the panel is up. Reading the hardware directly ({@link InputConstants#isKeyDown}) is
 * unaffected - that is what the panel measures.</p>
 *
 * <p><b>Build 387: the panel leaves only when the hands do.</b> Until now the panel closed on the same
 * frame the last key went down - the door was open, and the six or eight keys the player was still
 * holding were suddenly nobody's business, so they fell straight through to the game. Solving now only
 * <b>accepts</b> the hack: the panel keeps the keyboard, says so, and closes when <b>every</b> held key
 * has come back up, so a win can never press W, drop the item in hand or open the inventory by
 * accident. A deliberate escape stays: ESC held for a moment still closes it, so a stuck key cannot
 * trap the player in a panel.</p>
 *
 * <p><b>Build 385: «Сложность».</b> The door node has a third slider, and it decides which rows of the
 * keyboard the keys are drawn from: 1 - {@code qwertyuiop[]}, 2 - those plus {@code asdfghjkl;'},
 * 3 - those plus {@code zxcvbnm,./}. A harder door does not ask for more keys; it asks for the same
 * number of keys out of a bigger board, so the same two hands have to search further.</p>
 */
public class HackPanel377 extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/HackPanel377");

    /**
     * Build 385: the three rows a key can come from. GLFW codes are the layout positions of the keys,
     * and for these keys the code is the ASCII value - which is why the keycap label can be the code.
     *
     * <p>Row 1 is the one the panel used since build 378 ({@code qwertyuiop}) plus the two keys to its
     * right ({@code [ ]}), because the author named the row as {@code qwertyuiop[]}. Row 2 is
     * {@code asdfghjkl;'} and row 3 is {@code zxcvbnm,./} - the three rows of a QWERTY keyboard, in the
     * order a hand reaches them.</p>
     */
    private static final int[] ROW_TOP = {81, 87, 69, 82, 84, 89, 85, 73, 79, 80, 91, 93};
    private static final int[] ROW_HOME = {65, 83, 68, 70, 71, 72, 74, 75, 76, 59, 39};
    private static final int[] ROW_BOTTOM = {90, 88, 67, 86, 66, 78, 77, 44, 46, 47};

    public static final int MIN_DIFFICULTY = 1;
    public static final int MAX_DIFFICULTY = 3;
    public static final int DEFAULT_DIFFICULTY = 1;

    /** The key ESC, and the only key this panel lets through to the screen under it. */
    private static final int ESC = 256;

    /**
     * Build 387: how long ESC has to be held before a <b>solved</b> panel closes anyway. A tap must not
     * do it - that is the whole point of the build - but a key that is stuck down in hardware must not
     * leave the player locked inside a panel either.
     */
    private static final long ESC_HOLD_MS = 800L;

    private static final int MIN_BUTTONS = 4;
    private static final int MAX_BUTTONS = 10;

    /**
     * Build 378: how long a key stays where it is. When it runs out one key of the row changes - the
     * held keys are left alone, so the attempt is never thrown away, only made harder.
     */
    private static final long ROLL_MS = 5000L;

    /**
     * Build 382: the door's own graph can say how many keys this door asks for and how fast one of
     * them changes (the two sliders of its node). A door that was never edited in the editor has no
     * such numbers, and then the panel is exactly the panel of build 378: four to ten keys drawn at
     * random and this five-second clock.
     */

    private static final int PANEL_BG = 0xE00E1116;
    private static final int PANEL_EDGE = 0xFF6A7A6A;
    private static final int LABEL = 0xFFBFD4BF;
    private static final int DIM = 0xFF8A968A;
    private static final int BTN_BG = 0xFF1B2028;
    private static final int BTN_EDGE = 0xFF46505C;
    private static final int BTN_ON = 0xFF2F7D4F;
    private static final int BTN_ON_EDGE = 0xFF7BE0A0;
    private static final int SWAP = 0xFFD8A030;
    private static final int BAR = 0xFF3A7BD5;

    private static final int BTN = 34;
    private static final int GAP = 8;

    private final BlockPos pos;
    private final int count;
    private final int[] keys;
    private final long rollMs;
    private final int difficulty;
    /** The rows this panel draws from - fixed for the life of the panel, from the door's difficulty. */
    private final int[] pool;

    private long rolledAt;
    private long flashAt;
    private String flash = "";
    private boolean solved;
    private int swaps;

    /**
     * Build 387: the keys this panel saw go down and has not seen come up. A key that was already held
     * when the panel opened sends no press here, which is why the panel's own keys are measured on the
     * hardware as well ({@link #held()}); between the two, every key the player is holding is known.
     */
    private final java.util.Set<Integer> pressed = new java.util.LinkedHashSet<>();

    /**
     * Build 401: since when ESC is held down after the hack was accepted - the escape hatch needs a
     * deliberate hold.
     *
     * <p>The hold used to be measured from the {@code keyPressed} events, which only repeat while the window
     * manager feels like repeating them: on the author's machine ESC closed the panel «через раз» («проблемы с
     * закрытием взлома на ESC, срабатывает через раз»). The key is now read on the <b>hardware</b> every tick
     * ({@code InputConstants#isKeyDown}), the same call the panel already trusts for the keys it holds, so a
     * hold is a hold whether or not a repeat event ever arrives.</p>
     */
    private long escHeldSince;

    /**
     * Build 398: when the answer went out and how many times it has been sent. A chest's prize is a window
     * opened by the server; if that window never arrives, the client asks again instead of leaving the player
     * at a chest that answered him with nothing (the report: «после взлома сундука у нас не открывается
     * инвентарь сундука»).
     */
    private long answeredAt;
    private int asked;

    /**
     * Build 401: the panel does not click its chest any more - {@code ChestOpen401} does.
     *
     * <p>Build 400 opened the chest here, in {@code onClose}, and that was the first mechanism in this story
     * that worked. It also opened the chest <b>before</b> the «взлом» chain had said a word, which is the
     * author's report «сначала открывается инвентарь а потом проигрывается реплика». The click itself did not
     * change; <b>who decides the moment</b> did: the server runs the chain first and then says
     * {@code settledlands:chest_ready}, and the client clicks when the chain's window is gone. One panel, one
     * chain, one click - and the chain comes first.</p>
     */
    private static final long ASK_AGAIN_MS = 1200L;

    /** How many times the absence of a window is written to the log - a report, not a flood. */
    private static final int ASK_TIMES = 2;

    /**
     * Build 397: this panel is the <b>door's</b> panel, and a chest opens the very same one. One screen,
     * one keyboard rule, one way out of it - the flag only decides the three words that name the thing
     * being cracked and which packet carries the win back.
     */
    private final boolean chest;

    /** Build 397: the key of the chest this panel was opened for - the string the server compares. */
    private final String chestKey;

    /** Build 417: true when an NPC spotted the player and the server aborted this panel. */
    private boolean aborted;
    private boolean cancelSent;

    private HackPanel377(BlockPos pos, int count, int[] keys, long rollMs, int difficulty) {
        this(pos, count, keys, rollMs, difficulty, null);
    }

    private HackPanel377(BlockPos pos, int count, int[] keys, long rollMs, int difficulty,
            String chestKey) {
        super(title(chestKey));
        this.pos = pos;
        this.count = count;
        this.keys = keys;
        this.rollMs = rollMs > 0L ? rollMs : ROLL_MS;
        this.difficulty = clampDifficulty(difficulty);
        this.pool = pool(this.difficulty);
        this.chestKey = chestKey;
        this.chest = chestKey != null && !chestKey.isEmpty();
    }

    /**
     * Build 397: opens the panel for a locked <b>chest</b>, with the numbers its card carries.
     *
     * <p>The four numbers arrive from the server with the click ({@code ChestNet397}), so a chest's panel is
     * decided by the chest's node and not by this client - two players clicking the same chest see the same
     * keys. Everything else is the door's: the same board, the same hold, the same ESC, the same rule that
     * the panel leaves only when every key has come back up.</p>
     */
    public static void openForChest(String chestKey, BlockPos pos, int keys, int seconds,
            int difficulty) {
        try {
            if (pos == null || chestKey == null || chestKey.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.screen != null || mc.player == null) return;
            int count = clamp(keys, MIN_BUTTONS, MAX_BUTTONS);
            long rollMs = clamp(seconds, 1, 20) * 1000L;
            int level = clampDifficulty(difficulty);
            int[] board = pick(new Random(), count, level);
            mc.setScreen(new HackPanel377(pos, count, board, rollMs, level, chestKey));
            // Build 379's rule: a panel that never appeared and one nobody solved look the same in game -
            // the chest stays shut either way. This line says which one happened.
            LOG.info("chest: the lock panel of the chest at " + pos + " opened with " + count
                    + " keys every " + (rollMs / 1000L) + " s, difficulty " + level + ": "
                    + spell(board));
        } catch (Throwable failure) {
            LOG.warn("chest: the lock panel could not be opened, the chest stays locked and silent",
                    failure);
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
        }
    }

    /** The name of the thing being cracked - the only word this panel changes between a door and a chest. */
    private static Component title(String chestKey) {
        return Component.literal(chestKey == null || chestKey.isEmpty()
                ? "Взлом двери" : "Взлом сундука");
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Opens the panel for a locked door, unless a panel is already open.
     *
     * <p>Called from the client half of the door click. It never throws and never opens a second
     * screen on top of an existing one - an inventory that was open stays the thing the player has to
     * deal with first.</p>
     */
    public static void open(BlockPos pos) {
        try {
            if (pos == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            if (mc.screen != null) return;
            if (mc.player == null) return;
            Random random = new Random();
            // Build 382: the door's graph wins when it has something to say. The key of the door is
            // the one the store and the sync already use, so the numbers arrive with the locks.
            int[] settings = DoorSync375.settings(mc.player.level(), pos);
            int count = settings != null
                    ? Math.max(MIN_BUTTONS, Math.min(MAX_BUTTONS, settings[0]))
                    : MIN_BUTTONS + random.nextInt(MAX_BUTTONS - MIN_BUTTONS + 1);
            long rollMs = settings != null && settings[1] > 0
                    ? settings[1] * 1000L : ROLL_MS;
            // Build 385: the third number of the door's node. A door that was never edited, or one
            // whose graph predates the difficulty, plays on difficulty 1 - the row of build 378.
            int difficulty = settings != null && settings.length > 2
                    ? clampDifficulty(settings[2]) : DEFAULT_DIFFICULTY;
            int[] keys = pick(random, count, difficulty);
            mc.setScreen(new HackPanel377(pos, count, keys, rollMs, difficulty));
            // Build 379: a panel that never appeared and a panel nobody could solve look the same in
            // game - the door stays shut either way. This line says which one happened.
            LOG.info("door: the lock panel opened at " + DoorSync375.key(mc.player.level(), pos)
                    + " with " + count + " keys every " + (rollMs / 1000L) + " s, difficulty "
                    + difficulty + ": " + spell(keys));
        } catch (Throwable failure) {
            // A panel that cannot be built must cost the player the panel, not the click.
            LOG.warn("door: the lock panel could not be opened, the door stays locked and silent", failure);
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
        }
    }

    /** The keys of one difficulty: row 1, rows 1-2, or all three rows. */
    public static int[] pool(int difficulty) {
        int level = clampDifficulty(difficulty);
        int size = ROW_TOP.length + (level >= 2 ? ROW_HOME.length : 0)
                + (level >= 3 ? ROW_BOTTOM.length : 0);
        int[] out = new int[size];
        int at = 0;
        System.arraycopy(ROW_TOP, 0, out, at, ROW_TOP.length);
        at += ROW_TOP.length;
        if (level >= 2) {
            System.arraycopy(ROW_HOME, 0, out, at, ROW_HOME.length);
            at += ROW_HOME.length;
        }
        if (level >= 3) {
            System.arraycopy(ROW_BOTTOM, 0, out, at, ROW_BOTTOM.length);
        }
        return out;
    }

    public static int clampDifficulty(int value) {
        return value < MIN_DIFFICULTY ? MIN_DIFFICULTY
                : (value > MAX_DIFFICULTY ? MAX_DIFFICULTY : value);
    }

    /** {@code count} distinct keys of the rows this difficulty allows, left to right. */
    public static int[] pick(Random random, int count, int difficulty) {
        int[] pool = pool(difficulty);
        for (int i = pool.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = pool[i];
            pool[i] = pool[j];
            pool[j] = swap;
        }
        int take = Math.max(1, Math.min(count, pool.length));
        int[] out = new int[take];
        System.arraycopy(pool, 0, out, 0, take);
        java.util.Arrays.sort(out);
        return out;
    }

    @Override
    protected void init() {
        this.rolledAt = System.currentTimeMillis();
        this.solved = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Build 385: the panel owns the keyboard while it is up - every key but one.
     *
     * <p>A screen that returns {@code false} hands the key to the game's key mappings, and the author
     * pressed R in the panel of build 384 and reloaded his shaders. So an answer of {@code true} for
     * every key that is not ESC: no key mapping, no hotkey, no mod shortcut, no debug combination fires
     * while the panel is open. ESC goes to the screen's own handler, which closes the panel - and only
     * the panel: the door is untouched by anything this screen does.</p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ESC) {
            // Build 387: before the hack is in, ESC is the player's way out - it closes the panel and
            // nothing else; the door stays shut. After it, the panel has to leave the player free of
            // the keyboard before it goes, so ESC is swallowed unless it is held on purpose.
            if (!solved) return super.keyPressed(keyCode, scanCode, modifiers);
            // Build 401: the hold is timed in tick(), on the hardware. Here ESC is only swallowed, so the
            // game never sees an ESC that belongs to the panel.
            return true;
        }
        if (keyCode >= 0) pressed.add(Integer.valueOf(keyCode));
        return true;
    }

    /** The same rule for a release: the panel hears about every key, the game hears about none. */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ESC) {
            if (!solved) return super.keyReleased(keyCode, scanCode, modifiers);
            return true;
        }
        pressed.remove(Integer.valueOf(keyCode));
        return true;
    }

    /**
     * Build 387: true when nothing the player pressed is still down.
     *
     * <p>Two sources, because neither alone is enough. {@link #pressed} knows every key the panel was
     * told about - but a key that was already held when the panel opened never sends a press, and a
     * release that happens while the window is unfocused may never arrive. So the set is pruned against
     * the hardware every frame ({@code isKeyDown} is the truth, the same call the keys are read with),
     * and the panel's own keys are checked on the hardware as well.</p>
     */
    /**
     * Build 398: ask again when a chest's window did not arrive.
     *
     * <p>Two seconds of watching is enough: the server opens a container in the same tick it answers, so a
     * window that is not there after a second and a fifth is not late - it is not coming. Two repeats, then the
     * panel stops asking and the player is left with the lock lifted on the server, which the action bar said
     * out loud.</p>
     */
    private void settledlands$askAgain(long now) {
        if (asked >= ASK_TIMES || chestKey == null || chestKey.isEmpty()) return;
        if (now - answeredAt < ASK_AGAIN_MS) return;
        if (settledlands$windowOpen()) {
            asked = ASK_TIMES;                       // it arrived: nothing to report
            return;
        }
        answeredAt = now;
        asked++;
        // Build 401: a report, never a resend. The answer is sent once, because sending it again would run the
        // chest's «взлом» chain a second time - and that chain now plays BEFORE the chest opens, so a repeated
        // ask would put a second «Реплика» in front of a chest that is already waiting for one click.
        LOG.info("chest: no window " + (ASK_AGAIN_MS / 1000L) + " s after the answer for " + chestKey
                + " - the chain of that chest may have nothing to show; the click comes when the server says"
                + " the chain is done (" + asked + "/2)");
    }

    /** True when a container of any kind is open - the sign that the chest's window really arrived. */
    private static boolean settledlands$windowOpen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.player != null
                    && mc.player.containerMenu != mc.player.inventoryMenu;
        } catch (Throwable failure) {
            return false;
        }
    }

    /** Build 417, client side: immediately closes any open lockpick panel when spotted by an NPC. */
    public static void abortFromServer() {
        try {
            DoorHack377.clearPending();
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !(mc.screen instanceof HackPanel377 panel)) return;
            panel.aborted = true;
            panel.solved = false;
            mc.setScreen(null);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.solved && !this.aborted && !this.cancelSent) {
            this.cancelSent = true;
            DoorHack377.sendCancel();
        }
    }

    /**
     * Build 398: the panel closes only while it is the screen in front.
     *
     * <p>A solved chest's window opens on the client as a new screen, and the panel is what it replaces. The
     * panel used to close itself unconditionally when the last key came up - and if the window had already
     * arrived by then, that close took the <b>chest's window</b> down with it. From the player's chair the
     * chest simply never opened, which is exactly what he reported.</p>
     */
    @Override
    public void onClose() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.screen != null && mc.screen != this) return;
        } catch (Throwable ignored) {
            // a close must never throw
        }
        super.onClose();
        // Build 401: nothing is clicked here. The chest of a solved panel is opened once the server's
        // «взлом» chain has had its say (ChestOpen401); a click from this close would open the chest on top of
        // the «Реплика» the author wired for exactly this moment.
    }

    /**
     * Build 401: the escape hatch of a solved panel, measured on the hardware.
     *
     * <p>A solved panel closes itself the moment every key is up, and that is the normal way out. The escape
     * hatch exists for the other case - a key the keyboard never reports as released - and it is a deliberate
     * hold of ESC, not a tap: a tap is how a player leaves a screen, and this screen must not be left before
     * its answer is out. The hold is read from {@code InputConstants#isKeyDown} on every call, because the
     * {@code keyPressed} repeats it used to rely on arrived «через раз» and the panel with them.</p>
     */
    @Override
    public void tick() {
        super.tick();
        escHeldTick();
    }

    /**
     * The same question, asked again from {@code render}: a screen's tick is the game's business, its frame is
     * not. Two chances per frame at a key hold cost nothing and remove the one way this fix could be invisible.
     */
    private void escHeldTick() {
        try {
            if (!solved || escHoldDisabled()) {
                escHeldSince = 0L;
                return;
            }
            long now = System.currentTimeMillis();
            if (!settledlands$escDown()) {
                escHeldSince = 0L;
                return;
            }
            if (escHeldSince == 0L) escHeldSince = now;
            if (now - escHeldSince >= ESC_HOLD_MS) {
                escHeldSince = 0L;
                LOG.info((chest ? "chest" : "door") + ": ESC was held for " + ESC_HOLD_MS
                        + " ms - the panel leaves, the answer is already with the server");
                this.onClose();
            }
        } catch (Throwable ignored) {
            // a frame must never cost the screen
        }
    }

    /** True when the hardware cannot be asked (no window, no client) - the hold simply does not count. */
    private boolean escHoldDisabled() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null || mc.getWindow() == null;
        } catch (Throwable failure) {
            return true;
        }
    }

    /** Is ESC down right now, as the keyboard itself says? */
    private boolean settledlands$escDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), ESC);
        } catch (Throwable failure) {
            return false;
        }
    }

    /** How much of the hold is behind the player, 0..1 - what the panel draws so the hold is not a guess. */
    private float settledlands$escHeld() {
        if (escHeldSince == 0L) return 0.0F;
        float part = (System.currentTimeMillis() - escHeldSince) / (float) ESC_HOLD_MS;
        return Math.max(0.0F, Math.min(1.0F, part));
    }

    private boolean allReleased() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc == null || mc.getWindow() == null ? 0L : mc.getWindow().getWindow();
        if (window != 0L && !pressed.isEmpty()) {
            java.util.Iterator<Integer> it = pressed.iterator();
            while (it.hasNext()) {
                Integer code = it.next();
                if (code != null && !InputConstants.isKeyDown(window, code.intValue())) it.remove();
            }
        }
        return pressed.isEmpty() && held() == 0;
    }

    /**
     * How many keys are still down, as the player has to be told. The panel's own keys are counted once,
     * through {@link #held()} - a key can be both in the set and down, and telling the player "2" for
     * one finger would look like a bug in the counter rather than in his hands.
     */
    private int stillDown() {
        int extra = 0;
        for (Integer code : pressed) {
            if (code != null && !uses(code.intValue())) extra++;
        }
        return extra + held();
    }

    /** The panel's keys the keyboard is holding right now. 0 when no window is known. */
    private int held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return 0;
        long window = mc.getWindow().getWindow();
        if (window == 0L) return 0;
        int down = 0;
        for (int i = 0; i < keys.length; i++) {
            if (InputConstants.isKeyDown(window, keys[i])) down++;
        }
        return down;
    }

    private boolean isDown(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        if (window == 0L) return false;
        return InputConstants.isKeyDown(window, keyCode);
    }

    /**
     * Build 378: change exactly one key of the row to a letter the panel is not showing.
     *
     * <p>Nothing else is touched - the keys the player is holding keep their buttons, and the attempt
     * goes on. The panel chooses the button at random, so watching is not a strategy: the set has to be
     * completed fast enough that the next change does not land on the last key pressed.</p>
     */
    private void roll() {
        rolledAt = System.currentTimeMillis();
        Random random = new Random();
        int index = random.nextInt(count);
        int fresh = keys[index];
        for (int tries = 0; tries < pool.length * 4; tries++) {
            int candidate = pool[random.nextInt(pool.length)];
            if (!uses(candidate)) {
                fresh = candidate;
                break;
            }
        }
        if (fresh == keys[index]) return;
        keys[index] = fresh;
        swaps++;
        flash = "клавиша сменилась";
        flashAt = rolledAt;
    }

    private boolean uses(int keyCode) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == keyCode) return true;
        }
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Build 401: the ESC hold is asked here as well as in tick() - see escHeldTick().
        escHeldTick();
        long now = System.currentTimeMillis();
        // Build 387: a solved panel has no clock - the keys are the only thing left to watch.
        if (!solved && now - rolledAt > this.rollMs) {
            roll();
        }
        int down = held();
        int total = count * BTN + (count - 1) * GAP;
        int center = this.width / 2;
        int left = center - total / 2;
        int panelLeft = left - 22;
        int panelRight = left + total + 22;
        int panelTop = this.height / 2 - 78;
        int panelBottom = panelTop + 138;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, PANEL_EDGE);
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, PANEL_EDGE);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, PANEL_EDGE);
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, PANEL_EDGE);

        if (!solved && down == count) {
            solved = true;
            LOG.info((chest ? "chest" : "door") + ": all " + count + " keys of the panel are held - "
                    + "sending the hack for " + (chest ? String.valueOf(pos)
                            : DoorSync375.key(Minecraft.getInstance().player == null ? null
                                    : Minecraft.getInstance().player.level(), pos)));
            answeredAt = System.currentTimeMillis();
            asked = 1;
            if (chest) ChestNet397.sendSolved(chestKey);
            else DoorHack377.send(pos);
            // Build 387: and the panel does NOT close here. The door is open on the server, but the
            // hands are still on the keys - closing now is exactly how a win used to press W, drop the
            // held item or open the inventory. It closes when every key has come back up.
            LOG.info("door: the panel stays up and keeps the keyboard until every held key is released");
        }

        if (solved) {
            // Build 398: the chest's window is the server's answer, so the client watches for it. No window
            // and no key left to press is a broken promise, not a win - so the ask is repeated once or twice
            // before the panel leaves. The lock itself is lifted on the server as well, so even a client that
            // never sees the window can open this chest with one more click.
            if (chest) settledlands$askAgain(now);
            // Build 387: the frame every key is up is the frame the panel goes - nowhere left to cover,
            // and nothing left to explain.
            if (allReleased()) {
                LOG.info("door: every key is up - the panel closes, the door is already open");
                this.onClose();
                return;
            }
            // The whole inner panel is repainted: a solved panel has nothing left to press.
            graphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, PANEL_BG);
            int still = stillDown();
            graphics.drawCenteredString(this.font, Component.literal("ВЗЛОМ ПРИНЯТ"),
                    center, panelTop + 16, BTN_ON_EDGE);
            graphics.drawCenteredString(this.font,
                    Component.literal(chest ? "замок снят, сундук открыт" : "замок снят, дверь открыта"),
                    center, panelTop + 36, LABEL);
            graphics.drawCenteredString(this.font,
                    Component.literal("отпусти все клавиши - и панель уйдёт сама"),
                    center, panelTop + 60, LABEL);
            graphics.drawCenteredString(this.font, Component.literal("ещё зажато: " + still),
                    center, panelTop + 80, SWAP);
            // Build 401: the escape hatch shows itself. A hold that is measured but never drawn is the same
            // «срабатывает через раз» as before - the player cannot tell a hold in progress from a dead key.
            float escPart = settledlands$escHeld();
            if (escPart > 0.0F) {
                graphics.drawCenteredString(this.font,
                        Component.literal("ESC: " + Math.round(escPart * 100.0F) + "% - панель уйдёт"),
                        center, panelTop + 100, SWAP);
            }
            graphics.drawCenteredString(this.font,
                    Component.literal("пока панель здесь, клавиатура заблокирована"),
                    center, panelTop + 100, DIM);
            graphics.drawCenteredString(this.font,
                    Component.literal("ESC удержи ~1 с, если клавиша залипла"),
                    center, panelTop + 118, DIM);
            return;
        }

        graphics.drawCenteredString(this.font,
                Component.literal(chest ? "ВЗЛОМ СУНДУКА" : "ВЗЛОМ ДВЕРИ"), center, panelTop + 10, LABEL);

        int top = panelTop + 30;
        boolean fresh = !flash.isEmpty() && now - flashAt < 1200L;
        for (int i = 0; i < count; i++) {
            int x = left + i * (BTN + GAP);
            boolean on = isDown(keys[i]);
            graphics.fill(x, top, x + BTN, top + BTN, on ? BTN_ON : BTN_BG);
            int edge = on ? BTN_ON_EDGE : BTN_EDGE;
            graphics.fill(x, top, x + BTN, top + 1, edge);
            graphics.fill(x, top + BTN - 1, x + BTN, top + BTN, edge);
            graphics.fill(x, top, x + 1, top + BTN, edge);
            graphics.fill(x + BTN - 1, top, x + BTN, top + BTN, edge);
            graphics.drawCenteredString(this.font, Component.literal(label(keys[i])),
                    x + BTN / 2, top + BTN / 2 - 4, on ? 0xFFFFFFFF : LABEL);
        }

        graphics.drawCenteredString(this.font,
                Component.literal("Зажми все " + count + " клавиш одновременно"), center, panelTop + 76, DIM);

        String line = fresh ? flash : (down + " / " + count);
        graphics.drawCenteredString(this.font, Component.literal(line), center, panelTop + 92,
                fresh ? SWAP : (down == count ? BTN_ON_EDGE : LABEL));

        graphics.drawCenteredString(this.font,
                Component.literal("каждые " + (this.rollMs / 1000L) + " с одна клавиша меняется"
                        + " · сложность " + this.difficulty),
                center, panelTop + 108, DIM);

        long leftMs = this.rollMs - (now - rolledAt);
        if (leftMs < 0L) leftMs = 0L;
        int filled = (int) (total * leftMs / this.rollMs);
        graphics.fill(left, panelTop + 122, left + total, panelTop + 126, 0xFF20262E);
        graphics.fill(left, panelTop + 122, left + filled, panelTop + 126, fresh ? SWAP : BAR);
    }

    /** The letters of a row as a line of text, for the log. */
    private static String spell(int[] keys) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) out.append(' ');
            out.append(label(keys[i]));
        }
        return out.toString();
    }

    /** "Q", "[", ";" - the character on the keycap; the GLFW code of these keys is its ASCII value. */
    private static String label(int keyCode) {
        return String.valueOf((char) keyCode);
    }
}
