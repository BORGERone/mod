package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/** Compile-time signature only; never bundled into the mod JAR. */
public class Screen implements GuiEventListener {
    protected Screen(Component title) {
    }

    public List<GuiEventListener> children() {
        return java.util.Collections.emptyList();
    }

    public <T extends GuiEventListener> T addRenderableWidget(T listener) {
        return listener;
    }

    public GuiEventListener getFocused() {
        return null;
    }

    public void setFocused(GuiEventListener listener) {
    }

    protected void removeWidget(GuiEventListener listener) {
    }
}
