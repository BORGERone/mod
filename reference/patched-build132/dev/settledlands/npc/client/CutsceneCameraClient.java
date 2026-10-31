package dev.settledlands.npc.client;

import dev.settledlands.npc.CutsceneCamExitPayload;
import dev.settledlands.npc.CutsceneCamera;
import dev.settledlands.npc.CutsceneRecTogglePayload;
import dev.settledlands.npc.NpcContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of the cutscene system: in spectator the use-key never reaches the server,
 * so RMB is intercepted here and routed by mode:
 *  camera    — wand in main hand -> exit session, otherwise -> start recording;
 *  recording — any RMB -> stop recording;
 *  playing   — any RMB -> cancel playback.
 * Also holds the synced scene-name list for node editor dropdowns.
 */
@EventBusSubscriber(modid = "settledlands", value = Dist.CLIENT)
public final class CutsceneCameraClient {
   private static int mode = CutsceneCamera.MODE_OFF;
   private static boolean playing;
   private static List<String> scenes = List.of();

   private CutsceneCameraClient() {}

   public static void handleMode(int value) {
      mode = value;
      if (value != CutsceneCamera.MODE_PLAYING) playing = false;
   }

   public static void handleScenes(List<String> names) {
      List<String> copy = names == null ? List.of() : new ArrayList<>(names);
      Collections.sort(copy, String.CASE_INSENSITIVE_ORDER);
      scenes = List.copyOf(copy);
   }

   public static void handlePlaying(boolean value) {
      playing = value;
      if (value) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen != null) mc.setScreen(null);
      }
   }

   public static int mode() { return mode; }
   public static boolean isPlaying() { return playing; }
   public static List<String> scenes() { return scenes; }

   @SubscribeEvent
   public static void onKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
      if (mode == CutsceneCamera.MODE_OFF || !event.isUseItem()) return;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || !player.isSpectator() || mc.screen != null) return;
      event.setCanceled(true);
      event.setSwingHand(false);
      if (mode == CutsceneCamera.MODE_RECORDING) {
         PacketDistributor.sendToServer(new CutsceneRecTogglePayload());
      } else if (mode == CutsceneCamera.MODE_PLAYING) {
         PacketDistributor.sendToServer(new CutsceneCamExitPayload());
      } else if (player.getMainHandItem().is(NpcContent.cutsceneWand())) {
         PacketDistributor.sendToServer(new CutsceneCamExitPayload());
      } else {
         PacketDistributor.sendToServer(new CutsceneRecTogglePayload());
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      mode = CutsceneCamera.MODE_OFF;
      playing = false;
      scenes = List.of();
   }
}
