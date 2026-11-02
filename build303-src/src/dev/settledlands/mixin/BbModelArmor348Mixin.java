package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BodyArmor348;
import dev.settledlands.npc.GuideNpc;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 348 - draw the armour right after the body's root part was drawn.
 *
 * <p>Both bodies the mod renders end in the same call: the root model part is drawn into the entity
 * buffer, and the pose stack at that instant is the game's own entity model space (the body methods
 * have already applied {@code scale(-1,-1,1)} and {@code translate(0,-1.501,0)}). Injecting right
 * after that call is therefore the one place where the vanilla armour model lands exactly where the
 * game would have put it - no transform has to be guessed, and the NPC path (which draws no held
 * items) is covered by the same rule instead of a second guess.</p>
 *
 * <p>The two targets are the preserved bodies of the two renderers
 * ({@code settledlands$renderActorOriginal} for the player body, {@code settledlands$renderNpcOriginal}
 * for an NPC); the public entry points hand over to the motion bridges, which call them again from
 * several branches, so the injection sits inside the method every branch ends in.</p>
 */
@Mixin(value = BbModel.class, remap = false)
public abstract class BbModelArmor348Mixin {
    private static final String ROOT_RENDER =
            "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    @Inject(
            method = "settledlands$renderActorOriginal(Lnet/minecraft/world/entity/LivingEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = ROOT_RENDER, shift = At.Shift.AFTER))
    private void settledlands$armorOnPlayer(LivingEntity entity, float yaw, float partial, PoseStack pose,
                                            MultiBufferSource buffer, int light, CallbackInfo info) {
        BodyArmor348.draw((BbModel) (Object) this, entity, pose, buffer, light);
    }

    @Inject(
            method = "settledlands$renderNpcOriginal(Ldev/settledlands/npc/GuideNpc;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = ROOT_RENDER, shift = At.Shift.AFTER))
    private void settledlands$armorOnNpc(GuideNpc npc, float yaw, float partial, PoseStack pose,
                                         MultiBufferSource buffer, int light, int lod, CallbackInfo info) {
        BodyArmor348.draw((BbModel) (Object) this, npc, pose, buffer, light);
    }
}
