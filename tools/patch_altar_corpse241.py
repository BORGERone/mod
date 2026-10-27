#!/usr/bin/env python3
"""Build 241: altar resurrection appears on the owner's initiating body.

Keep the existing 9-emerald ritual, queue/selection, NPC resurrection and ordinary
player drops. The direct coffin path carries its exact corpse. On a new death the
stock spawn records its actual entity UUID on the player and clone; the general
path resolves only this ID in the loaded altar dimension. For old saves without a
recorded ID (or missing/unloaded bodies), retain the original altar destination
and never infer identity from ownership, location, or the number of bodies.

This is bytecode-only. A small generated helper uses the same 239 recovery code;
only successful, authorized full recovery (or a genuinely empty list) removes a
body. Remaining or malformed items stay, and attached leads are returned.
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
from patch_earlytex_mixin import CP, u2, u4, attr
from patch_probe233 import Asm
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke
from patch_corpse_reclaim238 import invokeinterface, ldc, local
import patch_corpse_reclaim238 as old

BASE_SHA = '2e4a29f6f147d6d081234ea5b7d3839b9a5ba297e984b3962b6f3113e47d76b5'
OLD_VERSION = '0.8.0-build240-npc-local-cult'
NEW_VERSION = '0.8.0-build241-altar-body-respawn'
CORPSE, E, SP, SL, SERVER = old.CORPSE, old.ENTITY, old.SERVER_PLAYER, old.SERVER_LEVEL, old.SERVER
L, EXT, NBT, TAG, LIST, PD, PM = old.LEVEL, old.EXT, old.COMPOUND, old.TAG, old.LISTTAG, old.PURGATORY_DATA, old.PURGATORY
KEY, RECOVERY = old.RESOURCE_KEY, old.HELPER
BLOCK, BOX, VEC = 'net/minecraft/core/BlockPos', 'net/minecraft/world/phys/AABB', 'net/minecraft/world/phys/Vec3'
POSS, MIXIN = 'dev/settledlands/npc/PlayerCorpsePossession', 'dev/settledlands/mixin/GuideCorpseKeepMixin'
LIFECYCLE = 'dev/settledlands/npc/CorpseLifecycle'
HELPER = 'dev/settledlands/npc/AltarCorpseRevival'
CORPSE_ITEMS = 'settledlands_corpse_items'
PLAYER_ITEMS = 'settledlands_body_items'
PLAYER_PENDING = 'settledlands_body_pending'
PLAYER_BODY = 'settledlands_player_body'
SUCCESS = 'settledlands_altar_revive_succeeded'
RESTORE = 'settledlands_altar_body_revived'
DEATH_CORPSE = 'settledlands_current_death_corpse'
ITEMS_CORPSE = 'settledlands_body_items_death_corpse'
SKIP_LEGACY = 'settledlands_altar_skip_legacy_items'
ALTAR_DESC = '(L' + SL + ';L' + SP + ';L' + BLOCK + ';)V'
TELEPORT_DESC = '(L' + SL + ';DDDLjava/util/Set;FF)Z'
DIRECT_DESC = '(L' + SP + ';L' + SL + ';DDDLjava/util/Set;FFL' + CORPSE + ';)Z'
BODY_DESC = '(L' + SP + ';L' + SL + ';L' + CORPSE + ';Ljava/util/Set;FF)Z'
FINISH_DESC = '(L' + CORPSE + ';L' + SP + ';)V'
SELECT_DESC = '(L' + SL + ';L' + SP + ';L' + BLOCK + ';)L' + CORPSE + ';'
ELIGIBLE_DESC = '(L' + SP + ';L' + CORPSE + ';L' + SL + ';)Z'
CHANGELOG = ('Build 241: altar resurrection returns the player to their own initiating corpse or coffin '
             'and safely recovers its stored items; the nine-emerald ritual and ordinary drops are unchanged.')
PATCHINFO = ('сборка 241: при возрождении на маяке игрок появляется у собственного тела-инициатора '
             'и получает его сохранённые вещи; остаток остаётся в теле, поводок возвращается. '
             'Расход девяти изумрудов и обычный дроп не меняются.')
CHANGED = {PM + '.class', CORPSE + '.class', MIXIN + '.class', POSS + '.class', LIFECYCLE + '.class',
           'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
ADDED = {HELPER + '.class'}


class Pool(CP):
    def double(self, value):
        key = ('double', value)
        if key in self.cache:
            return self.cache[key]
        idx = self._add(key, b'\x06' + struct.pack('>d', value))
        self.items.append(b'')  # long/double reserve the next constant-pool slot
        return idx


def xlocal(a, kind, i):
    if kind in ('aload', 'astore', 'iload', 'istore'):
        return local(a, kind, i)
    base, wide = {'dload': (0x26, 0x18), 'fload': (0x22, 0x17)}[kind]
    a.raw(base + i) if i < 4 else a.raw(wide, i)


def build_helper():
    cp = Pool(); this_i, super_i, code_name = cp.cls(HELPER), cp.cls('java/lang/Object'), cp.utf8('Code')
    m, fld = cp.method, cp.field
    specs = []
    def load(a, i): xlocal(a, 'aload', i)
    def store(a, i): xlocal(a, 'astore', i)
    def data(a, i):
        load(a, i); a.op(0xc0, cp.cls(EXT))
        invokeinterface(a, cp, EXT, 'getPersistentData', '()L' + NBT + ';', 1)
    def add(name, desc, stack, locals_, asm, flags=0x0009, exceptions=()):
        code = asm.finish() if isinstance(asm, Asm) else asm
        specs.append((flags, name, desc, stack, locals_, code, exceptions))

    # Exact owner, live player, same loaded world, out of purgatory and queue.
    a = Asm()
    load(a, 0); a.jump(0xc6, 'no'); load(a, 1); a.jump(0xc6, 'no'); load(a, 2); a.jump(0xc6, 'no')
    load(a, 0); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'no')
    load(a, 1); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'no')
    load(a, 1); a.op(0xb6, m(CORPSE, old.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 3)
    load(a, 3); a.jump(0xc6, 'no'); load(a, 3); load(a, 0); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'no')
    load(a, 0); a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';')); load(a, 2); a.jump(0xa6, 'no')
    load(a, 1); a.op(0xb6, m(E, 'level', '()L' + L + ';')); load(a, 2); a.jump(0xa6, 'no')
    load(a, 0); a.op(0xb6, m(SP, 'getServer', '()L' + SERVER + ';')); store(a, 4); load(a, 4); a.jump(0xc6, 'no')
    load(a, 4); a.op(0xb8, m(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';')); load(a, 3)
    a.op(0xb6, m(PD, 'isDeadInQueue', '(Ljava/util/UUID;)Z')); a.jump(0x9a, 'no')
    a.op(0xb2, fld(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')); load(a, 2)
    a.op(0xb6, m(SL, 'dimension', '()L' + KEY + ';')); a.op(0xb6, m('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z'))
    a.jump(0x9a, 'no'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac)
    add('eligible', ELIGIBLE_DESC, 3, 5, a, 0x000a)

    # Every stock death clears any stale association before spawning. This
    # metadata never changes ordinary drops or the nine-emerald queue.
    a = Asm(); data(a, 0); store(a, 1)
    for key in (DEATH_CORPSE, RESTORE):
        load(a, 1); ldc(a, cp, key); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    a.raw(0xb1); add('beginDeath', '(L' + SP + ';)V', 2, 2, a)

    # The stock corpse is created synchronously for this exact death. Record
    # its immutable entity UUID only AFTER successful addFreshEntity.
    a = Asm(); load(a, 0); load(a, 1); a.op(0xb6, m(SL, 'addFreshEntity', '(L' + E + ';)Z'))
    xlocal(a, 'istore', 3); xlocal(a, 'iload', 3); a.jump(0x99, 'done')
    load(a, 2); a.jump(0xc6, 'done'); load(a, 1); a.op(0xc1, cp.cls(CORPSE)); a.jump(0x99, 'done')
    load(a, 1); a.op(0xc0, cp.cls(CORPSE)); a.op(0xb6, m(CORPSE, old.OWNER_GETTER, '()Ljava/util/UUID;'))
    store(a, 4); load(a, 4); a.jump(0xc6, 'done'); load(a, 4); load(a, 2)
    a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    data(a, 2); ldc(a, cp, DEATH_CORPSE); load(a, 1); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
    a.label('done'); xlocal(a, 'iload', 3); a.raw(0xac)
    add('spawnAndRemember', '(L' + SL + ';L' + E + ';L' + SP + ';)Z', 3, 5, a)

    # Preserve both the current-death ID and the independent provenance of
    # any still-pending item list across NeoForge PlayerEvent.Clone. A stale
    # or absent source value must clear the corresponding destination value.
    a = Asm()
    for index, key in enumerate((DEATH_CORPSE, ITEMS_CORPSE)):
        clear, next_label = 'clear_id_' + str(index), 'next_id_' + str(index)
        load(a, 0); ldc(a, cp, key); a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z'))
        a.jump(0x99, clear); load(a, 1); ldc(a, cp, key); load(a, 0); ldc(a, cp, key)
        a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
        a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V')); a.jump(0xa7, next_label)
        a.label(clear); load(a, 1); ldc(a, cp, key); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
        a.label(next_label)
    load(a, 0); ldc(a, cp, SKIP_LEGACY)
    a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z')); a.jump(0x99, 'clear_guard')
    load(a, 1); ldc(a, cp, SKIP_LEGACY); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V')); a.raw(0xb1)
    a.label('clear_guard'); load(a, 1); ldc(a, cp, SKIP_LEGACY)
    a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V')); a.raw(0xb1)
    add('copyDeathId', '(L' + NBT + ';L' + NBT + ';)V', 4, 2, a)

    # Only the actual new drop capture can assign provenance to PLAYER_ITEMS.
    # This prevents an unresolved older list being adopted into a newer body.
    a = Asm(); data(a, 0); store(a, 1); load(a, 1); ldc(a, cp, DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x99, 'unknown')
    load(a, 1); ldc(a, cp, ITEMS_CORPSE); load(a, 1); ldc(a, cp, DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    a.op(0xb6, m(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V')); a.raw(0xb1)
    a.label('unknown'); load(a, 1); ldc(a, cp, ITEMS_CORPSE)
    a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V')); a.raw(0xb1)
    add('noteCaptured', '(L' + SP + ';)V', 4, 2, a)

    # The general altar path has no body argument. Never search by owner or
    # proximity: resolve only the recorded ID and verify the actual owner.
    a = Asm(); load(a, 0); a.jump(0xc6, 'none'); load(a, 1); a.jump(0xc6, 'none')
    data(a, 1); store(a, 3); load(a, 3); ldc(a, cp, DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x99, 'none')
    load(a, 0); load(a, 3); ldc(a, cp, DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    a.op(0xb6, m(SL, 'getEntity', '(Ljava/util/UUID;)L' + E + ';')); store(a, 4)
    load(a, 4); a.op(0xc1, cp.cls(CORPSE)); a.jump(0x99, 'none')
    load(a, 4); a.op(0xc0, cp.cls(CORPSE)); store(a, 5)
    load(a, 5); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'none')
    load(a, 5); a.op(0xb6, m(CORPSE, old.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 6)
    load(a, 6); a.jump(0xc6, 'none'); load(a, 6); load(a, 1)
    a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'none')
    load(a, 5); a.raw(0xb0); a.label('none'); a.raw(0x01, 0xb0)
    add('findRecorded', SELECT_DESC, 3, 7, a, 0x000a)

    # Replace the old first-owner-within-4-blocks capture lookup with the
    # same death-specific UUID; otherwise leave the captured NBT on the player.
    a = Asm(); load(a, 0); a.op(0xb6, m(SL, 'getServer', '()L' + SERVER + ';'))
    a.jump(0xc6, 'none'); load(a, 0); a.op(0xb6, m(SL, 'getServer', '()L' + SERVER + ';'))
    a.op(0xb6, m(SERVER, 'getPlayerList', '()Lnet/minecraft/server/players/PlayerList;'))
    load(a, 2); a.op(0xb6, m('net/minecraft/server/players/PlayerList', 'getPlayer', '(Ljava/util/UUID;)L' + SP + ';'))
    store(a, 3); load(a, 0); load(a, 3); a.raw(0x01); a.op(0xb8, m(HELPER, 'findRecorded', SELECT_DESC))
    a.raw(0xb0); a.label('none'); a.raw(0x01, 0xb0)
    add('findForDrops', '(L' + SL + ';L' + VEC + ';Ljava/util/UUID;)L' + CORPSE + ';', 3, 4, a)

    # Never overwrite an unresolved earlier player list on a further possessed
    # death: in that exceptional case leave this death's drops in the world.
    a = Asm(); data(a, 1); ldc(a, cp, PLAYER_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'empty')
    a.raw(0x04, 0xac); a.label('empty'); load(a, 0)
    invokeinterface(a, cp, 'java/util/Collection', 'isEmpty', '()Z', 1); a.raw(0xac)
    add('skipCapture', '(Ljava/util/Collection;L' + SP + ';)Z', 2, 2, a)

    # Move an already captured list only to the exact remembered corpse, and
    # never overwrite an existing list. Malformed player data is kept intact.
    a = Asm(); load(a, 0); a.jump(0xc6, 'done'); load(a, 1); a.jump(0xc6, 'done')
    data(a, 0); store(a, 2); load(a, 2); ldc(a, cp, DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x99, 'done')
    load(a, 2); ldc(a, cp, DEATH_CORPSE); a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    load(a, 1); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    load(a, 2); ldc(a, cp, ITEMS_CORPSE); a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z'))
    a.jump(0x99, 'done'); load(a, 2); ldc(a, cp, ITEMS_CORPSE)
    a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    load(a, 1); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    load(a, 1); a.op(0xb6, m(CORPSE, old.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 3)
    load(a, 3); a.jump(0xc6, 'done'); load(a, 3); load(a, 0)
    a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'done')
    data(a, 1); store(a, 4); load(a, 4); ldc(a, cp, CORPSE_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x9a, 'done')
    load(a, 2); ldc(a, cp, PLAYER_ITEMS); a.op(0xb6, m(NBT, 'get', '(Ljava/lang/String;)L' + TAG + ';'))
    store(a, 5); load(a, 5); a.op(0xc1, cp.cls(LIST)); a.jump(0x99, 'done')
    load(a, 4); ldc(a, cp, CORPSE_ITEMS); load(a, 5)
    a.op(0xb6, m(NBT, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';')); a.raw(0x57)
    load(a, 2); ldc(a, cp, PLAYER_ITEMS); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    load(a, 2); ldc(a, cp, ITEMS_CORPSE); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    a.label('done'); a.raw(0xb1); add('movePendingSafely', '(L' + SP + ';L' + CORPSE + ';)V', 3, 6, a)

    # A pending or conflicting legacy list must never reach 240's lossy legacy
    # parser after this ritual. Ordinary self-exits still use the old method.
    a = Asm(); data(a, 0); store(a, 1); load(a, 1); ldc(a, cp, SKIP_LEGACY)
    a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z')); a.jump(0x99, 'legacy')
    load(a, 1); ldc(a, cp, PLAYER_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z'))
    a.jump(0x9a, 'done')  # unresolved list: keep the guard for later lives
    load(a, 1); ldc(a, cp, SKIP_LEGACY); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    a.label('done'); a.raw(0xb1)
    a.label('legacy'); load(a, 0); a.op(0xb8, m(POSS, 'restoreBodyItems', '(L' + SP + ';)V')); a.raw(0xb1)
    add('guardLegacyRestore', '(L' + SP + ';)V', 2, 2, a)

    # Teleport to the actual entity. Transfer only after the verified successful
    # dimension change and queue removal; recover with strict existing 239 code.
    a = Asm()
    load(a, 0); a.jump(0xc6, 'fail'); load(a, 1); a.jump(0xc6, 'fail'); load(a, 2); a.jump(0xc6, 'fail')
    load(a, 2); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'fail')
    load(a, 2); a.op(0xb6, m(CORPSE, old.OWNER_GETTER, '()Ljava/util/UUID;')); store(a, 6)
    load(a, 6); a.jump(0xc6, 'fail'); load(a, 6); load(a, 0); a.op(0xb6, m(SP, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'fail')
    load(a, 2); a.op(0xb6, m(E, 'level', '()L' + L + ';')); load(a, 1); a.jump(0xa6, 'fail')
    # Direct legacy coffins already identify the initiator. If this player has
    # a recorded current-death ID, a different/older coffin is never accepted.
    data(a, 0); store(a, 8); load(a, 8); ldc(a, cp, DEATH_CORPSE)
    a.op(0xb6, m(NBT, 'hasUUID', '(Ljava/lang/String;)Z')); a.jump(0x99, 'teleport')
    load(a, 8); ldc(a, cp, DEATH_CORPSE); a.op(0xb6, m(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
    load(a, 2); a.op(0xb6, m(E, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, m('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'fail')
    a.label('teleport'); load(a, 0); load(a, 1)
    for axis in 'XYZ':
        load(a, 2); a.op(0xb6, m(CORPSE, 'get' + axis, '()D'))
    load(a, 3); xlocal(a, 'fload', 4); xlocal(a, 'fload', 5)
    a.op(0xb6, m(SP, 'teleportTo', TELEPORT_DESC)); a.jump(0x99, 'fail')
    load(a, 0); load(a, 2); load(a, 1); a.op(0xb8, m(HELPER, 'eligible', ELIGIBLE_DESC)); a.jump(0x99, 'success')
    data(a, 2); store(a, 7); load(a, 7); ldc(a, cp, SUCCESS); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    data(a, 0); store(a, 8)
    load(a, 8); ldc(a, cp, PLAYER_PENDING); a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z'))
    a.jump(0x99, 'pending_items')
    load(a, 8); ldc(a, cp, RESTORE); a.raw(0x04); a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    a.label('pending_items'); load(a, 8); ldc(a, cp, PLAYER_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'claim')
    load(a, 8); ldc(a, cp, SKIP_LEGACY); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    load(a, 0); load(a, 2); a.op(0xb8, m(HELPER, 'movePendingSafely', '(L' + SP + ';L' + CORPSE + ';)V'))
    # If both sides already contain data, or an old save has no proof of their
    # association, keep BOTH untouched rather than duplicate or delete either.
    load(a, 8); ldc(a, cp, PLAYER_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z'))
    a.jump(0x9a, 'success')
    a.label('claim'); load(a, 0); load(a, 2)
    a.op(0xb8, m(RECOVERY, 'tryClaim', '(L' + old.PLAYER + ';L' + E + ';)V'))
    a.label('success'); a.raw(0x04, 0xac); a.label('fail'); a.raw(0x03, 0xac)
    add('teleportBody', BODY_DESC, 11, 10, a, 0x000a)

    # The coffin's reviveOwner already pushed altar coordinates; keep its
    # fallback available, but prefer the exact receiver corpse supplied here.
    a = Asm(); load(a, 0); load(a, 1); load(a, 11); load(a, 8)
    xlocal(a, 'fload', 9); xlocal(a, 'fload', 10)
    a.op(0xb8, m(HELPER, 'teleportBody', BODY_DESC)); a.jump(0x99, 'fallback')
    a.raw(0x04, 0xac); a.label('fallback'); load(a, 0); load(a, 1)
    xlocal(a, 'dload', 2); xlocal(a, 'dload', 4); xlocal(a, 'dload', 6); load(a, 8)
    xlocal(a, 'fload', 9); xlocal(a, 'fload', 10); a.op(0xb6, m(SP, 'teleportTo', TELEPORT_DESC))
    xlocal(a, 'istore', 12); xlocal(a, 'iload', 12); a.jump(0x99, 'fallback_done')
    data(a, 0); store(a, 13); load(a, 13); ldc(a, cp, PLAYER_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'fallback_done')
    load(a, 13); ldc(a, cp, SKIP_LEGACY); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    a.label('fallback_done'); xlocal(a, 'iload', 12); a.raw(0xac)
    add('teleportDirect', DIRECT_DESC, 11, 14, a)

    # After a successful full claim tryClaim already discards. Empty bodies are
    # removed here; partial or malformed lists, and failed teleports, are kept.
    a = Asm(); load(a, 0); a.jump(0xc6, 'done')
    load(a, 0); a.op(0xb6, m(E, 'isRemoved', '()Z')); a.jump(0x9a, 'done')
    data(a, 0); store(a, 2)
    # An offline/dead owner has not yet reached the body; keep even an empty
    # body until the pending teleport actually succeeds.
    load(a, 1); a.jump(0xc6, 'done'); load(a, 1); a.op(0xb6, m(SP, 'isAlive', '()Z')); a.jump(0x99, 'done')
    load(a, 2); ldc(a, cp, SUCCESS); a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z')); a.jump(0x99, 'done')
    load(a, 2); ldc(a, cp, SUCCESS); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    load(a, 1); load(a, 0); load(a, 1); a.op(0xb6, m(SP, 'serverLevel', '()L' + SL + ';'))
    a.op(0xb8, m(HELPER, 'eligible', ELIGIBLE_DESC)); a.jump(0x99, 'done')
    # If the pending player list wasn't adoptable, do not delete the body.
    data(a, 1); ldc(a, cp, PLAYER_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x9a, 'done')
    a.label('offline')
    load(a, 2); ldc(a, cp, CORPSE_ITEMS); a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'remove')
    load(a, 2); ldc(a, cp, CORPSE_ITEMS); a.op(0xb6, m(NBT, 'get', '(Ljava/lang/String;)L' + TAG + ';')); store(a, 3)
    load(a, 3); a.op(0xc1, cp.cls(LIST)); a.jump(0x99, 'done')
    load(a, 3); a.op(0xc0, cp.cls(LIST)); a.op(0xb6, m(LIST, 'isEmpty', '()Z')); a.jump(0x99, 'done')
    load(a, 2); ldc(a, cp, CORPSE_ITEMS); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    a.label('remove'); load(a, 0); a.raw(0x04, 0x04); a.op(0xb6, m(CORPSE, 'dropLeash', '(ZZ)V'))
    load(a, 0); a.op(0xb6, m(E, 'discard', '()V')); a.label('done'); a.raw(0xb1)
    add('finish', FINISH_DESC, 3, 4, a)

    # Preserve any body with stored items on the later tick, even if an older
    # save lacks the possessed-body marker; other ordinary corpses are unchanged.
    a = Asm(); data(a, 0); store(a, 1)
    load(a, 1); ldc(a, cp, PLAYER_BODY); a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z'))
    a.jump(0x9a, 'yes'); load(a, 1); ldc(a, cp, CORPSE_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.raw(0xac)
    a.label('yes'); a.raw(0x04, 0xac)
    add('mustKeep', '(L' + E + ';)Z', 2, 2, a)

    # One-shot permission to restore the prior possessed model after a ritual
    # even when the corpse is 5..10 blocks from the altar. Ordinary self-exits
    # still use the old nearRevivalAltar result.
    a = Asm(); data(a, 1); store(a, 2); load(a, 2); ldc(a, cp, RESTORE)
    a.op(0xb6, m(NBT, 'getBoolean', '(Ljava/lang/String;)Z')); a.jump(0x99, 'original')
    load(a, 2); ldc(a, cp, RESTORE); a.op(0xb6, m(NBT, 'remove', '(Ljava/lang/String;)V'))
    a.raw(0x04, 0xac); a.label('original'); xlocal(a, 'iload', 0); a.raw(0xac)
    add('restoreAtBody', '(ZL' + SP + ';)Z', 2, 3, a)

    # General altar fallback reproduces the original altar teleport exactly.
    a = Asm(); load(a, 1); a.raw(0x0b); a.op(0xb5, fld(SP, 'fallDistance', 'F'))
    load(a, 1); a.op(0xb6, m(SP, 'clearFire', '()V')); load(a, 1)
    a.op(0xb2, fld(VEC, 'ZERO', 'L' + VEC + ';')); a.op(0xb6, m(SP, 'setDeltaMovement', '(L' + VEC + ';)V'))
    load(a, 0); load(a, 1); load(a, 2); a.op(0xb8, m(HELPER, 'findRecorded', SELECT_DESC)); store(a, 3)
    load(a, 3); a.jump(0xc6, 'fallback')
    load(a, 1); load(a, 0); load(a, 3); a.op(0xb8, m('java/util/Collections', 'emptySet', '()Ljava/util/Set;'))
    load(a, 1); a.op(0xb6, m(SP, 'getYRot', '()F')); a.raw(0x0b)
    a.op(0xb8, m(HELPER, 'teleportBody', BODY_DESC)); a.jump(0x99, 'fallback')
    load(a, 3); load(a, 1); a.op(0xb8, m(HELPER, 'finish', FINISH_DESC)); a.raw(0xb1)
    a.label('fallback'); load(a, 1); load(a, 0)
    for axis, offset in [('X', 0.5), ('Y', 1.05), ('Z', 0.5)]:
        load(a, 2); a.op(0xb6, m(BLOCK, 'get' + axis, '()I')); a.raw(0x87)
        a.op(0x14, cp.double(offset)); a.raw(0x63)  # dadd
    a.op(0xb8, m('java/util/Collections', 'emptySet', '()Ljava/util/Set;'))
    load(a, 1); a.op(0xb6, m(SP, 'getYRot', '()F')); a.raw(0x0b)
    a.op(0xb6, m(SP, 'teleportTo', TELEPORT_DESC)); a.jump(0x99, 'fallback_done')
    data(a, 1); store(a, 3); load(a, 3); ldc(a, cp, PLAYER_ITEMS)
    a.op(0xb6, m(NBT, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'fallback_done')
    load(a, 3); ldc(a, cp, SKIP_LEGACY); a.raw(0x04)
    a.op(0xb6, m(NBT, 'putBoolean', '(Ljava/lang/String;Z)V'))
    a.label('fallback_done'); a.raw(0xb1)
    add('teleportAtAltar', ALTAR_DESC, 11, 4, a)

    init = b'\x2a\xb7' + u2(m('java/lang/Object', '<init>', '()V')) + b'\xb1'
    specs.insert(0, (0x0002, '<init>', '()V', 1, 1, init, ()))
    bodies = []
    for flags, name, desc, stack, locals_, code, exceptions in specs:
        ex = b''.join(u2(s) + u2(e) + u2(h) + u2(t) for s, e, h, t in exceptions)
        info = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(len(exceptions)) + ex + u2(0)
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) + attr(code_name, info))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() + u2(0x0031) + u2(this_i) + u2(super_i) +
            u2(0) + u2(0) + u2(len(bodies)) + b''.join(bodies) + u2(0))


def patch_altar_call(raw, method_name, desc):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw); method = k.find_method(method_name, desc); assert method
    parts = classedit.code_parts(k, method)
    pos, n = find_invoke(k, parts['code'], PM, 'teleportPlayerOntoAltar', ALTAR_DESC)
    assert n == 3 and parts['code'][pos] == 0xb8
    dest = k.add_methodref(HELPER, 'teleportAtAltar', ALTAR_DESC)
    code = bytearray(parts['code']); code[pos + 1:pos + 3] = u2(dest); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_purgatory(raw):
    raw = patch_altar_call(raw, 'tryReviveAtAltar', '(L' + SL + ';L' + BLOCK + ';)Z')
    return patch_altar_call(raw, 'teleportToTarget', '(L' + SERVER + ';L' + SP + ';L' + PD + '$TargetPos;)V')


def patch_corpse(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    desc = '(L' + SERVER + ';L' + SL + ';L' + PD + ';L' + SP + ';L' + BLOCK + ';)V'
    method = k.find_method('reviveOwner', desc); assert method
    parts = classedit.code_parts(k, method)
    # Pending/offline branch: retain the initiating body's position, not the altar.
    pc, n = find_invoke(k, parts['code'], PD, 'setPendingRevive', '(Ljava/util/UUID;Ljava/lang/String;L' + BLOCK + ';)V')
    at = pc - 2
    assert parts['code'][at:pc] == b'\x19\x05' and at not in decode.targets(parts['code'])
    blockpos = k.add_methodref(CORPSE, 'blockPosition', '()L' + BLOCK + ';')
    parts = classedit.insert(parts, at, b'\x2a\xb6' + u2(blockpos))
    code = bytearray(parts['code']); code[at + 4:at + 6] = b'\x00\x00'; parts['code'] = bytes(code)
    # Replace the actual teleport with a same-stack helper plus receiver corpse.
    pc, n = find_invoke(k, parts['code'], SP, 'teleportTo', TELEPORT_DESC)
    assert n == 3 and pc not in decode.targets(parts['code'])
    direct = k.add_methodref(HELPER, 'teleportDirect', DIRECT_DESC)
    parts = classedit.insert(parts, pc, b'\x2a\x00\x00\x00')
    code = bytearray(parts['code']); code[pc + 4:pc + 7] = b'\xb8' + u2(direct); parts['code'] = bytes(code)
    # The unconditional discard must instead honor unsuccessful/partial claims.
    pc, n = find_invoke(k, parts['code'], CORPSE, 'discard', '()V')
    assert n == 3 and pc not in decode.targets(parts['code'])
    finish = k.add_methodref(HELPER, 'finish', FINISH_DESC)
    parts = classedit.insert(parts, pc, b'\x19\x04\x00\x00')
    code = bytearray(parts['code']); code[pc + 4:pc + 7] = b'\xb8' + u2(finish); parts['code'] = bytes(code)
    parts['max_stack'] = max(parts['max_stack'], 12)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_mixin(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    desc = '(L' + CORPSE + ';L' + SERVER + ';L' + SL + ';L' + PD + ';L' + SP + ';L' + BLOCK + ';)V'
    method = k.find_method('settledlands$reviveOnlyFromQueue', desc); assert method
    parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], MIXIN, 'handCorpseItemsToPlayer', '(L' + CORPSE + ';L' + SP + ';)V')
    assert n == 3 and parts['code'][pc] == 0xb8 and pc not in decode.targets(parts['code'])
    # Old hand-off moved the entire NBT list to the player before teleport, then
    # used the unsafe legacy restoreBodyItems. Preserve the stack, skip that call.
    code = bytearray(parts['code']); code[pc:pc + 3] = b'\x58\x00\x00'; parts['code'] = bytes(code)
    raw = k.dump(method, classedit.build_code_body(k, parts))
    k = classedit.Klass(raw); method = k.find_method('settledlands$keepBodyCorpse', '(L' + CORPSE + ';)V'); assert method
    parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], MIXIN, 'markedCorpse', '(L' + E + ';)Z')
    assert n == 3 and parts['code'][pc] == 0xb8
    keep = k.add_methodref(HELPER, 'mustKeep', '(L' + E + ';)Z')
    code = bytearray(parts['code']); code[pc + 1:pc + 3] = u2(keep); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def patch_lifecycle(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    method = k.find_method('onPlayerDeath', '(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V')
    assert method; parts = classedit.code_parts(k, method)
    # The first aload_1 on the success path is also a branch target: insert
    # AFTER it so every incoming branch executes beginDeath, without a new SMT.
    pc, n = find_invoke(k, parts['code'], SP, 'level', '()L' + L + ';')
    assert parts['code'][pc - 1] == 0x2b and pc not in decode.targets(parts['code'])
    begin = k.add_methodref(HELPER, 'beginDeath', '(L' + SP + ';)V')
    parts = classedit.insert(parts, pc, b'\x59\xb8' + u2(begin))  # dup, beginDeath
    pc, n = find_invoke(k, parts['code'], SL, 'addFreshEntity', '(L' + E + ';)Z')
    assert n == 3 and pc not in decode.targets(parts['code'])
    spawn = k.add_methodref(HELPER, 'spawnAndRemember', '(L' + SL + ';L' + E + ';L' + SP + ';)Z')
    parts = classedit.insert(parts, pc, b'\x2b\x00\x00\x00')  # player argument
    code = bytearray(parts['code']); code[pc + 4:pc + 7] = b'\xb8' + u2(spawn); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def replace_call(raw, method_name, method_desc, src_owner, src_name, src_desc, dst_name):
    k = classedit.Klass(raw); method = k.find_method(method_name, method_desc); assert method
    parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], src_owner, src_name, src_desc)
    assert n == 3 and parts['code'][pc] == 0xb8
    dst = k.add_methodref(HELPER, dst_name, src_desc)
    code = bytearray(parts['code']); code[pc + 1:pc + 3] = u2(dst); parts['code'] = bytes(code)
    return k.dump(method, classedit.build_code_body(k, parts))


def make_restore_public(raw):
    # The original method stays byte-for-byte unchanged; the separate helper
    # delegates to it only when this is NOT a guarded altar revival.
    k = classedit.Klass(raw); method = k.find_method('restoreBodyItems', '(L' + SP + ';)V')
    assert method and method['acc'] == 0x000a
    method['acc'] = 0x0009
    def member(v):
        attributes = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in v['attrs'])
        return u2(v['acc']) + u2(v['name_idx']) + u2(v['desc_idx']) + u2(len(v['attrs'])) + attributes
    attributes = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in k.attrs)
    return (raw[:8] + u2(len(k.cp)) + k._pool_bytes() + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) +
            u2(len(k.fields)) + b''.join(member(x) for x in k.fields) +
            u2(len(k.methods)) + b''.join(member(x) for x in k.methods) + u2(len(k.attrs)) + attributes)


def patch_possession(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    desc = '(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V'
    method = k.find_method('onServerTick', desc); assert method
    parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], POSS, 'nearRevivalAltar', '(L' + SP + ';L' + PD + ';)Z')
    at = pc + n
    assert n == 3 and parts['code'][at] == 0x9a and at not in decode.targets(parts['code'])
    restore = k.add_methodref(HELPER, 'restoreAtBody', '(ZL' + SP + ';)Z')
    parts = classedit.insert(parts, at, b'\x19\x04\xb8' + u2(restore) + b'\x00\x00\x00')
    parts['max_stack'] = max(parts['max_stack'], 2)
    raw = k.dump(method, classedit.build_code_body(k, parts))

    # Clone copies the actual UUID, independent of body-model pending status.
    k = classedit.Klass(raw); clone_desc = '(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$Clone;)V'
    method = k.find_method('onPlayerClone', clone_desc); parts = classedit.code_parts(k, method)
    owner = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$Clone'
    pc, n = find_invoke(k, parts['code'], owner, 'getEntity', '()L' + old.PLAYER + ';')
    at = pc + n + 3 + 1  # invokestatic data, astore_2
    assert parts['code'][pc + n] == 0xb8 and parts['code'][at - 1:at + 1] == b'\x4d\x2b'
    assert at not in decode.targets(parts['code'])
    copy = k.add_methodref(HELPER, 'copyDeathId', '(L' + NBT + ';L' + NBT + ';)V')
    parts = classedit.insert(parts, at, b'\x2b\x2c\xb8' + u2(copy))
    raw = k.dump(method, classedit.build_code_body(k, parts))

    # A pre-existing pending list must not be overwritten by another death.
    drops_desc = '(Lnet/neoforged/neoforge/event/entity/living/LivingDropsEvent;)V'
    k = classedit.Klass(raw); method = k.find_method('onPlayerDrops', drops_desc)
    parts = classedit.code_parts(k, method)
    pc, n = find_invoke(k, parts['code'], 'java/util/Collection', 'isEmpty', '()Z')
    assert n == 5 and parts['code'][pc] == 0xb9 and pc not in decode.targets(parts['code'])
    check = k.add_methodref(HELPER, 'skipCapture', '(Ljava/util/Collection;L' + SP + ';)Z')
    code = bytearray(parts['code']); code[pc:pc + 5] = b'\x2b\xb8' + u2(check) + b'\x00'; parts['code'] = bytes(code)
    # Record the provenance immediately after this death's list is written;
    # never relabel an older pending list when the capture was skipped.
    pc, n = find_invoke(k, parts['code'], NBT, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';')
    at = pc + n + 1
    assert n == 3 and parts['code'][pc + n] == 0x57 and at not in decode.targets(parts['code'])
    note = k.add_methodref(HELPER, 'noteCaptured', '(L' + SP + ';)V')
    parts = classedit.insert(parts, at, b'\x2b\xb8' + u2(note))
    raw = k.dump(method, classedit.build_code_body(k, parts))
    desc_find = '(L' + SL + ';L' + VEC + ';Ljava/util/UUID;)L' + CORPSE + ';'
    raw = replace_call(raw, 'onPlayerDrops', drops_desc, POSS, 'findOwnerCorpse', desc_find, 'findForDrops')
    desc_move = '(L' + SP + ';L' + CORPSE + ';)V'
    for method_name, method_desc in [
        ('onPlayerDrops', drops_desc),
        ('onCorpseJoin', '(Lnet/neoforged/neoforge/event/entity/EntityJoinLevelEvent;)V'),
        ('guaranteeCorpses', '()V'),
    ]:
        raw = replace_call(raw, method_name, method_desc, POSS, 'movePendingItemsToCorpse', desc_move, 'movePendingSafely')
    repossess_desc = '(L' + SP + ';Ljava/lang/String;Ljava/lang/String;)V'
    raw = replace_call(raw, 'repossess', repossess_desc, POSS, 'restoreBodyItems', '(L' + SP + ';)V', 'guardLegacyRestore')
    return make_restore_public(raw)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path); args = ap.parse_args()
    digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != BASE_SHA: raise SystemExit('unexpected base SHA-256: ' + digest)
    if args.base.resolve() == args.out.resolve(): raise SystemExit('refusing to overwrite base')
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}; updated = dict(entries)
        assert not (ADDED & set(entries))
        updated[PM + '.class'] = patch_purgatory(entries[PM + '.class'])
        updated[CORPSE + '.class'] = patch_corpse(entries[CORPSE + '.class'])
        updated[MIXIN + '.class'] = patch_mixin(entries[MIXIN + '.class'])
        updated[POSS + '.class'] = patch_possession(entries[POSS + '.class'])
        updated[LIFECYCLE + '.class'] = patch_lifecycle(entries[LIFECYCLE + '.class'])
        updated[HELPER + '.class'] = build_helper()
        text = entries['META-INF/neoforge.mods.toml'].decode()
        needle = 'version="' + OLD_VERSION + '"'; assert text.count(needle) == 1 and text.count("description='''\n") == 1
        text = text.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        text = text.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
        updated['META-INF/neoforge.mods.toml'] = text.encode()
        updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode()).encode()
        changed = {n for n in entries if entries[n] != updated[n]}; assert changed == CHANGED and set(updated) - set(entries) == ADDED
        with zipfile.ZipFile(args.out, 'w') as out:
            for i in base.infolist(): out.writestr(i, updated[i.filename])
            info = zipfile.ZipInfo(HELPER + '.class', (2026, 10, 28, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, updated[HELPER + '.class'], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(args.out) as z: assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes(); print('changed:', sorted(changed), 'added:', sorted(ADDED))
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__': main()
