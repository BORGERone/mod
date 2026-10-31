package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side cutscene camera core.
 * Camera = vanilla spectator physics. Session stores the previous game type in world
 * SavedData (survives relog and server restart). On top of the session:
 *  - recording: per-tick server sampling of the flying player's eye pos + view angles;
 *  - playback: per-tick absolute teleports along a stored track, restoring everything at the end.
 * Client receives a mode flag: 0=off, 1=camera, 2=recording, 3=playing.
 */
public final class CutsceneCamera {
   public static final int MODE_OFF = 0, MODE_CAMERA = 1, MODE_RECORDING = 2, MODE_PLAYING = 3;

   private static final Map<UUID, java.util.List<double[]>> recordings = new HashMap<>();
   private static final Map<UUID, Playback> playbacks = new HashMap<>();

   private record Playback(java.util.List<double[]> keys, int idx) {}

   private CutsceneCamera() {}

   private static final class SessionsData extends SavedData {
      private final Map<UUID, Integer> gameModes = new HashMap<>();

      static SavedData.Factory<SessionsData> factory() {
         return new SavedData.Factory<>(SessionsData::new, SessionsData::load, DataFixTypes.LEVEL);
      }

      static SessionsData load(CompoundTag tag, HolderLookup.Provider registries) {
         SessionsData data = new SessionsData();
         ListTag list = tag.getList("players", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            data.gameModes.put(e.getUUID("id"), e.getInt("gm"));
         }
         return data;
      }

