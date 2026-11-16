package net.minecraft.client.model.geom;

/**
 * Compile-time shape of the baked-model set (Build 349).
 *
 * <p>{@code bakeLayer(ModelLayerLocation)} returns the <b>root part</b> of the layer, not a model:
 * every renderer wraps it in the model it wants -
 * {@code new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false)} and
 * {@code new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR))} in Ars Nouveau's
 * {@code DummyRenderer} (1.21), {@code new AngelWingsModel<>(Minecraft.getInstance()
 * .getEntityModels().bakeLayer(...))} in irons-spells-n-spellbooks (1.21). Build 348 declared it as
 * returning {@code EntityModel} and the client died with a {@code NoSuchMethodError} on the first
 * frame - the wrapping constructor in that very evidence was the answer.</p>
 */
public class EntityModelSet {
    public ModelPart bakeLayer(ModelLayerLocation location) {
        return null;
    }
}
