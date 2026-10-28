package traben.entity_model_features.models.parts;
import java.util.*;import traben.entity_model_features.models.EMFModel_ID;import traben.entity_model_features.models.animation.EMFAnimationHandler;import traben.entity_model_features.models.animation.state.EMFEntityRenderState;import net.minecraft.client.model.geom.ModelPart;
public class EMFModelPartRoot extends EMFModelPartVanilla {
 public boolean isMainModel;public EMFModel_ID modelName;public traben.entity_texture_features.ETFApi.ETFVariantSuffixProvider variantTester;public traben.entity_texture_features.utils.ETFLruCache.UUIDInteger entitySuffixMap;public boolean hasAnimation(){return true;}public void doVariantCheck(EMFEntityRenderState state){}public void animate(){}
 public Map<String,ModelPart> getAllVanillaPartsByNameEMF(){return null;}public void receiveAnimationHandler(int n,EMFAnimationHandler a){}public void setVariantStateTo(int v){}public void checkIfShouldExpireEntity(EMFEntityRenderState s,java.util.UUID id){}
}
