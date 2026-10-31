package dev.settledlands.npc.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;

@EventBusSubscriber(
   modid = "settledlands",
   value = {Dist.CLIENT}
)
public final class NpcClientEvents {
   private NpcClientEvents() {
   }

   @SubscribeEvent
   public static void onComputeFov(ComputeFov var0) {
      if (GuideDialogueScreen.isOpen() || NpcShopScreen.isOpen()) {
         float var1 = GuideDialogueScreen.isOpen() ? GuideDialogueScreen.fovFactor() : 1.0F;
         if (var1 != 1.0F) {
            var0.setFOV(var0.getFOV() * var1);
         }
      }
   }

   @SubscribeEvent
   public static void onCameraAngles(ComputeCameraAngles var0) {
      if (GuideDialogueScreen.overrideCamera()) {
         float[] var1 = GuideDialogueScreen.cameraAngles(Minecraft.getInstance());
         if (var1 != null) {
            var0.setYaw(var1[0]);
            var0.setPitch(var1[1]);
            var0.setRoll(0.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onClientTick(Post var0) {
      GuideDialogueScreen.clientTick();
      MobilityWandClient.tick();
   }
}
