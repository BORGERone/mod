package traben.entity_model_features.models.animation;
import traben.entity_model_features.models.parts.*;import testing.*;import java.util.*;
public class EMFAnimationHandler {
 public static final class AnimLineData { public final EMFModelPart partToApplyTo;public final String expression;public AnimLineData(EMFModelPart p,String e){partToApplyTo=p;expression=e;} }
 private final Map<String,AnimLineData> lines=new HashMap<>();private final EMFModelPartRoot root;
 public EMFAnimationHandler(EMFModelPartRoot r){root=r;
  lines.put("var.Created_by_FreshLX_for_Fresh_Animations",new AnimLineData(null,"1"));
  for(String var:new String[]{"var.mvmnt_rlegrx","var.mvmnt_llegrx"})lines.put(var,new AnimLineData(null,"sqrt(limb_speed)*cos(var.ls)*var.run+var.walk"));
  for(String part:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"})for(String c:new String[]{"rx","ry","rz","tx","ty","tz"})lines.put(part+"."+c,new AnimLineData((EMFModelPart)r.getChild(part),part.equals("right_leg")?"var.mvmnt_rlegrx":part.equals("left_leg")?"var.mvmnt_llegrx":"0"));
 }
 public AnimLineData getLine(String key){if(Probe250.missingCreator&&key.equals("var.Created_by_FreshLX_for_Fresh_Animations"))return null;if(Probe250.missingMarker&&key.equals("var.mvmnt_rlegrx"))return null;if(Probe250.wrongTarget&&key.equals("body.rx"))return lines.get("head.rx");if(Probe250.badExpression&&key.equals("var.mvmnt_rlegrx"))return new AnimLineData(null,"var.mvmnt_rlegrx*(1-var.pc_own)");if(Probe250.unconnectedMovement&&key.equals("right_leg.rx"))return new AnimLineData((EMFModelPart)root.getChild("right_leg"),"0");return lines.get(key);}
 public void animate(){
  Probe.events.add("fresh");Probe250.packRuns++;if(Probe250.rootMoved)root.y+=1;
  var state=traben.entity_model_features.models.animation.state.EMFState.state();state.setLimbAngle(42);state.setLimbDistance(43);state.setHeadYaw(44);state.setHeadPitch(45);if(Probe250.throwAnimation)throw new IllegalStateException("synthetic EMF animation failure");
  // Deliberately unlike the vanilla test pose, to prove the pack stage is used.
  if(Probe.values!=null&&Probe.values[1]>0){root.getChild("right_leg").xRot=.61f;root.getChild("left_leg").xRot=-.27f;root.getChild("body").yRot=.11f;}
 }
}
