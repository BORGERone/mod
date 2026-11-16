package com.mojang.blaze3d.platform;

/**
 * Compile-time signature only; never bundled into the mod JAR.
 *
 * <p>Build 377 reads the real keyboard state every frame so that "all keys are held" is a fact about
 * the keyboard and not about the events this screen happened to receive. The shape is the one the base
 * mod's own screens already use ({@code MobilityWandScreen}, {@code NpcDialogueGraphScreen}:
 * {@code InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 32)}).</p>
 */
public class InputConstants {

    public static boolean isKeyDown(long window, int key) {
        return false;
    }
}
