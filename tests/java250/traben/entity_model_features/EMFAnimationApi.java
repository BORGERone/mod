package traben.entity_model_features;
import traben.entity_model_features.models.parts.EMFModelPartRoot;import traben.entity_model_features.models.animation.state.EMFEntityRenderState;import traben.entity_model_features.models.animation.EMFAnimationHandler;import traben.entity_model_features.models.animation.state.EMFState;import net.minecraft.client.model.geom.ModelPart;
public interface EMFAnimationApi {
 static boolean registerAnimationHook(EMFAnimationHook hook) throws Exception {EMFState.animationHooks.add(hook);return true;}
 abstract class EMFAnimationHook {
  public boolean onAnimationStart(AnimationContext c,boolean cancelled){return true;}
  public void onAnimationEnd(AnimationContext c,boolean cancelled){}
  public record AnimationContext(EMFEntityRenderState activeState, EMFModelPartRoot animatingModelRoot, Throwable error, ModelPart[] partsRequestedToPauseThisAnimation, EMFAnimationHandler animationHandler){}
 }
}
