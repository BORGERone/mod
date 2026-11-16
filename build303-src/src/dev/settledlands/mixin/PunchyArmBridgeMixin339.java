package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.PunchyArm339;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 339: hands Punchy!'s arm pass over to the possessed NPC.
 *
 * <p>Punchy! is closed source, so this hooks it by name only, as every other bridge to it does.
 * {@code renderArm} runs once per visible arm with the arm Punchy! is about to draw and the
 * {@code PoseStack} it would draw it in; control is handed to the NPC's arm, which takes that arm's own
 * transform before drawing, so it lands exactly where Punchy!'s arm would have been. The following
 * {@code renderArmArmor} would draw the player's own armour sleeve over an arm that is no longer the
 * player's, and is skipped for that arm only.</p>
 *
 * <p>{@link Pseudo} keeps the mod loadable without Punchy!: an absent target is skipped instead of
 * failing, and {@code require = 0} keeps this harmless on a Punchy! build whose renderer has no such
 * method. The handler takes no action unless the player is possessed.</p>
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmBridgeMixin339 {

    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$npcArm(PlayerModel<?> model, AbstractClientPlayer player,
            HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            ResourceLocation texture, boolean slim, float partialTick, CallbackInfo ci) {
        if (PunchyArm339.replace(model, player, arm, poseStack, buffer, packedLight)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmArmor", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$noPlayerSleeve(PlayerModel<?> model, AbstractClientPlayer player,
            HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            boolean slim, float partialTick, CallbackInfo ci) {
        if (PunchyArm339.consumeArmor(arm)) {
            ci.cancel();
        }
    }
}
