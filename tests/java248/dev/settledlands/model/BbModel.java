package dev.settledlands.model;
import java.util.*;import com.mojang.blaze3d.vertex.PoseStack;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.client.renderer.MultiBufferSource;import net.minecraft.world.entity.LivingEntity;import org.joml.Matrix4f;import testing.Probe;
public final class BbModel {
 public final IdentityHashMap<ModelPart,ModelPart> parents=new IdentityHashMap<>();
 public final Map<String,ModelPart> parts=new HashMap<>(); public String name="ivy";
 public Matrix4f drawnStack; public final Map<String,Matrix4f> drawn=new HashMap<>(); public Runnable nested;
 public BbModel(){
  ModelPart root=add("_root",null),skel=add("root",root),body=add("body",skel),waist=add("waist",body);
  add("head",waist);add("rightarm",waist);add("leftarm",waist);add("rightleg",waist);add("leftleg",waist);add("rightarmitemlocator",parts.get("rightarm"));
  skel.x=-1;skel.y=24; body.y=-18; waist.xRot=.12f;body.zRot=.08f;
  parts.get("head").y=-6; parts.get("rightarm").x=-5;parts.get("rightarm").y=-5;parts.get("leftarm").x=5;parts.get("leftarm").y=-5;
  parts.get("rightleg").x=-2;parts.get("rightleg").y=6;parts.get("leftleg").x=2;parts.get("leftleg").y=6;parts.get("rightarm").xRot=.4f;
  parts.get("rightarmitemlocator").y=10;
 }
 private ModelPart add(String n,ModelPart parent){ModelPart p=new ModelPart(List.of(),Map.of());parts.put(n,p);if(parent!=null)parents.put(p,parent);return p;}
 public String getName(){return name;}
 public ModelPart getPart(String n){return parts.get(n.toLowerCase(Locale.ROOT));}
 public ModelPart getRootPart(){return parts.get("_root");}
 public Matrix4f world(String n){Deque<ModelPart> chain=new ArrayDeque<>();ModelPart p=parts.get(n);while(p!=null){chain.addFirst(p);p=parents.get(p);}Matrix4f m=new Matrix4f();for(ModelPart q:chain)m.translate(q.x/16,q.y/16,q.z/16).rotate(new org.joml.Quaternionf().rotationZYX(q.zRot,q.yRot,q.xRot)).scale(q.xScale,q.yScale,q.zScale);return m;}
 public void settledlands$renderActorOriginal(LivingEntity e,float scale,float partial,PoseStack p,MultiBufferSource b,int light){
  p.pushPose(); p.translate(0,1,0);
  TouchMotionBridge.before(this,e,p,partial,parents);
  Probe.events.add("native");
  if(nested!=null){Runnable call=nested;nested=null;call.run();}
  TouchMotionBridge.after(this,e,p,parents);
  drawnStack=new Matrix4f(p.matrix());Probe.drawNormal=new org.joml.Matrix3f(p.last().normal());for(String n:parts.keySet())drawn.put(n,world(n));Probe.events.add("draw");
  if(Probe.failRender)throw new IllegalStateException("original renderer failed");
  p.popPose();
 }
}
