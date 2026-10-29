#!/usr/bin/env python3
"""Build 256 corpse health JVM verification (1/60 HP body, 60/60 HP coffin, reload, invulnerability & leash)
using the real GuideCorpseNpc + CorpseRecovery bytecode from base 255 and candidate 256 under -Xverify:all."""
import argparse
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_recovery238 as r238
from patch_probe233 import Asm


def health_harness_class() -> bytes:
    f = r238.f
    C, G, L, SL, SERVER = r238.C, r238.G, r238.L, r238.SL, r238.SERVER
    NBT, TYPE, KEY, DAMAGE = r238.NBT, r238.TYPE, r238.KEY, r238.DAMAGE
    ATTR, ATTRS, HOLDER = r238.ATTR, r238.ATTRS, r238.HOLDER

    def main(cp):
        a = Asm()

        def load(i):
            a.raw(0x19, i)

        def store(i):
            a.raw(0x3a, i)

        def boolean(v):
            a.raw(0x04 if v else 0x03)

        def print_kv(key, desc, push_val):
            a.op(0xb2, cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;'))
            r238.obj(a, cp, 'java/lang/StringBuilder')
            a.op(0x13, cp.string(key + '='))
            a.op(0xb6, cp.method('java/lang/StringBuilder', 'append', '(Ljava/lang/String;)Ljava/lang/StringBuilder;'))
            push_val()
            a.op(0xb6, cp.method('java/lang/StringBuilder', 'append', '(' + desc + ')Ljava/lang/StringBuilder;'))
            a.op(0xb6, cp.method('java/lang/StringBuilder', 'toString', '()Ljava/lang/String;'))
            a.op(0xb6, cp.method('java/io/PrintStream', 'println', '(Ljava/lang/String;)V'))

        def health_pair(prefix, slot):
            print_kv(
                prefix + '.max',
                'D',
                lambda: (
                    load(slot),
                    a.op(0xb2, cp.field(ATTRS, 'MAX_HEALTH', 'L' + HOLDER + ';')),
                    a.op(0xb6, cp.method(G, 'getAttribute', '(L' + HOLDER + ';)L' + ATTR + ';')),
                    a.op(0xb4, cp.field(ATTR, 'base', 'D')),
                ),
            )
            print_kv(
                prefix + '.hp',
                'F',
                lambda: (
                    load(slot),
                    a.op(0xb4, cp.field(G, 'health', 'F')),
                ),
            )

        # local 1: Server, local 2: ResourceKey, local 3: ServerLevel(isClientSide=false), local 4: GuideCorpseNpc
        r238.obj(a, cp, SERVER)
        store(1)
        r238.obj(a, cp, KEY)
        store(2)
        r238.obj(a, cp, SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(2), load(1)))
        store(3)
        r238.obj(a, cp, C, '(L' + TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(3)))
        store(4)

        # 1. Freshly spawned server corpse (isCoffin=false)
        health_pair('created', 4)

        # 2. Leashability & invulnerability on body vs coffin
        load(4)
        boolean(True)
        a.op(0xb5, cp.field(G, 'invulnerable', 'Z'))
        print_kv('body.leashable', 'Z', lambda: (load(4), a.op(0xb6, cp.method(C, 'canBeLeashed', '()Z'))))

        r238.obj(a, cp, DAMAGE)
        store(5)
        load(5)
        boolean(False)
        a.op(0xb5, cp.field(DAMAGE, 'generic', 'Z'))
        print_kv(
            'hurt.ordinary',
            'Z',
            lambda: (load(4), load(5), a.raw(0x0c), a.op(0xb6, cp.method(C, 'hurt', '(L' + DAMAGE + ';F)Z'))),
        )
        load(5)
        boolean(True)
        a.op(0xb5, cp.field(DAMAGE, 'generic', 'Z'))
        print_kv(
            'hurt.generic_kill',
            'Z',
            lambda: (load(4), load(5), a.raw(0x0c), a.op(0xb6, cp.method(C, 'hurt', '(L' + DAMAGE + ';F)Z'))),
        )

        # 3. Convert to coffin -> 60/60 HP + leashable
        load(4)
        boolean(True)
        a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        health_pair('coffin', 4)
        print_kv('coffin.leashable', 'Z', lambda: (load(4), a.op(0xb6, cp.method(C, 'canBeLeashed', '()Z'))))
        print_kv('invulnerable', 'Z', lambda: (load(4), a.op(0xb4, cp.field(G, 'invulnerable', 'Z'))))

        # 4. Revert coffin -> body -> 1/60 HP
        load(4)
        boolean(False)
        a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        health_pair('body_again', 4)

        # 5. Simulate legacy 1/1 saved corpse loaded from chunk NBT (Coffin=false) -> auto-upgraded to 1/60 HP!
        load(4)
        a.op(0xb2, cp.field(ATTRS, 'MAX_HEALTH', 'L' + HOLDER + ';'))
        a.op(0xb6, cp.method(G, 'getAttribute', '(L' + HOLDER + ';)L' + ATTR + ';'))
        a.raw(0x0f)  # dconst_1 (simulate old 1.0 maxHealth before chunk load)
        a.op(0xb6, cp.method(ATTR, 'setBaseValue', '(D)V'))

        r238.obj(a, cp, NBT)
        store(6)
        load(6)
        boolean(False)
        a.op(0xb5, cp.field(NBT, 'coffin', 'Z'))
        load(4)
        load(6)
        a.op(0xb6, cp.method(C, 'readAdditionalSaveData', '(L' + NBT + ';)V'))
        health_pair('loaded_body', 4)
        print_kv('loaded_anim', 'Ljava/lang/String;', lambda: (load(4), a.op(0xb4, cp.field(G, 'anim', 'Ljava/lang/String;'))))

        # 6. Load saved coffin from NBT (Coffin=true) -> 60/60 HP
        load(6)
        boolean(True)
        a.op(0xb5, cp.field(NBT, 'coffin', 'Z'))
        load(4)
        load(6)
        a.op(0xb6, cp.method(C, 'readAdditionalSaveData', '(L' + NBT + ';)V'))
        health_pair('loaded_coffin', 4)

        # 7. Client-side Level (isClientSide=true) -> syncHealth is a safe no-op
        r238.obj(a, cp, L, '(ZL' + KEY + ';L' + SERVER + ';)V', lambda: (boolean(True), load(2), load(1)))
        store(7)
        r238.obj(a, cp, C, '(L' + TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(7)))
        store(8)
        health_pair('client_created', 8)
        load(8)
        boolean(True)
        a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        health_pair('client_coffin', 8)

        a.raw(0xb1)
        return a.finish()

    return f.fake_class('CorpseHealthHarness256', [], [(9, 'main', '([Ljava/lang/String;)V', 8, 10, main)])


