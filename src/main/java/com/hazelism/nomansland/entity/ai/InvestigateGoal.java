package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Ports behavior.investigate_suspicious_location (speed multiplier 0.35 in the
 * original; 0.7 here relative to the Java movement attribute - tuned by feel).
 * Walks to the last smelled/heard spot; SenseGoal promotes it to a real target
 * as soon as the prey is seen or smelled up close.
 */
public class InvestigateGoal extends Goal {
    private static final double SPEED = 0.7D;
    private static final int REPATH_TICKS = 20;

    private final UndeadEntity mob;
    private int repathCooldown;

    public InvestigateGoal(UndeadEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() == null && this.mob.hasFreshInvestigatePos();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        moveToSpot();
    }

    @Override
    public void tick() {
        BlockPos spot = this.mob.getInvestigatePos();
        if (spot == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D);
        if (this.repathCooldown-- <= 0) {
            this.repathCooldown = REPATH_TICKS;
            moveToSpot();
        }
        if (this.mob.blockPosition().distSqr(spot) <= 4.0D) {
            this.mob.clearInvestigatePos(); // arrived, nothing here - give up
        }
    }

    private void moveToSpot() {
        BlockPos spot = this.mob.getInvestigatePos();
        if (spot != null) {
            this.mob.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, SPEED);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }
}
