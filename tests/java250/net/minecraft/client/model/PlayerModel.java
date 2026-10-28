package net.minecraft.client.model;
import net.minecraft.world.entity.LivingEntity;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.client.model.geom.builders.*;
import traben.entity_model_features.models.*;import traben.entity_model_features.models.parts.EMFModelPartRoot;
public class PlayerModel<T extends LivingEntity> extends HumanoidModel<T> implements IEMFModel {
 private final ModelPart root;
 public PlayerModel(ModelPart root,boolean slim){this.root=root;body=root.getChild("body");head=root.getChild("head");rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");}
 public static MeshDefinition createMesh(CubeDeformation c,boolean slim){return new MeshDefinition();}
 public void setupAnim(T e,float a,float b,float c,float d,float f){super.setupAnim(e,a,b,c,d,f);}
 public boolean emf$isEMFModel(){return root instanceof EMFModelPartRoot;}
 public EMFModelPartRoot emf$getEMFRootModel(){return root instanceof EMFModelPartRoot r?r:null;}
}
