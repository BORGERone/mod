package com.hazelism.nomansland.entity.ai;

import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractRaider;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Ports the Bedrock targeting rules of bp:undead:
 * {@code nearest_attackable_target} with must_see=false, follow range 20.
 *
 * <ul>
 *   <li>Players are only sensed while <b>moving and not sneaking</b>
 *       (revenge after being hit is handled separately by HurtByTargetGoal).</li>
 *   <li>Without line of sight a player is only fully targeted within smell
 *       range (16 blocks, the original behavior.sniff radius); further away
 *       the mob only walks over to investigate the spot.</li>
 *   <li>Villagers, iron golems and raiders are targeted while moving.</li>
 * </ul>
 *
 * Runs at most once per 10 ticks and stops at the first hit - the AABB query
 * below is the vanilla spatial index, not a world scan.
 */
public class SenseGoal extends TargetGoal {
    private static final double SMELL_RANGE_SQR = 16.0D * 16.0D;
    private static final int SCAN_INTERVAL = 10;

    private int scanCooldown;

    public SenseGoal(UndeadEntity mob) {
        super(mob, false);
    }

    @Override
    public boolean canUse() {
        UndeadEntity undead = (UndeadEntity) this.mob;
        if (undead.isCorpse() || this.mob.getTarget() != null) {
            return false;
        }
        if (this.scanCooldown-- > 0) {
            return false;
        }
        this.scanCooldown = SCAN_INTERVAL;

        Player player = this.mob.level().getNearestPlayer(this.mob, UndeadEntity.FOLLOW_RANGE,
                e -> e instanceof Player p && isSensiblePlayer(p));
        if (player != null) {
            if (this.mob.distanceToSqr(player) <= SMELL_RANGE_SQR || this.mob.getSensing().hasLineOfSight(player)) {
                this.mob.setTarget(player);
            } else {
                undead.setInvestigatePos(player.blockPosition());
            }
            return this.mob.getTarget() != null;
        }

        LivingEntity prey = findMovingPrey();
        if (prey != null) {
            this.mob.setTarget(prey);
            return true;
        }
        return false;
    }

    private boolean isSensiblePlayer(Player player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.isShiftKeyDown()) {
            return false;
        }
        // "is_moving" in the original filters. Sprinting always counts (noise).
        return player.isSprinting() || player.getDeltaMovement().horizontalDistanceSqr() > 0.0008D;
    }

    private LivingEntity findMovingPrey() {
        // Villagers/golems/raiders are rarer targets than players; one indexed query per scan is fine.
        List<? extends LivingEntity> candidates = this.mob.level().getEntitiesOfClass(AbstractVillager.class,
                this.mob.getBoundingBox().inflate(UndeadEntity.FOLLOW_RANGE, 8.0D, UndeadEntity.FOLLOW_RANGE),
                LivingEntity::isAlive);
        for (LivingEntity villager : candidates) {
            if (isMoving(villager)) {
                return villager;
            }
        }
        candidates = this.mob.level().getEntitiesOfClass(IronGolem.class,
                this.mob.getBoundingBox().inflate(UndeadEntity.FOLLOW_RANGE, 8.0D, UndeadEntity.FOLLOW_RANGE),
                LivingEntity::isAlive);
        for (LivingEntity golem : candidates) {
            if (isMoving(golem)) {
                return golem;
            }
        }
        candidates = this.mob.level().getEntitiesOfClass(AbstractRaider.class,
                this.mob.getBoundingBox().inflate(UndeadEntity.FOLLOW_RANGE, 8.0D, UndeadEntity.FOLLOW_RANGE),
                LivingEntity::isAlive);
        for (LivingEntity raider : candidates) {
            if (isMoving(raider)) {
                return raider;
            }
        }
        return null;
    }

    private static boolean isMoving(LivingEntity entity) {
        return entity.getDeltaMovement().horizontalDistanceSqr() > 0.0001D;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // one-shot acquisition; vanilla handles target retention/loss afterwards
    }
}
