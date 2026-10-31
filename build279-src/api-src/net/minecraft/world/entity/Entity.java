package net.minecraft.world.entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.util.UUID;
public class Entity {
    public int tickCount;
    public boolean isAlive() { return true; }
    public boolean isRemoved() { return false; }
    public Level level() { return null; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public float getYRot() { return 0; }
    public double distanceToSqr(Entity other) { return 0; }
    public Component getName() { return null; }
    public UUID getUUID() { return null; }
    public net.minecraft.world.phys.Vec3 getDeltaMovement() { return null; }
    public void setDeltaMovement(double x, double y, double z) {}
}
