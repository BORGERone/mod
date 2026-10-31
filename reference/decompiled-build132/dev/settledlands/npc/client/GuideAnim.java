package dev.settledlands.npc.client;

import dev.settledlands.npc.GuideNpc;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class GuideAnim {
   private static final float IDLE_LENGTH = 4.0F;
   private static final float TALK_LENGTH = 2.4F;
   private static final float[][] IDLE_BODY = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 1.5F, 0.0F}, {2.0F, 0.0F, 0.0F, 0.0F}, {3.0F, 0.0F, -1.5F, 0.0F}, {4.0F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] IDLE_HEAD = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {1.2F, 2.0F, 3.0F, 0.0F}, {2.4F, -1.0F, 0.0F, 1.5F}, {3.6F, 1.0F, -3.0F, -1.0F}, {4.0F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] IDLE_ARM_LEFT = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {1.6F, 0.0F, 0.0F, -2.5F}, {3.2F, 0.0F, 0.0F, 1.5F}, {4.0F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] IDLE_ARM_LEFT_LOWER = new float[][]{{0.0F, 0.0F, 0.0F, 0.0F}, {2.0F, 0.0F, 0.0F, -1.5F}, {4.0F, 0.0F, 0.0F, 0.0F}};
   private static final float[][] IDLE_ARM_RIGHT = new float[][]{{0.0F, 0.0F, 0.0F, 0.0F}, {2.0F, 1.5F, 0.0F, 0.0F}, {4.0F, 0.0F, 0.0F, 0.0F}};
   private static final float[][] IDLE_PROPS = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F, 4.0F}, {2.0F, 0.0F, 0.0F, 0.0F}, {3.0F, 0.0F, 0.0F, -4.0F}, {4.0F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] IDLE_JAW = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {1.2F, 3.0F, 0.0F, 0.0F}, {2.0F, 0.0F, 0.0F, 0.0F}, {3.4F, 1.5F, 0.0F, 0.0F}, {4.0F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] TALK_JAW = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F},
      {0.2F, 20.0F, 0.0F, 0.0F},
      {0.4F, 3.0F, 0.0F, 0.0F},
      {0.7F, 24.0F, 0.0F, 0.0F},
      {0.9F, 2.0F, 0.0F, 0.0F},
      {1.2F, 15.0F, 0.0F, 0.0F},
      {1.4F, 0.0F, 0.0F, 0.0F},
      {1.7F, 22.0F, 0.0F, 0.0F},
      {1.9F, 4.0F, 0.0F, 0.0F},
      {2.1F, 13.0F, 0.0F, 0.0F},
      {2.4F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] TALK_HEAD = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {0.6F, 2.5F, 3.0F, 0.0F}, {1.2F, -1.5F, -2.0F, 1.0F}, {1.8F, 1.5F, 2.0F, -1.0F}, {2.4F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] TALK_ARM_LEFT = new float[][]{{0.0F, 0.0F, 0.0F, 0.0F}, {1.2F, 0.0F, 0.0F, -5.0F}, {2.4F, 0.0F, 0.0F, 0.0F}};
   private static final float[][] TALK_ARM_LEFT_LOWER = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F}, {1.0F, -8.0F, 0.0F, 0.0F}, {1.8F, 0.0F, 0.0F, 0.0F}, {2.4F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] TALK_BODY = new float[][]{{0.0F, 0.0F, 0.0F, 0.0F}, {1.2F, 0.0F, 1.0F, 0.0F}, {2.4F, 0.0F, 0.0F, 0.0F}};
   private static final Map<String, float[][]> IDLE = new HashMap<>();
   private static final Map<String, float[][]> TALK = new HashMap<>();

   private GuideAnim() {
   }

   public static void apply(GuideModel var0, GuideNpc var1, float var2, float var3, float var4) {
      boolean var5 = var1.isTalking();
      Map var6 = var5 ? TALK : IDLE;
      float var7 = var5 ? 2.4F : 4.0F;
      float var8 = var2 / 20.0F % var7;

      for (String var10 : var0.boneNames()) {
         ModelPart var11 = var0.part(var10);
         float[] var12 = var0.rest(var10);
         var11.xRot = var12[0];
         var11.yRot = var12[1];
         var11.zRot = var12[2];
         float[] var13 = sample((float[][])var6.get(var10), var8);
         if (var13 != null) {
            var11.xRot = var11.xRot + (float)Math.toRadians(-var13[0]);
            var11.yRot = var11.yRot + (float)Math.toRadians(-var13[1]);
            var11.zRot = var11.zRot + (float)Math.toRadians(var13[2]);
         }
      }

      ModelPart var14 = var0.part("head");
      var14.yRot = var14.yRot + (float)Math.toRadians(Mth.clamp(var3, -55.0F, 55.0F));
      var14.xRot = var14.xRot + (float)Math.toRadians(Mth.clamp(var4, -22.0F, 22.0F));
   }

   private static float[] sample(float[][] var0, float var1) {
      if (var0 != null && var0.length != 0) {
         if (var1 <= var0[0][0]) {
            return var0[0];
         } else {
            float[] var2 = var0[var0.length - 1];
            if (var1 >= var2[0]) {
               return var2;
            } else {
               for (int var3 = 0; var3 < var0.length - 1; var3++) {
                  float var4 = var0[var3][0];
                  float var5 = var0[var3 + 1][0];
                  if (var1 >= var4 && var1 <= var5) {
                     float var6 = (var1 - var4) / (var5 - var4);
                     return new float[]{
                        0.0F,
                        var0[var3][1] + (var0[var3 + 1][1] - var0[var3][1]) * var6,
                        var0[var3][2] + (var0[var3 + 1][2] - var0[var3][2]) * var6,
                        var0[var3][3] + (var0[var3 + 1][3] - var0[var3][3]) * var6
                     };
                  }
               }

               return var2;
            }
         }
      } else {
         return null;
      }
   }

   static {
      IDLE.put("body", IDLE_BODY);
      IDLE.put("head", IDLE_HEAD);
      IDLE.put("jaw", IDLE_JAW);
      IDLE.put("arm_left", IDLE_ARM_LEFT);
      IDLE.put("arm_left_lower", IDLE_ARM_LEFT_LOWER);
      IDLE.put("arm_right", IDLE_ARM_RIGHT);
      IDLE.put("props", IDLE_PROPS);
      TALK.put("jaw", TALK_JAW);
      TALK.put("head", TALK_HEAD);
      TALK.put("body", TALK_BODY);
      TALK.put("arm_left", TALK_ARM_LEFT);
      TALK.put("arm_left_lower", TALK_ARM_LEFT_LOWER);
   }
}
