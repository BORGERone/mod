package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public class Screen implements GuiEventListener {

    protected final Component title;
    protected int width;
    protected int height;
    protected Font font;

    protected Screen(Component title) {
        this.title = title;
    }

    protected void init() {
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean isPauseScreen() {
        return true;
    }

    public void onClose() {
    }

    public void tick() {
    }

    public void removed() {
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

    protected void setInitialFocus(GuiEventListener listener) {
    }

    protected void setInitialFocus() {
    }

    protected void removeWidget(GuiEventListener listener) {
    }

    public static boolean hasShiftDown() {
        return false;
    }

    protected void setTooltipForNextRenderPass(Component tooltip) {
    }
}
