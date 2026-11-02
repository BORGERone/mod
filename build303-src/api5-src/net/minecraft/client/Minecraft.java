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
    public static Minecraft getInstance() {
        return null;
    }

    public net.minecraft.client.model.geom.EntityModelSet getEntityModels() {
        return null;
    }
}
