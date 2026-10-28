#!/usr/bin/env python3
"""Execute actual possession and NPC-local-variable bytecode on Java 21.

The real 239 bridge is a negative control: it overwrites missing/zero NPC-local
`культ` from the PLAYER's personal/shared points, then the gate accepts. 240
must reject those cases without changing the NPC, while real local values >= 1
still work. Minecraft/NeoForge APIs below are executable test doubles, not the
game, and no javac is used.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile
import jvm_altar242 as altar
import patch_cultprobe243 as probe

import jvm_recovery238 as r
from patch_probe233 import Asm

f = r.f
E, P, SP, G, C = r.E, r.P, r.SP, r.G, r.C
L, SERVER, NBT, TAG, EXT = r.L, r.SERVER, r.NBT, r.TAG, r.EXT
IT, ITEM, INV = r.IT, r.ITEM, r.INV
PS = r.POSS_SYS
BRIDGE = 'dev/settledlands/npc/PossessionCultBridge'
DIALOGUE = 'dev/settledlands/npc/GuideDialogue'
POINTS = 'dev/settledlands/npc/NpcPoints'
NPC_INV = PS + '$NpcInventory'
SYNC = 'dev/settledlands/npc/PossessionClientSync'
CONTENT = 'dev/settledlands/npc/NpcContent'
EVENT = r.EVENT_S
HOLDER = r.HOLDER
REF = HOLDER + '$Reference'
REG_ACCESS = 'net/minecraft/core/RegistryAccess'
LOOKUP = 'net/minecraft/core/HolderLookup$RegistryLookup'
FAKE_REG = 'fixture/CultRegistry'
FAKE_LOOKUP = 'fixture/CultLookup'
REGISTRIES = 'net/minecraft/core/registries/Registries'
RESOURCE = 'net/minecraft/resources/ResourceLocation'
KEY = 'net/minecraft/resources/ResourceKey'
ITEM_ENTITY = r.ITEM_ENTITY

op, text, obj, const = r.op, r.text, r.obj, r.const
getfield = r.getfield


def increment(cp, owner, name):
    index = cp.field(owner, name, 'I')
    return b'\x2a\x59' + op(0xb4, index) + b'\x04\x60' + op(0xb5, index)


def nbt_class():
    # Simplified NBT: integer/boolean/string values and nested compounds live in
    # a Java HashMap. getCompound returns a new unattached empty tag if missing,
    # just like vanilla. Numeric getInt returns zero for missing/non-numeric.
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method('java/lang/Object', '<init>', '()V'))
        a.raw(0x2a); obj(a, cp, 'java/util/HashMap'); a.op(0xb5, cp.field(NBT, 'map', 'Ljava/util/HashMap;'))
        a.raw(0xb1); return a.finish()
    def lookup(a, cp):
        a.raw(0x2a); a.op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')); a.raw(0x2b)
        a.op(0xb6, cp.method('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;'))
    def compound(cp):
        a = Asm(); lookup(a, cp); a.raw(0x4d, 0x2c); a.op(0xc1, cp.cls(NBT)); a.jump(0x99, 'empty')
        a.raw(0x2c); a.op(0xc0, cp.cls(NBT)); a.raw(0xb0)
        a.label('empty'); obj(a, cp, NBT); a.raw(0xb0); return a.finish()
    def get_int(cp):
        a = Asm(); lookup(a, cp); a.raw(0x4d, 0x2c); a.op(0xc1, cp.cls('java/lang/Number')); a.jump(0x99, 'zero')
        a.raw(0x2c); a.op(0xc0, cp.cls('java/lang/Number')); a.op(0xb6, cp.method('java/lang/Number', 'intValue', '()I'))
        a.raw(0xac); a.label('zero'); a.raw(0x03, 0xac); return a.finish()
    def get_bool(cp):
        a = Asm(); lookup(a, cp); a.raw(0x4d, 0x2c); a.op(0xc1, cp.cls('java/lang/Boolean')); a.jump(0x99, 'zero')
        a.raw(0x2c); a.op(0xc0, cp.cls('java/lang/Boolean')); a.op(0xb6, cp.method('java/lang/Boolean', 'booleanValue', '()Z'))
        a.raw(0xac); a.label('zero'); a.raw(0x03, 0xac); return a.finish()
    def get_string(cp):
        a = Asm(); lookup(a, cp); a.raw(0x4d, 0x2c); a.op(0xc1, cp.cls('java/lang/String')); a.jump(0x99, 'empty')
        a.raw(0x2c); a.op(0xc0, cp.cls('java/lang/String')); a.raw(0xb0)
        a.label('empty'); text(a, cp, ''); a.raw(0xb0); return a.finish()
    def put_value(kind):
        def generate(cp):
            a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')); a.raw(0x2b)
            if kind == 'I':
                a.raw(0x1c); a.op(0xb8, cp.method('java/lang/Integer', 'valueOf', '(I)Ljava/lang/Integer;'))
            elif kind == 'Z':
                a.raw(0x1c); a.op(0xb8, cp.method('java/lang/Boolean', 'valueOf', '(Z)Ljava/lang/Boolean;'))
            else:
                a.raw(0x2c)
            a.op(0xb6, cp.method('java/util/HashMap', 'put', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;'))
            a.raw(0x57)
            if kind == 'tag': a.raw(0x01, 0xb0)  # callers here ignore the old Tag
            else: a.raw(0xb1)
            return a.finish()
        return generate
    def contains(cp):
        return (b'\x2a' + op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, cp.method('java/util/HashMap', 'containsKey', '(Ljava/lang/Object;)Z')) + b'\xac')
    def contains_type(cp):
        a = Asm(); lookup(a, cp); a.op(0xc1, cp.cls(NBT)); a.raw(0xac); return a.finish()
    def remove(cp):
        return (b'\x2a' + op(0xb4, cp.field(NBT, 'map', 'Ljava/util/HashMap;')) + b'\x2b' +
                op(0xb6, cp.method('java/util/HashMap', 'remove', '(Ljava/lang/Object;)Ljava/lang/Object;')) + b'\x57\xb1')
    return f.fake_class(NBT, [(1, 'map', 'Ljava/util/HashMap;')], [
        (1, '<init>', '()V', 3, 1, ctor),
        (1, 'contains', '(Ljava/lang/String;)Z', 2, 2, contains),
        (1, 'contains', '(Ljava/lang/String;I)Z', 2, 3, contains_type),
        (1, 'getCompound', '(Ljava/lang/String;)L' + NBT + ';', 2, 3, compound),
        (1, 'getInt', '(Ljava/lang/String;)I', 2, 3, get_int),
        (1, 'getBoolean', '(Ljava/lang/String;)Z', 2, 3, get_bool),
        (1, 'getString', '(Ljava/lang/String;)Ljava/lang/String;', 2, 3, get_string),
        (1, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';', 3, 3, put_value('tag')),
        (1, 'putInt', '(Ljava/lang/String;I)V', 3, 3, put_value('I')),
        (1, 'putBoolean', '(Ljava/lang/String;Z)V', 3, 3, put_value('Z')),
        (1, 'putString', '(Ljava/lang/String;Ljava/lang/String;)V', 3, 3, put_value('str')),
        (1, 'remove', '(Ljava/lang/String;)V', 2, 2, remove),
    ], interfaces=[TAG])


def level_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x1b' + op(0xb5, cp.field(L, 'client', 'Z')) +
                b'\x2a\x2c' + op(0xb5, cp.field(L, 'server', 'L' + SERVER + ';')) + b'\xb1')
    def spawn(cp):
        return increment(cp, L, 'drops') + b'\x04\xac'
    return f.fake_class(L, [(1, 'client', 'Z'), (1, 'server', 'L' + SERVER + ';'), (1, 'drops', 'I')], [
        (1, '<init>', '(ZL' + SERVER + ';)V', 2, 3, ctor),
        (1, 'isClientSide', '()Z', 1, 1, getfield(L, 'client', 'Z', 0xac)),
        (1, 'addFreshEntity', '(L' + E + ';)Z', 3, 2, spawn),
    ])


def item_stack_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x1b' + op(0xb5, cp.field(IT, 'count', 'I')) + b'\xb1')
    def empty(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(IT, 'count', 'I')); a.jump(0x9d, 'no')
        a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def copy(cp):
        a = Asm(); obj(a, cp, IT, '(I)V', lambda: (a.raw(0x2a), a.op(0xb4, cp.field(IT, 'count', 'I'))))
        a.raw(0xb0); return a.finish()
    def is_item(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(IT, 'kind', 'L' + ITEM + ';')); a.raw(0x2b)
        a.jump(0xa6, 'no'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    def clinit(cp):
        a = Asm(); obj(a, cp, IT, '(I)V', lambda: a.raw(0x03))
        a.op(0xb3, cp.field(IT, 'EMPTY', 'L' + IT + ';')); a.raw(0xb1); return a.finish()
    return f.fake_class(IT, [(9, 'EMPTY', 'L' + IT + ';'), (1, 'count', 'I'), (1, 'kind', 'L' + ITEM + ';')], [
        (8, '<clinit>', '()V', 3, 0, clinit), (1, '<init>', '(I)V', 2, 2, ctor),
        (1, 'isEmpty', '()Z', 1, 1, empty), (1, 'copy', '()L' + IT + ';', 3, 1, copy),
        (1, 'is', '(L' + ITEM + ';)Z', 2, 2, is_item),
    ])


def inventory_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x1b' + op(0xb5, cp.field(INV, 'room', 'I')) + b'\xb1')
    def add(cp):
        # Either all fits, or false leaves the entire input for the drop branch.
        a = Asm(); a.raw(0x2b); a.op(0xb4, cp.field(IT, 'count', 'I')); a.raw(0x2a)
        a.op(0xb4, cp.field(INV, 'room', 'I')); a.jump(0xa3, 'full')
        a.raw(0x2a, 0x59); a.op(0xb4, cp.field(INV, 'collected', 'I')); a.raw(0x2b)
        a.op(0xb4, cp.field(IT, 'count', 'I')); a.raw(0x60); a.op(0xb5, cp.field(INV, 'collected', 'I'))
        a.raw(0x2a, 0x59); a.op(0xb4, cp.field(INV, 'room', 'I')); a.raw(0x2b)
        a.op(0xb4, cp.field(IT, 'count', 'I')); a.raw(0x64); a.op(0xb5, cp.field(INV, 'room', 'I'))
        a.raw(0x2b, 0x03); a.op(0xb5, cp.field(IT, 'count', 'I')); a.raw(0x04, 0xac)
        a.label('full'); a.raw(0x03, 0xac); return a.finish()
    return f.fake_class(INV, [(1, 'room', 'I'), (1, 'collected', 'I')], [
        (1, '<init>', '(I)V', 2, 2, ctor), (1, 'add', '(L' + IT + ';)Z', 4, 2, add),
    ])


def guide_class():
    def ctor(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb7, cp.method(E, '<init>', '()V'))
        a.raw(0x2a, 0x2b); a.op(0xb5, cp.field(E, 'world', 'L' + L + ';'))
        a.raw(0x2a); obj(a, cp, IT, '(I)V', lambda: a.raw(0x03)); a.op(0xb5, cp.field(G, 'stack', 'L' + IT + ';'))
        a.raw(0xb1); return a.finish()
    def name(cp):
        a = Asm(); obj(a, cp, r.MUT, '(Ljava/lang/String;)V', lambda: text(a, cp, 'Test NPC')); a.raw(0xb0); return a.finish()
    def model(cp):
        a = Asm(); text(a, cp, 'model.bbmodel'); a.raw(0xb0); return a.finish()
    return f.fake_class(G, [(1, 'stack', 'L' + IT + ';'), (1, 'opens', 'I'), (1, 'changed', 'I')], [
        (1, '<init>', '(L' + L + ';)V', 4, 2, ctor),
        (1, 'getDisplayName', '()L' + r.COMP + ';', 3, 1, name),
        (1, 'getName', '()L' + r.COMP + ';', 3, 1, name),
        (1, 'texturePath', '()Ljava/lang/String;', 1, 1, model),
        (1, 'getX', '()D', 2, 1, lambda cp: b'\x0e\xaf'),
        (1, 'getY', '()D', 2, 1, lambda cp: b'\x0e\xaf'),
        (1, 'getZ', '()D', 2, 1, lambda cp: b'\x0e\xaf'),
    ], super_name=E)


def npc_inventory_class():
    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + op(0xb5, cp.field(NPC_INV, 'npc', 'L' + G + ';')) +
                b'\x2b\x59' + op(0xb4, cp.field(G, 'opens', 'I')) + b'\x04\x60' +
                op(0xb5, cp.field(G, 'opens', 'I')) + b'\xb1')
    def get_item(cp):
        return (b'\x2a' + op(0xb4, cp.field(NPC_INV, 'npc', 'L' + G + ';')) +
                op(0xb4, cp.field(G, 'stack', 'L' + IT + ';')) + b'\xb0')
    def set_item(cp):
        return (b'\x2a' + op(0xb4, cp.field(NPC_INV, 'npc', 'L' + G + ';')) + b'\x2c' +
                op(0xb5, cp.field(G, 'stack', 'L' + IT + ';')) + b'\xb1')
    def changed(cp):
        return (b'\x2a' + op(0xb4, cp.field(NPC_INV, 'npc', 'L' + G + ';')) +
                b'\x59' + op(0xb4, cp.field(G, 'changed', 'I')) + b'\x04\x60' +
                op(0xb5, cp.field(G, 'changed', 'I')) + b'\xb1')
    return f.fake_class(NPC_INV, [(1, 'npc', 'L' + G + ';')], [
        (1, '<init>', '(L' + G + ';)V', 3, 2, ctor),
        (1, 'getContainerSize', '()I', 1, 1, lambda cp: b'\x04\xac'),
        (1, 'getItem', '(I)L' + IT + ';', 1, 2, get_item),
        (1, 'setItem', '(IL' + IT + ';)V', 2, 3, set_item),
        (1, 'setChanged', '()V', 3, 1, changed),
    ])


def server_player_class():
    fields = [(1, 'bodiless', 'Z'), (1, 'inventory', 'L' + INV + ';'), (1, 'server', 'L' + SERVER + ';'),
              (1, 'lastMessage', 'Ljava/lang/String;'), (1, 'messageCount', 'I')]
    def ctor(cp):
        a = Asm(); a.raw(0x2a, 0x2b, 0x2c); a.op(0xb7, cp.method(P, '<init>', '(Ljava/util/UUID;L' + L + ';)V'))
        a.raw(0x2a, 0x04); a.op(0xb5, cp.field(SP, 'bodiless', 'Z'))
        a.raw(0x2a, 0x2c); a.op(0xb4, cp.field(L, 'server', 'L' + SERVER + ';')); a.op(0xb5, cp.field(SP, 'server', 'L' + SERVER + ';'))
        a.raw(0x2a); obj(a, cp, INV, '(I)V', lambda: a.raw(0x1d)); a.op(0xb5, cp.field(SP, 'inventory', 'L' + INV + ';'))
        a.raw(0xb1); return a.finish()
    def message(cp):
        a = Asm(); a.raw(0x2a, 0x2b); a.op(0xc0, cp.cls(r.MUT)); a.op(0xb4, cp.field(r.MUT, 'key', 'Ljava/lang/String;'))
        a.op(0xb5, cp.field(SP, 'lastMessage', 'Ljava/lang/String;')); a.raw(*increment(cp, SP, 'messageCount'))
        a.raw(0xb1); return a.finish()
    def remove_effect(cp):
        return b'\x2a\x03' + op(0xb5, cp.field(SP, 'bodiless', 'Z')) + b'\x04\xac'
    def registry(cp):
        a = Asm(); obj(a, cp, FAKE_REG); a.raw(0xb0); return a.finish()
    return f.fake_class(SP, fields, [
        (1, '<init>', '(Ljava/util/UUID;L' + L + ';I)V', 4, 4, ctor),
        (1, 'getServer', '()L' + SERVER + ';', 1, 1, getfield(SP, 'server', 'L' + SERVER + ';')),
        (1, 'getInventory', '()L' + INV + ';', 1, 1, getfield(SP, 'inventory', 'L' + INV + ';')),
        (1, 'registryAccess', '()L' + REG_ACCESS + ';', 2, 1, registry),
        (1, 'hasEffect', '(L' + HOLDER + ';)Z', 1, 2, getfield(SP, 'bodiless', 'Z', 0xac)),
        (1, 'removeEffect', '(L' + HOLDER + ';)Z', 2, 2, remove_effect),
        (1, 'displayClientMessage', '(L' + r.COMP + ';Z)V', 3, 3, message),
    ], super_name=P)


def points_class():
    def get_server(cp):
        a = Asm(); obj(a, cp, POINTS); a.raw(0xb0); return a.finish()
    def get_value(cp):
        a = Asm(); a.op(0xb2, cp.field(POINTS, 'reads', 'I')); a.raw(0x04, 0x60)
        a.op(0xb3, cp.field(POINTS, 'reads', 'I')); a.raw(0x1d); a.jump(0x99, 'personal')
        a.op(0xb2, cp.field(POINTS, 'shared', 'I')); a.raw(0xac)
        a.label('personal'); a.op(0xb2, cp.field(POINTS, 'personal', 'I')); a.raw(0xac); return a.finish()
    return f.fake_class(POINTS, [(9, 'shared', 'I'), (9, 'personal', 'I'), (9, 'reads', 'I')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (9, 'get', '(L' + SERVER + ';)L' + POINTS + ';', 2, 1, get_server),
        (1, 'get', '(Ljava/util/UUID;Ljava/lang/String;Z)I', 2, 4, get_value),
        (1, 'add', '(Ljava/util/UUID;Ljava/lang/String;ZI)I', 1, 5, lambda cp: b'\x03\xac'),
    ])


def logger_classes():
    interface = r.interface(r.LOGGER, [r.abstract('info', '(Ljava/lang/String;[Ljava/lang/Object;)V'),
                                        r.abstract('warn', '(Ljava/lang/String;Ljava/lang/Object;)V')])
    fake = f.fake_class(r.FLOGGER, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'info', '(Ljava/lang/String;[Ljava/lang/Object;)V', 0, 3, lambda cp: b'\xb1'),
        (1, 'warn', '(Ljava/lang/String;Ljava/lang/Object;)V', 0, 3, lambda cp: b'\xb1')], interfaces=[r.LOGGER])
    def get_logger(cp):
        a = Asm(); obj(a, cp, r.FLOGGER); a.raw(0xb0); return a.finish()
    utils = f.fake_class(r.LOGUTILS, [], [(9, 'getLogger', '()L' + r.LOGGER + ';', 2, 0, get_logger)])
    return interface, fake, utils


def build_classes(jar):
    component, mutable = r.component_classes()
    player_event, interact_event = r.event_parents()
    logger, fake_logger, utils = logger_classes()
    def return_new(name):
        def generate(cp):
            a = Asm(); obj(a, cp, name); a.raw(0xb0); return a.finish()
        return generate
    def registries_init(cp):
        a = Asm(); obj(a, cp, KEY); a.op(0xb3, cp.field(REGISTRIES, 'MOB_EFFECT', 'L' + KEY + ';'))
        a.raw(0xb1); return a.finish()
    def content_init(cp):
        a = Asm()
        for name in ('wand_item', 'mobility_item', 'cutscene_item'):
            obj(a, cp, ITEM); a.op(0xb3, cp.field(CONTENT, name, 'L' + ITEM + ';'))
        a.raw(0xb1); return a.finish()
    def push(cp):
        index = cp.field(SYNC, 'pushes', 'I')
        return op(0xb2, index) + b'\x04\x60' + op(0xb3, index) + b'\xb1'
    def item_entity_ctor(cp):
        return b'\x2a' + op(0xb7, cp.method(E, '<init>', '()V')) + b'\xb1'
    classes = {
        PS: r.subset(jar, PS, {'<clinit>', 'data', 'bodiless', 'isBodiless', 'cult', 'isOpTool',
                                'onEntityInteractSpecific', 'possess', 'setPossessed'}, expose={'cult'}),
        BRIDGE: r.subset(jar, BRIDGE, {'<clinit>', 'onInteractSpecific', 'mirror'}),
        DIALOGUE: r.subset(jar, DIALOGUE, {'localVars', 'readVarValue', 'addVarValue'},
                           expose={'localVars', 'readVarValue', 'addVarValue'}),
        TAG: r.interface(TAG), EXT: r.interface(EXT, [r.abstract('getPersistentData', '()L' + NBT + ';')]),
        NBT: nbt_class(), E: r.entity_class(),
        r.LIVING: f.fake_class(r.LIVING, [], [(1, '<init>', '()V', 1, 1, f.empty_init(E))], super_name=E),
        P: r.player_class(), SP: server_player_class(),
        r.ABILITIES: f.fake_class(r.ABILITIES, [], [(1, '<init>', '()V', 1, 1, f.empty_init())]),
        SERVER: f.blank_class(SERVER), L: level_class(), G: guide_class(),
        C: f.fake_class(C, [], [(1, '<init>', '(L' + L + ';)V', 2, 2,
            lambda cp: b'\x2a\x2b' + op(0xb7, cp.method(G, '<init>', '(L' + L + ';)V')) + b'\xb1')], super_name=G),
        ITEM: f.blank_class(ITEM), IT: item_stack_class(), INV: inventory_class(), NPC_INV: npc_inventory_class(),
        ITEM_ENTITY: f.fake_class(ITEM_ENTITY, [], [(1, '<init>', '(L' + L + ';DDDL' + IT + ';)V', 1, 9, item_entity_ctor)], super_name=E),
        SYNC: f.fake_class(SYNC, [(9, 'pushes', 'I')], [(9, 'push', '(L' + SERVER + ';Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V', 2, 4, push)]),
        CONTENT: f.fake_class(CONTENT, [(9, 'wand_item', 'L' + ITEM + ';'), (9, 'mobility_item', 'L' + ITEM + ';'),
                                       (9, 'cutscene_item', 'L' + ITEM + ';')], [
            (8, '<clinit>', '()V', 2, 0, content_init),
            (9, 'wand', '()L' + ITEM + ';', 1, 0, lambda cp: op(0xb2, cp.field(CONTENT, 'wand_item', 'L' + ITEM + ';')) + b'\xb0'),
            (9, 'mobilityWand', '()L' + ITEM + ';', 1, 0, lambda cp: op(0xb2, cp.field(CONTENT, 'mobility_item', 'L' + ITEM + ';')) + b'\xb0'),
            (9, 'cutsceneWand', '()L' + ITEM + ';', 1, 0, lambda cp: op(0xb2, cp.field(CONTENT, 'cutscene_item', 'L' + ITEM + ';')) + b'\xb0'),
        ]),
        POINTS: points_class(), r.LOGGER: logger, r.FLOGGER: fake_logger, r.LOGUTILS: utils,
        r.COMP: component, r.MUT: mutable, r.PLAYER_EVENT: player_event, r.INTERACT_EVENT: interact_event,
        EVENT: r.event_class(EVENT),
        HOLDER: r.interface(HOLDER), REF: f.fake_class(REF, [], [(1, '<init>', '()V', 1, 1, f.empty_init())], interfaces=[HOLDER]),
        REG_ACCESS: r.interface(REG_ACCESS, [r.abstract('lookupOrThrow', '(L' + KEY + ';)L' + LOOKUP + ';')]),
        LOOKUP: r.interface(LOOKUP, [r.abstract('getOrThrow', '(L' + KEY + ';)L' + REF + ';')]),
        FAKE_REG: f.fake_class(FAKE_REG, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (1, 'lookupOrThrow', '(L' + KEY + ';)L' + LOOKUP + ';', 2, 2, return_new(FAKE_LOOKUP))], interfaces=[REG_ACCESS]),
        FAKE_LOOKUP: f.fake_class(FAKE_LOOKUP, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (1, 'getOrThrow', '(L' + KEY + ';)L' + REF + ';', 2, 2, return_new(REF))], interfaces=[LOOKUP]),
        REGISTRIES: f.fake_class(REGISTRIES, [(9, 'MOB_EFFECT', 'L' + KEY + ';')], [(8, '<clinit>', '()V', 2, 0, registries_init)]),
        RESOURCE: f.fake_class(RESOURCE, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (9, 'fromNamespaceAndPath', '(Ljava/lang/String;Ljava/lang/String;)L' + RESOURCE + ';', 2, 2, return_new(RESOURCE))]),
        KEY: f.fake_class(KEY, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
            (9, 'create', '(L' + KEY + ';L' + RESOURCE + ';)L' + KEY + ';', 2, 2, return_new(KEY))]),
        'net/minecraft/world/effect/MobEffect': f.blank_class('net/minecraft/world/effect/MobEffect'),
    }
    # Builds >=243 optionally contain the read-only attempt probe. Add actual
    # bytecode and game API doubles only in that case, retaining older tests.
    with zipfile.ZipFile(jar) as archive:
        if probe.PROBE + '.class' in archive.namelist():
            classes[probe.PROBE] = archive.read(probe.PROBE + '.class')
            def adapter(k):
                from types import SimpleNamespace
                return SimpleNamespace(field=k.add_fieldref,method=k.add_methodref,cls=k.add_class,string=k.add_string)
            def tag_type(k):
                cp=adapter(k)
                a=Asm(); a.raw(0x2a); a.op(0xb4,cp.field(NBT,'map','Ljava/util/HashMap;')); a.raw(0x2b)
                a.op(0xb6,cp.method('java/util/HashMap','get','(Ljava/lang/Object;)Ljava/lang/Object;')); a.raw(0x4d)
                for typ,val in ((NBT,10),('java/lang/Number',3),('java/lang/String',8)):
                    a.raw(0x2c); a.op(0xc1,cp.cls(typ)); a.jump(0x99,'not_'+str(val)); const(a,val); a.raw(0xac)
                    a.label('not_'+str(val))
                a.raw(0x03,0xac); return a.finish()
            classes[NBT]=altar.rewrite(classes[NBT],add_methods=[(1,'getTagType','(Ljava/lang/String;)B',2,3,tag_type)])
            def uuid(k):
                cp=adapter(k)
                a=Asm(); text(a,cp,'00000000-0000-0000-0000-000000000099')
                a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;')); a.raw(0xb0);return a.finish()
            classes[E]=altar.rewrite(classes[E],add_methods=[(1,'getUUID','()Ljava/util/UUID;',1,1,uuid)])
            classes[IT]=altar.rewrite(classes[IT],add_methods=[(1,'getItem','()L'+ITEM+';',1,1,
                lambda k:getfield(IT,'kind','L'+ITEM+';',0xb0)(adapter(k)))])
            classes[r.INTERACT_EVENT]=altar.rewrite(classes[r.INTERACT_EVENT],add_methods=[
                (1,'getHand','()L'+probe.HAND+';',1,1,lambda cp:b'\x01\xb0')])
            classes[probe.HAND]=f.blank_class(probe.HAND)
    return classes


def harness_class():
    def main(cp):
        a = Asm()
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        printers = {d: cp.method('java/io/PrintStream', 'println', '(' + d + ')V')
                    for d in ('I', 'Z', 'Ljava/lang/String;')}
        def load(i): r.insn_local(a, 'aload', i)
        def store(i): r.insn_local(a, 'astore', i)
        def number(n): const(a, n)
        def string(s): text(a, cp, s)
        def boolean(b): a.raw(0x04 if b else 0x03)
        def object_(name, desc='()V', args=None): obj(a, cp, name, desc, args)
        def field(i, owner, name, desc): load(i); a.op(0xb4, cp.field(owner, name, desc))
        def assign(i, owner, name, desc, emit): load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def put_static(owner, name, desc, emit): emit(); a.op(0xb3, cp.field(owner, name, desc))
        def value(label, typ, emit):
            a.op(0xb2, out); string(label + '='); a.op(0xb6, print_s)
            a.op(0xb2, out); emit(); a.op(0xb6, printers[typ])
        def uuid():
            string('00000000-0000-0000-0000-000000000001')
            a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(index=4):
            load(index); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def local(index=4):
            data(index); string('settledlands_local_vars')
            a.op(0xb6, cp.method(NBT, 'getCompound', '(Ljava/lang/String;)L' + NBT + ';'))
        def local_int(index=4, key='культ'):
            local(index); string(key); a.op(0xb6, cp.method(NBT, 'getInt', '(Ljava/lang/String;)I'))
        def local_exists(index=4, key='культ'):
            local(index); string(key); a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
        def player_flag():
            data(3); string('settledlands_possess'); a.op(0xb6, cp.method(NBT, 'getBoolean', '(Ljava/lang/String;)Z'))
        def env(*, shared=0, personal=0, bodiless=True, room=20, npc_items=0):
            for name, val in (('shared', shared), ('personal', personal), ('reads', 0)):
                put_static(POINTS, name, 'I', lambda val=val: number(val))
            put_static(SYNC, 'pushes', 'I', lambda: number(0))
            object_(SERVER); store(1)
            object_(L, '(ZL' + SERVER + ';)V', lambda: (boolean(False), load(1))); store(2)
            object_(SP, '(Ljava/util/UUID;L' + L + ';I)V', lambda: (uuid(), load(2), number(room))); store(3)
            if not bodiless: assign(3, SP, 'bodiless', 'Z', lambda: boolean(False))
            object_(G, '(L' + L + ';)V', lambda: load(2)); store(4)
            if npc_items:
                field(4, G, 'stack', 'L' + IT + ';'); number(npc_items); a.op(0xb5, cp.field(IT, 'count', 'I'))
        def add_local(amount, key='культ', npc=4):
            # Real graph-variable writer; exposes the private method only in fixture.
            load(3); load(npc); string(key); boolean(False); boolean(True); number(amount)
            a.op(0xb8, cp.method(DIALOGUE, 'addVarValue', '(L' + SP + ';L' + G + ';Ljava/lang/String;ZZI)V'))
        def put_local_string(s):
            # Create compound using the real localVars(create=true), then wrong-typed key.
            load(4); boolean(True); a.op(0xb8, cp.method(DIALOGUE, 'localVars', '(L' + G + ';Z)L' + NBT + ';'))
            string('культ'); string(s); a.op(0xb6, cp.method(NBT, 'putString', '(Ljava/lang/String;Ljava/lang/String;)V'))
        def click(*, actor=3, target=4, tool=False, reverse=False):
            object_(IT, '(I)V', lambda: number(1 if tool else 0)); store(7)
            if tool:
                assign(7, IT, 'kind', 'L' + ITEM + ';',
                       lambda: a.op(0xb2, cp.field(CONTENT, 'wand_item', 'L' + ITEM + ';')))
            object_(EVENT, '(L' + P + ';L' + E + ';L' + IT + ';)V', lambda: (load(actor), load(target), load(7)))
            store(5)
            for owner, method in ([(PS, 'onEntityInteractSpecific'), (BRIDGE, 'onInteractSpecific')] if reverse else
                                  [(BRIDGE, 'onInteractSpecific'), (PS, 'onEntityInteractSpecific')]):
                load(5); a.op(0xb8, cp.method(owner, method, '(L' + EVENT + ';)V'))
        def report(label, npc=4):
            value(label + '.removed', 'Z', lambda: field(npc, E, 'removed', 'Z'))
            value(label + '.discard_count', 'I', lambda: field(npc, E, 'discardCount', 'I'))
            value(label + '.local_exists', 'Z', lambda: local_exists(npc))
            value(label + '.local_value', 'I', lambda: local_int(npc))
            value(label + '.canceled', 'Z', lambda: field(5, EVENT, 'canceled', 'Z'))
            value(label + '.possessed', 'Z', player_flag)
            value(label + '.bodiless', 'Z', lambda: field(3, SP, 'bodiless', 'Z'))
            value(label + '.inv', 'I', lambda: (field(3, SP, 'inventory', 'L' + INV + ';'), a.op(0xb4, cp.field(INV, 'collected', 'I'))))
            value(label + '.opens', 'I', lambda: field(npc, G, 'opens', 'I'))
            value(label + '.changed', 'I', lambda: field(npc, G, 'changed', 'I'))
            value(label + '.points_reads', 'I', lambda: a.op(0xb2, cp.field(POINTS, 'reads', 'I')))
            value(label + '.message', 'Ljava/lang/String;', lambda: field(3, SP, 'lastMessage', 'Ljava/lang/String;'))
        # The graph's writer really stores per-NPC local NBT. 239 used to copy
        # personal/shared PLAYER points into that NBT before the normal gate.
        env(personal=1, npc_items=4); click(); report('personal_missing')
        env(shared=1, npc_items=4); click(); report('shared_missing')
        env(personal=5, shared=9, npc_items=4); add_local(-3); click(); report('negative')
        env(personal=1, npc_items=4); load(4); boolean(True)
        a.op(0xb8, cp.method(DIALOGUE, 'localVars', '(L' + G + ';Z)L' + NBT + ';')); string('культ'); number(0)
        a.op(0xb6, cp.method(NBT, 'putInt', '(Ljava/lang/String;I)V')); click(); report('zero')
        env(personal=1, npc_items=4); put_local_string('1'); click(); report('text_value')
        env(personal=0, npc_items=4); add_local(1, key='Культ'); click(); report('wrong_case')
        env(npc_items=4); add_local(1); click(); report('one')
        env(npc_items=4); add_local(12); click(); report('many')
        # Simulate restored persistent data on a fresh NPC instance. This is not
        # a NeoForge save/load test; the real local-variable reader is exercised.
        env(); add_local(1); object_(G, '(L' + L + ';)V', lambda: load(2)); store(8)
        assign(8, E, 'data', 'L' + NBT + ';', lambda: field(4, E, 'data', 'L' + NBT + ';'))
        click(target=8); report('reloaded', npc=8)
        # A previously valid value can be removed, and corrupt root data must not
        # be overwritten just because the player has global/personal points.
        env(personal=1, npc_items=4); add_local(1); add_local(-1); click(); report('lowered')
        env(personal=1, npc_items=4); data(4); string('settledlands_local_vars'); string('not-a-compound')
        a.op(0xb6, cp.method(NBT, 'putString', '(Ljava/lang/String;Ljava/lang/String;)V'))
        click(); report('wrong_root')
        value('wrong_root.original', 'Ljava/lang/String;', lambda: (data(4), string('settledlands_local_vars'),
              a.op(0xb6, cp.method(NBT, 'getString', '(Ljava/lang/String;)Ljava/lang/String;'))))
        # No forced writes: after a denial the author explicitly adds the local
        # flag in the real dialog-graph code; only the second interaction works.
        env(personal=2, npc_items=4); click(); report('before_set')
        add_local(1); click(); report('after_set')
        # Separate NPCs, same player: only the clicked target's own NBT matters.
        env(npc_items=4); object_(G, '(L' + L + ';)V', lambda: load(2)); store(8); add_local(3, npc=8)
        click(); report('two_unmarked'); click(target=8); report('two_marked', npc=8)
        # Other existing gates and operator tools retain behavior.
        env(bodiless=False, npc_items=4); add_local(1); click(); report('not_bodiless')
        env(personal=1, npc_items=4); add_local(1); click(tool=True); report('wand')
        env(personal=1, npc_items=4); object_(C, '(L' + L + ';)V', lambda: load(2)); store(8)
        click(target=8); report('corpse', npc=8)
        env(personal=1, npc_items=4); object_(P, '(Ljava/util/UUID;L' + L + ';)V', lambda: (uuid(), load(2))); store(8)
        click(actor=8); report('client')
        # Event order must not matter: with the local flag absent and global set,
        # the gate rejects even if the bridge would have run after the gate.
        env(personal=2, npc_items=4); click(reverse=True); report('reverse')
        # Existing NPC inventory overflow behavior still runs only on success.
        env(room=0, npc_items=4); add_local(1); click(); report('overflow')
        value('overflow.world_drops', 'I', lambda: field(2, L, 'drops', 'I'))
        a.raw(0xb1); return a.finish()
    return f.fake_class('CultHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 12, 12, main)])


def run_fixture(java, jar):
    with tempfile.TemporaryDirectory(prefix='npc-cult-jvm-') as td:
        folder = pathlib.Path(td)
        classes = build_classes(jar)
        classes['CultHarness'] = harness_class()
        for name, data in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        p = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'CultHarness'],
                           capture_output=True, text=True)
        if p.returncode:
            raise RuntimeError(f'cult JVM fixture failed ({jar}):\n{p.stdout}\n{p.stderr}')
        return dict(line.split('=', 1) for line in p.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base239', required=True)
    ap.add_argument('--jar', required=True)
    args = ap.parse_args()
    old = run_fixture(args.java, args.base239)
    assert old['personal_missing.removed'] == 'true' and old['personal_missing.local_value'] == '1', old
    assert old['shared_missing.removed'] == 'true' and old['shared_missing.local_value'] == '1', old
    assert old['negative.removed'] == 'true' and old['negative.local_value'] == '1', old
    print('239 negative control: player points are copied into an unmarked NPC, enabling possession')
    got = run_fixture(args.java, args.jar)
    expected = {}
    def state(label, *, success=False, exists=False, val=0, msg=None, inv=0):
        expected.update({label + '.removed': str(success).lower(), label + '.discard_count': '1' if success else '0',
            label + '.local_exists': str(exists).lower(), label + '.local_value': str(val),
            label + '.canceled': str(success).lower(), label + '.possessed': str(success).lower(),
            label + '.bodiless': str(not success).lower(), label + '.inv': str(inv),
            label + '.opens': '1' if success else '0', label + '.changed': '1' if success else '0',
            label + '.points_reads': '0', label + '.message': msg if msg is not None else
                ('message.settledlands.possession.possessed' if success else 'message.settledlands.possession.no_cult')})
    state('personal_missing'); state('shared_missing'); state('negative', exists=True, val=-3)
    state('zero', exists=True); state('text_value', exists=True); state('wrong_case')
    state('one', success=True, exists=True, val=1, inv=4)
    state('many', success=True, exists=True, val=12, inv=4)
    state('reloaded', success=True, exists=True, val=1)
    state('lowered', exists=True); state('wrong_root'); expected['wrong_root.original'] = 'not-a-compound'
    state('before_set'); state('after_set', success=True, exists=True, val=1, inv=4)
    state('two_unmarked'); state('two_marked', success=True, exists=True, val=3)
    state('not_bodiless', exists=True, val=1, msg='null'); expected['not_bodiless.bodiless'] = 'false'
    state('wand', exists=True, val=1, msg='null'); state('corpse', msg='null'); state('client', msg='null')
    state('reverse'); state('overflow', success=True, exists=True, val=1); expected['overflow.world_drops'] = '1'
    if got != expected:
        mismatched = [(k, got.get(k), v) for k, v in expected.items() if got.get(k) != v]
        extras = {k: v for k, v in got.items() if k not in expected}
        raise AssertionError(f'mismatched={mismatched} extra={extras}\nall={got}')
    print('240 JVM execution: target local культ >= 1 only; personal/shared never mark NPCs;')
    print('real graph variable writer, two NPCs, 0/negative/string/case, tools and inventory OK')
    print('Synthetic dependencies; not a NeoForge/in-game test.')


if __name__ == '__main__':
    main()
