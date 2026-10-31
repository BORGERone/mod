package dev.settledlands.npc;

import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

@EventBusSubscriber(
   modid = "settledlands"
)
public final class GuideDialogue {
   private static final String KIND_LEGACY = "#gui.settledlands.dlg.legacy.kind";
   private static final String HINT_SAY = "#gui.settledlands.dlg.hint.say";
   private static final String HINT_NEXT = "#gui.settledlands.dlg.hint.next";
   private static final String BTN_NEXT = "#gui.settledlands.dlg.next";
   private static final String BTN_MORE = "#gui.settledlands.dlg.more";
   private static final String BTN_FINISH = "#gui.settledlands.dlg.finish";
   private static final String LINE_LEGACY = "#gui.settledlands.dlg.legacy";
   private static final String LINE_SILENT = "#gui.settledlands.dlg.silent";
   private static final String QUEST_LIST = "#gui.settledlands.quest.list";
   private static final String QUEST_ROW = "#gui.settledlands.quest.row";
   private static final String QUEST_TAKEN_MARK = "#gui.settledlands.quest.mark.taken";
   private static final String QUEST_NONE = "#gui.settledlands.quest.none";
   private static final int QUEST_ENTRY = -2;
   private static final String QUEST_ACTIONS = "#gui.settledlands.quest.actions";
   public static final int MENU_HAND = 0;
   public static final int MENU_WHERE = 1;
   public static final int MENU_DROP = 2;
   private static final String[] MENU_KEYS = new String[]{"#gui.settledlands.quest.handin", "#gui.settledlands.quest.where", "#gui.settledlands.quest.abandon"};
   private static final long PICK_DEBOUNCE_MS = 250L;
   public static final int MENU_SKIP_REMIND = -1;
   private static final char ARG = '\u0001';
   private static final Map<UUID, GuideDialogue.Session> SESSIONS = new HashMap<>();
   private static final Map<UUID, GuideDialogue.Observation> OBSERVING = new HashMap<>();
   private static final Map<UUID, Set<UUID>> LISTENERS = new HashMap<>();

   private GuideDialogue() {
   }

   public static void start(Player var0, GuideNpc var1) {
      if (var0 instanceof ServerPlayer var2 && !var1.level().isClientSide()) {
         if (var1.isEscorting()) {
            if (!var1.escort().openArrival(var1, var2)) startEscort(var2, var1, false);
            return;
         }
         stop(var2);
         NpcProfile var3 = var1.profile();
         GuideDialogue.Session var4 = new GuideDialogue.Session(var1.getId(), var1.level().dimension(), var1.relationTo(var2));
         var4.focusEntityId = var1.getId();
         var4.focusSpeaker = speaker(var1);
         var4.focusTitle = var3.title == null ? "" : var3.title;
         var4.focusNameColor = NpcProfile.clamp(var3.nameColor, 0, 3);
         SESSIONS.put(var2.getUUID(), var4);
         var1.setTalking(true);
         var1.getNavigation().stop();
         if (var3.lookAtPlayer) {
            var1.getLookControl().setLookAt(var2, 40.0F, 40.0F);
         }

         var4.rootCursor = 0;
         var4.defaultTabDone = false;
         if (graphActive(var3) && var3.defaultTab == 0) startGraph(var2, var4, var1);
         else sendRoot(var2, var4, var1);
      }
   }

   public static boolean isInDialogue(ServerPlayer player) {
      return SESSIONS.containsKey(player.getUUID()) || OBSERVING.containsKey(player.getUUID());
   }

   public static boolean hasEscortConversation(ServerPlayer player, GuideNpc npc) {
      Session session = SESSIONS.get(player.getUUID());
      return session != null && session.entityId == npc.getId() && session.dimension.equals(npc.level().dimension()) && session.escortLine != 0;
   }

   public static boolean hasEscortArrival(ServerPlayer player, GuideNpc npc) {
      Session session = SESSIONS.get(player.getUUID());
      return session != null && session.entityId == npc.getId() && session.dimension.equals(npc.level().dimension()) && session.escortLine == 2;
   }

   public static void closeNpcDialogues(GuideNpc npc) {
      if (!(npc.level() instanceof ServerLevel level)) return;
      ArrayList<UUID> players = new ArrayList<>();
      for (Entry<UUID, Session> entry : SESSIONS.entrySet()) {
         Session session = entry.getValue();
         if (session.entityId == npc.getId() && session.dimension.equals(npc.level().dimension())) players.add(entry.getKey());
      }
      for (UUID id : players) {
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
         if (player != null) stop(player);
         else {
            Session session = SESSIONS.remove(id);
            if (session != null) closeListeners(level.getServer(), id, session.entityId);
         }
      }
      npc.setTalking(false);
   }

   public static void startEscort(ServerPlayer player, GuideNpc npc, boolean arrival) {
      if (!npc.isEscorting() || player.level() != npc.level()) return;
      stop(player);
      NpcProfile profile = npc.profile();
      Session session = new Session(npc.getId(), npc.level().dimension(), npc.relationTo(player));
      session.escortLine = arrival ? 2 : 1;
      session.focusEntityId = npc.getId();
      session.focusSpeaker = speaker(npc);
      session.focusTitle = profile.title;
      session.focusNameColor = NpcProfile.clamp(profile.nameColor, 0, 3);
      SESSIONS.put(player.getUUID(), session);
      npc.setTalking(true);
      if (arrival || npc.escort().belongsTo(player)) NpcEscort.stopMotion(npc);
      if (profile.lookAtPlayer) npc.getLookControl().setLookAt(player, 40.0F, 40.0F);
      sendEscortLine(player, session, npc);
   }

   private static void sendEscortLine(ServerPlayer player, Session session, GuideNpc npc) {
      NpcProfile profile = npc.profile();
      String text = session.escortLine == 2 ? profile.escortArrivalLine : profile.escortTravelLine;
      session.state = 2;
      session.inShop = false;
      NpcNet.DlgStyle restricted = new NpcNet.DlgStyle(
         profile.style, profile.textColor, profile.nameColor, profile.typingSpeed,
         profile.showSpeaker, profile.keyHints, profile.answerNumbers, profile.reputationLabel(session.relation),
         false, false, false, false, 0, profile.zoom, profile.offsetX, profile.offsetY, profile.cameraOnFace,
         profile.blockMouseLook, profile.dim, profile.blur, profile.muteHud, profile.vignette,
         profile.exitKey, profile.blockMovement, profile.pauseWorld, profile.voice, profile.voicePitch);
      NpcNet.sendTo(player, new NpcNet.DlgView(npc.getId(), speaker(npc), profile.title,
         fill(text, player, session, profile), List.of(BTN_FINISH), List.of(HINT_NEXT),
         session.relation, 2, false, restricted));
   }

   private static boolean sendDefaultTab(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, String var4) {
      return switch (NpcProfile.clamp(var3.defaultTab, 0, 3)) {
         case 1 -> defaultTabOr(var3.showTradeTab && var3.hasShop(), () -> {
            var1.inShop = true;
            NpcShop.send(var0, var2);
         });
         case 2 -> defaultTabOr(
            var3.showQuestTab && (!var3.quests.isEmpty() || (var3.dialogueGraph != null && var3.dialogueGraph.hasQuestNodes())) && (!questList(var0, var2, var3, var1.relation).isEmpty() || !graphQuestNodes(var0, var2, var3, var1.relation).isEmpty()),
            () -> openQuestList(var0, var1, var2, var3, questTopicIndex(var3, var2), var4)
         );
         case 3 -> defaultTabOr(var3.showPhrasesTab && !group(var2.sortedPhrases()).isEmpty(), () -> startLegacy(var0, var1, var2));
         default -> false;
      };
   }

   private static boolean defaultTabOr(boolean var0, Runnable var1) {
      if (!var0) {
         return false;
      } else {
         var1.run();
         return true;
      }
   }

   public static void stop(ServerPlayer var0) {
      if (OBSERVING.containsKey(var0.getUUID())) {
         stopListening(var0);
         return;
      }

      GuideDialogue.Session var1 = SESSIONS.remove(var0.getUUID());
      if (var1 != null) {
         GuideNpc var2 = npcOf(var0.server, var1);
         if (var2 != null && var2.isEscorting()) var2.escort().dialogueClosed(var0, var1.escortLine == 2);
         if (var2 != null && !stillTalking(var0.server, var2, var0.getUUID())) {
            var2.setTalking(false);
         }

         if (var1.graphNode >= 0 && var1.lastExecutedTriggersNode != var1.graphNode && var2 != null && graphActive(var2.profile())) {
            var1.lastExecutedTriggersNode = var1.graphNode;
            NpcDialogueGraph graph = var2.profile().dialogueGraph;
            NpcDialogueGraph.Node line = graph != null ? graph.get(var1.graphNode) : null;
            if (line != null) {
               if (!line.action.isBlank()) NpcActions.run(var0, var2, var1.relation, line.action);
               executeGraphTriggers(var0, var2, var1.relation, graph, line.triggers);
            }
         }

         closeListeners(var0.server, var0.getUUID(), var1.entityId);
         NpcNet.sendTo(var0, new NpcNet.DlgEnd(var1.entityId));
      }
   }

   public static boolean listenToNpc(ServerPlayer var0, GuideNpc var1) {
      if (var0 == null || var1 == null || var1.level().isClientSide()) {
         return false;
      }

      UUID var2 = null;
      GuideDialogue.Session var3 = null;
      double var4 = Double.MAX_VALUE;
      for (Entry<UUID, GuideDialogue.Session> var6 : SESSIONS.entrySet()) {
         GuideDialogue.Session var7 = var6.getValue();
         if (var7.entityId == var1.getId() && var7.dimension.equals(var1.level().dimension())) {
            ServerPlayer var8 = var0.server.getPlayerList().getPlayer(var6.getKey());
            if (var8 != null && var8.isAlive()) {
               double var9 = var8.distanceToSqr(var0);
               if (var9 < var4) {
                  var4 = var9;
                  var2 = var6.getKey();
                  var3 = var7;
               }
            }
         }
      }

      if (var2 == null || var3 == null || var2.equals(var0.getUUID()) || SESSIONS.containsKey(var0.getUUID())) {
         return false;
      }

      ServerPlayer var10 = var0.server.getPlayerList().getPlayer(var2);
      if (var10 == null) {
         return false;
      }
      return observe(var0, var10, var3);
   }

   public static boolean listenToPlayer(ServerPlayer var0, ServerPlayer var1) {
      if (var0 == null || var1 == null || var0.getUUID().equals(var1.getUUID()) || SESSIONS.containsKey(var0.getUUID())) {
         return false;
      }

      GuideDialogue.Session var2 = SESSIONS.get(var1.getUUID());
      if (var2 == null || npcOf(var1.server, var2) == null) {
         return false;
      }
      return observe(var0, var1, var2);
   }

   private static boolean observe(ServerPlayer var0, ServerPlayer var1, GuideDialogue.Session var2) {
      UUID var3 = var0.getUUID();
      UUID var4 = var1.getUUID();
      GuideDialogue.Observation var5 = OBSERVING.get(var3);
      if (var5 != null && var5.owner.equals(var4)) {
         stopListening(var0);
         return true;
      }

      if (var5 != null) {
         stopListening(var0);
      }

      OBSERVING.put(var3, new GuideDialogue.Observation(var4, var2.entityId));
      LISTENERS.computeIfAbsent(var4, var6 -> new HashSet<>()).add(var3);
      GuideNpc var6 = npcOf(var1.server, var2);
      if (var6 == null) {
         stopListening(var0);
         return false;
      }

      if (var2.inShop) {
         if (var2.lastView != null) {
            NpcNet.sendRaw(var0, var2.lastView.asObserver());
            NpcNet.sendRaw(var0, new NpcNet.DlgFocus(var2.entityId, var2.focusEntityId, var2.focusSpeaker,
               var2.focusTitle, var2.focusNameColor));
         }
         NpcShop.send(var1, var6);
      } else {
         resend(var1, var2, var6);
      }
      return true;
   }

   private static void stopListening(ServerPlayer var0) {
      UUID var1 = var0.getUUID();
      GuideDialogue.Observation var2 = OBSERVING.remove(var1);
      if (var2 == null) {
         return;
      }

      Set<UUID> var3 = LISTENERS.get(var2.owner);
      if (var3 != null) {
         var3.remove(var1);
         if (var3.isEmpty()) {
            LISTENERS.remove(var2.owner);
         }
      }
      NpcNet.sendTo(var0, new NpcNet.DlgEnd(var2.entityId));
   }

   private static void closeListeners(MinecraftServer var0, UUID var1, int var2) {
      Set<UUID> var3 = LISTENERS.remove(var1);
      if (var3 == null) {
         return;
      }

      for (UUID var4 : var3) {
         GuideDialogue.Observation var5 = OBSERVING.get(var4);
         if (var5 != null && var5.owner.equals(var1)) {
            OBSERVING.remove(var4);
            ServerPlayer var6 = var0.getPlayerList().getPlayer(var4);
            if (var6 != null) {
               NpcNet.sendTo(var6, new NpcNet.DlgEnd(var2));
            }
         }
      }
   }

