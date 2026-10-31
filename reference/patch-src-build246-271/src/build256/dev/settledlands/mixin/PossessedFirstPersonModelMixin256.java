package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.settledlands.npc.client.FirstPersonPossession256;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AgeableListModel.class})
public abstract class PossessedFirstPersonModelMixin256 {
    @Inject(
        method = {"renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void settledlands$renderPossessedFpModelArms(PoseStack poseStack, VertexConsumer buffer,
                                                         int packedLight, int packedOverlay, int color,
                                                         CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel<?> pm
                && FirstPersonPossession256.renderPossessedFirstPersonModelArms(pm, poseStack, packedLight, packedOverlay, color)) {
            ci.cancel();
        }
    }
}
