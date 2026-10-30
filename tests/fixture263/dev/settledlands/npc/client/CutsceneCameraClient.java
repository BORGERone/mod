package dev.settledlands.npc.client;

public final class CutsceneCameraClient {
    public static double[][] track;
    public static float speed;
    public static int smooth;
    public static boolean playing;
    private CutsceneCameraClient() {}
    public static void handlePlay(double[][] value, float newSpeed, int newSmooth) {
        track = value;
        speed = newSpeed;
        smooth = newSmooth;
    }
    public static void handlePlaying(boolean value) {
        playing = value;
        if (value) CutsceneFadeOverlay263.cameraStarted();
        else CutsceneFadeOverlay263.playbackEnded();
    }
    public static void reset() {
        track = null;
        speed = 0.0F;
        smooth = 0;
        playing = false;
    }
}
