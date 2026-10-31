package dev.settledlands.npc;

import net.minecraft.world.item.ItemStack;

public final class NpcEdit {
   private NpcEdit() {
   }

   public static NpcProfile apply(NpcProfile var0, String var1, int var2, int var3, int var4, String var5, String var6) {
      if (var0 != null && var1 != null) {
         NpcProfile var7 = var0.copy();
         switch (var1) {
            case "escort.settings":
               var7.escortCode = NpcProfile.clean(var5, EscortRules.MAX_CODE);
               var7.escortCoordinates = NpcProfile.clean(var6, NpcProfile.MAX_MOBILITY_COORDINATES);
               break;
            case "field":
               field(var7, var5, var6);
               break;
            case "welcome.add":
               if (var7.welcomeLines.size() >= 6) {
                  return null;
               }

               var7.welcomeLines.add("");
               break;
            case "welcome.del":
               if (!inRange(var2, var7.welcomeLines.size())) {
                  return null;
               }

               var7.welcomeLines.remove(var2);
               break;
            case "welcome.field":
               if (!inRange(var2, var7.welcomeLines.size())) {
                  return null;
               }

               var7.welcomeLines.set(var2, NpcProfile.clean(var6, 480));
               break;
            case "topic.add":
               if (NpcProfile.normalTopics(var7) >= 8) {
                  return null;
               }

               NpcProfile.Topic var10 = new NpcProfile.Topic("Новый ответ", NpcProfile.clamp(parse(var6, 0), 0, NpcProfile.TYPE_COUNT - 1));
               var10.steps.add(new NpcProfile.Step());
               var7.topics.add(var10);
               break;
            case "topic.del":
               if (!inRange(var2, var7.topics.size()) || NpcProfile.isQuestTopic(var7.topics.get(var2))) {
                  return null;
               }

               var7.topics.remove(var2);
               break;
            case "topic.move":
               if (!inRange(var2, var7.topics.size()) || NpcProfile.isQuestTopic(var7.topics.get(var2))) {
                  return null;
               }

               int var11 = var2 + var3;
               if (!inRange(var11, var7.topics.size())) {
                  return null;
               }

               if (NpcProfile.isQuestTopic(var7.topics.get(var11))) {
                  return null;
               }

               var7.topics.add(var11, var7.topics.remove(var2));
               break;
            case "topic.field":
               NpcProfile.Topic var12 = topic(var7, var2);
               if (var12 == null) {
                  return null;
               }

               switch (var5) {
                  case "title":
                     var12.title = NpcProfile.clean(var6, 64);
                     return var7.sanitized();
                  case "kind":
                     var12.kind = NpcProfile.clean(var6, 16);
                     return var7.sanitized();
                  case "type":
                     var12.type = NpcProfile.clamp(parse(var6, 0), 0, NpcProfile.TYPE_COUNT - 1);
                     return var7.sanitized();
                  default:
                     return null;
               }
            case "step.add":
               NpcProfile.Topic var13 = topic(var7, var2);
               if (var13 == null || var13.steps.size() >= 16) {
                  return null;
               }

               var13.steps.add(new NpcProfile.Step());
               break;
            case "step.insert":
               NpcProfile.Topic var14 = topic(var7, var2);
               if (var14 == null || var14.steps.size() >= 16) {
                  return null;
               }

               int var15 = NpcProfile.clamp(var3, 0, var14.steps.size());
               var14.steps.add(var15, new NpcProfile.Step());
               shiftLinks(var14, var15, 1);
               break;
            case "step.del":
               NpcProfile.Topic var16 = topic(var7, var2);
               if (var16 == null || !inRange(var3, var16.steps.size())) {
                  return null;
               }

               var16.steps.remove(var3);
               shiftLinks(var16, var3, -1);
               break;
            case "step.field":
               NpcProfile.Step var17 = step(var7, var2, var3);
               if (var17 == null) {
                  return null;
               }

               if ("line".equals(var5)) {
                  var17.line = NpcProfile.clean(var6, 480);
               } else {
                  if (!"action".equals(var5)) {
                     return null;
                  }

                  var17.action = NpcProfile.clean(var6, 2048);
               }
               break;
            case "variant.add":
               NpcProfile.Step var18 = step(var7, var2, var3);
               if (var18 == null || var18.variants.size() >= 5) {
                  return null;
               }

               var18.variants.add(new NpcProfile.Variant(40, ""));
               break;
            case "variant.del":
               NpcProfile.Step var19 = step(var7, var2, var3);
               if (var19 == null || !inRange(var4, var19.variants.size())) {
                  return null;
               }

               var19.variants.remove(var4);
               break;
            case "variant.field":
               NpcProfile.Step var20 = step(var7, var2, var3);
               if (var20 != null && inRange(var4, var20.variants.size())) {
                  NpcProfile.Variant var37 = var20.variants.get(var4);
                  switch (var5) {
                     case "min":
                        var37.minRelation = NpcProfile.clamp(parse(var6, 0), 0, 100);
                        return var7.sanitized();
                     case "text":
                        var37.text = NpcProfile.clean(var6, 480);
                        return var7.sanitized();
                     default:
                        return null;
                  }
               }

               return null;
            case "answer.add":
               NpcProfile.Step var21 = step(var7, var2, var3);
               if (var21 == null || var21.answers.size() >= 6) {
                  return null;
               }

               var21.answers.add(new NpcProfile.Answer());
               break;
            case "answer.del":
               NpcProfile.Step var22 = step(var7, var2, var3);
               if (var22 == null || !inRange(var4, var22.answers.size())) {
                  return null;
               }

               var22.answers.remove(var4);
               break;
            case "answer.field":
               NpcProfile.Answer var23 = answer(var7, var2, var3, var4);
               if (var23 == null) {
                  return null;
               }

               switch (var5) {
                  case "player":
                     var23.player = NpcProfile.clean(var6, 240);
                     return var7.sanitized();
                  case "reaction":
                     var23.reaction = NpcProfile.clean(var6, 480);
                     return var7.sanitized();
                  case "relation":
                     var23.relation = NpcProfile.clamp(parse(var6, 0), -100, 100);
                     return var7.sanitized();
                  case "minRelation":
                     var23.minRelation = NpcProfile.clamp(parse(var6, 0), 0, 100);
                     if (var23.maxRelation < var23.minRelation) {
                        var23.maxRelation = var23.minRelation;
                     }

                     return var7.sanitized();
                  case "maxRelation":
                     var23.maxRelation = NpcProfile.clamp(parse(var6, 100), 0, 100);
                     if (var23.minRelation > var23.maxRelation) {
                        var23.minRelation = var23.maxRelation;
                     }

                     return var7.sanitized();
                  case "pointsName":
                     var23.pointsName = NpcPoints.cleanName(var6);
                     return var7.sanitized();
                  case "minPoints":
                     var23.minPoints = NpcProfile.clamp(parse(var6, 1), 0, NpcPoints.MAX_VALUE);
                     if (var23.maxPoints < var23.minPoints) {
                        var23.maxPoints = var23.minPoints;
                     }
                     return var7.sanitized();
                  case "maxPoints":
                     var23.maxPoints = NpcProfile.clamp(parse(var6, NpcPoints.MAX_VALUE), 0, NpcPoints.MAX_VALUE);
                     if (var23.minPoints > var23.maxPoints) {
                        var23.minPoints = var23.maxPoints;
                     }
                     return var7.sanitized();
                  case "pointsShared":
                     var23.pointsShared = parse(var6, 0) != 0;
                     return var7.sanitized();
                  case "action":
                     var23.action = NpcProfile.clean(var6, 2048);
                     return var7.sanitized();
                  case "next":
                     var23.next = parse(var6, -1);
                     return var7.sanitized();
                  default:
                     return null;
               }
            case "quest.add":
               if (var7.quests.size() >= 6) {
                  return null;
               }

               var7.quests.add(NpcProfile.newQuest(ItemStack.EMPTY, ItemStack.EMPTY));
               NpcProfile.syncQuestTopics(var7);
               break;
            case "quest.del":
               if (!inRange(var2, var7.quests.size())) {
                  return null;
               }

               var7.quests.remove(var2);

               for (NpcProfile.Quest var40 : var7.quests) {
                  if (var40 != null) {
                     if (var40.chainPrev == var2) {
                        var40.chainPrev = -1;
                     } else if (var40.chainPrev > var2) {
                        var40.chainPrev--;
                     }
                  }
               }

               NpcProfile.syncQuestTopics(var7);
               break;
            case "quest.item":
               NpcProfile.Quest var24 = quest(var7, var2);
               if (var24 == null) {
                  return null;
               }

               ItemStack var25 = NpcItems.stack(var6, 1);
               if (var25.isEmpty()) {
                  return null;
               }

               if (var3 == 0) {
                  var25.setCount(var24.item.isEmpty() ? 1 : var24.item.getCount());
                  var24.item = var25;
                  var24.kind = 0;
                  syncHandLabel(var7, var2, 0);
               } else {
                  var25.setCount(var24.reward.isEmpty() ? 1 : var24.reward.getCount());
                  var24.reward = var25;
               }
               break;
            case "quest.count":
               NpcProfile.Quest var26 = quest(var7, var2);
               if (var26 == null) {
                  return null;
               }

               if (var3 == 0 && var26.points()) {
                  var26.pointsCount = NpcProfile.clamp(parse(var6, 1), 1, 1000000);
               } else {
                  ItemStack var42 = var3 == 0 ? var26.item : var26.reward;
                  if (var42.isEmpty()) {
                     return null;
                  }

                  var42.setCount(NpcProfile.clamp(parse(var6, 1), 1, 64));
               }
               break;
            case "quest.field":
               NpcProfile.Quest var27 = quest(var7, var2);
               if (var27 == null) {
                  return null;
               }

               switch (var5) {
                  case "title":
                     var27.title = NpcProfile.clean(var6, 64);
                     return var7.sanitized();
                  case "text":
                     var27.text = NpcProfile.clean(var6, 480);
                     return var7.sanitized();
                  case "done":
                     var27.done = NpcProfile.clean(var6, 480);
                     return var7.sanitized();
                  case "rewardRelation":
                     var27.rewardRelation = NpcProfile.clamp(parse(var6, 0), -100, 100);
                     return var7.sanitized();
                  case "minRelation":
                     var27.minRelation = NpcProfile.clamp(parse(var6, 0), 0, 100);
                     return var7.sanitized();
                  case "repeatable":
                     var27.repeatable = parse(var6, 0) != 0;
                     return var7.sanitized();
                  case "kind":
                     var27.kind = parse(var6, 0) == 1 ? 1 : 0;
                     syncHandLabel(var7, var2, var27.kind);
                     return var7.sanitized();
                  case "pointsName":
                     var27.pointsName = NpcPoints.cleanName(var6);
                     return var7.sanitized();
                  case "pointsCount":
                     var27.pointsCount = NpcProfile.clamp(parse(var6, 1), 1, 1000000);
                     return var7.sanitized();
                  case "pointsShared":
                     var27.pointsShared = parse(var6, 0) != 0;
                     return var7.sanitized();
                  case "pointsSpend":
                     var27.pointsSpend = parse(var6, 0) != 0;
                     return var7.sanitized();
                  case "chainPrev":
                     int var44 = parse(var6, -1);
                     var27.chainPrev = var44 >= 0 && var44 != var2 && inRange(var44, var7.quests.size()) ? var44 : -1;
                     return var7.sanitized();
                  case "chainShared":
                     var27.chainShared = parse(var6, 0) != 0;
                     return var7.sanitized();
                  default:
                     return null;
               }
            case "offer.add":
               if (var7.offers.size() >= 32) {
                  return null;
               }

               var7.offers.add(NpcProfile.newOffer(NpcItems.stack("minecraft:emerald", 1), NpcItems.stack("minecraft:iron_ingot", 3), "", 0));
               break;
            case "offer.del":
               if (!inRange(var2, var7.offers.size())) {
                  return null;
               }

               var7.offers.remove(var2);
               break;
            case "offer.move":
               if (!inRange(var2, var7.offers.size())) {
                  return null;
               }

               int var28 = var2 + var3;
               if (!inRange(var28, var7.offers.size())) {
                  return null;
               }

               var7.offers.add(var28, var7.offers.remove(var2));
               break;
            case "offer.item":
               if (!inRange(var2, var7.offers.size())) {
                  return null;
               }

               NpcProfile.Offer var29 = var7.offers.get(var2);
               ItemStack var30 = NpcItems.stack(var6, 1);
               if (var30.isEmpty()) {
                  return null;
               }

               if (var3 == 0) {
                  var30.setCount(var29.stack.isEmpty() ? 1 : var29.stack.getCount());
                  var29.stack = var30;
               } else {
                  var30.setCount(var29.price.isEmpty() ? 1 : var29.price.getCount());
                  var29.price = var30;
               }
               break;
            case "offer.count":
               if (!inRange(var2, var7.offers.size())) {
                  return null;
               }

               NpcProfile.Offer var31 = var7.offers.get(var2);
               ItemStack var32 = var3 == 0 ? var31.stack : var31.price;
               if (var32.isEmpty()) {
                  return null;
               }

               var32.setCount(NpcProfile.clamp(parse(var6, 1), 1, 64));
               break;
            case "offer.field":
               if (!inRange(var2, var7.offers.size())) {
                  return null;
               }

               NpcProfile.Offer var33 = var7.offers.get(var2);
               switch (var5) {
                  case "desc":
                     var33.description = NpcProfile.clean(var6, 240);
                     return var7.sanitized();
                  case "minRelation":
                     var33.minRelation = NpcProfile.clamp(parse(var6, 0), 0, 100);
                     return var7.sanitized();
                  default:
                     return null;
               }
            default:
               return null;
         }

         return var7.sanitized();
      } else {
         return null;
      }
   }

