package com.mojang.brigadier.exceptions;

/**
 * Build 420 / fixed in Build 426: the exception a command body is allowed to throw.
 *
 * <p>In Mojang Brigadier this resides in {@code com.mojang.brigadier.exceptions}, not
 * {@code com.mojang.brigadier}. Build 420 placed this stub in the root package by mistake; Build 425's
 * {@code DungeonCommand425} lambdas emitted {@code throws com/mojang/brigadier/CommandSyntaxException},
 * causing {@code NoClassDefFoundError} during NeoForge {@code AutomaticEventSubscriber.inject}.</p>
 */
public class CommandSyntaxException extends Exception {
    public CommandSyntaxException() {
        super();
    }
}
