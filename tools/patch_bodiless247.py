#!/usr/bin/env python3
"""Deterministic build 247 from exact 246; require full gate before distribution."""
import argparse,hashlib,json,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import gen_api247,patch_npc_stats246 as prev
BASE_SHA='fe62c48b5bfbb0607cf1fa1e9996649bcd90a144de57ca991dfb737ff78cc819'
NEW_VERSION='0.8.0-build247-bodiless-protection'
PROTECTION='dev/settledlands/npc/BodilessProtection'
ADDED={PROTECTION+'.class'}
CHANGED={'META-INF/neoforge.mods.toml','settledlands-patch-info.txt'}
CHANGELOG='Build 247: bodiless player attacks and attributed damage are blocked in the Overworld, but allowed in the Purgatory. Attributed poison, wither and hunger are also blocked there. Milk no longer removes bodilessness; other cures and possession remain unchanged.'
PATCHINFO='сборка 247: атаки и приписанный бестелесному урон в Верхнем мире запрещены; яд, иссушение и голод от него тоже. В чистилище бой разрешён. Молоко больше не снимает бестелесность.'

def compile_sources(java,work):
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(compiler.read_bytes()).hexdigest()==prev.ECJ_SHA
 api,out=work/'api',work/'classes';gen_api247.main(api);out.mkdir()
 sources=sorted(api.rglob('*.java'))+sorted((ROOT/'src/build247').rglob('*.java'))
 subprocess.run([str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn','-d',str(out),*map(str,sources)],check=True)
 return {name:(out/name).read_bytes() for name in ADDED}

def patch_entries(entries,compiled):
 assert set(compiled)==ADDED and not ADDED.intersection(entries);updated=dict(entries);updated.update(compiled)
 name='META-INF/neoforge.mods.toml';text=entries[name].decode();old='version="'+prev.NEW_VERSION+'"';marker="description='''"
 assert text.count(old)==1 and text.count(marker)==1;text=text.replace(old,'version="'+NEW_VERSION+'"',1).replace(marker,marker+CHANGELOG+'\n\n',1)
 assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION;updated[name]=text.encode()
 updated['settledlands-patch-info.txt']=(PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
 assert {n for n in entries if entries[n]!=updated[n]}==CHANGED and set(updated)-set(entries)==ADDED
 return updated

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--base',type=pathlib.Path,required=True);ap.add_argument('--out',type=pathlib.Path,required=True);ap.add_argument('--java',type=pathlib.Path,required=True);a=ap.parse_args()
 assert a.base.resolve()!=a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest()==BASE_SHA
 with tempfile.TemporaryDirectory(prefix='settledlands-247-compile-') as td:
  compiled=compile_sources(a.java.resolve(),pathlib.Path(td))
  with zipfile.ZipFile(a.base) as base:
   assert base.testzip() is None and len(base.namelist())==len(set(base.namelist()))
   entries={i.filename:base.read(i.filename) for i in base.infolist()};updated=patch_entries(entries,compiled);a.out.parent.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(a.out,'w') as out:
    for info in base.infolist():out.writestr(info,updated[info.filename])
    for name in sorted(ADDED):
     info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,updated[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 with zipfile.ZipFile(a.out) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
 data=a.out.read_bytes();print(f'{a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')
if __name__=='__main__':main()