   private static void field(NpcProfile var0, String var1, String var2) {
      if (var1 != null) {
         switch (var1) {
            case "name":
               var0.name = NpcProfile.clean(var2, 32);
               break;
            case "identifier":
               var0.identifier = NpcProfile.clean(var2, 64);
               break;
            case "title":
               var0.title = NpcProfile.clean(var2, 32);
               break;
            case "texture":
               var0.texture = NpcProfile.clean(var2, 96);
               break;
            case "focusNpcUuid":
               var0.focusNpcUuid = NpcProfile.clean(var2, 36);
               break;
            case "scale":
               var0.scale = NpcProfile.clamp(parse(var2, 100), 50, 150);
               break;
            case "pose":
               var0.pose = NpcProfile.clamp(parse(var2, 0), 0, 2);
               break;
            case "nameTag":
               var0.nameTag = parse(var2, 1) != 0;
               break;
            case "lookAt":
               var0.lookAtPlayer = parse(var2, 1) != 0;
               break;
            case "escortCode":
               var0.escortCode = NpcProfile.clean(var2, EscortRules.MAX_CODE);
               break;
            case "escortCoordinates":
               var0.escortCoordinates = NpcProfile.clean(var2, NpcProfile.MAX_MOBILITY_COORDINATES);
               break;
            case "escortTravelLine":
               var0.escortTravelLine = NpcProfile.clean(var2, NpcProfile.MAX_LINE);
               break;
            case "escortArrivalLine":
               var0.escortArrivalLine = NpcProfile.clean(var2, NpcProfile.MAX_LINE);
               break;
            case "idleWalking":
               var0.idleWalking = parse(var2, 0) != 0;
               break;
            case "mobilityGroupId":
               var0.mobilityGroupId = NpcProfile.clean(var2, 36);
               break;
            case "default":
               var0.defaultRelation = NpcProfile.clamp(parse(var2, 0), 0, 100);
               break;
            case "welcome":
               var0.welcome = NpcProfile.clean(var2, 480);
               break;
            case "style":
               var0.style = NpcProfile.clamp(parse(var2, 0), 0, 1);
               break;
            case "textColor":
               var0.textColor = NpcProfile.clamp(parse(var2, 0), 0, 3);
               break;
            case "nameColor":
               var0.nameColor = NpcProfile.clamp(parse(var2, 1), 0, 3);
               break;
            case "typingSpeed":
               var0.typingSpeed = NpcProfile.clamp(parse(var2, 30), 0, 200);
               break;
            case "voice":
               var0.voice = NpcProfile.clean(var2, 32);
               break;
            case "voicePitch":
               var0.voicePitch = NpcProfile.clamp(parse(var2, 0), 0, 12);
               break;
            case "showSpeaker":
               var0.showSpeaker = parse(var2, 1) != 0;
               break;
            case "keyHints":
               var0.keyHints = parse(var2, 1) != 0;
               break;
            case "answerNumbers":
               var0.answerNumbers = parse(var2, 0) != 0;
               break;
            case "showDialogueTab":
               var0.showDialogueTab = parse(var2, 1) != 0;
               break;
            case "showTradeTab":
               var0.showTradeTab = parse(var2, 1) != 0;
               break;
            case "showQuestTab":
               var0.showQuestTab = parse(var2, 1) != 0;
               break;
            case "showPhrasesTab":
               var0.showPhrasesTab = parse(var2, 1) != 0;
               break;
            case "defaultTab":
               var0.defaultTab = NpcProfile.clamp(parse(var2, 0), 0, 3);
               break;
            case "maxPerPurchase":
               var0.maxPerPurchase = NpcProfile.clamp(parse(var2, 8), 1, 8);
               break;
            case "discount":
               var0.discount = NpcProfile.clamp(parse(var2, 50), 0, 90);
               break;
            case "zoom":
               var0.zoom = NpcProfile.clamp(parse(var2, 100), 70, 150);
               break;
            case "offsetX":
               var0.offsetX = NpcProfile.clamp(parse(var2, 0), -40, 40);
               break;
            case "offsetY":
               var0.offsetY = NpcProfile.clamp(parse(var2, 0), -40, 40);
               break;
            case "cameraOnFace":
               var0.cameraOnFace = parse(var2, 0) != 0;
               break;
            case "blockMouseLook":
               var0.blockMouseLook = parse(var2, 0) != 0;
               break;
            case "dim":
               var0.dim = NpcProfile.clamp(parse(var2, 45), 0, 80);
               break;
            case "blur":
               var0.blur = NpcProfile.clamp(parse(var2, 0), 0, 2);
               break;
            case "muteHud":
               var0.muteHud = parse(var2, 1) != 0;
               break;
            case "vignette":
               var0.vignette = parse(var2, 1) != 0;
               break;
            case "radius":
               var0.radius = NpcProfile.clamp(parse(var2, 4), 2, 16);
               break;
            case "exitKey":
               var0.exitKey = NpcProfile.clamp(parse(var2, 0), 0, 1);
               break;
            case "blockMovement":
               var0.blockMovement = parse(var2, 1) != 0;
               break;
            case "pauseWorld":
               var0.pauseWorld = parse(var2, 0) != 0;
               break;
            case "exitAction":
               var0.exitAction = NpcProfile.clamp(parse(var2, 0), 0, 2);
               break;
            default:
               if (var1.startsWith("rep") && var1.length() == 4) {
                  int var5 = var1.charAt(3) - '0';
                  if (var5 >= 0 && var5 < 5) {
                     while (var0.reputationNames.size() < 5) {
                        var0.reputationNames.add("");
                     }

                     var0.reputationNames.set(var5, NpcProfile.clean(var2, 24));
                  }
               }
         }
      }
   }

