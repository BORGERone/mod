package dev.settledlands.npc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

public final class NpcPointsCommand {
   private static final UUID SHARED = new UUID(0L, 0L);

   private NpcPointsCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("points")
            .requires(source -> source != null && source.hasPermission(2))
            .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
            .then(
               Commands.literal("list")
                  .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                  .then(
                     Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                  )
            )
            .then(
               Commands.literal("add")
                  .then(
                     Commands.argument("name", PointNameArgument.pointName())
                        .then(
                           Commands.argument("count", IntegerArgumentType.integer(-1000000, 1000000))
                              .executes(ctx -> change(ctx.getSource(), ctx.getSource().getPlayerOrException(), ctx, false, true))
                              .then(
                                 Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> change(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ctx, false, true))
                              )
                        )
                  )
            )
            .then(
               Commands.literal("set")
                  .then(
                     Commands.argument("name", PointNameArgument.pointName())
                        .then(
                           Commands.argument("count", IntegerArgumentType.integer(0, 1000000))
                              .executes(ctx -> change(ctx.getSource(), ctx.getSource().getPlayerOrException(), ctx, false, false))
                              .then(
                                 Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> change(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ctx, false, false))
                              )
                        )
                  )
            )
            .then(
               Commands.literal("shared")
                  .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                  .then(
                     Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                  )
                  .then(
                     Commands.literal("add")
                        .then(
                           Commands.argument("name", PointNameArgument.pointName())
                              .then(
                                 Commands.argument("count", IntegerArgumentType.integer(-1000000, 1000000))
                                    .executes(ctx -> change(ctx.getSource(), ctx.getSource().getPlayerOrException(), ctx, true, true))
                              )
                        )
                  )
                  .then(
                     Commands.literal("set")
                        .then(
                           Commands.argument("name", PointNameArgument.pointName())
                              .then(
                                 Commands.argument("count", IntegerArgumentType.integer(0, 1000000))
                                    .executes(ctx -> change(ctx.getSource(), ctx.getSource().getPlayerOrException(), ctx, true, false))
                              )
                        )
                  )
            )
      );
   }

   public static void registerPointList(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("point")
            .requires(source -> source != null && source.hasPermission(2))
            .executes(ctx -> listAll(ctx.getSource()))
      );
   }

   private static int listAll(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      NpcPoints points = NpcPoints.get(server);
      List<UUID> players = points.playersWithPoints();
      source.sendSuccess(() -> Component.translatable("message.settledlands.points.players"), false);
      if (players.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("message.settledlands.points.noneany"), false);
      }

      for (UUID uuid : players) {
         String name = nameOf(server, uuid);
         String desc = NpcPoints.describe(points.personalPoints(uuid));
         source.sendSuccess(() -> Component.translatable("message.settledlands.points.line", name, desc), false);
      }

      Map<String, Integer> shared = points.sharedPoints();
      source.sendSuccess(() -> Component.translatable("message.settledlands.points.sharedhead"), false);
      if (shared.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("message.settledlands.points.noshared"), false);
      } else {
         String desc = NpcPoints.describe(shared);
         source.sendSuccess(() -> Component.literal(desc), false);
      }

      return players.size();
   }

   static String nameOf(MinecraftServer server, UUID uuid) {
      ServerPlayer player = server.getPlayerList().getPlayer(uuid);
      if (player != null) {
         return player.getName().getString();
      } else {
         GameProfileCache cache = server.getProfileCache();
         if (cache != null) {
            Optional<GameProfile> profile = cache.get(uuid);
            if (profile.isPresent() && profile.get().getName() != null) {
               return profile.get().getName();
            }
         }
         return uuid.toString().substring(0, 8);
      }
   }

   public static void registerAdd(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("add")
            .requires(source -> source != null && source.hasPermission(2))
            .executes(ctx -> help(ctx.getSource()))
            .then(pointBranch("point", false))
            .then(pointBranch("-point", true))
      );
      dispatcher.register(
         Commands.literal("addpoint")
            .requires(source -> source != null && source.hasPermission(2))
            .executes(ctx -> help(ctx.getSource()))
            .then(buildPointArgs(false))
      );
      dispatcher.register(
         Commands.literal("add-point")
            .requires(source -> source != null && source.hasPermission(2))
            .executes(ctx -> help(ctx.getSource()))
            .then(buildPointArgs(true))
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> pointBranch(String literal, boolean subtract) {
      return Commands.literal(literal).then(buildPointArgs(subtract));
   }

   private static RequiredArgumentBuilder<CommandSourceStack, ?> buildPointArgs(boolean subtract) {
      return Commands.argument("name", PointNameArgument.pointName())
         .executes(ctx -> grant(ctx, null, false, 1, subtract))
         .then(Commands.literal("all").executes(ctx -> grant(ctx, null, true, 1, subtract)))
         .then(
            Commands.argument("count", IntegerArgumentType.integer(-1000000, 1000000))
               .executes(ctx -> grant(ctx, null, false, IntegerArgumentType.getInteger(ctx, "count"), subtract))
               .then(Commands.literal("all").executes(ctx -> grant(ctx, null, true, IntegerArgumentType.getInteger(ctx, "count"), subtract)))
               .then(
                  Commands.argument("player", EntityArgument.player())
                     .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "player"), false, IntegerArgumentType.getInteger(ctx, "count"), subtract))
               )
         )
         .then(
            Commands.argument("player", EntityArgument.player())
               .executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "player"), false, 1, subtract))
         );
   }

   private static int grant(CommandContext<CommandSourceStack> context, ServerPlayer player, boolean shared, int count, boolean subtract) {
      CommandSourceStack source = context.getSource();
      String name = NpcPoints.cleanName(PointNameArgument.get(context, "name"));
      if (name.isEmpty()) {
         source.sendFailure(Component.translatable("message.settledlands.points.badname"));
         return 0;
      }
      ServerPlayer caller = source.getEntity() instanceof ServerPlayer sp ? sp : null;
      if (!shared && player == null && caller == null) {
         source.sendFailure(Component.translatable("message.settledlands.points.noplayer"));
         return 0;
      }
      ServerPlayer targetPlayer = player != null ? player : caller;
      UUID targetUuid = !shared && targetPlayer != null ? targetPlayer.getUUID() : SHARED;
      NpcPoints points = NpcPoints.get(source.getServer());
      int delta = subtract ? -count : count;
      int after = points.add(targetUuid, name, shared, delta);
      if (shared) {
         source.sendSuccess(() -> Component.translatable("message.settledlands.points.shared", name, after), false);
      } else {
         source.sendSuccess(
            () -> Component.translatable("message.settledlands.points.own", name, targetPlayer.getName().getString(), after), false
         );
      }
      return after;
   }

   private static int help(CommandSourceStack source) {
      source.sendSuccess(() -> Component.translatable("message.settledlands.points.addhelp"), false);
      return 0;
   }

   private static int list(CommandSourceStack source, ServerPlayer player) {
      List<String> lines = NpcPoints.get(source.getServer()).lines(player.getUUID());
      if (lines.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("message.settledlands.points.none", player.getName().getString()), false);
         return 0;
      } else {
         source.sendSuccess(() -> Component.translatable("message.settledlands.points.list", player.getName().getString()), false);
         for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
         }
         return lines.size();
      }
   }

   private static int change(CommandSourceStack source, ServerPlayer player, CommandContext<CommandSourceStack> context, boolean shared, boolean isAdd) {
      String name = NpcPoints.cleanName(PointNameArgument.get(context, "name"));
      int count = IntegerArgumentType.getInteger(context, "count");
      if (name.isEmpty()) {
         source.sendFailure(Component.translatable("message.settledlands.points.badname"));
         return 0;
      } else {
         NpcPoints points = NpcPoints.get(source.getServer());
         int after;
         if (isAdd) {
            after = points.add(player.getUUID(), name, shared, count);
         } else {
            points.set(player.getUUID(), name, shared, count);
            after = points.get(player.getUUID(), name, shared);
         }

         int result = after;
         source.sendSuccess(
            () -> Component.translatable(
               shared ? "message.settledlands.points.shared" : "message.settledlands.points.own", name, player.getName().getString(), result
            ),
            false
         );
         return after;
      }
   }
}
