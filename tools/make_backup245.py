#!/usr/bin/env python3
"""Create a reproducible build 245 backup with guide, tests and input JARs."""
import argparse
import pathlib
import zipfile

import make_backup238 as legacy
import make_backup239 as b239
import make_backup240 as b240
import make_backup241 as b241
import make_backup242 as b242
import make_backup243 as b243
import make_backup244 as b244

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR_NAME = 'settledlands-0.8.0-build245-body-wear.jar'
JAR_SHA = 'b6825213caa98096881f02659f81f719cb5f86588c22c2899ccca09f139aca86'
DOCS = ['README-Build245-BodyWear-RU.txt','CHECK-JAR-245-RU.txt',
        'CHECKLIST-TEST-BUILD245-RU.md','SHA256SUMS-build245.txt'] + b244.DOCS


def main():
    ap = argparse.ArgumentParser()
    for flag in ('inputs','base237','base238','base239','base240','base241','base242',
                 'base243','base244','jar','out'):
        ap.add_argument('--'+flag,type=pathlib.Path,required=True)
    a = ap.parse_args()
    baseinfo = [(a.base237,legacy.BASE_NAME,legacy.BASE_SHA),(a.base238,legacy.JAR_NAME,legacy.JAR_SHA),
                (a.base239,b239.JAR_NAME,b239.JAR_SHA),(a.base240,b240.JAR_NAME,b240.JAR_SHA),
                (a.base241,b241.JAR_NAME,b241.JAR_SHA),(a.base242,b242.JAR_NAME,b242.JAR_SHA),
                (a.base243,b243.JAR_NAME,b243.JAR_SHA),(a.base244,b244.JAR_NAME,b244.JAR_SHA)]
    assert len(DOCS) == len(set(DOCS)), 'duplicate documentation entry'
    sources = [a.jar]+[path for path,_,_ in baseinfo]+[a.inputs/name for name in legacy.INPUTS]+[ROOT/name for name in DOCS]
    if a.out.resolve() in {x.resolve() for x in sources}: raise SystemExit('refusing to overwrite an input or documentation')
    entries = {name:(ROOT/name).read_bytes() for name in DOCS}
    entries[JAR_NAME] = legacy.read_expected(a.jar,JAR_SHA)
    for path,name,sha in baseinfo: entries['test-inputs/'+name] = legacy.read_expected(path,sha)
    for name,sha in legacy.INPUTS.items(): entries['test-inputs/'+name] = legacy.read_expected(a.inputs/name,sha)
    for directory in ('tools','tests'):
        for file in sorted((ROOT/directory).iterdir()):
            if file.is_file() and file.suffix in ('.py','.sh'): entries[f'{directory}/{file.name}'] = file.read_bytes()
    assert {'AI-GUIDE-SettledLands.md','README-Build245-BodyWear-RU.txt','CHECK-JAR-245-RU.txt',
            'CHECKLIST-TEST-BUILD245-RU.md','tools/patch_body_wear245.py','tools/make_backup245.py',
            'tests/gate_build245.py','tests/wear_fixture245.py','tests/jvm_body_wear245.py',
            'tests/jvm_new_body245.py','tests/jvm_wear_login245.py','test-inputs/'+b244.JAR_NAME} <= set(entries)
    entries['BACKUP-MANIFEST.sha256'] = ''.join(f'{legacy.digest(data)}  {name}\n' for name,data in sorted(entries.items())).encode()
    with zipfile.ZipFile(a.out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,28,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o100644<<16
            z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(a.out) as z:
        if z.testzip() is not None or len(z.namelist()) != len(set(z.namelist())) or set(z.namelist()) != set(entries):
            raise SystemExit('backup ZIP invalid')
        for name,data in entries.items():
            if z.read(name) != data: raise SystemExit('backup ZIP differs: '+name)
        assert z.read('AI-GUIDE-SettledLands.md') == (ROOT/'AI-GUIDE-SettledLands.md').read_bytes()
    data=a.out.read_bytes()
    print(f'backup OK: {a.out}, {len(data)} bytes, sha256 {legacy.digest(data)}, {len(entries)} entries')


if __name__ == '__main__': main()
