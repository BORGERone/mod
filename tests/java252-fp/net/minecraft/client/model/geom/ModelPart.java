package net.minecraft.client.model.geom;
import java.util.*;import java.util.stream.Stream;
public class ModelPart {
 public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1;
 public boolean visible=true,skipDraw=false;
 public final Map<String,ModelPart> children;
 private float[] rest={0,0,0,0,0,0};
 public ModelPart(List<?> cubes,Map<String,ModelPart> children){this.children=children;}
 public boolean hasChild(String name){return children.containsKey(name);}
 public ModelPart getChild(String name){return children.get(name);}
 public void setPos(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
 public void setRotation(float x,float y,float z){xRot=x;yRot=y;zRot=z;}
 public void saveRest(){rest=new float[]{x,y,z,xRot,yRot,zRot};}
 public PartPose getInitialPose(){return new PartPose(rest[0],rest[1],rest[2],rest[3],rest[4],rest[5]);}
 public void translateAndRotate(com.mojang.blaze3d.vertex.PoseStack s){
  s.translate(x/16f,y/16f,z/16f);
  if(xRot!=0f||yRot!=0f||zRot!=0f)s.mulPose(new org.joml.Quaternionf().rotationZYX(zRot,yRot,xRot));
  if(xScale!=1f||yScale!=1f||zScale!=1f)s.scale(xScale,yScale,zScale);
 }
 public void resetPose(){x=rest[0];y=rest[1];z=rest[2];xRot=rest[3];yRot=rest[4];zRot=rest[5];xScale=yScale=zScale=1;}
 public void render(com.mojang.blaze3d.vertex.PoseStack s,com.mojang.blaze3d.vertex.VertexConsumer c,int light,int overlay,int color){
  testing.Probe.events.add("actual-draw");
  if(testing.Probe251.throwOnDraw)throw new IllegalStateException("controlled draw failure");
  var callback=testing.Probe251.onDraw;testing.Probe251.onDraw=null;
  if(callback!=null)callback.accept(s);
  testing.Probe251.rootAtDraw=new org.joml.Matrix4f(s.matrix());
  testing.Probe251.normalAtDraw=s.last().transformNormal(1,2,3,new org.joml.Vector3f());
 }
 public Stream<ModelPart> getAllParts(){return Stream.concat(Stream.of(this),children.values().stream().flatMap(ModelPart::getAllParts));}
}
