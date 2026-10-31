package dev.settledlands.npc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client -> server: toggle cutscene recording (RMB in camera mode, wand not in hand). */
public record CutsceneRecTogglePayload() implements CustomPacketPayload {
   public static final Type<CutsceneRecTogglePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "cutscene_rec_toggle"));

   public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneRecTogglePayload> CODEC = StreamCodec.unit(new CutsceneRecTogglePayload());

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToServer(TYPE, CODEC, (payload, ctx) ->
         ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.hasPermissions(2)) {
               CutsceneCamera.toggleRecording(player);
            }
         }));
   }
}
