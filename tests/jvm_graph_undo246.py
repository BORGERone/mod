#!/usr/bin/env python3
"""Actual screen history and bridges, synthetic superclass and widget rebuild."""
import argparse,pathlib,subprocess,tempfile,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import jvm_recovery238 as r,patch_npc_stats246 as p,patch_corpse_click244 as v

def screen_subset(jar):
 fields={'graph','graphs','tab','canvas','undo','redo','summaries','selected','revision','dirty','importError','status','sidebarScroll'}
 methods={'remember','history','changed','settledlands$remember','settledlands$changed','settledlands$graph','settledlands$canvas','mouseClicked','addAt'}
 raw=r.subset(jar,p.SCREEN,methods,fields=fields);k=p.classedit.Klass(raw);extra=[]
 def add(n,d,b,stack,loc,acc=1):extra.append(p.new_method(k,n,d,b,stack,loc,acc))
 add('<init>','()V',b'\x2a'+p.op(0xb7,k.add_methodref('net/minecraft/client/gui/screens/Screen','<init>','()V'))+b'\xb1',1,1)
 add('widgets','()V',b'\xb1',0,1,2);add('tr','(Ljava/lang/String;)Ljava/lang/String;',b'\x2b\xb0',1,2,2)
 add('activeDropdown','()Ldev/settledlands/npc/client/NpcUi$NpcDropdown;',b'\x01\xb0',1,1);add('settledlands$contextOpen','()Z',b'\x03\xac',1,1);add('settledlands$insideInspector','(DD)Z',b'\x03\xac',1,5)
 add('settledlands$mouseClickedOriginal','(DDI)Z',b'\x03\xac',1,6);add('settledlands$hideStatsInspector','()V',b'\xb1',0,1)
 return v.rebuild(k,k.methods+extra)

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 with tempfile.TemporaryDirectory(prefix='undo246-') as d:
  out=pathlib.Path(d);files=sorted((ROOT/'tests/java246-ui').rglob('*.java'))+sorted((ROOT/'tests/java246-graph').rglob('*.java'))+sorted((ROOT/'tests/java246-undo').rglob('*.java'))
  files=[f for f in files if f.name not in ('GraphStatsTest.java','GraphUiTest.java')]
  subprocess.run([str(a.java.resolve()),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-g:none','-nowarn','-cp',str(a.jar.resolve()),'-d',str(out),*map(str,files)],check=True)
  (out/(p.SCREEN+'.class')).write_bytes(screen_subset(a.jar))
  subprocess.run([str(a.java.resolve()),'-Xshare:off','-Xverify:all','-cp',str(out)+':'+str(a.jar.resolve()),'GraphUndoTest'],check=True)
 print('Synthetic GUI, initial fields and widget rebuild; real Minecraft screen not run.')
if __name__=='__main__':main()
