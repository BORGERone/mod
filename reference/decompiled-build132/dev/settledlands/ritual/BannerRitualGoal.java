package dev.settledlands.ritual;

import dev.settledlands.Sanctity;
import dev.settledlands.Settings;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.EventHooks;

public final class BannerRitualGoal extends Goal {
   private final Zombie zombie;
   private int nextSearch;
   private BlockPos target;
   private boolean provoked;

   public BannerRitualGoal(Zombie var1) {
      this.zombie = var1;
      this.nextSearch = var1.tickCount + 10 + var1.getRandom().nextInt(20);
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
   }

   private boolean checkProvoked() {
      LivingEntity var1 = this.zombie.getLastHurtByMob();
      if (var1 != null && var1.isAlive()) {
         this.provoked = true;
         if (this.zombie.getTarget() != var1) {
            this.zombie.setTarget(var1);
            this.zombie.setAggressive(true);
         }

         return true;
      } else {
         if (this.provoked) {
            LivingEntity var2 = this.zombie.getTarget();
            if (var2 != null && var2.isAlive()) {
               return true;
            }

            this.provoked = false;
         }

         return false;
      }
   }

   public boolean canUse() {
      if (this.checkProvoked()) {
         return false;
      } else if (this.zombie.tickCount < this.nextSearch) {
         return false;
      } else {
         this.nextSearch = this.zombie.tickCount + 15 + this.zombie.getRandom().nextInt(10);
         if ((Boolean)Settings.RITUALS.get() && !this.zombie.isNoAi() && !this.zombie.isPassenger() && this.zombie.isAlive()) {
            if (this.zombie.level() instanceof ServerLevel var2 && EventHooks.canEntityGrief(var2, this.zombie)) {
               this.target = Sanctity.ritualTarget(var2, this.zombie.blockPosition());
               if (this.target != null) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   public void start() {
      this.tick();
   }

   public boolean canContinueToUse() {
      if (RitualManager.active(this.zombie)) {
         return true;
      } else {
         return this.checkProvoked()
            ? false
            : (Boolean)Settings.RITUALS.get()
               && this.target != null
               && !this.zombie.isNoAi()
               && !this.zombie.isPassenger()
               && this.zombie.isAlive()
               && this.zombie.level() instanceof ServerLevel var2
               && EventHooks.canEntityGrief(var2, this.zombie)
               && this.target.distSqr(this.zombie.blockPosition()) <= 1024.0
               && Sanctity.validRitualTarget(var2, this.target);
      }
   }

   public void tick() {
      if (!RitualManager.active(this.zombie) && !this.checkProvoked() && this.target != null) {
         this.zombie.setTarget(null);
         this.zombie.setAggressive(false);
         double var1 = this.target.distSqr(this.zombie.blockPosition());
         if (!(var1 <= 64.0) || !this.zombie.onGround() || RitualManager.busy() || !RitualManager.begin(this.zombie, this.target)) {
            if (!(var1 <= 4.0) && (this.zombie.tickCount % 10 == 0 || this.zombie.getNavigation().isDone())) {
               this.zombie.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
            }
         }
      }
   }

   public void stop() {
      if (RitualManager.active(this.zombie)) {
         RitualManager.abort();
      }

      this.zombie.getNavigation().stop();
      this.target = null;
      this.nextSearch = this.zombie.tickCount + 20;
   }
}
