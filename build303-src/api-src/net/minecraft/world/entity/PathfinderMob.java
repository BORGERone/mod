package net.minecraft.world.entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
/**
 * Build 403: a pathfinding mob <b>is</b> a mob - {@code PathfinderMob extends Mob} in the game.
 *
 * <p>This mirror said {@code LivingEntity}, which is why the mod could not so much as name a guide's goal
 * list: {@code GuideNpc} is a {@code PathfinderMob}, and a field of {@code Mob} was unreachable from it in
 * the compile view even though it is there at run time.</p>
 */
public class PathfinderMob extends Mob {
    public PathNavigation getNavigation() { return null; }
    public void setTarget(LivingEntity target) {}
    public boolean isTalking() { return false; }
    public boolean isLeashed() { return false; }
}
