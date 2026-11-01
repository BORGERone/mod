package net.minecraft.world.entity;

import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Compile-time mirror (Build 317).
 *
 * <p>The project profile only declares the members earlier builds happened to call. Build 317 also
 * needs the water test and the air supply, which every living entity has: the base jar already
 * calls {@code LivingEntity.isInWater()} itself, and {@code getAirSupply}/{@code setAirSupply}/
 * {@code getMaxAirSupply} are the pair vanilla drowns and revives entities with.</p>
 */
public class LivingEntity extends Entity {
    public float xxa;
    public float yya;
    public float zza;
    public WalkAnimationState walkAnimation = null;

    public MoveControl getMoveControl() { return null; }

    public boolean isInWater() { return false; }

    public int getAirSupply() { return 0; }

    public void setAirSupply(int air) { }

    public int getMaxAirSupply() { return 0; }
}
