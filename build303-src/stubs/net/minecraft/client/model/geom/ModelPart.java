package net.minecraft.client.model.geom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.stream.Stream;

/**
 * Compile-time shape of the game's model part, as much of it as the mod touches.
 *
 * <p>The transform fields are the same public floats the real class carries - the base jar already
 * writes every one of them from {@code NpcMotionBridge256.Pose#set} - and the methods are the ones the
 * possession arm renderer of Build 256 already calls in game ({@code resetPose}, {@code getAllParts},
 * {@code render}). {@code translateAndRotate} is vanilla's own arm transform, used by the game to put
 * a part into its animated space. Nothing here invents an API.</p>
 */
public class ModelPart {
    public float x;
    public float y;
    public float z;
    public float xRot;
    public float yRot;
    public float zRot;
    public boolean visible = true;
    public boolean skipDraw;
    public float xScale = 1.0F;
    public float yScale = 1.0F;
    public float zScale = 1.0F;

    public void resetPose() {
    }

    public void translateAndRotate(PoseStack poseStack) {
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                       int color) {
    }

    public Stream<ModelPart> getAllParts() {
        return Stream.of(this);
    }
}
