package com.hazelism.nomansland.client.renderer;

import com.hazelism.nomansland.client.model.PolyMeshCache;
import com.hazelism.nomansland.client.model.UndeadModel;
import com.hazelism.nomansland.entity.UndeadEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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
 * GeckoLib's own - the transform is plain float math on local variables.
 */
public class UndeadRenderer extends GeoEntityRenderer<UndeadEntity> {
    private static final String POLYMESH_MODEL = "undead";


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

        // Manual matrix transform (JOML accessor API only - stable across versions, no allocation).
        for (int i = 0; i < mesh.length; i += stride) {
            float x = mesh[i], y = mesh[i + 1], z = mesh[i + 2];
            float px = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
            float py = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
            float pz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();

            float nx = mesh[i + 3], ny = mesh[i + 4], nz = mesh[i + 5];
            float tx = normalMatrix.m00() * nx + normalMatrix.m10() * ny + normalMatrix.m20() * nz;
            float ty = normalMatrix.m01() * nx + normalMatrix.m11() * ny + normalMatrix.m21() * nz;
            float tz = normalMatrix.m02() * nx + normalMatrix.m12() * ny + normalMatrix.m22() * nz;

            buffer.addVertex(px, py, pz, colour,
                    mesh[i + 6], mesh[i + 7], packedOverlay, packedLight, tx, ty, tz);
        }
    }
}
