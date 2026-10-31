package dev.settledlands.npc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;

public final class GuideModelData {
   private static final String RESOURCE = "/assets/settledlands/guide_model.json";
   public static final String ROOT_BONE = "root";
   private static LayerDefinition cached;

   private GuideModelData() {
   }

   public static ModelPart boneRoot(ModelPart var0) {
      try {
         return var0.getChild("root");
      } catch (RuntimeException var2) {
         return var0;
      }
   }

   public static LayerDefinition layer() {
      if (cached == null) {
         cached = load();
      }

      return cached;
   }

   private static LayerDefinition load() {
      try {
         LayerDefinition var24;
         try (InputStream var0 = GuideModelData.class.getResourceAsStream("/assets/settledlands/guide_model.json")) {
            if (var0 == null) {
               throw new IllegalStateException("missing /assets/settledlands/guide_model.json in the mod jar");
            }

            JsonObject var1 = JsonParser.parseReader(new InputStreamReader(var0, StandardCharsets.UTF_8)).getAsJsonObject();
            MeshDefinition var2 = new MeshDefinition();
            PartDefinition var3 = var2.getRoot();
            HashMap var4 = new HashMap();
            var4.put("root", var3);

            for (JsonElement var6 : var1.getAsJsonArray("bones")) {
               JsonObject var7 = var6.getAsJsonObject();
               String var8 = var7.get("name").getAsString();
               String var9 = var7.get("parent").isJsonNull() ? "root" : var7.get("parent").getAsString();
               PartDefinition var10 = var4.getOrDefault(var9, var3);
               CubeListBuilder var11 = CubeListBuilder.create();

               for (JsonElement var13 : var7.getAsJsonArray("cubes")) {
                  JsonObject var14 = var13.getAsJsonObject();
                  float[] var15 = readFloats(var14.getAsJsonArray("from"));
                  float[] var16 = readFloats(var14.getAsJsonArray("to"));
                  JsonArray var17 = var14.getAsJsonArray("uv");
                  var11.texOffs(var17.get(0).getAsInt(), var17.get(1).getAsInt());
                  float var18 = var16[0] - var15[0];
                  float var19 = var16[1] - var15[1];
                  float var20 = var16[2] - var15[2];
                  if (var14.has("hide")) {
                     var11.addBox(var15[0], var15[1], var15[2], var18, var19, var20, visible(var14.getAsJsonArray("hide")));
                  } else {
                     var11.addBox(var14.get("name").getAsString(), var15[0], var15[1], var15[2], var18, var19, var20, CubeDeformation.NONE);
                  }
               }

               float[] var25 = readFloats(var7.getAsJsonArray("pivot"));
               float[] var26 = var7.get("rotation") != null && !var7.get("rotation").isJsonNull()
                  ? readFloats(var7.getAsJsonArray("rotation"))
                  : new float[]{0.0F, 0.0F, 0.0F};
               PartDefinition var27 = var10.addOrReplaceChild(
                  var8,
                  var11,
                  PartPose.offsetAndRotation(
                     var25[0], var25[1], var25[2], (float)Math.toRadians(var26[0]), (float)Math.toRadians(var26[1]), (float)Math.toRadians(var26[2])
                  )
               );
               var4.put(var8, var27);
            }

            var24 = LayerDefinition.create(var2, var1.get("texture_width").getAsInt(), var1.get("texture_height").getAsInt());
         }

         return var24;
      } catch (Exception var23) {
         throw new IllegalStateException("cannot load the Guide model", var23);
      }
   }

   private static Set<Direction> visible(JsonArray var0) {
      HashSet var1 = new HashSet();

      for (Direction var5 : Direction.values()) {
         var1.add(var5);
      }

      for (JsonElement var7 : var0) {
         var1.remove(byName(var7.getAsString()));
      }

      return var1;
   }

   private static Direction byName(String var0) {
      for (Direction var4 : Direction.values()) {
         if (var4.getSerializedName().equals(var0)) {
            return var4;
         }
      }

      throw new IllegalArgumentException(var0);
   }

   private static float[] readFloats(JsonArray var0) {
      float[] var1 = new float[var0.size()];

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = var0.get(var2).getAsFloat();
      }

      return var1;
   }
}
