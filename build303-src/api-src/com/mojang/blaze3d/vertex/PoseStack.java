package com.mojang.blaze3d.vertex;

import org.joml.Matrix4f;

public class PoseStack {
    public static class Pose {
        public Matrix4f pose() { return null; }
    }

    public Pose last() { return null; }
    public double currentZ() { return 0.0D; }
    public void pushPose() {}
    public void popPose() {}
    public void translate(float x, float y, float z) {}
    public void translate(double x, double y, double z) {}
    public void scale(float x, float y, float z) {}
}
