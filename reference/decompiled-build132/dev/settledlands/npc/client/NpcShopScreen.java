package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class NpcShopScreen extends Screen {
   private static final int TAB_ACTIVE = -11702;
   private static final int ROW_BG = 822083583;
   private static final int ROW_HOVER = 1023410175;
   private static final int LABEL = -1907998;
   private static final int DIM = -6645094;
   private static final int LOCKED = -32640;
   private static final int GOLD = -12176;
   private static final int LINE = 1090519039;
   private static final int ROW_H = 30;
   private static final int HEAD_H = 76;
   private static final int HEADER_H = 14;
   private static NpcShopScreen active;
   private static int lastPage;
   private final int entityId;
   private final boolean observer;
   private boolean serverClosing;
   private final int relation;
   private final int maxPerPurchase;
   private final List<NpcNet.ShopEntry> entries;
   private int page;
   private int count;
   private int countX;

   private boolean narrow() {
      return this.width < 460;
   }

   public NpcShopScreen(NpcNet.ShopView var1) {
      super(Component.translatable("gui.settledlands.shop.title"));
      this.page = lastPage;
      this.count = 1;
      this.countX = -1;
      this.entityId = var1.entityId();
      this.observer = var1.observer();
      this.relation = var1.relation();
      this.maxPerPurchase = NpcProfile.clamp(var1.maxPerPurchase(), 1, 8);
      this.entries = var1.entries();
   }

   public static void open(NpcNet.ShopView var0) {
      Minecraft var1 = Minecraft.getInstance();
      var1.setScreen(new NpcShopScreen(var0));
   }

   public static boolean isOpen() {
      return active != null;
   }

   private int pages() {
      return Math.max(1, (this.entries.size() + this.perPage() - 1) / this.perPage());
   }

   private int perPage() {
      int var1 = this.height - 34 - this.rowTop();
      return NpcProfile.clamp(var1 / 30, 1, 6);
   }

   private int goodsTextX() {
      return this.goodsX() + (this.narrow() ? 18 : 20);
   }

   private int left() {
      return this.narrow() ? 8 : 20;
   }

   private int right() {
      return this.width - this.left();
   }

   private int headH() {
      if (this.height < 200) {
         return 44;
      } else {
         return this.height < 240 ? 58 : 76;
      }
   }

   private int rowTop() {
      return this.headH() + this.style().offsetY() + 14;
   }

   private int goodsX() {
      return this.left();
   }

   private int colGoodsW() {
      int var1 = this.right() - this.left();
      int var2 = this.narrow() ? 76 : 150;
      return NpcProfile.clamp((int)(var1 * 0.38), var2, 330);
   }

   private int priceX() {
      return this.goodsX() + this.colGoodsW();
   }

   private int colPriceW() {
      int var1 = this.right() - this.left();
      int var2 = this.narrow() ? 66 : 140;
      int var3 = NpcProfile.clamp((int)(var1 * 0.3), var2, 300);
      int var4 = this.buyX() - 8 - this.priceX();
      return Math.max(40, Math.min(var3, var4));
   }

   private int buyW() {
      return this.narrow() ? NpcProfile.clamp(this.width / 5, 46, 80) : 80;
   }

   private int buyX() {
      return this.right() - this.buyW();
   }

   private NpcNet.DlgStyle style() {
      NpcNet.DlgStyle var1 = GuideDialogueScreen.lastStyle();
      return var1 == null
         ? new NpcNet.DlgStyle(0, 0, 30, true, true, false, "", true, true, true, false, 0, 100, 0, 0, false, false, 45, 0, true, true, 0, true, false, "", 0)
         : var1;
   }

   protected void init() {
      active = this;
      this.page = NpcProfile.clamp(this.page, 0, this.pages() - 1);
      lastPage = this.page;
      NpcNet.DlgStyle var1 = this.style();
      ArrayList<String> var2 = new ArrayList<>();
      ArrayList<Runnable> var3 = new ArrayList<>();
      var2.add("gui.settledlands.tab.dialogue");
      var3.add(this::back);
      var2.add("gui.settledlands.tab.trade");
      var3.add(() -> {});
      if (var1.questTab()) {
         var2.add("gui.settledlands.tab.quest");
         var3.add(() -> {
            NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -4));
            Minecraft.getInstance().setScreen(null);
         });
      }

      if (var1.phrasesTab()) {
         var2.add("gui.settledlands.tab.phrases");
         var3.add(() -> {
            NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -3));
            Minecraft.getInstance().setScreen(null);
         });
      }

      byte var4 = 92;
      int var5 = var2.size() * (var4 + 4) - 4;
      int var6 = (this.width - var5) / 2;

      for (int var7 = 0; var7 < var2.size(); var7++) {
         Runnable var8 = (Runnable)var3.get(var7);
         MutableComponent var9 = Component.translatable((String)var2.get(var7));
         if (var7 == 1) {
            var9 = var9.copy().withStyle(var0 -> var0.withColor(-11702));
         }

         Button tab = Button.builder(var9, button -> {
            if (!this.observer) {
               var8.run();
            }
         }).bounds(var6 + var7 * (var4 + 4), 8, var4, 18).build();
         if (this.observer) {
            tab.active = false;
         }
         this.addRenderableWidget(tab);
      }

      int var15 = this.rowTop();
      int var16 = Math.min(this.perPage(), this.entries.size() - this.page * this.perPage());

      for (int var17 = 0; var17 < var16; var17++) {
         int var10 = this.page * this.perPage() + var17;
         NpcNet.ShopEntry var11 = this.entries.get(var10);
         int var12 = var15 + var17 * 30;
         int var13 = var11.index();
         Button var14 = Button.builder(Component.translatable("gui.settledlands.shop.buy"), var2x -> this.buy(var13))
            .bounds(this.buyX(), var12 + 5, this.buyW(), 20)
            .build();
         var14.active = !this.observer && this.relation >= var11.minRelation();
         this.addRenderableWidget(var14);
      }

      int var18 = this.height - 28;
      Button var19 = Button.builder(Component.literal("<"), var1x -> this.flip(-1)).bounds(this.left(), var18, 20, 20).build();
      Button var20 = Button.builder(Component.literal(">"), var1x -> this.flip(1)).bounds(this.left() + 44, var18, 20, 20).build();
      var19.active = this.page > 0;
      var20.active = this.page < this.pages() - 1;
      this.addRenderableWidget(var19);
      this.addRenderableWidget(var20);
      int var21 = NpcProfile.clamp(this.width / 3, 90, 140);
      int var22 = this.right() - var21;
      int var23 = this.pageLabelX();
      if (var23 + 110 <= var22 - 6) {
         this.addRenderableWidget(Button.builder(Component.literal("-"), var1x -> this.setCount(this.count - 1)).bounds(var23, var18, 20, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("+"), var1x -> this.setCount(this.count + 1)).bounds(var23 + 90, var18, 20, 20).build());
         this.countX = var23;
      } else {
         this.countX = -1;
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.shop.back"), var1x -> this.back()).bounds(var22, var18, var21, 20).build()
      );
   }

   private int pageLabelX() {
      return this.left() + 76;
   }

   private void setCount(int var1) {
      this.count = NpcProfile.clamp(var1, 1, this.maxPerPurchase);
      this.rebuildWidgets();
   }

   private void flip(int var1) {
      this.page = NpcProfile.clamp(this.page + var1, 0, this.pages() - 1);
      lastPage = this.page;
      this.rebuildWidgets();
   }

   private void buy(int var1) {
      if (!this.observer) {
         NpcNet.sendToServer(new NpcNet.ShopBuy(this.entityId, var1, this.count));
      }
   }

   private void back() {
      if (this.observer) {
         this.leaveObservation();
      } else {
         NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -1));
         Minecraft.getInstance().setScreen(null);
      }
   }

   private void leaveObservation() {
      if (!this.serverClosing) {
         NpcNet.sendToServer(new NpcNet.DlgClose(this.entityId));
      }
      Minecraft.getInstance().setScreen(null);
   }

   public void onClose() {
      if (this.serverClosing) {
         super.onClose();
      } else {
         this.back();
      }
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (this.observer && button == 1) {
         this.leaveObservation();
         return true;
      }
      return super.mouseClicked(x, y, button);
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (this.observer) {
         if (var1 == 256 || var1 == 258) {
            this.leaveObservation();
         }
         return true;
      }
      if (var1 == 258) {
         this.back();
         return true;
      } else {
         return super.keyPressed(var1, var2, var3);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public static void closeFromServer(NpcNet.DlgEnd var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof NpcShopScreen var2 && var2.entityId == var0.entityId()) {
         var2.serverClosing = true;
         var1.setScreen(null);
      }
   }

   public void removed() {
      active = null;
      super.removed();
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      super.renderBackground(var1, var2, var3, var4);
      NpcNet.DlgStyle var5 = this.style();
      if (NpcProfile.clamp(var5.blur(), 0, 2) > 0) {
         this.renderBlurredBackground(var4);
      }

      int var6 = NpcProfile.clamp(var5.dim(), 0, 80);
      if (var6 > 0) {
         var1.fill(0, 0, this.width, this.height, var6 * 255 / 100 << 24);
      }

      var1.fill(8, 4, this.width - 8, this.height - 8, -535818220);
      int var7 = this.left();
      int var8 = this.headH() < 76 ? 28 : 30;
      var1.drawString(
         this.font,
         this.clip(Component.translatable("gui.settledlands.shop.relation", new Object[]{this.relation, var5.reputation()}).getString(), this.width - 2 * var7),
         var7,
         var8,
         -1907998,
         true
      );
      if (this.headH() >= 76) {
         var1.drawString(this.font, Component.translatable("gui.settledlands.shop.hint").getString(), var7, var8 + 9 + 2, -6645094, false);
      }

      if (this.entries.isEmpty()) {
         var1.drawCenteredString(this.font, Component.translatable("gui.settledlands.shop.empty"), this.width / 2, this.height / 2, -6645094);
      }

      int var9 = this.rowTop() - 12;
      var1.drawString(this.font, Component.translatable("gui.settledlands.shop.col.goods").getString(), this.goodsX(), var9, -6645094, true);
      var1.drawString(this.font, Component.translatable("gui.settledlands.shop.col.price").getString(), this.priceX(), var9, -6645094, true);
      var1.fill(var7 - 6, this.rowTop() - 3, this.right() + 6, this.rowTop() - 2, 1090519039);
      int var10 = this.rowTop();
      int var11 = var10 + this.perPage() * 30 - 2;
      var1.fill(this.priceX() - 8, var10 - 2, this.priceX() - 7, var11, 1090519039);
      int var12 = this.rowTop();
      int var13 = Math.min(this.perPage(), this.entries.size() - this.page * this.perPage());

      for (int var14 = 0; var14 < var13; var14++) {
         int var15 = this.page * this.perPage() + var14;
         NpcNet.ShopEntry var16 = this.entries.get(var15);
         int var17 = var12 + var14 * 30;
         boolean var18 = this.relation < var16.minRelation();
         boolean var19 = var3 >= var17 - 2 && var3 <= var17 + 30 - 3 && var2 >= var7 - 6 && var2 <= this.right() + 6;
         var1.fill(var7 - 6, var17 - 2, this.right() + 6, var17 + 30 - 4, var19 ? 1023410175 : 822083583);
         int var20 = var16.stack().getCount() * this.count;
         var1.renderItem(var16.stack(), this.goodsX(), var17 + 3);
         var1.drawString(
            this.font,
            this.clip(var16.stack().getHoverName().getString() + " x" + var20, this.colGoodsW() - 24),
            this.goodsTextX(),
            var17 + 3,
            var18 ? -6645094 : -1907998,
            true
         );
         String var21 = var16.description().isEmpty() ? "" : var16.description();
         if (this.count > 1) {
            var21 = Component.translatable("gui.settledlands.shop.each", new Object[]{this.count, var20, var16.price().getCount() * this.count}).getString();
         }

         if (!var21.isEmpty()) {
            var1.drawString(this.font, this.clip(var21, this.colGoodsW() - 24), this.goodsX() + 20, var17 + 14, -6645094, false);
         }

         var1.renderItem(var16.price(), this.priceX(), var17 + 3);
         String var22 = var16.price().getCount() + " x " + var16.price().getHoverName().getString();
         var1.drawString(this.font, this.clip(var22, this.colPriceW() - 24), this.priceX() + 20, var17 + 3, var18 ? -32640 : -12176, true);
         if (var18) {
            var1.drawString(
               this.font,
               Component.translatable("gui.settledlands.shop.locked", new Object[]{var16.minRelation()}).getString(),
               this.priceX() + 20,
               var17 + 14,
               -32640,
               false
            );
         } else if (this.count > 1) {
            int var23 = var16.price().getCount() * this.count;
            var1.drawString(
               this.font,
               this.clip(
                  Component.translatable("gui.settledlands.shop.total", new Object[]{this.count, var23, var16.price().getHoverName()}).getString(),
                  this.colPriceW() - 24
               ),
               this.priceX() + 20,
               var17 + 14,
               -6645094,
               false
            );
         }
      }

      var1.drawString(this.font, this.page + 1 + " / " + this.pages(), var7 + 24, this.height - 23, -6645094, false);
      if (this.countX >= 0) {
         var1.drawString(
            this.font,
            Component.translatable("gui.settledlands.shop.count", new Object[]{this.count}).getString(),
            this.countX + 26,
            this.height - 23,
            -1907998,
            false
         );
      }

      super.render(var1, var2, var3, var4);
   }

   private String clip(String var1, int var2) {
      if (var1 != null && this.font.width(var1) > var2) {
         return this.font.plainSubstrByWidth(var1, Math.max(8, var2 - 8)) + "…";
      } else {
         return var1 == null ? "" : var1;
      }
   }
}
