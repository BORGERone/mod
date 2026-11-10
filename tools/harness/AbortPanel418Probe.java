import com.mojang.blaze3d.platform.InputConstants;
import dev.settledlands.door.DoorHack377;
import dev.settledlands.door.DoorSync375;
import dev.settledlands.door.HackPanel377;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Build 418 live JVM probe for {@link HackPanel377} abort behavior on {@code DoorSync375("X|")}.
 *
 * Verifies against the real compiled {@code HackPanel377.class}, {@code DoorSync375.class}, and
 * {@code DoorHack377.class} inside the target jar that getting spotted mid-lockpick does not drop
 * the screen while keys (especially {@code P}=80, {@code W}=87, {@code Q}=81) are held or in-flight.
 */
public final class AbortPanel418Probe {

    private static final int KEY_P = 80; // Social Interactions default keybind in vanilla MC
    private static final int KEY_Q = 81;
    private static final int KEY_W = 87;
    private static final int KEY_ESC = 256;

    public static void main(String[] args) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        mc.player = new LocalPlayer(new Level());
        BlockPos pos = new BlockPos(10, 64, -5);

        // 1) Holding P/W/Q when DoorSync375("X|") arrives -> panel MUST stay up, swallow P repeats
        //    and single-tap ESC, and close ONLY once all held keys are physically released (+ grace).
        InputConstants.clearAllForTests();
        mc.setScreen(null);
        PacketDistributor.clearSent();
        HackPanel377.openForChest("minecraft:overworld@10,64,-5", pos, 4, 5, 1);
        HackPanel377 p1 = (HackPanel377) mc.screen;
        InputConstants.setDownForTests(KEY_Q, true);
        InputConstants.setDownForTests(KEY_W, true);
        InputConstants.setDownForTests(KEY_P, true);
        p1.keyPressed(KEY_Q, 0, 0);
        p1.keyPressed(KEY_W, 0, 0);
        p1.keyPressed(KEY_P, 0, 0);

        DoorSync375.apply("X|"); // triggers HackPanel377.abortFromServer()
        boolean keptScreenImmediately = (mc.screen == p1);
        boolean swallowedPRepeat = p1.keyPressed(KEY_P, 0, 0);
        boolean swallowedEscTap = p1.keyPressed(KEY_ESC, 0, 0) && (mc.screen == p1);

        GuiGraphics gfx = new GuiGraphics();
        p1.render(gfx, 0, 0, 0.0F);
        boolean renderedAbortText = gfx.containsText("ВЗЛОМ ПРЕРВАН")
                && gfx.containsText("за вами наблюдают");

        // Wait past ABORT_GRACE_MS (450ms) while P is STILL held down.
        Thread.sleep(480L);
        InputConstants.setDownForTests(KEY_Q, false);
        InputConstants.setDownForTests(KEY_W, false);
        p1.keyReleased(KEY_Q, 0, 0);
        p1.keyReleased(KEY_W, 0, 0);
        p1.render(gfx, 0, 0, 0.0F);
        boolean stayedUpWhilePHeld = (mc.screen == p1);

        // Now release P -> panel MUST close cleanly on next render, without sending !cancel.
        InputConstants.setDownForTests(KEY_P, false);
        p1.keyReleased(KEY_P, 0, 0);
        p1.render(gfx, 0, 0, 0.0F);
        boolean closedAfterRelease = (mc.screen == null);
        boolean noCancelSentOnAbort = (PacketDistributor.lastPayload() == null);
        System.out.println("ABORT_HELD_KEYS kept=" + keptScreenImmediately
                + " swallowedP=" + swallowedPRepeat
                + " swallowedEsc=" + swallowedEscTap
                + " text=" + renderedAbortText
                + " stayedWithP=" + stayedUpWhilePHeld
                + " closedOnRelease=" + closedAfterRelease
                + " noCancel=" + noCancelSentOnAbort);

        // 2) Pre-held P (held BEFORE panel opened, never sent keyPressed) + reaction-guard catch of
        //    a late finger landing on P within 450ms after X|.
        InputConstants.clearAllForTests();
        mc.setScreen(null);
        InputConstants.setDownForTests(KEY_P, true); // already held before opening!
        HackPanel377.openForChest("minecraft:overworld@10,64,-5", pos, 4, 5, 1);
        HackPanel377 p2 = (HackPanel377) mc.screen;
        DoorSync375.apply("X|");
        Thread.sleep(480L);
        p2.render(gfx, 0, 0, 0.0F);
        boolean preHeldKeptUp = (mc.screen == p2);
        InputConstants.setDownForTests(KEY_P, false);
        p2.render(gfx, 0, 0, 0.0F);
        boolean preHeldReleasedClean = (mc.screen == null);

        // Reaction-guard case: 0 keys down at X| time, finger lands on P 80ms later.
        InputConstants.clearAllForTests();
        mc.setScreen(null);
        HackPanel377.openForChest("minecraft:overworld@10,64,-5", pos, 4, 5, 1);
        HackPanel377 p3 = (HackPanel377) mc.screen;
        DoorSync375.apply("X|");
        p3.render(gfx, 0, 0, 0.0F);
        boolean graceKeptUp = (mc.screen == p3);
        Thread.sleep(80L);
        InputConstants.setDownForTests(KEY_P, true);
        boolean latePSwallowed = p3.keyPressed(KEY_P, 0, 0);
        Thread.sleep(420L);
        p3.render(gfx, 0, 0, 0.0F);
        boolean latePStillBlocking = (mc.screen == p3);
        InputConstants.setDownForTests(KEY_P, false);
        p3.keyReleased(KEY_P, 0, 0);
        p3.render(gfx, 0, 0, 0.0F);
        boolean latePClosed = (mc.screen == null);
        System.out.println("ABORT_GUARD preHeld=" + (preHeldKeptUp && preHeldReleasedClean)
                + " grace=" + graceKeptUp
                + " latePSwallowed=" + (latePSwallowed && latePStillBlocking && latePClosed));

        // 3) ESC hardware-hold escape hatch on stuck aborted panel + normal ESC cancel before abort.
        InputConstants.clearAllForTests();
        mc.setScreen(null);
        HackPanel377.openForChest("minecraft:overworld@10,64,-5", pos, 4, 5, 1);
        HackPanel377 p4 = (HackPanel377) mc.screen;
        InputConstants.setDownForTests(KEY_P, true);
        p4.keyPressed(KEY_P, 0, 0);
        DoorSync375.apply("X|");
        InputConstants.setDownForTests(KEY_ESC, true);
        p4.tick();
        Thread.sleep(850L);
        p4.tick();
        boolean escHoldEscaped = (mc.screen == null);

        InputConstants.clearAllForTests();
        PacketDistributor.clearSent();
        HackPanel377.openForChest("minecraft:overworld@10,64,-5", pos, 4, 5, 1);
        HackPanel377 p5 = (HackPanel377) mc.screen;
        p5.keyPressed(KEY_ESC, 0, 0);
        boolean normalEscClosed = (mc.screen == null)
                && (PacketDistributor.lastPayload() instanceof DoorHack377 dh)
                && DoorHack377.CANCEL_KEY.equals(dh.key());
        System.out.println("ABORT_ESC_HATCH escHoldEscaped=" + escHoldEscaped
                + " normalEscCancel=" + normalEscClosed);
    }
}
