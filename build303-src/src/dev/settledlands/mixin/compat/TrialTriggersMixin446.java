package dev.settledlands.mixin.compat;

import dev.settledlands.trial.TrialSpawnerEvents446;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: optional Trial Tuner compatibility mixin that fires SettledLands spawner destruction
 * chains right before Trial Tuner removes/destroys the block entity.
 */
@Pseudo
@Mixin(targets = "dev.arena.trialtuner.trigger.TrialTriggers", remap = false)
public abstract class TrialTriggersMixin446 {

    @Inject(method = "onDestroyed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), require = 0, remap = false)
    private static void settledlands$onSpawnerDestroyed446(Level level, BlockPos pos,
            CallbackInfo ci) {
        TrialSpawnerEvents446.handleSpawnerDestroyed(level, pos);
    }
}
