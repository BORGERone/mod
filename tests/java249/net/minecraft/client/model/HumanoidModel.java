package net.minecraft.client.model;
import net.minecraft.world.entity.LivingEntity;import net.minecraft.client.model.geom.ModelPart;
public class HumanoidModel<T extends LivingEntity> extends EntityModel<T> {
 public ModelPart body,head,rightArm,leftArm,rightLeg,leftLeg; public boolean crouching; public ArmPose rightArmPose=ArmPose.EMPTY,leftArmPose=ArmPose.EMPTY; public enum ArmPose{EMPTY,ITEM}
 public void prepareMobModel(T e,float a,float b,float c){testing.Probe.events.add("prepare");testing.Probe.values=new float[]{a,b,c};}
 public void setupAnim(T e,float a,float b,float c,float d,float f){
  testing.Probe.events.add("vanilla");testing.Probe.values=new float[]{a,b,c,d,f,attackTime,riding?1:0,young?1:0,crouching?1:0,rightArmPose==ArmPose.ITEM?1:0,leftArmPose==ArmPose.ITEM?1:0};
  head.yRot=d*.017453292f;head.xRot=f*.017453292f;body.xRot=crouching?.4f:0;
  rightArm.xRot=-a*b*.1f+(rightArmPose==ArmPose.ITEM?.2f:0);leftArm.xRot=a*b*.1f+(leftArmPose==ArmPose.ITEM?.3f:0);
  rightLeg.xRot=a*b*.14f;leftLeg.xRot=-a*b*.14f;if(riding){rightLeg.xRot=-1.4f;leftLeg.xRot=-1.4f;}
 }
}
