package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcRadiusZone320;
import dev.settledlands.npc.RadiusZone456;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 456: suppresses the legacy circular particle ring in {@link NpcRadiusZone320#tick}
 * whenever a radius node is configured for Square mode (the square outline is drawn by
 * {@link RadiusZonePassMixin456} inside {@code RadiusPass354.npcTick}). Zero lambdas.
 */
@Mixin(value = NpcRadiusZone320.class, remap = false)
public abstract class RadiusZoneLegacy320Mixin456 {

    @Shadow
    public static boolean visible(NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Redirect(method = "tick(Ldev/settledlands/npc/GuideNpc;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcRadiusZone320;visible(Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            remap = false)
    private static boolean settledlands$skipCircleRingWhenSquare456(NpcDialogueGraph.Node node) {
        if (RadiusZone456.isSquare(node)) {
            return false;
        }
        return visible(node);
    }
}
