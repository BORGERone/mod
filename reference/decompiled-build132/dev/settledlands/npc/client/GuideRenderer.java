package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import dev.settledlands.npc.GuideNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class GuideRenderer extends MobRenderer<GuideNpc, GuideModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("settledlands", "textures/entity/guide_npc.png");

    public GuideRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, GuideModel.create(), 0.9f);
    }

    @Override
    public void render(GuideNpc npc, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        String modelKey = npc.texturePath();
        BbModel customModel = BbModelManager.getModel(modelKey);
        if (customModel != null) {
            double distSq = 0.0;
            if (this.entityRenderDispatcher != null && this.entityRenderDispatcher.camera != null) {
                distSq = npc.distanceToSqr(this.entityRenderDispatcher.camera.getPosition());
            }

            // Extreme distance culling (> 128 blocks = 16384 distSq): entity is out of visual range
            if (distSq > 16384.0) {
                return;
            }

            // Multi-level geometric LOD:
            // LOD 0: 0 .. 24 blocks (distSq <= 576.0)
            // LOD 1: 24 .. 64 blocks (576.0 < distSq <= 4096.0)
            // LOD 2: 64 .. 128 blocks (4096.0 < distSq <= 16384.0)
            int lodLevel = distSq > 4096.0 ? 2 : (distSq > 576.0 ? 1 : 0);

            customModel.render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight, lodLevel);
            if (this.shouldShowName(npc)) {
                this.renderNameTag(npc, npc.getDisplayName(), poseStack, buffer, packedLight, partialTicks);
            }
        } else {
            super.render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GuideNpc npc) {
        String modelKey = npc.texturePath();
        BbModel customModel = BbModelManager.getModel(modelKey);
        if (customModel != null) {
            return customModel.getTextureLocation();
        }
        return customTexture(npc);
    }

    private static ResourceLocation customTexture(GuideNpc npc) {
        String path = npc.texturePath();
        if (path == null || path.isEmpty()) return TEXTURE;
        ResourceLocation loc = ResourceLocation.tryParse(path);
        if (loc == null) return TEXTURE;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getResourceManager().getResource(loc).isEmpty()) {
                return TEXTURE;
            }
        } catch (Throwable ignored) {
            return TEXTURE;
        }
        return loc;
    }

    @Override
    protected int getBlockLightLevel(GuideNpc npc, BlockPos pos) {
        return Math.max(6, super.getBlockLightLevel(npc, pos));
    }

    @Override
    protected float getShadowRadius(GuideNpc npc) {
        return super.getShadowRadius(npc);
    }

    @Override
    protected boolean shouldShowName(GuideNpc npc) {
        return super.shouldShowName(npc);
    }
}
