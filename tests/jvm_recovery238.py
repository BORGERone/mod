#!/usr/bin/env python3
"""Behavioral fixture for build 238 on a real JVM, not a Minecraft test.

The JVM executes the candidate's original recovery class and unmodified Code of
its corpse, two event handlers, possessed drop-capture/move and purgatory-exit
methods. Minecraft/NeoForge/DFU dependencies are small executable doubles with
explicitly modeled inventories, codec failures, UUIDs and NBT. No Java compiler.
"""
import argparse
import pathlib
import subprocess
import sys
import tempfile
import zipfile

import jvm_corpse_load as f
from patch_earlytex_mixin import u2, u4, attr
from patch_probe233 import Asm
import patch_corpse_reclaim238 as p

C, G, H, IVY = p.CORPSE, 'dev/settledlands/npc/GuideNpc', p.HELPER, p.IVY
E, P, SP = p.ENTITY, p.PLAYER, p.SERVER_PLAYER
L, SL, SERVER = p.LEVEL, p.SERVER_LEVEL, p.SERVER
TAG, NBT, LIST = p.TAG, p.COMPOUND, p.LISTTAG
EXT, IT, ITEM, INV = p.EXT, p.STACK, 'net/minecraft/world/item/Item', p.INVENTORY
ITEMS, CONTAINER = 'net/minecraft/world/item/Items', 'net/minecraft/world/Container'
SED, ACCESSOR, SERIALIZER, SERIALIZERS = f.SED, f.ACCESSOR, f.SERIALIZER, f.SERIALIZERS
HOLDER, ATTR, ATTRS = p.HOLDER, p.ATTRIBUTE_INSTANCE, p.ATTRIBUTES
PROVIDER, REGISTRY, REGOPS, OPS, NBTO = p.PROVIDER, p.REGISTRY, p.REGISTRY_OPS, p.DYNAMIC_OPS, p.NBT_OPS
CODEC, RESULT = p.CODEC, p.DATA_RESULT
DECODER = 'com/mojang/serialization/Decoder'
FAKE_REG, FAKE_CODEC, FAKE_RESULT = 'fixture/Registry', 'fixture/Codec', 'fixture/Result'
KEY, PD, PM = p.RESOURCE_KEY, p.PURGATORY_DATA, p.PURGATORY
COMP, MUT = p.COMPONENT, p.MUTABLE
CONTENTS, TRANS = p.COMPONENT_CONTENTS, p.TRANSLATABLE_CONTENTS
TYPE = f.TYPE
EVENT_S = p.EVENTS['onInteractSpecificCorpse']
EVENT_G = p.EVENTS['onInteractCorpse']
PLAYER_EVENT = 'net/neoforged/neoforge/event/entity/player/PlayerEvent'
INTERACT_EVENT = 'net/neoforged/neoforge/event/entity/player/PlayerInteractEvent'
DIM_EVENT = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerChangedDimensionEvent'
MOB_EFFECTS = 'net/minecraft/world/effect/MobEffects'
HAND, INTERACTION, ABILITIES = ('net/minecraft/world/InteractionHand', 'net/minecraft/world/InteractionResult',
                                'net/minecraft/world/entity/player/Abilities')
SOUNDS, SOUND, FAKE_HOLDER = ('net/minecraft/sounds/SoundEvents', 'net/minecraft/sounds/SoundEvent', 'fixture/Holder')
DAMAGE, DAMAGE_TYPES = ('net/minecraft/world/damagesource/DamageSource', 'net/minecraft/world/damagesource/DamageTypes')
POSS = 'dev/settledlands/npc/PlayerCorpsePossession'
POSS_SYS = 'dev/settledlands/npc/PossessionSystem'
DROP_EVENT = 'net/neoforged/neoforge/event/entity/living/LivingDropsEvent'
LIVING = 'net/minecraft/world/entity/LivingEntity'
ITEM_ENTITY = 'net/minecraft/world/entity/item/ItemEntity'
VEC = 'net/minecraft/world/phys/Vec3'
AABB = 'net/minecraft/world/phys/AABB'
LOGGER, FLOGGER, LOGUTILS = 'org/slf4j/Logger', 'fixture/Logger', 'com/mojang/logging/LogUtils'
TOOLS = [f'dev/settledlands/npc/{x}' for x in ('NpcWandItem', 'MobilityWandItem', 'CutsceneWandItem',
                                                'CoordStickItem', 'GuideTomeItem', 'CoffinItem')]


def op(code, idx):
    return f.op(code, idx)


def insn_local(a, name, i):
    p.local(a, name, i)


def const(a, n):
    if -1 <= n <= 5:
        a.raw(0x03 + n)
    elif -128 <= n <= 127:
        a.raw(0x10, n & 255)
    else:
        assert -32768 <= n <= 32767
        a.raw(0x11, (n >> 8) & 255, n & 255)


def text(a, cp, s):
    a.raw(f.ldc(cp.string(s)))


def obj(a, cp, name, ctor='()V', args=None):
    a.op(0xbb, cp.cls(name)); a.raw(0x59)
    if args:
        args()
    a.op(0xb7, cp.method(name, '<init>', ctor))


def static_field(a, cp, owner, name, desc):
    a.op(0xb2, cp.field(owner, name, desc))


def getfield(owner, name, desc, ret=0xb0):
    return lambda cp: b'\x2a' + op(0xb4, cp.field(owner, name, desc)) + bytes([ret])


def setter(owner, name, desc, load=0x2b):
    return lambda cp: b'\x2a' + bytes([load]) + op(0xb5, cp.field(owner, name, desc)) + b'\xb1'


def interface(name, methods=(), parents=()):
    return f.fake_class(name, [], list(methods), access=0x0601, major=52, interfaces=parents)


def abstract(name, desc):
    return (0x0401, name, desc, 0, 1, None)


def subset(jar, name, names, *, expose=(), fields=None, add_clinit=None):
    with zipfile.ZipFile(jar) as z:
        raw = z.read(name + '.class')
    k = f.classedit.Klass(raw)
    selected = [dict(m) for m in k.methods if m['name'] in names]
    found = {m['name'] for m in selected}
    assert set(names) - {'settledlands$getOwnerUuid'} <= found, (name, found, names)
    for m in selected:
        if m['name'] in expose:
            m['acc'] = (m['acc'] & ~0x0006) | 0x0001  # expose private/protected only in fixture
    additions = []
    if add_clinit is not None:
        code = add_clinit(k)
        body = u2(3) + u2(0) + u4(len(code)) + code + u2(0) + u2(0)
        additions.append(u2(8) + u2(k.add_utf8('<clinit>')) + u2(k.add_utf8('()V')) + u2(1) +
                         u2(k.add_utf8('Code')) + u4(len(body)) + body)
    all_fields = k.fields if fields is None else [x for x in k.fields if x['name'] in fields]
    if fields is not None:
        assert {x['name'] for x in all_fields} == set(fields)
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in k.attrs)
    return (raw[:8] + u2(len(k.cp)) + k._pool_bytes() + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) +
            u2(len(all_fields)) + b''.join(f.encode_member(x) for x in all_fields) +
            u2(len(selected) + len(additions)) + b''.join(f.encode_member(x) for x in selected) +
            b''.join(additions) + u2(len(k.attrs)) + attrs)


