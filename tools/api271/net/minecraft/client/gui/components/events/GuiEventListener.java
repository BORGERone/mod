package net.minecraft.client.gui.components.events;

/** Compile-time signature only; never bundled into the mod JAR. */
public interface GuiEventListener {
    default boolean charTyped(char ch, int modifiers) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default void setFocused(boolean focused) {
    }
}
