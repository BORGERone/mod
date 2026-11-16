package net.minecraft.client.gui.screens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/** Harness screen: the same signatures, a real child list, no rendering. */
public class Screen implements GuiEventListener {
    public int width = 1280;
    public int height = 720;
    public Minecraft minecraft;
    public final List<GuiEventListener> children = new ArrayList<>();
    private GuiEventListener focused;

    protected Screen(Component title) {
    }

    protected void init() {
    }

    public List<GuiEventListener> children() {
        return children;
    }

    public <T extends GuiEventListener> T addRenderableWidget(T listener) {
        children.add(listener);
        return listener;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean charTyped(char character, int modifiers) { return false; }
    public void onClose() { }
    public boolean isPauseScreen() { return true; }
    public void clearWidgets() { children.clear(); }
    public void rebuildWidgets() { }
    public GuiEventListener getFocused() { return focused; }
    public void setFocused(GuiEventListener listener) { focused = listener; }
    public Font getFont() { return null; }
    protected void removeWidget(GuiEventListener listener) { }
}