def nbt_class():
    fields = [(1, 'map', 'Ljava/util/HashMap;'), (1, 'id', 'Ljava/lang/String;'),
              (1, 'meta', 'Ljava/lang/String;'), (1, 'count', 'I'), (1, 'invalid', 'I'), (1, 'damaged', 'Z'),
              (1, 'coffin', 'Z'), (1, 'owner', 'Ljava/util/UUID;'), (1, 'ownerName', 'Ljava/lang/String;'),
              (1, 'loadedMax', 'D'), (1, 'loadedHealth', 'F')]
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a); obj(a, cp, 'java/util/HashMap'); a.op(0xb5, cp.field(NBT, 'map', 'Ljava/util/HashMap;'))
        a.raw(0x2a); text(a, cp, ''); a.op(0xb5, cp.field(NBT, 'ownerName', 'Ljava/lang/String;'))
        a.raw(0x2a, 0x10, 20, 0x87); a.op(0xb5, cp.field(NBT, 'loadedMax', 'D'))
        a.raw(0x2a, 0x10, 10, 0x86); a.op(0xb5, cp.field(NBT, 'loadedHealth', 'F'))
        a.raw(0xb1); return a.finish()
    def contains(cp):
        return (b'\x2a' + op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, cp.method('java/util/HashMap', 'containsKey', '(Ljava/lang/Object;)Z')) + b'\xac')
    def put(cp):
        return (b'\x2a' + op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b\x2c' +
                op(0xb6, cp.method('java/util/HashMap', 'put', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;')) +
                op(0xc0, cp.cls(TAG)) + b'\xb0')
    def get_list(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;'))
        a.raw(0x4e, 0x2d); a.op(0xc1, cp.cls(LIST)); a.jump(0x99, 'empty')
        a.raw(0x2d); a.op(0xc0, cp.cls(LIST)); a.raw(0xb0)
        a.label('empty'); obj(a, cp, LIST); a.raw(0xb0); return a.finish()
    def remove(cp):
        return (b'\x2a' + op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, cp.method('java/util/HashMap', 'remove', '(Ljava/lang/Object;)Ljava/lang/Object;')) + b'\x57\xb1')
    def has_uuid(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(NBT, 'owner', 'Ljava/util/UUID;'))
        a.jump(0xc6, 'no'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    methods = [(1, '<init>', '()V', 4, 1, ctor),
               (1, 'contains', '(Ljava/lang/String;)Z', 2, 2, contains),
               (1, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';', 3, 3, put),
               (1, 'getList', '(Ljava/lang/String;I)L' + LIST + ';', 2, 4, get_list),
               (1, 'remove', '(Ljava/lang/String;)V', 2, 2, remove),
               (1, 'putInt', '(Ljava/lang/String;I)V', 2, 3, setter(NBT, 'count', 'I', 0x1c)),
               (1, 'getBoolean', '(Ljava/lang/String;)Z', 1, 2, getfield(NBT, 'coffin', 'Z', 0xac)),
               (1, 'hasUUID', '(Ljava/lang/String;)Z', 1, 2, has_uuid),
               (1, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;', 1, 2, getfield(NBT, 'owner', 'Ljava/util/UUID;')),
               (1, 'getString', '(Ljava/lang/String;)Ljava/lang/String;', 1, 2, getfield(NBT, 'ownerName', 'Ljava/lang/String;'))]
    return f.fake_class(NBT, fields, methods, interfaces=[TAG])


def list_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a); obj(a, cp, 'java/util/ArrayList'); a.op(0xb5, cp.field(LIST, 'values', 'Ljava/util/ArrayList;'))
        a.raw(0xb1); return a.finish()
    def delegate(name, desc, ret, cast=None):
        def generate(cp):
            b = b'\x2a' + op(0xb4, cp.field(LIST, 'values', 'Ljava/util/ArrayList;'))
            if name in ('add', 'get', 'remove'):
                b += b'\x2b' if name == 'add' else b'\x1b'
            b += op(0xb6, cp.method('java/util/ArrayList', name, desc))
            if cast:
                b += op(0xc0, cp.cls(cast))
            return b + bytes([ret])
        return generate
    def copy(cp):
        a = Asm(); obj(a, cp, LIST); a.raw(0x4c, 0x2b); a.op(0xb4, cp.field(LIST, 'values', 'Ljava/util/ArrayList;'))
        a.raw(0x2a); a.op(0xb4, cp.field(LIST, 'values', 'Ljava/util/ArrayList;'))
        a.op(0xb6, cp.method('java/util/ArrayList', 'addAll', '(Ljava/util/Collection;)Z')); a.raw(0x57, 0x2b, 0xb0)
        return a.finish()
    return f.fake_class(LIST, [(1, 'values', 'Ljava/util/ArrayList;')], [
        (1, '<init>', '()V', 3, 1, ctor),
        (1, 'add', '(Ljava/lang/Object;)Z', 2, 2, delegate('add', '(Ljava/lang/Object;)Z', 0xac)),
        (1, 'isEmpty', '()Z', 1, 1, delegate('isEmpty', '()Z', 0xac)),
        (1, 'size', '()I', 1, 1, delegate('size', '()I', 0xac)),
        (1, 'getCompound', '(I)L' + NBT + ';', 2, 2, delegate('get', '(I)Ljava/lang/Object;', 0xb0, NBT)),
        (1, 'remove', '(I)L' + TAG + ';', 2, 2, delegate('remove', '(I)Ljava/lang/Object;', 0xb0, TAG)),
        (1, 'copy', '()L' + LIST + ';', 2, 2, copy),
    ], interfaces=[TAG])


def entity_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a); obj(a, cp, NBT); a.op(0xb5, cp.field(E, 'data', 'L' + NBT + ';'))
        a.raw(0xb1); return a.finish()
    def discard(cp):
        count = cp.field(E, 'discardCount', 'I')
        return (b'\x2a\x04' + op(0xb5, cp.field(E, 'removed', 'Z')) +
                b'\x2a\x59' + op(0xb4, count) + b'\x04\x60' + op(0xb5, count) + b'\xb1')
    return f.fake_class(E, [(1, 'data', 'L' + NBT + ';'), (1, 'world', 'L' + L + ';'),
                             (1, 'removed', 'Z'), (1, 'discardCount', 'I')], [
        (1, '<init>', '()V', 3, 1, ctor),
        (1, 'getPersistentData', '()L' + NBT + ';', 1, 1, getfield(E, 'data', 'L' + NBT + ';')),
        (1, 'level', '()L' + L + ';', 1, 1, getfield(E, 'world', 'L' + L + ';')),
        # Models vanilla Entity.discard as an observable server-side removal;
        # build 238 never calls it through the recovery path.
        (0x0011, 'discard', '()V', 3, 1, discard),
    ], interfaces=[EXT])


def attribute_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x27' + op(0xb5, cp.field(ATTR, 'base', 'D')) + b'\xb1')
    return f.fake_class(ATTR, [(1, 'base', 'D')], [
        (1, '<init>', '(D)V', 3, 3, ctor),
        (1, 'setBaseValue', '(D)V', 3, 3, setter(ATTR, 'base', 'D', 0x27))])


def guide_class():
    fields = [(1, 'entityData', 'L' + SED + ';'), (1, 'attribute', 'L' + ATTR + ';'),
              (1, 'health', 'F'), (1, 'anim', 'Ljava/lang/String;'), (1, 'hasName', 'Z'),
              (1, 'invulnerable', 'Z'), (1, 'leashed', 'Z'), (1, 'leashHolder', 'L' + E + ';'),
              (1, 'leadDrops', 'I'), (1, 'leashBroadcasts', 'I'), (1, 'dropLeashCalls', 'I'),
              (1, 'soundCount', 'I'), (1, 'fallbackCount', 'I'), (1, 'genericDamageCount', 'I')]
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method(E, '<init>', '()V'))
        a.raw(0x2a, 0x2c); a.op(0xb5, cp.field(E, 'world', 'L' + L + ';'))
        a.raw(0x2a); obj(a, cp, SED); a.op(0xb5, cp.field(G, 'entityData', 'L' + SED + ';'))
        # Match the real defined default of DATA_COFFIN, before constructor returns.
        a.raw(0x2a); a.op(0xb4, cp.field(G, 'entityData', 'L' + SED + ';')); a.raw(0x01, 0x03)
        a.op(0xb8, cp.method('java/lang/Boolean', 'valueOf', '(Z)Ljava/lang/Boolean;'))
        a.op(0xb6, cp.method(SED, 'set', '(L' + ACCESSOR + ';Ljava/lang/Object;)V'))
        a.raw(0x2a); obj(a, cp, ATTR, '(D)V', lambda: a.raw(0x10, 60, 0x87))
        a.op(0xb5, cp.field(G, 'attribute', 'L' + ATTR + ';'))
        a.raw(0x2a, 0x10, 60, 0x86); a.op(0xb5, cp.field(G, 'health', 'F'))
        a.raw(0x2a); text(a, cp, ''); a.op(0xb5, cp.field(G, 'anim', 'Ljava/lang/String;'))
        a.raw(0xb1); return a.finish()
    def read(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(G, 'attribute', 'L' + ATTR + ';'))
        a.raw(0x2b); a.op(0xb4, cp.field(NBT, 'loadedMax', 'D'))
        a.op(0xb6, cp.method(ATTR, 'setBaseValue', '(D)V'))
        a.raw(0x2a, 0x2b); a.op(0xb4, cp.field(NBT, 'loadedHealth', 'F'))
        a.op(0xb5, cp.field(G, 'health', 'F')); a.raw(0xb1); return a.finish()
    def set_health(cp):
        # Match LivingEntity.setHealth's clamping to the maximum.
        a = Asm(); a.raw(0x2a, 0x0b, 0x23, 0x2a); a.op(0xb4, cp.field(G, 'attribute', 'L' + ATTR + ';'))
        a.op(0xb4, cp.field(ATTR, 'base', 'D')); a.raw(0x90)  # d2f
        a.op(0xb8, cp.method('java/lang/Math', 'min', '(FF)F'))
        a.op(0xb8, cp.method('java/lang/Math', 'max', '(FF)F'))
        a.op(0xb5, cp.field(G, 'health', 'F')); a.raw(0xb1); return a.finish()
    def leash(cp):
        return (b'\x2a\x04' + op(0xb5, cp.field(G, 'leashed', 'Z')) +
                b'\x2a\x2b' + op(0xb5, cp.field(G, 'leashHolder', 'L' + E + ';')) + b'\xb1')
    def increment(cp, name):
        x = cp.field(G, name, 'I')
        return b'\x2a\x59' + op(0xb4, x) + b'\x04\x60' + op(0xb5, x)
    def drop_leash(cp):
        # Approximate vanilla Leashable.dropLeash: no drop unless a live holder
        # is attached; flags independently control item drop and broadcast.
        a = Asm(); a.raw(*increment(cp, 'dropLeashCalls'))
        a.raw(0x2a); a.op(0xb4, cp.field(G, 'leashed', 'Z')); a.jump(0x99, 'done')
        a.raw(0x2a); a.op(0xb4, cp.field(G, 'leashHolder', 'L' + E + ';')); a.jump(0xc6, 'done')
        a.raw(0x2a, 0x03); a.op(0xb5, cp.field(G, 'leashed', 'Z'))
        a.raw(0x2a, 0x01); a.op(0xb5, cp.field(G, 'leashHolder', 'L' + E + ';'))
        a.raw(0x1c); a.jump(0x99, 'no_drop'); a.raw(*increment(cp, 'leadDrops')); a.label('no_drop')
        a.raw(0x1b); a.jump(0x99, 'done'); a.raw(*increment(cp, 'leashBroadcasts'))
        a.label('done'); a.raw(0xb1); return a.finish()
    def fallback(cp):
        return increment(cp, 'fallbackCount') + b'\x03' + op(0xb8, cp.method(INTERACTION, 'sidedSuccess', '(Z)L' + INTERACTION + ';')) + b'\xb0'
    return f.fake_class(G, fields, [
        (1, '<init>', '(L' + TYPE + ';L' + L + ';)V', 5, 3, ctor),
        (1, 'readAdditionalSaveData', '(L' + NBT + ';)V', 4, 2, read),
        (1, 'getAttribute', '(L' + HOLDER + ';)L' + ATTR + ';', 1, 2, getfield(G, 'attribute', 'L' + ATTR + ';')),
        (1, 'setHealth', '(F)V', 5, 2, set_health),
        (1, 'getCustomAnimName', '()Ljava/lang/String;', 1, 1, getfield(G, 'anim', 'Ljava/lang/String;')),
        (1, 'playCustomAnimation', '(Ljava/lang/String;Z)V', 2, 3, setter(G, 'anim', 'Ljava/lang/String;')),
        (1, 'hasCustomName', '()Z', 1, 1, getfield(G, 'hasName', 'Z', 0xac)),
        (1, 'setCustomName', '(L' + COMP + ';)V', 2, 2, lambda cp: b'\x2a\x04' + op(0xb5, cp.field(G, 'hasName', 'Z')) + b'\xb1'),
        (1, 'setLeashedTo', '(L' + E + ';Z)V', 2, 3, leash),
        (1, 'isLeashed', '()Z', 1, 1, getfield(G, 'leashed', 'Z', 0xac)),
        (1, 'getLeashHolder', '()L' + E + ';', 1, 1, getfield(G, 'leashHolder', 'L' + E + ';')),
        (1, 'dropLeash', '(ZZ)V', 3, 3, drop_leash),
        (1, 'isAlive', '()Z', 1, 1, lambda cp: b'\x04\xac'),
        (1, 'playSound', '(L' + SOUND + ';FF)V', 3, 4, lambda cp: increment(cp, 'soundCount') + b'\xb1'),
        (1, 'hurt', '(L' + DAMAGE + ';F)Z', 3, 3, lambda cp: increment(cp, 'genericDamageCount') + b'\x04\xac'),
        (1, 'mobInteract', '(L' + P + ';L' + HAND + ';)L' + INTERACTION + ';', 3, 3, fallback),
    ], super_name=E)


def level_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a, 0x1b); a.op(0xb5, cp.field(L, 'client', 'Z'))
        a.raw(0x2a, 0x2c); a.op(0xb5, cp.field(L, 'dim', 'L' + KEY + ';'))
        a.raw(0x2a, 0x2d); a.op(0xb5, cp.field(L, 'server', 'L' + SERVER + ';'))
        a.raw(0x2a); obj(a, cp, FAKE_REG); a.op(0xb5, cp.field(L, 'registry', 'L' + REGISTRY + ';'))
        a.raw(0xb1); return a.finish()
    return f.fake_class(L, [(1, 'client', 'Z'), (1, 'dim', 'L' + KEY + ';'),
                            (1, 'server', 'L' + SERVER + ';'), (1, 'registry', 'L' + REGISTRY + ';')], [
        (1, '<init>', '(ZL' + KEY + ';L' + SERVER + ';)V', 3, 4, ctor),
        (1, 'isClientSide', '()Z', 1, 1, getfield(L, 'client', 'Z', 0xac)),
        (1, 'dimension', '()L' + KEY + ';', 1, 1, getfield(L, 'dim', 'L' + KEY + ';')),
        (1, 'registryAccess', '()L' + REGISTRY + ';', 1, 1, getfield(L, 'registry', 'L' + REGISTRY + ';')),
    ])


