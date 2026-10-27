#!/usr/bin/env python3
"""Build 242: player altar revival requires the current body nearby and 9 emeralds.

No javac. The original FIFO and altar's emerald-entity query remain. Before
polling the queue, the new gate verifies the exact loaded body and 16.5 block-
position squared range, reserves precisely nine emeralds, polls, and teleports.
Failed teleport restores both stacks and the queue. Direct coffins use the same
player-only ritual for marked/unmarked bodies, without the NPC-priority mixin.
Pre-242 pending revivals are requeued: their body/payment provenance is unknown.
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
from patch_earlytex_mixin import u2, u4, attr
from patch_probe233 import Asm
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke

BASE_SHA = 'ded3b5b10db1edfeeea6f90ee753d420d1f7f17da92cf136d8db3012346f5b27'
OLD_VERSION = p.NEW_VERSION
NEW_VERSION = '0.8.0-build242-body-required'
E, SP, SL, SERVER, L, PD, PM, NBT, CORPSE = p.E, p.SP, p.SL, p.SERVER, p.L, p.PD, p.PM, p.NBT, p.CORPSE
BLOCK, VEC, KEY = p.BLOCK, p.VEC, p.KEY
GATE = 'dev/settledlands/npc/CorpseRitualGate'
ITEM = 'net/minecraft/world/item/Item'
ITEMS = 'net/minecraft/world/item/Items'
STACK = 'net/minecraft/world/item/ItemStack'
ITEM_ENTITY = 'net/minecraft/world/entity/item/ItemEntity'
PLAYERLIST = 'net/minecraft/server/players/PlayerList'
TARGET = PD + '$TargetPos'
ARRAYLIST = 'java/util/ArrayList'
LIST = 'java/util/List'
UUID = 'java/util/UUID'
PLAYER_ONLY = 'settledlands$tryRevivePlayerAtAltar'
PEEK = 'settledlands$peekFirstDead'
RESTORE_QUEUE = 'settledlands$restoreFirstDead'
MIX_DESC = '(L' + CORPSE + ';L' + SERVER + ';L' + SL + ';L' + PD + ';L' + SP + ';L' + BLOCK + ';)V'
TRY_DESC = '(L' + SL + ';L' + BLOCK + ';)Z'
FIRST_DESC = '(L' + PD + ';L' + SL + ';L' + BLOCK + ';Ljava/util/List;)Ljava/util/UUID;'
FIND_DESC = '(L' + SL + ';L' + SP + ';L' + BLOCK + ';)L' + CORPSE + ';'
PENDING_DESC = '(L' + PD + ';Ljava/util/UUID;L' + SP + ';)L' + TARGET + ';'
CHANGED = {PD + '.class', PM + '.class', p.MIXIN + '.class', CORPSE + '.class',
           'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
ADDED = {GATE + '.class'}
CHANGELOG = ('Build 242: player revival requires the current corpse or coffin near the altar '
             'and exactly nine emeralds; no body, insufficient payment or failed teleport keeps the queue and payment.')
PATCHINFO = ('сборка 242: возрождение игрока только у собственного тела/гроба рядом с маяком '
             'и строго за девять изумрудов; без тела, при нехватке или неудачном телепорте очередь и оплата сохраняются.')


def member(v):
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in v['attrs'])
    return u2(v['acc']) + u2(v['name_idx']) + u2(v['desc_idx']) + u2(len(v['attrs'])) + attrs


def rebuild(k, raw, extra_methods=()):
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in k.attrs)
    return (raw[:8] + u2(len(k.cp)) + k._pool_bytes() + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) +
            u2(len(k.fields)) + b''.join(member(x) for x in k.fields) +
            u2(len(k.methods) + len(extra_methods)) + b''.join(member(x) for x in k.methods) +
            b''.join(extra_methods) + u2(len(k.attrs)) + attrs)


def code_member(k, name, desc, stack, locals_, code):
    code_name = k.add_utf8('Code'); name_idx = k.add_utf8(name); desc_idx = k.add_utf8(desc)
    body = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(0) + u2(0)
    return u2(0x0001) + u2(name_idx) + u2(desc_idx) + u2(1) + attr(code_name, body)


def patch_data(raw):
    """Append two straight-line methods; existing saved data and methods stay intact."""
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    assert not k.find_method(PEEK) and not k.find_method(RESTORE_QUEUE)
    queue = k.add_fieldref(PD, 'deadQueue', 'Ljava/util/List;')
    get = k.add_interfaceref(LIST, 'get', '(I)Ljava/lang/Object;')
    remove = k.add_interfaceref(LIST, 'remove', '(Ljava/lang/Object;)Z')
    insert = k.add_interfaceref(LIST, 'add', '(ILjava/lang/Object;)V')
    dirty = k.add_methodref(PD, 'setDirty', '()V'); uuid_class = k.add_class(UUID)
    peek = (b'\x2a\xb4' + u2(queue) + b'\x03\xb9' + u2(get) + b'\x02\x00\xc0' + u2(uuid_class) + b'\xb0')
    restore = (b'\x2a\xb4' + u2(queue) + b'\x2b\xb9' + u2(remove) + b'\x02\x00\x57' +
               b'\x2a\xb4' + u2(queue) + b'\x03\x2b\xb9' + u2(insert) + b'\x03\x00' +
               b'\x2a\xb6' + u2(dirty) + b'\xb1')
    methods = [code_member(k, PEEK, '()Ljava/util/UUID;', 2, 1, peek),
               code_member(k, RESTORE_QUEUE, '(Ljava/util/UUID;)V', 3, 2, restore)]
    return rebuild(k, raw, methods)


def build_gate():
    cp = p.Pool(); this_i, super_i, code_name = cp.cls(GATE), cp.cls('java/lang/Object'), cp.utf8('Code')
    m, fld = cp.method, cp.field; specs = []
    def load(a, i): p.xlocal(a, 'aload', i)
    def store(a, i): p.xlocal(a, 'astore', i)
    def iload(a, i): p.xlocal(a, 'iload', i)
    def istore(a, i): p.xlocal(a, 'istore', i)
    def data(a, i):
        load(a, i); a.op(0xc0, cp.cls(p.EXT))
        p.invokeinterface(a, cp, p.EXT, 'getPersistentData', '()L' + NBT + ';', 1)
    def text(a, s): p.ldc(a, cp, s)
    def new(a, owner): a.op(0xbb, cp.cls(owner)); a.raw(0x59); a.op(0xb7, m(owner, '<init>', '()V'))
    def listcall(a, name, desc, count): p.invokeinterface(a, cp, LIST, name, desc, count)
    def add(name, desc, stack, locals_, asm, flags=0x0009, exceptions=()):
        code = asm.finish()
        resolved = tuple((asm.labels[start], asm.labels[end], asm.labels[handler], cp.cls(exc))
                         for start, end, handler, exc in exceptions)
        specs.append((flags, name, desc, stack, locals_, code, resolved))

    # Resolve exactly the recorded current-death body, not another body of the
    # same owner; use the existing coffin radius (block distance squared <= 16.5).
    a = Asm()
    for i in (0, 1, 2): load(a, i); a.jump(0xc6, 'no')
    data(a, 1); store(a, 3); load(a, 3); text(a, p.DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x99, 'no')
    load(a, 0); load(a, 3); text(a, p.DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    a.op(0xb6, m(SL, 'getEntity', '(Ljava/util/UUID;)L' + E + ';')); store(a, 4)
    load(a, 4); a.op(0xc1, cp.cls(CORPSE)); a.jump(0x99, 'no')
    load(a, 4); a.op(0xc0, cp.cls(CORPSE)); store(a, 5)
    load(a, 5); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'no')
    load(a, 5); a.op(0xb6, m(E, 'level', '()L' + L + ';')); load(a, 0); a.jump(0xa6, 'no')
    load(a, 5); a.op(0xb6, m(CORPSE, p.old.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 6)
    load(a, 6); a.jump(0xc6, 'no'); load(a, 6); load(a, 1)
    a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;')); a.op(0xb6, m(UUID, 'equals', '(Ljava/lang/Object;)Z'))
    a.jump(0x99, 'no'); load(a, 2); load(a, 5); a.op(0xb6, m(E, 'blockPosition', '()L' + BLOCK + ';'))
    a.op(0xb6, m(BLOCK, 'distSqr', '(Lnet/minecraft/core/Vec3i;)D')); a.op(0x14, cp.double(16.5))
    a.raw(0x98); a.jump(0x9d, 'no')  # dcmpg; ifgt
    load(a, 5); a.raw(0xb0); a.label('no'); a.raw(0x01, 0xb0)
    add('findBody', FIND_DESC, 4, 7, a, 0x000a)

    # A teleport callback can throw *after* the player arrived. Treat that as
    # a paid arrival, rather than refunding nine and allowing a free revival.
    a = Asm(); load(a, 0); a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';'))
    load(a, 1); a.jump(0xa6, 'no')
    for axis in 'XYZ':
        load(a, 0); a.op(0xb6, m(SP, 'get' + axis, '()D'))
        load(a, 2); a.op(0xb6, m(CORPSE, 'get' + axis, '()D'))
        a.raw(0x98); a.jump(0x9a, 'no')
    a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac)
    add('alreadyAtBody', '(L' + SP + ';L' + SL + ';L' + CORPSE + ';)Z', 4, 3, a, 0x000a)

    # Restore every touched ItemEntity from a full ItemStack copy, preserving
    # components and split stacks when teleport or preconditions fail.
    a = Asm(); a.raw(0x03); istore(a, 2)
    a.label('loop'); iload(a, 2); load(a, 0); listcall(a, 'size', '()I', 1); a.jump(0xa2, 'done')
    load(a, 0); iload(a, 2); listcall(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(ITEM_ENTITY))
    load(a, 1); iload(a, 2); listcall(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(STACK))
    a.op(0xb6, m(ITEM_ENTITY, 'setItem', '(L' + STACK + ';)V'))
    a.raw(0x84, 2, 1); a.jump(0xa7, 'loop'); a.label('done'); a.raw(0xb1)
    add('restoreStacks', '(Ljava/util/List;Ljava/util/List;)V', 3, 3, a, 0x000a)

    # Once teleport succeeds, discard only empty emerald item entities and
    # synchronize a partially consumed stack with vanilla's setItem.
    a = Asm(); a.raw(0x03); istore(a, 1)
    a.label('loop'); iload(a, 1); load(a, 0); listcall(a, 'size', '()I', 1); a.jump(0xa2, 'done')
    load(a, 0); iload(a, 1); listcall(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(ITEM_ENTITY)); store(a, 2)
    load(a, 2); a.op(0xb6, m(ITEM_ENTITY, 'getItem', '()L' + STACK + ';')); store(a, 3)
    load(a, 3); a.op(0xb6, m(STACK, 'isEmpty', '()Z')); a.jump(0x99, 'partial')
    load(a, 2); a.op(0xb6, m(ITEM_ENTITY, 'discard', '()V')); a.jump(0xa7, 'next')
    a.label('partial'); load(a, 2); load(a, 3); a.op(0xb6, m(ITEM_ENTITY, 'setItem', '(L' + STACK + ';)V'))
    a.label('next'); a.raw(0x84, 1, 1); a.jump(0xa7, 'loop'); a.label('done'); a.raw(0xb1)
    add('finishStacks', '(Ljava/util/List;)V', 2, 4, a, 0x000a)

    # Called where 241 used pollFirstDead, after the vanilla 9-emerald check.
    # Owner must be online/alive; no absent/unloaded/foreign/distant body can
    # consume money or leave the queue. Save full stack copies before reservation.
    a = Asm()
    for i in (0, 1, 2, 3): load(a, i); a.jump(0xc6, 'no')
    load(a, 0); a.op(0xb6, m(PD, 'hasDeadPlayers', '()Z')); a.jump(0x99, 'no')
    load(a, 0); a.op(0xb6, m(PD, PEEK, '()Ljava/util/UUID;')); store(a, 4)
    load(a, 4); a.jump(0xc6, 'no'); load(a, 1); a.op(0xb6, m(SL, 'getServer', '()L' + SERVER + ';')); store(a, 5)
    load(a, 5); a.jump(0xc6, 'no'); load(a, 5); a.op(0xb6, m(SERVER, 'getPlayerList', '()L' + PLAYERLIST + ';'))
    load(a, 4); a.op(0xb6, m(PLAYERLIST, 'getPlayer', '(Ljava/util/UUID;)L' + SP + ';')); store(a, 6)
    load(a, 6); a.jump(0xc6, 'no'); load(a, 6); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'no')
    load(a, 1); load(a, 6); load(a, 2); a.op(0xb8, m(GATE, 'findBody', FIND_DESC)); store(a, 7)
    load(a, 7); a.jump(0xc6, 'no')
    new(a, ARRAYLIST); store(a, 8); new(a, ARRAYLIST); store(a, 9)
    a.raw(0x10, 9); istore(a, 10); a.raw(0x03); istore(a, 11)
    a.label('reserve'); iload(a, 11); load(a, 3); listcall(a, 'size', '()I', 1); a.jump(0xa2, 'reserved')
    iload(a, 10); a.jump(0x9e, 'reserved')
    load(a, 3); iload(a, 11); listcall(a, 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(ITEM_ENTITY)); store(a, 12)
    load(a, 12); a.jump(0xc6, 'next'); load(a, 12); a.op(0xb6, m(ITEM_ENTITY, 'isAlive', '()Z')); a.jump(0x99, 'next')
    load(a, 12); a.op(0xb6, m(ITEM_ENTITY, 'getItem', '()L' + STACK + ';')); store(a, 13)
    load(a, 13); a.op(0xb6, m(STACK, 'isEmpty', '()Z')); a.jump(0x9a, 'next')
    load(a, 13); a.op(0xb2, fld(ITEMS, 'EMERALD', 'L' + ITEM + ';'))
    a.op(0xb6, m(STACK, 'is', '(L' + ITEM + ';)Z')); a.jump(0x99, 'next')
    load(a, 13); a.op(0xb6, m(STACK, 'getCount', '()I')); iload(a, 10)
    a.op(0xb8, m('java/lang/Math', 'min', '(II)I')); istore(a, 14)
    load(a, 8); load(a, 12); a.op(0xb6, m(ARRAYLIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
    load(a, 9); load(a, 13); a.op(0xb6, m(STACK, 'copy', '()L' + STACK + ';'))
    a.op(0xb6, m(ARRAYLIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
    load(a, 13); iload(a, 14); a.op(0xb6, m(STACK, 'shrink', '(I)V'))
    iload(a, 10); iload(a, 14); a.raw(0x64); istore(a, 10)
    a.label('next'); a.raw(0x84, 11, 1); a.jump(0xa7, 'reserve')
    a.label('reserved'); iload(a, 10); a.jump(0x9d, 'rollback_only')
    load(a, 0); a.op(0xb6, m(PD, 'pollFirstDead', '()Ljava/util/UUID;')); store(a, 15)
    load(a, 15); a.jump(0xc6, 'rollback_only'); load(a, 4); load(a, 15)
    a.op(0xb6, m(UUID, 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'wrong_poll')
    # Queue is removed BEFORE the dimension change: NeoForge's synchronous
    # onDimensionChange handler removes this player from the queue as well.
    # If a mod throws during teleport, roll back the queue and all reserved
    # stacks before rethrowing (not a silent successful ritual).
    a.label('teleport_start')
    load(a, 6); a.raw(0x0b); a.op(0xb5, fld(SP, 'fallDistance', 'F'))
    load(a, 6); a.op(0xb6, m(SP, 'clearFire', '()V')); load(a, 6)
    a.op(0xb2, fld(VEC, 'ZERO', 'L' + VEC + ';')); a.op(0xb6, m(SP, 'setDeltaMovement', '(L' + VEC + ';)V'))
    load(a, 6); load(a, 1)
    for axis in 'XYZ': load(a, 7); a.op(0xb6, m(CORPSE, 'get' + axis, '()D'))
    a.op(0xb8, m('java/util/Collections', 'emptySet', '()Ljava/util/Set;'))
    load(a, 6); a.op(0xb6, m(SP, 'getYRot', '()F')); a.raw(0x0b)
    a.op(0xb6, m(SP, 'teleportTo', p.TELEPORT_DESC)); a.label('teleport_end'); a.jump(0x99, 'rollback_queue')
    load(a, 8); a.op(0xb8, m(GATE, 'finishStacks', '(Ljava/util/List;)V'))
    load(a, 4); a.raw(0xb0)
    a.label('wrong_poll'); load(a, 0); load(a, 15); a.op(0xb6, m(PD, RESTORE_QUEUE, '(Ljava/util/UUID;)V'))
    a.jump(0xa7, 'rollback_only')
    a.label('rollback_queue'); load(a, 0); load(a, 4); a.op(0xb6, m(PD, RESTORE_QUEUE, '(Ljava/util/UUID;)V'))
    a.label('rollback_only'); load(a, 8); load(a, 9)
    a.op(0xb8, m(GATE, 'restoreStacks', '(Ljava/util/List;Ljava/util/List;)V'))
    a.label('no'); a.raw(0x01, 0xb0)
    a.label('teleport_exception'); store(a, 16)
    load(a, 6); load(a, 1); load(a, 7)
    a.op(0xb8, m(GATE, 'alreadyAtBody', '(L' + SP + ';L' + SL + ';L' + CORPSE + ';)Z'))
    a.jump(0x9a, 'arrived_then_threw')
    load(a, 0); load(a, 4); a.op(0xb6, m(PD, RESTORE_QUEUE, '(Ljava/util/UUID;)V'))
    load(a, 8); load(a, 9); a.op(0xb8, m(GATE, 'restoreStacks', '(Ljava/util/List;Ljava/util/List;)V'))
    load(a, 16); a.raw(0xbf)
    a.label('arrived_then_threw'); load(a, 8); a.op(0xb8, m(GATE, 'finishStacks', '(Ljava/util/List;)V'))
    data(a, 6); text(a, 'settledlands_awaiting_purgatory'); a.raw(0x03)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    load(a, 1); load(a, 6); load(a, 2); a.op(0xb8, m(GATE, 'complete', p.ALTAR_DESC))
    load(a, 16); a.raw(0xbf)
    add('tryFirst', FIRST_DESC, 11, 17, a,
        exceptions=(('teleport_start', 'teleport_end', 'teleport_exception', 'java/lang/Throwable'),))

    # After the original ritual's visual/awaiting updates, claim the same body
    # once, without a second teleport or the previous altar fallback.
    a = Asm(); load(a, 0); load(a, 1); load(a, 2); a.op(0xb8, m(GATE, 'findBody', FIND_DESC)); store(a, 3)
    load(a, 3); a.jump(0xc6, 'done'); load(a, 1); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'done')
    load(a, 1); a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';')); load(a, 0); a.jump(0xa6, 'done')
    a.op(0xb2, fld(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')); load(a, 0)
    a.op(0xb6, m(SL, 'dimension', '()L' + KEY + ';')); a.op(0xb6, m('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z'))
    a.jump(0x9a, 'done'); load(a, 0); a.op(0xb6, m(SL, 'getServer', '()L' + SERVER + ';')); store(a, 4)
    load(a, 4); a.jump(0xc6, 'done'); load(a, 4); a.op(0xb8, m(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';'))
    load(a, 1); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z')); a.jump(0x9a, 'done')
    data(a, 3); text(a, p.SUCCESS); a.raw(0x04); a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    data(a, 1); store(a, 5); load(a, 5); text(a, p.PLAYER_PENDING)
    a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z')); a.jump(0x99, 'items')
    load(a, 5); text(a, p.RESTORE); a.raw(0x04); a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    a.label('items'); load(a, 5); text(a, p.PLAYER_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'claim')
    load(a, 5); text(a, p.SKIP_LEGACY); a.raw(0x04); a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    load(a, 1); load(a, 3); a.op(0xb8, m(p.HELPER, 'movePendingSafely', '(L' + SP + ';L' + CORPSE + ';)V'))
    load(a, 5); text(a, p.PLAYER_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z'))
    a.jump(0x9a, 'finish')
    a.label('claim'); load(a, 1); load(a, 3); a.op(0xb8, m(p.RECOVERY, 'tryClaim', '(L' + p.old.PLAYER + ';L' + E + ';)V'))
    a.label('finish'); load(a, 3); load(a, 1); a.op(0xb8, m(p.HELPER, 'finish', p.FINISH_DESC))
    a.label('done'); a.raw(0xb1)
    add('complete', p.ALTAR_DESC, 3, 6, a)

    # Direct coffin redirect calls the same player-only ritual. A legacy body
    # without a current-death UUID cannot prove that it belongs to this death,
    # even when it has the right owner, so the direct path fails closed too.
    a = Asm()
    for i in range(6): load(a, i); a.jump(0xc6, 'done')
    load(a, 4); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'done')
    load(a, 0); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'done')
    load(a, 0); a.op(0xb6, m(E, 'level', '()L' + L + ';')); load(a, 2); a.jump(0xa6, 'done')
    load(a, 0); a.op(0xb6, m(CORPSE, p.old.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 6)
    load(a, 6); a.jump(0xc6, 'done'); load(a, 6); load(a, 4); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m(UUID, 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    load(a, 3); a.op(0xb6, m(PD, 'hasDeadPlayers', '()Z')); a.jump(0x99, 'done')
    load(a, 6); load(a, 3); a.op(0xb6, m(PD, PEEK, '()Ljava/util/UUID;'))
    a.op(0xb6, m(UUID, 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    load(a, 5); load(a, 0); a.op(0xb6, m(E, 'blockPosition', '()L' + BLOCK + ';'))
    a.op(0xb6, m(BLOCK, 'distSqr', '(Lnet/minecraft/core/Vec3i;)D')); a.op(0x14, cp.double(16.5))
    a.raw(0x98); a.jump(0x9d, 'done')
    data(a, 4); store(a, 7); load(a, 7); text(a, p.DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x99, 'done')
    load(a, 7); text(a, p.DEATH_CORPSE); a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    load(a, 0); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;')); a.op(0xb6, m(UUID, 'equals', '(Ljava/lang/Object;)Z'))
    a.jump(0x99, 'done'); load(a, 2); load(a, 5); a.op(0xb8, m(PM, PLAYER_ONLY, TRY_DESC)); a.raw(0x57)
    a.label('done'); a.raw(0xb1)
    add('tryDirect', MIX_DESC, 4, 8, a)

    # Old pending records do not prove both a body and a nine-emerald payment:
    # turn them back into queued players, then use the current ritual.
    a = Asm(); load(a, 0); load(a, 1)
    a.op(0xb6, m(PD, 'consumePendingRevive', '(Ljava/util/UUID;)L' + TARGET + ';')); store(a, 3)
    load(a, 3); a.jump(0xc6, 'none'); load(a, 0); load(a, 1)
    a.op(0xb6, m(PD, 'enqueueDead', '(Ljava/util/UUID;)V'))
    load(a, 2); a.jump(0xc6, 'none'); data(a, 2); text(a, 'settledlands_awaiting_purgatory'); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    load(a, 2); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'none')
    a.op(0xb2, fld(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')); load(a, 2)
    a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';')); a.op(0xb6, m(SL, 'dimension', '()L' + KEY + ';'))
    a.op(0xb6, m('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, 'none')
    load(a, 2); a.op(0xb6, m(SP, 'getServer', '()L' + SERVER + ';')); store(a, 4)
    load(a, 4); a.jump(0xc6, 'none'); load(a, 4); load(a, 2); a.raw(0x03)
    a.op(0xb8, m(PM, 'teleportToPurgatory', '(L' + SERVER + ';L' + SP + ';Z)V'))
    a.label('none'); a.raw(0x01, 0xb0)
    add('requeuePending', PENDING_DESC, 3, 5, a)

    # Defensive fail-closed target path even if a legacy caller bypasses the
    # three patched pending consumers; never fall back to the altar for free.
    a = Asm(); load(a, 1); a.jump(0xc6, 'done'); load(a, 1)
    a.op(0xb6, m(SP, 'getServer', '()L' + SERVER + ';')); store(a, 3); load(a, 3); a.jump(0xc6, 'done')
    load(a, 3); a.op(0xb8, m(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';'))
    load(a, 1); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m(PD, 'enqueueDead', '(Ljava/util/UUID;)V'))
    data(a, 1); text(a, 'settledlands_awaiting_purgatory'); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    load(a, 3); load(a, 1); a.raw(0x03); a.op(0xb8, m(PM, 'teleportToPurgatory', '(L' + SERVER + ';L' + SP + ';Z)V'))
    a.label('done'); a.raw(0xb1)
    add('rejectLegacyTarget', p.ALTAR_DESC, 3, 4, a)

    init = b'\x2a\xb7' + u2(m('java/lang/Object', '<init>', '()V')) + b'\xb1'
    specs.insert(0, (0x0002, '<init>', '()V', 1, 1, init, ()))
    bodies = []
    for flags, name, desc, stack, locals_, code, exceptions in specs:
        exc = b''.join(u2(s) + u2(e) + u2(h) + u2(t) for s, e, h, t in exceptions)
        info = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(len(exceptions)) + exc + u2(0)
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) + attr(code_name, info))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() + u2(0x0031) + u2(this_i) + u2(super_i) +
            u2(0) + u2(0) + u2(len(bodies)) + b''.join(bodies) + u2(0))


def patch_try(raw):
    k = classedit.Klass(raw); method = k.find_method('tryReviveAtAltar', TRY_DESC); assert method
    parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], PD, 'pollFirstDead', '()Ljava/util/UUID;')
    assert n == 3 and parts['code'][pc] == 0xb6 and pc not in decode.targets(parts['code'])
    new = k.add_methodref(GATE, 'tryFirst', FIRST_DESC)
    parts = classedit.insert(parts, pc, b'\x2a\x2b\x19\x05')  # level, position, original valid-emerald list
    code = bytearray(parts['code']); code[pc + 4:pc + 7] = b'\xb8' + u2(new)
    # The new gate already reserved exactly nine. Preserve all verifier frames
    # and loop structure, but skip the original second consumption loop.
    pattern = b'\x10\x09\x36\x09'; start = pc + 7
    where = code.find(pattern, start); assert where >= 0 and code.find(pattern, where + 1) < 0
    code[where:where + 2] = b'\x03\x00'  # iconst_0; nop -> remaining=0
    parts['code'] = bytes(code); raw = k.dump(method, classedit.build_code_body(k, parts))
    k = classedit.Klass(raw); method = k.find_method('tryReviveAtAltar', TRY_DESC); parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], p.HELPER, 'teleportAtAltar', p.ALTAR_DESC)
    assert n == 3 and parts['code'][pc] == 0xb8
    ref = k.add_methodref(GATE, 'complete', p.ALTAR_DESC); code = bytearray(parts['code'])
    code[pc + 1:pc + 3] = u2(ref); parts['code'] = bytes(code)
    raw = k.dump(method, classedit.build_code_body(k, parts))
    # The clone has identical Code/frames but a distinct name, so NPC-first
    # @Inject on tryReviveAtAltar does not intercept a direct coffin trigger.
    k = classedit.Klass(raw); method = k.find_method('tryReviveAtAltar', TRY_DESC)
    assert method['acc'] == 0x0009 and not k.find_method(PLAYER_ONLY)
    clone = dict(method); clone['name_idx'] = k.add_utf8(PLAYER_ONLY)
    return rebuild(k, raw, [member(clone)])


def patch_pending_call(raw, name, desc, player_slot):
    k = classedit.Klass(raw); method = k.find_method(name, desc); assert method
    parts = classedit.code_parts(k, method)
    old_desc = '(Ljava/util/UUID;)L' + TARGET + ';'
    pc, n = find_invoke(k, parts['code'], PD, 'consumePendingRevive', old_desc)
    assert n == 3 and parts['code'][pc] == 0xb6 and pc not in decode.targets(parts['code'])
    ref = k.add_methodref(GATE, 'requeuePending', PENDING_DESC)
    argument = bytes([0x2a + player_slot]) if player_slot < 4 else bytes([0x19, player_slot])
    parts = classedit.insert(parts, pc, argument + b'\x00' * (4 - len(argument)))
    code = bytearray(parts['code']); code[pc + 4:pc + 7] = b'\xb8' + u2(ref); parts['code'] = bytes(code)
    parts['max_stack'] = max(parts['max_stack'], 3)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_purgatory(raw):
    raw = patch_try(raw)
    for name, desc, slot in [
        ('onPlayerRespawn', '(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerRespawnEvent;)V', 1),
        ('onPlayerLogin', '(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent;)V', 1),
        ('onServerTick', '(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V', 7),
    ]: raw = patch_pending_call(raw, name, desc, slot)
    k = classedit.Klass(raw); method = k.find_method('teleportToTarget', '(L' + SERVER + ';L' + SP + ';L' + TARGET + ';)V')
    parts = classedit.code_parts(k, method); pc, n = find_invoke(k, parts['code'], p.HELPER, 'teleportAtAltar', p.ALTAR_DESC)
    assert n == 3 and parts['code'][pc] == 0xb8
    ref = k.add_methodref(GATE, 'rejectLegacyTarget', p.ALTAR_DESC); code = bytearray(parts['code'])
    code[pc + 1:pc + 3] = u2(ref); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_corpse(raw):
    # Defense in depth: even if some caller reaches the private original body
    # method without the redirect, it must use the same paid player gate.
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    desc = '(L' + SERVER + ';L' + SL + ';L' + PD + ';L' + SP + ';L' + BLOCK + ';)V'
    method = k.find_method('reviveOwner', desc); assert method
    ref = k.add_methodref(GATE, 'tryDirect', MIX_DESC)
    code = b'\x2a\x2b\x2c\x2d\x19\x04\x19\x05\xb8' + u2(ref) + b'\xb1'
    parts = {'max_stack': 6, 'max_locals': 6, 'code': code, 'exc': [], 'nested': []}
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_mixin(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    method = k.find_method('settledlands$reviveOnlyFromQueue', MIX_DESC); assert method
    ref = k.add_methodref(GATE, 'tryDirect', MIX_DESC)
    # Redirect annotation and all other attributes are untouched. The single
    # player-only gate replaces marked/unmarked branches and their reflection.
    code = b'\x2b\x2c\x2d\x19\x04\x19\x05\x19\x06\xb8' + u2(ref) + b'\xb1'
    parts = {'max_stack': 6, 'max_locals': 7, 'code': code, 'exc': [], 'nested': []}
    return k.dump(method, classedit.build_code_body(k, parts))


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--base', type=pathlib.Path, required=True); ap.add_argument('--out', type=pathlib.Path, required=True)
    args = ap.parse_args(); digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != BASE_SHA: raise SystemExit('unexpected base SHA-256: ' + digest)
    if args.base.resolve() == args.out.resolve(): raise SystemExit('refusing to overwrite base')
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}; updated = dict(entries)
        assert not (ADDED & set(entries))
        updated[PD + '.class'] = patch_data(entries[PD + '.class'])
        updated[PM + '.class'] = patch_purgatory(entries[PM + '.class'])
        updated[CORPSE + '.class'] = patch_corpse(entries[CORPSE + '.class'])
        updated[p.MIXIN + '.class'] = patch_mixin(entries[p.MIXIN + '.class'])
        updated[GATE + '.class'] = build_gate()
        text = entries['META-INF/neoforge.mods.toml'].decode(); needle = 'version="' + OLD_VERSION + '"'
        assert text.count(needle) == 1 and text.count("description='''\n") == 1
        text = text.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        text = text.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
        updated['META-INF/neoforge.mods.toml'] = text.encode()
        updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode()).encode()
        changed = {n for n in entries if entries[n] != updated[n]}; assert changed == CHANGED and set(updated) - set(entries) == ADDED
        with zipfile.ZipFile(args.out, 'w') as out:
            for i in base.infolist(): out.writestr(i, updated[i.filename])
            info = zipfile.ZipInfo(GATE + '.class', (2026, 10, 28, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, updated[GATE + '.class'], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(args.out) as z: assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes(); print('changed:', sorted(changed), 'added:', sorted(ADDED))
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__': main()
