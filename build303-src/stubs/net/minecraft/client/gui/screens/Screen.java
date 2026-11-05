package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/**
 * Compile-time signature only; never bundled into the mod JAR.
 *
 * <p>Build 377 widened this mirror from the bare widget list to the members a screen actually
 * overrides. Every one of them is taken from the base mod's own compiled screens, so the shapes are
 * the ones the game already calls: {@code GuideTomeScreen#init/render/renderBackground/keyPressed/
 * isPauseScreen}, {@code GuideDialogueScreen#onClose} and {@code NpcDialogueGraphScreen#keyReleased}.
 * </p>
 */
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

    protected void removeWidget(GuiEventListener listener) {
    }
}
