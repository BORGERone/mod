package net.minecraft.client.renderer.texture;

/**
 * Compile-time shape of the damage-overlay packer Build 338 passes on.
 *
 * <p>{@link #NO_OVERLAY} carries the game's own value (10 &lt;&lt; 16): first-person arms are drawn
 * without the hurt flash, and the constant is inlined at compile time, so it has to match.
 */
public class OverlayTexture {
    public static final int NO_OVERLAY = 655360;
}
