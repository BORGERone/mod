package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;

public class GuiGraphics {
    public final List<String> words = new ArrayList<>();
    public final List<Double> textMaxZ = new ArrayList<>();
    public final List<int[]> fills = new ArrayList<>();
    public int rectangles;
    public int shadowCalls;
    public int centeredCalls;
    public int flushCalls;
    private final PoseStack pose = new PoseStack();

    public PoseStack pose() {
        return pose;
    }

    public void flush() {
        flushCalls++;
    }

    public void fill(int a, int b, int c, int d, int color) {
        rectangles++;
        fills.add(new int[]{a, b, c, d, color});
    }

    public int drawString(Font f, String s, int x, int y, int color, boolean shadow) {
        words.add(s);
        if (shadow) shadowCalls++;
        textMaxZ.add(pose.currentZ() + (shadow ? 0.03 : 0.0));
        return 0;
    }

    public void drawCenteredString(Font f, String s, int x, int y, int color) {
        words.add(s);
        centeredCalls++;
        shadowCalls++;
        textMaxZ.add(pose.currentZ() + 0.03);
    }
}
