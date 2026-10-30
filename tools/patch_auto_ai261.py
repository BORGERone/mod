#!/usr/bin/env python3
"""Deterministic Build 261 from Build 260: idle head scan, unified NPC animation, Punchy hands."""
import argparse
import hashlib
import os
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
import patch_auto_ai260 as previous

BASE_SHA = '8d47171845652c1a3499643ca1936a7d6b742d9e88811751d94d84bef47d2ebc'
BASE_VERSION = '0.8.0-build260-npc-facing-sounds'
NEW_VERSION = '0.8.0-build261-idle-look-punchy-animation'
ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255'
BRIDGE = 'dev/settledlands/model/NpcMotionBridge256'
FP = 'dev/settledlands/npc/client/FirstPersonPossession256'
PUNCHY_MIXIN = 'dev/settledlands/mixin/PunchyFirstPersonContextMixin261'
MIXIN_CONFIG = 'settledlands.mixins.json'
CHANGED = {
    ENGINE + '.class', BRIDGE + '.class', FP + '.class', FP + '$RenderContext.class',
    PUNCHY_MIXIN + '.class', MIXIN_CONFIG,
    'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt',
}
CHANGELOG = (
    'Build 261: give idle NPCs a smooth vanilla-like head-only look-around; keep Ivy Fresh Animations, '
    'Touch\'n Motion, Blockbench custom clips, dialogue, hurt, and locomotion on one renderer path, '
    'preserving walking leg motion under full-body clips; bracket the first-person hand pass so '
    'PlayerModel-based renderers such as Punchy can use the possessed NPC\'s animated arm bones and skin.'
)
PATCHINFO = (
    'сборка 261: добавлен плавный случайный взгляд idle-NPC только головой; для Иви единый путь '
    'Fresh Animations/Touch\'n Motion/Blockbench при диалоге, уроне и ходьбе; ноги сохраняют локомоцию '
    'во время полнотельных клипов при ходьбе; первый-person PlayerModel рендер получает руки и текстуру '
    'NPC, включая совместимые рендереры вроде Punchy.'
)


def compile_sources(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> dict[str, bytes]:
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == previous.previous.prior.prev.ECJ_SHA
    result: dict[str, bytes] = {}

    api_engine = work / 'api-engine'
    out_engine = work / 'classes-engine'
    gen_api261.main_engine(api_engine)
    out_engine.mkdir()
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', str(base), '-d', str(out_engine),
         *map(str, sorted(api_engine.rglob('*.java'))),
         str(ROOT / 'src/build261/dev/settledlands/npc/NpcAutoAiEngine255.java')],
        check=True,
    )
    result[ENGINE + '.class'] = (out_engine / (ENGINE + '.class')).read_bytes()

    joml = work / 'joml'
    build_joml248.build(java, joml)
    api_bridge = work / 'api-bridge'
    out_bridge = work / 'classes-bridge'
    gen_api261.main_bridge(api_bridge)
    out_bridge.mkdir()
    bridge_sources = [
        ROOT / 'src/build261/dev/settledlands/model/NpcMotionBridge256.java',
        ROOT / 'src/build261/dev/settledlands/npc/client/FirstPersonPossession256.java',
        ROOT / 'src/build261/dev/settledlands/mixin/PunchyFirstPersonContextMixin261.java',
    ]
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', str(base) + os.pathsep + str(joml), '-d', str(out_bridge),
         *map(str, sorted(api_bridge.rglob('*.java'))), *map(str, bridge_sources)],
        check=True,
    )
    for rel in (BRIDGE + '.class', FP + '.class', FP + '$RenderContext.class',
                FP + '$PartSnapshot.class', PUNCHY_MIXIN + '.class'):
        path = out_bridge / rel
        if path.is_file():
            result[rel] = path.read_bytes()
    assert BRIDGE + '.class' in result and FP + '$RenderContext.class' in result
    assert PUNCHY_MIXIN + '.class' in result
    return result


def patch_entries(entries: dict[str, bytes], compiled: dict[str, bytes]) -> dict[str, bytes]:
    updated = dict(entries)
    updated.update(compiled)
    cfg = __import__('json').loads(entries[MIXIN_CONFIG])
    client = cfg['client']
    assert PUNCHY_MIXIN.split('/')[-1] not in client
    client.append(PUNCHY_MIXIN.split('/')[-1])
    updated[MIXIN_CONFIG] = (__import__('json').dumps(cfg, indent=2, ensure_ascii=False) + '\n').encode('utf-8')

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
    assert set(updated) == set(entries) | {PUNCHY_MIXIN + '.class'}
    return updated


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, out, java = a.base.resolve(), a.out.resolve(), a.java.resolve()
    assert base != out and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-261-compile-') as td:
        compiled = compile_sources(java, base, pathlib.Path(td))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            updated = patch_entries(original, compiled)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, updated[info.filename])
                for name in sorted(set(updated) - set(original)):
                    info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    target.writestr(info, updated[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    print('wrote', out, 'bytes=', out.stat().st_size,
          'sha256=', hashlib.sha256(out.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
