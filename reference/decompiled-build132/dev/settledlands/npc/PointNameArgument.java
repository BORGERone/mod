package dev.settledlands.npc;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.server.MinecraftServer;
import org.slf4j.LoggerFactory;

public final class PointNameArgument implements ArgumentType<String> {
   public static final PointNameArgument INSTANCE = new PointNameArgument();
   public static final String ID = "point_name";
   public static final ArgumentTypeInfo<PointNameArgument, ?> INFO = SingletonArgumentInfo.contextFree(PointNameArgument::pointName);

   private PointNameArgument() {
   }

   public static PointNameArgument pointName() {
      return INSTANCE;
   }

   public static String get(CommandContext<?> var0, String var1) {
      return (String)var0.getArgument(var1, String.class);
   }

   public String parse(StringReader var1) throws CommandSyntaxException {
      if (!var1.canRead() || var1.peek() != '"' && var1.peek() != '\'') {
         int var2 = var1.getCursor();

         while (var1.canRead() && var1.peek() != ' ') {
            var1.skip();
         }

         String var3 = var1.getString().substring(var2, var1.getCursor());
         if (var3.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedStartOfQuote().createWithContext(var1);
         } else {
            return var3;
         }
      } else {
         return var1.readQuotedString();
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> var1, SuggestionsBuilder var2) {
      if (var1.getSource() instanceof CommandSourceStack var3) {
         MinecraftServer var8 = var3.getServer();
         if (var8 != null) {
            String var5 = var2.getRemainingLowerCase();

            for (String var7 : NpcPoints.get(var8).names()) {
               if (var7.toLowerCase(Locale.ROOT).startsWith(var5)) {
                  var2.suggest(var7);
               }
            }
         }
      }

      return var2.buildFuture();
   }

   public Collection<String> getExamples() {
      return List.of("одолжение", "открытие");
   }

   public static void registerClassMapping() {
      try {
         Class var0 = Class.forName("net.minecraft.commands.synchronization.ArgumentTypeInfos");
         var0.getMethod("registerByClass", Class.class, ArgumentTypeInfo.class).invoke(null, PointNameArgument.class, INFO);
      } catch (ReflectiveOperationException var1) {
         LoggerFactory.getLogger("settledlands").warn("Settled Lands: не удалось зарегистрировать разборщик названия очков ({})", var1.toString());
      }
   }
}
