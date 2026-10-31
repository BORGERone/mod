package dev.settledlands.npc;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Cutscene server hooks: tick sampling/stepping, relog safety, logout cleanup, op scene-list sync. */
@EventBusSubscriber(modid = "settledlands")
public final class CutsceneCamEvents {
   private CutsceneCamEvents() {}

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      CutsceneCamera.tick(event.getServer());
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CutsceneCamera.onLogin(player);
         if (player.hasPermissions(2)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
               player, new CutsceneSceneListPayload(CutsceneStorage.get(player.server).names()));
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CutsceneCamera.onLogout(player);
      }
   }
}
