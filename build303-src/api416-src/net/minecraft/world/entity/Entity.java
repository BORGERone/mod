package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Compile-time mirror (Build 416): {@code api-src}'s {@code Entity} plus {@link #isInvisible()},
 * declared on {@code Entity} in Minecraft 1.21.1 and already referenced in the base jar
 * ({@code GuideTriggerNpc#isInvisible()}, {@code AbstractClientPlayer#isInvisible()}) and in
 * Hackers 'n Slashers ({@code StealthManager#calculateSightPoints}).
 */
public class Entity {
    public int tickCount;
    public boolean horizontalCollision;
    public float yRotO;
    public float xRotO;

    public boolean isAlive() { return true; }
    public boolean isRemoved() { return false; }
    public boolean isPassenger() { return false; }
    public boolean isInvisible() { return false; }
    public boolean isShiftKeyDown() { return false; }
    public Level level() { return null; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public float getYRot() { return 0; }
    public double distanceToSqr(Entity other) { return 0; }
    public Component getName() { return null; }
    public UUID getUUID() { return null; }
    public net.minecraft.world.phys.Vec3 getDeltaMovement() { return null; }
    public double distanceToSqr(double x, double y, double z) { return 0; }
    public net.minecraft.world.phys.AABB getBoundingBox() { return null; }
    public final void moveTo(double x, double y, double z) {}
    public void setDeltaMovement(double x, double y, double z) {}
    public double getEyeY() { return 0; }
    public void setYRot(float yRot) {}
    public void setXRot(float xRot) {}
}
