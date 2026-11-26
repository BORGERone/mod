package net.minecraft.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/** Compile-time mirror (Build 446): preserves Build 420 factories plus {@link #performPrefixedCommand}. */
public class Commands {

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name,
            ArgumentType<T> type) {
        return new RequiredArgumentBuilder<CommandSourceStack, T>();
    }

    public void performPrefixedCommand(CommandSourceStack source, String command) {
    }
}
