package dev.settledlands.mixin;

import dev.settledlands.trial.TrialGraph446;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: routes {@code CfgSave} packets carrying a trial spawner editor id to
 * {@link TrialGraph446#save} instead of trying to resolve a {@code GuideNpc} entity.
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcTrialSave446Mixin {

    @Inject(method = "applyConfig", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialSave446(ServerPlayer player, int entityId,
            CompoundTag data, CallbackInfo ci) {
        if (!TrialGraph446.isTrialEditorId(entityId)) return;
        ci.cancel();
        TrialGraph446.save(player, entityId, data);
    }
}
