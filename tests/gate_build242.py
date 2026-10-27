#!/usr/bin/env python3
"""Fail-closed build 242 release gate; real Java, synthetic game APIs."""
import argparse
import hashlib
import pathlib
import struct
import sys
import tempfile
import tomllib
import zipfile

import gate_build237 as g237
import gate_build238 as g238
import gate_build241 as g241
ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
import classedit
import decode
import patch_altar_corpse242 as q
from patch_corpse_pose237 import find_invoke, constant_ref

PY = sys.executable
EXPECTED_SHA = '375e8faed7ea19e7bc1c343a043f833f82112a10b0ff17c1f6982bc18c2afe6d'
EXPECTED_SIZE = 1551583
TARGETS = [
    q.PM + '#tryReviveAtAltar(net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos)',
    q.PM + '#' + q.PLAYER_ONLY + '(net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos)',
    q.PM + '#onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerRespawnEvent)',
    q.PM + '#onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
    q.PM + '#onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent$Post)',
    q.PM + '#teleportToTarget(net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerPlayer,dev.settledlands.revival.PurgatoryData$TargetPos)',
    q.PD + '#' + q.PEEK,
    q.PD + '#' + q.RESTORE_QUEUE + '(java.util.UUID)',
    q.CORPSE + '#reviveOwner(net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerLevel,dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    q.p.MIXIN + '#settledlands$reviveOnlyFromQueue(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerLevel,dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    q.GATE + '#tryFirst(dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos,java.util.List)',
    q.GATE + '#complete(net.minecraft.server.level.ServerLevel,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    q.GATE + '#tryDirect(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerLevel,dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
    q.GATE + '#requeuePending(dev.settledlands.revival.PurgatoryData,java.util.UUID,net.minecraft.server.level.ServerPlayer)',
    q.GATE + '#rejectLegacyTarget(net.minecraft.server.level.ServerLevel,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
]


def compare_methods(before, after, changed=(), added=()):
    a, b = classedit.Klass(before), classedit.Klass(after)
    old = {g238.member_key(m): m for m in a.methods}; new = {g238.member_key(m): m for m in b.methods}
    assert set(new) - set(old) == set(added), (set(new) - set(old), added)
    assert not set(old) - set(new)
    assert {(x['name'], x['desc'], x['acc'], tuple(g238.attributes(x))) for x in a.fields} == \
           {(x['name'], x['desc'], x['acc'], tuple(g238.attributes(x))) for x in b.fields}
    for key, m in old.items():
        n = new[key]; assert m['acc'] == n['acc'], key
        if key in changed:
            assert [(x, v) for x, v in g238.attributes(m) if x != 'Code'] == \
                   [(x, v) for x, v in g238.attributes(n) if x != 'Code'], key
        else: assert g238.attributes(m) == g238.attributes(n), key
    assert [(x['name'], x['body']) for x in a.attrs] == [(x['name'], x['body']) for x in b.attrs]


