package dev.settledlands.mixin;

import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.GuideNpc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 436: handles «Пул предметов» nodes gracefully during runtime graph execution by treating
 * them as no-ops (since loot generation only occurs during {@code settledlands:hell_dungeon} room
 * placement).
 */
@Mixin(targets = "dev.settledlands.npc.NpcActions", remap = false)
public abstract class LootPoolActionsMixin436 {

    @Inject(method = "runSingle(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$skipLootPoolSingle436(ServerPlayer player, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (LootPool436.isLootPoolAction(action)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "runForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$skipLootPoolForEntity436(Entity entity, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (LootPool436.isLootPoolAction(action)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$skipLootPoolTrigger436(ServerPlayer player, GuideNpc npc,
            int relation, String action, CallbackInfoReturnable<Boolean> cir) {
        if (LootPool436.isStrippedLootPoolAction(action)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
