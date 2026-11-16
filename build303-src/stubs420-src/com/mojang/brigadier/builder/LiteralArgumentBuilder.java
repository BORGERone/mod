package com.mojang.brigadier.builder;

import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Build 420: {@code Commands.literal(name)} - a keyword of a command.
 *
 * <p>{@code register} answers a node, which is why this type has to name it: the game's
 * {@code CommandDispatcher#register} is declared to return the node it built.</p>
 */
public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {

    public static <S> LiteralArgumentBuilder<S> literal(String name) {
        return new LiteralArgumentBuilder<S>();
    }

    public LiteralCommandNode<S> build() {
        return new LiteralCommandNode<S>();
    }
}
