package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.entity.BannerBearerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Horde Climbing for the banner bearer: identical pile mechanics to the rest of
 * the horde (support from the mass below, fatigue slips, mantle over the lip -
 * no spider-style solo wall scaling), except the bearer has no hunt target, so
 * the "prey above" is simply the nearest player he is shadowing.
 *
 * Player lookup is cached and refreshed at most once per 10 ticks.
 */
public class BearerHordeClimbGoal extends HordeClimbGoal {
    private final BannerBearerEntity bearer;
    @Nullable
    private Player cachedPlayer;
    private int refreshCooldown;

    public BearerHordeClimbGoal(BannerBearerEntity bearer) {
        super(bearer);
        this.bearer = bearer;
    }

    @Override
    protected LivingEntity climbTarget() {
        if (this.refreshCooldown-- <= 0) {
            this.refreshCooldown = 10;
            Player player = this.bearer.level().getNearestPlayer(this.bearer, 24.0D);
            this.cachedPlayer = player != null && player.isAlive() && !player.isSpectator() ? player : null;
        }
        return this.cachedPlayer;
    }
}
