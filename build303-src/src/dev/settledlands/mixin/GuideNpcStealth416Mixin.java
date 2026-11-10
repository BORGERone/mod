package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcHnsStealth416;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 416: gates {@code GuideNpc#customServerAiStep()} player head-tracking through
 * {@link NpcHnsStealth416} so sneaking / occluded / behind-the-back players are not spotted by
 * 360-degree {@code yHeadRot} auto-aim, and clears Hackers 'n Slashers sight entries on non-perceiving
 * {@code GuideTriggerNpc} / possessed {@code GuideNpc} instances.
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class GuideNpcStealth416Mixin {

    @Shadow
    private Player trackedLookPlayer;

    @Shadow
    private int lookScanTimer;

    @Inject(method = "customServerAiStep()V", at = @At("HEAD"), require = 0, remap = false)
    private void settledlands$clearBlindSightOnTick(CallbackInfo info) {
        NpcHnsStealth416.clearBlindEntitySight((GuideNpc) (Object) this);
    }

    @Redirect(
            method = "customServerAiStep()V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcAutoAiEngine255;shouldLookAtPlayer(Ldev/settledlands/npc/GuideNpc;)Z"
            ),
            require = 0,
            remap = false
    )
    private boolean settledlands$shouldLookAtPlayer(GuideNpc npc) {
        return NpcHnsStealth416.shouldLookAtPlayer(npc);
    }

    @Redirect(
            method = "customServerAiStep()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"
            ),
            require = 0,
            remap = false
    )
    private Player settledlands$pickVisibleLookPlayer(Level level, Entity entity, double maxDist) {
        return NpcHnsStealth416.pickVisibleLookPlayer(level, entity, maxDist);
    }

    @Redirect(
            method = "customServerAiStep()V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcAutoAiEngine255;lookHeadAtPlayer(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/entity/player/Player;)V"
            ),
            require = 0,
            remap = false
    )
    private void settledlands$lookHeadAtVisiblePlayer(GuideNpc npc, Player player) {
        if (!NpcHnsStealth416.lookHeadAtVisiblePlayer(npc, player)) {
            this.trackedLookPlayer = null;
            this.lookScanTimer = 0;
        }
    }
}
