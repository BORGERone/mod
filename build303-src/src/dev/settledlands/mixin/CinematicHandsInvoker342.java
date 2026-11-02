package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Build 342: a way into Cinematic Interactions' own model-arm pass.
 *
 * <p>{@code Hands.drawModelArm} is private to Cinematic and is the only place that places a scene arm
 * and then lets {@code PlayerRenderer.renderRightHand} draw it - the method Build 339 replaces with the
 * possessed NPC's arm. Reaching it through an {@link Invoker} keeps Cinematic's placement, reach and bob
 * intact instead of copying that geometry here, where it would drift away from the mod on its next
 * update. Mixin generates the accessor into the target class, so there is no reflection and no lookup
 * at render time.</p>
 *
 * <p>{@link Pseudo} keeps the mod loadable without Cinematic Interactions; the caller also refuses
 * before asking when the player is not possessed.</p>
 */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.Hands", remap = false)
public interface CinematicHandsInvoker342 {

    @Invoker("drawModelArm")
    static void settledlands$drawModelArm(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera,
            Vec3 hand, Vec3 shoulder, boolean right, int packedLight, Vec3 forward, LocalPlayer player) {
        throw new AssertionError("Mixin accessor");
    }
}
