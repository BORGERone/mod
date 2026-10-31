package dev.settledlands.audio;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class PlayAudioCommand {
    private PlayAudioCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("play")
                .requires(s -> s.hasPermission(0))
                .then(Commands.literal("stop")
                    .executes(ctx -> stop(ctx, 0.0f))
                    .then(Commands.argument("fade_sec", FloatArgumentType.floatArg(0.0f, 10.0f))
                        .executes(ctx -> stop(ctx, FloatArgumentType.getFloat(ctx, "fade_sec")))))
                .then(Commands.argument("file", StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        List<String> suggestions = new ArrayList<>(AudioStorage.listAvailableFiles());
                        suggestions.add("stop");
                        return SharedSuggestionProvider.suggest(suggestions, builder);
                    })
                    .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "file"), 0.0f, 1.0f, false, 0.0f, 1.0f))
                    .then(Commands.argument("radius", FloatArgumentType.floatArg(0.0f, 10000.0f))
                        .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "file"), FloatArgumentType.getFloat(ctx, "radius"), 1.0f, false, 0.0f, 1.0f))
                        .then(Commands.argument("volume", FloatArgumentType.floatArg(0.0f, 1.0f))
                            .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "file"), FloatArgumentType.getFloat(ctx, "radius"), FloatArgumentType.getFloat(ctx, "volume"), false, 0.0f, 1.0f))
                            .then(Commands.argument("loop", BoolArgumentType.bool())
                                .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "file"), FloatArgumentType.getFloat(ctx, "radius"), FloatArgumentType.getFloat(ctx, "volume"), BoolArgumentType.getBool(ctx, "loop"), 0.0f, 1.0f))
                                .then(Commands.argument("fade_sec", FloatArgumentType.floatArg(0.0f, 10.0f))
                                    .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "file"), FloatArgumentType.getFloat(ctx, "radius"), FloatArgumentType.getFloat(ctx, "volume"), BoolArgumentType.getBool(ctx, "loop"), FloatArgumentType.getFloat(ctx, "fade_sec"), 1.0f))
                                    .then(Commands.argument("pitch", FloatArgumentType.floatArg(0.1f, 3.0f))
                                        .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "file"), FloatArgumentType.getFloat(ctx, "radius"), FloatArgumentType.getFloat(ctx, "volume"), BoolArgumentType.getBool(ctx, "loop"), FloatArgumentType.getFloat(ctx, "fade_sec"), FloatArgumentType.getFloat(ctx, "pitch")))))))))
        );
    }

    private static int stop(CommandContext<CommandSourceStack> ctx, float fadeSec) {
        CommandSourceStack source = ctx.getSource();
        Vec3 pos = source.getPosition();
        PlayAudioPayload payload = new PlayAudioPayload("", pos.x, pos.y, pos.z, 0.0f, 0.0f, false, fadeSec, 1.0f, true);
        if (source.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, payload);
        } else {
            for (ServerPlayer sp : source.getLevel().players()) {
                PacketDistributor.sendToPlayer(sp, payload);
            }
        }
        source.sendSuccess(() -> Component.literal("Воспроизведение остановлено."), false);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, String file, float radius, float volume, boolean loop, float fadeSec, float pitch) {
        if ("stop".equalsIgnoreCase(file)) {
            return stop(ctx, fadeSec);
        }
        CommandSourceStack source = ctx.getSource();
        Vec3 pos = source.getPosition();
        ServerLevel level = source.getLevel();
        PlayAudioPayload payload = new PlayAudioPayload(file, pos.x, pos.y, pos.z, radius, volume, loop, fadeSec, pitch, false);

        if (radius <= 0.0f) {
            if (source.getEntity() instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, payload);
            } else {
                Player nearest = level.getNearestPlayer(pos.x, pos.y, pos.z, 64.0, false);
                if (nearest instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp, payload);
                }
            }
            source.sendSuccess(() -> Component.literal("Воспроизведение «" + file + "» запущено для инициатора."), false);
            return 1;
        } else {
            double rSq = (double) radius * (double) radius;
            int count = 0;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(pos.x, pos.y, pos.z) <= rSq) {
                    PacketDistributor.sendToPlayer(player, payload);
                    count++;
                }
            }
            final int c = count;
            source.sendSuccess(() -> Component.literal("Воспроизведение «" + file + "» запущено для " + c + " игроков (радиус: " + radius + " блоков)."), false);
            return count;
        }
    }
}
