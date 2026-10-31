package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class NpcProfile {
   public static final int MAX_WELCOME = 480;
   public static final int MAX_NAME = 32;
   public static final int MAX_NPC_TITLE = 32;
   public static final int MAX_TITLE = 64;
   public static final int MAX_KIND = 16;
   public static final int MAX_LINE = 480;
   public static final int MAX_ANSWER = 240;
   public static final int MAX_REACTION = 480;
   public static final int MAX_DESC = 240;
   public static final int MAX_ACTION = 2048;
   public static final int MAX_TEXTURE = 96;
   public static final int MAX_REP_NAME = 24;
   public static final int MAX_TOPICS = 8;
   public static final int MAX_WELCOME_LINES = 6;
   public static final int MAX_STEPS = 16;
   public static final int MAX_ANSWERS = 6;
   public static final int MAX_VARIANTS = 5;
   public static final int MAX_OFFERS = 32;
   public static final int MAX_QUESTS = 6;
   public static final int MAX_MOBILITY_POINTS = 32;
   public static final int MAX_MOBILITY_COORDINATES = 64;
   public static final int MAX_MOBILITY_DURATION = 16;
   public static final int MAX_MOBILITY_SCHEDULE_ENTRIES = 32;
   public static final int MOBILITY_DAY_SECONDS = 1200;
   public static final int QUEST_KIND_ITEM = 0;
   public static final int QUEST_KIND_POINTS = 1;
   public static final char ARG = '\u0001';
   public static final char ARG_END = '\u0002';
   public static final int QUEST_ROLES = 4;
   public static final int QUEST_NONE = 0;
   public static final int QUEST_OFFER = 1;
   public static final int QUEST_REVISIT = 2;
   public static final int QUEST_HAND = 3;
   public static final int QUEST_WHERE = 4;
   public static final int MAX_COUNT = 64;
   public static final int END = -1;
   public static final int TOPIC_BASE = 1000;
   public static final int TYPE_REPLY = 0;
   public static final int TYPE_TRADE = 1;
   public static final int TYPE_JOB = 2;
   public static final int TYPE_QUEST = 3;
   public static final int TYPE_EXIT = 4;
   public static final String[] TYPE_KEYS = new String[]{
      "gui.settledlands.type.reply", "gui.settledlands.type.trade", "gui.settledlands.type.job", "gui.settledlands.type.quest", "gui.settledlands.type.exit"
   };
   public static final int TYPE_COUNT = TYPE_KEYS.length;
   public static final int POSE_COUNT = 3;
   public static final int COLOR_COUNT = 4;
   public static final int STYLE_COUNT = 2;
   public static final int EXIT_KEYS = 2;
   public static final int[] REP_STEPS = new int[]{20, 40, 60, 80};
   public String name = "";
   public String identifier = "";
   public String title = "";
   public String texture = "";
   public String focusNpcUuid = "";
   public int scale = 100;
   public int pose = 0;
   public boolean nameTag = true;
   public boolean lookAtPlayer = true;
   public final List<String> reputationNames = new ArrayList<>(List.of("Враждебный", "Настороженный", "Нейтральный", "Дружелюбный", "Преданный"));
   public int defaultRelation = 0;
   public NpcDialogueGraph dialogueGraph;
   public NpcDialogueGraph walkGraph;
   public NpcDialogueGraph animGraph;
   public String welcome = "";
   public final List<String> welcomeLines = new ArrayList<>();
   public int style = 0;
   public int textColor = 0;
   public int nameColor = 1;
   public int typingSpeed = 30;
   public String voice = "";
   public int voicePitch = 0;
   public boolean showSpeaker = true;
   public boolean keyHints = true;
   public boolean answerNumbers = false;
   public boolean showDialogueTab = true;
   public boolean showTradeTab = true;
   public boolean showQuestTab = true;
   public boolean showPhrasesTab = false;
   public boolean questDropButton = false;
   public boolean questOfferClean = false;
   public int defaultTab = 0;
   public int maxPerPurchase = 8;
   public int discount = 50;
   public final List<NpcProfile.Offer> offers = new ArrayList<>();
   public final List<NpcProfile.Quest> quests = new ArrayList<>();
   public final List<NpcProfile.Topic> topics = new ArrayList<>();
   public String escortCode = "";
   public String escortCoordinates = "";
   public String escortTravelLine = "Мы почти пришли";
   public String escortArrivalLine = "Мы пришли. Спасибо за сопровождение.";
   public boolean idleWalking = false;
   public String mobilityGroupId = "";
   public String mobilityGroupName = "";
   public final List<NpcProfile.MobilityPoint> mobilityPoints = new ArrayList<>();
   public final List<NpcProfile.MobilityScheduleEntry> mobilitySchedule = new ArrayList<>();
   public int zoom = 100;
   public int offsetX = 0;
   public int offsetY = 0;
   public boolean cameraOnFace = false;
   public boolean blockMouseLook = false;
   public int dim = 45;
   public int blur = 0;
   public boolean muteHud = true;
   public boolean vignette = true;
   public int radius = 4;
   public int exitKey = 0;
   public boolean blockMovement = true;
   public boolean pauseWorld = false;
   public int exitAction = 0;

   public static NpcProfile.Quest newQuest(ItemStack var0, ItemStack var1) {
      NpcProfile.Quest var2 = new NpcProfile.Quest();
      var2.item = var0 == null ? ItemStack.EMPTY : var0;
      var2.reward = var1 == null ? ItemStack.EMPTY : var1;
      return var2;
   }

   public static int normalTopics(NpcProfile var0) {
      int var1 = 0;

      for (NpcProfile.Topic var3 : var0.topics) {
         if (!isQuestTopic(var3)) {
            var1++;
         }
      }

      return var1;
   }

   public static boolean isQuestTopic(NpcProfile.Topic var0) {
      return var0 != null && var0.questRole >= 1 && var0.questRole <= 4;
   }

   public static boolean questAction(String var0) {
      return var0 != null && var0.startsWith("quest:");
   }

   public static boolean readsAfterAction(int var0, int var1) {
      return var0 >= 0 && var0 != var1;
   }

   public int questTopicIndex(int var1, int var2) {
      if (var2 >= 1 && var2 <= 4 && var1 >= 0) {
         int var3 = var1 * 4 + (var2 - 1);
         int var4 = 0;

         for (int var5 = 0; var5 < this.topics.size(); var5++) {
            if (isQuestTopic(this.topics.get(var5))) {
               if (var4 == var3) {
                  return var5;
               }

               var4++;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public static int questOfTopic(NpcProfile var0, int var1) {
      if (var0 != null && var1 >= 0 && var1 < var0.topics.size() && isQuestTopic(var0.topics.get(var1))) {
         int var2 = 0;

         for (int var3 = 0; var3 < var1; var3++) {
            if (isQuestTopic(var0.topics.get(var3))) {
               var2++;
            }
         }

         int var4 = var2 / 4;
         return var4 < var0.quests.size() ? var4 : -1;
      } else {
         return -1;
      }
   }

   public static NpcProfile.Topic newQuestTopic(NpcProfile.Quest var0, int var1, int var2) {
      String var3 = switch (var1) {
         case 2 -> "Задание: напоминание";
         case 3 -> "Задание: сдача";
         case 4 -> "Задание: где взять";
         default -> "Задание: знакомство";
      };
      NpcProfile.Topic var4 = new NpcProfile.Topic(var3, 0);
      var4.questRole = var1;
      NpcProfile.Step var5 = new NpcProfile.Step();
      switch (var1) {
         case 2:
            var5.line = var0 != null && var0.text != null && !var0.text.isEmpty() ? var0.text : "Ещё не принёс? Мне нужно %quest%.";
            break;
         case 3:
            var5.line = var0 != null && var0.done != null && !var0.done.isEmpty() ? var0.done : "О, это оно! Держи %reward%.";
            break;
         case 4:
            var5.line = "Где взять %quest%? Обычно такое ищут в шахтах и пещерах — загляни в подсказку предмета, там видно, из чего он получается.";
            break;
         default:
            var5.line = var0 != null && var0.text != null && !var0.text.isEmpty() ? var0.text : "Принеси мне %quest% — и я в долгу не останусь.";
      }

      if (var1 == 1) {
         var5.answers.add(questAnswer("#gui.settledlands.quest.take", "quest:take"));
      } else if (var1 == 3) {
         var5.answers.add(questAnswer(var0 != null && var0.points() ? "#gui.settledlands.quest.handinpoints" : "#gui.settledlands.quest.handin", "quest:hand"));
      }

      if (var2 >= 0 && (var1 == 1 || var1 == 2)) {
         NpcProfile.Answer var6 = questAnswer("#gui.settledlands.quest.where", "");
         var6.next = 1000 + var2;
         var5.answers.add(var6);
      }

      if (var1 == 2) {
         var5.answers.add(questAnswer("#gui.settledlands.quest.abandon", "quest:drop"));
      }

      var4.steps.add(var5);
      return var4;
   }

   private static NpcProfile.Answer questAnswer(String var0, String var1) {
      NpcProfile.Answer var2 = new NpcProfile.Answer();
      var2.player = var0;
      var2.action = var1;
      var2.next = -1;
      return var2;
   }

   public static void ensureQuestDrop(NpcProfile var0, boolean var1) {
      if (var0 != null) {
         var0.questDropButton = true;
         if (!var1) {
            for (int var2 = 0; var2 < var0.quests.size(); var2++) {
               int var3 = var0.questTopicIndex(var2, 2);
               if (var3 >= 0 && var3 < var0.topics.size()) {
                  NpcProfile.Topic var4 = var0.topics.get(var3);
                  if (!hasDropAnswer(var4)) {
                     NpcProfile.Step var5 = firstAnswerStep(var4);
                     if (var5 != null && var5.answers.size() < 6) {
                        var5.answers.add(questAnswer("#gui.settledlands.quest.abandon", "quest:drop"));
                     }
                  }
               }
            }
         }
      }
   }

   public static void ensureQuestOfferClean(NpcProfile var0, boolean var1) {
      if (var0 != null) {
         var0.questOfferClean = true;
         if (!var1) {
            for (int var2 = 0; var2 < var0.quests.size(); var2++) {
               int var3 = var0.questTopicIndex(var2, 1);
               if (var3 >= 0 && var3 < var0.topics.size()) {
                  for (NpcProfile.Step var5 : var0.topics.get(var3).steps) {
                     var5.answers.removeIf(var0x -> "quest:decline".equals(ActionSequence.first(var0x.action)));
                  }
               }
            }
         }
      }
   }

   private static boolean hasDropAnswer(NpcProfile.Topic var0) {
      for (NpcProfile.Step var2 : var0.steps) {
         for (NpcProfile.Answer var4 : var2.answers) {
            if ("quest:drop".equals(ActionSequence.first(var4.action))) {
               return true;
            }
         }
      }

      return false;
   }

   private static NpcProfile.Step firstAnswerStep(NpcProfile.Topic var0) {
      if (var0.steps.isEmpty()) {
         return null;
      } else {
         for (NpcProfile.Step var2 : var0.steps) {
            if (!var2.answers.isEmpty()) {
               return var2;
            }
         }

         return var0.steps.get(0);
      }
   }

   public static void syncQuestTopics(NpcProfile var0) {
      if (var0 != null) {
         ArrayList var1 = new ArrayList();

         for (NpcProfile.Topic var3 : var0.topics) {
            if (isQuestTopic(var3)) {
               var1.add(var3);
            }
         }

         var0.topics.removeIf(NpcProfile::isQuestTopic);
         assembleQuestDialogs(var0, var1);
      }
   }

   private static void assembleQuestDialogs(NpcProfile var0, List<NpcProfile.Topic> var1) {
      ArrayList var2 = new ArrayList();

      for (int var3 = 0; var3 < var0.quests.size(); var3++) {
         var2.add(new ArrayList<>(Arrays.asList(null, null, null, null)));
      }

      int var10 = 0;
      int var4 = -1;

      for (NpcProfile.Topic var6 : var1) {
         int var7 = var6.questRole - 1;
         if (var7 >= 0 && var7 < 4) {
            if (var7 <= var4) {
               var10++;
            }

            var4 = var7;
            if (var10 < var2.size() && ((List)var2.get(var10)).get(var7) == null) {
               ((List)var2.get(var10)).set(var7, var6);
            }
         }
      }

      for (int var11 = 0; var11 < var2.size(); var11++) {
         int var12 = var0.topics.size();
         int var13 = var12 + 3;

         for (int var8 = 0; var8 < 4; var8++) {
            NpcProfile.Topic var9 = (NpcProfile.Topic)((List)var2.get(var11)).get(var8);
            if (var9 == null) {
               var9 = newQuestTopic(var0.quests.get(var11), 1 + var8, var8 == 3 ? -1 : var13);
            }

            var0.topics.add(var9);
         }
      }
   }

   public static String clean(String var0, int var1) {
      if (var0 == null) {
         return "";
      } else {
         String var2 = var0.replace('\n', ' ').replace('\r', ' ').replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s{2,}", " ").trim();
         return var2.length() > var1 ? var2.substring(0, var1) : var2;
      }
   }

   public static int clamp(int var0, int var1, int var2) {
      return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
   }

   public static String fill(String var0, Player var1, int var2) {
      if (var0 != null && !var0.isEmpty()) {
         String var3 = var1 == null ? "" : var1.getName().getString();
         return var0.replace("%player%", var3).replace("%relation%", String.valueOf(var2));
      } else {
         return "";
      }
   }

   public int priceFor(int var1, int var2) {
      double var3 = 1.0 - clamp(this.discount, 0, 90) / 100.0 * clamp(var2, 0, 100) / 100.0;
      return Math.max(1, (int)Math.round(var1 * var3));
   }

   public static String lineFor(NpcProfile.Step var0, int var1) {
      if (var0 == null) {
         return "";
      } else {
         String var2 = var0.line == null ? "" : var0.line;
         int var3 = -1;

         for (NpcProfile.Variant var5 : var0.variants) {
            if (var5 != null && var5.text != null && !var5.text.isEmpty() && var5.minRelation <= var1 && var5.minRelation >= var3) {
               var2 = var5.text;
               var3 = var5.minRelation;
            }
         }

         return var2;
      }
   }

   public static int reputationStep(int var0) {
      int var1 = 0;

      for (int var5 : REP_STEPS) {
         if (var0 >= var5) {
            var1++;
         }
      }

      return clamp(var1, 0, 4);
   }

   public String reputationLabel(int var1) {
      int var2 = reputationStep(var1);
      String var3 = var2 < this.reputationNames.size() ? this.reputationNames.get(var2) : "";
      return var3 == null ? "" : var3;
   }

   public static String kindOf(NpcProfile.Topic var0, String[] var1) {
      if (var0.kind != null && !var0.kind.isEmpty()) {
         return var0.kind;
      } else {
         int var2 = clamp(var0.type, 0, TYPE_COUNT - 1);
         return var1 != null && var2 < var1.length ? var1[var2] : "";
      }
   }

   public boolean hasShop() {
      for (NpcProfile.Offer var2 : this.offers) {
         if (!var2.stack.isEmpty() && !var2.price.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static boolean visible(NpcProfile.Answer var0, int var1) {
      return var0 != null && var0.player != null && !var0.player.isEmpty() && var0.minRelation <= var1 && var1 <= var0.maxRelation;
   }

   public static String actionKind(String var0) {
      String var1 = var0 == null ? "" : var0.trim();
      if (var1.isEmpty()) {
         return "";
      } else {
         if (var1.regionMatches(true, 0, "trigger:", 0, 8)) {
            return "trigger";
         }

         return var1.equalsIgnoreCase("focus") || var1.regionMatches(true, 0, "focus ", 0, 6) ? "trigger" : "cmd";
      }
   }

   public static boolean hasText(NpcProfile.Step var0) {
      if (var0 == null) {
         return false;
      } else if (var0.line != null && !var0.line.isEmpty()) {
         return true;
      } else {
         for (NpcProfile.Variant var2 : var0.variants) {
            if (var2.text != null && !var2.text.isEmpty()) {
               return true;
            }
         }

         for (NpcProfile.Answer var4 : var0.answers) {
            if (var4.player != null && !var4.player.isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean claimed(NpcProfile.Topic var0, int var1) {
      if (var0 == null) {
         return false;
      } else {
         for (NpcProfile.Step var3 : var0.steps) {
            for (NpcProfile.Answer var5 : var3.answers) {
               if (var5.next == var1) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public static int chainNext(NpcProfile.Topic var0, int var1) {
      if (var0 == null) {
         return -1;
      } else {
         for (int var2 = var1 + 1; var2 < var0.steps.size(); var2++) {
            if (!claimed(var0, var2) && hasText(var0.steps.get(var2))) {
               return var2;
            }
         }

         return -1;
      }
   }

   public static int nextText(NpcProfile.Topic var0, int var1, int var2) {
      if (var0 == null) {
         return -1;
      } else {
         for (int var3 = var1 + 1; var3 < var0.steps.size(); var3++) {
            if (!lineFor(var0.steps.get(var3), var2).isEmpty()) {
               return var3;
            }
         }

         return -1;
      }
   }

   public static int editorNext(NpcProfile.Topic var0, int var1, int var2) {
      if (var0 == null) {
         return -1;
      } else {
         return var2 == var1 + 1 && var2 < var0.steps.size() ? var2 : chainNext(var0, var1);
      }
   }

   public static boolean hasVisibleAnswers(NpcProfile.Topic var0, int var1, int var2) {
      if (var0 != null && var1 >= 0 && var1 < var0.steps.size()) {
         for (NpcProfile.Answer var4 : var0.steps.get(var1).answers) {
            if (visible(var4, var2)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean chainReads(NpcProfile.Topic var0, int var1, int var2) {
      if (!hasVisibleAnswers(var0, var1, var2)) {
         return false;
      } else {
         int var3 = var1;

         for (int var4 = 0; var4 < 64; var4++) {
            int var5 = chainNext(var0, var3);
            if (var5 < 0) {
               return false;
            }

            if (hasVisibleAnswers(var0, var5, var2)) {
               return false;
            }

            if (chainNext(var0, var5) < 0) {
               return true;
            }

            var3 = var5;
         }

         return false;
      }
   }

   public static boolean usable(NpcProfile.Step var0) {
      if (var0 == null) {
         return false;
      } else if (!lineFor(var0, 0).isEmpty()) {
         return true;
      } else {
         for (NpcProfile.Answer var2 : var0.answers) {
            if (!var2.player.isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean usable(NpcProfile.Topic var0) {
      if (var0 == null) {
         return false;
      } else {
         for (NpcProfile.Step var2 : var0.steps) {
            if (usable(var2)) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean showsKind(int var1) {
      return switch (var1) {
         case 1 -> this.showTradeTab;
         default -> this.showDialogueTab;
         case 3 -> this.showQuestTab;
         case 4 -> true;
      };
   }

   public boolean isEmpty() {
      return this.welcome.isEmpty() && this.welcomeLines.isEmpty() && this.topics.isEmpty() && this.offers.isEmpty() && this.quests.isEmpty();
   }

   public static String fillQuest(String var0, Player var1, int var2, NpcProfile.Quest var3) {
      String var4 = var0 == null ? "" : var0;
      if (var3 != null) {
         String var5 = var3.points()
            ? var3.pointsName + NpcQuests.need(var3)
            : (var3.item != null && !var3.item.isEmpty() ? NpcQuests.need(var3) + " × #" + var3.item.getDescriptionId() : "");
         String var6 = var3.reward != null && !var3.reward.isEmpty() ? var3.reward.getCount() + " × #" + var3.reward.getDescriptionId() : "";
         var4 = var4.replace("%quest%", var5).replace("%reward%", var6);
      }

      return fill(var4, var1, var2);
   }

   public List<String> rootSequence() {
      ArrayList var1 = new ArrayList();
      if (this.welcome != null && !this.welcome.isEmpty()) {
         var1.add(this.welcome);
      }

      for (String var3 : this.welcomeLines) {
         if (var3 != null && !var3.isEmpty()) {
            var1.add(var3);
         }
      }

      return var1;
   }

   public NpcProfile copy() {
      NpcProfile var1 = new NpcProfile();
      var1.name = this.name;
      var1.identifier = this.identifier;
      var1.title = this.title;
      var1.texture = this.texture;
      var1.focusNpcUuid = this.focusNpcUuid;
      var1.scale = this.scale;
      var1.pose = this.pose;
      var1.nameTag = this.nameTag;
      var1.lookAtPlayer = this.lookAtPlayer;
      var1.reputationNames.clear();
      var1.reputationNames.addAll(this.reputationNames);
      var1.defaultRelation = this.defaultRelation;
      var1.welcome = this.welcome;
      var1.dialogueGraph = this.dialogueGraph == null ? null : this.dialogueGraph.copy();
      var1.walkGraph = this.walkGraph == null ? null : this.walkGraph.copy();
      var1.animGraph = this.animGraph == null ? null : this.animGraph.copy();
      var1.welcomeLines.clear();
      var1.welcomeLines.addAll(this.welcomeLines);
      var1.style = this.style;
      var1.textColor = this.textColor;
      var1.nameColor = this.nameColor;
      var1.typingSpeed = this.typingSpeed;
      var1.voice = this.voice;
      var1.voicePitch = this.voicePitch;
      var1.showSpeaker = this.showSpeaker;
      var1.keyHints = this.keyHints;
      var1.answerNumbers = this.answerNumbers;
      var1.showDialogueTab = this.showDialogueTab;
      var1.showTradeTab = this.showTradeTab;
      var1.showQuestTab = this.showQuestTab;
      var1.showPhrasesTab = this.showPhrasesTab;
      var1.questDropButton = this.questDropButton;
      var1.questOfferClean = this.questOfferClean;
      var1.defaultTab = this.defaultTab;
      var1.maxPerPurchase = this.maxPerPurchase;
      var1.discount = this.discount;
      var1.zoom = this.zoom;
      var1.offsetX = this.offsetX;
      var1.offsetY = this.offsetY;
      var1.cameraOnFace = this.cameraOnFace;
      var1.blockMouseLook = this.blockMouseLook;
      var1.dim = this.dim;
      var1.blur = this.blur;
      var1.muteHud = this.muteHud;
      var1.vignette = this.vignette;
      var1.radius = this.radius;
      var1.exitKey = this.exitKey;
      var1.blockMovement = this.blockMovement;
      var1.pauseWorld = this.pauseWorld;
      var1.exitAction = this.exitAction;
      var1.escortCode = this.escortCode;
      var1.escortCoordinates = this.escortCoordinates;
      var1.escortTravelLine = this.escortTravelLine;
      var1.escortArrivalLine = this.escortArrivalLine;
      var1.idleWalking = this.idleWalking;
      var1.mobilityGroupId = this.mobilityGroupId;
      var1.mobilityGroupName = this.mobilityGroupName;
      for (NpcProfile.MobilityPoint point : this.mobilityPoints) {
         if (point != null) {
            var1.mobilityPoints.add(point.copy());
         }
      }
      for (NpcProfile.MobilityScheduleEntry entry : this.mobilitySchedule) {
         if (entry != null && var1.mobilitySchedule.size() < MAX_MOBILITY_SCHEDULE_ENTRIES) {
            var1.mobilitySchedule.add(new NpcProfile.MobilityScheduleEntry(entry.second, entry.groupId));
         }
      }

      for (NpcProfile.Topic var3 : this.topics) {
         NpcProfile.Topic var4 = new NpcProfile.Topic(var3.title, var3.type);
         var4.kind = var3.kind;
         var4.questRole = var3.questRole;

         for (NpcProfile.Step var6 : var3.steps) {
            NpcProfile.Step var7 = new NpcProfile.Step();
            var7.line = var6.line;
            var7.action = var6.action;

            for (NpcProfile.Variant var9 : var6.variants) {
               var7.variants.add(new NpcProfile.Variant(var9.minRelation, var9.text));
            }

            for (NpcProfile.Answer var16 : var6.answers) {
               NpcProfile.Answer var10 = new NpcProfile.Answer();
               var10.player = var16.player;
               var10.reaction = var16.reaction;
               var10.relation = var16.relation;
               var10.minRelation = var16.minRelation;
               var10.maxRelation = var16.maxRelation;
               var10.pointsName = var16.pointsName;
               var10.minPoints = var16.minPoints;
               var10.maxPoints = var16.maxPoints;
               var10.pointsShared = var16.pointsShared;
               var10.action = var16.action;
               var10.next = var16.next;
               var7.answers.add(var10);
            }

            var4.steps.add(var7);
         }

         var1.topics.add(var4);
      }

      for (NpcProfile.Offer var13 : this.offers) {
         var1.offers.add(newOffer(var13.stack.copy(), var13.price.copy(), var13.description, var13.minRelation));
      }

      var1.quests.clear();

      for (NpcProfile.Quest var14 : this.quests) {
         var1.quests.add(var14.copy());
      }

      return var1;
   }

   public static NpcProfile.Offer newOffer(ItemStack var0, ItemStack var1, String var2, int var3) {
      NpcProfile.Offer var4 = new NpcProfile.Offer();
      var4.stack = var0 == null ? ItemStack.EMPTY : var0;
      var4.price = var1 == null ? ItemStack.EMPTY : var1;
      var4.description = var2 == null ? "" : var2;
      var4.minRelation = var3;
      return var4;
   }

   public NpcProfile sanitized() {
      NpcProfile var1 = new NpcProfile();
      var1.name = clean(this.name, 32);
      var1.identifier = clean(this.identifier, 64);
      var1.title = clean(this.title, 32);
      var1.texture = clean(this.texture, 96);
      var1.focusNpcUuid = clean(this.focusNpcUuid, 36);
      var1.scale = clamp(this.scale, 50, 150);
      var1.pose = clamp(this.pose, 0, 2);
      var1.nameTag = this.nameTag;
      var1.lookAtPlayer = this.lookAtPlayer;
      var1.reputationNames.clear();

      for (int var2 = 0; var2 < 5; var2++) {
         String var3 = var2 < this.reputationNames.size() ? this.reputationNames.get(var2) : "";
         var1.reputationNames.add(clean(var3, 24));
      }

      var1.defaultRelation = clamp(this.defaultRelation, 0, 100);
      var1.welcome = clean(this.welcome, 480);
      var1.dialogueGraph = this.dialogueGraph == null ? null : this.dialogueGraph.copy();
      var1.walkGraph = this.walkGraph == null ? null : this.walkGraph.copy();
      var1.animGraph = this.animGraph == null ? null : this.animGraph.copy();

      for (int var13 = 0; var13 < this.welcomeLines.size() && var1.welcomeLines.size() < 6; var13++) {
         var1.welcomeLines.add(clean(this.welcomeLines.get(var13), 480));
      }

      var1.style = clamp(this.style, 0, 1);
      var1.textColor = clamp(this.textColor, 0, 3);
      var1.nameColor = clamp(this.nameColor, 0, 3);
      var1.typingSpeed = clamp(this.typingSpeed, 0, 200);
      var1.voice = clean(this.voice, 32);
      var1.voicePitch = clamp(this.voicePitch, 0, 12);
      var1.showSpeaker = this.showSpeaker;
      var1.keyHints = this.keyHints;
      var1.answerNumbers = this.answerNumbers;
      var1.showDialogueTab = this.showDialogueTab;
      var1.showTradeTab = this.showTradeTab;
      var1.showQuestTab = this.showQuestTab;
      var1.showPhrasesTab = this.showPhrasesTab;
      var1.defaultTab = clamp(this.defaultTab, 0, 3);
      var1.maxPerPurchase = clamp(this.maxPerPurchase, 1, 8);
      var1.discount = clamp(this.discount, 0, 90);
      var1.zoom = clamp(this.zoom, 70, 150);
      var1.offsetX = clamp(this.offsetX, -40, 40);
      var1.offsetY = clamp(this.offsetY, -40, 40);
      var1.cameraOnFace = this.cameraOnFace;
      var1.blockMouseLook = this.blockMouseLook;
      var1.dim = clamp(this.dim, 0, 80);
      var1.blur = clamp(this.blur, 0, 2);
      var1.muteHud = this.muteHud;
      var1.vignette = this.vignette;
      var1.radius = clamp(this.radius, 2, 16);
      var1.exitKey = clamp(this.exitKey, 0, 1);
      var1.blockMovement = this.blockMovement;
      var1.pauseWorld = this.pauseWorld;
      var1.exitAction = clamp(this.exitAction, 0, 2);
      var1.escortCode = this.escortCode;
      var1.escortCoordinates = this.escortCoordinates;
      var1.escortTravelLine = this.escortTravelLine;
      var1.escortArrivalLine = this.escortArrivalLine;
      var1.idleWalking = this.idleWalking;
      var1.escortCode = clean(this.escortCode, EscortRules.MAX_CODE);
      var1.escortCoordinates = clean(this.escortCoordinates, MAX_MOBILITY_COORDINATES);
      var1.escortTravelLine = clean(this.escortTravelLine, MAX_LINE);
      var1.escortArrivalLine = clean(this.escortArrivalLine, MAX_LINE);
      if (var1.escortTravelLine.isEmpty()) var1.escortTravelLine = "Мы почти пришли";
      if (var1.escortArrivalLine.isEmpty()) var1.escortArrivalLine = "Мы пришли. Спасибо за сопровождение.";
      var1.mobilityGroupId = clean(this.mobilityGroupId, 36);
      var1.mobilityGroupName = clean(this.mobilityGroupName, 32);
      for (int i = 0; i < this.mobilityPoints.size() && var1.mobilityPoints.size() < MAX_MOBILITY_POINTS; i++) {
         NpcProfile.MobilityPoint point = this.mobilityPoints.get(i);
         if (point != null) {
            var1.mobilityPoints.add(point.copy());
         }
      }
      if (var1.mobilityGroupId.isEmpty()) {
         var1.mobilityGroupName = "";
         var1.mobilityPoints.clear();
      }
      boolean[] scheduledSeconds = new boolean[MOBILITY_DAY_SECONDS];
      for (NpcProfile.MobilityScheduleEntry entry : this.mobilitySchedule) {
         if (entry == null || var1.mobilitySchedule.size() >= MAX_MOBILITY_SCHEDULE_ENTRIES) {
            continue;
         }
         int second = clamp(entry.second, 0, MOBILITY_DAY_SECONDS - 1);
         String groupId = clean(entry.groupId, 36);
         if (!groupId.isBlank() && !scheduledSeconds[second]) {
            scheduledSeconds[second] = true;
            var1.mobilitySchedule.add(new NpcProfile.MobilityScheduleEntry(second, groupId));
         }
      }
      ArrayList var14 = new ArrayList();

      for (int var15 = 0; var15 < this.topics.size(); var15++) {
         NpcProfile.Topic var4 = this.topics.get(var15);
         if (var4 != null) {
            boolean var5 = isQuestTopic(var4);
            if (var5 || var1.topics.size() < 8) {
               NpcProfile.Topic var6 = new NpcProfile.Topic(clean(var4.title, 64), clamp(var4.type, 0, TYPE_COUNT - 1));
               var6.kind = clean(var4.kind, 16);
               var6.questRole = var5 ? clamp(var4.questRole, 1, 4) : 0;
               if (var6.title.isEmpty()) {
                  var6.title = "Topic " + (var1.topics.size() + 1);
               }

               for (int var7 = 0; var7 < var4.steps.size() && var6.steps.size() < 16; var7++) {
                  NpcProfile.Step var8 = var4.steps.get(var7);
                  if (var8 != null) {
                     NpcProfile.Step var9 = new NpcProfile.Step();
                     var9.line = clean(var8.line, 480);
                     var9.action = clean(var8.action, 2048);

                     for (int var10 = 0; var10 < var8.variants.size() && var9.variants.size() < 5; var10++) {
                        NpcProfile.Variant var11 = var8.variants.get(var10);
                        if (var11 != null) {
                           String var12 = clean(var11.text, 480);
                           if (!var12.isEmpty()) {
                              var9.variants.add(new NpcProfile.Variant(clamp(var11.minRelation, 0, 100), var12));
                           }
                        }
                     }

                     var9.variants.sort((var0, var1x) -> Integer.compare(var0.minRelation, var1x.minRelation));

                     for (int var29 = 0; var29 < var8.answers.size() && var9.answers.size() < 6; var29++) {
                        NpcProfile.Answer var30 = var8.answers.get(var29);
                        if (var30 != null) {
                           NpcProfile.Answer var31 = new NpcProfile.Answer();
                           var31.player = clean(var30.player, 240);
                           var31.reaction = clean(var30.reaction, 480);
                           var31.relation = clamp(var30.relation, -100, 100);
                           var31.minRelation = clamp(var30.minRelation, 0, 100);
                           var31.maxRelation = clamp(var30.maxRelation, 0, 100);
                           if (var31.maxRelation < var31.minRelation) {
                              var31.maxRelation = var31.minRelation;
                           }
                           var31.pointsName = NpcPoints.cleanName(var30.pointsName);
                           var31.minPoints = clamp(var30.minPoints, 0, NpcPoints.MAX_VALUE);
                           var31.maxPoints = clamp(var30.maxPoints, 0, NpcPoints.MAX_VALUE);
                           if (var31.maxPoints < var31.minPoints) {
                              var31.maxPoints = var31.minPoints;
                           }
                           var31.pointsShared = var30.pointsShared;

                           var31.action = clean(var30.action, 2048);
                           var31.next = var30.next;
                           var9.answers.add(var31);
                        }
                     }

                     var6.steps.add(var9);
                  }
               }

               (var5 ? var14 : var1.topics).add(var6);
            }
         }
      }

      for (int var16 = 0; var16 < this.offers.size() && var1.offers.size() < 32; var16++) {
         NpcProfile.Offer var19 = this.offers.get(var16);
         if (var19 != null) {
            ItemStack var22 = var19.stack == null ? ItemStack.EMPTY : var19.stack.copy();
            ItemStack var25 = var19.price == null ? ItemStack.EMPTY : var19.price.copy();
            if (!var22.isEmpty()) {
               var22.setCount(clamp(var22.getCount(), 1, 64));
            }

            if (!var25.isEmpty()) {
               var25.setCount(clamp(var25.getCount(), 1, 64));
            }

            var1.offers.add(newOffer(var22, var25, clean(var19.description, 240), clamp(var19.minRelation, 0, 100)));
         }
      }

      for (int var17 = 0; var17 < this.quests.size() && var1.quests.size() < 6; var17++) {
         NpcProfile.Quest var20 = this.quests.get(var17);
         if (var20 != null) {
            NpcProfile.Quest var23 = new NpcProfile.Quest();
            var23.title = clean(var20.title, 64);
            if (var23.title.isEmpty()) {
               var23.title = "Задание " + (var1.quests.size() + 1);
            }

            var23.text = clean(var20.text, 480);
            var23.done = clean(var20.done, 480);
            var23.item = var20.item == null ? ItemStack.EMPTY : var20.item.copy();
            var23.reward = var20.reward == null ? ItemStack.EMPTY : var20.reward.copy();
            if (!var23.item.isEmpty()) {
               var23.item.setCount(clamp(var23.item.getCount(), 1, 64));
            }

            if (!var23.reward.isEmpty()) {
               var23.reward.setCount(clamp(var23.reward.getCount(), 1, 64));
            }

            var23.rewardRelation = clamp(var20.rewardRelation, -100, 100);
            var23.minRelation = clamp(var20.minRelation, 0, 100);
            var23.repeatable = var20.repeatable;
            var23.kind = clamp(var20.kind, 0, 1);
            var23.pointsName = clean(var20.pointsName, 32);
            var23.pointsCount = clamp(var20.pointsCount, 1, 1000000);
            var23.pointsShared = var20.pointsShared;
            var23.pointsSpend = var20.pointsSpend;
            var23.chainPrev = var20.chainPrev == var17 ? -1 : clamp(var20.chainPrev, -1, 5);
            var23.chainShared = var20.chainShared;
            var1.quests.add(var23);
         }
      }

      assembleQuestDialogs(var1, var14);
      ensureQuestDrop(var1, this.questDropButton);
      ensureQuestOfferClean(var1, this.questOfferClean);

      for (NpcProfile.Topic var21 : var1.topics) {
         for (NpcProfile.Step var26 : var21.steps) {
            for (NpcProfile.Answer var28 : var26.answers) {
               if (var28.next != -1) {
                  if (var28.next >= 1000) {
                     var28.next = var28.next - 1000 >= var1.topics.size() ? -1 : var28.next;
                  } else {
                     var28.next = clamp(var28.next, 0, var21.steps.size() - 1);
                  }
               }
            }
         }
      }

      return var1;
   }

   public NpcProfile.MobilityScheduleEntry mobilityScheduleAt(int secondOfDay) {
      if (this.mobilitySchedule.isEmpty()) {
         return null;
      }
      int second = Math.floorMod(secondOfDay, MOBILITY_DAY_SECONDS);
      NpcProfile.MobilityScheduleEntry first = null;
      NpcProfile.MobilityScheduleEntry active = null;
      for (NpcProfile.MobilityScheduleEntry entry : this.mobilitySchedule) {
         if (entry == null || entry.groupId == null || entry.groupId.isBlank()) {
            continue;
         }
         if (first == null || entry.second < first.second) {
            first = entry;
         }
         if (entry.second <= second && (active == null || entry.second > active.second)) {
            active = entry;
         }
      }
      return active != null ? active : first;
   }

   public CompoundTag save(Provider var1) {
      CompoundTag var2 = new CompoundTag();
      var2.putString("Name", nullTo(this.name));
      var2.putString("Identifier", nullTo(this.identifier));
      var2.putString("Title", nullTo(this.title));
      var2.putString("Texture", nullTo(this.texture));
      var2.putString("FocusNpc", nullTo(this.focusNpcUuid));
      var2.putInt("Scale", this.scale);
      var2.putInt("Pose", this.pose);
      var2.putBoolean("NameTag", this.nameTag);
      var2.putBoolean("LookAt", this.lookAtPlayer);
      ListTag var3 = new ListTag();

      for (String var5 : this.reputationNames) {
         CompoundTag var6 = new CompoundTag();
         var6.putString("T", nullTo(var5));
         var3.add(var6);
      }

      var2.put("RepNames", var3);
      var2.putInt("Default", this.defaultRelation);
      var2.putString("Welcome", nullTo(this.welcome));
      if (this.dialogueGraph != null) var2.put("DialogueGraph", this.dialogueGraph.save());
      if (this.walkGraph != null) var2.put("WalkGraph", this.walkGraph.save());
      if (this.animGraph != null) var2.put("AnimGraph", this.animGraph.save());
      ListTag var18 = new ListTag();

      for (String var21 : this.welcomeLines) {
         var18.add(StringTag.valueOf(nullTo(var21)));
      }

      var2.put("WelcomeLines", var18);
      var2.putInt("Style", this.style);
      var2.putInt("TextColor", this.textColor);
      var2.putInt("NameColor", this.nameColor);
      var2.putInt("TypeSpeed", this.typingSpeed);
      var2.putString("Voice", nullTo(this.voice));
      var2.putInt("VoicePitch", this.voicePitch);
      var2.putBoolean("ShowSpeaker", this.showSpeaker);
      var2.putBoolean("ShowKeys", this.keyHints);
      var2.putBoolean("AnswerNums", this.answerNumbers);
      var2.putBoolean("TabDialogue", this.showDialogueTab);
      var2.putBoolean("TabTrade", this.showTradeTab);
      var2.putBoolean("TabQuest", this.showQuestTab);
      var2.putBoolean("TabPhrases", this.showPhrasesTab);
      var2.putBoolean("QuestDrop", this.questDropButton);
      var2.putBoolean("QuestOfferClean", this.questOfferClean);
      var2.putInt("DefaultTab", this.defaultTab);
      var2.putInt("MaxPerPurchase", this.maxPerPurchase);
      var2.putInt("Discount", this.discount);
      var2.putInt("Zoom", this.zoom);
      var2.putInt("OffX", this.offsetX);
      var2.putInt("OffY", this.offsetY);
      var2.putBoolean("CamFace", this.cameraOnFace);
      var2.putBoolean("BlockLook", this.blockMouseLook);
      var2.putInt("Dim", this.dim);
      var2.putInt("Blur", this.blur);
      var2.putBoolean("MuteHud", this.muteHud);
      var2.putBoolean("Vignette", this.vignette);
      var2.putInt("Radius", this.radius);
      var2.putInt("ExitKey", this.exitKey);
      var2.putBoolean("BlockMove", this.blockMovement);
      var2.putBoolean("PauseWorld", this.pauseWorld);
      var2.putInt("ExitAction", this.exitAction);
      var2.putBoolean("IdleWalking", this.idleWalking);
      var2.putString("EscortCode", nullTo(this.escortCode));
      var2.putString("EscortCoordinates", nullTo(this.escortCoordinates));
      var2.putString("EscortTravelLine", nullTo(this.escortTravelLine));
      var2.putString("EscortArrivalLine", nullTo(this.escortArrivalLine));
      var2.putString("MobilityGroupId", nullTo(this.mobilityGroupId));
      var2.putString("MobilityGroupName", nullTo(this.mobilityGroupName));
      ListTag assignedMobilityTags = new ListTag();
      for (NpcProfile.MobilityPoint point : this.mobilityPoints) {
         if (point != null && assignedMobilityTags.size() < MAX_MOBILITY_POINTS) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putString("Coordinates", nullTo(point.coordinates));
            pointTag.putInt("Chance", point.chance);
            pointTag.putString("Duration", nullTo(point.duration));
            if (point.type != 0) pointTag.putInt("Type", point.type);
            if (point.next != -1) pointTag.putInt("Next", point.next);
            if (point.falseNext != -1) pointTag.putInt("FalseNext", point.falseNext);
            if (point.randomMin != 1) pointTag.putInt("RandomMin", point.randomMin);
            if (point.randomMax != 2) pointTag.putInt("RandomMax", point.randomMax);
            if (!point.randomBranches.isEmpty()) pointTag.putIntArray("RandomBranches", point.randomBranches.stream().mapToInt(Integer::intValue).toArray());
            if (point.canvasX != 0) pointTag.putInt("CanvasX", point.canvasX);
            if (point.canvasY != 0) pointTag.putInt("CanvasY", point.canvasY);
            if (!point.targetGroupId.isBlank()) pointTag.putString("TargetGroup", point.targetGroupId);
            assignedMobilityTags.add(pointTag);
         }
      }
      var2.put("AssignedMobilityPoints", assignedMobilityTags);
      ListTag mobilityScheduleTags = new ListTag();
      for (NpcProfile.MobilityScheduleEntry entry : this.mobilitySchedule) {
         if (entry != null && mobilityScheduleTags.size() < MAX_MOBILITY_SCHEDULE_ENTRIES) {
            CompoundTag scheduleTag = new CompoundTag();
            scheduleTag.putInt("Second", entry.second);
            scheduleTag.putString("GroupId", nullTo(entry.groupId));
            mobilityScheduleTags.add(scheduleTag);
         }
      }
      var2.put("MobilitySchedule", mobilityScheduleTags);
      ListTag var20 = new ListTag();

      for (NpcProfile.Topic var7 : this.topics) {
         CompoundTag var8 = new CompoundTag();
         var8.putString("Title", nullTo(var7.title));
         var8.putString("Kind", nullTo(var7.kind));
         var8.putInt("Type", var7.type);
         var8.putInt("QRole", var7.questRole);
         ListTag var9 = new ListTag();

         for (NpcProfile.Step var11 : var7.steps) {
            CompoundTag var12 = new CompoundTag();
            var12.putString("Line", nullTo(var11.line));
            var12.putString("Action", nullTo(var11.action));
            ListTag var13 = new ListTag();

            for (NpcProfile.Variant var15 : var11.variants) {
               CompoundTag var16 = new CompoundTag();
               var16.putInt("MinRel", var15.minRelation);
               var16.putString("Text", nullTo(var15.text));
               var13.add(var16);
            }

            var12.put("Variants", var13);
            ListTag var31 = new ListTag();

            for (NpcProfile.Answer var33 : var11.answers) {
               CompoundTag var17 = new CompoundTag();
               var17.putString("Player", nullTo(var33.player));
               var17.putString("Reaction", nullTo(var33.reaction));
               var17.putInt("Rel", var33.relation);
               var17.putInt("MinRel", var33.minRelation);
               var17.putInt("MaxRel", var33.maxRelation);
               var17.putString("PointsName", nullTo(var33.pointsName));
               var17.putInt("MinPoints", var33.minPoints);
               var17.putInt("MaxPoints", var33.maxPoints);
               var17.putBoolean("PointsShared", var33.pointsShared);
               var17.putString("Action", nullTo(var33.action));
               var17.putInt("Next", var33.next);
               var31.add(var17);
            }

            var12.put("Answers", var31);
            var9.add(var12);
         }

         var8.put("Steps", var9);
         var20.add(var8);
      }

      var2.put("Topics", var20);
      ListTag var23 = new ListTag();

      for (NpcProfile.Offer var26 : this.offers) {
         CompoundTag var28 = new CompoundTag();
         putStack(var28, "Item", var26.stack, var1);
         putStack(var28, "Price", var26.price, var1);
         var28.putString("Desc", nullTo(var26.description));
         var28.putInt("MinRel", var26.minRelation);
         var23.add(var28);
      }

      var2.put("Shop", var23);
      ListTag var25 = new ListTag();

      for (NpcProfile.Quest var29 : this.quests) {
         CompoundTag var30 = new CompoundTag();
         var30.putString("Title", nullTo(var29.title));
         var30.putString("Text", nullTo(var29.text));
         var30.putString("Done", nullTo(var29.done));
         putStack(var30, "Item", var29.item, var1);
         putStack(var30, "Reward", var29.reward, var1);
         var30.putInt("Rel", var29.rewardRelation);
         var30.putInt("MinRel", var29.minRelation);
         var30.putBoolean("Repeat", var29.repeatable);
         var30.putInt("Kind", var29.kind);
         var30.putString("PName", nullTo(var29.pointsName));
         var30.putInt("PCount", var29.pointsCount);
         var30.putBoolean("PShared", var29.pointsShared);
         var30.putBoolean("PSpend", var29.pointsSpend);
         var30.putInt("Chain", var29.chainPrev);
         var30.putBoolean("ChainShared", var29.chainShared);
         var25.add(var30);
      }

      var2.put("Quests", var25);
      return var2;
   }

   public static NpcProfile load(CompoundTag var0, Provider var1) {
      NpcProfile var2 = new NpcProfile();
      if (var0 == null) {
         return var2;
      } else {
         var2.name = var0.getString("Name");
         var2.identifier = var0.contains("Identifier") ? var0.getString("Identifier") : "";
         var2.title = var0.getString("Title");
         var2.texture = var0.getString("Texture");
         var2.focusNpcUuid = var0.contains("FocusNpc") ? var0.getString("FocusNpc") : "";
         var2.scale = var0.contains("Scale") ? var0.getInt("Scale") : 100;
         var2.pose = var0.getInt("Pose");
         var2.nameTag = !var0.contains("NameTag") || var0.getBoolean("NameTag");
         var2.lookAtPlayer = !var0.contains("LookAt") || var0.getBoolean("LookAt");
         ListTag var3 = var0.getList("RepNames", 10);
         if (var3.size() >= 5) {
            var2.reputationNames.clear();

            for (int var4 = 0; var4 < 5; var4++) {
               var2.reputationNames.add(var3.getCompound(var4).getString("T"));
            }
         }

         var2.defaultRelation = var0.getInt("Default");
         var2.welcome = var0.getString("Welcome");
         var2.dialogueGraph = var0.contains("DialogueGraph", 10) ? NpcDialogueGraph.load(var0.getCompound("DialogueGraph")) : null;
         var2.walkGraph = var0.contains("WalkGraph", 10) ? NpcDialogueGraph.load(var0.getCompound("WalkGraph")) : null;
         var2.animGraph = var0.contains("AnimGraph", 10) ? NpcDialogueGraph.load(var0.getCompound("AnimGraph")) : null;
         ListTag var18 = var0.getList("WelcomeLines", 8);

         for (int var5 = 0; var5 < var18.size() && var2.welcomeLines.size() < 6; var5++) {
            var2.welcomeLines.add(var18.getString(var5));
         }

         var2.style = var0.getInt("Style");
         var2.textColor = var0.getInt("TextColor");
         var2.nameColor = var0.contains("NameColor") ? var0.getInt("NameColor") : 1;
         var2.typingSpeed = var0.contains("TypeSpeed") ? var0.getInt("TypeSpeed") : 30;
         var2.voice = var0.contains("Voice") ? var0.getString("Voice") : "";
         var2.voicePitch = var0.contains("VoicePitch") ? var0.getInt("VoicePitch") : 0;
         var2.showSpeaker = !var0.contains("ShowSpeaker") || var0.getBoolean("ShowSpeaker");
         var2.keyHints = !var0.contains("ShowKeys") || var0.getBoolean("ShowKeys");
         var2.answerNumbers = var0.getBoolean("AnswerNums");
         var2.showDialogueTab = !var0.contains("TabDialogue") || var0.getBoolean("TabDialogue");
         var2.showTradeTab = !var0.contains("TabTrade") || var0.getBoolean("TabTrade");
         var2.showQuestTab = !var0.contains("TabQuest") || var0.getBoolean("TabQuest");
         var2.showPhrasesTab = var0.getBoolean("TabPhrases");
         var2.questDropButton = var0.getBoolean("QuestDrop");
         var2.questOfferClean = var0.getBoolean("QuestOfferClean");
         var2.defaultTab = var0.getInt("DefaultTab");
         var2.maxPerPurchase = var0.contains("MaxPerPurchase") ? var0.getInt("MaxPerPurchase") : 8;
         var2.discount = var0.contains("Discount") ? var0.getInt("Discount") : 50;
         var2.zoom = var0.contains("Zoom") ? var0.getInt("Zoom") : 100;
         var2.offsetX = var0.getInt("OffX");
         var2.offsetY = var0.getInt("OffY");
         var2.cameraOnFace = var0.getBoolean("CamFace");
         var2.blockMouseLook = var0.getBoolean("BlockLook");
         var2.dim = var0.contains("Dim") ? var0.getInt("Dim") : 45;
         var2.blur = var0.getInt("Blur");
         var2.muteHud = !var0.contains("MuteHud") || var0.getBoolean("MuteHud");
         var2.vignette = !var0.contains("Vignette") || var0.getBoolean("Vignette");
         var2.radius = var0.contains("Radius") ? var0.getInt("Radius") : 4;
         var2.exitKey = var0.getInt("ExitKey");
         var2.blockMovement = !var0.contains("BlockMove") || var0.getBoolean("BlockMove");
         var2.pauseWorld = var0.getBoolean("PauseWorld");
         var2.exitAction = var0.getInt("ExitAction");
         var2.idleWalking = var0.getBoolean("IdleWalking");
         var2.escortCode = var0.getString("EscortCode");
         var2.escortCoordinates = var0.getString("EscortCoordinates");
         if (var0.contains("EscortTravelLine", 8)) var2.escortTravelLine = var0.getString("EscortTravelLine");
         if (var0.contains("EscortArrivalLine", 8)) var2.escortArrivalLine = var0.getString("EscortArrivalLine");
         var2.mobilityGroupId = var0.contains("MobilityGroupId") ? var0.getString("MobilityGroupId") : "";
         var2.mobilityGroupName = var0.contains("MobilityGroupName") ? var0.getString("MobilityGroupName") : "";
         ListTag mobilityTags = var0.getList("AssignedMobilityPoints", 10);
         for (int i = 0; i < mobilityTags.size() && var2.mobilityPoints.size() < MAX_MOBILITY_POINTS; i++) {
            CompoundTag pointTag = mobilityTags.getCompound(i);
            NpcProfile.MobilityPoint mp = new NpcProfile.MobilityPoint(pointTag.getString("Coordinates"),
               pointTag.contains("Chance") ? pointTag.getInt("Chance") : 100,
               pointTag.contains("Duration") ? pointTag.getString("Duration") : "5-10");
            mp.type = pointTag.contains("Type") ? pointTag.getInt("Type") : 0;
            mp.next = pointTag.contains("Next") ? pointTag.getInt("Next") : -1;
            mp.falseNext = pointTag.contains("FalseNext") ? pointTag.getInt("FalseNext") : -1;
            mp.randomMin = pointTag.contains("RandomMin") ? pointTag.getInt("RandomMin") : 1;
            mp.randomMax = pointTag.contains("RandomMax") ? pointTag.getInt("RandomMax") : 2;
            if (pointTag.contains("RandomBranches")) {
               for (int b : pointTag.getIntArray("RandomBranches")) mp.randomBranches.add(b);
            }
            mp.canvasX = pointTag.contains("CanvasX") ? pointTag.getInt("CanvasX") : 0;
            mp.canvasY = pointTag.contains("CanvasY") ? pointTag.getInt("CanvasY") : 0;
            mp.targetGroupId = pointTag.getString("TargetGroup");
            var2.mobilityPoints.add(mp);
         }
         boolean[] seenScheduleSeconds = new boolean[MOBILITY_DAY_SECONDS];
         ListTag mobilityScheduleTags = var0.getList("MobilitySchedule", 10);
         for (int i = 0; i < mobilityScheduleTags.size() && var2.mobilitySchedule.size() < MAX_MOBILITY_SCHEDULE_ENTRIES; i++) {
            CompoundTag scheduleTag = mobilityScheduleTags.getCompound(i);
            int second = clamp(scheduleTag.getInt("Second"), 0, MOBILITY_DAY_SECONDS - 1);
            String groupId = clean(scheduleTag.getString("GroupId"), 36);
            if (!groupId.isBlank() && !seenScheduleSeconds[second]) {
               seenScheduleSeconds[second] = true;
               var2.mobilitySchedule.add(new NpcProfile.MobilityScheduleEntry(second, groupId));
            }
         }
         ListTag var19 = var0.getList("Topics", 10);

         for (int var6 = 0; var6 < var19.size(); var6++) {
            CompoundTag var7 = var19.getCompound(var6);
            NpcProfile.Topic var8 = new NpcProfile.Topic(var7.getString("Title"), var7.getInt("Type"));
            var8.kind = var7.getString("Kind");
            var8.questRole = var7.getInt("QRole");
            ListTag var9 = var7.getList("Steps", 10);

            for (int var10 = 0; var10 < var9.size(); var10++) {
               CompoundTag var11 = var9.getCompound(var10);
               NpcProfile.Step var12 = new NpcProfile.Step();
               var12.line = var11.getString("Line");
               var12.action = var11.contains("Action") ? var11.getString("Action") : "";
               ListTag var13 = var11.getList("Variants", 10);

               for (int var14 = 0; var14 < var13.size(); var14++) {
                  CompoundTag var15 = var13.getCompound(var14);
                  var12.variants.add(new NpcProfile.Variant(var15.getInt("MinRel"), var15.getString("Text")));
               }

               ListTag var31 = var11.getList("Answers", 10);

               for (int var32 = 0; var32 < var31.size(); var32++) {
                  CompoundTag var16 = var31.getCompound(var32);
                  NpcProfile.Answer var17 = new NpcProfile.Answer();
                  var17.player = var16.getString("Player");
                  var17.reaction = var16.getString("Reaction");
                  var17.relation = var16.getInt("Rel");
                  var17.minRelation = var16.getInt("MinRel");
                  var17.maxRelation = var16.contains("MaxRel") ? var16.getInt("MaxRel") : 100;
                  var17.pointsName = var16.contains("PointsName") ? var16.getString("PointsName") : "";
                  var17.minPoints = var16.contains("MinPoints") ? var16.getInt("MinPoints") : 1;
                  var17.maxPoints = var16.contains("MaxPoints") ? var16.getInt("MaxPoints") : NpcPoints.MAX_VALUE;
                  var17.pointsShared = var16.getBoolean("PointsShared");
                  var17.action = var16.contains("Action") ? var16.getString("Action") : "";
                  var17.next = var16.contains("Next") ? var16.getInt("Next") : -1;
                  var12.answers.add(var17);
               }

               var8.steps.add(var12);
            }

            var2.topics.add(var8);
         }

         ListTag var20 = var0.getList("Shop", 10);

         for (int var21 = 0; var21 < var20.size(); var21++) {
            CompoundTag var23 = var20.getCompound(var21);
            ItemStack var25 = ItemStack.parseOptional(var1, var23.getCompound("Item"));
            ItemStack var27 = ItemStack.parseOptional(var1, var23.getCompound("Price"));
            var2.offers.add(newOffer(var25, var27, var23.getString("Desc"), var23.getInt("MinRel")));
         }

         ListTag var22 = var0.getList("Quests", 10);

         for (int var24 = 0; var24 < var22.size(); var24++) {
            CompoundTag var26 = var22.getCompound(var24);
            ItemStack var28 = ItemStack.parseOptional(var1, var26.getCompound("Item"));
            ItemStack var29 = ItemStack.parseOptional(var1, var26.getCompound("Reward"));
            NpcProfile.Quest var30 = newQuest(var28, var29);
            var30.title = var26.getString("Title");
            var30.text = var26.getString("Text");
            var30.done = var26.getString("Done");
            var30.rewardRelation = var26.getInt("Rel");
            var30.minRelation = var26.getInt("MinRel");
            var30.repeatable = var26.getBoolean("Repeat");
            var30.kind = var26.contains("Kind") ? var26.getInt("Kind") : 0;
            var30.pointsName = var26.getString("PName");
            var30.pointsCount = var26.contains("PCount") ? var26.getInt("PCount") : 1;
            var30.pointsShared = var26.getBoolean("PShared");
            var30.pointsSpend = !var26.contains("PSpend") || var26.getBoolean("PSpend");
            var30.chainPrev = var26.contains("Chain") ? var26.getInt("Chain") : -1;
            var30.chainShared = var26.getBoolean("ChainShared");
            var2.quests.add(var30);
         }

         return var2.sanitized();
      }
   }

   private static void putStack(CompoundTag var0, String var1, ItemStack var2, Provider var3) {
      if (var2 != null && !var2.isEmpty()) {
         var0.put(var1, var2.save(var3));
      }
   }

   private static String nullTo(String var0) {
      return var0 == null ? "" : var0;
   }

   public static final class Answer {
      public String player = "";
      public String reaction = "";
      public int relation = 0;
      public int minRelation = 0;
      public int maxRelation = 100;
      public String pointsName = "";
      public int minPoints = 1;
      public int maxPoints = NpcPoints.MAX_VALUE;
      public boolean pointsShared = false;
      public String action = "";
      public int next = -1;
   }

   public static final class Offer {
      public ItemStack stack = ItemStack.EMPTY;
      public ItemStack price = ItemStack.EMPTY;
      public String description = "";
      public int minRelation = 0;
   }

   public static final class Quest {
      public String title = "";
      public String text = "";
      public String done = "";
      public ItemStack item = ItemStack.EMPTY;
      public ItemStack reward = ItemStack.EMPTY;
      public int rewardRelation = 0;
      public int minRelation = 0;
      public boolean repeatable = false;
      public int kind = 0;
      public String pointsName = "";
      public int pointsCount = 1;
      public boolean pointsShared = false;
      public boolean pointsSpend = true;
      public int chainPrev = -1;
      public boolean chainShared = false;

      public NpcProfile.Quest copy() {
         NpcProfile.Quest var1 = new NpcProfile.Quest();
         var1.title = this.title;
         var1.text = this.text;
         var1.done = this.done;
         var1.item = this.item == null ? ItemStack.EMPTY : this.item.copy();
         var1.reward = this.reward == null ? ItemStack.EMPTY : this.reward.copy();
         var1.rewardRelation = this.rewardRelation;
         var1.minRelation = this.minRelation;
         var1.repeatable = this.repeatable;
         var1.kind = this.kind;
         var1.pointsName = this.pointsName;
         var1.pointsCount = this.pointsCount;
         var1.pointsShared = this.pointsShared;
         var1.pointsSpend = this.pointsSpend;
         var1.chainPrev = this.chainPrev;
         var1.chainShared = this.chainShared;
         return var1;
      }

      public boolean points() {
         return this.kind == 1;
      }

      public boolean usable() {
         return this.points() ? this.pointsName != null && !this.pointsName.isEmpty() : !this.item.isEmpty();
      }
   }

   public static final class Step {
      public String line = "";
      public String action = "";
      public final List<NpcProfile.Variant> variants = new ArrayList<>();
      public final List<NpcProfile.Answer> answers = new ArrayList<>();
   }

   public static final class Topic {
      public String title = "";
      public String kind = "";
      public int type = 0;
      public int questRole = 0;
      public final List<NpcProfile.Step> steps = new ArrayList<>();

      public Topic() {
      }

      public Topic(String var1) {
         this.title = var1;
      }

      public Topic(String var1, int var2) {
         this.title = var1;
         this.type = var2;
      }
   }

   public static final class MobilityScheduleEntry {
      public int second;
      public String groupId = "";

      public MobilityScheduleEntry() {
      }

      public MobilityScheduleEntry(int second, String groupId) {
         this.second = second;
         this.groupId = groupId == null ? "" : groupId;
      }

      public boolean sameAs(NpcProfile.MobilityScheduleEntry other) {
         return other != null && this.second == other.second && java.util.Objects.equals(this.groupId, other.groupId);
      }
   }

   public static final class MobilityPoint {
      public static final int TYPE_POINT = 0;
      public static final int TYPE_RANDOM = 1;
      public static final int TYPE_IF = 2;
      public static final int TYPE_LINK = 3;

      public int type = TYPE_POINT;
      public String coordinates = "";
      public int chance = 100;
      public String duration = "5-10";
      public int next = -1;
      public int falseNext = -1;
      public int randomMin = 1;
      public int randomMax = 2;
      public final List<Integer> randomBranches = new ArrayList<>();
      public int canvasX = 0;
      public int canvasY = 0;
      public String targetGroupId = "";

      public MobilityPoint() {
      }

      public MobilityPoint(String coordinates, int chance, String duration) {
         this.coordinates = coordinates == null ? "" : coordinates;
         this.chance = chance;
         this.duration = duration == null ? "5-10" : duration;
      }

      public MobilityPoint copy() {
         MobilityPoint p = new MobilityPoint(this.coordinates, this.chance, this.duration);
         p.type = this.type;
         p.next = this.next;
         p.falseNext = this.falseNext;
         p.randomMin = this.randomMin;
         p.randomMax = this.randomMax;
         p.randomBranches.addAll(this.randomBranches);
         p.canvasX = this.canvasX;
         p.canvasY = this.canvasY;
         p.targetGroupId = this.targetGroupId;
         return p;
      }

      public boolean sameAs(NpcProfile.MobilityPoint other) {
         return other != null
            && this.type == other.type
            && this.chance == other.chance
            && this.next == other.next
            && this.falseNext == other.falseNext
            && this.randomMin == other.randomMin
            && this.randomMax == other.randomMax
            && java.util.Objects.equals(this.coordinates, other.coordinates)
            && java.util.Objects.equals(this.duration, other.duration)
            && java.util.Objects.equals(this.randomBranches, other.randomBranches);
      }
   }

   public static final class Variant {
      public int minRelation = 0;
      public String text = "";

      public Variant() {
      }

      public Variant(int var1, String var2) {
         this.minRelation = var1;
         this.text = var2;
      }
   }
}
