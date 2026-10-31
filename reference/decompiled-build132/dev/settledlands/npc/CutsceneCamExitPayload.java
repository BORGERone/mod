package dev.settledlands.npc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client -> server: requested to leave the camera session (RMB while in spectator). */
public record CutsceneCamExitPayload() implements CustomPacketPayload {
   public static final Type<CutsceneCamExitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "cutscene_cam_exit"));

   public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneCamExitPayload> CODEC = StreamCodec.unit(new CutsceneCamExitPayload());

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToServer(TYPE, CODEC, (payload, ctx) ->
         ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.hasPermissions(2)) {
               CutsceneCamera.exit(player);
            }
         }));
   }
}
