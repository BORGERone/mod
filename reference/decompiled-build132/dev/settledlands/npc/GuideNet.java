package dev.settledlands.npc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
   modid = "settledlands",
   bus = Bus.MOD
)
public final class GuideNet {
   private GuideNet() {
   }

   private static ResourceLocation id(String var0) {
      return ResourceLocation.fromNamespaceAndPath("settledlands", var0);
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("1");
      var1.playToServer(GuideNet.Sign.TYPE, GuideNet.Sign.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var2) {
            GuideTomeItem.sign(var2, var0x);
         }
      }));
   }

   public record Sign(boolean offhand, int number, String text) implements CustomPacketPayload {
      public static final Type<GuideNet.Sign> TYPE = new Type(GuideNet.id("guide_sign"));
      public static final StreamCodec<RegistryFriendlyByteBuf, GuideNet.Sign> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeBoolean(var1.offhand);
         var0.writeVarInt(var1.number);
         var0.writeUtf(var1.text, 480);
      }, var0 -> new GuideNet.Sign(var0.readBoolean(), var0.readVarInt(), var0.readUtf()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
