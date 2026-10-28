#!/usr/bin/env python3
"""Build 243: bodies only for possessed deaths; preserve IDs in purgatory.

The LOWEST stock spawner and the HIGHEST delayed-spawn watcher formerly ran on
all deaths, including a bodiless player's repeated deaths in purgatory. The
former cleared and replaced the player's current-death corpse UUID each time.
Keep old bodies/items and the nine-emerald FIFO ritual untouched; never infer an
already-overwritten UUID. No Java compiler is used.
"""
import argparse
import hashlib
import pathlib
import struct
import sys
import tomllib
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classedit
import decode
import patch_altar_corpse241 as p
import patch_altar_corpse242 as q
from patch_earlytex_mixin import u2, u4, attr
from patch_probe233 import Asm
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke

BASE_SHA = '375e8faed7ea19e7bc1c343a043f833f82112a10b0ff17c1f6982bc18c2afe6d'
OLD_VERSION = q.NEW_VERSION
NEW_VERSION = '0.8.0-build243-death-scope'
GUARD = 'dev/settledlands/npc/CorpseDeathGuard'
POSS_SYS = 'dev/settledlands/npc/PossessionSystem'
LIFECYCLE, POSS = p.LIFECYCLE, p.POSS
E, SP, SL, L, PM, KEY = p.E, p.SP, p.SL, p.L, p.PM, p.KEY
DEATH = 'net/neoforged/neoforge/event/entity/living/LivingDeathEvent'
DEATH_DESC = '(L' + DEATH + ';)V'
SELECT_DESC = '(L' + SP + ';)L' + L + ';'
ELIGIBLE_DESC = '(L' + SP + ';)Z'
CHANGED = {LIFECYCLE + '.class', POSS + '.class', 'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
ADDED = {GUARD + '.class'}
CHANGELOG = ('Build 243: spawn a player body only after a possessed death outside purgatory; '
             'repeated deaths in purgatory preserve the original death-body association.')
PATCHINFO = ('сборка 243: тело создаётся только при смерти во вселённом теле вне чистилища; '
             'повторные смерти в чистилище не меняют привязку к исходному телу.')


def build_guard():
    cp = p.Pool(); this_i, super_i, code_name = cp.cls(GUARD), cp.cls('java/lang/Object'), cp.utf8('Code')
    m, fld = cp.method, cp.field
    specs = []
    def add(name, desc, stack, locals_, a, flags=0x0009):
        specs.append((flags, name, desc, stack, locals_, a.finish() if isinstance(a, Asm) else a))
    def is_server_outside(a, fail):
        # The exact same world used by the stock spawner, no dimension loads.
        a.raw(0x2a); a.jump(0xc6, fail)
        a.raw(0x2a); a.op(0xb6, m(SP, 'level', '()L' + L + ';')); a.raw(0x4c)
        a.raw(0x2b); a.op(0xc1, cp.cls(SL)); a.jump(0x99, fail)
        a.op(0xb2, fld(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';'))
        a.raw(0x2b); a.op(0xc0, cp.cls(SL)); a.op(0xb6, m(SL, 'dimension', '()L' + KEY + ';'))
        a.op(0xb6, m('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, fail)

    # Used by the HIGHEST handler: never schedule a fallback watch for an
    # ordinary player or for any further death while in purgatory.
    a = Asm(); is_server_outside(a, 'no'); a.raw(0x2a)
    a.op(0xb8, m(POSS_SYS, 'isPossessed', '(L' + E + ';)Z')); a.raw(0xac)
    a.label('no'); a.raw(0x03, 0xac)
    add('eligible', ELIGIBLE_DESC, 2, 2, a)

    # Used by the LOWEST stock spawner. A genuinely new death outside
    # purgatory invalidates stale death-ID metadata even when it is ordinary,
    # but an ordinary player gets no corpse. Death inside purgatory changes
    # nothing: its original death ID and old body remain the ritual target.
    a = Asm(); is_server_outside(a, 'no'); a.raw(0x2a)
    a.op(0xb8, m(p.HELPER, 'beginDeath', '(L' + SP + ';)V'))
    a.raw(0x2a); a.op(0xb8, m(POSS_SYS, 'isPossessed', '(L' + E + ';)Z'))
    a.jump(0x99, 'no'); a.raw(0x2b, 0xb0)
    a.label('no'); a.raw(0x01, 0xb0)
    add('selectLevel', SELECT_DESC, 2, 2, a)

    # Preserve the pending-body marker while its living owner is in purgatory;
    # queue membership no longer exists as a proxy for that state.
    a = Asm(); a.raw(0x2a); a.jump(0xc6, 'no')
    a.op(0xb2, fld(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')); a.raw(0x2a)
    a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';'))
    a.op(0xb6, m(SL, 'dimension', '()L' + KEY + ';'))
    a.op(0xb6, m('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z')); a.raw(0xac)
    a.label('no'); a.raw(0x03, 0xac)
    add('inPurgatory', ELIGIBLE_DESC, 2, 1, a)

    # The old login check depended on saved FIFO membership. The marker that
    # the death handler already writes is now authoritative for login.
    a = Asm(); a.raw(0x2a); a.jump(0xc6, 'no'); a.raw(0x2a)
    a.op(0xc0, cp.cls(p.EXT)); p.invokeinterface(a, cp, p.EXT, 'getPersistentData', '()L' + p.NBT + ';', 1)
    p.ldc(a, cp, 'settledlands_awaiting_purgatory')
    a.op(0xb6, m(p.NBT, 'getBoolean', '(Ljava/lang/String;)Z')); a.raw(0xac)
    a.label('no'); a.raw(0x03, 0xac)
    add('awaiting', ELIGIBLE_DESC, 2, 1, a)

    add('<init>', '()V', 1, 1, b'\x2a\xb7' + u2(m('java/lang/Object', '<init>', '()V')) + b'\xb1', 0x0002)
    bodies = []
    for flags, name, desc, stack, locals_, code in specs:
        info = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(0) + u2(0)
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) + attr(code_name, info))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() + u2(0x0031) + u2(this_i) + u2(super_i) +
            u2(0) + u2(0) + u2(len(bodies)) + b''.join(bodies) + u2(0))


