#!/usr/bin/env python3
"""Combined external PAL/HnS, EMF 3.3.12 root and TnM 1.0.4 hook, real actor.

Minecraft, FA animation handler, TnM feature providers, PAL processor/events and
Fastutil are test doubles. EMF/TnM binaries, PAL, Gson and HnS are external and
never packaged; this is NOT a Minecraft, NeoForge or visual test.
"""
import argparse,hashlib,os,pathlib,shutil,subprocess,sys,tarfile,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import build_joml248
from jvm_fresh_motion250 import merge
from jvm_real_tnm104_249 import generate,MANUAL_SRC
from jvm_real_emf_tnm250 import SHA as EXT
PAL_SHA='e41ee06000fc95444a482018ec24b3f1e79eb111ca6b8ca119e61ceabf9136c5'
HNS_SHA='0e1e54fc03adcd59bd1d46f821d42788bc8d9757cd8f41ed118cfc30c241a7bb'
GSON_SOURCE_SHA='e358710d663ca799101a51d0568ccc6dbd30be9bc55b1a85b1a21a93e96e8a9b'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--pal',required=True,type=pathlib.Path);q.add_argument('--hns',required=True,type=pathlib.Path);q.add_argument('--gson-source',required=True,type=pathlib.Path)
 for flag in ('tnm','emf','etf'):q.add_argument('--'+flag,required=True,type=pathlib.Path)
 a=q.parse_args()
 java=a.java.resolve();jar=a.jar.resolve();pal=a.pal.resolve();hns=a.hns.resolve();gson=a.gson_source.resolve()
 assert sha(pal)==PAL_SHA and sha(hns)==HNS_SHA and sha(gson)==GSON_SOURCE_SHA,'unexpected external input'
 tnm=a.tnm.resolve();emf=a.emf.resolve();etf=a.etf.resolve();assert sha(tnm)==EXT['tnm'] and sha(emf)==EXT['emf'] and sha(etf)==EXT['etf']
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
  generated=tmp/'generated'
  with zipfile.ZipFile(tnm) as z:assert generate(generated,z)==25
  keep=('strm/touchnmotion/compat/FirstPersonHead.java','strm/touchnmotion/compat/ShadowPass.java','strm/touchnmotion/platform/Platform.java','net/minecraft/world/entity/animal/horse/AbstractHorse.java','strm/emfcompat/core/ik/IKFrame.java','org/slf4j/Logger.java','org/slf4j/LoggerFactory.java','strm/touchnmotion/footgrounding/compat/HorseFootGrounding.java','strm/emfcompat/core/PoseSnapshot.java','traben/entity_model_features/models/animation/state/EMFBipedPose.java')
  for name in keep:
   p=generated/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(MANUAL_SRC[name]+'\n')
  extra={
   'strm/touchnmotion/cape/CapeFollow.java':'''package strm.touchnmotion.cape; import org.joml.Matrix4f;import java.util.*;import java.util.function.Function;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.world.entity.player.Player; public class CapeFollow { public static Matrix4f pose(ModelPart p){return new Matrix4f();} public static void keep(UUID id,Matrix4f m,ModelPart p){} public static Matrix4f capeFrame(Player p,float f){return null;} public static void carry(UUID id,Function<String,ModelPart> f,Matrix4f a,Matrix4f b){} }''',
   'strm/emfcompat/core/EMFCompatCore.java':'''package strm.emfcompat.core; public class EMFCompatCore { public static boolean isLocalPlayerInFirstPerson(java.util.UUID id){return false;} }'''}
  for name,text in extra.items():
   p=generated/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n')
  excluded={'dev/settledlands/model/BbModel.java','testing/Bridge249Scenarios.java','testing/Math249Scenario.java','testing/Fresh250Scenarios.java','testing/RealTnmFresh250Scenario.java'}
  merged=merge(ROOT/'tests/java249',ROOT/'tests/java250',generated,ROOT/'tests/java250-realemf',ROOT/'tests/java249-realmodel',ROOT/'tests/java251',ROOT/'tests/java251-hns');merged.pop('strm/touchnmotion/TouchNMotionHook.java',None)
  src=[p for name,p in merged.items() if name not in excluded];cp=':'.join(map(str,[jar,pal,tnm,autostub,gout,mocha,javassist,joml]))
  subprocess.run(common+['-cp',cp,'-d',str(out),*map(str,sorted(src))],check=True)
  (out/'traben/entity_model_features/models/parts/EMFModelPartRoot.class').unlink()
  cp=':'.join(map(str,[out,emf,etf,jar,pal,autostub,gout,mocha,javassist,joml,tnm]))
  r=subprocess.run([str(java),'-Xverify:all','-DcombinedExternal=true','-DactualEmfPath='+str(emf),'-DactualTnmPath='+str(tnm),'-cp',cp,'testing.RealHns251Scenario',str(hns)],capture_output=True,text=True)
  assert r.returncode==0 and 'PASS real HnS JSON 39 attacks / 195 sampled frames' in r.stdout and 'actual EMF 3.3.12 root and TnM 1.0.4 hook' in r.stdout,(r.stdout,r.stderr)
  print(r.stdout.strip());print('Combined external PAL/EMF root/TnM hook + HnS data; synthetic animation engine/providers/processor/game, NOT a game or visual test')
if __name__=='__main__':main()
