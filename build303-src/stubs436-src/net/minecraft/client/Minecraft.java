package net.minecraft.client;

/** Compile-time mirror (Build 436): adds hitResult (already read by base jar NpcPickCopy). */
public class Minecraft {

    public KeyboardHandler keyboardHandler;

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
}