   private static NpcProfile.Topic topic(NpcProfile var0, int var1) {
      return inRange(var1, var0.topics.size()) ? var0.topics.get(var1) : null;
   }

   private static NpcProfile.Step step(NpcProfile var0, int var1, int var2) {
      NpcProfile.Topic var3 = topic(var0, var1);
      return var3 != null && inRange(var2, var3.steps.size()) ? var3.steps.get(var2) : null;
   }

   private static void shiftLinks(NpcProfile.Topic var0, int var1, int var2) {
      int var3 = var0.steps.size() - 1;

      for (NpcProfile.Step var5 : var0.steps) {
         for (NpcProfile.Answer var7 : var5.answers) {
            if (var7.next != -1 && var7.next < 1000) {
               if (var2 > 0) {
                  if (var7.next >= var1) {
                     var7.next = var3 < 0 ? -1 : NpcProfile.clamp(var7.next + 1, 0, var3);
                  }
               } else if (var7.next == var1) {
                  var7.next = -1;
               } else if (var7.next > var1) {
                  var7.next = var3 < 0 ? -1 : NpcProfile.clamp(var7.next - 1, 0, var3);
               }
            }
         }
      }
   }

   private static NpcProfile.Answer answer(NpcProfile var0, int var1, int var2, int var3) {
      NpcProfile.Step var4 = step(var0, var1, var2);
      return var4 != null && inRange(var3, var4.answers.size()) ? var4.answers.get(var3) : null;
   }

