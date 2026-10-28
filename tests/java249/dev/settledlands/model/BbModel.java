package dev.settledlands.model;
import java.util.*;import com.mojang.blaze3d.vertex.PoseStack;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.client.renderer.MultiBufferSource;import net.minecraft.world.entity.LivingEntity;import org.joml.Matrix4f;import testing.Probe;
/** Synthetic hierarchy uses the actual Ivy bbmodel pivots, not actual Minecraft geometry. */
public final class BbModel {
 public final IdentityHashMap<ModelPart,ModelPart> parents=new IdentityHashMap<>(); public final Map<String,ModelPart> parts=new LinkedHashMap<>(); public String name="ivy";
 public Matrix4f drawnStack; public final Map<String,Matrix4f> drawn=new HashMap<>(); public Runnable nested; public float swing=2f,amount=.75f,age=16.5f,headYaw=25f,pitch=-10f;
 public BbModel(){
  Map<String,ModelPart> r=new LinkedHashMap<>(),rootC=new LinkedHashMap<>(),bodyC=new LinkedHashMap<>(),waistC=new LinkedHashMap<>(),headC=new LinkedHashMap<>(),armC=new LinkedHashMap<>();
  ModelPart outer=add("_root",r,null),root=add("root",rootC,outer),body=add("body",bodyC,root),waist=add("waist",waistC,body);
  ModelPart head=add("head",headC,waist);add("bone",Map.of(),head);add("backpack",Map.of(),waist);
  ModelPart ra=add("rightarm",armC,waist);add("rightarmitemlocator",Map.of(),ra);add("leftarm",Map.of(),waist);add("rightleg",Map.of(),waist);add("leftleg",Map.of(),waist);
  root.setPos(-1,24,0);body.setPos(1,-18,0);head.setPos(0,-6,1);parts.get("bone").setPos(0,-5.5f,0);parts.get("backpack").setPos(0,-1.275f,4.1f);
  ra.setPos(-5,-5,1);parts.get("rightarmitemlocator").setPos(-1,10,0);parts.get("leftarm").setPos(5,-5,1);parts.get("rightleg").setPos(-2,6,1);parts.get("leftleg").setPos(2,6,1);
  parts.values().forEach(ModelPart::saveRest);
 }
 private ModelPart add(String n,Map<String,ModelPart> children,ModelPart parent){ModelPart p=new ModelPart(List.of(),children);parts.put(n,p);if(parent!=null){parents.put(p,parent);((Map<String,ModelPart>)getChildren(parent)).put(n,p);}return p;}
 // Fixture-only helper; maps remain mutable while hierarchy is constructed.
 private Map<String,ModelPart> getChildren(ModelPart parent){try{var f=ModelPart.class.getDeclaredField("children");f.setAccessible(true);return (Map<String,ModelPart>)f.get(parent);}catch(Exception e){throw new RuntimeException(e);}}
 public String getName(){return name;} public ModelPart getPart(String n){return parts.get(n.toLowerCase(Locale.ROOT));} public ModelPart getRootPart(){return parts.get("_root");}
 public Matrix4f world(String n){Deque<ModelPart> chain=new ArrayDeque<>();ModelPart p=parts.get(n);while(p!=null){chain.addFirst(p);p=parents.get(p);}Matrix4f m=new Matrix4f();for(ModelPart q:chain)m.translate(q.x/16,q.y/16,q.z/16).rotate(new org.joml.Quaternionf().rotationZYX(q.zRot,q.yRot,q.xRot)).scale(q.xScale,q.yScale,q.zScale);return m;}
 public void setupAnimRaw(boolean a,boolean b,String c,String d,boolean e,int f,int g,float h,float i,float j,float k,float l,float t){Probe.nativeCount++;Probe.events.add("native");getRootPart().getAllParts().forEach(ModelPart::resetPose);getPart("head").xRot=.91f;}
 public void settledlands$renderActorOriginal(LivingEntity e,float scale,float partial,PoseStack p,MultiBufferSource b,int light){
  p.pushPose();p.translate(0,1,0);PlayerMotionBridge.before(this,e,p,partial,parents);
  if(nested!=null){Runnable n=nested;nested=null;n.run();}
  PlayerMotionBridge.setup(this,false,true,"","",false,0,0,swing,amount,age,headYaw,pitch,partial);
  drawnStack=new Matrix4f(p.matrix());Probe.drawNormal=new org.joml.Matrix3f(p.last().normal());for(String n:parts.keySet())drawn.put(n,world(n));Probe.events.add("draw");if(Probe.failRender)throw new IllegalStateException("original renderer failed");p.popPose();
 }
}
