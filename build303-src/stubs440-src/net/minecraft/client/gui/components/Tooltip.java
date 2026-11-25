package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public final class Tooltip {
    private Tooltip() {}

    public static Tooltip create(Component message) {
        return new Tooltip();
    }
}
