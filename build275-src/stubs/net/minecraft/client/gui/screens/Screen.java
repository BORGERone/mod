package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compile-time signature only; never bundled into the mod JAR. */
public class Screen implements GuiEventListener {
    protected Screen(Component title) {
    }

    public List<GuiEventListener> children() {
        return java.util.Collections.emptyList();
    }
}
