package dev.settledlands.mixin.compat;

import dev.settledlands.trial.TrialSpawnerEvents446;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: optional Trial Tuner compatibility mixin that notifies SettledLands whenever a
 * {@code trialtuner:trial_spawner} starts or finishes a wave.
 */
@Pseudo
@Mixin(targets = "dev.arena.trialtuner.content.TrialSpawnerBlockEntity", remap = false)
public abstract class TrialSpawnerEntityMixin446 {

    @Inject(method = "startWave(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;J)V",
            at = @At("RETURN"), require = 0, remap = false)
    private void settledlands$onWaveStart446(ServerLevel serverLevel, List<?> players, long now,
            CallbackInfo ci) {
        TrialSpawnerEvents446.handleWaveStart(this, serverLevel);
    }

    @Inject(method = "finishWave(Lnet/minecraft/server/level/ServerLevel;J)V",
            at = @At("HEAD"), require = 0, remap = false)
    private void settledlands$onWaveClear446(ServerLevel serverLevel, long now, CallbackInfo ci) {
        TrialSpawnerEvents446.handleWaveClear(this, serverLevel);
    }
}
