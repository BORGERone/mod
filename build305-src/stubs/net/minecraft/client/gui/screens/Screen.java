package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/** Compile-time signature only; never bundled into the mod JAR. */
public class Screen implements GuiEventListener {
    public int width;
    public int height;
    public Minecraft minecraft;

    protected Screen(Component title) {
    }

    protected void init() {
    }

    public List<GuiEventListener> children() {
        return java.util.Collections.emptyList();
    }

    public <T extends GuiEventListener> T addRenderableWidget(T listener) {
        return listener;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX,
            double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char character, int modifiers) {
        return false;
    }

    public void onClose() {
    }

    public boolean isPauseScreen() {
        return true;
    }

    public void clearWidgets() {
    }

    public void rebuildWidgets() {
    }

    public GuiEventListener getFocused() {
        return null;
    }

    public void setFocused(GuiEventListener listener) {
    }

    public Font getFont() {
        return null;
    }

    protected void removeWidget(GuiEventListener listener) {
    }
}
