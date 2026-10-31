package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcQuests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Checkbox.OnValueChange;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NpcQuestScreen extends Screen {
   private static final int MAX_WIN_W = 894;
   private static final int MAX_WIN_H = 600;
   private static final int GAP = 8;
   private final NpcConfigScreen parent;
   private final int entityId;
   private NpcProfile profile;
   private final List<NpcConfigScreen.RelRow> relations = new ArrayList<>();
   private int quest;
   private final List<Object[]> labels = new ArrayList<>();
   private final List<Object[]> blocks = new ArrayList<>();
   private int contentFrom;
   private int scrollY;
   private int contentH;
   private boolean scrollFix;

   public NpcQuestScreen(NpcConfigScreen var1, int var2, NpcProfile var3, List<NpcConfigScreen.RelRow> var4, int var5) {
      super(Component.translatable("gui.settledlands.questscreen.title", new Object[]{var5 + 1, var3 == null ? 0 : var3.quests.size()}));
      this.parent = var1;
      this.entityId = var2;
      this.profile = var3;
      if (var4 != null) {
         this.relations.addAll(var4);
      }

      this.quest = Math.max(0, var5);
   }

   int entityId() {
      return this.entityId;
   }

   void applySnapshot(NpcProfile var1, List<NpcConfigScreen.RelRow> var2) {
      if (var1 != null) {
         boolean var3 = this.profile == null
            || var1.quests.size() != this.profile.quests.size()
            || this.quest >= var1.quests.size()
            || this.kindOf(this.quest) != this.kindOf(var1, this.quest);
         this.profile = var1;
         if (var2 != null) {
            this.relations.clear();
            this.relations.addAll(var2);
         }

         if (this.quest >= var1.quests.size()) {
            this.quest = Math.max(0, var1.quests.size() - 1);
         }

         if (this.parent != null) {
            this.parent.applySnapshot(var1, var2);
         }

         if (var3) {
            this.rebuildWidgets();
         }
      }
   }

   private int kindOf(int var1) {
      return this.kindOf(this.profile, var1);
   }

   private int kindOf(NpcProfile var1, int var2) {
      NpcProfile.Quest var3 = var1 != null && var2 >= 0 && var2 < var1.quests.size() ? var1.quests.get(var2) : null;
      return var3 == null ? -1 : var3.kind;
   }

   private int winW() {
      return Math.min(894, this.width - 16);
   }

   private int winH() {
      return Math.min(600, this.height - 16);
   }

   private int ox() {
      return (this.width - this.winW()) / 2;
   }

   private int oy() {
      return (this.height - this.winH()) / 2;
   }

   private int left() {
      return this.ox() + 12;
   }

   private int right() {
      return this.ox() + this.winW() - 12;
   }

   private int bodyTop() {
      return this.oy() + 56;
   }

   private int bodyBottom() {
      return this.oy() + this.winH() - 34;
   }

   private int rowH() {
      return this.winH() < 520 ? 21 : 24;
   }

   private int labelW() {
      return NpcProfile.clamp((int)(this.winW() * 0.22), 88, 150);
   }

   private int fieldX() {
      return this.left() + this.labelW() + 8;
   }

   private int mid(int var1) {
      return Math.max(60, Math.min(var1, Math.max(70, this.right() - this.fieldX())));
   }

   private String clip(String var1, int var2) {
      return this.font.width(var1) <= var2 - 2 ? var1 : this.font.plainSubstrByWidth(var1, Math.max(8, var2 - 8));
   }

   protected void init() {
      this.labels.clear();
      this.blocks.clear();
      this.contentFrom = this.renderables.size();
      this.build();
      this.trackContent();
      if (!this.clampScroll()) {
         this.hideOutsideBody();
         this.bottomBar();
      }
   }

   private void build() {
      NpcProfile.Quest var1 = this.questAt();
      int var2 = this.bodyTop() - this.scrollY;
      if (var1 == null) {
         int var33 = this.blockStart("gui.settledlands.cfg.quests", var2);
         this.labelText(Component.translatable("gui.settledlands.cfg.questnone").getString(), this.contentTop(var33), this.left());
         this.blockEnd(var33, this.contentTop(var33) + 10);
      } else {
         String var3 = var1.title != null && !var1.title.isEmpty() ? var1.title : "—";
         int var4 = this.blockStart(
            Component.translatable("gui.settledlands.questscreen.title", new Object[]{this.quest + 1, this.profile.quests.size()})
               .copy()
               .append(Component.literal(" · «" + var3 + "»")),
            var2
         );
         var2 = this.contentTop(var4);
         this.rowLabel("gui.settledlands.field.questtitle", var2);
         EditBox var5 = this.box(this.fieldX(), var2, Math.max(70, this.right() - this.fieldX()), "gui.settledlands.hint.questtitle", var1.title, 64);
         var5.setResponder(var1x -> this.parent.questField(this.quest, "title", var1x));
         this.addRenderableWidget(var5);
         var2 += this.rowH();
         this.rowLabel("gui.settledlands.field.requirement", var2);
         var2 += this.rowH();
         boolean var6 = var1.points();
         int var7 = this.kindWidth();
         int var8 = var6 ? 46 : 34;
         Button var9 = Button.builder(Component.translatable(var6 ? "gui.settledlands.cfg.kind.points" : "gui.settledlands.cfg.kind.item"), var2x -> {
            this.parent.questField(this.quest, "kind", String.valueOf(var6 ? 0 : 1));
            this.rebuildWidgets();
         }).bounds(this.left(), var2, var7, 20).build();
         var9.setTooltip(Tooltip.create(Component.translatable("gui.settledlands.cfg.kind.hint")));
         this.addRenderableWidget(var9);
         int var10 = this.left() + var7 + 6;
         int var11 = Math.max(70, Math.min(this.mid(220), this.right() - var8 - 6 - var10));
         if (var6) {
            EditBox var12 = this.box(var10, var2, var11, "gui.settledlands.hint.pointsname", var1.pointsName, 32);
            var12.setResponder(var1x -> this.parent.questField(this.quest, "pointsName", var1x));
            this.addRenderableWidget(var12);
         } else {
            this.addRenderableWidget(NpcUi.itemButton(var10, var2, var11, var1.item, () -> this.openPicker(0)));
         }

         this.countBox(var10 + var11 + 6, var2, var8, var6 ? NpcQuests.needPoints(var1) : var1.item.getCount(), 0, var6);
         var2 += this.rowH();
         if (var6) {
            var2 = this.placeChecks(
               var2,
               List.of(
                  new NpcQuestScreen.Check(
                     Component.translatable("gui.settledlands.field.pointsshared"),
                     var1.pointsShared,
                     (var1x, var2x) -> this.parent.questField(this.quest, "pointsShared", var2x ? "1" : "0")
                  ),
                  new NpcQuestScreen.Check(
                     Component.translatable("gui.settledlands.field.pointsspend"),
                     var1.pointsSpend,
                     (var1x, var2x) -> this.parent.questField(this.quest, "pointsSpend", var2x ? "1" : "0")
                  )
               )
            );
         }

         this.rowLabel("gui.settledlands.field.reward", var2);
         int var34 = Math.max(40, Math.min(this.mid(150), this.right() - this.fieldX() - 44));
         this.addRenderableWidget(NpcUi.itemButton(this.fieldX(), var2, var34, var1.reward, () -> this.openPicker(1)));
         this.countBox(this.fieldX() + var34 + 6, var2, 34, var1.reward.getCount(), 1, false);
         var2 += this.rowH();
         var2 = this.blockEnd(var4, var2);
         int var13 = this.blockStart("gui.settledlands.block.questdialogs", var2);
         var2 = this.contentTop(var13);
         this.rowLabel("gui.settledlands.field.questdialogs", var2);
         int var14 = Math.max(20, (this.mid(420) - 12) / 4);
         int var15 = this.fieldX();
         int[] var16 = new int[]{1, 2, 3, 4};
         String[] var17 = new String[]{
            "gui.settledlands.quest.dialog.offer",
            "gui.settledlands.quest.dialog.revisit",
            "gui.settledlands.quest.dialog.hand",
            "gui.settledlands.quest.dialog.where"
         };

         for (int var18 = 0; var18 < var16.length; var18++) {
            int var19 = var16[var18];
            this.addRenderableWidget(
               Button.builder(Component.translatable(var17[var18]), var2x -> this.openBranch(var19)).bounds(var15, var2, var14, 20).build()
            );
            var15 += var14 + 4;
         }

         var2 += this.rowH();
         var2 = this.blockEnd(var13, var2);
         int var35 = this.blockStart("gui.settledlands.block.questlinks", var2);
         var2 = this.contentTop(var35);
         this.rowLabel("gui.settledlands.field.questrel", var2);
         this.addRenderableWidget(
            NpcUi.slider(
               this.fieldX(),
               var2,
               this.mid(220),
               -100,
               100,
               var1.rewardRelation,
               var0 -> Component.translatable("gui.settledlands.edit.set.rel.delta", new Object[]{var0}),
               var1x -> this.parent.questField(this.quest, "rewardRelation", String.valueOf(var1x))
            )
         );
         var2 += this.rowH();
         this.rowLabel("gui.settledlands.field.questmin", var2);
         this.addRenderableWidget(
            NpcUi.slider(
               this.fieldX(),
               var2,
               this.mid(220),
               0,
               100,
               var1.minRelation,
               var0 -> Component.literal(String.valueOf(var0)),
               var1x -> this.parent.questField(this.quest, "minRelation", String.valueOf(var1x))
            )
         );
         var2 += this.rowH();
         var2 = this.placeChecks(
            var2,
            List.of(
               new NpcQuestScreen.Check(
                  Component.translatable("gui.settledlands.field.completeall"),
                  var1.chainShared,
                  (var1x, var2x) -> this.parent.questField(this.quest, "chainShared", var2x ? "1" : "0")
               ),
               new NpcQuestScreen.Check(
                  Component.translatable("gui.settledlands.field.questrepeat"),
                  var1.repeatable,
                  (var1x, var2x) -> this.parent.questField(this.quest, "repeatable", var2x ? "1" : "0")
               )
            )
         );
         this.blockEnd(var35, var2);
      }
   }

   private int doneW() {
      return Math.max(60, Math.min(100, this.winW() / 4));
   }

   private int backW() {
      return Math.max(56, Math.min(120, this.winW() / 5));
   }

   private int navW() {
      return Math.max(22, Math.min(96, (this.winW() - 24 - this.backW() - this.doneW() - 18) / 2));
   }

   private void bottomBar() {
      int var1 = this.oy() + this.winH() - 26;
      int var2 = this.doneW();
      int var3 = this.backW();
      int var4 = this.navW();
      boolean var5 = var4 < 60;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.questscreen.back"), var1x -> this.onClose()).bounds(this.left(), var1, var3, 20).build()
      );
      int var6 = this.right() - var2 - 6 - var4;
      int var7 = var6 - 6 - var4;
      Button var8 = Button.builder(Component.literal(var5 ? "‹" : "‹ Предыдущее"), var1x -> this.flip(-1)).bounds(var7, var1, var4, 20).build();
      Button var9 = Button.builder(Component.literal(var5 ? "›" : "Следующее ›"), var1x -> this.flip(1)).bounds(var6, var1, var4, 20).build();
      int var10 = this.profile == null ? 0 : this.profile.quests.size();
      var8.active = this.quest > 0;
      var9.active = this.quest < var10 - 1;
      this.addRenderableWidget(var8);
      this.addRenderableWidget(var9);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.cfg.done"), var1x -> this.onClose()).bounds(this.right() - var2, var1, var2, 20).build()
      );
   }

   private void flip(int var1) {
      int var2 = this.profile == null ? 0 : this.profile.quests.size();
      int var3 = this.quest + var1;
      if (var3 >= 0 && var3 < var2) {
         this.quest = var3;
         this.scrollY = 0;
         this.rebuildWidgets();
      }
   }

   private void openPicker(int var1) {
      NpcProfile.Quest var2 = this.questAt();
      if (var2 != null && this.parent != null) {
         Minecraft.getInstance().setScreen(new NpcItemPickerScreen(this.parent, this.quest, var1, var1 == 0 ? var2.item : var2.reward).forQuest().backTo(this));
      }
   }

   private void openBranch(int var1) {
      if (this.profile != null) {
         int var2 = this.profile.questTopicIndex(this.quest, var1);
         if (var2 >= 0) {
            Minecraft.getInstance().setScreen(new NpcBranchScreen(this.entityId, this.profile, var2, this.relations).backTo(this));
         }
      }
   }

   private NpcProfile.Quest questAt() {
      return this.profile != null && this.quest >= 0 && this.quest < this.profile.quests.size() ? this.profile.quests.get(this.quest) : null;
   }

   private int kindWidth() {
      int var1 = this.font.width(Component.translatable("gui.settledlands.cfg.kind.item"));
      int var2 = this.font.width(Component.translatable("gui.settledlands.cfg.kind.points"));
      return Math.max(48, Math.max(var1, var2) + 14);
   }

   private EditBox countBox(int var1, int var2, int var3, int var4, int var5, boolean var6) {
      int var7 = var6 ? 6 : 2;
      EditBox var8 = new EditBox(this.font, var1, var2, var3, 20, Component.empty());
      var8.setMaxLength(var7);
      var8.setFilter(var2x -> this.digits(var2x, var7));
      var8.setValue(String.valueOf(var4));
      var8.setResponder(var2x -> this.parent.questOp("quest.count", this.quest, var5, var2x));
      this.addRenderableWidget(var8);
      return var8;
   }

   private boolean digits(String var1, int var2) {
      if (var1 != null && !var1.isEmpty()) {
         if (var1.length() > var2) {
            return false;
         } else {
            for (int var3 = 0; var3 < var1.length(); var3++) {
               if (!Character.isDigit(var1.charAt(var3))) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return true;
      }
   }

   private void rowLabel(String var1, int var2) {
      this.labels.add(new Object[]{this.clip(Component.translatable(var1).getString(), this.labelW()), this.left(), var2 + 6});
   }

   private void labelText(String var1, int var2, int var3) {
      this.labels.add(new Object[]{var1, var3, var2});
   }

   private int blockStart(Component var1, int var2) {
      this.blocks.add(new Object[]{var1.getString(), var2, var2 + 20});
      return this.blocks.size() - 1;
   }

   private int blockStart(String var1, int var2) {
      return this.blockStart(Component.translatable(var1), var2);
   }

   private int contentTop(int var1) {
      return (Integer)this.blocks.get(var1)[1] + 20;
   }

   private int blockEnd(int var1, int var2) {
      this.blocks.get(var1)[2] = var2 + 5;
      return var2 + 11;
   }

   private int placeChecks(int var1, List<NpcQuestScreen.Check> var2) {
      int var3 = this.left();
      int var4 = var1;

      for (NpcQuestScreen.Check var6 : var2) {
         int var7 = this.font.width(var6.label()) + 26;
         if (var3 > this.left() && var3 + var7 > this.right()) {
            var3 = this.left();
            var4 += 22;
         }

         this.addRenderableWidget(NpcUi.checkbox(var3, var4, var6.label(), var6.value(), var6.action()));
         var3 += var7 + 14;
      }

      return var4 + 26;
   }

   private EditBox box(int var1, int var2, int var3, String var4, String var5, int var6) {
      EditBox var7 = new EditBox(this.font, var1, var2, var3, 20, Component.empty());
      var7.setMaxLength(var6);
      if (var4 != null && !var4.isEmpty()) {
         var7.setHint(Component.translatable(var4).withStyle(ChatFormatting.DARK_GRAY));
      }

      var7.setValue(var5 == null ? "" : var5);
      return var7;
   }

   private int bodyHeight() {
      return Math.max(1, this.bodyBottom() - this.bodyTop());
   }

   private int scrollMax() {
      return Math.max(0, this.contentH - this.bodyHeight());
   }

   private void trackContent() {
      int var1 = this.bodyTop();

      for (Object[] var3 : this.labels) {
         var1 = Math.max(var1, (Integer)var3[2] + 24);
      }

      for (int var5 = this.contentFrom; var5 < this.renderables.size(); var5++) {
         if (this.renderables.get(var5) instanceof AbstractWidget var6) {
            var1 = Math.max(var1, var6.getY() + var6.getHeight() + 4);
         }
      }

      this.contentH = var1 - (this.bodyTop() - this.scrollY);
   }

   private boolean clampScroll() {
      int var1 = NpcProfile.clamp(this.scrollY, 0, this.scrollMax());
      if (var1 == this.scrollY) {
         return false;
      } else {
         this.scrollY = var1;
         if (this.scrollFix) {
            return false;
         } else {
            this.scrollFix = true;
            this.rebuildWidgets();
            this.scrollFix = false;
            return true;
         }
      }
   }

   private void hideOutsideBody() {
      for (int var1 = this.contentFrom; var1 < this.renderables.size(); var1++) {
         if (this.renderables.get(var1) instanceof AbstractWidget var2
            && (var2.getY() < this.bodyTop() - 1 || var2.getY() + var2.getHeight() > this.bodyBottom() + 1)) {
            var2.visible = false;
         }
      }
   }

   private boolean outsideBody(int var1, int var2) {
      return var1 < this.bodyTop() - 2 || var1 + var2 > this.bodyBottom() + 2;
   }

   private boolean scrollBy(int var1) {
      if (this.scrollMax() <= 0) {
         return false;
      } else {
         int var2 = this.scrollY;
         this.scrollY = NpcProfile.clamp(this.scrollY + var1, 0, this.scrollMax());
         if (this.scrollY != var2) {
            this.rebuildWidgets();
         }

         return true;
      }
   }

   public boolean mouseClicked(double var1, double var3, int var5) {
      return NpcUi.grabSlider(this, var1, var3) ? true : super.mouseClicked(var1, var3, var5);
   }

   public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
      return var5 == 0 && NpcUi.dragSlider(var1) ? true : super.mouseDragged(var1, var3, var5, var6, var8);
   }

   public boolean mouseReleased(double var1, double var3, int var5) {
      NpcUi.endSliderDrag();
      return super.mouseReleased(var1, var3, var5);
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      return var7 != 0.0 && this.scrollBy(var7 > 0.0 ? -14 : 14) ? true : super.mouseScrolled(var1, var3, var5, var7);
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (var1 == 266 && this.scrollBy(-this.bodyHeight())) {
         return true;
      } else if (var1 == 267 && this.scrollBy(this.bodyHeight())) {
         return true;
      } else if (var1 == 268 && this.scrollBy(-this.scrollY)) {
         return true;
      } else if (var1 == 269 && this.scrollBy(this.scrollMax() - this.scrollY)) {
         return true;
      } else if (var1 == 263 && this.quest > 0) {
         this.flip(-1);
         return true;
      } else if (var1 == 262 && this.profile != null && this.quest < this.profile.quests.size() - 1) {
         this.flip(1);
         return true;
      } else {
         return super.keyPressed(var1, var2, var3);
      }
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      super.renderBackground(var1, var2, var3, var4);
      int var5 = this.ox();
      int var6 = this.oy();
      int var7 = this.ox() + this.winW();
      int var8 = this.oy() + this.winH();
      var1.fill(var5 - 2, var6 - 2, var7 + 2, var8 + 2, Integer.MIN_VALUE);
      var1.fill(var5, var6, var7, var8, -267251180);
      var1.fill(var5, var6, var7, var6 + 1, 822083583);
      var1.drawCenteredString(
         this.font,
         this.clip(Component.translatable("gui.settledlands.questscreen.header").getString(), this.winW() - 24),
         var5 + this.winW() / 2,
         var6 + 7,
         -11702
      );
      var1.drawCenteredString(
         this.font,
         this.clip(Component.translatable("gui.settledlands.questscreen.subtitle").getString(), this.winW() - 24),
         var5 + this.winW() / 2,
         var6 + 18,
         -5723992
      );

      for (Object[] var10 : this.blocks) {
         int var11 = (Integer)var10[1];
         int var12 = (Integer)var10[2];
         int var13 = Math.max(var11, this.bodyTop());
         int var14 = Math.min(var12, this.bodyBottom());
         if (var14 > var13) {
            var1.fill(this.left() - 6, var13, this.right() + 4, var14, 419430399);
            var1.fill(this.left() - 6, var13, this.left() - 4, var14, 721420287);
            if (var11 >= this.bodyTop() - 1) {
               var1.fill(this.left() - 6, var11, this.right() + 4, var11 + 1, 721420287);
               var1.drawString(this.font, this.clip((String)var10[0], this.right() - this.left()), this.left(), var11 + 5, -11702, false);
               var1.fill(this.left(), var11 + 15, this.right(), var11 + 16, 486539263);
            }
         }
      }

      for (Object[] var16 : this.labels) {
         int var17 = (Integer)var16[1];
         int var18 = (Integer)var16[2];
         if (!this.outsideBody(var18, 10)) {
            var1.drawString(this.font, this.clip((String)var16[0], this.right() - var17), var17, var18, -2302756, false);
         }
      }

      this.drawScrollbar(var1);
      super.render(var1, var2, var3, var4);
   }

   private void drawScrollbar(GuiGraphics var1) {
      int var2 = this.scrollMax();
      if (var2 > 0) {
         int var3 = this.right() + 5;
         int var4 = this.bodyTop();
         int var5 = this.bodyHeight();
         var1.fill(var3, var4, var3 + 3, var4 + var5, 1342177280);
         int var6 = Math.max(16, var5 * this.bodyHeight() / this.contentH);
         int var7 = var4 + (var5 - var6) * this.scrollY / var2;
         var1.fill(var3, var7, var3 + 3, var7 + var6, -8080);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      Minecraft.getInstance().setScreen(this.parent);
   }

   private record Check(Component label, boolean value, OnValueChange action) {
   }
}
