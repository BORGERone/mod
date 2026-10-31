package dev.settledlands.npc;

import dev.settledlands.npc.client.CutsceneCameraClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server -> client: cutscene mode (0=off, 1=camera, 2=recording, 3=playing). */
public record CutsceneCamStatePayload(int mode) implements CustomPacketPayload {
   public static final Type<CutsceneCamStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "cutscene_cam_state"));

   public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneCamStatePayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, CutsceneCamStatePayload::mode,
      CutsceneCamStatePayload::new
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, ctx) ->
         ctx.enqueueWork(() -> CutsceneCameraClient.handleMode(payload.mode())));
   }
}
