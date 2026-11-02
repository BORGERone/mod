package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.CinematicLegs344;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 344: the legs Cinematic Interactions folds at a campfire are the possessed NPC's legs.
 *
 * <p>{@code LegMesh.draw} is the whole of the seated body in that scene: it builds the thigh and shin
 * cubes between the joints the station worked out and textures them with the player's skin, so a
 * possessed player - an Ivy rig - sat at the fire with the player's own legs. The joints themselves are
 * Cinematic's and are left alone; only the mesh drawn between them changes.</p>
 *
 * <p>This is a plain class mixin with {@code require = 0} and a single HEAD injection, exactly like the
 * hands of Build 343 - never an interface mixin, and never an accessor, the shape Mixin refused for a
 * class target in build 342. When the hand-over cannot be made - not possessed, no leg bone, no model,
 * any failure - nothing is cancelled and Cinematic draws its own legs, so a scene never loses a body.</p>
 */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.LegMesh", remap = false)
public abstract class CinematicLegMesh344 {

    @Inject(
            method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/player/LocalPlayer;Z"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$npcSceneLegs(PoseStack poseStack, MultiBufferSource buffers,
            Vec3 camera, LocalPlayer player, boolean right, Vec3 hip, Vec3 knee, Vec3 ankle,
            Vec3 reference, int packedLight, CallbackInfo ci) {
        if (CinematicLegs344.drawLegs(poseStack, buffers, camera, player, right, hip, knee, ankle,
                reference, packedLight)) {
            ci.cancel();
        }
    }
}
