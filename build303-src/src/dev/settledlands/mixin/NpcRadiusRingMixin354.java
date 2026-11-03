package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.RadiusPass354;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 354: a body's radius ring travels, and a body gets one for "В радиусе" too, not only for
 * "Цель".
 *
 * <p>Build 320 ticks a body's radius itself: it walks the dialogue graph, finds the "Цель" node and
 * draws a still circle of twelve points. That left the check node - the one the palette adds under
 * the name "В радиусе" - with a radius field and no ring at all, which is why an author with an NPC
 * saw a radius on a block but not on a body. So the whole drawing is taken over at the head of
 * {@code NpcRadiusZone320#tick}: {@link RadiusPass354#npcTick} draws a travelling ring for every
 * radius node of the body that has its visibility on, and the original body is cancelled. The node
 * lookup, the flag and the card toggle of build 320 stay exactly as they were - the same field is
 * read and written, only the picture moved.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcRadiusZone320", remap = false)
public abstract class NpcRadiusRingMixin354 {

    @Inject(method = "tick(Ldev/settledlands/npc/GuideNpc;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void settledlands$travellingRing(GuideNpc npc, CallbackInfo ci) {
        RadiusPass354.npcTick(npc);
        ci.cancel();
    }
}
