package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.TeleportActions424;
import dev.settledlands.npc.TeleportNode424;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 424: executes «Телепорт» actions cleanly through {@code NpcActions}.
 */
@Mixin(targets = "dev.settledlands.npc.NpcActions", remap = false)
public abstract class TeleportActionsMixin424 {

    @Inject(method = "runSingle(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runTeleportSingle424(ServerPlayer player, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (TeleportNode424.isTeleportAction(action)) {
            cir.setReturnValue(player != null && TeleportActions424.teleport(player, action));
        }
    }

    @Inject(method = "runForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runTeleportForEntity424(Entity entity, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (TeleportNode424.isTeleportAction(action)) {
            cir.setReturnValue(TeleportActions424.executeForEntity(entity, action));
        }
    }

    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$runTeleportTrigger424(ServerPlayer player, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (TeleportNode424.isStrippedTeleportAction(action)) {
            cir.setReturnValue(player != null && TeleportActions424.teleport(player, action));
        }
    }
}
