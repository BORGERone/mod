#!/usr/bin/env python3
"""Real NpcBodyStats + BodyWear, explicit synthetic attribute and event APIs."""
import argparse,pathlib,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import wear_fixture245 as x,fixture_stats_regression246 as extra

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 with tempfile.TemporaryDirectory(prefix='body-stats246-') as d:
  root=pathlib.Path(d);classes=extra.install(x.build(a.jar),a.jar)
  for name,raw in classes.items():
   path=root/(name+'.class');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
  files=sorted((ROOT/'tests/java246-body').rglob('*.java'))
  subprocess.run([str(a.java.resolve()),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(root),'-d',str(root),*map(str,files)],check=True)
  subprocess.run([str(a.java.resolve()),'-Xshare:off','-Xverify:all','-cp',str(root),'BodyStatsTest'],check=True)
 print('Synthetic entities, attributes, death clone and incoming-damage event; no Minecraft runtime.')
if __name__=='__main__':main()
