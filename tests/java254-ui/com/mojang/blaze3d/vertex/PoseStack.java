package com.mojang.blaze3d.vertex;

public class PoseStack {
    public int depth;
    private final double[] zStack = new double[32];

    public double currentZ() {
        return zStack[depth];
    }

    public void pushPose() {
        zStack[depth + 1] = zStack[depth];
        depth++;
    }

    public void popPose() {
        depth--;
    }

    public void translate(float x, float y, float z) {
        zStack[depth] += z;
    }

    public void translate(double x, double y, double z) {
        zStack[depth] += z;
    }

    public void scale(float x, float y, float z) {}
}
