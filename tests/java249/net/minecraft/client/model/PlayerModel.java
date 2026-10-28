package net.minecraft.client.model;
import net.minecraft.world.entity.LivingEntity;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.client.model.geom.builders.*;
public class PlayerModel<T extends LivingEntity> extends HumanoidModel<T> {
 public PlayerModel(ModelPart root,boolean slim){body=root.getChild("body");head=root.getChild("head");rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");}
 public static MeshDefinition createMesh(CubeDeformation c,boolean slim){if(slim)throw new AssertionError("unexpected slim");return new MeshDefinition();}
 public void setupAnim(T e,float a,float b,float c,float d,float f){super.setupAnim(e,a,b,c,d,f);}
}
