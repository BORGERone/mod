package dev.settledlands.ritual;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public record RitualPayload(String dimension, UUID zombie, int entityId, BlockPos banner, int age, boolean active) implements CustomPacketPayload {
   public static final Type<RitualPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("settledlands", "ritual"));
   public static Consumer<RitualPayload> receiver = var0 -> {};
   public static final StreamCodec<RegistryFriendlyByteBuf, RitualPayload> CODEC = StreamCodec.of((var0, var1) -> {
      var0.writeUtf(var1.dimension, 128);
      var0.writeUUID(var1.zombie);
      var0.writeVarInt(var1.entityId);
      var0.writeBlockPos(var1.banner);
      var0.writeVarInt(var1.age);
      var0.writeBoolean(var1.active);
   }, var0 -> new RitualPayload(var0.readUtf(128), var0.readUUID(), var0.readVarInt(), var0.readBlockPos(), var0.readVarInt(), var0.readBoolean()));

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent var0) {
      var0.registrar("1").optional().playToClient(TYPE, CODEC, (var0x, var1) -> var1.enqueueWork(() -> receiver.accept(var0x)));
   }

   public static void broadcast(RitualManager.State var0, boolean var1) {
      RitualPayload var2 = new RitualPayload(
         var0.level().dimension().location().toString(), var0.zombie().getUUID(), var0.zombie().getId(), var0.target(), var0.age(), var1
      );

      for (ServerPlayer var4 : var0.level().players()) {
         if (var4.distanceToSqr(var0.zombie()) < 16384.0 && NetworkRegistry.hasChannel(var4.connection, TYPE.id())) {
            PacketDistributor.sendToPlayer(var4, var2, new CustomPacketPayload[0]);
         }
      }
   }
}
