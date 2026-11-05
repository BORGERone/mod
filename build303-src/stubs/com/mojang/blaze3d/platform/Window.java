package com.mojang.blaze3d.platform;

/** Compile-time signature only; never bundled into the mod JAR. */
public class Window {

    /** The GLFW window handle the base mod already passes to {@code InputConstants.isKeyDown}. */
    public long getWindow() {
        return 0L;
    }
}
