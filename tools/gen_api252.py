#!/usr/bin/env python3
"""Compile-only Minecraft/NeoForge/PAL/EMF API silhouettes for 252; never package in a mod jar."""
import pathlib,sys
import gen_api251

SRC=dict(gen_api251.SRC)
SRC.update({
'net/minecraft/client/CameraType.java':'''package net.minecraft.client; public enum CameraType { FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_FRONT; public boolean isFirstPerson(){return this==FIRST_PERSON;} }''',
'net/minecraft/client/Options.java':'''package net.minecraft.client; public class Options { public CameraType getCameraType(){return CameraType.FIRST_PERSON;} }''',
'net/minecraft/client/player/AbstractClientPlayer.java':'''package net.minecraft.client.player; import net.minecraft.world.entity.player.Player; import net.minecraft.client.resources.PlayerSkin; public class AbstractClientPlayer extends Player { public PlayerSkin getSkin(){return null;} }''',
'net/minecraft/client/player/LocalPlayer.java':'''package net.minecraft.client.player; public class LocalPlayer extends AbstractClientPlayer {}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client; import net.minecraft.client.model.geom.EntityModelSet; import net.minecraft.client.player.LocalPlayer; import net.minecraft.client.renderer.entity.EntityRenderDispatcher; import net.minecraft.world.entity.Entity; public class Minecraft { public LocalPlayer player; public Options options; public static Minecraft getInstance(){return null;} public Entity getCameraEntity(){return player;} public EntityRenderDispatcher getEntityRenderDispatcher(){return null;} public EntityModelSet getEntityModels(){return null;} }''',
'net/neoforged/neoforge/client/event/RenderPlayerEvent.java':'''package net.neoforged.neoforge.client.event; import net.minecraft.world.entity.player.Player; public class RenderPlayerEvent { public Player getEntity(){return null;} public static class Pre extends RenderPlayerEvent {} }''',
'net/minecraft/client/model/HumanoidModel.java':'''package net.minecraft.client.model; import net.minecraft.client.model.geom.ModelPart; import net.minecraft.world.entity.LivingEntity; public class HumanoidModel<T extends LivingEntity> extends EntityModel<T> { public ModelPart body,head,hat,rightArm,leftArm,rightLeg,leftLeg; public boolean crouching; public ArmPose rightArmPose,leftArmPose; public enum ArmPose { EMPTY,ITEM } public void prepareMobModel(T e,float a,float b,float c){} public void setupAnim(T e,float a,float b,float c,float d,float f){} }''',
'net/minecraft/client/model/PlayerModel.java':'''package net.minecraft.client.model; import net.minecraft.client.model.geom.ModelPart; import net.minecraft.client.model.geom.builders.*; import net.minecraft.world.entity.LivingEntity; public class PlayerModel<T extends LivingEntity> extends HumanoidModel<T> { public ModelPart leftSleeve,rightSleeve,leftPants,rightPants,jacket; public PlayerModel(ModelPart root,boolean slim){} public static MeshDefinition createMesh(CubeDeformation c,boolean slim){return null;} public void setupAnim(T e,float a,float b,float c,float d,float f){} }''',
'net/minecraft/client/model/geom/ModelPart.java':'''package net.minecraft.client.model.geom; import java.util.*; import java.util.stream.Stream; public class ModelPart { public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1; public boolean visible=true,skipDraw; public ModelPart(List<?> cubes,Map<String,ModelPart> children){} public Stream<ModelPart> getAllParts(){return Stream.of(this);} public void resetPose(){} }''',
'traben/entity_model_features/models/parts/EMFModelPart.java':'''package traben.entity_model_features.models.parts; import net.minecraft.client.model.geom.ModelPart; import java.util.*; public class EMFModelPart extends ModelPart { public boolean isSetByAnimation; public EMFModelPart(){super(List.of(),Map.of());} }''',
'traben/entity_model_features/models/animation/state/EMFEntityRenderState.java':'''package traben.entity_model_features.models.animation.state; import java.util.UUID; import traben.entity_model_features.utils.EMFEntity; import traben.entity_texture_features.features.state.ETFEntityRenderState; public interface EMFEntityRenderState extends ETFEntityRenderState { EMFEntity emfEntity(); boolean isFirstPersonHand(); boolean skipModelVariate(); UUID uuid(); float limbAngle(); void setLimbAngle(float v); float limbDistance(); void setLimbDistance(float v); float headYaw(); void setHeadYaw(float v); float headPitch(); void setHeadPitch(float v); }''',
'com/zigythebird/playeranimcore/api/firstPerson/FirstPersonMode.java':'''package com.zigythebird.playeranimcore.api.firstPerson; public enum FirstPersonMode { NONE, VANILLA, THIRD_PERSON_MODEL, DISABLED; public static boolean isFirstPersonPass(){return false;} public static void setFirstPersonPass(boolean v){} }''',
'com/zigythebird/playeranimcore/animation/layered/AnimationStack.java':'''package com.zigythebird.playeranimcore.animation.layered; import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode; public class AnimationStack implements IAnimation { public boolean isActive(){return false;} public FirstPersonMode getFirstPersonMode(){return FirstPersonMode.NONE;} }''',
'com/zigythebird/playeranim/animation/PlayerAnimManager.java':'''package com.zigythebird.playeranim.animation; import com.zigythebird.playeranimcore.animation.layered.AnimationStack; import com.zigythebird.playeranimcore.bones.PlayerAnimBone; import net.minecraft.client.model.geom.ModelPart; public class PlayerAnimManager extends AnimationStack { public float getTickDelta(){return 0;} public PlayerAnimBone get3DTransform(PlayerAnimBone b){return b;} public void updatePart(ModelPart first,ModelPart second,PlayerAnimBone b){} }''',
})

def main(dest):
 root=pathlib.Path(dest)
 for name,content in SRC.items():
  path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content+'\n',encoding='utf8')
 print('wrote',len(SRC),'252 compile-only silhouettes')

if __name__=='__main__':main(sys.argv[1])
