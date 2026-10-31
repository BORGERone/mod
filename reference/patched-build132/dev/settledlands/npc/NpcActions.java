package dev.settledlands.npc;

import dev.settledlands.revival.PurgatoryManager;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NpcActions {
   private static final Logger LOG = LoggerFactory.getLogger("settledlands");

   private NpcActions() {
   }

   public static boolean runSingle(ServerPlayer var0, GuideNpc var1, int var2, String var3) {
      if ((var0 != null || var1 != null) && var3 != null) {
         String var4 = var3.trim();
         if (var4.isEmpty()) {
            return false;
         } else if (NpcProfile.actionKind(var4).equals("trigger") || isTriggerFocusForm(var4) || isTriggerGoForm(var4) || isTriggerAnimForm(var4) || isTriggerHealForm(var4)) {
            String triggerText = var4.regionMatches(true, 0, "trigger:", 0, 8)
               ? var4.substring(8).trim()
               : (var4.regionMatches(true, 0, "trigger ", 0, 8) ? var4.substring(8).trim() : var4);
            return trigger(var0, var1, var2, normalizeFocusCommand(triggerText));
         } else {
            if (var0 == null && var1 == null) return false;
            String var5 = var4.regionMatches(true, 0, "cmd:", 0, 4) ? var4.substring(4).trim() : var4;
            return command(var0, var1, var2, var5);
         }
      } else {
         return false;
      }
   }

   private static boolean isTriggerHealForm(String action) {
      if (action == null) return false;
      String value = action.trim();
      while (value.startsWith("/")) value = value.substring(1).trim();
      String lower = value.toLowerCase(Locale.ROOT);
      return lower.equals("heal") || lower.startsWith("heal ")
          || lower.equals("лечение") || lower.startsWith("лечение ")
          || lower.equals("исцеление") || lower.startsWith("исцеление ")
          || lower.equals("хп") || lower.startsWith("хп ");
   }

   private static boolean isTriggerAnimForm(String action) {
      if (action == null) return false;
      String value = action.trim();
      while (value.startsWith("/")) value = value.substring(1).trim();
      String lower = value.toLowerCase(Locale.ROOT);
      return lower.equals("anim") || lower.startsWith("anim ")
          || lower.equals("playanim") || lower.startsWith("playanim ")
          || lower.equals("animation") || lower.startsWith("animation ")
          || lower.equals("play_anim") || lower.startsWith("play_anim ")
          || lower.equals("анимация") || lower.startsWith("анимация ")
          || lower.equals("аним") || lower.startsWith("аним ");
   }

   private static boolean isTriggerFocusForm(String action) {
      if (action == null || !action.regionMatches(true, 0, "trigger ", 0, 8)) {
         return false;
      }
      return isFocusCommand(action.substring(8).trim());
   }

   private static boolean isTriggerGoForm(String action) {
      return action != null && action.regionMatches(true, 0, "trigger ", 0, 8) && isGoCommand(action.substring(8).trim());
   }

   private static boolean isGoCommand(String action) {
      if (action == null) return false;
      String value = action.trim();
      while (value.startsWith("/")) value = value.substring(1).trim();
      return value.equals("go") || value.startsWith("go ");
   }

   private static boolean isFocusCommand(String action) {
      if (action == null) {
         return false;
      }
      String value = action.trim();
      int end = 0;
      while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
         end++;
      }
      return end == 5 && value.regionMatches(true, 0, "focus", 0, 5);
   }

   private static String normalizeFocusCommand(String action) {
      if (!isFocusCommand(action)) {
         return action;
      }
      String value = action.trim();
      int end = 0;
      while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
         end++;
      }
      String target = value.substring(end).trim();
      int targetEnd = 0;
      while (targetEnd < target.length() && !Character.isWhitespace(target.charAt(targetEnd))) {
         targetEnd++;
      }
      if (targetEnd == 3 && target.regionMatches(true, 0, "set", 0, 3)) {
         target = target.substring(targetEnd).trim();
      }
      return target.isEmpty() ? "focus" : "focus " + target;
   }

   private static boolean command(ServerPlayer var0, GuideNpc var1, int var2, String var3) {
      MinecraftServer var4 = var0 != null ? var0.getServer() : (var1 != null && var1.level() instanceof ServerLevel sl ? sl.getServer() : null);
      if (var4 == null) {
         return false;
      } else {
         String var5 = fill(var3, var0, var1, var2);

         while (var5.startsWith("/")) {
            var5 = var5.substring(1).trim();
         }

         if (var1 != null && isGoCommand(var5) && var0 != null) {
            return NpcEscortCommands.start(var0, var1, var5.length() <= 2 ? "" : var5.substring(3).trim());
         }
         if (var5.isEmpty()) {
            return false;
         } else {
            if (var5.matches("^(tp|teleport)\\s+([~^\\-0-9].*)")) {
               var5 = var5.replaceFirst("^(tp|teleport)\\s+", "$1 @s ");
            }

            CommandSourceStack var6;
            if (var0 != null) {
               var6 = var0.createCommandSourceStack().withPermission(2);
            } else if (var1 != null) {
               var6 = var1.createCommandSourceStack().withPermission(2);
            } else {
               var6 = var4.createCommandSourceStack().withPermission(2);
            }

            try {
               var4.getCommands().performPrefixedCommand(var6, var5);
               LOG.info(
                  "НПС {}: выполнена команда триггера: «/{}»",
                  new Object[]{var1 == null ? "?" : var1.getName().getString(), var5}
               );
               return true;
            } catch (Exception var8) {
               LOG.warn(
                  "НПС {}: команда «{}» не выполнена: {}. Проверьте строку действия",
                  new Object[]{var1 == null ? "?" : var1.getName().getString(), var5, var8.getMessage()}
               );
               return false;
            }
         }
      }
   }

   private static boolean trigger(ServerPlayer var0, GuideNpc var1, int var2, String var3) {
      String var4 = var3 == null ? "" : var3.trim();
      while (var4.startsWith("/")) var4 = var4.substring(1).trim();
      String var5 = "";
      int var6 = var4.indexOf(32);
      if (var6 >= 0) {
         var5 = var4.substring(var6 + 1).trim();
         var4 = var4.substring(0, var6);
      }

      String var7 = var4.toLowerCase(Locale.ROOT);
      if (var7.startsWith("settledlands:")) {
         var7 = var7.substring("settledlands:".length());
      }

      MinecraftServer var8 = var0 != null ? var0.getServer() : (var1 != null && var1.level() instanceof ServerLevel sl ? sl.getServer() : null);
      switch (var7) {
         case "go":
            String route = fill(var5, var0, var1, var2).trim();
            return var1 == null ? (var0 != null ? NpcEscortCommands.startNearby(var0, route) : false) : (var0 != null ? NpcEscortCommands.start(var0, var1, route) : false);
         case "shop":
         case "trade":
            if (var0 != null) NpcShop.send(var0, var1);
            return true;
         case "purgatory":
            if (var8 == null || var0 == null) {
               return false;
            }

            GuideDialogue.stop(var0);
            PurgatoryManager.teleportToPurgatory(var8, var0, "true".equalsIgnoreCase(var5));
            return true;
         case "relation":
         case "relate":
            int var11 = parse(var5, 0);
            if (var11 != 0 && var1 != null && var0 != null) {
               GuideNpc.addRelation(var0, var1, var11);
               return true;
            }

            return false;
         case "focus": {
            if (var1 == null || var0 == null) {
               return false;
            }

            GuideNpc focusNpc = resolveFocusTarget(var0, var1, var8, var5);
            if (focusNpc == null) {
               LOG.warn("НПС {}: цель focus «{}» не найдена; проверьте identifier/имя цели, измерение и расстояние (до 128 блоков)", var1.getName().getString(), var5);
               return false;
            }
            if (!GuideDialogue.setVisualFocus(var0, var1, focusNpc)) {
               LOG.warn("НПС {}: не удалось переключить фокус на «{}»; проверьте, что диалог ещё открыт и NPC рядом", var1.getName().getString(), var5);
               return false;
            }
            return true;
         }
         case "anim":
         case "playanim":
         case "animation":
         case "play_anim":
         case "анимация":
         case "аним": {
            String[] parts = var5.trim().split("\\s+");
            String anim = parts.length > 0 ? parts[0] : "";
            boolean loop = parts.length > 1 && (parts[1].equalsIgnoreCase("loop") || parts[1].equalsIgnoreCase("repeat") || parts[1].equals("1") || parts[1].equalsIgnoreCase("true") || parts[1].equalsIgnoreCase("цикл") || parts[1].equalsIgnoreCase("да"));
            String bone = parts.length > 2 ? parts[2] : "";
            if (var1 != null) {
               var1.playCustomAnimation(anim, loop, bone);
            }
            return true;
         }
         case "heal":
         case "лечение":
         case "исцеление":
         case "хп":
         case "hp": {
            if (var1 != null) {
               float amount = 0.0f;
               if (!var5.isBlank()) {
                  try {
                     amount = Float.parseFloat(var5.trim());
                  } catch (Exception ignored) {}
               }
               if (amount <= 0.0f) {
                  var1.setHealth(var1.getMaxHealth());
               } else {
                  var1.heal(amount);
               }
               return true;
            }
            return false;
         }
         case "close":
         case "bye":
         case "exit":
            if (var0 != null) GuideDialogue.stop(var0);
            return true;
         default:
            LOG.warn("НПС {}: неизвестный триггер «{}». Доступны: go [код пути], shop, purgatory, relation, focus [имя/identifier], focus origin, close", var1 == null ? "?" : var1.getName().getString(), var3);
            return false;
      }
   }

   private static GuideNpc resolveFocusTarget(ServerPlayer player, GuideNpc owner, MinecraftServer server, String selector) {
      String requested = unquote(selector == null ? "" : selector.trim());
      if (requested.equalsIgnoreCase("origin") || requested.equalsIgnoreCase("self")) {
         return owner;
      }

      if (requested.isEmpty()) {
         requested = owner.profile().focusNpcUuid == null ? "" : owner.profile().focusNpcUuid.trim();
      }
      if (requested.isEmpty() || server == null) {
         return null;
      }

      try {
         UUID uuid = UUID.fromString(requested);
         for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof GuideNpc target && target.isAlive()) {
               return target;
            }
         }
      } catch (IllegalArgumentException ignored) {
      }

      String wanted = normalizeTarget(requested);
      if (wanted.isEmpty()) {
         return null;
      }

      GuideNpc identifierMatch = null;
      GuideNpc nameMatch = null;
      double identifierDistance = Double.MAX_VALUE;
      double nameDistance = Double.MAX_VALUE;
      for (ServerLevel level : server.getAllLevels()) {
         for (GuideNpc candidate : level.getEntitiesOfClass(GuideNpc.class, player.getBoundingBox().inflate(128.0))) {
            if (!candidate.isAlive() || candidate.level() != player.level() || player.distanceToSqr(candidate) > 16384.0) {
               continue;
            }

            double distance = player.distanceToSqr(candidate);
            NpcProfile candidateProfile = candidate.profile();
            if (wanted.equals(normalizeTarget(candidateProfile.identifier)) && distance < identifierDistance) {
               identifierMatch = candidate;
               identifierDistance = distance;
            }

            String candidateName = candidateProfile.name;
            if (candidateName == null || candidateName.isBlank()) {
               candidateName = candidate.getDisplayName().getString();
            }
            if (wanted.equals(normalizeTarget(candidateName)) && distance < nameDistance) {
               nameMatch = candidate;
               nameDistance = distance;
            }
         }
      }

      return identifierMatch != null ? identifierMatch : nameMatch;
   }

   private static String unquote(String value) {
      if (value != null && value.length() >= 2) {
         char first = value.charAt(0);
         char last = value.charAt(value.length() - 1);
         if ((first == '"' && last == '"') || (first == '\'' && last == '\'') || (first == '[' && last == ']')) {
            return value.substring(1, value.length() - 1).trim();
         }
      }
      return value == null ? "" : value;
   }

   private static String normalizeTarget(String value) {
      return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
   }

   private static String fill(String var0, ServerPlayer var1, GuideNpc var2, int var3) {
      String var4 = var2 == null ? "" : var2.getName().getString();
      String var5 = NpcProfile.fill(var0, var1, var3);
      var5 = var5.replace("%npc_id%", var2 == null ? "" : String.valueOf(var2.getId()));
      String uuidStr = var2 == null ? "" : var2.getUUID().toString();
      var5 = var5.replace("%npc_uuid%", uuidStr);
      var5 = var5.replace("%uuid%", uuidStr);
      if (var2 != null) {
         var5 = var5.replaceAll("(?i)@npc\\b", uuidStr);
      }
      return var5.replace("%npc%", var4);
   }

   private static int parse(String var0, int var1) {
      try {
         return Integer.parseInt(var0.trim());
      } catch (RuntimeException var3) {
         return var1;
      }
   }

   public static boolean run(ServerPlayer var0, GuideNpc var1, int var2, String var3) {
      if ((var0 == null && var1 == null) || var3 == null || var3.isEmpty()) {
         return false;
      }
      if (var3.indexOf('␞') >= 0) {
         return ActionSequence.runAll(var0, var1, var2, var3);
      }
      return runSingle(var0, var1, var2, var3);
   }
}
