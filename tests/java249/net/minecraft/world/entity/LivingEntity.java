package net.minecraft.world.entity;
import net.minecraft.world.item.ItemStack;import net.minecraft.world.phys.Vec3;
public class LivingEntity extends Entity {
 public boolean baby,alive=true,swinging;public float attack=.33f,yBodyRotO,yBodyRot,yHeadRotO,yHeadRot,xRotO;public int tickCount,hurtTime;public WalkAnimationState walkAnimation=new WalkAnimationState();
 public float getAttackAnim(float partial){return attack+partial*.01f;}public boolean isBaby(){return baby;}public boolean isAlive(){return alive;}
 public float getXRot(){return 0;}public float getYRot(){return 0;}public double getY(){return 64;}public boolean onGround(){return true;}public boolean isInWater(){return false;}public boolean isSwimming(){return false;}public boolean isSprinting(){return false;}public boolean onClimbable(){return false;}public boolean isBlocking(){return false;}public boolean isUsingItem(){return false;}public ItemStack getUseItem(){return ItemStack.EMPTY;}public ItemStack getMainHandItem(){return ItemStack.EMPTY;}public ItemStack getOffhandItem(){return ItemStack.EMPTY;}public Vec3 getDeltaMovement(){return new Vec3(0,0,0);}public Vec3 getViewVector(float p){return new Vec3(0,0,1);}public float getHealth(){return 20;}
}
