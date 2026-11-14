package com.mojang.brigadier;

import com.mojang.brigadier.context.CommandContext;

/**
 * Build 420: one command body.
 *
 * <p>The descriptor is the base jar's own - {@code NpcEscortCommands} hands a lambda of this shape to
 * {@code executes} - so nothing here is guessed. {@code throws CommandSyntaxException} is part of the
 * game's signature; the anchor's own body never throws it, and a compiler that is told about it is what
 * keeps a future edit honest.</p>
 */
@FunctionalInterface
public interface Command<S> {
    int run(CommandContext<S> context) throws CommandSyntaxException;
}
