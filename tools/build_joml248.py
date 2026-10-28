#!/usr/bin/env python3
"""Build MIT-licensed JOML 1.10.5 from the pinned source archive for tests.

The generated classes are NOT included in the SettledLands mod jar. Minecraft
already supplies JOML. Only the upstream default Java preprocessing flags are
selected; no upstream Java sources are modified in the archive.
"""
import hashlib,pathlib,subprocess,sys,tarfile,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/'tools/vendor/joml-1.10.5-source.tar.gz'
SHA='003ecf4b6be6bea0aa81de5015c923a567debed8bca44e70a02735ed65da5f4b'
ECJ_SHA='74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'
DEFINES={'__JRE__','__HAS_NIO__','__HAS_UNSAFE__','__HAS_MATH_FMA__','__HAS_INTEGER_SIGNUM__'}

def preprocess(text):
 out=[];stack=[];active=True
 for line in text.splitlines(keepends=True):
  s=line.strip()
  if s.startswith('//#ifdef '):
   ok=s.split()[1] in DEFINES;stack.append((active,ok));active=active and ok
  elif s.startswith('//#ifndef '):
   ok=s.split()[1] not in DEFINES;stack.append((active,ok));active=active and ok
  elif s=='//#else':
   parent,ok=stack[-1];stack[-1]=(parent,not ok);active=parent and not ok
  elif s=='//#endif':active=stack.pop()[0]
  elif active:out.append(line)
 assert not stack
 return ''.join(out)

def build(java,dest):
 assert hashlib.sha256(SRC.read_bytes()).hexdigest()==SHA
 ecj=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(ecj.read_bytes()).hexdigest()==ECJ_SHA
 dest=pathlib.Path(dest);dest.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='joml-pre248-') as work:
  work=pathlib.Path(work);files=[]
  with tarfile.open(SRC,'r:gz') as tar:
   for info in tar.getmembers():
    parts=pathlib.PurePosixPath(info.name).parts
    if not info.isfile() or len(parts)<5 or parts[1:4]!=('src','org','joml') or 'jre' in parts or not info.name.endswith('.java'):continue
    out=work/pathlib.Path(*parts[1:]);out.parent.mkdir(parents=True,exist_ok=True)
    raw=tar.extractfile(info).read().decode('utf-8');out.write_text(preprocess(raw),encoding='utf-8');files.append(out)
  assert len(files)==81,len(files)
  subprocess.run([str(java),'-jar',str(ecj),'-21','-nowarn','-g:none','-d',str(dest),*map(str,sorted(files))],check=True)
 assert (dest/'org/joml/Matrix4f.class').exists();print('JOML 1.10.5 (upstream MIT source) compiled for tests:',dest)

if __name__=='__main__':
 if len(sys.argv)!=3:raise SystemExit('usage: build_joml248.py JAVA OUT_DIR')
 build(pathlib.Path(sys.argv[1]).resolve(),pathlib.Path(sys.argv[2]).resolve())
