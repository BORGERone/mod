package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
   modid = "settledlands",
   bus = Bus.MOD
)
public final class NpcNet {
   public static final int DLG_WELCOME = 0;
   public static final int DLG_STEP = 1;
   public static final int DLG_REACTION = 2;
   public static final int DLG_LEGACY = 3;
   public static final int DLG_LINE = 4;
   public static final int DLG_QUEST_LIST = 5;
   public static final int DLG_QUEST_MENU = 6;
   public static final int MOBILITY_EDITOR_ID = -2_000_000_000;
   public static Consumer<NpcNet.CfgView> clientCfg = var0 -> {};
   public static Consumer<NpcNet.DlgView> clientDlg = var0 -> {};
   public static Consumer<NpcNet.DlgEnd> clientEnd = var0 -> {};
   public static Consumer<NpcNet.DlgFocus> clientFocus = var0 -> {};
   public static Consumer<NpcNet.ShopView> clientShop = var0 -> {};

   private NpcNet() {
   }

   private static ResourceLocation id(String var0) {
      return ResourceLocation.fromNamespaceAndPath("settledlands", var0);
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("2");
      var1.playToClient(NpcNet.CfgView.TYPE, NpcNet.CfgView.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientCfg.accept(var0x)));
      var1.playToClient(NpcNet.DlgView.TYPE, NpcNet.DlgView.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientDlg.accept(var0x)));
      var1.playToClient(NpcNet.DlgFocus.TYPE, NpcNet.DlgFocus.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientFocus.accept(var0x)));
      var1.playToClient(NpcNet.DlgEnd.TYPE, NpcNet.DlgEnd.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientEnd.accept(var0x)));
      var1.playToClient(NpcNet.ShopView.TYPE, NpcNet.ShopView.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientShop.accept(var0x)));
      var1.playToServer(NpcNet.CfgSave.TYPE, NpcNet.CfgSave.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var2) {
            GuideNpc.applyConfig(var2, var0x.entityId(), var0x.data());
         }
      }));
      var1.playToServer(NpcNet.CfgEdit.TYPE, NpcNet.CfgEdit.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var2) {
            GuideNpc.applyEdit(var2, var0x.entityId(), var0x.op(), var0x.a(), var0x.b(), var0x.c(), var0x.key(), var0x.value());
         }
      }));
      var1.playToServer(NpcNet.RelSet.TYPE, NpcNet.RelSet.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var2) {
            GuideNpc.applyRelation(var2, var0x.entityId(), var0x.target(), var0x.value());
         }
      }));
      var1.playToServer(NpcNet.DlgPick.TYPE, NpcNet.DlgPick.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var2) {
            GuideDialogue.pick(var2, var0x.entityId(), var0x.index());
         }
      }));
      var1.playToServer(NpcNet.DlgClose.TYPE, NpcNet.DlgClose.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var1xx) {
            GuideDialogue.stop(var1xx);
         }
      }));
      var1.playToServer(NpcNet.ShopBuy.TYPE, NpcNet.ShopBuy.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         if (var1x.player() instanceof ServerPlayer var2) {
            NpcShop.buy(var2, var0x.entityId(), var0x.index(), var0x.count());
         }
      }));
   }

   public static void sendTo(ServerPlayer var0, CustomPacketPayload var1) {
      sendRaw(var0, var1);
      if (var1 instanceof NpcNet.DlgView var2) {
         GuideDialogue.broadcastView(var0, var2);
         GuideDialogue.sendCurrentFocus(var0);
      } else if (var1 instanceof NpcNet.DlgFocus var3) {
         GuideDialogue.broadcastFocus(var0, var3);
      } else if (var1 instanceof NpcNet.ShopView var4) {
         GuideDialogue.broadcastShop(var0, var4);
      }
   }

   public static void sendRaw(ServerPlayer var0, CustomPacketPayload var1) {
      PacketDistributor.sendToPlayer(var0, var1, new CustomPacketPayload[0]);
   }

   public static void sendToServer(CustomPacketPayload var0) {
      PacketDistributor.sendToServer(var0, new CustomPacketPayload[0]);
   }

   private static void writeStrings(RegistryFriendlyByteBuf var0, List<String> var1) {
      var0.writeVarInt(var1.size());

      for (String var3 : var1) {
         var0.writeUtf(var3 == null ? "" : var3, 2048);
      }
   }

   private static List<String> readStrings(RegistryFriendlyByteBuf var0) {
      int var1 = Math.max(0, Math.min(64, var0.readVarInt()));
      ArrayList var2 = new ArrayList(var1);

      for (int var3 = 0; var3 < var1; var3++) {
         var2.add(var0.readUtf());
      }

      return var2;
   }

   public record CfgEdit(int entityId, String op, int a, int b, int c, String key, String value) implements CustomPacketPayload {
      public static final Type<NpcNet.CfgEdit> TYPE = new Type(NpcNet.id("npc_cfg_edit"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.CfgEdit> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeUtf(var1.op == null ? "" : var1.op, 64);
         var0.writeVarInt(var1.a);
         var0.writeVarInt(var1.b);
         var0.writeVarInt(var1.c);
         var0.writeUtf(var1.key == null ? "" : var1.key, 64);
         var0.writeUtf(var1.value == null ? "" : var1.value, 2048);
      }, var0 -> new NpcNet.CfgEdit(var0.readVarInt(), var0.readUtf(), var0.readVarInt(), var0.readVarInt(), var0.readVarInt(), var0.readUtf(), var0.readUtf()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record CfgSave(int entityId, CompoundTag data) implements CustomPacketPayload {
      public static final Type<NpcNet.CfgSave> TYPE = new Type(NpcNet.id("npc_cfg_save"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.CfgSave> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeNbt(var1.data);
      }, var0 -> new NpcNet.CfgSave(var0.readVarInt(), var0.readNbt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record CfgView(int entityId, CompoundTag data) implements CustomPacketPayload {
      public static final Type<NpcNet.CfgView> TYPE = new Type(NpcNet.id("npc_cfg_view"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.CfgView> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeNbt(var1.data);
      }, var0 -> new NpcNet.CfgView(var0.readVarInt(), var0.readNbt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record DlgClose(int entityId) implements CustomPacketPayload {
      public static final Type<NpcNet.DlgClose> TYPE = new Type(NpcNet.id("npc_dlg_close"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.DlgClose> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeVarInt(var1.entityId), var0 -> new NpcNet.DlgClose(var0.readVarInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record DlgEnd(int entityId) implements CustomPacketPayload {
      public static final Type<NpcNet.DlgEnd> TYPE = new Type(NpcNet.id("npc_dlg_end"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.DlgEnd> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeVarInt(var1.entityId), var0 -> new NpcNet.DlgEnd(var0.readVarInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record DlgFocus(int entityId, int focusEntityId, String speaker, String title, int nameColor) implements CustomPacketPayload {
      public static final Type<NpcNet.DlgFocus> TYPE = new Type(NpcNet.id("npc_dlg_focus"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.DlgFocus> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeVarInt(var1.entityId);
            var0.writeVarInt(var1.focusEntityId);
            var0.writeUtf(var1.speaker == null ? "" : var1.speaker, 256);
            var0.writeUtf(var1.title == null ? "" : var1.title, 256);
            var0.writeVarInt(var1.nameColor);
         },
         var0 -> new NpcNet.DlgFocus(var0.readVarInt(), var0.readVarInt(), var0.readUtf(), var0.readUtf(), var0.readVarInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record DlgPick(int entityId, int index) implements CustomPacketPayload {
      public static final Type<NpcNet.DlgPick> TYPE = new Type(NpcNet.id("npc_dlg_pick"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.DlgPick> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeVarInt(var1.index);
      }, var0 -> new NpcNet.DlgPick(var0.readVarInt(), var0.readVarInt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record DlgStyle(
      int style,
      int textColor,
      int nameColor,
      int typingSpeed,
      boolean speakerName,
      boolean keyHints,
      boolean answerNumbers,
      String reputation,
      boolean dialogueTab,
      boolean tradeTab,
      boolean questTab,
      boolean phrasesTab,
      int defaultTab,
      int zoom,
      int offsetX,
      int offsetY,
      boolean cameraOnFace,
      boolean blockMouseLook,
      int dim,
      int blur,
      boolean muteHud,
      boolean vignette,
      int exitKey,
      boolean blockMovement,
      boolean pauseWorld,
      String voice,
      int voicePitch
   ) {
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.DlgStyle> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeVarInt(var1.style);
            var0.writeVarInt(var1.textColor);
            var0.writeVarInt(var1.nameColor);
            var0.writeVarInt(var1.typingSpeed);
            var0.writeBoolean(var1.speakerName);
            var0.writeBoolean(var1.keyHints);
            var0.writeBoolean(var1.answerNumbers);
            var0.writeUtf(var1.reputation == null ? "" : var1.reputation, 64);
            var0.writeBoolean(var1.dialogueTab);
            var0.writeBoolean(var1.tradeTab);
            var0.writeBoolean(var1.questTab);
            var0.writeBoolean(var1.phrasesTab);
            var0.writeVarInt(var1.defaultTab);
            var0.writeVarInt(var1.zoom);
            var0.writeVarInt(var1.offsetX + 64);
            var0.writeVarInt(var1.offsetY + 64);
            var0.writeBoolean(var1.cameraOnFace);
            var0.writeBoolean(var1.blockMouseLook);
            var0.writeVarInt(var1.dim);
            var0.writeVarInt(var1.blur);
            var0.writeBoolean(var1.muteHud);
            var0.writeBoolean(var1.vignette);
            var0.writeVarInt(var1.exitKey);
            var0.writeBoolean(var1.blockMovement);
            var0.writeBoolean(var1.pauseWorld);
            var0.writeUtf(var1.voice == null ? "" : var1.voice, 64);
            var0.writeVarInt(var1.voicePitch);
         },
         var0 -> new NpcNet.DlgStyle(
            var0.readVarInt(),
            var0.readVarInt(),
            var0.readVarInt(),
            var0.readVarInt(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readUtf(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readVarInt(),
            var0.readVarInt(),
            var0.readVarInt() - 64,
            var0.readVarInt() - 64,
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readVarInt(),
            var0.readVarInt(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readVarInt(),
            var0.readBoolean(),
            var0.readBoolean(),
            var0.readUtf(),
            var0.readVarInt()
         )
      );

      public DlgStyle(
         int style,
         int textColor,
         int typingSpeed,
         boolean speakerName,
         boolean keyHints,
         boolean answerNumbers,
         String reputation,
         boolean dialogueTab,
         boolean tradeTab,
         boolean questTab,
         boolean phrasesTab,
         int defaultTab,
         int zoom,
         int offsetX,
         int offsetY,
         boolean cameraOnFace,
         boolean blockMouseLook,
         int dim,
         int blur,
         boolean muteHud,
         boolean vignette,
         int exitKey,
         boolean blockMovement,
         boolean pauseWorld,
         String voice,
         int voicePitch
      ) {
         this(
            style,
            textColor,
            1,
            typingSpeed,
            speakerName,
            keyHints,
            answerNumbers,
            reputation,
            dialogueTab,
            tradeTab,
            questTab,
            phrasesTab,
            defaultTab,
            zoom,
            offsetX,
            offsetY,
            cameraOnFace,
            blockMouseLook,
            dim,
            blur,
            muteHud,
            vignette,
            exitKey,
            blockMovement,
            pauseWorld,
            voice,
            voicePitch
         );
      }
   }

   public record DlgView(
      int entityId,
      String speaker,
      String title,
      String text,
      List<String> options,
      List<String> hints,
      int relation,
      int state,
      boolean shop,
      NpcNet.DlgStyle style,
      boolean observer
   ) implements CustomPacketPayload {
      public static final Type<NpcNet.DlgView> TYPE = new Type(NpcNet.id("npc_dlg_view"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.DlgView> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeVarInt(var1.entityId);
            var0.writeUtf(var1.speaker == null ? "" : var1.speaker, 256);
            var0.writeUtf(var1.title == null ? "" : var1.title, 256);
            var0.writeUtf(var1.text == null ? "" : var1.text, 2048);
            NpcNet.writeStrings(var0, var1.options);
            NpcNet.writeStrings(var0, var1.hints);
            var0.writeVarInt(var1.relation);
            var0.writeVarInt(var1.state);
            var0.writeBoolean(var1.shop);
            NpcNet.DlgStyle.CODEC.encode(var0, var1.style);
            var0.writeBoolean(var1.observer);
         },
         var0 -> new NpcNet.DlgView(
            var0.readVarInt(),
            var0.readUtf(),
            var0.readUtf(),
            var0.readUtf(),
            NpcNet.readStrings(var0),
            NpcNet.readStrings(var0),
            var0.readVarInt(),
            var0.readVarInt(),
            var0.readBoolean(),
            (NpcNet.DlgStyle)NpcNet.DlgStyle.CODEC.decode(var0),
            var0.readBoolean()
         )
      );

      public DlgView(int entityId, String speaker, String title, String text, List<String> options, List<String> hints,
                     int relation, int state, boolean shop, NpcNet.DlgStyle style) {
         this(entityId, speaker, title, text, options, hints, relation, state, shop, style, false);
      }

      public DlgView asObserver() {
         return new DlgView(this.entityId, this.speaker, this.title, this.text, this.options, this.hints,
            this.relation, this.state, this.shop, this.style, true);
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record RelSet(int entityId, String target, int value) implements CustomPacketPayload {
      public static final Type<NpcNet.RelSet> TYPE = new Type(NpcNet.id("npc_rel_set"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.RelSet> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeUtf(var1.target == null ? "" : var1.target, 64);
         var0.writeVarInt(var1.value + 1);
      }, var0 -> new NpcNet.RelSet(var0.readVarInt(), var0.readUtf(), var0.readVarInt() - 1));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record ShopBuy(int entityId, int index, int count) implements CustomPacketPayload {
      public static final Type<NpcNet.ShopBuy> TYPE = new Type(NpcNet.id("npc_shop_buy"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.ShopBuy> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeVarInt(var1.index);
         var0.writeVarInt(var1.count);
      }, var0 -> new NpcNet.ShopBuy(var0.readVarInt(), var0.readVarInt(), var0.readVarInt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record ShopEntry(int index, ItemStack stack, ItemStack price, String description, int minRelation) {
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.ShopEntry> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeVarInt(var1.index);
            ItemStack.STREAM_CODEC.encode(var0, var1.stack);
            ItemStack.STREAM_CODEC.encode(var0, var1.price);
            var0.writeUtf(var1.description == null ? "" : var1.description, 1024);
            var0.writeVarInt(var1.minRelation);
         },
         var0 -> new NpcNet.ShopEntry(
            var0.readVarInt(),
            (ItemStack)ItemStack.STREAM_CODEC.decode(var0),
            (ItemStack)ItemStack.STREAM_CODEC.decode(var0),
            var0.readUtf(),
            var0.readVarInt()
         )
      );
   }

   public record ShopView(int entityId, int relation, int maxPerPurchase, List<NpcNet.ShopEntry> entries, boolean observer) implements CustomPacketPayload {
      public static final Type<NpcNet.ShopView> TYPE = new Type(NpcNet.id("npc_shop_view"));
      public static final StreamCodec<RegistryFriendlyByteBuf, NpcNet.ShopView> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.entityId);
         var0.writeVarInt(var1.relation);
         var0.writeVarInt(var1.maxPerPurchase);
         var0.writeVarInt(var1.entries.size());

         for (NpcNet.ShopEntry var3 : var1.entries) {
            NpcNet.ShopEntry.CODEC.encode(var0, var3);
         }
         var0.writeBoolean(var1.observer);
      }, var0 -> {
         int var1 = var0.readVarInt();
         int var2 = var0.readVarInt();
         int var3 = var0.readVarInt();
         int var4 = Math.max(0, Math.min(32, var0.readVarInt()));
         ArrayList var5 = new ArrayList(var4);

         for (int var6 = 0; var6 < var4; var6++) {
            var5.add((NpcNet.ShopEntry)NpcNet.ShopEntry.CODEC.decode(var0));
         }

         return new NpcNet.ShopView(var1, var2, var3, var5, var0.readBoolean());
      });

      public ShopView(int entityId, int relation, int maxPerPurchase, List<NpcNet.ShopEntry> entries) {
         this(entityId, relation, maxPerPurchase, entries, false);
      }

      public ShopView asObserver() {
         return new ShopView(this.entityId, this.relation, this.maxPerPurchase, this.entries, true);
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
