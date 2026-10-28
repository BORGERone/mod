package strm.touchnmotion;
import java.util.*;import java.util.function.Function;import net.minecraft.client.model.geom.ModelPart;import org.joml.Matrix4f;import testing.*;import traben.entity_model_features.EMFAnimationApi;
/** Synthetic TnM hook; separate tests will execute the external real hook. */
public class TouchNMotionHook extends EMFAnimationApi.EMFAnimationHook {
 public static void register(){traben.entity_model_features.models.animation.state.EMFState.animationHooks.add(new TouchNMotionHook());}
 @Override public void onAnimationEnd(AnimationContext c,boolean cancelled){
  if(c.activeState()==null||c.activeState().isFirstPersonHand())return;
  var root=c.animatingModelRoot();if(!root.isMainModel)throw new AssertionError("must be main");
  UUID id=c.activeState().uuid();Function<String,ModelPart> p=root.getAllVanillaPartsByNameEMF()::get;
  strm.touchnmotion.footgrounding.compat.FootGrounding.recordAnimated(id,p);
  Probe.events.add("apply");Probe250.tnmRuns++;
  if(!id.equals(Probe.recorded))throw new AssertionError("identity");
  for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"}){ModelPart a=p.apply(n);Probe.initial.put(n,new Matrix4f().translation(a.x/16,a.y/16,a.z/16).rotate(new org.joml.Quaternionf().rotationZYX(a.zRot,a.yRot,a.xRot)).scale(a.xScale,a.yScale,a.zScale));}
  if(Probe.mutate){ModelPart body=p.apply("body"),arm=p.apply("right_arm"),leg=p.apply("left_leg");body.zRot+=.21f;body.x+=1.1f;arm.xRot-=.35f;arm.y+=.8f;leg.xRot+=.17f;}
  if(Probe.override!=null)Probe.override.accept(p);
  if(Probe.badPose)p.apply("body").x=Float.NaN;
  if(Probe.failApply)throw new IllegalStateException("apply failed");
  for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"}){ModelPart a=p.apply(n);Probe.expected.put(n,new Matrix4f().translation(a.x/16,a.y/16,a.z/16).rotate(new org.joml.Quaternionf().rotationZYX(a.zRot,a.yRot,a.xRot)).scale(a.xScale,a.yScale,a.zScale));}
 }
}
