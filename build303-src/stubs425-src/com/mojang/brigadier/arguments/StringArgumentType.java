package com.mojang.brigadier.arguments;

import com.mojang.brigadier.context.CommandContext;

/**
 * Compile-time mirror (Build 425): {@code StringArgumentType.word()} and
 * {@code StringArgumentType.getString(CommandContext, String)}, already called by {@code NpcEscortCommands}
 * and {@code PlayAudioCommand} in the base jar.
 */
public class StringArgumentType implements ArgumentType<String> {
    private StringArgumentType() {}

    public static StringArgumentType word() {
        return new StringArgumentType();
    }

    public static String getString(CommandContext<?> context, String name) {
        return "";
    }
}
