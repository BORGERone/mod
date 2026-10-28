#!/usr/bin/env python3
"""Read-only cult probe with the 246 inspection path replacing instant possession."""
import argparse,sys,zipfile
import jvm_cultprobe243 as old
import jvm_npc_cult240 as c
import fixture_stats_regression246 as ex
r,f,t=c.r,c.f,ex.t
SI='dev/settledlands/npc/NpcStatsInteraction';EVENT=c.EVENT

def install(jar):
 classes=ex.install(original(jar),jar)
 with zipfile.ZipFile(jar) as z:
  if SI+'.class' in z.namelist():
   # Only this old probe harness uses an inspection stub. Actual interaction,
   # network/cult and possession bridges execute in dedicated 246 fixtures.
   classes[SI]=f.fake_class(SI,[],[(9,'specific','(L'+EVENT+';)V',2,1,lambda cp:b'\x2a\x04'+c.op(0xb6,cp.method(EVENT,'setCanceled','(Z)V'))+b'\xb1')])
 return classes
original=c.build_classes;c.build_classes=install

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--jar',required=True);a=p.parse_args()
 got=old.run(a.java,a.jar)
 with zipfile.ZipFile(a.jar) as z:new=SI+'.class' in z.namelist()
 expected={'first.count':'1','first.arg0':'00000000-0000-0000-0000-000000000001','first.arg1':'00000000-0000-0000-0000-000000000099','first.arg2':'10','first.arg3':'3','first.arg4':'1','first.arg5':'true','first.arg6':'null','first.nbt':'1','first.canceled':'false','first.removed':'false','rate.count':'1','second.count':'2','second.arg0':'00000000-0000-0000-0000-000000000002','second.arg2':'10','second.arg3':'8','second.arg4':'0','second.arg5':'false','second.nbt':'1','excluded.count':'2','failure.removed':'false' if new else 'true','failure.canceled':'true','points.reads':'0'}
 diff={k:(got.get(k),v) for k,v in expected.items() if got.get(k)!=v};assert not diff,(diff,got)
 assert got['first.arg7'].startswith('net.minecraft.world.item.Item@') and '[SettledLands cult probe]' in got['first.message'],got
 print('Actual read-only cult probe: data, exclusions, rate limit, safe logger exception: PASS')
 print('246 inspection stub only here; real network and possession tested separately.')
if __name__=='__main__':main()
