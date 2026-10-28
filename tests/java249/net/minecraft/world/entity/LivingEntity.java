package net.minecraft.world.entity;
public class LivingEntity extends Entity { public boolean baby,alive=true; public float attack=.33f; public float getAttackAnim(float partial){return attack+partial*.01f;} public boolean isBaby(){return baby;} public boolean isAlive(){return alive;} }
