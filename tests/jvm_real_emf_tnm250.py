#!/usr/bin/env python3
"""Actual EMF 3.3.12 root + actual TnM 1.0.4 hook, synthetic MC/pack/providers.
EMF 3.3.9 and 3.3.12 root/API/handler upstream sources are identical; this does
NOT execute 3.3.9 bytecode, an actual pack, NeoForge or Minecraft.
External binaries are GPL and must be supplied separately, never redistributed.
"""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import build_joml248
from jvm_fresh_motion250 import merge
from jvm_real_tnm104_249 import generate,MANUAL_SRC
SHA={
 'tnm':'3754909bf0d12445b18a60de315619734c1229bcc621f88f315ccf076701ab56',
 'emf':'3ba0bf7b487660920d12cc0ff6cb5de1763f024a080fe0cd7d64e1c8e0f7f190',
 'etf':'b47173468febc21bb565af19a82bcd2f851e34ecb333660cdc48a8c37c27c1cd'}
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 q=argparse.ArgumentParser()
 for flag in ('java','jar','tnm','emf','etf'):q.add_argument('--'+flag,required=True,type=pathlib.Path)
 a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve();tnm=a.tnm.resolve();emf=a.emf.resolve();etf=a.etf.resolve()
 for flag in ('tnm','emf','etf'):assert digest(locals()[flag])==SHA[flag],flag
 with tempfile.TemporaryDirectory(prefix='realemftnm250-') as d:
  tmp=pathlib.Path(d);joml=tmp/'joml';out=tmp/'classes';out.mkdir();generated=tmp/'generated';build_joml248.build(java,joml)
  with zipfile.ZipFile(tnm) as z:assert generate(generated,z)==25
  keep=('strm/touchnmotion/compat/FirstPersonHead.java','strm/touchnmotion/compat/ShadowPass.java','strm/touchnmotion/platform/Platform.java','net/minecraft/world/entity/animal/horse/AbstractHorse.java','strm/emfcompat/core/ik/IKFrame.java','org/slf4j/Logger.java','org/slf4j/LoggerFactory.java','strm/touchnmotion/footgrounding/compat/HorseFootGrounding.java','strm/emfcompat/core/PoseSnapshot.java','traben/entity_model_features/models/animation/state/EMFBipedPose.java')
  for name in keep:
   p=generated/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(MANUAL_SRC[name]+'\n')
  extra={
   'strm/touchnmotion/cape/CapeFollow.java':'''package strm.touchnmotion.cape; import org.joml.Matrix4f;import java.util.*;import java.util.function.Function;import net.minecraft.client.model.geom.ModelPart;import net.minecraft.world.entity.player.Player; public class CapeFollow { public static Matrix4f pose(ModelPart p){return new Matrix4f();} public static void keep(UUID id,Matrix4f m,ModelPart p){} public static Matrix4f capeFrame(Player p,float f){return null;} public static void carry(UUID id,Function<String,ModelPart> f,Matrix4f a,Matrix4f b){} }''',
   'strm/emfcompat/core/EMFCompatCore.java':'''package strm.emfcompat.core; public class EMFCompatCore { public static boolean isLocalPlayerInFirstPerson(java.util.UUID id){return false;} }'''}
  for name,text in extra.items():
   p=generated/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n')
  src=merge(ROOT/'tests/java249',ROOT/'tests/java250',generated,ROOT/'tests/java250-realemf')
  for n in ('strm/touchnmotion/TouchNMotionHook.java','testing/Fresh250Scenarios.java'):src.pop(n,None)
  cp=':'.join(map(str,[jar,joml,tnm]));subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',cp,'-d',str(out),*map(str,sorted(src.values()))],check=True)
  # Make the VM load the supplied genuine EMF root, not the compile stub.
  (out/'traben/entity_model_features/models/parts/EMFModelPartRoot.class').unlink()
  cp=':'.join(map(str,[out,emf,etf,jar,joml,tnm]))
  for mode in ('standard','variant'):
   r=subprocess.run([str(java),'-Xverify:all','-DactualEmf=true','-DactualEmfPath='+str(emf),*(['-DvariantTest=true'] if mode=='variant' else []),'-cp',cp,'testing.RealTnmFresh250Scenario'],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS actual TnM 1.0.4' in r.stdout and 'actual EMF 3.3.12 root' in r.stdout,(mode,r.stdout,r.stderr)
   print(r.stdout.strip(),mode,flush=True)
 print('PASS actual EMF 3.3.12 root and TnM hook, two variants; synthetic animation engine/pack/providers; NOT a game test',flush=True)
if __name__=='__main__':main()
