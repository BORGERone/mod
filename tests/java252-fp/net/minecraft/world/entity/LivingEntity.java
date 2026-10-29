package net.minecraft.world.entity;
import net.minecraft.world.item.ItemStack;import net.minecraft.world.phys.Vec3;
public class LivingEntity extends Entity {
 public boolean baby,alive=true,swinging,fallFlying,visualSwimming,inWater,inFluid,blocking,crouching,sleeping,spectator,invisible;
 public float swim,pitch,yaw,yRotO,eyeHeight=1.62f;
 public double px,py,pz;
 public ItemStack main=ItemStack.EMPTY,off=ItemStack.EMPTY;
 public float attack=.33f,yBodyRotO,yBodyRot,yHeadRotO,yHeadRot,xRotO;
 public int tickCount,hurtTime;
 public WalkAnimationState walkAnimation=new WalkAnimationState();
 public float getAttackAnim(float partial){return attack+partial*.01f;}
 public boolean isBaby(){return baby;}
 public boolean isAlive(){return alive;}
 public boolean isCrouching(){return crouching;}
 public boolean isSleeping(){return sleeping;}
 public boolean isSpectator(){return spectator;}
 public boolean isInvisible(){return invisible;}
 public float getXRot(){return pitch;}
 public float getYRot(){return yaw;}
 public float getViewXRot(float t){return xRotO+t*(pitch-xRotO);}
 public float getViewYRot(float t){return yRotO+t*(yaw-yRotO);}
 public float getEyeHeight(){return eyeHeight;}
 public float getSwimAmount(float t){return swim;}
 public boolean isFallFlying(){return fallFlying;}
 public boolean isVisuallySwimming(){return visualSwimming;}
 public boolean isInFluidType(java.util.function.BiPredicate<net.neoforged.neoforge.fluids.FluidType,Double> f){return inFluid && f.test(new net.neoforged.neoforge.fluids.FluidType(),1.0);}
 public boolean canSwimInFluidType(net.neoforged.neoforge.fluids.FluidType f){return true;}
 public double getX(){return px;}
 public double getY(){return py;}
 public double getZ(){return pz;}
 public boolean onGround(){return true;}
 public boolean isInWater(){return inWater;}
 public boolean isSwimming(){return false;}
 public boolean isSprinting(){return false;}
 public boolean onClimbable(){return false;}
 public boolean isBlocking(){return blocking;}
 public boolean isUsingItem(){return false;}
 public ItemStack getUseItem(){return ItemStack.EMPTY;}
 public ItemStack getMainHandItem(){return main;}
 public ItemStack getOffhandItem(){return off;}
 public Vec3 getDeltaMovement(){return new Vec3(0,0,0);}
 public Vec3 getViewVector(float p){return new Vec3(0,0,1);}
 public float getHealth(){return 20;}
}
