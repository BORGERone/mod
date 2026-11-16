package com.mojang.brigadier;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Build 420 (fixed package import in Build 426): one command body.
 *
 * <p>The descriptor is the base jar's own - {@code NpcEscortCommands} hands a lambda of this shape to
 * {@code executes} - so nothing here is guessed. {@code throws CommandSyntaxException} is part of the
 * game's signature ({@code com.mojang.brigadier.exceptions.CommandSyntaxException}).</p>
 */
@FunctionalInterface
public interface Command<S> {
    int run(CommandContext<S> context) throws CommandSyntaxException;
}
