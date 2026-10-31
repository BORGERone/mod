package dev.settledlands.npc.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

public final class CommandTabCompleter {
   private static final Map<Object, CommandTabCompleter.State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final String[] TRIGGERS = new String[]{"go", "shop", "trade", "purgatory", "relation", "focus", "focus origin", "close", "bye", "exit", "anim", "heal"};

   private CommandTabCompleter() {
   }

   public static boolean handleKey(Object var0, int var1, int var2, int var3) {
      if (var0 != null && (var1 == 258 || var1 == 264 || var1 == 265)) {
         Object var4 = triggerBox(var0);
         if (var4 != null && isFocused(var4)) {
            CommandTabCompleter.State var5 = refreshState(var0, var4);
            if (var5 == null) {
               return false;
            } else if (var5.candidates == null) {
               return var5.future != null;
            } else if (var5.candidates.isEmpty()) {
               return false;
            } else {
               String var6 = stringValue(var4, "getValue");
               int var7 = cursor(var4, var6);
               boolean var8 = var5.lastValue != null && var5.lastValue.equals(var6) && var5.lastCursor == var7;
               int var9;
               if (var1 == 265) {
                  var9 = var8 ? (var5.index - 1 + var5.candidates.size()) % var5.candidates.size() : var5.candidates.size() - 1;
               } else {
                  var9 = var8 ? (var5.index + 1) % var5.candidates.size() : 0;
               }
               return apply(var4, var5, var9);
            }
         } else {
            return false;
         }
      } else if (var0 != null && (var1 == 257 || var1 == 335)) {
         Object var4 = triggerBox(var0);
         if (var4 != null && isFocused(var4)) {
            CommandTabCompleter.State var5 = STATES.get(var4);
            if (var5 != null && var5.candidates != null && !var5.candidates.isEmpty()) {
               var5.candidates = null;
               var5.future = null;
               updateSuggestionSuffix(var4, null);
               return true;
            }
         }
      } else if (var0 != null && var1 == 256) {
         Object var4 = triggerBox(var0);
         if (var4 != null) {
            CommandTabCompleter.State var5 = STATES.get(var4);
            if (var5 != null && var5.candidates != null && !var5.candidates.isEmpty()) {
               var5.candidates = null;
               var5.future = null;
               updateSuggestionSuffix(var4, null);
               return true;
            }
         }
      }
      return false;
   }

   public static void captureTriggerBox(Object var0, String var1, Object var2) {
      if (var0 != null && var2 != null && "gui.settledlands.branch.trigger.hint".equals(var1)) {
         try {
            Field var3 = findField(var0.getClass(), "triggerBox");
            if (var3 != null) {
               var3.setAccessible(true);
               var3.set(var0, var2);
            }
         } catch (Throwable var4) {
         }
      }
   }

   public static void render(Object var0, Object var1, int var2, int var3) {
      if (var0 != null && var1 != null) {
         Object var4 = triggerBox(var0);
         if (var4 != null && isFocused(var4)) {
            Object var5 = null;
            boolean var6 = false;

            try {
               CommandTabCompleter.State var7 = refreshState(var0, var4);
               if (var7 == null) {
                  return;
               }

               List var8 = var7.candidates;
               boolean var9 = var7.future != null && !var7.future.isDone();
               if (var8 != null && !var8.isEmpty() || var9) {
                  Object var10 = readField(var0, "font");
                  CommandTabCompleter.PopupLayout var11 = popupLayout(var0, var4, var10, var8, var9, var7);
                  var5 = invoke(var1, "pose");
                  invoke(var5, "pushPose");
                  var6 = true;
                  invoke(var5, "translate", new Class[]{double.class, double.class, double.class}, 0.0, 0.0, 550.0);
                  invoke(
                     var1,
                     "fill",
                     new Class[]{int.class, int.class, int.class, int.class, int.class},
                     var11.x,
                     var11.y,
                     var11.x + var11.width,
                     var11.y + var11.height,
                     -184088048
                  );
                  invoke(
                     var1,
                     "fill",
                     new Class[]{int.class, int.class, int.class, int.class, int.class},
                     var11.x,
                     var11.y,
                     var11.x + var11.width,
                     var11.y + 1,
                     -13018252
                  );
                  invoke(
                     var1,
                     "fill",
                     new Class[]{int.class, int.class, int.class, int.class, int.class},
                     var11.x,
                     var11.y,
                     var11.x + 1,
                     var11.y + var11.height,
                     -14271667
                  );
                  if (var9) {
                     drawText(var1, var10, "…", var11.x + var11.pad, var11.y + var11.pad, -986122);
                     return;
                  }

                  int var12 = var7.index >= 0 ? var7.index : var7.scrollOffset;

                  for (int var13 = 0; var13 < var11.rows; var13++) {
                     int var14 = var7.scrollOffset + var13;
                     if (var14 >= var8.size()) {
                        break;
                     }

                     int var15 = var11.rowsY + var13 * var11.rowHeight;
                     boolean var16 = var14 == var12;
                     if (var16) {
                        int var17 = var11.scrollbar ? var11.trackX - 3 : var11.x + var11.width - 1;
                        invoke(
                           var1,
                           "fill",
                           new Class[]{int.class, int.class, int.class, int.class, int.class},
                           var11.x + 1,
                           var15 - 1,
                           var17,
                           var15 + var11.rowHeight - 1,
                           -14202268
                        );
                     }

                     String var31 = clip(var10, ((CommandTabCompleter.Candidate)var8.get(var14)).display, var11.textWidth);
                     drawText(var1, var10, var31, var11.x + var11.pad, var15, var16 ? -1 : -1972501);
                  }

                  if (var11.scrollbar) {
                     invoke(
                        var1,
                        "fill",
                        new Class[]{int.class, int.class, int.class, int.class, int.class},
                        var11.trackX,
                        var11.trackY,
                        var11.trackX + var11.trackWidth,
                        var11.trackY + var11.trackHeight,
                        -14998739
                     );
                     invoke(
                        var1,
                        "fill",
                        new Class[]{int.class, int.class, int.class, int.class, int.class},
                        var11.trackX,
                        var11.thumbY,
                        var11.trackX + var11.trackWidth,
                        var11.thumbY + var11.thumbHeight,
                        -8283986
                     );
                  }

                  return;
               }
            } catch (Throwable var29) {
               return;
            } finally {
               if (var6) {
                  try {
                     invoke(var5, "popPose");
                  } catch (Throwable var28) {
                  }
               }
            }
         }
      }
   }

