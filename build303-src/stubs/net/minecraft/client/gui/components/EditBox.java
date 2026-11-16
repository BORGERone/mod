package net.minecraft.client.gui.components;

import java.util.function.Consumer;

/** Compile-time signature only; never bundled into the mod JAR. */
public class EditBox extends AbstractWidget {
    @Override public int getY() { return 0; }

    @Override public int getHeight() { return 0; }

    public void setValue(String value) {
    }

    public void setMaxLength(int maxLength) {
    }

    public void setResponder(Consumer<String> responder) {
    }
}
