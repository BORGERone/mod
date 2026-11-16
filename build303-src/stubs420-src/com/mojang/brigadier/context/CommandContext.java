package com.mojang.brigadier.context;

/** Build 420: one run of a command, with the source it came from. */
public class CommandContext<S> {

    @SuppressWarnings("unchecked")
    public S getSource() {
        return (S) new Object();
    }
}
