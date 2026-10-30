#!/usr/bin/env python3
"""Deterministically patch Build 261 with smooth travel-facing and idle torso scanning."""
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
import build_joml248
import gen_api261

BASE_SHA = '979d64831b47336a84a72ec0d803da4f19d5ee6e1fdb85e542e7e08b03647df6'
BASE_VERSION = '0.8.0-build261-idle-look-punchy-animation'
NEW_VERSION = '0.8.0-build262-smooth-npc-turning'
ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255'
CHANGED = {
    ENGINE + '.class', 'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt',
}
CHANGELOG = (
    'Build 262: preserve NPC travel-facing yaw when movement ends, then smoothly vary torso/entity yaw '
    'and head direction during idle; retain Build 261 animation, dialogue, combat, wandering and Punchy support.'
)
PATCHINFO = (
    'сборка 262: направление корпуса при ходьбе сохраняется после остановки; затем корпус и голова '
    'плавно и случайно поворачиваются в idle. Сохранены механики, анимации и совместимость сборки 261.'
)


def compile_engine(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> bytes:
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == build_joml248.ECJ_SHA
    api = work / 'api'
    out = work / 'classes'
    gen_api261.main_engine(api)
    out.mkdir()
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', str(base), '-d', str(out), *map(str, sorted(api.rglob('*.java'))),
         str(ROOT / 'src/build262/dev/settledlands/npc/NpcAutoAiEngine255.java')],
        check=True,
    )
    return (out / (ENGINE + '.class')).read_bytes()


def patch_entries(entries: dict[str, bytes], engine: bytes) -> dict[str, bytes]:
    updated = dict(entries)
    updated[ENGINE + '.class'] = engine
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
    changed = {name for name in updated if name not in entries or entries[name] != updated[name]}
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
    with tempfile.TemporaryDirectory(prefix='settledlands-262-compile-') as td:
        engine = compile_engine(java, base, pathlib.Path(td))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            updated = patch_entries(original, engine)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, updated[info.filename])
    print('wrote', out, 'bytes=', out.stat().st_size,
          'sha256=', hashlib.sha256(out.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
