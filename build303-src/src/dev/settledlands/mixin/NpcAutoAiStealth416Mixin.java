package dev.settledlands.mixin;

import dev.settledlands.npc.NpcHnsStealth416;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 416: prevents {@code NpcAutoAiEngine255#tickIdleLookAround(GuideNpc)} from freezing an NPC's
 * idle head/body turns when a nearby player is hidden behind the NPC, behind a wall, or concealed by
 * Hackers 'n Slashers stealth.
 */
@Mixin(targets = "dev.settledlands.npc.NpcAutoAiEngine255", remap = false)
public abstract class NpcAutoAiStealth416Mixin {

    @Redirect(
            method = "tickIdleLookAround(Ldev/settledlands/npc/GuideNpc;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"
            ),
            require = 0,
            remap = false
    )
    private static Player settledlands$idleLookVisiblePlayer(Level level, Entity entity, double maxDist) {
        return NpcHnsStealth416.pickVisibleLookPlayer(level, entity, maxDist);
    }
}
