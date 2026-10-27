#!/usr/bin/env python3
"""Execute 240/241 altar, corpse, mixin and recovery bytecode on Java 21.

Game, event bus and visual effects are synthetic executable stand-ins; the
altar's 9-emerald/queue instructions, selected direct coffin handler, player
model tick and item-recovery instructions are real. NOT a Minecraft test.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

import jvm_recovery238 as r
import jvm_corpse_load as f
from patch_probe233 import Asm
from patch_earlytex_mixin import u2, u4, attr
import patch_altar_corpse241 as p

E, SP, SL, L, SERVER, C, G = r.E, r.SP, r.SL, r.L, r.SERVER, r.C, r.G
NBT, TAG, LIST, EXT, PD, PM, KEY = r.NBT, r.TAG, r.LIST, r.EXT, r.PD, r.PM, r.KEY
INV, IT, ITEM, ITEMS, ITEM_ENTITY = r.INV, r.IT, r.ITEM, r.ITEMS, r.ITEM_ENTITY
BLOCK, BOX, VEC, HELPER = p.BLOCK, p.BOX, p.VEC, p.HELPER
POSS, MIXIN, COMP = p.POSS, p.MIXIN, r.COMP
PLAYERLIST = 'net/minecraft/server/players/PlayerList'
RESOURCE = 'net/minecraft/resources/ResourceLocation'
REGISTRIES = 'net/minecraft/core/registries/Registries'
TARGET = PD + '$TargetPos'
LOGIN = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent'
POST = 'net/neoforged/neoforge/event/tick/ServerTickEvent$Post'
DEATH = 'net/neoforged/neoforge/event/entity/living/LivingDeathEvent'
CLONE = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$Clone'
CONTENT = 'dev/settledlands/npc/NpcContent'
LONGSET = 'it/unimi/dsi/fastutil/longs/LongOpenHashSet'
LONGITER = 'it/unimi/dsi/fastutil/longs/LongIterator'
SYNC = 'dev/settledlands/npc/PossessionClientSync'
SOUNDS, SOUNDSOURCE = r.SOUNDS, 'net/minecraft/sounds/SoundSource'
LIGHTNING = 'net/minecraft/world/entity/LightningBolt'


def op(code, idx): return f.op(code, idx)
def text(a, cp, s): r.text(a, cp, s)
def local(a, kind, i): r.insn_local(a, kind, i)
def obj(a, cp, name, desc='()V', args=None): r.obj(a, cp, name, desc, args)
def field(owner, name, desc, ret=0xb0): return r.getfield(owner, name, desc, ret)


def rewrite(raw, *, add_fields=(), add_methods=(), replace=(), clear_access=0):
    """Add executable stand-in members without changing real mod method code."""
    k = r.f.classedit.Klass(raw)
    def encode_code(stack, locals_, code):
        return u2(stack) + u2(locals_) + u4(len(code)) + code + u2(0) + u2(0)
    existing = []
    for method in k.methods:
        item = dict(method); item['attrs'] = [dict(a) for a in method['attrs']]
        for name, desc, stack, locals_, gen in replace:
            if method['name'] == name and method['desc'] == desc:
                body = encode_code(stack, locals_, gen(k))
                for a in item['attrs']:
                    if a['name'] == 'Code': a['body'] = body
        existing.append(item)
    added_f = [u2(flags) + u2(k.add_utf8(name)) + u2(k.add_utf8(desc)) + u2(0)
               for flags, name, desc in add_fields]
    added_m = []
    code_name = k.add_utf8('Code')
    for flags, name, desc, stack, locals_, gen in add_methods:
        body = encode_code(stack, locals_, gen(k))
        added_m.append(u2(flags) + u2(k.add_utf8(name)) + u2(k.add_utf8(desc)) + u2(1) + attr(code_name, body))
    attrs = b''.join(u2(x['name_idx']) + u4(len(x['body'])) + x['body'] for x in k.attrs)
    return (raw[:8] + u2(len(k.cp)) + k._pool_bytes() + u2(k.access & ~clear_access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) +
            u2(len(k.fields) + len(added_f)) + b''.join(f.encode_member(x) for x in k.fields) + b''.join(added_f) +
            u2(len(existing) + len(added_m)) + b''.join(f.encode_member(x) for x in existing) + b''.join(added_m) +
            u2(len(k.attrs)) + attrs)


def fake_nbt():
    def get(k):
        return (b'\x2a' + op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, k.add_methodref('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;')) +
                op(0xc0, k.add_class(TAG)) + b'\xb0')
    def get_bool(k):
        a = Asm(); a.raw(0x2a); a.op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')); a.raw(0x2b)
        a.op(0xb6, k.add_methodref('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;')); a.raw(0x4d, 0x2c)
        a.op(0xc1, k.add_class('java/lang/Boolean')); a.jump(0x99, 'no'); a.raw(0x2c)
        a.op(0xc0, k.add_class('java/lang/Boolean')); a.op(0xb6, k.add_methodref('java/lang/Boolean', 'booleanValue', '()Z'))
        a.raw(0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def put_bool(k):
        return (b'\x2a' + op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b\x1c' +
                op(0xb8, k.add_methodref('java/lang/Boolean', 'valueOf', '(Z)Ljava/lang/Boolean;')) +
                op(0xb6, k.add_methodref('java/util/HashMap', 'put', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;')) + b'\x57\xb1')
    def put_uuid(k):
        return (b'\x2a' + op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b\x2c' +
                op(0xb6, k.add_methodref('java/util/HashMap', 'put', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;')) + b'\x57\xb1')
    def get_uuid(k):
        return (b'\x2a' + op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, k.add_methodref('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;')) +
                op(0xc0, k.add_class('java/util/UUID')) + b'\xb0')
    def has_uuid(k):
        return (b'\x2a' + op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, k.add_methodref('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;')) +
                op(0xc1, k.add_class('java/util/UUID')) + b'\xac')
    def put_string(k):
        return (b'\x2a' + op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b\x2c' +
                op(0xb6, k.add_methodref('java/util/HashMap', 'put', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;')) + b'\x57\xb1')
    def get_string(k):
        a = Asm(); a.raw(0x2a); a.op(0xb4, k.add_fieldref(NBT, 'map', 'Ljava/util/HashMap;')); a.raw(0x2b)
        a.op(0xb6, k.add_methodref('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;')); a.raw(0x4d, 0x2c)
        a.op(0xc1, k.add_class('java/lang/String')); a.jump(0x99, 'empty'); a.raw(0x2c)
        a.op(0xc0, k.add_class('java/lang/String')); a.raw(0xb0)
        a.label('empty'); a.raw(f.ldc(k.add_string('')), 0xb0); return a.finish()
    return rewrite(r.nbt_class(), add_methods=[(1, 'get', '(Ljava/lang/String;)L' + TAG + ';', 2, 2, get),
                   (1, 'putBoolean', '(Ljava/lang/String;Z)V', 3, 3, put_bool),
                   (1, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V', 3, 3, put_uuid),
                   (1, 'putString', '(Ljava/lang/String;Ljava/lang/String;)V', 3, 3, put_string)],
                   replace=[('getBoolean', '(Ljava/lang/String;)Z', 2, 3, get_bool),
                            ('getUUID', '(Ljava/lang/String;)Ljava/util/UUID;', 2, 2, get_uuid),
                            ('hasUUID', '(Ljava/lang/String;)Z', 2, 2, has_uuid),
                            ('getString', '(Ljava/lang/String;)Ljava/lang/String;', 2, 3, get_string)])


def fake_entity():
    fields = [(1, 'x', 'D'), (1, 'y', 'D'), (1, 'z', 'D'), (1, 'entityUuid', 'Ljava/util/UUID;')]
    methods = [(1, 'isRemoved', '()Z', 1, 1, lambda k: b'\x2a' + op(0xb4, k.add_fieldref(E, 'removed', 'Z')) + b'\xac'),
               (1, 'getUUID', '()Ljava/util/UUID;', 1, 1, lambda k: b'\x2a' + op(0xb4, k.add_fieldref(E, 'entityUuid', 'Ljava/util/UUID;')) + b'\xb0')]
    def ctor(k):
        return (b'\x2a' + op(0xb7, k.add_methodref('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + op(0xbb, k.add_class(NBT)) + b'\x59' + op(0xb7, k.add_methodref(NBT, '<init>', '()V')) +
                op(0xb5, k.add_fieldref(E, 'data', 'L' + NBT + ';')) +
                b'\x2a' + op(0xb8, k.add_methodref('java/util/UUID', 'randomUUID', '()Ljava/util/UUID;')) +
                op(0xb5, k.add_fieldref(E, 'entityUuid', 'Ljava/util/UUID;')) + b'\xb1')
    for axis in 'xyz':
        methods.append((1, 'get' + axis.upper(), '()D', 2, 1,
                        lambda k, axis=axis: b'\x2a' + op(0xb4, k.add_fieldref(E, axis, 'D')) + b'\xaf'))
    def blockpos(k):
        a = Asm(); a.op(0xbb, k.add_class(BLOCK)); a.raw(0x59)
        for axis in 'xyz': a.raw(0x2a); a.op(0xb4, k.add_fieldref(E, axis, 'D')); a.raw(0x8e)  # d2i
        a.op(0xb7, k.add_methodref(BLOCK, '<init>', '(III)V')); a.raw(0xb0); return a.finish()
    methods.append((1, 'blockPosition', '()L' + BLOCK + ';', 8, 1, blockpos))
    def move(k):
        a = Asm()
        for axis, pos in [('x', 1), ('y', 3), ('z', 5)]:
            a.raw(0x2a); p.xlocal(a, 'dload', pos); a.op(0xb5, k.add_fieldref(E, axis, 'D'))
        a.raw(0xb1); return a.finish()
    methods.append((1, 'moveTo', '(DDDFF)V', 3, 9, move))
    return rewrite(r.entity_class(), add_fields=fields, add_methods=methods, replace=[('<init>', '()V', 3, 1, ctor)])


def fake_blockpos():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        for i, axis in enumerate('xyz', 1): a.raw(0x2a, 0x1a + i); a.op(0xb5, cp.field(BLOCK, axis, 'I'))
        a.raw(0xb1); return a.finish()
    def aslong(cp):
        return b'\x2a' + op(0xb3, cp.field(BLOCK, 'last', 'L' + BLOCK + ';')) + b'\x0a\xad'
    return f.fake_class(BLOCK, [(1, c, 'I') for c in 'xyz'] + [(9, 'last', 'L' + BLOCK + ';')], [
        (1, '<init>', '(III)V', 2, 4, ctor),
        *[(1, 'get' + c.upper(), '()I', 1, 1, field(BLOCK, c, 'I', 0xac)) for c in 'xyz'],
        (1, 'asLong', '()J', 2, 1, aslong),
        (9, 'of', '(J)L' + BLOCK + ';', 1, 2, lambda cp: op(0xb2, cp.field(BLOCK, 'last', 'L' + BLOCK + ';')) + b'\xb0')])


def fake_box():
    def frompos(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a, 0x2b); a.op(0xb6, cp.method(BLOCK, 'getX', '()I')); a.raw(0x87)
        a.op(0xb5, cp.field(BOX, 'x', 'D')); a.raw(0xb1); return a.finish()
    def inflated(cp):
        return b'\x2a\x27' + op(0xb5, cp.field(BOX, 'radius', 'D')) + b'\x2a\xb0'
    def contains_x(cp):
        # AABB(block).inflate(r) is [x-r, x+1+r); exercise x-range.
        a = Asm(); a.raw(0x27, 0x2a); a.op(0xb4, cp.field(BOX, 'x', 'D'))
        a.raw(0x2a); a.op(0xb4, cp.field(BOX, 'radius', 'D')); a.raw(0x67, 0x97); a.jump(0x9b, 'no')
        a.raw(0x27, 0x2a); a.op(0xb4, cp.field(BOX, 'x', 'D')); a.raw(0x0f, 0x63, 0x2a)
        a.op(0xb4, cp.field(BOX, 'radius', 'D')); a.raw(0x63, 0x98); a.jump(0x9c, 'no')
        a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    return f.fake_class(BOX, [(1, 'x', 'D'), (1, 'radius', 'D')], [
        (1, '<init>', '(L' + BLOCK + ';)V', 3, 2, frompos),
        # Broad six-coordinate box; only old first-owner capture uses it here.
        (1, '<init>', '(DDDDDD)V', 3, 13, lambda cp: b'\x2a' +
         op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) + b'\x2a\x10\x64\x87' +
         op(0xb5, cp.field(BOX, 'radius', 'D')) + b'\xb1'),
        (1, 'inflate', '(D)L' + BOX + ';', 3, 3, inflated),
        (1, 'containsX', '(D)Z', 6, 3, contains_x)])


def fake_server_level():
    def ctor(cp):
        a = Asm(); a.raw(0x2a, 0x03, 0x2b, 0x2c); a.op(0xb7, cp.method(L, '<init>', '(ZL' + KEY + ';L' + SERVER + ';)V'))
        for name in ('near', 'altarItems'):
            a.raw(0x2a); obj(a, cp, 'java/util/ArrayList'); a.op(0xb5, cp.field(SL, name, 'Ljava/util/List;'))
        a.raw(0xb1); return a.finish()
    def bodies(cp):
        a = Asm(); obj(a, cp, 'java/util/ArrayList'); a.raw(0x4e)
        a.raw(0x2a); a.op(0xb4, cp.field(SL, 'near', 'Ljava/util/List;'))
        r.p.invokeinterface(a, cp, 'java/util/List', 'iterator', '()Ljava/util/Iterator;', 1); a.raw(0x3a, 4)
        a.label('loop'); a.raw(0x19, 4); r.p.invokeinterface(a, cp, 'java/util/Iterator', 'hasNext', '()Z', 1); a.jump(0x99, 'done')
        a.raw(0x19, 4); r.p.invokeinterface(a, cp, 'java/util/Iterator', 'next', '()Ljava/lang/Object;', 1)
        a.op(0xc0, cp.cls(E)); a.raw(0x3a, 5, 0x19, 5); a.op(0xb6, cp.method(E, 'isRemoved', '()Z')); a.jump(0x9a, 'loop')
        a.raw(0x2c, 0x19, 5); a.op(0xb6, cp.method(E, 'getX', '()D'))
        a.op(0xb6, cp.method(BOX, 'containsX', '(D)Z')); a.jump(0x99, 'loop')
        a.raw(0x2d, 0x19, 5); a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z'))
        a.raw(0x57); a.jump(0xa7, 'loop'); a.label('done'); a.raw(0x2d, 0xb0); return a.finish()
    def items(cp):
        a = Asm(); obj(a, cp, 'java/util/ArrayList'); a.raw(0x3a, 4, 0x2a)
        a.op(0xb4, cp.field(SL, 'altarItems', 'Ljava/util/List;'))
        r.p.invokeinterface(a, cp, 'java/util/List', 'iterator', '()Ljava/util/Iterator;', 1); a.raw(0x3a, 5)
        a.label('loop'); a.raw(0x19, 5); r.p.invokeinterface(a, cp, 'java/util/Iterator', 'hasNext', '()Z', 1); a.jump(0x99, 'done')
        a.raw(0x19, 5); r.p.invokeinterface(a, cp, 'java/util/Iterator', 'next', '()Ljava/lang/Object;', 1); a.raw(0x3a, 6)
        a.raw(0x2d, 0x19, 6); r.p.invokeinterface(a, cp, 'java/util/function/Predicate', 'test', '(Ljava/lang/Object;)Z', 2)
        a.jump(0x99, 'loop'); a.raw(0x19, 4, 0x19, 6)
        a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
        a.jump(0xa7, 'loop'); a.label('done'); a.raw(0x19, 4, 0xb0); return a.finish()
    def by_uuid(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(SL, 'near', 'Ljava/util/List;'))
        r.p.invokeinterface(a, cp, 'java/util/List', 'iterator', '()Ljava/util/Iterator;', 1); a.raw(0x4d)
        a.label('loop'); a.raw(0x2c); r.p.invokeinterface(a, cp, 'java/util/Iterator', 'hasNext', '()Z', 1)
        a.jump(0x99, 'none'); a.raw(0x2c); r.p.invokeinterface(a, cp, 'java/util/Iterator', 'next', '()Ljava/lang/Object;', 1)
        a.op(0xc0, cp.cls(E)); a.raw(0x4e, 0x2d); a.op(0xb6, cp.method(E, 'isRemoved', '()Z')); a.jump(0x9a, 'loop')
        a.raw(0x2b, 0x2d); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'loop')
        a.raw(0x2d, 0xb0); a.label('none'); a.raw(0x01, 0xb0); return a.finish()
    def spawn(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(SL, 'failSpawn', 'Z')); a.jump(0x9a, 'no')
        a.raw(0x2a); a.op(0xb4, cp.field(SL, 'near', 'Ljava/util/List;')); a.raw(0x2b)
        r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0xac)
        a.label('no'); a.raw(0x03, 0xac); return a.finish()
    return f.fake_class(SL, [(1, 'near', 'Ljava/util/List;'), (1, 'altarItems', 'Ljava/util/List;'), (1, 'failSpawn', 'Z')], [
        (1, '<init>', '(L' + KEY + ';L' + SERVER + ';)V', 4, 3, ctor),
        (1, 'getEntity', '(Ljava/util/UUID;)L' + E + ';', 2, 4, by_uuid),
        (1, 'getServer', '()L' + SERVER + ';', 1, 1, field(L, 'server', 'L' + SERVER + ';')),
        (1, 'getEntitiesOfClass', '(Ljava/lang/Class;L' + BOX + ';)Ljava/util/List;', 3, 6, bodies),
        (1, 'getEntitiesOfClass', '(Ljava/lang/Class;L' + BOX + ';Ljava/util/function/Predicate;)Ljava/util/List;', 3, 7, items),
        (1, 'addFreshEntity', '(L' + E + ';)Z', 2, 2, spawn),
        (1, 'playSound', '(L' + r.P + ';L' + BLOCK + ';L' + r.SOUND + ';L' + SOUNDSOURCE + ';FF)V', 0, 7, lambda cp: b'\xb1')], super_name=L)


def fake_server_player():
    raw = r.server_player_class()
    fields = [(1, 'fallDistance', 'F'), (1, 'failTeleport', 'Z'), (1, 'teleports', 'I')]
    def teleport(k):
        a = Asm(); a.raw(0x2a); a.op(0xb4, k.add_fieldref(SP, 'failTeleport', 'Z')); a.jump(0x9a, 'no')
        a.raw(0x2a, 0x2b); a.op(0xb5, k.add_fieldref(E, 'world', 'L' + L + ';'))
        for axis, pos in [('x', 2), ('y', 4), ('z', 6)]:
            a.raw(0x2a); p.xlocal(a, 'dload', pos); a.op(0xb5, k.add_fieldref(E, axis, 'D'))
        ff = k.add_fieldref(SP, 'teleports', 'I'); a.raw(0x2a, 0x59); a.op(0xb4, ff); a.raw(0x04, 0x60); a.op(0xb5, ff)
        a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    return rewrite(raw, add_fields=fields, add_methods=[
        (1, 'teleportTo', p.TELEPORT_DESC, 3, 11, teleport),
        (1, 'clearFire', '()V', 0, 1, lambda k: b'\xb1'),
        (1, 'setDeltaMovement', '(L' + VEC + ';)V', 0, 2, lambda k: b'\xb1'),
        (1, 'getYRot', '()F', 1, 1, lambda k: b'\x0b\xae')])


def fake_purgatory_data():
    def master(cp):
        a = Asm(); obj(a, cp, PD); a.raw(0xb0); return a.finish()
    def queued(cp):
        a = Asm(); a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.jump(0xc6, 'second')
        a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, 'yes')
        a.label('second'); a.op(0xb2, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.jump(0xc6, 'no')
        a.op(0xb2, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.raw(0xac)
        a.label('yes'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def remove(cp):
        a = Asm(); a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.jump(0xc6, 'second')
        a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'second')
        a.op(0xb2, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
        a.raw(0x01); a.op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.raw(0x04, 0xac)
        a.label('second'); a.op(0xb2, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.jump(0xc6, 'no')
        a.op(0xb2, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'no')
        a.raw(0x01); a.op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;')); a.raw(0x04, 0xac)
        a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def poll(cp):
        return (op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')) +
                op(0xb2, cp.field(PD, 'second', 'Ljava/util/UUID;')) + op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;')) +
                b'\x01' + op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;')) + b'\xb0')
    def has(cp):
        a = Asm(); a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.jump(0xc6, 'no')
        a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def pending(cp):
        a = Asm(); a.raw(0x2b); a.op(0xb3, cp.field(PD, 'pendingId', 'Ljava/util/UUID;'))
        obj(a, cp, TARGET, '(Ljava/lang/String;J)V', lambda: (a.raw(0x2c, 0x2d), a.op(0xb6, cp.method(BLOCK, 'asLong', '()J'))))
        a.op(0xb3, cp.field(PD, 'pending', 'L' + TARGET + ';')); a.raw(0xb1); return a.finish()
    def consume(cp):
        a = Asm(); a.op(0xb2, cp.field(PD, 'pendingId', 'Ljava/util/UUID;')); a.jump(0xc6, 'none')
        a.op(0xb2, cp.field(PD, 'pendingId', 'Ljava/util/UUID;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'none')
        a.op(0xb2, cp.field(PD, 'pending', 'L' + TARGET + ';')); a.raw(0x01)
        a.op(0xb3, cp.field(PD, 'pending', 'L' + TARGET + ';')); a.raw(0x01)
        a.op(0xb3, cp.field(PD, 'pendingId', 'Ljava/util/UUID;')); a.raw(0xb0)
        a.label('none'); a.raw(0x01, 0xb0); return a.finish()
    def altars(cp):
        a = Asm(); obj(a, cp, LONGSET); a.raw(0xb0); return a.finish()
    return f.fake_class(PD, [(9, 'queued', 'Ljava/util/UUID;'), (9, 'second', 'Ljava/util/UUID;'), (9, 'pendingId', 'Ljava/util/UUID;'),
                             (9, 'pending', 'L' + TARGET + ';')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()), (9, 'getMaster', '(L' + SERVER + ';)L' + PD + ';', 2, 1, master),
        (1, 'isDeadInQueue', '(Ljava/util/UUID;)Z', 2, 2, queued), (1, 'removeFromQueue', '(Ljava/util/UUID;)Z', 2, 2, remove),
        (1, 'hasDeadPlayers', '()Z', 1, 1, has), (1, 'pollFirstDead', '()Ljava/util/UUID;', 2, 1, poll),
        (1, 'setPendingRevive', '(Ljava/util/UUID;Ljava/lang/String;L' + BLOCK + ';)V', 5, 4, pending),
        (1, 'consumePendingRevive', '(Ljava/util/UUID;)L' + TARGET + ';', 2, 2, consume),
        (1, 'altars', '()L' + LONGSET + ';', 2, 1, altars)])


def fake_server():
    return f.fake_class(SERVER, [(1, 'players', 'L' + PLAYERLIST + ';'), (1, 'world', 'L' + SL + ';')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'getPlayerList', '()L' + PLAYERLIST + ';', 1, 1, field(SERVER, 'players', 'L' + PLAYERLIST + ';')),
        (1, 'getLevel', '(L' + KEY + ';)L' + SL + ';', 1, 2, field(SERVER, 'world', 'L' + SL + ';')),
        (1, 'overworld', '()L' + SL + ';', 1, 1, field(SERVER, 'world', 'L' + SL + ';'))])


def fake_playerlist():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a); obj(a, cp, 'java/util/ArrayList'); a.op(0xb5, cp.field(PLAYERLIST, 'list', 'Ljava/util/List;'))
        a.raw(0xb1); return a.finish()
    def find(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(PLAYERLIST, 'online', 'L' + SP + ';')); a.jump(0xc6, 'none')
        a.raw(0x2b, 0x2a); a.op(0xb4, cp.field(PLAYERLIST, 'online', 'L' + SP + ';'))
        a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'none')
        a.raw(0x2a); a.op(0xb4, cp.field(PLAYERLIST, 'online', 'L' + SP + ';')); a.raw(0xb0)
        a.label('none'); a.raw(0x01, 0xb0); return a.finish()
    return f.fake_class(PLAYERLIST, [(1, 'online', 'L' + SP + ';'), (1, 'list', 'Ljava/util/List;')], [
        (1, '<init>', '()V', 3, 1, ctor),
        (1, 'getPlayer', '(Ljava/util/UUID;)L' + SP + ';', 2, 2, find),
        (1, 'getPlayers', '()Ljava/util/List;', 1, 1, field(PLAYERLIST, 'list', 'Ljava/util/List;'))])


def fake_items():
    def clinit(cp):
        a = Asm()
        for name in ('LEAD', 'EMERALD'):
            obj(a, cp, ITEM); a.op(0xb3, cp.field(ITEMS, name, 'L' + ITEM + ';'))
        a.raw(0xb1); return a.finish()
    return f.fake_class(ITEMS, [(9, 'LEAD', 'L' + ITEM + ';'), (9, 'EMERALD', 'L' + ITEM + ';')], [(8, '<clinit>', '()V', 2, 0, clinit)])


def extra_stubs(classes):
    def resource_get(s):
        return lambda cp: f.ldc(cp.string(s)) + b'\xb0'
    classes[RESOURCE] = f.fake_class(RESOURCE, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'getNamespace', '()Ljava/lang/String;', 1, 1, resource_get('minecraft')),
        (1, 'getPath', '()Ljava/lang/String;', 1, 1, resource_get('overworld')),
        (9, 'tryParse', '(Ljava/lang/String;)L' + RESOURCE + ';', 2, 1,
         lambda cp: op(0xbb, cp.cls(RESOURCE)) + b'\x59' + op(0xb7, cp.method(RESOURCE, '<init>', '()V')) + b'\xb0')])
    classes[KEY] = f.fake_class(KEY, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'location', '()L' + RESOURCE + ';', 2, 1,
         lambda cp: op(0xbb, cp.cls(RESOURCE)) + b'\x59' + op(0xb7, cp.method(RESOURCE, '<init>', '()V')) + b'\xb0'),
        (9, 'create', '(L' + KEY + ';L' + RESOURCE + ';)L' + KEY + ';', 1, 2, lambda cp: b'\x2a\xb0')])
    classes[REGISTRIES] = f.fake_class(REGISTRIES, [(9, 'DIMENSION', 'L' + KEY + ';')], [])
    classes[BLOCK], classes[BOX] = fake_blockpos(), fake_box()
    classes[E], classes[NBT], classes[SL], classes[SP] = fake_entity(), fake_nbt(), fake_server_level(), fake_server_player()
    classes[PD], classes[SERVER], classes[PLAYERLIST], classes[ITEMS] = fake_purgatory_data(), fake_server(), fake_playerlist(), fake_items()
    classes[LONGITER] = r.interface(LONGITER, parents=['java/util/Iterator'])
    classes[LONGSET] = f.fake_class(LONGSET, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'isEmpty', '()Z', 1, 1, lambda cp: b'\x04\xac'),
        (1, 'iterator', '()L' + LONGITER + ';', 1, 1, lambda cp: b'\x01\xb0')])
    classes[TARGET] = f.fake_class(TARGET, [(1, 'dimension', 'Ljava/lang/String;'), (1, 'posLong', 'J')], [
        (1, '<init>', '(Ljava/lang/String;J)V', 3, 4, lambda cp: b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
         b'\x2a\x2b' + op(0xb5, cp.field(TARGET, 'dimension', 'Ljava/lang/String;')) +
         b'\x2a\x20' + op(0xb5, cp.field(TARGET, 'posLong', 'J')) + b'\xb1')])
    def item_copy(k):
        return b'\x2a\x2a' + op(0xb4, k.add_fieldref(IT, 'count', 'I')) + op(0xb6, k.add_methodref(IT, 'copyWithCount', '(I)L' + IT + ';')) + b'\xb0'
    def legacy_parse(k):
        # Realistic enough to expose the old parser's unconditional NBT removal
        # under a verifier-valid guard-bypass mutant; CODEC stays independently real.
        a = Asm(); a.op(0xbb, k.add_class(IT)); a.raw(0x59); a.op(0xb7, k.add_methodref(IT, '<init>', '()V')); a.raw(0x4d)
        for name, desc in [('count', 'I'), ('id', 'Ljava/lang/String;'), ('meta', 'Ljava/lang/String;')]:
            a.raw(0x2c, 0x2b); a.op(0xc0, k.add_class(NBT)); a.op(0xb4, k.add_fieldref(NBT, name, desc))
            a.op(0xb5, k.add_fieldref(IT, name, desc))
        a.raw(0x2c); a.op(0xb8, k.add_methodref('java/util/Optional', 'of', '(Ljava/lang/Object;)Ljava/util/Optional;'))
        a.raw(0xb0); return a.finish()
    classes[IT] = rewrite(r.item_stack_class(), add_methods=[(1, 'copy', '()L' + IT + ';', 2, 1, item_copy),
        (9, 'parse', '(L' + r.PROVIDER + ';L' + TAG + ';)Ljava/util/Optional;', 2, 3, legacy_parse)])
    def alive(k):
        a = Asm(); a.raw(0x2a); a.op(0xb4, k.add_fieldref(E, 'removed', 'Z')); a.jump(0x99, 'yes')
        a.raw(0x03, 0xac); a.label('yes'); a.raw(0x04, 0xac); return a.finish()
    classes[ITEM_ENTITY] = rewrite(r.item_entity_class(), add_methods=[
        (1, 'isAlive', '()Z', 1, 1, alive),
        (1, 'setItem', '(L' + IT + ';)V', 2, 2, lambda k: b'\x2a\x2b' + op(0xb5, k.add_fieldref(ITEM_ENTITY, 'item', 'L' + IT + ';')) + b'\xb1')])
    def type_init(cp):
        a = Asm(); obj(a, cp, r.TYPE); a.op(0xb3, cp.field(r.TYPE, 'LIGHTNING_BOLT', 'L' + r.TYPE + ';'))
        a.raw(0xb1); return a.finish()
    def create_entity(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb2, cp.field(r.TYPE, 'LIGHTNING_BOLT', 'L' + r.TYPE + ';'))
        a.jump(0xa5, 'none'); obj(a, cp, C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: a.raw(0x2a, 0x2b))
        a.raw(0xb0); a.label('none'); a.raw(0x01, 0xb0); return a.finish()
    classes[r.TYPE] = f.fake_class(r.TYPE, [(9, 'LIGHTNING_BOLT', 'L' + r.TYPE + ';')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()), (8, '<clinit>', '()V', 2, 0, type_init),
        (1, 'create', '(L' + L + ';)L' + E + ';', 4, 2, create_entity)])
    classes[CONTENT] = f.fake_class(CONTENT, [], [(9, 'guideCorpse', '()L' + r.TYPE + ';', 2, 0,
        lambda cp: op(0xbb, cp.cls(r.TYPE)) + b'\x59' + op(0xb7, cp.method(r.TYPE, '<init>', '()V')) + b'\xb0')])
    classes[LIGHTNING] = f.fake_class(LIGHTNING, [], [
        (1, 'moveTo', '(DDD)V', 0, 7, lambda cp: b'\xb1'),
        (1, 'setVisualOnly', '(Z)V', 0, 2, lambda cp: b'\xb1')], super_name=E)
    def vec_init(cp):
        a = Asm(); obj(a, cp, VEC); a.op(0xb3, cp.field(VEC, 'ZERO', 'L' + VEC + ';')); a.raw(0xb1); return a.finish()
    classes[VEC] = f.fake_class(VEC, [(9, 'ZERO', 'L' + VEC + ';'), (1, 'x', 'D'), (1, 'y', 'D'), (1, 'z', 'D')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()), (8, '<clinit>', '()V', 2, 0, vec_init)])
    classes[LOGIN] = f.fake_class(LOGIN, [], [(1, '<init>', '(L' + r.P + ';)V', 2, 2,
        lambda cp: b'\x2a\x2b' + op(0xb7, cp.method(r.PLAYER_EVENT, '<init>', '(L' + r.P + ';)V')) + b'\xb1')], super_name=r.PLAYER_EVENT)
    classes[POST] = f.fake_class(POST, [(1, 'server', 'L' + SERVER + ';')], [
        (1, '<init>', '(L' + SERVER + ';)V', 2, 2, lambda cp: b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
         b'\x2a\x2b' + op(0xb5, cp.field(POST, 'server', 'L' + SERVER + ';')) + b'\xb1'),
        (1, 'getServer', '()L' + SERVER + ';', 1, 1, field(POST, 'server', 'L' + SERVER + ';'))])
    classes[SYNC] = f.fake_class(SYNC, [], [(9, 'push', '(L' + SERVER + ';Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V', 0, 4, lambda cp: b'\xb1')])
    classes[SOUNDSOURCE] = f.fake_class(SOUNDSOURCE, [(9, 'PLAYERS', 'L' + SOUNDSOURCE + ';')], [])
    classes[SOUNDS] = rewrite(classes[SOUNDS], add_fields=[(9, 'TOTEM_USE', 'L' + r.SOUND + ';')])
    classes[r.POSS_SYS] = f.fake_class(r.POSS_SYS, [(9, 'possessed', 'Z')], [
        (9, 'isPossessed', '(L' + E + ';)Z', 1, 1,
         lambda cp: op(0xb2, cp.field(r.POSS_SYS, 'possessed', 'Z')) + b'\xac'),
        (9, 'bodiless', '(L' + SP + ';)L' + r.HOLDER + ';', 1, 1, lambda cp: b'\x01\xb0')])
    classes[DEATH] = f.fake_class(DEATH, [(1, 'entity', 'L' + r.LIVING + ';')], [
        (1, '<init>', '(L' + r.LIVING + ';)V', 2, 2, lambda cp: b'\x2a' +
         op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) + b'\x2a\x2b' +
         op(0xb5, cp.field(DEATH, 'entity', 'L' + r.LIVING + ';')) + b'\xb1'),
        (1, 'getEntity', '()L' + r.LIVING + ';', 1, 1, field(DEATH, 'entity', 'L' + r.LIVING + ';'))])
    classes[CLONE] = f.fake_class(CLONE, [(1, 'original', 'L' + r.P + ';')], [
        (1, '<init>', '(L' + r.P + ';L' + r.P + ';)V', 2, 3, lambda cp: b'\x2a\x2c' +
         op(0xb7, cp.method(r.PLAYER_EVENT, '<init>', '(L' + r.P + ';)V')) + b'\x2a\x2b' +
         op(0xb5, cp.field(CLONE, 'original', 'L' + r.P + ';')) + b'\xb1'),
        (1, 'getOriginal', '()L' + r.P + ';', 1, 1, field(CLONE, 'original', 'L' + r.P + ';'))], super_name=r.PLAYER_EVENT)


def pm_clinit(k):
    return (op(0xbb, k.add_class(KEY)) + b'\x59' + op(0xb7, k.add_methodref(KEY, '<init>', '()V')) +
            op(0xb3, k.add_fieldref(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')) +
            op(0xb8, k.add_methodref('java/util/Collections', 'emptySet', '()Ljava/util/Set;')) +
            op(0xb3, k.add_fieldref(PM, 'NO_RELATIVES', 'Ljava/util/Set;')) + b'\xb1')


def build_classes(jar):
    classes = r.build_classes(jar, jar)
    extra_stubs(classes)
    classes[PM] = r.subset(jar, PM, {'tryReviveAtAltar', 'isValidEmeraldEntity', 'spawnVisualLightning',
                                    'teleportPlayerOntoAltar', 'teleportToTarget', 'onPlayerLogin'},
                           fields={'PURGATORY_LEVEL', 'NO_RELATIVES'}, add_clinit=pm_clinit)
    classes[C] = r.subset(jar, C, {'<init>', '<clinit>', 'setOwner', 'isCoffin', 'setCoffin',
                                   'settledlands$getOwnerUuid', 'reviveOwner', 'playSouls', 'spawnVisualLightning'})
    # Visual-only method is a synthetic no-op, all resurrection instructions are real.
    classes[C] = rewrite(classes[C], replace=[('playSouls', '(L' + SL + ';L' + BLOCK + ';)V', 0, 3, lambda k: b'\xb1')])
    classes[MIXIN] = r.subset(jar, MIXIN, {'<init>', 'markedCorpse', 'settledlands$keepBodyCorpse',
                                            'tryConsumeEmeralds', 'handCorpseItemsToPlayer',
                                            'settledlands$reviveOnlyFromQueue', 'lambda$tryConsumeEmeralds$0'},
                              expose={'settledlands$reviveOnlyFromQueue', 'settledlands$keepBodyCorpse'})
    classes[MIXIN] = rewrite(classes[MIXIN], clear_access=0x0400)  # instantiate synthetic mixin receiver
    classes[p.LIFECYCLE] = r.subset(jar, p.LIFECYCLE, {'onPlayerDeath'})
    classes[POSS] = r.subset(jar, POSS, {'<clinit>', 'data', 'onServerTick', 'nearRevivalAltar', 'clearMarker',
        'repossess', 'restoreBodyItems', 'guaranteeCorpses', 'healStuckIvyBodies', 'onPlayerClone', 'onPlayerDrops',
        'findOwnerCorpse', 'movePendingItemsToCorpse', 'ownerUuid'})
    # Only unrelated periodic visual/watch work is stubbed; onServerTick, range
    # decision, clearMarker and repossess remain real mod bytecode.
    classes[POSS] = rewrite(classes[POSS], replace=[('guaranteeCorpses', '()V', 0, 0, lambda k: b'\xb1'),
        ('healStuckIvyBodies', '(L' + SERVER + ';)V', 0, 1, lambda k: b'\xb1')])
    with zipfile.ZipFile(jar) as z:
        if HELPER + '.class' in z.namelist(): classes[HELPER] = z.read(HELPER + '.class')
    return classes


def harness_class():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        printers = {d: cp.method('java/io/PrintStream', 'println', '(' + d + ')V')
                    for d in ('I', 'Z', 'D', 'Ljava/lang/String;')}
        def load(i): local(a, 'aload', i)
        def store(i): local(a, 'astore', i)
        def num(n): r.const(a, n)
        def string(s): text(a, cp, s)
        def boolean(v): a.raw(0x04 if v else 0x03)
        def make(name, desc='()V', args=None): obj(a, cp, name, desc, args)
        def get(i, owner, name, desc): load(i); a.op(0xb4, cp.field(owner, name, desc))
        def put(i, owner, name, desc, emit): load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def val(label, typ, emit):
            a.op(0xb2, out); string(label + '='); a.op(0xb6, cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V'))
            a.op(0xb2, out); emit(); a.op(0xb6, printers[typ])
        def uuid(n=1):
            string('00000000-0000-0000-0000-%012d' % n)
            a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(i): load(i); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def flag(i, key, b=True): data(i); string(key); boolean(b); a.op(0xb6, cp.method(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
        def flagvalue(i, key): data(i); string(key); a.op(0xb6, cp.method(NBT, 'getBoolean', '(Ljava/lang/String;)Z'))
        def contains(i, key): data(i); string(key); a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
        def setxyz(i, x, y=64, z=2):
            for axis, value in zip('xyz', (x, y, z)):
                put(i, E, axis, 'D', lambda value=value: (num(value), a.raw(0x87)))
        def inventory(): get(3, SP, 'inventory', 'L' + INV + ';')
        def itemfield(name, desc): get(8, ITEM_ENTITY, 'item', 'L' + IT + ';'); a.op(0xb4, cp.field(IT, name, desc))
        def env(room=30, *, marked=True, coffin=False, x=8):
            a.raw(0x03); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'pending', 'L' + TARGET + ';'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'pendingId', 'Ljava/util/UUID;'))
            make(SERVER); store(1); make(KEY); store(10)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(10), load(1))); store(2)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V',
                 lambda: (a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')), load(1))); store(11)
            make(PLAYERLIST); store(9); put(1, SERVER, 'players', 'L' + PLAYERLIST + ';', lambda: load(9))
            put(1, SERVER, 'world', 'L' + SL + ';', lambda: load(2))
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(11), num(room))); store(3)
            put(9, PLAYERLIST, 'online', 'L' + SP + ';', lambda: load(3))
            get(9, PLAYERLIST, 'list', 'Ljava/util/List;'); load(3)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
            load(3); a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;')); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(4)
            load(4); load(3); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
            # Simulate the persisted, death-specific association. The real
            # stock spawn and clone paths are exercised separately below.
            data(3); string(p.DEATH_CORPSE); load(4); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
            a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
            setxyz(4, x)
            if marked: flag(4, p.PLAYER_BODY); flag(3, p.PLAYER_PENDING)
            if coffin: load(4); boolean(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
            get(2, SL, 'near', 'Ljava/util/List;'); load(4)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
            make(BLOCK, '(III)V', lambda: (num(0), num(64), num(0))); store(5)
        def list_at(index=4, key=p.CORPSE_ITEMS):
            make(LIST); store(6); data(index); string(key); load(6)
            a.op(0xb6, cp.method(NBT, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';')); a.raw(0x57)
        def stored(amount, invalid=0, meta='enchanted'):
            make(NBT); store(7); put(7, NBT, 'count', 'I', lambda: num(amount))
            put(7, NBT, 'id', 'Ljava/lang/String;', lambda: string('test:relic'))
            put(7, NBT, 'meta', 'Ljava/lang/String;', lambda: string(meta))
            if invalid: put(7, NBT, 'invalid', 'I', lambda: num(invalid))
            load(6); load(7); a.op(0xb6, cp.method(LIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
        def emeralds(amount):
            make(IT); store(14); put(14, IT, 'count', 'I', lambda: num(amount))
            put(14, IT, 'kind', 'L' + ITEM + ';', lambda: a.op(0xb2, cp.field(ITEMS, 'EMERALD', 'L' + ITEM + ';')))
            make(ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(14)); store(8)
            get(2, SL, 'altarItems', 'Ljava/util/List;'); load(8)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def other(own=True, x=5, localnum=13):
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(localnum)
            if own:
                load(localnum); load(3); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
            else:
                make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(2), load(11), num(10))); store(15)
                load(localnum); load(15); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
            setxyz(localnum, x)
            get(2, SL, 'near', 'Ljava/util/List;'); load(localnum)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def altar():
            load(2); load(5); a.op(0xb8, cp.method(PM, 'tryReviveAtAltar', '(L' + SL + ';L' + BLOCK + ';)Z')); a.raw(0x57)
        def direct():
            make(MIXIN); store(12); load(12); load(4); load(1); load(2)
            load(1); a.op(0xb8, cp.method(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';'))
            load(3); load(5)
            desc = '(L' + C + ';L' + SERVER + ';L' + SL + ';L' + PD + ';L' + SP + ';L' + BLOCK + ';)V'
            a.op(0xb6, cp.method(MIXIN, 'settledlands$reviveOnlyFromQueue', desc))
        def report(label):
            val(label + '.x', 'D', lambda: get(3, E, 'x', 'D'))
            val(label + '.y', 'D', lambda: get(3, E, 'y', 'D'))
            val(label + '.z', 'D', lambda: get(3, E, 'z', 'D'))
            val(label + '.removed', 'Z', lambda: get(4, E, 'removed', 'Z'))
            val(label + '.discard_count', 'I', lambda: get(4, E, 'discardCount', 'I'))
            val(label + '.items', 'I', lambda: (inventory(), a.op(0xb4, cp.field(INV, 'collected', 'I'))))
            val(label + '.meta', 'Ljava/lang/String;', lambda: (inventory(), a.op(0xb4, cp.field(INV, 'lastMeta', 'Ljava/lang/String;'))))
            val(label + '.corpse_key', 'Z', lambda: contains(4, p.CORPSE_ITEMS))
            val(label + '.player_key', 'Z', lambda: contains(3, p.PLAYER_ITEMS))
            val(label + '.lead_drops', 'I', lambda: get(4, G, 'leadDrops', 'I'))
            val(label + '.queued', 'Z', lambda: (load(1), a.op(0xb8, cp.method(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';')),
                load(3), a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;')),
                a.op(0xb6, cp.method(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z'))))
            val(label + '.emerald_removed', 'Z', lambda: get(8, E, 'removed', 'Z'))
            val(label + '.emerald_count', 'I', lambda: itemfield('count', 'I'))
        def tick():
            make(POST, '(L' + SERVER + ';)V', lambda: load(1)); a.op(0xb8, cp.method(POSS, 'onServerTick', '(L' + POST + ';)V'))
        def leash():
            put(4, G, 'leashed', 'Z', lambda: boolean(True)); put(4, G, 'leashHolder', 'L' + E + ';', lambda: load(3))
        count_labels = iter(range(1000))
        def first_count(index, key):
            n = next(count_labels); empty = 'count_empty_' + str(n); done = 'count_done_' + str(n)
            data(index); string(key); num(10); a.op(0xb6, cp.method(NBT, 'getList', '(Ljava/lang/String;I)L' + LIST + ';'))
            store(18); load(18); a.op(0xb6, cp.method(LIST, 'isEmpty', '()Z')); a.jump(0x9a, empty)
            load(18); num(0); a.op(0xb6, cp.method(LIST, 'getCompound', '(I)L' + NBT + ';'))
            a.op(0xb4, cp.field(NBT, 'count', 'I')); a.jump(0xa7, done)
            a.label(empty); a.raw(0x02); a.label(done)
        def drops(amount, possessed=True):
            boolean(possessed); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
            make('java/util/ArrayList'); store(16); make(IT); store(14)
            put(14, IT, 'count', 'I', lambda: num(amount)); put(14, IT, 'id', 'Ljava/lang/String;', lambda: string('test:drop'))
            put(14, IT, 'meta', 'Ljava/lang/String;', lambda: string('new-metadata'))
            make(ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(14)); store(17)
            load(16); load(17); a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
            make(r.DROP_EVENT, '(L' + r.LIVING + ';Ljava/util/Collection;)V', lambda: (load(3), load(16)))
            a.op(0xb8, cp.method(POSS, 'onPlayerDrops', '(L' + r.DROP_EVENT + ';)V'))
            boolean(False); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
        def dropcount(label):
            val(label, 'I', lambda: (load(16), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))

        # General altar path: actual queue, 9-emerald code, exact owner and loot.
        env(); list_at(); stored(5); emeralds(12); altar(); report('general')
        tick(); val('general.model_restored', 'Z', lambda: flagvalue(3, 'settledlands_possess'))
        val('general.one_shot_marker', 'Z', lambda: flagvalue(3, p.RESTORE))
        emeralds(9); altar(); report('general_repeat')
        # Exact direct coffin: another owned body nearby must not be selected.
        env(coffin=True); list_at(); stored(7); other(own=True, x=5); leash(); emeralds(9); direct(); report('direct')
        val('direct.other_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        emeralds(9); direct(); report('direct_repeat')
        # A partial inventory keeps the exact corpse and its lead; retry via the
        # unchanged real recovery helper after making room.
        env(room=2, coffin=True); list_at(); stored(5); leash(); emeralds(9); direct(); report('partial')
        val('partial.remaining', 'I', lambda: get(7, NBT, 'count', 'I'))
        make(MIXIN); store(12); load(12); load(4)
        a.op(0xb6, cp.method(MIXIN, 'settledlands$keepBodyCorpse', '(L' + C + ';)V'))
        val('partial.kept_tick', 'Z', lambda: get(4, E, 'removed', 'Z'))
        inventory(); num(20); a.op(0xb5, cp.field(INV, 'room', 'I'))
        load(3); load(4); a.op(0xb8, cp.method(r.H, 'tryClaim', '(L' + r.P + ';L' + E + ';)V'))
        report('retry')
        # Invalid CODEC data stays on the body, no duplicate on repeated claim.
        env(); list_at(); stored(3, invalid=1); emeralds(9); altar(); report('invalid')
        load(3); load(4); a.op(0xb8, cp.method(r.H, 'tryClaim', '(L' + r.P + ';L' + E + ';)V'))
        val('invalid.after_retry', 'Z', lambda: get(4, E, 'removed', 'Z'))
        # Corrupt root tag (not a list) must not be mistaken for empty loot.
        env(); data(4); string(p.CORPSE_ITEMS); make(NBT)
        a.op(0xb6, cp.method(NBT, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';')); a.raw(0x57)
        emeralds(9); altar(); report('invalid_root')
        # An empty body and an explicitly empty list both disappear after ritual.
        env(coffin=True); leash(); emeralds(9); altar(); report('empty')
        env(); list_at(); emeralds(9); altar(); report('empty_list')
        # Eight emeralds cannot revive or remove the initiating body, in
        # either the direct coffin path or the general altar path.
        env(coffin=True); list_at(); stored(4); emeralds(8); direct(); report('insufficient')
        env(); list_at(); stored(4); emeralds(8); altar(); report('general_insufficient')
        # The real general ritual accepts exactly nine across two stacks.
        env(); list_at(); stored(4); emeralds(4); load(8); store(17); emeralds(5); altar(); report('split_cost')
        val('split_cost.first_removed', 'Z', lambda: get(17, E, 'removed', 'Z'))
        # Two different owners in the queue: the unchanged altar call polls
        # the first, leaves the second queued, then handles the second.
        env(); list_at(); stored(5)
        make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(2), load(11), num(30))); store(15)
        load(15); a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;')); a.op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;'))
        make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(13)
        load(13); load(15); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V')); setxyz(13, 6)
        data(15); string(p.DEATH_CORPSE); load(13); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        get(2, SL, 'near', 'Ljava/util/List;'); load(13)
        r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        emeralds(9); altar(); report('fifo_first')
        val('fifo.second_still_queued', 'Z', lambda: (load(1), a.op(0xb8, cp.method(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';')),
            load(15), a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;')),
            a.op(0xb6, cp.method(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z'))))
        val('fifo.second_before_x', 'D', lambda: get(15, E, 'x', 'D'))
        put(9, PLAYERLIST, 'online', 'L' + SP + ';', lambda: load(15)); emeralds(9); altar()
        val('fifo.second_after_x', 'D', lambda: get(15, E, 'x', 'D'))
        val('fifo.second_body_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        val('fifo.empty_after', 'Z', lambda: (load(1), a.op(0xb8, cp.method(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';')),
            a.op(0xb6, cp.method(PD, 'hasDeadPlayers', '()Z'))))
        # Direct body not in queue does not consume nine emeralds.
        env(coffin=True); list_at(); stored(4); emeralds(9); a.raw(0x01)
        a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;')); direct(); report('not_queued')
        # The general queue path never takes a foreign corpse.
        env(); get(2, SL, 'near', 'Ljava/util/List;'); load(4)
        r.p.invokeinterface(a, cp, 'java/util/List', 'remove', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        other(own=False, x=6); emeralds(9); altar(); report('foreign')
        val('foreign.other_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        # Even a forged/malformed saved association to a different owner must
        # not teleport there or recover their loot.
        env(); get(2, SL, 'near', 'Ljava/util/List;'); load(4)
        r.p.invokeinterface(a, cp, 'java/util/List', 'remove', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        other(own=False, x=6); list_at(13); stored(5)
        data(3); string(p.DEATH_CORPSE); load(13); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        emeralds(9); altar(); report('foreign_spoof')
        val('foreign_spoof.other_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        val('foreign_spoof.other_items', 'Z', lambda: contains(13, p.CORPSE_ITEMS))
        # Two same-owner bodies: the recorded ID selects the current one.
        env(); list_at(); stored(5); other(own=True, x=5); emeralds(9); altar(); report('ambiguous')
        val('ambiguous.other_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        # An older single same-owner body is never guessed for a legacy player.
        env(); list_at(); stored(5); data(3); string(p.DEATH_CORPSE)
        a.op(0xb6, cp.method(NBT, 'remove', '(Ljava/lang/String;)V')); emeralds(9); altar(); report('legacy')
        # A different directly-triggering old coffin is not the current death.
        env(coffin=True); list_at(); stored(5); other(own=True, x=5)
        data(3); string(p.DEATH_CORPSE); load(13); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        emeralds(9); direct(); report('old_direct')
        val('old_direct.other_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        # UUID lookup has no arbitrary distance limit, but only loaded entities.
        env(x=20); list_at(); stored(5); emeralds(9); altar(); report('far')
        env(x=20); list_at(); stored(5); get(2, SL, 'near', 'Ljava/util/List;'); load(4)
        r.p.invokeinterface(a, cp, 'java/util/List', 'remove', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        emeralds(9); altar(); report('unloaded')
        # Teleport failure cannot cause item delivery or body loss.
        env(); list_at(); stored(5); put(3, SP, 'failTeleport', 'Z', lambda: boolean(True))
        emeralds(9); altar(); report('teleport_failed')
        # A captured list still on the player is adopted once by the selected
        # body before the strict claim (late body spawn), not legacy restore.
        env(); list_at(3, p.PLAYER_ITEMS); stored(4)
        data(3); string(p.ITEMS_CORPSE); load(4); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        emeralds(9); altar(); report('pending_items')
        # A still-pending list from an earlier death is not relabelled as the
        # new body's items: preserve it, the body, and any ordinary new drops.
        env(); list_at(3, p.PLAYER_ITEMS); stored(7); other(own=True, x=0)
        data(3); string(p.ITEMS_CORPSE); load(13); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        emeralds(9); altar(); report('old_pending')
        val('old_pending.other_removed', 'Z', lambda: get(13, E, 'removed', 'Z'))
        tick(); val('old_pending.guard', 'Z', lambda: flagvalue(3, p.SKIP_LEGACY))
        # Offline player: existing ritual schedules a pending target. After login
        # the actual onPlayerLogin resolves the same saved body and recovers loot.
        env(); list_at(); stored(6); emeralds(9); put(9, PLAYERLIST, 'online', 'L' + SP + ';', lambda: a.raw(0x01))
        altar(); val('offline.pending', 'Z', lambda: (a.op(0xb2, cp.field(PD, 'pending', 'L' + TARGET + ';')),
            a.op(0xb8, cp.method('java/util/Objects', 'nonNull', '(Ljava/lang/Object;)Z'))))
        val('offline.body_before', 'Z', lambda: get(4, E, 'removed', 'Z'))
        put(9, PLAYERLIST, 'online', 'L' + SP + ';', lambda: load(3))
        make(LOGIN, '(L' + r.P + ';)V', lambda: load(3)); a.op(0xb8, cp.method(PM, 'onPlayerLogin', '(L' + LOGIN + ';)V'))
        report('offline.login')
        # Ordinary deaths retain vanilla drops; the selected empty body simply
        # determines the ritual's spawn point (drop capture is unchanged).
        env(marked=False); emeralds(9); altar(); report('ordinary')
        # Conflicting old player/body lists: claim neither, preserve both; the
        # real model tick must not call the lossy legacy parser afterwards.
        env(); list_at(); stored(5); list_at(3, p.PLAYER_ITEMS); stored(4); emeralds(9); altar(); report('conflict')
        tick(); val('conflict.after_tick_player_key', 'Z', lambda: contains(3, p.PLAYER_ITEMS))
        val('conflict.after_tick_body_key', 'Z', lambda: contains(4, p.CORPSE_ITEMS))
        val('conflict.after_tick_removed', 'Z', lambda: get(4, E, 'removed', 'Z'))
        val('conflict.guard_persistent', 'Z', lambda: flagvalue(3, p.SKIP_LEGACY))
        make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(11), num(30))); store(15)
        make(CLONE, '(L' + r.P + ';L' + r.P + ';)V', lambda: (load(3), load(15)))
        a.op(0xb8, cp.method(POSS, 'onPlayerClone', '(L' + CLONE + ';)V'))
        val('conflict.clone_guard', 'Z', lambda: flagvalue(15, p.SKIP_LEGACY))
        val('conflict.clone_items', 'Z', lambda: contains(15, p.PLAYER_ITEMS))
        # A legacy direct coffin really is the initiator even without a saved ID.
        env(coffin=True); list_at(); stored(4); data(3); string(p.DEATH_CORPSE)
        a.op(0xb6, cp.method(NBT, 'remove', '(Ljava/lang/String;)V')); emeralds(9); direct(); report('legacy_direct')
        # Actual stock death creation and the actual NeoForge clone handler.
        # A second failed spawn must clear the former death's ID.
        env(); put(3, E, 'world', 'L' + L + ';', lambda: load(2))
        make(DEATH, '(L' + r.LIVING + ';)V', lambda: load(3)); a.op(0xb8, cp.method(p.LIFECYCLE, 'onPlayerDeath', '(L' + DEATH + ';)V'))
        get(2, SL, 'near', 'Ljava/util/List;'); num(1)
        r.p.invokeinterface(a, cp, 'java/util/List', 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(C)); store(13)
        val('birth.exact_id', 'Z', lambda: (load(13), a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;')),
            data(3), string(p.DEATH_CORPSE), a.op(0xb6, cp.method(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;')),
            a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))))
        make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(11), num(30))); store(15)
        make(CLONE, '(L' + r.P + ';L' + r.P + ';)V', lambda: (load(3), load(15)))
        a.op(0xb8, cp.method(POSS, 'onPlayerClone', '(L' + CLONE + ';)V'))
        val('birth.clone_id', 'Z', lambda: (load(13), a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;')),
            data(15), string(p.DEATH_CORPSE), a.op(0xb6, cp.method(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;')),
            a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))))
        put(2, SL, 'failSpawn', 'Z', lambda: boolean(True))
        make(DEATH, '(L' + r.LIVING + ';)V', lambda: load(3)); a.op(0xb8, cp.method(p.LIFECYCLE, 'onPlayerDeath', '(L' + DEATH + ';)V'))
        val('birth.failed_clears_old', 'Z', lambda: contains(3, p.DEATH_CORPSE))
        make(CLONE, '(L' + r.P + ';L' + r.P + ';)V', lambda: (load(3), load(15)))
        a.op(0xb8, cp.method(POSS, 'onPlayerClone', '(L' + CLONE + ';)V'))
        val('birth.failed_clone_clears_old', 'Z', lambda: contains(15, p.DEATH_CORPSE))
        # Actual LivingDrops handler with two same-owner bodies: prior body
        # comes first in entity iteration; 240 would overwrite its item list.
        env(); put(3, E, 'world', 'L' + L + ';', lambda: load(2)); other(own=True, x=0)
        get(2, SL, 'near', 'Ljava/util/List;'); load(4)
        r.p.invokeinterface(a, cp, 'java/util/List', 'remove', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        get(2, SL, 'near', 'Ljava/util/List;'); load(4)
        r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        list_at(13); stored(9); drops(4); dropcount('capture.world_drops')
        val('capture.old_count', 'I', lambda: first_count(13, p.CORPSE_ITEMS))
        val('capture.current_has', 'Z', lambda: contains(4, p.CORPSE_ITEMS))
        # If even the exact current body already has a list, never overwrite
        # it: leave the new list on player with copied, explicit provenance.
        env(); put(3, E, 'world', 'L' + L + ';', lambda: load(2)); list_at(); stored(9); drops(4)
        val('capture.collision_body_count', 'I', lambda: first_count(4, p.CORPSE_ITEMS))
        val('capture.collision_player_count', 'I', lambda: first_count(3, p.PLAYER_ITEMS))
        val('capture.collision_bound', 'Z', lambda: (load(4), a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;')),
            data(3), string(p.ITEMS_CORPSE), a.op(0xb6, cp.method(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;')),
            a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))))
        make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(11), num(30))); store(15)
        make(CLONE, '(L' + r.P + ';L' + r.P + ';)V', lambda: (load(3), load(15)))
        a.op(0xb8, cp.method(POSS, 'onPlayerClone', '(L' + CLONE + ';)V'))
        val('capture.collision_clone_bound', 'Z', lambda: (load(4), a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;')),
            data(15), string(p.ITEMS_CORPSE), a.op(0xb6, cp.method(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;')),
            a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))))
        # Existing unresolved player data prevents a later death overwriting it;
        # new drops remain vanilla, and ordinary drops are unchanged as well.
        env(); put(3, E, 'world', 'L' + L + ';', lambda: load(2)); list_at(3, p.PLAYER_ITEMS); stored(7)
        drops(4); dropcount('capture.pending_world_drops')
        val('capture.pending_count', 'I', lambda: first_count(3, p.PLAYER_ITEMS))
        env(marked=False); put(3, E, 'world', 'L' + L + ';', lambda: load(2)); drops(4, possessed=False)
        dropcount('capture.ordinary_world_drops'); val('capture.ordinary_key', 'Z', lambda: contains(3, p.PLAYER_ITEMS))
        a.raw(0xb1); return a.finish()
    return f.fake_class('AltarHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 18, 19, main)])


def run_fixture(java, jar):
    with tempfile.TemporaryDirectory(prefix='altar-241-jvm-') as td:
        folder = pathlib.Path(td); classes = build_classes(jar); classes['AltarHarness'] = harness_class()
        for name, data in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        proc = subprocess.run([java, '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'AltarHarness'],
                              text=True, capture_output=True)
        if proc.returncode:
            raise RuntimeError(f'JVM altar fixture failed ({jar}):\n{proc.stdout}\n{proc.stderr}')
        return dict(line.split('=', 1) for line in proc.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--base240', required=True)
    ap.add_argument('--jar', required=True); args = ap.parse_args()
    old = run_fixture(args.java, args.base240)
    assert old['general.x'] == '0.5' and old['general.items'] == '0' and old['general.removed'] == 'false', old
    assert old['direct.x'] == '0.5' and old['direct.player_key'] == 'true', old
    assert old['birth.exact_id'] == 'false' and old['capture.old_count'] == '4', old
    assert old['capture.pending_world_drops'] == '0' and old['capture.pending_count'] == '-1', old
    print('240 negative control: altar location, legacy hand-off and unsafe same-owner capture reproduced')
    got = run_fixture(args.java, args.jar)
    expected = {
        'general.x': '8.0', 'general.y': '64.0', 'general.z': '2.0', 'general.removed': 'true',
        'general.items': '5', 'general.meta': 'enchanted', 'general.corpse_key': 'false',
        'general.queued': 'false', 'general.emerald_removed': 'false', 'general.emerald_count': '3',
        'general.model_restored': 'true', 'general.one_shot_marker': 'false',
        'general_repeat.items': '5', 'general_repeat.discard_count': '1',
        'general_repeat.emerald_removed': 'false', 'general_repeat.emerald_count': '9',
        'direct.x': '8.0', 'direct.y': '64.0', 'direct.removed': 'true', 'direct.items': '7',
        'direct.player_key': 'false', 'direct.lead_drops': '1', 'direct.emerald_removed': 'true',
        'direct.other_removed': 'false', 'direct.queued': 'false',
        'direct_repeat.items': '7', 'direct_repeat.discard_count': '1', 'direct_repeat.lead_drops': '1',
        'direct_repeat.emerald_removed': 'false', 'direct_repeat.emerald_count': '9',
        'partial.x': '8.0', 'partial.removed': 'false', 'partial.items': '2', 'partial.corpse_key': 'true',
        'partial.lead_drops': '0', 'partial.remaining': '3', 'partial.kept_tick': 'false',
        'retry.items': '5', 'retry.removed': 'true', 'retry.lead_drops': '1', 'retry.discard_count': '1',
        'invalid.removed': 'false', 'invalid.items': '0', 'invalid.corpse_key': 'true', 'invalid.after_retry': 'false',
        'invalid_root.removed': 'false', 'invalid_root.items': '0', 'invalid_root.corpse_key': 'true',
        'empty.removed': 'true', 'empty.items': '0', 'empty.lead_drops': '1', 'empty_list.removed': 'true',
        'insufficient.queued': 'true', 'insufficient.emerald_count': '8', 'insufficient.emerald_removed': 'false',
        'insufficient.removed': 'false', 'insufficient.items': '0',
        'general_insufficient.queued': 'true', 'general_insufficient.emerald_removed': 'false',
        'general_insufficient.emerald_count': '8', 'general_insufficient.removed': 'false', 'general_insufficient.items': '0',
        'split_cost.items': '4', 'split_cost.removed': 'true', 'split_cost.emerald_removed': 'true', 'split_cost.first_removed': 'true',
        'fifo_first.x': '8.0', 'fifo_first.items': '5', 'fifo_first.removed': 'true',
        'fifo.second_still_queued': 'true', 'fifo.second_before_x': '0.0', 'fifo.second_after_x': '6.0',
        'fifo.second_body_removed': 'true', 'fifo.empty_after': 'false',
        'not_queued.emerald_count': '9', 'not_queued.emerald_removed': 'false', 'not_queued.removed': 'false',
        'foreign.x': '0.5', 'foreign.y': '65.05', 'foreign.other_removed': 'false', 'foreign.items': '0',
        'foreign_spoof.x': '0.5', 'foreign_spoof.items': '0', 'foreign_spoof.other_removed': 'false', 'foreign_spoof.other_items': 'true',
        'ambiguous.x': '8.0', 'ambiguous.removed': 'true', 'ambiguous.other_removed': 'false', 'ambiguous.items': '5',
        'legacy.x': '0.5', 'legacy.removed': 'false', 'legacy.items': '0',
        'old_direct.x': '0.5', 'old_direct.removed': 'false', 'old_direct.other_removed': 'false', 'old_direct.items': '0',
        'far.x': '20.0', 'far.removed': 'true', 'far.items': '5',
        'unloaded.x': '0.5', 'unloaded.removed': 'false', 'unloaded.items': '0',
        'teleport_failed.removed': 'false', 'teleport_failed.items': '0', 'teleport_failed.corpse_key': 'true',
        'pending_items.items': '4', 'pending_items.player_key': 'false', 'pending_items.removed': 'true',
        'old_pending.x': '8.0', 'old_pending.items': '0', 'old_pending.player_key': 'true',
        'old_pending.removed': 'false', 'old_pending.other_removed': 'false', 'old_pending.guard': 'true',
        'offline.pending': 'true', 'offline.body_before': 'false', 'offline.login.x': '8.0',
        'offline.login.items': '6', 'offline.login.removed': 'true',
        'ordinary.x': '8.0', 'ordinary.removed': 'true', 'ordinary.items': '0',
        'conflict.items': '0', 'conflict.removed': 'false', 'conflict.player_key': 'true', 'conflict.corpse_key': 'true',
        'conflict.after_tick_player_key': 'true', 'conflict.after_tick_body_key': 'true', 'conflict.after_tick_removed': 'false',
        'conflict.guard_persistent': 'true', 'conflict.clone_guard': 'true', 'conflict.clone_items': 'true',
        'legacy_direct.x': '8.0', 'legacy_direct.items': '4', 'legacy_direct.removed': 'true',
        'birth.exact_id': 'true', 'birth.clone_id': 'true', 'birth.failed_clears_old': 'false',
        'birth.failed_clone_clears_old': 'false', 'capture.world_drops': '0', 'capture.old_count': '9',
        'capture.current_has': 'true', 'capture.collision_body_count': '9', 'capture.collision_player_count': '4',
        'capture.collision_bound': 'true', 'capture.collision_clone_bound': 'true',
        'capture.pending_world_drops': '1', 'capture.pending_count': '7',
        'capture.ordinary_world_drops': '1', 'capture.ordinary_key': 'false',
    }
    bad = {k: (got.get(k), v) for k, v in expected.items() if got.get(k) != v}
    if bad: raise AssertionError(f'mismatched={bad}\nall={got}')
    print('241 JVM execution: real 9-emerald ritual and direct coffin path -> own body position -> safe claim;')
    print('queue/price, exact initiator, partial/invalid/empty, owner/ambiguous/range, leads, offline and model OK')
    print('Synthetic dependencies and visual stubs; not a NeoForge/in-game test.')


if __name__ == '__main__': main()
