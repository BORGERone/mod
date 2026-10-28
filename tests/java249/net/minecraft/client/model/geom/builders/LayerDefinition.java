package net.minecraft.client.model.geom.builders;
import java.util.*;import net.minecraft.client.model.geom.ModelPart;
public class LayerDefinition {
 public static LayerDefinition create(MeshDefinition mesh,int w,int h){if(w!=64||h!=64)throw new AssertionError("texture dimensions");return new LayerDefinition();}
 public ModelPart bakeRoot(){Map<String,ModelPart> children=new LinkedHashMap<>();for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"})children.put(n,new ModelPart(List.of(),Map.of()));ModelPart root=new ModelPart(List.of(),children);
  children.get("right_arm").setPos(-5,2,0);children.get("left_arm").setPos(5,2,0);children.get("right_leg").setPos(-1.9f,12,0);children.get("left_leg").setPos(1.9f,12,0);children.values().forEach(ModelPart::saveRest);return root;}
}
