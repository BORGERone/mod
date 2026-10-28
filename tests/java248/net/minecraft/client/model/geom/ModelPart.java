package net.minecraft.client.model.geom;
import java.util.*;
public final class ModelPart {
 public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1; private final Map<String,ModelPart> children;
 public ModelPart(List<?> cubes,Map<String,ModelPart> children){this.children=children;}
 public boolean hasChild(String name){return children.containsKey(name);}
 public void setPos(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
 public void setRotation(float x,float y,float z){xRot=x;yRot=y;zRot=z;}
}
