package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.NMLConfig;
import com.hazelism.nomansland.entity.BannerBearerEntity;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

    private final Mob mob;
    private int repathCooldown;
    private int queryCooldown;
    private int climbTicks;
    private int fatigueTicks;
    private int mantleCooldown;
    private boolean hordeMateAhead;
    private boolean supportedFromBelow;
    private int hordeMatesNearby;

    public HordeClimbGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    /** Whom we are climbing toward. Undead use their hunt target; the banner bearer overrides this. */
    protected LivingEntity climbTarget() {
        return this.mob.getTarget();
    }

    @Override
    public boolean canUse() {
        if (!NMLConfig.HORDE_CLIMBING.getAsBoolean()) {
            return false;
        }
        LivingEntity target = climbTarget();
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
        LivingEntity target = climbTarget();
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
        if (!wantsClimb) {
            return;
        }
        // Mounting the lip comes first: step-up physics never triggers mid-air,
        // which is why climbers used to cling to the edge forever.
        if (tryMantle(target)) {
            return;
        }
        // The pile is the only support: a mate underneath or a crowd around.
        // No spider-style solo wall scaling - that was explicitly rejected.
        boolean supported = this.supportedFromBelow || this.hordeMatesNearby >= 2;
        if (!supported) {
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
        List<Mob> near = this.mob.level().getEntitiesOfClass(Mob.class,
                this.mob.getBoundingBox().inflate(4.0D, 2.0D, 4.0D),
                u -> u != this.mob && u.isAlive() && isHordeMember(u));
        this.hordeMatesNearby = near.size();

        double dirX = target.getX() - this.mob.getX();
        double dirZ = target.getZ() - this.mob.getZ();
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        dirX = length > 0.001D ? dirX / length : 0.0D;
        dirZ = length > 0.001D ? dirZ / length : 0.0D;

        this.hordeMateAhead = false;
        this.supportedFromBelow = false;
        for (Mob mate : near) {
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

    /**
     * If the obstacle in front of us tops out within reach, pull up and over it:
     * place the mob on the ledge (after a no-collision check) with a small forward
     * push. Runs at most once per 10 ticks per mob and only while pressed to a wall.
     */
    private boolean tryMantle(LivingEntity target) {
        if (this.mantleCooldown-- > 0 || !this.mob.horizontalCollision) {
            return false;
        }
        double dirX = target.getX() - this.mob.getX();
        double dirZ = target.getZ() - this.mob.getZ();
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001D) {
            return false;
        }
        dirX /= length;
        dirZ /= length;

        BlockPos front = BlockPos.containing(
                this.mob.getX() + dirX * 0.9D, this.mob.getY() + 0.2D, this.mob.getZ() + dirZ * 0.9D);
        if (isFree(front)) {
            return false; // nothing to mantle over in front of us
        }
        int topY = -1;
        for (int dy = 1; dy <= 3; dy++) {
            if (isFree(front.above(dy))) {
                topY = front.getY() + dy;
                break;
            }
        }
        if (topY < 0 || topY - this.mob.getY() > 1.4D || topY - this.mob.getY() < 0.1D) {
            return false; // ledge is not within arm's reach yet - keep climbing
        }

        double landX = this.mob.getX() + dirX * 0.75D;
        double landZ = this.mob.getZ() + dirZ * 0.75D;
        var landingBox = this.mob.getDimensions(this.mob.getPose())
                .makeBoundingBox(new net.minecraft.world.phys.Vec3(landX, topY, landZ));
        if (!this.mob.level().noCollision(this.mob, landingBox)) {
            return false;
        }
        this.mob.setPos(landX, topY, landZ);
        this.mob.setDeltaMovement(dirX * 0.18D, 0.06D, dirZ * 0.18D);
        this.mob.fallDistance = 0.0F;
        this.mantleCooldown = 10;
        this.climbTicks = 0;
        return true;
    }

    /** Members of the climbing horde: the undead mass plus its banner bearer. */
    public static boolean isHordeMember(Mob mob) {
        return mob instanceof UndeadEntity || mob instanceof BannerBearerEntity;
    }

    /**
     * Pile support, shared by every horde member: mobs are not solid blocks in Java,
     * so a climber would sink through the mate below. If a horde member's head is
     * right under our feet while we fall, cancel the fall - the pile behaves like a
     * soft, writhing staircase. Throttled to every 3 ticks (staggered per entity).
     */
    public static void applyPileSupport(Mob mob, LivingEntity anchor) {
        if (mob.onGround() || anchor == null || (mob.tickCount + mob.getId()) % 3 != 0) {
            return;
        }
        var movement = mob.getDeltaMovement();
        if (movement.y >= 0.0D) {
            return;
        }
        for (Mob mate : mob.level().getEntitiesOfClass(Mob.class,
                mob.getBoundingBox().inflate(0.3D, 0.6D, 0.3D),
                u -> u != mob && u.isAlive() && isHordeMember(u))) {
            double mateHead = mate.getY() + mate.getBbHeight();
            double feet = mob.getY();
            if (feet > mateHead - 0.75D && feet < mateHead + 0.25D) {
                mob.setDeltaMovement(movement.x, 0.0D, movement.z);
                mob.fallDistance = 0.0F;
                return;
            }
        }
    }

    private boolean isFree(BlockPos pos) {
        return this.mob.level().getBlockState(pos).getCollisionShape(this.mob.level(), pos).isEmpty();
    }

    private boolean isTargetAbove(LivingEntity target) {
        return target.getY() > this.mob.getY() + MIN_HEIGHT_GAIN;
    }
}
