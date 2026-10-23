package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.entity.BannerBearerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * The bearer's survival dance: shadow the nearest player at mid-range.
 *
 * <ul>
 *   <li>closer than {@link #FLEE_DISTANCE}: flee directly away at full speed;</li>
 *   <li>farther than {@link #FOLLOW_DISTANCE}: close in, but never into melee reach;</li>
 *   <li>in between: slowly orbit the player - the rally stays in range of the fight
 *       while the bearer never stands still to be hit;</li>
 *   <li>any recent attacker (arrow, zombie, ...) is treated as the threat when no
 *       player is near: this mob avoids damage, whoever deals it.</li>
 * </ul>
 *
 * Threat lookup and repaths are throttled; movement maths is scratch-free.
 */
public class BearerEscortGoal extends Goal {
    private static final double FLEE_DISTANCE = 7.5D;
    private static final double FOLLOW_DISTANCE = 15.0D;
    private static final double ORBIT_RADIUS = 10.5D;
    private static final int REPATH_FLEE_TICKS = 5;
    private static final int REPATH_NORMAL_TICKS = 15;

    private final BannerBearerEntity mob;
    private LivingEntity threat;
    private int threatScanCooldown;
    private int repathCooldown;
    private double orbitAngle;

    public BearerEscortGoal(BannerBearerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        refreshThreat();
        return this.threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.threat != null && this.threat.isAlive()
                && this.mob.distanceToSqr(this.threat) < 40.0D * 40.0D;
    }

    @Override
    public void tick() {
        if (this.threatScanCooldown-- <= 0) {
            this.threatScanCooldown = 10;
            refreshThreat();
        }
        if (this.threat == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(this.threat, 30.0F, 30.0F);
        if (this.mob.isCasting()) {
            this.mob.getNavigation().stop(); // planted the banner - holds still while channeling
            return;
        }

        double distance = this.mob.distanceTo(this.threat);
        if (this.repathCooldown-- > 0) {
            return;
        }
        if (distance < FLEE_DISTANCE) {
            this.repathCooldown = REPATH_FLEE_TICKS;
            double dx = this.mob.getX() - this.threat.getX();
            double dz = this.mob.getZ() - this.threat.getZ();
            double len = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double run = (FLEE_DISTANCE + 5.0D - distance) / len;
            this.mob.getNavigation().moveTo(
                    this.mob.getX() + dx * run * 2.0D, this.mob.getY(), this.mob.getZ() + dz * run * 2.0D, 1.45D);
        } else if (distance > FOLLOW_DISTANCE) {
            this.repathCooldown = REPATH_NORMAL_TICKS;
            this.mob.getNavigation().moveTo(this.threat, 1.0D);
        } else {
            this.repathCooldown = REPATH_NORMAL_TICKS * 2;
            this.orbitAngle += 0.5D; // slow circling keeps the rally inside the fight
            double slotX = this.threat.getX() + Math.sin(this.orbitAngle) * ORBIT_RADIUS;
            double slotZ = this.threat.getZ() + Math.cos(this.orbitAngle) * ORBIT_RADIUS;
            this.mob.getNavigation().moveTo(slotX, this.threat.getY(), slotZ, 0.75D);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.threat = null;
    }

    /** Nearest player wins; a recent non-player attacker is the fallback threat. */
    private void refreshThreat() {
        Player player = this.mob.level().getNearestPlayer(this.mob, 32.0D);
        if (player != null && !player.isSpectator() && player.isAlive()) {
            this.threat = player;
            return;
        }
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() < 100) {
            this.threat = attacker;
        } else if (this.threat instanceof Player) {
            this.threat = null;
        }
    }
}
