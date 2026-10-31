package dev.settledlands.npc;

import dev.settledlands.npc.client.CutsceneCameraClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server -> client: cutscene playback flag (letterbox bars, hidden HUD, screen close). */
public record CutscenePlayingPayload(boolean playing) implements CustomPacketPayload {
   public static final Type<CutscenePlayingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "cutscene_playing"));

   public static final StreamCodec<RegistryFriendlyByteBuf, CutscenePlayingPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, CutscenePlayingPayload::playing,
      CutscenePlayingPayload::new
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, ctx) ->
         ctx.enqueueWork(() -> CutsceneCameraClient.handlePlaying(payload.playing())));
   }
}