   public static boolean mouseScrolled(Object var0, double var1, double var3, double var5) {
      if (var0 != null && var5 != 0.0) {
         Object var7 = triggerBox(var0);
         if (var7 != null && isFocused(var7)) {
            try {
               CommandTabCompleter.State var8 = refreshState(var0, var7);
               CommandTabCompleter.PopupLayout var9 = activeLayout(var0, var7, var8);
               if (var9 != null && var9.scrollbar && var9.contains(var1, var3)) {
                  int var10 = Math.max(1, (int)Math.round(Math.abs(var5) * 2.0));
                  var8.scrollOffset = clamp(var8.scrollOffset + (var5 > 0.0 ? -var10 : var10), 0, var9.maxScroll);
                  return true;
               } else {
                  return false;
               }
            } catch (Throwable var11) {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean mouseClicked(Object var0, double var1, double var3, int var5) {
      if (var0 != null && var5 == 0) {
         Object var6 = triggerBox(var0);
         if (var6 != null && isFocused(var6)) {
            try {
               CommandTabCompleter.State var7 = refreshState(var0, var6);
               CommandTabCompleter.PopupLayout var8 = activeLayout(var0, var6, var7);
               if (var8 != null && var8.contains(var1, var3)) {
                  if (var8.scrollbar
                     && var1 >= var8.trackX - 2
                     && var1 < var8.trackX + var8.trackWidth + 2
                     && var3 >= var8.trackY
                     && var3 < var8.trackY + var8.trackHeight) {
                     boolean var13 = var3 >= var8.thumbY && var3 < var8.thumbY + var8.thumbHeight;
                     var7.dragging = true;
                     var7.dragGrabOffset = var13 ? (int)var3 - var8.thumbY : var8.thumbHeight / 2;
                     if (!var13) {
                        scrollThumbTo(var7, var8, var3);
                     }

                     return true;
                  } else {
                     List var9 = var7.candidates;
                     if (var9 != null && var3 >= var8.rowsY && var3 < var8.rowsY + var8.rows * var8.rowHeight) {
                        int var10 = ((int)var3 - var8.rowsY) / var8.rowHeight;
                        int var11 = var7.scrollOffset + var10;
                        if (var11 >= 0 && var11 < var9.size()) {
                           apply(var6, var7, var11);
                        }

                        return true;
                     } else {
                        return true;
                     }
                  }
               } else {
                  return false;
               }
            } catch (Throwable var12) {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean mouseDragged(Object var0, double var1, double var3, int var5, double var6, double var8) {
      if (var0 != null && var5 == 0) {
         Object var10 = triggerBox(var0);
         if (var10 == null) {
            return false;
         } else {
            try {
               CommandTabCompleter.State var11 = STATES.get(var10);
               if (var11 != null && var11.dragging) {
                  CommandTabCompleter.PopupLayout var12 = activeLayout(var0, var10, var11);
                  if (var12 != null && var12.scrollbar) {
                     scrollThumbTo(var11, var12, var3);
                     return true;
                  } else {
                     var11.dragging = false;
                     return false;
                  }
               } else {
                  return false;
               }
            } catch (Throwable var13) {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static void mouseReleased(Object var0, double var1, double var3, int var5) {
      if (var0 != null && var5 == 0) {
         Object var6 = triggerBox(var0);
         if (var6 != null) {
            CommandTabCompleter.State var7 = STATES.get(var6);
            if (var7 != null) {
               var7.dragging = false;
               var7.dragGrabOffset = 0;
            }
         }
      }
   }

   private static CommandTabCompleter.PopupLayout activeLayout(Object var0, Object var1, CommandTabCompleter.State var2) throws Exception {
      if (var2 == null) {
         return null;
      } else {
         List var3 = var2.candidates;
         boolean var4 = var2.future != null && !var2.future.isDone();
         if ((var3 == null || var3.isEmpty()) && !var4) {
            return null;
         } else {
            Object var5 = readField(var0, "font");
            return popupLayout(var0, var1, var5, var3, var4, var2);
         }
      }
   }

   private static CommandTabCompleter.PopupLayout popupLayout(
      Object var0, Object var1, Object var2, List<CommandTabCompleter.Candidate> var3, boolean var4, CommandTabCompleter.State var5
   ) {
      int var9 = Math.max(48, intField(var0, "width", 320));
      int var10 = Math.max(32, intField(var0, "height", 240));
      int var11 = intValue(invokeUnchecked(var1, "getX"), 4);
      int var12 = intValue(invokeUnchecked(var1, "getY"), 20);
      int var13 = intValue(invokeUnchecked(var1, "getHeight"), 18);
      int var14 = var12 + var13 + 2;
      int var15 = Math.max(0, var10 - 4 - var14);
      int var16 = Math.max(0, var12 - 2 - 4);
      boolean var17 = var15 >= var16;
      int var18 = var17 ? var15 : var16;
      int var19 = Math.max(1, Math.max(0, var18 - 8) / 12);
      int var20 = Math.max(1, Math.max(0, var10 - 8 - 8) / 12);
      int var21 = var4 ? 1 : (var3 == null ? 0 : var3.size());
      int var22 = Math.max(1, Math.min(var21, Math.min(var19, var20)));
      int var23 = var22 * 12 + 8;
      int var24 = var17 ? var14 : var12 - var23 - 2;
      var24 = clamp(var24, 4, Math.max(4, var10 - var23 - 4));
      boolean var25 = !var4 && var3 != null && var3.size() > var22;
      int var26 = 0;
      if (!var4 && var3 != null) {
         for (CommandTabCompleter.Candidate var28 : var3) {
            var26 = Math.max(var26, textWidth(var2, var28.display));
         }
      }

      int var42 = var25 ? 10 : 0;
      int var43 = var4 ? 96 : Math.max(96, var26 + 8 + var42 + 2);
      var43 = Math.max(48, Math.min(var43, Math.max(48, var9 - 8)));
      int var29 = clamp(var11, 4, Math.max(4, var9 - var43 - 4));
      int var30 = Math.max(1, var43 - 8 - var42 - 2);
      int var31 = var24 + 4;
      byte var32 = 4;
      int var33 = var29 + var43 - 4 - var32;
      int var35 = var22 * 12;
      int var36 = var25 ? var3.size() - var22 : 0;
      var5.scrollOffset = clamp(var5.scrollOffset, 0, var36);
      var5.visibleRows = var22;
      int var37 = var25 ? Math.max(8, var35 * var22 / var3.size()) : 0;
      var37 = Math.min(var35, var37);
      int var38 = Math.max(0, var35 - var37);
      int var39 = var31 + (var36 == 0 ? 0 : var5.scrollOffset * var38 / var36);
      CommandTabCompleter.PopupLayout var40 = new CommandTabCompleter.PopupLayout(
         var29, var24, var43, var23, var22, 12, 4, var30, var31, var25, var33, var31, var32, var35, var39, var37, var36
      );
      var5.layout = var40;
      return var40;
   }

   private static Object invokeUnchecked(Object var0, String var1) {
      try {
         return invoke(var0, var1);
      } catch (Throwable var3) {
         return null;
      }
   }

   private static void scrollThumbTo(CommandTabCompleter.State var0, CommandTabCompleter.PopupLayout var1, double var2) {
      int var4 = var1.trackHeight - var1.thumbHeight;
      if (var4 > 0 && var1.maxScroll > 0) {
         int var5 = clamp((int)Math.round(var2) - var0.dragGrabOffset, var1.trackY, var1.trackY + var4);
         int var6 = var5 - var1.trackY;
         var0.scrollOffset = clamp((int)Math.round((double)var6 * var1.maxScroll / var4), 0, var1.maxScroll);
      } else {
         var0.scrollOffset = 0;
      }
   }

   private static boolean isFocused(Object var0) {
      try {
         Object var1 = invoke(var0, "isFocused");
         return Boolean.TRUE.equals(var1);
      } catch (Throwable var2) {
         return false;
      }
   }

   private static int cursor(Object var0, String var1) {
      try {
         return Math.max(0, Math.min(var1.length(), ((Number)invoke(var0, "getCursorPosition")).intValue()));
      } catch (Throwable var3) {
         try {
            Field f = findField(var0.getClass(), "textField");
            if (f != null) {
               f.setAccessible(true);
               Object tf = f.get(var0);
               if (tf != null) {
                  return Math.max(0, Math.min(var1.length(), ((Number)invoke(tf, "cursor")).intValue()));
               }
            }
         } catch (Throwable ignored) {
         }
         return var1.length();
      }
   }

   private static String stringValue(Object var0, String var1) {
      try {
         Object var2 = invoke(var0, var1);
         return var2 == null ? "" : var2.toString();
      } catch (Throwable var3) {
         return "";
      }
   }

   private static Object triggerBox(Object var0) {
      if (var0 == null) {
         return null;
      }
      String var1 = var0.getClass().getName();
      String var2;
      if (var1.equals("dev.settledlands.npc.client.GuideDialogueScreen")) {
         try {
            Field stepAction = findField(var0.getClass(), "stepActionBox");
            if (stepAction != null) {
               stepAction.setAccessible(true);
               Object box = stepAction.get(var0);
               if (box != null && isFocused(box)) {
                  return box;
               }
            }
         } catch (Throwable ignored) {
         }
         var2 = "setActionBox";
      } else if (var1.equals("dev.settledlands.npc.client.NpcBranchScreen") || var1.equals("dev.settledlands.npc.client.NpcDialogueGraphScreen")) {
         var2 = "triggerBox";
      } else {
         Field f = findField(var0.getClass(), "triggerBox");
         if (f != null) {
            var2 = "triggerBox";
         } else {
            return null;
         }
      }

      try {
         Field var3 = findField(var0.getClass(), var2);
         if (var3 == null) {
            return null;
         } else {
            var3.setAccessible(true);
            return var3.get(var0);
         }
      } catch (Throwable var4) {
         return null;
      }
   }

   private static CommandTabCompleter.State refreshState(Object var0, Object var1) {
      try {
         String var2 = (String)invoke(var1, "getValue");
         int var3 = cursor(var1, var2);
         CommandTabCompleter.State var4 = STATES.get(var1);
         if (var4 != null && var4.candidates != null && var4.lastValue != null && var4.lastValue.equals(var2) && var4.lastCursor == var3) {
            updateSuggestionSuffix(var1, var4);
            return var4;
         } else {
            String var5 = var2 + "\u0000" + var3;
            if (var4 == null || !var5.equals(var4.request)) {
               CommandTabCompleter.Context var6 = context(var2, var3);
               var4 = new CommandTabCompleter.State();
               var4.request = var5;
               var4.context = var6;
               STATES.put(var1, var4);
               if (var6.triggerNameCompletion) {
                  var4.candidates = triggerCandidates(var6);
               } else {
                  var4.future = requestSuggestions(var0, var6.command);
                  if (var4.future == null) {
                     var4.candidates = fallbackCandidates(var6);
                  }
               }
            }

            if (var4.future != null && var4.future.isDone()) {
               var4.candidates = collect(var4.future, var4.context);
               var4.future = null;
            }

            if ((var4.candidates == null || var4.candidates.isEmpty()) && var4.future == null && var4.context != null) {
               var4.candidates = fallbackCandidates(var4.context);
            }

            updateSuggestionSuffix(var1, var4);
            return var4;
         }
      } catch (Throwable var7) {
         return null;
      }
   }

   private static void updateSuggestionSuffix(Object var1, CommandTabCompleter.State var4) {
      if (var1 == null) return;
      try {
         if (var4 != null && var4.candidates != null && !var4.candidates.isEmpty()) {
            int active = var4.index >= 0 && var4.index < var4.candidates.size() ? var4.index : 0;
            CommandTabCompleter.Candidate cand = var4.candidates.get(active);
            String val = stringValue(var1, "getValue");
            int cur = cursor(var1, val);
            if (cur == val.length() && cand.value.startsWith(val)) {
               String suffix = cand.value.substring(val.length());
               invoke(var1, "setSuggestion", new Class[]{String.class}, suffix);
               return;
            }
         }
         invoke(var1, "setSuggestion", new Class[]{String.class}, (String)null);
      } catch (Throwable ignored) {
      }
   }

   private static List<CommandTabCompleter.Candidate> fallbackCandidates(CommandTabCompleter.Context var0) {
      ArrayList<CommandTabCompleter.Candidate> list = new ArrayList<>();
      String cmd = var0.command.toLowerCase(Locale.ROOT);
      String[] defaults = new String[]{"go ", "play ", "heal ", "give @p ", "playsound ", "say ", "tp @p ", "tp @npc ", "effect give @p ", "effect give @npc ", "title @p title "};
      for (String d : defaults) {
         if (d.toLowerCase(Locale.ROOT).startsWith(cmd)) {
            String full = var0.prefix + d;
            String val = var0.value.substring(0, var0.segmentStart) + full + var0.after;
            list.add(new CommandTabCompleter.Candidate(val, var0.segmentStart + full.length(), full));
         }
      }
      if (cmd.endsWith("@") || cmd.endsWith("@n") || cmd.endsWith("@np")) {
         int lastAt = var0.command.lastIndexOf('@');
         String beforeAt = lastAt >= 0 ? var0.command.substring(0, lastAt) : var0.command;
         String full = var0.prefix + beforeAt + "@npc ";
         String val = var0.value.substring(0, var0.segmentStart) + full + var0.after;
         list.add(0, new CommandTabCompleter.Candidate(val, var0.segmentStart + full.length(), "@npc (этот NPC)"));
      }
      if (var0.prefix.isEmpty() && ("trigger:".startsWith(cmd) || "trigger".startsWith(cmd))) {
         String full = "trigger:";
         String val = var0.value.substring(0, var0.segmentStart) + full + var0.after;
         list.add(new CommandTabCompleter.Candidate(val, var0.segmentStart + full.length(), full));
      }
      return list;
   }

   private static boolean apply(Object var0, CommandTabCompleter.State var1, int var2) {
      if (var1.candidates != null && !var1.candidates.isEmpty()) {
         CommandTabCompleter.Candidate var3 = var1.candidates.get(var2);

         try {
            invoke(var0, "setValue", new Class[]{String.class}, var3.value);
            try {
               invoke(var0, "setCursorPosition", new Class[]{int.class}, var3.cursor);
            } catch (Throwable t) {
               try {
                  Field f = findField(var0.getClass(), "textField");
                  if (f != null) {
                     f.setAccessible(true);
                     Object tf = f.get(var0);
                     if (tf != null) {
                        Class<?> whenceCls = Class.forName("net.minecraft.client.gui.components.Whence");
                        Object absolute = Enum.valueOf((Class<Enum>)whenceCls, "ABSOLUTE");
                        invoke(tf, "seekCursor", new Class[]{whenceCls, int.class}, absolute, var3.cursor);
                     }
                  }
               } catch (Throwable ignored) {
               }
            }
            var1.index = var2;
            var1.lastValue = var3.value;
            var1.lastCursor = var3.cursor;
            var1.dragging = false;
            int var4 = Math.max(1, var1.visibleRows > 0 ? var1.visibleRows : 7);
            if (var2 < var1.scrollOffset) {
               var1.scrollOffset = var2;
            } else if (var2 >= var1.scrollOffset + var4) {
               var1.scrollOffset = var2 - var4 + 1;
            }

            updateSuggestionSuffix(var0, var1);
            return true;
         } catch (Throwable var5) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static CommandTabCompleter.Context context(String var0, int var1) {
      int var2 = var0.lastIndexOf(9246, Math.max(-1, var1 - 1)) + 1;
      String var3 = var0.substring(var2, var1);
      int var4 = 0;

      while (var4 < var3.length() && Character.isWhitespace(var3.charAt(var4))) {
         var4++;
      }

      String var5 = var3.substring(0, var4);
      String var6 = var3.substring(var4);
      if (var6.regionMatches(true, 0, "trigger:", 0, 8)) {
         String var7 = var6.substring(8);
         return new CommandTabCompleter.Context(var0, var1, var2, var5 + "trigger:", var7, var0.substring(var1), true);
      } else {
         if (var6.regionMatches(true, 0, "cmd:", 0, 4)) {
            var5 = var5 + var6.substring(0, 4);

            for (var6 = var6.substring(4); !var6.isEmpty() && Character.isWhitespace(var6.charAt(0)); var6 = var6.substring(1)) {
               var5 = var5 + var6.substring(0, 1);
            }
         }

         if (var6.startsWith("/")) {
            var5 = var5 + "/";
            var6 = var6.substring(1);
         }

         while (!var6.isEmpty() && Character.isWhitespace(var6.charAt(0))) {
            var5 = var5 + var6.substring(0, 1);
            var6 = var6.substring(1);
         }

         return new CommandTabCompleter.Context(var0, var1, var2, var5, var6, var0.substring(var1), false);
      }
   }

   private static List<CommandTabCompleter.Candidate> triggerCandidates(CommandTabCompleter.Context var0) {
      ArrayList var1 = new ArrayList();
      String var2 = var0.command.toLowerCase(Locale.ROOT);

      for (String var6 : TRIGGERS) {
         if (var6.startsWith(var2)) {
            String var7 = var0.prefix + var6;
            String var8 = var0.value.substring(0, var0.segmentStart) + var7 + var0.after;
            var1.add(new CommandTabCompleter.Candidate(var8, var0.segmentStart + var7.length(), var7));
         }
      }

      return var1;
   }

   private static Future<?> requestSuggestions(Object var0, String var1) {
      try {
         String query = sanitizeNpcSelectors(var1);
         ClassLoader var2 = var0.getClass().getClassLoader();
         Class var3 = Class.forName("net.minecraft.client.Minecraft", true, var2);
         Object var4 = var3.getMethod("getInstance").invoke(null);
         Object var5 = invoke(var4, "getConnection");
         if (var5 == null) {
            return null;
         } else {
            Object var6 = invoke(var5, "getCommands");
            Object var7 = invoke(var5, "getSuggestionsProvider");
            if (var6 != null && var7 != null) {
               Class var8 = Class.forName("com.mojang.brigadier.StringReader", true, var2);
               Constructor var9 = var8.getConstructor(String.class);
               Object var10 = var9.newInstance(query);
               Object var11 = null;

               for (Method var15 : var6.getClass().getMethods()) {
                  if (var15.getName().equals("parse") && var15.getParameterCount() == 2) {
                     Class[] var16 = var15.getParameterTypes();
                     if (var16[0].isAssignableFrom(var8) && var16[1].isInstance(var7)) {
                        var11 = var15.invoke(var6, var10, var7);
                        break;
                     }
                  }
               }

               if (var11 == null) {
                  return null;
               } else {
                  Object var18 = null;

                  for (Method var22 : var6.getClass().getMethods()) {
                     if (var22.getName().equals("getCompletionSuggestions")) {
                        if (var22.getParameterCount() == 1 && var22.getParameterTypes()[0].isInstance(var11)) {
                           var18 = var22.invoke(var6, var11);
                           break;
                        }

                        if (var22.getParameterCount() == 2 && var22.getParameterTypes()[0].isInstance(var11) && var22.getParameterTypes()[1] == int.class) {
                           var18 = var22.invoke(var6, var11, query.length());
                           break;
                        }
                     }
                  }

                  return var18 instanceof Future ? (Future)var18 : null;
               }
            } else {
               return null;
            }
         }
      } catch (Throwable var17) {
         return null;
      }
   }

   private static List<CommandTabCompleter.Candidate> collect(Future<?> var0, CommandTabCompleter.Context var1) {
      ArrayList var2 = new ArrayList();

      try {
         Object var3 = var0.get();
         Object var4 = invoke(var3, "getList");
         if (!(var4 instanceof Iterable)) {
            return var2;
         }

         for (Object var6 : (Iterable)var4) {
            Object var7 = invoke(var6, "getText");
            if (var7 instanceof String) {
               String var8 = var1.prefix + tokenPrefix(var1.command) + (String)var7;
               String var9 = var1.value.substring(0, var1.segmentStart) + var8 + var1.after;
               int var10 = var1.segmentStart + var8.length();
               if (var2.isEmpty() || !((CommandTabCompleter.Candidate)var2.get(var2.size() - 1)).value.equals(var9)) {
                  var2.add(new CommandTabCompleter.Candidate(var9, var10, var8));
                  if (var2.size() >= 64) {
                     break;
                  }
               }
            }
         }

         boolean hasSelector = false;
         for (int i = 0; i < var2.size(); i++) {
            CommandTabCompleter.Candidate cand = (CommandTabCompleter.Candidate) var2.get(i);
            if (cand.display != null && (cand.display.endsWith("@s") || cand.display.endsWith("@p") || cand.display.endsWith("@a") || cand.display.endsWith("@e") || cand.display.endsWith("@r"))) {
               hasSelector = true;
               break;
            }
         }
         String trimmedCmd = var1.command.trim();
         if (hasSelector || trimmedCmd.endsWith("@") || trimmedCmd.endsWith("@n") || trimmedCmd.endsWith("@np")) {
            int atIdx = var1.command.lastIndexOf('@');
            String prefixBeforeAt = atIdx >= 0 ? var1.command.substring(0, atIdx) : var1.command;
            String atNpc = prefixBeforeAt + "@npc ";
            String full = var1.prefix + atNpc;
            String val = var1.value.substring(0, var1.segmentStart) + full + var1.after;
            var2.add(0, new CommandTabCompleter.Candidate(val, var1.segmentStart + full.length(), "@npc (этот NPC)"));
         }
         injectArgHints(var1, var2);
      } catch (Throwable var11) {
      }

      return var2;
   }

   private static void injectArgHints(CommandTabCompleter.Context var1, List<CommandTabCompleter.Candidate> out) {
      String cmd = var1.command;
      boolean trailing = cmd.length() > 0 && Character.isWhitespace(cmd.charAt(cmd.length() - 1));
      String[] toks = cmd.trim().split("\\s+");
      if (toks.length == 0 || toks[0].isEmpty()) return;
      String head = toks[0].toLowerCase(Locale.ROOT);
      int argIndex = toks.length + (trailing ? 0 : -1);
      String sub = toks.length > 1 ? toks[1].toLowerCase(Locale.ROOT) : "";
      String hint = null;
      if ("effect".equals(head) && "give".equals(sub)) {
         if (argIndex == 4) hint = "<секунды|infinite> [усилитель] [скрыть-частицы]";
         else if (argIndex == 5) hint = "<усилитель 0..4> [скрыть-частицы]";
         else if (argIndex == 6) hint = "<true|false>";
      } else if ("give".equals(head)) {
         if (argIndex == 3) hint = "<количество 1..64>";
      } else if ("summon".equals(head)) {
         if (argIndex == 2) hint = "[x] [y] [z] [{NBT}]";
         else if (argIndex == 3) hint = "[y] [z] [{NBT}]";
         else if (argIndex == 4) hint = "[z] [{NBT}]";
         else if (argIndex == 5) hint = "{NBT}";
      } else if ("tp".equals(head) || "teleport".equals(head)) {
         if (argIndex == 2) hint = "[x] [y] [z]";
         else if (argIndex == 3) hint = "[y] [z]";
         else if (argIndex == 4) hint = "[z]";
      } else if ("playsound".equals(head)) {
         if (argIndex == 4) hint = "[x] [y] [z] [громкость] [высота]";
         else if (argIndex == 5) hint = "[y] [z] [громкость] [высота]";
         else if (argIndex == 6) hint = "[z] [громкость] [высота]";
         else if (argIndex == 7) hint = "<громкость 0.1..2>";
         else if (argIndex == 8) hint = "<высота 0.5..2>";
         else if (argIndex == 9) hint = "<мин. громкость 0..1>";
      } else if ("enchant".equals(head)) {
         if (argIndex == 3) hint = "<уровень>";
      } else if (("experience".equals(head) || "xp".equals(head)) && "add".equals(sub)) {
         if (argIndex == 3) hint = "<количество>";
      }
      if (hint == null) return;
      // Информационная строка: выбор её НЕ меняет текст команды (value/cursor остаются как есть).
      out.add(new CommandTabCompleter.Candidate(var1.value, var1.cursor, var1.prefix + cmd + (trailing ? "" : " ") + hint));
   }

   private static String sanitizeNpcSelectors(String var0) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("@npc(\\[[^\\]]*\\])?").matcher(var0);
      StringBuffer out = new StringBuffer();
      while (m.find()) m.appendReplacement(out, "@s");
      m.appendTail(out);
      return out.toString();
   }

   private static String tokenPrefix(String var0) {
      int i = var0.length();
      while (i > 0 && !Character.isWhitespace(var0.charAt(i - 1))) i--;
      return var0.substring(0, i);
   }

   private static void drawText(Object var0, Object var1, String var2, int var3, int var4, int var5) throws Exception {
      invoke(var0, "drawString", new Class[]{var1.getClass(), String.class, int.class, int.class, int.class, boolean.class}, var1, var2, var3, var4, var5, true);
   }

   private static int textWidth(Object var0, String var1) {
      try {
         return ((Number)invoke(var0, "width", new Class[]{String.class}, var1)).intValue();
      } catch (Throwable var3) {
         return var1 == null ? 0 : var1.length() * 6;
      }
   }

   private static String clip(Object var0, String var1, int var2) {
      if (textWidth(var0, var1) <= var2) {
         return var1;
      } else {
         try {
            String var3 = (String)invoke(var0, "plainSubstrByWidth", new Class[]{String.class, int.class}, var1, Math.max(1, var2 - 8));
            return var3 + "…";
         } catch (Throwable var5) {
            int var4 = Math.max(1, var2 / 6 - 2);
            return var1.substring(0, Math.min(var4, var1.length())) + "…";
         }
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var0, var2));
   }

   private static int intValue(Object var0, int var1) {
      return var0 instanceof Number ? ((Number)var0).intValue() : var1;
   }

   private static int intField(Object var0, String var1, int var2) {
      try {
         return ((Number)readField(var0, var1)).intValue();
      } catch (Throwable var4) {
         return var2;
      }
   }

   private static Object readField(Object var0, String var1) throws Exception {
      Field var2 = findField(var0.getClass(), var1);
      if (var2 == null) {
         throw new NoSuchFieldException(var1);
      } else {
         var2.setAccessible(true);
         return var2.get(var0);
      }
   }

   private static Object invoke(Object var0, String var1) throws Exception {
      Method var2 = findMethod(var0.getClass(), var1, new Class[0]);
      if (var2 == null) {
         throw new NoSuchMethodException(var1);
      } else {
         var2.setAccessible(true);
         return var2.invoke(var0);
      }
   }

   private static Object invoke(Object var0, String var1, Class<?>[] var2, Object... var3) throws Exception {
      Method var4 = findMethod(var0.getClass(), var1, var2);
      if (var4 == null) {
         throw new NoSuchMethodException(var1);
      } else {
         var4.setAccessible(true);
         return var4.invoke(var0, var3);
      }
   }

   private static Method findMethod(Class<?> var0, String var1, Class<?>[] var2) {
      for (Class var3 = var0; var3 != null; var3 = var3.getSuperclass()) {
         try {
            return var3.getDeclaredMethod(var1, var2);
         } catch (NoSuchMethodException var6) {
         }
      }

      try {
         return var0.getMethod(var1, var2);
      } catch (NoSuchMethodException var5) {
         return null;
      }
   }

   private static Field findField(Class<?> var0, String var1) {
      for (Class var2 = var0; var2 != null; var2 = var2.getSuperclass()) {
         try {
            return var2.getDeclaredField(var1);
         } catch (NoSuchFieldException var4) {
         }
      }

      return null;
   }

   private static final class Candidate {
      final String value;
      final int cursor;
      final String display;

      Candidate(String var1, int var2, String var3) {
         this.value = var1;
         this.cursor = var2;
         this.display = var3;
      }
   }

   private static final class Context {
      final String value;
      final int cursor;
      final int segmentStart;
      final String prefix;
      final String command;
      final String after;
      final boolean triggerNameCompletion;

      Context(String var1, int var2, int var3, String var4, String var5, String var6, boolean var7) {
         this.value = var1;
         this.cursor = var2;
         this.segmentStart = var3;
         this.prefix = var4;
         this.command = var5;
         this.after = var6;
         this.triggerNameCompletion = var7;
      }
   }

   private static final class PopupLayout {
      final int x;
      final int y;
      final int width;
      final int height;
      final int rows;
      final int rowHeight;
      final int pad;
      final int textWidth;
      final int rowsY;
      final boolean scrollbar;
      final int trackX;
      final int trackY;
      final int trackWidth;
      final int trackHeight;
      final int thumbY;
      final int thumbHeight;
      final int maxScroll;

      PopupLayout(
         int var1,
         int var2,
         int var3,
         int var4,
         int var5,
         int var6,
         int var7,
         int var8,
         int var9,
         boolean var10,
         int var11,
         int var12,
         int var13,
         int var14,
         int var15,
         int var16,
         int var17
      ) {
         this.x = var1;
         this.y = var2;
         this.width = var3;
         this.height = var4;
         this.rows = var5;
         this.rowHeight = var6;
         this.pad = var7;
         this.textWidth = var8;
         this.rowsY = var9;
         this.scrollbar = var10;
         this.trackX = var11;
         this.trackY = var12;
         this.trackWidth = var13;
         this.trackHeight = var14;
         this.thumbY = var15;
         this.thumbHeight = var16;
         this.maxScroll = var17;
      }

      boolean contains(double var1, double var3) {
         return var1 >= this.x && var1 < this.x + this.width && var3 >= this.y && var3 < this.y + this.height;
      }
   }

   private static final class State {
      String request;
      CommandTabCompleter.Context context;
      Future<?> future;
      List<CommandTabCompleter.Candidate> candidates;
      int index = -1;
      String lastValue;
      int lastCursor = -1;
      int scrollOffset;
      int visibleRows;
      boolean dragging;
      int dragGrabOffset;
      CommandTabCompleter.PopupLayout layout;
   }
}
