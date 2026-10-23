package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.NMLConfig;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Horde Climbing: when the prey climbed out of reach, the horde piles up under it.
 *
 * <p>Three cooperating pieces (this goal drives the first two, the entity tick
 * provides the third):
 * <ol>
 *   <li><b>Gather</b> - navigate to the spot directly below the target instead of
 *       endlessly pathing into the pillar/wall the target stands on.</li>
 *   <li><b>Climb</b> - while pressed against a wall or another undead and the target
 *       is above, push upwards (the Java port of Bedrock navigation.climb).</li>
 *   <li><b>Pile support</b> - {@link UndeadEntity} lets undead stand on each other's
 *       heads, so the pile actually gains height instead of sinking through.</li>
 * </ol>
 *
 * <p>Cost control: the goal only activates with a live target above, the nearby-horde
 * query runs at most every 5 ticks (staggered per entity), and all state is cached
 * in fields - no per-tick allocations.
 */
public class HordeClimbGoal extends Goal {
    /** Target must be at least this many blocks above to count as "climbed away". */
    private static final double MIN_HEIGHT_GAIN = 1.2D;
    private static final double CLIMB_SPEED = 0.25D;
    private static final int QUERY_INTERVAL = 5;

    private final UndeadEntity mob;
    private int repathCooldown;
    private int queryCooldown;
    private boolean hordeMateAhead;

    public HordeClimbGoal(UndeadEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!NMLConfig.HORDE_CLIMBING.getAsBoolean() || this.mob.isCorpse()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && isTargetAbove(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.queryCooldown = this.mob.getId() % QUERY_INTERVAL; // stagger horde queries
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Gather under the target; repath on a timer, vanilla navigation does the rest.
        if (this.repathCooldown-- <= 0) {
            this.repathCooldown = 20;
            this.mob.getNavigation().moveTo(target.getX(), this.mob.getY(), target.getZ(), 1.0D);
        }

        if (this.queryCooldown-- <= 0) {
            this.queryCooldown = QUERY_INTERVAL;
            this.hordeMateAhead = hasHordeMateAhead(target);
        }

        // Climb when pressed against a wall or a horde mate and still below the prey.
        Vec3 movement = this.mob.getDeltaMovement();
        if ((this.mob.horizontalCollision || this.hordeMateAhead) && isTargetAbove(target)) {
            double forwardX = target.getX() - this.mob.getX();
            double forwardZ = target.getZ() - this.mob.getZ();
            double length = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
            double push = length > 0.001D ? 0.12D / length : 0.0D;
            this.mob.setDeltaMovement(
                    movement.x * 0.6D + forwardX * push,
                    Math.max(movement.y, CLIMB_SPEED),
                    movement.z * 0.6D + forwardZ * push);
            this.mob.fallDistance = 0.0F;
        }
    }

    /** Is there another undead in the direction of the target, close enough to climb onto? */
    private boolean hasHordeMateAhead(LivingEntity target) {
        List<UndeadEntity> mates = this.mob.level().getEntitiesOfClass(UndeadEntity.class,
                this.mob.getBoundingBox().inflate(1.1D, 1.5D, 1.1D),
                u -> u != this.mob && !u.isCorpse() && u.isAlive());
        double dirX = target.getX() - this.mob.getX();
        double dirZ = target.getZ() - this.mob.getZ();
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001D) {
            return !mates.isEmpty();
        }
        dirX /= length;
        dirZ /= length;
        for (UndeadEntity mate : mates) {
            double toMateX = mate.getX() - this.mob.getX();
            double toMateZ = mate.getZ() - this.mob.getZ();
            // Mate is roughly in front of us and its head is at or above our feet - a step up.
            if (toMateX * dirX + toMateZ * dirZ > 0.0D && mate.getY() + 1.8D > this.mob.getY()) {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetAbove(LivingEntity target) {
        return target.getY() > this.mob.getY() + MIN_HEIGHT_GAIN;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }
}
