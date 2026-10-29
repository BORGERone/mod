package traben.entity_model_features.models.animation.state;
import java.util.UUID;
import traben.entity_model_features.utils.EMFEntity;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
public interface EMFEntityRenderState extends ETFEntityRenderState {
 EMFEntity emfEntity();
 default boolean isFirstPersonHand(){return EMFState.FIRST_PERSON;}
 default boolean isSneaking(){return testing.Probe252.sneaking;}
 default boolean skipModelVariate(){return testing.Probe250.skip;}
 default UUID uuid(){return ((net.minecraft.world.entity.Entity)emfEntity()).getUUID();}
 float limbAngle();void setLimbAngle(float v);
 float limbDistance();void setLimbDistance(float v);
 float headYaw();void setHeadYaw(float v);
 float headPitch();void setHeadPitch(float v);
}
