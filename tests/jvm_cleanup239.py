#!/usr/bin/env python3
"""Exercise actual 239 event/recovery bytecode on Java 21 with test doubles.

A calibrated build 238 is the negative control: it recovers the same items but
never discards the entity. These are JVM fixture tests, not a Minecraft test.
"""
import argparse
import pathlib
import subprocess
import tempfile

import jvm_recovery238 as r
from patch_probe233 import Asm


def harness_class():
    def main(cp):
        a = Asm()
        E, P, SP, L, SL, SERVER, C = r.E, r.P, r.SP, r.L, r.SL, r.SERVER, r.C
        TAG, NBT, LIST, IT, INV = r.TAG, r.NBT, r.LIST, r.IT, r.INV
        KEY, PD, PM, IVY = r.KEY, r.PD, r.PM, r.IVY
        ES, EG = r.EVENT_S, r.EVENT_G
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        printers = {d: cp.method('java/io/PrintStream', 'println', '(' + d + ')V')
                    for d in ('I', 'Z', 'Ljava/lang/String;')}

        def load(i): r.insn_local(a, 'aload', i)
        def store(i): r.insn_local(a, 'astore', i)
        def text(s): r.text(a, cp, s)
        def num(i): r.const(a, i)
        def boolean(x): a.raw(0x04 if x else 0x03)
        def obj(name, desc='()V', args=None): r.obj(a, cp, name, desc, args)
        def field(i, owner, name, desc):
            load(i); a.op(0xb4, cp.field(owner, name, desc))
        def assign(i, owner, name, desc, emit):
            load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def uuid(n=1):
            text(f'00000000-0000-0000-0000-{n:012d}')
            a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def println(label, typ, emit):
            a.op(0xb2, out); text(label + '='); a.op(0xb6, print_s)
            a.op(0xb2, out); emit(); a.op(0xb6, printers[typ])
        def inventory(actor=3): field(actor, SP, 'inventory', 'L' + INV + ';')
        def collected(label, actor=3):
            println(label, 'I', lambda: (inventory(actor), a.op(0xb4, cp.field(INV, 'collected', 'I'))))
        def removed(label, corpse=4): println(label, 'Z', lambda: field(corpse, E, 'removed', 'Z'))
        def count(label, corpse=4): println(label, 'I', lambda: field(corpse, E, 'discardCount', 'I'))
        def canceled(label, event): println(label, 'Z', lambda: field(5, event, 'canceled', 'Z'))
        def data(corpse=4):
            load(corpse); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def contains(label, corpse=4, key='settledlands_corpse_items'):
            def emit():
                data(corpse); text(key)
                a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
            println(label, 'Z', emit)
        def tag_count(label, tag=7): println(label, 'I', lambda: field(tag, NBT, 'count', 'I'))
        def set_room(amount):
            inventory(); num(amount); a.op(0xb5, cp.field(INV, 'room', 'I'))
        def set_queue():
            load(3); a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;'))
            a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
        def env(room=30, *, owner=True, coffin=False):
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
            obj(SERVER); store(1); obj(KEY); store(13)
            obj(SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(13), load(1))); store(2)
            obj(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(2), num(room))); store(3)
            obj(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(4)
            if owner:
                load(4); load(3); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
            if coffin:
                load(4); boolean(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        def make_list(corpse=4, local=6):
            obj(LIST); store(local); data(corpse); text('settledlands_corpse_items'); load(local)
            a.op(0xb6, cp.method(NBT, 'put', '(Ljava/lang/String;L' + TAG + ';)L' + TAG + ';')); a.raw(0x57)
        def item(amount, *, local=7, lst=6, invalid=0):
            obj(NBT); store(local)
            assign(local, NBT, 'count', 'I', lambda: num(amount))
            assign(local, NBT, 'id', 'Ljava/lang/String;', lambda: text('test:relic'))
            assign(local, NBT, 'meta', 'Ljava/lang/String;', lambda: text('test:{enchantment:kept}'))
            if invalid: assign(local, NBT, 'invalid', 'I', lambda: num(invalid))
            load(lst); load(local); a.op(0xb6, cp.method(LIST, 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
        def other_corpse():
            obj(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(8)
            load(8); load(3); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
        def click(event=ES, actor=3, target=4, kind=None):
            obj(IT); store(12)
            if kind:
                if kind == 'lead':
                    assign(12, IT, 'kind', 'L' + r.ITEM + ';',
                           lambda: r.static_field(a, cp, r.ITEMS, 'LEAD', 'L' + r.ITEM + ';'))
                else:
                    assign(12, IT, 'kind', 'L' + r.ITEM + ';', lambda: obj(kind))
            obj(event, '(L' + P + ';L' + E + ';L' + IT + ';)V', lambda: (load(actor), load(target), load(12)))
            store(5); load(5)
            name = 'onInteractSpecificCorpse' if event == ES else 'onInteractCorpse'
            a.op(0xb8, cp.method(IVY, name, '(L' + event + ';)V'))
        def msg_count(label): println(label, 'I', lambda: field(3, SP, 'messageCount', 'I'))

        # End-to-end real mod methods: capture a possessed player's actual drops,
        # attach to that owner's corpse, handle purgatory exit, then reclaim.
        env()
        field(2, SL, 'near', 'Ljava/util/List;'); load(4)
        r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        boolean(True); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
        obj('java/util/ArrayList'); store(6); obj(IT); store(7)
        assign(7, IT, 'count', 'I', lambda: num(5))
        assign(7, IT, 'id', 'Ljava/lang/String;', lambda: text('mod:enchanted_tool'))
        assign(7, IT, 'meta', 'Ljava/lang/String;', lambda: text('mod:{enchanted:yes}'))
        load(6); obj(r.ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(7))
        a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
        obj(r.DROP_EVENT, '(L' + r.LIVING + ';Ljava/util/Collection;)V', lambda: (load(3), load(6))); store(5)
        load(5); a.op(0xb8, cp.method(r.POSS, 'onPlayerDrops', '(L' + r.DROP_EVENT + ';)V'))
        println('pipeline.world_drops', 'I', lambda: (load(6), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))
        contains('pipeline.pending', corpse=3, key='settledlands_body_items'); contains('pipeline.corpse_key')
        set_queue(); assign(2, L, 'dim', 'L' + KEY + ';', lambda: r.static_field(a, cp, PM, 'PURGATORY_LEVEL', 'L' + KEY + ';'))
        click(); removed('pipeline.blocked_removed'); collected('pipeline.blocked_items')
        assign(2, L, 'dim', 'L' + KEY + ';', lambda: load(13))
        obj(r.DIM_EVENT, '(L' + P + ';L' + KEY + ';L' + KEY + ';)V',
            lambda: (load(3), r.static_field(a, cp, PM, 'PURGATORY_LEVEL', 'L' + KEY + ';'), load(13)))
        a.op(0xb8, cp.method(PM, 'onDimensionChange', '(L' + r.DIM_EVENT + ';)V'))
        click(); removed('pipeline.removed'); count('pipeline.discards'); collected('pipeline.items')
        println('pipeline.meta', 'Ljava/lang/String;', lambda: (inventory(), a.op(0xb4, cp.field(INV, 'lastMeta', 'Ljava/lang/String;'))))
        contains('pipeline.key_after')

        # An ordinary death still drops items normally, and its empty body is
        # never removed by a subsequent recovery click.
        env(); boolean(False); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
        obj('java/util/ArrayList'); store(6); obj(IT); store(7)
        assign(7, IT, 'count', 'I', lambda: num(2))
        load(6); obj(r.ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(7))
        a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
        obj(r.DROP_EVENT, '(L' + r.LIVING + ';Ljava/util/Collection;)V', lambda: (load(3), load(6))); store(5)
        load(5); a.op(0xb8, cp.method(r.POSS, 'onPlayerDrops', '(L' + r.DROP_EVENT + ';)V'))
        println('ordinary.world_drops', 'I', lambda: (load(6), a.op(0xb6, cp.method('java/util/ArrayList', 'size', '()I'))))
        contains('ordinary.pending', corpse=3, key='settledlands_body_items')
        click(); collected('ordinary.items'); removed('ordinary.removed'); count('ordinary.discards')

        # Normal body: two stacks, then the second event/repeated packet.
        env(); make_list(); item(5); item(4, local=10); click()
        collected('body.items'); removed('body.removed'); count('body.discards'); contains('body.key'); canceled('body.cancel', ES)
        msg_count('body.messages'); click(EG); collected('body.repeat_items'); count('body.repeat_discards'); msg_count('body.repeat_messages')
        println('body.lead_drops', 'I', lambda: field(4, r.G, 'leadDrops', 'I'))

        # Coffin, including a previously attached lead: same cleanup path.
        env(coffin=True); make_list(); item(7)
        assign(4, r.G, 'leashed', 'Z', lambda: boolean(True))
        assign(4, r.G, 'leashHolder', 'L' + E + ';', lambda: load(3))
        click(EG); collected('coffin.items'); removed('coffin.removed'); count('coffin.discards')
        println('coffin.is_coffin', 'Z', lambda: (load(4), a.op(0xb6, cp.method(C, 'isCoffin', '()Z'))))
        println('coffin.leashed', 'Z', lambda: field(4, r.G, 'leashed', 'Z'))
        println('coffin.lead_drops', 'I', lambda: field(4, r.G, 'leadDrops', 'I'))
        println('coffin.leash_broadcasts', 'I', lambda: field(4, r.G, 'leashBroadcasts', 'I'))
        click(); println('coffin.repeat_lead_drops', 'I', lambda: field(4, r.G, 'leadDrops', 'I'))

        # Partially filled inventory: keep actual corpse, leash and leftover until retry.
        env(2, coffin=True); make_list(); item(5)
        assign(4, r.G, 'leashed', 'Z', lambda: boolean(True))
        assign(4, r.G, 'leashHolder', 'L' + E + ';', lambda: load(3))
        click(EG); collected('partial.items'); tag_count('partial.left'); removed('partial.removed'); count('partial.discards')
        println('partial.leashed', 'Z', lambda: field(4, r.G, 'leashed', 'Z'))
        println('partial.lead_drops', 'I', lambda: field(4, r.G, 'leadDrops', 'I'))
        set_room(3); click(); collected('partial.retry_items'); removed('partial.retry_removed'); count('partial.retry_discards')
        println('partial.retry_leashed', 'Z', lambda: field(4, r.G, 'leashed', 'Z'))
        println('partial.retry_lead_drops', 'I', lambda: field(4, r.G, 'leadDrops', 'I'))

        # Full inventory and malformed components must not be mistaken for empty.
        env(0); make_list(); item(4); click(); collected('full.items'); tag_count('full.left'); removed('full.removed')
        set_room(4); click(EG); collected('full.retry_items'); removed('full.retry_removed'); count('full.retry_discards')
        env(); make_list(); item(4); item(2, local=10, invalid=2); click()
        collected('invalid.items'); removed('invalid.removed'); tag_count('invalid.left', 10); contains('invalid.key')
        assign(10, NBT, 'invalid', 'I', lambda: num(0)); click(EG)
        collected('invalid.retry_items'); removed('invalid.retry_removed'); count('invalid.retry_discards')
        env(); make_list(); item(1, invalid=3); click()
        removed('exception.removed'); tag_count('exception.left'); contains('exception.key')
        assign(7, NBT, 'invalid', 'I', lambda: num(0)); click(EG)
        collected('exception.retry_items'); removed('exception.retry_removed')

        # Ownership and client checks protect the entity as well as its items.
        env(); make_list(); item(3)
        obj(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(2), load(2), num(30))); store(9)
        click(EG, actor=9); collected('foreign.items', 9); removed('foreign.removed'); tag_count('foreign.left')
        obj(P, '(Ljava/util/UUID;L' + L + ';)V', lambda: (uuid(), load(2))); store(9)
        click(actor=9); removed('client.removed'); tag_count('client.left')
        assign(3, SP, 'alive', 'Z', lambda: boolean(False))
        click(); removed('dead.removed'); tag_count('dead.left')
        assign(3, SP, 'alive', 'Z', lambda: boolean(True))
        click(); removed('owner_after_blocked.removed'); collected('owner_after_blocked.items')
        env(owner=False); make_list(); item(3); click(); removed('unowned.removed'); tag_count('unowned.left')

        # Queue and dimension both block; only the eligible owner can complete.
        env(); make_list(); item(3); set_queue(); click(); removed('queued.removed'); tag_count('queued.left')
        a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
        assign(2, L, 'dim', 'L' + KEY + ';', lambda: r.static_field(a, cp, PM, 'PURGATORY_LEVEL', 'L' + KEY + ';'))
        click(); removed('dimension.removed'); tag_count('dimension.left')
        assign(2, L, 'dim', 'L' + KEY + ';', lambda: load(13))
        click(); collected('eligible.items'); removed('eligible.removed')

        # Missing or already empty list is NOT a successful recovery; untouched.
        env(); click(); removed('absent.removed'); count('absent.discards')
        env(); make_list(); click(); removed('empty.removed'); count('empty.discards'); contains('empty.key')

        # Two corpses of the same owner are independent.
        env(); make_list(); item(2); other_corpse(); make_list(8, 10); item(6, local=11, lst=10)
        click(); removed('two.first_after_first'); removed('two.second_after_first', 8); collected('two.first_items')
        click(EG, target=8); removed('two.first_after_second'); removed('two.second_after_second', 8); collected('two.total_items')

        # Tools bypass the recovery handler exactly as in 238.
        env(); make_list(); item(1); click(kind=r.TOOLS[-1]); removed('tools.coffin_removed'); canceled('tools.coffin_canceled', ES)
        click(EG, kind='lead'); removed('tools.lead_removed'); canceled('tools.lead_canceled', EG)
        click(kind=r.TOOLS[0]); removed('tools.wand_removed'); canceled('tools.wand_canceled', ES)
        click(); removed('tools.normal_removed'); collected('tools.normal_items')

        # Simulated persistent-data reload: saved UUID is read by actual corpse
        # load code; only that restored entity is removed on successful reclaim.
        env(); make_list(); item(6); obj(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(8)
        obj(NBT); store(9)
        assign(9, NBT, 'owner', 'Ljava/util/UUID;', lambda: (load(3), a.op(0xb6, cp.method(SP, 'getUUID', '()Ljava/util/UUID;'))))
        load(8); load(9); a.op(0xb6, cp.method(C, 'readAdditionalSaveData', '(L' + NBT + ';)V'))
        assign(8, E, 'data', 'L' + NBT + ';', lambda: field(4, E, 'data', 'L' + NBT + ';'))
        click(target=8); collected('reloaded.items'); removed('reloaded.removed', 8); count('reloaded.discards', 8)
        a.raw(0xb1); return a.finish()
    return r.f.fake_class('CleanupHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 12, 15, main)])


def run_fixture(java, jar):
    with tempfile.TemporaryDirectory(prefix='corpse-cleanup-jvm-') as td:
        folder = pathlib.Path(td)
        classes = r.build_classes(jar, jar)
        classes['CleanupHarness'] = harness_class()
        for name, raw in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
        p = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'CleanupHarness'],
                           capture_output=True, text=True)
        if p.returncode:
            raise RuntimeError(f'cleanup JVM fixture failed ({jar}):\n{p.stdout}\n{p.stderr}')
        return dict(line.split('=', 1) for line in p.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base238', required=True)
    ap.add_argument('--jar', required=True)
    args = ap.parse_args()
    old = run_fixture(args.java, args.base238)
    assert old['body.items'] == '9' and old['body.removed'] == 'false' and old['body.discards'] == '0', old
    assert old['coffin.items'] == '7' and old['coffin.removed'] == 'false', old
    print('238 negative control: items are recovered, but body and coffin remain')
    got = run_fixture(args.java, args.jar)
    expected = {
        'pipeline.world_drops': '0', 'pipeline.pending': 'false', 'pipeline.corpse_key': 'true',
        'pipeline.blocked_removed': 'false', 'pipeline.blocked_items': '0', 'pipeline.removed': 'true',
        'pipeline.discards': '1', 'pipeline.items': '5', 'pipeline.meta': 'mod:{enchanted:yes}', 'pipeline.key_after': 'false',
        'ordinary.world_drops': '1', 'ordinary.pending': 'false', 'ordinary.items': '0',
        'ordinary.removed': 'false', 'ordinary.discards': '0',
        'body.items': '9', 'body.removed': 'true', 'body.discards': '1', 'body.key': 'false', 'body.cancel': 'true',
        'body.messages': '1', 'body.repeat_items': '9', 'body.repeat_discards': '1', 'body.repeat_messages': '1', 'body.lead_drops': '0',
        'coffin.items': '7', 'coffin.removed': 'true', 'coffin.discards': '1', 'coffin.is_coffin': 'true',
        'coffin.leashed': 'false', 'coffin.lead_drops': '1', 'coffin.leash_broadcasts': '1', 'coffin.repeat_lead_drops': '1',
        'partial.items': '2', 'partial.left': '3', 'partial.removed': 'false', 'partial.discards': '0',
        'partial.leashed': 'true', 'partial.lead_drops': '0', 'partial.retry_items': '5',
        'partial.retry_removed': 'true', 'partial.retry_discards': '1', 'partial.retry_leashed': 'false', 'partial.retry_lead_drops': '1',
        'full.items': '0', 'full.left': '4', 'full.removed': 'false',
        'full.retry_items': '4', 'full.retry_removed': 'true', 'full.retry_discards': '1',
        'invalid.items': '4', 'invalid.removed': 'false', 'invalid.left': '2', 'invalid.key': 'true',
        'invalid.retry_items': '6', 'invalid.retry_removed': 'true', 'invalid.retry_discards': '1',
        'exception.removed': 'false', 'exception.left': '1', 'exception.key': 'true',
        'exception.retry_items': '1', 'exception.retry_removed': 'true',
        'foreign.items': '0', 'foreign.removed': 'false', 'foreign.left': '3',
        'client.removed': 'false', 'client.left': '3', 'dead.removed': 'false', 'dead.left': '3',
        'owner_after_blocked.removed': 'true', 'owner_after_blocked.items': '3',
        'unowned.removed': 'false', 'unowned.left': '3',
        'queued.removed': 'false', 'queued.left': '3', 'dimension.removed': 'false', 'dimension.left': '3',
        'eligible.items': '3', 'eligible.removed': 'true',
        'absent.removed': 'false', 'absent.discards': '0', 'empty.removed': 'false', 'empty.discards': '0', 'empty.key': 'true',
        'two.first_after_first': 'true', 'two.second_after_first': 'false', 'two.first_items': '2',
        'two.first_after_second': 'true', 'two.second_after_second': 'true', 'two.total_items': '8',
        'tools.coffin_removed': 'false', 'tools.coffin_canceled': 'false', 'tools.lead_removed': 'false',
        'tools.lead_canceled': 'false', 'tools.wand_removed': 'false', 'tools.wand_canceled': 'false',
        'tools.normal_removed': 'true', 'tools.normal_items': '1',
        'reloaded.items': '6', 'reloaded.removed': 'true', 'reloaded.discards': '1',
    }
    if got != expected:
        mismatches = [(k, got.get(k), v) for k, v in expected.items() if got.get(k) != v]
        extras = {k: v for k, v in got.items() if k not in expected}
        raise AssertionError(f'mismatched={mismatches} extra={extras}\nall={got}')
    print('239 JVM execution: body/coffin removed only after all items; partial/full/invalid stay;')
    print('owner/queue/client, tools, duplicate events, distinct and reloaded corpses OK')
    print('Synthetic dependencies; not a NeoForge/in-game test.')


if __name__ == '__main__':
    main()
