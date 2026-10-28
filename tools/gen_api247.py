#!/usr/bin/env python3
"""Compile-only silhouettes for build 247; never package API stubs."""
import pathlib,sys
import gen_api246
OVERRIDES={
'net/minecraft/core/Holder.java':'''package net.minecraft.core; import net.minecraft.resources.ResourceLocation; public interface Holder<T> { T value(); boolean is(Holder<T> other); boolean is(ResourceLocation id); public static class Reference<T> implements Holder<T> { public T value(){return null;} public boolean is(Holder<T> o){return false;} public boolean is(ResourceLocation id){return false;} } }''',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level; import java.util.UUID; import net.minecraft.resources.ResourceKey; import net.minecraft.world.entity.Entity; public class Level { public static final ResourceKey<Level> OVERWORLD=null; public ResourceKey<Level> dimension(){return null;} public long getGameTime(){return 0;} public Entity getEntity(UUID id){return null;} }''',
'net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent.java':'''package net.neoforged.neoforge.event.entity.living; import net.minecraft.world.damagesource.DamageSource; import net.minecraft.world.entity.LivingEntity; public class LivingIncomingDamageEvent { public LivingEntity getEntity(){return null;} public DamageSource getSource(){return null;} public float getAmount(){return 0;} public void setAmount(float f){} public void setCanceled(boolean value){} }''',
'net/minecraft/world/damagesource/DamageSource.java':'''package net.minecraft.world.damagesource; import net.minecraft.world.entity.Entity; public class DamageSource { public Entity getEntity(){return null;} public Entity getDirectEntity(){return null;} }''',
'net/minecraft/world/entity/projectile/Projectile.java':'''package net.minecraft.world.entity.projectile; import net.minecraft.world.entity.Entity; public class Projectile extends Entity { public Entity getOwner(){return null;} }''',
'net/minecraft/world/entity/AreaEffectCloud.java':'''package net.minecraft.world.entity; public class AreaEffectCloud extends Entity { public LivingEntity getOwner(){return null;} }''',
'net/minecraft/world/effect/MobEffectInstance.java':'''package net.minecraft.world.effect; import net.minecraft.core.Holder; public class MobEffectInstance { public boolean is(Holder<MobEffect> holder){return false;} }''',
'net/minecraft/world/effect/MobEffects.java':'''package net.minecraft.world.effect; import net.minecraft.core.Holder; public class MobEffects { public static Holder<MobEffect> POISON,WITHER,HUNGER; }''',
'net/neoforged/neoforge/event/entity/player/AttackEntityEvent.java':'''package net.neoforged.neoforge.event.entity.player; import net.minecraft.world.entity.Entity; import net.minecraft.world.entity.player.Player; public class AttackEntityEvent { public Player getEntity(){return null;} public Entity getTarget(){return null;} public void setCanceled(boolean value){} }''',
'net/neoforged/neoforge/common/EffectCure.java':'''package net.neoforged.neoforge.common; public final class EffectCure {}''',
'net/neoforged/neoforge/common/EffectCures.java':'''package net.neoforged.neoforge.common; public final class EffectCures { public static final EffectCure MILK=null; }''',
'net/neoforged/neoforge/event/entity/living/MobEffectEvent.java':'''package net.neoforged.neoforge.event.entity.living; import net.minecraft.core.Holder; import net.minecraft.world.effect.MobEffect; import net.minecraft.world.effect.MobEffectInstance; import net.minecraft.world.entity.Entity; import net.minecraft.world.entity.LivingEntity; import net.neoforged.neoforge.common.EffectCure; public class MobEffectEvent { public static class Remove extends MobEffectEvent { public Holder<MobEffect> getEffect(){return null;} public EffectCure getCure(){return null;} public void setCanceled(boolean value){} } public static class Applicable extends MobEffectEvent { public enum Result { DEFAULT,APPLY,DO_NOT_APPLY } public MobEffectInstance getEffectInstance(){return null;} public Entity getEffectSource(){return null;} public LivingEntity getEntity(){return null;} public void setResult(Result r){} } }''',
'dev/settledlands/npc/NpcStatsInteraction.java':'''package dev.settledlands.npc; import net.minecraft.world.entity.player.Player; public class NpcStatsInteraction { public static boolean isBodiless(Player p){return false;} }''',
}
def main(dest):
 gen_api246.main(dest);root=pathlib.Path(dest)
 for name,content in OVERRIDES.items():
  path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content+'\n',encoding='utf8')
 print('added',len(OVERRIDES),'247 compile-only silhouettes')
if __name__=='__main__':
 if len(sys.argv)!=2:raise SystemExit('usage: gen_api247.py DEST')
 main(sys.argv[1])
