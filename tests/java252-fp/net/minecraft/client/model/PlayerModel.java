package net.minecraft.client.model;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
import traben.entity_model_features.models.*;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
public class PlayerModel<T extends LivingEntity> extends HumanoidModel<T> implements IEMFModel {
 private final ModelPart root;
 public final boolean slim;
 public ModelPart leftSleeve,rightSleeve,leftPants,rightPants,jacket;
 public PlayerModel(ModelPart root,boolean slim){
  this.root=root;this.slim=slim;
  body=root.getChild("body");head=root.getChild("head");hat=root.getChild("hat");
  rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");
  rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");
  jacket=root.getChild("jacket");rightSleeve=root.getChild("right_sleeve");leftSleeve=root.getChild("left_sleeve");
  rightPants=root.getChild("right_pants");leftPants=root.getChild("left_pants");
 }
 public static MeshDefinition createMesh(CubeDeformation c,boolean slim){return new MeshDefinition();}
 public void setupAnim(T e,float a,float b,float c,float d,float f){
  super.setupAnim(e,a,b,c,d,f);
  if(jacket!=null){jacket.x=body.x;jacket.y=body.y;jacket.z=body.z;jacket.xRot=body.xRot;jacket.yRot=body.yRot;jacket.zRot=body.zRot;}
  if(rightSleeve!=null){rightSleeve.x=rightArm.x;rightSleeve.y=rightArm.y;rightSleeve.z=rightArm.z;rightSleeve.xRot=rightArm.xRot;rightSleeve.yRot=rightArm.yRot;rightSleeve.zRot=rightArm.zRot;}
  if(leftSleeve!=null){leftSleeve.x=leftArm.x;leftSleeve.y=leftArm.y;leftSleeve.z=leftArm.z;leftSleeve.xRot=leftArm.xRot;leftSleeve.yRot=leftArm.yRot;leftSleeve.zRot=leftArm.zRot;}
  if(rightPants!=null){rightPants.x=rightLeg.x;rightPants.y=rightLeg.y;rightPants.z=rightLeg.z;rightPants.xRot=rightLeg.xRot;rightPants.yRot=rightLeg.yRot;rightPants.zRot=rightLeg.zRot;}
  if(leftPants!=null){leftPants.x=leftLeg.x;leftPants.y=leftLeg.y;leftPants.z=leftLeg.z;leftPants.xRot=leftLeg.xRot;leftPants.yRot=leftLeg.yRot;leftPants.zRot=leftLeg.zRot;}
 }
 @Override
 public void translateToHand(HumanoidArm arm,PoseStack stack){
  ModelPart part=arm==HumanoidArm.LEFT?leftArm:rightArm;
  if(slim){
   float offset=0.5f*(arm==HumanoidArm.LEFT?-1f:1f);
   part.x+=offset;
   part.translateAndRotate(stack);
   part.x-=offset;
   if(com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode.isFirstPersonPass()){
    stack.translate(offset/16f,0,0);
   }
  }else{
   part.translateAndRotate(stack);
  }
 }
 public void setAllVisible(boolean v){
  for(ModelPart p:new ModelPart[]{head,hat,body,jacket,rightArm,rightSleeve,leftArm,leftSleeve,rightLeg,rightPants,leftLeg,leftPants}){
   if(p!=null)p.visible=v;
  }
 }
 public boolean emf$isEMFModel(){return root instanceof EMFModelPartRoot;}
 public EMFModelPartRoot emf$getEMFRootModel(){return root instanceof EMFModelPartRoot r?r:null;}
}
