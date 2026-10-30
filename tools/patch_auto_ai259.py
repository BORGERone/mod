#!/usr/bin/env python3
"""Deterministic Build 259 NPC movement/combo tuning from exact Build 258."""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import gen_api258
import patch_auto_ai257 as prior
import patch_auto_ai258 as previous

BASE_SHA = '15698d90c57f2aca7b38e30474a130be1d9c17a489c08bf73e61b221553ad19e'
BASE_VERSION = '0.8.0-build258-stats-abi-fix'
NEW_VERSION = '0.8.0-build259-npc-combo-tuning'
ENGINE = previous.ENGINE
CHANGED = {
    ENGINE + '.class',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}
CHANGELOG = (
    'Build 259: stop explicitly turning NPCs toward their combat target while navigating; '
    'raise calm route-walking speed by 10%; use Ivy multi-hit jab-cross/jab-jab-hook sequences '
    'for melee attacks, including lunge attacks.'
)
PATCHINFO = (
    'сборка 259: NPC не поворачивает корпус к цели во время ходьбы; скорость спокойной ходьбы повышена на 10%; '
    'в ближнем бою используются серии ударов Иви, включая атаки с рывком.'
)


def compile_engine(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> bytes:
    api = work / 'api'
    out = work / 'classes'
    gen_api258.main(api)
    out.mkdir()
    sources = sorted(api.rglob('*.java')) + [ROOT / 'src/build259/dev/settledlands/npc/NpcAutoAiEngine255.java']
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == prior.prev.ECJ_SHA
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', str(base), '-d', str(out), *map(str, sources)],
        check=True,
    )
    return (out / (ENGINE + '.class')).read_bytes()


def patch_entries(entries: dict[str, bytes], compiled: bytes) -> dict[str, bytes]:
    updated = dict(entries)
    updated[ENGINE + '.class'] = compiled
    toml_name = 'META-INF/neoforge.mods.toml'
    text = entries[toml_name].decode('utf-8')
    old_version = f'version="{BASE_VERSION}"'
    marker = "description='''"
    assert text.count(old_version) == 1 and text.count(marker) == 1
    text = text.replace(old_version, f'version="{NEW_VERSION}"', 1).replace(
        marker, marker + CHANGELOG + '\n\n', 1
    )
    assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
    updated[toml_name] = text.encode('utf-8')
    updated['settledlands-patch-info.txt'] = (
        PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode('utf-8')
    ).encode('utf-8')
    changed = {name for name in entries if entries[name] != updated[name]}
    assert changed == CHANGED, (changed ^ CHANGED, changed)
    assert set(updated) == set(entries)
    return updated


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, out, java = a.base.resolve(), a.out.resolve(), a.java.resolve()
    assert base != out and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-259-compile-') as td:
        compiled = compile_engine(java, base, pathlib.Path(td))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            updated = patch_entries(original, compiled)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, updated[info.filename])
    print('wrote', out, 'bytes=', out.stat().st_size,
          'sha256=', hashlib.sha256(out.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
