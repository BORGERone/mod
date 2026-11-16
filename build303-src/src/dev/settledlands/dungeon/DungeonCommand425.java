package dev.settledlands.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.settledlands.npc.BlockTrigger351;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Build 425: {@code /settledlands dungeon} commands for testing, entering, creating grid instances,
 * listing and leaving the «Адский данж» dimension.
 */
@EventBusSubscriber(modid = "settledlands")
public final class DungeonCommand425 {

    private DungeonCommand425() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (event == null || event.getDispatcher() == null) return;
        try {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("settledlands")
                    .then(Commands.literal("dungeon")
                            .then(Commands.literal("enter")
                                    .requires(src -> src.hasPermission(2))
                                    .executes(ctx -> enter(ctx, DungeonData425.DEFAULT_INSTANCE_ID))
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> enter(ctx, StringArgumentType.getString(ctx, "id")))))
                            .then(Commands.literal("new")
                                    .requires(src -> src.hasPermission(2))
                                    .executes(ctx -> createNew(ctx, ""))
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> createNew(ctx, StringArgumentType.getString(ctx, "id")))))
                            .then(Commands.literal("leave")
                                    .executes(DungeonCommand425::leave))
                            .then(Commands.literal("info")
                                    .executes(DungeonCommand425::info))
                            .then(Commands.literal("list")
                                    .requires(src -> src.hasPermission(2))
                                    .executes(DungeonCommand425::list)));
            event.getDispatcher().register(root);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
        }
    }

    private static int enter(CommandContext<CommandSourceStack> ctx, String id) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        boolean ok = DungeonManager425.enterDungeon(player, id);
        if (ok) {
            DungeonData425.Instance inst = DungeonData425.get(player.getServer()).getInstance(id);
            String coords = inst == null ? "0, 0, 0"
                    : inst.originX + ", " + inst.originY + ", " + inst.originZ;
            tell(player, "данж: вход в '" + DungeonData425.normalizeInstanceId(id) + "' (" + coords + ")");
        }
        return ok ? 1 : 0;
    }

    private static int createNew(CommandContext<CommandSourceStack> ctx, String requestedId) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        boolean ok = DungeonManager425.createAndEnterNewInstance(player, requestedId);
        if (ok) {
            tell(player, "данж: создан новый инстанс в сетке");
        }
        return ok ? 1 : 0;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        boolean ok = DungeonManager425.leaveDungeon(player);
        tell(player, ok ? "данж: выход выполнен" : "данж: не удалось вернуть игрока");
        return ok ? 1 : 0;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        if (ctx == null || ctx.getSource() == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        ServerLevel dungeon = DungeonManager425.dungeonLevel(server);
        DungeonData425 data = DungeonData425.get(server);
        String msg = "данж: измерение "
                + (dungeon == null ? "НЕ загружено (нужен новый мир)" : "готово")
                + ", инстансов в сетке: " + data.instanceCount()
                + ", шаг сетки: " + DungeonData425.GRID_SPACING;
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (ctx == null || ctx.getSource() == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        DungeonData425 data = DungeonData425.get(server);
        if (data.instanceCount() == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("данж: инстансов пока нет"), false);
            return 1;
        }
        for (DungeonData425.Instance inst : data.allInstances()) {
            String line = "  #" + inst.index + " " + inst.id + " @ ("
                    + inst.originX + ", " + inst.originY + ", " + inst.originZ + ")"
                    + (inst.placed ? " [placed]" : " [pending]");
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        if (ctx == null || ctx.getSource() == null) return null;
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void tell(ServerPlayer player, String line) {
        if (player != null && line != null) {
            player.displayClientMessage(Component.literal(line), false);
        }
    }
}
