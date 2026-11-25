package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public class EditBox extends AbstractWidget {

    public EditBox() {
    }

    public EditBox(Font font, int x, int y, int width, int height, Component message) {
    }

    @Override public int getY() { return 0; }

    @Override public int getHeight() { return 0; }

    public void setValue(String value) {
    }

    public String getValue() {
        return "";
    }

    public void setMaxLength(int maxLength) {
    }

    public void setResponder(Consumer<String> responder) {
    }

    public void setHint(Component hint) {
    }

    public void setFilter(Predicate<String> filter) {
    }

    public void setEditable(boolean editable) {
    }

    public void setBordered(boolean bordered) {
    }

    public void setCursorPosition(int pos) {
    }

    public void setTooltip(Tooltip tooltip) {
    }

    public boolean isFocused() {
        return false;
    }

    public void setFocused(boolean focused) {
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }
}
