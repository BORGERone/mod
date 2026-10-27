#!/usr/bin/env python3
"""Full fail-closed build 238 gate, including build 237's entire §8 gate.

The JVM checks run real mod bytecode with explicitly synthetic dependencies;
this is not a NeoForge or in-game run. Requires Python 3.11 and Java 21.
"""
import argparse
import hashlib
import json
import pathlib
import sys
import tempfile
import tomllib
import zipfile

import gate_build237 as old
ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
import classedit
import patch_corpse_reclaim238 as patch
from patch_corpse_pose237 import find_invoke

PY = sys.executable
EXPECTED_SHA = '632a64b9a94c31165f351152e260ece774c62870570a4ac6101c06ca634a84c2'
EXPECTED_SIZE = 1544550
TARGETS = old.TARGETS + [
    'dev/settledlands/npc/IvyDeathScene#onInteractCorpse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract)',
    'dev/settledlands/npc/IvyDeathScene#onInteractSpecificCorpse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
    'dev/settledlands/npc/CorpseRecovery#syncHealth(dev.settledlands.npc.GuideCorpseNpc)',
    'dev/settledlands/npc/CorpseRecovery#insertSafely(net.minecraft.world.entity.player.Inventory,net.minecraft.world.item.ItemStack,net.minecraft.nbt.CompoundTag)',
    'dev/settledlands/npc/CorpseRecovery#tryClaim(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.Entity)',
    'dev/settledlands/npc/GuideCorpseNpc#settledlands$getOwnerUuid',
]


def member_key(m):
    return (m['name'], m['desc'])


def attributes(member):
    return [(a['name'], a['body']) for a in member['attrs']]


def unchanged_methods(before, after, changed, added=()):
    """Everything except the named hooks is byte-identical, incl. annotations."""
    a = classedit.Klass(before); b = classedit.Klass(after)
    old_methods = {member_key(m): m for m in a.methods}
    new_methods = {member_key(m): m for m in b.methods}
    assert len(old_methods) == len(a.methods) and len(new_methods) == len(b.methods)
    assert set(new_methods) - set(old_methods) == set(added)
    assert not set(old_methods) - set(new_methods)
    assert {(x['name'], x['desc'], x['acc'], tuple(attributes(x))) for x in a.fields} == \
           {(x['name'], x['desc'], x['acc'], tuple(attributes(x))) for x in b.fields}
    for key, m in old_methods.items():
        actual = new_methods[key]
        assert m['acc'] == actual['acc'], key
        if key not in changed:
            assert attributes(m) == attributes(actual), key
        else:
            # Event subscriber annotations and all non-Code method metadata stay intact.
            assert [(n, v) for n, v in attributes(m) if n != 'Code'] == \
                   [(n, v) for n, v in attributes(actual) if n != 'Code'], key
    assert [(x['name'], x['body']) for x in a.attrs] == [(x['name'], x['body']) for x in b.attrs]


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as oldz, zipfile.ZipFile(jar) as newz:
        old_names, new_names = set(oldz.namelist()), set(newz.namelist())
        assert not old_names - new_names and new_names - old_names == {patch.HELPER + '.class'}
        changed = {n for n in old_names if oldz.read(n) != newz.read(n)}
        assert changed == patch.EXPECTED_CHANGED, changed
        unchanged_methods(oldz.read(patch.CORPSE + '.class'), newz.read(patch.CORPSE + '.class'), {
            ('<init>', '(Lnet/minecraft/world/entity/EntityType;L' + patch.LEVEL + ';)V'),
            ('setCoffin', '(Z)V'),
        }, {(patch.OWNER_GETTER, '()Ljava/util/UUID;')})
        unchanged_methods(oldz.read(patch.IVY + '.class'), newz.read(patch.IVY + '.class'), {
            (name, '(L' + event + ';)V') for name, event in patch.EVENTS.items()})
        for lang in ('en_us', 'ru_ru'):
            path = f'assets/settledlands/lang/{lang}.json'
            before, after = json.loads(oldz.read(path)), json.loads(newz.read(path))
            assert set(after) - set(before) == {patch.DONE_KEY, patch.PARTIAL_KEY}
            assert {k: v for k, v in after.items() if k in before} == before
        meta = tomllib.loads(newz.read('META-INF/neoforge.mods.toml').decode())
        assert meta['mods'][0]['version'] == patch.NEW_VERSION
        assert meta['mods'][0]['description'].startswith('Build 238:')
        # All unchanged members (including ordinary death drops, altar, renderer,
        # coffin/lead mobInteract and invulnerability) are independently checked.
        assert newz.read('dev/settledlands/npc/PlayerCorpsePossession.class') == oldz.read('dev/settledlands/npc/PlayerCorpsePossession.class')
        assert newz.read('dev/settledlands/revival/PurgatoryManager.class') == oldz.read('dev/settledlands/revival/PurgatoryManager.class')
        assert newz.read('settledlands.mixins.json') == oldz.read('settledlands.mixins.json')
        assert newz.read(patch.HELPER + '.class') == patch.build_recovery_class()
    print('OK exact methods and unchanged ordinary deaths, altar, renderer, invulnerability, lead and event annotations')


