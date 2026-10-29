package traben.entity_model_features.models.animation;
import traben.entity_model_features.models.parts.*;
import net.minecraft.client.model.geom.ModelPart;
import testing.*;
import java.util.*;
public class EMFAnimationHandler {
 public static final class AnimLineData {
  public final EMFModelPart partToApplyTo;
  public final String expression;
  public AnimLineData(EMFModelPart p,String e){partToApplyTo=p;expression=e;}
 }
 private final Map<String,AnimLineData> lines=new HashMap<>();
 private final EMFModelPartRoot root;
 public EMFAnimationHandler(EMFModelPartRoot r){
  root=r;
  lines.put("var.Created_by_FreshLX_for_Fresh_Animations",new AnimLineData(null,"1"));
  for(String var:new String[]{"var.mvmnt_rlegrx","var.mvmnt_llegrx"})
   lines.put(var,new AnimLineData(null,"sqrt(limb_speed)*cos(var.ls)*var.run+var.walk"));
  for(String part:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"})
   for(String c:new String[]{"rx","ry","rz","tx","ty","tz"})
    lines.put(part+"."+c,new AnimLineData((EMFModelPart)r.getChild(part),part.equals("right_leg")?"var.mvmnt_rlegrx":part.equals("left_leg")?"var.mvmnt_llegrx":"0"));
 }
 public AnimLineData getLine(String key){
  if(Probe250.missingCreator&&key.equals("var.Created_by_FreshLX_for_Fresh_Animations"))return null;
  if(Probe250.missingMarker&&key.equals("var.mvmnt_rlegrx"))return null;
  if(Probe250.wrongTarget&&key.equals("body.rx"))return lines.get("head.rx");
  if(Probe250.badExpression&&key.equals("var.mvmnt_rlegrx"))return new AnimLineData(null,"var.mvmnt_rlegrx*(1-var.pc_own)");
  if(Probe250.unconnectedMovement&&key.equals("right_leg.rx"))return new AnimLineData((EMFModelPart)root.getChild("right_leg"),"0");
  return lines.get(key);
 }
 public void animate(){animate(null);}
 public void animate(ModelPart[] paused){
  Probe.events.add("fresh");
  Probe252.hookEvents.add("fa-animate");
  Probe250.packRuns++;
  if(Probe250.rootMoved)root.y+=1;
  var state=traben.entity_model_features.models.animation.state.EMFState.state();
  state.setLimbAngle(42);state.setLimbDistance(43);state.setHeadYaw(44);state.setHeadPitch(45);
  if(Probe250.throwAnimation||Probe252.throwInAnimate)throw new IllegalStateException("synthetic EMF animation failure");
  if(Probe.values!=null&&Probe.values[1]>0){
   root.getChild("right_leg").xRot=.61f;root.getChild("left_leg").xRot=-.27f;root.getChild("body").yRot=.11f;
  }
  if(Probe252.firstPersonPackClobber && root.isMainModel){
   if(state.isFirstPersonHand()){
    // Ordinary FA first-person hand animation (`is_first_person_hand == true`)
    ModelPart ra=root.getChild("right_arm");
    ra.xRot=-0.42f;ra.yRot=0.13f;ra.zRot=0.07f;
   }else{
    // Real FA `player.jem` behavior during PAL's THIRD_PERSON_MODEL pass (`is_first_person_hand == false`):
    // resets arms to idle downward hang (`xRot ~ 0`), shifts arm pivots on sneak, zeroes sleeves (copied by TnM),
    // and mutates custom JEM child boxes + visibility.
    ModelPart ra=root.getChild("right_arm"),la=root.getChild("left_arm");
    ModelPart rs=root.getChild("right_sleeve"),ls=root.getChild("left_sleeve");
    ModelPart body=root.getChild("body"),head=root.getChild("head");
    ra.x=-5f;ra.y=state.isSneaking()?4.2f:2f;ra.z=state.isSneaking()?1.1f:0f;
    ra.xRot=0.04f;ra.yRot=0.02f;ra.zRot=0.05f;ra.xScale=1f;ra.yScale=1f;ra.zScale=1f;
    la.x=5f;la.y=state.isSneaking()?4.2f:2f;la.z=state.isSneaking()?1.1f:0f;
    la.xRot=-0.04f;la.yRot=-0.02f;la.zRot=-0.05f;la.xScale=1f;la.yScale=1f;la.zScale=1f;
    if(ra instanceof EMFModelPart e)e.isSetByAnimation=true;
    if(la instanceof EMFModelPart e)e.isSetByAnimation=true;
    if(rs!=null){rs.x=0;rs.y=0;rs.z=0;rs.xRot=0;rs.yRot=0;rs.zRot=0;rs.xScale=1;rs.yScale=1;rs.zScale=1;}
    if(ls!=null){ls.x=0;ls.y=0;ls.z=0;ls.xRot=0;ls.yRot=0;ls.zRot=0;ls.xScale=1;ls.yScale=1;ls.zScale=1;}
    if(body!=null){body.visible=true;if(state.isSneaking()){body.y=2.0f;body.xRot=0.35f;}}
    if(head!=null){head.visible=true;}
    if(ra.hasChild("EMF_right_arm")){ModelPart sub=ra.getChild("EMF_right_arm");sub.xRot=0.19f;sub.y+=1.5f;}
    if(la.hasChild("EMF_left_arm")){ModelPart sub=la.getChild("EMF_left_arm");sub.xRot=-0.19f;sub.y+=1.5f;}
   }
  }
 }
}
