#!/usr/bin/env python3
"""Build 245 from the confirmed build 244, without a Java compiler.

A paid, successful teleport records a one-use death UUID; actual repossession
consumes it and advances this body's wear. The standard, syncable max-health
attribute gets one namespaced permanent modifier. A narrowly targeted mixin
recalculates that modifier only when an existing max-health attribute is dirty,
so additive/multiplicative modifiers from other mods still compose to an exact
final -4/-8/-16 HP (subject to the attribute's own minimum). No player tick scan
is added. Both corpse spawners and capture use the same eligibility guard.
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
import patch_altar_corpse241 as p
import patch_altar_corpse242 as q
import patch_corpse_click244 as prev
import patch_death_scope243 as d
import patch_noqueue243 as nq
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke, constant_ref
from patch_earlytex_mixin import CP, u2, u4, attr
from patch_probe233 import Asm

BASE_SHA = 'd9e558d17ba4ff123e6588d7048a5b0cdfbf359eac092101553da54b1eaae2c8'
OLD_VERSION = prev.NEW_VERSION
NEW_VERSION = '0.8.0-build245-body-wear'
WEAR = 'dev/settledlands/npc/BodyWear'
MIXIN = 'dev/settledlands/mixin/BodyWearAttributeMixin'
MIXIN_SIMPLE = 'BodyWearAttributeMixin'
PS = 'dev/settledlands/npc/PossessionSystem'
POSS = p.POSS
GUARD, NQ = d.GUARD, nq.NQ
SP, P, E, SL, C = p.SP, prev.P, p.E, p.SL, p.CORPSE
NBT, EXT = p.NBT, p.EXT
HOLDER = 'net/minecraft/core/Holder'
LIVING = 'net/minecraft/world/entity/LivingEntity'
ATTRIBUTE = 'net/minecraft/world/entity/ai/attributes/Attribute'
ATTRIBUTES = 'net/minecraft/world/entity/ai/attributes/Attributes'
ATTR = 'net/minecraft/world/entity/ai/attributes/AttributeInstance'
MOD = 'net/minecraft/world/entity/ai/attributes/AttributeModifier'
OP = MOD + '$Operation'
RL = 'net/minecraft/resources/ResourceLocation'
CLONE = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$Clone'
LOGIN = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent'
CALLBACK = 'org/spongepowered/asm/mixin/injection/callback/CallbackInfo'
COUNT = 'settledlands_body_wear_count'
ACTIVE = 'settledlands_body_wear_active'
PAID = 'settledlands_body_wear_paid_death'
LAST = 'settledlands_body_wear_last_death'
CURRENT = p.DEATH_CORPSE
PENDING = 'settledlands_body_pending'
ID_NS, ID_PATH = 'settledlands', 'body_wear'
SPVOID = '(L' + SP + ';)V'
SPBOOL = '(L' + SP + ';)Z'
PAID_DESC = '(L' + SP + ';L' + C + ';)V'
CANCEL_DESC = '(Ljava/util/List;Ljava/util/UUID;)V'
CANCEL_BRIDGE = 'settledlands$cancelDeadWatches'
WATCH = POSS + '$DeadWatch'
DIRTY_DESC = '(Ljava/lang/Object;L' + HOLDER + ';)V'
CHANGED = {GUARD + '.class', NQ + '.class', PS + '.class', POSS + '.class',
           'settledlands.mixins.json', 'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
ADDED = {WEAR + '.class', MIXIN + '.class'}
CHANGELOG = ('Build 245: an inhabited body wears down on successful paid altar revival: '
             'maximum health is reduced by 4, 8, then 16; the fourth death leaves no body '
             'and uses normal world drops. Other health modifiers are preserved.')
PATCHINFO = ('сборка 245: после платных возвращений в одно тело максимум здоровья снижается '
             'на 4, 8 и 16; при четвёртой смерти тело не создаётся, действуют обычные правила дропа. '
             'Другие модификаторы максимального здоровья сохраняются.')


def build_wear():
    cp = p.Pool(); this_i, super_i, code_name = cp.cls(WEAR), cp.cls('java/lang/Object'), cp.utf8('Code')
    m, fld = cp.method, cp.field
    specs = []
    def a_load(a, i): p.xlocal(a, 'aload', i)
    def a_store(a, i): p.xlocal(a, 'astore', i)
    def i_load(a, i): p.xlocal(a, 'iload', i)
    def i_store(a, i): p.xlocal(a, 'istore', i)
    def d_load(a, i): p.xlocal(a, 'dload', i)
    def d_store(a, i): a.raw(0x47 + i) if i < 4 else a.raw(0x39, i)
    def text(a, s): p.ldc(a, cp, s)
    def dbl(a, n):
        if n == 0.0: a.raw(0x0e)
        elif n == 1.0: a.raw(0x0f)
        else: a.op(0x14, cp.double(n))
    def data(a, i):
        a_load(a, i); a.op(0xc0, cp.cls(EXT))
        p.invokeinterface(a, cp, EXT, 'getPersistentData', '()L' + NBT + ';', 1)
    def getid(a): a.op(0xb2, fld(WEAR, 'ID', 'L' + RL + ';'))
    def flag(a, idx, key):
        a_load(a, idx); text(a, key); a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z'))
    def hasuuid(a, idx, key):
        a_load(a, idx); text(a, key); a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z'))
    def uuid(a, idx, key):
        a_load(a, idx); text(a, key); a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    def remove(a, idx, key):
        a_load(a, idx); text(a, key); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    def add(name, desc, stack, locals_, asm, flags=0x0009):
        specs.append((flags, name, desc, stack, locals_, asm.finish() if isinstance(asm, Asm) else asm))
    def call_sync(a, i): a_load(a, i); a.op(0xb8, m(WEAR, 'sync', SPVOID))
    def get_attr(a, i):
        a_load(a, i); a.op(0xb2, fld(ATTRIBUTES, 'MAX_HEALTH', 'L' + HOLDER + ';'))
        a.op(0xb6, m(LIVING, 'getAttribute', '(L' + HOLDER + ';)L' + ATTR + ';'))
    def li(a, owner, name, desc, n): p.invokeinterface(a, cp, owner, name, desc, n)

    # One stable resource ID; only this mod's own modifier is ever replaced.
    a = Asm(); text(a, ID_NS); text(a, ID_PATH)
    a.op(0xb8, m(RL, 'fromNamespaceAndPath', '(Ljava/lang/String;Ljava/lang/String;)L' + RL + ';'))
    a.op(0xb3, fld(WEAR, 'ID', 'L' + RL + ';')); a.raw(0xb1)
    add('<clinit>', '()V', 2, 0, a, 0x0008)

    # Corrupt or future values fail safely within the three defined wear levels.
    a = Asm(); a.raw(0x03, 0x06); a_load(a, 0); text(a, COUNT)
    a.op(0xb6, m(NBT, 'getInt', '(Ljava/lang/String;)I'))
    a.op(0xb8, m('java/lang/Math', 'min', '(II)I')); a.op(0xb8, m('java/lang/Math', 'max', '(II)I'))
    a.raw(0xac); add('count', '(L' + NBT + ';)I', 4, 1, a, 0x000a)

    # Exactly the two existing death guards call this. The guard's purgatory and
    # server checks remain unchanged; ordinary (bodiless) deaths still fail.
    a = Asm(); a_load(a, 0); a.op(0xb8, m(PS, 'isPossessed', '(L' + E + ';)Z')); a.jump(0x99, 'no')
    data(a, 0); a.op(0xb8, m(WEAR, 'count', '(L' + NBT + ';)I')); a.raw(0x06)
    a.jump(0xa1, 'yes')
    # A delayed watch from an earlier death must not spawn a stale fallback
    # after the fourth death. Only this owner's watches/maps are removed.
    a_load(a, 0); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb8, m(POSS, CANCEL_BRIDGE, '(Ljava/util/UUID;)V')); a.jump(0xa7, 'no')
    a.label('yes'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac)
    add('canSpawn', SPBOOL, 2, 1, a)

    # Delete only watches for this owner; the caller bridge owns the private
    # list. No reflection, cross-player cleanup or per-tick scan is introduced.
    a = Asm(); a_load(a, 1); a.jump(0xc6, 'done'); a_load(a, 0)
    li(a, 'java/util/List', 'iterator', '()Ljava/util/Iterator;', 1); a_store(a, 2)
    a.label('loop'); a_load(a, 2); li(a, 'java/util/Iterator', 'hasNext', '()Z', 1)
    a.jump(0x99, 'done'); a_load(a, 2); li(a, 'java/util/Iterator', 'next', '()Ljava/lang/Object;', 1)
    a.op(0xc0, cp.cls(WATCH)); a_store(a, 3); a_load(a, 1); a_load(a, 3)
    a.op(0xb6, m(WATCH, 'uuid', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'loop')
    a_load(a, 2); li(a, 'java/util/Iterator', 'remove', '()V', 1); a.jump(0xa7, 'loop')
    a.label('done'); a.raw(0xb1); add('cancelWatches', CANCEL_DESC, 2, 4, a)

    # Record a committed payment for this exact current death. This does not
    # itself increase wear: the player must actually repossess that same body.
    a = Asm(); a_load(a, 0); a.jump(0xc6, 'done'); a_load(a, 1); a.jump(0xc6, 'done')
    data(a, 0); a_store(a, 2); flag(a, 2, PENDING); a.jump(0x99, 'done')
    hasuuid(a, 2, CURRENT); a.jump(0x99, 'done'); uuid(a, 2, CURRENT); a_store(a, 3)
    a_load(a, 3); a_load(a, 1); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    hasuuid(a, 2, LAST); a.jump(0x99, 'mark'); uuid(a, 2, LAST); a_load(a, 3)
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, 'done')
    a.label('mark'); a_load(a, 2); text(a, PAID); a_load(a, 3)
    a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
    a_load(a, 0); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb8, m(POSS, CANCEL_BRIDGE, '(Ljava/util/UUID;)V'))
    a.label('done'); a.raw(0xb1); add('recordPaid', PAID_DESC, 3, 4, a)

    # Run only after the existing repossess has written the possession fields.
    # A self-escape can also reach repossess through the old near-altar fallback:
    # reactivate existing wear, but never increase it without a paid UUID.
    a = Asm(); data(a, 0); a_store(a, 1)
    hasuuid(a, 1, PAID); a.jump(0x99, 'activate')
    hasuuid(a, 1, CURRENT); a.jump(0x99, 'forget_paid')
    uuid(a, 1, PAID); a_store(a, 2); uuid(a, 1, CURRENT); a_load(a, 2)
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'forget_paid')
    hasuuid(a, 1, LAST); a.jump(0x99, 'increment')
    uuid(a, 1, LAST); a_load(a, 2); a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))
    a.jump(0x9a, 'forget_paid')
    a.label('increment'); a_load(a, 1); text(a, COUNT); a.raw(0x06)
    a_load(a, 1); a.op(0xb8, m(WEAR, 'count', '(L' + NBT + ';)I')); a.raw(0x04, 0x60)
    a.op(0xb8, m('java/lang/Math', 'min', '(II)I')); a.op(0xb6, m(NBT, 'putInt', '(Ljava/lang/String;I)V'))
    a_load(a, 1); text(a, LAST); a_load(a, 2); a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
    a.label('forget_paid'); remove(a, 1, PAID)
    a.label('activate'); a_load(a, 1); text(a, ACTIVE); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V')); call_sync(a, 0); a.raw(0xb1)
    add('resume', SPVOID, 5, 3, a)

    # A new live NPC is a new body; abandoning one ends its active modifier.
    a = Asm(); data(a, 0); a_store(a, 1)
    for key in (COUNT, ACTIVE, PAID, LAST): remove(a, 1, key)
    call_sync(a, 0); a.raw(0xb1); add('reset', SPVOID, 2, 2, a)
    a = Asm(); data(a, 0); a_store(a, 1); remove(a, 1, ACTIVE)
    call_sync(a, 0); a.raw(0xb1); add('deactivate', SPVOID, 2, 2, a)

    # Clone is called for death and End-conquest; only death becomes bodiless.
    # UUID and count survive repeated deaths in purgatory. Pending payments
    # cannot survive a fresh death, but do survive a non-death clone or login.
    a = Asm(); a_load(a, 0); a.op(0xb6, m(CLONE, 'getEntity', '()L' + P + ';'))
    a.op(0xc1, cp.cls(SP)); a.jump(0x99, 'done'); a_load(a, 0)
    a.op(0xb6, m(CLONE, 'getEntity', '()L' + P + ';')); a.op(0xc0, cp.cls(SP)); a_store(a, 1)
    a_load(a, 0); a.op(0xb6, m(CLONE, 'getOriginal', '()L' + P + ';'))
    a.op(0xc0, cp.cls(EXT)); li(a, EXT, 'getPersistentData', '()L' + NBT + ';', 1); a_store(a, 2)
    data(a, 1); a_store(a, 3)
    a_load(a, 3); text(a, COUNT); a_load(a, 2); a.op(0xb8, m(WEAR, 'count', '(L' + NBT + ';)I'))
    a.op(0xb6, m(NBT, 'putInt', '(Ljava/lang/String;I)V'))
    hasuuid(a, 2, LAST); a.jump(0x99, 'clear_last'); a_load(a, 3); text(a, LAST); uuid(a, 2, LAST)
    a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V')); a.jump(0xa7, 'after_last')
    a.label('clear_last'); remove(a, 3, LAST); a.label('after_last')
    a_load(a, 0); a.op(0xb6, m(CLONE, 'isWasDeath', '()Z')); a.jump(0x9a, 'death')
    hasuuid(a, 2, PAID); a.jump(0x99, 'no_paid'); a_load(a, 3); text(a, PAID); uuid(a, 2, PAID)
    a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V')); a.jump(0xa7, 'paid_done')
    a.label('no_paid'); remove(a, 3, PAID); a.label('paid_done')
    a_load(a, 3); text(a, ACTIVE); flag(a, 2, ACTIVE); a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    a.jump(0xa7, 'apply')
    a.label('death'); remove(a, 3, PAID); remove(a, 3, ACTIVE)
    a.label('apply'); call_sync(a, 1); a.label('done'); a.raw(0xb1)
    add('clonePlayer', '(L' + CLONE + ';)V', 4, 4, a)

    a = Asm(); a_load(a, 0); a.op(0xb6, m(LOGIN, 'getEntity', '()L' + P + ';'))
    a.op(0xc1, cp.cls(SP)); a.jump(0x99, 'done'); a_load(a, 0)
    a.op(0xb6, m(LOGIN, 'getEntity', '()L' + P + ';')); a.op(0xc0, cp.cls(SP))
    a.op(0xb8, m(WEAR, 'sync', SPVOID)); a.label('done'); a.raw(0xb1)
    add('login', '(L' + LOGIN + ';)V', 1, 1, a)

    a = Asm(); i_load(a, 0); a.raw(0x04); a.jump(0xa0, 'second'); dbl(a, 4.0); a.raw(0xaf)
    a.label('second'); i_load(a, 0); a.raw(0x05); a.jump(0xa0, 'third'); dbl(a, 8.0); a.raw(0xaf)
    a.label('third'); dbl(a, 16.0); a.raw(0xaf); add('penalty', '(I)D', 2, 1, a, 0x000a)

    # Reproduce vanilla AttributeInstance.calculateValue without our own ID.
    # Three passes preserve operation ordering and the base-multiplier semantics.
    a = Asm(); a_load(a, 0); a.op(0xb6, m(ATTR, 'getBaseValue', '()D')); d_store(a, 1)
    a_load(a, 0); a.op(0xb6, m(ATTR, 'getModifiers', '()Ljava/util/Set;')); a_store(a, 3)
    for stage, operation in enumerate(('ADD_VALUE', 'ADD_MULTIPLIED_BASE', 'ADD_MULTIPLIED_TOTAL')):
        if stage == 1: d_load(a, 1); d_store(a, 4)
        a_load(a, 3); li(a, 'java/util/Set', 'iterator', '()Ljava/util/Iterator;', 1); a_store(a, 6)
        a.label('loop' + str(stage)); a_load(a, 6); li(a, 'java/util/Iterator', 'hasNext', '()Z', 1)
        a.jump(0x99, 'end' + str(stage)); a_load(a, 6); li(a, 'java/util/Iterator', 'next', '()Ljava/lang/Object;', 1)
        a.op(0xc0, cp.cls(MOD)); a_store(a, 7)
        a_load(a, 7); a.op(0xb6, m(MOD, 'id', '()L' + RL + ';')); getid(a)
        a.op(0xb6, m(RL, 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, 'loop' + str(stage))
        a_load(a, 7); a.op(0xb6, m(MOD, 'operation', '()L' + OP + ';'))
        a.op(0xb2, fld(OP, operation, 'L' + OP + ';')); a.jump(0xa6, 'loop' + str(stage))
        if stage == 0:
            d_load(a, 1); a_load(a, 7); a.op(0xb6, m(MOD, 'amount', '()D')); a.raw(0x63); d_store(a, 1)
        elif stage == 1:
            d_load(a, 4); d_load(a, 1); a_load(a, 7); a.op(0xb6, m(MOD, 'amount', '()D'))
            a.raw(0x6b, 0x63); d_store(a, 4)
        else:
            d_load(a, 4); dbl(a, 1.0); a_load(a, 7); a.op(0xb6, m(MOD, 'amount', '()D'))
            a.raw(0x63, 0x6b); d_store(a, 4)
        a.jump(0xa7, 'loop' + str(stage)); a.label('end' + str(stage))
    d_load(a, 4); a.raw(0xaf); add('unwornRaw', '(L' + ATTR + ';)D', 6, 8, a, 0x000a)

    # Calculate the desired *final* max, not a hardcoded base. A namespaced
    # ADD_MULTIPLIED_TOTAL modifier is solved to subtract a fixed HP amount
    # even when other mods multiply the maximum or vanilla's range caps it.
    a = Asm(); a_load(a, 0); a.jump(0xc6, 'done'); get_attr(a, 0); a_store(a, 1)
    a_load(a, 1); a.jump(0xc6, 'done'); data(a, 0); a_store(a, 2)
    flag(a, 2, ACTIVE); a.jump(0x99, 'remove'); a_load(a, 2)
    a.op(0xb8, m(WEAR, 'count', '(L' + NBT + ';)I')); i_store(a, 3)
    i_load(a, 3); a.jump(0x9e, 'remove')
    a_load(a, 1); a.op(0xb8, m(WEAR, 'unwornRaw', '(L' + ATTR + ';)D')); d_store(a, 4)
    d_load(a, 4); a.op(0xb8, m('java/lang/Double', 'isFinite', '(D)Z')); a.jump(0x99, 'done')
    d_load(a, 4); dbl(a, 0.0); a.raw(0x97); a.jump(0x9e, 'remove')
    a_load(a, 1); a.op(0xb6, m(ATTR, 'getAttribute', '()L' + HOLDER + ';'))
    li(a, HOLDER, 'value', '()Ljava/lang/Object;', 1); a.op(0xc0, cp.cls(ATTRIBUTE)); a_store(a, 6)
    a_load(a, 6); d_load(a, 4); a.op(0xb6, m(ATTRIBUTE, 'sanitizeValue', '(D)D')); d_store(a, 7)
    a_load(a, 6); d_load(a, 7); i_load(a, 3); a.op(0xb8, m(WEAR, 'penalty', '(I)D'))
    a.raw(0x67); a.op(0xb6, m(ATTRIBUTE, 'sanitizeValue', '(D)D')); d_store(a, 9)
    d_load(a, 9); d_load(a, 7); a.raw(0x98); a.jump(0x9c, 'remove')
    d_load(a, 9); d_load(a, 4); a.raw(0x6f); dbl(a, 1.0); a.raw(0x67); d_store(a, 11)
    a_load(a, 1); getid(a); a.op(0xb6, m(ATTR, 'getModifier', '(L' + RL + ';)L' + MOD + ';')); a_store(a, 13)
    a_load(a, 13); a.jump(0xc6, 'replace'); a_load(a, 13); a.op(0xb6, m(MOD, 'operation', '()L' + OP + ';'))
    a.op(0xb2, fld(OP, 'ADD_MULTIPLIED_TOTAL', 'L' + OP + ';')); a.jump(0xa6, 'replace')
    a_load(a, 13); a.op(0xb6, m(MOD, 'amount', '()D')); d_load(a, 11); a.raw(0x67)
    a.op(0xb8, m('java/lang/Math', 'abs', '(D)D')); dbl(a, 1e-12); a.raw(0x98); a.jump(0x9b, 'done')
    a.label('replace'); a_load(a, 1); a.op(0xbb, cp.cls(MOD)); a.raw(0x59); getid(a); d_load(a, 11)
    a.op(0xb2, fld(OP, 'ADD_MULTIPLIED_TOTAL', 'L' + OP + ';'))
    a.op(0xb7, m(MOD, '<init>', '(L' + RL + ';DL' + OP + ';)V'))
    a.op(0xb6, m(ATTR, 'addOrReplacePermanentModifier', '(L' + MOD + ';)V'))
    # Health cannot stay above a newly reduced cap between attribute updates.
    a_load(a, 0); a_load(a, 0); a.op(0xb6, m(LIVING, 'getHealth', '()F'))
    a_load(a, 0); a.op(0xb6, m(LIVING, 'getMaxHealth', '()F'))
    a.op(0xb8, m('java/lang/Math', 'min', '(FF)F')); a.op(0xb6, m(LIVING, 'setHealth', '(F)V'))
    a.raw(0xb1); a.label('remove'); a_load(a, 1); getid(a)
    a.op(0xb6, m(ATTR, 'removeModifier', '(L' + RL + ';)Z')); a.raw(0x57)
    a.label('done'); a.raw(0xb1); add('sync', SPVOID, 7, 14, a)

    # Called from a HEAD injection into LivingEntity.onAttributeUpdated, which
    # vanilla already invokes only for attributes marked dirty. No periodic scan.
    a = Asm(); a_load(a, 0); a.op(0xc1, cp.cls(SP)); a.jump(0x99, 'done')
    a_load(a, 1); a.op(0xb2, fld(ATTRIBUTES, 'MAX_HEALTH', 'L' + HOLDER + ';'))
    li(a, HOLDER, 'is', '(L' + HOLDER + ';)Z', 2); a.jump(0x99, 'done')
    a_load(a, 0); a.op(0xc0, cp.cls(SP)); a_store(a, 2); data(a, 2); a_store(a, 3)
    flag(a, 3, ACTIVE); a.jump(0x99, 'done'); a_load(a, 3)
    a.op(0xb8, m(WEAR, 'count', '(L' + NBT + ';)I')); a.jump(0x9e, 'done')
    call_sync(a, 2); a.label('done'); a.raw(0xb1); add('attributeDirty', DIRTY_DESC, 2, 4, a)

    specs.insert(0, (0x0002, '<init>', '()V', 1, 1,
                     b'\x2a\xb7' + u2(m('java/lang/Object', '<init>', '()V')) + b'\xb1'))
    fields = [(0x001a, 'ID', 'L' + RL + ';')]
    field_bytes = b''.join(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(0)
                           for flags, name, desc in fields)
    bodies = []
    for flags, name, desc, stack, locals_, code in specs:
        info = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(0) + u2(0)
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) + attr(code_name, info))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() + u2(0x0031) + u2(this_i) + u2(super_i) +
            u2(0) + u2(len(fields)) + field_bytes + u2(len(bodies)) + b''.join(bodies) + u2(0))


def build_mixin():
    cp = CP(); code_name = cp.utf8('Code'); rva = cp.utf8('RuntimeVisibleAnnotations')
    ria = cp.utf8('RuntimeInvisibleAnnotations'); src = cp.utf8('SourceFile')
    this_i, super_i = cp.cls(MIXIN), cp.cls('java/lang/Object')
    init = b'\x2a\xb7' + u2(cp.method('java/lang/Object', '<init>', '()V')) + b'\xb1'
    init_attr = u2(1) + u2(1) + u4(len(init)) + init + u2(0) + u2(0)
    init_m = u2(1) + u2(cp.utf8('<init>')) + u2(cp.utf8('()V')) + u2(1) + attr(code_name, init_attr)
    desc = '(L' + HOLDER + ';L' + CALLBACK + ';)V'
    code = b'\x2a\x2b\xb8' + u2(cp.method(WEAR, 'attributeDirty', DIRTY_DESC)) + b'\xb1'
    code_attr = u2(2) + u2(3) + u4(len(code)) + code + u2(0) + u2(0)
    at_ann = (u2(cp.utf8('Lorg/spongepowered/asm/mixin/injection/At;')) + u2(1) +
              u2(cp.utf8('value')) + b's' + u2(cp.utf8('HEAD')))
    # method is String[]; at is At[]. Encode both as arrays, as javac does.
    inject_ann = (u2(cp.utf8('Lorg/spongepowered/asm/mixin/injection/Inject;')) + u2(2) +
                  u2(cp.utf8('method')) + b'[' + u2(1) + b's' +
                  u2(cp.utf8('onAttributeUpdated(L' + HOLDER + ';)V')) +
                  u2(cp.utf8('at')) + b'[' + u2(1) + b'@' + at_ann)
    handler = (u2(2) + u2(cp.utf8('settledlands$wearAttributeDirty')) + u2(cp.utf8(desc)) +
               u2(2) + attr(code_name, code_attr) + attr(rva, u2(1) + inject_ann))
    mix_ann = (u2(cp.utf8('Lorg/spongepowered/asm/mixin/Mixin;')) + u2(1) +
               u2(cp.utf8('value')) + b'[' + u2(1) + b'c' + u2(cp.utf8('L' + LIVING + ';')))
    attrs = attr(src, u2(cp.utf8('BodyWearAttributeMixin.java'))) + attr(ria, u2(1) + mix_ann)
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(65) + cp.dump() + u2(0x0421) + u2(this_i) + u2(super_i) +
            u2(0) + u2(0) + u2(2) + init_m + handler + u2(2) + attrs)


def patch_guard(raw):
    for name in ('eligible', 'selectLevel'):
        k = classedit.Klass(raw); assert_pool_roundtrip(k, raw); met = k.find_method(name)
        parts = classedit.code_parts(k, met); code = bytearray(parts['code'])
        pc, size = find_invoke(k, code, PS, 'isPossessed', '(L' + E + ';)Z'); assert size == 3
        code[pc+1:pc+3] = u2(k.add_methodref(WEAR, 'canSpawn', SPBOOL)); parts['code'] = bytes(code)
        raw = k.dump(met, classedit.build_code_body(k, parts))
    return raw


def patch_transaction(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    met = k.find_method('transact'); parts = classedit.code_parts(k, met); code = parts['code']
    hits = [pc for pc,ins in decode.walk(code) if ins[0] == 0xb8 and
            constant_ref(k, struct.unpack('>H',ins[1:3])[0]) == (q.GATE,'finishStacks','(Ljava/util/List;)V')]
    assert len(hits) == 2, hits
    ref = k.add_methodref(WEAR, 'recordPaid', PAID_DESC)
    # Insert immediately *after* committing stacks in both the normal and the
    # exception-after-arrival path. These locations are not branch targets.
    for pc in reversed(hits):
        at = pc + 3; assert at not in decode.targets(parts['code'])
        parts = classedit.insert(parts, at, b'\x2d\x19\x04\xb8' + u2(ref))
    return k.dump(met, classedit.build_code_body(k, parts))


def insert_call(raw, name, desc, at_selector, method, call_desc, loads):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw); met = k.find_method(name, desc); assert met
    parts = classedit.code_parts(k, met); at = at_selector(k, parts['code'])
    assert at not in decode.targets(parts['code']), (name, at)
    parts = classedit.insert(parts, at, loads + b'\xb8' + u2(k.add_methodref(WEAR, method, call_desc)))
    parts['max_stack'] = max(parts['max_stack'], len(loads))
    return k.dump(met, classedit.build_code_body(k, parts))


def patch_possystem(raw):
    # Reset only after a live NPC has actually been consumed and possession set.
    possess_desc = '(L' + SP + ';L' + prev.GUIDE + ';)V'
    set_desc = '(L' + SP + ';Ljava/lang/String;Ljava/lang/String;)V'
    def after_set(k, code):
        pc, size = find_invoke(k, code, PS, 'setPossessed', set_desc); assert size == 3
        return pc + size
    raw = insert_call(raw, 'possess', possess_desc, after_set, 'reset', SPVOID, b'\x2a')
    raw = insert_call(raw, 'clearPossessed', SPVOID, lambda k,c:0, 'deactivate', SPVOID, b'\x2a')
    raw = insert_call(raw, 'onPlayerLogin', '(L' + LOGIN + ';)V', lambda k,c:0,
                      'login', '(L' + LOGIN + ';)V', b'\x2a')
    return raw


def patch_playercorpse(raw):
    raw = insert_call(raw, 'onPlayerClone', '(L' + CLONE + ';)V', lambda k,c:0,
                      'clonePlayer', '(L' + CLONE + ';)V', b'\x2a')
    def after_name(k, code):
        hits = [pc for pc,ins in decode.walk(code) if ins[0] == 0xb6 and
                constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (NBT,'putString','(Ljava/lang/String;Ljava/lang/String;)V')]
        assert len(hits) == 2
        return hits[1] + 3
    raw = insert_call(raw, 'repossess', '(L' + SP + ';Ljava/lang/String;Ljava/lang/String;)V',
                      after_name, 'resume', SPVOID, b'\x2a')
    # A straight-line public bridge safely accesses the class's existing
    # private lists. The iterator/branches are in our version-49 helper, not a
    # new unframed branch in this major-65 class. Never touch other owners.
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    assert k.find_method(CANCEL_BRIDGE, '(Ljava/util/UUID;)V') is None
    code = b'\xb2' + u2(k.add_fieldref(POSS, 'DEAD_WATCHES', 'Ljava/util/List;')) + b'\x2a\xb8' + \
           u2(k.add_methodref(WEAR, 'cancelWatches', CANCEL_DESC))
    for name in ('PENDING', 'STOCK_CORPSES'):
        code += (b'\xb2' + u2(k.add_fieldref(POSS, name, 'Ljava/util/Map;')) + b'\x2a\xb9' +
                 u2(k.add_interfaceref('java/util/Map', 'remove', '(Ljava/lang/Object;)Ljava/lang/Object;')) +
                 b'\x02\x00\x57')
    code += b'\xb1'
    method = {'acc':0x0009, 'name_idx':k.add_utf8(CANCEL_BRIDGE), 'desc_idx':k.add_utf8('(Ljava/util/UUID;)V'),
              'attrs':[{'name_idx':k.add_utf8('Code'), 'name':'Code', 'body':prev.code_body(2,1,code)}]}
    return prev.rebuild(k, k.methods + [method])


def patch_entries(entries):
    updated = dict(entries); assert not (ADDED & set(entries))
    for cls, fun in ((GUARD,patch_guard),(NQ,patch_transaction),(PS,patch_possystem),(POSS,patch_playercorpse)):
        updated[cls+'.class'] = fun(entries[cls+'.class'])
    updated[WEAR+'.class'] = build_wear(); updated[MIXIN+'.class'] = build_mixin()
    config = json.loads(entries['settledlands.mixins.json']); assert MIXIN_SIMPLE not in config['mixins']
    config['mixins'].append(MIXIN_SIMPLE)
    updated['settledlands.mixins.json'] = (json.dumps(config, indent=2, ensure_ascii=False)+'\n').encode()
    text = entries['META-INF/neoforge.mods.toml'].decode(); needle = 'version="'+OLD_VERSION+'"'
    marker = "description='''"
    assert text.count(needle)==1 and text.count(marker)==1
    text = text.replace(needle,'version="'+NEW_VERSION+'"',1)
    text = text.replace(marker,marker+CHANGELOG+'\n\n',1)
    assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION
    updated['META-INF/neoforge.mods.toml'] = text.encode()
    updated['settledlands-patch-info.txt'] = (PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
    changed = {n for n in entries if entries[n] != updated[n]}
    assert changed == CHANGED and set(updated)-set(entries)==ADDED, changed
    return updated


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--base',type=pathlib.Path,required=True)
    ap.add_argument('--out',type=pathlib.Path,required=True); a=ap.parse_args()
    sha=hashlib.sha256(a.base.read_bytes()).hexdigest()
    if sha!=BASE_SHA: raise SystemExit('unexpected base SHA-256: '+sha)
    if a.base.resolve()==a.out.resolve(): raise SystemExit('refusing to overwrite base')
    with zipfile.ZipFile(a.base) as base:
        assert base.testzip() is None and len(base.namelist())==len(set(base.namelist()))
        entries={i.filename:base.read(i.filename) for i in base.infolist()}; updated=patch_entries(entries)
        a.out.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(a.out,'w') as out:
            for info in base.infolist(): out.writestr(info,updated[info.filename])
            for name in sorted(ADDED):
                info=zipfile.ZipInfo(name,(2026,10,28,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
                info.external_attr=0o100644<<16
                out.writestr(info,updated[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(a.out) as z: assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
    data=a.out.read_bytes(); print('changed:',sorted(CHANGED),'added:',sorted(ADDED))
    print(f'{a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__=='__main__': main()
