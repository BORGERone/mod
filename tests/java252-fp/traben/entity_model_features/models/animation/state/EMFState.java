package traben.entity_model_features.models.animation.state;
import traben.entity_model_features.utils.EMFEntity;
import java.util.*;
import traben.entity_model_features.EMFAnimationApi;
public class EMFState {
 public static boolean FIRST_PERSON,isLayerPhase,isMainPhase;
 public static EMFEntity ENTITY;
 public static final List<EMFAnimationApi.EMFAnimationHook> animationHooks=new ArrayList<>();
 private static final IdentityHashMap<EMFEntity,EMFEntityRenderState> states=new IdentityHashMap<>();
 static {
  resetDefaultHooks();
 }
 public static void resetDefaultHooks(){
  animationHooks.clear();
  strm.emfcompat.core.EMFCompatAnimationHook.register();
  strm.touchnmotion.TouchNMotionHook.register();
 }
 private static final class S implements EMFEntityRenderState {
  final EMFEntity entity;
  float a=Float.NaN,b=Float.NaN,y=Float.NaN,p=Float.NaN;
  S(EMFEntity e){entity=e;}
  public EMFEntity emfEntity(){return entity;}
  public float limbAngle(){return a;}public void setLimbAngle(float v){a=v;}
  public float limbDistance(){return b;}public void setLimbDistance(float v){b=v;}
  public float headYaw(){return y;}public void setHeadYaw(float v){y=v;}
  public float headPitch(){return p;}public void setHeadPitch(float v){p=v;}
 }
 public static EMFEntityRenderState state(){if(ENTITY==null)return null;return states.computeIfAbsent(ENTITY,S::new);}
 public static boolean isEntityForcedToVanillaModel(EMFEntityRenderState s){return testing.Probe250.forced;}
 public static float getFrameCounter(){return 1;}
}