def server_level_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a, 0x03, 0x2b, 0x2c)
        a.op(0xb7, cp.method(L, '<init>', '(ZL' + KEY + ';L' + SERVER + ';)V'))
        a.raw(0x2a); obj(a, cp, 'java/util/ArrayList'); a.op(0xb5, cp.field(SL, 'near', 'Ljava/util/List;'))
        a.raw(0xb1); return a.finish()
    return f.fake_class(SL, [(1, 'near', 'Ljava/util/List;')], [
        (1, '<init>', '(L' + KEY + ';L' + SERVER + ';)V', 4, 3, ctor),
        (1, 'getServer', '()L' + SERVER + ';', 1, 1, getfield(L, 'server', 'L' + SERVER + ';')),
        (1, 'getEntitiesOfClass', '(Ljava/lang/Class;L' + AABB + ';)Ljava/util/List;', 1, 3, getfield(SL, 'near', 'Ljava/util/List;')),
    ], super_name=L)


def player_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method(LIVING, '<init>', '()V'))
        a.raw(0x2a, 0x2b); a.op(0xb5, cp.field(P, 'uuid', 'Ljava/util/UUID;'))
        a.raw(0x2a, 0x2c); a.op(0xb5, cp.field(E, 'world', 'L' + L + ';'))
        a.raw(0x2a); obj(a, cp, ABILITIES); a.op(0xb5, cp.field(P, 'abilities', 'L' + ABILITIES + ';'))
        a.raw(0xb1); return a.finish()
    return f.fake_class(P, [(1, 'uuid', 'Ljava/util/UUID;'), (1, 'held', 'L' + IT + ';'),
                            (1, 'abilities', 'L' + ABILITIES + ';')], [
        (1, '<init>', '(Ljava/util/UUID;L' + L + ';)V', 3, 3, ctor),
        (1, 'getUUID', '()Ljava/util/UUID;', 1, 1, getfield(P, 'uuid', 'Ljava/util/UUID;')),
        (1, 'getItemInHand', '(L' + HAND + ';)L' + IT + ';', 1, 2, getfield(P, 'held', 'L' + IT + ';')),
        (1, 'getAbilities', '()L' + ABILITIES + ';', 1, 1, getfield(P, 'abilities', 'L' + ABILITIES + ';')),
    ], super_name=LIVING)


def server_player_class():
    fields = [(1, 'alive', 'Z'), (1, 'inventory', 'L' + INV + ';'),
              (1, 'lastMessage', 'Ljava/lang/String;'), (1, 'messageCount', 'I')]
    def ctor(cp):
        a = Asm(); a.raw(0x2a, 0x2b, 0x2c); a.op(0xb7, cp.method(P, '<init>', '(Ljava/util/UUID;L' + L + ';)V'))
        a.raw(0x2a, 0x04); a.op(0xb5, cp.field(SP, 'alive', 'Z'))
        a.raw(0x2a); obj(a, cp, INV, '(I)V', lambda: a.raw(0x1d)); a.op(0xb5, cp.field(SP, 'inventory', 'L' + INV + ';'))
        a.raw(0xb1); return a.finish()
    def server_level(cp):
        return b'\x2a' + op(0xb4, cp.field(E, 'world', 'L' + L + ';')) + op(0xc0, cp.cls(SL)) + b'\xb0'
    def get_server(cp):
        return b'\x2a' + op(0xb4, cp.field(E, 'world', 'L' + L + ';')) + op(0xb4, cp.field(L, 'server', 'L' + SERVER + ';')) + b'\xb0'
    def message(cp):
        a = Asm(); a.raw(0x2a, 0x2b); a.op(0xc0, cp.cls(MUT)); a.op(0xb4, cp.field(MUT, 'key', 'Ljava/lang/String;'))
        a.op(0xb5, cp.field(SP, 'lastMessage', 'Ljava/lang/String;'))
        x = cp.field(SP, 'messageCount', 'I'); a.raw(0x2a, 0x59); a.op(0xb4, x); a.raw(0x04, 0x60); a.op(0xb5, x)
        a.raw(0xb1); return a.finish()
    def name(cp):
        a = Asm(); obj(a, cp, MUT, '(Ljava/lang/String;)V', lambda: text(a, cp, 'Player'))
        a.raw(0xb0); return a.finish()
    def position(cp):
        a = Asm(); obj(a, cp, VEC); a.raw(0xb0); return a.finish()
    return f.fake_class(SP, fields, [
        (1, '<init>', '(Ljava/util/UUID;L' + SL + ';I)V', 4, 4, ctor),
        (1, 'isAlive', '()Z', 1, 1, getfield(SP, 'alive', 'Z', 0xac)),
        (1, 'serverLevel', '()L' + SL + ';', 1, 1, server_level),
        (1, 'getServer', '()L' + SERVER + ';', 1, 1, get_server),
        (1, 'getInventory', '()L' + INV + ';', 1, 1, getfield(SP, 'inventory', 'L' + INV + ';')),
        (1, 'getName', '()L' + COMP + ';', 3, 1, name),
        (1, 'displayClientMessage', '(L' + COMP + ';Z)V', 3, 3, message),
        (1, 'position', '()L' + VEC + ';', 2, 1, position),
        (1, 'removeEffect', '(L' + HOLDER + ';)Z', 1, 2, lambda cp: b'\x04\xac'),
    ], super_name=P)


def inventory_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x1b' + op(0xb5, cp.field(INV, 'room', 'I')) + b'\xb1')
    def add(cp):
        # Model Minecraft's partial-return quirk and creative's voiding of any
        # leftover if called with a too-large stack.
        a = Asm()
        calls = cp.field(INV, 'calls', 'I'); a.raw(0x2a, 0x59); a.op(0xb4, calls); a.raw(0x04, 0x60); a.op(0xb5, calls)
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'room', 'I'))
        a.raw(0x2b); a.op(0xb4, cp.field(IT, 'count', 'I'))
        a.op(0xb8, cp.method('java/lang/Math', 'min', '(II)I')); a.raw(0x3d)  # moved
        a.raw(0x2a, 0x59); a.op(0xb4, cp.field(INV, 'room', 'I')); a.raw(0x1c, 0x64); a.op(0xb5, cp.field(INV, 'room', 'I'))
        a.raw(0x2a, 0x59); a.op(0xb4, cp.field(INV, 'collected', 'I')); a.raw(0x1c, 0x60); a.op(0xb5, cp.field(INV, 'collected', 'I'))
        a.raw(0x2b, 0x59); a.op(0xb4, cp.field(IT, 'count', 'I')); a.raw(0x1c, 0x64); a.op(0xb5, cp.field(IT, 'count', 'I'))
        a.raw(0x1c); a.jump(0x9e, 'none')
        a.raw(0x2a, 0x2b); a.op(0xb4, cp.field(IT, 'meta', 'Ljava/lang/String;'))
        a.op(0xb5, cp.field(INV, 'lastMeta', 'Ljava/lang/String;'))
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'occupied', 'Z')); a.jump(0x99, 'none')
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'slotStack', 'L' + IT + ';')); a.raw(0x59)
        a.op(0xb4, cp.field(IT, 'count', 'I')); a.raw(0x1c, 0x60); a.op(0xb5, cp.field(IT, 'count', 'I'))
        a.label('none'); a.raw(0x2b); a.op(0xb4, cp.field(IT, 'count', 'I')); a.jump(0x99, 'yes')
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'creative', 'Z')); a.jump(0x99, 'no')
        a.raw(0x2b, 0x03); a.op(0xb5, cp.field(IT, 'count', 'I'))
        a.label('yes'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def select_slot(occupied):
        def generate(cp):
            a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(INV, 'room', 'I')); a.jump(0x9e, 'no')
            a.raw(0x2a); a.op(0xb4, cp.field(INV, 'occupied', 'Z')); a.jump(0x99 if occupied else 0x9a, 'no')
            a.raw(0x03, 0xac); a.label('no'); a.raw(0x02, 0xac); return a.finish()
        return generate
    def slot_item(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(INV, 'occupied', 'Z')); a.jump(0x99, 'empty')
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'slotStack', 'L' + IT + ';')); a.raw(0xb0)
        a.label('empty'); a.op(0xb2, cp.field(IT, 'EMPTY', 'L' + IT + ';')); a.raw(0xb0); return a.finish()
    def max_size(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(INV, 'occupied', 'Z')); a.jump(0x99, 'empty')
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'room', 'I')); a.raw(0x2a)
        a.op(0xb4, cp.field(INV, 'slotStack', 'L' + IT + ';')); a.op(0xb4, cp.field(IT, 'count', 'I'))
        a.raw(0x60, 0xac); a.label('empty')
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'chunkLimit', 'I')); a.jump(0x9e, 'unlimited')
        a.raw(0x2a); a.op(0xb4, cp.field(INV, 'room', 'I')); a.raw(0x2a)
        a.op(0xb4, cp.field(INV, 'chunkLimit', 'I')); a.op(0xb8, cp.method('java/lang/Math', 'min', '(II)I')); a.raw(0xac)
        a.label('unlimited'); a.raw(0x2a); a.op(0xb4, cp.field(INV, 'room', 'I')); a.raw(0xac)
        return a.finish()
    def add_slot(cp):
        return b'\x2a\x2c' + op(0xb6, cp.method(INV, 'add', '(L' + IT + ';)Z')) + b'\xac'
    return f.fake_class(INV, [(1, 'room', 'I'), (1, 'collected', 'I'), (1, 'lastMeta', 'Ljava/lang/String;'),
                              (1, 'creative', 'Z'), (1, 'occupied', 'Z'), (1, 'slotStack', 'L' + IT + ';'),
                              (1, 'chunkLimit', 'I'), (1, 'calls', 'I')], [
        (1, '<init>', '(I)V', 2, 2, ctor), (1, 'add', '(L' + IT + ';)Z', 3, 3, add),
        (1, 'add', '(IL' + IT + ';)Z', 2, 3, add_slot),
        (1, 'getSlotWithRemainingSpace', '(L' + IT + ';)I', 1, 2, select_slot(True)),
        (1, 'getFreeSlot', '()I', 1, 1, select_slot(False)),
        (1, 'getItem', '(I)L' + IT + ';', 1, 2, slot_item),
        (1, 'fixtureMaxSize', '(L' + IT + ';)I', 2, 2, max_size)], interfaces=[CONTAINER])


