#!/usr/bin/env python3
"""Fail-closed 241 release gate; true Java 21, not a Minecraft test.

Runs the full 240/239/238/237 §8 gate, deterministic rebuild, strict diff,
verifier and executable bytecode fixtures. Calibration mutants must fail.
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
import gate_build240 as g240
ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
import classedit
import decode
import patch_altar_corpse241 as patch
from patch_corpse_pose237 import find_invoke, constant_ref

PY = sys.executable
EXPECTED_SHA = 'ded3b5b10db1edfeeea6f90ee753d420d1f7f17da92cf136d8db3012346f5b27'
EXPECTED_SIZE = 1548387
TARGETS = [
    patch.LIFECYCLE + '#onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent)',
    patch.POSS + '#onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent$Clone)',
    patch.POSS + '#onPlayerDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent)',
    patch.POSS + '#onCorpseJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent)',
    patch.POSS + '#onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent$Post)',
    patch.POSS + '#guaranteeCorpses',
    patch.POSS + '#repossess(net.minecraft.server.level.ServerPlayer,java.lang.String,java.lang.String)',
    patch.CORPSE + '#reviveOwner(net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerLevel,dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    patch.MIXIN + '#settledlands$reviveOnlyFromQueue(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerLevel,dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    patch.PM + '#tryReviveAtAltar(net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos)',
    patch.HELPER + '#teleportAtAltar(net.minecraft.server.level.ServerLevel,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    patch.HELPER + '#findForDrops(net.minecraft.server.level.ServerLevel,net.minecraft.world.phys.Vec3,java.util.UUID)',
    patch.HELPER + '#copyDeathId(net.minecraft.nbt.CompoundTag,net.minecraft.nbt.CompoundTag)',
    patch.HELPER + '#spawnAndRemember(net.minecraft.server.level.ServerLevel,net.minecraft.world.entity.Entity,net.minecraft.server.level.ServerPlayer)',
    patch.HELPER + '#movePendingSafely(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideCorpseNpc)',
    patch.HELPER + '#guardLegacyRestore(net.minecraft.server.level.ServerPlayer)',
    patch.HELPER + '#finish(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.server.level.ServerPlayer)',
]


def check_possession(before, after):
    a, b = classedit.Klass(before), classedit.Klass(after)
    old = {g238.member_key(m): m for m in a.methods}; new = {g238.member_key(m): m for m in b.methods}
    assert set(old) == set(new)
    assert {(x['name'], x['desc'], x['acc'], tuple(g238.attributes(x))) for x in a.fields} == \
           {(x['name'], x['desc'], x['acc'], tuple(g238.attributes(x))) for x in b.fields}
    changes = {'onServerTick', 'onPlayerClone', 'onPlayerDrops', 'onCorpseJoin', 'guaranteeCorpses', 'repossess'}
    for key, m in old.items():
        actual = new[key]
        if key[0] == 'restoreBodyItems':
            assert (m['acc'], actual['acc']) == (0x000a, 0x0009)
            assert g238.attributes(m) == g238.attributes(actual), 'legacy bytecode changed'
        else:
            assert m['acc'] == actual['acc'], key
            if key[0] not in changes:
                assert g238.attributes(m) == g238.attributes(actual), key
            else:
                assert [(n, v) for n, v in g238.attributes(m) if n != 'Code'] == \
                       [(n, v) for n, v in g238.attributes(actual) if n != 'Code'], key
    assert [(x['name'], x['body']) for x in a.attrs] == [(x['name'], x['body']) for x in b.attrs]


def exact_altar_body(before, after, name, desc):
    a, b = classedit.Klass(before), classedit.Klass(after)
    old = classedit.code_parts(a, a.find_method(name, desc)); new = classedit.code_parts(b, b.find_method(name, desc))
    p1, n1 = find_invoke(a, old['code'], patch.PM, 'teleportPlayerOntoAltar', patch.ALTAR_DESC)
    p2, n2 = find_invoke(b, new['code'], patch.HELPER, 'teleportAtAltar', patch.ALTAR_DESC)
    assert p1 == p2 and n1 == n2 == 3
    code = bytearray(new['code']); code[p2:p2 + n2] = old['code'][p1:p1 + n1]
    assert bytes(code) == old['code'], name
    for key in ('max_stack', 'max_locals', 'exc', 'nested'):
        assert old[key] == new[key], (name, key)


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as j:
        names = set(b.namelist()); assert set(j.namelist()) - names == patch.ADDED
        assert not names - set(j.namelist())
        assert {n for n in names if b.read(n) != j.read(n)} == patch.CHANGED
        transforms = {patch.PM: patch.patch_purgatory, patch.CORPSE: patch.patch_corpse,
                      patch.MIXIN: patch.patch_mixin, patch.POSS: patch.patch_possession,
                      patch.LIFECYCLE: patch.patch_lifecycle}
        for name, transform in transforms.items():
            assert j.read(name + '.class') == transform(b.read(name + '.class')), name
        assert j.read(patch.HELPER + '.class') == patch.build_helper()
        pm_before, pm_after = b.read(patch.PM + '.class'), j.read(patch.PM + '.class')
        descs = [('tryReviveAtAltar', '(L' + patch.SL + ';L' + patch.BLOCK + ';)Z'),
                 ('teleportToTarget', '(L' + patch.SERVER + ';L' + patch.SP + ';L' + patch.PD + '$TargetPos;)V')]
        g238.unchanged_methods(pm_before, pm_after, set(descs))
        for name, desc in descs: exact_altar_body(pm_before, pm_after, name, desc)
        corpse_desc = '(L' + patch.SERVER + ';L' + patch.SL + ';L' + patch.PD + ';L' + patch.SP + ';L' + patch.BLOCK + ';)V'
        g238.unchanged_methods(b.read(patch.CORPSE + '.class'), j.read(patch.CORPSE + '.class'), {('reviveOwner', corpse_desc)})
        mix_desc = '(L' + patch.CORPSE + ';L' + patch.SERVER + ';L' + patch.SL + ';L' + patch.PD + ';L' + patch.SP + ';L' + patch.BLOCK + ';)V'
        g238.unchanged_methods(b.read(patch.MIXIN + '.class'), j.read(patch.MIXIN + '.class'), {
            ('settledlands$reviveOnlyFromQueue', mix_desc), ('settledlands$keepBodyCorpse', '(L' + patch.CORPSE + ';)V')})
        g238.unchanged_methods(b.read(patch.LIFECYCLE + '.class'), j.read(patch.LIFECYCLE + '.class'), {
            ('onPlayerDeath', '(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V')})
        check_possession(b.read(patch.POSS + '.class'), j.read(patch.POSS + '.class'))
        for name in (patch.PD, patch.RECOVERY, 'dev/settledlands/revival/RevivalNpcHelper',
                     'dev/settledlands/mixin/AltarNpcReviveMixin', 'dev/settledlands/npc/IvyDeathScene',
                     'dev/settledlands/npc/PossessionSystem'):
            assert b.read(name + '.class') == j.read(name + '.class'), name
        for name in ('settledlands.mixins.json', 'assets/settledlands/lang/ru_ru.json', 'assets/settledlands/lang/en_us.json'):
            assert b.read(name) == j.read(name), name
        mod = tomllib.loads(j.read('META-INF/neoforge.mods.toml').decode())['mods'][0]
        assert mod['version'] == patch.NEW_VERSION and mod['description'].startswith(patch.CHANGELOG)
        assert j.read('settledlands-patch-info.txt').decode().startswith(patch.PATCHINFO)
    print('OK: altar instructions identical except teleport target; queue/price, ordinary drops, NPC revival, invulnerability and recovery preserved')


def mutate(jar, base, dest, mode):
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(base) as old:
        if mode == 'old-altar':
            g237.alter_zip(jar, dest, replace={patch.PM + '.class': old.read(patch.PM + '.class')}); return
        if mode == 'old-spawn':
            g237.alter_zip(jar, dest, replace={patch.LIFECYCLE + '.class': old.read(patch.LIFECYCLE + '.class')}); return
        name = patch.POSS if mode == 'old-capture' else patch.PM if mode == 'price-eight' else patch.HELPER
        k = classedit.Klass(z.read(name + '.class'))
        if mode == 'owner-bypass':
            raw = z.read(patch.HELPER + '.class')
            for method_name, desc in [('findRecorded', patch.SELECT_DESC), ('teleportBody', patch.BODY_DESC)]:
                cls = classedit.Klass(raw); method = cls.find_method(method_name, desc)
                parts = classedit.code_parts(cls, method); code = bytearray(parts['code'])
                matches = [pc for pc, ins in decode.walk(code) if ins[0] == 0xb6 and
                    constant_ref(cls, struct.unpack('>H', ins[1:3])[0]) ==
                    ('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')]
                assert matches; branch = matches[0] + 3; assert code[branch] == 0x99
                code[branch:branch + 3] = b'\x57\x00\x00'; parts['code'] = bytes(code)
                raw = cls.dump(method, classedit.build_code_body(cls, parts))
            g237.alter_zip(jar, dest, replace={patch.HELPER + '.class': raw}); return
        if mode == 'old-capture':
            desc = '(Lnet/neoforged/neoforge/event/entity/living/LivingDropsEvent;)V'
            method = k.find_method('onPlayerDrops', desc)
            source = classedit.Klass(old.read(name + '.class')); original = source.find_method('onPlayerDrops', desc)
            body = next(a['body'] for a in original['attrs'] if a['name'] == 'Code')
            changed = k.dump(method, body)
        else:
            if mode == 'price-eight':
                method = k.find_method('tryReviveAtAltar', '(L' + patch.SL + ';L' + patch.BLOCK + ';)Z')
                parts = classedit.code_parts(k, method); code = bytearray(parts['code'])
                positions = [pc for pc, raw in decode.walk(code) if raw == b'\x10\x09']
                assert len(positions) == 3, positions
                for pc in positions[:2]: code[pc + 1] = 8
            elif mode == 'guard-bypass':
                method = k.find_method('guardLegacyRestore', '(L' + patch.SP + ';)V')
                parts = classedit.code_parts(k, method); code = bytearray(parts['code'])
                pc, n = find_invoke(k, code, patch.NBT, 'getBoolean', '(Ljava/lang/String;)Z'); assert n == 3
                code[pc:pc + 3] = b'\x58\x03\x00'  # discard tag/key, constant false
            elif mode in ('partial-discard', 'no-lead'):
                method = k.find_method('finish', patch.FINISH_DESC); parts = classedit.code_parts(k, method); code = bytearray(parts['code'])
                if mode == 'partial-discard':
                    pc, n = find_invoke(k, code, patch.LIST, 'isEmpty', '()Z'); assert n == 3
                    code[pc:pc + 3] = b'\x57\x04\x00'  # always empty
                else:
                    pc, n = find_invoke(k, code, patch.CORPSE, 'dropLeash', '(ZZ)V'); assert n == 3
                    code[pc:pc + 3] = b'\x58\x57\x00'
            elif mode in ('wrong-direct-id', 'old-items-id'):
                method_name = 'teleportBody' if mode == 'wrong-direct-id' else 'movePendingSafely'
                desc = patch.BODY_DESC if mode == 'wrong-direct-id' else '(L' + patch.SP + ';L' + patch.CORPSE + ';)V'
                method = k.find_method(method_name, desc); parts = classedit.code_parts(k, method); code = bytearray(parts['code'])
                matches = []
                for pc, raw in decode.walk(code):
                    if raw[0] == 0xb6 and constant_ref(k, struct.unpack('>H', raw[1:3])[0]) == (
                            'java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'):
                        matches.append(pc)
                assert len(matches) == (2 if mode == 'wrong-direct-id' else 3), matches
                branch = matches[1] + 3; assert code[branch] == 0x99
                code[branch:branch + 3] = b'\x57\x00\x00'  # ignore mismatch without new frames
            else:
                raise ValueError(mode)
            parts['code'] = bytes(code); changed = k.dump(method, classedit.build_code_body(k, parts))
        g237.alter_zip(jar, dest, replace={name + '.class': changed})


def main():
    ap = argparse.ArgumentParser()
    for flag in ('inputs', 'base237', 'base238', 'base239', 'base240', 'jar', 'java'):
        ap.add_argument('--' + flag, type=pathlib.Path, required=True)
    a = ap.parse_args(); inputs, b237, b238, b239, b240, jar, java = (getattr(a, k).resolve() for k in
        ('inputs', 'base237', 'base238', 'base239', 'base240', 'jar', 'java'))
    assert hashlib.sha256(b240.read_bytes()).hexdigest() == patch.BASE_SHA, 'incorrect base 240'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == EXPECTED_SHA and jar.stat().st_size == EXPECTED_SIZE, ('incorrect candidate', digest)
    print(f'build241 size={jar.stat().st_size} sha256={digest}')
    g237.run('complete build 240 gate incl. all HANDOFF §8 negatives', [PY, ROOT / 'tests/gate_build240.py',
        '--inputs', inputs, '--base237', b237, '--base238', b238, '--base239', b239, '--java', java, '--jar', b240],
        contains=('BUILD 240 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build241-') as td:
        tmp = pathlib.Path(td); rebuilt = tmp / 'rebuilt.jar'
        g237.run('241 deterministic patch', [PY, TOOLS / 'patch_altar_corpse241.py', '--base', b240, '--out', rebuilt],
                 contains=('sha256 ' + EXPECTED_SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes()
        g237.run('241 structure, frames and opcodes', [PY, TOOLS / 'validate_jar.py', jar, '--base', b240],
                 contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, TOOLS / 'verify_jar.py']
        g237.run('241 CRC/TOML/lang/mixins and strict diff', verify + [jar, '--base', b240] +
                 g237.option_entries('--expect-added', sorted(patch.ADDED)) +
                 g237.option_entries('--expect-changed', sorted(patch.CHANGED)), contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(b240, jar)
        bad_crc = tmp / 'bad-crc.jar'; bad_crc.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('241 CRC negative', verify + [bad_crc], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        lang = 'assets/settledlands/lang/ru_ru.json'
        missing = tmp / 'missing-lang.jar'; g237.alter_zip(jar, missing, remove={lang})
        g237.run('241 missing language negative', verify + [missing], success=False, contains=('ПРОБЛЕМЫ: языки',))
        invalid = tmp / 'invalid-lang.jar'; g237.alter_zip(jar, invalid, replace={lang: b'{broken'})
        g237.run('241 invalid language negative', verify + [invalid], success=False, contains=('ПРОБЛЕМЫ: языки',))
        with zipfile.ZipFile(jar) as z: russian = json.loads(z.read(lang))
        del russian[next(iter(russian))]
        mismatch = tmp / 'mismatched-lang.jar'; g237.alter_zip(jar, mismatch, replace={lang: json.dumps(russian).encode()})
        g237.run('241 mismatched language negative', verify + [mismatch], success=False, contains=('ПРОБЛЕМЫ: языки',))
        mixin = tmp / 'missing-mixin.jar'; g237.alter_zip(jar, mixin, remove={'settledlands.mixins.json'})
        g237.run('241 missing mixin negative', verify + [mixin], success=False, contains=('ПРОБЛЕМЫ: mixins',))
        g237.run('241 allowlist negative', verify + [jar, '--base', b240, '--expect-changed', 'fake.class'],
                 success=False, contains=('НЕОЖИДАННЫЙ ДИФФ',))
        for target in TARGETS:
            g237.run('241 Java verifier ' + target.split('#')[1].split('(')[0],
                     [PY, TOOLS / 'jvm_verify.py', jar, target, '--java', java], contains=('DECLARED ', 'ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
            g237.run('241 renderer regression ' + fixture, [PY, ROOT / 'tests' / fixture, '--java', java,
                '--base236', inputs / g237.NAMES[236], '--jar', jar], contains=('236 negative control', '237 JVM execution'))
        print('NOTE: old isolated spawn/recovery fixtures use pass-through adapters for the 241 hook; actual 241 hook is tested next.')
        g237.run('241 strict item recovery/health component regression', [PY, ROOT / 'tests/jvm_recovery238.py',
            '--java', java, '--base237', b237, '--jar', jar], contains=('237 negative control', '238 JVM execution'))
        g237.run('241 item cleanup/lead component regression', [PY, ROOT / 'tests/jvm_cleanup239.py',
            '--java', java, '--base238', b238, '--jar', jar], contains=('238 negative control', '239 JVM execution'))
        g237.run('241 NPC local-cult regression', [PY, ROOT / 'tests/jvm_npc_cult240.py', '--java', java,
            '--base239', b239, '--jar', jar], contains=('239 negative control', '240 JVM execution'))
        run_fixture = [PY, ROOT / 'tests/jvm_altar241.py', '--java', java, '--base240', b240, '--jar']
        g237.run('241 real death/clone/capture/altar/corpse/claim JVM behaviors', run_fixture + [jar],
                 contains=('240 negative control', '241 JVM execution', 'not a NeoForge/in-game test'))
        for mode, signal in [('old-altar', 'general.x'), ('old-spawn', 'birth.exact_id'),
                             ('old-capture', 'capture.old_count'), ('price-eight', 'general_insufficient.queued'),
                             ('owner-bypass', 'foreign_spoof.x'), ('wrong-direct-id', 'old_direct.removed'), ('old-items-id', 'old_pending.items'),
                             ('guard-bypass', 'conflict.after_tick_player_key'), ('partial-discard', 'partial.removed'),
                             ('no-lead', 'empty.lead_drops')]:
            mutant = tmp / (mode + '.jar'); mutate(jar, b240, mutant, mode)
            g237.run('241 negative JVM mutant ' + mode, run_fixture + [mutant], success=False,
                     contains=('AssertionError: mismatched=', signal))
    print('BUILD 241 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge not run)')


if __name__ == '__main__': main()
