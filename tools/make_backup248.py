#!/usr/bin/env python3
"""Independent deterministic backup for experimental 248.
No Minecraft, TnM GPL JAR, NeoForge, Java or generated JOML classes are bundled.
Historical inputs and the MIT JOML source/license are included. Rebuild and
run the full gate from an independent unpack with an external pinned TnM JAR.
"""
import argparse,hashlib,pathlib,sys,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tests'));import gate_build248 as gate

EXCLUDED={'GATE-RESULT-248.txt','GATE-BACKUP-RESULT-248.txt','SHA256SUMS-build248.txt'}
def digest(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--inputs',required=True,type=pathlib.Path);p.add_argument('--out',required=True,type=pathlib.Path);a=p.parse_args();files={}
 def add(path,name):
  assert path.is_file() and name not in files,(path,name);files[name]=path.read_bytes()
 for glob in ('*.md','*.txt'):
  for path in sorted(ROOT.glob(glob)):
   if path.is_file() and path.name not in EXCLUDED:add(path,path.name)
 for folder in ('tools','tests','src/build246','src/build247','src/build248'):
  for path in sorted((ROOT/folder).rglob('*')):
   if path.is_file() and not any(part.startswith('.') or part=='__pycache__' for part in path.relative_to(ROOT).parts) and path.suffix not in ('.pyc','.pyo'):
    add(path,path.relative_to(ROOT).as_posix())
 for name in ('settledlands-0.8.0-build245-body-wear.jar','settledlands-0.8.0-build246-npc-characteristics.jar','settledlands-0.8.0-build247-bodiless-protection.jar','settledlands-0.8.0-build248-touchmotion-player.jar','SettledLands-Build228-SceneFix-Source.zip'):
  add(ROOT/name,name)
 input_names=sorted(set(gate.g247.g246.NAMES.values())|set(gate.g237.NAMES.values()))
 for name in input_names:add(a.inputs/name,'test-inputs/'+name)
 assert digest(files['settledlands-0.8.0-build245-body-wear.jar'])==gate.g247.g246.p.BASE_SHA
 assert digest(files['settledlands-0.8.0-build246-npc-characteristics.jar'])==gate.g247.p.BASE_SHA
 assert digest(files['settledlands-0.8.0-build247-bodiless-protection.jar'])==gate.p.BASE_SHA
 assert digest(files['settledlands-0.8.0-build248-touchmotion-player.jar'])==gate.SHA
 assert digest(files['tools/vendor/ecj-3.46.100.jar'])==gate.g247.g246.p.ECJ_SHA
 assert digest(files['tools/vendor/joml-1.10.5-source.tar.gz'])==gate.p.build_joml248.SHA
 assert {'AI-GUIDE-SettledLands.md','tools/vendor/LICENSE-JOML.txt','BACKUP-248-RU.txt'} <= files.keys()
 assert not any(n.lower().endswith('.jar') and ('touch_n_motion' in n.lower() or 'emf_compat' in n.lower()) for n in files)
 manifest=''.join(digest(files[n])+'  '+n+'\n' for n in sorted(files));files['BACKUP-MANIFEST.sha256']=manifest.encode('utf8')
 a.out.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.out,'w') as z:
  for name in sorted(files):
   info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
   z.writestr(info,files[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 with zipfile.ZipFile(a.out) as z:assert z.testzip() is None and len(z.namelist())==len(files)
 print(f'{a.out}: {a.out.stat().st_size} bytes sha256 {digest(a.out.read_bytes())}; files={len(files)}')
if __name__=='__main__':main()
