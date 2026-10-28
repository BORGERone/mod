import dev.settledlands.npc.NpcStatsInteraction;
import dev.settledlands.npc.PossessionSystem;
import fixture.TestBus;
import net.minecraft.core.Holder;
import net.minecraft.resources.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.*;

public class BodilessProtectionTest {
    static final Holder<MobEffect> BODILESS=new Holder.Reference<>(PossessionSystem.BODILESS_ID);
    static void ck(boolean b,String message){if(!b)throw new AssertionError(message);}
    static Level level(String namespace,String id){return new Level(new ResourceKey<Level>(ResourceLocation.fromNamespaceAndPath(namespace,id)));}
    static MobEffectInstance effect(Holder<MobEffect> h){return new MobEffectInstance(h,1798,2);}
    static Player ghost(Level l){Player p=new Player(l);ck(p.addEffect(effect(BODILESS),null),"add bodiless");return p;}
    static boolean hit(LivingEntity victim,Entity direct,Entity cause){return victim.hurt(new DamageSource(direct,cause),3);}
    public static void main(String[] args){
        Level ow=level("minecraft","overworld"), purg=level("settledlands","purgatory"), nether=level("minecraft","the_nether");
        Player ghost=ghost(ow), normal=new Player(ow); LivingEntity npc=new LivingEntity(ow); Player victim=new Player(ow);
        ck(TestBus.handlers.size()==4 && NpcStatsInteraction.isBodiless(ghost),"real predicate and four subscribers");
        ck(!ghost.attack(npc)&&npc.hits==0,"melee NPC blocked before custom hurt");
        ck(!ghost.attack(victim)&&victim.hits==0,"melee another player blocked");
        Entity stand=new Entity(ow);ck(!ghost.attack(stand)&&stand.hits==0,"custom nonliving entity protected from player attack");
        ck(normal.attack(npc)&&npc.hits==1,"ordinary player attacks work");
        ck(!hit(npc,ghost,ghost)&&npc.health==20,"direct damage blocked");
        ck(!hit(victim,ghost,ghost)&&victim.health==20,"direct player damage blocked");
        Projectile arrow=new Projectile(ghost);ck(!hit(npc,arrow,ghost),"arrow attribution");
        ck(!hit(npc,arrow,arrow),"projectile as source with owner");
        ck(!hit(npc,arrow,null),"projectile direct fallback");
        ck(!hit(npc,null,ghost),"attributed explosion or indirect magic");
        AreaEffectCloud cloud=new AreaEffectCloud(ghost);ck(!hit(npc,cloud,cloud),"cloud owner attribution");
        ck(hit(npc,normal,normal)&&npc.health==17,"non-bodiless player can damage");
        ck(hit(npc,null,null)&&npc.health==14,"unattributed environment not made invulnerable");
        ck(hit(ghost,normal,normal)&&ghost.health==17,"bodiless victim not made invulnerable");
        Player otherGhost=ghost(purg);LivingEntity purgNpc=new LivingEntity(purg);
        ck(otherGhost.attack(purgNpc)&&hit(purgNpc,otherGhost,otherGhost)&&hit(purgNpc,new Projectile(otherGhost),otherGhost),"purgatory combat allowed");
        Player netherGhost=ghost(nether);LivingEntity netherNpc=new LivingEntity(nether);ck(netherGhost.attack(netherNpc)&&hit(netherNpc,netherGhost,netherGhost),"nether unaffected");
        Level end=level("minecraft","the_end");Player endGhost=ghost(end);LivingEntity endNpc=new LivingEntity(end);ck(endGhost.attack(endNpc)&&hit(endNpc,endGhost,endGhost),"end unaffected");
        for(Holder<MobEffect> h:new Holder[]{MobEffects.POISON,MobEffects.WITHER,MobEffects.HUNGER}){
            ck(!npc.addEffect(effect(h),ghost),"splash damage-over-time prevented");
            ck(!npc.addEffect(effect(h),new Projectile(ghost)),"projectile damage-over-time prevented");
            ck(!npc.addEffect(effect(h),cloud),"lingering cloud damage-over-time prevented");
            ck(purgNpc.addEffect(effect(h),otherGhost),"purgatory damage-over-time allowed");
        }
        ck(npc.addEffect(effect(MobEffects.SPEED),ghost),"beneficial potion preserved");
        ck(npc.addEffect(effect(MobEffects.MOVEMENT_SLOWDOWN),ghost),"non-damaging slowness preserved");
        ck(npc.addEffect(effect(MobEffects.POISON),normal),"normal attacker poison allowed");
        ck(npc.addEffect(effect(MobEffects.WITHER),null),"unknown source not blocked");
        MobEffectInstance bodiless=ghost.active.get(BODILESS), speed=effect(MobEffects.SPEED), slow=effect(MobEffects.MOVEMENT_SLOWDOWN);
        Object hidden=bodiless.hidden;ck(ghost.addEffect(speed,null)&&ghost.addEffect(slow,null),"other effects");
        ck(ghost.removeEffectsCuredBy(EffectCures.MILK),"milk clears other effects");
        ck(ghost.active.get(BODILESS)==bodiless && bodiless.duration==1798 && bodiless.amplifier==2 && bodiless.hidden==hidden,"old saved bodiless instance survives milk intact");
        ck(!ghost.hasEffect(MobEffects.SPEED)&&!ghost.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"milk still removes normal effects");
        ck(NpcStatsInteraction.isBodiless(ghost)&&!ghost.attack(npc)&&!hit(npc,ghost,ghost),"milk cannot escape restrictions");
        LivingEntity otherBearer=new LivingEntity(ow);ck(otherBearer.addEffect(effect(BODILESS),null),"other bearer setup");otherBearer.removeEffectsCuredBy(EffectCures.MILK);ck(otherBearer.hasEffect(BODILESS),"milk protects effect even on another bearer");
        ck(ghost.removeEffect(BODILESS)&&!NpcStatsInteraction.isBodiless(ghost),"explicit removal / possession still works");
        ck(ghost.attack(npc)&&hit(npc,ghost,ghost),"attacks work after normal removal");
        ghost.addEffect(effect(BODILESS),null);ck(ghost.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM)&&!NpcStatsInteraction.isBodiless(ghost),"totem remains unchanged");
        ghost.addEffect(effect(BODILESS),null);ghost.removeEffectsCuredBy(EffectCures.HONEY);ck(NpcStatsInteraction.isBodiless(ghost),"honey not a default cure");
        System.out.println("247 actual protection + 246 predicate, annotated events / milk / dimensions / attributed damage / DOT: PASS");
    }
}
