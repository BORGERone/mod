package net.minecraft.client.gui.components;

import net.minecraft.client.gui.components.events.GuiEventListener;

/** Compile-time signature only; never bundled into the mod JAR. */
public abstract class AbstractWidget implements GuiEventListener {
    public boolean active = true;
    public boolean visible = true;

    public abstract int getY();

    public abstract int getHeight();

    public void setY(int y) {
    }
}
