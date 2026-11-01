package net.minecraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

/** Compile-time signature only; never bundled into the mod JAR. */
public class Minecraft {
    public Font font;
    public Screen screen;

    public static Minecraft getInstance() {
        return null;
    }

    public void setScreen(Screen screen) {
    }
}
