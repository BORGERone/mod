package net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;

/** Compile-time shape of the player render event the mod's listeners take. */
public class RenderPlayerEvent {

    private final Player entity;
    private final PoseStack poseStack;
    private final MultiBufferSource buffer;
    private final int packedLight;
    private final float partialTick;

    public RenderPlayerEvent(Player entity, PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, float partialTick) {
        this.entity = entity;
        this.poseStack = poseStack;
        this.buffer = buffer;
        this.packedLight = packedLight;
        this.partialTick = partialTick;
    }

    public Player getEntity() {
        return entity;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public MultiBufferSource getMultiBufferSource() {
        return buffer;
    }

    public int getPackedLight() {
        return packedLight;
    }

    public float getPartialTick() {
        return partialTick;
    }

    /** Fired before a player is drawn; cancelling it skips the player for this pass. */
    public static class Pre extends RenderPlayerEvent {

        public Pre(Player entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                   float partialTick) {
            super(entity, poseStack, buffer, packedLight, partialTick);
        }

        public void setCanceled(boolean canceled) {
        }
    }
}