def item_stack_class():
    fields = [(9, 'EMPTY', 'L' + IT + ';'), (9, 'CODEC', 'L' + CODEC + ';'),
              (1, 'count', 'I'), (1, 'id', 'Ljava/lang/String;'), (1, 'meta', 'Ljava/lang/String;'),
              (1, 'kind', 'L' + ITEM + ';'), (1, 'damaged', 'Z')]
    def clinit(cp):
        a = Asm(); obj(a, cp, IT); a.op(0xb3, cp.field(IT, 'EMPTY', 'L' + IT + ';'))
        obj(a, cp, FAKE_CODEC); a.op(0xb3, cp.field(IT, 'CODEC', 'L' + CODEC + ';')); a.raw(0xb1); return a.finish()
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a); obj(a, cp, ITEM); a.op(0xb5, cp.field(IT, 'kind', 'L' + ITEM + ';')); a.raw(0xb1); return a.finish()
    def is_empty(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(IT, 'count', 'I')); a.jump(0x9d, 'not_empty')
        a.raw(0x04, 0xac); a.label('not_empty'); a.raw(0x03, 0xac); return a.finish()
    def save(cp):
        a = Asm(); a.raw(0x2c); a.op(0xc0, cp.cls(NBT)); a.raw(0x4e)
        for field, desc in [('id', 'Ljava/lang/String;'), ('meta', 'Ljava/lang/String;'), ('count', 'I')]:
            a.raw(0x2d, 0x2a); a.op(0xb4, cp.field(IT, field, desc)); a.op(0xb5, cp.field(NBT, field, desc))
        a.raw(0x2d, 0xb0); return a.finish()
    def is_item(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(IT, 'kind', 'L' + ITEM + ';')); a.raw(0x2b)
        a.jump(0xa6, 'no'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def shrink(cp):
        x = cp.field(IT, 'count', 'I')
        return b'\x2a\x59' + op(0xb4, x) + b'\x1b\x64' + op(0xb5, x) + b'\xb1'
    def copy_count(cp):
        a = Asm(); obj(a, cp, IT); a.raw(0x4d)
        for field, desc in [('id', 'Ljava/lang/String;'), ('meta', 'Ljava/lang/String;'),
                            ('kind', 'L' + ITEM + ';'), ('damaged', 'Z')]:
            a.raw(0x2c, 0x2a); a.op(0xb4, cp.field(IT, field, desc)); a.op(0xb5, cp.field(IT, field, desc))
        a.raw(0x2c, 0x1b); a.op(0xb5, cp.field(IT, 'count', 'I')); a.raw(0x2c, 0xb0); return a.finish()
    return f.fake_class(IT, fields, [
        (8, '<clinit>', '()V', 2, 0, clinit), (1, '<init>', '()V', 3, 1, ctor),
        (1, 'isEmpty', '()Z', 1, 1, is_empty),
        (1, 'getCount', '()I', 1, 1, getfield(IT, 'count', 'I', 0xac)),
        (1, 'getItem', '()L' + ITEM + ';', 1, 1, getfield(IT, 'kind', 'L' + ITEM + ';')),
        (1, 'is', '(L' + ITEM + ';)Z', 2, 2, is_item),
        (1, 'isDamaged', '()Z', 1, 1, getfield(IT, 'damaged', 'Z', 0xac)),
        (1, 'copyWithCount', '(I)L' + IT + ';', 3, 3, copy_count),
        (1, 'shrink', '(I)V', 3, 2, shrink),
        (1, 'save', '(L' + PROVIDER + ';L' + TAG + ';)L' + TAG + ';', 2, 4, save)])


def fake_codec_class():
    def parse(cp):
        a = Asm(); a.raw(0x2c); a.op(0xc0, cp.cls(NBT)); a.raw(0x4e)
        a.raw(0x2d); a.op(0xb4, cp.field(NBT, 'invalid', 'I')); const(a, 3); a.jump(0xa0, 'check')
        obj(a, cp, 'java/lang/IllegalArgumentException'); a.raw(0xbf)
        a.label('check'); a.raw(0x2d); a.op(0xb4, cp.field(NBT, 'invalid', 'I')); a.jump(0x9a, 'bad')
        obj(a, cp, IT); insn_local(a, 'astore', 4)
        for field, desc in [('id', 'Ljava/lang/String;'), ('meta', 'Ljava/lang/String;'), ('count', 'I'), ('damaged', 'Z')]:
            insn_local(a, 'aload', 4); a.raw(0x2d); a.op(0xb4, cp.field(NBT, field, desc)); a.op(0xb5, cp.field(IT, field, desc))
        obj(a, cp, FAKE_RESULT, '(Ljava/lang/Object;)V', lambda: insn_local(a, 'aload', 4)); a.raw(0xb0)
        a.label('bad')
        # Includes DataResult partial-result errors: result() must be empty.
        obj(a, cp, FAKE_RESULT, '(Ljava/lang/Object;)V', lambda: a.raw(0x01)); a.raw(0xb0)
        return a.finish()
    return f.fake_class(FAKE_CODEC, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'parse', '(L' + OPS + ';Ljava/lang/Object;)L' + RESULT + ';', 3, 5, parse)], interfaces=[CODEC])


def fake_result_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + op(0xb5, cp.field(FAKE_RESULT, 'value', 'Ljava/lang/Object;')) + b'\xb1')
    def result(cp):
        return (b'\x2a' + op(0xb4, cp.field(FAKE_RESULT, 'value', 'Ljava/lang/Object;')) +
                op(0xb8, cp.method('java/util/Optional', 'ofNullable', '(Ljava/lang/Object;)Ljava/util/Optional;')) + b'\xb0')
    return f.fake_class(FAKE_RESULT, [(1, 'value', 'Ljava/lang/Object;')], [
        (1, '<init>', '(Ljava/lang/Object;)V', 2, 2, ctor),
        (1, 'result', '()Ljava/util/Optional;', 1, 1, result)], interfaces=[RESULT])


def component_classes():
    def translate(cp):
        a = Asm(); obj(a, cp, MUT, '(Ljava/lang/String;)V', lambda: a.raw(0x2a)); a.raw(0xb0); return a.finish()
    c = interface(COMP, [abstract('getString', '()Ljava/lang/String;'),
        (9, 'translatable', '(Ljava/lang/String;)L' + MUT + ';', 3, 1, translate),
        (9, 'translatable', '(Ljava/lang/String;[Ljava/lang/Object;)L' + MUT + ';', 3, 2, translate)])
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + op(0xb5, cp.field(MUT, 'key', 'Ljava/lang/String;')) + b'\xb1')
    def create(cp):
        a = Asm()
        def key():
            a.raw(0x2a); a.op(0xc0, cp.cls(TRANS)); a.op(0xb4, cp.field(TRANS, 'key', 'Ljava/lang/String;'))
        obj(a, cp, MUT, '(Ljava/lang/String;)V', key); a.raw(0xb0); return a.finish()
    m = f.fake_class(MUT, [(1, 'key', 'Ljava/lang/String;')], [
        (1, '<init>', '(Ljava/lang/String;)V', 2, 2, ctor),
        (9, 'create', '(L' + CONTENTS + ';)L' + MUT + ';', 3, 1, create),
        (1, 'getString', '()Ljava/lang/String;', 1, 1, getfield(MUT, 'key', 'Ljava/lang/String;'))], interfaces=[COMP])
    return c, m


def event_parents():
    def parent_ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + op(0xb5, cp.field(PLAYER_EVENT, 'player', 'L' + P + ';')) + b'\xb1')
    player_event = f.fake_class(PLAYER_EVENT, [(1, 'player', 'L' + P + ';')], [
        (1, '<init>', '(L' + P + ';)V', 2, 2, parent_ctor),
        (1, 'getEntity', '()L' + P + ';', 1, 1, getfield(PLAYER_EVENT, 'player', 'L' + P + ';'))])
    def interact_ctor(cp):
        return (b'\x2a\x2b' + op(0xb7, cp.method(PLAYER_EVENT, '<init>', '(L' + P + ';)V')) +
                b'\x2a\x2c' + op(0xb5, cp.field(INTERACT_EVENT, 'hand', 'L' + IT + ';')) + b'\xb1')
    interact = f.fake_class(INTERACT_EVENT, [(1, 'hand', 'L' + IT + ';')], [
        (1, '<init>', '(L' + P + ';L' + IT + ';)V', 2, 3, interact_ctor),
        (1, 'getItemStack', '()L' + IT + ';', 1, 1, getfield(INTERACT_EVENT, 'hand', 'L' + IT + ';'))],
        super_name=PLAYER_EVENT)
    return player_event, interact


