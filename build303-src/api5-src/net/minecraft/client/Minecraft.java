package net.minecraft.client;

/**
 * Compile-time mirror (Build 348) of the client instance, narrowed to what the armour pass asks for.
 *
 * <p>{@code getEntityModels()} is the set every baked entity model lives in; 1.21 mods take their
 * armour and wing layers from it as {@code Minecraft.getInstance().getEntityModels()
 * .bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)} (iron431/irons-spells-n-spellbooks,
 * {@code render/AngelWingsLayer}, 1.21 NeoForge).</p>
 */
public class Minecraft {

    /**
     * Build 358: the two fields every client path in the jar reads. {@code player} is the body the
     * aim turns and {@code keyboardHandler} is the clipboard of build 318; both are public on the real
     * class, and this mirror is the one the build classpath sees first, so it has to carry them.
     */
    public KeyboardHandler keyboardHandler;

    /** Build 377: is a screen already open? The base mod reads this field to ask the same thing. */
    public net.minecraft.client.gui.screens.Screen screen;

    /** Build 377: the window handle the base mod's screens pass to InputConstants.isKeyDown. */
    public com.mojang.blaze3d.platform.Window getWindow() {
        return null;
    }
    public net.minecraft.client.player.LocalPlayer player;

    public static Minecraft getInstance() {
        return null;
    }

    /** Build 351: NpcConfigScreen#open already switches screens this way. */
    public void setScreen(net.minecraft.client.gui.screens.Screen screen) {
    }

    public net.minecraft.client.model.geom.EntityModelSet getEntityModels() {
        return null;
    }
}
