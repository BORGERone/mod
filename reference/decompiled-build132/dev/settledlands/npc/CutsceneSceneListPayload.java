package dev.settledlands.npc;

import dev.settledlands.npc.client.CutsceneCameraClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server -> client: names of all recorded cutscenes (feeds node editor dropdowns). */
public record CutsceneSceneListPayload(List<String> names) implements CustomPacketPayload {
   public static final Type<CutsceneSceneListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "cutscene_scene_list"));

   public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneSceneListPayload> CODEC = StreamCodec.of(
      (buf, p) -> {
         buf.writeVarInt(p.names.size());
         for (String name : p.names) buf.writeUtf(name, 64);
      },
      buf -> {
         int size = Math.min(buf.readVarInt(), 256);
         List<String> names = new ArrayList<>(size);
         for (int i = 0; i < size; i++) names.add(buf.readUtf(64));
         return new CutsceneSceneListPayload(names);
      });

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, ctx) ->
         ctx.enqueueWork(() -> CutsceneCameraClient.handleScenes(payload.names())));
   }
}
