package dev.settledlands.door;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Build 377: the lock panel - "взлом" - of a locked door.
 *
 * <p>A locked door used to answer a click with a line in the action bar and nothing else. It now opens
 * this panel: a row of four to ten buttons, each one a key of the number row, and the door opens only
 * when <b>every</b> button is held down at the same moment. Holding ten keys with two hands is the
 * whole game; letting go of any one of them puts that button out.</p>
 *
 * <p>Why the keyboard and not the mouse: one cursor cannot hold four buttons. The number row can hold
 * ten keys at once, and {@code InputConstants.isKeyDown} answers for real hardware every frame - the
 * same call the base mod's own screens make for the space bar. The panel therefore never guesses from
 * the events it received: if a key was already held when the panel opened, or was released while the
 * window lost focus, the reading is still the truth.</p>
 *
 * <p>Nothing here is a rule. A panel that is closed, timed out or never solved changes nothing about
 * the door: it is still locked, still unbreakable, and still open to NPCs. The only thing the panel can
 * do is ask the server, once, to open the door it was opened for - and the server checks the lock
 * itself before it answers.</p>
 */
public class HackPanel377 extends Screen {

    /** GLFW codes of the number row, 1..9 then 0. Layout-independent: these are positions, not signs. */
    private static final int[] DIGITS = {49, 50, 51, 52, 53, 54, 55, 56, 57, 48};

    private static final int MIN_BUTTONS = 4;
    private static final int MAX_BUTTONS = 10;

    /** How long one attempt lasts. When it runs out the buttons clear and the door stays shut. */
    private static final long LIMIT_MS = 15000L;

    private static final int PANEL_BG = 0xE00E1116;
    private static final int PANEL_EDGE = 0xFF6A7A6A;
    private static final int LABEL = 0xFFBFD4BF;
    private static final int DIM = 0xFF8A968A;
    private static final int BTN_BG = 0xFF1B2028;
    private static final int BTN_EDGE = 0xFF46505C;
    private static final int BTN_ON = 0xFF2F7D4F;
    private static final int BTN_ON_EDGE = 0xFF7BE0A0;
    private static final int BAD = 0xFFCC4444;
    private static final int BAR = 0xFF3A7BD5;

    private static final int BTN = 34;
    private static final int GAP = 8;

    private final BlockPos pos;
    private final int count;
    private final int[] keys;

    private long started;
    private long flashAt;
    private boolean flashOk;
    private String flash = "";
    private boolean solved;
    private int best;

    private HackPanel377(BlockPos pos, int count, int[] keys) {
        super(Component.literal("Взлом двери"));
        this.pos = pos;
        this.count = count;
        this.keys = keys;
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
            int count = MIN_BUTTONS + random.nextInt(MAX_BUTTONS - MIN_BUTTONS + 1);
            int[] pool = new int[DIGITS.length];
            System.arraycopy(DIGITS, 0, pool, 0, DIGITS.length);
            for (int i = pool.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = pool[i];
                pool[i] = pool[j];
                pool[j] = swap;
            }
            int[] keys = new int[count];
            System.arraycopy(pool, 0, keys, 0, count);
            java.util.Arrays.sort(keys);
            mc.setScreen(new HackPanel377(pos, count, keys));
        } catch (Throwable failure) {
            // A panel that cannot be built must cost the player the panel, not the click.
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    protected void init() {
        this.started = System.currentTimeMillis();
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

    /** How many of the panel's keys the keyboard is holding right now. 0 when no window is known. */
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int down = held();
        if (down > best) best = down;
        long now = System.currentTimeMillis();
        if (!solved && now - started > LIMIT_MS) {
            started = now;
            best = 0;
            flash = "не успел - кнопки сброшены";
            flashOk = false;
            flashAt = now;
        }
        int total = count * BTN + (count - 1) * GAP;
        int center = this.width / 2;
        int left = center - total / 2;
        int panelLeft = left - 20;
        int panelRight = left + total + 20;
        int panelTop = this.height / 2 - 70;
        int panelBottom = panelTop + 122;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_BG);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, PANEL_EDGE);
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, PANEL_EDGE);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, PANEL_EDGE);
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, PANEL_EDGE);

        graphics.drawCenteredString(this.font, Component.literal("ВЗЛОМ ДВЕРИ"), center, panelTop + 10, LABEL);

        int top = panelTop + 30;
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
                Component.literal("Зажми все " + count + " клавиш одновременно"), center, panelTop + 72, DIM);

        boolean bad = !flash.isEmpty() && now - flashAt < 1500L;
        String line = bad ? flash : (down + " / " + count);
        graphics.drawCenteredString(this.font, Component.literal(line), center, panelTop + 87,
                bad ? BAD : (down == count ? BTN_ON_EDGE : LABEL));

        long leftMs = LIMIT_MS - (now - started);
        if (leftMs < 0L) leftMs = 0L;
        int barWidth = total;
        int filled = (int) (barWidth * leftMs / LIMIT_MS);
        graphics.fill(left, panelTop + 104, left + barWidth, panelTop + 108, 0xFF20262E);
        graphics.fill(left, panelTop + 104, left + filled, panelTop + 108, BAR);

        if (!solved && down == count) {
            solved = true;
            flash = "замок вскрыт";
            flashOk = true;
            flashAt = now;
            DoorHack377.send(pos);
            this.onClose();
        }
    }

    private boolean isDown(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        if (window == 0L) return false;
        return InputConstants.isKeyDown(window, keyCode);
    }

    /** "1".."9", "0" - the sign on the keycap, not the character the layout produces. */
    private static String label(int keyCode) {
        return String.valueOf((char) keyCode);
    }
}
