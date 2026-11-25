package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public class Button extends AbstractWidget {
    @Override public int getY() { return 0; }

    @Override public int getHeight() { return 0; }

    public void setMessage(Component message) {
    }

    @Override public void setTooltip(Tooltip tooltip) {
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder();
    }

    public interface OnPress {
        void onPress(Button button);
    }

    public static class Builder {
        public Builder bounds(int x, int y, int width, int height) {
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            return this;
        }

        public Button build() {
            return new Button();
        }
    }
}
