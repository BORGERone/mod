package dev.settledlands.mixin;

import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 450: re-rolls {@code TIMER}, {@code TIME}, {@code CHECK}, {@code DAMAGE}, and {@code RADIUS}
 * ranges after an event node fires on a block/door/chest/trial graph. Zero synthetic lambdas.
 */
@Mixin(value = RadiusPass354.class, remap = false)
public abstract class RadiusRange450Mixin {

    @Inject(method = "fired(Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph$Node;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$rerollEventFired450(RadiusPass354.Spot spot, NpcDialogueGraph.Node node,
            ServerPlayer player, CallbackInfo ci) {
        GraphRanges450.rerollNode(node, ThreadLocalRandom.current());
    }
}
