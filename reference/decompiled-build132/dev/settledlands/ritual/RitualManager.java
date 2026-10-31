package dev.settledlands.ritual;

import dev.settledlands.Sanctity;
import dev.settledlands.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

public final class RitualManager {
   public static final String FROZEN = "settledlands_ritual_frozen";
   public static final String OLD_GLOW = "settledlands_ritual_old_glow";
   public static final String BOLT = "settledlands_ritual_bolt";
   public static final String PROTECTED = "settledlands_ritual_replacement";
   private static RitualManager.State current;

   public static RitualManager.State state() {
      return current;
   }

   public static boolean busy() {
      return current != null;
   }

   public static boolean active(Entity e) {
      return current != null && current.zombie == e;
   }

   public static boolean begin(Zombie zombie, BlockPos target) {
      if (!busy()
         && target != null
         && (Boolean)Settings.RITUALS.get()
         && zombie.getType() == EntityType.ZOMBIE
         && zombie.isAlive()
         && !zombie.isNoAi()
         && !zombie.isPassenger()) {
         if (!(zombie.level() instanceof ServerLevel level && level.hasChunkAt(target) && EventHooks.canEntityGrief(level, zombie))) {
            return false;
         } else if (!(zombie.blockPosition().distSqr(target) <= 64.0)) {
            return false;
         } else if (level.getBlockEntity(target) instanceof BannerBlockEntity banner
            && Sanctity.holy(banner)
            && !RitualWard.immune(banner, level.getGameTime())) {
            current = new RitualManager.State(zombie, level, banner, target.immutable(), zombie.position(), level.getGameTime(), zombie.hasGlowingTag());
            RitualWard.mark(banner, level.getGameTime());
            zombie.getPersistentData().putBoolean("settledlands_ritual_frozen", true);
            zombie.getPersistentData().putBoolean("settledlands_ritual_old_glow", current.oldGlow);
            zombie.setTarget(null);
            zombie.setAggressive(false);
            zombie.getNavigation().stop();
            zombie.setDeltaMovement(Vec3.ZERO);
            zombie.setNoAi(true);
            zombie.setYRot((float)Math.toDegrees(Math.atan2(-(target.getX() + 0.5 - zombie.getX()), target.getZ() + 0.5 - zombie.getZ())));
            zombie.yBodyRot = zombie.getYRot();
            zombie.yHeadRot = zombie.getYRot();
            sound(current, SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, 0.7F);
            RitualPayload.broadcast(current, true);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void sound(RitualManager.State s, SoundEvent sound, float volume, float pitch) {
      s.level.playSound(null, s.zombie.blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
   }

   public static void abort() {
      RitualManager.State s = current;
      if (s != null) {
         current = null;
         s.zombie.setNoAi(false);
         s.zombie.setGlowingTag(s.oldGlow);
         s.zombie.setDeltaMovement(Vec3.ZERO);
         s.zombie.getPersistentData().remove("settledlands_ritual_frozen");
         s.zombie.getPersistentData().remove("settledlands_ritual_old_glow");
         RitualPayload.broadcast(s, false);
      }
   }

   @SubscribeEvent
   public void tick(Post event) {
      RitualManager.State s = current;
      if (s != null) {
         Zombie z = s.zombie;
         if (z.isAlive()
            && !z.isRemoved()
            && z.level() == s.level
            && !z.isPassenger()
            && (Boolean)Settings.RITUALS.get()
            && s.level.hasChunkAt(s.target)
            && s.level.getBlockEntity(s.target) == s.banner
            && Sanctity.holy(s.banner)
            && z.blockPosition().distSqr(s.target) <= 64.0
            && EventHooks.canEntityGrief(s.level, z)) {
            z.getNavigation().stop();
            z.setDeltaMovement(Vec3.ZERO);
            z.setTarget(null);
            z.setAggressive(false);
            z.setPos(s.anchor);
            z.yHeadRot = z.yBodyRot = z.getYRot();
            int age = s.age();
            if (age == 40) {
               sound(s, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.65F);
            }

            if (age >= 40) {
               z.setGlowingTag(true);
            }

            if (age % 20 == 0) {
               RitualPayload.broadcast(s, true);
            }

            if (RitualRules.finished(age)) {
               finish(s);
            }
         } else {
            abort();
         }
      }
   }

   private static void finish(RitualManager.State s) {
      if (!s.level.destroyBlock(s.target, true, s.zombie)) {
         abort();
      } else {
         Zombie z = s.zombie;
         Stray stray = (Stray)EntityType.STRAY.create(s.level);
         LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(s.level);
         if (stray != null && bolt != null) {
            stray.moveTo(z.getX(), z.getY(), z.getZ(), z.getYRot(), 0.0F);
            stray.finalizeSpawn(s.level, s.level.getCurrentDifficultyAt(z.blockPosition()), MobSpawnType.CONVERSION, null);
            stray.setPersistenceRequired();
            if (z.hasCustomName()) {
               stray.setCustomName(z.getCustomName());
               stray.setCustomNameVisible(z.isCustomNameVisible());
            }

            bolt.moveTo(z.position());
            bolt.setVisualOnly(false);
            bolt.getPersistentData().putBoolean("settledlands_ritual_bolt", true);
            bolt.getPersistentData().putUUID("settledlands_ritual_replacement", stray.getUUID());
            if (!s.level.addFreshEntity(stray)) {
               abort();
            } else {
               abort();
               z.discard();
               s.level.addFreshEntity(bolt);
            }
         } else {
            abort();
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public void death(LivingDeathEvent e) {
      if (active(e.getEntity())) {
         abort();
      }
   }

   @SubscribeEvent
   public void leave(EntityLeaveLevelEvent e) {
      if (active(e.getEntity())) {
         abort();
      }
   }

   @SubscribeEvent
   public void join(EntityJoinLevelEvent e) {
      if (e.getLevel() instanceof ServerLevel && e.getEntity() instanceof Zombie z && z.getType() == EntityType.ZOMBIE) {
         if (z.getPersistentData().getBoolean("settledlands_ritual_frozen")) {
            z.setNoAi(false);
            z.setGlowingTag(z.getPersistentData().getBoolean("settledlands_ritual_old_glow"));
            z.getPersistentData().remove("settledlands_ritual_frozen");
            z.getPersistentData().remove("settledlands_ritual_old_glow");
         }

         if (z.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof BannerRitualGoal)) {
            z.goalSelector.addGoal(0, new BannerRitualGoal(z));
         }
      }
   }

   @SubscribeEvent
   public void stopped(ServerStoppedEvent e) {
      abort();
   }

   public record State(Zombie zombie, ServerLevel level, BannerBlockEntity banner, BlockPos target, Vec3 anchor, long started, boolean oldGlow) {
      public int age() {
         return (int)Math.max(0L, this.level.getGameTime() - this.started);
      }
   }
}
