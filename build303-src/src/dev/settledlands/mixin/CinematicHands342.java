package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.CinematicArms342;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 342: a Cinematic Interactions scene hand is drawn by the same pass Build 339 already gives to
 * the possessed NPC.
 *
 * <p>{@code Hands.drawArm} is the entry point of every scene arm: it decides between the cube arms
 * (arm styles 0 and 2) and the model arm, and every one of those styles ends up texturing the player's
 * own skin - the player's arm at the NPC's crafting table. Cinematic's model path,
 * {@code Hands.drawModelArm}, places the arm at the station and calls
 * {@code PlayerRenderer.renderRightHand} / {@code renderLeftHand}, which Build 339 replaces with the
 * possessed NPC's arm. So for a possessed player the call is simply moved onto that path: the arm keeps
 * Cinematic's own reach, bob and held item, and the hand that appears is the NPC's.</p>
 *
 * <p>The handler takes no action for an unpossessed player, for an arm Cinematic itself would not draw,
 * and on any failure - in all of those cases Cinematic's own arm is drawn by Cinematic, so a scene never
 * loses a hand. {@link Pseudo} keeps the mod loadable without Cinematic Interactions, and
 * {@code require = 0} keeps this harmless on a build whose {@code drawArm} has another shape.</p>
 */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.Hands", remap = false)
public abstract class CinematicHands342 {

    @Inject(
            method = "drawArm(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;ZILnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$npcSceneHand(PoseStack poseStack, MultiBufferSource buffers,
            Vec3 camera, Vec3 hand, Vec3 shoulder, boolean right, int packedLight, Vec3 forward,
            CallbackInfo ci) {
        LocalPlayer player = CinematicArms342.handOwner(hand, shoulder);
        if (player == null) return;
        try {
            CinematicHandsInvoker342.settledlands$drawModelArm(poseStack, buffers, camera, hand, shoulder,
                    right, packedLight, forward, player);
            ci.cancel();
        } catch (Throwable failure) {
            CinematicArms342.noteFailure(failure);
        }
    }
}
