package dev.settledlands.mixin;

import dev.settledlands.npc.client.CutsceneFadeOverlay263;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds a scene payload until fade-to-black completes; reveal starts only after camera playback begins. */
@Mixin(targets = {"dev.settledlands.npc.client.CutsceneCameraClient"}, remap = false)
public abstract class CutsceneCameraFadePlaybackMixin263 {
    @Inject(method = {"handlePlay([[DFI)V"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void settledlands$waitForBlack(double[][] track, float speed, int smooth,
                                                   CallbackInfo ci) {
        if (CutsceneFadeOverlay263.deferCameraStart(track, speed, smooth)) ci.cancel();
    }

    @Inject(method = {"handlePlaying(Z)V"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void settledlands$holdPlayingSignal(boolean playing, CallbackInfo ci) {
        if (playing && CutsceneFadeOverlay263.deferPlayingSignal()) ci.cancel();
    }

    @Inject(method = {"handlePlaying(Z)V"}, at = {@At("RETURN")}, remap = false)
    private static void settledlands$restoreAfterPlayback(boolean playing, CallbackInfo ci) {
        if (playing) CutsceneFadeOverlay263.cameraStarted();
        else CutsceneFadeOverlay263.playbackEnded();
    }
}
