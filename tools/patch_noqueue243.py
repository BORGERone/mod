#!/usr/bin/env python3
"""Build 243 (no compiler): disable FIFO and choose any owner's current body.

A loaded body, its exact death UUID, its owner, the owner's purgatory dimension
and nine emeralds remain mandatory. Old saved FIFO data are not used or erased.
The original altar emerald query/visual and the strict 242 recovery are retained.
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
import patch_death_scope243 as d
import patch_altar_corpse241 as p
import patch_altar_corpse242 as q
import patch_cultprobe243 as cult
from patch_earlytex_mixin import u2, u4, attr
from patch_probe233 import Asm
from patch_corpse_pose237 import assert_pool_roundtrip, constant_ref, find_invoke

NEW_VERSION = '0.8.0-build243-body-without-queue'
NQ = 'dev/settledlands/npc/NoQueueRitual'
CHANGED = {p.LIFECYCLE + '.class', p.POSS + '.class', p.PD + '.class', p.PM + '.class',
           q.GATE + '.class', cult.BRIDGE + '.class', 'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
ADDED = {d.GUARD + '.class', NQ + '.class', cult.PROBE + '.class'}
CHANGELOG = ('Build 243: no player revival queue; each ritual chooses any online owner in purgatory '
             'whose current body is near the altar, for nine emeralds. Ordinary/purgatory deaths create no body. '
             'A read-only, rate-limited local-cult diagnostic records actual interaction conditions.')
PATCHINFO = ('сборка 243: очередь отключена; за девять изумрудов возрождается любой доступный владелец '
             'своего текущего тела возле алтаря. Обычная смерть и смерти в чистилище не создают нового тела.')


def build_noqueue():
    cp = p.Pool(); this_i, super_i, code_name = cp.cls(NQ), cp.cls('java/lang/Object'), cp.utf8('Code')
    m, fld = cp.method, cp.field; specs = []
    def load(a, i): p.xlocal(a, 'aload', i)
    def store(a, i): p.xlocal(a, 'astore', i)
    def iload(a, i): p.xlocal(a, 'iload', i)
    def istore(a, i): p.xlocal(a, 'istore', i)
    def text(a, s): p.ldc(a, cp, s)
    def data(a, i):
        load(a, i); a.op(0xc0, cp.cls(p.EXT))
        p.invokeinterface(a, cp, p.EXT, 'getPersistentData', '()L' + q.NBT + ';', 1)
    def new(a, owner): a.op(0xbb, cp.cls(owner)); a.raw(0x59); a.op(0xb7, m(owner, '<init>', '()V'))
    def li(a, name, desc, n): p.invokeinterface(a, cp, q.LIST, name, desc, n)
    def add(name, desc, stack, locals_, a, flags=0x0009, exceptions=()):
        code = a.finish()
        exc = tuple((a.labels[s], a.labels[e], a.labels[h], cp.cls(t)) for s, e, h, t in exceptions)
        specs.append((flags, name, desc, stack, locals_, code, exc))

    # Cheap top-level tick gate: no scan of registered altars when no living
    # online player is in purgatory. The actual body/price check is elsewhere.
    any_desc = '(L' + q.SERVER + ';)Z'
    a = Asm(); load(a, 0); a.jump(0xc6, 'none')
    load(a, 0); a.op(0xb6, m(q.SERVER, 'getPlayerList', '()L' + q.PLAYERLIST + ';'))
    a.op(0xb6, m(q.PLAYERLIST, 'getPlayers', '()Ljava/util/List;')); store(a, 1)
    a.raw(0x03); istore(a, 2)
    a.label('loop'); iload(a, 2); load(a, 1); li(a, 'size', '()I', 1); a.jump(0xa2, 'none')
    load(a, 1); iload(a, 2); li(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(q.SP)); store(a, 3)
    load(a, 3); a.jump(0xc6, 'next'); load(a, 3); a.op(0xb6, m(q.SP, 'isAlive', '()Z')); a.jump(0x99, 'next')
    load(a, 3); a.op(0xb8, m(d.GUARD, 'inPurgatory', d.ELIGIBLE_DESC)); a.jump(0x99, 'next')
    a.raw(0x04, 0xac); a.label('next'); a.raw(0x84, 2, 1); a.jump(0xa7, 'loop')
    a.label('none'); a.raw(0x03, 0xac); add('hasPlayers', any_desc, 2, 4, a)

    # Same signature as 242's tryFirst so the original altar code can call it.
    # Iteration order is not a queue and offline/invalid players are skipped.
    a = Asm()
    for i in (0, 1, 2, 3): load(a, i); a.jump(0xc6, 'none')
    load(a, 1); a.op(0xb6, m(q.SL, 'getServer', '()L' + q.SERVER + ';')); store(a, 4)
    load(a, 4); a.jump(0xc6, 'none'); load(a, 4)
    a.op(0xb6, m(q.SERVER, 'getPlayerList', '()L' + q.PLAYERLIST + ';'))
    a.op(0xb6, m(q.PLAYERLIST, 'getPlayers', '()Ljava/util/List;')); store(a, 5)
    a.raw(0x03); istore(a, 6)
    a.label('loop'); iload(a, 6); load(a, 5); li(a, 'size', '()I', 1); a.jump(0xa2, 'none')
    load(a, 5); iload(a, 6); li(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(q.SP)); store(a, 7)
    load(a, 7); a.jump(0xc6, 'next'); load(a, 7); a.op(0xb6, m(q.SP, 'isAlive', '()Z')); a.jump(0x99, 'next')
    load(a, 7); a.op(0xb8, m(d.GUARD, 'inPurgatory', d.ELIGIBLE_DESC)); a.jump(0x99, 'next')
    load(a, 1); load(a, 7); load(a, 2); a.op(0xb8, m(q.GATE, 'findBody', q.FIND_DESC)); store(a, 8)
    load(a, 8); a.jump(0xc6, 'next')
    load(a, 1); load(a, 2); load(a, 3); load(a, 7); load(a, 8)
    a.op(0xb8, m(NQ, 'transact', '(L' + q.SL + ';L' + q.BLOCK + ';Ljava/util/List;L' + q.SP + ';L' + q.CORPSE + ';)Z'))
    a.jump(0x99, 'next'); load(a, 7); a.op(0xb6, m(q.SP, 'getUUID', '()Ljava/util/UUID;')); a.raw(0xb0)
    a.label('next'); a.raw(0x84, 6, 1); a.jump(0xa7, 'loop')
    a.label('none'); a.raw(0x01, 0xb0); add('tryFirst', q.FIRST_DESC, 5, 9, a)

    # Reuse the proven 242 full-stack snapshots and rollback. No saved queue is
    # read or mutated; one successful teleport consumes exactly nine emeralds.
    tx_desc = '(L' + q.SL + ';L' + q.BLOCK + ';Ljava/util/List;L' + q.SP + ';L' + q.CORPSE + ';)Z'
    a = Asm(); new(a, q.ARRAYLIST); store(a, 5); new(a, q.ARRAYLIST); store(a, 6)
    a.raw(0x10, 9); istore(a, 7); a.raw(0x03); istore(a, 8)
    a.label('reserve'); iload(a, 8); load(a, 2); li(a, 'size', '()I', 1); a.jump(0xa2, 'reserved')
    iload(a, 7); a.jump(0x9e, 'reserved')
    load(a, 2); iload(a, 8); li(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(q.ITEM_ENTITY)); store(a, 9)
    load(a, 9); a.jump(0xc6, 'next'); load(a, 9); a.op(0xb6, m(q.ITEM_ENTITY, 'isAlive', '()Z')); a.jump(0x99, 'next')
    load(a, 9); a.op(0xb6, m(q.ITEM_ENTITY, 'getItem', '()L' + q.STACK + ';')); store(a, 10)
    load(a, 10); a.op(0xb6, m(q.STACK, 'isEmpty', '()Z')); a.jump(0x9a, 'next')
    load(a, 10); a.op(0xb2, fld(q.ITEMS, 'EMERALD', 'L' + q.ITEM + ';'))
    a.op(0xb6, m(q.STACK, 'is', '(L' + q.ITEM + ';)Z')); a.jump(0x99, 'next')
    load(a, 10); a.op(0xb6, m(q.STACK, 'getCount', '()I')); iload(a, 7)
    a.op(0xb8, m('java/lang/Math', 'min', '(II)I')); istore(a, 11)
    load(a, 5); load(a, 9); a.op(0xb6, m(q.ARRAYLIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
    load(a, 6); load(a, 10); a.op(0xb6, m(q.STACK, 'copy', '()L' + q.STACK + ';'))
    a.op(0xb6, m(q.ARRAYLIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
    load(a, 10); iload(a, 11); a.op(0xb6, m(q.STACK, 'shrink', '(I)V'))
    iload(a, 7); iload(a, 11); a.raw(0x64); istore(a, 7)
    a.label('next'); a.raw(0x84, 8, 1); a.jump(0xa7, 'reserve')
    a.label('reserved'); iload(a, 7); a.jump(0x9d, 'rollback')
    a.label('teleport_start')
    load(a, 3); a.raw(0x0b); a.op(0xb5, fld(q.SP, 'fallDistance', 'F'))
    load(a, 3); a.op(0xb6, m(q.SP, 'clearFire', '()V')); load(a, 3)
    a.op(0xb2, fld(q.VEC, 'ZERO', 'L' + q.VEC + ';')); a.op(0xb6, m(q.SP, 'setDeltaMovement', '(L' + q.VEC + ';)V'))
    load(a, 3); load(a, 0)
    for axis in 'XYZ': load(a, 4); a.op(0xb6, m(q.CORPSE, 'get' + axis, '()D'))
    a.op(0xb8, m('java/util/Collections', 'emptySet', '()Ljava/util/Set;'))
    load(a, 3); a.op(0xb6, m(q.SP, 'getYRot', '()F')); a.raw(0x0b)
    a.op(0xb6, m(q.SP, 'teleportTo', p.TELEPORT_DESC)); a.label('teleport_end'); a.jump(0x99, 'rollback')
    load(a, 5); a.op(0xb8, m(q.GATE, 'finishStacks', '(Ljava/util/List;)V')); a.raw(0x04, 0xac)
    a.label('rollback'); load(a, 5); load(a, 6)
    a.op(0xb8, m(q.GATE, 'restoreStacks', '(Ljava/util/List;Ljava/util/List;)V')); a.raw(0x03, 0xac)
    a.label('caught'); store(a, 12); load(a, 3); load(a, 0); load(a, 4)
    a.op(0xb8, m(q.GATE, 'alreadyAtBody', '(L' + q.SP + ';L' + q.SL + ';L' + q.CORPSE + ';)Z'))
    a.jump(0x9a, 'arrived')
    load(a, 5); load(a, 6); a.op(0xb8, m(q.GATE, 'restoreStacks', '(Ljava/util/List;Ljava/util/List;)V'))
    load(a, 12); a.raw(0xbf)
    a.label('arrived'); load(a, 5); a.op(0xb8, m(q.GATE, 'finishStacks', '(Ljava/util/List;)V'))
    data(a, 3); text(a, 'settledlands_awaiting_purgatory'); a.raw(0x03)
    a.op(0xb6, m(q.NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    load(a, 0); load(a, 3); load(a, 1); a.op(0xb8, m(q.GATE, 'complete', p.ALTAR_DESC))
    load(a, 12); a.raw(0xbf)
    add('transact', tx_desc, 11, 13, a, 0x000a, (('teleport_start', 'teleport_end', 'caught', 'java/lang/Throwable'),))

    # A coffin's own tick may trigger the player-only altar path. Validate that
    # this exact body is its online owner's current body before triggering it.
    a = Asm()
    for i in range(6): load(a, i); a.jump(0xc6, 'done')
    load(a, 4); a.op(0xb6, m(q.SP, 'isAlive', '()Z')); a.jump(0x99, 'done')
    load(a, 4); a.op(0xb8, m(d.GUARD, 'inPurgatory', d.ELIGIBLE_DESC)); a.jump(0x99, 'done')
    load(a, 2); load(a, 4); load(a, 5); a.op(0xb8, m(q.GATE, 'findBody', q.FIND_DESC))
    load(a, 0); a.jump(0xa6, 'done')
    load(a, 2); load(a, 5); a.op(0xb8, m(q.PM, q.PLAYER_ONLY, q.TRY_DESC)); a.raw(0x57)
    a.label('done'); a.raw(0xb1); add('tryDirect', q.MIX_DESC, 3, 6, a)

    specs.insert(0, (0x0002, '<init>', '()V', 1, 1,
                     b'\x2a\xb7' + u2(m('java/lang/Object', '<init>', '()V')) + b'\xb1', ()))
    bodies = []
    for flags, name, desc, stack, locals_, code, exceptions in specs:
        exc = b''.join(u2(s) + u2(e) + u2(h) + u2(t) for s, e, h, t in exceptions)
        info = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(len(exceptions)) + exc + u2(0)
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) + attr(code_name, info))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() + u2(0x0031) + u2(this_i) + u2(super_i) +
            u2(0) + u2(0) + u2(len(bodies)) + b''.join(bodies) + u2(0))


def replace_body(raw, name, desc, code, stack, locals_):
    k = classedit.Klass(raw); method = k.find_method(name, desc); assert method
    parts = {'max_stack': stack, 'max_locals': locals_, 'code': code, 'exc': [], 'nested': []}
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_data(raw):
    assert_pool_roundtrip(classedit.Klass(raw), raw)
    for name, desc, code, stack, locals_ in [
        ('enqueueDead', '(Ljava/util/UUID;)V', b'\xb1', 0, 2),
        ('isDeadInQueue', '(Ljava/util/UUID;)Z', b'\x03\xac', 1, 2),
        ('removeFromQueue', '(Ljava/util/UUID;)Z', b'\x03\xac', 1, 2),
        ('hasDeadPlayers', '()Z', b'\x03\xac', 1, 1),
        ('pollFirstDead', '()Ljava/util/UUID;', b'\x01\xb0', 1, 1),
        (q.PEEK, '()Ljava/util/UUID;', b'\x01\xb0', 1, 1),
        (q.RESTORE_QUEUE, '(Ljava/util/UUID;)V', b'\xb1', 0, 2),
    ]: raw = replace_body(raw, name, desc, code, stack, locals_)
    return raw


def patch_gate(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    # Expose unchanged helpers to the new same-package class. They still
    # enforce current death/owner/range and component-preserving rollback.
    for met in k.methods:
        if met['name'] in {'findBody', 'alreadyAtBody', 'restoreStacks', 'finishStacks'}:
            assert met['acc'] == 0x000a; met['acc'] = 0x0009
    raw = q.rebuild(k, raw)
    for name, desc, insns, stack, locals_ in [
        ('tryFirst', q.FIRST_DESC, b'\x2a\x2b\x2c\x2d', 4, 4),
        ('tryDirect', q.MIX_DESC, b'\x2a\x2b\x2c\x2d\x19\x04\x19\x05', 6, 6),
    ]:
        k = classedit.Klass(raw); ref = k.add_methodref(NQ, name, desc); method = k.find_method(name, desc)
        code = insns + b'\xb8' + u2(ref) + (b'\xb0' if name == 'tryFirst' else b'\xb1')
        parts = {'max_stack': stack, 'max_locals': locals_, 'code': code, 'exc': [], 'nested': []}
        raw = k.dump(method, classedit.build_code_body(k, parts))
    return raw


def refs(k, code, owner, name, desc):
    return [pc for pc, ins in decode.walk(code) if ins[0] in (0xb6, 0xb8) and
            constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (owner, name, desc)]


def patch_manager(raw):
    # The two pre-existing player altar implementations keep all their real
    # emerald checks and visuals but no longer gate on saved FIFO membership.
    for method_name in ('tryReviveAtAltar', q.PLAYER_ONLY):
        k = classedit.Klass(raw); method = k.find_method(method_name, q.TRY_DESC); parts = classedit.code_parts(k, method)
        hits = refs(k, parts['code'], q.PD, 'hasDeadPlayers', '()Z'); assert len(hits) == 1
        pc = hits[0]; code = bytearray(parts['code']); assert code[pc] == 0xb6
        code[pc:pc+3] = b'\x57\x04\x00'  # pop stale data; true; nop
        parts['code'] = bytes(code); raw = k.dump(method, classedit.build_code_body(k, parts))

    # Scan altars only while at least one online, alive player is in purgatory.
    # A temporary local reference to the server replaces the old FIFO query.
    for name, argument, count in [('onServerTick', b'\x2b', 2), ('tickLevelAltars', b'\x19\x09', 1)]:
        k = classedit.Klass(raw); method = k.find_method(name); parts = classedit.code_parts(k, method)
        hits = refs(k, parts['code'], q.PD, 'hasDeadPlayers', '()Z'); assert len(hits) == count, (name, hits)
        ref = k.add_methodref(NQ, 'hasPlayers', '(L' + q.SERVER + ';)Z')
        for pc in reversed(hits):
            assert pc not in decode.targets(parts['code'])
            parts = classedit.insert(parts, pc, b'\x57' + argument)  # pop PD; load server, padded to four
            code = bytearray(parts['code']); assert code[pc+4] == 0xb6
            code[pc+4:pc+7] = b'\xb8' + u2(ref); parts['code'] = bytes(code)
        raw = k.dump(method, classedit.build_code_body(k, parts))

    # On login replace `data.isDeadInQueue(uuid)` with the already-written
    # player's awaiting-purgatory marker, rather than consulting the old FIFO.
    k = classedit.Klass(raw); method = k.find_method('onPlayerLogin'); parts = classedit.code_parts(k, method)
    hits = refs(k, parts['code'], q.PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z'); assert len(hits) == 1
    pc = hits[0]; assert pc not in decode.targets(parts['code'])
    parts = classedit.insert(parts, pc, b'\x58\x2b')  # pop data, UUID; aload player
    code = bytearray(parts['code']); assert code[pc+4] == 0xb6
    ref = k.add_methodref(d.GUARD, 'awaiting', d.ELIGIBLE_DESC)
    code[pc+4:pc+7] = b'\xb8' + u2(ref); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_possession(raw):
    raw = d.patch_watch(raw)
    # A death in purgatory creates no new body: never attach that death's
    # drops to the previous death's body even if possession is still set.
    k = classedit.Klass(raw); method = k.find_method('onPlayerDrops'); parts = classedit.code_parts(k, method)
    hits = refs(k, parts['code'], d.POSS_SYS, 'isPossessed', '(L' + d.E + ';)Z'); assert len(hits) == 1
    pc = hits[0]; ref = k.add_methodref(d.GUARD, 'eligible', d.ELIGIBLE_DESC)
    code = bytearray(parts['code']); code[pc+1:pc+3] = u2(ref); parts['code'] = bytes(code)
    raw = k.dump(method, classedit.build_code_body(k, parts))
    # Without FIFO, the tick must defer model restoration while the player is
    # physically in purgatory. Otherwise it would erase the pending marker.
    k = classedit.Klass(raw); method = k.find_method('onServerTick'); parts = classedit.code_parts(k, method)
    hits = refs(k, parts['code'], q.PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z'); assert len(hits) == 1
    pc = hits[0]; assert pc not in decode.targets(parts['code'])
    parts = classedit.insert(parts, pc, b'\x58\x19\x04')  # pop data and UUID; aload player
    code = bytearray(parts['code']); assert code[pc+4] == 0xb6
    ref = k.add_methodref(d.GUARD, 'inPurgatory', d.ELIGIBLE_DESC)
    code[pc+4:pc+7] = b'\xb8' + u2(ref); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--base', type=pathlib.Path, required=True); ap.add_argument('--out', type=pathlib.Path, required=True)
    args = ap.parse_args(); digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != d.BASE_SHA: raise SystemExit('unexpected base SHA-256: ' + digest)
    if args.base.resolve() == args.out.resolve(): raise SystemExit('refusing to overwrite base')
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}; updated = dict(entries)
        assert not (ADDED & set(entries))
        updated[p.LIFECYCLE + '.class'] = d.patch_lifecycle(entries[p.LIFECYCLE + '.class'])
        updated[p.POSS + '.class'] = patch_possession(entries[p.POSS + '.class'])
        updated[p.PD + '.class'] = patch_data(entries[p.PD + '.class'])
        updated[p.PM + '.class'] = patch_manager(entries[p.PM + '.class'])
        updated[q.GATE + '.class'] = patch_gate(entries[q.GATE + '.class'])
        updated[cult.BRIDGE + '.class'] = cult.patch_bridge(entries[cult.BRIDGE + '.class'])
        updated[cult.PROBE + '.class'] = cult.build_probe()
        updated[d.GUARD + '.class'] = d.build_guard(); updated[NQ + '.class'] = build_noqueue()
        text = entries['META-INF/neoforge.mods.toml'].decode(); needle = 'version="' + q.NEW_VERSION + '"'
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
