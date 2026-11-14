package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;
import java.util.function.Predicate;

/**
 * Build 420: the parent of a literal and a required argument, with the three calls the base jar makes on
 * both ({@code then}, {@code requires}, {@code executes}).
 */
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public T then(ArgumentBuilder<S, ?> argument) {
        return self();
    }

    public T requires(Predicate<S> requirement) {
        return self();
    }

    public T executes(Command<S> command) {
        return self();
    }
}
