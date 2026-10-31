package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

/** Compile-time signature only; never bundled into the mod JAR. */
public class Button extends AbstractWidget {
    @Override public int getY() { return 0; }

    @Override public int getHeight() { return 0; }

    public void setMessage(Component message) {
    }
}
