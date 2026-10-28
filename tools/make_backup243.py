#!/usr/bin/env python3
"""Build a reproducible, verified 243 source/test backup with all input JARs."""
import argparse
import pathlib
import zipfile
import make_backup238 as legacy
import make_backup239 as b239
import make_backup240 as b240
import make_backup241 as b241
import make_backup242 as b242

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAR_NAME='settledlands-0.8.0-build243-body-without-queue.jar'
JAR_SHA='a78c2943ec481aab0ed9b9fa67b6aa09a905d019b46b9866a5e6feab91cc584c'
DOCS=['README-Build243-BodyWithoutQueue-RU.txt','CHECK-JAR-243-RU.txt','SHA256SUMS-build243.txt',
      'SHA256SUMS-build242.txt','CHECKLIST-TEST-BUILD242-RU.md']+b242.DOCS


def main():
    ap=argparse.ArgumentParser()
    for flag in ('inputs','base237','base238','base239','base240','base241','base242','jar','out'):
        ap.add_argument('--'+flag,type=pathlib.Path,required=True)
    a=ap.parse_args()
    baseinfo=[(a.base237,legacy.BASE_NAME,legacy.BASE_SHA),(a.base238,legacy.JAR_NAME,legacy.JAR_SHA),
              (a.base239,b239.JAR_NAME,b239.JAR_SHA),(a.base240,b240.JAR_NAME,b240.JAR_SHA),
              (a.base241,b241.JAR_NAME,b241.JAR_SHA),(a.base242,b242.JAR_NAME,b242.JAR_SHA)]
    sources=[a.jar]+[x[0] for x in baseinfo]+[a.inputs/name for name in legacy.INPUTS]+[ROOT/name for name in DOCS]
    if a.out.resolve() in {x.resolve() for x in sources}:raise SystemExit('refusing to overwrite an input or documentation')
    entries={name:(ROOT/name).read_bytes() for name in DOCS}
    entries[JAR_NAME]=legacy.read_expected(a.jar,JAR_SHA)
    for path,name,sha in baseinfo:entries['test-inputs/'+name]=legacy.read_expected(path,sha)
    for name,sha in legacy.INPUTS.items():entries['test-inputs/'+name]=legacy.read_expected(a.inputs/name,sha)
    for directory in ('tools','tests'):
        for file in sorted((ROOT/directory).iterdir()):
            if file.is_file() and file.suffix in ('.py','.sh'):entries[f'{directory}/{file.name}']=file.read_bytes()
    assert {'AI-GUIDE-SettledLands.md','tools/patch_noqueue243.py','tools/patch_death_scope243.py',
            'tools/patch_cultprobe243.py','tools/make_backup243.py','tests/gate_build243.py',
            'tests/jvm_noqueue243.py','tests/jvm_noqueue_altar243.py','tests/jvm_queue_disabled243.py',
            'tests/jvm_server_tick243.py','tests/jvm_login243.py','tests/jvm_cultprobe243.py',
            'test-inputs/'+b242.JAR_NAME} <= set(entries)
    entries['BACKUP-MANIFEST.sha256']=''.join(f'{legacy.digest(data)}  {name}\n' for name,data in sorted(entries.items())).encode()
    with zipfile.ZipFile(a.out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(a.out) as z:
        if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(entries):
            raise SystemExit('backup ZIP invalid')
        for name,data in entries.items():
            if z.read(name)!=data:raise SystemExit('backup ZIP differs: '+name)
        assert z.read('AI-GUIDE-SettledLands.md')==(ROOT/'AI-GUIDE-SettledLands.md').read_bytes()
    raw=a.out.read_bytes();print(f'backup OK: {a.out}, {len(raw)} bytes, sha256 {legacy.digest(raw)}, {len(entries)} entries')


if __name__=='__main__':main()
