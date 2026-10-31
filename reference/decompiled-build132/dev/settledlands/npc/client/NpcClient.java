package dev.settledlands.npc.client;

import dev.settledlands.npc.GuideTomeItem;
import dev.settledlands.npc.NpcContent;
import dev.settledlands.npc.NpcNet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

@EventBusSubscriber(
   modid = "settledlands",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class NpcClient {
   private NpcClient() {
   }

   @SubscribeEvent
   public static void renderers(RegisterRenderers var0) {
      var0.registerEntityRenderer(NpcContent.guide(), GuideRenderer::new);
   }

   @SubscribeEvent
   public static void setup(FMLClientSetupEvent var0) {
      var0.enqueueWork(() -> {
         NpcNet.clientCfg = NpcConfigScreen::open;
         NpcNet.clientDlg = GuideDialogueScreen::open;
         NpcNet.clientFocus = GuideDialogueScreen::focusFromServer;
         NpcNet.clientEnd = view -> {
            GuideDialogueScreen.closeFromServer(view);
            NpcShopScreen.closeFromServer(view);
         };
         NpcNet.clientShop = NpcShopScreen::open;
         GuideTomeItem.editor = GuideTomeScreen::open;
      });
   }
}
