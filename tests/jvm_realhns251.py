#!/usr/bin/env python3
"""Execute supplied HnS attack JSON through genuine PAL parser/controller, plus real actor.

PAL's Molang platform-event wiring, Fastutil collections, Minecraft, EMF and
TnM are test doubles. Gson is compiled locally from its official source archive;
no third-party binary or user content is packaged or committed.
"""
import argparse,hashlib,os,pathlib,shutil,subprocess,sys,tarfile,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import build_joml248
from jvm_fresh_motion250 import merge
PAL_SHA='e41ee06000fc95444a482018ec24b3f1e79eb111ca6b8ca119e61ceabf9136c5'
HNS_SHA='0e1e54fc03adcd59bd1d46f821d42788bc8d9757cd8f41ed118cfc30c241a7bb'
GSON_SOURCE_SHA='e358710d663ca799101a51d0568ccc6dbd30be9bc55b1a85b1a21a93e96e8a9b'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--pal',required=True,type=pathlib.Path);q.add_argument('--hns',required=True,type=pathlib.Path);q.add_argument('--gson-source',required=True,type=pathlib.Path);a=q.parse_args()
 java=a.java.resolve();jar=a.jar.resolve();pal=a.pal.resolve();hns=a.hns.resolve();gson=a.gson_source.resolve()
 assert sha(pal)==PAL_SHA and sha(hns)==HNS_SHA and sha(gson)==GSON_SOURCE_SHA,'unexpected external input'
 with tempfile.TemporaryDirectory(prefix='realhns251-') as d:
  tmp=pathlib.Path(d);fake=tmp/'auto';fake.mkdir();env=dict(os.environ,TMPDIR=str(fake))
  r=subprocess.run([sys.executable,str(ROOT/'tools/jvm_verify.py'),str(jar),'dev/settledlands/model/BbModel#getName','--java',str(java)],env=env,capture_output=True,text=True);assert r.returncode==0 and 'DECLARED dev/settledlands/model/BbModel#getName' in r.stdout,(r.stdout,r.stderr)
  autos=list(fake.glob('jvmverify-*'));assert len(autos)==1;autostub=autos[0]
  for n in ('java','javax','jdk','sun','org/joml','com/zigythebird'):shutil.rmtree(autostub/n,ignore_errors=True)
  with tarfile.open(gson,'r:gz') as t:
   for m in t.getmembers():
    assert not m.issym() and not m.islnk() and (tmp/'gson'/m.name).resolve().is_relative_to((tmp/'gson').resolve())
   t.extractall(tmp/'gson')
  sr=next((tmp/'gson').iterdir())/'gson/src';gout=tmp/'gsonclasses';gout.mkdir();template=sr/'main/java-templates/com/google/gson/internal/GsonBuildConfig.java';gen=tmp/'GsonBuildConfig.java';gen.write_text(template.read_text().replace('${project.version}','2.10.1'))
  compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';common=[str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn']
  subprocess.run(common+['-d',str(gout),*map(str,sorted((sr/'main/java/com').rglob('*.java'))),str(gen)],check=True)
  with zipfile.ZipFile(pal) as z:
   mocha=tmp/'mocha.jar';mocha.write_bytes(z.read('META-INF/jars/mochafloats-4.1.0.jar'));javassist=tmp/'javassist.jar';javassist.write_bytes(z.read('META-INF/jars/javassist-3.30.2-GA.jar'))
  assert sha(mocha)=='583f287565ea75c84df35573a9d449648e214c0a949418c1d7efcf276fe067bc'
  assert sha(javassist)=='0877af9f34806369cff06c0f74fad9135a8a322d4c654ee754b301fb440b1ebf'
  joml=tmp/'joml';build_joml248.build(java,joml);out=tmp/'out';out.mkdir()
  excluded={'dev/settledlands/model/BbModel.java','testing/Bridge249Scenarios.java','testing/Math249Scenario.java','testing/Fresh250Scenarios.java','testing/RealTnmFresh250Scenario.java'}
  merged=merge(ROOT/'tests/java249',ROOT/'tests/java250',ROOT/'tests/java249-realmodel',ROOT/'tests/java251',ROOT/'tests/java251-hns')
  src=[p for name,p in merged.items() if name not in excluded];cp=':'.join(map(str,[jar,pal,autostub,gout,mocha,javassist,joml]))
  subprocess.run(common+['-cp',cp,'-d',str(out),*map(str,sorted(src))],check=True)
  cp=':'.join(map(str,[out,jar,pal,autostub,gout,mocha,javassist,joml]))
  r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.RealHns251Scenario',str(hns)],capture_output=True,text=True)
  assert r.returncode==0 and 'PASS real HnS JSON 39 attacks / 195 sampled frames' in r.stdout,(r.stdout,r.stderr)
  print(r.stdout.strip());print('Real PAL loading/interpolation/mixin; controlled processor and synthetic game/EMF/TnM, NOT a game or visual test')
if __name__=='__main__':main()
