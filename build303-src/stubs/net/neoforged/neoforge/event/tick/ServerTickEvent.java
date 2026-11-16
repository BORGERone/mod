package net.neoforged.neoforge.event.tick;

import net.minecraft.server.MinecraftServer;

/**
 * Compile-time signature only (Build 352). The base jar already subscribes to exactly this event from
 * {@code Sanctity}, {@code WorldEvents}, {@code GuideDialogue} and others, and calls {@code getServer}
 * on it, so the shape is proven by the jar, not by memory.
 */
public class ServerTickEvent {
    public MinecraftServer getServer() { return null; }

    /** The phase the base jar listens to. */
    public static class Post extends ServerTickEvent {
    }
}
