package testing;
import java.util.*;import java.lang.reflect.*;import dev.settledlands.model.*;import dev.settledlands.npc.GuideNpc;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.world.entity.LivingEntity;import com.mojang.blaze3d.vertex.PoseStack;import traben.entity_model_features.models.animation.state.EMFState;
/** Uses real BbModel/BbAnimation/249 bridge bytecode. Everything Minecraft/TnM is synthetic. */
public final class RealModel249Scenario {
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m+" events="+Probe.events);}
 static ModelPart part(Map<String,ModelPart> children,float x,float y,float z){ModelPart p=new ModelPart(List.of(),children);p.setPos(x,y,z);p.saveRest();return p;}
 static BbModel make(){
  Map<String,ModelPart> all=new LinkedHashMap<>(),wc=new LinkedHashMap<>(),bc=new LinkedHashMap<>(),rc=new LinkedHashMap<>(),oc=new LinkedHashMap<>();
  ModelPart root=part(rc,-1,24,0),body=part(bc,1,-18,0),waist=part(wc,0,0,0);rc.put("body",body);bc.put("waist",waist);oc.put("root",root);
  all.put("root",root);all.put("body",body);all.put("waist",waist);
  for(String n:new String[]{"head","rightarm","leftarm","rightleg","leftleg"}){float x=n.equals("rightarm")?-5:n.equals("leftarm")?5:n.equals("rightleg")?-2:n.equals("leftleg")?2:0;float y=n.equals("head")?-6:n.endsWith("arm")?-5:6;ModelPart q=part(new LinkedHashMap<>(),x,y,1);all.put(n,q);wc.put(n,q);}
  Map<String,float[]> rots=new HashMap<>(),pos=new HashMap<>();all.forEach((name,q)->{rots.put(name,new float[]{q.xRot,q.yRot,q.zRot});pos.put(name,new float[]{q.x,q.y,q.z});});
  BbAnimation clip=new BbAnimation("idle",1f,"loop",Map.of("head/rotation",List.of(new BbAnimation.Keyframe(0,90f,0,0,"linear"))));
  return new BbModel("ivy",128,128,part(oc,0,0,0),null,all,rots,pos,Map.of("idle",clip));
 }
 static void raw(BbModel m){m.setupAnimRaw(false,true,"","",false,0,0,0,0,0,0,0,.5f);}
 public static void main(String[] args)throws Exception{
  BbModel m=make();raw(m);float nativeHead=m.getPart("head").xRot;ok(Math.abs(nativeHead)>1f,"real native BbAnimation must animate head as negative control: "+nativeHead);
  PlayerMotionBridge.setup(m,false,true,"","",false,0,0,0,0,0,0,0,.5f);ok(Math.abs(m.getPart("head").xRot-nativeHead)<1e-5,"no scope uses actual native clip");
  GuideNpc npc=new GuideNpc();m.setupAnim(npc,0,0,0,0,0,.5f);ok(Math.abs(m.getPart("head").xRot-nativeHead)<1e-5,"real NPC setup retains native clip");
  AbstractClientPlayer player=new AbstractClientPlayer();EMFState.ENTITY=player;PoseStack stack=new PoseStack();
  Method available=PlayerMotionBridge.class.getDeclaredMethod("available");available.setAccessible(true);ok(Boolean.TRUE.equals(available.invoke(null)),"optional bridge initialized");
  Field pf=BbModel.class.getDeclaredField("partParents");pf.setAccessible(true);@SuppressWarnings("unchecked") IdentityHashMap<ModelPart,ModelPart> parents=(IdentityHashMap<ModelPart,ModelPart>)pf.get(m);
  Class<?> scopeType=Class.forName("dev.settledlands.model.PlayerMotionBridge$Scope");Constructor<?> ctor=scopeType.getDeclaredConstructor(BbModel.class,LivingEntity.class,PoseStack.class);ctor.setAccessible(true);Object scope=ctor.newInstance(m,player,stack);
  Field tf=PlayerMotionBridge.class.getDeclaredField("SCOPES");tf.setAccessible(true);@SuppressWarnings("unchecked") ThreadLocal<Deque<Object>> local=(ThreadLocal<Deque<Object>>)tf.get(null);Deque<Object> stackScopes=new ArrayDeque<>();stackScopes.push(scope);local.set(stackScopes);
  PlayerMotionBridge.before(m,player,stack,.5f,parents);PlayerMotionBridge.setup(m,false,true,"","",false,0,0,0,0,0,0,0,.5f);
  ok(Math.abs(m.getPart("head").xRot)<.0001,"active bridge skips real native animation");ok(Probe.events.containsAll(List.of("before","vanilla","record","apply")),"real model + bridge active");
  local.remove();stack.popPose();ok(stack.depth()==1,"fixture manually unwound scope");System.out.println("PASS real BbModel/BbAnimation: native and NPC clip retained; active player scope bypasses clip (synthetic game/TnM)");
 }
}
