package dev.settledlands.npc.client;

import dev.settledlands.npc.ActionSequence;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcPoints;
import dev.settledlands.npc.NpcProfile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class GuideDialogueScreen extends Screen {
   private static final int REP_COLOR = -6645094;
   private static final int[] TEXT_COLORS = new int[]{-1, -5680, -11702, -4210753};
   private static final int[] NAME_COLORS = new int[]{-1, -5720, -11702, -4210753};
   private static final int OPTION_COLOR = -1907998;
   private static final int OPTION_SELECTED = -11702;
   private static final int HINT_COLOR = -6645094;
   private static final int DIM = -5197648;
   private static final int BAR_W = 120;
   private static final int BAR_H = 5;
   private static final int BAR_BACK = Integer.MIN_VALUE;
   private static final int BAR_EDGE = 1358954495;
   private static final int TAB_ACTIVE = -11702;
   private static final int ROW_HEIGHT = 12;
   private static final int OPTION_LEFT = 22;
   private static GuideDialogueScreen active;
   private static NpcNet.DlgStyle lastStyle;
   private final int entityId;
   private final boolean observer;
   private int focusEntityId;
   private int focusNameColor = 1;
   private String speaker;
   private String title;
   private final String text;
   private final List<String> options;
   private final List<String> hints;
   private final int relation;
   private final int state;
   private final boolean shop;
   private final NpcNet.DlgStyle style;
   private final List<GuideDialogueScreen.Row> rows = new ArrayList<>();
   private int selected;
   private boolean serverClosing;
   private float typed;
   private int lastSoundChars;
   private boolean hudHidden;
   private boolean hudWasHidden;
   private boolean looking;
   private boolean freeLook;
   private float camYaw;
   private float camPitch;
   private long lastTypedAt;
   private static final long EDIT_DELAY = 350L;
   private static final int EDIT_MARK = 12;
   private static final int CHAIN_ROW = -2;
   private static final int CHAIN_END = -3;
   private static final int ROW_WELCOME = -10;
   private static final int ROOT_LINE_BASE = -100;
   private static final int GEAR_SIZE = 12;
   private static final int GEAR_AT = 26;
   private static final int TAIL_W = 44;
   private static final int SET_H = 196;
   private static final int SET_H_COMPACT = 168;
   private static final int ACTION_ROW = 24;
   private static final int EDIT_TOP = 30;
   private static final int POPUP_H = 92;
   private boolean edit;
   private NpcProfile profile;
   private Screen parent;
   private int posTopic = -1;
   private int posStep;
   private int posAnswer = -1;
   private int rootLine = -1;
   private final List<int[]> trail = new ArrayList<>();
   private int autoStep = -1;
   private int addedStep = -1;
   private EditBox lineBox;
   private EditBox answerBox;
   private EditBox titleBox;
   private EditBox popupBox;
   private boolean adding;
   private boolean addingTopic;
   private int settings = -1;
   private boolean stepSettings;
   private EditBox setTextBox;
   private EditBox setMinBox;
   private EditBox setMaxBox;
   private EditBox setPointsNameBox;
   private EditBox setMinPointsBox;
   private EditBox setMaxPointsBox;
   private EditBox setActionBox;
   private EditBox stepActionBox;
   private String note = "";
   private long noteUntil;
   private final List<Object[]> pending = new ArrayList<>();
   private long pendingAt;
   private boolean suppress;
   private int shadeAlpha;
   private double lastMouseX;
   private double lastMouseY;
   private boolean editCompact;
   private int editScroll;
   private int editContentH;
   private int editContentFrom;
   private static final char ARG = '\u0001';
   private static final char ARG_END = '\u0002';
   private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.";

   public static NpcNet.DlgStyle lastStyle() {
      return lastStyle;
   }

   public GuideDialogueScreen(NpcNet.DlgView var1) {
      super(Component.literal(var1.speaker()));
      this.entityId = var1.entityId();
      this.observer = var1.observer();
      this.focusEntityId = var1.entityId();
      this.speaker = var1.speaker();
      this.title = var1.title();
      this.text = var1.text();
      this.options = var1.options();
      this.hints = var1.hints();
      this.relation = var1.relation();
      this.state = var1.state();
      this.shop = var1.shop();
      this.style = var1.style() == null
         ? new NpcNet.DlgStyle(0, 0, 1, 30, true, true, false, "", true, false, false, false, 0, 100, 0, 0, false, false, 45, 0, true, true, 0, true, false, "", 0)
         : var1.style();
      this.focusNameColor = NpcProfile.clamp(this.style.nameColor(), 0, NAME_COLORS.length - 1);
   }

   public static void open(NpcNet.DlgView var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof GuideDialogueScreen var2 && var2.edit) {
         return;
      }

      LocalPlayer var3 = var1.player;
      if (var3 == null || var1.level == null) {
         return;
      }

      GuideDialogueScreen var4 = var1.screen instanceof GuideDialogueScreen var5 && !var5.edit && var5.entityId == var0.entityId() ? var5 : null;
      int var6 = var4 == null ? var0.entityId() : var4.focusEntityId;
      Entity var7 = var1.level.getEntity(var6);
      if (var7 != null) {
         face(var3, var7);
      }

      GuideDialogueScreen var8 = new GuideDialogueScreen(var0);
      if (var4 != null) {
         var8.focusEntityId = var4.focusEntityId;
         var8.focusNameColor = var4.focusNameColor;
         var8.speaker = var4.speaker;
         var8.title = var4.title;
      }
      var1.setScreen(var8);
   }

   public static void focusFromServer(NpcNet.DlgFocus var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof GuideDialogueScreen var2 && !var2.edit && var2.entityId == var0.entityId()) {
         var2.focusEntityId = var0.focusEntityId();
         var2.focusNameColor = NpcProfile.clamp(var0.nameColor(), 0, NAME_COLORS.length - 1);
         var2.speaker = var0.speaker() == null ? "" : var0.speaker();
         var2.title = var0.title() == null ? "" : var0.title();
         if (var1.player != null && var1.level != null) {
            Entity var3 = var1.level.getEntity(var2.focusEntityId);
            if (var3 != null) {
               face(var1.player, var3);
            }
         }
      }
   }

   public static void closeFromServer(NpcNet.DlgEnd var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof GuideDialogueScreen var2 && var2.entityId == var0.entityId() && !var2.edit) {
         var2.serverClosing = true;
         var1.setScreen(null);
      }
   }

   private static void face(Player var0, Entity var1) {
      double var2 = var1.getX() - var0.getX();
      double var4 = var1.getZ() - var0.getZ();
      double var6 = var1.getEyeY() - var0.getEyeY();
      double var8 = Math.sqrt(var2 * var2 + var4 * var4);
      float var10 = (float)(Math.toDegrees(Math.atan2(var4, var2)) - 90.0);
      float var11 = (float)(-Math.toDegrees(Math.atan2(var6, var8)));
      var0.setYRot(var10);
      var0.setXRot(var11);
      var0.yRotO = var10;
      var0.xRotO = var11;
   }

   private int zoom() {
      return NpcProfile.clamp(this.style.zoom(), 70, 150);
   }

   private boolean blockMouseLook() {
      return this.style.blockMouseLook();
   }

   private boolean cameraOnFace() {
      return this.style.cameraOnFace();
   }

   protected void init() {
      active = this;
      if (this.edit) {
         this.initEdit();
      } else {
         this.rows.clear();
         if (!this.continuation()) {
            for (int var1 = 0; var1 < this.options.size(); var1++) {
               this.rows
                  .add(new GuideDialogueScreen.Row(component(this.options.get(var1)), component(var1 < this.hints.size() ? this.hints.get(var1) : ""), var1));
            }
         }

         boolean var15 = this.state == 6;
         boolean var2 = this.state == 0 || this.state == 3 || this.state == 5 || var15 && !this.options.isEmpty();
         if (!this.observer && (var2 || !this.continuation() && !this.options.isEmpty())) {
            this.rows
               .add(new GuideDialogueScreen.Row(Component.translatable("gui.settledlands.dlg.bye"), Component.translatable("gui.settledlands.dlg.exit"), -1));
         }

         this.selected = Mth.clamp(this.selected, 0, Math.max(0, this.rows.size() - 1));
         Minecraft var3 = Minecraft.getInstance();
         if (var3.player != null) {
            this.camYaw = var3.player.getYRot();
            this.camPitch = var3.player.getXRot();
         }

         this.lastMouseX = this.width / 2.0;
         this.lastMouseY = this.height / 2.0;
         this.lastTypedAt = System.currentTimeMillis();
         lastStyle = this.style;
         if (this.style.muteHud() && !var3.options.hideGui) {
            this.hudWasHidden = false;
            var3.options.hideGui = true;
            this.hudHidden = true;
         }

         byte var4 = 92;
         ArrayList<String> var5 = new ArrayList<>();
         ArrayList<Runnable> var6 = new ArrayList<>();
         var5.add("gui.settledlands.tab.dialogue");
         var6.add(() -> {
            if (this.state != 0) {
               NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -5));
            }
         });
         if (this.style.tradeTab()) {
            var5.add("gui.settledlands.tab.trade");
            var6.add(() -> NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -2)));
         }

         int var7 = -1;
         if (this.style.questTab()) {
            var7 = var5.size();
            var5.add("gui.settledlands.tab.quest");
            var6.add(() -> NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -4)));
         }

         if (this.style.phrasesTab()) {
            var5.add("gui.settledlands.tab.phrases");
            var6.add(() -> NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, -3)));
         }

         int var8 = var5.size() * (var4 + 4) - 4;
         int var9 = (this.width - var8) / 2;
         boolean var10 = this.state == 5 || this.state == 6;
         int var11 = var10 && var7 >= 0 ? var7 : 0;

         for (int var12 = 0; var12 < var5.size(); var12++) {
            Runnable var13 = (Runnable)var6.get(var12);
            MutableComponent var14 = Component.translatable((String)var5.get(var12));
            if (var12 == var11) {
               var14 = var14.copy().withStyle(var0 -> var0.withColor(-11702));
            }

            Button observerTab = Button.builder(var14, button -> {
               if (!this.observer) {
                  var13.run();
               }
            }).bounds(var9 + var12 * (var4 + 4), 8, var4, 18).build();
            if (this.observer) {
               observerTab.active = false;
            }
            this.addRenderableWidget(observerTab);
         }
      }
   }

   public void removed() {
      active = null;
      this.flush();
      if (this.edit) {
         super.removed();
      } else {
         if (this.hudHidden) {
            Minecraft.getInstance().options.hideGui = this.hudWasHidden;
            this.hudHidden = false;
         }

         super.removed();
      }
   }

   private int rowTop() {
      int var1 = Math.max(1, this.rows.size());
      return this.height - 30 - var1 * 12;
   }

   private int optionWidth() {
      return Math.min(430, this.width / 2);
   }

   private int left() {
      return 22 + this.offX();
   }

   private int offX() {
      return this.edit ? 0 : this.style.offsetX();
   }

   private int offY() {
      return this.edit ? 0 : this.style.offsetY();
   }

   private int rowAtY(double var1) {
      int var3 = this.rowTop();

      for (int var4 = 0; var4 < this.rows.size(); var4++) {
         int var5 = var3 + var4 * 12;
         if (var1 >= var5 - 2 && var1 <= var5 + 12 - 2) {
            return var4;
         }
      }

      return -1;
   }

   private int rowAt(double var1, double var3) {
      int var5 = this.rowTop();
      if (!(var1 < this.left() - 4) && !(var1 > this.left() + this.optionWidth())) {
         for (int var6 = 0; var6 < this.rows.size(); var6++) {
            int var7 = var5 + var6 * 12;
            if (var3 >= var7 - 2 && var3 <= var7 + 12 - 2) {
               return var6;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public boolean mouseClicked(double var1, double var3, int var5) {
      if (this.observer) {
         if (var5 == 1) {
            this.onClose();
         }
         return true;
      }
      if (CommandTabCompleter.mouseClicked(this, var1, var3, var5)) {
         return true;
      } else if (NpcUi.grabSlider(this, var1, var3)) {
         return true;
      } else if (this.edit) {
         return this.editClick(var1, var3, var5);
      } else {
         int var6 = this.rowAt(var1, var3);
         if (var6 >= 0) {
            this.selected = var6;
            this.pick(var6);
            return true;
         } else if (super.mouseClicked(var1, var3, var5)) {
            return true;
         } else if (var5 == 0 && this.continuation()) {
            this.continueTalk();
            return true;
         } else if (var5 == 1 && !this.blockMouseLook()) {
            this.looking = true;
            this.freeLook = true;
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean mouseReleased(double var1, double var3, int var5) {
      CommandTabCompleter.mouseReleased(this, var1, var3, var5);
      if (var5 == 1) {
         this.looking = false;
      }

      NpcUi.endSliderDrag();
      return super.mouseReleased(var1, var3, var5);
   }

   public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
      if (CommandTabCompleter.mouseDragged(this, var1, var3, var5, var6, var8)) {
         return true;
      } else {
         return var5 == 0 && NpcUi.dragSlider(var1) ? true : super.mouseDragged(var1, var3, var5, var6, var8);
      }
   }

   public void mouseMoved(double var1, double var3) {
      if (this.looking && !this.blockMouseLook()) {
         double var5 = var1 - this.lastMouseX;
         double var7 = var3 - this.lastMouseY;
         double var9 = (Double)Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
         this.camYaw += (float)(var5 * var9 * 0.4);
         this.camPitch = Mth.clamp(this.camPitch + (float)(var7 * var9 * 0.4), -80.0F, 80.0F);
      }

      this.lastMouseX = var1;
      this.lastMouseY = var3;
      int var11 = this.rowAt(var1, var3);
      if (!this.observer && var11 >= 0) {
         this.selected = var11;
      }

      super.mouseMoved(var1, var3);
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      if (CommandTabCompleter.mouseScrolled(this, var1, var3, var7)) {
         return true;
      } else {
         return this.edit && !this.adding && this.settings < 0 && !this.stepSettings && var7 != 0.0 && var1 >= this.editColX() - 12 && this.scrollEditBy(var7 > 0.0 ? -14 : 14)
            ? true
            : super.mouseScrolled(var1, var3, var5, var7);
      }
   }

   private boolean continuation() {
      return this.state == 4 || this.state == 2 || (this.state == 1 || this.state == 6) && this.options.isEmpty();
   }

   private void continueTalk() {
      if (this.observer) {
         this.typed = this.fullText().getString().length();
         return;
      }
      if (!this.textDone()) {
         this.typed = this.fullText().getString().length();
      } else {
         NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, 0));
      }
   }

   private void pick(int var1) {
      if (this.observer) {
         return;
      }
      if (var1 >= 0 && var1 < this.rows.size()) {
         GuideDialogueScreen.Row var2 = this.rows.get(var1);
         if (var2.action() < 0) {
            this.onClose();
         } else {
            NpcNet.sendToServer(new NpcNet.DlgPick(this.entityId, var2.action()));
         }
      }
   }

   public void onClose() {
      if (this.edit) {
         this.flush();
         Minecraft var1 = Minecraft.getInstance();
         if (this.parent != null) {
            var1.setScreen(this.parent);
         } else {
            super.onClose();
         }

         this.send("refresh", 0, 0, 0, "", "");
      } else {
         if (!this.serverClosing) {
            NpcNet.sendToServer(new NpcNet.DlgClose(this.entityId));
         }

         super.onClose();
      }
   }

   public boolean isPauseScreen() {
      return !this.edit && !this.observer && this.style.pauseWorld();
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
   }

   private Component fullText() {
      return (Component)(this.text != null && !this.text.isEmpty() ? component(this.text) : Component.translatable("gui.settledlands.dlg.topics"));
   }

   private boolean textDone() {
      return this.style.typingSpeed() <= 0 || this.typed >= this.fullText().getString().length();
   }

   private Component partial() {
      Component var1 = this.fullText();
      if (this.textDone()) {
         return var1;
      } else {
         String var2 = var1.getString();
         int var3 = Mth.clamp((int)this.typed, 0, var2.length());
         return Component.literal(var2.substring(0, var3));
      }
   }

   private void tickTyping() {
      if (!this.edit) {
         long var1 = System.currentTimeMillis();
         long var3 = Math.max(0L, var1 - this.lastTypedAt);
         this.lastTypedAt = var1;
         if (!this.textDone() && this.style.typingSpeed() > 0) {
            this.typed = this.typed + (float)var3 / Math.max(1, this.style.typingSpeed());
            int var5 = (int)this.typed;
            if (var5 - this.lastSoundChars >= 3) {
               this.lastSoundChars = var5;
               NpcVoice.playTyping(this.style.voice(), this.style.voicePitch());
            }
         } else {
            this.typed = this.fullText().getString().length();
         }
      }
   }

   public static void clientTick() {
      if (active != null) {
         active.tickTyping();
         if (active.edit) {
            active.tickEdit();
         }
      }
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      int var5 = this.height;
      int var6 = this.width;
      int var7 = NpcProfile.clamp(this.style.blur(), 0, 2);
      if (var7 > 0) {
         this.renderBlurredBackground(var4);
      }

      int var8 = NpcProfile.clamp(this.style.dim(), 0, 80);
      int var9 = var8 * 255 / 100;
      if (var7 > 1) {
         var9 = Math.max(var9, 64);
      }

      this.shadeAlpha = var9;
      if (var9 == 0 && this.style.vignette()) {
         var1.fillGradient(0, (int)(var5 * 0.55F), var6, var5, 0, -1879048192);
      }

      if (this.style.vignette()) {
         var1.fillGradient(0, (int)(var5 * 0.8F), var6, var5, 0, -1342177280);
      }

      if (this.style.style() == 1) {
         var1.fill(0, var5 - 118 + this.offY(), var6, var5, -804253676);
      }

      if (this.settings < 0 && !this.stepSettings) {
         this.drawIslands(var1);
         this.drawLine(var1);
         this.drawRows(var1, var2, var3);
         this.drawHints(var1);
         if (this.edit) {
            this.drawEdit(var1, var2, var3);
         }
      }

      this.drawSettings(var1);
      this.drawStepSettings(var1);
      super.render(var1, var2, var3, var4);
      CommandTabCompleter.render(this, var1, var2, var3);
   }

   private int textShown() {
      return this.edit ? 1 : Math.min(this.font.split(this.partial(), this.optionWidth()).size(), 4);
   }

   private int textTop() {
      return this.rowTop() - 10 - this.textShown() * 10;
   }

   private int repTextWidth() {
      String var1 = this.style.reputation() == null ? "" : this.style.reputation();
      return var1.isEmpty() ? 0 : this.font.width(var1 + " · " + this.relation + "/100");
   }

   private String speakerLine() {
      String var1 = this.speaker == null ? "" : this.speaker.toUpperCase();
      String var2 = this.title == null ? "" : this.title.toUpperCase();
      return var2.isEmpty() ? var1 : var1 + " · " + var2;
   }

   private int nameIslandRight() {
      int var1 = Math.max(this.font.width(this.speakerLine()), 126 + this.repTextWidth());
      int var2 = this.left() + var1 + 10;
      int var3 = (this.edit ? this.editColX() - 12 : this.width) - 10;
      return Math.max(this.left() + 20, Math.min(var2, var3));
   }

   private void drawIslands(GuiGraphics var1) {
      int var2 = Math.min(255, this.shadeAlpha + 48) << 24;
      int var3 = this.edit ? this.editColX() - 12 : this.width;
      int var4 = this.textTop() + this.offY();
      int var5 = this.rowTop() + this.offY();
      boolean var6 = !this.rows.isEmpty();
      if (this.style.speakerName()) {
         int var7 = Math.max(2, var4 - 30);
         var1.fill(0, var7, this.nameIslandRight(), Math.max(var7 + 6, var4 - 6), var2);
      }

      int var10 = Math.max(2, var4 - 4);
      int var8 = var6 ? Math.min(var4 + this.textShown() * 10 + 3, var5 - 4) : this.height;
      var1.fill(0, var10, var3, var8, var2);
      if (var6) {
         int var9 = Math.max(var10 + 6, var5 - 4);
         this.drawSeam(var1, var8, var9, var3, var2);
         var1.fill(0, var9, var3, this.height, var2);
      }

      if (this.edit) {
         var1.fill(Math.max(0, this.editColX() - 12), 2, this.width, this.height, var2);
      }
   }

   private void drawSeam(GuiGraphics var1, int var2, int var3, int var4, int var5) {
      int var6 = Math.max(var2 + 3, var3);
      var1.fill(0, var2, var4, var6, var5);
      int var7 = (var2 + var6) / 2;
      int var8 = var4 / 2;
      int var9 = Math.max(30, var4 / 3);
      byte var10 = 18;

      for (int var11 = 0; var11 < var10; var11++) {
         float var12 = (var11 + 1.0F) / var10;
         int var13 = (int)(144.0F * (1.0F - var12) * (1.0F - var12));
         if (var13 >= 4) {
            int var14 = var8 - var9 + var9 * var11 / var10;
            int var15 = var8 - var9 + var9 * (var11 + 1) / var10;
            var1.fill(var14, var7, var15, var7 + 1, var13 << 24 | 16765514);
            var1.fill(var4 - var15, var7, var4 - var14, var7 + 1, var13 << 24 | 16765514);
         }
      }

      int var16 = Math.max(2, Math.min(4, (var6 - var2) / 2));

      for (int var17 = 0; var17 <= var16; var17++) {
         int var18 = var16 - var17;
         var1.fill(var8 - var18, var7 - var17, var8 + var18 + 1, var7 - var17 + 1, -11702);
         var1.fill(var8 - var18, var7 + var17, var8 + var18 + 1, var7 + var17 + 1, -11702);
      }
   }

   private void drawRelationBar(GuiGraphics var1, int var2, int var3) {
      var1.fill(var2, var3, var2 + 120, var3 + 5, Integer.MIN_VALUE);
      var1.fill(var2, var3, var2 + 120, var3 + 1, 1358954495);
      var1.fill(var2, var3 + 5 - 1, var2 + 120, var3 + 5, 1358954495);
      int var4 = NpcProfile.clamp(this.relation, 0, 100) * 118 / 100;
      if (this.relation > 0) {
         var4 = Math.max(2, var4);
      }

      if (var4 > 0) {
         var1.fill(var2 + 1, var3 + 1, var2 + 1 + var4, var3 + 5 - 1, this.relationColor());
      }
   }

   private int relationColor() {
      int var1 = NpcProfile.reputationStep(this.relation);
      return var1 <= 1 ? -6645094 : (var1 == 2 ? -11702 : -10770372);
   }

   private void drawLine(GuiGraphics var1) {
      int var2 = this.left();
      int var3 = this.rowTop();
      int var4 = TEXT_COLORS[NpcProfile.clamp(this.style.textColor(), 0, TEXT_COLORS.length - 1)];
      Component var5 = this.partial();
      List var6 = this.font.split(var5, this.optionWidth());
      int var7 = this.edit ? 1 : Math.min(var6.size(), 4);
      int var8 = var3 - 10 - var7 * 10;
      if (this.style.speakerName()) {
         var1.drawString(
            this.font,
            this.speakerLine(),
            var2,
            var8 - 24 + this.offY(),
            NAME_COLORS[NpcProfile.clamp(this.focusNameColor, 0, NAME_COLORS.length - 1)],
            true
         );
         int var9 = var8 - 12 + this.offY();
         this.drawRelationBar(var1, var2, var9);
         String var10 = this.style.reputation() == null ? "" : this.style.reputation();
         if (!var10.isEmpty()) {
            var1.drawString(this.font, var10 + " · " + this.relation + "/100", var2 + 120 + 6, var9 - 3, -6645094, true);
         }
      }

      if (!this.edit) {
         for (int var11 = 0; var11 < var7; var11++) {
            var1.drawString(this.font, (FormattedCharSequence)var6.get(var11), var2, var8 + var11 * 10 + this.offY(), var4, true);
         }
      }
   }

   private void drawRows(GuiGraphics var1, int var2, int var3) {
      int var4 = this.left();
      int var5 = this.optionWidth();
      boolean var6 = this.textDone() || this.state == 1;

      for (int var7 = 0; var7 < this.rows.size(); var7++) {
         GuideDialogueScreen.Row var8 = this.rows.get(var7);
         int var9 = this.rowTop() + var7 * 12 + this.offY();
         boolean var10 = !this.observer && var7 == this.selected;
         String var11 = var8.text().getString();
         String var12 = ""; // No inline editor hints over the actual dialogue/answer text.
         String var13 = this.style.answerNumbers() ? var7 + 1 + ". " : "";
         if (var12.isEmpty()) {
            boolean var14 = false;
         } else {
            int var15 = this.font.width(var12) + 10;
         }

         int var16 = var10 ? -11702 : -1907998;
         if (!var6 && !var10) {
            var16 = -1862270977;
         }

         if (var10) {
            var1.fill(var4 - 6, var9 - 2, var4 + var5, var9 + 12 - 2, 956301311);
         }

         var1.drawString(this.font, var13 + var11, var4, var9, var16, true);
         if (!var12.isEmpty()) {
            var1.drawString(this.font, var12, var4 + var5 - this.font.width(var12), var9, -6645094, true);
         }
      }
   }

   private void drawHints(GuiGraphics var1) {
      if (!this.edit && this.style.keyHints()) {
         MutableComponent var2;
         if (this.observer) {
            var2 = Component.translatable("gui.settledlands.dlg.keys.listen");
         } else if (this.continuation()) {
            var2 = Component.translatable("gui.settledlands.dlg.keys.line");
         } else if (this.blockMouseLook()) {
            var2 = Component.translatable("gui.settledlands.dlg.keys.locked");
         } else {
            var2 = Component.translatable("gui.settledlands.dlg.keys.look");
         }

         var1.drawString(this.font, var2, this.left(), this.height - 12, -6645094, true);
         if (!this.textDone()) {
            var1.drawString(this.font, "▍", this.left() + this.font.width(var2) + 6, this.height - 12, -5197648, true);
         }
      }
   }

   private GuideDialogueScreen(int var1, NpcProfile var2, Screen var3) {
      super(Component.literal(var2.name == null ? "" : var2.name));
      this.edit = true;
      this.entityId = var1;
      this.observer = false;
      this.focusEntityId = var1;
      this.profile = var2;
      this.parent = var3;
      this.speaker = var2.name == null ? "" : var2.name;
      this.title = var2.title == null ? "" : var2.title;
      this.text = "";
      this.options = List.of();
      this.hints = List.of();
      this.relation = 0;
      this.state = 0;
      this.shop = false;
      this.style = editStyle(var2);
      this.focusNameColor = NpcProfile.clamp(this.style.nameColor(), 0, NAME_COLORS.length - 1);
   }

   private static NpcNet.DlgStyle editStyle(NpcProfile var0) {
      return new NpcNet.DlgStyle(
         var0.style,
         var0.textColor,
         var0.nameColor,
         var0.typingSpeed,
         var0.showSpeaker,
         var0.keyHints,
         var0.answerNumbers,
         var0.reputationLabel(0),
         var0.showDialogueTab,
         var0.showTradeTab && var0.hasShop(),
         var0.showQuestTab && (!var0.quests.isEmpty() || (var0.dialogueGraph != null && var0.dialogueGraph.hasQuestNodes())),
         var0.showPhrasesTab,
         var0.defaultTab,
         var0.zoom,
         var0.offsetX,
         var0.offsetY,
         var0.cameraOnFace,
         var0.blockMouseLook,
         var0.dim,
         var0.blur,
         var0.muteHud,
         var0.vignette,
         var0.exitKey,
         var0.blockMovement,
         var0.pauseWorld,
         var0.voice,
         var0.voicePitch
      );
   }

   public static void openEdit(int var0, NpcProfile var1, Screen var2, int var3) {
      NpcDialogueGraphScreen.open(var0, var1, var2, var3);
   }

   public boolean editMode() {
      return this.edit;
   }

   public int editorEntityId() {
      return this.entityId;
   }

   public void applySnapshot(NpcProfile var1) {
      if (var1 != null) {
         this.profile = var1;
         this.editClamp();
         this.rebuildEditRows();
         this.syncBoxes();
      }
   }

   private NpcProfile.Topic editTopic() {
      return this.edit && this.posTopic >= 0 && this.posTopic < this.profile.topics.size() ? this.profile.topics.get(this.posTopic) : null;
   }

   private NpcProfile.Step editStep() {
      return this.editStepAt(this.posTopic, this.posStep);
   }

   private NpcProfile.Step editStepAt(int var1, int var2) {
      NpcProfile.Topic var3 = var1 >= 0 && var1 < this.profile.topics.size() ? this.profile.topics.get(var1) : null;
      return var3 != null && var2 >= 0 && var2 < var3.steps.size() ? var3.steps.get(var2) : null;
   }

   private NpcProfile.Answer editAnswer() {
      if (this.posAnswer >= 0 && !this.trail.isEmpty()) {
         int[] var1 = this.trail.get(this.trail.size() - 1);
         NpcProfile.Step var2 = this.editStepAt(var1[0], var1[1]);
         return var2 != null && var1[2] < var2.answers.size() ? var2.answers.get(var1[2]) : null;
      } else {
         return null;
      }
   }

   private boolean editReaction() {
      NpcProfile.Answer var1 = this.editAnswer();
      return var1 != null && var1.next == -1;
   }

   private boolean editFieldVisible() {
      return this.posTopic < 0 || this.editReaction() || this.editStep() != null;
   }

   private String editLine() {
      if (this.posTopic < 0) {
         if (this.rootLine == -2) return this.profile.escortTravelLine;
         if (this.rootLine == -3) return this.profile.escortArrivalLine;
         if (this.rootLine >= 0 && this.rootLine < this.profile.welcomeLines.size()) {
            String var3 = this.profile.welcomeLines.get(this.rootLine);
            return var3 == null ? "" : var3;
         } else {
            return this.profile.welcome == null ? "" : this.profile.welcome;
         }
      } else if (this.editReaction()) {
         NpcProfile.Answer var2 = this.editAnswer();
         return var2 != null && var2.reaction != null ? var2.reaction : "";
      } else {
         NpcProfile.Step var1 = this.editStep();
         return var1 != null && var1.line != null ? var1.line : "";
      }
   }

   private String editLineKey() {
      if (this.posTopic < 0) {
         if (this.rootLine == -2) return "";
         if (this.rootLine == -3) return "";
         return this.rootLine >= 0 ? "gui.settledlands.edit.hint.line" : "gui.settledlands.edit.hint.welcome";
      } else {
         return this.editReaction() ? "gui.settledlands.edit.hint.reaction" : "gui.settledlands.edit.hint.line";
      }
   }

   private String editPlace() {
      if (this.posTopic < 0) {
         if (this.rootLine == -2 || this.rootLine == -3) return "";
         String var4 = Component.translatable("gui.settledlands.edit.place.root").getString();
         if (this.rootLine >= 0) {
            var4 = var4 + " · " + Component.translatable("gui.settledlands.edit.place.step", new Object[]{this.rootLine + 1}).getString();
         }

         return var4;
      } else {
         NpcProfile.Topic var1 = this.editTopic();
         String var2 = var1 != null && var1.title != null ? var1.title : "";
         StringBuilder var3 = new StringBuilder(Component.translatable("gui.settledlands.edit.place.topic", new Object[]{var2}).getString());
         var3.append(" · ").append(Component.translatable("gui.settledlands.edit.place.step", new Object[]{this.posStep + 1}).getString());
         if (this.posAnswer >= 0 && !this.trail.isEmpty()) {
            var3.append(" · ")
               .append(Component.translatable("gui.settledlands.edit.place.answer", new Object[]{this.trail.get(this.trail.size() - 1)[2] + 1}).getString());
         }

         return var3.toString();
      }
   }

   private String editDest(NpcProfile.Answer var1) {
      String var2 = "";
      if (var1.maxRelation < 100) {
         var2 = Component.translatable("gui.settledlands.edit.dest.range", new Object[]{var1.minRelation, var1.maxRelation}).getString();
      } else if (var1.minRelation > 0) {
         var2 = Component.translatable("gui.settledlands.edit.dest.min", new Object[]{var1.minRelation}).getString();
      }

      if (var1.relation != 0) {
         var2 = var2
            + " · "
            + Component.translatable("gui.settledlands.edit.dest.rel", new Object[]{(var1.relation > 0 ? "+" : "") + var1.relation}).getString();
      }

      if (var1.next != -1 && var1.next < 1000 && this.replyShared(var1.next)) {
         var2 = var2 + " · " + Component.translatable("gui.settledlands.edit.dest.shared").getString();
      }

      String var3 = NpcProfile.actionKind(var1.action);
      if (!var3.isEmpty()) {
         var2 = var2
            + " · "
            + Component.translatable(var3.equals("trigger") ? "gui.settledlands.edit.dest.trigger" : "gui.settledlands.edit.dest.cmd").getString();
      }

      if (var1.next >= 1000) {
         int var6 = var1.next - 1000;
         return var6 < this.profile.topics.size()
            ? Component.translatable("gui.settledlands.edit.dest.topic", new Object[]{this.profile.topics.get(var6).title}).getString() + var2
            : Component.translatable("gui.settledlands.edit.dest.none").getString() + var2;
      } else if (var1.next == -1) {
         return Component.translatable("gui.settledlands.edit.dest.end").getString() + var2;
      } else {
         NpcProfile.Topic var4 = this.editTopic();
         int var5 = var4 == null ? 0 : var4.steps.size();
         return var1.next < var5
            ? Component.translatable("gui.settledlands.edit.dest.step", new Object[]{var1.next + 1}).getString() + var2
            : Component.translatable("gui.settledlands.edit.dest.none").getString() + var2;
      }
   }

   private boolean replyShared(int var1) {
      NpcProfile.Topic var2 = this.editTopic();
      if (var2 != null && var1 >= 0 && var1 < var2.steps.size()) {
         int var3 = 0;

         for (NpcProfile.Step var5 : var2.steps) {
            for (NpcProfile.Answer var7 : var5.answers) {
               if (var7.next == var1) {
                  if (++var3 > 1) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private String editTopicDest(NpcProfile.Topic var1) {
      if (NpcProfile.isQuestTopic(var1)) {
         return Component.translatable("gui.settledlands.edit.quest").getString();
      } else {
         int var2 = NpcProfile.clamp(var1.type, 0, NpcProfile.TYPE_COUNT - 1);
         return Component.translatable(NpcProfile.TYPE_KEYS[var2]).getString();
      }
   }

   private Component editAnswerText(NpcProfile.Answer var1) {
      return var1.player != null && !var1.player.isEmpty() ? Component.literal(var1.player) : Component.empty();
   }

   private void editNote(String var1, Object... var2) {
      this.note = Component.translatable(var1, var2).getString();
      this.noteUntil = System.currentTimeMillis() + 3000L;
   }

   private void send(String var1, int var2, int var3, int var4, String var5, String var6) {
      NpcNet.sendToServer(new NpcNet.CfgEdit(this.entityId, var1, var2, var3, var4, var5, var6));
   }

   private void queue(String var1, int var2, int var3, int var4, String var5, String var6) {
      Iterator var7 = this.pending.iterator();

      while (var7.hasNext()) {
         Object[] var8 = (Object[])var7.next();
         if (var1.equals(var8[0])
            && Integer.valueOf(var2).equals(var8[1])
            && Integer.valueOf(var3).equals(var8[2])
            && Integer.valueOf(var4).equals(var8[3])
            && var5.equals(var8[4])) {
            var7.remove();
         }
      }

      this.pending.add(new Object[]{var1, var2, var3, var4, var5, var6});
      this.pendingAt = System.currentTimeMillis() + 350L;
   }

   private void flush() {
      for (Object[] var2 : this.pending) {
         this.send((String)var2[0], (Integer)var2[1], (Integer)var2[2], (Integer)var2[3], (String)var2[4], (String)var2[5]);
      }

      this.pending.clear();
   }

   private void editClamp() {
      if (this.rootLine >= this.profile.welcomeLines.size()) {
         this.rootLine = -1;
      }

      if (this.posTopic >= this.profile.topics.size()) {
         this.posTopic = -1;
         this.posAnswer = -1;
         this.trail.clear();
      }

      NpcProfile.Topic var1 = this.editTopic();
      if (var1 != null) {
         this.posStep = var1.steps.isEmpty() ? 0 : NpcProfile.clamp(this.posStep, 0, var1.steps.size() - 1);
      }

      while (this.posAnswer >= 0 && !this.trail.isEmpty() && this.editAnswer() == null) {
         this.trail.remove(this.trail.size() - 1);
         this.posAnswer = -1;
      }
   }

   private Component editRowText(String var1) {
      return var1 != null && !var1.isEmpty() ? Component.literal(var1) : Component.empty();
   }

   private void rebuildEditRows() {
      this.rows.clear();
      if (this.edit) {
         if (this.posTopic < 0) {
            String var1 = this.profile.welcome == null ? "" : this.profile.welcome;
            this.rows.add(new GuideDialogueScreen.Row(this.editRowText(var1), Component.translatable("gui.settledlands.edit.place.root"), -10));

            for (int var2 = 0; var2 < this.profile.welcomeLines.size(); var2++) {
               String var3 = this.profile.welcomeLines.get(var2);
               this.rows
                  .add(
                     new GuideDialogueScreen.Row(
                        this.editRowText(var3), Component.translatable("gui.settledlands.edit.place.step", new Object[]{var2 + 1}), rootLineAction(var2)
                     )
                  );
            }

            this.rows.add(new GuideDialogueScreen.Row(this.editRowText(this.profile.escortTravelLine), Component.empty(), -11));
            this.rows.add(new GuideDialogueScreen.Row(this.editRowText(this.profile.escortArrivalLine), Component.empty(), -12));
            for (int var5 = 0; var5 < this.profile.topics.size(); var5++) {
               NpcProfile.Topic var9 = this.profile.topics.get(var5);
               this.rows
                  .add(
                     new GuideDialogueScreen.Row(
                        (Component)(NpcProfile.isQuestTopic(var9) ? NpcUi.questTopicName(var9) : Component.literal(var9.title == null ? "" : var9.title)),
                        Component.literal(this.editTopicDest(var9)),
                        var5
                     )
                  );
            }
         } else {
            NpcProfile.Step var4 = this.editStep();
            if (var4 != null) {
               for (int var6 = 0; var6 < var4.answers.size(); var6++) {
                  NpcProfile.Answer var10 = var4.answers.get(var6);
                  this.rows.add(new GuideDialogueScreen.Row(this.editAnswerText(var10), Component.literal(this.editDest(var10)), var6));
               }

               if (this.editFreshRow()) {
                  MutableComponent var7 = Component.translatable("gui.settledlands.edit.chain", new Object[]{this.addedStep + 1})
                     .withStyle(ChatFormatting.YELLOW);
                  this.rows.add(new GuideDialogueScreen.Row(var7, Component.empty(), -2));
               } else if (var4.answers.isEmpty() && this.posAnswer < 0) {
                  int var8 = this.editChainNext();
                  if (var8 >= 0) {
                     this.rows
                        .add(
                           new GuideDialogueScreen.Row(
                              Component.translatable("gui.settledlands.edit.chain", new Object[]{var8 + 1}),
                              Component.empty(),
                              -2
                           )
                        );
                  }
               }
            }
         }
      }
   }

   private int editChainNext() {
      return NpcProfile.chainNext(this.editTopic(), this.posStep);
   }

   private boolean editFreshRow() {
      NpcProfile.Topic var1 = this.editTopic();
      return var1 != null && this.addedStep == this.posStep + 1 && this.addedStep < var1.steps.size();
   }

   private boolean editChainBlocked() {
      NpcProfile.Topic var1 = this.editTopic();
      if (var1 == null) {
         return false;
      } else {
         for (int var2 = this.posStep + 1; var2 < var1.steps.size(); var2++) {
            if (NpcProfile.hasText(var1.steps.get(var2))) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isChainMarker(int var0) {
      return var0 == -2 || var0 == -3;
   }

   private static boolean isRootLine(int var0) {
      return var0 <= -100;
   }

   private static int rootLineIndex(int var0) {
      return -100 - var0;
   }

   private static int rootLineAction(int var0) {
      return -100 - var0;
   }

   private void syncBoxes() {
      this.syncBox(this.lineBox, this.editLine());
      if (this.titleBox != null) {
         NpcProfile.Topic var1 = this.editTopic();
         this.syncBox(this.titleBox, var1 != null && var1.title != null ? var1.title : "");
      }

      if (this.answerBox != null) {
         NpcProfile.Answer var2 = this.editAnswer();
         this.syncBox(this.answerBox, var2 != null && var2.player != null ? var2.player : "");
      }

      if (this.stepActionBox != null) {
         NpcProfile.Step var3 = this.editStep();
         this.syncBox(this.stepActionBox, var3 != null && var3.action != null ? var3.action : "");
      }
   }

   private void syncBox(EditBox var1, String var2) {
      if (var1 != null && !var1.isFocused() && !var2.equals(var1.getValue())) {
         this.suppress = true;
         var1.setValue(var2);
         this.suppress = false;
      }
   }

   private void editLineTyped(String var1) {
      if (!this.suppress) {
         if (this.posTopic < 0) {
            if (this.rootLine == -2 || this.rootLine == -3) {
               String key = this.rootLine == -2 ? "escortTravelLine" : "escortArrivalLine";
               if (this.rootLine == -2) this.profile.escortTravelLine = var1;
               else this.profile.escortArrivalLine = var1;
               this.queue("field", 0, 0, 0, key, var1);
               this.rebuildEditRows();
            } else if (this.rootLine >= 0 && this.rootLine < this.profile.welcomeLines.size()) {
               this.profile.welcomeLines.set(this.rootLine, var1);
               this.queue("welcome.field", this.rootLine, 0, 0, "text", var1);
               this.rebuildEditRows();
            } else {
               this.profile.welcome = var1;
               this.queue("field", 0, 0, 0, "welcome", var1);
            }
         } else if (this.editReaction()) {
            NpcProfile.Answer var2 = this.editAnswer();
            if (var2 != null && !this.trail.isEmpty()) {
               var2.reaction = var1;
               int[] var3 = this.trail.get(this.trail.size() - 1);
               this.queue("answer.field", var3[0], var3[1], var3[2], "reaction", var1);
            }
         } else {
            NpcProfile.Step var4 = this.editStep();
            if (var4 != null) {
               var4.line = var1;
               this.queue("step.field", this.posTopic, this.posStep, 0, "line", var1);
            }
         }
      }
   }

   private void editStepActionTyped(String value) {
      if (!this.suppress) {
         NpcProfile.Step step = this.editStep();
         if (step != null) {
            step.action = value;
            this.queue("step.field", this.posTopic, this.posStep, 0, "action", value);
         }
      }
   }

   private void editTitleTyped(String var1) {
      if (!this.suppress) {
         NpcProfile.Topic var2 = this.editTopic();
         if (var2 != null) {
            var2.title = var1;
            this.queue("topic.field", this.posTopic, 0, 0, "title", var1);
            if (this.posTopic >= 0) {
               this.rebuildEditRows();
            }
         }
      }
   }

   private void editAnswerTyped(String var1) {
      if (!this.suppress) {
         NpcProfile.Answer var2 = this.editAnswer();
         if (var2 != null && !this.trail.isEmpty()) {
            var2.player = var1;
            int[] var3 = this.trail.get(this.trail.size() - 1);
            this.queue("answer.field", var3[0], var3[1], var3[2], "player", var1);
         }
      }
   }

   private void editEnterTopic(int var1) {
      if (var1 >= 0 && var1 < this.profile.topics.size()) {
         this.flush();
         this.addedStep = -1;
         this.trail.clear();
         this.trail.add(new int[]{-1, 0, 0});
         this.rootLine = -1;
         this.posTopic = var1;
         this.posStep = 0;
         this.posAnswer = -1;
         this.editEnsureStep();
         this.rebuildEditRows();
         this.rebuildWidgets();
      }
   }

   private void editEnsureStep() {
      NpcProfile.Topic var1 = this.editTopic();
      if (var1 != null && var1.steps.isEmpty() && var1.steps.size() < 16) {
         var1.steps.add(new NpcProfile.Step());
         this.send("step.add", this.posTopic, 0, 0, "", "");
      }
   }

   private void editEnterAnswer(int var1) {
      NpcProfile.Step var2 = this.editStep();
      NpcProfile.Topic var3 = this.editTopic();
      if (var2 != null && var3 != null && var1 >= 0 && var1 < var2.answers.size()) {
         this.flush();
         NpcProfile.Answer var4 = var2.answers.get(var1);
         int[] var5 = new int[]{this.posTopic, this.posStep, var1};
         if (var4.next >= 1000) {
            int var6 = var4.next - 1000;
            if (var6 < this.profile.topics.size() && var6 != this.posTopic) {
               this.trail.add(var5);
               this.posTopic = var6;
               this.posStep = 0;
               this.posAnswer = var1;
               this.editEnsureStep();
               this.rebuildEditRows();
               this.rebuildWidgets();
               return;
            }
         }

         int var8 = var4.next;
         if (var8 < 0 || var8 >= var3.steps.size()) {
            int var7 = this.editNewStep();
            if (var7 < 0) {
               return;
            }

            var8 = var7;
            var4.next = var7;
            this.send("answer.field", var5[0], var5[1], var5[2], "next", String.valueOf(var7));
         }

         this.trail.add(var5);
         this.posStep = var8;
         this.posAnswer = var1;
         this.rebuildEditRows();
         this.rebuildWidgets();
      }
   }

   private void editBack() {
      this.flush();
      this.addedStep = -1;
      if (this.trail.isEmpty()) {
         this.posTopic = -1;
         this.posStep = 0;
         this.posAnswer = -1;
         this.rebuildEditRows();
         this.rebuildWidgets();
      } else {
         int[] var1 = this.trail.remove(this.trail.size() - 1);
         if (var1[0] < 0) {
            this.posTopic = -1;
            this.posStep = 0;
            this.posAnswer = -1;
            this.rootLine = -1;
            this.rebuildEditRows();
            this.rebuildWidgets();
         } else {
            this.posTopic = NpcProfile.clamp(var1[0], 0, Math.max(0, this.profile.topics.size() - 1));
            this.posStep = var1[1];
            this.posAnswer = -1;
            this.editClamp();
            this.rebuildEditRows();
            this.rebuildWidgets();
         }
      }
   }

   private void editOpenReply() {
      NpcProfile.Answer var1 = this.editAnswer();
      NpcProfile.Topic var2 = this.editTopic();
      if (var1 != null && var2 != null && !this.trail.isEmpty()) {
         int var3 = this.posStep;
         if (var3 < 0 || var3 >= var2.steps.size()) {
            var3 = this.editNewStep();
            if (var3 < 0) {
               return;
            }

            this.posStep = var3;
         }

         var1.next = var3;
         int[] var4 = this.trail.get(this.trail.size() - 1);
         this.send("answer.field", var4[0], var4[1], var4[2], "next", String.valueOf(var3));
         this.rebuildEditRows();
         this.rebuildWidgets();
      }
   }

   private void editSetEnd() {
      NpcProfile.Answer var1 = this.editAnswer();
      if (var1 != null && !this.trail.isEmpty()) {
         this.editDropAuto(var1);
         var1.next = -1;
         int[] var2 = this.trail.get(this.trail.size() - 1);
         this.send("answer.field", var2[0], var2[1], var2[2], "next", String.valueOf(-1));
         this.rebuildEditRows();
         this.rebuildWidgets();
      }
   }

   private int editNewStep() {
      NpcProfile.Topic var1 = this.editTopic();
      if (var1 == null) {
         return -1;
      } else if (var1.steps.size() >= 16) {
         this.editNote("gui.settledlands.edit.limit");
         return -1;
      } else {
         int var2 = var1.steps.size();
         var1.steps.add(new NpcProfile.Step());
         this.send("step.add", this.posTopic, 0, 0, "", "");
         this.autoStep = var2;
         return var2;
      }
   }

   private void editAddLine() {
      if (this.posTopic < 0) {
         this.editAddRootLine();
      } else {
         this.editAddStep();
      }
   }

   private void editAddRootLine() {
      if (this.profile.welcomeLines.size() >= 6) {
         this.editNote("gui.settledlands.edit.limit");
      } else {
         this.flush();
         this.profile.welcomeLines.add("");
         this.send("welcome.add", 0, 0, 0, "", "");
         this.rebuildEditRows();
         this.rebuildWidgets();
         this.editNote("gui.settledlands.edit.added.root", this.profile.welcomeLines.size());
      }
   }

   private void editDeleteRootLine(int var1) {
      if (var1 >= 0 && var1 < this.profile.welcomeLines.size()) {
         this.flush();
         this.profile.welcomeLines.remove(var1);
         this.send("welcome.del", var1, 0, 0, "", "");
         if (this.rootLine == var1) {
            this.rootLine = -1;
         } else if (this.rootLine > var1) {
            this.rootLine--;
         }

         this.rebuildEditRows();
         this.rebuildWidgets();
         this.editNote("gui.settledlands.edit.deleted");
      }
   }

   private void editAddStep() {
      NpcProfile.Topic var1 = this.editTopic();
      if (var1 != null) {
         if (var1.steps.size() >= 16) {
            this.editNote("gui.settledlands.edit.limit");
         } else {
            this.flush();
            int var2 = NpcProfile.clamp(this.posStep + 1, 0, var1.steps.size());
            if (this.addedStep != var2) {
               var1.steps.add(var2, new NpcProfile.Step());

               for (NpcProfile.Step var4 : var1.steps) {
                  for (NpcProfile.Answer var6 : var4.answers) {
                     if (var6.next != -1 && var6.next < 1000 && var6.next >= var2) {
                        var6.next++;
                     }
                  }
               }

               this.send("step.insert", this.posTopic, var2, 0, "", "");
               this.addedStep = var2;
            }

            NpcProfile.Step var9 = this.editStep();
            boolean var10 = var9 == null || var9.answers.isEmpty();
            boolean var11 = false;
            boolean var12 = false;
            if (this.posAnswer < 0 && var9 != null && !var9.answers.isEmpty()) {
               for (int var7 = 0; var7 < var9.answers.size(); var7++) {
                  NpcProfile.Answer var8 = var9.answers.get(var7);
                  if (var8.next == -1) {
                     if (var1 == null || !NpcProfile.isQuestTopic(var1) || !NpcProfile.questAction(var8.action)) {
                        var8.next = var2;
                        this.send("answer.field", this.posTopic, this.posStep, var7, "next", String.valueOf(var2));
                        var10 = true;
                        var12 = true;
                        break;
                     }

                     var11 = true;
                     var10 = true;
                  }
               }
            }

            this.autoStep = -1;
            this.rebuildEditRows();
            this.rebuildWidgets();
            this.editNote(
               var11 && !var12 ? "gui.settledlands.edit.added.chain" : (var10 ? "gui.settledlands.edit.added.after" : "gui.settledlands.edit.nolink"), var2 + 1
            );
         }
      }
   }

   private void editAddAnswer() {
      if (this.posTopic < 0) {
         if (this.profile.topics.size() >= 8) {
            this.editNote("gui.settledlands.edit.limit");
            return;
         }

         this.addingTopic = true;
      } else {
         NpcProfile.Step var1 = this.editStep();
         if (var1 == null || this.editReaction()) {
            return;
         }

         if (var1.answers.size() >= 6) {
            this.editNote("gui.settledlands.edit.limit");
            return;
         }

         this.addingTopic = false;
      }

      this.flush();
      this.adding = true;
      this.rebuildWidgets();
   }

   private void editSavePopup() {
      String var1 = this.popupBox == null ? "" : this.popupBox.getValue().trim();
      if (!var1.isEmpty()) {
         if (this.addingTopic) {
            NpcProfile.Topic var2 = new NpcProfile.Topic(var1, 0);
            var2.steps.add(new NpcProfile.Step());
            this.profile.topics.add(var2);
            this.send("topic.add", 0, 0, 0, "", String.valueOf(0));
            this.send("topic.field", this.profile.topics.size() - 1, 0, 0, "title", var1);
         } else {
            NpcProfile.Step var4 = this.editStep();
            if (var4 == null) {
               return;
            }

            NpcProfile.Answer var3 = new NpcProfile.Answer();
            var3.player = var1;
            var4.answers.add(var3);
            this.send("answer.add", this.posTopic, this.posStep, 0, "", "");
            this.send("answer.field", this.posTopic, this.posStep, var4.answers.size() - 1, "player", var1);
         }

         this.adding = false;
         this.rebuildEditRows();
         this.rebuildWidgets();
         this.editNote("gui.settledlands.edit.added");
      }
   }

   private void editDeleteAnswer() {
      NpcProfile.Answer var1 = this.editAnswer();
      if (var1 != null && !this.trail.isEmpty()) {
         int[] var2 = this.trail.get(this.trail.size() - 1);
         NpcProfile.Step var3 = this.editStepAt(var2[0], var2[1]);
         if (var3 != null && var2[2] < var3.answers.size()) {
            this.flush();
            this.editDropAuto(var1);
            var3.answers.remove(var2[2]);
            this.send("answer.del", var2[0], var2[1], var2[2], "", "");
            this.trail.remove(this.trail.size() - 1);
            this.posTopic = var2[0];
            this.posStep = var2[1];
            this.posAnswer = -1;
            this.editClamp();
            this.rebuildEditRows();
            this.rebuildWidgets();
            this.editNote("gui.settledlands.edit.deleted");
         }
      }
   }

   private void editDeleteRow(int var1) {
      if (this.editCanDelete() && var1 >= 0 && var1 < this.rows.size()) {
         if (this.posTopic < 0) {
            int var2 = this.rows.get(var1).action();
            if (isRootLine(var2)) {
               this.editDeleteRootLine(rootLineIndex(var2));
            }
         } else {
            NpcProfile.Step var4 = this.editStep();
            if (var4 != null && var1 >= 0 && var1 < var4.answers.size()) {
               this.flush();
               NpcProfile.Answer var3 = var4.answers.get(var1);
               if (var3 == this.editAnswer()) {
                  this.editDeleteAnswer();
               } else {
                  this.editDropAuto(var3);
                  var4.answers.remove(var1);
                  this.send("answer.del", this.posTopic, this.posStep, var1, "", "");
                  this.rebuildEditRows();
                  this.rebuildWidgets();
                  this.editNote("gui.settledlands.edit.deleted");
               }
            }
         }
      }
   }

   private void editDropAuto(NpcProfile.Answer var1) {
      NpcProfile.Topic var2 = this.editTopic();
      int var3 = this.autoStep;
      if (var2 != null && var3 >= 0 && var3 == var2.steps.size() - 1 && var1 != null && var1.next == var3) {
         NpcProfile.Step var4 = var2.steps.get(var3);
         boolean var5 = (var4.line == null || var4.line.isEmpty()) && var4.answers.isEmpty() && var4.variants.isEmpty();
         if (var5) {
            for (NpcProfile.Step var7 : var2.steps) {
               for (NpcProfile.Answer var9 : var7.answers) {
                  if (var9 != var1 && var9.next == var3) {
                     return;
                  }
               }
            }

            var2.steps.remove(var3);
            this.send("step.del", this.posTopic, var3, 0, "", "");
            this.autoStep = -1;
            this.posStep = NpcProfile.clamp(this.posStep, 0, Math.max(0, var2.steps.size() - 1));
         }
      }
   }

   private boolean editCanDelete() {
      return this.edit && !this.adding && (this.posTopic < 0 || this.editStep() != null);
   }

   private boolean canDeleteRow(int var1) {
      return this.posTopic < 0 ? isRootLine(var1) : !isChainMarker(var1);
   }

   private int editColX() {
      return this.left() + this.optionWidth() + 44;
   }

   private int editColW() {
      return Math.max(60, this.width - 12 - this.editColX());
   }

   private int editBottom() {
      return this.height - 26;
   }

   private List<Object[]> editActionItems() {
      ArrayList<Object[]> var1 = new ArrayList<>();
      var1.add(new Object[]{Component.translatable("gui.settledlands.edit.addline"), (Runnable)this::editAddLine});
      var1.add(new Object[]{Component.translatable("gui.settledlands.edit.add"), (Runnable)this::editAddAnswer});
      if (this.posAnswer >= 0 && this.editAnswer() != null) {
         var1.add(new Object[]{Component.translatable("gui.settledlands.edit.del"), (Runnable)this::editDeleteAnswer});
      }

      if (!this.trail.isEmpty()) {
         var1.add(new Object[]{Component.translatable("gui.settledlands.edit.back"), (Runnable)this::editBack});
      }

      var1.add(new Object[]{Component.translatable("gui.settledlands.cfg.done"), (Runnable)this::onClose});
      return var1;
   }

   private int editActionRows() {
      int var1 = this.editActionItems().size();
      return this.editTwoColumns() ? (var1 + 1) / 2 : var1;
   }

   private boolean editTwoColumns() {
      return this.editColW() >= 126;
   }

   private void updateEditCompact() {
      int var1 = this.editFieldsBottom() + 6 + this.editActionRows() * 24 + 6;
      this.editCompact = var1 > this.editBottom() - 30;
      if (!this.editCompact) {
         this.editScroll = 0;
      }
   }

   private int editScrollMax() {
      return Math.max(0, this.editContentH - (this.editBottom() - 30));
   }

   private void clampEditScroll() {
      this.editScroll = NpcProfile.clamp(this.editScroll, 0, this.editScrollMax());
   }

   private boolean scrollEditBy(int var1) {
      if (this.editCompact && this.editScrollMax() > 0) {
         int var2 = this.editScroll;
         this.editScroll = NpcProfile.clamp(this.editScroll + var1, 0, this.editScrollMax());
         if (this.editScroll != var2) {
            this.rebuildWidgets();
         }

         return true;
      } else {
         return false;
      }
   }

   private void hideEditOutside() {
      for (int var1 = this.editContentFrom; var1 < this.renderables.size(); var1++) {
         if (this.renderables.get(var1) instanceof AbstractWidget var2 && (var2.getY() < 29 || var2.getY() + var2.getHeight() > this.editBottom() + 1)) {
            var2.visible = false;
         }
      }
   }

   private int editLineY() {
      return Math.max(4, this.rowTop() - 25);
   }

   private int popupW() {
      return Math.max(140, Math.min(340, this.width - 40));
   }

   private int popupX() {
      return Math.max(4, (this.width - this.popupW()) / 2);
   }

   private int popupY() {
      return Math.max(16, this.height / 2 - 46);
   }

   protected void setInitialFocus() {
      if (this.edit && this.popupBox != null) {
         this.setInitialFocus(this.popupBox);
      } else if (this.edit && this.stepSettings && this.stepActionBox != null) {
         this.setInitialFocus(this.stepActionBox);
      } else if (this.edit && this.lineBox != null) {
         this.setInitialFocus(this.lineBox);
      } else {
         super.setInitialFocus();
      }
   }

   private void initEdit() {
      this.rows.clear();
      this.lineBox = null;
      this.answerBox = null;
      this.titleBox = null;
      this.popupBox = null;
      this.setTextBox = null;
      this.setMinBox = null;
      this.setMaxBox = null;
      this.setPointsNameBox = null;
      this.setMinPointsBox = null;
      this.setMaxPointsBox = null;
      this.setActionBox = null;
      this.stepActionBox = null;
      this.lastMouseX = this.width / 2.0;
      this.lastMouseY = this.height / 2.0;
      lastStyle = this.style;
      this.editEnsureStep();
      this.rebuildEditRows();
      this.buildEditWidgets();
   }

   private void buildEditWidgets() {
      if (this.settings >= 0) {
         this.buildSettings();
      } else if (this.stepSettings) {
         this.buildStepSettings();
      } else {
         if (this.editFieldVisible() && !this.adding) {
            this.lineBox = new EditBox(this.font, this.left(), this.editLineY(), this.optionWidth(), 20, Component.empty());
            this.lineBox.setBordered(false);
            this.lineBox.setMaxLength(this.posTopic < 0 ? (this.rootLine >= 0 ? 480 : 480) : (this.editReaction() ? 480 : 480));
            this.lineBox.setHint(Component.translatable(this.editLineKey()).withStyle(ChatFormatting.DARK_GRAY));
            this.suppress = true;
            this.lineBox.setValue(this.editLine());
            this.suppress = false;
            this.lineBox.setResponder(this::editLineTyped);
            this.addRenderableWidget(this.lineBox);
            this.setInitialFocus(this.lineBox);
         }

         int var1 = this.editColX();
         int var2 = this.editColW();
         this.updateEditCompact();
         this.editContentFrom = this.renderables.size();
         int var3 = 30 - (this.editCompact ? this.editScroll : 0);
         NpcProfile.Topic var4 = this.editTopic();
         if (var4 != null && this.posStep == 0 && this.posAnswer < 0 && !this.adding) {
            this.titleBox = new EditBox(this.font, var1, var3 + 12, var2, 20, Component.empty());
            this.titleBox.setMaxLength(64);
            this.titleBox.setHint(Component.translatable("gui.settledlands.edit.hint.title").withStyle(ChatFormatting.DARK_GRAY));
            this.suppress = true;
            this.titleBox.setValue(var4.title == null ? "" : var4.title);
            this.suppress = false;
            this.titleBox.setResponder(this::editTitleTyped);
            this.addRenderableWidget(this.titleBox);
         }

         NpcProfile.Answer var5 = this.editAnswer();
         if (var5 != null && !this.adding) {
            boolean var6 = var5.next != -1;
            this.answerBox = new EditBox(this.font, var1, var3 + 12, var2, 20, Component.empty());
            this.answerBox.setMaxLength(240);
            this.answerBox.setHint(Component.translatable("gui.settledlands.edit.hint.answer").withStyle(ChatFormatting.DARK_GRAY));
            this.suppress = true;
            this.answerBox.setValue(var5.player == null ? "" : var5.player);
            this.suppress = false;
            this.answerBox.setResponder(this::editAnswerTyped);
            this.addRenderableWidget(this.answerBox);
            boolean var7 = var2 < 86;
            int var8 = Math.max(40, (var2 - 6) / 2);
            MutableComponent var9 = Component.translatable("gui.settledlands.edit.goto");
            MutableComponent var10 = Component.translatable("gui.settledlands.edit.end");
            if (var6) {
               var9 = var9.copy().withStyle(ChatFormatting.YELLOW);
            } else {
               var10 = var10.copy().withStyle(ChatFormatting.YELLOW);
            }

            this.addRenderableWidget(Button.builder(var9, var1x -> this.editOpenReply()).bounds(var1, var3 + 38, var7 ? var2 : var8, 20).build());
            this.addRenderableWidget(
               Button.builder(var10, var1x -> this.editSetEnd())
                  .bounds(var7 ? var1 : var1 + var8 + 6, var7 ? var3 + 62 : var3 + 38, var7 ? var2 : Math.max(40, var2 - var8 - 6), 20)
                  .build()
            );
         }

         if (!this.adding) {
            this.buildEditActions();
            this.editContentH = this.editFieldsBottom() + 6 + this.editActionRows() * 24 + 6;
            this.clampEditScroll();
            this.hideEditOutside();
         } else {
            this.buildEditPopup();
         }
      }
   }

   private void buildEditActions() {
      int var1 = this.editColX();
      int var2 = this.editColW();
      List var3 = this.editActionItems();
      int var4 = var3.size();
      byte var5 = 6;
      boolean var6 = this.editTwoColumns();
      int var7 = var6 ? Math.max(60, (var2 - var5) / 2) : Math.max(40, Math.min(210, var2));
      int var8 = this.editCompact ? -this.editScroll : 0;

      for (int var9 = 0; var9 < var4; var9++) {
         int var10 = var6 ? var9 % Math.max(1, (var4 + 1) / 2) : 0;
         int var11 = var6 ? var9 / Math.max(1, (var4 + 1) / 2) : var9;
         int var12 = var6 ? Math.min(Math.max(1, (var4 + 1) / 2), var4 - var10 * Math.max(1, (var4 + 1) / 2)) : var4;
         int var13 = var1 + var2 - (var6 ? 2 - var10 : 1) * (var7 + var5) + var5;
         int var14 = this.editCompact ? this.editFieldsBottom() + 6 + var8 + var11 * 24 : this.editBottom() - 20 - (var12 - 1 - var11) * 24;
         String var15 = ((Component)((Object[])var3.get(var9))[0]).getString();
         Runnable var16 = (Runnable)((Object[])var3.get(var9))[1];
         this.addRenderableWidget(Button.builder(Component.literal(this.clipEdit(var15, var7)), var1x -> var16.run()).bounds(var13, var14, var7, 20).build());
      }
   }

   private int editFieldsBottom() {
      return this.posAnswer >= 0 ? 88 : (this.titleBox != null ? 62 : 24);
   }

   private void buildEditPopup() {
      int var1 = this.popupX();
      int var2 = this.popupW();
      int var3 = this.popupY();
      this.popupBox = new EditBox(this.font, var1 + 12, var3 + 30, var2 - 24, 20, Component.empty());
      this.popupBox.setMaxLength(this.addingTopic ? 64 : 240);
      this.popupBox
         .setHint(
            Component.translatable(this.addingTopic ? "gui.settledlands.edit.ph.topic" : "gui.settledlands.edit.ph.answer").withStyle(ChatFormatting.DARK_GRAY)
         );
      this.addRenderableWidget(this.popupBox);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.edit.save"), var1x -> this.editSavePopup())
            .bounds(var1 + 12, var3 + 58, var2 - 56, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("×"), var1x -> this.editClosePopup()).bounds(var1 + var2 - 38, var3 + 58, 26, 20).build());
      this.setInitialFocus(this.popupBox);
   }

   private void editClosePopup() {
      this.adding = false;
      this.rebuildWidgets();
   }

   private NpcProfile.Answer settingsAnswer() {
      NpcProfile.Step var1 = this.editStep();
      return this.settings >= 0 && var1 != null && this.settings < var1.answers.size() ? var1.answers.get(this.settings) : null;
   }

   private void editOpenSettings(int var1) {
      NpcProfile.Step var2 = this.editStep();
      if (var2 != null && var1 >= 0 && var1 < var2.answers.size()) {
         this.flush();
         this.settings = var1;
         this.rebuildWidgets();
      }
   }

   private void editOpenStepSettings() {
      if (this.posTopic >= 0 && this.posAnswer < 0 && this.editStep() != null) {
         this.flush();
         this.settings = -1;
         this.stepSettings = true;
         this.rebuildWidgets();
      }
   }

   private void editCloseSettings() {
      this.settings = -1;
      this.stepSettings = false;
      this.rebuildWidgets();
   }

   private int setW() {
      return Math.max(220, Math.min(420, this.width - 40));
   }

   private int setX() {
      return Math.max(4, (this.width - this.setW()) / 2);
   }

   private boolean setCompact() {
      return this.height < 280;
   }

   private int setH() {
      return this.setCompact() ? 230 : 258;
   }

   private int setTextY() {
      return this.setCompact() ? 28 : 32;
   }

   private int setRelY() {
      return this.setCompact() ? 56 : 64;
   }

   private int setPointsNameY() {
      return this.setCompact() ? 84 : 96;
   }

   private int setPointsRangeY() {
      return this.setCompact() ? 112 : 128;
   }

   private int setActionY() {
      return this.setCompact() ? 148 : 170;
   }

   private int setSliderY() {
      return this.setActionY() + 24;
   }

   private int setButtonsY() {
      return this.setSliderY() + (this.setCompact() ? 26 : 32);
   }

   private int setY() {
      return Math.max(4, Math.min(this.height / 2 - this.setH() / 2, this.height - this.setH() - 4));
   }

   private void buildSettings() {
      NpcProfile.Answer var1 = this.settingsAnswer();
      if (var1 != null && this.posTopic >= 0) {
         int var2 = this.setX();
         int var3 = this.setY();
         int var4 = this.setW();
         int var5 = var4 - 24;
         this.setTextBox = new EditBox(this.font, var2 + 12, var3 + this.setTextY(), var5, 18, Component.empty());
         this.setTextBox.setMaxLength(240);
         this.setTextBox.setHint(Component.translatable("gui.settledlands.edit.hint.answer").withStyle(ChatFormatting.DARK_GRAY));
         this.suppress = true;
         this.setTextBox.setValue(var1.player == null ? "" : var1.player);
         this.suppress = false;
         this.setTextBox.setResponder(var1x -> this.editSettingsTyped("player", var1x));
         this.addRenderableWidget(this.setTextBox);
         int relationWidth = Math.max(48, Math.min(72, (var5 - 60) / 2));
         this.setMinBox = new EditBox(this.font, var2 + 12, var3 + this.setRelY(), relationWidth, 18, Component.empty());
         this.setMinBox.setMaxLength(3);
         this.setMinBox.setHint(Component.translatable("gui.settledlands.edit.set.rel.min").withStyle(ChatFormatting.DARK_GRAY));
         this.suppress = true;
         this.setMinBox.setValue(String.valueOf(var1.minRelation));
         this.suppress = false;
         this.setMinBox.setResponder(value -> this.editSettingsTyped("minRelation", value));
         this.addRenderableWidget(this.setMinBox);
         this.setMaxBox = new EditBox(this.font, var2 + 20 + relationWidth, var3 + this.setRelY(), relationWidth, 18, Component.empty());
         this.setMaxBox.setMaxLength(3);
         this.setMaxBox.setHint(Component.translatable("gui.settledlands.edit.set.rel.max").withStyle(ChatFormatting.DARK_GRAY));
         this.suppress = true;
         this.setMaxBox.setValue(String.valueOf(var1.maxRelation));
         this.suppress = false;
         this.setMaxBox.setResponder(value -> this.editSettingsTyped("maxRelation", value));
         this.addRenderableWidget(this.setMaxBox);

         this.setPointsNameBox = new EditBox(this.font, var2 + 12, var3 + this.setPointsNameY(), var5, 18, Component.empty());
         this.setPointsNameBox.setMaxLength(32);
         this.suppress = true;
         this.setPointsNameBox.setValue(var1.pointsName == null ? "" : var1.pointsName);
         this.suppress = false;
         this.setPointsNameBox.setResponder(value -> this.editSettingsTyped("pointsName", value));
         this.addRenderableWidget(this.setPointsNameBox);

         int pointWidth = Math.max(44, Math.min(64, (var5 - 84) / 2));
         this.setMinPointsBox = new EditBox(this.font, var2 + 12, var3 + this.setPointsRangeY(), pointWidth, 18, Component.empty());
         this.setMinPointsBox.setMaxLength(7);
         this.setMinPointsBox.setFilter(value -> value.isEmpty() || value.matches("\\d{1,7}"));
         this.setMinPointsBox.setHint(Component.translatable("gui.settledlands.edit.set.points.min").withStyle(ChatFormatting.DARK_GRAY));
         this.suppress = true;
         this.setMinPointsBox.setValue(String.valueOf(var1.minPoints));
         this.suppress = false;
         this.setMinPointsBox.setResponder(value -> this.editSettingsTyped("minPoints", value));
         this.addRenderableWidget(this.setMinPointsBox);

         this.setMaxPointsBox = new EditBox(
            this.font, var2 + 18 + pointWidth, var3 + this.setPointsRangeY(), pointWidth, 18, Component.empty()
         );
         this.setMaxPointsBox.setMaxLength(7);
         this.setMaxPointsBox.setFilter(value -> value.isEmpty() || value.matches("\\d{1,7}"));
         this.setMaxPointsBox.setHint(Component.translatable("gui.settledlands.edit.set.points.max").withStyle(ChatFormatting.DARK_GRAY));
         this.suppress = true;
         this.setMaxPointsBox.setValue(String.valueOf(var1.maxPoints));
         this.suppress = false;
         this.setMaxPointsBox.setResponder(value -> this.editSettingsTyped("maxPoints", value));
         this.addRenderableWidget(this.setMaxPointsBox);

         int sharedX = var2 + 24 + pointWidth * 2;
         int sharedWidth = Math.max(56, var5 - pointWidth * 2 - 12);
         this.addRenderableWidget(
            NpcUi.dropdown(
               sharedX,
               var3 + this.setPointsRangeY(),
               sharedWidth,
               List.of(
                  Component.translatable("gui.settledlands.edit.set.points.personal"),
                  Component.translatable("gui.settledlands.edit.set.points.shared")
               ),
               var1.pointsShared ? 1 : 0,
               index -> this.editSettingsTyped("pointsShared", index == 1 ? "1" : "0")
            )
         );
         this.setActionBox = new EditBox(this.font, var2 + 12, var3 + this.setActionY(), var5 - 72, 18, Component.empty());
         this.setActionBox.setMaxLength(2048);
         this.setActionBox.setHint(Component.translatable("gui.settledlands.edit.set.action.ph").withStyle(ChatFormatting.DARK_GRAY));
         this.suppress = true;
         this.setActionBox.setValue(var1.action == null ? "" : var1.action);
         this.suppress = false;
         this.setActionBox.setResponder(var1x -> this.editSettingsTyped("action", var1x));
         this.addRenderableWidget(this.setActionBox);
         this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.edit.action.add"), var1x -> {
            EditBox var2x = this.setActionBox;
            String var3x = ActionSequence.addSeparator(var2x.getValue());
            this.suppress = true;
            var2x.setValue(var3x);
            this.suppress = false;
            var2x.setCursorPosition(var3x.length());
            this.setInitialFocus(var2x);
            this.editSettingsTyped("action", var3x);
         }).bounds(var2 + 12 + (var5 - 68), var3 + this.setActionY(), 68, 18).build());
         this.addRenderableWidget(
            NpcUi.slider(
               var2 + 12,
               var3 + this.setSliderY(),
               var5,
               -100,
               100,
               var1.relation,
               var0 -> Component.translatable("gui.settledlands.edit.set.rel.delta", new Object[]{(var0 > 0 ? "+" : "") + var0}),
               var1x -> this.editSettingsTyped("relation", String.valueOf(var1x))
            )
         );
         int var7 = Math.min(150, Math.max(90, var5 / 3));
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.settledlands.edit.set.del"), var1x -> this.editSettingsDelete())
               .bounds(var2 + 12, var3 + this.setButtonsY(), var7, 18)
               .build()
         );
         int var8 = Math.max(50, Math.min(90, var5 / 4));
         int var9 = var2 + var4 - 12 - var8;
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.settledlands.edit.set.done"), var1x -> this.editCloseSettings())
               .bounds(var9, var3 + this.setButtonsY(), var8, 18)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.literal("×"), var1x -> this.editCloseSettings()).bounds(var9 - 28, var3 + this.setButtonsY(), 22, 18).build()
         );
         this.setInitialFocus(this.setTextBox);
      } else {
         this.settings = -1;
         this.initEdit();
      }
   }

   private int stepSetH() {
      return 112;
   }

   private int stepSetY() {
      return Math.max(4, Math.min(this.height / 2 - this.stepSetH() / 2, this.height - this.stepSetH() - 4));
   }

   private void buildStepSettings() {
      NpcProfile.Step step = this.editStep();
      if (step == null || this.posTopic < 0) {
         this.stepSettings = false;
         this.rebuildWidgets();
         return;
      }

      int x = this.setX();
      int y = this.stepSetY();
      int width = this.setW();
      int innerWidth = width - 24;
      int actionY = y + 44;
      this.stepActionBox = new EditBox(this.font, x + 12, actionY, Math.max(40, innerWidth - 72), 18, Component.empty());
      this.stepActionBox.setMaxLength(2048);
      this.suppress = true;
      this.stepActionBox.setValue(step.action == null ? "" : step.action);
      this.suppress = false;
      this.stepActionBox.setResponder(this::editStepActionTyped);
      this.addRenderableWidget(this.stepActionBox);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.edit.action.add"), button -> {
            EditBox box = this.stepActionBox;
            String value = ActionSequence.addSeparator(box.getValue());
            this.suppress = true;
            box.setValue(value);
            this.suppress = false;
            box.setCursorPosition(value.length());
            this.setInitialFocus(box);
            this.editStepActionTyped(value);
         }).bounds(x + width - 80, actionY, 68, 18).build()
      );
      int doneX = x + width - 12 - 82;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.edit.set.done"), button -> this.editCloseSettings())
            .bounds(doneX, y + 82, 82, 18)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("×"), button -> this.editCloseSettings()).bounds(doneX - 28, y + 82, 22, 18).build()
      );
      this.setInitialFocus(this.stepActionBox);
   }

   private void drawStepSettings(GuiGraphics graphics) {
      if (!this.stepSettings || this.stepActionBox == null) {
         return;
      }
      int x = this.setX();
      int y = this.stepSetY();
      int width = this.setW();
      int height = this.stepSetH();
      graphics.fill(0, 0, this.width, this.height, -1879048192);
      graphics.fill(x, y, x + width, y + height, -267251178);
      graphics.fill(x, y, x + width, y + 1, -10855838);
      graphics.fill(x, y + height - 1, x + width, y + height, -10855838);
      graphics.fill(x, y, x + 1, y + height, -10855838);
      graphics.fill(x + width - 1, y, x + width, y + height, -10855838);
      graphics.fill(x + 1, y + 22, x + width - 1, y + 23, 1090519039);
      graphics.drawString(this.font, Component.translatable("gui.settledlands.edit.line.settings").getString(), x + 12, y + 8, -11702, true);
      graphics.drawString(this.font, Component.translatable("gui.settledlands.edit.line.action").getString(), x + 12, y + 30, -2302756, true);
   }

   private void editSettingsTyped(String var1, String var2) {
      if (!this.suppress && this.posTopic >= 0) {
         NpcProfile.Answer var3 = this.settingsAnswer();
         NpcProfile.Step var4 = this.editStep();
         if (var3 != null && var4 != null) {
            String var5 = var2;
            switch (var1) {
               case "player":
                  var3.player = var2;
                  break;
               case "minRelation":
                  var3.minRelation = this.editNumber(var2, var3.minRelation, 0);
                  if (var3.maxRelation < var3.minRelation) {
                     var3.maxRelation = var3.minRelation;
                     this.syncBox(this.setMaxBox, String.valueOf(var3.maxRelation));
                  }

                  var5 = String.valueOf(var3.minRelation);
                  break;
               case "maxRelation":
                  var3.maxRelation = this.editNumber(var2, var3.maxRelation, 100);
                  if (var3.minRelation > var3.maxRelation) {
                     var3.minRelation = var3.maxRelation;
                     this.syncBox(this.setMinBox, String.valueOf(var3.minRelation));
                  }

                  var5 = String.valueOf(var3.maxRelation);
                  break;
               case "pointsName":
                  var3.pointsName = NpcPoints.cleanName(var2);
                  var5 = var3.pointsName;
                  break;
               case "minPoints":
                  var3.minPoints = this.editPointsNumber(var2, var3.minPoints, 1);
                  if (var3.maxPoints < var3.minPoints) {
                     var3.maxPoints = var3.minPoints;
                     this.syncBox(this.setMaxPointsBox, String.valueOf(var3.maxPoints));
                  }
                  var5 = String.valueOf(var3.minPoints);
                  break;
               case "maxPoints":
                  var3.maxPoints = this.editPointsNumber(var2, var3.maxPoints, NpcPoints.MAX_VALUE);
                  if (var3.minPoints > var3.maxPoints) {
                     var3.minPoints = var3.maxPoints;
                     this.syncBox(this.setMinPointsBox, String.valueOf(var3.minPoints));
                  }
                  var5 = String.valueOf(var3.maxPoints);
                  break;
               case "pointsShared":
                  var3.pointsShared = "1".equals(var2);
                  var5 = var3.pointsShared ? "1" : "0";
                  break;
               case "action":
                  var3.action = var2;
                  break;
               case "relation":
                  var3.relation = NpcProfile.clamp(this.editNumber(var2, var3.relation, 0), -100, 100);
                  var5 = String.valueOf(var3.relation);
                  break;
               default:
                  return;
            }

            this.queue("answer.field", this.posTopic, this.posStep, this.settings, var1, var5);
            this.rebuildEditRows();
         }
      }
   }

   private int editNumber(String var1, int var2, int var3) {
      String var4 = var1 == null ? "" : var1.trim();
      if (var4.isEmpty()) {
         return var3;
      } else {
         int var5;
         try {
            var5 = Integer.parseInt(var4);
         } catch (RuntimeException var7) {
            return NpcProfile.clamp(var2, 0, 100);
         }

         return NpcProfile.clamp(var5, 0, 100);
      }
   }

   private int editPointsNumber(String value, int current, int emptyDefault) {
      String trimmed = value == null ? "" : value.trim();
      if (trimmed.isEmpty()) {
         return emptyDefault;
      }
      try {
         return NpcProfile.clamp(Integer.parseInt(trimmed), 0, NpcPoints.MAX_VALUE);
      } catch (RuntimeException exception) {
         return NpcProfile.clamp(current, 0, NpcPoints.MAX_VALUE);
      }
   }

   private void editSettingsDelete() {
      int var1 = this.settings;
      this.settings = -1;
      this.editDeleteRow(var1);
      this.rebuildWidgets();
   }

   private void drawEdit(GuiGraphics var1, int var2, int var3) {
      int var4 = this.left();
      if (this.editFieldVisible() && !this.adding && this.settings < 0) {
         int var5 = this.editLineY();
         var1.fill(var4 - 3, var5 - 2, var4 + this.optionWidth() + 3, var5 + 18, 822083583);
         var1.fill(var4 - 3, var5 + 18, var4 + this.optionWidth() + 3, var5 + 19, 1627378250);
         if (this.lineBox != null && this.lineBox.getValue().isEmpty()) {
            var1.drawString(this.font, Component.translatable(this.editLineKey()).getString(), var4 + 1, var5 + 6, -8750470, true);
         }
      }

      if (this.editCanDelete()) {
         int deleteButtonX = var4 + this.optionWidth() + 5;

         for (int row = 0; row < this.rows.size(); row++) {
            if (this.canDeleteRow(this.rows.get(row).action())) {
               int rowY = this.rowTop() + row * 12 + this.offY();
               int buttonY = rowY - 1;
               boolean hovered = var2 >= deleteButtonX && var2 <= deleteButtonX + 14 && var3 >= rowY - 2 && var3 <= rowY + 10;
               this.drawDeleteButton(var1, deleteButtonX, buttonY, hovered);
            }
         }
      }

      if (this.settings < 0 && !this.stepSettings && !this.adding && this.posTopic >= 0 && this.posAnswer < 0 && this.editStep() != null && this.lineBox != null) {
         int lineGearX = var4 + this.optionWidth() + 25;
         int lineGearY = this.lineBox.getY() + 3;
         boolean lineGearHover = var2 >= lineGearX - 3 && var2 <= lineGearX + 14 && var3 >= lineGearY - 2 && var3 <= lineGearY + 14;
         this.drawGearButton(var1, lineGearX, lineGearY, lineGearHover);
      }

      if (this.settings < 0 && !this.stepSettings && !this.adding && this.posTopic >= 0 && this.editStep() != null) {
         int gearButtonX = var4 + this.optionWidth() + 25;

         for (int row = 0; row < this.rows.size(); row++) {
            if (!isChainMarker(this.rows.get(row).action())) {
               int rowY = this.rowTop() + row * 12 + this.offY();
               int buttonY = rowY - 1;
               boolean hovered = var2 >= gearButtonX - 3 && var2 <= gearButtonX + 15 && var3 >= rowY - 2 && var3 <= rowY + 10;
               this.drawGearButton(var1, gearButtonX, buttonY, hovered);
            }
         }
      }

      if (!this.adding) {
         int var13 = this.editColX();
         var1.drawString(this.font, this.clipEdit(this.editPlace(), this.editColW()), var13, 18, -11702, true);
         if (this.titleBox != null && !this.outsideEdit(this.titleBox.getY() - 11)) {
            var1.drawString(
               this.font, Component.translatable("gui.settledlands.edit.buttontitle").getString(), var13, this.titleBox.getY() - 11, -2302756, true
            );
         }

         if (this.answerBox != null && !this.outsideEdit(this.answerBox.getY() - 11)) {
            var1.drawString(this.font, Component.translatable("gui.settledlands.edit.answer").getString(), var13, this.answerBox.getY() - 11, -2302756, true);
         }

         this.drawEditScrollbar(var1);
      }

      long var14 = System.currentTimeMillis();
      if (var14 < this.noteUntil && !this.note.isEmpty()) {
         var1.drawString(this.font, this.note, var4, this.height - 22, -11702, true);
      }

      if (this.adding) {
         this.drawEditPopup(var1);
      } else if (this.settings >= 0) {
         this.drawSettings(var1);
      }
   }

   private boolean outsideEdit(int var1) {
      return var1 < 28 || var1 > this.editBottom();
   }

   private void drawEditScrollbar(GuiGraphics var1) {
      int var2 = this.editScrollMax();
      if (this.editCompact && var2 > 0) {
         int var3 = this.editColX() - 8;
         byte var4 = 30;
         int var5 = this.editBottom() - 30;
         var1.fill(var3, var4, var3 + 3, var4 + var5, 1342177280);
         int var6 = Math.max(16, var5 * var5 / Math.max(1, this.editContentH));
         int var7 = var4 + (var5 - var6) * this.editScroll / var2;
         var1.fill(var3, var7, var3 + 3, var7 + var6, -11702);
      }
   }

   private void drawEditIconFrame(GuiGraphics graphics, int x, int y, boolean hovered) {
      int edge = hovered ? 0xFFFFD42A : 0xFFE6E6E6;
      int fill = hovered ? 0xFF514000 : 0xFF202124;
      graphics.fill(x, y, x + 12, y + 12, 0xFF000000);
      graphics.fill(x + 1, y + 1, x + 11, y + 11, edge);
      graphics.fill(x + 2, y + 2, x + 10, y + 10, fill);
   }

   private void drawGearButton(GuiGraphics graphics, int x, int y, boolean hovered) {
      this.drawEditIconFrame(graphics, x, y, hovered);
      int icon = hovered ? 0xFFFFD42A : 0xFFFFFFFF;
      graphics.fill(x + 4, y + 2, x + 8, y + 10, icon);
      graphics.fill(x + 2, y + 4, x + 10, y + 8, icon);
      graphics.fill(x + 3, y + 3, x + 9, y + 9, icon);
      graphics.fill(x + 5, y + 5, x + 7, y + 7, 0xFF171717);
   }

   private void drawDeleteButton(GuiGraphics graphics, int x, int y, boolean hovered) {
      this.drawEditIconFrame(graphics, x, y, hovered);
      graphics.drawString(this.font, "×", x + 3, y + 1, hovered ? 0xFF171717 : 0xFFFFFFFF, true);
   }

   private void drawSettings(GuiGraphics var1) {
      if (this.settingsAnswer() != null) {
         int var2 = this.setX();
         int var3 = this.setY();
         int var4 = this.setW();
         var1.fill(0, 0, this.width, this.height, -1879048192);
         int var5 = this.setH();
         var1.fill(var2, var3, var2 + var4, var3 + var5, -267251178);
         var1.fill(var2, var3, var2 + var4, var3 + 1, -10855838);
         var1.fill(var2, var3 + var5 - 1, var2 + var4, var3 + var5, -10855838);
         var1.fill(var2, var3, var2 + 1, var3 + var5, -10855838);
         var1.fill(var2 + var4 - 1, var3, var2 + var4, var3 + var5, -10855838);
         var1.fill(var2 + 1, var3 + 20, var2 + var4 - 1, var3 + 21, 1090519039);
         var1.drawString(this.font, Component.translatable("gui.settledlands.edit.set.title").getString(), var2 + 12, var3 + 8, -11702, true);
         var1.drawString(this.font, Component.translatable("gui.settledlands.edit.set.text").getString(), var2 + 12, var3 + 22, -2302756, true);
         var1.drawString(this.font, Component.translatable("gui.settledlands.edit.set.rel").getString(), var2 + 12, var3 + this.setRelY() - 10, -2302756, true);
         var1.drawString(
            this.font,
            Component.translatable("gui.settledlands.edit.set.points.name").getString(),
            var2 + 12,
            var3 + this.setPointsNameY() - 10,
            -2302756,
            true
         );
         var1.drawString(
            this.font,
            Component.translatable("gui.settledlands.edit.set.points.range").getString(),
            var2 + 12,
            var3 + this.setPointsRangeY() - 10,
            -2302756,
            true
         );
         var1.drawString(
            this.font, Component.translatable("gui.settledlands.edit.set.action").getString(), var2 + 12, var3 + this.setActionY() - 10, -2302756, true
         );
      }
   }

   private String clipEdit(String var1, int var2) {
      if (var1 != null && this.font.width(var1) > var2) {
         return this.font.plainSubstrByWidth(var1, Math.max(8, var2 - 8)) + "…";
      } else {
         return var1 == null ? "" : var1;
      }
   }

   private void drawEditPopup(GuiGraphics var1) {
      int var2 = this.popupX();
      int var3 = this.popupY();
      int var4 = this.popupW();
      var1.fill(0, 0, this.width, this.height, -1879048192);
      var1.fill(var2, var3, var2 + var4, var3 + 92, -267251178);
      var1.fill(var2, var3, var2 + var4, var3 + 1, -10855838);
      var1.fill(var2, var3 + 92 - 1, var2 + var4, var3 + 92, -10855838);
      var1.fill(var2, var3, var2 + 1, var3 + 92, -10855838);
      var1.fill(var2 + var4 - 1, var3, var2 + var4, var3 + 92, -10855838);
      var1.drawString(
         this.font,
         Component.translatable(this.addingTopic ? "gui.settledlands.edit.newtopic" : "gui.settledlands.edit.newanswer").getString(),
         var2 + 12,
         var3 + 10,
         -11702,
         true
      );
   }

   private boolean editClick(double var1, double var3, int var5) {
      if (!this.adding && this.settings < 0 && !this.stepSettings) {
         int var6 = this.left() + this.optionWidth() + 26;
         if (this.posTopic >= 0 && this.posAnswer < 0 && this.editStep() != null && this.lineBox != null) {
            int lineGearY = this.lineBox.getY() + 4;
            if (var1 >= var6 - 4 && var1 <= var6 + 14 && var3 >= lineGearY - 2 && var3 <= lineGearY + 14) {
               this.editOpenStepSettings();
               return true;
            }
         }
         if (this.posTopic >= 0 && this.editStep() != null && var1 >= var6 - 4 && var1 <= var6 + 12 + 2) {
            int var7 = this.rowAtY(var3);
            if (var7 >= 0 && !isChainMarker(this.rows.get(var7).action())) {
               this.editOpenSettings(var7);
               return true;
            }
         }

         if (this.editCanDelete()) {
            int var9 = this.left() + this.optionWidth() + 8;
            if (var1 >= var9 - 3 && var1 <= var9 + 12) {
               int var8 = this.rowAtY(var3);
               if (var8 >= 0 && this.canDeleteRow(this.rows.get(var8).action())) {
                  this.editDeleteRow(var8);
                  return true;
               }
            }
         }

         int var10 = this.rowAt(var1, var3);
         if (var10 >= 0) {
            this.editOpenRow(var10);
            return true;
         } else {
            return super.mouseClicked(var1, var3, var5);
         }
      } else {
         return super.mouseClicked(var1, var3, var5);
      }
   }

   private void editOpenRow(int var1) {
      if (var1 >= 0 && var1 < this.rows.size()) {
         int var2 = this.rows.get(var1).action();
         if (var2 == -2) {
            this.editGoNext();
         } else if (var2 != -3) {
            if (this.posTopic < 0) {
               if (var2 == -11 || var2 == -12) {
                  this.flush();
                  this.rootLine = var2 == -11 ? -2 : -3;
                  this.rebuildWidgets();
                  return;
               }
               if (var2 == -10) {
                  this.flush();
                  this.rootLine = -1;
                  this.rebuildWidgets();
                  return;
               }

               if (isRootLine(var2)) {
                  this.flush();
                  this.rootLine = rootLineIndex(var2);
                  this.rebuildWidgets();
                  return;
               }

               this.editEnterTopic(var2);
            } else {
               this.editEnterAnswer(var2);
            }
         }
      }
   }

   private void editGoNext() {
      int var1 = NpcProfile.editorNext(this.editTopic(), this.posStep, this.addedStep);
      if (var1 >= 0) {
         this.flush();
         this.posAnswer = -1;
         this.posStep = var1;
         this.addedStep = -1;
         this.rebuildEditRows();
         this.rebuildWidgets();
      }
   }

   private boolean editKey(int var1, int var2, int var3) {
      if (this.settings >= 0 || this.stepSettings) {
         if (var1 != 256 && var1 != 257 && var1 != 335) {
            return super.keyPressed(var1, var2, var3);
         } else {
            this.editCloseSettings();
            return true;
         }
      } else if (this.adding) {
         if (var1 == 256) {
            this.editClosePopup();
            return true;
         } else if (var1 != 257 && var1 != 335) {
            return super.keyPressed(var1, var2, var3);
         } else {
            this.editSavePopup();
            return true;
         }
      } else if (var1 == 267 && this.scrollEditBy(24)) {
         return true;
      } else if (var1 == 266 && this.scrollEditBy(-24)) {
         return true;
      } else if (var1 == 265) {
         this.selected = this.rows.isEmpty() ? 0 : (this.selected - 1 + this.rows.size()) % this.rows.size();
         return true;
      } else if (var1 == 264) {
         this.selected = this.rows.isEmpty() ? 0 : (this.selected + 1) % this.rows.size();
         return true;
      } else {
         boolean var4 = this.lineBox != null && this.lineBox.isFocused();
         if (!var4 && (var1 == 257 || var1 == 335) && !this.rows.isEmpty()) {
            this.editOpenRow(this.selected);
            return true;
         } else {
            return super.keyPressed(var1, var2, var3);
         }
      }
   }

   private void tickEdit() {
      if (!this.pending.isEmpty() && System.currentTimeMillis() >= this.pendingAt) {
         this.flush();
      }
   }

   public static boolean isOpen() {
      return active != null;
   }

   public static float fovFactor() {
      return active == null ? 1.0F : 100.0F / active.zoom();
   }

   public static boolean overrideCamera() {
      return active != null && (active.cameraOnFace() || active.freeLook);
   }

   public static float[] cameraAngles(Minecraft var0) {
      if (active == null) {
         return null;
      } else {
         if (active.cameraOnFace()) {
            Entity var1 = var0.level == null ? null : var0.level.getEntity(active.focusEntityId);
            LocalPlayer var2 = var0.player;
            if (var1 != null && var2 != null) {
               double var3 = var1.getX() - var2.getX();
               double var5 = var1.getZ() - var2.getZ();
               double var7 = var1.getEyeY() - var2.getEyeY();
               double var9 = Math.sqrt(var3 * var3 + var5 * var5);
               float var11 = (float)(Math.toDegrees(Math.atan2(var5, var3)) - 90.0);
               float var12 = (float)(-Math.toDegrees(Math.atan2(var7, var9)));
               return new float[]{var11, var12};
            }
         }

         return active.freeLook ? new float[]{active.camYaw, active.camPitch} : null;
      }
   }

   private static Component component(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         MutableComponent var1 = Component.empty();
         int var2 = 0;

         while (var2 < var0.length()) {
            int var3 = var0.indexOf(35, var2);
            if (var3 < 0) {
               var1.append(Component.literal(var0.substring(var2)));
               break;
            }

            if (var3 > var2) {
               var1.append(Component.literal(var0.substring(var2, var3)));
            }

            int var4 = var3 + 1;

            while (var4 < var0.length() && "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.".indexOf(var0.charAt(var4)) >= 0) {
               var4++;
            }

            String var5 = var0.substring(var3 + 1, var4);
            if (!var5.isEmpty() && var5.contains(".")) {
               if (var4 < var0.length() && var0.charAt(var4) == 1) {
                  int var6 = var0.indexOf(2, var4);
                  int var7 = var6 < 0 ? var0.length() : var6;
                  String[] var8 = var0.substring(var4 + 1, var7).split(String.valueOf('\u0001'), -1);
                  Object[] var9 = new Object[var8.length];

                  for (int var10 = 0; var10 < var8.length; var10++) {
                     var9[var10] = component(var8[var10]);
                  }

                  var1.append(Component.translatable(var5, var9));
                  var2 = var6 < 0 ? var0.length() : var6 + 1;
               } else {
                  var1.append(Component.translatable(var5));
                  var2 = var4;
               }
            } else {
               var1.append(Component.literal(var0.substring(var3, var4)));
               var2 = var4;
            }
         }

         return var1;
      } else {
         return Component.empty();
      }
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (this.observer) {
         if (var1 == 256) {
            this.onClose();
         }
         return true;
      }
      if (CommandTabCompleter.handleKey(this, var1, var2, var3)) {
         return true;
      }
      if (!this.edit && this.continuation() && (var1 == 257 || var1 == 335 || var1 == 32 || var1 == 69)) {
         this.continueTalk();
         return true;
      }
      return this.edit ? this.editKey(var1, var2, var3) : super.keyPressed(var1, var2, var3);
   }

   private record Row(Component text, Component hint, int action) {
   }
}
