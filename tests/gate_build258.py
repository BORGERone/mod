#!/usr/bin/env python3
"""Release gate 258: fix the 1.21.1 AttributeInstance.removeModifier ABI mismatch."""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit
import gate_build257 as prior_gate
import patch_auto_ai258 as patch
from patch_corpse_pose237 import constant_ref

BASE_SHA = patch.BASE_SHA
BASE_SIZE = 2256459


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


def prior_evidence(base: pathlib.Path) -> None:
    assert base.is_file() and base.stat().st_size == BASE_SIZE and sha(base) == BASE_SHA
    for name in ('GATE-RESULT-257.txt', 'GATE-BACKUP-RESULT-257.txt'):
        text = (ROOT / name).read_text(encoding='utf-8')
        assert 'BUILD 257 GATE: PASS' in text
    # The prior build passed synthetic tests, but its uploaded crash exposed this missed ABI.
    assert prior_gate.sha((ROOT / 'settledlands-0.8.0-build257-calm-ai-stats.jar')) == BASE_SHA
    print('OK exact Build 257 ancestry and recorded prior gates; this hotfix adds real ABI conformance', flush=True)


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as zin, zipfile.ZipFile(jar) as zout:
        assert zin.testzip() is None and zout.testzip() is None
        old = {i.filename: zin.read(i.filename) for i in zin.infolist()}
        new = {i.filename: zout.read(i.filename) for i in zout.infolist()}
    assert set(new) == set(old)
    assert {name for name in old if old[name] != new[name]} == patch.CHANGED
    for name in old:
        if name not in patch.CHANGED:
            assert old[name] == new[name], name
    assert new['dev/settledlands/npc/GuideNpc.class'] == old['dev/settledlands/npc/GuideNpc.class']

    engine = classedit.Klass(new[patch.ENGINE + '.class'])
    boolean_ref = ('net/minecraft/world/entity/ai/attributes/AttributeInstance',
                   'removeModifier', '(Lnet/minecraft/resources/ResourceLocation;)Z')
    void_ref = ('net/minecraft/world/entity/ai/attributes/AttributeInstance',
                'removeModifier', '(Lnet/minecraft/resources/ResourceLocation;)V')
    refs = []
    for index, entry in enumerate(engine.cp):
        if entry and entry['tag'] in (10, 11):
            ref = constant_ref(engine, index)
            if ref[0].endswith('/AttributeInstance') and ref[1] == 'removeModifier':
                refs.append(ref)
    assert refs and set(refs) == {boolean_ref}, refs
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION and meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)
    print('OK exact three-entry hotfix; only ABI changed, call descriptor is removeModifier(ResourceLocation)Z', flush=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, jar, java, gson = (p.resolve() for p in (a.base, a.jar, a.java, a.gson_source))
    assert base == (ROOT / 'settledlands-0.8.0-build257-calm-ai-stats.jar').resolve()
    assert jar.is_file() and java.is_file() and gson.is_file()
    prior_evidence(base)
    with tempfile.TemporaryDirectory(prefix='sl258-gate-') as td:
        reproduced = pathlib.Path(td) / 'reproduced258.jar'
        run([sys.executable, str(ROOT / 'tools/patch_auto_ai258.py'), '--base', str(base),
             '--out', str(reproduced), '--java', str(java)], 'deterministic build-258 ABI hotfix rebuild')
        assert reproduced.read_bytes() == jar.read_bytes()
        print(f'OK reproducible hotfix sha256={sha(jar)} size={jar.stat().st_size}', flush=True)
    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(base)], 'base-257 validator calibration')
    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar), '--base', str(base)], 'hotfix structural validator')
    run([sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base)], 'hotfix ZIP/TOML/assets/mixins validator')
    static_semantics(base, jar)
    targets = [
        'dev/settledlands/npc/NpcAutoAiEngine255#syncStats(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcAutoAiEngine255#navigationSpeedFactor(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/GuideNpc#customServerAiStep',
    ]
    run([sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        'Java 21 verification of NPC synchronization and navigation')
    run([sys.executable, str(ROOT / 'tests/jvm_auto_ai258.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 functional scenarios with exact boolean AttributeInstance ABI and default-stat removal path')
    run([sys.executable, str(ROOT / 'tests/mutants258.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'four verifier-valid Auto-AI/Stats negative controls')
    print('BUILD 258 GATE: PASS (1.21.1 removeModifier(ResourceLocation)Z; default and changed Stats; preserved Auto-AI behavior; 4 mutants killed; full game not run)', flush=True)

if __name__ == '__main__':
    main()
