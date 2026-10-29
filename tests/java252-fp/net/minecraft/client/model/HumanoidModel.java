package net.minecraft.client.model;
import java.util.function.Function;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import com.zigythebird.playeranim.accessors.IMutableModel;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
public class HumanoidModel<T extends LivingEntity> extends EntityModel<T> implements IMutableModel {
 public ModelPart head,hat,body,rightArm,leftArm,rightLeg,leftLeg;
 public boolean crouching,riding,young;
 public float swimAmount;
 private PlayerAnimManager palAnim;
 public HumanoidModel(){}
 public HumanoidModel(ModelPart root){this(root,null);}
 public HumanoidModel(ModelPart root,Function<?,?> renderType){
  head=root.getChild("head");hat=root.getChild("hat");body=root.getChild("body");
  rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");
  rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");
 }
 public void setupAnim(T e,float a,float b,float c,float d,float f){}
 public void translateToHand(HumanoidArm arm,PoseStack stack){
  (arm==HumanoidArm.LEFT?leftArm:rightArm).translateAndRotate(stack);
 }
 public void playerAnimLib$setAnimation(PlayerAnimManager a){palAnim=a;}
 public PlayerAnimManager playerAnimLib$getAnimation(){return palAnim;}
}
