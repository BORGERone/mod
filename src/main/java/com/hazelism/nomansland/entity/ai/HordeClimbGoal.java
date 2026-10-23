package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.NMLConfig;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Horde Climbing: when the prey climbed out of reach, the horde piles up under it.
 *
 * <p>Tuned to feel like a writhing pile, not an elevator:
 * <ul>
 *   <li>Climbing is <b>slow</b> (0.14/tick upward) and needs <b>support</b>: either
 *       a horde mate directly below (standing on someone) or at least two more
 *       undead crowding within 4 blocks. A lone zombie cannot scale a wall.</li>
 *   <li><b>Fatigue</b> ports the original climb_disturb/climb_fail: after roughly
 *       1.75s of continuous climbing the zombie slips and cannot climb for 2s.
 *       Towers therefore grow in surges and sometimes partially collapse.</li>
 *   <li>While the goal runs, step height is raised (+0.5) so climbers can mount
 *       the top edge of the obstacle instead of clinging to its lip.</li>
 * </ul>
 *
 * <p>Cost: only active with a live target above; horde queries at most every
 * 5 ticks (staggered per entity); no allocations in tick.
 */
public class HordeClimbGoal extends Goal {
    private static final double MIN_HEIGHT_GAIN = 1.2D;
    private static final double CLIMB_SPEED = 0.14D;
    private static final int CLIMB_BUDGET_TICKS = 35;
    private static final int FATIGUE_TICKS = 40;
    private static final int QUERY_INTERVAL = 5;
    private static final AttributeModifier STEP_BOOST = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath("nomansland", "horde_climb_step"),
            0.5D, AttributeModifier.Operation.ADD_VALUE);

    private final UndeadEntity mob;
    private int repathCooldown;
    private int queryCooldown;
    private int climbTicks;
    private int fatigueTicks;
    private boolean hordeMateAhead;
    private boolean supportedFromBelow;
    private int hordeMatesNearby;

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
        this.climbTicks = 0;
        this.fatigueTicks = 0;
        this.queryCooldown = this.mob.getId() % QUERY_INTERVAL; // stagger horde queries
        var stepHeight = this.mob.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null && !stepHeight.hasModifier(STEP_BOOST.id())) {
            stepHeight.addTransientModifier(STEP_BOOST);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        var stepHeight = this.mob.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null) {
            stepHeight.removeModifier(STEP_BOOST.id());
        }
        this.climbTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.repathCooldown-- <= 0) {
            this.repathCooldown = 20;
            this.mob.getNavigation().moveTo(target.getX(), this.mob.getY(), target.getZ(), 1.0D);
        }

        if (this.queryCooldown-- <= 0) {
            this.queryCooldown = QUERY_INTERVAL;
            queryHorde(target);
        }

        if (this.mob.onGround()) {
            this.climbTicks = 0;
        }
        if (this.fatigueTicks > 0) {
            this.fatigueTicks--;
            return; // slipped off - scrabble at the base until recovered
        }

        boolean wantsClimb = (this.mob.horizontalCollision || this.hordeMateAhead) && isTargetAbove(target);
        boolean supported = this.supportedFromBelow || this.hordeMatesNearby >= 2;
        if (!wantsClimb || !supported) {
            return;
        }

        if (++this.climbTicks > CLIMB_BUDGET_TICKS) {
            this.climbTicks = 0;
            this.fatigueTicks = FATIGUE_TICKS; // the original's climb_fail moment
            return;
        }

        Vec3 movement = this.mob.getDeltaMovement();
        double forwardX = target.getX() - this.mob.getX();
        double forwardZ = target.getZ() - this.mob.getZ();
        double length = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        double push = length > 0.001D ? 0.10D / length : 0.0D;
        this.mob.setDeltaMovement(
                movement.x * 0.7D + forwardX * push,
                Math.max(movement.y, CLIMB_SPEED),
                movement.z * 0.7D + forwardZ * push);
        this.mob.fallDistance = 0.0F;
    }

    /** One staggered spatial query feeding all climb decisions for the next few ticks. */
    private void queryHorde(LivingEntity target) {
        List<UndeadEntity> near = this.mob.level().getEntitiesOfClass(UndeadEntity.class,
                this.mob.getBoundingBox().inflate(4.0D, 2.0D, 4.0D),
                u -> u != this.mob && !u.isCorpse() && u.isAlive());
        this.hordeMatesNearby = near.size();

        double dirX = target.getX() - this.mob.getX();
        double dirZ = target.getZ() - this.mob.getZ();
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        dirX = length > 0.001D ? dirX / length : 0.0D;
        dirZ = length > 0.001D ? dirZ / length : 0.0D;

        this.hordeMateAhead = false;
        this.supportedFromBelow = false;
        for (UndeadEntity mate : near) {
            double toMateX = mate.getX() - this.mob.getX();
            double toMateZ = mate.getZ() - this.mob.getZ();
            double horizontalDistSqr = toMateX * toMateX + toMateZ * toMateZ;
            // In front of us and head at/above our feet: a step to climb onto.
            if (horizontalDistSqr < 1.2D * 1.2D && toMateX * dirX + toMateZ * dirZ > 0.0D
                    && mate.getY() + mate.getBbHeight() > this.mob.getY()) {
                this.hordeMateAhead = true;
            }
            // Directly underneath, carrying us.
            if (horizontalDistSqr < 0.8D * 0.8D && mate.getY() + mate.getBbHeight() <= this.mob.getY() + 0.3D
                    && mate.getY() + mate.getBbHeight() > this.mob.getY() - 2.0D) {
                this.supportedFromBelow = true;
            }
        }
    }

    private boolean isTargetAbove(LivingEntity target) {
        return target.getY() > this.mob.getY() + MIN_HEIGHT_GAIN;
    }
}
