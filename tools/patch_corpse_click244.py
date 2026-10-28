#!/usr/bin/env python3
"""Build 244 from build 243, without javac.

Owner clicks retry the existing provenance-checked pending->corpse transfer before
using the strict recovery code. Ambiguous/conflicting pending lists are never
merged or discarded. The server interaction paths consume handled clicks; the
client-specific path returns PASS so delayed coffin/leash state cannot block the
general packet. Meaningful coffin/lead/editor interactions keep their priority.
The corpse's entity fallback is protected too; ordinary dialogue entry points
refuse GuideCorpseNpc but are otherwise byte-for-byte delegated to the originals.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import sys
import tomllib
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classedit
import decode
import patch_altar_corpse241 as a241
import patch_corpse_reclaim238 as r
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke
from patch_earlytex_mixin import CP, u2, u4, attr
from patch_probe233 import Asm

BASE_SHA = 'a78c2943ec481aab0ed9b9fa67b6aa09a905d019b46b9866a5e6feab91cc584c'
OLD_VERSION = '0.8.0-build243-body-without-queue'
NEW_VERSION = '0.8.0-build244-corpse-click'
GUARD = 'dev/settledlands/npc/CorpseClickGuard'
DIALOGUE = 'dev/settledlands/npc/GuideDialogue'
GUIDE = 'dev/settledlands/npc/GuideNpc'
C, IVY, REC = r.CORPSE, r.IVY, r.HELPER
E, P, SP, L, SL, SERVER = r.ENTITY, r.PLAYER, r.SERVER_PLAYER, r.LEVEL, r.SERVER_LEVEL, r.SERVER
EXT, NBT, IT, KEY = r.EXT, r.COMPOUND, r.STACK, r.RESOURCE_KEY
HAND, RESULT = 'net/minecraft/world/InteractionHand', 'net/minecraft/world/InteractionResult'
ITEM, ITEMS = 'net/minecraft/world/item/Item', 'net/minecraft/world/item/Items'
COFFIN = 'dev/settledlands/npc/CoffinItem'
EVENT_G, EVENT_S = r.EVENTS['onInteractCorpse'], r.EVENTS['onInteractSpecificCorpse']
ALTAR, POSS, PD, PM = a241.HELPER, a241.POSS, r.PURGATORY_DATA, r.PURGATORY
CLAIM_DESC = '(L' + P + ';L' + E + ';)V'
MOB_DESC = '(L' + P + ';L' + HAND + ';)L' + RESULT + ';'
INTERACT_DESC = '(L' + C + ';L' + P + ';L' + HAND + ';)L' + RESULT + ';'
START_DESC = '(L' + P + ';L' + GUIDE + ';)V'
LISTEN_DESC = '(L' + SP + ';L' + GUIDE + ';)Z'
ALIAS_CLAIM = 'settledlands$originalTryClaim'
ALIAS_MOB = 'settledlands$originalMobInteract'
ALIAS_START = 'settledlands$originalStart'
ALIAS_LISTEN = 'settledlands$originalListenToNpc'
EDIT_BRIDGE = 'settledlands$isEditingTool'
CORPSE_ITEMS, PLAYER_ITEMS, DEATH_ID, SOURCE_ID = (a241.CORPSE_ITEMS, a241.PLAYER_ITEMS,
                                                   a241.DEATH_CORPSE, a241.ITEMS_CORPSE)
MISSING = 'message.settledlands.corpse.items_missing'
UNVERIFIED = 'message.settledlands.corpse.items_unverified'
CONFLICT = 'message.settledlands.corpse.items_conflict'
CHANGED = {C + '.class', IVY + '.class', REC + '.class', DIALOGUE + '.class', ALTAR + '.class',
           'assets/settledlands/lang/en_us.json', 'assets/settledlands/lang/ru_ru.json',
           'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
ADDED = {GUARD + '.class'}
CHANGELOG = ('Build 244: an owner can recover proven pending items by right-clicking their corpse or coffin; '
             'conflicting or unverified lists are preserved. Corpses do not start ordinary dialogue, '
             'and meaningful coffin, leash and editing-tool actions retain priority.')
PATCHINFO = ('сборка 244: ПКМ владельца по своему трупу или гробу повторно переносит подтверждённые '
             'отложенные вещи и безопасно возвращает их. Неясные и конфликтующие списки сохраняются. '
             'Обычный разговор с трупом отключён; гроб, поводок и инструменты сохранены.')


def member(m):
    attrs = b''.join(u2(x['name_idx']) + u4(len(x['body'])) + x['body'] for x in m['attrs'])
    return u2(m['acc']) + u2(m['name_idx']) + u2(m['desc_idx']) + u2(len(m['attrs'])) + attrs


def rebuild(k, methods):
    attrs = b''.join(u2(x['name_idx']) + u4(len(x['body'])) + x['body'] for x in k.attrs)
    return (k.raw[:8] + u2(len(k.cp)) + k._pool_bytes() + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) + u2(len(k.fields)) +
            b''.join(member(m) for m in k.fields) + u2(len(methods)) + b''.join(member(m) for m in methods) +
            u2(len(k.attrs)) + attrs)


def code_body(stack, locals_, code, exc=()):
    return (u2(stack) + u2(locals_) + u4(len(code)) + code + u2(len(exc)) +
            b''.join(u2(s) + u2(e) + u2(h) + u2(t) for s, e, h, t in exc) + u2(0))


def alias_and_forward(raw, name, desc, alias, guard_method, guard_desc, loads, ret):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    m = k.find_method(name, desc); assert m is not None and k.find_method(alias, desc) is None
    orig = [x for x in m['attrs'] if x['name'] == 'Code']; assert len(orig) == 1
    original_code = orig[0]['body']
    ref = k.add_methodref(GUARD, guard_method, guard_desc)
    raw = k.dump(m, code_body(len(loads), len(loads), bytes(loads) + b'\xb8' + u2(ref) + bytes([ret])))
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    m = k.find_method(name, desc)
    alias_method = dict(m)
    alias_method['acc'] = (m['acc'] & ~0x0006) | 0x0001  # original implementation, callable by helper
    alias_method['name_idx'] = k.add_utf8(alias)
    alias_method['name'] = alias
    alias_method['attrs'] = [dict(name_idx=k.add_utf8('Code'), name='Code', body=original_code)]
    return rebuild(k, k.methods + [alias_method])


def patch_recovery(raw):
    return alias_and_forward(raw, 'tryClaim', CLAIM_DESC, ALIAS_CLAIM, 'claim', CLAIM_DESC,
                             (0x2a, 0x2b), 0xb1)


def patch_corpse(raw):
    return alias_and_forward(raw, 'mobInteract', MOB_DESC, ALIAS_MOB, 'interact', INTERACT_DESC,
                             (0x2a, 0x2b, 0x2c), 0xb0)


def patch_dialogue(raw):
    raw = alias_and_forward(raw, 'start', START_DESC, ALIAS_START, 'dialogueStart', START_DESC,
                            (0x2a, 0x2b), 0xb1)
    return alias_and_forward(raw, 'listenToNpc', LISTEN_DESC, ALIAS_LISTEN, 'dialogueListen', LISTEN_DESC,
                             (0x2a, 0x2b), 0xac)


def patch_ivy(raw):
    for name, event, call in [('onInteractCorpse', EVENT_G, 'onGeneral'),
                              ('onInteractSpecificCorpse', EVENT_S, 'onSpecific')]:
        k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
        m = k.find_method(name, '(L' + event + ';)V'); assert m
        ref = k.add_methodref(GUARD, call, '(L' + event + ';)V')
        raw = k.dump(m, code_body(1, 1, b'\x2a\xb8' + u2(ref) + b'\xb1'))
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    desc = '(L' + IT + ';)Z'; assert k.find_method('editingTool', desc) and not k.find_method(EDIT_BRIDGE, desc)
    ref = k.add_methodref(IVY, 'editingTool', desc)
    method = dict(acc=0x0009, name_idx=k.add_utf8(EDIT_BRIDGE), desc_idx=k.add_utf8(desc),
                  attrs=[dict(name_idx=k.add_utf8('Code'), name='Code',
                              body=code_body(1, 1, b'\x2a\xb8' + u2(ref) + b'\xac'))])
    return rebuild(k, k.methods + [method])


def patch_altar(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    m = k.find_method('guardLegacyRestore', '(L' + SP + ';)V'); assert m
    parts = classedit.code_parts(k, m); code = bytearray(parts['code'])
    pc, width = find_invoke(k, code, POSS, 'restoreBodyItems', '(L' + SP + ';)V')
    assert width == 3 and code[pc] == 0xb8
    code[pc + 1:pc + 3] = u2(k.add_methodref(GUARD, 'guardLegacyRestore', '(L' + SP + ';)V'))
    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def build_guard():
    cp = CP(); this_i, super_i, code_i = cp.cls(GUARD), cp.cls('java/lang/Object'), cp.utf8('Code')
    m, fld = cp.method, cp.field
    specs = []
    def add(name, desc, stack, locals_, asm, flags=0x0009, exceptions=()):
        code = asm.finish() if isinstance(asm, Asm) else asm
        specs.append((flags, name, desc, stack, locals_, code, exceptions))
    def load(a, n): r.local(a, 'aload', n)
    def store(a, n): r.local(a, 'astore', n)
    def text(a, s): r.ldc(a, cp, s)
    def data(a, n):
        load(a, n); a.op(0xc0, cp.cls(EXT)); r.invokeinterface(a, cp, EXT, 'getPersistentData', '()L' + NBT + ';', 1)
    def contains(a, local, key):
        load(a, local); text(a, key); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z'))
    def feedback(a, player, key):
        load(a, player); text(a, key); a.op(0xb8, m(GUARD, 'message', '(L' + SP + ';Ljava/lang/String;)V'))

    # message(ServerPlayer,String): the same class-based Component factory as 238.
    a = Asm(); a.raw(0x2a); a.op(0xbb, cp.cls(r.TRANSLATABLE_CONTENTS)); a.raw(0x59, 0x2b, 0x01)
    a.op(0xb2, fld(r.TRANSLATABLE_CONTENTS, 'NO_ARGS', '[Ljava/lang/Object;'))
    a.op(0xb7, m(r.TRANSLATABLE_CONTENTS, '<init>', '(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V'))
    a.op(0xb8, m(r.MUTABLE, 'create', '(L' + r.COMPONENT_CONTENTS + ';)L' + r.MUTABLE + ';'))
    a.raw(0x04); a.op(0xb6, m(SP, 'displayClientMessage', '(L' + r.COMPONENT + ';Z)V')); a.raw(0xb1)
    add('message', '(L' + SP + ';Ljava/lang/String;)V', 6, 2, a, 0x000a)

    # claim: duplicate the existing owner/alive/dimension guards BEFORE moving
    # anything, then use the actual strict recovery body. A pending list can be
    # transferred only with BOTH current-death and source UUIDs via the real
    # 241 movePendingSafely helper. If both lists might describe this body,
    # never let the old recovery delete the body or merge ambiguous lists.
    a = Asm(); load(a, 0); a.op(0xc1, cp.cls(SP)); a.jump(0x99, 'done')
    load(a, 1); a.op(0xc1, cp.cls(C)); a.jump(0x99, 'done')
    load(a, 0); a.op(0xc0, cp.cls(SP)); store(a, 2)
    load(a, 1); a.op(0xc0, cp.cls(C)); store(a, 3)
    load(a, 2); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'done')
    load(a, 3); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'done')
    load(a, 3); a.op(0xb6, m(C, r.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 4)
    load(a, 4); a.jump(0xc6, 'done'); load(a, 4); load(a, 2)
    a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    load(a, 2); a.op(0xb6, m(SP, 'getServer', '()L' + SERVER + ';')); store(a, 5)
    load(a, 5); a.jump(0xc6, 'done'); load(a, 5)
    a.op(0xb8, m(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';')); load(a, 4)
    a.op(0xb6, m(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z')); a.jump(0x9a, 'done')
    a.op(0xb2, fld(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')); load(a, 2)
    a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';'))
    a.op(0xb6, m(SL, 'dimension', '()L' + KEY + ';'))
    a.op(0xb6, m('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, 'done')
    data(a, 2); store(a, 6); data(a, 3); store(a, 7)
    load(a, 2); load(a, 3); a.op(0xb8, m(ALTAR, 'movePendingSafely', '(L' + SP + ';L' + C + ';)V'))
    contains(a, 6, PLAYER_ITEMS); a.jump(0x99, 'body_check')
    load(a, 6); text(a, SOURCE_ID); a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z'))
    a.jump(0x99, 'unverified')
    load(a, 6); text(a, SOURCE_ID); a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    load(a, 3); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'body_check')
    contains(a, 7, CORPSE_ITEMS); a.jump(0x99, 'unverified')
    feedback(a, 2, CONFLICT); a.raw(0xb1)
    a.label('unverified'); feedback(a, 2, UNVERIFIED); a.raw(0xb1)
    a.label('body_check'); contains(a, 7, CORPSE_ITEMS); a.jump(0x9a, 'claim')
    feedback(a, 2, MISSING); a.raw(0xb1)
    a.label('claim'); load(a, 2); load(a, 3); a.op(0xb8, m(REC, ALIAS_CLAIM, CLAIM_DESC))
    a.label('done'); a.raw(0xb1)
    add('claim', CLAIM_DESC, 3, 8, a)

    # A lead on an ordinary body, or a coffin item on an already boxed body,
    # cannot perform its special action; in those cases they can reclaim loot.
    a = Asm(); load(a, 1); a.op(0xb6, m(IT, 'getItem', '()L' + ITEM + ';'))
    a.op(0xc1, cp.cls(COFFIN)); a.jump(0x99, 'not_coffin_item')
    # The synchronized coffin flag can lag on the client. Always send both
    # relevant packets for lead/coffin items there; the server decides whether
    # this specific action is meaningful and may instead claim the body.
    load(a, 0); a.op(0xb6, m(C, 'level', '()L' + L + ';')); a.op(0xb6, m(L, 'isClientSide', '()Z'))
    a.jump(0x9a, 'yes')
    load(a, 0); a.op(0xb6, m(C, 'isCoffin', '()Z')); a.jump(0x9a, 'no')
    a.label('yes'); a.raw(0x04, 0xac)
    a.label('not_coffin_item'); load(a, 1); a.op(0xb2, fld(ITEMS, 'LEAD', 'L' + ITEM + ';'))
    a.op(0xb6, m(IT, 'is', '(L' + ITEM + ';)Z')); a.jump(0x99, 'editor')
    load(a, 0); a.op(0xb6, m(C, 'level', '()L' + L + ';')); a.op(0xb6, m(L, 'isClientSide', '()Z'))
    a.jump(0x9a, 'yes')
    load(a, 0); a.op(0xb6, m(C, 'isCoffin', '()Z')); a.raw(0xac)
    a.label('editor'); load(a, 1); a.op(0xb8, m(IVY, EDIT_BRIDGE, '(L' + IT + ';)Z')); a.raw(0xac)
    a.label('no'); a.raw(0x03, 0xac)
    add('isTool', '(L' + C + ';L' + IT + ';)Z', 2, 2, a)

    # Allow an empty main hand to yield to a meaningful off-hand tool.
    a = Asm(); load(a, 2); a.op(0xb2, fld(HAND, 'MAIN_HAND', 'L' + HAND + ';')); a.jump(0xa6, 'no')
    load(a, 1); load(a, 2); a.op(0xb6, m(P, 'getItemInHand', '(L' + HAND + ';)L' + IT + ';'))
    a.op(0xb6, m(IT, 'isEmpty', '()Z')); a.jump(0x99, 'no')
    load(a, 0); load(a, 1); a.op(0xb2, fld(HAND, 'OFF_HAND', 'L' + HAND + ';'))
    a.op(0xb6, m(P, 'getItemInHand', '(L' + HAND + ';)L' + IT + ';'))
    a.op(0xb8, m(GUARD, 'isTool', '(L' + C + ';L' + IT + ';)Z')); a.raw(0xac)
    a.label('no'); a.raw(0x03, 0xac)
    add('deferOffhand', '(L' + C + ';L' + P + ';L' + HAND + ';)Z', 3, 3, a)

    # Empty-hand unlink is retained when no owner loot is waiting. A non-owner
    # can detach their own lead without gaining access to the stored items.
    # Client persistent tags need not be synced; the server decides again.
    a = Asm(); load(a, 0); a.op(0xb6, m(C, 'isCoffin', '()Z')); a.jump(0x99, 'no')
    load(a, 0); a.op(0xb6, m(C, 'isLeashed', '()Z')); a.jump(0x99, 'no')
    load(a, 0); a.op(0xb6, m(C, 'getLeashHolder', '()L' + E + ';')); load(a, 1); a.jump(0xa6, 'no')
    load(a, 2); a.op(0xb6, m(IT, 'isEmpty', '()Z')); a.jump(0x99, 'no')
    load(a, 0); a.op(0xb6, m(C, r.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 3)
    load(a, 3); a.jump(0xc6, 'yes'); load(a, 3); load(a, 1)
    a.op(0xb6, m(P, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'yes')
    data(a, 0); text(a, CORPSE_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x9a, 'no')
    data(a, 1); text(a, PLAYER_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x9a, 'no')
    a.label('yes'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac)
    add('mayUnlink', '(L' + C + ';L' + P + ';L' + IT + ';)Z', 2, 4, a)

    # Actual event wrappers retain SubscribeEvent annotations. Keep the client
    # specific result PASS so vanilla sends the general packet too: its local
    # coffin/leash state may lag behind the server. The client general event is
    # consumed after its packet is sent, preventing unwanted off-hand actions.
    # Both server paths are idempotent after removal and preserve partial NBT.
    for event, name in ((EVENT_G, 'onGeneral'), (EVENT_S, 'onSpecific')):
        a = Asm(); load(a, 0); a.op(0xb6, m(event, 'getTarget', '()L' + E + ';')); store(a, 1)
        load(a, 1); a.op(0xc1, cp.cls(C)); a.jump(0x99, 'done')
        load(a, 1); a.op(0xc0, cp.cls(C)); store(a, 2)
        load(a, 0); a.op(0xb6, m(event, 'getEntity', '()L' + P + ';')); store(a, 3)
        load(a, 0); a.op(0xb6, m(event, 'getItemStack', '()L' + IT + ';')); store(a, 4)
        load(a, 2); load(a, 4); a.op(0xb8, m(GUARD, 'isTool', '(L' + C + ';L' + IT + ';)Z'))
        a.jump(0x9a, 'done')
        load(a, 2); load(a, 3); load(a, 0); a.op(0xb6, m(event, 'getHand', '()L' + HAND + ';'))
        a.op(0xb8, m(GUARD, 'deferOffhand', '(L' + C + ';L' + P + ';L' + HAND + ';)Z'))
        a.jump(0x9a, 'done')
        load(a, 2); load(a, 3); load(a, 4); a.op(0xb8, m(GUARD, 'mayUnlink', '(L' + C + ';L' + P + ';L' + IT + ';)Z'))
        a.jump(0x9a, 'done')
        load(a, 3); load(a, 2); a.op(0xb8, m(REC, 'tryClaim', CLAIM_DESC))
        load(a, 0)
        if name == 'onSpecific':
            load(a, 3); a.op(0xb6, m(P, 'level', '()L' + L + ';'))
            a.op(0xb6, m(L, 'isClientSide', '()Z')); a.jump(0x99, 'server_result')
            a.op(0xb2, fld(RESULT, 'PASS', 'L' + RESULT + ';')); a.jump(0xa7, 'set_result')
            a.label('server_result'); a.op(0xb2, fld(RESULT, 'SUCCESS', 'L' + RESULT + ';'))
            a.label('set_result')
        else:
            a.op(0xb2, fld(RESULT, 'SUCCESS', 'L' + RESULT + ';'))
        a.op(0xb6, m(event, 'setCancellationResult', '(L' + RESULT + ';)V'))
        load(a, 0); a.raw(0x04); a.op(0xb6, m(event, 'setCanceled', '(Z)V'))
        a.label('done'); a.raw(0xb1)
        add(name, '(L' + event + ';)V', 3, 5, a)

    # Entity fallback even if the event was not dispatched. Preserve the real
    # coffin conversion and lead/editor implementation by calling its alias.
    a = Asm(); load(a, 1); load(a, 2); a.op(0xb6, m(P, 'getItemInHand', '(L' + HAND + ';)L' + IT + ';')); store(a, 3)
    load(a, 0); load(a, 1); load(a, 2)
    a.op(0xb8, m(GUARD, 'deferOffhand', '(L' + C + ';L' + P + ';L' + HAND + ';)Z'))
    a.jump(0x99, 'not_deferred'); a.op(0xb2, fld(RESULT, 'PASS', 'L' + RESULT + ';')); a.raw(0xb0)
    a.label('not_deferred'); load(a, 0); load(a, 3)
    a.op(0xb8, m(GUARD, 'isTool', '(L' + C + ';L' + IT + ';)Z')); a.jump(0x9a, 'original')
    load(a, 0); load(a, 1); load(a, 3)
    a.op(0xb8, m(GUARD, 'mayUnlink', '(L' + C + ';L' + P + ';L' + IT + ';)Z')); a.jump(0x9a, 'original')
    load(a, 1); load(a, 0); a.op(0xb8, m(REC, 'tryClaim', CLAIM_DESC))
    load(a, 0); a.op(0xb6, m(C, 'level', '()L' + L + ';')); a.op(0xb6, m(L, 'isClientSide', '()Z'))
    a.op(0xb8, m(RESULT, 'sidedSuccess', '(Z)L' + RESULT + ';')); a.raw(0xb0)
    a.label('original'); load(a, 0); load(a, 1); load(a, 2)
    a.op(0xb6, m(C, ALIAS_MOB, MOB_DESC)); a.raw(0xb0)
    add('interact', INTERACT_DESC, 3, 4, a)

    # All ordinary dialogue starts/listen paths, including a bypassed event or
    # editor test, delegate unchanged for a live GuideNpc, not for a corpse.
    a = Asm(); load(a, 1); a.op(0xc1, cp.cls(C)); a.jump(0x9a, 'done')
    load(a, 0); load(a, 1); a.op(0xb8, m(DIALOGUE, ALIAS_START, START_DESC))
    a.label('done'); a.raw(0xb1)
    add('dialogueStart', START_DESC, 2, 2, a)
    a = Asm(); load(a, 1); a.op(0xc1, cp.cls(C)); a.jump(0x99, 'live')
    a.raw(0x03, 0xac); a.label('live'); load(a, 0); load(a, 1)
    a.op(0xb8, m(DIALOGUE, ALIAS_LISTEN, LISTEN_DESC)); a.raw(0xac)
    add('dialogueListen', LISTEN_DESC, 2, 2, a)

    # The old automatic restore is unsafe for partial inventories. Leave any
    # pending list untouched so a later proven corpse click can handle it.
    a = Asm(); data(a, 0); text(a, PLAYER_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x9a, 'done')
    load(a, 0); a.op(0xb8, m(POSS, 'restoreBodyItems', '(L' + SP + ';)V'))
    a.label('done'); a.raw(0xb1)
    add('guardLegacyRestore', '(L' + SP + ';)V', 2, 1, a)

    add('<init>', '()V', 1, 1, b'\x2a\xb7' + u2(m('java/lang/Object', '<init>', '()V')) + b'\xb1', 0x0002)
    bodies = []
    for flags, name, desc, stack, locals_, code, exc in specs:
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) +
                      attr(code_i, code_body(stack, locals_, code, exc)))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() + u2(0x0031) + u2(this_i) + u2(super_i) +
            u2(0) + u2(0) + u2(len(bodies)) + b''.join(bodies) + u2(0))


def add_messages(raw, translations):
    text = raw.decode('utf-8'); old = json.loads(text); assert text.endswith('\n}\n')
    assert not set(translations) & set(old)
    for key, value in translations.items():
        text = text[:-3] + ',\n  ' + json.dumps(key) + ': ' + json.dumps(value, ensure_ascii=False) + '\n}\n'
    new = json.loads(text); assert {k: v for k, v in new.items() if k not in translations} == old
    return text.encode('utf-8')


def patch_entries(entries):
    updated = dict(entries)
    for cls, fn in [(REC, patch_recovery), (C, patch_corpse), (IVY, patch_ivy),
                    (DIALOGUE, patch_dialogue), (ALTAR, patch_altar)]:
        updated[cls + '.class'] = fn(entries[cls + '.class'])
    updated[GUARD + '.class'] = build_guard()
    translations = {
        'ru_ru': {MISSING: 'В этом теле нет сохранённых предметов.',
                  UNVERIFIED: 'Происхождение отложенных вещей не подтверждено: вещи сохранены.',
                  CONFLICT: 'Два набора вещей: выдача приостановлена, ничего не удалено.'},
        'en_us': {MISSING: 'There are no saved items in this body.',
                  UNVERIFIED: 'The pending items could not be verified; they were kept safe.',
                  CONFLICT: 'Two item lists conflict. Recovery paused; nothing was removed.'},
    }
    for lang, messages in translations.items():
        path = f'assets/settledlands/lang/{lang}.json'
        updated[path] = add_messages(entries[path], messages)
    path = 'META-INF/neoforge.mods.toml'; original = entries[path].decode('utf-8')
    assert original.count(OLD_VERSION) == 1
    changed = original.replace(OLD_VERSION, NEW_VERSION, 1)
    marker = "description='''"; assert changed.count(marker) == 1
    changed = changed.replace(marker, marker + CHANGELOG + '\n\n', 1)
    assert tomllib.loads(changed)['mods'][0]['version'] == NEW_VERSION
    updated[path] = changed.encode('utf-8')
    path = 'settledlands-patch-info.txt'; updated[path] = (PATCHINFO + '\n\n').encode() + entries[path]
    assert set(updated) - set(entries) == ADDED
    assert {name for name in entries if entries[name] != updated[name]} == CHANGED
    return updated


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--base', type=pathlib.Path, required=True)
    parser.add_argument('--out', type=pathlib.Path, required=True); args = parser.parse_args()
    if args.base.resolve() == args.out.resolve(): raise SystemExit('refusing to overwrite base')
    if hashlib.sha256(args.base.read_bytes()).hexdigest() != BASE_SHA: raise SystemExit('unexpected build 243 SHA-256')
    with zipfile.ZipFile(args.base) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
        infos = z.infolist(); entries = {i.filename: z.read(i.filename) for i in infos}
    updated = patch_entries(entries)
    with zipfile.ZipFile(args.out, 'w') as z:
        for info in infos: z.writestr(info, updated[info.filename])
        for name in sorted(ADDED):
            info = zipfile.ZipInfo(name, (2026, 10, 28, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16; z.writestr(info, updated[name])
    with zipfile.ZipFile(args.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
        assert {name: z.read(name) for name in z.namelist()} == updated
    print('build 244:', args.out, 'bytes', args.out.stat().st_size, 'sha256', hashlib.sha256(args.out.read_bytes()).hexdigest())


if __name__ == '__main__': main()
