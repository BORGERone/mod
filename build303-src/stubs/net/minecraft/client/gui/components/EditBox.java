package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

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

    public void setHint(Component hint) {
    }

    public void setFilter(Predicate<String> filter) {
    }
}
