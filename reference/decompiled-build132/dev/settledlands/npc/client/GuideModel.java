package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.settledlands.npc.GuideNpc;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

public class GuideModel extends EntityModel<GuideNpc> {
   private static final String[][] PATHS = new String[][]{
      {"body", "body"},
      {"chest", "body", "chest"},
      {"head", "head"},
      {"jaw", "head", "jaw"},
      {"feet", "feet"},
      {"arm_left", "arm_left"},
      {"arm_left_lower", "arm_left", "arm_left_lower"},
      {"arm_right", "arm_right"},
      {"arm_right_lower", "arm_right", "arm_right_lower"},
      {"staff", "arm_right", "arm_right_lower", "staff"},
      {"props", "props"}
   };
   private final ModelPart root;
   private final Map<String, ModelPart> parts = new HashMap<>();
   private final Map<String, float[]> rest = new HashMap<>();

   public GuideModel(ModelPart var1) {
      this.root = var1;
      ModelPart var2 = GuideModelData.boneRoot(var1);

      for (String[] var6 : PATHS) {
         ModelPart var7 = var2.getChild(var6[1]);

         for (int var8 = 2; var8 < var6.length; var8++) {
            var7 = var7.getChild(var6[var8]);
         }

         this.parts.put(var6[0], var7);
         this.rest.put(var6[0], new float[]{var7.xRot, var7.yRot, var7.zRot});
      }
   }

   public static GuideModel create() {
      return new GuideModel(GuideModelData.layer().bakeRoot());
   }

   public ModelPart part(String var1) {
      return this.parts.get(var1);
   }

   public float[] rest(String var1) {
      return this.rest.get(var1);
   }

   public Iterable<String> boneNames() {
      return this.parts.keySet();
   }

   public void setupAnim(GuideNpc var1, float var2, float var3, float var4, float var5, float var6) {
      GuideAnim.apply(this, var1, var4, var5, var6);
      this.applyPose(var1.poseIndex());
   }

   private void applyPose(int var1) {
      if (var1 == 1) {
         ModelPart var2 = this.part("arm_left");
         ModelPart var3 = this.part("arm_left_lower");
         ModelPart var4 = this.part("arm_right");
         ModelPart var5 = this.part("arm_right_lower");
         if (var2 != null) {
            var2.xRot = -1.15F;
            var2.zRot = 0.75F;
         }

         if (var3 != null) {
            var3.xRot = -0.9F;
         }

         if (var4 != null) {
            var4.xRot = -1.15F;
            var4.zRot = -0.75F;
         }

         if (var5 != null) {
            var5.xRot = -0.9F;
         }
      } else if (var1 == 2) {
         ModelPart var6 = this.part("arm_right");
         ModelPart var7 = this.part("arm_right_lower");
         if (var6 != null) {
            var6.xRot = -0.35F;
            var6.zRot = -0.12F;
         }

         if (var7 != null) {
            var7.xRot = -0.25F;
         }
      }
   }

   public void renderToBuffer(PoseStack var1, VertexConsumer var2, int var3, int var4, int var5) {
      this.root.render(var1, var2, var3, var4, var5);
   }
}
