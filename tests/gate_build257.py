#!/usr/bin/env python3
"""Release gate 257: deterministic 256-based AI tuning/stats patch and Java 21 behavior regressions."""
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
import gate_build256 as base_gate
import patch_auto_ai257 as patch

BASE_SHA = patch.BASE_SHA
BASE_SIZE = 2254719
CHANGED = patch.CHANGED


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


def baseline_gate() -> None:
    base = ROOT / 'settledlands-0.8.0-build256-npc-fp-corpse.jar'
    assert base.is_file() and base.stat().st_size == BASE_SIZE and sha(base) == BASE_SHA
    for name in ('GATE-RESULT-256.txt', 'GATE-BACKUP-RESULT-256.txt'):
        log = ROOT / name
        assert log.is_file() and 'BUILD 256 GATE: PASS' in log.read_text(encoding='utf-8')
    checksums = ROOT / 'SHA256SUMS-build256.txt'
    run(['sha256sum', '-c', str(checksums)], 'verify recorded build-256 release and dual-gate checksums')
    print('OK exact build 256 base and independently repeated prior full release gates', flush=True)


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as zin, zipfile.ZipFile(jar) as zout:
        assert zin.testzip() is None and zout.testzip() is None
        old = {item.filename: zin.read(item.filename) for item in zin.infolist()}
        new = {item.filename: zout.read(item.filename) for item in zout.infolist()}
    assert set(new) == set(old)
    assert {name for name in old if old[name] != new[name]} == CHANGED
    assert all(old[name] == new[name] for name in old if name not in CHANGED)

    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION
    assert meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)

    old_cls = classedit.Klass(old[patch.GUIDE + '.class'])
    new_cls = classedit.Klass(new[patch.GUIDE + '.class'])
    changed_methods = set()
    for method in old_cls.methods:
        name = old_cls.utf8(method['name_idx'])
        desc = old_cls.utf8(method['desc_idx'])
        newer = new_cls.find_method(name, desc)
        assert newer is not None
        before = next((a['body'] for a in method['attrs'] if a['name'] == 'Code'), None)
        after = next((a['body'] for a in newer['attrs'] if a['name'] == 'Code'), None)
        if before != after:
            changed_methods.add((name, desc))
    assert changed_methods == {
        ('customServerAiStep', '()V'),
        ('walkingNavigationSpeed', '(Z)D'),
    }, changed_methods

    engine = classedit.Klass(new[patch.ENGINE + '.class'])
    for entry in engine.cp:
        if entry and entry['tag'] == 7:
            name = engine.utf8(entry['a'])
            assert not name.startswith(('net/minecraft/client/', 'dev/settledlands/npc/client/')), name
    print('OK exact four-entry diff; only two GuideNpc methods changed; engine has no client references', flush=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, jar, java, gson = (p.resolve() for p in (a.base, a.jar, a.java, a.gson_source))
    assert base == (ROOT / 'settledlands-0.8.0-build256-npc-fp-corpse.jar').resolve()
    assert jar.is_file() and java.is_file() and gson.is_file()
    assert sha(base) == BASE_SHA
    baseline_gate()

    with tempfile.TemporaryDirectory(prefix='sl257-gate-') as td:
        rebuilt = pathlib.Path(td) / 'reproduced257.jar'
        run([sys.executable, str(ROOT / 'tools/patch_auto_ai257.py'),
             '--base', str(base), '--out', str(rebuilt), '--java', str(java)],
            'deterministic Build 257 rebuild')
        assert rebuilt.read_bytes() == jar.read_bytes()
        print(f'OK reproducible candidate sha256={sha(jar)} size={jar.stat().st_size}', flush=True)

    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(base)], 'base-256 validator calibration')
    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar), '--base', str(base)], 'candidate structural validator')
    run([sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base)], 'candidate ZIP/TOML/assets/mixins validator')
    static_semantics(base, jar)

    targets = [
        'dev/settledlands/npc/NpcAutoAiEngine255#legacyAiMode(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcAutoAiEngine255#syncStats(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcAutoAiEngine255#navigationSpeedFactor(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcAutoAiEngine255#runMobilityAndAutoAi(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/GuideNpc#customServerAiStep',
    ]
    run([sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        'Java 21 verifier declarations for new NPC AI paths')
    run([sys.executable, str(ROOT / 'tests/jvm_auto_ai257.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 inherited Auto-AI 255 and new 257 end-to-end scenarios under -Xverify:all')
    run([sys.executable, str(ROOT / 'tests/mutants257.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'four verifier-valid negative controls for stats sync, calm pace, legacy priority, and dwell')
    print('BUILD 257 GATE: PASS (calm walk half-speed; combat pace preserved; 20–45 s dwell; hostile toggle priority; node-backed NPC HP/damage/speed; inherited build 255 scenarios; four verifier-valid mutants killed; actual Minecraft/NeoForge/visual test NOT run)', flush=True)


if __name__ == '__main__':
    main()
