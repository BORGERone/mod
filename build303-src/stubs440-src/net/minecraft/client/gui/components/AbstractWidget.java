package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public abstract class AbstractWidget implements GuiEventListener {
    public boolean active = true;
    public boolean visible = true;

    public AbstractWidget() {
    }

    protected AbstractWidget(int x, int y, int width, int height, Component message) {
    }

    public int getX() { return 0; }

    public int getY() { return 0; }

    public int getWidth() { return 0; }

    public int getHeight() { return 0; }

    public void setX(int x) {
    }

    public void setY(int y) {
    }

    public void setWidth(int width) {
    }

    public void setTooltip(Tooltip tooltip) {
    }

    protected boolean isHoveredOrFocused() {
        return false;
    }

    public void onClick(double mouseX, double mouseY) {
    }

    public void onRelease(double mouseX, double mouseY) {
    }

    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }

    protected void defaultButtonNarrationText(NarrationElementOutput narration) {
    }
}
