import dev.settledlands.npc.client.CutsceneCameraClient;
import dev.settledlands.npc.client.CutsceneFadeOverlay263;
import net.minecraft.client.Minecraft;

public final class CutsceneFadeOverlayProbe263 {
    private static void near(String label, float actual, float expected, float tolerance) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        long now;
        CutsceneFadeOverlay263.begin(true, true);
        now = System.nanoTime() / 1_000_000L;
        near("fade-out starts clear", CutsceneFadeOverlay263.sampleAlphaAt(now), 0.0F, 0.03F);
        near("fade-out midpoint", CutsceneFadeOverlay263.sampleAlphaAt(now + 250), 0.5F, 0.04F);
        near("fade-out reaches full black", CutsceneFadeOverlay263.sampleAlphaAt(now + 510), 1.0F, 0.001F);
        near("black is held until camera start", CutsceneFadeOverlay263.sampleAlphaAt(now + 760), 1.0F, 0.001F);
        CutsceneFadeOverlay263.cameraStarted();
        now = System.nanoTime() / 1_000_000L;
        near("reveal begins after camera start", CutsceneFadeOverlay263.sampleAlphaAt(now + 250), 0.5F, 0.04F);
        near("reveal ends clear", CutsceneFadeOverlay263.sampleAlphaAt(now + 510), 0.0F, 0.001F);

        CutsceneFadeOverlay263.begin(true, false);
        now = System.nanoTime() / 1_000_000L;
        near("fade-out-only reaches black", CutsceneFadeOverlay263.sampleAlphaAt(now + 510), 1.0F, 0.001F);
        CutsceneFadeOverlay263.cameraStarted();
        near("fade-out-only clears at camera start without implicit reveal",
                CutsceneFadeOverlay263.sampleAlphaAt(now + 520), 0.0F, 0.001F);
        CutsceneFadeOverlay263.playbackEnded();
        near("fade-out-only remains clear after playback", CutsceneFadeOverlay263.sampleAlphaAt(now + 530),
                0.0F, 0.001F);

        CutsceneFadeOverlay263.begin(true, false);
        now = System.nanoTime() / 1_000_000L;
        CutsceneFadeOverlay263.sampleAlphaAt(now + 510);
        near("missing camera packet eventually clears black",
                CutsceneFadeOverlay263.sampleAlphaAt(now + 10_510), 0.0F, 0.001F);

        CutsceneFadeOverlay263.begin(false, true);
        now = System.nanoTime() / 1_000_000L;
        near("fade-in-only waits on black", CutsceneFadeOverlay263.sampleAlphaAt(now), 1.0F, 0.001F);
        CutsceneFadeOverlay263.cameraStarted();
        now = System.nanoTime() / 1_000_000L;
        near("fade-in-only midpoint", CutsceneFadeOverlay263.sampleAlphaAt(now + 250), 0.5F, 0.04F);
        near("fade-in-only ends clear", CutsceneFadeOverlay263.sampleAlphaAt(now + 510), 0.0F, 0.001F);

        CutsceneCameraClient.reset();
        double[][] points = {{0.0, 1.0, 2.0, 3.0}, {1.0, 2.0, 3.0, 4.0}};
        CutsceneFadeOverlay263.begin(true, true);
        check("camera payload is held while fading out",
                CutsceneFadeOverlay263.deferCameraStart(points, 1.25F, 5));
        check("playing signal is held with the camera payload",
                CutsceneFadeOverlay263.deferPlayingSignal());
        check("camera has not started before black", CutsceneCameraClient.track == null
                && !CutsceneCameraClient.playing);
        // A late stop signal from the previous chained scene must not cancel this start.
        CutsceneFadeOverlay263.playbackEnded();
        now = System.nanoTime() / 1_000_000L;
        near("previous scene end does not interrupt current fade",
                CutsceneFadeOverlay263.sampleAlphaAt(now + 250), 0.5F, 0.04F);
        near("deferred start reaches black", CutsceneFadeOverlay263.sampleAlphaAt(now + 510), 1.0F, 0.001F);
        check("camera start is scheduled on client thread", CutsceneCameraClient.track == null);
        Minecraft.getInstance().runQueuedTasks();
        check("camera and playing signal release at black", CutsceneCameraClient.track == points
                && CutsceneCameraClient.playing && CutsceneCameraClient.speed == 1.25F
                && CutsceneCameraClient.smooth == 5);
        now = System.nanoTime() / 1_000_000L;
        near("deferred scene reveals only after actual start", CutsceneFadeOverlay263.sampleAlphaAt(now + 250), 0.5F, 0.04F);

        CutsceneFadeOverlay263.begin(false, false);
        near("disabled flags clear overlay", CutsceneFadeOverlay263.sampleAlphaAt(now + 510), 0.0F, 0.001F);
        System.out.println("CUTSCENE FADE OVERLAY 263: PASS");
    }
}