def event_class(name):
    fields = [(1, 'target', 'L' + E + ';'), (1, 'canceled', 'Z')]
    def ctor(cp):
        return (b'\x2a\x2b\x2d' + op(0xb7, cp.method(INTERACT_EVENT, '<init>', '(L' + P + ';L' + IT + ';)V')) +
                b'\x2a\x2c' + op(0xb5, cp.field(name, 'target', 'L' + E + ';')) + b'\xb1')
    return f.fake_class(name, fields, [
        (1, '<init>', '(L' + P + ';L' + E + ';L' + IT + ';)V', 3, 4, ctor),
        (1, 'getTarget', '()L' + E + ';', 1, 1, getfield(name, 'target', 'L' + E + ';')),
        (1, 'setCanceled', '(Z)V', 2, 2, setter(name, 'canceled', 'Z', 0x1b)),
    ], super_name=INTERACT_EVENT)


def dimension_event_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        for local, field, desc in [(0x2b, 'player', 'L' + P + ';'), (0x2c, 'from', 'L' + KEY + ';'),
                                   (0x2d, 'to', 'L' + KEY + ';')]:
            a.raw(0x2a, local); a.op(0xb5, cp.field(DIM_EVENT, field, desc))
        a.raw(0xb1); return a.finish()
    return f.fake_class(DIM_EVENT, [(1, 'player', 'L' + P + ';'), (1, 'from', 'L' + KEY + ';'),
                                    (1, 'to', 'L' + KEY + ';')], [
        (1, '<init>', '(L' + P + ';L' + KEY + ';L' + KEY + ';)V', 2, 4, ctor),
        (1, 'getEntity', '()L' + P + ';', 1, 1, getfield(DIM_EVENT, 'player', 'L' + P + ';')),
        (1, 'getFrom', '()L' + KEY + ';', 1, 1, getfield(DIM_EVENT, 'from', 'L' + KEY + ';')),
        (1, 'getTo', '()L' + KEY + ';', 1, 1, getfield(DIM_EVENT, 'to', 'L' + KEY + ';')),
    ])


def purgatory_data_class():
    def master(cp):
        a = Asm(); obj(a, cp, PD); a.raw(0xb0); return a.finish()
    def queued(cp):
        a = Asm(); a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.jump(0xc6, 'no')
        a.op(0xb2, cp.field(PD, 'queued', 'Ljava/util/UUID;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.raw(0xac)
        a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def remove(cp):
        a = Asm(); a.raw(0x2a, 0x2b); a.op(0xb6, cp.method(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z'))
        a.jump(0x99, 'no'); a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
        a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    return f.fake_class(PD, [(9, 'queued', 'Ljava/util/UUID;')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (9, 'getMaster', '(L' + SERVER + ';)L' + PD + ';', 2, 1, master),
        (1, 'isDeadInQueue', '(Ljava/util/UUID;)Z', 2, 2, queued),
        (1, 'removeFromQueue', '(Ljava/util/UUID;)Z', 2, 2, remove),
    ])


def purgatory_clinit(k):
    return (op(0xbb, k.add_class(KEY)) + b'\x59' + op(0xb7, k.add_methodref(KEY, '<init>', '()V')) +
            op(0xb3, k.add_fieldref(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')) + b'\xb1')


def drops_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + op(0xb5, cp.field(DROP_EVENT, 'entity', 'L' + LIVING + ';')) +
                b'\x2a\x2c' + op(0xb5, cp.field(DROP_EVENT, 'drops', 'Ljava/util/Collection;')) + b'\xb1')
    return f.fake_class(DROP_EVENT, [(1, 'entity', 'L' + LIVING + ';'), (1, 'drops', 'Ljava/util/Collection;')], [
        (1, '<init>', '(L' + LIVING + ';Ljava/util/Collection;)V', 2, 3, ctor),
        (1, 'getEntity', '()L' + LIVING + ';', 1, 1, getfield(DROP_EVENT, 'entity', 'L' + LIVING + ';')),
        (1, 'getDrops', '()Ljava/util/Collection;', 1, 1, getfield(DROP_EVENT, 'drops', 'Ljava/util/Collection;')),
    ])


def item_entity_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method(E, '<init>', '()V')) +
                b'\x2a\x2b' + op(0xb5, cp.field(ITEM_ENTITY, 'item', 'L' + IT + ';')) + b'\xb1')
    return f.fake_class(ITEM_ENTITY, [(1, 'item', 'L' + IT + ';')], [
        (1, '<init>', '(L' + IT + ';)V', 2, 2, ctor),
        (1, 'getItem', '()L' + IT + ';', 1, 1, getfield(ITEM_ENTITY, 'item', 'L' + IT + ';')),
    ], super_name=E)


def items_class():
    def clinit(cp):
        a = Asm(); obj(a, cp, ITEM); a.op(0xb3, cp.field(ITEMS, 'LEAD', 'L' + ITEM + ';')); a.raw(0xb1); return a.finish()
    return f.fake_class(ITEMS, [(9, 'LEAD', 'L' + ITEM + ';')], [(8, '<clinit>', '()V', 2, 0, clinit)])


def logger_classes():
    logger = interface(LOGGER, [abstract('info', '(Ljava/lang/String;Ljava/lang/Object;)V'),
                                abstract('info', '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V')])
    fake = f.fake_class(FLOGGER, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'info', '(Ljava/lang/String;Ljava/lang/Object;)V', 0, 3, lambda cp: b'\xb1'),
        (1, 'info', '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V', 0, 4, lambda cp: b'\xb1')], interfaces=[LOGGER])
    def get_logger(cp):
        a = Asm(); obj(a, cp, FLOGGER); a.raw(0xb0); return a.finish()
    utils = f.fake_class(LOGUTILS, [], [(9, 'getLogger', '()L' + LOGGER + ';', 2, 0, get_logger)])
    return logger, fake, utils


def build_classes(jar, helper_jar):
    with zipfile.ZipFile(helper_jar) as z:
        helper = z.read(H + '.class')
    component, mutable = component_classes()
    player_event, interact_event = event_parents()
    logger, fake_logger, utils = logger_classes()
    def registry_context(cp):
        a = Asm(); obj(a, cp, REGOPS); a.raw(0xb0); return a.finish()
    def nbt_ops_init(cp):
        a = Asm(); obj(a, cp, NBTO); a.op(0xb3, cp.field(NBTO, 'INSTANCE', 'L' + NBTO + ';')); a.raw(0xb1); return a.finish()
    def sound_value(cp):
        a = Asm(); obj(a, cp, SOUND); a.raw(0xb0); return a.finish()
    def sounds_init(cp):
        a = Asm(); obj(a, cp, FAKE_HOLDER); a.op(0xb3, cp.field(SOUNDS, 'ARMOR_EQUIP_NETHERITE', 'L' + HOLDER + ';'))
        a.raw(0xb1); return a.finish()
    def sided_success(cp):
        a = Asm(); obj(a, cp, INTERACTION); a.raw(0xb0); return a.finish()
    classes = {
        C: subset(jar, C, {'<init>', '<clinit>', 'readAdditionalSaveData', 'setOwner', 'mobInteract', 'hurt',
                           'canBeLeashed', 'isCoffin', 'setCoffin', 'settledlands$getOwnerUuid'}, expose={'mobInteract'}),
        IVY: subset(jar, IVY, {'editingTool', 'onInteractCorpse', 'onInteractSpecificCorpse'}),
        PM: subset(jar, PM, {'onDimensionChange'}, fields={'PURGATORY_LEVEL'}, add_clinit=purgatory_clinit),
        POSS: subset(jar, POSS, {'<clinit>', 'data', 'onPlayerDrops', 'movePendingItemsToCorpse',
                                 'findOwnerCorpse', 'ownerUuid'}, expose={'movePendingItemsToCorpse'}),
        H: helper,
        TAG: interface(TAG), NBT: nbt_class(), LIST: list_class(),
        EXT: interface(EXT, [abstract('getPersistentData', '()L' + NBT + ';')]),
        E: entity_class(), LIVING: f.fake_class(LIVING, [], [(1, '<init>', '()V', 1, 1, f.empty_init(E))], super_name=E),
        G: guide_class(), C + '$unused': f.blank_class(C + '$unused'),
        L: level_class(), SL: server_level_class(), SERVER: f.blank_class(SERVER),
        P: player_class(), SP: server_player_class(), INV: inventory_class(),
        # In real Minecraft this is an inherited interface default, not an
        # Inventory-declared method; exercise actual invokevirtual resolution.
        CONTAINER: interface(CONTAINER, [(1, 'getMaxStackSize', '(L' + IT + ';)I', 2, 2,
            lambda cp: b'\x2a' + op(0xc0, cp.cls(INV)) + b'\x2b' +
                       op(0xb6, cp.method(INV, 'fixtureMaxSize', '(L' + IT + ';)I')) + b'\xac')]),
        TYPE: f.blank_class(TYPE), HOLDER: interface(HOLDER, [abstract('value', '()Ljava/lang/Object;')]), ATTR: attribute_class(),
        ATTRS: f.fake_class(ATTRS, [(9, 'MAX_HEALTH', 'L' + HOLDER + ';')], []),
        FAKE_HOLDER: f.fake_class(FAKE_HOLDER, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (1, 'value', '()Ljava/lang/Object;', 2, 1, sound_value)], interfaces=[HOLDER]),
        SOUNDS: f.fake_class(SOUNDS, [(9, 'ARMOR_EQUIP_NETHERITE', 'L' + HOLDER + ';')], [
            (8, '<clinit>', '()V', 2, 0, sounds_init)]), SOUND: f.blank_class(SOUND),
        ABILITIES: f.fake_class(ABILITIES, [(1, 'instabuild', 'Z')], [(1, '<init>', '()V', 1, 1, f.empty_init())]),
        HAND: f.blank_class(HAND), INTERACTION: f.fake_class(INTERACTION, [], [
            (1, '<init>', '()V', 1, 1, f.empty_init()),
            (9, 'sidedSuccess', '(Z)L' + INTERACTION + ';', 2, 1, sided_success)]),
        DAMAGE: f.fake_class(DAMAGE, [(1, 'generic', 'Z')], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (1, 'is', '(L' + KEY + ';)Z', 1, 2, getfield(DAMAGE, 'generic', 'Z', 0xac))]),
        DAMAGE_TYPES: f.fake_class(DAMAGE_TYPES, [(9, 'GENERIC_KILL', 'L' + KEY + ';')], []),
        SED: f.synched_data_class(), ACCESSOR: f.blank_class(ACCESSOR), SERIALIZER: f.blank_class(SERIALIZER),
        SERIALIZERS: f.fake_class(SERIALIZERS, [(9, 'BOOLEAN', 'L' + SERIALIZER + ';')], []),
        COMP: component, MUT: mutable, CONTENTS: interface(CONTENTS),
        TRANS: f.fake_class(TRANS, [(9, 'NO_ARGS', '[Ljava/lang/Object;'), (1, 'key', 'Ljava/lang/String;')], [
            (1, '<init>', '(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V', 2, 4,
             lambda cp: b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                        b'\x2a\x2b' + op(0xb5, cp.field(TRANS, 'key', 'Ljava/lang/String;')) + b'\xb1')],
            interfaces=[CONTENTS]),
        ITEM: f.blank_class(ITEM), ITEMS: items_class(), IT: item_stack_class(),
        OPS: interface(OPS),
        # Codec inherits parse from Decoder in DFU; keep that method ownership.
        CODEC: interface(CODEC, parents=[DECODER]),
        DECODER: interface(DECODER, [abstract('parse', '(L' + OPS + ';Ljava/lang/Object;)L' + RESULT + ';')]),
        RESULT: interface(RESULT, [abstract('result', '()Ljava/util/Optional;')]),
        PROVIDER: interface(PROVIDER, [abstract('createSerializationContext', '(L' + OPS + ';)L' + REGOPS + ';')]),
        REGISTRY: interface(REGISTRY, parents=[PROVIDER]),
        REGOPS: f.fake_class(REGOPS, [], [(1, '<init>', '()V', 1, 1, f.empty_init())], interfaces=[OPS]),
        NBTO: f.fake_class(NBTO, [(9, 'INSTANCE', 'L' + NBTO + ';')], [
            (1, '<init>', '()V', 1, 1, f.empty_init()), (8, '<clinit>', '()V', 2, 0, nbt_ops_init)], interfaces=[OPS]),
        FAKE_REG: f.fake_class(FAKE_REG, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (1, 'createSerializationContext', '(L' + OPS + ';)L' + REGOPS + ';', 2, 2, registry_context)], interfaces=[REGISTRY]),
        FAKE_CODEC: fake_codec_class(), FAKE_RESULT: fake_result_class(),
        KEY: f.blank_class(KEY), PD: purgatory_data_class(), DIM_EVENT: dimension_event_class(),
        MOB_EFFECTS: f.fake_class(MOB_EFFECTS, [(9, 'DIG_SLOWDOWN', 'L' + HOLDER + ';')], []),
        PLAYER_EVENT: player_event, INTERACT_EVENT: interact_event,
        EVENT_S: event_class(EVENT_S), EVENT_G: event_class(EVENT_G),
        POSS_SYS: f.fake_class(POSS_SYS, [(9, 'possessed', 'Z')], [
            (9, 'isPossessed', '(L' + E + ';)Z', 1, 1,
             lambda cp: op(0xb2, cp.field(POSS_SYS, 'possessed', 'Z')) + b'\xac')]),
        DROP_EVENT: drops_class(), ITEM_ENTITY: item_entity_class(),
        VEC: f.fake_class(VEC, [(1, 'x', 'D'), (1, 'y', 'D'), (1, 'z', 'D')], [(1, '<init>', '()V', 1, 1, f.empty_init())]),
        AABB: f.fake_class(AABB, [], [(1, '<init>', '(DDDDDD)V', 1, 13, f.empty_init())]),
        LOGGER: logger, FLOGGER: fake_logger, LOGUTILS: utils,
    }
    for tool in TOOLS:
        classes[tool] = f.fake_class(tool, [], [(1, '<init>', '()V', 1, 1, f.empty_init(ITEM))], super_name=ITEM)
    return classes