def refs(k, method):
    code = classedit.code_parts(k, method)['code']; out = []
    for pc, ins in decode.walk(code):
        if ins[0] in (0xb6, 0xb7, 0xb8, 0xb9):
            out.append(constant_ref(k, struct.unpack('>H', ins[1:3])[0]))
    return out


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old = set(b.namelist()); assert set(z.namelist()) - old == q.ADDED and not old - set(z.namelist())
        assert {n for n in old if b.read(n) != z.read(n)} == q.CHANGED
        transforms = {q.PD: q.patch_data, q.PM: q.patch_purgatory, q.CORPSE: q.patch_corpse, q.p.MIXIN: q.patch_mixin}
        for name, fn in transforms.items(): assert z.read(name + '.class') == fn(b.read(name + '.class')), name
        assert z.read(q.GATE + '.class') == q.build_gate()
        pd_added = {(q.PEEK, '()Ljava/util/UUID;'), (q.RESTORE_QUEUE, '(Ljava/util/UUID;)V')}
        compare_methods(b.read(q.PD + '.class'), z.read(q.PD + '.class'), added=pd_added)
        changed_pm = {('tryReviveAtAltar', q.TRY_DESC),
            ('onPlayerRespawn', '(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerRespawnEvent;)V'),
            ('onPlayerLogin', '(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent;)V'),
            ('onServerTick', '(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V'),
            ('teleportToTarget', '(L' + q.SERVER + ';L' + q.SP + ';L' + q.TARGET + ';)V')}
        compare_methods(b.read(q.PM + '.class'), z.read(q.PM + '.class'), changed_pm, {(q.PLAYER_ONLY, q.TRY_DESC)})
        corpse_desc = '(L' + q.SERVER + ';L' + q.SL + ';L' + q.PD + ';L' + q.SP + ';L' + q.BLOCK + ';)V'
        compare_methods(b.read(q.CORPSE + '.class'), z.read(q.CORPSE + '.class'), {('reviveOwner', corpse_desc)})
        compare_methods(b.read(q.p.MIXIN + '.class'), z.read(q.p.MIXIN + '.class'), {('settledlands$reviveOnlyFromQueue', q.MIX_DESC)})
        k = classedit.Klass(z.read(q.PM + '.class')); normal = k.find_method('tryReviveAtAltar', q.TRY_DESC)
        clone = k.find_method(q.PLAYER_ONLY, q.TRY_DESC)
        assert g238.attributes(normal) == g238.attributes(clone), 'player-only clone diverged'
        for method in (normal, clone):
            calls = refs(k, method)
            assert (q.GATE, 'tryFirst', q.FIRST_DESC) in calls
            assert (q.GATE, 'complete', q.p.ALTAR_DESC) in calls
            assert (q.PD, 'pollFirstDead', '()Ljava/util/UUID;') not in calls
        for name, desc in changed_pm - {('tryReviveAtAltar', q.TRY_DESC), ('teleportToTarget', '(L' + q.SERVER + ';L' + q.SP + ';L' + q.TARGET + ';)V')}:
            calls = refs(k, k.find_method(name, desc))
            assert (q.GATE, 'requeuePending', q.PENDING_DESC) in calls
            assert not any(x[1] == 'consumePendingRevive' for x in calls)
        calls = refs(k, k.find_method('teleportToTarget'))
        assert (q.GATE, 'rejectLegacyTarget', q.p.ALTAR_DESC) in calls
        for name, method, desc in [(q.p.MIXIN, 'settledlands$reviveOnlyFromQueue', q.MIX_DESC),
                                   (q.CORPSE, 'reviveOwner', corpse_desc)]:
            c = classedit.Klass(z.read(name + '.class'))
            assert refs(c, c.find_method(method, desc)) == [(q.GATE, 'tryDirect', q.MIX_DESC)]
        for name in (q.p.HELPER, q.p.LIFECYCLE, q.p.POSS, q.p.RECOVERY,
                     'dev/settledlands/revival/RevivalNpcHelper', 'dev/settledlands/revival/AltarNpcReviveEvents',
                     'dev/settledlands/mixin/AltarNpcReviveMixin', 'dev/settledlands/npc/IvyDeathScene',
                     'dev/settledlands/npc/PossessionSystem'):
            assert b.read(name + '.class') == z.read(name + '.class'), name
        for name in ('settledlands.mixins.json', 'assets/settledlands/lang/ru_ru.json', 'assets/settledlands/lang/en_us.json'):
            assert b.read(name) == z.read(name), name
        mod = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods'][0]
        assert mod['version'] == q.NEW_VERSION and mod['description'].startswith(q.CHANGELOG)
        assert z.read('settledlands-patch-info.txt').decode().startswith(q.PATCHINFO)
    print('OK strict player gate, both direct entry points, legacy pending, FIFO, NPC and ordinary-drops allowlist')


def change_method(raw, name, desc, fn):
    k = classedit.Klass(raw); method = k.find_method(name, desc); parts = classedit.code_parts(k, method)
    code = bytearray(parts['code']); fn(k, code); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def replace_call(k, code, owner, name, desc, replacement):
    pc, n = find_invoke(k, code, owner, name, desc); assert len(replacement) == n
    code[pc:pc + n] = replacement


