#!/usr/bin/env python3
"""Deterministic build 257 from exact build 256; stats, calm pace, and Auto-AI priority fix."""
import argparse
import hashlib
import pathlib
import subprocess
import struct
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import classedit
import decode
import gen_api257
import patch_corpse_click244 as v244
import patch_npc_stats246 as prev
from patch_corpse_pose237 import assert_pool_roundtrip, constant_ref

BASE_SHA = '024937159ceb6f7173591dacf30d0c46b2fcc5233e8b495e4918d56b715c2021'
OLD_VERSION = '0.8.0-build256-npc-fp-corpse'
NEW_VERSION = '0.8.0-build257-calm-ai-stats'
ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255'
GUIDE = 'dev/settledlands/npc/GuideNpc'
CHANGED = {
    ENGINE + '.class',
    GUIDE + '.class',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}
CHANGELOG = (
    'Build 257: halve peaceful NPC walking speed while preserving full build-255 combat pace; '
    'increase idle stays at wander destinations to 20–45 seconds; prioritize enabled hostile-mob '
    'Auto-AI over the legacy combat tick so hostile targets are not starved; synchronize NPC max '
    'health, attack damage, and movement speed from its Characteristics graph node.'
)
PATCHINFO = (
    'сборка 257: мирная ходьба вдвое медленнее при прежней скорости в бою; остановки в точках '
    'прогулки увеличены до 20–45 секунд; включённая автоатака врагов имеет приоритет над старым '
    'боевым тиком; максимум здоровья, урон и скорость NPC синхронизируются с его нодой '
    '«Характеристики».'
)


def compile_engine(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> bytes:
    api = work / 'api'
    out = work / 'classes'
    gen_api257.main(api)
    out.mkdir()
    sources = sorted(api.rglob('*.java')) + [ROOT / 'src/build257/dev/settledlands/npc/NpcAutoAiEngine255.java']
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == prev.ECJ_SHA
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', str(base), '-d', str(out), *map(str, sources)],
        check=True,
    )
    return (out / (ENGINE + '.class')).read_bytes()


def patch_guide(raw: bytes) -> bytes:
    # Synchronize node stats at entry; a four-byte insertion only shifts existing frames.
    raw = prev.insert_before(
        raw, 'customServerAiStep', '()V', lambda _k, _code: 0,
        lambda k: prev.static_call(k, ENGINE, 'syncStats', f'(L{GUIDE};)V', b'\x2a'),
        stack=2,
    )

    # Replace only the first aiMode() call. The helper returns zero while Auto-Attack
    # is enabled, so the original branch skips legacy IvyCombatAi.tick() without new
    # branch targets or hand-built StackMapTable frames.
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    method = k.find_method('customServerAiStep', '()V')
    parts = classedit.code_parts(k, method)
    code = bytearray(parts['code'])
    matches = [pc for pc, ins in decode.walk(code)
               if len(ins) == 3 and ins[0] == 0xB6
               and constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (GUIDE, 'aiMode', '()I')]
    assert matches, 'customServerAiStep aiMode call not found'
    pc = matches[0]
    code[pc] = 0xB8
    code[pc + 1:pc + 3] = struct.pack('>H', k.add_methodref(ENGINE, 'legacyAiMode', f'(L{GUIDE};)I'))
    parts['code'] = bytes(code)
    raw = k.dump(method, classedit.build_code_body(k, parts))

    # walkingNavigationSpeed is inverse to MOVEMENT_SPEED. After node modifiers are
    # applied, multiply once by the node value and by 0.5 only when the NPC is calm.
    def walking_speed(k):
        attrs = 'net/minecraft/world/entity/ai/attributes/Attributes'
        holder = 'Lnet/minecraft/core/Holder;'
        code = (
            b'\x2a'
            + prev.op(0xB2, k.add_fieldref(attrs, 'MOVEMENT_SPEED', holder))
            + prev.op(0xB6, k.add_methodref(GUIDE, 'getAttributeValue', f'({holder})D'))
            + b'\x1b'
            + prev.op(0xB8, k.add_methodref('dev/settledlands/npc/EscortRules', 'navigationSpeed', '(DZ)D'))
            + b'\x2a'
            + prev.op(0xB8, k.add_methodref(ENGINE, 'navigationSpeedFactor', f'(L{GUIDE};)D'))
            + b'\x6b\xaf'
        )
        return code

    return prev.replace_code(raw, 'walkingNavigationSpeed', '(Z)D', walking_speed, 4, 2)


def patch_entries(entries: dict[str, bytes], compiled: bytes) -> dict[str, bytes]:
    assert ENGINE + '.class' in entries and GUIDE + '.class' in entries
    updated = dict(entries)
    updated[ENGINE + '.class'] = compiled
    updated[GUIDE + '.class'] = patch_guide(entries[GUIDE + '.class'])

    toml_name = 'META-INF/neoforge.mods.toml'
    text = entries[toml_name].decode('utf-8')
    old = f'version="{OLD_VERSION}"'
    marker = "description='''"
    assert text.count(old) == 1 and text.count(marker) == 1
    text = text.replace(old, f'version="{NEW_VERSION}"', 1).replace(
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', required=True, type=pathlib.Path)
    parser.add_argument('--out', required=True, type=pathlib.Path)
    parser.add_argument('--java', required=True, type=pathlib.Path)
    args = parser.parse_args()
    base = args.base.resolve()
    out = args.out.resolve()
    assert base != out and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-257-compile-') as tmp:
        compiled = compile_engine(args.java.resolve(), base, pathlib.Path(tmp))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            changed = patch_entries(original, compiled)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, changed[info.filename])
    print('wrote', out, 'bytes=', out.stat().st_size,
          'sha256=', hashlib.sha256(out.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
