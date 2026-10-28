package traben.entity_model_features.models.parts;
import net.minecraft.client.model.geom.ModelPart;import java.util.*;import traben.entity_model_features.*;import traben.entity_model_features.models.EMFModel_ID;import traben.entity_model_features.models.animation.EMFAnimationHandler;import traben.entity_model_features.models.animation.state.*;import testing.Probe250;
public class EMFModelPartRoot extends EMFModelPart {
 public boolean isMainModel;public final EMFModel_ID modelName;public traben.entity_texture_features.ETFApi.ETFVariantSuffixProvider variantTester;public final traben.entity_texture_features.utils.ETFLruCache.UUIDInteger entitySuffixMap=new traben.entity_texture_features.utils.ETFLruCache.UUIDInteger();private final EMFAnimationHandler handler;private long last=-1;private final Map<String,ModelPart> parts;public boolean sourceHasAnimation=true;
 private EMFModelPartRoot(Map<String,ModelPart> children,boolean slim){super(children);parts=children;modelName=new EMFModel_ID(slim?"player_slim":"player");handler=new EMFAnimationHandler(this);if(Probe250.useVariants)variantTester=state->{Probe250.variantCalls++;return ((EMFEntityRenderState)state).uuid().equals(Probe250.firstPlayerId)?2:3;};}
 public static EMFModelPartRoot create(boolean slim){
  Map<String,ModelPart> c=new LinkedHashMap<>();for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"})c.put(n,new EMFModelPart(new LinkedHashMap<>()));
  EMFModelPartRoot root=new EMFModelPartRoot(c,slim);c.get("right_arm").setPos(-5,2,0);c.get("left_arm").setPos(5,2,0);c.get("right_leg").setPos(-1.9f,12,0);c.get("left_leg").setPos(1.9f,12,0);c.values().forEach(ModelPart::saveRest);return root;
 }
 public boolean hasAnimation(){return sourceHasAnimation&&!Probe250.noModelAnimation;}
 public void setVariantStateTo(int variant){testing.Probe.events.add("variant");testing.Probe250.selectedVariant=variant;}
 public void checkIfShouldExpireEntity(EMFEntityRenderState state,java.util.UUID id){testing.Probe250.expireChecks++;}
 public Map<String,ModelPart> getAllVanillaPartsByNameEMF(){return parts;}
 public void animate(){
  if(Probe250.paused || last==EMFManager.getInstance().entityRenderCount)return;
  last=EMFManager.getInstance().entityRenderCount;
  var context=new EMFAnimationApi.EMFAnimationHook.AnimationContext(EMFState.state(),this,null,null,handler);
  boolean cancel=Probe250.cancel;
  try {
   for(var hook:EMFState.animationHooks)if(!hook.onAnimationStart(context,cancel))cancel=true;
   if(!cancel)handler.animate();
   for(var hook:EMFState.animationHooks)hook.onAnimationEnd(context,cancel);
  }catch(Throwable t){
   var error=new EMFAnimationApi.EMFAnimationHook.AnimationContext(EMFState.state(),this,t,null,handler);
   for(var hook:EMFState.animationHooks)hook.onAnimationEnd(error,false);
  }
 }
}
