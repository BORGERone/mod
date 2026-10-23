package com.hazelism.nomansland.client.renderer;

import com.hazelism.nomansland.client.model.PolyMeshCache;
import com.hazelism.nomansland.client.model.UndeadModel;
import com.hazelism.nomansland.entity.UndeadEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renders the cube bones and animates every bone; this renderer also draws
 * the Bedrock poly_mesh geometry (head/jaw/body detail) that GeckoLib cannot bake.
 *
 * <p>{@link #renderCubesOfBone} is called by GeckoLib with the PoseStack already
 * transformed into the bone's animated space, so the pre-baked Bedrock vertices
 * (absolute model-space coords - same convention as Bedrock cubes) can be emitted
 * directly through the same VertexConsumer. Per frame: no allocations besides
 * GeckoLib's own - the two temp vectors are reused instance fields (rendering is
 * single-threaded on the client).
 */
public class UndeadRenderer extends GeoEntityRenderer<UndeadEntity> {
    private static final String POLYMESH_MODEL = "undead";

    private final Vector3f tmpPos = new Vector3f();
    private final Vector3f tmpNormal = new Vector3f();

    public UndeadRenderer(EntityRendererProvider.Context context) {
        super(context, new UndeadModel());
        this.shadowRadius = 0.5F; // matches a 0.6-wide humanoid
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight,
                                  int packedOverlay, int colour) {
        if (bone.isHidden())
            return;

        // Same as GeckoLib's default implementation (interface default, not reachable via super here).
        for (GeoCube cube : bone.getCubes()) {
            poseStack.pushPose();
            renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }

        float[] mesh = PolyMeshCache.getBone(POLYMESH_MODEL, bone.getName());
        if (mesh == null || buffer == null)
            return;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        int stride = PolyMeshCache.stride();

        for (int i = 0; i < mesh.length; i += stride) {
            tmpPos.set(mesh[i], mesh[i + 1], mesh[i + 2]).transformPosition(matrix);
            tmpNormal.set(mesh[i + 3], mesh[i + 4], mesh[i + 5]).transform(normalMatrix);
            buffer.addVertex(tmpPos.x(), tmpPos.y(), tmpPos.z(), colour,
                    mesh[i + 6], mesh[i + 7], packedOverlay, packedLight,
                    tmpNormal.x(), tmpNormal.y(), tmpNormal.z());
        }
    }
}
