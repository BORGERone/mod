package dev.settledlands.chest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 401: the click that opens a cracked chest, kept out of the panel so it can wait for the chain.
 *
 * <p>Build 400 opened the chest from {@code HackPanel377#onClose}: the panel was solved, the server lifted
 * the lock, the panel clicked the chest through the game's own handler, and the chest opened. That is the one
 * mechanism in this story that has ever worked in the author's game, and it stays - but the panel does not
 * own the moment any more. The author's order is «после успешного взлома проиграется репика, после чего
 * откроется инвентарь сундука», and the «Реплика» of a chest's «взлом» socket is a window the <b>server</b>
 * opens and the player closes. So the server says when: {@code settledlands:chest_ready} arrives with
 * {@code wait = 1} when a window of the chain is in front of the player ({@code ChestNet397#solve}), and this
 * class holds the click until that window is gone.</p>
 *
 * <p><b>Why a tick and not a callback.</b> The end of a «Реплика» is a screen closing on the client, and a
 * screen closing is not an event this mod can subscribe to. What it can do is look: every client tick, is a
 * screen in front? Once the screen is gone for two ticks in a row the click is delivered. Two ticks and not
 * zero, because the chain may put its next window up in the same breath ({@code BlockTalk358#pick} walks on),
 * and a chest that opens on top of the second line of a dialogue is the report this build exists to answer.</p>
 *
 * <p><b>What it will not do.</b> It never clicks while the player has any container open (the chest's own
 * window may have arrived by other means), never clicks a second time for one panel, never clicks for a
 * different chest than the one the server named, and forgets everything after a minute - a click that arrives
 * a minute late is a click the player already made himself. Every path is wrapped: a client that cannot click
 * must cost the player one right-click, never the game.</p>
 *
 * <p>Client-only: this class touches {@code Minecraft} and is reached only from the client half of
 * {@code chest_ready}, exactly like {@code HackPanel377}.</p>
 */
public final class ChestOpen401 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestOpen401");

    /** How long a requested click is kept. Longer than any reply of this mod, shorter than a session. */
    private static final long TTL_MS = 60_000L;

    /** How long a chain's window may fail to appear before the click is delivered anyway. */
    private static final long WINDOW_GRACE_MS = 5_000L;

    /** Ticks of a clear screen before the click - see the class comment for why it is two and not zero. */
    private static final int QUIET_TICKS = 2;

    private static String armedKey;
    private static BlockPos armedPos;
    private static boolean waitForWindow;
    private static boolean sawWindow;
    private static long armedAt;
    private static int quiet;

    private ChestOpen401() {}

    /**
     * The server says this chest may be clicked now, and whether a window of its chain is still in the way.
     *
     * <p>Called from the client half of {@code settledlands:chest_ready}. A second request replaces the first:
     * one panel, one chest, one click.</p>
     */
    public static void arm(String key, BlockPos pos, boolean wait) {
        if (pos == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            // A window of the chain is already in front of the player when this arrives: the server opened it
            // before it sent this packet, and packets are handled in order. Record it now, so the click waits
            // for that window and not for a timer.
            boolean inFront = mc != null && mc.screen != null;
            armedKey = key;
            armedPos = pos.immutable();
            waitForWindow = wait;
            sawWindow = inFront && wait;
            armedAt = System.currentTimeMillis();
            quiet = 0;
            LOG.info("chest: " + key + " is ready to be clicked" + (wait ? " once the chain's window is gone"
                    : " now") + (inFront ? " (a screen is already open)" : ""));
        } catch (Throwable failure) {
            LOG.warn("chest: a ready chest could not be armed: " + failure);
        }
    }

    /** True while a click is waiting for its moment - for the gate and the log, never for the game. */
    public static boolean armed() {
        return armedPos != null;
    }

    /** Forgets the click - the panel's own close, a disconnect, a different chest. */
    public static void clear() {
        armedKey = null;
        armedPos = null;
        waitForWindow = false;
        sawWindow = false;
        quiet = 0;
        armedAt = 0L;
    }

    /**
     * One client tick of the click. Called from {@link ChestOpenWatch401}.
     *
     * <p>The order of the checks is the order of the mistakes that are possible: a screen in front (the
     * chain's window, a pause menu, anything the player opened), a container already open (the chest opened
     * some other way and a click would open a second window), the chest being out of reach (the player walked
     * away, and the server would refuse it anyway), and time.</p>
     */
    public static void watch() {
        if (armedPos == null) return;
        try {
            long now = System.currentTimeMillis();
            if (now - armedAt > TTL_MS) {
                LOG.info("chest: " + armedKey + " was never clicked - a minute passed, the request is"
                        + " forgotten and one more right-click opens it");
                clear();
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            if (mc.screen != null) {
                if (waitForWindow) sawWindow = true;
                quiet = 0;
                return;
            }
            quiet++;
            if (quiet < QUIET_TICKS) return;
            if (waitForWindow && !sawWindow && now - armedAt < WINDOW_GRACE_MS) return;
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                LOG.info("chest: " + armedKey + " already has a window open - nothing to click");
                clear();
                return;
            }
            BlockPos pos = armedPos;
            String key = armedKey;
            clear();
            mc.execute(() -> click(mc, key, pos));
        } catch (Throwable failure) {
            LOG.warn("chest: the click of a cracked chest could not be delivered: " + failure);
            clear();
        }
    }

    /**
     * The click itself: the game's own use handler, aimed at the centre of the chest.
     *
     * <p>{@code MultiPlayerGameMode#useItemOn} is the entry a real right-click takes, so the server checks
     * reach, sneaking and the lock exactly as it does for a human click. This is the code build 400 wrote in
     * {@code HackPanel377}; it moved here so the panel and the packet can share one click and one guard.</p>
     */
    private static void click(Minecraft mc, String key, BlockPos pos) {
        try {
            if (mc == null || mc.player == null || mc.gameMode == null || pos == null) return;
            if (mc.screen != null) return;                                   // something opened in the meantime
            if (mc.player.containerMenu != mc.player.inventoryMenu) return;  // the chest is already up
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D,
                            pos.getZ() + 0.5D), Direction.UP, pos, false));
            LOG.info("chest: the click of the cracked chest at " + pos + " (" + key + ") was delivered");
        } catch (Throwable failure) {
            LOG.warn("chest: the chest at " + pos + " could not be clicked - one more click by hand opens it",
                    failure);
        }
    }
}
