package net.minecraft.client.model.geom;

/**
 * Compile-time shape of the game's layer registry (Build 348).
 *
 * <p>{@code PLAYER_INNER_ARMOR} (leggings) and {@code PLAYER_OUTER_ARMOR} (everything else) are the
 * two layers vanilla's player renderer bakes for armour, exactly the pair {@code usesInnerModel}
 * chooses between.</p>
 */
public class ModelLayers {
    public static final ModelLayerLocation PLAYER_INNER_ARMOR = null;
    public static final ModelLayerLocation PLAYER_OUTER_ARMOR = null;
}
