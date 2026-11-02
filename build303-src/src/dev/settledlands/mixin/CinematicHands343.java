package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.CinematicArms343;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 343: a Cinematic Interactions scene hand is drawn by the same pass Build 339 already gives to
 * the possessed NPC.
 *
 * <p>{@code Hands.drawArm} is the entry point of every scene arm: every arm style ends up texturing the
 * player's own skin, so a possessed player - an Ivy rig - was given the player's arm at the NPC's
 * crafting table. Cinematic's model path, {@code Hands.drawModelArm}, places the arm at the station and
 * calls {@code PlayerRenderer.renderRightHand} / {@code renderLeftHand}, which Build 339 replaces with
 * the possessed NPC's arm. For a possessed player the call is moved onto that path, so the arm keeps
 * Cinematic's own reach, bob and held item and the hand that appears is the NPC's.</p>
 *
 * <p>This is the only thing this build asks Mixin for on Cinematic, and it is a plain class mixin with
 * {@code require = 0}: build 342 also declared an {@code @Invoker} in an interface mixin, which Mixin
 * refuses for a class target and which killed the client in PREPARE. The private method is now reached
 * from {@link CinematicArms343}, resolved once and never at startup.</p>
 *
 * <p>The handler takes no action for an unpossessed player, for an arm Cinematic itself would not draw,
 * and whenever the hand-over cannot be made - in all of those cases Cinematic's own arm is drawn by
 * Cinematic, so a scene never loses a hand.</p>
 */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.Hands", remap = false)
public abstract class CinematicHands343 {

    @Inject(
            method = "drawArm(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;ZILnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$npcSceneHand(PoseStack poseStack, MultiBufferSource buffers,
            Vec3 camera, Vec3 hand, Vec3 shoulder, boolean right, int packedLight, Vec3 forward,
            CallbackInfo ci) {
        LocalPlayer player = CinematicArms343.handOwner(hand, shoulder);
        if (player == null) return;
        if (CinematicArms343.drawModelArm(poseStack, buffers, camera, hand, shoulder, right, packedLight,
                forward, player)) {
            ci.cancel();
        }
    }
}
