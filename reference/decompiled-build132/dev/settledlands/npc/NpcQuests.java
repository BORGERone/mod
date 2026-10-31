package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class NpcQuests {
   public static final String ACTION_TAKE = "quest:take";
   public static final String ACTION_DECLINE = "quest:decline";
   public static final String ACTION_HAND = "quest:hand";
   public static final String ACTION_DROP = "quest:drop";

   private NpcQuests() {
   }

   public static String questId(GuideNpc var0, int var1) {
      if (var0 == null) {
         return null;
      } else {
         NpcProfile.Quest var2 = questAt(var0.profile(), var1);
         if (var2 == null) {
            return null;
         } else {
            String var3 = var2.title == null ? "" : NpcProfile.clean(var2.title, 64).trim().toLowerCase(Locale.ROOT);
            return var3.isEmpty() ? "n:" + var0.relationKey() + "#" + var1 : "t:" + var3;
         }
      }
   }

   public static List<String> questIds(GuideNpc var0) {
      ArrayList var1 = new ArrayList();
      if (var0 != null) {
         for (int var2 = 0; var2 < var0.profile().quests.size(); var2++) {
            var1.add(questId(var0, var2));
         }
      }

      return var1;
   }

   public static boolean isTaken(NpcRelations var0, GuideNpc var1, ServerPlayer var2, int var3) {
      return var0.questTakenId(var2.getUUID(), questId(var1, var3));
   }

   public static void setTaken(NpcRelations var0, GuideNpc var1, ServerPlayer var2, int var3) {
      var0.markQuestTakenId(var2.getUUID(), questId(var1, var3));
   }

   public static void clearTaken(NpcRelations var0, GuideNpc var1, ServerPlayer var2, int var3) {
      var0.clearQuestTakenId(var2.getUUID(), questId(var1, var3));
   }

   public static boolean isDone(NpcRelations var0, GuideNpc var1, ServerPlayer var2, int var3) {
      return var0.questDoneId(var2.getUUID(), questId(var1, var3));
   }

   public static void setDone(NpcRelations var0, GuideNpc var1, ServerPlayer var2, int var3) {
      var0.markQuestDoneId(var2.getUUID(), questId(var1, var3));
   }

   public static boolean isChainDone(NpcRelations var0, GuideNpc var1, int var2) {
      return var0.chainDoneId(questId(var1, var2));
   }

   public static void setChainDone(NpcRelations var0, GuideNpc var1, int var2) {
      var0.markChainDoneId(questId(var1, var2));
   }

   public static boolean isQuestActionSingle(String var0) {
      return "quest:take".equals(var0) || "quest:decline".equals(var0) || "quest:hand".equals(var0) || "quest:drop".equals(var0);
   }

   public static boolean runActionSingle(ServerPlayer var0, GuideNpc var1, int var2, String var3) {
      if (var1 != null && var1.isEscorting()) return false;
      NpcProfile var4 = var1 == null ? null : var1.profile();
      if (var0 != null && var4 != null && var2 >= 0 && var2 < var4.quests.size()) {
         NpcProfile.Quest var5 = var4.quests.get(var2);
         switch (var3) {
            case "quest:take":
               NpcRelations var8 = NpcRelations.get(var0.server);
               if (!chainReady(var4, var2, var3x -> isDone(var8, var1, var0, var3x), var2x -> isChainDone(var8, var1, var2x))) {
                  NpcProfile.Quest var11 = questAt(var4, var5.chainPrev);
                  String var10 = var11 != null && var11.title != null && !var11.title.isEmpty() ? var11.title : "?";
                  var0.displayClientMessage(Component.translatable("message.settledlands.quest.chainlocked", new Object[]{var10}), true);
                  sound(var0, SoundEvents.VILLAGER_NO, 0.7F, 1.0F);
                  return false;
               }

               take(var0, var1, var2);
               break;
            case "quest:decline":
               clearTaken(NpcRelations.get(var0.server), var1, var0, var2);
               break;
            case "quest:drop":
               drop(var0, var1, var2);
               break;
            case "quest:hand":
               if (!handIn(var0, var1, var5)) {
                  var0.displayClientMessage(needMessage(var5), true);
                  sound(var0, SoundEvents.VILLAGER_NO, 0.7F, 1.0F);
                  return false;
               }

               NpcRelations var9 = NpcRelations.get(var0.server);
               setDone(var9, var1, var0, var2);
               clearTaken(var9, var1, var0, var2);
               if (var5.chainShared) {
                  setChainDone(var9, var1, var2);
               }

               if (!var5.reward.isEmpty()) {
                  var0.displayClientMessage(
                     Component.translatable("message.settledlands.quest.done", new Object[]{var5.reward.getHoverName(), var5.reward.getCount()}), true
                  );
               }

               sound(var0, SoundEvents.PLAYER_LEVELUP, 0.7F, 1.2F);
               break;
            default:
               return true;
         }

         return true;
      } else {
         return true;
      }
   }

   public static void drop(ServerPlayer var0, GuideNpc var1, int var2) {
      if (var1 != null && var1.isEscorting()) return;
      if (var0 != null && var1 != null) {
         NpcProfile var3 = var1.profile();
         NpcProfile.Quest var4 = var2 >= 0 && var2 < var3.quests.size() ? var3.quests.get(var2) : null;
         clearTaken(NpcRelations.get(var0.server), var1, var0, var2);
         String var5 = var4 != null && var4.title != null && !var4.title.isEmpty() ? var4.title : "?";
         var0.displayClientMessage(Component.translatable("message.settledlands.quest.dropped", new Object[]{var5}), true);
         sound(var0, SoundEvents.VILLAGER_NO, 0.7F, 0.9F);
      }
   }

   public static void take(ServerPlayer var0, GuideNpc var1, int var2) {
      if (var1 != null && var1.isEscorting()) return;
      if (var0 != null && var1 != null) {
         NpcProfile var3 = var1.profile();
         if (var2 >= 0 && var2 < var3.quests.size()) {
            NpcProfile.Quest var4 = var3.quests.get(var2);
            setTaken(NpcRelations.get(var0.server), var1, var0, var2);
            String var5 = var4.title != null && !var4.title.isEmpty() ? var4.title : "?";
            var0.displayClientMessage(Component.translatable("message.settledlands.quest.taken", new Object[]{var5}), true);
            sound(var0, SoundEvents.VILLAGER_YES, 0.7F, 1.1F);
         }
      }
   }

   private static void sound(ServerPlayer var0, SoundEvent var1, float var2, float var3) {
      var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), var1, SoundSource.PLAYERS, var2, var3);
   }

   public static List<Integer> visible(NpcProfile var0, int var1, IntPredicate var2, IntPredicate var3) {
      return visible(var0, var1, var2, var3, null);
   }

   public static List<Integer> visible(NpcProfile var0, int var1, IntPredicate var2, IntPredicate var3, IntPredicate var4) {
      ArrayList var5 = new ArrayList();
      if (var0 == null) {
         return var5;
      } else {
         for (int var6 = 0; var6 < var0.quests.size(); var6++) {
            NpcProfile.Quest var7 = var0.quests.get(var6);
            if (var7 != null
               && var7.usable()
               && var7.minRelation <= var1
               && (var7.repeatable || var3 == null || !var3.test(var6))
               && (var4 == null || var4.test(var6))) {
               var5.add(var6);
            }
         }

         return var5;
      }
   }

   public static NpcProfile.Quest questAt(NpcProfile var0, int var1) {
      return var0 != null && var1 >= 0 && var1 < var0.quests.size() ? var0.quests.get(var1) : null;
   }

   public static boolean doneFor(NpcProfile var0, int var1, boolean var2, boolean var3) {
      NpcProfile.Quest var4 = questAt(var0, var1);
      return var2 || var4 != null && var4.chainShared && var3;
   }

   public static boolean chainReady(NpcProfile var0, int var1, IntPredicate var2, IntPredicate var3) {
      NpcProfile.Quest var4 = questAt(var0, var1);
      if (var4 != null && var4.chainPrev >= 0 && var4.chainPrev != var1 && !inChainLoop(var0, var1)) {
         NpcProfile.Quest var5 = questAt(var0, var4.chainPrev);
         if (var5 == null) {
            return true;
         } else {
            return var5.chainShared
               ? var2 != null && var2.test(var4.chainPrev) || var3 != null && var3.test(var4.chainPrev)
               : var2 != null && var2.test(var4.chainPrev);
         }
      } else {
         return true;
      }
   }

   public static int chainOption(int var0, int var1, int var2) {
      if (var0 >= 0 && var0 != var1 && var0 < var2) {
         return var0 < var1 ? var0 + 1 : var0;
      } else {
         return 0;
      }
   }

   public static int chainOptionPrev(int var0, int var1) {
      if (var0 <= 0) {
         return -1;
      } else {
         return var0 <= var1 ? var0 - 1 : var0;
      }
   }

   public static boolean inChainLoop(NpcProfile var0, int var1) {
      int var2 = var1;

      for (int var3 = 0; var3 <= 6; var3++) {
         NpcProfile.Quest var4 = questAt(var0, var2);
         int var5 = var4 != null && var4.chainPrev != var2 ? var4.chainPrev : -1;
         if (var5 < 0) {
            return false;
         }

         if (var5 == var1) {
            return true;
         }

         var2 = var5;
      }

      return true;
   }

   public static int count(ServerPlayer var0, ItemStack var1) {
      if (var0 != null && var1 != null && !var1.isEmpty()) {
         int var2 = 0;

         for (int var3 = 0; var3 < var0.getInventory().getContainerSize(); var3++) {
            ItemStack var4 = var0.getInventory().getItem(var3);
            if (!var4.isEmpty() && ItemStack.isSameItemSameComponents(var4, var1)) {
               var2 += var4.getCount();
            }
         }

         return var2;
      } else {
         return 0;
      }
   }

   public static int needPoints(NpcProfile.Quest var0) {
      return var0 == null ? 0 : NpcProfile.clamp(var0.pointsCount, 1, 1000000);
   }

   public static int need(NpcProfile.Quest var0) {
      if (var0 == null) {
         return 0;
      } else if (var0.points()) {
         return needPoints(var0);
      } else {
         return var0.item != null && !var0.item.isEmpty() ? Math.max(1, var0.item.getCount()) : 0;
      }
   }

   public static boolean ready(ServerPlayer var0, NpcProfile.Quest var1) {
      if (var0 != null && var1 != null) {
         int var2 = need(var1);
         if (var2 <= 0) {
            return false;
         } else {
            return var1.points() ? NpcPoints.get(var0.server).have(var0.getUUID(), var1) >= var2 : count(var0, var1.item) >= var2;
         }
      } else {
         return false;
      }
   }

   public static Component needMessage(NpcProfile.Quest var0) {
      return var0 != null && var0.points()
         ? Component.translatable("message.settledlands.quest.needpoints", new Object[]{var0.pointsName, needPoints(var0)})
         : Component.translatable("message.settledlands.quest.need", new Object[]{var0.item.getHoverName(), need(var0)});
   }

   public static boolean handIn(ServerPlayer var0, GuideNpc var1, NpcProfile.Quest var2) {
      if (var1 != null && var1.isEscorting()) return false;
      if (var0 != null && var1 != null && var2 != null && ready(var0, var2)) {
         if (var2.points()) {
            if (var2.pointsSpend && !NpcPoints.get(var0.server).spend(var0.getUUID(), var2.pointsName, var2.pointsShared, needPoints(var2))) {
               return false;
            }
         } else {
            take(var0, var2.item, need(var2));
         }

         ItemStack var3 = var2.reward == null ? ItemStack.EMPTY : var2.reward.copy();
         if (!var3.isEmpty() && !var0.getInventory().add(var3)) {
            var0.drop(var3, false);
         }

         if (var2.rewardRelation != 0) {
            GuideNpc.addRelation(var0, var1, var2.rewardRelation);
         }

         var0.containerMenu.broadcastChanges();
         return true;
      } else {
         return false;
      }
   }

   private static int take(ServerPlayer var0, ItemStack var1, int var2) {
      int var3 = var2;

      for (int var4 = 0; var4 < var0.getInventory().getContainerSize() && var3 > 0; var4++) {
         ItemStack var5 = var0.getInventory().getItem(var4);
         if (!var5.isEmpty() && ItemStack.isSameItemSameComponents(var5, var1)) {
            int var6 = Math.min(var3, var5.getCount());
            var5.shrink(var6);
            var3 -= var6;
         }
      }

      return var2 - var3;
   }

   public static boolean runAction(ServerPlayer var0, GuideNpc var1, int var2, String var3) {
      return runActionSingle(var0, var1, var2, ActionSequence.first(var3));
   }

   public static boolean isQuestAction(String var0) {
      return isQuestActionSingle(ActionSequence.first(var0));
   }
}
