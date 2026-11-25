package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Compile-time mirror (Build 443): MultiLineEditBox API from base JAR & ForgeJavaDocs-NG 1.21.x. */
public class MultiLineEditBox extends AbstractWidget {

    public MultiLineEditBox(Font font, int x, int y, int width, int height,
            Component placeholder, Component narrationTitle) {
    }

    public void setCharacterLimit(int maxLength) {}

    public void setValueListener(Consumer<String> listener) {}

    public void setValue(String value) {}

    public String getValue() { return ""; }

    public void setFocused(boolean focused) {}

    public void setHeight(int height) {}

    public int getInnerHeight() { return 0; }

    @Override
    public int getY() { return 0; }

    @Override
    public int getHeight() { return 0; }

    public boolean isMouseOver(double mouseX, double mouseY) { return false; }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }
}
