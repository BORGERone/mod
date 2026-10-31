package dev.settledlands.npc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Full-screen, frame-rate-independent fade layer drawn over the cutscene camera. */
@EventBusSubscriber(modid = "settledlands", value = Dist.CLIENT)
public final class CutsceneFadeOverlay263 {
    private static final long FADE_MILLIS = 500L;
    private static final long CAMERA_START_TIMEOUT_MILLIS = 10_000L;
    private static final int IDLE = 0;
    private static final int TO_BLACK = 1;
    private static final int FROM_BLACK = 2;
    private static final int HOLD_BLACK = 3;

    private static int phase = IDLE;
    private static float alpha;
    private static float fromAlpha;
    private static long phaseStarted;
    private static boolean revealAfterBlack;
    private static boolean cameraStarted;
    private static double[][] deferredTrack;
    private static float deferredSpeed;
    private static int deferredSmooth;
    private static boolean deferredPlayingSignal;
    private static boolean releaseScheduled;
    private static long releaseToken;

    private CutsceneFadeOverlay263() {}

    /** Called by the client payload handler; both enabled phases form one transition. */
    public static synchronized void begin(boolean fadeOut, boolean fadeIn) {
        long now = nowMillis();
        revealAfterBlack = fadeIn;
        cameraStarted = false;
        deferredTrack = null;
        deferredPlayingSignal = false;
        releaseScheduled = false;
        releaseToken++;
        if (!fadeOut && !fadeIn) {
            phase = IDLE;
            alpha = 0.0F;
            revealAfterBlack = false;
            return;
        }
        if (fadeOut) {
            startPhase(TO_BLACK, 0.0F, now);
        } else {
            // For fade-in-only, keep the view black until the camera's playing signal.
            startPhase(HOLD_BLACK, 1.0F, now);
        }
    }

    /**
     * The camera's scene payload is held until fade-out reaches full black. The original
     * CutsceneCameraClient method and playing signal are then replayed on the client thread.
     */
    public static synchronized boolean deferCameraStart(double[][] track, float speed, int smooth) {
        if (phase != TO_BLACK) return false;
        deferredTrack = track;
        deferredSpeed = speed;
        deferredSmooth = smooth;
        deferredPlayingSignal = false;
        return true;
    }

    /** Called by the playback mixin before handlePlaying(true). */
    public static synchronized boolean deferPlayingSignal() {
        if (deferredTrack == null && !releaseScheduled) return false;
        deferredPlayingSignal = true;
        return true;
    }

    /** Called after the camera playback signal starts. */
    public static synchronized void cameraStarted() {
        cameraStarted = true;
        if (phase != HOLD_BLACK) return;
        if (revealAfterBlack) {
            startPhase(FROM_BLACK, 1.0F, nowMillis());
        } else {
            // Fade-out-only ends at the camera start without an implicit fade-in.
            phase = IDLE;
            alpha = 0.0F;
            cameraStarted = false;
        }
    }

    /** Restore the game view after playback or a cancelled scene. */
    public static synchronized void playbackEnded() {
        // An ending signal from the previous chained scene can arrive while this scene
        // is still waiting at black; it must not cancel the pending start.
        if (!cameraStarted && (phase == TO_BLACK || phase == HOLD_BLACK || releaseScheduled)) return;
        if (phase == IDLE && deferredTrack == null && !releaseScheduled) return;
        deferredTrack = null;
        deferredPlayingSignal = false;
        releaseScheduled = false;
        releaseToken++;
        long now = nowMillis();
        float current = sampleAlphaAt(now);
        if (current <= 0.0F) {
            phase = IDLE;
            alpha = 0.0F;
            revealAfterBlack = false;
            cameraStarted = false;
        } else {
            revealAfterBlack = false;
            cameraStarted = false;
            startPhase(FROM_BLACK, current, now);
        }
    }

    private static void startPhase(int nextPhase, float startAlpha, long now) {
        phase = nextPhase;
        alpha = startAlpha;
        fromAlpha = startAlpha;
        phaseStarted = now;
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static void releaseDeferredCamera() {
        if (deferredTrack == null) return;
        double[][] track = deferredTrack;
        float speed = deferredSpeed;
        int smooth = deferredSmooth;
        boolean playingSignal = deferredPlayingSignal;
        deferredTrack = null;
        releaseScheduled = true;
        long token = ++releaseToken;
        Minecraft.getInstance().execute(() -> {
            boolean shouldSignalPlaying;
            synchronized (CutsceneFadeOverlay263.class) {
                if (!releaseScheduled || releaseToken != token) return;
                shouldSignalPlaying = deferredPlayingSignal;
                deferredPlayingSignal = false;
                releaseScheduled = false;
            }
            CutsceneCameraClient.handlePlay(track, speed, smooth);
            if (playingSignal || shouldSignalPlaying) CutsceneCameraClient.handlePlaying(true);
        });
    }

    /** Public deterministic sampler is also used by the JVM regression scenario. */
    public static synchronized float sampleAlphaAt(long now) {
        if (phase == HOLD_BLACK && !cameraStarted
                && now - phaseStarted >= CAMERA_START_TIMEOUT_MILLIS) {
            // A dropped/missing camera packet must not strand the player on a black screen.
            if (revealAfterBlack) startPhase(FROM_BLACK, 1.0F, now);
            else {
                phase = IDLE;
                alpha = 0.0F;
            }
            revealAfterBlack = false;
        }
        if (phase != TO_BLACK && phase != FROM_BLACK) return alpha;
        float t = Math.max(0.0F, Math.min(1.0F, (now - phaseStarted) / (float) FADE_MILLIS));
        float eased = t * t * (3.0F - 2.0F * t);
        alpha = phase == TO_BLACK ? fromAlpha + (1.0F - fromAlpha) * eased : fromAlpha * (1.0F - eased);
        if (t >= 1.0F) {
            if (phase == TO_BLACK) {
                phase = HOLD_BLACK;
                alpha = 1.0F;
                fromAlpha = 1.0F;
                phaseStarted = now;
                releaseDeferredCamera();
                if (phase == HOLD_BLACK && cameraStarted && revealAfterBlack) {
                    startPhase(FROM_BLACK, 1.0F, now);
                }
            } else {
                phase = IDLE;
                alpha = 0.0F;
                revealAfterBlack = false;
                cameraStarted = false;
            }
        }
        return alpha;
    }

    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post event) {
        float current = sampleAlphaAt(nowMillis());
        if (current <= 0.001F) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int alphaByte = Math.max(0, Math.min(255, Math.round(current * 255.0F)));
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alphaByte << 24);
    }
}
