package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.FirstPersonPossession256;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerRenderer.class})
public abstract class PossessedFirstPersonHandMixin256 {
    @Inject(
        method = {"render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
        at = {@At("HEAD")},
        require = 0
    )
    private void settledlands$pushPossessedFpContext(AbstractClientPlayer player, float entityYaw, float partialTick,
                                                     PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                     CallbackInfo ci) {
        FirstPersonPossession256.pushPlayerRenderContext((PlayerRenderer) (Object) this, player, buffer, packedLight);
    }

    @Inject(
        method = {"render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
        at = {@At("RETURN")},
        require = 0
    )
    private void settledlands$popPossessedFpContext(AbstractClientPlayer player, float entityYaw, float partialTick,
                                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                    CallbackInfo ci) {
        FirstPersonPossession256.popPlayerRenderContext();
    }

    @Inject(
        method = {"renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void settledlands$renderPossessedRightHand(PoseStack poseStack, MultiBufferSource buffer,
                                                       int combinedLight, AbstractClientPlayer player,
                                                       CallbackInfo ci) {
        if (FirstPersonPossession256.renderPossessedArm(poseStack, buffer, combinedLight, player, true)) {
            ci.cancel();
        }
    }

    @Inject(
        method = {"renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void settledlands$renderPossessedLeftHand(PoseStack poseStack, MultiBufferSource buffer,
                                                      int combinedLight, AbstractClientPlayer player,
                                                      CallbackInfo ci) {
        if (FirstPersonPossession256.renderPossessedArm(poseStack, buffer, combinedLight, player, false)) {
            ci.cancel();
        }
    }
}
