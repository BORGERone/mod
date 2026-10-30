package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.GuiGraphics;

public class RenderGuiEvent {
    public static class Post extends RenderGuiEvent {
        public GuiGraphics getGuiGraphics() { return null; }
    }
}
