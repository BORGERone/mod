package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.entity.BannerBearerEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Periodically offers the bearer a chance to rally. All real work (conditions,
 * particles, buffs) lives in {@link BannerBearerEntity#tryStartRallyCast()};
 * this goal only throttles the attempts and holds the bearer in place while the
 * cast runs. No MOVE flag: BearerEscortGoal owns movement and already stops
 * navigation during a cast, so the two never fight over the navigator.
 */
public class RallyCastGoal extends Goal {
    private final BannerBearerEntity mob;
    private int attemptCooldown;

    public RallyCastGoal(BannerBearerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.attemptCooldown-- > 0) {
            return false;
        }
        this.attemptCooldown = 20;
        return this.mob.tryStartRallyCast();
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isCasting();
    }

    @Override
    public boolean isInterruptable() {
        return false; // movement goals deal with fleeing; a cast in progress stands its ground
    }
}
