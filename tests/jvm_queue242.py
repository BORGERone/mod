#!/usr/bin/env python3
"""Execute the real 242 PurgatoryData FIFO and rollback methods on Java 21.

Only SavedData/fastutil are executable stand-ins; this is not Minecraft.
"""
import argparse
import pathlib
import subprocess
import tempfile
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import jvm_altar242 as t
from patch_probe233 import Asm

PD, q, r, f = t.PD, t.q, t.r, t.f
SAVED = 'net/minecraft/world/level/saveddata/SavedData'
LONGSET = t.LONGSET


def saved_class():
    def dirty(cp):
        x = cp.field(SAVED, 'dirty', 'I')
        return b'\x2a\x59' + t.op(0xb4, x) + b'\x04\x60' + t.op(0xb5, x) + b'\xb1'
    return f.fake_class(SAVED, [(1, 'dirty', 'I')], [(1, '<init>', '()V', 1, 1, f.empty_init()),
                                                   (1, 'setDirty', '()V', 3, 1, dirty)])


def harness():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        print_b = cp.method('java/io/PrintStream', 'println', '(Z)V')
        def obj(owner): t.obj(a, cp, owner)
        def uuid(s):
            t.text(a, cp, s); a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def result(label, emit):
            a.op(0xb2, out); t.text(a, cp, label + '='); a.op(0xb6, print_s)
            a.op(0xb2, out); emit(); a.op(0xb6, print_b)
        def eq_expected(method, local):
            a.raw(0x2b); a.op(0xb6, cp.method(PD, method, '()Ljava/util/UUID;'))
            t.local(a, 'aload', local); a.op(0xb6, cp.method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z'))
        obj(PD); a.raw(0x4c)
        uuid('00000000-0000-0000-0000-000000000001'); a.raw(0x4d)
        uuid('00000000-0000-0000-0000-000000000002'); a.raw(0x4e)
        for local in (2, 3, 2):
            a.raw(0x2b); t.local(a, 'aload', local); a.op(0xb6, cp.method(PD, 'enqueueDead', '(Ljava/util/UUID;)V'))
        result('peek_first', lambda: eq_expected(q.PEEK, 2))
        result('poll_first', lambda: eq_expected('pollFirstDead', 2))
        result('peek_second', lambda: eq_expected(q.PEEK, 3))
        for _ in range(2):
            a.raw(0x2b, 0x2c); a.op(0xb6, cp.method(PD, q.RESTORE_QUEUE, '(Ljava/util/UUID;)V'))
        result('restored_first', lambda: eq_expected(q.PEEK, 2))
        result('repolled_first', lambda: eq_expected('pollFirstDead', 2))
        result('repolled_second', lambda: eq_expected('pollFirstDead', 3))
        result('empty', lambda: (a.raw(0x2b), a.op(0xb6, cp.method(PD, 'hasDeadPlayers', '()Z')), a.raw(0x04, 0x82)))
        a.raw(0xb1); return a.finish()
    return f.fake_class('RealQueueHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 5, 4, main)])


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--jar', required=True); a = ap.parse_args()
    names = {'<init>', 'enqueueDead', 'pollFirstDead', 'hasDeadPlayers', 'isDeadInQueue', 'removeFromQueue', q.PEEK, q.RESTORE_QUEUE}
    classes = {PD: r.subset(a.jar, PD, names), SAVED: saved_class(),
               LONGSET: f.fake_class(LONGSET, [], [(1, '<init>', '()V', 1, 1, f.empty_init())]),
               'RealQueueHarness': harness()}
    with tempfile.TemporaryDirectory(prefix='queue242-jvm-') as td:
        root = pathlib.Path(td)
        for name, raw in classes.items():
            path = root / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
        p = subprocess.run([a.java, '-Xshare:off', '-Xverify:all', '-cp', str(root), 'RealQueueHarness'], text=True, capture_output=True)
        if p.returncode: raise RuntimeError(p.stdout + '\n' + p.stderr)
        got = dict(line.split('=', 1) for line in p.stdout.splitlines() if '=' in line)
        expected = {x: 'true' for x in ('peek_first', 'poll_first', 'peek_second', 'restored_first',
                                         'repolled_first', 'repolled_second', 'empty')}
        if got != expected: raise AssertionError(f'queue mismatched={got}, expected={expected}')
    print('242 real PurgatoryData FIFO and exact first-position rollback: OK (synthetic SavedData)')


if __name__ == '__main__': main()
