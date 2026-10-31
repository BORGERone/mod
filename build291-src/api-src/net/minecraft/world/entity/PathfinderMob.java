package net.minecraft.world.entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
public class PathfinderMob extends LivingEntity {
    public PathNavigation getNavigation() { return null; }
    public void setTarget(LivingEntity target) {}
    public boolean isTalking() { return false; }
    public boolean isLeashed() { return false; }
}
