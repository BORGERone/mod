package dev.settledlands.npc.client;

import dev.settledlands.model.BbModelManager;
import dev.settledlands.model.BbModelStorage;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.EscortRules;
import dev.settledlands.npc.NpcItems;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcQuests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Checkbox.OnValueChange;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class NpcConfigScreen extends Screen implements NpcUi.PopupHost {
   private static final String[] TABS = new String[]{"npc", "talk", "shop", "quest", "frame", "mobility", "escort"};
   private static final int MAX_WIN_W = 894;
   private static final int MAX_WIN_H = 600;
   private static final int GAP = 8;
   private final int entityId;
   private NpcProfile profile;
   private final List<NpcConfigScreen.RelRow> relations = new ArrayList<>();
   final List<NpcConfigScreen.MobilityGroupOption> mobilityGroups = new ArrayList<>();
   private final List<EditBox> mobilityScheduleTimes = new ArrayList<>();
   private static boolean jumpTalk;
   private int tab;
   private boolean escortSettingsDirty;
   private long escortSettingsAt;
   private int topicIdx;
   private int topicPage;
   private int offerIdx;
   private int offerPage;
   private int questIdx;
   private int questPage;
   private int repIdx = 2;
   private int relPage;
   private String relName = "";
   private int relValue = 0;
   private NpcUi.NpcDropdown open;
   private Component status = Component.empty();
   private long statusUntil;
   private int scrollY;
   private int contentH;
   private int contentFrom;
   private boolean scrollFix;
   private boolean mobilityGroupsRequested;
   private boolean updatingMobilityScheduleTimes;
   private int relLabelY;
   private int relPerPage = 6;
   private EditBox nameBox;
   private EditBox identifierBox;
   private EditBox titleBox;
   private EditBox textureBox;
   private EditBox repBox;
   private EditBox welcomeBox;
   private EditBox relBox;
   private final List<EditBox> topicTitles = new ArrayList<>();
   private final List<EditBox> topicKinds = new ArrayList<>();
   private final List<EditBox> offerCounts = new ArrayList<>();
   private final List<EditBox> offerPrices = new ArrayList<>();
   private final List<EditBox> offerDescs = new ArrayList<>();
   private final List<Object[]> labels = new ArrayList<>();
   private final List<Object[]> blocks = new ArrayList<>();
   private final List<int[]> selectedRows = new ArrayList<>();

   public static void jumpToTalk() {
      jumpTalk = true;
   }

   public NpcConfigScreen(int var1, NpcProfile var2, List<NpcConfigScreen.RelRow> var3) {
      this(var1, var2, var3, List.of());
   }

   public NpcConfigScreen(int var1, NpcProfile var2, List<NpcConfigScreen.RelRow> var3, List<NpcConfigScreen.MobilityGroupOption> groups) {
      super(Component.translatable("gui.settledlands.cfg.title"));
      this.entityId = var1;
      this.profile = var2;
      this.relations.clear();
      this.relations.addAll(var3);
      this.mobilityGroups.clear();
      this.mobilityGroups.addAll(groups);
   }

   public static void open(NpcNet.CfgView var0) {
      if (var0.entityId() == NpcNet.MOBILITY_EDITOR_ID) {
         MobilityWandScreen.open(var0);
         return;
      }
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null) {
         ArrayList var2 = new ArrayList();
         CompoundTag var3 = var0.data();
         ListTag var4 = var3 == null ? new ListTag() : var3.getList("Relations", 10);

         for (int var5 = 0; var5 < var4.size(); var5++) {
            CompoundTag var6 = var4.getCompound(var5);
            var2.add(new NpcConfigScreen.RelRow(var6.getString("Name"), var6.getInt("V")));
         }

         List<NpcConfigScreen.MobilityGroupOption> groupOptions = readMobilityGroups(var3);
         NpcProfile var12 = NpcProfile.load(var3 == null ? new CompoundTag() : var3.getCompound("Profile"), registries(var1));
         if (var1.screen instanceof NpcBranchScreen var13 && var13.entityId() == var0.entityId()) {
            var13.applySnapshot(var12, var2);
         } else if (var1.screen instanceof NpcDialogueGraphScreen graph && graph.entityId() == var0.entityId()) {
            graph.applySnapshot(var12);
         } else if (var1.screen instanceof GuideDialogueScreen var7 && var7.editMode() && var7.editorEntityId() == var0.entityId()) {
            var7.applySnapshot(var12);
         } else if (var1.screen instanceof NpcQuestScreen var8 && var8.entityId() == var0.entityId()) {
            var8.applySnapshot(var12, var2);
         } else if (var1.screen instanceof NpcItemPickerScreen var9 && var9.entityId() == var0.entityId()) {
            var9.applySnapshot(var12, var2);
         } else if (var1.screen instanceof NpcConfigScreen var10 && var10.entityId == var0.entityId()) {
            var10.applySnapshot(var12, var2, groupOptions);
         } else {
            var1.setScreen(new NpcConfigScreen(var0.entityId(), var12, var2, groupOptions));
         }
      }
   }

   private static Provider registries(Minecraft var0) {
      return var0.level == null ? null : var0.level.registryAccess();
   }

   private static List<NpcConfigScreen.MobilityGroupOption> readMobilityGroups(CompoundTag data) {
      ArrayList<NpcConfigScreen.MobilityGroupOption> result = new ArrayList<>();
      ListTag list = data == null ? new ListTag() : data.getList("MobilityGroups", 10);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag group = list.getCompound(i);
         String id = group.getString("Id");
         if (!id.isBlank()) {
            String name = group.getString("Name");
            if (name.isBlank()) {
               name = id.substring(0, Math.min(8, id.length()));
            }
            result.add(new NpcConfigScreen.MobilityGroupOption(id, name, Math.max(0, group.getInt("PointCount"))));
         }
      }
      return result;
   }

   int entityId() {
      return this.entityId;
   }

   void applySnapshot(NpcProfile var1, List<NpcConfigScreen.RelRow> var2) {
      this.applySnapshot(var1, var2, new ArrayList<>(this.mobilityGroups));
   }

   void applySnapshot(NpcProfile var1, List<NpcConfigScreen.RelRow> var2, List<NpcConfigScreen.MobilityGroupOption> groups) {
      boolean rebuild = var1.topics.size() != this.profile.topics.size() || var1.offers.size() != this.profile.offers.size()
         || var1.mobilityPoints.size() != this.profile.mobilityPoints.size()
         || !java.util.Objects.equals(this.profile.mobilityGroupId, var1.mobilityGroupId)
         || scheduleLayoutChanged(this.profile.mobilitySchedule, var1.mobilitySchedule)
         || !this.mobilityGroups.equals(groups) || stepsChanged(this.profile, var1);
      if (this.escortSettingsDirty) {
         var1.escortCode = this.profile.escortCode;
         var1.escortCoordinates = this.profile.escortCoordinates;
      }
      this.profile = var1;
      this.relations.clear();
      this.relations.addAll(var2);
      this.mobilityGroups.clear();
      this.mobilityGroups.addAll(groups);
      if (rebuild) {
         this.rebuildWidgets();
      } else {
         this.updatingMobilityScheduleTimes = true;
         for (int i = 0; i < this.mobilityScheduleTimes.size() && i < this.profile.mobilitySchedule.size(); i++) {
            EditBox timeBox = this.mobilityScheduleTimes.get(i);
            String time = String.valueOf(this.profile.mobilitySchedule.get(i).second);
            if (!timeBox.isFocused() && !timeBox.getValue().equals(time)) {
               timeBox.setValue(time);
            }
         }
         this.updatingMobilityScheduleTimes = false;
      }
   }

   private static boolean scheduleLayoutChanged(List<NpcProfile.MobilityScheduleEntry> before,
      List<NpcProfile.MobilityScheduleEntry> after) {
      if (before.size() != after.size()) {
         return true;
      }
      for (int i = 0; i < before.size(); i++) {
         NpcProfile.MobilityScheduleEntry oldEntry = before.get(i);
         NpcProfile.MobilityScheduleEntry newEntry = after.get(i);
         if (oldEntry == null ? newEntry != null : newEntry == null || !java.util.Objects.equals(oldEntry.groupId, newEntry.groupId)) {
            return true;
         }
      }
      return false;
   }

   private static boolean stepsChanged(NpcProfile var0, NpcProfile var1) {
      if (var0.topics.size() != var1.topics.size()) {
         return true;
      } else {
         for (int var2 = 0; var2 < var0.topics.size(); var2++) {
            NpcProfile.Topic var3 = var0.topics.get(var2);
            NpcProfile.Topic var4 = var1.topics.get(var2);
            if (var3.steps.size() != var4.steps.size()) {
               return true;
            }

            for (int var5 = 0; var5 < var3.steps.size(); var5++) {
               if (var3.steps.get(var5).answers.size() != var4.steps.get(var5).answers.size()
                  || var3.steps.get(var5).variants.size() != var4.steps.get(var5).variants.size()) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private void status(String var1) {
      this.status = Component.translatable(var1);
      this.statusUntil = System.currentTimeMillis() + 2500L;
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

   private boolean usePreview() {
      return this.tab == 0 && this.winW() >= 700;
   }

   private int previewW() {
      return this.usePreview() ? 176 : 0;
   }

   private int fieldW() {
      return Math.max(70, this.right() - this.fieldX() - this.previewW());
   }

   private int mid(int var1) {
      return Math.max(60, Math.min(var1, this.fieldW()));
   }

   private int contentRight() {
      return this.usePreview() ? this.right() - this.previewW() : this.right();
   }

   private int panelRight() {
      return this.contentRight() + 4;
   }

   private String clip(String var1, int var2) {
      return this.font.width(var1) <= var2 - 2 ? var1 : this.font.plainSubstrByWidth(var1, Math.max(8, var2 - 8));
   }

   protected void init() {
      if (jumpTalk) {
         this.tab = 1;
         jumpTalk = false;
      }

      this.labels.clear();
      this.blocks.clear();
      this.selectedRows.clear();
      this.open = null;
      this.topicTitles.clear();
      this.topicKinds.clear();
      this.offerCounts.clear();
      this.offerPrices.clear();
      this.offerDescs.clear();
      this.mobilityScheduleTimes.clear();
      if (!this.mobilityGroupsRequested && this.mobilityGroups.isEmpty()) {
         this.mobilityGroupsRequested = true;
         this.send("refresh", 0, 0, 0, "", "");
      }
      int var1 = this.oy() + 24;
      int var2 = Math.max(24, Math.min(130, (this.winW() - 24 - (TABS.length - 1) * 4) / TABS.length));

      for (int var3 = 0; var3 < TABS.length; var3++) {
         int var4 = var3;
         String var5 = Component.translatable("gui.settledlands.tab." + TABS[var3]).getString();
         MutableComponent var6 = Component.literal(this.clip(var5, var2));
         var6 = var4 == this.tab ? var6.copy().withStyle(ChatFormatting.YELLOW) : var6;
         int var7 = this.left() + var3 * (var2 + 4);
         Button var8 = Button.builder(var6, var2x -> this.switchTab(var4)).bounds(var7, var1, var2, 20).build();
         var8.setTooltip(Tooltip.create(Component.translatable("gui.settledlands.tab." + TABS[var3])));
         this.addRenderableWidget(var8);
      }

      this.contentFrom = this.renderables.size();
      switch (this.tab) {
         case 0:
            this.initCharacter();
            break;
         case 1:
            this.initTalk();
            break;
         case 2:
            this.initShop();
            break;
         case 3:
            this.initQuests();
            break;
         case 4:
            this.initFrame();
            break;
         case 5:
            this.initMobility();
            break;
         default:
            this.initEscort();
      }

      this.trackContent();
      if (!this.clampScroll()) {
         this.hideOutsideBody();
         int var9 = this.oy() + this.winH() - 26;
         int var10 = Math.max(60, Math.min(100, this.winW() / 4));
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.settledlands.cfg.done"), var1x -> this.onClose()).bounds(this.right() - var10, var9, var10, 20).build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.settledlands.cfg.refresh"), var1x -> this.refresh())
               .bounds(this.right() - var10 - 6 - var10, var9, var10, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(
                  Component.literal(this.clip(Component.translatable("gui.settledlands.cfg.editdlg").getString(), var10)), var1x -> this.openDialogue()
               )
               .bounds(this.right() - 2 * var10 - 12 - var10, var9, var10, 20)
               .build()
         );
      }
   }

   public void acceptGraphProfile(NpcProfile profile) {
      this.profile = profile.copy();
      this.rebuildWidgets();
   }

   private void openDialogue() {
      this.flushEscortSettings();
      GuideDialogueScreen.openEdit(this.entityId, this.profile == null ? null : this.profile.copy(), this, -1);
   }

   private void switchTab(int var1) {
      if (this.tab != var1) {
         this.flushEscortSettings();
         this.tab = var1;
         this.scrollY = 0;
         this.open = null;
         this.rebuildWidgets();
      }
   }

   private void refresh() {
      this.status("gui.settledlands.cfg.reloading");
      this.send("refresh", 0, 0, 0, "", "");
   }

   private void refreshModelsList() {
      BbModelManager.clearCache();
      this.rebuildWidgets();
   }

   private int rowLabel(String var1, int var2) {
      this.labels.add(new Object[]{this.clip(Component.translatable(var1).getString(), this.labelW()), this.left(), var2 + 6});
      return var2;
   }

   private int placeChecks(int var1, List<NpcConfigScreen.Check> var2) {
      int var3 = this.left();
      int var4 = var1;

      for (NpcConfigScreen.Check var6 : var2) {
         int var7 = this.font.width(var6.label()) + 26;
         if (var3 > this.left() && var3 + var7 > this.contentRight()) {
            var3 = this.left();
            var4 += 22;
         }

         this.addRenderableWidget(NpcUi.checkbox(var3, var4, var6.label(), var6.value(), var6.action()));
         var3 += var7 + 14;
      }

      return var4 + 26;
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

      for (Object[] var7 : this.blocks) {
         var1 = Math.max(var1, (Integer)var7[2] + 4);
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

   private int rowsPerPage(int var1, int var2, int var3) {
      int var4 = this.bodyBottom() - var2 - var1;
      return NpcProfile.clamp(var4 / Math.max(12, var3), 1, 6);
   }

   private void initCharacter() {
      int var1 = this.bodyTop() - this.scrollY;
      int var2 = this.fieldW();
      int var3 = this.blockStart("gui.settledlands.block.identity", var1);
      var1 = this.contentTop(var3);
      this.rowLabel("gui.settledlands.field.name", var1);
      this.nameBox = this.box(this.fieldX(), var1, var2, "gui.settledlands.hint.name", this.profile.name, 32, "name");
      this.addRenderableWidget(this.nameBox);
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.namecolor", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            var2,
            List.of(
               Component.translatable("gui.settledlands.color.0"),
               Component.translatable("gui.settledlands.color.1"),
               Component.translatable("gui.settledlands.color.2"),
               Component.translatable("gui.settledlands.color.3")
            ),
            this.profile.nameColor,
            var1x -> this.field("nameColor", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.identifier", var1);
      this.identifierBox = this.box(this.fieldX(), var1, var2, "gui.settledlands.hint.identifier", this.profile.identifier, 64, "identifier");
      this.addRenderableWidget(this.identifierBox);
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.title", var1);
      this.titleBox = this.box(this.fieldX(), var1, var2, "gui.settledlands.hint.title", this.profile.title, 32, "title");
      this.addRenderableWidget(this.titleBox);
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.texture", var1);
      List<String> modelFiles = BbModelStorage.listAvailableModels();
      List<Component> modelOptions = new ArrayList<>();
      modelOptions.add(Component.translatable("gui.settledlands.model.default"));
      for (String mf : modelFiles) {
         modelOptions.add(Component.literal(mf));
      }
      int selectedModelIdx = 0;
      for (int mi = 0; mi < modelFiles.size(); mi++) {
         if (modelFiles.get(mi).equalsIgnoreCase(this.profile.texture)) {
            selectedModelIdx = mi + 1;
            break;
         }
      }
      if (selectedModelIdx == 0 && this.profile.texture != null && !this.profile.texture.isBlank()) {
         modelOptions.add(Component.literal(this.profile.texture));
         selectedModelIdx = modelOptions.size() - 1;
      }
      int dropdownW = Math.max(60, var2 - 48);
      this.addRenderableWidget(
         NpcUi.dropdown(this.fieldX(), var1, dropdownW, modelOptions, selectedModelIdx, idx -> {
            if (idx == 0) {
               this.field("texture", "");
            } else if (idx - 1 < modelFiles.size()) {
               this.field("texture", modelFiles.get(idx - 1));
            }
         })
      );
      Button refreshModelsBtn = Button.builder(Component.literal("↻"), b -> this.refreshModelsList())
         .bounds(this.fieldX() + dropdownW + 4, var1, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.settledlands.model.refresh")))
         .build();
      this.addRenderableWidget(refreshModelsBtn);

      Button openModelsFolderBtn = Button.builder(Component.literal("📁"), b -> net.minecraft.Util.getPlatform().openPath(BbModelStorage.getModelsDir()))
         .bounds(this.fieldX() + dropdownW + 26, var1, 20, 20)
         .tooltip(Tooltip.create(Component.translatable("gui.settledlands.model.open_folder")))
         .build();
      this.addRenderableWidget(openModelsFolderBtn);
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.pose", var1);
      this.addRenderableWidget(NpcUi.dropdown(this.fieldX(), var1, var2, this.poseNames(), this.profile.pose, var1x -> this.field("pose", var1x)));
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.scale", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            50,
            150,
            this.profile.scale,
            var0 -> Component.translatable("gui.settledlands.hint.scale", new Object[]{var0}),
            var1x -> this.field("scale", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.blockEnd(var3, var1);
      int var4 = this.blockStart("gui.settledlands.block.reputation", var1);
      var1 = this.contentTop(var4);
      int var5 = Math.min(this.mid(140), Math.max(60, var2 - 46));
      this.rowLabel("gui.settledlands.field.reputation", var1);
      this.addRenderableWidget(NpcUi.dropdown(this.fieldX(), var1, var5, this.repTierNames(), this.repIdx, var1x -> this.onRepTier(var1x)));
      this.repBox = this.box(
         this.fieldX() + var5 + 6, var1, Math.max(40, var2 - var5 - 6), "gui.settledlands.hint.repname", this.repTierName(this.repIdx), 24, "rep" + this.repIdx
      );
      this.addRenderableWidget(this.repBox);
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.defaultrel", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            0,
            100,
            this.profile.defaultRelation,
            var0 -> Component.translatable("gui.settledlands.hint.defaultrel", new Object[]{var0}),
            var1x -> this.field("default", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.blockEnd(var4, var1);
      int var6 = this.blockStart("gui.settledlands.block.behaviour", var1);
      var1 = this.contentTop(var6);
      var1 = this.placeChecks(
         var1,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.nametag"), this.profile.nameTag, (var1x, var2x) -> this.field("nameTag", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.lookat"), this.profile.lookAtPlayer, (var1x, var2x) -> this.field("lookAt", var2x ? 1 : 0)
            )
         )
      );
      var1 = this.blockEnd(var6, var1);
      int var7 = this.blockStart("gui.settledlands.block.focus", var1);
      var1 = this.contentTop(var7);
      this.rowLabel("gui.settledlands.field.focus", var1);
      List<GuideNpc> var8 = this.focusNpcCandidates();
      ArrayList<Component> var9 = new ArrayList<>();
      ArrayList<String> var10 = new ArrayList<>();
      var9.add(Component.translatable("gui.settledlands.focus.none"));
      var10.add("");
      if (this.profile.focusNpcUuid != null && !this.profile.focusNpcUuid.isBlank()
         && var8.stream().noneMatch(var0 -> this.profile.focusNpcUuid.equals(var0.getUUID().toString()))) {
         String var11 = this.profile.focusNpcUuid;
         var9.add(Component.translatable("gui.settledlands.focus.missing", var11.length() > 8 ? var11.substring(0, 8) : var11));
         var10.add(var11);
      }

      for (GuideNpc var12 : var8) {
         String var13 = var12.profile().name;
         if (var13 == null || var13.isBlank()) {
            var13 = var12.getDisplayName().getString();
         }
         String var14 = var12.getUUID().toString();
         var9.add(Component.literal(var13 + " · " + var14.substring(0, 8)));
         var10.add(var14);
      }

      int var15 = Math.max(0, var10.indexOf(this.profile.focusNpcUuid == null ? "" : this.profile.focusNpcUuid));
      this.addRenderableWidget(NpcUi.dropdown(this.fieldX(), var1, var2, var9, var15, var16 -> {
         if (var16 >= 0 && var16 < var10.size()) {
            this.setFocusUuid(var10.get(var16));
         }
      }));
      var1 += this.rowH();
      this.blockEnd(var7, var1);
   }

   private void setFocusUuid(String value) {
      this.field("focusNpcUuid", value);
   }

   private List<GuideNpc> focusNpcCandidates() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level == null) {
         return List.of();
      }

      Entity var2 = var1.level.getEntity(this.entityId);
      ArrayList<GuideNpc> var3 = new ArrayList<>();
      for (Entity var4 : var1.level.entitiesForRendering()) {
         if (var4 instanceof GuideNpc var5 && var5.getId() != this.entityId && var5.isAlive()
            && (var2 == null || var2.distanceToSqr(var5) <= 128.0 * 128.0)) {
            var3.add(var5);
         }
      }
      var3.sort(Comparator.comparing((GuideNpc var0) -> {
         String var1x = var0.profile().name;
         return var1x == null || var1x.isBlank() ? var0.getDisplayName().getString() : var1x;
      }).thenComparing(var0 -> var0.getUUID().toString()));
      return var3;
   }

   private List<Component> repTierNames() {
      ArrayList var1 = new ArrayList(5);
      int[] var2 = new int[]{0, 20, 40, 60, 80};
      int[] var3 = new int[]{19, 39, 59, 79, 100};

      for (int var4 = 0; var4 < 5; var4++) {
         String var5 = var4 < this.profile.reputationNames.size() ? this.profile.reputationNames.get(var4) : "";
         var1.add(Component.literal(var2[var4] + "–" + var3[var4] + ": " + (var5.isEmpty() ? "—" : var5)));
      }

      return var1;
   }

   private String repTierName(int var1) {
      return var1 < this.profile.reputationNames.size() ? this.profile.reputationNames.get(var1) : "";
   }

   private void onRepTier(int var1) {
      this.repIdx = NpcProfile.clamp(var1, 0, 4);
      this.status("gui.settledlands.cfg.rep.autosave");
      this.rebuildWidgets();
   }

   private List<Component> poseNames() {
      ArrayList var1 = new ArrayList(3);

      for (int var2 = 0; var2 < 3; var2++) {
         var1.add(Component.translatable("gui.settledlands.pose." + var2));
      }

      return var1;
   }

   private void initTalk() {
      int var1 = this.bodyTop() - this.scrollY;
      int var2 = this.fieldW();
      int var3 = this.blockStart("gui.settledlands.block.welcome", var1);
      var1 = this.contentTop(var3);
      this.rowLabel("gui.settledlands.field.replica", var1);
      if (this.profile.dialogueGraph != null && this.profile.dialogueGraph.enabled) {
         this.welcomeBox = null;
         this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.graph.open"), b -> this.openDialogue())
            .bounds(this.fieldX(), var1, var2, 20).build());
      } else {
         this.welcomeBox = this.box(this.fieldX(), var1, var2, "gui.settledlands.hint.replica", this.profile.welcome, 480, "welcome");
         this.addRenderableWidget(this.welcomeBox);
      }
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.style", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            var2,
            List.of(Component.translatable("gui.settledlands.style.0"), Component.translatable("gui.settledlands.style.1")),
            this.profile.style,
            var1x -> this.field("style", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.textcolor", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            var2,
            List.of(
               Component.translatable("gui.settledlands.color.0"),
               Component.translatable("gui.settledlands.color.1"),
               Component.translatable("gui.settledlands.color.2"),
               Component.translatable("gui.settledlands.color.3")
            ),
            this.profile.textColor,
            var1x -> this.field("textColor", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.typespeed", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            0,
            120,
            this.profile.typingSpeed,
            var0 -> var0 == 0
               ? Component.translatable("gui.settledlands.hint.typespeed.off")
               : Component.translatable("gui.settledlands.hint.typespeed", new Object[]{var0}),
            var1x -> this.field("typingSpeed", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.voice", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(this.fieldX(), var1, var2, this.voiceOptions(), this.voiceIndex(), var1x -> this.field("voice", this.voiceName(var1x)))
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.voicepitch", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            0,
            12,
            this.profile.voicePitch,
            var0 -> var0 == 0
               ? Component.translatable("gui.settledlands.hint.voicepitch.off")
               : Component.translatable("gui.settledlands.hint.voicepitch", new Object[]{var0}),
            var1x -> this.field("voicePitch", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.blockEnd(var3, var1);
      int var4 = this.blockStart("gui.settledlands.block.talk", var1);
      var1 = this.contentTop(var4);
      var1 = this.placeChecks(
         var1,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.speaker"), this.profile.showSpeaker, (var1x, var2x) -> this.field("showSpeaker", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.keyhints"), this.profile.keyHints, (var1x, var2x) -> this.field("keyHints", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.answernums"),
               this.profile.answerNumbers,
               (var1x, var2x) -> this.field("answerNumbers", var2x ? 1 : 0)
            )
         )
      );
      var1 = this.blockEnd(var4, var1);
      int var5 = this.blockStart("gui.settledlands.cfg.buttons", var1);
      int var6 = this.contentTop(var5);
      if (this.profile.dialogueGraph != null && this.profile.dialogueGraph.enabled) {
         this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.graph.open"), b -> this.openDialogue())
            .bounds(this.left(), var6, this.right() - this.left(), 20).build());
         var6 += this.rowH();
      } else var6 = this.initTopicRows(var6);
      this.blockEnd(var5, var6);
   }

   private int initTopicRows(int var1) {
      int var2 = this.right() - this.left();
      boolean var3 = var2 < 560;
      int var4 = var3 ? 46 : this.rowH();
      byte var5 = 44;
      int var6 = this.rowsPerPage(var1, var5, var4);
      ArrayList var7 = new ArrayList();

      for (int var8 = 0; var8 < this.profile.topics.size(); var8++) {
         if (!NpcProfile.isQuestTopic(this.profile.topics.get(var8))) {
            var7.add(var8);
         }
      }

      int var20 = Math.max(1, (var7.size() + var6 - 1) / var6);
      this.topicPage = Math.max(0, Math.min(this.topicPage, var20 - 1));
      int var9 = Math.min(var6, var7.size() - this.topicPage * var6);

      for (int var10 = 0; var10 < var9; var10++) {
         int var11 = (Integer)var7.get(this.topicPage * var6 + var10);
         NpcProfile.Topic var12 = this.profile.topics.get(var11);
         int var13 = var1 + var10 * var4;
         if (var11 == this.topicIdx) {
            this.selectedRows.add(new int[]{this.left(), var13 - 3, this.right(), var13 + var4 - 5});
         }

         if (var3) {
            EditBox var14 = this.box(this.left(), var13, Math.max(60, var2 - 24), "gui.settledlands.hint.button", var12.title, 64, null);
            var14.setResponder(var2x -> this.topicField(var11, "title", var2x));
            this.topicTitles.add(var14);
            this.addRenderableWidget(var14);
            this.addRenderableWidget(
               Button.builder(Component.literal("×"), var2x -> this.topicOp("topic.del", var11)).bounds(this.right() - 20, var13, 20, 20).build()
            );
            int var15 = var13 + 24;
            int var16 = Math.max(50, (var2 - 96) / 2);
            EditBox var17 = this.box(this.left(), var15, var16, "gui.settledlands.hint.kind", var12.kind, 16, null);
            var17.setResponder(var2x -> this.topicField(var11, "kind", var2x));
            this.topicKinds.add(var17);
            this.addRenderableWidget(var17);
            this.addRenderableWidget(
               NpcUi.dropdown(
                  this.left() + var16 + 6,
                  var15,
                  Math.max(60, var2 - var16 - 82),
                  this.typeNames(),
                  var12.type,
                  var2x -> this.topicField(var11, "type", String.valueOf(var2x))
               )
            );
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.settledlands.cfg.branches"), var2x -> this.openBranches(var11))
                  .bounds(this.right() - 72, var15, 72, 20)
                  .build()
            );
         } else {
            int var26 = NpcProfile.clamp((int)(var2 * 0.16), 80, 130);
            int var28 = NpcProfile.clamp((int)(var2 * 0.16), 70, 110);
            int var29 = Math.max(80, var2 - var28 - var26 - 72 - 20 - 16);
            EditBox var30 = this.box(this.left(), var13, var29, "gui.settledlands.hint.button", var12.title, 64, null);
            var30.setResponder(var2x -> this.topicField(var11, "title", var2x));
            this.topicTitles.add(var30);
            this.addRenderableWidget(var30);
            int var18 = this.left() + var29 + 4;
            EditBox var19 = this.box(var18, var13, var28, "gui.settledlands.hint.kind", var12.kind, 16, null);
            var19.setResponder(var2x -> this.topicField(var11, "kind", var2x));
            this.topicKinds.add(var19);
            this.addRenderableWidget(var19);
            var18 += var28 + 4;
            this.addRenderableWidget(
               NpcUi.dropdown(var18, var13, var26, this.typeNames(), var12.type, var2x -> this.topicField(var11, "type", String.valueOf(var2x)))
            );
            var18 += var26 + 4;
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.settledlands.cfg.branches"), var2x -> this.openBranches(var11)).bounds(var18, var13, 72, 20).build()
            );
            var18 += 76;
            this.addRenderableWidget(Button.builder(Component.literal("×"), var2x -> this.topicOp("topic.del", var11)).bounds(var18, var13, 20, 20).build());
         }
      }

      int var21 = var1 + var6 * var4 + 2;
      int var22 = this.mid(200);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.cfg.addbutton"), var1x -> this.topicOp("topic.add", 0))
            .bounds(this.left(), var21, var22, 20)
            .build()
      );
      if (var20 > 1) {
         int var23 = this.left() + var22 + 6;
         Button var25 = Button.builder(Component.literal("<"), var1x -> this.flipTopics(-1)).bounds(var23, var21, 20, 20).build();
         Button var27 = Button.builder(Component.literal(">"), var1x -> this.flipTopics(1)).bounds(var23 + 60, var21, 20, 20).build();
         var25.active = this.topicPage > 0;
         var27.active = this.topicPage < var20 - 1;
         this.addRenderableWidget(var25);
         this.addRenderableWidget(var27);
         this.labelText(this.topicPage + 1 + " / " + var20, var21 + 5, var23 + 24);
      }

      boolean var24 = this.bodyBottom() - var21 > 34;
      if (var24) {
         this.label("gui.settledlands.cfg.buttons.hint", var21 + 26);
      }

      return var21 + 22 + (var24 ? 26 : 0);
   }

   private void initQuests() {
      int var1 = this.bodyTop() - this.scrollY;
      int var2 = this.blockStart("gui.settledlands.cfg.quests", var1);
      var1 = this.contentTop(var2);
      int var3 = this.right() - this.left();
      int var4 = NpcProfile.clamp((int)(var3 * 0.42), 84, 320);
      int var5 = this.left() + var4 + 6;
      int var6 = Math.max(70, this.right() - 26 - var5);
      this.labelText(Component.translatable("gui.settledlands.cfg.questtable.quest").getString(), var1, this.left());
      this.labelText(Component.translatable("gui.settledlands.field.depends").getString(), var1, var5);
      var1 += 12;
      byte var7 = 22;
      int var8 = var7 + 22 + 8;
      int var9 = this.rowsPerPage(var1, var8, var7);
      int var10 = Math.max(1, (this.profile.quests.size() + var9 - 1) / var9);
      this.questPage = NpcProfile.clamp(this.questPage, 0, var10 - 1);
      int var11 = Math.min(var9, this.profile.quests.size() - this.questPage * var9);
      if (this.profile.quests.isEmpty()) {
         this.labelText(Component.translatable("gui.settledlands.cfg.questnone").getString(), var1, this.left());
      }

      for (int var12 = 0; var12 < var11; var12++) {
         int var13 = this.questPage * var9 + var12;
         int var14 = var1 + var12 * var7;
         NpcProfile.Quest var15 = this.questAt(var13);
         String var16 = var15 != null && var15.title != null && !var15.title.isEmpty() ? var15.title : "—";
         this.addRenderableWidget(
            Button.builder(Component.literal(this.clip(var13 + 1 + ". " + var16, var4 - 8)), var2x -> this.openQuest(var13))
               .bounds(this.left(), var14, var4, 20)
               .build()
         );
         this.addRenderableWidget(
            NpcUi.dropdown(
               var5,
               var14,
               var6,
               this.chainNames(var13),
               this.chainIndex(var13),
               var2x -> this.questField(var13, "chainPrev", String.valueOf(NpcQuests.chainOptionPrev(var2x, var13)))
            )
         );
         this.addRenderableWidget(
            Button.builder(Component.literal("×"), var2x -> this.questOp("quest.del", var13, 0, "")).bounds(this.right() - 20, var14, 20, 20).build()
         );
      }

      int var19 = var1 + var9 * var7 + 2;
      int var20 = this.mid(200);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.cfg.addquest"), var1x -> this.questOp("quest.add", 0, 0, ""))
            .bounds(this.left(), var19, var20, 20)
            .build()
      );
      if (var10 > 1) {
         Button var21 = Button.builder(Component.literal("<"), var1x -> this.flipQuests(-1)).bounds(this.left() + var20 + 6, var19, 20, 20).build();
         Button var22 = Button.builder(Component.literal(">"), var1x -> this.flipQuests(1)).bounds(this.left() + var20 + 60, var19, 20, 20).build();
         var21.active = this.questPage > 0;
         var22.active = this.questPage < var10 - 1;
         this.addRenderableWidget(var21);
         this.addRenderableWidget(var22);
         this.labelText(this.questPage + 1 + " / " + var10, var19 + 5, this.left() + var20 + 30);
      }

      this.blockEnd(var2, var19 + 20);
   }

   private void openQuest(int var1) {
      if (this.questAt(var1) != null) {
         this.questIdx = var1;
         Minecraft.getInstance().setScreen(new NpcQuestScreen(this, this.entityId, this.profile, this.relations, var1));
      }
   }

   private List<Component> chainNames(int var1) {
      ArrayList var2 = new ArrayList();
      var2.add(Component.translatable("gui.settledlands.field.depends.none"));

      for (int var3 = 0; var3 < this.profile.quests.size(); var3++) {
         if (var3 != var1) {
            NpcProfile.Quest var4 = this.profile.quests.get(var3);
            String var5 = var4 != null && var4.title != null && !var4.title.isEmpty() ? var4.title : "—";
            var2.add(Component.translatable("gui.settledlands.field.depends.quest", new Object[]{var3 + 1, var5}));
         }
      }

      return var2;
   }

   private int chainIndex(int var1) {
      NpcProfile.Quest var2 = this.questAt(var1);
      int var3 = var2 == null ? -1 : var2.chainPrev;
      return NpcQuests.chainOption(var3, var1, this.profile.quests.size());
   }

   private NpcProfile.Quest questAt(int var1) {
      return var1 >= 0 && var1 < this.profile.quests.size() ? this.profile.quests.get(var1) : null;
   }

   void questField(int var1, String var2, String var3) {
      NpcProfile.Quest var4 = this.questAt(var1);
      if (var4 != null) {
         boolean var5 = false;
         switch (var2) {
            case "title":
               var4.title = var3;
               break;
            case "text":
               var4.text = var3;
               break;
            case "done":
               var4.done = var3;
               break;
            case "rewardRelation":
               var4.rewardRelation = NpcProfile.clamp(Integer.parseInt(var3), -100, 100);
               break;
            case "minRelation":
               var4.minRelation = NpcProfile.clamp(Integer.parseInt(var3), 0, 100);
               break;
            case "repeatable":
               var4.repeatable = "1".equals(var3);
               break;
            case "chainPrev":
               var4.chainPrev = Integer.parseInt(var3);
               break;
            case "chainShared":
               var4.chainShared = "1".equals(var3);
               break;
            case "kind":
               var4.kind = Integer.parseInt(var3) == 1 ? 1 : 0;
               var5 = true;
               break;
            case "pointsName":
               var4.pointsName = var3;
               break;
            case "pointsCount":
               var4.pointsCount = NpcProfile.clamp(Integer.parseInt(var3), 1, 1000000);
               break;
            case "pointsShared":
               var4.pointsShared = "1".equals(var3);
               break;
            case "pointsSpend":
               var4.pointsSpend = "1".equals(var3);
               break;
            default:
               return;
         }

         this.send("quest.field", var1, 0, 0, var2, var3);
         if (var5) {
            this.rebuildWidgets();
         }
      }
   }

   void questOp(String var1, int var2, int var3, String var4) {
      this.send(var1, var2, var3, 0, "", var4);
      if ("quest.add".equals(var1) || "quest.del".equals(var1)) {
         if ("quest.add".equals(var1)) {
            this.questIdx = Math.max(0, this.questIdx);
         }

         this.rebuildWidgets();
      }
   }

   private void flipQuests(int var1) {
      this.questPage += var1;
      this.rebuildWidgets();
   }

   void applyPickedQuest(int var1, int var2, ItemStack var3) {
      String var4 = NpcItems.idOf(var3);
      NpcProfile.Quest var5 = this.questAt(var1);
      if (!var4.isEmpty() && var5 != null) {
         ItemStack var6 = var2 == 0 ? var5.item : var5.reward;
         ItemStack var7 = NpcItems.stack(var4, var6.isEmpty() ? 1 : var6.getCount());
         if (var2 == 0) {
            var5.item = var7;
         } else {
            var5.reward = var7;
         }

         this.questOp("quest.item", var1, var2, var4);
      }
   }

   private List<Component> typeNames() {
      ArrayList var1 = new ArrayList(NpcProfile.TYPE_COUNT);

      for (int var2 = 0; var2 < NpcProfile.TYPE_COUNT; var2++) {
         var1.add(Component.translatable(NpcProfile.TYPE_KEYS[var2]));
      }

      return var1;
   }

   private void flipTopics(int var1) {
      this.topicPage += var1;
      this.rebuildWidgets();
   }

   private void openBranches(int var1) {
      this.topicIdx = var1;
      Minecraft var2 = Minecraft.getInstance();
      this.flushEscortSettings();
      NpcDialogueGraphScreen.open(this.entityId, this.profile, this, var1);
   }

   private void initShop() {
      int var1 = this.bodyTop() - this.scrollY;
      int var2 = this.blockStart("gui.settledlands.cfg.tabs", var1);
      var1 = this.contentTop(var2);
      var1 = this.placeChecks(
         var1,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.tab.dialogue"),
               this.profile.showDialogueTab,
               (var1x, var2x) -> this.field("showDialogueTab", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.tab.trade"), this.profile.showTradeTab, (var1x, var2x) -> this.field("showTradeTab", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.tab.quest"), this.profile.showQuestTab, (var1x, var2x) -> this.field("showQuestTab", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.tab.phrases"),
               this.profile.showPhrasesTab,
               (var1x, var2x) -> this.field("showPhrasesTab", var2x ? 1 : 0)
            )
         )
      );
      this.label("gui.settledlands.cfg.tabs.hint", var1);
      var1 += 12;
      this.labelText(
         Component.translatable(
               "gui.settledlands.cfg.summary",
               new Object[]{
                  NpcProfile.normalTopics(this.profile),
                  this.profile.welcomeLines.size(),
                  this.profile.quests.size(),
                  Math.max(0, this.profile.topics.size() - NpcProfile.normalTopics(this.profile))
               }
            )
            .getString(),
         var1,
         this.left()
      );
      var1 += 12;
      if (!this.profile.showDialogueTab && NpcProfile.normalTopics(this.profile) > 0) {
         this.labelText(Component.translatable("gui.settledlands.cfg.tabs.warn").getString(), var1, this.left());
         var1 += 12;
      }

      this.rowLabel("gui.settledlands.cfg.defaulttab", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            this.mid(200),
            List.of(
               Component.translatable("gui.settledlands.tab.dialogue"),
               Component.translatable("gui.settledlands.tab.trade"),
               Component.translatable("gui.settledlands.tab.quest"),
               Component.translatable("gui.settledlands.tab.phrases")
            ),
            this.profile.defaultTab,
            var1x -> this.field("defaultTab", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.blockEnd(var2, var1);
      int var3 = this.blockStart("gui.settledlands.cfg.offers", var1);
      int var4 = this.initOfferRows(this.contentTop(var3));
      this.blockEnd(var3, var4);
   }

   private int initOfferRows(int var1) {
      int var2 = this.right() - this.left();
      boolean var3 = var2 < 620;
      int var4 = var3 ? 68 : this.rowH();
      int var5 = this.rowH() + 56;
      int var6 = this.rowsPerPage(var1, var5, var4);
      int var7 = Math.max(1, (this.profile.offers.size() + var6 - 1) / var6);
      this.offerPage = Math.max(0, Math.min(this.offerPage, var7 - 1));
      int var8 = Math.min(var6, this.profile.offers.size() - this.offerPage * var6);
      int var9 = var3 ? Math.max(70, var2 - 64) : NpcProfile.clamp((int)(var2 * 0.2), 80, 150);

      for (int var10 = 0; var10 < var8; var10++) {
         int var11 = this.offerPage * var6 + var10;
         NpcProfile.Offer var12 = this.profile.offers.get(var11);
         int var13 = var1 + var10 * var4;
         if (var11 == this.offerIdx) {
            this.selectedRows.add(new int[]{this.left(), var13 - 3, this.right(), var13 + var4 - 5});
         }

         if (var3) {
            this.addRenderableWidget(NpcUi.itemButton(this.left(), var13, var9, var12.stack, () -> this.openItemPicker(var11, 0)));
            EditBox var14 = this.countBox(this.left() + var9 + 6, var13, var12.stack.getCount(), var11, 0);
            this.addRenderableWidget(
               Button.builder(Component.literal("×"), var2x -> this.offerOp("offer.del", var11, 0, "")).bounds(this.right() - 20, var13, 20, 20).build()
            );
            int var15 = var13 + 22;
            this.addRenderableWidget(NpcUi.itemButton(this.left(), var15, var9, var12.price, () -> this.openItemPicker(var11, 1)));
            EditBox var16 = this.countBox(this.left() + var9 + 6, var15, var12.price.getCount(), var11, 1);
            int var17 = var13 + 44;
            EditBox var18 = this.box(this.left(), var17, Math.max(60, var2 - 26), "gui.settledlands.hint.desc", var12.description, 240, null);
            var18.setResponder(var2x -> this.offerField(var11, "desc", var2x));
            this.offerDescs.add(var18);
            this.addRenderableWidget(var18);
         } else {
            this.addRenderableWidget(NpcUi.itemButton(this.left(), var13, var9, var12.stack, () -> this.openItemPicker(var11, 0)));
            int var26 = this.left() + var9 + 6;
            this.addRenderableWidget(NpcUi.itemButton(var26, var13, var9, var12.price, () -> this.openItemPicker(var11, 1)));
            EditBox var28 = this.countBox(var26 + var9 + 6, var13, var12.stack.getCount(), var11, 0);
            EditBox var29 = this.countBox(var26 + var9 + 46, var13, var12.price.getCount(), var11, 1);
            int var30 = var26 + var9 + 86;
            int var31 = Math.max(60, this.right() - 24 - var30);
            EditBox var19 = this.box(var30, var13, var31, "gui.settledlands.hint.desc", var12.description, 240, null);
            var19.setResponder(var2x -> this.offerField(var11, "desc", var2x));
            this.offerDescs.add(var19);
            this.addRenderableWidget(var19);
            this.addRenderableWidget(
               Button.builder(Component.literal("×"), var2x -> this.offerOp("offer.del", var11, 0, "")).bounds(this.right() - 20, var13, 20, 20).build()
            );
         }
      }

      int var20 = var1 + var6 * var4 + 2;
      int var21 = this.mid(200);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.cfg.addoffer"), var1x -> this.offerOp("offer.add", 0, 0, ""))
            .bounds(this.left(), var20, var21, 20)
            .build()
      );
      if (var7 > 1) {
         int var22 = this.left() + var21 + 6;
         Button var25 = Button.builder(Component.literal("<"), var1x -> this.flipOffers(-1)).bounds(var22, var20, 20, 20).build();
         Button var27 = Button.builder(Component.literal(">"), var1x -> this.flipOffers(1)).bounds(var22 + 60, var20, 20, 20).build();
         var25.active = this.offerPage > 0;
         var27.active = this.offerPage < var7 - 1;
         this.addRenderableWidget(var25);
         this.addRenderableWidget(var27);
         this.labelText(this.offerPage + 1 + " / " + var7, var20 + 5, var22 + 24);
      }

      int var23 = var20 + 26;
      this.rowLabel("gui.settledlands.cfg.maxperpurchase", var23);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var23,
            this.mid(300),
            1,
            8,
            this.profile.maxPerPurchase,
            var0 -> Component.translatable("gui.settledlands.hint.maxper", new Object[]{var0}),
            var1x -> this.field("maxPerPurchase", var1x)
         )
      );
      var23 += this.rowH();
      this.rowLabel("gui.settledlands.cfg.discount", var23);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var23,
            this.mid(300),
            0,
            90,
            this.profile.discount,
            var0 -> Component.translatable("gui.settledlands.hint.discount", new Object[]{var0}),
            var1x -> this.field("discount", var1x)
         )
      );
      return var23 + this.rowH();
   }

   private EditBox countBox(int var1, int var2, int var3, int var4, int var5) {
      EditBox var6 = this.box(var1, var2, 34, "", String.valueOf(var3), 2, null);
      var6.setFilter(this::digits);
      var6.setResponder(var3x -> this.offerOp("offer.count", var4, var5, var3x));
      if (var5 == 0) {
         this.offerCounts.add(var6);
      } else {
         this.offerPrices.add(var6);
      }

      this.addRenderableWidget(var6);
      return var6;
   }

   private void openItemPicker(int var1, int var2) {
      if (var1 < this.profile.offers.size()) {
         NpcProfile.Offer var3 = this.profile.offers.get(var1);
         this.minecraft.setScreen(new NpcItemPickerScreen(this, var1, var2, var2 == 0 ? var3.stack : var3.price));
      }
   }

   void applyPickedItem(int var1, int var2, ItemStack var3) {
      String var4 = NpcItems.idOf(var3);
      if (!var4.isEmpty() && var1 < this.profile.offers.size()) {
         NpcProfile.Offer var5 = this.profile.offers.get(var1);
         ItemStack var6 = var2 == 0 ? var5.stack : var5.price;
         ItemStack var7 = NpcItems.stack(var4, var6.isEmpty() ? 1 : var6.getCount());
         if (var2 == 0) {
            var5.stack = var7;
         } else {
            var5.price = var7;
         }

         this.offerOp("offer.item", var1, var2, var4);
      }
   }

   private void flipOffers(int var1) {
      this.offerPage += var1;
      this.rebuildWidgets();
   }

   private void initEscort() {
      int y = this.bodyTop() - this.scrollY;
      int block = this.blockStart("gui.settledlands.escort.section", y);
      y = this.contentTop(block);
      this.rowLabel("gui.settledlands.escort.code", y);
      EditBox code = this.box(this.fieldX(), y, this.fieldW(), "", this.profile.escortCode, EscortRules.MAX_CODE, null);
      code.setFilter(value -> value.isEmpty() || EscortRules.validCode(value));
      code.setTooltip(Tooltip.create(Component.translatable("gui.settledlands.escort.code.hint")));
      code.setResponder(value -> { this.profile.escortCode = value; this.queueEscortSettings(); });
      this.addRenderableWidget(code);
      y += this.rowH();
      this.rowLabel("gui.settledlands.escort.coordinates", y);
      EditBox coordinates = this.box(this.fieldX(), y, this.fieldW(), "", this.profile.escortCoordinates, NpcProfile.MAX_MOBILITY_COORDINATES, null);
      coordinates.setTooltip(Tooltip.create(Component.translatable("gui.settledlands.escort.coordinates.hint")));
      coordinates.setResponder(value -> { this.profile.escortCoordinates = value; this.queueEscortSettings(); });
      this.addRenderableWidget(coordinates);
      y += this.rowH() + 6;
      int buttonWidth = Math.max(60, this.right() - this.left());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.escort.edit.travel"), button -> this.openEscortDialogue(false))
         .bounds(this.left(), y, buttonWidth, 20).build());
      y += this.rowH();
      this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.escort.edit.arrival"), button -> this.openEscortDialogue(true))
         .bounds(this.left(), y, buttonWidth, 20).build());
      y += this.rowH();
      this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.escort.cancel"), button -> {
         this.flushEscortSettings();
         this.send("escort.cancel", 0, 0, 0, "", "");
      }).bounds(this.left(), y, buttonWidth, 20).build());
      y += this.rowH();
      this.blockEnd(block, y);
   }

   private void openEscortDialogue(boolean arrival) {
      this.flushEscortSettings();
      GuideDialogueScreen.openEdit(this.entityId, this.profile.copy(), this, arrival ? -3 : -2);
   }

   private void queueEscortSettings() {
      this.escortSettingsDirty = true;
      this.escortSettingsAt = System.currentTimeMillis() + 350L;
   }

   private void flushEscortSettings() {
      if (!this.escortSettingsDirty) return;
      this.escortSettingsDirty = false;
      this.send("escort.settings", 0, 0, 0, this.profile.escortCode, this.profile.escortCoordinates);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.escortSettingsDirty && System.currentTimeMillis() >= this.escortSettingsAt) this.flushEscortSettings();
   }

   private void initMobility() {
      int y = this.bodyTop() - this.scrollY;
      int mobilityBlock = this.blockStart("gui.settledlands.mobility.section", y);
      y = this.contentTop(mobilityBlock);
      y = this.placeChecks(
         y,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.mobility.idle"),
               this.profile.idleWalking,
               (button, checked) -> this.field("idleWalking", checked ? 1 : 0)
            )
         )
      );

      this.labelText(Component.translatable("gui.settledlands.mobility.group").getString(), y + 5, this.left());
      int dropdownX = this.left() + this.labelW() + 8;
      int dropdownWidth = Math.max(100, this.right() - dropdownX);
      ArrayList<Component> groupLabels = new ArrayList<>();
      groupLabels.add(Component.translatable("gui.settledlands.mobility.no_group"));
      int selected = 0;
      for (int i = 0; i < this.mobilityGroups.size(); i++) {
         NpcConfigScreen.MobilityGroupOption group = this.mobilityGroups.get(i);
         groupLabels.add(Component.literal(group.name()));
         if (group.id().equals(this.profile.mobilityGroupId)) {
            selected = i + 1;
         }
      }
      this.addRenderableWidget(NpcUi.dropdown(dropdownX, y, dropdownWidth, groupLabels, selected, index -> {
         if (index == 0) {
            this.selectMobilityGroup("");
         } else if (index - 1 < this.mobilityGroups.size()) {
            this.selectMobilityGroup(this.mobilityGroups.get(index - 1).id());
         }
      }));
      y += this.rowH() + 4;

      NpcConfigScreen.MobilityGroupOption selectedGroup = this.groupOption(this.profile.mobilityGroupId);
      String groupName = this.profile.mobilityGroupName;
      if ((groupName == null || groupName.isBlank()) && selectedGroup != null) {
         groupName = selectedGroup.name();
      }
      int pointCount = this.profile.mobilityPoints.size();
      String summary = groupName == null || groupName.isBlank()
         ? Component.translatable("gui.settledlands.mobility.no_group_selected").getString()
         : Component.translatable("gui.settledlands.mobility.group_summary", groupName, pointCount).getString();
      this.labelText(this.clip(summary, this.right() - this.left()), y + 2, this.left());
      y += 22;
      this.blockEnd(mobilityBlock, y);

      int scheduleBlock = this.blockStart("gui.settledlands.mobility.schedule.title", y);
      y = this.contentTop(scheduleBlock);

      int contentWidth = this.right() - this.left();
      int timeWidth = Math.max(68, Math.min(92, contentWidth / 8));
      int deleteWidth = 22;
      int deleteX = this.right() - deleteWidth;
      int groupX = this.left() + timeWidth + 6;
      int groupWidth = Math.max(72, deleteX - groupX - 6);
      ArrayList<Component> scheduleGroupLabels = new ArrayList<>();
      for (NpcConfigScreen.MobilityGroupOption group : this.mobilityGroups) {
         scheduleGroupLabels.add(Component.literal(group.name()));
      }
      if (!this.profile.mobilitySchedule.isEmpty()) {
         this.labelText(Component.translatable("gui.settledlands.mobility.schedule.time").getString(), y + 5, this.left());
         this.labelText(Component.translatable("gui.settledlands.mobility.schedule.group").getString(), y + 5, groupX);
         y += this.rowH();
      }
      for (int row = 0; row < this.profile.mobilitySchedule.size(); row++) {
         int scheduleIndex = row;
         NpcProfile.MobilityScheduleEntry entry = this.profile.mobilitySchedule.get(row);
         NpcConfigScreen.ScheduleTimeBox timeBox = new NpcConfigScreen.ScheduleTimeBox(
            this.font, this.left(), y, timeWidth, 20, Component.empty()
         );
         timeBox.setMaxLength(4);
         timeBox.setValue(String.valueOf(entry.second));
         timeBox.setFilter(value -> this.validMobilityScheduleTime(scheduleIndex, value));
         timeBox.setResponder(value -> this.mobilityScheduleTimeChanged(scheduleIndex, value));
         timeBox.setTooltip(Tooltip.create(Component.translatable("gui.settledlands.mobility.schedule.time_hint")));
         this.addRenderableWidget(timeBox);
         this.mobilityScheduleTimes.add(timeBox);

         int selectedScheduleGroup = 0;
         for (int i = 0; i < this.mobilityGroups.size(); i++) {
            if (this.mobilityGroups.get(i).id().equals(entry.groupId)) {
               selectedScheduleGroup = i;
               break;
            }
         }
         NpcUi.NpcDropdown scheduleGroup = NpcUi.dropdown(groupX, y, groupWidth, scheduleGroupLabels, selectedScheduleGroup, selectedIndex -> {
            if (selectedIndex >= 0 && selectedIndex < this.mobilityGroups.size()
               && scheduleIndex < this.profile.mobilitySchedule.size()) {
               String newGroupId = this.mobilityGroups.get(selectedIndex).id();
               this.profile.mobilitySchedule.get(scheduleIndex).groupId = newGroupId;
               this.send("mobility.schedule.group", scheduleIndex, 0, 0, "", newGroupId);
            }
         });
         scheduleGroup.active = !this.mobilityGroups.isEmpty();
         this.addRenderableWidget(scheduleGroup);
         Button removeSchedule = Button.builder(Component.literal("×"), button -> {
            if (scheduleIndex < this.profile.mobilitySchedule.size()) {
               this.profile.mobilitySchedule.remove(scheduleIndex);
               this.send("mobility.schedule.remove", scheduleIndex, 0, 0, "", "");
               this.rebuildWidgets();
            }
         }).bounds(deleteX, y, deleteWidth, 20).build();
         removeSchedule.setTooltip(Tooltip.create(Component.translatable("gui.settledlands.mobility.schedule.remove")));
         this.addRenderableWidget(removeSchedule);
         y += this.rowH();
      }
      Button addSchedule = Button.builder(Component.translatable("gui.settledlands.mobility.schedule.add"),
         button -> this.send("mobility.schedule.add", 0, 0, 0, "", ""))
         .bounds(this.left(), y, Math.min(180, contentWidth), 20).build();
      addSchedule.active = !this.mobilityGroups.isEmpty()
         && this.profile.mobilitySchedule.size() < NpcProfile.MAX_MOBILITY_SCHEDULE_ENTRIES;
      this.addRenderableWidget(addSchedule);
      y += this.rowH() + 4;
      this.blockEnd(scheduleBlock, y);
   }

   private boolean validMobilityScheduleTime(int row, String value) {
      if (value == null || !value.matches("[0-9]{1,4}")) {
         return false;
      }
      int second;
      try {
         second = Integer.parseInt(value);
      } catch (NumberFormatException ignored) {
         return false;
      }
      if (second < 0 || second >= NpcProfile.MOBILITY_DAY_SECONDS) {
         return false;
      }
      for (int i = 0; i < this.profile.mobilitySchedule.size(); i++) {
         if (i != row && this.profile.mobilitySchedule.get(i).second == second) {
            return false;
         }
      }
      return true;
   }

   private void mobilityScheduleTimeChanged(int row, String value) {
      if (this.updatingMobilityScheduleTimes || value == null || value.isEmpty() || row < 0 || row >= this.profile.mobilitySchedule.size()
         || !this.validMobilityScheduleTime(row, value)) {
         return;
      }
      int second;
      try {
         second = Integer.parseInt(value);
      } catch (NumberFormatException ignored) {
         return;
      }
      this.profile.mobilitySchedule.get(row).second = second;
      this.send("mobility.schedule.time", row, 0, 0, "", String.valueOf(second));
   }

   private NpcConfigScreen.MobilityGroupOption groupOption(String id) {
      if (id == null || id.isBlank()) {
         return null;
      }
      for (NpcConfigScreen.MobilityGroupOption group : this.mobilityGroups) {
         if (group.id().equals(id)) {
            return group;
         }
      }
      return null;
   }

   private void selectMobilityGroup(String id) {
      this.field("mobilityGroupId", id);
      this.rebuildWidgets();
   }

   private void initFrame() {
      int var1 = this.bodyTop() - this.scrollY;
      int var2 = this.mid(300);
      int var3 = this.blockStart("gui.settledlands.cfg.frame", var1);
      var1 = this.contentTop(var3);
      this.rowLabel("gui.settledlands.field.zoom", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            70,
            150,
            this.profile.zoom,
            var0 -> Component.translatable("gui.settledlands.hint.zoom", new Object[]{var0}),
            var1x -> this.field("zoom", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.offsetx", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            -40,
            40,
            this.profile.offsetX,
            var0 -> Component.translatable("gui.settledlands.hint.offsetx", new Object[]{var0}),
            var1x -> this.field("offsetX", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.offsety", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            -40,
            40,
            this.profile.offsetY,
            var0 -> Component.translatable("gui.settledlands.hint.offsety", new Object[]{var0}),
            var1x -> this.field("offsetY", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.placeChecks(
         var1,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.camface"), this.profile.cameraOnFace, (var1x, var2x) -> this.field("cameraOnFace", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.blocklook"),
               this.profile.blockMouseLook,
               (var1x, var2x) -> this.field("blockMouseLook", var2x ? 1 : 0)
            )
         )
      );
      var1 = this.blockEnd(var3, var1);
      int var4 = this.blockStart("gui.settledlands.block.screen", var1);
      var1 = this.contentTop(var4);
      this.rowLabel("gui.settledlands.field.dim", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            0,
            80,
            this.profile.dim,
            var0 -> Component.translatable("gui.settledlands.hint.dim", new Object[]{var0}),
            var1x -> this.field("dim", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.blur", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            var2,
            List.of(
               Component.translatable("gui.settledlands.blur.0"),
               Component.translatable("gui.settledlands.blur.1"),
               Component.translatable("gui.settledlands.blur.2")
            ),
            this.profile.blur,
            var1x -> this.field("blur", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.placeChecks(
         var1,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.mutehud"), this.profile.muteHud, (var1x, var2x) -> this.field("muteHud", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.vignette"), this.profile.vignette, (var1x, var2x) -> this.field("vignette", var2x ? 1 : 0)
            )
         )
      );
      var1 = this.blockEnd(var4, var1);
      int var5 = this.blockStart("gui.settledlands.cfg.access", var1);
      var1 = this.contentTop(var5);
      this.rowLabel("gui.settledlands.field.radius", var1);
      this.addRenderableWidget(
         NpcUi.slider(
            this.fieldX(),
            var1,
            var2,
            2,
            16,
            this.profile.radius,
            var0 -> Component.translatable("gui.settledlands.hint.radius", new Object[]{var0}),
            var1x -> this.field("radius", var1x)
         )
      );
      var1 += this.rowH();
      this.rowLabel("gui.settledlands.field.exitkey", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            var2,
            List.of(Component.translatable("gui.settledlands.exitkey.0"), Component.translatable("gui.settledlands.exitkey.1")),
            this.profile.exitKey,
            var1x -> this.field("exitKey", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.placeChecks(
         var1,
         List.of(
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.blockmove"),
               this.profile.blockMovement,
               (var1x, var2x) -> this.field("blockMovement", var2x ? 1 : 0)
            ),
            new NpcConfigScreen.Check(
               Component.translatable("gui.settledlands.field.pause"), this.profile.pauseWorld, (var1x, var2x) -> this.field("pauseWorld", var2x ? 1 : 0)
            )
         )
      );
      this.rowLabel("gui.settledlands.field.exitaction", var1);
      this.addRenderableWidget(
         NpcUi.dropdown(
            this.fieldX(),
            var1,
            var2,
            List.of(Component.translatable("gui.settledlands.exitaction.0"), Component.translatable("gui.settledlands.exitaction.1")),
            this.profile.exitAction,
            var1x -> this.field("exitAction", var1x)
         )
      );
      var1 += this.rowH();
      var1 = this.blockEnd(var5, var1);
      int var6 = this.blockStart("gui.settledlands.cfg.relations", var1);
      int var7 = this.initRelationEditor(this.contentTop(var6));
      this.blockEnd(var6, var7);
   }

   private int initRelationEditor(int var1) {
      int var2 = this.right() - this.left();
      byte var3 = 88;
      int var4 = NpcProfile.clamp((int)(var2 * 0.22), 80, 150);
      boolean var5 = var2 < var4 + 6 + 90 + 8 + var3 * 2 + 6;
      this.relBox = this.box(this.left(), var1, var4, "gui.settledlands.hint.relname", this.relName, 32, null);
      this.addRenderableWidget(this.relBox);
      this.relBox.setResponder(var1x -> this.relName = var1x);
      int var6 = this.left() + var4 + 6;
      int var7 = this.right() - var3 * 2 - 6;
      int var8 = var5 ? Math.max(70, this.right() - var6) : Math.max(70, var7 - 8 - var6);
      this.addRenderableWidget(
         NpcUi.slider(var6, var1, var8, 0, 100, this.relValue, var0 -> Component.literal(String.valueOf(var0)), var1x -> this.relValue = var1x)
      );
      int var9 = var5 ? var1 + this.rowH() : var1;
      this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.cfg.relset"), var1x -> {
         NpcNet.sendToServer(new NpcNet.RelSet(this.entityId, this.relName, this.relValue));
         this.status("gui.settledlands.cfg.relsent");
      }).bounds(var7, var9, var3, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.cfg.relreset"), var1x -> {
         NpcNet.sendToServer(new NpcNet.RelSet(this.entityId, this.relName, -1));
         this.status("gui.settledlands.cfg.relsent");
      }).bounds(var7 + var3 + 6, var9, var3, 20).build());
      var1 = (var5 ? var9 : var1) + this.rowH();
      this.relLabelY = var1;
      int var10 = this.bodyBottom() - var1 - 22;
      if (var10 < 30) {
         this.relPerPage = 0;
         return var1;
      } else {
         byte var11 = 11;
         this.relPerPage = NpcProfile.clamp(var10 / var11, 1, 10);
         int var12 = Math.max(1, (this.relations.size() + this.relPerPage - 1) / this.relPerPage);
         this.relPage = Math.max(0, Math.min(this.relPage, var12 - 1));
         if (var12 > 1) {
            int var13 = var1 + this.relPerPage * var11 + 2;
            int var14 = this.left() + 120;
            Button var15 = Button.builder(Component.literal("<"), var1x -> {
               this.relPage = Math.max(0, this.relPage - 1);
               this.rebuildWidgets();
            }).bounds(var14, var13, 20, 20).build();
            Button var16 = Button.builder(Component.literal(">"), var2x -> {
               this.relPage = Math.min(var12 - 1, this.relPage + 1);
               this.rebuildWidgets();
            }).bounds(var14 + 60, var13, 20, 20).build();
            var15.active = this.relPage > 0;
            var16.active = this.relPage < var12 - 1;
            this.addRenderableWidget(var15);
            this.addRenderableWidget(var16);
            this.labelText(this.relPage + 1 + " / " + var12, var13 + 5, var14 + 24);
            return Math.max(var1 + this.relPerPage * var11, var13 + 22);
         } else {
            return var1 + this.relPerPage * var11;
         }
      }
   }

   private void label(String var1, int var2) {
      this.label(var1, var2, this.left());
   }

   private void label(String var1, int var2, int var3) {
      this.labels.add(new Object[]{Component.translatable(var1).getString(), var3, var2});
   }

   private void labelText(String var1, int var2, int var3) {
      this.labels.add(new Object[]{var1, var3, var2});
   }

   private int blockStart(String var1, int var2) {
      return this.blockStart(Component.translatable(var1), var2);
   }

   private int blockStart(Component var1, int var2) {
      this.blocks.add(new Object[]{var1.getString(), var2, var2 + 20});
      return this.blocks.size() - 1;
   }

   private int contentTop(int var1) {
      return (Integer)this.blocks.get(var1)[1] + 20;
   }

   private int blockEnd(int var1, int var2) {
      this.blocks.get(var1)[2] = var2 + 5;
      return var2 + 11;
   }

   private EditBox box(int var1, int var2, int var3, String var4, String var5, int var6, String var7) {
      EditBox var8 = new EditBox(this.font, var1, var2, var3, 20, Component.empty());
      var8.setMaxLength(var6);
      if (var4 != null && !var4.isEmpty()) {
         var8.setHint(Component.translatable(var4).withStyle(ChatFormatting.DARK_GRAY));
      }

      var8.setValue(var5 == null ? "" : var5);
      if (var7 != null) {
         var8.setResponder(var2x -> this.field(var7, var2x));
      }

      return var8;
   }

   private boolean digits(String var1) {
      return this.digits(var1, 4);
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

   private List<Component> voiceOptions() {
      ArrayList var1 = new ArrayList();
      var1.add(Component.translatable("gui.settledlands.voice.standard"));

      for (String var3 : NpcVoice.list()) {
         var1.add(Component.literal(var3));
      }

      boolean var5 = this.profile.voice.isEmpty();

      for (String var4 : NpcVoice.list()) {
         if (var4.equals(this.profile.voice)) {
            var5 = true;
            break;
         }
      }

      if (!var5) {
         var1.add(Component.literal(this.profile.voice));
      }

      return var1;
   }

   private int voiceIndex() {
      if (this.profile.voice.isEmpty()) {
         return 0;
      } else {
         List var1 = NpcVoice.list();
         int var2 = var1.indexOf(this.profile.voice);
         return var2 >= 0 ? var2 + 1 : var1.size() + 1;
      }
   }

   private String voiceName(int var1) {
      if (var1 <= 0) {
         return "";
      } else {
         List var2 = NpcVoice.list();
         return var1 - 1 < var2.size() ? (String)var2.get(var1 - 1) : this.profile.voice;
      }
   }

   private void field(String var1, int var2) {
      this.field(var1, String.valueOf(var2));
   }

   private void field(String var1, String var2) {
      switch (var1) {
         case "name":
            this.profile.name = var2;
            break;
         case "identifier":
            this.profile.identifier = var2;
            break;
         case "title":
            this.profile.title = var2;
            break;
         case "texture":
            this.profile.texture = var2;
            break;
         case "focusNpcUuid":
            this.profile.focusNpcUuid = var2;
            break;
         case "welcome":
            this.profile.welcome = var2;
            break;
         case "scale":
            try {
               this.profile.scale = Integer.parseInt(var2);
               if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(this.entityId) instanceof GuideNpc gNpc) {
                  gNpc.setClientScale(this.profile.scale);
               }
            } catch (Throwable ignored) {}
            break;
         case "pose":
            this.profile.pose = Integer.parseInt(var2);
            break;
         case "nameTag":
            this.profile.nameTag = "1".equals(var2);
            break;
         case "lookAt":
            this.profile.lookAtPlayer = "1".equals(var2);
            break;
         case "idleWalking":
            this.profile.idleWalking = "1".equals(var2);
            break;
         case "mobilityGroupId":
            this.profile.mobilityGroupId = var2;
            NpcConfigScreen.MobilityGroupOption mobilityGroup = this.groupOption(var2);
            this.profile.mobilityGroupName = mobilityGroup == null ? "" : mobilityGroup.name();
            break;
         case "default":
            this.profile.defaultRelation = Integer.parseInt(var2);
            break;
         case "style":
            this.profile.style = Integer.parseInt(var2);
            break;
         case "textColor":
            this.profile.textColor = Integer.parseInt(var2);
            break;
         case "nameColor":
            this.profile.nameColor = Integer.parseInt(var2);
            break;
         case "typingSpeed":
            this.profile.typingSpeed = Integer.parseInt(var2);
            break;
         case "voice":
            this.profile.voice = var2;
            break;
         case "voicePitch":
            this.profile.voicePitch = Integer.parseInt(var2);
            break;
         case "showSpeaker":
            this.profile.showSpeaker = "1".equals(var2);
            break;
         case "keyHints":
            this.profile.keyHints = "1".equals(var2);
            break;
         case "answerNumbers":
            this.profile.answerNumbers = "1".equals(var2);
            break;
         case "showDialogueTab":
            this.profile.showDialogueTab = "1".equals(var2);
            break;
         case "showTradeTab":
            this.profile.showTradeTab = "1".equals(var2);
            break;
         case "showQuestTab":
            this.profile.showQuestTab = "1".equals(var2);
            break;
         case "showPhrasesTab":
            this.profile.showPhrasesTab = "1".equals(var2);
            break;
         case "defaultTab":
            this.profile.defaultTab = Integer.parseInt(var2);
            break;
         case "maxPerPurchase":
            this.profile.maxPerPurchase = Integer.parseInt(var2);
            break;
         case "discount":
            this.profile.discount = Integer.parseInt(var2);
            break;
         case "zoom":
            this.profile.zoom = Integer.parseInt(var2);
            break;
         case "offsetX":
            this.profile.offsetX = Integer.parseInt(var2);
            break;
         case "offsetY":
            this.profile.offsetY = Integer.parseInt(var2);
            break;
         case "cameraOnFace":
            this.profile.cameraOnFace = "1".equals(var2);
            break;
         case "blockMouseLook":
            this.profile.blockMouseLook = "1".equals(var2);
            break;
         case "dim":
            this.profile.dim = Integer.parseInt(var2);
            break;
         case "blur":
            this.profile.blur = Integer.parseInt(var2);
            break;
         case "muteHud":
            this.profile.muteHud = "1".equals(var2);
            break;
         case "vignette":
            this.profile.vignette = "1".equals(var2);
            break;
         case "radius":
            this.profile.radius = Integer.parseInt(var2);
            break;
         case "exitKey":
            this.profile.exitKey = Integer.parseInt(var2);
            break;
         case "blockMovement":
            this.profile.blockMovement = "1".equals(var2);
            break;
         case "pauseWorld":
            this.profile.pauseWorld = "1".equals(var2);
            break;
         case "exitAction":
            this.profile.exitAction = Integer.parseInt(var2);
            break;
         default:
            if (var1.startsWith("rep") && var1.length() == 4) {
               int var5 = var1.charAt(3) - '0';

               while (this.profile.reputationNames.size() < 5) {
                  this.profile.reputationNames.add("");
               }

               if (var5 >= 0 && var5 < 5) {
                  this.profile.reputationNames.set(var5, var2);
               }
            }
      }

      this.send("field", 0, 0, 0, var1, var2);
   }

   private void topicField(int var1, String var2, String var3) {
      NpcProfile.Topic var4 = var1 < this.profile.topics.size() ? this.profile.topics.get(var1) : null;
      if (var4 != null) {
         switch (var2) {
            case "title":
               var4.title = var3;
               break;
            case "kind":
               var4.kind = var3;
               break;
            case "type":
               var4.type = NpcProfile.clamp(Integer.parseInt(var3), 0, NpcProfile.TYPE_COUNT - 1);
         }
      }

      this.send("topic.field", var1, 0, 0, var2, var3);
   }

   private void offerField(int var1, String var2, String var3) {
      if (var1 < this.profile.offers.size()) {
         this.profile.offers.get(var1).description = var3;
      }

      this.send("offer.field", var1, 0, 0, var2, var3);
   }

   private void topicOp(String var1, int var2) {
      this.send(var1, var2, 0, 0, "", "");
      this.topicIdx = Math.max(0, Math.min(this.topicIdx, Math.max(0, this.profile.topics.size() - 1)));
      this.questIdx = Math.max(0, Math.min(this.questIdx, Math.max(0, this.profile.quests.size() - 1)));
      this.rebuildWidgets();
   }

   private void offerOp(String var1, int var2, int var3, String var4) {
      this.send(var1, var2, var3, 0, "", var4);
      if ("offer.add".equals(var1) || "offer.del".equals(var1)) {
         this.rebuildWidgets();
      }
   }

   private void send(String var1, int var2, int var3, int var4, String var5, String var6) {
      NpcNet.sendToServer(new NpcNet.CfgEdit(this.entityId, var1, var2, var3, var4, var5, var6));
   }

   private void saveAll() {
      Provider var1 = registries(Minecraft.getInstance());
      NpcNet.sendToServer(new NpcNet.CfgSave(this.entityId, this.profile.save(var1)));
      this.status("gui.settledlands.cfg.saved");
   }

   public boolean mouseClicked(double var1, double var3, int var5) {
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

   public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
      return var5 == 0 && NpcUi.dragSlider(var1) ? true : super.mouseDragged(var1, var3, var5, var6, var8);
   }

   public boolean mouseReleased(double var1, double var3, int var5) {
      NpcUi.endSliderDrag();
      return super.mouseReleased(var1, var3, var5);
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      if (this.open != null && this.open.popupScrolled(var1, var3, (int)var7)) {
         return true;
      } else if (this.tab == 2 && var7 != 0.0 && this.stepCount(var1, var3, var7 > 0.0 ? 1 : -1)) {
         return true;
      } else {
         return var7 != 0.0 && this.scrollBy(var7 > 0.0 ? -14 : 14) ? true : super.mouseScrolled(var1, var3, var5, var7);
      }
   }

   private boolean stepCount(double var1, double var3, int var5) {
      for (int var6 = 0; var6 < this.offerCounts.size(); var6++) {
         EditBox var7 = null;
         if (this.offerCounts.get(var6).isMouseOver(var1, var3)) {
            var7 = this.offerCounts.get(var6);
         } else if (var6 < this.offerPrices.size() && this.offerPrices.get(var6).isMouseOver(var1, var3)) {
            var7 = this.offerPrices.get(var6);
         }

         if (var7 != null) {
            int var8;
            try {
               var8 = Integer.parseInt(var7.getValue());
            } catch (NumberFormatException var10) {
               var8 = 1;
            }

            var7.setValue(String.valueOf(NpcProfile.clamp(var8 + var5, 1, 64)));
            return true;
         }
      }

      return false;
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (this.open != null && var1 == 256) {
         this.open.closePopup();
         this.open = null;
         return true;
      } else if (var1 == 83 && (var3 & 2) != 0) {
         this.saveAll();
         return true;
      } else if (var1 == 294) {
         this.send("test", 0, 0, 0, "", "");
         this.onClose();
         return true;
      } else if (var1 == 295) {
         this.openDialogue();
         return true;
      } else if (var1 == 267 && this.scrollBy(this.bodyHeight() / 2)) {
         return true;
      } else if (var1 == 266 && this.scrollBy(-this.bodyHeight() / 2)) {
         return true;
      } else if (var1 == 268 && this.scrollBy(-this.scrollY)) {
         return true;
      } else {
         return var1 == 269 && this.scrollBy(this.scrollMax() - this.scrollY) ? true : super.keyPressed(var1, var2, var3);
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
         this.font, this.clip(Component.translatable("gui.settledlands.cfg.title").getString(), this.winW() - 24), var5 + this.winW() / 2, var6 + 7, -11702
      );
      var1.drawCenteredString(
         this.font,
         this.clip(Component.translatable("gui.settledlands.cfg.subtitle").getString(), this.winW() - 24),
         var5 + this.winW() / 2,
         var6 + 18,
         -5723992
      );

      for (int[] var10 : this.selectedRows) {
         var1.fill(var10[0], var10[1], var10[2], var10[3], 822083583);
      }

      for (Object[] var19 : this.blocks) {
         int var11 = (Integer)var19[1];
         int var12 = (Integer)var19[2];
         int var13 = Math.max(var11, this.bodyTop());
         int var14 = Math.min(var12, this.bodyBottom());
         if (var14 > var13) {
            int var15 = this.panelRight();
            var1.fill(this.left() - 6, var13, var15, var14, 419430399);
            var1.fill(this.left() - 6, var13, this.left() - 4, var14, 721420287);
            if (var11 >= this.bodyTop() - 1) {
               var1.fill(this.left() - 6, var11, var15, var11 + 1, 721420287);
               var1.drawString(this.font, this.clip((String)var19[0], this.contentRight() - this.left()), this.left(), var11 + 5, -11702, false);
               var1.fill(this.left(), var11 + 15, this.contentRight(), var11 + 16, 486539263);
            }
         }
      }

      for (Object[] var20 : this.labels) {
         int var21 = (Integer)var20[1];
         int var22 = (Integer)var20[2];
         if (!this.outsideBody(var22, 10)) {
            var1.drawString(this.font, this.clip((String)var20[0], this.right() - var21), var21, var22, -2302756, false);
         }
      }

      this.drawScrollbar(var1);
      if (this.usePreview() && this.scrollMax() == 0) {
         this.drawPreview(var1, var2, var3);
      }

      if (this.tab == 4) {
         this.drawRelations(var1);
      }

      super.render(var1, var2, var3, var4);
      if (this.status.getString().isEmpty() || System.currentTimeMillis() > this.statusUntil) {
         this.status = Component.empty();
      }

      int var18 = this.oy() + this.winH() - 21;
      if (!this.status.getString().isEmpty()) {
         var1.drawString(this.font, this.clip(this.status.getString(), this.winW() / 2), this.left(), var18, -8080, false);
      }

      if (this.open != null) {
         this.open.renderPopup(var1, var2, var3);
      }
   }

   private boolean outsideBody(int var1, int var2) {
      return var1 < this.bodyTop() - 2 || var1 + var2 > this.bodyBottom() + 2;
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

   private void drawPreview(GuiGraphics var1, int var2, int var3) {
      int var4 = this.previewW() - 12;
      int var5 = this.right() - var4;
      int var6 = this.bodyTop() + 6;
      int var7 = this.right() - 12;
      int var8 = Math.min(var6 + 130, this.bodyBottom() - 14);
      if (var8 - var6 >= 60) {
         var1.fill(var5, var6, var7, var8, 1073741824);
         if ((Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(this.entityId)) instanceof GuideNpc var9) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(var1, var5, var6, var7, var8, 45, 0.35F, var2, var3, var9);
         }

         var1.drawCenteredString(this.font, Component.translatable("gui.settledlands.cfg.preview").getString(), (var5 + var7) / 2, var8 - 12, -5723992);
      }
   }

   private void drawRelations(GuiGraphics var1) {
      if (this.relPerPage > 0) {
         int var2 = this.relPerPage;
         int var3 = Math.max(1, (this.relations.size() + var2 - 1) / var2);
         this.relPage = Math.max(0, Math.min(this.relPage, var3 - 1));
         int var4 = Math.min(var2, this.relations.size() - this.relPage * var2);

         for (int var5 = 0; var5 < var4; var5++) {
            NpcConfigScreen.RelRow var6 = this.relations.get(this.relPage * var2 + var5);
            var1.drawString(
               this.font, this.clip(var6.name() + " — " + var6.value(), this.labelW() + 60), this.left(), this.relLabelY + var5 * 11, -2302756, false
            );
         }

         if (this.relations.isEmpty()) {
            var1.drawString(this.font, Component.translatable("gui.settledlands.cfg.relempty").getString(), this.left(), this.relLabelY, -5723992, false);
         }
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.flushEscortSettings();
      this.open = null;
      super.onClose();
   }

   private static final class ScheduleTimeBox extends EditBox {
      private ScheduleTimeBox(Font font, int x, int y, int width, int height, Component message) {
         super(font, x, y, width, height, message);
      }

      @Override
      public void setFocused(boolean focused) {
         boolean selectOnFocus = focused && !this.isFocused();
         super.setFocused(focused);
         if (selectOnFocus) {
            this.setCursorPosition(this.getValue().length());
            this.setHighlightPos(0);
         }
      }
   }

   private record Check(Component label, boolean value, OnValueChange action) {
   }

   public record RelRow(String name, int value) {
   }

   public record MobilityGroupOption(String id, String name, int pointCount) {
   }
}
