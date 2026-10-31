package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class NpcBranchScreen extends Screen implements NpcUi.PopupHost {
   private static final int MAX_WIN_W = 894;
   private static final int MAX_WIN_H = 600;
   private static final int ROW = 22;
   private static final int MAX_STEPS_VISIBLE = 12;
   private int entityId;
   private NpcProfile profile;
   private final List<NpcConfigScreen.RelRow> relations = new ArrayList<>();
   private Screen backTo;
   private int topicIdx;
   private int stepIdx;
   private int answerPage;
   private NpcUi.NpcDropdown open;
   private EditBox lineBox;
   private boolean triggerOpen;
   private int answersPerPage = 3;
   private int scrollY;
   private int contentH;
   private int contentFrom;
   private boolean scrollFix;
   private int editorW = 600;
   private int treeW = 0;
   private final List<Object[]> labels = new ArrayList<>();
   private final List<Object[]> sections = new ArrayList<>();
   private final List<Object[]> answerLabels = new ArrayList<>();
   private boolean pendingAnswerAdd;
   private EditBox triggerBox;

   public NpcBranchScreen backTo(Screen var1) {
      this.backTo = var1;
      return this;
   }

   public NpcBranchScreen(int var1, NpcProfile var2, int var3, List<NpcConfigScreen.RelRow> var4) {
      super(Component.translatable("gui.settledlands.branch.title"));
      this.entityId = var1;
      this.profile = var2;
      this.topicIdx = NpcProfile.clamp(var3, 0, Math.max(0, var2.topics.size() - 1));
      this.relations.addAll(var4);
   }

   public void applySnapshot(NpcProfile var1, List<NpcConfigScreen.RelRow> var2) {
      boolean var3 = this.pendingAnswerAdd || this.branchStructureChanged(this.profile, var1);
      this.pendingAnswerAdd = false;
      this.profile = var1;
      this.relations.clear();
      this.relations.addAll(var2);
      this.topicIdx = NpcProfile.clamp(this.topicIdx, 0, Math.max(0, this.profile.topics.size() - 1));
      if (var3) {
         this.rebuildWidgets();
      }
   }

   private boolean branchStructureChanged(NpcProfile var1, NpcProfile var2) {
      if (var1 == null || var2 == null || var1.topics.size() != var2.topics.size()) {
         return true;
      }

      for (int var3 = 0; var3 < var1.topics.size(); var3++) {
         NpcProfile.Topic var4 = var1.topics.get(var3);
         NpcProfile.Topic var5 = var2.topics.get(var3);
         if (var4.steps.size() != var5.steps.size()) {
            return true;
         }

         for (int var6 = 0; var6 < var4.steps.size(); var6++) {
            NpcProfile.Step var7 = var4.steps.get(var6);
            NpcProfile.Step var8 = var5.steps.get(var6);
            if (var7.answers.size() != var8.answers.size() || var7.variants.size() != var8.variants.size()) {
               return true;
            }
         }
      }

      return false;
   }

   public int entityId() {
      return this.entityId;
   }

   private NpcProfile.Topic topic() {
      return this.topicIdx < this.profile.topics.size() ? this.profile.topics.get(this.topicIdx) : null;
   }

   private NpcProfile.Step step() {
      NpcProfile.Topic var1 = this.topic();
      return var1 != null && this.stepIdx >= 0 && this.stepIdx < var1.steps.size() ? var1.steps.get(this.stepIdx) : null;
   }

   private void send(String var1, int var2, int var3, int var4, String var5, String var6) {
      NpcNet.sendToServer(new NpcNet.CfgEdit(this.entityId, var1, var2, var3, var4, var5, var6));
   }

   private void rebuild() {
      this.rebuildWidgets();
   }

   private List<Component> nextNames() {
      ArrayList var1 = new ArrayList();
      var1.add(Component.translatable("gui.settledlands.branch.next.list"));
      NpcProfile.Topic var2 = this.topic();
      int var3 = var2 == null ? 0 : var2.steps.size();

      for (int var4 = 0; var4 < var3; var4++) {
         var1.add(Component.translatable("gui.settledlands.branch.next.step", new Object[]{var4 + 1}));
      }

      for (int var6 = 0; var6 < this.profile.topics.size(); var6++) {
         if (var6 != this.topicIdx) {
            NpcProfile.Topic var5 = this.profile.topics.get(var6);
            var1.add(
               NpcProfile.isQuestTopic(var5)
                  ? NpcUi.questTopicName(var5)
                  : Component.translatable("gui.settledlands.branch.next.topic", new Object[]{var5.title})
            );
         }
      }

      return var1;
   }

   private int nextToIndex(int var1) {
      if (var1 == -1) {
         return 0;
      } else {
         NpcProfile.Topic var2 = this.topic();
         int var3 = var2 == null ? 0 : var2.steps.size();
         if (var1 < 1000) {
            return 1 + NpcProfile.clamp(var1, 0, Math.max(0, var3 - 1));
         } else {
            int var4 = var1 - 1000;
            int var5 = 1 + var3;

            for (int var6 = 0; var6 < this.profile.topics.size(); var6++) {
               if (var6 != this.topicIdx) {
                  if (var6 == var4) {
                     return var5;
                  }

                  var5++;
               }
            }

            return 0;
         }
      }
   }

   private int indexToNext(int var1) {
      if (var1 == 0) {
         return -1;
      } else {
         NpcProfile.Topic var2 = this.topic();
         int var3 = var2 == null ? 0 : var2.steps.size();
         if (var1 <= var3) {
            return var1 - 1;
         } else {
            int var4 = var1 - 1 - var3;
            int var5 = 0;

            for (int var6 = 0; var6 < this.profile.topics.size(); var6++) {
               if (var6 != this.topicIdx) {
                  if (var5 == var4) {
                     return 1000 + var6;
                  }

                  var5++;
               }
            }

            return -1;
         }
      }
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
      return this.oy() + this.winH() - 14;
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

      for (Object[] var7 : this.answerLabels) {
         var1 = Math.max(var1, (Integer)var7[1] + 24);
      }

      for (int var6 = this.contentFrom; var6 < this.renderables.size(); var6++) {
         if (this.renderables.get(var6) instanceof AbstractWidget var8) {
            var1 = Math.max(var1, var8.getY() + var8.getHeight() + 4);
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

   private boolean outsideBody(int var1, int var2) {
      return var1 < this.bodyTop() - 2 || var1 + var2 > this.bodyBottom() + 2;
   }

   private String clip(String var1, int var2) {
      return this.font.width(var1) <= var2 - 2 ? var1 : this.font.plainSubstrByWidth(var1, Math.max(8, var2 - 8));
   }

   protected void init() {
      this.triggerBox = null;
      this.labels.clear();
      this.sections.clear();
      this.answerLabels.clear();
      this.open = null;
      int var1 = this.right() - this.left();
      boolean var2 = this.winW() >= 760;
      this.treeW = var2 ? Math.max(220, (int)(var1 * 0.42)) : 0;
      this.editorW = var2 ? var1 - this.treeW - 10 : var1;
      NpcProfile.Topic var3 = this.topic();
      int var4 = this.oy() + 10;
      int var5 = Math.max(70, Math.min(100, this.winW() / 6));
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.branch.back"), var1x -> this.back()).bounds(this.left(), var4, var5, 20).build()
      );
      if (var3 != null) {
         int var6 = this.editorW >= 560 ? Math.max(80, Math.min(120, this.editorW / 5)) : 0;
         List var7 = this.shownTopics();
         int var8 = Math.max(0, var7.indexOf(this.topicIdx));
         this.addRenderableWidget(
            NpcUi.dropdown(
               this.left() + var5 + 6, var4, Math.max(80, this.editorW - var5 - 6 - (var6 == 0 ? 0 : var6 + 6)), this.topicNames(), var8, var2x -> {
                  this.topicIdx = var2x >= 0 && var2x < var7.size() ? (Integer)var7.get(var2x) : this.topicIdx;
                  this.stepIdx = 0;
                  this.answerPage = 0;
                  this.rebuild();
               }
            )
         );
         if (var6 > 0) {
            this.addRenderableWidget(
               Button.builder(
                     Component.literal(this.clip(Component.translatable("gui.settledlands.branch.talk").getString(), var6)),
                     var1x -> GuideDialogueScreen.openEdit(this.entityId, this.profile == null ? null : this.profile.copy(), this, this.topicIdx)
                  )
                  .bounds(this.left() + this.editorW - var6, var4, var6, 20)
                  .build()
            );
         }

         this.stepIdx = NpcProfile.clamp(this.stepIdx, 0, Math.max(0, var3.steps.size() - 1));
         this.contentFrom = this.renderables.size();
         int var9 = this.oy() + 38 - this.scrollY;
         this.addRenderableWidget(Button.builder(Component.literal("<"), var1x -> this.moveStep(-1)).bounds(this.left(), var9, 20, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal(">"), var1x -> this.moveStep(1)).bounds(this.left() + 24, var9, 20, 20).build());
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.settledlands.branch.trigger")
                     .append(Component.literal(this.step() != null && !this.step().action.isEmpty() ? " •" : "")),
                  var1x -> {
                     this.triggerOpen = !this.triggerOpen;
                     this.rebuild();
                  }
               )
               .bounds(this.left() + 48, var9, 70, 20)
               .build()
         );
         int var10 = Math.max(60, Math.min(120, this.editorW / 4));
         this.addRenderableWidget(
            Button.builder(Component.literal(this.clip(Component.translatable("gui.settledlands.branch.step.add").getString(), var10)), var1x -> this.addStep())
               .bounds(this.left() + this.editorW - 26 - var10, var9, var10, 20)
               .build()
         );
         this.addAnswerShortcut(var9, var10);
         this.addRenderableWidget(
            Button.builder(Component.literal("×"), var1x -> this.deleteStep()).bounds(this.left() + this.editorW - 20, var9, 20, 20).build()
         );
         var9 += 24;
         this.labels.add(new Object[]{Component.translatable("gui.settledlands.branch.line").getString(), this.left(), var9});
         this.lineBox = this.box(this.left(), var9 + 12, this.editorW, "gui.settledlands.branch.line.hint", this.step() == null ? "" : this.step().line, 480);
         this.lineBox.setResponder(var1x -> this.send("step.field", this.topicIdx, this.stepIdx, 0, "line", var1x));
         var9 += 38;
         if (this.triggerOpen && this.step() != null) {
            this.labels.add(new Object[]{Component.translatable("gui.settledlands.branch.trigger").getString(), this.left(), var9});
            EditBox var11 = this.box(this.left(), var9 + 12, this.editorW, "gui.settledlands.branch.trigger.hint", this.step().action, 2048);
            var11.setResponder(var1x -> this.send("step.field", this.topicIdx, this.stepIdx, 0, "action", var1x));
            this.addRenderableWidget(var11);
            var9 += 38;
         }

         this.sections.add(new Object[]{Component.translatable("gui.settledlands.branch.variants").getString(), var9});
         var9 += 14;
         var9 = this.initVariantRows(var9);
         var9 += 4;
         this.sections.add(new Object[]{Component.translatable("gui.settledlands.branch.answers").getString(), var9});
         var9 += 15;
         this.initAnswerRows(var9);
         this.trackContent();
         if (!this.clampScroll()) {
            this.hideOutsideBody();
         }
      }
   }

   private int initVariantRows(int var1) {
      NpcProfile.Step var2 = this.step();
      if (var2 == null) {
         return var1;
      } else {
         int var3 = Math.max(60, this.editorW - 78);

         for (int var4 = 0; var4 < var2.variants.size(); var4++) {
            NpcProfile.Variant var5 = var2.variants.get(var4);
            int var6 = var4;
            EditBox var7 = this.box(this.left(), var1, 34, "", String.valueOf(var5.minRelation), 3);
            var7.setFilter(var0 -> var0.isEmpty() || var0.matches("\\d{1,3}"));
            var7.setResponder(var2x -> this.send("variant.field", this.topicIdx, this.stepIdx, var6, "min", var2x));
            this.addRenderableWidget(var7);
            EditBox var8 = this.box(this.left() + 38, var1, var3, "gui.settledlands.branch.variant.hint", var5.text, 480);
            var8.setResponder(var2x -> this.send("variant.field", this.topicIdx, this.stepIdx, var6, "text", var2x));
            this.addRenderableWidget(var8);
            this.addRenderableWidget(
               Button.builder(Component.literal("×"), var2x -> this.send("variant.del", this.topicIdx, this.stepIdx, var6, "", ""))
                  .bounds(this.left() + this.editorW - 18, var1, 18, 20)
                  .build()
            );
            var1 += 24;
         }

         if (var2.variants.size() < 5) {
            this.addRenderableWidget(
               Button.builder(
                     Component.translatable("gui.settledlands.branch.variant.add"), var1x -> this.send("variant.add", this.topicIdx, this.stepIdx, 0, "", "")
                  )
                  .bounds(this.left(), var1, Math.min(170, this.editorW), 20)
                  .build()
            );
            var1 += 24;
         }

         return var1;
      }
   }

   private void initAnswerRows(int var1) {
      NpcProfile.Step var2 = this.step();
      if (var2 != null) {
         boolean var3 = this.editorW < 560;
         int var4 = var3 ? 3 : 2;
         int var5 = var4 * 22 + 6;
         int var6 = this.bodyBottom() - 30 - var1;
         this.answersPerPage = NpcProfile.clamp(var6 / Math.max(22, var5), 1, 5);
         int var7 = Math.max(1, (var2.answers.size() + this.answersPerPage - 1) / this.answersPerPage);
         this.answerPage = NpcProfile.clamp(this.answerPage, 0, var7 - 1);
         int var8 = Math.min(this.answersPerPage, var2.answers.size() - this.answerPage * this.answersPerPage);

         for (int var9 = 0; var9 < var8; var9++) {
            int var10 = this.answerPage * this.answersPerPage + var9;
            NpcProfile.Answer var11 = var2.answers.get(var10);
            int var12 = var1 + var9 * var5;
            this.answerLabels.add(new Object[]{var10 + 1, var12 + 6});
            int var13 = this.left() + 66;
            int var14 = Math.max(60, this.editorW - 66 - 26);
            EditBox var15 = this.box(var13, var12, var14, "gui.settledlands.branch.answer.hint", var11.player, 240);
            var15.setResponder(var2x -> this.send("answer.field", this.topicIdx, this.stepIdx, var10, "player", var2x));
            this.addRenderableWidget(var15);
            this.addRenderableWidget(
               Button.builder(Component.literal("×"), var2x -> this.send("answer.del", this.topicIdx, this.stepIdx, var10, "", ""))
                  .bounds(this.left() + this.editorW - 18, var12, 18, 20)
                  .build()
            );
            int var16 = var12 + 22;
            if (var3) {
               EditBox var17 = this.box(var13, var16, var14, "gui.settledlands.branch.reaction.hint", var11.reaction, 480);
               var17.setResponder(var2x -> this.send("answer.field", this.topicIdx, this.stepIdx, var10, "reaction", var2x));
               this.addRenderableWidget(var17);
               var16 += 22;
            }

            int var28 = Math.max(80, this.left() + this.editorW - 24 - var13);
            int var18 = NpcProfile.clamp(var28 / 4, 40, 110);
            byte var19 = 4;
            int var20 = var3 ? 0 : Math.max(90, Math.min(220, this.editorW - var18 * 2 - 130));
            int var21 = var13;
            if (!var3) {
               EditBox var22 = this.box(var13, var16, var20, "gui.settledlands.branch.reaction.hint", var11.reaction, 480);
               var22.setResponder(var2x -> this.send("answer.field", this.topicIdx, this.stepIdx, var10, "reaction", var2x));
               this.addRenderableWidget(var22);
               var21 = var13 + var20 + var19;
            }

            this.addRenderableWidget(
               NpcUi.slider(
                  var21,
                  var16,
                  var18,
                  -100,
                  100,
                  var11.relation,
                  var0 -> Component.translatable("gui.settledlands.branch.rel", new Object[]{var0}),
                  var2x -> this.send("answer.field", this.topicIdx, this.stepIdx, var10, "relation", String.valueOf(var2x))
               )
            );
            var21 += var18 + var19;
            this.addRenderableWidget(
               NpcUi.slider(
                  var21,
                  var16,
                  var18,
                  0,
                  100,
                  var11.minRelation,
                  var0 -> Component.translatable("gui.settledlands.branch.minrel", new Object[]{var0}),
                  var2x -> this.send("answer.field", this.topicIdx, this.stepIdx, var10, "minRelation", String.valueOf(var2x))
               )
            );
            var21 += var18 + var19;
            int var31 = Math.max(40, this.left() + this.editorW - 18 - 6 - var21);
            this.addRenderableWidget(
               NpcUi.dropdown(
                  var21,
                  var16,
                  var31,
                  this.nextNames(),
                  this.nextToIndex(var11.next),
                  var2x -> this.send("answer.field", this.topicIdx, this.stepIdx, var10, "next", String.valueOf(this.indexToNext(var2x)))
               )
            );
         }

         int var23 = var1 + this.answersPerPage * var5 + 2;
         int var24 = Math.max(70, Math.min(170, this.editorW / 3));
         if (var2.answers.size() < 6) {
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.settledlands.branch.answer.add"), var1x -> this.addAnswer())
                  .bounds(this.left() + 66, var23, var24, 20)
                  .build()
            );
         }

         if (var7 > 1) {
            int var25 = this.left() + 66 + var24 + 8;
            Button var26 = Button.builder(Component.literal("<"), var1x -> this.flipAnswers(-1)).bounds(var25, var23, 20, 20).build();
            Button var27 = Button.builder(Component.literal(">"), var1x -> this.flipAnswers(1)).bounds(var25 + 60, var23, 20, 20).build();
            var26.active = this.answerPage > 0;
            var27.active = this.answerPage < var7 - 1;
            this.addRenderableWidget(var26);
            this.addRenderableWidget(var27);
            this.labels.add(new Object[]{this.answerPage + 1 + " / " + var7, var25 + 24, var23 + 6});
         }
      }
   }

   private void flipAnswers(int var1) {
      this.answerPage += var1;
      this.rebuild();
   }

   private void moveStep(int var1) {
      NpcProfile.Topic var2 = this.topic();
      if (var2 != null && !var2.steps.isEmpty()) {
         this.stepIdx = Math.floorMod(this.stepIdx + var1, var2.steps.size());
         this.answerPage = 0;
         this.rebuild();
      }
   }

   private void addStep() {
      NpcProfile.Topic var1 = this.topic();
      if (var1 != null && var1.steps.size() < 16) {
         int var2 = Math.min(this.stepIdx + 1, var1.steps.size());
         this.send("step.insert", this.topicIdx, var2, 0, "", "");
         this.stepIdx = var2;
         this.answerPage = 0;
         this.rebuild();
      }
   }

   private void deleteStep() {
      NpcProfile.Topic var1 = this.topic();
      if (var1 != null && !var1.steps.isEmpty()) {
         this.send("step.del", this.topicIdx, this.stepIdx, 0, "", "");
         this.stepIdx = Math.max(0, this.stepIdx - 1);
         this.answerPage = 0;
         this.rebuild();
      }
   }

   private void back() {
      if (this.backTo != null) {
         Minecraft.getInstance().setScreen(this.backTo);
      } else {
         NpcConfigScreen.jumpToTalk();
         Minecraft.getInstance().setScreen(new NpcConfigScreen(this.entityId, this.profile, this.relations));
      }
   }

   private List<Integer> shownTopics() {
      ArrayList var1 = new ArrayList();

      for (int var2 = 0; var2 < this.profile.topics.size(); var2++) {
         var1.add(var2);
      }

      return var1;
   }

   private List<Component> topicNames() {
      ArrayList var1 = new ArrayList();

      for (int var3 : this.shownTopics()) {
         NpcProfile.Topic var4 = this.profile.topics.get(var3);
         var1.add(NpcProfile.isQuestTopic(var4) ? NpcUi.questTopicName(var4) : Component.literal(var4.title.isEmpty() ? "—" : var4.title));
      }

      return var1;
   }

   private EditBox box(int var1, int var2, int var3, String var4, String var5, int var6) {
      EditBox var7 = new EditBox(this.font, var1, var2, var3, 20, Component.empty());
      var7.setMaxLength(var6);
      if (var4 != null && !var4.isEmpty()) {
         var7.setHint(Component.translatable(var4).withStyle(ChatFormatting.DARK_GRAY));
      }

      var7.setValue(var5 == null ? "" : var5);
      CommandTabCompleter.captureTriggerBox(this, var4, var7);
      return var7;
   }

   public boolean mouseClicked(double var1, double var3, int var5) {
      if (CommandTabCompleter.mouseClicked(this, var1, var3, var5)) {
         return true;
      } else {
         if (this.open != null) {
            NpcUi.NpcDropdown var6 = this.open;
            if (var6.popupClicked(var1, var3)) {
               if (!var6.popupOpen()) {
                  this.open = null;
               }

               return true;
            }

            this.open = null;
         }

         return NpcUi.grabSlider(this, var1, var3) ? true : super.mouseClicked(var1, var3, var5);
      }
   }

   public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
      if (CommandTabCompleter.mouseDragged(this, var1, var3, var5, var6, var8)) {
         return true;
      } else {
         return var5 == 0 && NpcUi.dragSlider(var1) ? true : super.mouseDragged(var1, var3, var5, var6, var8);
      }
   }

   public boolean mouseReleased(double var1, double var3, int var5) {
      CommandTabCompleter.mouseReleased(this, var1, var3, var5);
      NpcUi.endSliderDrag();
      return super.mouseReleased(var1, var3, var5);
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      if (CommandTabCompleter.mouseScrolled(this, var1, var3, var7)) {
         return true;
      } else if (this.open != null && this.open.popupScrolled(var1, var3, (int)var7)) {
         return true;
      } else {
         return var7 != 0.0 && this.scrollBy(var7 > 0.0 ? -14 : 14) ? true : super.mouseScrolled(var1, var3, var5, var7);
      }
   }

   @Override
   public void openDropdown(NpcUi.NpcDropdown var1) {
      this.open = var1;
   }

   @Override
   public NpcUi.NpcDropdown activeDropdown() {
      return this.open;
   }

   public void setTooltipForNextRenderPass(List<FormattedCharSequence> var1) {
      if (this.open == null) {
         super.setTooltipForNextRenderPass(var1);
      }
   }

   public void setTooltipForNextRenderPass(List<FormattedCharSequence> var1, ClientTooltipPositioner var2, boolean var3) {
      if (this.open == null) {
         super.setTooltipForNextRenderPass(var1, var2, var3);
      }
   }

   public void setTooltipForNextRenderPass(Component var1) {
      if (this.open == null) {
         super.setTooltipForNextRenderPass(var1);
      }
   }

   public void setTooltipForNextRenderPass(Tooltip var1, ClientTooltipPositioner var2, boolean var3) {
      if (this.open == null) {
         super.setTooltipForNextRenderPass(var1, var2, var3);
      }
   }

   public void onClose() {
      this.open = null;
      if (this.backTo != null) {
         Minecraft.getInstance().setScreen(this.backTo);
      } else {
         NpcConfigScreen.jumpToTalk();
         Minecraft.getInstance().setScreen(new NpcConfigScreen(this.entityId, this.profile, this.relations));
      }
   }

   public boolean isPauseScreen() {
      return false;
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
      NpcProfile.Topic var9 = this.topic();
      String var10 = Component.translatable("gui.settledlands.branch.title").getString() + (var9 == null ? "" : " — «" + var9.title + "»");
      var1.drawString(this.font, this.clip(var10, this.winW() - 160), this.left() + 106, this.oy() + 16, -11702, false);
      if (var9 != null) {
         String var11 = Component.translatable("gui.settledlands.branch.step", new Object[]{this.stepIdx + 1, var9.steps.size()}).getString();
         var1.drawString(this.font, this.clip(var11, this.editorW - 140), this.left() + 48, this.oy() + 44, -8080, false);
      }

      for (Object[] var12 : this.sections) {
         int var13 = (Integer)var12[1];
         if (!this.outsideBody(var13, 12)) {
            var1.drawString(this.font, this.clip((String)var12[0], this.editorW), this.left(), var13, -11702, false);
            var1.fill(this.left(), var13 + 10, this.left() + this.editorW, var13 + 11, 1090519039);
         }
      }

      for (Object[] var18 : this.labels) {
         int var20 = (Integer)var18[1];
         int var14 = (Integer)var18[2];
         if (!this.outsideBody(var14, 10)) {
            var1.drawString(this.font, this.clip((String)var18[0], this.right() - var20), var20, var14, -2302756, false);
         }
      }

      for (Object[] var19 : this.answerLabels) {
         int var21 = (Integer)var19[1];
         if (!this.outsideBody(var21, 12)) {
            var1.drawString(this.font, "Ответ " + var19[0], this.left(), var21, -8080, false);
         }
      }

      this.drawScrollbar(var1);
      this.drawTree(var1);
      super.render(var1, var2, var3, var4);
      if (this.open != null) {
         this.open.renderPopup(var1, var2, var3);
      }

      CommandTabCompleter.render(this, var1, var2, var3);
   }

   private void drawScrollbar(GuiGraphics var1) {
      int var2 = this.scrollMax();
      if (var2 > 0) {
         int var3 = this.left() + this.editorW + 3;
         int var4 = this.bodyTop();
         int var5 = this.bodyHeight();
         var1.fill(var3, var4, var3 + 3, var4 + var5, 1342177280);
         int var6 = Math.max(16, var5 * this.bodyHeight() / this.contentH);
         int var7 = var4 + (var5 - var6) * this.scrollY / var2;
         var1.fill(var3, var7, var3 + 3, var7 + var6, -8080);
      }
   }

   private void drawTree(GuiGraphics var1) {
      if (this.treeW > 0) {
         int var2 = this.left() + this.editorW + 10;
         int var3 = this.oy() + 34;
         int var4 = this.oy() + this.winH() - 12;
         var1.fill(var2 - 6, var3 - 4, var2 + this.treeW, var4, 805306368);
         var1.drawString(this.font, Component.translatable("gui.settledlands.branch.tree").getString(), var2, var3 - 2, -11702, false);
         NpcProfile.Topic var5 = this.topic();
         if (var5 != null) {
            int var6 = var3 + 12;

            for (int var7 = 0; var7 < var5.steps.size() && var7 < 12; var7++) {
               NpcProfile.Step var8 = var5.steps.get(var7);
               String var9 = this.shorten(var8.line, this.treeW - 24);
               var1.drawString(
                  this.font, (var7 == this.stepIdx ? "▶ " : "  ") + (var7 + 1) + ". " + var9, var2, var6, var7 == this.stepIdx ? -8080 : -2302756, false
               );
               var6 += 10;

               for (NpcProfile.Answer var11 : var8.answers) {
                  String var12 = this.shorten(var11.player, this.treeW - 70);
                  String var13 = this.targetName(var11.next);
                  var1.drawString(this.font, "     «" + var12 + "» → " + var13, var2, var6, -5723992, false);
                  var6 += 10;
                  if (var6 > var4 - 12) {
                     return;
                  }
               }

               var6 += 3;
            }

            if (var5.steps.size() > 12) {
               var1.drawString(
                  this.font,
                  Component.translatable("gui.settledlands.branch.tree.more", new Object[]{var5.steps.size() - 12}).getString(),
                  var2,
                  var6,
                  -5723992,
                  false
               );
            }
         }
      }
   }

   private String targetName(int var1) {
      if (var1 == -1) {
         return Component.translatable("gui.settledlands.branch.next.list").getString();
      } else if (var1 >= 1000) {
         int var2 = var1 - 1000;
         return var2 < this.profile.topics.size()
            ? Component.translatable("gui.settledlands.branch.next.topic", new Object[]{this.profile.topics.get(var2).title}).getString()
            : "?";
      } else {
         return Component.translatable("gui.settledlands.branch.next.step", new Object[]{var1 + 1}).getString();
      }
   }

   private String shorten(String var1, int var2) {
      if (var1 != null && !var1.isEmpty()) {
         String var3 = this.font.plainSubstrByWidth(var1, Math.max(20, var2));
         return var3.length() < var1.length() ? var3 + "…" : var3;
      } else {
         return "…";
      }
   }

   private void addAnswerShortcut(int var1, int var2) {
      NpcProfile.Step var3 = this.step();
      if (var3 != null && var3.answers.size() < 6) {
         int var4 = this.editorW - 26 - var2 - 6 - var2;
         var4 = Math.max(var4, 124);
         int var5 = Math.min(this.editorW - 26 - var2 - 6 - var4, var2);
         if (var5 >= 48) {
            this.addRenderableWidget(
               Button.builder(
                     Component.literal(this.clip(Component.translatable("gui.settledlands.branch.answer.add.quick").getString(), var5)),
                     var1x -> this.addAnswer()
                  )
                  .bounds(this.left() + var4, var1, var5, 20)
                  .build()
            );
            return;
         }
      }
   }

   private void addAnswer() {
      NpcProfile.Step var1 = this.step();
      if (var1 != null && var1.answers.size() < 6) {
         this.pendingAnswerAdd = true;
         this.answerPage = var1.answers.size() / this.answersPerPage;
         this.send("answer.add", this.topicIdx, this.stepIdx, 0, "", "");
      }
   }

   private boolean keyPressed$original(int var1, int var2, int var3) {
      if (var1 == 256 && this.open != null) {
         this.open.closePopup();
         this.open = null;
         return true;
      }
      if (var1 == 294) {
         this.send("test", 0, 0, 0, "", "");
         this.onClose();
         return true;
      }
      if (var1 == 267 && this.scrollBy(this.bodyHeight() / 2)) {
         return true;
      }
      if (var1 == 266 && this.scrollBy(-this.bodyHeight() / 2)) {
         return true;
      }
      if (var1 == 268 && this.scrollBy(-this.scrollY)) {
         return true;
      }
      return var1 == 269 && this.scrollBy(this.scrollMax() - this.scrollY) ? true : super.keyPressed(var1, var2, var3);
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      return CommandTabCompleter.handleKey(this, var1, var2, var3) ? true : this.keyPressed$original(var1, var2, var3);
   }
}
