package net.minecraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

/** Harness: a singleton, so the screen can ask for its font. */
public class Minecraft {
    public Font font;
    public Screen screen;
    private static final Minecraft INSTANCE = new Minecraft();

    public static Minecraft getInstance() {
        return INSTANCE;
    }

    public void setScreen(Screen screen) {
        System.out.println("[harness] setScreen " + (screen == null ? "null" : screen.getClass().getName()));
        this.screen = screen;
    }
}
