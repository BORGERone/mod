package com.mojang.blaze3d.systems;

import java.util.function.Supplier;
import net.minecraft.client.renderer.ShaderInstance;

public class RenderSystem {
    public static void enableBlend() {}
    public static void disableBlend() {}
    public static void defaultBlendFunc() {}
    public static void enableDepthTest() {}
    public static void disableDepthTest() {}
    public static void enableCull() {}
    public static void disableCull() {}
    public static void setShader(Supplier<ShaderInstance> shader) {}
}