   public static boolean isObserver(ServerPlayer var0) {
      return var0 != null && OBSERVING.containsKey(var0.getUUID());
   }

   public static void broadcastView(ServerPlayer var0, NpcNet.DlgView var1) {
      GuideDialogue.Session var0Session = SESSIONS.get(var0.getUUID());
      if (var0Session != null) {
         var0Session.lastView = var1;
      }
      Set<UUID> var2 = LISTENERS.get(var0.getUUID());
      if (var2 == null) {
         return;
      }
      for (UUID var3 : new HashSet<>(var2)) {
         ServerPlayer var4 = var0.server.getPlayerList().getPlayer(var3);
         if (var4 != null) {
            NpcNet.sendRaw(var4, var1.asObserver());
         }
      }
   }

   public static void broadcastFocus(ServerPlayer var0, NpcNet.DlgFocus var1) {
      Set<UUID> var2 = LISTENERS.get(var0.getUUID());
      if (var2 == null) {
         return;
      }
      for (UUID var3 : new HashSet<>(var2)) {
         ServerPlayer var4 = var0.server.getPlayerList().getPlayer(var3);
         if (var4 != null) {
            NpcNet.sendRaw(var4, var1);
         }
      }
   }

   public static void broadcastShop(ServerPlayer var0, NpcNet.ShopView var1) {
      Set<UUID> var2 = LISTENERS.get(var0.getUUID());
      if (var2 == null) {
         return;
      }
      for (UUID var3 : new HashSet<>(var2)) {
         ServerPlayer var4 = var0.server.getPlayerList().getPlayer(var3);
         if (var4 != null) {
            NpcNet.sendRaw(var4, var1.asObserver());
         }
      }
   }