      @Override
      public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
         ListTag list = new ListTag();
         for (Map.Entry<UUID, Integer> e : gameModes.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            entry.putInt("gm", e.getValue());
            list.add(entry);
         }
         tag.put("players", list);
         return tag;
      }
   }

   private static SessionsData data(ServerPlayer player) {
      return player.server.overworld().getDataStorage()
         .computeIfAbsent(SessionsData.factory(), "settledlands_cutscene_cam");
   }

   private static void sendMode(ServerPlayer player, int mode) {
      PacketDistributor.sendToPlayer(player, new CutsceneCamStatePayload(mode));
   }

   public static boolean isActive(ServerPlayer player) {
      return data(player).gameModes.containsKey(player.getUUID());
   }

   public static boolean isRecording(ServerPlayer player) {
      return recordings.containsKey(player.getUUID());
   }

   public static boolean isPlaying(ServerPlayer player) {
      return playbacks.containsKey(player.getUUID());
   }

   public static void enter(ServerPlayer player) {
      enter(player, false);
   }

   public static void enter(ServerPlayer player, boolean quiet) {
      SessionsData data = data(player);
      if (data.gameModes.containsKey(player.getUUID())) return;
      data.gameModes.put(player.getUUID(), player.gameMode.getGameModeForPlayer().getId());
      data.setDirty();
      player.setGameMode(GameType.SPECTATOR);
      sendMode(player, MODE_CAMERA);
      if (!quiet) {
         player.displayClientMessage(Component.translatable("message.settledlands.cutscene.cam_enter"), true);
      }
   }

   public static void exit(ServerPlayer player) {
      if (isPlaying(player)) { cancelPlayback(player); return; }
      if (isRecording(player)) { stopRecording(player); return; }
      if (!restore(player)) return;
      sendMode(player, MODE_OFF);
      player.displayClientMessage(Component.translatable("message.settledlands.cutscene.cam_exit"), true);
   }

   private static void quietExit(ServerPlayer player) {
      restore(player);
      sendMode(player, MODE_OFF);
   }

   private static boolean restore(ServerPlayer player) {
      SessionsData data = data(player);
      Integer gm = data.gameModes.remove(player.getUUID());
      if (gm == null) return false;
      data.setDirty();
      player.setGameMode(gm >= 0 && gm <= 3 ? GameType.byId(gm) : GameType.SURVIVAL);
      return true;
   }

   // --- recording ---------------------------------------------------------

   public static void toggleRecording(ServerPlayer player) {
      if (isRecording(player)) stopRecording(player); else startRecording(player);
   }

   public static void startRecording(ServerPlayer player) {
      if (isPlaying(player)) return;
      if (!isActive(player)) enter(player);
      recordings.put(player.getUUID(), new ArrayList<>());
      sendMode(player, MODE_RECORDING);
      player.displayClientMessage(Component.translatable("message.settledlands.cutscene.rec_start"), true);
   }

   public static void stopRecording(ServerPlayer player) {
      java.util.List<double[]> keys = recordings.remove(player.getUUID());
      if (keys == null) return;
      if (keys.size() >= 2) {
         String name = CutsceneStorage.get(player.server).store(keys);
         sendSceneList(player.server);
         player.displayClientMessage(Component.translatable("message.settledlands.cutscene.rec_stop", name), false);
      } else {
         player.displayClientMessage(Component.translatable("message.settledlands.cutscene.rec_too_short"), true);
      }
      sendMode(player, isActive(player) ? MODE_CAMERA : MODE_OFF);
   }

   // --- playback ----------------------------------------------------------

   /** Plays a recorded scene for any player. Returns false when the scene is missing. */
   public static boolean play(ServerPlayer player, String sceneName) {
      java.util.List<double[]> keys = sceneName == null ? null : CutsceneStorage.get(player.server).scene(sceneName);
      if (keys == null || keys.size() < 2) {
         player.displayClientMessage(Component.translatable("message.settledlands.cutscene.play_missing", sceneName == null ? "?" : sceneName), false);
         return false;
      }
      recordings.remove(player.getUUID());
      enter(player, true);
      playbacks.put(player.getUUID(), new Playback(keys, 0));
      PacketDistributor.sendToPlayer(player, new CutscenePlayingPayload(true));
      sendMode(player, MODE_PLAYING);
      return true;
   }

   public static void cancelPlayback(ServerPlayer player) {
      if (playbacks.remove(player.getUUID()) == null) return;
      PacketDistributor.sendToPlayer(player, new CutscenePlayingPayload(false));
      quietExit(player);
   }

   public static void sendSceneList(MinecraftServer server) {
      CutsceneSceneListPayload payload = new CutsceneSceneListPayload(CutsceneStorage.get(server).names());
      for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
         PacketDistributor.sendToPlayer(pl, payload);
      }
   }

   /** Per-tick sampling (recording) and track stepping (playback). Called from ServerTickEvent.Post. */
   public static void tick(MinecraftServer server) {
      if (!recordings.isEmpty()) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            java.util.List<double[]> keys = recordings.get(player.getUUID());
            if (keys == null) continue;
            if (keys.size() >= 120000) { // 100 min safety cap
               stopRecording(player);
               continue;
            }
            Vec3 eye = player.getEyePosition();
            keys.add(new double[]{eye.x, eye.y, eye.z, player.getYRot(), player.getXRot()});
         }
      }
      if (!playbacks.isEmpty()) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Playback pb = playbacks.get(player.getUUID());
            if (pb == null) continue;
            java.util.List<double[]> keys = pb.keys();
            if (pb.idx() >= keys.size()) { cancelPlayback(player); continue; }
            double[] key = keys.get(pb.idx());
            playbacks.put(player.getUUID(), new Playback(keys, pb.idx() + 1));
            player.connection.teleport(key[0], key[1], key[2], (float) key[3], (float) key[4], Set.of());
         }
      }
   }

   /** Relog safety: restore game mode; drop live recording/playback state. */
   public static void onLogin(ServerPlayer player) {
      if (restore(player)) {
         sendMode(player, MODE_OFF);
      }
   }

   public static void onLogout(ServerPlayer player) {
      recordings.remove(player.getUUID());
      if (playbacks.remove(player.getUUID()) != null) {
         PacketDistributor.sendToPlayer(player, new CutscenePlayingPayload(false));
      }
   }
}
