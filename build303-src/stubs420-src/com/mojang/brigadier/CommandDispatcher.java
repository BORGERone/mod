package com.mojang.brigadier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Build 420: {@code CommandDispatcher#register}.
 *
 * <p>The parameter is {@code LiteralArgumentBuilder}, not {@code ArgumentBuilder}: the base jar compiles
 * its own call as {@code register(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/
 * brigadier/tree/LiteralCommandNode;} (see {@code ModCommands#register}), and brigadier's source has
 * exactly {@code public LiteralCommandNode<S> register(final LiteralArgumentBuilder<S> command)} because
 * a {@code RootCommandNode} can only hold literals. The first version of this stub took an
 * {@code ArgumentBuilder} and compiled a method the game does not have - the Build 280 mistake; the gate
 * caught it and this is the fix.</p>
 */
public class CommandDispatcher<S> {

    @SuppressWarnings("unchecked")
    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> command) {
        return new LiteralCommandNode<S>();
    }
}
