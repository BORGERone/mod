package dev.settledlands.mixin;

import dev.settledlands.npc.NpcActions;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarDataNode453;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: keeps {@link VarDataNode453} action markers silent inside {@link NpcActions}.
 * Zero synthetic lambdas.
 */
@Mixin(value = NpcActions.class, remap = false)
public abstract class VarActionsSilentMixin453 {

    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$ignoreVarDataTrigger453(ServerPlayer player,
            dev.settledlands.npc.GuideNpc npc, int relation, String body,
            CallbackInfoReturnable<Boolean> cir) {
        if (VarDataNode453.isStrippedVarDataAction(body)
                || VarDataNode453.isVarDataAction(body)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "runSingle(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$ignoreVarDataRunSingle453(ServerPlayer player,
            dev.settledlands.npc.GuideNpc npc, int relation, String line,
            CallbackInfoReturnable<Boolean> cir) {
        if (VarDataNode453.isVarDataAction(line)
                || VarDataNode453.isStrippedVarDataAction(line)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