   @SubscribeEvent
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && var0.getTarget() instanceof ServerPlayer var2 && listenToPlayer(var1, var2)) {
         var0.setCancellationResult(InteractionResult.SUCCESS);
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      ItemStack stack = event.getItemStack();
      if (!stack.isEmpty() && (stack.is(Items.STICK) || (NpcContent.coordStick() != null && stack.is(NpcContent.coordStick())))) {
         BlockPos pos = event.getPos();
         String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
         stack.set(DataComponents.CUSTOM_NAME, Component.literal(coords));
         if (event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§a[SettledLands] §fКоординаты записаны в палку: §e" + coords), true);
         }
         event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
         event.setCanceled(true);
      }
   }

   private static void leave(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3) {
      if (var3.exitAction == 1) {
         MutableComponent var4 = Component.translatable("gui.settledlands.dlg.farewell", new Object[]{speaker(var2)});
         var0.displayClientMessage(var4, false);
      }

      stop(var0);
   }

   public static boolean setVisualFocus(ServerPlayer var0, GuideNpc var1, GuideNpc var2) {
      GuideDialogue.Session var3 = SESSIONS.get(var0.getUUID());
      if (var3 == null || var1 == null || var2 == null || var3.entityId != var1.getId() || !var2.isAlive()) {
         return false;
      }

      if (var2.level() != var0.level() || var0.distanceToSqr((Entity)var2) > 16384.0) {
         return false;
      }

      var3.focusEntityId = var2.getId();
      var3.focusSpeaker = speaker(var2);
      var3.focusTitle = var2.profile().title == null ? "" : var2.profile().title;
      var3.focusNameColor = NpcProfile.clamp(var2.profile().nameColor, 0, 3);
      NpcNet.sendTo(var0, new NpcNet.DlgFocus(var3.entityId, var3.focusEntityId, var3.focusSpeaker, var3.focusTitle, var3.focusNameColor));
      return true;
   }

   public static void sendCurrentFocus(ServerPlayer var0) {
      GuideDialogue.Session var1 = SESSIONS.get(var0.getUUID());
      if (var1 != null) {
         NpcNet.sendTo(var0, new NpcNet.DlgFocus(var1.entityId, var1.focusEntityId, var1.focusSpeaker, var1.focusTitle, var1.focusNameColor));
      }
   }

   public static void pick(ServerPlayer var0, int var1, int var2) {
      GuideDialogue.Session var3 = SESSIONS.get(var0.getUUID());
      if (var3 != null && var3.entityId == var1) {
         var3.inShop = var2 == -2;
         GuideNpc var4 = npcOf(var0.server, var3);
         if (var4 == null) {
            stop(var0);
         } else {
            if (var3.escortLine != 0 || var4.isEscorting()) {
               if (var3.escortLine == 0) { startEscort(var0, var4, false); return; }
               if (var2 == 0) stop(var0);
               else sendEscortLine(var0, var3, var4);
               return;
            }
            NpcProfile var5 = var4.profile();
            var3.relation = var4.relationTo(var0);
            if (var2 >= 0) {
               long var6 = System.currentTimeMillis();
               if (var6 - var3.lastPick < 250L) {
                  return;
               }

               var3.lastPick = var6;
            }

            if (var2 == -2) {
               var3.graphNode = -1;
               NpcShop.send(var0, var4);
            } else if (var2 == -3) {
               var3.graphNode = -1;
               startLegacy(var0, var3, var4);
            } else if (var2 == -4) {
               var3.graphNode = -1;
               openQuestList(var0, var3, var4, var5, graphActive(var5) ? -1 : questTopicIndex(var5, var4));
            } else if (var2 == -5) {
               if (graphActive(var5)) { startGraph(var0, var3, var4); return; }
               if (var3.rootCursor == 0) {
                  sendRoot(var0, var3, var4);
               } else {
                  sendWelcome(var0, var3, var4);
               }
            } else if (var2 < 0) {
               resend(var0, var3, var4);
            } else {
               if (var3.graphNode >= 0 && graphActive(var5)) { pickGraph(var0, var3, var4, var2); return; }
               switch (var3.state) {
                  case 0:
                     chooseTopic(var0, var3, var4, var5, var2);
                     break;
                  case 1:
                     chooseAnswer(var0, var3, var4, var5, var2);
                     break;
                  case 2:
                     if (var3.pendingTopic >= 0) {
                        openTopic(var0, var3, var4, var3.pendingTopic);
                     } else if (var3.pendingNext == -1) {
                        finish(var0, var3, var4, var5);
                     } else {
                        var3.step = var3.pendingNext;
                        sendStep(var0, var3, var4);
                     }
                     break;
                  case 3:
                     advanceLegacy(var0, var3, var4);
                     break;
                  case 4:
                     sendRoot(var0, var3, var4);
                     break;
                  case 5:
                     chooseQuest(var0, var3, var4, var5, var2);
                     break;
                  case 6:
                     chooseQuestMenu(var0, var3, var4, var5, var2);
                     break;
                  default:
                     sendWelcome(var0, var3, var4);
               }
            }
         }
      }
   }

   private static void openQuestList(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4) {
      openQuestList(var0, var1, var2, var3, var4, null);
   }

   private static void openQuestList(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4, String var5) {
      var1.questTopic = var4;
      var1.quest = -1;
      NpcProfile.Topic var6 = var4 >= 0 && var3 != null && var3.topics != null && var4 < var3.topics.size() ? var3.topics.get(var4) : null;
      sendQuestList(var0, var1, var2, var6, var5, "#gui.settledlands.quest.none");
   }

   private static int questTopicIndex(NpcProfile var0, GuideNpc var1) {
      if (var0 == null || var0.topics == null) {
         return -1;
      }
      for (int var3 : visibleTopics(var0, var1)) {
         if (var3 >= 0 && var3 < var0.topics.size() && var0.topics.get(var3).type == 3) {
            return var3;
         }
      }

      for (int var4 = 0; var4 < var0.topics.size(); var4++) {
         if (var0.topics.get(var4).type == 3) {
            return var4;
         }
      }

      return -1;
   }

   private static void chooseTopic(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4) {
      List var5 = visibleTopics(var3, var2);
      if (var4 < var5.size()) {
         int var6 = (Integer)var5.get(var4);
         if (var6 == -2) {
            openQuestList(var0, var1, var2, var3, questTopicIndex(var3, var2));
         } else if (var6 < 0) {
            startLegacy(var0, var1, var2);
         } else {
            openTopic(var0, var1, var2, var6);
         }
      }
   }

   private static void openTopic(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, int var3) {
      NpcProfile var4 = var2.profile();
      if (var3 >= 0 && var3 < var4.topics.size()) {
         NpcProfile.Topic var5 = var4.topics.get(var3);
         var1.topic = var3;
         var1.step = 0;
         var1.chainFrom = -1;
         var1.answerStep = -1;
         var1.quest = NpcProfile.questOfTopic(var4, var3);
         switch (var5.type) {
            case 1:
               if (var4.hasShop()) {
                  var1.inShop = true;
                  NpcShop.send(var0, var2);
               } else {
                  sendSilent(var0, var1, var2, var5);
               }
               break;
            case 2:
            default:
               if (NpcProfile.usable(var5)) {
                  sendStep(var0, var1, var2);
               } else {
                  sendSilent(var0, var1, var2, var5);
               }
               break;
            case 3:
               if (!questList(var0, var2, var4, var1.relation).isEmpty()) {
                  openQuestList(var0, var1, var2, var4, var3);
                  return;
               }

               if (NpcProfile.usable(var5)) {
                  sendStep(var0, var1, var2);
               } else {
                  openQuestList(var0, var1, var2, var4, var3);
               }
               break;
            case 4:
               leave(var0, var1, var2, var4);
         }
      } else {
         sendWelcome(var0, var1, var2);
      }
   }

   private static void chooseAnswer(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4) {
      NpcProfile.Topic var5 = topicOf(var3, var1);
      NpcProfile.Step var6 = stepOf(var5, var1);
      if (var6 == null) {
         sendWelcome(var0, var1, var2);
      } else if (var1.chainFrom >= 0 && var1.answerStep < 0) {
         runStepTrigger(var0, var1, var2, var6);
         if (SESSIONS.get(var0.getUUID()) != var1) return;
         advanceStep(var0, var1, var2);
      } else {
         if (var1.answerStep >= 0 && var5 != null && var1.answerStep < var5.steps.size()) {
            var6 = var5.steps.get(var1.answerStep);
         }

         runStepTrigger(var0, var1, var2, var6);
         if (SESSIONS.get(var0.getUUID()) != var1) return;
         List var7 = visibleAnswers(var0, var6, var1.relation);
         if (var7.isEmpty()) {
            advanceStep(var0, var1, var2);
         } else if (var4 >= 0 && var4 < var7.size()) {
            NpcProfile.Answer var8 = (NpcProfile.Answer)var7.get(var4);
            if (var8.relation != 0) {
               GuideNpc.addRelation(var0, var2, var8.relation);
               var1.relation = var2.relationTo(var0);
            }

            if (!var8.action.isEmpty()) {
               if (var1.quest >= 0 && NpcQuests.isQuestAction(var8.action)) {
                  if (!NpcQuests.runAction(var0, var2, var1.quest, var8.action)) {
                     sendStep(var0, var1, var2);
                     return;
                  }

                  ActionSequence.runExtras(var0, var2, var1.relation, var8.action);
                  if (SESSIONS.get(var0.getUUID()) != var1) return;
                  var1.menuOnEnd = "quest:take".equals(ActionSequence.first(var8.action)) ? var1.quest : -1;
                  var1.remindOnEnd = false;
                  if (!NpcProfile.readsAfterAction(var8.next, var1.step)) {
                     if (var1.menuOnEnd >= 0) {
                        openQuestMenu(var0, var1, var2, var3, var1.menuOnEnd, var1.remindOnEnd);
                        return;
                     }

                     stop(var0);
                     return;
                  }

                  var1.closeOnEnd = var1.menuOnEnd < 0;
               } else {
                  NpcActions.run(var0, var2, var1.relation, var8.action);
               }
            }

            if (SESSIONS.containsKey(var0.getUUID())) {
               var1.pendingNext = var8.next;
               var1.pendingTopic = var8.next >= 1000 ? var8.next - 1000 : -1;
               var1.chainFrom = -1;
               var1.answerStep = -1;
               String var9 = var8.reaction.isEmpty() ? "" : fill(var8.reaction, var0, var1, var3);
               if (var9.isEmpty()) {
                  if (var1.pendingTopic >= 0) {
                     openTopic(var0, var1, var2, var1.pendingTopic);
                  } else if (var1.pendingNext == -1) {
                     finish(var0, var1, var2, var3);
                  } else {
                     var1.step = var1.pendingNext;
                     sendStep(var0, var1, var2);
                  }
               } else {
                  var1.pendingReaction = var9;
                  sendLine(var0.getServer(), var0, var1, var2, var9, 2);
               }
            }
         }
      }
   }

   private static void runStepTrigger(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile.Step var3) {
      if (var3 != null && var3.action != null && !var3.action.isEmpty()) {
         NpcActions.run(var0, var2, var1.relation, var3.action);
      }
   }

   private static void advanceStep(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      if (SESSIONS.get(var0.getUUID()) != var1) return;
      NpcProfile var3 = var2.profile();
      NpcProfile.Topic var4 = topicOf(var3, var1);
      int var5 = var4 == null ? -1 : NpcProfile.chainNext(var4, var1.step);
      if (var5 < 0) {
         finish(var0, var1, var2, var3);
      } else {
         var1.step = var5;
         sendStep(var0, var1, var2);
      }
   }

   private static void sendLine(MinecraftServer var0, ServerPlayer var1, GuideDialogue.Session var2, GuideNpc var3, String var4, int var5) {
      NpcProfile var6 = var3.profile();
      var2.state = var5;
      List var7 = var5 == 2 ? List.of("#gui.settledlands.dlg.next") : List.of();
      List var8 = var5 == 2 ? List.of("#gui.settledlands.dlg.hint.next") : List.of();
      NpcNet.sendTo(
         var1,
         new NpcNet.DlgView(
            var3.getId(),
            speaker(var3),
            var6.title,
            fill(var4, var1, var2, var6),
            var7,
            var8,
            var2.relation,
            var5,
            var6.hasShop(),
            style(var6, var1, var2.relation)
         )
      );
   }

   private static void sendSilent(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile.Topic var3) {
      NpcProfile var4 = var2.profile();
      var1.state = 2;
      var1.pendingNext = -1;
      var1.pendingTopic = -1;
      NpcNet.sendTo(
         var0,
         new NpcNet.DlgView(
            var2.getId(),
            speaker(var2),
            var4.title,
            "#gui.settledlands.dlg.silent",
            List.of("#gui.settledlands.dlg.next"),
            List.of("#gui.settledlands.dlg.hint.next"),
            var1.relation,
            2,
            var4.hasShop(),
            style(var4, var0, var1.relation)
         )
      );
   }

   private static int rankOf(int var0, int var1) {
      return switch (var1) {
         case 1 -> var0 == 1 ? 0 : 1;
         case 2 -> var0 == 3 ? 0 : 1;
         case 3 -> 1;
         default -> var0 != 1 && var0 != 4 ? 1 : 0;
      };
   }

   private static void sendRoot(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      NpcProfile var3 = var2.profile();
      if (var1.rootCursor == 0 && visibleTopics(var3, var2).isEmpty()) {
         var1.state = 4;
         var1.rootCursor = 1;
         var1.rootLine = var3.welcome;
         sendLine(var0.getServer(), var0, var1, var2, var3.welcome, 4);
      } else {
         List var4 = var3.rootSequence();
         int var5 = Math.max(0, var1.rootCursor);
         if (var5 < var4.size() - 1) {
            var1.rootCursor = var5 + 1;
            var1.state = 4;
            var1.rootLine = (String)var4.get(var5);
            sendLine(var0.getServer(), var0, var1, var2, (String)var4.get(var5), 4);
         } else {
            var1.rootCursor = Math.max(var5, var4.size() - 1);
            String var6 = var4.isEmpty() ? null : (String)var4.get(var4.size() - 1);
            if (!var1.defaultTabDone) {
               var1.defaultTabDone = true;
               if (sendDefaultTab(var0, var1, var2, var3, var6)) {
                  return;
               }
            }

            sendWelcome(var0, var1, var2, var6);
         }
      }
   }

   private static List<Integer> questList(ServerPlayer var0, GuideNpc var1, NpcProfile var2, int var3) {
      if (var2 == null || var2.quests.isEmpty()) {
         return new ArrayList<>();
      }
      NpcRelations var4 = null;
      try {
         var4 = NpcRelations.get(var0.server);
      } catch (Throwable ignored) {
      }
      final NpcRelations fRels = var4;
      return NpcQuests.visible(
         var2,
         var3,
         var3x -> fRels != null && NpcQuests.isTaken(fRels, var1, var0, var3x),
         var4x -> NpcQuests.doneFor(var2, var4x, fRels != null && NpcQuests.isDone(fRels, var1, var0, var4x), fRels != null && NpcQuests.isChainDone(fRels, var1, var4x)),
         var4x -> NpcQuests.chainReady(var2, var4x, var3xx -> fRels != null && NpcQuests.isDone(fRels, var1, var0, var3xx), var2xx -> fRels != null && NpcQuests.isChainDone(fRels, var1, var2xx))
      );
   }

   public static boolean evalIfNode(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, NpcDialogueGraph.Node ifNode) {
      if (ifNode == null || ifNode.type != NpcDialogueGraph.IF) return true;
      String varName = ifNode.pointsName;
      boolean shared = ifNode.pointsShared;
      if (ifNode.varInputNode >= 0 && graph != null) {
         NpcDialogueGraph.Node inNode = graph.get(ifNode.varInputNode);
         if (inNode != null && inNode.type == NpcDialogueGraph.VARIABLE && !inNode.pointsName.isBlank()) {
            varName = inNode.pointsName;
            shared = inNode.pointsShared;
         }
      }
      int currentVal = 0;
      if (!varName.isBlank() && player != null && player.getServer() != null) {
         try {
            currentVal = NpcPoints.get(player.getServer()).get(player.getUUID(), varName, shared);
         } catch (Throwable ignored) {
         }
      } else {
         currentVal = relation;
      }
      return evalCondition(currentVal, ifNode.varOp, ifNode.varCompare);
   }

   private static boolean isIfBranchSatisfied(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, NpcDialogueGraph.Node ifNode, int targetId, Set<Integer> visited) {
      if (ifNode == null || ifNode.type != NpcDialogueGraph.IF) return false;
      if (!visited.add(ifNode.id) || visited.size() > 64) return false;

      boolean conditionPass = evalIfNode(player, npc, relation, graph, ifNode);
      int activeTarget = conditionPass ? ifNode.next : ifNode.falseNext;

      if (activeTarget == targetId) {
         List<NpcDialogueGraph.Node> upstreamIfs = new ArrayList<>();
         for (NpcDialogueGraph.Node src : graph.nodes.values()) {
            if (src.type == NpcDialogueGraph.IF && (src.next == ifNode.id || src.falseNext == ifNode.id)) {
               upstreamIfs.add(src);
            }
         }
         if (upstreamIfs.isEmpty()) {
            return true;
         }
         for (NpcDialogueGraph.Node up : upstreamIfs) {
            if (isIfBranchSatisfied(player, npc, relation, graph, up, ifNode.id, visited)) {
               return true;
            }
         }
         return false;
      }

      return false;
   }

   public static boolean isIncomingIfSatisfied(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, NpcDialogueGraph.Node targetNode) {
      if (targetNode == null || graph == null) return true;

      List<NpcDialogueGraph.Node> incomingIfs = new ArrayList<>();
      for (NpcDialogueGraph.Node src : graph.nodes.values()) {
         if (src.type == NpcDialogueGraph.IF && (src.next == targetNode.id || src.falseNext == targetNode.id)) {
            incomingIfs.add(src);
         }
      }

      if (incomingIfs.isEmpty()) {
         return true;
      }

      for (NpcDialogueGraph.Node ifNode : incomingIfs) {
         if (isIfBranchSatisfied(player, npc, relation, graph, ifNode, targetNode.id, new HashSet<>())) {
            return true;
         }
      }

      return false;
   }

   public static boolean isQuestNodeVisible(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, NpcDialogueGraph.Node questNode) {
      if (questNode == null || questNode.type != NpcDialogueGraph.QUEST) return false;
      if (questNode.minRelation > 0 && relation < questNode.minRelation) return false;
      if (questNode.maxRelation < 100 && relation > questNode.maxRelation) return false;
      return isIncomingIfSatisfied(player, npc, relation, graph, questNode);
   }

   public static boolean executeMoveNode(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node moveNode) {
      if (npc == null || moveNode == null || moveNode.type != NpcDialogueGraph.MOVE) return false;
      String coords = moveNode.moveCoords;
      if ((coords == null || coords.isBlank()) && moveNode.moveEscort && npc.profile() != null) {
         coords = npc.profile().escortCoordinates;
      }
      if (coords == null || coords.isBlank()) return false;

      Vec3 origin = new Vec3(npc.getX(), npc.getY(), npc.getZ());
      Vec3 target = parseCoordinates(coords, origin);
      if (target == null) return false;

      if (moveNode.moveEscort) {
         if (player == null || player.level() != npc.level()) return false;
         BlockPos rawDest = BlockPos.containing(target.x, target.y, target.z);
         BlockPos destination = (npc.level() instanceof ServerLevel sl) ? npc.findStablePosition(sl, rawDest) : rawDest;
         if (!npc.level().getWorldBorder().isWithinBounds(destination)) return false;
         String code = (npc.profile() != null && !npc.profile().escortCode.isBlank()) ? npc.profile().escortCode : "escort";
         npc.beginEscort(player, code, destination);
         return true;
      } else {
         double speed = moveNode.moveSpeed <= 0.001 ? 1.0 : moveNode.moveSpeed;
         return npc.walkTo(target.x, target.y, target.z, speed);
      }
   }

   public static boolean executeMobilityNode(GuideNpc npc, NpcDialogueGraph.Node mobilityNode) {
      if (npc == null || mobilityNode == null || mobilityNode.type != NpcDialogueGraph.MOBILITY) return false;
      String groupId = mobilityNode.mobilityGroupId;
      if (groupId != null && !groupId.isBlank()) {
         npc.profile().mobilityGroupId = groupId;
         if (!mobilityNode.mobilityGroupName.isBlank()) {
            npc.profile().mobilityGroupName = mobilityNode.mobilityGroupName;
         }
         if (npc.level() instanceof ServerLevel level) {
            NpcMobilityGroups catalog = NpcMobilityGroups.get(level.getServer());
            if (catalog != null) {
               catalog.assignProfile(npc.profile(), groupId);
            }
         }
      }
      npc.profile().idleWalking = true;
      npc.beginMobilityWatch(mobilityNode.id);
      return true;
   }

   public static void triggerMobilityChain(GuideNpc npc, int nodeId) {
      if (npc == null || npc.profile() == null || npc.profile().dialogueGraph == null) return;
      NpcDialogueGraph.Node node = npc.profile().dialogueGraph.get(nodeId);
      if (node != null) {
         triggerMobilityChain(npc, null, node);
      }
   }

   public static void triggerMobilityChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node mobilityNode) {
      if (npc == null || mobilityNode == null || mobilityNode.type != NpcDialogueGraph.MOBILITY) return;
      NpcDialogueGraph graph = npc.profile().dialogueGraph;
      if (graph == null) return;

      int relation = player != null ? npc.relationTo(player) : 0;
      if (!isIncomingIfSatisfied(player, npc, relation, graph, mobilityNode)) return;
      Set<Integer> executedTracker = new HashSet<>();

      if (!mobilityNode.action.isBlank() && (player != null || npc != null)) {
         NpcActions.run(player, npc, relation, mobilityNode.action);
      }
      executeGraphTriggers(player, npc, relation, graph, mobilityNode.triggers, executedTracker);

      if (mobilityNode.next >= 0) {
         int targetLine = resolveExecutionTarget(player, npc, null, graph, mobilityNode.next, executedTracker);
         if (targetLine >= 0 && player != null) {
            startAtLine(player, npc, targetLine, executedTracker);
         }
      }
   }

   public static Vec3 parseCoordinates(String coords, Vec3 origin) {
      if (coords == null || coords.isBlank()) return null;
      String s = coords.trim();
      s = s.replaceAll("(?i)^(?:/tp|/teleport)\\s+", "");
      s = s.replaceAll("(?i)[XYZxyz]\\s*[:=]\\s*", "");
      s = s.replaceAll("~\\s+(?=[0-9+\\-])", "~");
      String[] parts = s.split("[,\\s]+");
      if (parts.length != 2 && parts.length != 3) return null;
      try {
         double x = parseCoordComponent(parts[0], origin != null ? origin.x : 0.0);
         double y = parts.length == 3
            ? parseCoordComponent(parts[1], origin != null ? origin.y : 0.0)
            : (origin != null ? origin.y : 64.0);
         double z = parseCoordComponent(parts[parts.length - 1], origin != null ? origin.z : 0.0);
         return new Vec3(x, y, z);
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static double parseCoordComponent(String part, double origin) throws NumberFormatException {
      part = part.trim();
      if (part.startsWith("~")) {
         String rest = part.substring(1).trim();
         if (rest.isEmpty()) return origin;
         return origin + Double.parseDouble(rest);
      }
      return Double.parseDouble(part);
   }

   public static List<NpcDialogueGraph.Node> graphQuestNodes(ServerPlayer player, GuideNpc npc, NpcProfile profile, int relation) {
      List<NpcDialogueGraph.Node> list = new ArrayList<>();
      if (profile != null && profile.dialogueGraph != null && profile.dialogueGraph.enabled) {
         for (NpcDialogueGraph.Node n : profile.dialogueGraph.nodes.values()) {
            if (n.type == NpcDialogueGraph.QUEST && isQuestNodeVisible(player, npc, relation, profile.dialogueGraph, n)) {
               list.add(n);
            }
         }
         list.sort(Comparator.comparingInt(n -> n.id));
      }
      return list;
   }

   public static List<NpcDialogueGraph.Node> graphQuestNodes(NpcProfile profile, int relation) {
      return graphQuestNodes(null, null, profile, relation);
   }

   private static void sendQuestList(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile.Topic var3) {
      sendQuestList(var0, var1, var2, var3, null, null);
   }

   private static void sendQuestList(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile.Topic var3, String var4, String var5) {
      NpcProfile var6 = var2.profile();
      NpcRelations var7 = null;
      try {
         var7 = NpcRelations.get(var0.server);
      } catch (Throwable ignored) {
      }
      List<NpcDialogueGraph.Node> gQuests = graphQuestNodes(var0, var2, var6, var1.relation);
      List<Integer> var8 = questList(var0, var2, var6, var1.relation);
      ArrayList<String> var9 = new ArrayList<>();

      for (NpcDialogueGraph.Node q : gQuests) {
         String qTitle = q.text == null || q.text.isBlank() ? ("#gui.settledlands.graph.quest #" + q.id) : q.text;
         var9.add(qTitle);
      }

      for (int var11 : var8) {
         NpcProfile.Quest var12 = var6.quests.get(var11);
         String var13 = var12.title == null ? "" : var12.title;
         String var14 = (var7 != null && NpcQuests.isTaken(var7, var2, var0, var11)) ? "#gui.settledlands.quest.mark.taken" : "";
         var9.add(var13 + var14);
      }

      String var15 = "#gui.settledlands.quest.list";
      if (var9.isEmpty() && var5 != null) {
         var15 = var5;
      } else if (var4 != null && !var4.isEmpty()) {
         var15 = var4;
      } else if (var3 != null) {
         for (NpcProfile.Step var17 : var3.steps) {
            String var18 = NpcProfile.lineFor(var17, var1.relation);
            if (!var18.isEmpty()) {
               var15 = var18;
               break;
            }
         }
      }

      var1.state = 5;
      var1.quest = -1;
      NpcNet.sendTo(
         var0,
         new NpcNet.DlgView(
            var2.getId(),
            speaker(var2),
            var6.title,
            fill(var15, var0, var1, var6),
            var9,
            List.of(),
            var1.relation,
            5,
            var6.hasShop(),
            style(var6, var0, var1.relation)
         )
      );
   }

   private static void chooseQuest(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4) {
      List<NpcDialogueGraph.Node> gQuests = graphQuestNodes(var0, var2, var3, var1.relation);
      if (var4 >= 0 && var4 < gQuests.size()) {
         NpcDialogueGraph.Node qNode = gQuests.get(var4);
         NpcDialogueGraph graph = var3.dialogueGraph;
         Set<Integer> executedTracker = new HashSet<>();
         if (!qNode.action.isBlank()) {
            NpcActions.run(var0, var2, var1.relation, qNode.action);
         }
         executeGraphTriggers(var0, var2, var1.relation, graph, qNode.triggers, executedTracker);
         if (qNode.next >= 0) {
            int targetLine = resolveExecutionTarget(var0, var2, var1, graph, qNode.next, executedTracker);
            if (targetLine >= 0) {
               startAtLine(var0, var2, targetLine, executedTracker);
            }
         }
         return;
      }

      int legacyIdx = var4 - gQuests.size();
      List var5 = questList(var0, var2, var3, var1.relation);
      if (legacyIdx >= 0 && legacyIdx < var5.size()) {
         int var6 = (Integer)var5.get(legacyIdx);
         NpcRelations var7 = NpcRelations.get(var0.server);
         if (!NpcQuests.isTaken(var7, var2, var0, var6)) {
            playQuest(var0, var1, var2, var6, 1);
         } else {
            openQuestMenu(var0, var1, var2, var3, var6, true);
         }
      }
   }

   public static int menuStart(boolean var0) {
      return var0 ? 0 : -1;
   }

   private static void openQuestMenu(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4, boolean var5) {
      var1.menuStep = menuStart(var5);
      sendQuestMenu(var0, var1, var2, var3, var4);
   }

   private static void sendQuestMenu(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4) {
      if (var4 >= 0 && var4 < var3.quests.size()) {
         NpcProfile.Quest var5 = var3.quests.get(var4);
         NpcProfile.Topic var6 = questTopic(var3, var4, 2);
         String var7 = null;
         if (var6 != null && var1.menuStep >= 0 && var1.menuStep < var6.steps.size()) {
            String var8 = NpcProfile.lineFor(var6.steps.get(var1.menuStep), var1.relation);
            if (!var8.isEmpty()) {
               var7 = var8;
            }
         }

         boolean var13 = var7 != null && NpcProfile.nextText(var6, var1.menuStep, var1.relation) >= 0;
         if (var7 == null) {
            String var9 = var5.title != null && !var5.title.isEmpty() ? var5.title : "?";
            var7 = QUEST_ACTIONS + ARG + var9;
         }

         ArrayList var14 = new ArrayList();
         ArrayList var10 = new ArrayList();
         if (!var13) {
            for (int var12 : questMenuActions(var3, var0, var2, var4, var1.relation)) {
               var14.add(var12 == 0 && var5.points() ? "#gui.settledlands.quest.handinpoints" : MENU_KEYS[var12]);
               var10.add("#gui.settledlands.dlg.hint.say");
            }
         }

         var1.state = 6;
         var1.quest = var4;
         var1.questTopic = var3.questTopicIndex(var4, 1);
         var1.chainFrom = -1;
         var1.answerStep = -1;
         var1.closeOnEnd = false;
         var1.menuOnEnd = -1;
         var1.menuButtons = !var13;
         NpcNet.sendTo(
            var0,
            new NpcNet.DlgView(
               var2.getId(),
               speaker(var2),
               var3.title,
               fill(var7, var0, var1, var3),
               var14,
               var10,
               var1.relation,
               6,
               var3.hasShop(),
               style(var3, var0, var1.relation)
            )
         );
      } else {
         sendWelcome(var0, var1, var2);
      }
   }

   private static NpcProfile.Topic questTopic(NpcProfile var0, int var1, int var2) {
      int var3 = var0.questTopicIndex(var1, var2);
      return var3 >= 0 && var3 < var0.topics.size() ? var0.topics.get(var3) : null;
   }

   private static List<Integer> questMenuActions(NpcProfile var0, ServerPlayer var1, GuideNpc var2, int var3, int var4) {
      NpcProfile.Quest var5 = var0.quests.get(var3);
      boolean var6 = NpcQuests.ready(var1, var5) && usableQuestTopic(var0, var3, 3);
      boolean var7 = usableQuestTopic(var0, var3, 4);
      return questMenuButtons(var6, var7);
   }

   public static List<Integer> questMenuButtons(boolean var0, boolean var1) {
      ArrayList var2 = new ArrayList();
      if (var0) {
         var2.add(0);
      }

      if (var1) {
         var2.add(1);
      }

      var2.add(2);
      return var2;
   }

   private static boolean usableQuestTopic(NpcProfile var0, int var1, int var2) {
      int var3 = var0.questTopicIndex(var1, var2);
      return var3 >= 0 && var3 < var0.topics.size() && NpcProfile.usable(var0.topics.get(var3));
   }

   private static void chooseQuestMenu(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4) {
      if (var1.quest < 0 || var1.quest >= var3.quests.size()) {
         sendWelcome(var0, var1, var2);
      } else if (!var1.menuButtons) {
         int var5 = NpcProfile.nextText(questTopic(var3, var1.quest, 2), var1.menuStep, var1.relation);
         if (var5 >= 0) {
            var1.menuStep = var5;
         }

         sendQuestMenu(var0, var1, var2, var3, var1.quest);
      } else {
         List<Integer> var6 = questMenuActions(var3, var0, var2, var1.quest, var1.relation);
         if (var4 >= 0 && var4 < var6.size()) {
            switch (var6.get(var4)) {
               case 0:
                  playQuest(var0, var1, var2, var1.quest, 3);
                  break;
               case 1:
                  playQuest(var0, var1, var2, var1.quest, 4);
                  break;
               case 2:
                  NpcQuests.drop(var0, var2, var1.quest);
                  stop(var0);
            }
         }
      }
   }

   private static void playQuest(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, int var3, int var4) {
      NpcProfile var5 = var2.profile();
      var1.quest = var3;
      int var6 = var5.questTopicIndex(var3, var4);
      NpcProfile.Topic var7 = var6 < 0 ? null : var5.topics.get(var6);
      if (!NpcProfile.usable(var7)) {
         questShortcut(var0, var1, var2, var5, var3, var4);
      } else {
         var1.topic = var6;
         var1.step = 0;
         var1.chainFrom = -1;
         var1.answerStep = -1;
         var1.closeOnEnd = false;
         var1.menuOnEnd = -1;
         var1.remindOnEnd = true;
         sendStep(var0, var1, var2);
      }
   }

   private static void questShortcut(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3, int var4, int var5) {
      if (var5 == 1) {
         NpcQuests.take(var0, var2, var4);
         openQuestMenu(var0, var1, var2, var3, var4, false);
      } else if (var5 == 3) {
         NpcQuests.runAction(var0, var2, var4, "quest:hand");
         sendWelcome(var0, var1, var2);
      } else {
         int var6 = var3.questTopicIndex(var4, var5);
         sendSilent(var0, var1, var2, var6 < 0 ? null : var3.topics.get(var6));
      }
   }

   private static void sendWelcome(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      var1.quest = -1;
      sendWelcome(var0, var1, var2, null);
   }

   private static void finish(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, NpcProfile var3) {
      NpcProfile.Topic var4 = topicOf(var3, var1);
      if (var4 != null && var4.questRole == 4) {
         if (var1.quest >= 0 && var1.quest < var3.quests.size() && NpcQuests.isTaken(NpcRelations.get(var0.server), var2, var0, var1.quest)) {
            sendQuestMenu(var0, var1, var2, var3, var1.quest);
            return;
         }

         if (var1.questTopic >= 0 && var1.questTopic < var3.topics.size()) {
            var1.quest = -1;
            sendQuestList(var0, var1, var2, var3.topics.get(var1.questTopic));
            return;
         }
      }

      if (var1.menuOnEnd >= 0 && var1.menuOnEnd < var3.quests.size()) {
         int var5 = var1.menuOnEnd;
         boolean var6 = var1.remindOnEnd;
         var1.menuOnEnd = -1;
         var1.remindOnEnd = true;
         openQuestMenu(var0, var1, var2, var3, var5, var6);
      } else if (var1.closeOnEnd) {
         stop(var0);
      } else {
         sendWelcome(var0, var1, var2);
      }
   }

   private static void sendWelcome(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2, String var3) {
      NpcProfile var4 = var2.profile();
      var1.state = 0;
      var1.topic = -1;
      var1.step = 0;
      var1.closeOnEnd = false;
      var1.menuOnEnd = -1;
      var1.remindOnEnd = true;
      var1.relation = var2.relationTo(var0);
      ArrayList var5 = new ArrayList();
      ArrayList var6 = new ArrayList();

      for (int var8 : visibleTopics(var4, var2)) {
         if (var8 == -2) {
            var5.add("#gui.settledlands.tab.quest");
            var6.add("#gui.settledlands.type.quest");
         } else {
            NpcProfile.Topic var9 = var8 < 0 ? null : var4.topics.get(var8);
            if (var9 == null) {
               var5.add("#gui.settledlands.dlg.legacy");
               var6.add("#gui.settledlands.dlg.legacy.kind");
            } else {
               var5.add(var9.title);
               var6.add(kindOf(var9));
            }
         }
      }

      NpcNet.sendTo(
         var0,
         new NpcNet.DlgView(
            var2.getId(),
            speaker(var2),
            var4.title,
            fill(var3 == null ? var4.welcome : var3, var0, var1, var4),
            var5,
            var6,
            var1.relation,
            0,
            var4.hasShop(),
            style(var4, var0, var1.relation)
         )
      );
   }

   private static String fill(String var0, ServerPlayer var1, GuideDialogue.Session var2, NpcProfile var3) {
      NpcProfile.Quest var4 = var2.quest >= 0 && var2.quest < var3.quests.size() ? var3.quests.get(var2.quest) : null;
      return NpcProfile.fillQuest(var0, var1, var2.relation, var4);
   }

   private static void sendStep(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      NpcProfile var3 = var2.profile();
      NpcProfile.Topic var4 = topicOf(var3, var1);
      NpcProfile.Step var5 = stepOf(var4, var1);
      if (var5 == null) {
         sendWelcome(var0, var1, var2);
      } else {
         var1.state = 1;
         boolean var6 = var4 != null && NpcProfile.isQuestTopic(var4);
         int var7 = var1.step;
         if (var6 && var1.chainFrom >= 0 && var1.chainFrom != var1.step) {
            boolean var8 = var1.step > var1.chainFrom;
            boolean var9 = hasVisibleAnswers(var0, var4, var1.step, var1.relation);
            if (!var8) {
               var1.chainFrom = -1;
            } else if (var9) {
               var1.chainFrom = -1;
            } else if (NpcProfile.chainNext(var4, var1.step) >= 0) {
               var7 = -1;
            } else {
               var7 = var1.chainFrom;
            }
         }

         if (var6 && var1.chainFrom < 0 && var7 == var1.step && chainReads(var0, var4, var1.step, var1.relation)) {
            var1.chainFrom = var1.step;
            var7 = -1;
         }

         var1.answerStep = var7;
         ArrayList var12 = new ArrayList();
         ArrayList var13 = new ArrayList();
         if (var7 >= 0 && var7 < var4.steps.size()) {
            for (NpcProfile.Answer var11 : visibleAnswers(var0, var4.steps.get(var7), var1.relation)) {
               var12.add(fill(var11.player, var0, var1, var3));
               var13.add("#gui.settledlands.dlg.hint.say");
            }
         }

         NpcNet.sendTo(
            var0,
            new NpcNet.DlgView(
               var2.getId(),
               speaker(var2),
               var3.title,
               fill(NpcProfile.lineFor(var5, var1.relation), var0, var1, var3),
               var12,
               var13,
               var1.relation,
               1,
               var3.hasShop(),
               style(var3, var0, var1.relation)
            )
         );
      }
   }

   public static void resend(ServerPlayer var0, NpcProfile var1) {
      GuideDialogue.Session var2 = SESSIONS.get(var0.getUUID());
      if (var2 != null) {
         GuideNpc var3 = npcOf(var0.server, var2);
         if (var3 != null) {
            resend(var0, var2, var3);
         }
      }
   }

   private static void resend(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      if (var1.escortLine != 0) { sendEscortLine(var0, var1, var2); return; }
      if (var2.isEscorting()) { startEscort(var0, var2, false); return; }
      if (var1.inShop) {
         NpcShop.send(var0, var2);
         return;
      }
      if (var1.graphNode >= 0 && graphActive(var2.profile())) { sendGraph(var0, var1, var2); return; }
      switch (var1.state) {
         case 1:
            sendStep(var0, var1, var2);
            break;
         case 2:
            sendLine(var0.getServer(), var0, var1, var2, var1.pendingReaction, 2);
            break;
         case 3:
            if (var1.legacy.isEmpty()) {
               sendWelcome(var0, var1, var2);
            } else {
               sendLegacyLine(var0, var1, var2);
            }
            break;
         case 4:
            sendLine(var0.getServer(), var0, var1, var2, var1.rootLine.isEmpty() ? var2.profile().welcome : var1.rootLine, 4);
            break;
         case 5:
            NpcProfile var3 = var2.profile();
            NpcProfile.Topic var4 = var1.questTopic >= 0 && var1.questTopic < var3.topics.size() ? var3.topics.get(var1.questTopic) : null;
            sendQuestList(var0, var1, var2, var4, null, "#gui.settledlands.quest.none");
            break;
         case 6:
            sendQuestMenu(var0, var1, var2, var2.profile(), var1.quest);
            break;
         default:
            sendWelcome(var0, var1, var2);
      }
   }

   private static NpcNet.DlgStyle style(NpcProfile var0, ServerPlayer var1, int var2) {
      String var3 = var0.reputationLabel(var2);
      return new NpcNet.DlgStyle(
         var0.style,
         var0.textColor,
         var0.nameColor,
         var0.typingSpeed,
         var0.showSpeaker,
         var0.keyHints,
         var0.answerNumbers,
         var3,
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

   private static void startLegacy(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      var1.legacy = group(var2.sortedPhrases());
      var1.legacyCursor = 0;
      if (var1.legacy.isEmpty()) {
         sendWelcome(var0, var1, var2);
      } else {
         sendLegacyLine(var0, var1, var2);
      }
   }

   private static void advanceLegacy(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      if (var1.legacyCursor + 1 >= var1.legacy.size()) {
         sendWelcome(var0, var1, var2);
      } else {
         var1.legacyCursor++;
         sendLegacyLine(var0, var1, var2);
      }
   }

   private static void sendLegacyLine(ServerPlayer var0, GuideDialogue.Session var1, GuideNpc var2) {
      List var3 = var1.legacy.get(var1.legacyCursor);
      GuidePhrase var4 = (GuidePhrase)var3.get(var0.getRandom().nextInt(var3.size()));
      boolean var5 = var1.legacyCursor >= var1.legacy.size() - 1;
      var1.state = 3;
      NpcNet.sendTo(
         var0,
         new NpcNet.DlgView(
            var2.getId(),
            speaker(var2),
            var2.profile().title,
            var4.text(),
            List.of(var5 ? "#gui.settledlands.dlg.finish" : "#gui.settledlands.dlg.more"),
            List.of("#gui.settledlands.dlg.hint.next"),
            var1.relation,
            3,
            var2.profile().hasShop(),
            style(var2.profile(), var0, var1.relation)
         )
      );
   }

   private static List<List<GuidePhrase>> group(List<GuidePhrase> var0) {
      ArrayList var1 = new ArrayList();

      for (GuidePhrase var3 : var0) {
         if (var1.isEmpty() || ((GuidePhrase)((List)var1.get(var1.size() - 1)).get(0)).number() != var3.number()) {
            var1.add(new ArrayList());
         }

         ((List)var1.get(var1.size() - 1)).add(var3);
      }

      return var1;
   }

   private static List<Integer> visibleTopics(NpcProfile var0, GuideNpc var1) {
      if (var0 == null || var0.topics == null) {
         return new ArrayList<>();
      }
      List<Integer> var2 = collectTopics(var0, true);
      if (var2.isEmpty()) {
         var2 = collectTopics(var0, false);
      }

      int var3 = var0.defaultTab;
      var2.sort(Comparator.comparingInt((Integer var2x) -> {
         NpcProfile.Topic var3x = var0.topics.get(var2x);
         return rankOf(var3x.type, var3);
      }));
      if (var2.isEmpty() && var0.showQuestTab && !var0.quests.isEmpty()) {
         var2.add(-2);
      }

      if (var2.isEmpty() && !var1.sortedPhrases().isEmpty()) {
         var2.add(-1);
      }

      return var2;
   }

   private static List<Integer> collectTopics(NpcProfile var0, boolean var1) {
      ArrayList<Integer> var2 = new ArrayList<>();
      if (var0 == null || var0.topics == null) {
         return var2;
      }

      for (int var3 = 0; var3 < var0.topics.size(); var3++) {
         NpcProfile.Topic var4 = var0.topics.get(var3);
         if (!NpcProfile.isQuestTopic(var4)
            && (!var1 || var0.showsKind(var4.type))
            && (var4.type != 1 || var0.hasShop())
            && (!var1 || var4.type == 1 || var4.type == 4 || NpcProfile.usable(var4))) {
            var2.add(var3);
         }
      }

      return var2;
   }

   private static String kindOf(NpcProfile.Topic var0) {
      if (var0.kind != null && !var0.kind.isEmpty()) {
         return var0.kind;
      } else {
         int var1 = NpcProfile.clamp(var0.type, 0, NpcProfile.TYPE_COUNT - 1);
         return "#" + NpcProfile.TYPE_KEYS[var1];
      }
   }

   private static List<NpcProfile.Answer> visibleAnswers(ServerPlayer player, NpcProfile.Step step, int relation) {
      ArrayList<NpcProfile.Answer> visible = new ArrayList<>();
      if (step == null || player == null) {
         return visible;
      }

      NpcPoints points = player.getServer() == null ? null : NpcPoints.get(player.getServer());
      for (NpcProfile.Answer answer : step.answers) {
         if (answer.pointsName != null && !answer.pointsName.isBlank()) {
            if (points != null) {
               int balance = points.get(player.getUUID(), answer.pointsName, answer.pointsShared);
               if (answer.minPoints <= balance && balance <= answer.maxPoints) {
                  visible.add(answer);
               }
            }
         } else if (NpcProfile.visible(answer, relation)) {
            visible.add(answer);
         }
      }
      return visible;
   }

   private static boolean hasVisibleAnswers(ServerPlayer player, NpcProfile.Topic topic, int step, int relation) {
      return topic != null && step >= 0 && step < topic.steps.size()
         && !visibleAnswers(player, topic.steps.get(step), relation).isEmpty();
   }

   private static boolean chainReads(ServerPlayer player, NpcProfile.Topic topic, int step, int relation) {
      if (!hasVisibleAnswers(player, topic, step, relation)) {
         return false;
      }
      int current = step;
      for (int i = 0; i < 64; i++) {
         int next = NpcProfile.chainNext(topic, current);
         if (next < 0) {
            return false;
         }
         if (hasVisibleAnswers(player, topic, next, relation)) {
            return false;
         }
         if (NpcProfile.chainNext(topic, next) < 0) {
            return true;
         }
         current = next;
      }
      return false;
   }

   private static NpcProfile.Topic topicOf(NpcProfile var0, GuideDialogue.Session var1) {
      return var1.topic >= 0 && var1.topic < var0.topics.size() ? var0.topics.get(var1.topic) : null;
   }

   private static NpcProfile.Step stepOf(NpcProfile.Topic var0, GuideDialogue.Session var1) {
      return var0 != null && var1.step >= 0 && var1.step < var0.steps.size() ? var0.steps.get(var1.step) : null;
   }

   private static String speaker(GuideNpc var0) {
      if (var0 == null) {
         return "";
      } else {
         String var1 = var0.profile().name;
         return var1 != null && !var1.isEmpty() ? var1 : var0.getDisplayName().getString();
      }
   }

   private static GuideNpc npcOf(MinecraftServer var0, GuideDialogue.Session var1) {
      ServerLevel var2 = var0.getLevel(var1.dimension);
      if (var2 == null) {
         return null;
      } else {
         return var2.getEntity(var1.entityId) instanceof GuideNpc var3 && var3.isAlive() ? var3 : null;
      }
   }

   private static boolean stillTalking(MinecraftServer var0, GuideNpc var1, UUID var2) {
      for (Entry var4 : SESSIONS.entrySet()) {
         if (!((UUID)var4.getKey()).equals(var2) && ((GuideDialogue.Session)var4.getValue()).entityId == var1.getId()) {
            ServerLevel var5 = var0.getLevel(((GuideDialogue.Session)var4.getValue()).dimension);
            if (var5 != null && var5.getEntity(((GuideDialogue.Session)var4.getValue()).entityId) == var1) {
               return true;
            }
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void onServerTick(Post var0) {
      if (!SESSIONS.isEmpty()) {
         MinecraftServer var1 = var0.getServer();
         pruneListeners(var1);
         Iterator var2 = SESSIONS.entrySet().iterator();

         while (var2.hasNext()) {
            Entry var3 = (Entry)var2.next();
            UUID var12 = (UUID)var3.getKey();
            GuideDialogue.Session var4 = (GuideDialogue.Session)var3.getValue();
            ServerPlayer var5 = var1.getPlayerList().getPlayer(var12);
            GuideNpc var6 = npcOf(var1, var4);
            NpcProfile var7 = var6 == null ? null : var6.profile();
            double var8 = var4.escortLine != 0 ? 8.0 : (var4.graphNode >= 0 ? Math.max(var7 == null ? 4.0 : var7.radius, 16.0) : (var7 == null ? 4.0 : var7.radius));
            boolean var10 = var6 != null && var5 != null && var5.level() == var6.level() && var5.distanceToSqr(var6) <= var8 * var8;
            if (var5 != null && var5.isAlive() && !var5.isRemoved() && var6 != null && var10 && !var5.isSpectator()) {
               if (var7.blockMovement) {
                  Vec3 var11 = var5.getDeltaMovement();
                  var5.setDeltaMovement(0.0, var11.y, 0.0);
                  var5.xxa = 0.0F;
                  var5.yya = 0.0F;
                  var5.zza = 0.0F;
               }

               if (!var6.isTalking()) {
                  var6.setTalking(true);
               }

               if (var7.lookAtPlayer) {
                  var6.getLookControl().setLookAt(var5, 30.0F, 30.0F);
               }
            } else {
               var2.remove();
               if (var6 != null && var5 != null && var6.isEscorting()) var6.escort().dialogueClosed(var5, var4.escortLine == 2);
               if (var6 != null && !stillTalking(var1, var6, var12)) {
                  var6.setTalking(false);
               }

               closeListeners(var1, var12, var4.entityId);
               if (var5 != null) {
                  NpcNet.sendTo(var5, new NpcNet.DlgEnd(var4.entityId));
               }
            }
         }
      }
   }

   private static void pruneListeners(MinecraftServer var0) {
      Iterator<Entry<UUID, Set<UUID>>> var1 = LISTENERS.entrySet().iterator();
      while (var1.hasNext()) {
         Entry<UUID, Set<UUID>> var2 = var1.next();
         UUID var3 = var2.getKey();
         Iterator<UUID> var4 = var2.getValue().iterator();
         while (var4.hasNext()) {
            UUID var5 = var4.next();
            if (var0.getPlayerList().getPlayer(var5) == null) {
               var4.remove();
               GuideDialogue.Observation var6 = OBSERVING.get(var5);
               if (var6 != null && var6.owner.equals(var3)) {
                  OBSERVING.remove(var5);
               }
            }
         }
         if (var2.getValue().isEmpty()) {
            var1.remove();
         }
      }
   }


   private static final int GRAPH_NEXT = -1, GRAPH_CLOSE = -2;

   public static boolean graphActive(NpcProfile profile) {
      if (profile == null || profile.dialogueGraph == null || !profile.dialogueGraph.enabled) return false;
      NpcDialogueGraph graph = profile.dialogueGraph;
      return graph.get(graph.entry) != null;
   }

   public static boolean evalCondition(int current, int op, int target) {
      return switch (op) {
         case 0 -> current == target;
         case 1 -> current != target;
         case 2 -> current >= target;
         case 3 -> current > target;
         case 4 -> current <= target;
         case 5 -> current < target;
         default -> false;
      };
   }

   public static int resolveExecutionTarget(ServerPlayer player, GuideNpc npc, Session session, NpcDialogueGraph graph, int startNodeId, Set<Integer> executedTracker) {
      if (graph == null || startNodeId < 0) return -1;
      HashSet<Integer> visited = new HashSet<>();
      int curId = startNodeId;
      while (curId >= 0 && visited.add(curId) && visited.size() <= 256) {
         NpcDialogueGraph.Node cur = graph.get(curId);
         if (cur == null) return -1;

         int rel = session != null ? session.relation : (npc != null && player != null ? npc.relationTo(player) : 0);

         if (cur.type != NpcDialogueGraph.IF && !isIncomingIfSatisfied(player, npc, rel, graph, cur)) {
            return -1;
         }

         if (cur.type == NpcDialogueGraph.LINE) {
            return cur.id;
         }

         if (cur.type == NpcDialogueGraph.TRIGGER) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (!cur.action.isBlank() && player != null && npc != null) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = cur.next;
            continue;
         }

         if (cur.type == NpcDialogueGraph.VARIABLE) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (!cur.pointsName.isBlank() && cur.varValue != 0 && player != null && player.getServer() != null) {
               NpcPoints.get(player.getServer()).add(player.getUUID(), cur.pointsName, cur.pointsShared, cur.varValue);
            }
            if (!cur.action.isBlank() && player != null && npc != null) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = cur.next;
            continue;
         }

         if (cur.type == NpcDialogueGraph.IF) {
            if (executedTracker != null) executedTracker.add(cur.id);
            String varName = cur.pointsName;
            boolean shared = cur.pointsShared;
            if (cur.varInputNode >= 0) {
               NpcDialogueGraph.Node inNode = graph.get(cur.varInputNode);
               if (inNode != null && inNode.type == NpcDialogueGraph.VARIABLE && !inNode.pointsName.isBlank()) {
                  varName = inNode.pointsName;
                  shared = inNode.pointsShared;
               }
            }
            int currentVal = 0;
            if (!varName.isBlank() && player != null && player.getServer() != null) {
               currentVal = NpcPoints.get(player.getServer()).get(player.getUUID(), varName, shared);
            }
            boolean pass = evalCondition(currentVal, cur.varOp, cur.varCompare);
            if (!cur.action.isBlank() && player != null && npc != null) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = pass ? cur.next : cur.falseNext;
            continue;
         }

         if (cur.type == NpcDialogueGraph.CHECK) {
            if (executedTracker != null) executedTracker.add(cur.id);
            boolean pass = checkProximityCondition(npc, player, cur);
            if (pass) {
               if (!cur.action.isBlank() && player != null && npc != null) {
                  NpcActions.run(player, npc, rel, cur.action);
               }
               executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
               curId = cur.next;
            } else {
               curId = -1;
            }
            continue;
         }

         if (cur.type == NpcDialogueGraph.DAMAGE || cur.type == NpcDialogueGraph.TIME || cur.type == NpcDialogueGraph.TIMER) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (!cur.action.isBlank() && player != null && npc != null) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = cur.next;
            continue;
         }

         if (cur.type == NpcDialogueGraph.ANIMATION || cur.type == NpcDialogueGraph.QUEST) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (cur.type == NpcDialogueGraph.ANIMATION && npc != null && !cur.animName.isBlank()) {
               npc.playCustomAnimation(cur.animName, cur.animLoop, cur.animBone);
            }
            if (!cur.action.isBlank() && (player != null || npc != null)) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = cur.next;
            continue;
         }

         if (cur.type == NpcDialogueGraph.MOVE) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (npc != null) {
               executeMoveNode(npc, player, cur);
            }
            if (!cur.action.isBlank() && (player != null || npc != null)) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = cur.next;
            continue;
         }

         if (cur.type == NpcDialogueGraph.MOBILITY) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (npc != null) {
               executeMobilityNode(npc, cur);
            }
            if (!cur.action.isBlank() && (player != null || npc != null)) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            curId = -1;
            continue;
         }

         if (cur.type == NpcDialogueGraph.CUTSCENE) {
            if (executedTracker != null) executedTracker.add(cur.id);
            if (!cur.action.isBlank() && (player != null || npc != null)) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            if (player != null && !cur.cutsceneName.isBlank()) {
               CutsceneCamera.play(player, cur.cutsceneName);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            curId = -1; // cutscene is terminal: the cinema takes over
            continue;
         }

         if (cur.type == NpcDialogueGraph.RANDOM) {
            if (executedTracker != null) executedTracker.add(cur.id);
            int min = Math.max(1, cur.randomMin);
            int max = Math.max(min, cur.randomMax);
            int picked = min + java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1);
            int port = picked - 1;
            if (!cur.action.isBlank() && (player != null || npc != null)) {
               NpcActions.run(player, npc, rel, cur.action);
            }
            executeGraphTriggers(player, npc, rel, graph, cur.triggers, executedTracker);
            if (port >= 0 && port < cur.randomBranches.size()) {
               curId = cur.randomBranches.get(port);
            } else {
               curId = -1;
            }
            continue;
         }

         break;
      }
      return -1;
   }

   public static int peekExecutionTarget(ServerPlayer player, NpcDialogueGraph graph, int startNodeId) {
      if (graph == null || startNodeId < 0) return -1;
      HashSet<Integer> visited = new HashSet<>();
      int curId = startNodeId;
      while (curId >= 0 && visited.add(curId) && visited.size() <= 256) {
         NpcDialogueGraph.Node cur = graph.get(curId);
         if (cur == null) return -1;
         if (cur.type != NpcDialogueGraph.IF && !isIncomingIfSatisfied(player, null, 0, graph, cur)) {
            return -1;
         }
         if (cur.type == NpcDialogueGraph.LINE) return cur.id;
         if (cur.type == NpcDialogueGraph.TRIGGER || cur.type == NpcDialogueGraph.VARIABLE) {
            curId = cur.next;
            continue;
         }
         if (cur.type == NpcDialogueGraph.CHECK || cur.type == NpcDialogueGraph.DAMAGE || cur.type == NpcDialogueGraph.TIME || cur.type == NpcDialogueGraph.TIMER || cur.type == NpcDialogueGraph.ANIMATION || cur.type == NpcDialogueGraph.QUEST || cur.type == NpcDialogueGraph.MOVE || cur.type == NpcDialogueGraph.CUTSCENE) {
            curId = cur.next;
            continue;
         }
         if (cur.type == NpcDialogueGraph.RANDOM) {
            int min = Math.max(1, cur.randomMin);
            int max = Math.max(min, cur.randomMax);
            int picked = min + java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1);
            int port = picked - 1;
            if (port >= 0 && port < cur.randomBranches.size()) {
               curId = cur.randomBranches.get(port);
            } else {
               curId = -1;
            }
            continue;
         }
         if (cur.type == NpcDialogueGraph.IF) {
            String varName = cur.pointsName;
            boolean shared = cur.pointsShared;
            if (cur.varInputNode >= 0) {
               NpcDialogueGraph.Node inNode = graph.get(cur.varInputNode);
               if (inNode != null && inNode.type == NpcDialogueGraph.VARIABLE && !inNode.pointsName.isBlank()) {
                  varName = inNode.pointsName;
                  shared = inNode.pointsShared;
               }
            }
            int currentVal = 0;
            if (player != null && !varName.isBlank() && player.getServer() != null) {
               currentVal = NpcPoints.get(player.getServer()).get(player.getUUID(), varName, shared);
            }
            boolean pass = evalCondition(currentVal, cur.varOp, cur.varCompare);
            curId = pass ? cur.next : cur.falseNext;
            continue;
         }
         break;
      }
      return -1;
   }

   public static void startAtLine(ServerPlayer player, GuideNpc npc, int lineId, Set<Integer> alreadyExecuted) {
      if (player == null || npc == null || npc.level().isClientSide() || !npc.isAlive()) return;
      if (npc.isEscorting()) return;
      stop(player);
      NpcProfile profile = npc.profile();
      Session session = new Session(npc.getId(), npc.level().dimension(), npc.relationTo(player));
      session.focusEntityId = npc.getId();
      session.focusSpeaker = speaker(npc);
      session.focusTitle = profile.title == null ? "" : profile.title;
      session.focusNameColor = NpcProfile.clamp(profile.nameColor, 0, 3);
      SESSIONS.put(player.getUUID(), session);
      npc.setTalking(true);
      if (!npc.isMovingToCustomTarget()) {
         npc.getNavigation().stop();
      }
      if (profile.lookAtPlayer && !npc.isMovingToCustomTarget()) {
         npc.getLookControl().setLookAt(player, 40.0F, 40.0F);
      }
      session.rootCursor = 0;
      session.defaultTabDone = true;
      session.inShop = false;
      session.graphNode = lineId;
      executeIncomingTriggers(player, npc, session.relation, profile.dialogueGraph, lineId, alreadyExecuted);
      if (SESSIONS.get(player.getUUID()) != session || npc.isEscorting() || session.inShop) return;
      sendGraph(player, session, npc);
   }

   public static void triggerCheckChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node checkNode) {
      triggerCheckChain(npc, player, npc == null || npc.profile() == null ? null : npc.profile().dialogueGraph, checkNode);
   }

   public static void triggerCheckChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph graph, NpcDialogueGraph.Node checkNode) {
      if (npc == null || checkNode == null) return;
      if (graph == null) return;

      int relation = player != null ? npc.relationTo(player) : 0;
      if (!isIncomingIfSatisfied(player, npc, relation, graph, checkNode)) return;
      Set<Integer> executedTracker = new HashSet<>();

      if (!checkNode.action.isBlank() && (player != null || npc != null)) {
         NpcActions.run(player, npc, relation, checkNode.action);
      }
      executeGraphTriggers(player, npc, relation, graph, checkNode.triggers, executedTracker);

      if (checkNode.next >= 0) {
         int targetLine = resolveExecutionTarget(player, npc, null, graph, checkNode.next, executedTracker);
         if (targetLine >= 0 && player != null && npc.profile() != null && npc.profile().dialogueGraph == graph) {
            startAtLine(player, npc, targetLine, executedTracker);
         }
      }
   }

   public static void triggerDamageChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node damageNode) {
      triggerDamageChain(npc, player, damageNode, player != null);
   }

   public static void triggerDamageChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node damageNode, boolean attackerIsPlayer) {
      triggerDamageChain(npc, player, npc == null || npc.profile() == null ? null : npc.profile().dialogueGraph, damageNode, attackerIsPlayer);
   }

   public static void triggerDamageChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph graph, NpcDialogueGraph.Node damageNode, boolean attackerIsPlayer) {
      if (npc == null || damageNode == null) return;
      if (graph == null) return;

      int relation = player != null ? npc.relationTo(player) : 0;
      if (!isIncomingIfSatisfied(player, npc, relation, graph, damageNode)) return;
      Set<Integer> executedTracker = new HashSet<>();

      if (!damageNode.action.isBlank() && (player != null || npc != null)) {
         NpcActions.run(player, npc, relation, damageNode.action);
      }
      executeGraphTriggers(player, npc, relation, graph, damageNode.triggers, executedTracker);

      if (damageNode.next >= 0) {
         int targetLine = resolveExecutionTarget(player, npc, null, graph, damageNode.next, executedTracker);
         if (targetLine >= 0 && attackerIsPlayer && player != null && npc.profile() != null && npc.profile().dialogueGraph == graph) {
            startAtLine(player, npc, targetLine, executedTracker);
         }
      }
   }

   public static boolean matchesDamageFilter(int filter, DamageSource source) {
      Entity attacker = source != null ? (source.getEntity() != null ? source.getEntity() : source.getDirectEntity()) : null;
      boolean isPlayer = attacker instanceof Player;
      boolean isMonster = attacker instanceof Enemy;
      return switch (filter) {
         case NpcDialogueGraph.DAMAGE_PLAYER -> isPlayer;
         case NpcDialogueGraph.DAMAGE_MONSTER -> isMonster;
         case NpcDialogueGraph.DAMAGE_NON_PLAYER -> !isPlayer;
         case NpcDialogueGraph.DAMAGE_ANY -> true;
         default -> true;
      };
   }

   public static void handleNpcDamaged(GuideNpc npc, DamageSource source, float amount, Map<Integer, Long> cooldowns) {
      handleNpcDamaged(npc, source, amount, cooldowns,
         npc != null && npc.profile() != null && graphActive(npc.profile()) ? npc.profile().dialogueGraph : null);
   }

   public static void handleNpcDamaged(GuideNpc npc, DamageSource source, float amount, Map<Integer, Long> cooldowns, NpcDialogueGraph graph) {
      if (npc == null || npc.level().isClientSide() || !npc.isAlive()) return;
      if (!(npc.level() instanceof ServerLevel level)) return;
      if (graph == null) return;

      long now = level.getGameTime();
      boolean attackerIsPlayer = false;
      ServerPlayer player = null;
      if (source != null) {
         if (source.getEntity() instanceof ServerPlayer sp) {
            player = sp;
            attackerIsPlayer = true;
         } else if (source.getDirectEntity() instanceof ServerPlayer sp) {
            player = sp;
            attackerIsPlayer = true;
         }
      }
      if (player == null) {
         if (level.getNearestPlayer(npc, 16.0) instanceof ServerPlayer sp) {
            player = sp;
         }
      }

      for (NpcDialogueGraph.Node node : graph.nodes.values()) {
         if (node.type != NpcDialogueGraph.DAMAGE) continue;
         if (amount < (float) Math.max(0, node.minDamage)) continue;
         if (!matchesDamageFilter(node.damageFilter, source)) continue;

         long cooldownTicks = Math.max(0, node.damageCooldown) * 20L;
         Long lastTime = cooldowns.get(node.id);
         if (lastTime != null && now - lastTime < cooldownTicks) continue;

         int rel = player != null ? npc.relationTo(player) : 0;
         if (!isIncomingIfSatisfied(player, npc, rel, graph, node)) continue;

         cooldowns.put(node.id, now);
         triggerDamageChain(npc, player, graph, node, attackerIsPlayer);
      }
   }

   public static boolean checkProximityCondition(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node checkNode) {
      if (npc == null || checkNode == null) return false;
      int radius = Math.max(1, Math.min(64, checkNode.checkRadius));
      double rSq = (double) radius * (double) radius;
      if (checkNode.checkTarget == NpcDialogueGraph.TARGET_PLAYER || checkNode.checkTarget == NpcDialogueGraph.TARGET_ANY) {
         if (player != null && player.isAlive() && !player.isSpectator()) {
            return npc.distanceToSqr(player) <= rSq;
         }
      }
      if (npc.level() instanceof ServerLevel level) {
         return findCheckTarget(npc, level, checkNode.checkTarget, radius, Map.of(), 0, 0) != null;
      }
      return false;
   }

   public static void tickGraphCheckNodes(GuideNpc npc, NpcDialogueGraph graph, Map<Integer, Map<UUID, Long>> cooldowns) {
      if (npc == null || graph == null || npc.level().isClientSide() || npc.isTalking() || !npc.isAlive()) return;
      if (!(npc.level() instanceof ServerLevel level)) return;
      long now = level.getGameTime();

      for (NpcDialogueGraph.Node node : graph.nodes.values()) {
         if (node.type != NpcDialogueGraph.CHECK) continue;
         if (node.next < 0 && node.triggers.isEmpty() && node.action.isBlank()) continue;

         int radius = Math.max(1, Math.min(64, node.checkRadius));
         long cooldownTicks = Math.max(0, node.checkCooldown) * 20L;
         Map<UUID, Long> nodeCds = cooldowns.computeIfAbsent(node.id, k -> new HashMap<>());

         if (nodeCds.size() > 64) {
            nodeCds.entrySet().removeIf(e -> now - e.getValue() > Math.max(cooldownTicks, 200L));
         }

         Entity matchedEntity = findCheckTarget(npc, level, node.checkTarget, radius, nodeCds, now, cooldownTicks);
         if (matchedEntity == null) continue;

         nodeCds.put(matchedEntity.getUUID(), now);

         ServerPlayer player = matchedEntity instanceof ServerPlayer sp ? sp : (level.getNearestPlayer(npc, radius + 8.0) instanceof ServerPlayer sp2 ? sp2 : null);
         int rel = player != null ? npc.relationTo(player) : 0;
         if (!isIncomingIfSatisfied(player, npc, rel, graph, node)) continue;

         triggerCheckChain(npc, player, graph, node);
         if (npc.isTalking()) break;
      }
   }

   private static Entity findCheckTarget(GuideNpc npc, ServerLevel level, int checkTarget, int radius, Map<UUID, Long> nodeCds, long now, long cooldownTicks) {
      double r = (double) radius;
      net.minecraft.world.phys.AABB aabb = npc.getBoundingBox().inflate(r);

      switch (checkTarget) {
         case NpcDialogueGraph.TARGET_NPC: {
            List<GuideNpc> npcs = level.getEntitiesOfClass(GuideNpc.class, aabb, e -> e != npc && e.isAlive() && npc.distanceToSqr(e) <= r * r);
            for (GuideNpc other : npcs) {
               Long lastTime = nodeCds.get(other.getUUID());
               if (lastTime != null && now - lastTime < cooldownTicks) continue;
               return other;
            }
            return null;
         }
         case NpcDialogueGraph.TARGET_MONSTER: {
            List<Entity> monsters = level.getEntitiesOfClass(Entity.class, aabb, e -> e instanceof Enemy && e.isAlive() && npc.distanceToSqr(e) <= r * r);
            for (Entity monster : monsters) {
               Long lastTime = nodeCds.get(monster.getUUID());
               if (lastTime != null && now - lastTime < cooldownTicks) continue;
               return monster;
            }
            return null;
         }
         case NpcDialogueGraph.TARGET_ANY: {
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, aabb, e -> e != npc && e.isAlive() && !(e instanceof Player p && p.isSpectator()) && npc.distanceToSqr(e) <= r * r);
            for (Entity ent : entities) {
               if (ent instanceof ServerPlayer sp && (isInDialogue(sp) || sp.isSpectator())) continue;
               Long lastTime = nodeCds.get(ent.getUUID());
               if (lastTime != null && now - lastTime < cooldownTicks) continue;
               return ent;
            }
            return null;
         }
         case NpcDialogueGraph.TARGET_PLAYER:
         default: {
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, aabb, p -> !p.isSpectator() && p.isAlive() && npc.distanceToSqr(p) <= r * r);
            for (ServerPlayer player : players) {
               if (isInDialogue(player)) continue;
               Long lastTime = nodeCds.get(player.getUUID());
               if (lastTime != null && now - lastTime < cooldownTicks) continue;
               return player;
            }
            return null;
         }
      }
   }

   public static void triggerTimeChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node timeNode) {
      triggerTimeChain(npc, player, npc == null || npc.profile() == null ? null : npc.profile().dialogueGraph, timeNode);
   }

   public static void triggerTimeChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph graph, NpcDialogueGraph.Node timeNode) {
      if (npc == null || timeNode == null) return;
      if (graph == null) return;

      int relation = player != null ? npc.relationTo(player) : 0;
      if (!isIncomingIfSatisfied(player, npc, relation, graph, timeNode)) return;
      Set<Integer> executedTracker = new HashSet<>();

      if (!timeNode.action.isBlank() && (player != null || npc != null)) {
         NpcActions.run(player, npc, relation, timeNode.action);
      }
      executeGraphTriggers(player, npc, relation, graph, timeNode.triggers, executedTracker);

      if (timeNode.next >= 0) {
         int targetLine = resolveExecutionTarget(player, npc, null, graph, timeNode.next, executedTracker);
         if (targetLine >= 0 && player != null && npc.profile() != null && npc.profile().dialogueGraph == graph) {
            startAtLine(player, npc, targetLine, executedTracker);
         }
      }
   }

   public static void tickGraphTimeNodes(GuideNpc npc, NpcDialogueGraph graph, Map<Integer, Long> lastTriggerDays) {
      if (npc == null || graph == null || npc.level().isClientSide() || npc.isTalking() || !npc.isAlive()) return;
      if (!(npc.level() instanceof ServerLevel level)) return;

      long dayTime = level.getDayTime();
      long currentDay = dayTime / 24000L;
      long tickInDay = (dayTime % 24000L + 24000L) % 24000L;

      for (NpcDialogueGraph.Node node : graph.nodes.values()) {
         if (node.type != NpcDialogueGraph.TIME) continue;
         if (node.next < 0 && node.triggers.isEmpty() && node.action.isBlank()) continue;

         long targetTick = switch (node.timeMode) {
            case NpcDialogueGraph.TIME_DAWN -> 0L;
            case NpcDialogueGraph.TIME_NOON -> 6000L;
            case NpcDialogueGraph.TIME_SUNSET -> 12000L;
            case NpcDialogueGraph.TIME_MIDNIGHT -> 18000L;
            case NpcDialogueGraph.TIME_CUSTOM -> (long) Math.max(0, Math.min(23999, node.timeTicks));
            default -> (long) Math.max(0, Math.min(23999, node.timeTicks));
         };

         Long lastDay = lastTriggerDays.get(node.id);
         if (lastDay != null && lastDay == currentDay) continue;

         if (tickInDay >= targetTick && tickInDay < targetTick + 400L) {
            lastTriggerDays.put(node.id, currentDay);
            ServerPlayer player = level.getNearestPlayer(npc, 32.0) instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
            int rel = player != null ? npc.relationTo(player) : 0;
            if (!isIncomingIfSatisfied(player, npc, rel, graph, node)) continue;
            triggerTimeChain(npc, player, graph, node);
            if (npc.isTalking()) break;
         }
      }
   }

   public static void triggerTimerChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph.Node timerNode) {
      triggerTimerChain(npc, player, npc == null || npc.profile() == null ? null : npc.profile().dialogueGraph, timerNode);
   }

   public static void triggerTimerChain(GuideNpc npc, ServerPlayer player, NpcDialogueGraph graph, NpcDialogueGraph.Node timerNode) {
      if (npc == null || timerNode == null) return;
      if (graph == null) return;

      int relation = player != null ? npc.relationTo(player) : 0;
      if (!isIncomingIfSatisfied(player, npc, relation, graph, timerNode)) return;
      Set<Integer> executedTracker = new HashSet<>();

      if (!timerNode.action.isBlank() && (player != null || npc != null)) {
         NpcActions.run(player, npc, relation, timerNode.action);
      }
      executeGraphTriggers(player, npc, relation, graph, timerNode.triggers, executedTracker);

      if (timerNode.next >= 0) {
         int targetLine = resolveExecutionTarget(player, npc, null, graph, timerNode.next, executedTracker);
         if (targetLine >= 0 && player != null && npc.profile() != null && npc.profile().dialogueGraph == graph) {
            startAtLine(player, npc, targetLine, executedTracker);
         }
      }
   }

   public static void tickGraphTimerNodes(GuideNpc npc, NpcDialogueGraph graph, Map<Integer, Long> lastTriggerTicks) {
      if (npc == null || graph == null || npc.level().isClientSide() || npc.isTalking() || !npc.isAlive()) return;
      if (!(npc.level() instanceof ServerLevel level)) return;
      long now = level.getGameTime();

      for (NpcDialogueGraph.Node node : graph.nodes.values()) {
         if (node.type != NpcDialogueGraph.TIMER) continue;
         if (node.next < 0 && node.triggers.isEmpty() && node.action.isBlank()) continue;

         long intervalTicks = (long) Math.max(1, node.timerInterval) * 20L;
         Long lastTick = lastTriggerTicks.get(node.id);
         if (lastTick == null) {
            lastTriggerTicks.put(node.id, now);
            continue;
         }
         if (lastTick == Long.MAX_VALUE) continue;

         if (now - lastTick >= intervalTicks) {
            if (node.timerRepeat) {
               lastTriggerTicks.put(node.id, now);
            } else {
               lastTriggerTicks.put(node.id, Long.MAX_VALUE);
            }
            ServerPlayer player = level.getNearestPlayer(npc, 32.0) instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
            int rel = player != null ? npc.relationTo(player) : 0;
            if (!isIncomingIfSatisfied(player, npc, rel, graph, node)) continue;
            triggerTimerChain(npc, player, graph, node);
            if (npc.isTalking()) break;
         }
      }
   }

   private static void startGraph(ServerPlayer player, Session session, GuideNpc npc) {
      NpcDialogueGraph graph = npc.profile().dialogueGraph;
      session.inShop = false;
      session.defaultTabDone = true;
      HashSet<Integer> executedThisStep = new HashSet<>();
      int targetLine = resolveExecutionTarget(player, npc, session, graph, graph.entry, executedThisStep);
      if (targetLine < 0) {
         stop(player);
         return;
      }
      session.graphNode = targetLine;
      executeIncomingTriggers(player, npc, session.relation, graph, targetLine, executedThisStep);
      if (SESSIONS.get(player.getUUID()) != session || npc.isEscorting() || session.inShop) return;
      sendGraph(player, session, npc);
   }

   private static boolean graphButtonVisible(ServerPlayer player, GuideNpc npc, NpcDialogueGraph.Node node, int relation) {
      if (node == null || node.type != NpcDialogueGraph.BUTTON || node.text.isBlank()) return false;
      NpcDialogueGraph graph = npc != null && npc.profile() != null ? npc.profile().dialogueGraph : null;
      if (!isIncomingIfSatisfied(player, npc, relation, graph, node)) return false;
      if (!node.pointsName.isBlank()) {
         NpcPoints points = NpcPoints.get(player.getServer());
         int balance = points.get(player.getUUID(), node.pointsName, node.pointsShared);
         return node.minPoints <= balance && balance <= node.maxPoints;
      }
      return node.relationVisible(relation);
   }

   private static boolean graphButtonVisible(ServerPlayer player, NpcDialogueGraph.Node node, int relation) {
      return graphButtonVisible(player, null, node, relation);
   }

   public static int effectiveNext(ServerPlayer player, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
      if (node == null || graph == null) return -1;
      int start = node.next;
      if (start < 0 && node.builtinTopic < 0) {
         for (int tid : node.triggers) {
            NpcDialogueGraph.Node trig = graph.get(tid);
            if (trig != null && trig.type == NpcDialogueGraph.TRIGGER) {
               int target = resolveTriggerNext(graph, trig);
               if (target >= 0) { start = target; break; }
            }
         }
      }
      if (start < 0) return -1;
      return peekExecutionTarget(player, graph, start);
   }

   public static int effectiveNext(NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
      return effectiveNext(null, graph, node);
   }

   private static int resolveTriggerNext(NpcDialogueGraph graph, NpcDialogueGraph.Node trig) {
      HashSet<Integer> visited = new HashSet<>();
      NpcDialogueGraph.Node cur = trig;
      while (cur != null && cur.type == NpcDialogueGraph.TRIGGER && visited.add(cur.id)) {
         if (cur.next >= 0) return cur.next;
         NpcDialogueGraph.Node nextTrig = null;
         for (int subId : cur.triggers) {
            NpcDialogueGraph.Node sub = graph.get(subId);
            if (sub != null && sub.type == NpcDialogueGraph.TRIGGER) {
               nextTrig = sub;
               break;
            }
         }
         cur = nextTrig;
      }
      return -1;
   }

   private static void sendGraph(ServerPlayer player, Session session, GuideNpc npc) {
      NpcProfile profile = npc.profile();
      NpcDialogueGraph graph = profile.dialogueGraph;
      if (!graphActive(profile)) { session.graphNode = -1; sendRoot(player, session, npc); return; }
      NpcDialogueGraph.Node node = graph.get(session.graphNode);
      if (node == null || node.type != NpcDialogueGraph.LINE || node.special != NpcDialogueGraph.NORMAL) {
         node = graph.get(graph.entry);
         if (node == null) { stop(player); return; }
         session.graphNode = node.id;
      }
      session.relation = npc.relationTo(player);
      ArrayList<String> options = new ArrayList<>(), hints = new ArrayList<>();
      ArrayList<Integer> ids = new ArrayList<>();
      for (int id : node.choices) {
         NpcDialogueGraph.Node button = graph.get(id);
         if (graphButtonVisible(player, npc, button, session.relation)) {
            options.add(NpcProfile.fill(button.text, player, session.relation)); hints.add(""); ids.add(id);
         }
      }
      int nextNode = effectiveNext(player, graph, node);
      boolean hasForward = nextNode >= 0 || node.builtinTopic >= 0 || (node.choices.isEmpty() && node.next >= 0);
      if (node.choices.isEmpty()) {
         // Sequential chain of replicas: no Next/Finish/Exit buttons.
         // Flip replicas by clicking LMB on empty space (or E / Enter / Space).
         ids.add(hasForward ? GRAPH_NEXT : GRAPH_CLOSE);
      } else {
         // Branching choices present:
         if (nextNode >= 0 || node.builtinTopic >= 0) {
            options.add(BTN_NEXT); hints.add(HINT_NEXT); ids.add(GRAPH_NEXT);
         } else if (options.isEmpty()) {
            options.add(BTN_FINISH); hints.add(""); ids.add(GRAPH_CLOSE);
         }
      }
      session.graphOptions = ids.stream().mapToInt(Integer::intValue).toArray();
      session.state = 1;
      String line = node.lineFor(session.relation);
      NpcNet.sendTo(player, new NpcNet.DlgView(npc.getId(), speaker(npc), profile.title,
         fill(line, player, session, profile), options, hints, session.relation,
         1, profile.hasShop(), style(profile, player, session.relation)));
   }

   /** Exactly one explicit player choice per transition: backward links never execute an automatic loop. */
   private static void executeGraphTriggers(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, List<Integer> triggerIds) {
      executeGraphTriggers(player, npc, relation, graph, triggerIds, null);
   }

   private static void executeGraphTriggers(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, List<Integer> triggerIds, Set<Integer> executedTracker) {
      if (triggerIds == null || triggerIds.isEmpty()) return;
      HashSet<Integer> visited = new HashSet<>();
      ArrayDeque<Integer> queue = new ArrayDeque<>(triggerIds);
      while (!queue.isEmpty()) {
         int id = queue.poll();
         if (!visited.add(id)) continue;
         if (executedTracker != null && !executedTracker.add(id)) continue;
         NpcDialogueGraph.Node trig = graph.get(id);
         if (trig == null) continue;
         if (!isIncomingIfSatisfied(player, npc, relation, graph, trig)) continue;
         if (trig.type == NpcDialogueGraph.TRIGGER || trig.type == NpcDialogueGraph.ANIMATION || trig.type == NpcDialogueGraph.MOVE) {
            if (trig.type == NpcDialogueGraph.ANIMATION && npc != null && !trig.animName.isBlank()) {
               npc.playCustomAnimation(trig.animName, trig.animLoop, trig.animBone);
            }
            if (trig.type == NpcDialogueGraph.MOVE && npc != null) {
               executeMoveNode(npc, player, trig);
            }
            if (!trig.action.isBlank()) {
               NpcActions.run(player, npc, relation, trig.action);
            }
            queue.addAll(trig.triggers);
            if (trig.next >= 0) {
               NpcDialogueGraph.Node nextNode = graph.get(trig.next);
               if (nextNode != null && (nextNode.type == NpcDialogueGraph.TRIGGER || nextNode.type == NpcDialogueGraph.ANIMATION || nextNode.type == NpcDialogueGraph.MOVE)) {
                  queue.add(trig.next);
               }
            }
         }
      }
   }

   private static void executeIncomingTriggers(ServerPlayer player, GuideNpc npc, int relation, NpcDialogueGraph graph, int targetNodeId, Set<Integer> alreadyExecuted) {
      if (graph == null || targetNodeId < 0) return;
      HashSet<Integer> leadsToTarget = new HashSet<>();
      for (NpcDialogueGraph.Node n : graph.nodes.values()) {
         if ((n.type == NpcDialogueGraph.TRIGGER || n.type == NpcDialogueGraph.ANIMATION || n.type == NpcDialogueGraph.MOVE) && n.next == targetNodeId) {
            leadsToTarget.add(n.id);
         }
      }
      if (leadsToTarget.isEmpty()) return;

      boolean expanded = true;
      while (expanded) {
         expanded = false;
         for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if ((n.type == NpcDialogueGraph.TRIGGER || n.type == NpcDialogueGraph.ANIMATION || n.type == NpcDialogueGraph.MOVE) && !leadsToTarget.contains(n.id)) {
               if (leadsToTarget.contains(n.next) || n.triggers.stream().anyMatch(leadsToTarget::contains)) {
                  leadsToTarget.add(n.id);
                  expanded = true;
               }
            }
         }
      }

      HashSet<Integer> nonRoots = new HashSet<>();
      for (int id : leadsToTarget) {
         NpcDialogueGraph.Node n = graph.get(id);
         if (n != null) {
            if (leadsToTarget.contains(n.next)) nonRoots.add(n.next);
            for (int child : n.triggers) {
               if (leadsToTarget.contains(child)) nonRoots.add(child);
            }
         }
      }

      ArrayList<Integer> roots = new ArrayList<>();
      for (int id : leadsToTarget) {
         if (!nonRoots.contains(id)) {
            roots.add(id);
         }
      }
      if (roots.isEmpty()) roots.addAll(leadsToTarget);

      executeGraphTriggers(player, npc, relation, graph, roots, alreadyExecuted);
   }

   private static void pickGraph(ServerPlayer player, Session session, GuideNpc npc, int index) {
      NpcDialogueGraph graph = npc.profile().dialogueGraph;
      NpcDialogueGraph.Node line = graph.get(session.graphNode);
      if (line == null || index < 0 || index >= session.graphOptions.length) { sendGraph(player, session, npc); return; }
      int id = session.graphOptions[index];
      NpcDialogueGraph.Node button = id >= 0 ? graph.get(id) : null;
      // Recheck requirements and membership; a stale/forged pick cannot bypass them.
      if (id >= 0 && (!line.choices.contains(id) || !graphButtonVisible(player, npc, button, npc.relationTo(player)))) {
         sendGraph(player, session, npc); return;
      }
      int nextNode = effectiveNext(player, graph, line);
      int lineTarget = line.next >= 0 ? line.next : nextNode;
      if (id == GRAPH_NEXT && lineTarget < 0 && line.builtinTopic < 0) { sendGraph(player, session, npc); return; }
      HashSet<Integer> executedThisStep = new HashSet<>();
      if (!line.action.isBlank()) NpcActions.run(player, npc, session.relation, line.action);
      session.lastExecutedTriggersNode = line.id;
      executeGraphTriggers(player, npc, session.relation, graph, line.triggers, executedThisStep);
      if (SESSIONS.get(player.getUUID()) != session || npc.isEscorting() || session.inShop) return;
      if (id == GRAPH_CLOSE) { stop(player); return; }
      if (id == GRAPH_NEXT) {
         if (lineTarget >= 0) {
            int targetLine = resolveExecutionTarget(player, npc, session, graph, lineTarget, executedThisStep);
            if (targetLine >= 0) {
               session.graphNode = targetLine;
               executeIncomingTriggers(player, npc, session.relation, graph, targetLine, executedThisStep);
               if (SESSIONS.get(player.getUUID()) != session || npc.isEscorting() || session.inShop) return;
               sendGraph(player, session, npc);
               return;
            } else {
               stop(player);
               return;
            }
         }
         if (line.builtinTopic >= 0) { session.graphNode = -1; openTopic(player, session, npc, line.builtinTopic); return; }
         sendGraph(player, session, npc);
         return;
      }
      if (button.relation != 0) { GuideNpc.addRelation(player, npc, button.relation); session.relation = npc.relationTo(player); }
      if (!button.action.isBlank()) NpcActions.run(player, npc, session.relation, button.action);
      executeGraphTriggers(player, npc, session.relation, graph, button.triggers, executedThisStep);
      if (SESSIONS.get(player.getUUID()) != session || npc.isEscorting() || session.inShop) return;
      int buttonNext = effectiveNext(player, graph, button);
      int buttonTarget = button.next >= 0 ? button.next : buttonNext;
      if (buttonTarget >= 0) {
         int targetLine = resolveExecutionTarget(player, npc, session, graph, buttonTarget, executedThisStep);
         if (targetLine >= 0) {
            session.graphNode = targetLine;
            executeIncomingTriggers(player, npc, session.relation, graph, targetLine, executedThisStep);
            if (SESSIONS.get(player.getUUID()) != session || npc.isEscorting() || session.inShop) return;
            sendGraph(player, session, npc);
         } else stop(player);
      } else if (button.builtinTopic >= 0) {
         session.graphNode = -1;
         openTopic(player, session, npc, button.builtinTopic);
      } else stop(player);
   }

   private record Observation(UUID owner, int entityId) {
   }

   private static final class Session {
      final int entityId;
      final ResourceKey<Level> dimension;
      int relation;
      int state = 4;
      int escortLine;
      int graphNode = -1;
      int lastExecutedTriggersNode = -1;
      int[] graphOptions = new int[0];
      int topic = -1;
      int step = 0;
      int pendingNext = -1;
      int pendingTopic = -1;
      String pendingReaction = "";
      int legacyCursor = 0;
      int rootCursor = 0;
      String rootLine = "";
      int questTopic = -1;
      int quest = -1;
      boolean closeOnEnd;
      int menuOnEnd = -1;
      int menuStep = 0;
      boolean remindOnEnd = true;
      boolean menuButtons = true;
      long lastPick;
      boolean defaultTabDone;
      boolean inShop;
      NpcNet.DlgView lastView;
      int chainFrom = -1;
      int answerStep = -1;
      int focusEntityId;
      String focusSpeaker = "";
      String focusTitle = "";
      int focusNameColor = 1;
      List<List<GuidePhrase>> legacy = List.of();

      Session(int var1, ResourceKey<Level> var2, int var3) {
         this.entityId = var1;
         this.dimension = var2;
         this.relation = var3;
      }
   }
}
