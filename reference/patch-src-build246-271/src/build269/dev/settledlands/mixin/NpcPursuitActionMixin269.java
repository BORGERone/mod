package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcPursuit269;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.settledlands.npc.NpcActions", remap = false)
public abstract class NpcPursuitActionMixin269 {
    @Inject(
            method = "runSingle(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runPursuitAction(ServerPlayer player, GuideNpc npc, int source,
                                                       String action, CallbackInfoReturnable<Boolean> cir) {
        if (NpcPursuit269.isPursuitAction(action)) {
            cir.setReturnValue(NpcPursuit269.run(player, npc, action));
        }
    }

    @Inject(
            method = "runForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runPursuitActionForEntity(Entity actor, GuideNpc npc, int source,
                                                                String action, CallbackInfoReturnable<Boolean> cir) {
        if (NpcPursuit269.isPursuitAction(action)) {
            cir.setReturnValue(NpcPursuit269.run(null, npc, action));
        }
    }
}
