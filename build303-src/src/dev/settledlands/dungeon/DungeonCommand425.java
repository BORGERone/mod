package dev.settledlands.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * Build 425 / 427: {@code /settledlands dungeon} commands for entering, creating grid instances,
 * listing progress, reloading external {@code .bp} folders, and leaving «Адский данж».
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
                                    .executes(DungeonCommand425::enterDefault)
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
                                    .executes(DungeonCommand425::list))
                            .then(Commands.literal("reload")
                                    .requires(src -> src.hasPermission(2))
                                    .executes(DungeonCommand425::reload)));
            event.getDispatcher().register(root);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
        }
    }

    private static int enterDefault(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        DungeonData425.Instance inst = DungeonManager425.enterUniqueOrDefault(player);
        if (inst != null) {
            String coords = inst.originX + ", " + inst.originY + ", " + inst.originZ;
            String rooms = inst.pieces.isEmpty() ? "" : (", комнат в плане: " + inst.pieces.size());
            tell(player, "данж: вход в '" + inst.id + "' (" + coords + ")" + rooms);
            return 1;
        }
        return 0;
    }

    private static int enter(CommandContext<CommandSourceStack> ctx, String id) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        boolean ok = DungeonManager425.enterDungeon(player, id);
        if (ok) {
            DungeonData425.Instance inst = DungeonData425.get(player.getServer()).getInstance(id);
            String coords = inst == null ? "0, 0, 0"
                    : inst.originX + ", " + inst.originY + ", " + inst.originZ;
            String rooms = inst == null || inst.pieces.isEmpty() ? ""
                    : (", комнат в плане: " + inst.pieces.size());
            tell(player, "данж: вход в '" + DungeonData425.normalizeInstanceId(id)
                    + "' (" + coords + ")" + rooms);
        }
        return ok ? 1 : 0;
    }

    private static int createNew(CommandContext<CommandSourceStack> ctx, String requestedId) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        DungeonData425.Instance inst = DungeonManager425.createAndEnterUniqueInstance(player, requestedId);
        if (inst != null) {
            String coords = inst.originX + ", " + inst.originY + ", " + inst.originZ;
            String rooms = inst.pieces.isEmpty() ? "" : (", комнат в плане: " + inst.pieces.size());
            tell(player, "данж: создан новый инстанс '" + inst.id + "' (" + coords + ")" + rooms);
            return 1;
        }
        return 0;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) return 0;
        boolean ok = DungeonManager425.leaveDungeon(player);
        tell(player, ok ? "данж: выход выполнен" : "данж: не удалось вернуть игрока");
        return ok ? 1 : 0;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        if (ctx == null || ctx.getSource() == null) return 0;
        DungeonBlueprints427.Library lib = DungeonBlueprints427.reload();
        String msg = "данж: блупринты перечитаны — " + lib.summaryLine()
                + (lib.skippedNoMarkers > 0 ? (", без lapis_block: " + lib.skippedNoMarkers) : "")
                + (lib.failedParse > 0 ? (", ошибок: " + lib.failedParse) : "");
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        if (ctx == null || ctx.getSource() == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        ServerLevel dungeon = DungeonManager425.dungeonLevel(server);
        DungeonData425 data = DungeonData425.get(server);
        DungeonBlueprints427.Library lib = DungeonBlueprints427.get();
        String msg = "данж: измерение "
                + (dungeon == null ? "НЕ загружено (нужен новый мир)" : "готово")
                + ", инстансов: " + data.instanceCount()
                + ", шаг сетки: " + DungeonData425.GRID_SPACING
                + ", уникальный/вход: " + (lib.config == null || lib.config.uniqueDungeonPerEntry)
                + " | " + lib.summaryLine();
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
            String state = inst.pieces.isEmpty()
                    ? (inst.placed ? "[placed]" : "[pending]")
                    : (inst.placed
                            ? "[done " + inst.pieces.size() + "/" + inst.pieces.size() + "]"
                            : "[building " + inst.donePieceCount() + "/" + inst.pieces.size() + "]");
            String line = "  #" + inst.index + " " + inst.id + " @ ("
                    + inst.originX + ", " + inst.originY + ", " + inst.originZ + ") " + state;
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        if (ctx == null || ctx.getSource() == null) return null;
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException ignored) {
            return null;
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
