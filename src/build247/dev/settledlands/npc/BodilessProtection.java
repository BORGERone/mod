package dev.settledlands.npc;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Bodiless players cannot hurt living entities in the vanilla Overworld. */
@EventBusSubscriber(modid="settledlands")
public final class BodilessProtection {
    private BodilessProtection() {}

    private static boolean blocked(Entity target, Entity source) {
        if (!Level.OVERWORLD.equals(target.level().dimension())) return false;
        if (source instanceof Projectile projectile) source=projectile.getOwner();
        else if (source instanceof AreaEffectCloud cloud) source=cloud.getOwner();
        return source instanceof Player player && NpcStatsInteraction.isBodiless(player);
    }

    /** Stop player attacks before entities with custom hurt logic can react. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        if (blocked(event.getTarget(), event.getEntity())) event.setCanceled(true);
    }

    /** Catch direct damage and attributed projectiles, explosions and magic. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (blocked(event.getEntity(), event.getSource().getEntity()) ||
            blocked(event.getEntity(), event.getSource().getDirectEntity())) event.setCanceled(true);
    }

    /** Poison/wither/hunger later lose their source; block their application, not other effects. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance effect=event.getEffectInstance();
        if ((effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.HUNGER)) &&
            blocked(event.getEntity(), event.getEffectSource())) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** Cancel only the milk cure for this effect; normal removal still works. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getCure() == EffectCures.MILK && event.getEffect().is(PossessionSystem.BODILESS_ID)) {
            event.setCanceled(true);
        }
    }
}
