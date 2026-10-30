package dev.settledlands.mixin;

import dev.settledlands.npc.CutsceneFadeControl263;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the next graph node's fade settings when sequential cutscenes chain. */
@Mixin(targets = {"dev.settledlands.npc.CutsceneCamera"}, remap = false)
public abstract class CutsceneCameraFadeChainMixin263 {
    @Inject(method = {"playChained(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z"},
            at = {@At("HEAD")}, remap = false)
    private static void settledlands$prepareChainedFade(ServerPlayer player, String scene,
            CallbackInfoReturnable<Boolean> cir) {
        CutsceneFadeControl263.prepareChained(player, scene);
    }

    @Inject(method = {"playChained(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z"},
            at = {@At("RETURN")}, remap = false)
    private static void settledlands$clearFailedChain(ServerPlayer player, String scene,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) CutsceneFadeControl263.clearChain(player);
    }

    @Inject(method = {"onLogout(Lnet/minecraft/server/level/ServerPlayer;)V"},
            at = {@At("RETURN")}, remap = false)
    private static void settledlands$clearOnLogout(ServerPlayer player, CallbackInfo ci) {
        CutsceneFadeControl263.clearChain(player);
    }
}
