package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;

/**
 * Compile-time signature only; never bundled into the mod JAR.
 *
 * <p>Build 377 added the {@link Component} overloads of the two text calls: the base mod's screens all
 * pass a Component ({@code GuideTomeScreen#render}), and build 377's hack panel does the same, so the
 * game calls the overload that is already proven.</p>
 */
public class GuiGraphics {

    public PoseStack pose() {
        return null;
    }

    public void flush() {
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        return 0;
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        return 0;
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return 0;
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
    }
}