def patch_lifecycle(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    method = k.find_method('onPlayerDeath', DEATH_DESC); assert method
    parts = classedit.code_parts(k, method); code = bytearray(parts['code'])
    begin, n = find_invoke(k, code, p.HELPER, 'beginDeath', '(L' + SP + ';)V')
    level, n2 = find_invoke(k, code, SP, 'level', '()L' + L + ';')
    assert n == n2 == 3 and begin >= 2 and code[begin-2:begin] == b'\x2b\x59' and level == begin + 3
    start, end = begin - 2, level + n2
    assert not set(range(start + 1, end)) & set(decode.targets(code))
    ref = k.add_methodref(GUARD, 'selectLevel', SELECT_DESC)
    replacement = b'\x2b\xb8' + u2(ref)
    assert end - start == 8 and len(replacement) == 4
    code[start:end] = replacement + b'\x00' * 4
    parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_watch(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    method = k.find_method('onPlayerDeath', DEATH_DESC); assert method
    parts = classedit.code_parts(k, method); code = bytearray(parts['code'])
    pos, n = find_invoke(k, code, POSS_SYS, 'isPossessed', '(L' + E + ';)Z')
    assert n == 3 and code[pos-1] == 0x2b and code[pos+3:pos+6] == b'\x3e\x1d\x99'
    ref = k.add_methodref(GUARD, 'eligible', ELIGIBLE_DESC)
    code[pos+1:pos+3] = u2(ref)
    branch = pos + 5
    # The original frame at pc 20 has only the event argument and an empty
    # stack. Reuse that existing `return` as the early-denial branch.
    assert code[20] == 0xb1 and struct.unpack('>h', code[branch+1:branch+3])[0] + branch == 45
    code[branch+1:branch+3] = struct.pack('>h', 20 - branch)
    parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', type=pathlib.Path, required=True)
    ap.add_argument('--out', type=pathlib.Path, required=True)
    args = ap.parse_args()
    digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != BASE_SHA: raise SystemExit('unexpected base SHA-256: ' + digest)
    if args.base.resolve() == args.out.resolve(): raise SystemExit('refusing to overwrite base')
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}; updated = dict(entries)
        assert not (ADDED & set(entries))
        updated[LIFECYCLE + '.class'] = patch_lifecycle(entries[LIFECYCLE + '.class'])
        updated[POSS + '.class'] = patch_watch(entries[POSS + '.class'])
        updated[GUARD + '.class'] = build_guard()
        text = entries['META-INF/neoforge.mods.toml'].decode(); needle = 'version="' + OLD_VERSION + '"'
        assert text.count(needle) == 1 and text.count("description='''\n") == 1
        text = text.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        text = text.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
        updated['META-INF/neoforge.mods.toml'] = text.encode()
        updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode()).encode()
        changed = {n for n in entries if entries[n] != updated[n]}
        assert changed == CHANGED and set(updated) - set(entries) == ADDED, changed
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.out, 'w') as out:
            for i in base.infolist(): out.writestr(i, updated[i.filename])
            for name in sorted(ADDED):
                info = zipfile.ZipInfo(name, (2026, 10, 28, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                out.writestr(info, updated[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(args.out) as z: assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes(); print('changed:', sorted(changed), 'added:', sorted(ADDED))
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__': main()
