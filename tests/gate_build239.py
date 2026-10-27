#!/usr/bin/env python3
"""Complete build 239 release gate (including 238/237 and HANDOFF §8).

Requires Python 3.11, Java 21 and inputs 230, 232–236. Executes actual mod
bytecode on a real JVM with synthetic game dependencies; NOT an in-game test.
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

import gate_build237 as gate237
import gate_build238 as gate238

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
import classedit
import decode
import patch_corpse_cleanup239 as patch
from patch_corpse_pose237 import constant_ref, find_invoke

PY = sys.executable
EXPECTED_SHA = 'b68fd79357da507fff35939964816c2a301101ac3afb31298bf4ea31ab7a4290'
EXPECTED_SIZE = 1544746
TRY_DESC = '(L' + patch.PLAYER + ';L' + patch.ENTITY + ';)V'


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as j:
        names = set(b.namelist())
        assert len(names) == len(b.namelist()) and len(set(j.namelist())) == len(j.namelist())
        assert names == set(j.namelist())
        changed = {name for name in names if b.read(name) != j.read(name)}
        assert changed == patch.EXPECTED_DIFF, changed
        before = b.read(patch.HELPER + '.class'); after = j.read(patch.HELPER + '.class')
        gate238.unchanged_methods(before, after, {('tryClaim', TRY_DESC)})
        assert after == patch.patch_recovery(before), 'unexpected helper changes'
        # Explicitly check behavior-critical files, beyond the exact diff above.
        for path in ('dev/settledlands/npc/PlayerCorpsePossession.class',
                     'dev/settledlands/npc/GuideCorpseNpc.class',
                     'dev/settledlands/npc/IvyDeathScene.class',
                     'dev/settledlands/revival/PurgatoryManager.class', 'settledlands.mixins.json',
                     'assets/settledlands/lang/ru_ru.json', 'assets/settledlands/lang/en_us.json'):
            assert b.read(path) == j.read(path), path
        mod = tomllib.loads(j.read('META-INF/neoforge.mods.toml').decode())['mods'][0]
        assert mod['version'] == patch.NEW_VERSION and mod['description'].startswith(patch.CHANGELOG)
        assert j.read('settledlands-patch-info.txt').decode().startswith(patch.PATCHINFO)
    print('OK exact 238→239 diff; only recovery tryClaim changed; normal death, health, render and tools unchanged')


def mutate(path, dest, mode):
    """Create verifier-valid, deliberately faulty recovery code for calibration."""
    with zipfile.ZipFile(path) as z:
        raw = z.read(patch.HELPER + '.class')
    k = classedit.Klass(raw)
    method = k.find_method('tryClaim', TRY_DESC)
    parts = classedit.code_parts(k, method)
    code = bytearray(parts['code'])
    if mode == 'no-discard':
        pos, _ = find_invoke(k, code, patch.ENTITY, 'discard', '()V')
        assert code[pos - 1] == 0x2d
        code[pos - 1:pos + 3] = b'\0' * 4
    elif mode == 'no-leash-release':
        pos, _ = find_invoke(k, code, patch.CORPSE, 'dropLeash', '(ZZ)V')
        assert code[pos - 3:pos] == b'\x2d\x04\x04'
        code[pos - 3:pos + 3] = b'\0' * 6
    elif mode in ('remove-partial', 'remove-empty'):
        calls = []
        for pc, ins in decode.walk(code):
            if ins[0] == 0xb6 and constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == \
                    ('net/minecraft/nbt/ListTag', 'isEmpty', '()Z'):
                calls.append(pc)
        assert len(calls) == 2, calls
        pos, expected = (calls[-1] + 3, 0x99) if mode == 'remove-partial' else (calls[0] + 3, 0x9a)
        assert code[pos] == expected
        code[pos:pos + 3] = b'\x57\0\0'  # pop boolean, nop, nop: bypass the guard
    else:
        raise ValueError(mode)
    parts['code'] = bytes(code)
    changed = k.dump(method, classedit.build_code_body(k, parts))
    gate237.alter_zip(path, dest, replace={patch.HELPER + '.class': changed})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True, type=pathlib.Path)
    ap.add_argument('--base237', required=True, type=pathlib.Path)
    ap.add_argument('--base238', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    args = ap.parse_args()
    inputs, base237, base238, jar, java = (x.resolve() for x in
                                           (args.inputs, args.base237, args.base238, args.jar, args.java))
    assert hashlib.sha256(base238.read_bytes()).hexdigest() == patch.BASE_SHA, 'incorrect base 238'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == EXPECTED_SHA and jar.stat().st_size == EXPECTED_SIZE, ('incorrect candidate', digest)
    print(f'build239 size={jar.stat().st_size} sha256={digest}')

    gate237.run('complete build 238 gate including build 237 and HANDOFF §8',
                [PY, ROOT / 'tests/gate_build238.py', '--inputs', inputs, '--base237', base237,
                 '--java', java, '--jar', base238], contains=('BUILD 238 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build239-') as td:
        tmp = pathlib.Path(td)
        rebuilt = tmp / 'rebuilt.jar'
        gate237.run('239 deterministic bytecode patch', [PY, TOOLS / 'patch_corpse_cleanup239.py',
                    '--base', base238, '--out', rebuilt], contains=('sha256 ' + EXPECTED_SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes(), 'candidate differs from reproducible build'
        gate237.run('239 class structure, frames and opcodes', [PY, TOOLS / 'validate_jar.py', jar, '--base', base238],
                    contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, TOOLS / 'verify_jar.py']
        gate237.run('239 CRC, TOML, languages, mixins and exact diff', verify + [jar, '--base', base238] +
                    gate237.option_entries('--expect-changed', sorted(patch.EXPECTED_DIFF)),
                    contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(base238, jar)

        bad_crc = tmp / 'bad-crc.jar'; bad_crc.write_bytes(gate237.corrupt_crc(jar.read_bytes()))
        gate237.run('239 CRC negative', verify + [bad_crc], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        lang = 'assets/settledlands/lang/ru_ru.json'
        missing = tmp / 'missing-lang.jar'; gate237.alter_zip(jar, missing, remove={lang})
        gate237.run('239 missing language negative', verify + [missing], success=False, contains=('ПРОБЛЕМЫ: языки',))
        invalid = tmp / 'invalid-lang.jar'; gate237.alter_zip(jar, invalid, replace={lang: b'{broken'})
        gate237.run('239 invalid language negative', verify + [invalid], success=False, contains=('ПРОБЛЕМЫ: языки',))
        with zipfile.ZipFile(jar) as z:
            ru = json.loads(z.read(lang))
        del ru['message.settledlands.corpse.items_returned']
        mismatch = tmp / 'mismatched-lang.jar'; gate237.alter_zip(jar, mismatch, replace={lang: json.dumps(ru).encode()})
        gate237.run('239 mismatched language negative', verify + [mismatch], success=False, contains=('ПРОБЛЕМЫ: языки',))
        missing_mixin = tmp / 'missing-mixin.jar'; gate237.alter_zip(jar, missing_mixin, remove={'settledlands.mixins.json'})
        gate237.run('239 missing mixin negative', verify + [missing_mixin], success=False, contains=('ПРОБЛЕМЫ: mixins',))
        gate237.run('239 wrong allowlist negative', verify + [jar, '--base', base238, '--expect-changed', 'fake.class'],
                    success=False, contains=('НЕОЖИДАННЫЙ ДИФФ',))

        jvm = [PY, TOOLS / 'jvm_verify.py']
        guide = gate237.GUIDE
        gate237.run('239 JVM missing target negative', jvm + [jar, guide,
                    'dev/settledlands/npc/GuideNpc#noSuchMethod', '--java', java],
                    success=False, contains=('target method with parameters not in class',))
        gate237.run('239 JVM unavailable negative', jvm + [jar, guide, '--java', '/bin/false'],
                    success=False, contains=('ИТОГ: НЕ ПРОВЕРЕНО',))
        for target in gate238.TARGETS:
            gate237.run('239 real JVM verifier ' + target.split('#')[1].split('(')[0],
                        jvm + [jar, target, '--java', java], contains=('DECLARED ', 'ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
            gate237.run('239 renderer regression ' + fixture, [PY, ROOT / 'tests' / fixture,
                        '--java', java, '--base236', inputs / gate237.NAMES[236], '--jar', jar],
                        contains=('236 negative control', '237 JVM execution'))
        recovery = [PY, ROOT / 'tests/jvm_recovery238.py', '--java', java, '--base237', base237, '--jar']
        gate237.run('239 full recovery and health JVM regression', recovery + [jar],
                    contains=('237 negative control', '238 JVM execution', 'not a NeoForge/in-game test'))
        cleanup = [PY, ROOT / 'tests/jvm_cleanup239.py', '--java', java, '--base238', base238, '--jar']
        gate237.run('239 cleanup JVM behavior vs 238 negative control', cleanup + [jar],
                    contains=('238 negative control', '239 JVM execution', 'not a NeoForge/in-game test'))
        for mode, expected in [('no-discard', 'body.removed'), ('remove-partial', 'partial.removed'),
                               ('remove-empty', 'empty.removed'), ('no-leash-release', 'coffin.lead_drops')]:
            bad = tmp / (mode + '.jar'); mutate(jar, bad, mode)
            gate237.run('239 JVM negative: ' + mode, cleanup + [bad], success=False,
                        contains=('AssertionError: mismatched=', expected))
        for name, owner, method, desc, branch, expected in [
            ('wrong-owner', 'java/util/UUID', 'equals', '(Ljava/lang/Object;)Z', 0x99, 'foreign.removed'),
            ('wrong-queue', gate238.patch.PURGATORY_DATA, 'isDeadInQueue', '(Ljava/util/UUID;)Z', 0x9a, 'queued.removed')]:
            bad = tmp / (name + '.jar'); gate238.mutant(jar, bad, owner, method, desc, branch)
            gate237.run('239 JVM negative: ' + name, cleanup + [bad], success=False,
                        contains=('AssertionError: mismatched=', expected))
    print('BUILD 239 GATE: PASS (real JVM with synthetic dependencies; NOT an in-game test)')


if __name__ == '__main__':
    main()
