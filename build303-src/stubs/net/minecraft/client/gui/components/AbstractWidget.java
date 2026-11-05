package net.minecraft.client.gui.components;

import net.minecraft.client.gui.components.events.GuiEventListener;

/** Compile-time signature only; never bundled into the mod JAR. */
public abstract class AbstractWidget implements GuiEventListener {
    public boolean active = true;
    public boolean visible = true;

    public int getX() { return 0; }

    public int getY() { return 0; }

    public int getWidth() { return 0; }

    public int getHeight() { return 0; }

    public void setX(int x) {
    }

    public void setY(int y) {
    }

    public void setWidth(int width) {
    }
}
