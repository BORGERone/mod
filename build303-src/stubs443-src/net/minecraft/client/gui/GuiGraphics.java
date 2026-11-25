package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Compile-time mirror (Build 443): extends Build 440 GuiGraphics with FormattedCharSequence drawString. */
public class GuiGraphics {

    public PoseStack pose() { return null; }

    public void flush() {}

    public void fill(int x1, int y1, int x2, int y2, int color) {}

    public void renderOutline(int x, int y, int width, int height, int color) {}

    public void enableScissor(int minX, int minY, int maxX, int maxY) {}

    public void disableScissor() {}

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) { return 0; }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) { return 0; }

    public int drawString(Font font, Component text, int x, int y, int color) { return 0; }

    public int drawString(Font font, FormattedCharSequence seq, int x, int y, int color, boolean dropShadow) {
        return 0;
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {}

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {}

    public void renderItem(ItemStack stack, int x, int y) {}

    public void renderTooltip(Font font, ItemStack stack, int mouseX, int mouseY) {}
}
