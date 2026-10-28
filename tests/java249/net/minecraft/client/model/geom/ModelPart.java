package net.minecraft.client.model.geom;
import java.util.*;import java.util.stream.Stream;
public final class ModelPart {
 public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1; private final Map<String,ModelPart> children; private float[] rest={0,0,0,0,0,0};
 public ModelPart(List<?> cubes,Map<String,ModelPart> children){this.children=children;}
 public boolean hasChild(String name){return children.containsKey(name);} public ModelPart getChild(String name){return children.get(name);}
 public void setPos(float x,float y,float z){this.x=x;this.y=y;this.z=z;} public void setRotation(float x,float y,float z){xRot=x;yRot=y;zRot=z;}
 public void saveRest(){rest=new float[]{x,y,z,xRot,yRot,zRot};} public void resetPose(){x=rest[0];y=rest[1];z=rest[2];xRot=rest[3];yRot=rest[4];zRot=rest[5];xScale=yScale=zScale=1;}
 public void render(com.mojang.blaze3d.vertex.PoseStack s,com.mojang.blaze3d.vertex.VertexConsumer c,int light,int overlay,int color){testing.Probe.events.add("actual-draw");}
 public Stream<ModelPart> getAllParts(){return Stream.concat(Stream.of(this),children.values().stream().flatMap(ModelPart::getAllParts));}
}
