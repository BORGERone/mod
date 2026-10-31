package dev.settledlands;

import java.util.ArrayList;
import java.util.List;
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

public record DebugPayload(String dimension, int tempMax, int permanentMax, int habitationMax, List<DebugPayload.Entry> cells) implements CustomPacketPayload {
   public static final int MAX_ENTRIES = 512;
   public static final Type<DebugPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("settledlands", "debug"));
   public static Consumer<DebugPayload> receiver = var0 -> {};
   public static final StreamCodec<RegistryFriendlyByteBuf, DebugPayload> CODEC = StreamCodec.of(DebugPayload::encode, DebugPayload::decode);

   private static void encode(RegistryFriendlyByteBuf var0, DebugPayload var1) {
      var0.writeUtf(var1.dimension, 128);
      var0.writeVarInt(var1.tempMax);
      var0.writeVarInt(var1.permanentMax);
      var0.writeVarInt(var1.habitationMax);
      var0.writeVarInt(var1.cells.size());

      for (DebugPayload.Entry var3 : var1.cells) {
         var0.writeLong(var3.key);
         var0.writeFloat(var3.score);
         var0.writeVarInt(var3.kills);
         var0.writeVarInt(var3.habitation);
         var0.writeBoolean(var3.permanent);
         var0.writeVarInt(var3.banners);
         var0.writeVarInt(var3.fireBanners);
      }
   }

   private static DebugPayload decode(RegistryFriendlyByteBuf var0) {
      String var1 = var0.readUtf(128);
      int var2 = var0.readVarInt();
      int var3 = var0.readVarInt();
      int var4 = var0.readVarInt();
      int var5 = var0.readVarInt();
      if (var5 >= 0 && var5 <= 512 && var2 >= 1 && var3 >= 1 && var4 >= 1) {
         ArrayList var6 = new ArrayList(var5);

         for (int var7 = 0; var7 < var5; var7++) {
            var6.add(
               new DebugPayload.Entry(
                  var0.readLong(), var0.readFloat(), var0.readVarInt(), var0.readVarInt(), var0.readBoolean(), var0.readVarInt(), var0.readVarInt()
               )
            );
         }

         return new DebugPayload(var1, var2, var3, var4, List.copyOf(var6));
      } else {
         throw new IllegalArgumentException("Invalid Settled Lands debug payload");
      }
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void register(RegisterPayloadHandlersEvent var0) {
      var0.registrar("3").optional().playToClient(TYPE, CODEC, (var0x, var1) -> var1.enqueueWork(() -> receiver.accept(var0x)));
   }

   public static boolean available(ServerPlayer var0) {
      return NetworkRegistry.hasChannel(var0.connection, TYPE.id());
   }

   public static void clear(ServerPlayer var0) {
      if (available(var0)) {
         PacketDistributor.sendToPlayer(
            var0, new DebugPayload(var0.serverLevel().dimension().location().toString(), 1, 1, 1, List.of()), new CustomPacketPayload[0]
         );
      }
   }

   public static void send(ServerPlayer var0, int var1, long var2) {
      if (available(var0)) {
         TerritoryData var4 = TerritoryData.get(var0.serverLevel());
         long var5 = Cell.at(var0.blockPosition());
         int var7 = (var1 + 15) / 16;
         ArrayList var8 = new ArrayList();

         for (int var9 = 0; var9 <= var7; var9++) {
            for (int var10 = -1; var10 <= 1; var10++) {
               for (int var11 = -var9; var11 <= var9; var11++) {
                  for (int var12 = -var9; var12 <= var9; var12++) {
                     if (Math.max(Math.abs(var11), Math.abs(var12)) == var9) {
                        long var13 = Cell.key(BlockPos.getX(var5) + var11, BlockPos.getY(var5) + var10, BlockPos.getZ(var5) + var12);
                        float var15 = (float)var4.score(var13, var2);
                        int var16 = var4.kills(var13);
                        int var17 = (int)var4.habitation(var13);
                        boolean var18 = var4.permanent.contains(var13) && (Boolean)Settings.PERMANENT.get();
                        int var19 = Sanctity.count(var0.serverLevel(), var13);
                        if (var15 != 0.0F || var16 != 0 || var17 != 0 || var19 != 0 || var13 == var5) {
                           var8.add(new DebugPayload.Entry(var13, var15, var16, var17, var18, var19, Sanctity.countFire(var0.serverLevel(), var13)));
                           if (var8.size() >= 512) {
                              PacketDistributor.sendToPlayer(var0, packet(var0, var8), new CustomPacketPayload[0]);
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }

         PacketDistributor.sendToPlayer(var0, packet(var0, var8), new CustomPacketPayload[0]);
      }
   }

   private static DebugPayload packet(ServerPlayer var0, List<DebugPayload.Entry> var1) {
      return new DebugPayload(
         var0.serverLevel().dimension().location().toString(),
         (Integer)Settings.TEMP_MAX.get(),
         (Integer)Settings.PERM_MAX.get(),
         (Integer)Settings.HABITATION.get(),
         var1
      );
   }

   public record Entry(long key, float score, int kills, int habitation, boolean permanent, int banners, int fireBanners) {
   }
}
