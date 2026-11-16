package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.PunchyArm338;
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
 * Build 338: hands Punchy!'s arm pass over to the possessed NPC.
 *
 * <p>Punchy! is closed source, so this hooks it by name only, exactly as the other mods that bridge
 * it do. {@code renderArm} is called once per visible arm at the head of the pass, with the texture
 * it was going to use; the mod's own arm renderer needs exactly that pose and one arm, so the NPC arm
 * is submitted there and Punchy!'s arm is cancelled. {@code renderArmArmor} follows immediately with
 * the player's armour sleeve, which would be drawn over an arm that is no longer the player's - it is
 * skipped for that arm only.</p>
 *
 * <p>{@link Pseudo} keeps the mod loadable without Punchy!: when the target class is absent the mixin
 * is skipped instead of failing, and {@code require = 0} keeps it harmless on a Punchy! build whose
 * renderer has no such method. The handler takes no action unless the player is possessed.</p>
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmBridgeMixin338 {

    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$npcArm(PlayerModel<?> model, AbstractClientPlayer player,
            HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            ResourceLocation texture, boolean slim, float partialTick, CallbackInfo ci) {
        if (PunchyArm338.replace(model, player, arm, poseStack, buffer, packedLight, slim)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmArmor", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void settledlands$noPlayerSleeve(PlayerModel<?> model, AbstractClientPlayer player,
            HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            boolean slim, float partialTick, CallbackInfo ci) {
        if (PunchyArm338.consumeArmor(arm)) {
            ci.cancel();
        }
    }
}
