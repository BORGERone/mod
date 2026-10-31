package dev.settledlands.client;

import dev.settledlands.DebugPayload;
import dev.settledlands.ritual.RitualPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
   modid = "settledlands",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class ClientSetup {
   @SubscribeEvent
   public static void setup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         DebugPayload.receiver = ClientDebug::receive;
         RitualPayload.receiver = RitualClient::receive;
      });
   }
}
