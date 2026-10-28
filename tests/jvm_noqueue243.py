#!/usr/bin/env python3
"""Real 242/243 death, join, clone, drop, altar and recovery bytecodes on Java.

The surrounding Minecraft/NeoForge API and event dispatch are executable
stand-ins. This is NOT a game test. Dispatch explicitly models HIGHEST death,
synchronous addFreshEntity/onCorpseJoin, LOWEST death, drop, and player clone.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

import jvm_altar242 as t
import patch_death_scope243 as d
import patch_noqueue243 as nq
from patch_probe233 import Asm

r, f, p, q = t.r, t.f, t.p, t.q
E, SP, SL, L, SERVER, C, G = t.E, t.SP, t.SL, t.L, t.SERVER, t.C, t.G
NBT, PD, PM, KEY, INV, IT, ITEM, ITEMS, ITEM_ENTITY = t.NBT, t.PD, t.PM, t.KEY, t.INV, t.IT, t.ITEM, t.ITEMS, t.ITEM_ENTITY
BLOCK, VEC, POSS, PLAYERLIST = t.BLOCK, t.VEC, t.POSS, t.PLAYERLIST
DEATH, CLONE, POST, LIST, TAG = t.DEATH, t.CLONE, t.POST, t.LIST, t.TAG
JOIN = 'net/neoforged/neoforge/event/entity/EntityJoinLevelEvent'
RESPAWN = 'net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerRespawnEvent'
WATCH, PENDING = POSS + '$DeadWatch', POSS + '$PendingBody'
POSITION = 'net/minecraft/core/Position'
SURVIVAL = POSS + '$SurvivalCheck'
CHUNK = 'net/minecraft/server/level/ServerChunkCache'
PACKET = 'net/minecraft/network/protocol/Packet'
TPACKET = 'net/minecraft/network/protocol/game/ClientboundTeleportEntityPacket'


def build_classes(jar):
    classes = t.build_classes(jar)
    with zipfile.ZipFile(jar) as z:
        noqueue = nq.NQ + '.class' in z.namelist()
        if noqueue:
            classes[nq.NQ] = z.read(nq.NQ + '.class')
            classes[d.GUARD] = z.read(d.GUARD + '.class')
        for name in (WATCH, PENDING, SURVIVAL): classes[name] = z.read(name + '.class')
    # Synthetic queue API only; the actual disabled PurgatoryData methods are
    # executed independently in jvm_queue_disabled243.py.
    if noqueue:
        classes[PD] = t.rewrite(classes[PD], replace=[
            ('isDeadInQueue', '(Ljava/util/UUID;)Z', 1, 2, lambda k: b'\x03\xac'),
            ('hasDeadPlayers', '()Z', 1, 1, lambda k: b'\x03\xac'),
            ('enqueueDead', '(Ljava/util/UUID;)V', 0, 2, lambda k: b'\xb1'),
            ('removeFromQueue', '(Ljava/util/UUID;)Z', 1, 2, lambda k: b'\x03\xac')])
    classes[PD] = t.rewrite(classes[PD], add_methods=[
        (1, 'consumeFirstPurgatorySpawn', '(Ljava/util/UUID;)Z', 1, 2, lambda k: b'\x03\xac')])
    classes[RESPAWN] = f.fake_class(RESPAWN, [], [
        (1, '<init>', '(L' + r.P + ';)V', 2, 2, lambda cp: b'\x2a\x2b' +
         t.op(0xb7, cp.method(r.PLAYER_EVENT, '<init>', '(L' + r.P + ';)V')) + b'\xb1'),
        (1, 'isEndConquered', '()Z', 1, 1, lambda cp: b'\x03\xac')], super_name=r.PLAYER_EVENT)
    pm_names = {'tryReviveAtAltar', 'isValidEmeraldEntity', 'spawnVisualLightning',
                'teleportPlayerOntoAltar', 'teleportToTarget', 'onPlayerLogin', 'onDimensionChange', 'onPlayerDeath', 'onPlayerRespawn'}
    with zipfile.ZipFile(jar) as z:
        if r.f.classedit.Klass(z.read(PM + '.class')).find_method(q.PLAYER_ONLY): pm_names.add(q.PLAYER_ONLY)
    classes[PM] = r.subset(jar, PM, pm_names, fields={'PURGATORY_LEVEL', 'NO_RELATIVES'}, add_clinit=t.pm_clinit)
    def to_purg(k):
        return (b'\x2b' + t.op(0xb6, k.add_methodref(SP, 'getServer', '()L' + SERVER + ';')) +
                t.op(0xb4, k.add_fieldref(SERVER, 'purgatory', 'L' + SL + ';')) +
                b'\x4e\x2b\x2d' + t.op(0xb5, k.add_fieldref(E, 'world', 'L' + L + ';')) + b'\xb1')
    classes[PM] = t.rewrite(classes[PM], add_methods=[(9, 'teleportToPurgatory', '(L' + SERVER + ';L' + SP + ';Z)V', 2, 4, to_purg)])
    names = {'<clinit>', 'safe', 'data', 'onPlayerDeath', 'onPlayerClone', 'onPlayerDrops', 'onCorpseJoin', 'ownerUuid',
             'dressBodyCorpse', 'findOwnerCorpse', 'movePendingItemsToCorpse', 'onServerTick', 'nearRevivalAltar',
             'clearMarker', 'repossess', 'restoreBodyItems', 'guaranteeCorpses', 'healStuckIvyBodies'}
    with zipfile.ZipFile(jar) as z:
        if r.f.classedit.Klass(z.read(POSS + '.class')).find_method('settledlands$cancelDeadWatches'):
            names.add('settledlands$cancelDeadWatches')
    classes[POSS] = r.subset(jar, POSS, names)
    # The visual dressing and unrelated sweep are the only stubbed mod methods.
    # The event handlers, UUID association, tick decisions, drop capture,
    # fallback watch and ritual/recovery bytecodes remain real.
    def count(k):
        return (t.op(0xb2, k.add_fieldref(POSS, 'DEAD_WATCHES', 'Ljava/util/List;')) +
                t.op(0xb9, k.add_interfaceref('java/util/List', 'size', '()I')) + b'\x01\x00\xac')
    def reset(k):
        b = b''
        for name, owner in [('DEAD_WATCHES', 'java/util/List'), ('PENDING', 'java/util/Map'),
                            ('STOCK_CORPSES', 'java/util/Map'), ('SURVIVAL_CHECKS', 'java/util/List')]:
            b += t.op(0xb2, k.add_fieldref(POSS, name, 'L' + owner + ';'))
            b += t.op(0xb9, k.add_interfaceref(owner, 'clear', '()V')) + b'\x01\x00'
        return b + b'\xb1'
    def guarantee(k): return t.op(0xb8, k.add_methodref(POSS, 'guaranteeCorpses', '()V')) + b'\xb1'
    classes[POSS] = t.rewrite(classes[POSS], add_methods=[
        (9, 'fixtureWatchCount', '()I', 1, 0, count), (9, 'fixtureReset', '()V', 1, 0, reset),
        (9, 'fixtureGuarantee', '()V', 0, 0, guarantee)], replace=[
        ('dressBodyCorpse', '(L' + C + ';L' + PENDING + ';)V', 0, 2, lambda k: b'\xb1'),
        ('healStuckIvyBodies', '(L' + SERVER + ';)V', 0, 1, lambda k: b'\xb1')])
    classes[r.POSS_SYS] = t.rewrite(classes[r.POSS_SYS], add_methods=[
        (9, 'possessModelKey', '(L' + E + ';)Ljava/lang/String;', 1, 1, lambda k: f.ldc(k.add_string('test:model')) + b'\xb0'),
        (9, 'possessName', '(L' + E + ';)Ljava/lang/String;', 1, 1, lambda k: f.ldc(k.add_string('Test Body')) + b'\xb0')])
    classes[SP] = t.rewrite(classes[SP], add_fields=[(1, 'yBodyRot', 'F'), (1, 'yHeadRot', 'F')])
    classes[E] = t.rewrite(classes[E], add_fields=[(1, 'tickCount', 'I')])
    classes[POSITION] = r.interface(POSITION)
    classes[PACKET] = r.interface(PACKET)
    classes[TPACKET] = f.fake_class(TPACKET, [], [(1, '<init>', '(L' + E + ';)V', 1, 2, f.empty_init())], interfaces=[PACKET])
    classes[CHUNK] = f.fake_class(CHUNK, [], [(1, 'broadcast', '(L' + E + ';L' + PACKET + ';)V', 0, 3, lambda cp: b'\xb1')])
    def vec_init(cp):
        a = Asm(); t.obj(a, cp, VEC); a.op(0xb3, cp.field(VEC, 'ZERO', 'L' + VEC + ';')); a.raw(0xb1); return a.finish()
    classes[VEC] = f.fake_class(VEC, [(9, 'ZERO', 'L' + VEC + ';'), (1, 'x', 'D'), (1, 'y', 'D'), (1, 'z', 'D')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()), (8, '<clinit>', '()V', 2, 0, vec_init)], interfaces=[POSITION])
    classes[L] = t.rewrite(classes[L], add_methods=[
        (1, 'getServer', '()L' + SERVER + ';', 1, 1, lambda k: b'\x2a' + t.op(0xb4, k.add_fieldref(L, 'server', 'L' + SERVER + ';')) + b'\xb0')])
    # Add methods to the no-op logger used by the real watch path.
    log, fake = r.LOGGER, r.FLOGGER
    logger_methods = [('warn', '(Ljava/lang/String;Ljava/lang/Object;)V'),
                      ('warn', '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V'),
                      ('info', '(Ljava/lang/String;[Ljava/lang/Object;)V')]
    classes[log] = r.interface(log, [r.abstract('info', '(Ljava/lang/String;Ljava/lang/Object;)V'),
        r.abstract('info', '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V')] +
        [r.abstract(n, desc) for n, desc in logger_methods])
    classes[fake] = t.rewrite(classes[fake], add_methods=[
        (1, name, desc, 0, 4 if desc.count('Ljava/lang/Object;') == 2 else 3, lambda k: b'\xb1')
        for name, desc in logger_methods])
    # Real addFreshEntity invokes the NeoForge join callback before the caller
    # writes the resulting UUID. Model that exact order, not an async callback.
    classes[JOIN] = f.fake_class(JOIN, [(1, 'world', 'L' + L + ';'), (1, 'entity', 'L' + E + ';')], [
        (1, '<init>', '(L' + E + ';L' + L + ';)V', 2, 3, lambda cp: b'\x2a' +
         t.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) + b'\x2a\x2b' +
         t.op(0xb5, cp.field(JOIN, 'entity', 'L' + E + ';')) + b'\x2a\x2c' +
         t.op(0xb5, cp.field(JOIN, 'world', 'L' + L + ';')) + b'\xb1'),
        (1, 'getLevel', '()L' + L + ';', 1, 1, t.field(JOIN, 'world', 'L' + L + ';')),
        (1, 'getEntity', '()L' + E + ';', 1, 1, t.field(JOIN, 'entity', 'L' + E + ';'))])
    def add_entity(k):
        a = Asm(); a.raw(0x2a); a.op(0xb4, k.add_fieldref(SL, 'failSpawn', 'Z')); a.jump(0x9a, 'no')
        a.raw(0x2a); a.op(0xb4, k.add_fieldref(SL, 'near', 'Ljava/util/List;')); a.raw(0x2b)
        a.op(0xb9, k.add_interfaceref('java/util/List', 'add', '(Ljava/lang/Object;)Z')); a.raw(2, 0, 0x57)
        a.raw(0x2b); a.op(0xc1, k.add_class(C)); a.jump(0x99, 'done')
        a.raw(0x2a, 0x59); a.op(0xb4, k.add_fieldref(SL, 'joins', 'I')); a.raw(0x04, 0x60)
        a.op(0xb5, k.add_fieldref(SL, 'joins', 'I'))
        a.op(0xbb, k.add_class(JOIN)); a.raw(0x59, 0x2b, 0x2a)
        a.op(0xb7, k.add_methodref(JOIN, '<init>', '(L' + E + ';L' + L + ';)V'))
        a.op(0xb8, k.add_methodref(POSS, 'onCorpseJoin', '(L' + JOIN + ';)V'))
        a.label('done'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    classes[SL] = t.rewrite(classes[SL], add_fields=[(1, 'joins', 'I'), (1, 'gameTime', 'J')], add_methods=[
        (1, 'getGameTime', '()J', 2, 1, lambda k: b'\x2a' + t.op(0xb4, k.add_fieldref(SL, 'gameTime', 'J')) + b'\xad')],
        replace=[('addFreshEntity', '(L' + E + ';)Z', 4, 2, add_entity)])
    # Online list lookup by UUID permits several independent players.
    def find_player(k):
        a = Asm(); a.raw(0x03, 0x3d); a.label('loop'); a.raw(0x1c, 0x2a)
        a.op(0xb4, k.add_fieldref(PLAYERLIST, 'list', 'Ljava/util/List;'))
        a.op(0xb9, k.add_interfaceref('java/util/List', 'size', '()I')); a.raw(1, 0); a.jump(0xa2, 'none')
        a.raw(0x2a); a.op(0xb4, k.add_fieldref(PLAYERLIST, 'list', 'Ljava/util/List;')); a.raw(0x1c)
        a.op(0xb9, k.add_interfaceref('java/util/List', 'get', '(I)Ljava/lang/Object;')); a.raw(2, 0)
        a.op(0xc0, k.add_class(SP)); a.raw(0x4e, 0x2b, 0x2d)
        a.op(0xb6, k.add_methodref(SP, 'getUUID', '()Ljava/util/UUID;'))
        a.op(0xb6, k.add_methodref('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'next')
        a.raw(0x2d, 0xb0); a.label('next'); a.raw(0x84, 2, 1); a.jump(0xa7, 'loop')
        a.label('none'); a.raw(0x01, 0xb0); return a.finish()
    classes[PLAYERLIST] = t.rewrite(classes[PLAYERLIST], replace=[
        ('getPlayer', '(Ljava/util/UUID;)L' + SP + ';', 2, 4, find_player)])
    return classes, noqueue


def build_with_wear(jar):
    # 245 retains the real wear helper in these earlier end-to-end scenarios;
    # the health/world APIs remain synthetic. Older baselines are unchanged.
    with zipfile.ZipFile(jar) as z:
        has_wear = 'dev/settledlands/npc/BodyWear.class' in z.namelist()
    if has_wear:
        import wear_fixture245
        return wear_fixture245.build(jar), True
    return build_classes(jar)


def harness():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        def load(i): t.local(a, 'aload', i)
        def store(i): t.local(a, 'astore', i)
        def num(n): r.const(a, n)
        def string(s): t.text(a, cp, s)
        def bool_(v): a.raw(0x04 if v else 0x03)
        def make(name, desc='()V', args=None): t.obj(a, cp, name, desc, args)
        def get(i, owner, name, desc): load(i); a.op(0xb4, cp.field(owner, name, desc))
        def put(i, owner, name, desc, emit): load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def val(label, typ, emit):
            a.op(0xb2, out); string(label + '='); a.op(0xb6, cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V'))
            a.op(0xb2, out); emit(); a.op(0xb6, cp.method('java/io/PrintStream', 'println', '(' + typ + ')V'))
        def uuid(n=1):
            string('00000000-0000-0000-0000-%012d' % n)
            a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(i): load(i); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def contains(i, key): data(i); string(key); a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
        def flag(i, key): data(i); string(key); a.op(0xb6, cp.method(NBT, 'getBoolean', '(Ljava/lang/String;)Z'))
        def size(i): get(i, SL, 'near', 'Ljava/util/List;'); r.p.invokeinterface(a, cp, 'java/util/List', 'size', '()I', 1)
        def position(i, x, y=64, z=2):
            for axis, value in zip('xyz', (x, y, z)):
                put(i, E, axis, 'D', lambda value=value: (num(value), a.raw(0x87)))
        def env():
            a.op(0xb8, cp.method(POSS, 'fixtureReset', '()V')); bool_(False)
            a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;'))
            make(SERVER); store(1); make(KEY); store(10)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(10), load(1))); store(2)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V',
                 lambda: (a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')), load(1))); store(11)
            make(PLAYERLIST); store(9); put(1, SERVER, 'players', 'L' + PLAYERLIST + ';', lambda: load(9))
            put(1, SERVER, 'world', 'L' + SL + ';', lambda: load(2)); put(1, SERVER, 'purgatory', 'L' + SL + ';', lambda: load(11))
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(2), num(30))); store(3)
            get(9, PLAYERLIST, 'list', 'Ljava/util/List;'); load(3)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
            make(BLOCK, '(III)V', lambda: (num(0), num(64), num(0))); store(5)
        def death(possessed):
            bool_(possessed); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
            make(DEATH, '(L' + r.LIVING + ';)V', lambda: load(3)); store(6)
            for cls in (POSS, p.LIFECYCLE, PM):
                load(6); a.op(0xb8, cp.method(cls, 'onPlayerDeath', '(L' + DEATH + ';)V'))
        def drop(amount):
            make('java/util/ArrayList'); store(16); make(IT); store(14)
            put(14, IT, 'count', 'I', lambda: num(amount)); put(14, IT, 'id', 'Ljava/lang/String;', lambda: string('test:relic'))
            put(14, IT, 'meta', 'Ljava/lang/String;', lambda: string('enchanted'))
            make(ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(14)); store(17)
            load(16); load(17); a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
            make(r.DROP_EVENT, '(L' + r.LIVING + ';Ljava/util/Collection;)V', lambda: (load(3), load(16)))
            a.op(0xb8, cp.method(POSS, 'onPlayerDrops', '(L' + r.DROP_EVENT + ';)V'))
        def clone():
            # The real respawn handler is invoked after the real clone handler;
            # only teleportToPurgatory itself is an explicit fixture adapter.
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(2), num(30))); store(15)
            make(CLONE, '(L' + r.P + ';L' + r.P + ';)V', lambda: (load(3), load(15)))
            a.op(0xb8, cp.method(POSS, 'onPlayerClone', '(L' + CLONE + ';)V'))
            get(9, PLAYERLIST, 'list', 'Ljava/util/List;'); num(0); load(15)
            r.p.invokeinterface(a, cp, 'java/util/List', 'set', '(ILjava/lang/Object;)Ljava/lang/Object;', 3); a.raw(0x57)
            load(15); store(3)
            make(RESPAWN, '(L' + r.P + ';)V', lambda: load(3))
            a.op(0xb8, cp.method(PM, 'onPlayerRespawn', '(L' + RESPAWN + ';)V'))
        def body_id_matches():
            load(4); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;')); data(3); string(p.DEATH_CORPSE)
            a.op(0xb6, cp.method(NBT, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;'))
            a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))
        def emeralds(amount):
            make(IT); store(14); put(14, IT, 'count', 'I', lambda: num(amount))
            put(14, IT, 'kind', 'L' + ITEM + ';', lambda: a.op(0xb2, cp.field(ITEMS, 'EMERALD', 'L' + ITEM + ';')))
            make(ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(14)); store(8)
            get(2, SL, 'altarItems', 'Ljava/util/List;'); load(8)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def altar(): load(2); load(5); a.op(0xb8, cp.method(PM, 'tryReviveAtAltar', q.TRY_DESC)); a.raw(0x57)
        def tick(): make(POST, '(L' + SERVER + ';)V', lambda: load(1)); a.op(0xb8, cp.method(POSS, 'onServerTick', '(L' + POST + ';)V'))
        def count_watch(): a.op(0xb8, cp.method(POSS, 'fixtureWatchCount', '()I'))

        # Ordinary new death: stale association cleared, no corpse/watch in 243,
        # original drops stay in the world.
        env(); data(3); string(p.DEATH_CORPSE); uuid(99); a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        death(False); drop(4)
        val('ordinary.bodies', 'I', lambda: size(2)); val('ordinary.watches', 'I', count_watch)
        val('ordinary.id', 'Z', lambda: contains(3, p.DEATH_CORPSE))
        val('ordinary.world_drops', 'I', lambda: (load(16), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))
        # A different, old death ID must not survive a new ordinary death.
        env(); data(3); string(p.DEATH_CORPSE); uuid(999)
        a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        death(False); val('ordinary_old_id', 'Z', lambda: contains(3, p.DEATH_CORPSE))

        # Death in an occupied NPC, then repeated deaths and clones while in
        # purgatory; the body created in the first death stays the exact target.
        env(); position(3, 3); death(True)
        get(2, SL, 'near', 'Ljava/util/List;'); num(0); r.p.invokeinterface(a, cp, 'java/util/List', 'get', '(I)Ljava/lang/Object;', 2)
        a.op(0xc0, cp.cls(C)); store(4)
        val('start.join_count', 'I', lambda: get(2, SL, 'joins', 'I'))
        val('start.body_marked', 'Z', lambda: flag(4, p.PLAYER_BODY)); val('start.id_matches', 'Z', body_id_matches)
        drop(5); val('start.world_drops', 'I', lambda: (load(16), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))
        val('start.body_items', 'Z', lambda: contains(4, p.CORPSE_ITEMS))
        clone(); bool_(False); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
        for _ in range(3): death(False); clone()
        # Even a stale possessed flag in purgatory cannot spawn or capture.
        death(True); drop(4)
        val('repeat.possessed_world_drops', 'I', lambda: (load(16), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))
        clone(); bool_(False); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
        val('repeat.original_id', 'Z', body_id_matches); val('repeat.purg_bodies', 'I', lambda: size(11))
        val('repeat.watches', 'I', count_watch); tick(); val('repeat.pending_marker', 'Z', lambda: flag(3, p.PLAYER_PENDING))
        load(4); bool_(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        put(4, G, 'leashed', 'Z', lambda: bool_(True)); put(4, G, 'leashHolder', 'L' + E + ';', lambda: load(3))
        emeralds(12); altar()
        val('ritual.x', 'D', lambda: get(3, E, 'x', 'D')); val('ritual.body_removed', 'Z', lambda: get(4, E, 'removed', 'Z'))
        val('ritual.emeralds', 'I', lambda: (get(8, ITEM_ENTITY, 'item', 'L' + IT + ';'), a.op(0xb4, cp.field(IT, 'count', 'I'))))
        val('ritual.items', 'I', lambda: (get(3, SP, 'inventory', 'L' + INV + ';'), a.op(0xb4, cp.field(INV, 'collected', 'I'))))
        val('ritual.meta', 'Ljava/lang/String;', lambda: (get(3, SP, 'inventory', 'L' + INV + ';'), a.op(0xb4, cp.field(INV, 'lastMeta', 'Ljava/lang/String;'))))
        val('ritual.leads', 'I', lambda: get(4, G, 'leadDrops', 'I')); tick()
        val('ritual.model_restored', 'Z', lambda: flag(3, 'settledlands_possess'))
        a.raw(0xb1); return a.finish()
    return f.fake_class('NoQueueDeathHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 20, 20, main)])


def run(java, jar):
    classes, noqueue = build_with_wear(jar); classes['NoQueueDeathHarness'] = harness()
    with tempfile.TemporaryDirectory(prefix='noqueue243-jvm-') as td:
        root = pathlib.Path(td)
        for name, raw in classes.items():
            path = root / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
        result = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(root), 'NoQueueDeathHarness'], text=True, capture_output=True)
        if result.returncode: raise RuntimeError(f'{jar}: JVM exit {result.returncode}\n{result.stdout}\n{result.stderr}')
        return dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--base242', required=True); ap.add_argument('--jar', required=True)
    a = ap.parse_args(); old = run(a.java, a.base242)
    assert old['ordinary.bodies'] == '1' and old['ordinary.watches'] == '1', old
    assert old['repeat.original_id'] == 'false' and old['repeat.purg_bodies'] == '4' and old['ritual.emeralds'] == '12', old
    print('242 negative control: ordinary corpse and purgatory UUID replacement reproduced')
    got = run(a.java, a.jar)
    expected = {'ordinary.bodies':'0', 'ordinary.watches':'0', 'ordinary.id':'false', 'ordinary.world_drops':'1',
                'ordinary_old_id':'false', 'repeat.possessed_world_drops':'1',
                'start.join_count':'1', 'start.body_marked':'true', 'start.id_matches':'true',
                'start.world_drops':'0', 'start.body_items':'true', 'repeat.original_id':'true',
                'repeat.purg_bodies':'0', 'repeat.watches':'1', 'repeat.pending_marker':'true',
                'ritual.x':'3.0', 'ritual.body_removed':'true', 'ritual.emeralds':'3',
                'ritual.items':'5', 'ritual.meta':'enchanted', 'ritual.leads':'1', 'ritual.model_restored':'true'}
    bad = {k: (got.get(k), v) for k, v in expected.items() if got.get(k) != v}
    if bad: raise AssertionError(f'mismatched={bad}\nall={got}')
    print('243 JVM: real death/join/clone/drops/altar/recovery sequence, model, 9 emeralds and lead OK')
    print('Synthetic game/event APIs and visual stubs; Minecraft/NeoForge not run.')


if __name__ == '__main__': main()
