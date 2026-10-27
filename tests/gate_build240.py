#!/usr/bin/env python3
"""Complete build 240 gate, including all of build 239/238/237 and §8.

The behavioral fixture runs the real possession/graph bytecode on a real JVM
with synthetic game dependencies, not Minecraft/NeoForge itself.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import sys
import tempfile
import tomllib
import zipfile

import gate_build237 as g237
import gate_build238 as g238

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
import classedit
import decode
import patch_npc_local_cult240 as patch
from patch_corpse_pose237 import constant_ref, find_invoke

PY = sys.executable
EXPECTED_SHA = '2e4a29f6f147d6d081234ea5b7d3839b9a5ba297e984b3962b6f3113e47d76b5'
EXPECTED_SIZE = 1544935
BRIDGE_DESC = '(L' + patch.EVENT + ';)V'
TARGETS = [
    patch.BRIDGE + '#onInteractSpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
    patch.BRIDGE + '#mirror(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.Entity)',
    patch.GATE + '#onEntityInteractSpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
    patch.GATE + '#cult(dev.settledlands.npc.GuideNpc)',
    patch.GATE + '#possess(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc)',
]


def executable_calls(z, owner, name, desc):
    calls = []
    for entry in z.namelist():
        if not entry.endswith('.class'):
            continue
        k = classedit.Klass(z.read(entry))
        for m in k.methods:
            if not any(a['name'] == 'Code' for a in m['attrs']):
                continue
            for pc, raw in decode.walk(classedit.code_parts(k, m)['code']):
                if raw[0] in (0xb6, 0xb7, 0xb8, 0xb9):
                    i = struct.unpack('>H', raw[1:3])[0]
                    if constant_ref(k, i) == (owner, name, desc):
                        calls.append((entry, m['name'], m['desc'], pc))
    return calls


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as j:
        names = set(b.namelist())
        assert len(names) == len(b.namelist()) and len(set(j.namelist())) == len(j.namelist())
        assert names == set(j.namelist())
        changed = {name for name in names if b.read(name) != j.read(name)}
        assert changed == patch.EXPECTED_DIFF, changed
        before = b.read(patch.BRIDGE + '.class'); after = j.read(patch.BRIDGE + '.class')
        g238.unchanged_methods(before, after, {('onInteractSpecific', BRIDGE_DESC)})
        assert after == patch.patch_bridge(before), 'unexpected bridge edits'
        bridge = classedit.Klass(after); m = bridge.find_method('onInteractSpecific', BRIDGE_DESC)
        assert classedit.code_parts(bridge, m)['code'] == b'\0' * 11 + b'\xb1'
        mirror = executable_calls(j, patch.BRIDGE, 'mirror', '(L' + patch.PLAYER + ';L' + patch.ENTITY + ';)V')
        assert mirror == [], ('some other caller can still mirror player points', mirror)
        poss_desc = '(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;)V'
        calls = executable_calls(j, patch.GATE, 'possess', poss_desc)
        assert [(x[0], x[1]) for x in calls] == [(patch.GATE + '.class', 'onEntityInteractSpecific')], calls
        assert j.read(patch.GATE + '.class') == b.read(patch.GATE + '.class')
        assert j.read(patch.DIALOGUE + '.class') == b.read(patch.DIALOGUE + '.class')
        g = classedit.Klass(j.read(patch.GATE + '.class'))
        handler = g.find_method('onEntityInteractSpecific', BRIDGE_DESC)
        body = classedit.code_parts(g, handler)['code']
        pos, n = find_invoke(g, body, patch.GATE, 'cult', '(Ldev/settledlands/npc/GuideNpc;)I')
        assert n == 3 and body[pos + n:pos + n + 2] == b'\x04\xa2'  # >= 1
        local = g.find_method('cult', '(Ldev/settledlands/npc/GuideNpc;)I')
        body = classedit.code_parts(g, local)['code']
        find_invoke(g, body, 'net/minecraft/nbt/CompoundTag', 'getCompound', '(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;')
        find_invoke(g, body, 'net/minecraft/nbt/CompoundTag', 'getInt', '(Ljava/lang/String;)I')
        values = []
        for _, ins in decode.walk(body):
            if ins[0] in (0x12, 0x13):
                index = ins[1] if ins[0] == 0x12 else struct.unpack('>H', ins[1:3])[0]
                e = g.cp[index]
                if e and e['tag'] == 8:
                    values.append(g.utf8(e['a']))
        assert values == ['settledlands_local_vars', 'культ'], values
        for lang in patch.NEW_MESSAGES:
            path = f'assets/settledlands/lang/{lang}.json'
            before_lang, after_lang = json.loads(b.read(path)), json.loads(j.read(path))
            assert set(before_lang) == set(after_lang)
            assert after_lang[patch.LANG_KEY] == patch.NEW_MESSAGES[lang]
            assert {k: v for k, v in before_lang.items() if k != patch.LANG_KEY} == \
                   {k: v for k, v in after_lang.items() if k != patch.LANG_KEY}
        mod = tomllib.loads(j.read('META-INF/neoforge.mods.toml').decode())['mods'][0]
        assert mod['version'] == patch.NEW_VERSION and mod['description'].startswith(patch.CHANGELOG)
        assert j.read('settledlands-patch-info.txt').decode().startswith(patch.PATCHINFO)
    print('OK exact 239→240 diff; sole bridge subscriber changed; original NPC-local gate and altar untouched')


def mutant_gate(jar, dest, mode):
    """Verifier-valid faulty gates used to calibrate behavior tests."""
    with zipfile.ZipFile(jar) as z:
        raw = z.read(patch.GATE + '.class')
    k = classedit.Klass(raw)
    if mode == 'bypass':
        m = k.find_method('cult', '(Ldev/settledlands/npc/GuideNpc;)I')
        parts = classedit.code_parts(k, m); code = bytearray(parts['code'])
        pos, n = find_invoke(k, code, 'net/minecraft/nbt/CompoundTag', 'getInt', '(Ljava/lang/String;)I')
        assert n == 3
        code[pos:pos + n] = b'\x58\x04\x00'  # pop tag/key, return constant 1; no new dead block
    elif mode == 'gt-not-ge':
        m = k.find_method('onEntityInteractSpecific', BRIDGE_DESC)
        parts = classedit.code_parts(k, m); code = bytearray(parts['code'])
        pos, n = find_invoke(k, code, patch.GATE, 'cult', '(Ldev/settledlands/npc/GuideNpc;)I')
        assert n == 3 and code[pos + n:pos + n + 2] == b'\x04\xa2'
        code[pos + n + 1] = 0xa3  # > 1 rather than >= 1
    else:
        raise ValueError(mode)
    parts['code'] = bytes(code)
    changed = k.dump(m, classedit.build_code_body(k, parts))
    g237.alter_zip(jar, dest, replace={patch.GATE + '.class': changed})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True, type=pathlib.Path)
    ap.add_argument('--base237', required=True, type=pathlib.Path)
    ap.add_argument('--base238', required=True, type=pathlib.Path)
    ap.add_argument('--base239', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    a = ap.parse_args()
    inputs, base237, base238, base239, jar, java = (x.resolve() for x in
        (a.inputs, a.base237, a.base238, a.base239, a.jar, a.java))
    assert hashlib.sha256(base239.read_bytes()).hexdigest() == patch.BASE_SHA, 'incorrect base 239'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == EXPECTED_SHA and jar.stat().st_size == EXPECTED_SIZE, ('incorrect candidate', digest)
    print(f'build240 size={jar.stat().st_size} sha256={digest}')

    g237.run('complete build 239 gate including 238/237 and HANDOFF §8',
             [PY, ROOT / 'tests/gate_build239.py', '--inputs', inputs, '--base237', base237,
              '--base238', base238, '--jar', base239, '--java', java], contains=('BUILD 239 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build240-') as td:
        tmp = pathlib.Path(td); rebuilt = tmp / 'rebuilt.jar'
        g237.run('240 deterministic bytecode patch', [PY, TOOLS / 'patch_npc_local_cult240.py',
                 '--base', base239, '--out', rebuilt], contains=('sha256 ' + EXPECTED_SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes(), 'candidate differs from reproducible build'
        g237.run('240 class structure, frames and opcodes', [PY, TOOLS / 'validate_jar.py', jar, '--base', base239],
                 contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, TOOLS / 'verify_jar.py']
        g237.run('240 CRC, TOML, languages, mixins and exact diff', verify + [jar, '--base', base239] +
                 g237.option_entries('--expect-changed', sorted(patch.EXPECTED_DIFF)),
                 contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(base239, jar)
        bad_crc = tmp / 'bad-crc.jar'; bad_crc.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('240 CRC negative', verify + [bad_crc], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        lang = 'assets/settledlands/lang/ru_ru.json'
        missing = tmp / 'missing-lang.jar'; g237.alter_zip(jar, missing, remove={lang})
        g237.run('240 missing language negative', verify + [missing], success=False, contains=('ПРОБЛЕМЫ: языки',))
        invalid = tmp / 'invalid-lang.jar'; g237.alter_zip(jar, invalid, replace={lang: b'{broken'})
        g237.run('240 invalid language negative', verify + [invalid], success=False, contains=('ПРОБЛЕМЫ: языки',))
        with zipfile.ZipFile(jar) as z:
            ru = json.loads(z.read(lang))
        del ru[patch.LANG_KEY]
        mismatch = tmp / 'mismatched-lang.jar'; g237.alter_zip(jar, mismatch, replace={lang: json.dumps(ru).encode()})
        g237.run('240 mismatched language negative', verify + [mismatch], success=False, contains=('ПРОБЛЕМЫ: языки',))
        missing_mixin = tmp / 'missing-mixin.jar'; g237.alter_zip(jar, missing_mixin, remove={'settledlands.mixins.json'})
        g237.run('240 missing mixin negative', verify + [missing_mixin], success=False, contains=('ПРОБЛЕМЫ: mixins',))
        g237.run('240 wrong allowlist negative', verify + [jar, '--base', base239, '--expect-changed', 'fake.class'],
                 success=False, contains=('НЕОЖИДАННЫЙ ДИФФ',))
        jvm = [PY, TOOLS / 'jvm_verify.py']
        for target in TARGETS:
            g237.run('240 real JVM verifier ' + target.split('#')[1].split('(')[0],
                     jvm + [jar, target, '--java', java], contains=('DECLARED ', 'ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
            g237.run('240 renderer regression ' + fixture, [PY, ROOT / 'tests' / fixture, '--java', java,
                     '--base236', inputs / g237.NAMES[236], '--jar', jar],
                     contains=('236 negative control', '237 JVM execution'))
        g237.run('240 recovery and health JVM regression', [PY, ROOT / 'tests/jvm_recovery238.py', '--java', java,
                 '--base237', base237, '--jar', jar], contains=('237 negative control', '238 JVM execution'))
        g237.run('240 corpse/coffin cleanup JVM regression', [PY, ROOT / 'tests/jvm_cleanup239.py', '--java', java,
                 '--base238', base238, '--jar', jar], contains=('238 negative control', '239 JVM execution'))
        cult = [PY, ROOT / 'tests/jvm_npc_cult240.py', '--java', java, '--base239', base239, '--jar']
        g237.run('240 local-NPC-cult JVM behavior vs 239 negative control', cult + [jar],
                 contains=('239 negative control', '240 JVM execution', 'not a NeoForge/in-game test'))
        with zipfile.ZipFile(base239) as z:
            old_bridge = z.read(patch.BRIDGE + '.class')
        restored = tmp / 'restored-bridge.jar'; g237.alter_zip(jar, restored, replace={patch.BRIDGE + '.class': old_bridge})
        g237.run('240 JVM negative: bridge copies player points', cult + [restored], success=False,
                 contains=('AssertionError: mismatched=', 'personal_missing.removed'))
        for mode, expected in [('bypass', 'personal_missing.removed'), ('gt-not-ge', 'one.removed')]:
            bad = tmp / (mode + '.jar'); mutant_gate(jar, bad, mode)
            g237.run('240 JVM negative: ' + mode, cult + [bad], success=False,
                     contains=('AssertionError: mismatched=', expected))
    print('BUILD 240 GATE: PASS (real JVM with synthetic dependencies; NOT an in-game test)')


if __name__ == '__main__':
    main()
