package net.minecraft.client.gui.components;

/** Compile-time signature only; never bundled into the mod JAR. */
public abstract class AbstractWidget {
    public boolean visible;
    public abstract int getY();
    public abstract int getHeight();
}
