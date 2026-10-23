package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Encirclement - the visible half of the Horde Mind. Undead that share a target
 * do not queue up single-file behind the leader (the vanilla look); each one
 * claims a slot on a ring around the prey and approaches from its own angle,
 * so the player gets surrounded and cannot kite the whole horde in a line.
 *
 * <p>Mechanics: while farther than melee range and at least two undead share
 * the target, this goal preempts the plain melee chase. Slots are the 8 compass
 * points at 2 blocks from the prey; a zombie's slot is its index among the
 * pack members sorted by entity id (stable, no negotiation traffic). Once the
 * zombie is close to the prey, this goal releases and MeleeAttackGoal strikes.
 *
 * <p>Cost: pack query and repath at most once per 10 ticks, staggered per
 * entity; slot maths is plain floats.
 */
public class EncirclementGoal extends Goal {
    private static final int SLOT_COUNT = 8;
    private static final double ENGAGE_DISTANCE_SQR = 2.4D * 2.4D;
    private static final double RELEASE_DISTANCE_SQR = 2.2D * 2.2D;
    private static final int QUERY_INTERVAL = 10;

    private final UndeadEntity mob;
    private int scanCooldown;
    private int repathCooldown;
    private int slotIndex = -1;
    private int packSize;
    private int targetId = -1;
    // Organic-ring noise, fixed per entity in the constructor (stable between
    // repaths, otherwise the zombie would jitter its slot every cycle).
    private final double angleJitter;
    private final double radiusJitter;

    public EncirclementGoal(UndeadEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        // Deterministic hash spread of the entity id -> +-0.33 rad / 1.7-2.3 blocks.
        // No RandomSource allocation, and identical on every tick.
        int id = mob.getId();
        this.angleJitter = (((id * 37) % 13) - 6) * 0.055D;
        this.radiusJitter = 1.7D + ((id * 53) % 11) * 0.06D;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || this.mob.distanceToSqr(target) <= ENGAGE_DISTANCE_SQR) {
            return false;
        }
        if (this.scanCooldown-- > 0) {
            return false;
        }
        this.scanCooldown = QUERY_INTERVAL;
        updatePack(target);
        return this.packSize >= 2 && this.slotIndex >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && target.getId() == this.targetId
                && this.mob.distanceToSqr(target) > RELEASE_DISTANCE_SQR;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.scanCooldown-- <= 0) {
            this.scanCooldown = QUERY_INTERVAL;
            updatePack(target);
        }
        if (this.repathCooldown-- <= 0) {
            this.repathCooldown = QUERY_INTERVAL;
            double angle = (Math.PI * 2.0D / SLOT_COUNT) * (this.slotIndex % SLOT_COUNT) + this.angleJitter
                    + Math.sin((this.mob.tickCount + this.mob.getId() * 31L) * 0.01D) * 0.12D; // slow breathing drift
            double slotX = target.getX() + Math.sin(angle) * this.radiusJitter;
            double slotZ = target.getZ() + Math.cos(angle) * this.radiusJitter;
            this.mob.getNavigation().moveTo(slotX, target.getY(), slotZ, 1.05D);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.slotIndex = -1;
        this.packSize = 0;
        this.targetId = -1;
    }

    /** Count pack members hunting the same prey and find our stable slot among them. */
    private void updatePack(LivingEntity target) {
        List<UndeadEntity> pack = this.mob.level().getEntitiesOfClass(UndeadEntity.class,
                this.mob.getBoundingBox().inflate(24.0D, 8.0D, 24.0D),
                u -> u.isAlive() && u.getTarget() == target);
        pack.sort(Comparator.comparingInt(UndeadEntity::getId));
        this.packSize = pack.size();
        this.slotIndex = pack.indexOf(this.mob);
        this.targetId = target.getId();
    }
}
