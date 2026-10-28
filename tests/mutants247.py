#!/usr/bin/env python3
"""Compile verifier-valid 247 behavioral mutants and require targeted failures."""
import argparse,pathlib,subprocess,tempfile,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tests'));import jvm_bodiless247 as fixture
SRC=ROOT/'src/build247/dev/settledlands/npc/BodilessProtection.java'
# label, exact original, replacement, expected assertion from independent fixture
MUTANTS=[
 ('no melee cancel','if (blocked(event.getTarget(), event.getEntity())) event.setCanceled(true);','if (false && blocked(event.getTarget(), event.getEntity())) event.setCanceled(true);','melee NPC blocked'),
 ('no incoming cancel','blocked(event.getEntity(), event.getSource().getDirectEntity())) event.setCanceled(true);','blocked(event.getEntity(), event.getSource().getDirectEntity())) {}','direct damage blocked'),
 ('wrong dimension','if (!Level.OVERWORLD.equals(target.level().dimension())) return false;','if (Level.OVERWORLD.equals(target.level().dimension())) return false;','melee NPC blocked'),
 ('all players blocked','return source instanceof Player player && NpcStatsInteraction.isBodiless(player);','return source instanceof Player;','ordinary player attacks work'),
 ('nobody blocked','return source instanceof Player player && NpcStatsInteraction.isBodiless(player);','return false;','melee NPC blocked'),
 ('lose projectile owner','if (source instanceof Projectile projectile) source=projectile.getOwner();','if (source instanceof Projectile projectile) source=projectile;','projectile as source with owner'),
 ('lose cloud owner','else if (source instanceof AreaEffectCloud cloud) source=cloud.getOwner();','else if (source instanceof AreaEffectCloud cloud) source=cloud;','cloud owner attribution'),
 ('no direct fallback','blocked(event.getEntity(), event.getSource().getEntity()) ||\n            blocked(event.getEntity(), event.getSource().getDirectEntity())','blocked(event.getEntity(), event.getSource().getEntity())','projectile direct fallback'),
 ('no DOT gate','event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);','event.setResult(MobEffectEvent.Applicable.Result.DEFAULT);','splash damage-over-time prevented'),
 ('poison ignored','effect.is(MobEffects.POISON) || ','','splash damage-over-time prevented'),
 ('wither ignored','effect.is(MobEffects.WITHER) || ','','splash damage-over-time prevented'),
 ('hunger ignored',' || effect.is(MobEffects.HUNGER)','','splash damage-over-time prevented'),
 ('all potions blocked','(effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.HUNGER))','(true)','beneficial potion preserved'),
 ('milk clears bodiless','event.getCure() == EffectCures.MILK','event.getCure() == EffectCures.HONEY','old saved bodiless instance survives milk intact'),
 ('all milk effects protected','event.getCure() == EffectCures.MILK && event.getEffect().is(PossessionSystem.BODILESS_ID)','event.getCure() == EffectCures.MILK','milk clears other effects'),
 ('all cures protected','event.getCure() == EffectCures.MILK && event.getEffect().is(PossessionSystem.BODILESS_ID)','event.getEffect().is(PossessionSystem.BODILESS_ID)','explicit removal / possession still works'),
 ('damage handler not registered','@SubscribeEvent(priority=EventPriority.LOWEST)\n    public static void onIncomingDamage','public static void onIncomingDamage','handler count 3'),
]

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();text=SRC.read_text(encoding='utf8')
 with tempfile.TemporaryDirectory(prefix='mutants247-') as td:
  root=pathlib.Path(td);out=fixture.build_fixture(java,a.jar,root);main=['-Xshare:off','-Xverify:all','-cp',str(out),'BodilessProtectionTest'];subprocess.run([str(java),*main],check=True)
  path=out/'dev/settledlands/npc/BodilessProtection.class';original=path.read_bytes()
  for index,(label,old,new,expected) in enumerate(MUTANTS,1):
   assert text.count(old)==1,(label,text.count(old));src=root/'mutant/dev/settledlands/npc/BodilessProtection.java';src.parent.mkdir(parents=True,exist_ok=True);src.write_text(text.replace(old,new,1),encoding='utf8');tmp=root/'mutant-classes'
   subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(out),'-d',str(tmp),str(src)],check=True,stdout=subprocess.DEVNULL)
   path.write_bytes((tmp/'dev/settledlands/npc/BodilessProtection.class').read_bytes())
   run=subprocess.run([str(java),*main],capture_output=True,text=True);output=run.stdout+run.stderr
   assert run.returncode!=0 and 'AssertionError: '+expected in output,(label,run.returncode,output)
   assert 'VerifyError' not in output and 'LinkageError' not in output,(label,output)
   print(f'  {index:02} {label}: REJECTED ({expected})',flush=True);path.write_bytes(original)
  subprocess.run([str(java),*main],check=True)
 print(f'247 behavioral mutants: PASS {len(MUTANTS)} (all verifier-valid; synthetic APIs)',flush=True)
if __name__=='__main__':main()
