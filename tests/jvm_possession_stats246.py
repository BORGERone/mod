#!/usr/bin/env python3
"""Real cult reader/writer and actual possession bridge with synthetic APIs."""
import argparse,pathlib,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import jvm_npc_cult240 as c,fixture_stats_regression246 as ex
r,t=c.r,ex.t;G=c.G;PS=c.PS;PROFILE=ex.PROFILE

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 with tempfile.TemporaryDirectory(prefix='bridge246-') as d:
  root=pathlib.Path(d);classes=ex.install(c.build_classes(a.jar),a.jar)
  classes[PS]=r.subset(a.jar,PS,{'<clinit>','data','bodiless','isBodiless','isPossessed','cult','possess','setPossessed','settledlands$possess'},expose={'cult'})
  classes[G]=t.rewrite(classes[G],add_fields=[(1,'profile','L'+PROFILE+';')],replace=[('profile','()L'+PROFILE+';',1,1,lambda k:b'\x2a'+t.op(0xb4,k.add_fieldref(G,'profile','L'+PROFILE+';'))+b'\xb0')])
  for name,raw in classes.items():p=root/(name+'.class');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
  body=ROOT/'tests/java246-body/dev/settledlands/npc';files=[body/'NpcProfile.java',body/'NpcDialogueGraph.java',body/'NpcStatsGraph.java',ROOT/'tests/java246-body/net/minecraft/world/entity/ai/attributes/AttributeModifier.java',ROOT/'tests/java246-bridge/PossessionBridgeTest.java']
  subprocess.run([str(a.java.resolve()),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(root),'-d',str(root),*map(str,files)],check=True)
  subprocess.run([str(a.java.resolve()),'-Xshare:off','-Xverify:all','-cp',str(root),'PossessionBridgeTest'],check=True)
 print('Network gate tested separately; synthetic game APIs and event dispatch; no Minecraft runtime.')
if __name__=='__main__':main()
