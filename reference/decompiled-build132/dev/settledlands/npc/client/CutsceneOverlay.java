package dev.settledlands.npc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Cinema look during cutscene playback: HUD is hidden and letterbox bars are drawn. */
@EventBusSubscriber(modid = "settledlands", value = Dist.CLIENT)
public final class CutsceneOverlay {
   private CutsceneOverlay() {}

   @SubscribeEvent
   public static void onGuiPre(RenderGuiEvent.Pre event) {
      if (CutsceneCameraClient.isPlaying()) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onGuiPost(RenderGuiEvent.Post event) {
      if (!CutsceneCameraClient.isPlaying()) return;
      GuiGraphics g = event.getGuiGraphics();
      int width = g.guiWidth();
      int height = g.guiHeight();
      int bar = Math.max(6, (int) (height * 0.12));
      g.fill(0, 0, width, bar, 0xff000000);
      g.fill(0, height - bar, width, height, 0xff000000);
   }
}
