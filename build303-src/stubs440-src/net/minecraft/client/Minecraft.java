package net.minecraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.sounds.SoundManager;

/** Compile-time mirror (Build 440): exposes font and getSoundManager on top of Build 436 stub. */
public class Minecraft {

    public KeyboardHandler keyboardHandler;

    public Font font;

    public net.minecraft.client.gui.screens.Screen screen;

    public net.minecraft.client.multiplayer.ClientLevel level;

    public net.minecraft.world.phys.HitResult hitResult;

    public com.mojang.blaze3d.platform.Window getWindow() {
        return null;
    }

    public net.minecraft.client.player.LocalPlayer player;

    public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode;

    public static Minecraft getInstance() {
        return null;
    }

    public void execute(Runnable task) {}

    public void setScreen(net.minecraft.client.gui.screens.Screen screen) {}

    public net.minecraft.client.model.geom.EntityModelSet getEntityModels() {
        return null;
    }

    public SoundManager getSoundManager() {
        return null;
    }
}