   private static void syncHandLabel(NpcProfile var0, int var1, int var2) {
      int var3 = var0.questTopicIndex(var1, 3);
      if (var3 >= 0 && var3 < var0.topics.size()) {
         String var4 = var2 == 1 ? "#gui.settledlands.quest.handinpoints" : "#gui.settledlands.quest.handin";
         String var5 = var2 == 1 ? "#gui.settledlands.quest.handin" : "#gui.settledlands.quest.handinpoints";

         for (NpcProfile.Step var7 : var0.topics.get(var3).steps) {
            for (NpcProfile.Answer var9 : var7.answers) {
               if ("quest:hand".equals(ActionSequence.first(var9.action))
                  && (var9.player == null || var9.player.isEmpty() || var4.equals(var9.player) || var5.equals(var9.player))) {
                  var9.player = var4;
               }
            }
         }
      }
   }

   private static NpcProfile.Quest quest(NpcProfile var0, int var1) {
      return inRange(var1, var0.quests.size()) ? var0.quests.get(var1) : null;
   }

   private static boolean inRange(int var0, int var1) {
      return var0 >= 0 && var0 < var1;
   }

   private static int parse(String var0, int var1) {
      if (var0 != null && !var0.isEmpty()) {
         try {
            return Integer.parseInt(var0.trim());
         } catch (NumberFormatException var3) {
            return var1;
         }
      } else {
         return var1;
      }
   }
}
