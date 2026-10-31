package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcItems;
import dev.settledlands.npc.NpcProfile;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class NpcItemPickerScreen extends Screen {
   private static final int PAD = 12;
   private static final int CELL = 20;
   private static final int MIN_COLS = 4;
   private static final int MAX_COLS = 12;
   private static final int GRID_TOP = 60;
   private static final int SEARCH_Y = 24;
   private static final int HINT_Y = 46;
   private static final int CANCEL_W = 100;
   private static final int FOOTER = 30;
   private static final int SEARCH_H = 18;
   private static final int LIST_ROW = 18;
   private static final int LIST_KEYS = 12;
   private static final int LIST_MAX_ROWS = 8;
   private final NpcConfigScreen parent;
   private Screen backTo;
   private final int offer;
   private final int side;
   private boolean quest;
   private final ItemStack current;
   private final List<ItemStack> all = NpcItems.allStacks();
   private List<ItemStack> shown = List.of();
   private EditBox search;
   private String query = "";
   private int scroll;
   private boolean focusedCurrent;
   private int listSel;
   private int listScroll;

   public NpcItemPickerScreen(NpcConfigScreen var1, int var2, int var3, ItemStack var4) {
      super(Component.translatable("gui.settledlands.picker.title"));
      this.parent = var1;
      this.offer = var2;
      this.side = var3;
      this.current = var4 == null ? ItemStack.EMPTY : var4.copy();
   }

   public NpcItemPickerScreen backTo(Screen var1) {
      this.backTo = var1;
      return this;
   }

   int entityId() {
      return this.parent == null ? -1 : this.parent.entityId();
   }

   void applySnapshot(NpcProfile var1, List<NpcConfigScreen.RelRow> var2) {
      if (this.backTo instanceof NpcQuestScreen var3) {
         var3.applySnapshot(var1, var2);
      } else if (this.parent != null) {
         this.parent.applySnapshot(var1, var2);
      }
   }

   public NpcItemPickerScreen forQuest() {
      this.quest = true;
      return this;
   }

   private int cols() {
      return NpcProfile.clamp((this.width - 24 - 6) / 20, 4, 12);
   }

   private int gridLeft() {
      return Math.max(12, (this.width - this.cols() * 20) / 2);
   }

   private int gridBottom() {
      return this.height - 30;
   }

   private int rows() {
      return Math.max(1, (this.gridBottom() - 60) / 20);
   }

   private int maxScroll() {
      int var1 = (this.shown.size() + this.cols() - 1) / this.cols();
      return Math.max(0, var1 - this.rows());
   }

   private int searchWidth() {
      return Math.max(80, Math.min(this.width - 24 - 130, 320));
   }

   private boolean listVisible() {
      return this.search != null && this.search.isFocused() && !this.query.isEmpty() && !this.shown.isEmpty();
   }

   private int listLeft() {
      return 12;
   }

   private int listWidth() {
      return this.searchWidth();
   }

   private int listTop() {
      return 45;
   }

   private int listRows() {
      int var1 = Math.max(3, (this.gridBottom() - this.listTop() - 12 - 4) / 18);
      return Math.min(8, var1);
   }

   private int listFirst() {
      int var1 = Math.min(this.listRows(), this.shown.size());
      int var2 = Math.max(0, this.shown.size() - var1);
      this.listScroll = NpcProfile.clamp(this.listScroll, 0, var2);
      int var3 = this.listScroll;
      if (this.listSel < var3) {
         var3 = this.listSel;
      }

      if (this.listSel >= var3 + var1) {
         var3 = this.listSel - var1 + 1;
      }

      return NpcProfile.clamp(var3, 0, var2);
   }

   private boolean listHit(double var1, double var3) {
      if (!this.listVisible()) {
         return false;
      } else {
         int var5 = Math.min(this.listRows(), this.shown.size());
         int var6 = this.listLeft() - 3;
         int var7 = this.listLeft() + this.listWidth() + 3;
         int var8 = this.listTop() - 3;
         int var9 = this.listTop() + var5 * 18 + 12;
         return var1 >= var6 && var1 < var7 && var3 >= var8 && var3 < var9;
      }
   }

   private int listAt(double var1, double var3) {
      if (!this.listHit(var1, var3)) {
         return -1;
      } else {
         int var5 = (int)((var3 - this.listTop()) / 18.0);
         if (var5 >= 0 && var5 < Math.min(this.listRows(), this.shown.size())) {
            int var6 = this.listFirst() + var5;
            return var6 >= 0 && var6 < this.shown.size() ? var6 : -1;
         } else {
            return -1;
         }
      }
   }

   private void moveList(int var1) {
      if (!this.shown.isEmpty()) {
         this.listSel = NpcProfile.clamp(this.listSel + var1, 0, this.shown.size() - 1);
         this.listFirst();
      }
   }

   protected void init() {
      this.shown = NpcItems.filter(this.all, this.query);
      if (!this.focusedCurrent) {
         this.focusedCurrent = true;
         this.scrollToCurrent();
      }

      this.scroll = NpcProfile.clamp(this.scroll, 0, this.maxScroll());
      this.search = new EditBox(this.font, 12, 24, this.searchWidth(), 18, Component.empty());
      this.search.setHint(Component.translatable("gui.settledlands.picker.search").withStyle(ChatFormatting.DARK_GRAY));
      this.search.setValue(this.query);
      this.search.setResponder(var1 -> {
         this.query = var1;
         this.shown = NpcItems.filter(this.all, var1);
         this.scroll = 0;
         this.listSel = 0;
         this.listScroll = 0;
      });
      this.addRenderableWidget(this.search);
      this.setInitialFocus(this.search);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.picker.cancel"), var1 -> this.onClose())
            .bounds(this.width - 12 - 100, this.height - 26, 100, 20)
            .build()
      );
   }

   private void scrollToCurrent() {
      String var1 = NpcItems.idOf(this.current);
      if (!var1.isEmpty()) {
         for (int var2 = 0; var2 < this.shown.size(); var2++) {
            if (var1.equals(NpcItems.idOf(this.shown.get(var2)))) {
               this.scroll = var2 / this.cols();
               return;
            }
         }
      }
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderBackground(var1, var2, var3, var4);
      var1.fill(0, 0, this.width, this.height, -1744830464);
      String var5 = this.title.getString()
         + " · "
         + Component.translatable(this.side == 0 ? "gui.settledlands.shop.col.goods" : "gui.settledlands.shop.col.price").getString();
      var1.drawString(this.font, this.clip(var5, this.width - 24), 12, 10, -11702, false);
      var1.drawString(this.font, this.clip(Component.translatable("gui.settledlands.picker.hint").getString(), this.width - 24), 12, 46, -5723992, false);
      super.render(var1, var2, var3, var4);
      this.drawGrid(var1, var2, var3);
      this.drawMatches(var1, var2, var3);
      var1.drawString(
         this.font,
         this.clip(Component.translatable("gui.settledlands.picker.found", new Object[]{this.shown.size()}).getString(), this.width - 24 - 100 - 8),
         12,
         this.height - 20,
         -5723992,
         false
      );
   }

   private void drawGrid(GuiGraphics var1, int var2, int var3) {
      int var4 = this.cols();
      int var5 = this.rows();
      int var6 = this.gridLeft();
      byte var7 = 60;
      int var8 = this.scroll * var4;
      String var9 = NpcItems.idOf(this.current);
      ItemStack var10 = null;

      for (int var11 = 0; var11 < var4 * var5; var11++) {
         int var12 = var8 + var11;
         if (var12 >= this.shown.size()) {
            break;
         }

         int var13 = var6 + var11 % var4 * 20;
         int var14 = var7 + var11 / var4 * 20;
         ItemStack var15 = this.shown.get(var12);
         if (var2 >= var13 && var2 < var13 + 20 && var3 >= var14 && var3 < var14 + 20) {
            var1.fill(var13, var14, var13 + 20, var14 + 20, 1358954495);
            var10 = var15;
         }

         if (!var9.isEmpty() && var9.equals(NpcItems.idOf(var15))) {
            var1.fill(var13, var14, var13 + 20, var14 + 1, -11702);
            var1.fill(var13, var14 + 20 - 1, var13 + 20, var14 + 20, -11702);
            var1.fill(var13, var14, var13 + 1, var14 + 20, -11702);
            var1.fill(var13 + 20 - 1, var14, var13 + 20, var14 + 20, -11702);
         }

         var1.renderItem(var15, var13 + 2, var14 + 2);
      }

      int var17 = this.maxScroll();
      if (var17 > 0) {
         int var18 = Math.min(this.width - 12 - 4, var6 + var4 * 20 + 6);
         int var19 = var5 * 20 - 2;
         var1.fill(var18, var7, var18 + 3, var7 + var19, 1090519039);
         int var20 = Math.max(12, var19 * var5 / (var17 + var5));
         int var16 = var7 + (var19 - var20) * this.scroll / var17;
         var1.fill(var18, var16, var18 + 3, var16 + var20, -1056964609);
      }

      if (this.shown.isEmpty()) {
         var1.drawCenteredString(this.font, Component.translatable("gui.settledlands.picker.nothing"), this.width / 2, this.height / 2, -5723992);
      }

      if (var10 != null && !this.listHit(var2, var3)) {
         var1.renderTooltip(this.font, var10, var2, var3);
      }
   }

   private void drawMatches(GuiGraphics var1, int var2, int var3) {
      if (this.listVisible()) {
         int var4 = this.listLeft();
         int var5 = this.listWidth();
         int var6 = this.listTop();
         int var7 = Math.min(this.listRows(), this.shown.size());
         int var8 = this.listFirst();
         int var9 = var6 + var7 * 18;
         var1.fill(var4 - 3, var6 - 3, var4 + var5 + 3, var9 + 12 + 3, -435549680);
         var1.fill(var4 - 3, var6 - 3, var4 + var5 + 3, var6 - 2, -11513766);
         var1.fill(var4 - 3, var6 - 3, var4 - 2, var9 + 12 + 3, -11513766);
         var1.fill(var4 + var5 + 2, var6 - 3, var4 + var5 + 3, var9 + 12 + 3, -11513766);
         var1.fill(var4 - 3, var9 + 12 + 2, var4 + var5 + 3, var9 + 12 + 3, -11513766);

         for (int var10 = 0; var10 < var7; var10++) {
            ItemStack var11 = this.shown.get(var8 + var10);
            int var12 = var6 + var10 * 18;
            boolean var13 = var8 + var10 == this.listSel;
            if (var13) {
               var1.fill(var4 - 3, var12, var4 + var5 + 3, var12 + 18, 1090507338);
            }

            var1.renderItem(var11, var4, var12 + 1);
            String var14 = NpcItems.idOf(var11);
            int var15 = this.font.width(var14);
            String var16 = var11.getHoverName().getString();
            int var17 = Math.max(8, var5 - 24 - var15);
            if (this.font.width(var16) > var17) {
               var1.drawString(this.font, this.font.plainSubstrByWidth(var16, var17), var4 + 18, var12 + 5, var13 ? -11702 : -1644826, false);
            } else {
               var1.drawString(this.font, var16, var4 + 18, var12 + 5, var13 ? -11702 : -1644826, false);
            }

            var1.drawString(this.font, var14, var4 + var5 - var15 - 2, var12 + 5, -5723992, false);
         }

         var1.drawString(this.font, Component.translatable("gui.settledlands.picker.listkeys").getString(), var4, var9 + 2, -5723992, false);
      }
   }

   private String clip(String var1, int var2) {
      return this.font.width(var1) <= var2 ? var1 : this.font.plainSubstrByWidth(var1, Math.max(8, var2 - 8));
   }

   private int cellAt(double var1, double var3) {
      if (!(var1 < this.gridLeft()) && !(var3 < 60.0)) {
         int var5 = (int)((var1 - this.gridLeft()) / 20.0);
         int var6 = (int)((var3 - 60.0) / 20.0);
         if (var5 >= 0 && var5 < this.cols() && var6 >= 0 && var6 < this.rows()) {
            int var7 = (this.scroll + var6) * this.cols() + var5;
            return var7 < this.shown.size() ? var7 : -1;
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   public boolean mouseClicked(double var1, double var3, int var5) {
      if (var5 == 0 && this.listHit(var1, var3)) {
         int var8 = this.listAt(var1, var3);
         if (var8 >= 0) {
            this.pick(this.shown.get(var8));
         }

         return true;
      } else {
         boolean var6 = super.mouseClicked(var1, var3, var5);
         if (this.search != null && this.search.isFocused() && !this.search.isMouseOver(var1, var3)) {
            this.search.setFocused(false);
         }

         if (var6) {
            return true;
         } else if (var5 != 0) {
            return false;
         } else {
            int var7 = this.cellAt(var1, var3);
            if (var7 < 0) {
               return false;
            } else {
               this.pick(this.shown.get(var7));
               return true;
            }
         }
      }
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (this.listVisible()) {
         if (var1 == 264) {
            this.moveList(1);
            return true;
         }

         if (var1 == 265) {
            this.moveList(-1);
            return true;
         }

         if (var1 == 257 || var1 == 335) {
            this.pick(this.shown.get(this.listSel));
            return true;
         }
      }

      return super.keyPressed(var1, var2, var3);
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      if (this.listHit(var1, var3) && var7 != 0.0) {
         int var9 = Math.min(this.listRows(), this.shown.size());
         int var10 = Math.max(0, this.shown.size() - var9);
         this.listScroll = NpcProfile.clamp(this.listScroll - (int)Math.signum(var7), 0, var10);
         return true;
      } else if (this.maxScroll() > 0 && var7 != 0.0) {
         this.scroll = NpcProfile.clamp(this.scroll - (int)Math.signum(var7), 0, this.maxScroll());
         return true;
      } else {
         return super.mouseScrolled(var1, var3, var5, var7);
      }
   }

   private void pick(ItemStack var1) {
      if (this.parent != null && !var1.isEmpty()) {
         if (this.quest) {
            this.parent.applyPickedQuest(this.offer, this.side, var1);
         } else {
            this.parent.applyPickedItem(this.offer, this.side, var1);
         }
      }

      Minecraft.getInstance().setScreen((Screen)(this.backTo == null ? this.parent : this.backTo));
   }

   public void onClose() {
      Minecraft.getInstance().setScreen((Screen)(this.backTo == null ? this.parent : this.backTo));
   }

   public boolean isPauseScreen() {
      return false;
   }
}
