#!/usr/bin/env python3
"""Actual graph classes, synthetic NBT; this is not a Minecraft run."""
import argparse,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True,type=pathlib.Path);p.add_argument('--jar',required=True,type=pathlib.Path);a=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='graph246-') as d:
  out=pathlib.Path(d);sources=sorted((ROOT/'tests/java246-graph').rglob('*.java'))
  subprocess.run([str(a.java.resolve()),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-g:none','-nowarn','-cp',str(a.jar.resolve()),'-d',str(out),*map(str,sources)],check=True)
  subprocess.run([str(a.java.resolve()),'-Xshare:off','-Xverify:all','-cp',str(out)+':'+str(a.jar.resolve()),'GraphStatsTest'],check=True)
 print('Synthetic NBT; Minecraft/NeoForge not run.')
if __name__=='__main__':main()
