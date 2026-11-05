package dev.settledlands.door;

import com.mojang.blaze3d.platform.InputConstants;
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
 */
public class HackPanel377 extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/HackPanel377");

    /** GLFW codes of the letter row Q W E R T Y U I O P. Layout positions, not the characters. */
    private static final int[] LETTERS = {81, 87, 69, 82, 84, 89, 85, 73, 79, 80};

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

    private long rolledAt;
    private long flashAt;
    private String flash = "";
    private boolean solved;
    private int swaps;

    private HackPanel377(BlockPos pos, int count, int[] keys, long rollMs) {
        super(Component.literal("Взлом двери"));
        this.pos = pos;
        this.count = count;
        this.keys = keys;
        this.rollMs = rollMs > 0L ? rollMs : ROLL_MS;
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
            int[] keys = pick(random, count);
            mc.setScreen(new HackPanel377(pos, count, keys, rollMs));
            // Build 379: a panel that never appeared and a panel nobody could solve look the same in
            // game - the door stays shut either way. This line says which one happened.
            LOG.info("door: the lock panel opened at " + DoorSync375.key(mc.player.level(), pos)
                    + " with " + count + " keys every " + (rollMs / 1000L) + " s: " + spell(keys));
        } catch (Throwable failure) {
            // A panel that cannot be built must cost the player the panel, not the click.
            LOG.warn("door: the lock panel could not be opened, the door stays locked and silent", failure);
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
        }
    }

    /** {@code count} distinct letters of the row, left to right. */
    private static int[] pick(Random random, int count) {
        int[] pool = new int[LETTERS.length];
        System.arraycopy(LETTERS, 0, pool, 0, LETTERS.length);
        for (int i = pool.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = pool[i];
            pool[i] = pool[j];
            pool[j] = swap;
        }
        int[] out = new int[count];
        System.arraycopy(pool, 0, out, 0, count);
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

    /** The keys of this panel are ours: nothing else may read them while the panel is up. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (owns(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (owns(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean owns(int keyCode) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == keyCode) return true;
        }
        return false;
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
        for (int tries = 0; tries < LETTERS.length * 4; tries++) {
            int candidate = LETTERS[random.nextInt(LETTERS.length)];
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
        long now = System.currentTimeMillis();
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

        graphics.drawCenteredString(this.font, Component.literal("ВЗЛОМ ДВЕРИ"), center, panelTop + 10, LABEL);

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
                Component.literal("каждые " + (this.rollMs / 1000L) + " с одна клавиша меняется"),
                center, panelTop + 108, DIM);

        long leftMs = this.rollMs - (now - rolledAt);
        if (leftMs < 0L) leftMs = 0L;
        int filled = (int) (total * leftMs / this.rollMs);
        graphics.fill(left, panelTop + 122, left + total, panelTop + 126, 0xFF20262E);
        graphics.fill(left, panelTop + 122, left + filled, panelTop + 126, fresh ? SWAP : BAR);

        if (!solved && down == count) {
            solved = true;
            LOG.info("door: all " + count + " keys of the panel are held - sending the hack for "
                    + DoorSync375.key(Minecraft.getInstance().player == null ? null
                            : Minecraft.getInstance().player.level(), pos));
            DoorHack377.send(pos);
            this.onClose();
        }
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

    /** "Q".."P" - the letter on the keycap, whatever the layout makes of the key. */
    private static String label(int keyCode) {
        return String.valueOf((char) keyCode);
    }
}
