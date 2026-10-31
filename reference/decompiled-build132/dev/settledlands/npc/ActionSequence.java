package dev.settledlands.npc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ActionSequence {
   private static volatile Method runner;

   private ActionSequence() {
   }

   public static String first(String var0) {
      if (var0 == null) {
         return "";
      } else {
         int var1 = var0.indexOf(9246);
         String var2 = var1 < 0 ? var0 : var0.substring(0, var1);
         return var2.trim();
      }
   }

   public static String addSeparator(String var0) {
      if (var0 != null && !var0.trim().isEmpty()) {
         int var1 = var0.length();

         while (var1 > 0 && Character.isWhitespace(var0.charAt(var1 - 1))) {
            var1--;
         }

         String var2 = var0.substring(0, var1);
         return var2.endsWith("␞") ? var2 + " " : var2 + " ␞ ";
      } else {
         return var0 == null ? "" : var0;
      }
   }

   public static boolean runAll(Object var0, Object var1, int var2, String var3) {
      boolean var4 = false;

      for (String var6 : split(var3)) {
         var4 = invoke(var0, var1, var2, var6) || var4;
      }

      return var4;
   }

   public static void runExtras(Object var0, Object var1, int var2, String var3) {
      List var4 = split(var3);

      for (int var5 = 1; var5 < var4.size(); var5++) {
         invoke(var0, var1, var2, (String)var4.get(var5));
      }
   }

   private static List<String> split(String var0) {
      ArrayList var1 = new ArrayList();
      if (var0 != null && !var0.isEmpty()) {
         int var2 = 0;

         for (int var3 = 0; var3 <= var0.length(); var3++) {
            if (var3 == var0.length() || var0.charAt(var3) == 9246) {
               String var4 = var0.substring(var2, var3).trim();
               if (!var4.isEmpty()) {
                  var1.add(var4);
               }

               var2 = var3 + 1;
            }
         }

         return var1;
      } else {
         return var1;
      }
   }

   private static boolean invoke(Object var0, Object var1, int var2, String var3) {
      if (var0 != null && var3 != null && !var3.isEmpty()) {
         try {
            Method var4 = runner;
            if (var4 == null) {
               synchronized (ActionSequence.class) {
                  var4 = runner;
                  if (var4 == null) {
                     Class var6 = Class.forName("dev.settledlands.npc.NpcActions", true, ActionSequence.class.getClassLoader());

                     for (Method var10 : var6.getDeclaredMethods()) {
                        Class[] var11 = var10.getParameterTypes();
                        if (var10.getName().equals("runSingle") && var11.length == 4 && var11[2] == int.class && var11[3] == String.class) {
                           var10.setAccessible(true);
                           var4 = var10;
                           runner = var10;
                           break;
                        }
                     }
                  }
               }
            }

            return var4 != null && Boolean.TRUE.equals(var4.invoke(null, var0, var1, var2, var3));
         } catch (Throwable var14) {
            return false;
         }
      } else {
         return false;
      }
   }
}
