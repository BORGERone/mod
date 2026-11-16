package net.minecraft.world.entity;

/**
 * Compile-time mirror (Build 348) of the pathfinding mob.
 *
 * <p>Earlier mirrors hung {@code PathfinderMob} straight off {@link LivingEntity} because nothing had
 * to reach a mob member from an NPC yet. Build 348 puts equipment on the NPC, so the chain has to be
 * the game's own: {@code GuideNpc -> PathfinderMob -> Mob -> LivingEntity}.</p>
 */
public class PathfinderMob extends Mob {
}