def mutate(jar, base, dest, mode):
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(base) as b:
        if mode == 'old-direct-both':
            g237.alter_zip(jar, dest, replace={q.CORPSE + '.class': b.read(q.CORPSE + '.class'),
                                               q.p.MIXIN + '.class': b.read(q.p.MIXIN + '.class')}); return
        if mode == 'old-bare-corpse':
            g237.alter_zip(jar, dest, replace={q.CORPSE + '.class': b.read(q.CORPSE + '.class')}); return
        if mode == 'old-pending':
            raw = z.read(q.PM + '.class'); original = classedit.Klass(b.read(q.PM + '.class'))
            for name in ('onPlayerLogin', 'teleportToTarget'):
                k = classedit.Klass(raw); method = k.find_method(name); old = original.find_method(name, method['desc'])
                body = next(x['body'] for x in old['attrs'] if x['name'] == 'Code'); raw = k.dump(method, body)
            g237.alter_zip(jar, dest, replace={q.PM + '.class': raw}); return
        if mode in ('price-eight', 'double-charge'):
            raw = z.read(q.PM + '.class')
            for name in ('tryReviveAtAltar', q.PLAYER_ONLY):
                def transform(k, code):
                    if mode == 'price-eight':
                        hits = [pc for pc, ins in decode.walk(code) if ins == b'\x10\x09']
                        assert len(hits) == 2, hits
                        for pc in hits: code[pc + 1] = 8
                    else:
                        pat = b'\x03\x00\x36\x09'; pc = code.find(pat); assert pc >= 0 and code.find(pat, pc + 1) < 0
                        code[pc:pc + 2] = b'\x10\x09'
                raw = change_method(raw, name, q.TRY_DESC, transform)
            replacements = {q.PM + '.class': raw}
            if mode == 'price-eight':
                def eight(k, code):
                    hits = [pc for pc, ins in decode.walk(code) if ins == b'\x10\x09']; assert len(hits) == 1
                    code[hits[0] + 1] = 8
                replacements[q.GATE + '.class'] = change_method(z.read(q.GATE + '.class'), 'tryFirst', q.FIRST_DESC, eight)
            g237.alter_zip(jar, dest, replace=replacements); return
        raw = z.read(q.GATE + '.class')
        if mode == 'range-bypass':
            def no_range(k, code):
                hits = [pc for pc, ins in decode.walk(code) if ins[0] == 0x98]; assert len(hits) == 1
                branch = hits[0] + 1; assert code[branch] == 0x9d; code[branch:branch + 3] = b'\x57\x00\x00'
            raw = change_method(raw, 'findBody', q.FIND_DESC, no_range)
        elif mode == 'owner-bypass':
            def no_owner(k, code):
                hits = [pc for pc, ins in decode.walk(code) if ins[0] == 0xb6 and
                        constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (q.UUID, 'equals', '(Ljava/lang/Object;)Z')]
                assert len(hits) == 1; branch = hits[0] + 3; assert code[branch] == 0x99
                code[branch:branch + 3] = b'\x57\x00\x00'
            raw = change_method(raw, 'findBody', q.FIND_DESC, no_owner)
        elif mode == 'legacy-direct-bind':
            # Mutant restores the unsafe inference: any same-owner old coffin
            # may manufacture a current-death ID before the direct ritual.
            k = classedit.Klass(raw); method = k.find_method('tryDirect', q.MIX_DESC); parts = classedit.code_parts(k, method)
            a = q.Asm(); a.raw(0x19, 4); a.op(0xc0, k.add_class(q.p.EXT))
            a.op(0xb9, k.add_interfaceref(q.p.EXT, 'getPersistentData', '()L' + q.NBT + ';')); a.raw(1, 0)
            a.raw(0x3a, 7, 0x19, 7); a.op(0x13, k.add_string(q.p.DEATH_CORPSE))
            a.op(0xb6, k.add_methodref(q.NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x9a, 'bound')
            a.raw(0x19, 7); a.op(0x13, k.add_string(q.p.DEATH_CORPSE)); a.raw(0x2a)
            a.op(0xb6, k.add_methodref(q.E, 'getUUID', '()Ljava/util/UUID;'))
            a.op(0xb6, k.add_methodref(q.NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
            a.label('bound'); a.raw(0x00)
            parts = classedit.insert(parts, 0, a.finish())
            raw = k.dump(method, classedit.build_code_body(k, parts))
        elif mode == 'no-money-rollback':
            def no_restore(k, code):
                hits = [pc for pc, ins in decode.walk(code) if ins[0] == 0xb8 and
                    constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (q.GATE, 'restoreStacks', '(Ljava/util/List;Ljava/util/List;)V')]
                assert len(hits) == 2
                for pc in hits: code[pc:pc + 3] = b'\x58\x00\x00'
            raw = change_method(raw, 'tryFirst', q.FIRST_DESC, no_restore)
        elif mode == 'no-exception-rollback':
            k = classedit.Klass(raw); method = k.find_method('tryFirst', q.FIRST_DESC); parts = classedit.code_parts(k, method)
            assert len(parts['exc']) == 1; parts['exc'] = []
            raw = k.dump(method, classedit.build_code_body(k, parts))
        elif mode == 'refund-after-arrival':
            k = classedit.Klass(raw); method = k.find_method('alreadyAtBody'); parts = classedit.code_parts(k, method)
            parts['code'] = b'\x03\xac'; parts['exc'] = []; parts['nested'] = []
            raw = k.dump(method, classedit.build_code_body(k, parts))
        elif mode == 'no-queue-rollback':
            def no_queue(k, code):
                hits = [pc for pc, ins in decode.walk(code) if ins[0] == 0xb6 and
                    constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (q.PD, q.RESTORE_QUEUE, '(Ljava/util/UUID;)V')]
                assert len(hits) == 3
                for pc in hits: code[pc:pc + 3] = b'\x58\x00\x00'
            raw = change_method(raw, 'tryFirst', q.FIRST_DESC, no_queue)
        elif mode == 'no-empty-finish':
            def no_finish(k, code): replace_call(k, code, q.p.HELPER, 'finish', q.p.FINISH_DESC, b'\x58\x00\x00')
            raw = change_method(raw, 'complete', q.p.ALTAR_DESC, no_finish)
        else: raise ValueError(mode)
        g237.alter_zip(jar, dest, replace={q.GATE + '.class': raw})


def main():
    ap = argparse.ArgumentParser()
    for flag in ('inputs', 'base237', 'base238', 'base239', 'base240', 'base241', 'jar', 'java'):
        ap.add_argument('--' + flag, type=pathlib.Path, required=True)
    a = ap.parse_args(); inputs, b237, b238, b239, b240, b241, jar, java = (getattr(a, k).resolve() for k in
        ('inputs', 'base237', 'base238', 'base239', 'base240', 'base241', 'jar', 'java'))
    assert hashlib.sha256(b241.read_bytes()).hexdigest() == q.BASE_SHA, 'incorrect base 241'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == EXPECTED_SHA and jar.stat().st_size == EXPECTED_SIZE, ('incorrect candidate', digest)
    print(f'build242 size={jar.stat().st_size} sha256={digest}')
    g237.run('complete build 241 gate incl. HANDOFF §8', [PY, ROOT / 'tests/gate_build241.py', '--inputs', inputs,
        '--base237', b237, '--base238', b238, '--base239', b239, '--base240', b240, '--java', java, '--jar', b241],
        contains=('BUILD 241 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build242-') as td:
        tmp = pathlib.Path(td); rebuilt = tmp / 'rebuilt.jar'
        g237.run('242 deterministic patch', [PY, TOOLS / 'patch_altar_corpse242.py', '--base', b241, '--out', rebuilt],
                 contains=('sha256 ' + EXPECTED_SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes()
        g237.run('242 structure, frames and opcodes', [PY, TOOLS / 'validate_jar.py', jar, '--base', b241],
                 contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, TOOLS / 'verify_jar.py']
        g237.run('242 CRC/TOML/lang/mixins and strict diff', verify + [jar, '--base', b241] +
                 g237.option_entries('--expect-added', sorted(q.ADDED)) + g237.option_entries('--expect-changed', sorted(q.CHANGED)),
                 contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(b241, jar)
        bad = tmp / 'bad-crc.jar'; bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('242 CRC negative', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        bad = tmp / 'missing-lang.jar'; g237.alter_zip(jar, bad, remove={'assets/settledlands/lang/ru_ru.json'})
        g237.run('242 missing language negative', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: языки',))
        bad = tmp / 'invalid-lang.jar'; g237.alter_zip(jar, bad, replace={'assets/settledlands/lang/ru_ru.json': b'{broken'})
        g237.run('242 invalid language negative', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: языки',))
        bad = tmp / 'missing-mixin.jar'; g237.alter_zip(jar, bad, remove={'settledlands.mixins.json'})
        g237.run('242 missing mixin negative', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: mixins',))
        g237.run('242 allowlist negative', verify + [jar, '--base', b241, '--expect-changed', 'fake.class'],
                 success=False, contains=('НЕОЖИДАННЫЙ ДИФФ',))
        for target in TARGETS:
            g237.run('242 Java verifier ' + target.split('#')[1].split('(')[0],
                     [PY, TOOLS / 'jvm_verify.py', jar, target, '--java', java], contains=('DECLARED ', 'ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
            g237.run('242 renderer regression ' + fixture, [PY, ROOT / 'tests' / fixture, '--java', java,
                '--base236', inputs / g237.NAMES[236], '--jar', jar], contains=('236 negative control', '237 JVM execution'))
        g237.run('242 strict recovery/health component regression', [PY, ROOT / 'tests/jvm_recovery238.py',
            '--java', java, '--base237', b237, '--jar', jar], contains=('237 negative control', '238 JVM execution'))
        g237.run('242 cleanup/lead component regression', [PY, ROOT / 'tests/jvm_cleanup239.py',
            '--java', java, '--base238', b238, '--jar', jar], contains=('238 negative control', '239 JVM execution'))
        g237.run('242 NPC local-cult regression', [PY, ROOT / 'tests/jvm_npc_cult240.py', '--java', java,
            '--base239', b239, '--jar', jar], contains=('239 negative control', '240 JVM execution'))
        g237.run('242 real FIFO/restore bytecodes', [PY, ROOT / 'tests/jvm_queue242.py', '--java', java, '--jar', jar],
                 contains=('real PurgatoryData FIFO',))
        fixture = [PY, ROOT / 'tests/jvm_altar242.py', '--java', java, '--base241', b241, '--jar']
        g237.run('242 real ritual/direct/loot/rollback bytecodes', fixture + [jar],
                 contains=('241 negative control', '242 JVM execution', 'not a NeoForge/in-game test'))
        for mode, signal in [('old-direct-both', 'unmarked8.queued'), ('old-bare-corpse', 'bare8.queued'),
                             ('old-pending', 'old_pending_login.in_purgatory'), ('price-eight', 'general_insufficient.queued'),
                             ('range-bypass', 'boundary_outside.queued'), ('owner-bypass', 'foreign_spoof.queued'),
                             ('legacy-direct-bind', 'legacy_direct.queued'),
                             ('no-money-rollback', 'rollback.first_count'), ('no-queue-rollback', 'rollback.queued'),
                             ('no-exception-rollback', 'throw_rollback.queued'), ('refund-after-arrival', 'throw_after.queued'),
                             ('double-charge', 'general.emerald_count'), ('no-empty-finish', 'empty.removed')]:
            mutant = tmp / (mode + '.jar'); mutate(jar, b241, mutant, mode)
            g237.run('242 negative JVM mutant ' + mode, fixture + [mutant], success=False,
                     contains=('AssertionError: mismatched=', signal))
    print('BUILD 242 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge not run)')


if __name__ == '__main__': main()