def run_jar(java: pathlib.Path, jar: pathlib.Path) -> dict[str, str]:
    with tempfile.TemporaryDirectory(prefix='corpse-hp256-') as td:
        folder = pathlib.Path(td)
        classes = r238.build_classes(str(jar), str(jar))
        classes['CorpseHealthHarness256'] = health_harness_class()
        for name, data in classes.items():
            p = folder / (name + '.class')
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(data)
        res = subprocess.run(
            [str(java), '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'CorpseHealthHarness256'],
            text=True,
            capture_output=True,
        )
        if res.returncode != 0:
            raise AssertionError(f'CorpseHealthHarness256 failed ({jar}):\n{res.stdout}\n{res.stderr}')
        return dict(line.split('=', 1) for line in res.stdout.splitlines() if '=' in line)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', type=pathlib.Path, required=True)
    ap.add_argument('--base-jar', type=pathlib.Path, default=None)
    ap.add_argument('--jar', type=pathlib.Path, required=True)
    args = ap.parse_args()
    java = args.java.resolve()

    if args.base_jar is not None:
        b255 = run_jar(java, args.base_jar.resolve())
        assert b255['created.max'] == '1.0' and b255['created.hp'] == '1.0', b255
        assert b255['loaded_body.max'] == '1.0' and b255['loaded_body.hp'] == '1.0', b255
        print('CONFIRMED build 255 corpse HP bug: non-coffin corpse has 1.0/1.0 HP instead of 1.0/60.0 HP')

    got = run_jar(java, args.jar.resolve())
    expected = {
        'created.max': '60.0', 'created.hp': '1.0',
        'body.leashable': 'false',
        'hurt.ordinary': 'false', 'hurt.generic_kill': 'true',
        'coffin.max': '60.0', 'coffin.hp': '60.0',
        'coffin.leashable': 'true', 'invulnerable': 'true',
        'body_again.max': '60.0', 'body_again.hp': '1.0',
        'loaded_body.max': '60.0', 'loaded_body.hp': '1.0', 'loaded_anim': 'die',
        'loaded_coffin.max': '60.0', 'loaded_coffin.hp': '60.0',
        'client_created.max': '60.0', 'client_created.hp': '60.0',
        'client_coffin.max': '60.0', 'client_coffin.hp': '60.0',
    }
    if got != expected:
        diff = {k: (got.get(k), v) for k, v in expected.items() if got.get(k) != v}
        raise AssertionError(f'candidate 256 corpse health mismatch: {diff}\nall={got}')
    print('PASS build 256 corpse 1/60 HP (created 1.0/60.0, loaded 1.0/60.0, coffin 60.0/60.0, invulnerable, leash intact)')


if __name__ == '__main__':
    main()
