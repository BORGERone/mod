package net.minecraft.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Build 420: {@code Commands.literal} and {@code Commands.argument}.
 *
 * <p>The base jar registers its own commands with exactly these two factories
 * ({@code NpcEscortCommands}, {@code CutsceneCommands}, {@code ModCommands}), so this is the game's own
 * entry point and not a new registration path.</p>
 */
public final class Commands {

    private Commands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name,
            ArgumentType<T> type) {
        return new RequiredArgumentBuilder<CommandSourceStack, T>();
    }
}
