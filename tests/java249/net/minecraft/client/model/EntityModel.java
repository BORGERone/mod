package net.minecraft.client.model;import net.minecraft.world.entity.Entity;
public abstract class EntityModel<T extends Entity> {public boolean riding,young;public float attackTime; public void prepareMobModel(T e,float a,float b,float c){} public abstract void setupAnim(T e,float a,float b,float c,float d,float f);}
