package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Compile-time signature only; never bundled into the mod JAR. */
public class EditBox extends AbstractWidget {
    public EditBox(Font font, int x, int y, int width, int height, Component message) {
    }

    @Override public int getY() { return 0; }

    @Override public int getHeight() { return 0; }

    public String getValue() {
        return "";
    }

    public void setValue(String value) {
    }

    public void setMaxLength(int maxLength) {
    }

    public void setResponder(Consumer<String> responder) {
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}
