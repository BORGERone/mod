package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.FirstPersonPossession256;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets the ordinary hands pass so PlayerModel-based renderers such as Punchy can inherit NPC arms. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class PunchyFirstPersonContextMixin261 {
    @Inject(
        method = {"renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"},
        at = {@At("HEAD")},
        require = 0
    )
    private void settledlands$pushFirstPersonContext(float partialTick, PoseStack poseStack,
                                                     MultiBufferSource.BufferSource buffer,
                                                     LocalPlayer player, int packedLight,
                                                     CallbackInfo ci) {
        FirstPersonPossession256.pushFirstPersonRenderContext(player, buffer, packedLight);
    }

    @Inject(
        method = {"renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"},
        at = {@At("RETURN")},
        require = 0
    )
    private void settledlands$popFirstPersonContext(float partialTick, PoseStack poseStack,
                                                    MultiBufferSource.BufferSource buffer,
                                                    LocalPlayer player, int packedLight,
                                                    CallbackInfo ci) {
        FirstPersonPossession256.popPlayerRenderContext();
    }
}
