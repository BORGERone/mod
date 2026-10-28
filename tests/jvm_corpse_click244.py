#!/usr/bin/env python3
"""Build 243 negative control and 244 click/recovery paths on a real JVM.

Executes the actual 243/244 event handlers, corpse methods, strict recovery,
provenance helper and 244 dialogue guards. The client packet loop, game/NeoForge
APIs and GuideNpc's superclass dialogue fallback are executable test doubles,
not Minecraft. It models NeoForge's PASS-by-default cancellation and sends the
specific packet before the client callback, as MultiPlayerGameMode does.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

import jvm_altar242 as t
import jvm_recovery238 as r
import patch_corpse_click244 as p
from patch_probe233 import Asm

f = r.f
E, P, SP, C, G, L, SL, SERVER = r.E, r.P, r.SP, r.C, r.G, r.L, r.SL, r.SERVER
NBT, LIST, INV, IT, ITEM, ITEMS, KEY = r.NBT, r.LIST, r.INV, r.IT, r.ITEM, r.ITEMS, r.KEY
HAND, RESULT, IVY = r.HAND, r.INTERACTION, r.IVY
DIALOGUE, ALTAR, POSS, PD, PM = p.DIALOGUE, p.ALTAR, p.POSS, p.PD, p.PM
ES, EG = r.EVENT_S, r.EVENT_G
DISPATCH = 'fixture/ClientDispatch'


def hand_class():
    def init(cp):
        a = Asm()
        for name in ('MAIN_HAND', 'OFF_HAND'):
            r.obj(a, cp, HAND); a.op(0xb3, cp.field(HAND, name, 'L' + HAND + ';'))
        a.raw(0xb1); return a.finish()
    return f.fake_class(HAND, [(9, 'MAIN_HAND', 'L' + HAND + ';'), (9, 'OFF_HAND', 'L' + HAND + ';')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()), (8, '<clinit>', '()V', 2, 0, init)])


def result_class():
    def init(cp):
        a = Asm()
        for name in ('PASS', 'SUCCESS', 'CONSUME'):
            r.obj(a, cp, RESULT); a.op(0xb3, cp.field(RESULT, name, 'L' + RESULT + ';'))
        a.raw(0xb1); return a.finish()
    def success(cp):
        a = Asm(); a.raw(0x1a); a.jump(0x99, 'server')
        a.op(0xb2, cp.field(RESULT, 'SUCCESS', 'L' + RESULT + ';')); a.raw(0xb0)
        a.label('server'); a.op(0xb2, cp.field(RESULT, 'CONSUME', 'L' + RESULT + ';')); a.raw(0xb0)
        return a.finish()
    def consumes(cp):
        a = Asm(); a.raw(0x2a); a.op(0xb2, cp.field(RESULT, 'PASS', 'L' + RESULT + ';'))
        a.jump(0xa5, 'no'); a.raw(0x04, 0xac); a.label('no'); a.raw(0x03, 0xac); return a.finish()
    return f.fake_class(RESULT, [(9, name, 'L' + RESULT + ';') for name in ('PASS', 'SUCCESS', 'CONSUME')], [
        (1, '<init>', '()V', 1, 1, f.empty_init()), (8, '<clinit>', '()V', 2, 0, init),
        (9, 'sidedSuccess', '(Z)L' + RESULT + ';', 1, 1, success),
        (1, 'consumesAction', '()Z', 2, 1, consumes)])


def with_event_api(raw, name):
    def ctor(k):
        return (b'\x2a\x2b\x2d' + t.op(0xb7, k.add_methodref(r.INTERACT_EVENT, '<init>', '(L' + P + ';L' + IT + ';)V')) +
                b'\x2a\x2c' + t.op(0xb5, k.add_fieldref(name, 'target', 'L' + E + ';')) +
                b'\x2a' + t.op(0xb2, k.add_fieldref(RESULT, 'PASS', 'L' + RESULT + ';')) +
                t.op(0xb5, k.add_fieldref(name, 'result', 'L' + RESULT + ';')) + b'\xb1')
    return t.rewrite(raw, add_fields=[(1, 'handType', 'L' + HAND + ';'), (1, 'result', 'L' + RESULT + ';')],
        add_methods=[(1, 'getHand', '()L' + HAND + ';', 1, 1,
                      lambda k: b'\x2a' + t.op(0xb4, k.add_fieldref(name, 'handType', 'L' + HAND + ';')) + b'\xb0'),
                     (1, 'setCancellationResult', '(L' + RESULT + ';)V', 2, 2,
                      lambda k: b'\x2a\x2b' + t.op(0xb5, k.add_fieldref(name, 'result', 'L' + RESULT + ';')) + b'\xb1')],
        replace=[('<init>', '(L' + P + ';L' + E + ';L' + IT + ';)V', 3, 4, ctor)])


def player_hands(raw):
    def get(k):
        a = Asm(); a.raw(0x2b); a.op(0xb2, k.add_fieldref(HAND, 'OFF_HAND', 'L' + HAND + ';'))
        a.jump(0xa6, 'main'); a.raw(0x2a); a.op(0xb4, k.add_fieldref(P, 'offhand', 'L' + IT + ';')); a.raw(0xb0)
        a.label('main'); a.raw(0x2a); a.op(0xb4, k.add_fieldref(P, 'held', 'L' + IT + ';')); a.raw(0xb0); return a.finish()
    return t.rewrite(raw, add_fields=[(1, 'offhand', 'L' + IT + ';')],
                     replace=[('getItemInHand', '(L' + HAND + ';)L' + IT + ';', 2, 2, get)])


def dialogue_stub(with_guard, jar):
    def start(k):
        # Straight-line bridge: the major-65 original class needs stack maps
        # for branches, so counting lives in a separate major-49 fixture class.
        return b'\x2a' + t.op(0xb8, k.add_methodref('fixture/DialogueCounter', 'record', '(L' + P + ';)V')) + b'\xb1'
    if with_guard:
        raw = r.subset(jar, DIALOGUE, {'start', p.ALIAS_START, 'listenToNpc', p.ALIAS_LISTEN})
        return t.rewrite(raw, add_fields=[(9, 'opens', 'I')], replace=[
            (p.ALIAS_START, p.START_DESC, 2, 2, start),
            (p.ALIAS_LISTEN, p.LISTEN_DESC, 1, 2, lambda k: b'\x04\xac')])
    def old_start(cp):
        a = Asm(); a.raw(0x2a); a.op(0xc1, cp.cls(SP)); a.jump(0x99, 'done')
        a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')); a.raw(0x04, 0x60)
        a.op(0xb3, cp.field(DIALOGUE, 'opens', 'I')); a.label('done'); a.raw(0xb1); return a.finish()
    return f.fake_class(DIALOGUE, [(9, 'opens', 'I')], [
        (9, 'start', p.START_DESC, 2, 2, old_start), (9, 'listenToNpc', p.LISTEN_DESC, 1, 2, lambda cp: b'\x04\xac')])


def superclass_dialogue_fallback(raw):
    # Models GuideNpc.mobInteract's unlocked ordinary-dialogue branch. The real
    # branch is executed separately in jvm_real_corpse_dialogue244.py.
    def mob(k):
        count = k.add_fieldref(G, 'fallbackCount', 'I')
        return (b'\x2a\x59' + t.op(0xb4, count) + b'\x04\x60' + t.op(0xb5, count) +
                b'\x2b\x2a' + t.op(0xb8, k.add_methodref(DIALOGUE, 'start', p.START_DESC)) +
                b'\x2a' + t.op(0xb6, k.add_methodref(G, 'level', '()L' + L + ';')) +
                t.op(0xb6, k.add_methodref(L, 'isClientSide', '()Z')) +
                t.op(0xb8, k.add_methodref(RESULT, 'sidedSuccess', '(Z)L' + RESULT + ';')) + b'\xb0')
    return t.rewrite(raw, replace=[('mobInteract', p.MOB_DESC, 3, 3, mob)])


def build_classes(jar):
    classes = t.build_classes(jar)
    with zipfile.ZipFile(jar) as z:
        guarded = p.GUARD + '.class' in z.namelist()
        if guarded: classes[p.GUARD] = z.read(p.GUARD + '.class')
    corpse_names = {'<init>', '<clinit>', 'setOwner', 'isCoffin', 'setCoffin', 'settledlands$getOwnerUuid', 'mobInteract'}
    if guarded: corpse_names.add(p.ALIAS_MOB)
    classes[C] = r.subset(jar, C, corpse_names, expose={'mobInteract'})
    ivy_names = {'editingTool', 'onInteractCorpse', 'onInteractSpecificCorpse'}
    if guarded: ivy_names.add(p.EDIT_BRIDGE)
    classes[IVY] = r.subset(jar, IVY, ivy_names)
    classes[DIALOGUE] = dialogue_stub(guarded, jar)
    def record(cp):
        a = Asm(); a.raw(0x2a); a.op(0xc1, cp.cls(SP)); a.jump(0x99, 'done')
        a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')); a.raw(0x04, 0x60)
        a.op(0xb3, cp.field(DIALOGUE, 'opens', 'I')); a.label('done'); a.raw(0xb1); return a.finish()
    classes['fixture/DialogueCounter'] = f.fake_class('fixture/DialogueCounter', [], [
        (9, 'record', '(L' + P + ';)V', 2, 1, record)])
    classes[G] = superclass_dialogue_fallback(classes[G])
    classes[P] = player_hands(classes[P]); classes[HAND] = hand_class(); classes[RESULT] = result_class()
    for event in (ES, EG): classes[event] = with_event_api(classes[event], event)
    # Queue disabled in 243/244; the real disabled queue is tested separately.
    classes[PD] = t.rewrite(classes[PD], replace=[('isDeadInQueue', '(Ljava/util/UUID;)Z', 1, 2, lambda k: b'\x03\xac')])
    classes[DISPATCH] = dispatch_class()
    classes['CorpseClickHarness'] = harness()
    return classes


def dispatch_class():
    # (client player, client body, server player, server body, hand) -> consumed.
    desc = '(L' + P + ';L' + C + ';L' + SP + ';L' + C + ';L' + HAND + ';)Z'
    def emit(cp):
        a = Asm()
        def load(i): r.p.local(a, 'aload', i)
        def store(i): r.p.local(a, 'astore', i)
        def event(event_type, player, target, dest):
            r.obj(a, cp, event_type, '(L' + P + ';L' + E + ';L' + IT + ';)V',
                  lambda: (load(player), load(target), load(player), load(4),
                           a.op(0xb6, cp.method(P, 'getItemInHand', '(L' + HAND + ';)L' + IT + ';'))))
            store(dest); load(dest); load(4); a.op(0xb5, cp.field(event_type, 'handType', 'L' + HAND + ';'))
        def packet():
            a.op(0xb2, cp.field(DISPATCH, 'packets', 'I')); a.raw(0x04, 0x60)
            a.op(0xb3, cp.field(DISPATCH, 'packets', 'I'))
        def server_event(event_type, name, handler):
            event(event_type, 2, 3, 6); load(6); a.op(0xb8, cp.method(IVY, handler, '(L' + event_type + ';)V'))
            if name == 'general':
                load(6); a.op(0xb4, cp.field(event_type, 'canceled', 'Z')); a.jump(0x9a, 'server_general_done')
                load(3); load(2); load(4); a.op(0xb6, cp.method(C, 'mobInteract', p.MOB_DESC)); a.raw(0x57)
                a.label('server_general_done')
        # The client sends the packet before its event hook. Server execution
        # is interleaved here but both sides use separate player/body objects.
        packet(); event(ES, 0, 1, 5)
        load(5); a.op(0xb8, cp.method(IVY, 'onInteractSpecificCorpse', '(L' + ES + ';)V'))
        server_event(ES, 'specific', 'onInteractSpecificCorpse')
        load(5); a.op(0xb4, cp.field(ES, 'canceled', 'Z')); a.jump(0x99, 'try_general')
        load(5); a.op(0xb4, cp.field(ES, 'result', 'L' + RESULT + ';'))
        a.op(0xb6, cp.method(RESULT, 'consumesAction', '()Z')); a.jump(0x9a, 'yes')
        a.label('try_general'); packet(); event(EG, 0, 1, 5)
        load(5); a.op(0xb8, cp.method(IVY, 'onInteractCorpse', '(L' + EG + ';)V'))
        server_event(EG, 'general', 'onInteractCorpse')
        load(5); a.op(0xb4, cp.field(EG, 'canceled', 'Z')); a.jump(0x99, 'client_mob')
        load(5); a.op(0xb4, cp.field(EG, 'result', 'L' + RESULT + ';')); a.jump(0xa7, 'result')
        a.label('client_mob'); load(1); load(0); load(4); a.op(0xb6, cp.method(C, 'mobInteract', p.MOB_DESC))
        a.label('result'); a.op(0xb6, cp.method(RESULT, 'consumesAction', '()Z')); a.raw(0xac)
        a.label('yes'); a.raw(0x04, 0xac); return a.finish()
    return f.fake_class(DISPATCH, [(9, 'packets', 'I')], [(9, 'oneHand', desc, 6, 7, emit)])


def harness():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        def load(i): r.p.local(a, 'aload', i)
        def store(i): r.p.local(a, 'astore', i)
        def num(n): r.const(a, n)
        def s(x): r.text(a, cp, x)
        def boolean(v): a.raw(0x04 if v else 0x03)
        def make(name, desc='()V', args=None): r.obj(a, cp, name, desc, args)
        def field(i, owner, name, desc): load(i); a.op(0xb4, cp.field(owner, name, desc))
        def put(i, owner, name, desc, emit): load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def value(label, typ, emit):
            a.op(0xb2, out); s(label + '='); a.op(0xb6, cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V'))
            a.op(0xb2, out); emit(); a.op(0xb6, cp.method('java/io/PrintStream', 'println', '(' + typ + ')V'))
        def uuid(n):
            s('00000000-0000-0000-0000-%012d' % n); a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(i): load(i); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def has(i, key): data(i); s(key); a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
        def world(i, target): put(i, E, 'world', 'L' + L + ';', lambda: load(target))
        def held(player, hand, kind=None, count=0):
            make(IT); store(9); put(9, IT, 'count', 'I', lambda: num(count))
            if kind == 'lead': put(9, IT, 'kind', 'L' + ITEM + ';', lambda: a.op(0xb2, cp.field(ITEMS, 'LEAD', 'L' + ITEM + ';')))
            elif kind:
                put(9, IT, 'kind', 'L' + ITEM + ';', lambda: make(kind))
            put(player, P, 'held' if hand == 'main' else 'offhand', 'L' + IT + ';', lambda: load(9))
        def env(room=30, coffin=False):
            a.raw(0x03); a.op(0xb3, cp.field(DISPATCH, 'packets', 'I'))
            a.raw(0x03); a.op(0xb3, cp.field(DIALOGUE, 'opens', 'I'))
            make(SERVER); store(1); make(KEY); store(2)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(2), load(1))); store(3)
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(1), load(3), num(room))); store(4)
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(3))); store(5)
            load(5); load(4); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
            make(L, '(ZL' + KEY + ';L' + SERVER + ';)V', lambda: (boolean(True), load(2), a.raw(0x01))); store(6)
            make(P, '(Ljava/util/UUID;L' + L + ';)V', lambda: (uuid(1), load(6))); store(7)
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(6))); store(8)
            for player in (4, 7): held(player, 'main'); held(player, 'off')
            if coffin:
                for body in (5, 8): load(body); boolean(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        def items(target, key, count=5, invalid=0, meta='enchanted'):
            make(LIST); store(10); make(NBT); store(11)
            put(11, NBT, 'count', 'I', lambda: num(count)); put(11, NBT, 'invalid', 'I', lambda: num(invalid))
            put(11, NBT, 'id', 'Ljava/lang/String;', lambda: s('test:relic'))
            put(11, NBT, 'meta', 'Ljava/lang/String;', lambda: s(meta))
            load(10); load(11); a.op(0xb6, cp.method(LIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
            data(target); s(key); load(10); a.op(0xb6, cp.method(NBT, 'put', '(Ljava/lang/String;L' + r.TAG + ';)L' + r.TAG + ';')); a.raw(0x57)
        def bind(current=True, source=True):
            for key, enabled in [(p.DEATH_ID, current), (p.SOURCE_ID, source)]:
                if enabled:
                    data(4); s(key); load(5); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
                    a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        def different_id(key):
            data(4); s(key); uuid(999); a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V'))
        def click(hand='main'):
            load(7); load(8); load(4); load(5)
            a.op(0xb2, cp.field(HAND, 'MAIN_HAND' if hand == 'main' else 'OFF_HAND', 'L' + HAND + ';'))
            a.op(0xb8, cp.method(DISPATCH, 'oneHand', '(L' + P + ';L' + C + ';L' + SP + ';L' + C + ';L' + HAND + ';)Z'))
        def collected(): field(4, SP, 'inventory', 'L' + INV + ';'); a.op(0xb4, cp.field(INV, 'collected', 'I'))
        def room(n): field(4, SP, 'inventory', 'L' + INV + ';'); num(n); a.op(0xb5, cp.field(INV, 'room', 'I'))
        def leashed(): put(5, G, 'leashed', 'Z', lambda: boolean(True)); put(5, G, 'leashHolder', 'L' + E + ';', lambda: load(4))
        def message(): field(4, SP, 'lastMessage', 'Ljava/lang/String;')
        def remaining():
            has(5, p.CORPSE_ITEMS); a.jump(0x99, 'remaining_missing')
            data(5); s(p.CORPSE_ITEMS); num(10); a.op(0xb6, cp.method(NBT, 'getList', '(Ljava/lang/String;I)L' + LIST + ';'))
            num(0); a.op(0xb6, cp.method(LIST, 'getCompound', '(I)L' + NBT + ';')); a.op(0xb4, cp.field(NBT, 'count', 'I'))
            a.jump(0xa7, 'remaining_done'); a.label('remaining_missing'); a.raw(0x02); a.label('remaining_done')

        # Captured list stayed on the respawned owner even though both IDs prove
        # the clicked body. Build 243's event reaches tryClaim but misses it.
        env(); items(4, p.PLAYER_ITEMS); bind(); value('pending.consumed', 'Z', click)
        value('pending.items', 'I', collected); value('pending.kept', 'Z', lambda: has(4, p.PLAYER_ITEMS))
        value('pending.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))
        value('pending.packets', 'I', lambda: a.op(0xb2, cp.field(DISPATCH, 'packets', 'I')))

        # Each server event must independently handle loot. Invoke them in
        # isolation in different hands, rather than relying on dispatch order.
        for label, event, handler, hand in [('general_main', EG, 'onInteractCorpse', 'MAIN_HAND'),
                                            ('specific_off', ES, 'onInteractSpecificCorpse', 'OFF_HAND')]:
            env(); items(4, p.PLAYER_ITEMS); bind()
            make(event, '(L' + P + ';L' + E + ';L' + IT + ';)V',
                 lambda: (load(4), load(5), field(4, P, 'held' if hand == 'MAIN_HAND' else 'offhand', 'L' + IT + ';')))
            store(14); put(14, event, 'handType', 'L' + HAND + ';', lambda: a.op(0xb2, cp.field(HAND, hand, 'L' + HAND + ';')))
            load(14); a.op(0xb8, cp.method(IVY, handler, '(L' + event + ';)V'))
            value(label + '.items', 'I', collected); value(label + '.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))
            value(label + '.canceled', 'Z', lambda: field(14, event, 'canceled', 'Z'))
            value(label + '.consumed', 'Z', lambda: (field(14, event, 'result', 'L' + RESULT + ';'),
                  a.op(0xb6, cp.method(RESULT, 'consumesAction', '()Z'))))
        # The entity fallback also works if the event bus was bypassed.
        env(); items(4, p.PLAYER_ITEMS); bind(); load(5); load(4)
        a.op(0xb2, cp.field(HAND, 'MAIN_HAND', 'L' + HAND + ';')); a.op(0xb6, cp.method(C, 'mobInteract', p.MOB_DESC)); store(14)
        value('fallback.consumed', 'Z', lambda: (load(14), a.op(0xb6, cp.method(RESULT, 'consumesAction', '()Z'))))
        value('fallback.items', 'I', collected); value('fallback.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))

        # Partial recovery retains the exact NBT, body and lead; next click
        # completes only the remainder and returns exactly one lead.
        env(3, True); items(4, p.PLAYER_ITEMS, 5); bind(); leashed(); click(); a.raw(0x57)
        value('partial.items', 'I', collected); value('partial.remaining', 'I', remaining)
        value('partial.body_removed', 'Z', lambda: field(5, E, 'removed', 'Z'))
        value('partial.leashed', 'Z', lambda: field(5, G, 'leashed', 'Z'))
        value('partial.meta', 'Ljava/lang/String;', lambda: (field(4, SP, 'inventory', 'L' + INV + ';'), a.op(0xb4, cp.field(INV, 'lastMeta', 'Ljava/lang/String;'))))
        room(8); click(); a.raw(0x57)
        value('complete.items', 'I', collected); value('complete.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))
        value('complete.leads', 'I', lambda: field(5, G, 'leadDrops', 'I'))
        # Direct repeated call is also harmless after removal.
        load(4); load(5); a.op(0xb8, cp.method(r.H, 'tryClaim', p.CLAIM_DESC))
        value('repeat.items', 'I', collected); value('repeat.leads', 'I', lambda: field(5, G, 'leadDrops', 'I'))
        # Adversarial stale event: even a removed object whose old NBT is
        # erroneously visible again must not grant or drop anything twice.
        items(5, p.CORPSE_ITEMS, 5); load(4); load(5); a.op(0xb8, cp.method(r.H, 'tryClaim', p.CLAIM_DESC))
        value('removed_replay.items', 'I', collected); value('removed_replay.leads', 'I', lambda: field(5, G, 'leadDrops', 'I'))

        # Missing provenance, stale current ID, and conflicting lists never get
        # merged or discarded. A different proven body's list is independent.
        env(); items(4, p.PLAYER_ITEMS); bind(current=True, source=False); click(); a.raw(0x57)
        value('unknown.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('unknown.items', 'I', collected)
        value('unknown.message', 'Ljava/lang/String;', message)
        env(); items(4, p.PLAYER_ITEMS); bind(); different_id(p.DEATH_ID); click(); a.raw(0x57)
        value('stale.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('stale.items', 'I', collected)
        value('stale.message', 'Ljava/lang/String;', message)
        env(); items(4, p.PLAYER_ITEMS, 5); bind(); items(5, p.CORPSE_ITEMS, 3); click(); a.raw(0x57)
        value('conflict.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('conflict.body', 'Z', lambda: has(5, p.CORPSE_ITEMS))
        value('conflict.items', 'I', collected); value('conflict.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))
        value('conflict.message', 'Ljava/lang/String;', message)
        env(); items(4, p.PLAYER_ITEMS, 5); bind(); different_id(p.SOURCE_ID); items(5, p.CORPSE_ITEMS, 3)
        click(); a.raw(0x57); value('otherdeath.items', 'I', collected)
        value('otherdeath.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS))
        value('otherdeath.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))

        # Unauthorized, dead, or purgatory players cannot transfer even a
        # perfectly proven list, including when the event is forced directly.
        env(); items(4, p.PLAYER_ITEMS); bind(); uuid(2); store(12)
        put(4, P, 'uuid', 'Ljava/util/UUID;', lambda: load(12)); click(); a.raw(0x57)
        value('foreign.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('foreign.items', 'I', collected)
        value('foreign.body', 'Z', lambda: has(5, p.CORPSE_ITEMS))
        env(); items(4, p.PLAYER_ITEMS); bind(); put(4, SP, 'alive', 'Z', lambda: boolean(False))
        load(4); load(5); a.op(0xb8, cp.method(r.H, 'tryClaim', p.CLAIM_DESC))
        value('dead.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('dead.items', 'I', collected)
        env(); items(4, p.PLAYER_ITEMS); bind(); make(SL, '(L' + KEY + ';L' + SERVER + ';)V',
            lambda: (a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')), load(1))); store(13)
        world(4, 13); load(4); load(5); a.op(0xb8, cp.method(r.H, 'tryClaim', p.CLAIM_DESC))
        value('purg.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('purg.items', 'I', collected)

        # A body without any list reports it and is not destroyed.
        env(); click(); a.raw(0x57); value('missing.message', 'Ljava/lang/String;', message)
        value('missing.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))

        # The strict codec keeps invalid items, even when the inventory has room.
        env(); items(5, p.CORPSE_ITEMS, 5, invalid=2); click(); a.raw(0x57)
        value('invalid.body', 'Z', lambda: has(5, p.CORPSE_ITEMS)); value('invalid.items', 'I', collected)
        value('invalid.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))

        # Unboxed corpse + held lead was a real editing-tool bypass into the
        # superclass dialogue branch. Tome exercises the guarded fallback too.
        for label, kind in [('lead_dialogue', 'lead'), ('tome_dialogue', r.TOOLS[4])]:
            env(); held(4, 'main', kind, 1); held(7, 'main', kind, 1); click(); a.raw(0x57)
            value(label, 'I', lambda: a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')))
        # Corpse-specific and ordinary live NPC dialogue entry points.
        env(); load(4); load(5); a.op(0xb8, cp.method(DIALOGUE, 'start', p.START_DESC))
        value('direct.corpse', 'I', lambda: a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')))
        value('direct.corpse_listen', 'Z', lambda: (load(4), load(5), a.op(0xb8, cp.method(DIALOGUE, 'listenToNpc', p.LISTEN_DESC))))
        make(G, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(3))); store(12)
        load(4); load(12); a.op(0xb8, cp.method(DIALOGUE, 'start', p.START_DESC))
        value('direct.live', 'I', lambda: a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')))
        value('direct.live_listen', 'Z', lambda: (load(4), load(12), a.op(0xb8, cp.method(DIALOGUE, 'listenToNpc', p.LISTEN_DESC))))

        # Neither NeoForge handler should seize the original editing tools on
        # an unboxed body; the held tool is in a different hand for each event.
        for tool in r.TOOLS:
            for event, handler, hand in [(EG, 'onInteractCorpse', 'MAIN_HAND'),
                                         (ES, 'onInteractSpecificCorpse', 'OFF_HAND')]:
                env(); held(4, 'main' if hand == 'MAIN_HAND' else 'off', tool, 1)
                make(event, '(L' + P + ';L' + E + ';L' + IT + ';)V',
                     lambda: (load(4), load(5), field(4, P, 'held' if hand == 'MAIN_HAND' else 'offhand', 'L' + IT + ';')))
                store(14); put(14, event, 'handType', 'L' + HAND + ';', lambda: a.op(0xb2, cp.field(HAND, hand, 'L' + HAND + ';')))
                load(14); a.op(0xb8, cp.method(IVY, handler, '(L' + event + ';)V'))
                value('editor.' + tool.split('/')[-1] + '.' + hand, 'Z', lambda: field(14, event, 'canceled', 'Z'))

        # Real coffin conversion, lead attachment, off-hand priority and unlink.
        env(); held(4, 'main', p.COFFIN, 1); held(7, 'main', p.COFFIN, 1); click(); a.raw(0x57)
        value('tool.boxed', 'Z', lambda: (load(5), a.op(0xb6, cp.method(C, 'isCoffin', '()Z'))))
        value('tool.coffin_count', 'I', lambda: (field(4, P, 'held', 'L' + IT + ';'), a.op(0xb4, cp.field(IT, 'count', 'I'))))
        held(4, 'main', 'lead', 1); held(7, 'main', 'lead', 1); click(); a.raw(0x57)
        value('tool.lead_attached', 'Z', lambda: field(5, G, 'leashed', 'Z'))
        held(4, 'main'); held(7, 'main'); click(); a.raw(0x57)
        value('tool.unlinked', 'Z', lambda: field(5, G, 'leashed', 'Z'))
        value('tool.lead_drops', 'I', lambda: field(5, G, 'leadDrops', 'I'))
        env(coffin=True); held(4, 'off', 'lead', 1); held(7, 'off', 'lead', 1)
        value('offhand.main_consumed', 'Z', click); click('off'); a.raw(0x57)
        value('offhand.attached', 'Z', lambda: field(5, G, 'leashed', 'Z'))

        # Legacy auto-restore should not consume a pending list on a full inventory.
        env(0); items(4, p.PLAYER_ITEMS); bind()
        load(4); a.op(0xb8, cp.method(ALTAR, 'guardLegacyRestore', '(L' + SP + ';)V'))
        value('legacy.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); value('legacy.items', 'I', collected)
        a.raw(0xb1); return a.finish()
    return f.fake_class('CorpseClickHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 18, 16, main)])


def run(java, jar):
    classes = build_classes(jar)
    with tempfile.TemporaryDirectory(prefix='corpse-click244-jvm-') as temp:
        root = pathlib.Path(temp)
        for name, raw in classes.items():
            path = root / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
        result = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(root), 'CorpseClickHarness'],
                                text=True, capture_output=True)
        if result.returncode: raise RuntimeError(f'{jar}: JVM exit {result.returncode}\n{result.stdout}\n{result.stderr}')
        return dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--base243', required=True); ap.add_argument('--jar', required=True)
    args = ap.parse_args(); old = run(args.java, args.base243); got = run(args.java, args.jar)
    for key, expected in {'pending.items':'0', 'pending.kept':'true', 'pending.consumed':'false', 'pending.packets':'2',
                          'lead_dialogue':'1', 'tome_dialogue':'1', 'direct.corpse':'1',
                          'conflict.removed':'true', 'legacy.pending':'false'}.items():
        if old.get(key) != expected: raise AssertionError(f'243 negative control {key}: {old.get(key)!r} != {expected!r}\n{old}')
    expected = {
        'pending.consumed':'true', 'pending.items':'5', 'pending.kept':'false', 'pending.removed':'true', 'pending.packets':'2',
        'general_main.items':'5', 'general_main.removed':'true', 'general_main.canceled':'true', 'general_main.consumed':'true',
        'specific_off.items':'5', 'specific_off.removed':'true', 'specific_off.canceled':'true', 'specific_off.consumed':'true',
        'fallback.consumed':'true', 'fallback.items':'5', 'fallback.removed':'true',
        'partial.items':'3', 'partial.remaining':'2', 'partial.body_removed':'false', 'partial.leashed':'true', 'partial.meta':'enchanted',
        'complete.items':'5', 'complete.removed':'true', 'complete.leads':'1', 'repeat.items':'5', 'repeat.leads':'1',
        'removed_replay.items':'5', 'removed_replay.leads':'1',
        'unknown.pending':'true', 'unknown.items':'0', 'unknown.message':p.UNVERIFIED,
        'stale.pending':'true', 'stale.items':'0', 'stale.message':p.UNVERIFIED,
        'conflict.pending':'true', 'conflict.body':'true', 'conflict.items':'0', 'conflict.removed':'false', 'conflict.message':p.CONFLICT,
        'otherdeath.items':'3', 'otherdeath.pending':'true', 'otherdeath.removed':'true',
        'foreign.pending':'true', 'foreign.items':'0', 'foreign.body':'false', 'dead.pending':'true', 'dead.items':'0',
        'purg.pending':'true', 'purg.items':'0', 'missing.message':p.MISSING, 'missing.removed':'false',
        'invalid.body':'true', 'invalid.items':'0', 'invalid.removed':'false',
        'lead_dialogue':'0', 'tome_dialogue':'0', 'direct.corpse':'0', 'direct.corpse_listen':'false',
        'direct.live':'1', 'direct.live_listen':'true', 'tool.boxed':'true', 'tool.coffin_count':'0',
        'tool.lead_attached':'true', 'tool.unlinked':'false', 'tool.lead_drops':'1',
        'offhand.main_consumed':'false', 'offhand.attached':'true', 'legacy.pending':'true', 'legacy.items':'0',
    }
    expected.update({'editor.' + tool.split('/')[-1] + '.' + hand:'false'
                     for tool in r.TOOLS for hand in ('MAIN_HAND', 'OFF_HAND')})
    bad = {key: (got.get(key), want) for key, want in expected.items() if got.get(key) != want}
    if bad: raise AssertionError(f'mismatches={bad}\nall={got}')
    print('243 negative controls: pending retrieval, PASS packets, tool dialogue, conflict deletion and legacy loss reproduced')
    print('244 JVM: complete/partial recovery, provenance/conflicts, permissions, invalid NBT, tool/dialogue/lead and offhand dispatch OK')
    print('Synthetic game/event APIs; superclass dialogue fallback and original dialogue effects are explicit stand-ins, not Minecraft.')


if __name__ == '__main__': main()
