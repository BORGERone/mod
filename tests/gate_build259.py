#!/usr/bin/env python3
"""Release gate 259: movement orientation, faster calm pace, and Ivy multi-hit combos."""
import argparse
import hashlib
import pathlib
import struct
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit
import patch_auto_ai259 as patch
from patch_corpse_pose237 import constant_ref

BASE_SHA = patch.BASE_SHA
BASE_SIZE = 2256623


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


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

    raw = new[patch.ENGINE + '.class']
    engine = classedit.Klass(raw)
    abi = ('net/minecraft/world/entity/ai/attributes/AttributeInstance',
           'removeModifier', '(Lnet/minecraft/resources/ResourceLocation;)Z')
    refs = []
    for index, entry in enumerate(engine.cp):
        if entry and entry['tag'] in (10, 11):
            ref = constant_ref(engine, index)
            if ref[0].endswith('/AttributeInstance') and ref[1] == 'removeModifier':
                refs.append(ref)
    assert refs and set(refs) == {abi}, refs
    enum_class = classedit.Klass(old[patch.ENGINE + '$MeleeCombo.class'])
    enum_utf8 = [enum_class.utf8(i) for i, entry in enumerate(enum_class.cp)
                 if entry and entry['tag'] == 1]
    assert 'orthodox_left_jab_right_cross' in enum_utf8
    assert 'orthodox_jab_jab_hook' in enum_utf8
    doubles = [struct.unpack('>d', entry['raw'])[0] for entry in engine.cp
               if entry and entry['tag'] == 6]
    assert 0.55 in doubles
    source = (ROOT / 'src/build259/dev/settledlands/npc/NpcAutoAiEngine255.java').read_text(encoding='utf-8')
    movement = source.split('double distSq = npc.distanceToSqr(target);', 1)[1].split(
        'private LivingEntity findHostileTarget', 1)[0]
    assert 'faceTarget(npc, target, 0.35F)' not in movement
    assert 'getLookControl().setLookAt(target' not in movement
    assert 'return rng.nextBoolean() ? MeleeCombo.JAB_RIGHT_CROSS : MeleeCombo.JAB_JAB_HOOK;' in source
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION and meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)
    print('OK three-entry diff; 1.21.1 boolean ABI preserved; movement does not target-face; multi-hit animations and 0.55 pace present', flush=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, jar, java, gson = (p.resolve() for p in (a.base, a.jar, a.java, a.gson_source))
    assert base == (ROOT / 'settledlands-0.8.0-build258-stats-abi-fix.jar').resolve()
    assert base.is_file() and base.stat().st_size == BASE_SIZE and sha(base) == BASE_SHA
    assert jar.is_file() and java.is_file() and gson.is_file()
    assert 'BUILD 258 GATE: PASS' in (ROOT / 'GATE-RESULT-258.txt').read_text(encoding='utf-8')
    assert 'BUILD 258 GATE: PASS' in (ROOT / 'GATE-BACKUP-RESULT-258.txt').read_text(encoding='utf-8')
    print('OK exact Build 258 ancestry and recorded gate evidence', flush=True)

    with tempfile.TemporaryDirectory(prefix='sl259-gate-') as td:
        reproduced = pathlib.Path(td) / 'reproduced259.jar'
        run([sys.executable, str(ROOT / 'tools/patch_auto_ai259.py'), '--base', str(base),
             '--out', str(reproduced), '--java', str(java)], 'deterministic Build 259 rebuild')
        assert reproduced.read_bytes() == jar.read_bytes()
        print(f'OK reproducible JAR sha256={sha(jar)} size={jar.stat().st_size}', flush=True)

    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar), '--base', str(base)],
        'structural JAR validator')
    run([sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base)],
        'ZIP/TOML/assets/mixins validator')
    static_semantics(base, jar)
    targets = [
        'dev/settledlands/npc/NpcAutoAiEngine255#syncStats(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcAutoAiEngine255#navigationSpeedFactor(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/GuideNpc#customServerAiStep',
    ]
    run([sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        'Java 21 verification of AI and preserved Stats ABI')
    run([sys.executable, str(ROOT / 'tests/jvm_auto_ai259.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 scenarios: default/changed Stats, combat target, Ivy combo follow-up hit, faster calm pace')
    run([sys.executable, str(ROOT / 'tests/mutants259.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'four verifier-valid Auto-AI/Stats regression mutants')
    print('BUILD 259 GATE: PASS (body-target turning removed during navigation; calm speed +10%; multi-hit Ivy combos; 1.21.1 ABI retained; 4 mutants killed; full game not run)', flush=True)

if __name__ == '__main__':
    main()
