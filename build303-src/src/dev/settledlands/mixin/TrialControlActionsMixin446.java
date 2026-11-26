package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.trial.TrialControlActions446;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialModule447;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: executes {@link TrialControlNode446} actions ({@code trigger:spawner:...}) from
 * any graph (NPC, common, block, door, chest, or trial spawner).
 */
@Mixin(targets = "dev.settledlands.npc.NpcActions", remap = false)
public abstract class TrialControlActionsMixin446 {

    @Inject(method = "runSingle(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runTrialControlSingle446(ServerPlayer player, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (TrialModule447.isModuleAction(action)) {
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        if (TrialControlNode446.isControlAction(action)) {
            cir.setReturnValue(TrialControlActions446.execute(player, npc, action));
        }
    }

    @Inject(method = "runForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runTrialControlForEntity446(Entity entity, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (TrialModule447.isModuleAction(action)) {
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        if (TrialControlNode446.isControlAction(action)) {
            cir.setReturnValue(TrialControlActions446.executeForEntity(entity, npc, action));
        }
    }

    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runTrialControlTrigger446(ServerPlayer player, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (TrialModule447.isModuleAction(action)) {
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        if (TrialControlNode446.isStrippedControlAction(action)) {
            cir.setReturnValue(TrialControlActions446.execute(player, npc, action));
        }
    }
}