def harness_class():
    def main(cp):
        a = Asm()
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        printers = {d: cp.method('java/io/PrintStream', 'println', '(' + d + ')V')
                    for d in ('I', 'Z', 'F', 'D', 'Ljava/lang/String;')}
        def load(i):
            insn_local(a, 'aload', i)
        def store(i):
            insn_local(a, 'astore', i)
        def assign(i, owner, field, desc, emit):
            load(i); emit(); a.op(0xb5, cp.field(owner, field, desc))
        def field(i, owner, name, desc):
            load(i); a.op(0xb4, cp.field(owner, name, desc))
        def boolean(value):
            a.raw(0x04 if value else 0x03)
        def uuid(s):
            text(a, cp, s); a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def print_value(name, typ, emit):
            a.op(0xb2, out); text(a, cp, name + '='); a.op(0xb6, print_s)
            a.op(0xb2, out); emit(); a.op(0xb6, printers[typ])
        def inventory():
            field(3, SP, 'inventory', 'L' + INV + ';')
        def inv_field(label, name, typ):
            print_value(label, typ, lambda: (inventory(), a.op(0xb4, cp.field(INV, name, typ))))
        def msg(label):
            print_value(label, 'Ljava/lang/String;', lambda: field(3, SP, 'lastMessage', 'Ljava/lang/String;'))
        def messages(label):
            print_value(label, 'I', lambda: field(3, SP, 'messageCount', 'I'))
        def canceled(label, event):
            print_value(label, 'Z', lambda: field(5, event, 'canceled', 'Z'))
        def corpse_data(corpse=4):
            load(corpse); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def contains(label, corpse=4, key=p.ITEM_KEY):
            def emit():
                corpse_data(corpse); text(a, cp, key); a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
            print_value(label, 'Z', emit)
        def list_size(label, corpse=4):
            def emit():
                corpse_data(corpse); text(a, cp, p.ITEM_KEY); const(a, 10)
                a.op(0xb6, cp.method(NBT, 'getList', '(Ljava/lang/String;I)L' + LIST + ';'))
                a.op(0xb6, cp.method(LIST, 'size', '()I'))
            print_value(label, 'I', emit)
        def queue(label):
            def emit():
                load(1); a.op(0xb8, cp.method(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';'))
                load(3); a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;'))
                a.op(0xb6, cp.method(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z'))
            print_value(label, 'Z', emit)
        def env(room=30, owner=True):
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
            obj(a, cp, SERVER); store(1)
            obj(a, cp, KEY); store(13)
            obj(a, cp, SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(13), load(1))); store(2)
            obj(a, cp, SP, '(Ljava/util/UUID;L' + SL + ';I)V',
                lambda: (uuid('00000000-0000-0000-0000-000000000001'), load(2), const(a, room)))
            store(3)
            obj(a, cp, C, '(L' + TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(4)
            if owner:
                load(4); load(3); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
        def make_list(corpse=4, dest=6):
            obj(a, cp, LIST); store(dest)
            corpse_data(corpse); text(a, cp, p.ITEM_KEY); load(dest)
            a.op(0xb6, cp.method(NBT, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';')); a.raw(0x57)
        def item(count, item_id='test:item', meta='mod:{enchanted:yes}', invalid=0, lst=6, dest=7):
            obj(a, cp, NBT); store(dest)
            assign(dest, NBT, 'count', 'I', lambda: const(a, count))
            assign(dest, NBT, 'id', 'Ljava/lang/String;', lambda: text(a, cp, item_id))
            assign(dest, NBT, 'meta', 'Ljava/lang/String;', lambda: text(a, cp, meta))
            if invalid:
                assign(dest, NBT, 'invalid', 'I', lambda: const(a, invalid))
            load(lst); load(dest); a.op(0xb6, cp.method(LIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
        def hand(kind=None):
            obj(a, cp, IT); store(12)
            if kind:
                if kind == 'lead':
                    assign(12, IT, 'kind', 'L' + ITEM + ';', lambda: static_field(a, cp, ITEMS, 'LEAD', 'L' + ITEM + ';'))
                else:
                    assign(12, IT, 'kind', 'L' + ITEM + ';', lambda: obj(a, cp, kind))
        def click(event=EVENT_S, actor=3, target=4, kind=None):
            hand(kind)
            obj(a, cp, event, '(L' + P + ';L' + E + ';L' + IT + ';)V', lambda: (load(actor), load(target), load(12)))
            store(5); load(5)
            method = 'onInteractSpecificCorpse' if event == EVENT_S else 'onInteractCorpse'
            a.op(0xb8, cp.method(IVY, method, '(L' + event + ';)V'))
        def tag_field(label, loc, name, typ):
            print_value(label, typ, lambda: field(loc, NBT, name, typ))
        def health(label, corpse=4):
            print_value(label + '.max', 'D', lambda: (field(corpse, G, 'attribute', 'L' + ATTR + ';'),
                                                       a.op(0xb4, cp.field(ATTR, 'base', 'D'))))
            print_value(label + '.hp', 'F', lambda: field(corpse, G, 'health', 'F'))
        def set_room(n):
            inventory(); const(a, n); a.op(0xb5, cp.field(INV, 'room', 'I'))
        def set_queue():
            load(3); a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;'))
            a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
        def set_dimension(purgatory):
            assign(2, L, 'dim', 'L' + KEY + ';',
                   lambda: static_field(a, cp, PM, 'PURGATORY_LEVEL', 'L' + KEY + ';') if purgatory else load(13))
        def corpse_other():
            obj(a, cp, C, '(L' + TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(8)
            load(8); load(3); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
        def drop_event():
            obj(a, cp, 'java/util/ArrayList'); store(6)
            obj(a, cp, IT); store(7)
            assign(7, IT, 'count', 'I', lambda: const(a, 5))
            assign(7, IT, 'id', 'Ljava/lang/String;', lambda: text(a, cp, 'mod:enchanted_tool'))
            assign(7, IT, 'meta', 'Ljava/lang/String;', lambda: text(a, cp, 'mod:{enchanted:yes}'))
            load(6); obj(a, cp, ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(7))
            a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
            obj(a, cp, DROP_EVENT, '(L' + LIVING + ';Ljava/util/Collection;)V', lambda: (load(3), load(6))); store(5)
            load(5); a.op(0xb8, cp.method(POSS, 'onPlayerDrops', '(L' + DROP_EVENT + ';)V'))
        def drops_size(label):
            print_value(label, 'I', lambda: (load(6), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))

        # Real existing capture -> nearest own corpse -> real queue-exit event ->
        # real interaction handler -> real 238 recovery helper.
        env(30)
        field(2, SL, 'near', 'Ljava/util/List;'); load(4)
        p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        boolean(True); a.op(0xb3, cp.field(POSS_SYS, 'possessed', 'Z'))
        drop_event(); drops_size('pipeline.drops'); contains('pipeline.pending', corpse=3, key='settledlands_body_items')
        list_size('pipeline.corpse_stacks')
        set_queue(); set_dimension(True); click(); inv_field('pipeline.blocked', 'collected', 'I')
        set_dimension(False)
        obj(a, cp, DIM_EVENT, '(L' + P + ';L' + KEY + ';L' + KEY + ';)V',
            lambda: (load(3), static_field(a, cp, PM, 'PURGATORY_LEVEL', 'L' + KEY + ';'), load(13)))
        a.op(0xb8, cp.method(PM, 'onDimensionChange', '(L' + DIM_EVENT + ';)V'))
        queue('pipeline.still_queued'); click(); canceled('pipeline.specific_cancel', EVENT_S)
        inv_field('pipeline.received', 'collected', 'I'); inv_field('pipeline.meta', 'lastMeta', 'Ljava/lang/String;')
        contains('pipeline.remains'); msg('pipeline.message'); messages('pipeline.messages')
        click(EVENT_G); canceled('pipeline.general_cancel', EVENT_G); inv_field('pipeline.repeat', 'collected', 'I')
        messages('pipeline.repeat_messages')

        # Existing ordinary (not possessed) death never captures drops.
        env(); boolean(False); a.op(0xb3, cp.field(POSS_SYS, 'possessed', 'Z'))
        drop_event(); drops_size('ordinary.drops'); contains('ordinary.pending', corpse=3, key='settledlands_body_items')
        contains('ordinary.corpse'); click(); inv_field('ordinary.received', 'collected', 'I')

        # Foreign UUID, missing owner, non-server actor, and a non-corpse target.
        env(); make_list(); item(3)
        obj(a, cp, SP, '(Ljava/util/UUID;L' + SL + ';I)V',
            lambda: (uuid('00000000-0000-0000-0000-000000000002'), load(2), const(a, 30))); store(9)
        click(EVENT_G, actor=9); canceled('foreign.canceled', EVENT_G)
        print_value('foreign.received', 'I', lambda: (field(9, SP, 'inventory', 'L' + INV + ';'),
                                                       a.op(0xb4, cp.field(INV, 'collected', 'I'))))
        list_size('foreign.stacks')
        obj(a, cp, P, '(Ljava/util/UUID;L' + L + ';)V',
            lambda: (uuid('00000000-0000-0000-0000-000000000001'), load(2))); store(9)
        click(actor=9); canceled('client.canceled', EVENT_S); list_size('client.stacks')
        obj(a, cp, E); store(8); click(target=8); canceled('other_entity.canceled', EVENT_S); list_size('other_entity.stacks')
        env(owner=False); make_list(); item(3); click(); inv_field('unowned.received', 'collected', 'I'); list_size('unowned.stacks')

        # Full inventory keeps all items; retry recovers exactly once.
        env(0); make_list(); item(5); click(EVENT_G)
        inv_field('full.received', 'collected', 'I'); tag_field('full.count', 7, 'count', 'I'); msg('full.message')
        set_room(5); click(); inv_field('full.retry', 'collected', 'I'); contains('full.remains')
        click(EVENT_G); inv_field('full.repeat', 'collected', 'I')

        # Partial transfer of an enchanted/modded stack: mutate only count;
        # leave later entries in the corpse until another click.
        env(4); make_list(); item(7, 'mod:relic', 'mod:{enchanted:yes}', dest=7)
        item(2, 'test:other', 'components:{custom_name:kept}', dest=10)
        click(EVENT_G); inv_field('partial.received', 'collected', 'I'); list_size('partial.stacks')
        tag_field('partial.count', 7, 'count', 'I'); tag_field('partial.id', 7, 'id', 'Ljava/lang/String;')
        tag_field('partial.meta', 7, 'meta', 'Ljava/lang/String;'); tag_field('partial.second', 10, 'count', 'I')
        msg('partial.message'); set_room(10); click(); inv_field('partial.retry', 'collected', 'I'); contains('partial.remains')

        # Calibrate the creative-mode hazard: vanilla-style add can consume the
        # input after putting only part of it into a full creative inventory.
        env(4); inventory(); boolean(True); a.op(0xb5, cp.field(INV, 'creative', 'Z'))
        obj(a, cp, IT); store(7); assign(7, IT, 'count', 'I', lambda: const(a, 7))
        inventory(); load(7); a.op(0xb6, cp.method(INV, 'add', '(L' + IT + ';)Z')); a.raw(0x57)
        inv_field('creative_control.inserted', 'collected', 'I')
        print_value('creative_control.stack_left', 'I', lambda: field(7, IT, 'count', 'I'))
        # The actual helper must keep all 3 overflow items, even in creative.
        env(4); inventory(); boolean(True); a.op(0xb5, cp.field(INV, 'creative', 'Z'))
        make_list(); item(7, 'mod:relic', 'creative:components'); click()
        inv_field('creative.inserted', 'collected', 'I'); tag_field('creative.left', 7, 'count', 'I')
        tag_field('creative.meta', 7, 'meta', 'Ljava/lang/String;'); set_room(3); click(EVENT_G)
        inv_field('creative.retry', 'collected', 'I'); contains('creative.remains')

        # Existing partially filled slot and damaged equipment follow distinct
        # vanilla branches: damaged items must use an actually free slot.
        env(4); obj(a, cp, IT); store(11); assign(11, IT, 'count', 'I', lambda: const(a, 60))
        inventory(); boolean(True); a.op(0xb5, cp.field(INV, 'occupied', 'Z'))
        inventory(); load(11); a.op(0xb5, cp.field(INV, 'slotStack', 'L' + IT + ';'))
        make_list(); item(7); click(); inv_field('occupied.inserted', 'collected', 'I')
        tag_field('occupied.left', 7, 'count', 'I')
        print_value('occupied.final_slot', 'I', lambda: field(11, IT, 'count', 'I'))
        env(4); obj(a, cp, IT); store(11); assign(11, IT, 'count', 'I', lambda: const(a, 60))
        inventory(); boolean(True); a.op(0xb5, cp.field(INV, 'occupied', 'Z'))
        inventory(); load(11); a.op(0xb5, cp.field(INV, 'slotStack', 'L' + IT + ';'))
        make_list(); item(1, 'test:damaged', 'damage:10'); assign(7, NBT, 'damaged', 'Z', lambda: boolean(True))
        click(); inv_field('damaged.blocked', 'collected', 'I'); tag_field('damaged.left', 7, 'count', 'I')
        inventory(); boolean(False); a.op(0xb5, cp.field(INV, 'occupied', 'Z')); set_room(1)
        click(); inv_field('damaged.retry', 'collected', 'I'); contains('damaged.remains')
        env(10); inventory(); const(a, 3); a.op(0xb5, cp.field(INV, 'chunkLimit', 'I'))
        make_list(); item(7); click(); inv_field('multislot.inserted', 'collected', 'I')
        inv_field('multislot.calls', 'calls', 'I'); contains('multislot.remains')

        # Full codec error, partial-result error and exception all remain intact;
        # an ordinary valid entry after them still transfers.
        env(30); make_list(); item(2, 'mod:missing', 'unknown:a', 1, dest=7)
        item(3, 'mod:partial', 'unknown:b', 2, dest=10); item(1, 'mod:throws', 'unknown:c', 3, dest=11)
        item(4, 'test:valid', 'valid:d', 0, dest=14)
        click(); inv_field('invalid.received', 'collected', 'I'); list_size('invalid.stacks')
        tag_field('invalid.first', 7, 'count', 'I'); tag_field('invalid.partial', 10, 'count', 'I')
        tag_field('invalid.exception', 11, 'count', 'I'); tag_field('invalid.meta', 10, 'meta', 'Ljava/lang/String;')
        click(EVENT_G); inv_field('invalid.repeat', 'collected', 'I')

        # Two corpses of one owner must not share their saved item lists.
        env(30); make_list(); item(2); corpse_other(); make_list(corpse=8, dest=10); item(6, lst=10, dest=11)
        click(); inv_field('two.first', 'collected', 'I'); contains('two.first_remains'); contains('two.second_remains', 8)
        click(EVENT_G, target=8); inv_field('two.second', 'collected', 'I'); contains('two.second_after', 8)

        # Simulate NeoForge restoring persistent data and execute the original
        # readAdditionalSaveData to restore owner/coffin on a reloaded entity.
        env(30); make_list(); item(6, 'test:saved', 'saved:components')
        obj(a, cp, C, '(L' + TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(8)
        obj(a, cp, NBT); store(9)
        assign(9, NBT, 'owner', 'Ljava/util/UUID;', lambda: (load(3), a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;'))))
        load(8); load(9); a.op(0xb6, cp.method(C, 'readAdditionalSaveData', '(L' + NBT + ';)V'))
        assign(8, E, 'data', 'L' + NBT + ';', lambda: field(4, E, 'data', 'L' + NBT + ';'))
        click(target=8); inv_field('reloaded.received', 'collected', 'I'); contains('reloaded.remains', 8)

        # Queue and dimension are independent blockers; dead/no-server are too.
        env(30); make_list(); item(3); set_queue(); click(); inv_field('blocked.queue', 'collected', 'I')
        a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;')); set_dimension(True)
        click(EVENT_G); inv_field('blocked.dimension', 'collected', 'I'); set_dimension(False)
        assign(3, SP, 'alive', 'Z', lambda: boolean(False)); click(); inv_field('blocked.dead', 'collected', 'I')
        assign(3, SP, 'alive', 'Z', lambda: boolean(True)); assign(2, L, 'server', 'L' + SERVER + ';', lambda: a.raw(0x01))
        click(); inv_field('blocked.no_server', 'collected', 'I')
        assign(2, L, 'server', 'L' + SERVER + ';', lambda: load(1)); click(); inv_field('blocked.allowed', 'collected', 'I')

        # Cof fin, lead and NPC tools bypass the recovery handler and are not
        # canceled; an ordinary empty-hand interaction continues to work.
        env(30); make_list(); item(1)
        click(kind=TOOLS[-1]); canceled('tools.coffin_canceled', EVENT_S); inv_field('tools.coffin_received', 'collected', 'I')
        click(EVENT_G, kind='lead'); canceled('tools.lead_canceled', EVENT_G); inv_field('tools.lead_received', 'collected', 'I')
        click(kind=TOOLS[0]); canceled('tools.wand_canceled', EVENT_S); inv_field('tools.wand_received', 'collected', 'I')
        click(); inv_field('tools.normal_received', 'collected', 'I')

        # Execute the unchanged real mobInteract and hurt bytecode, including
        # actual coffin conversion, lead attach/release and the /kill exception.
        env()
        print_value('mechanics.body_leashable', 'Z', lambda: (load(4), a.op(0xb6, cp.method(C, 'canBeLeashed', '()Z'))))
        obj(a, cp, DAMAGE); store(10)
        print_value('mechanics.ordinary_damage', 'Z', lambda: (load(4), load(10), a.raw(0x0c),
                      a.op(0xb6, cp.method(C, 'hurt', '(L' + DAMAGE + ';F)Z'))))
        assign(10, DAMAGE, 'generic', 'Z', lambda: boolean(True))
        print_value('mechanics.generic_kill', 'Z', lambda: (load(4), load(10), a.raw(0x0c),
                      a.op(0xb6, cp.method(C, 'hurt', '(L' + DAMAGE + ';F)Z'))))
        def mob_interact():
            load(4); load(3); a.raw(0x01); a.op(0xb6, cp.method(C, 'mobInteract',
                  '(L' + P + ';L' + HAND + ';)L' + INTERACTION + ';')); a.raw(0x57)
        def player_holds(kind, count):
            hand(kind); assign(12, IT, 'count', 'I', lambda: const(a, count))
            assign(3, P, 'held', 'L' + IT + ';', lambda: load(12))
        player_holds(TOOLS[-1], 1); mob_interact(); health('mechanics.boxed')
        print_value('mechanics.coffin_consumed', 'I', lambda: field(12, IT, 'count', 'I'))
        print_value('mechanics.coffin_sound', 'I', lambda: field(4, G, 'soundCount', 'I'))
        print_value('mechanics.coffin_leashable', 'Z', lambda: (load(4), a.op(0xb6, cp.method(C, 'canBeLeashed', '()Z'))))
        player_holds('lead', 1); mob_interact(); print_value('mechanics.attached', 'Z', lambda: field(4, G, 'leashed', 'Z'))
        player_holds(None, 0); mob_interact(); print_value('mechanics.released', 'Z', lambda: field(4, G, 'leashed', 'Z'))
        load(4); boolean(False); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        player_holds(None, 1); mob_interact(); print_value('mechanics.fallback', 'I', lambda: field(4, G, 'fallbackCount', 'I'))

        # Test real ctor/setCoffin/load Code with the real health helper.
        env(); health('health.created'); assign(4, G, 'invulnerable', 'Z', lambda: boolean(True))
        load(4); boolean(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V')); health('health.coffin')
        print_value('health.invulnerable', 'Z', lambda: field(4, G, 'invulnerable', 'Z'))
        load(4); boolean(False); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V')); health('health.body_again')
        obj(a, cp, NBT); store(7); assign(7, NBT, 'coffin', 'Z', lambda: boolean(True))
        load(4); load(7); a.op(0xb6, cp.method(C, 'readAdditionalSaveData', '(L' + NBT + ';)V')); health('health.loaded_coffin')
        assign(7, NBT, 'coffin', 'Z', lambda: boolean(False)); load(4); load(7)
        a.op(0xb6, cp.method(C, 'readAdditionalSaveData', '(L' + NBT + ';)V')); health('health.loaded_body')
        print_value('health.loaded_anim', 'Ljava/lang/String;', lambda: field(4, G, 'anim', 'Ljava/lang/String;'))
        obj(a, cp, L, '(ZL' + KEY + ';L' + SERVER + ';)V', lambda: (boolean(True), load(13), load(1))); store(9)
        obj(a, cp, C, '(L' + TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(9))); store(8)
        health('health.client_created', 8); load(8); boolean(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        health('health.client_coffin', 8)
        a.raw(0xb1)
        return a.finish()
    return f.fake_class('RecoveryHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 12, 15, main)])


def run_fixture(java, jar, helper_jar):
    with tempfile.TemporaryDirectory(prefix='corpse-recovery-jvm-') as td:
        folder = pathlib.Path(td)
        classes = build_classes(jar, helper_jar)
        classes['RecoveryHarness'] = harness_class()
        for name, data in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        r = subprocess.run([java, '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'RecoveryHarness'],
                           text=True, capture_output=True)
        if r.returncode:
            raise RuntimeError(f'JVM recovery fixture failed ({jar}):\n{r.stdout}\n{r.stderr}')
        return dict(line.split('=', 1) for line in r.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base237', required=True)
    ap.add_argument('--jar', required=True)
    args = ap.parse_args()
    old = run_fixture(args.java, args.base237, args.jar)
    assert old['pipeline.drops'] == '0' and old['pipeline.corpse_stacks'] == '1', old
    assert old['pipeline.still_queued'] == 'false' and old['pipeline.received'] == '0', old
    assert old['health.created.max'] == '60.0' and old['health.loaded_body.max'] == '20.0', old
    print('237 negative control: stored items not recovered; body maximum remains 60')
    got = run_fixture(args.java, args.jar, args.jar)
    expected = {
        'pipeline.drops': '0', 'pipeline.pending': 'false', 'pipeline.corpse_stacks': '1',
        'pipeline.blocked': '0', 'pipeline.still_queued': 'false', 'pipeline.specific_cancel': 'true',
        'pipeline.received': '5', 'pipeline.meta': 'mod:{enchanted:yes}', 'pipeline.remains': 'false',
        'pipeline.message': p.DONE_KEY, 'pipeline.messages': '1', 'pipeline.general_cancel': 'true',
        'pipeline.repeat': '5', 'pipeline.repeat_messages': '1',
        'ordinary.drops': '1', 'ordinary.pending': 'false', 'ordinary.corpse': 'false', 'ordinary.received': '0',
        'foreign.canceled': 'true', 'foreign.received': '0', 'foreign.stacks': '1', 'client.canceled': 'true',
        'client.stacks': '1', 'other_entity.canceled': 'false', 'other_entity.stacks': '1',
        'unowned.received': '0', 'unowned.stacks': '1',
        'full.received': '0', 'full.count': '5', 'full.message': p.PARTIAL_KEY,
        'full.retry': '5', 'full.remains': 'false', 'full.repeat': '5',
        'partial.received': '4', 'partial.stacks': '2', 'partial.count': '3', 'partial.id': 'mod:relic',
        'partial.meta': 'mod:{enchanted:yes}', 'partial.second': '2', 'partial.message': p.PARTIAL_KEY,
        'partial.retry': '9', 'partial.remains': 'false',
        'creative_control.inserted': '4', 'creative_control.stack_left': '0',
        'creative.inserted': '4', 'creative.left': '3', 'creative.meta': 'creative:components',
        'creative.retry': '7', 'creative.remains': 'false',
        'occupied.inserted': '4', 'occupied.left': '3', 'occupied.final_slot': '64',
        'damaged.blocked': '0', 'damaged.left': '1', 'damaged.retry': '1', 'damaged.remains': 'false',
        'multislot.inserted': '7', 'multislot.calls': '3', 'multislot.remains': 'false',
        'invalid.received': '4', 'invalid.stacks': '3', 'invalid.first': '2', 'invalid.partial': '3',
        'invalid.exception': '1', 'invalid.meta': 'unknown:b', 'invalid.repeat': '4',
        'two.first': '2', 'two.first_remains': 'false', 'two.second_remains': 'true',
        'two.second': '8', 'two.second_after': 'false',
        'reloaded.received': '6', 'reloaded.remains': 'false',
        'blocked.queue': '0', 'blocked.dimension': '0', 'blocked.dead': '0', 'blocked.no_server': '0',
        'blocked.allowed': '3',
        'tools.coffin_canceled': 'false', 'tools.coffin_received': '0', 'tools.lead_canceled': 'false',
        'tools.lead_received': '0', 'tools.wand_canceled': 'false', 'tools.wand_received': '0', 'tools.normal_received': '1',
        'mechanics.body_leashable': 'false', 'mechanics.ordinary_damage': 'false', 'mechanics.generic_kill': 'true',
        'mechanics.boxed.max': '60.0', 'mechanics.boxed.hp': '60.0', 'mechanics.coffin_consumed': '0',
        'mechanics.coffin_sound': '1', 'mechanics.coffin_leashable': 'true', 'mechanics.attached': 'true',
        'mechanics.released': 'false', 'mechanics.fallback': '1',
        'health.created.max': '1.0', 'health.created.hp': '1.0',
        'health.coffin.max': '60.0', 'health.coffin.hp': '60.0', 'health.invulnerable': 'true',
        'health.body_again.max': '1.0', 'health.body_again.hp': '1.0',
        'health.loaded_coffin.max': '60.0', 'health.loaded_coffin.hp': '60.0',
        'health.loaded_body.max': '1.0', 'health.loaded_body.hp': '1.0', 'health.loaded_anim': 'die',
        'health.client_created.max': '60.0', 'health.client_created.hp': '60.0',
        'health.client_coffin.max': '60.0', 'health.client_coffin.hp': '60.0',
    }
    if got != expected:
        missing = {k: v for k, v in expected.items() if got.get(k) != v}
        extra = {k: v for k, v in got.items() if k not in expected}
        raise AssertionError(f'mismatched={[(k, got.get(k), v) for k, v in missing.items()]} extra={extra}\nall={got}')
    print('238 JVM execution: capture -> self-exit -> owner click; authorization, full/partial/creative inventory,')
    print('codec errors, saved and distinct corpses, repeat safety, coffin/lead/damage, health create/load/transition OK')
    print('Fixture uses executable stand-ins for game APIs; it is not a NeoForge/in-game test.')


if __name__ == '__main__':
    main()