def mutant(path, out, owner, name, desc, expected_branch):
    """Construct a valid JVM class that incorrectly skips one authorization check."""
    with zipfile.ZipFile(path) as z:
        raw = z.read(patch.HELPER + '.class')
    k = classedit.Klass(raw)
    method = k.find_method('tryClaim', '(L' + patch.PLAYER + ';L' + patch.ENTITY + ';)V')
    parts = classedit.code_parts(k, method)
    pos, _ = find_invoke(k, parts['code'], owner, name, desc)
    branch = pos + 3
    code = bytearray(parts['code'])
    assert code[branch] == expected_branch, (owner, name, pos, code[branch])
    code[branch:branch + 3] = b'\x57\x00\x00'  # pop boolean; nop; nop (same byte length)
    parts['code'] = bytes(code)
    mutated = k.dump(method, classedit.build_code_body(k, parts))
    old.alter_zip(path, out, replace={patch.HELPER + '.class': mutated})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True, type=pathlib.Path)
    ap.add_argument('--base237', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    args = ap.parse_args()
    inputs, base, jar, java = (x.resolve() for x in (args.inputs, args.base237, args.jar, args.java))
    assert hashlib.sha256(base.read_bytes()).hexdigest() == patch.BASE_SHA, 'incorrect base 237'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == EXPECTED_SHA and jar.stat().st_size == EXPECTED_SIZE, ('incorrect candidate', digest)
    print(f'build238 size={jar.stat().st_size} sha256={digest}')

    # Re-run every historical gate, including real JVM negative calibrations.
    old.run('complete build 237 gate §8', [PY, ROOT / 'tests/gate_build237.py', '--inputs', inputs,
            '--java', java, '--jar', base], contains=('BUILD 237 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build238-') as td:
        tmp = pathlib.Path(td)
        rebuilt = tmp / 'rebuilt.jar'
        old.run('build 238 deterministic bytecode patch', [PY, TOOLS / 'patch_corpse_reclaim238.py',
                '--base', base, '--out', rebuilt], contains=('sha256 ' + EXPECTED_SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes(), 'candidate differs from reproducible build'
        old.run('class structure, frames and opcodes', [PY, TOOLS / 'validate_jar.py', jar, '--base', base],
                contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, TOOLS / 'verify_jar.py']
        old.run('CRC, TOML, languages, mixins and exact allowlist', verify + [jar, '--base', base] +
                old.option_entries('--expect-added', [patch.HELPER + '.class']) +
                old.option_entries('--expect-changed', sorted(patch.EXPECTED_CHANGED)), contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(base, jar)
        bad_crc = tmp / 'bad-crc.jar'; bad_crc.write_bytes(old.corrupt_crc(jar.read_bytes()))
        old.run('238 CRC negative', verify + [bad_crc], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        lang = 'assets/settledlands/lang/ru_ru.json'
        missing = tmp / 'missing-lang.jar'; old.alter_zip(jar, missing, remove={lang})
        old.run('238 missing language negative', verify + [missing], success=False, contains=('ПРОБЛЕМЫ: языки',))
        invalid = tmp / 'invalid-lang.jar'; old.alter_zip(jar, invalid, replace={lang: b'{broken'})
        old.run('238 invalid language negative', verify + [invalid], success=False, contains=('ПРОБЛЕМЫ: языки',))
        with zipfile.ZipFile(jar) as z:
            ru = json.loads(z.read(lang))
        del ru[patch.DONE_KEY]
        mismatch = tmp / 'mismatched-lang.jar'; old.alter_zip(jar, mismatch, replace={lang: json.dumps(ru).encode()})
        old.run('238 mismatched language negative', verify + [mismatch], success=False, contains=('ПРОБЛЕМЫ: языки',))
        missing_mixin = tmp / 'missing-mixin.jar'; old.alter_zip(jar, missing_mixin, remove={'settledlands.mixins.json'})
        old.run('238 missing mixin negative', verify + [missing_mixin], success=False, contains=('ПРОБЛЕМЫ: mixins',))
        old.run('238 wrong allowlist negative', verify + [jar, '--base', base, '--expect-changed', 'fake.class'],
                success=False, contains=('НЕОЖИДАННЫЙ ДИФФ',))
        jvm = [PY, TOOLS / 'jvm_verify.py']
        for target in TARGETS:
            old.run('real JVM verifier ' + target.split('#')[1].split('(')[0], jvm + [jar, target, '--java', java],
                    contains=('DECLARED ', 'ИТОГ: OK'))
        # Retest 237's four executable behaviors against 238, not only its base.
        source236 = inputs / old.NAMES[236]
        for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
            old.run('238 renderer regression ' + fixture, [PY, ROOT / 'tests' / fixture,
                    '--java', java, '--base236', source236, '--jar', jar],
                    contains=('236 negative control', '237 JVM execution'))
        behavior = [PY, ROOT / 'tests/jvm_recovery238.py', '--java', java, '--base237', base, '--jar']
        old.run('actual recovery/health and event bytecodes on JVM', behavior + [jar],
                contains=('237 negative control', '238 JVM execution', 'not a NeoForge/in-game test'))
        wrong_owner = tmp / 'wrong-owner.jar'
        mutant(jar, wrong_owner, 'java/util/UUID', 'equals', '(Ljava/lang/Object;)Z', 0x99)
        old.run('JVM behavior negative: owner check bypass', behavior + [wrong_owner], success=False,
                contains=('AssertionError: mismatched=', 'foreign.received'))
        wrong_queue = tmp / 'wrong-queue.jar'
        mutant(jar, wrong_queue, patch.PURGATORY_DATA, 'isDeadInQueue', '(Ljava/util/UUID;)Z', 0x9a)
        old.run('JVM behavior negative: queue check bypass', behavior + [wrong_queue], success=False,
                contains=('AssertionError: mismatched=', 'blocked.queue'))
    print('BUILD 238 GATE: PASS (real JVM with synthetic dependencies; NOT an in-game test)')


if __name__ == '__main__':
    main()
