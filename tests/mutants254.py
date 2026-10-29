#!/usr/bin/env python3
"""17 calibrated, verifier-valid (-Xverify:all) behavioral mutants for Build 254."""
import argparse, pathlib, struct, subprocess, sys, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import classedit, decode, gate_build237 as g237, gate_build244 as g244, jvm_ui_ivy254 as ui254, patch_npc_stats246 as p246, patch_ui_ivy254 as p254
from patch_corpse_pose237 import constant_ref


def mutate(jar: pathlib.Path, base253: pathlib.Path, dest: pathlib.Path, mode: str):
    with zipfile.ZipFile(jar) as z:
        raw = {n: z.read(n) for n in z.namelist()}
    with zipfile.ZipFile(base253) as b:
        old253 = {n: b.read(n) for n in b.namelist()}
    repl = {}

    def change(cls: str, name: str, fn, desc: str = None):
        key = cls + '.class'
        cur = repl.get(key, raw[key])
        k = classedit.Klass(cur)
        m = k.find_method(name, desc) if desc else k.find_method(name)
        assert m is not None, (cls, name, desc)
        repl[key] = g244.change(cur, name, m['desc'], fn)

    def replacement(cls: str, name: str, body, stack: int = 4, desc: str = None):
        key = cls + '.class'
        cur = repl.get(key, raw[key])
        k = classedit.Klass(cur)
        m = k.find_method(name, desc) if desc else k.find_method(name)
        assert m is not None, (cls, name, desc)
        parts = classedit.code_parts(k, m)
        repl[key] = p246.replace_code(cur, name, m['desc'], body, stack, parts['max_locals'])

    if mode == 'pos-right-of-node':
        def fn(k, c):
            # sipush -360 == 0x11 0xFE 0x98 -> sipush +288 == 0x11 0x01 0x20
            at = bytes(c).find(b'\x11\xfe\x98')
            assert at >= 0
            c[at:at + 3] = b'\x11\x01\x20'
        change(p254.SG, 'ensure', fn)

    elif mode == 'pos-anchor-rightmost':
        # Return the rightmost node overall (like 246 picking "Прибытие #2" at x=280 instead of entry at x=0)
        def fn(k, c):
            walk = list(decode.walk(c))
            for pc, ins in walk:
                if ins[0] == 0xB4 and constant_ref(k, struct.unpack('>H', ins[1:3])[0]) in (
                    ('dev/settledlands/npc/NpcDialogueGraph', 'entry', 'I'),
                    ('dev/settledlands/npc/NpcDialogueGraph$Node', 'special', 'I'),
                ):
                    if constant_ref(k, struct.unpack('>H', ins[1:3])[0])[1] == 'entry':
                        c[pc:pc + 3] = b'\x57\x02\x00'  # pop; iconst_m1 -> skip entry
                    else:
                        c[pc:pc + 3] = b'\x57\x03\x00'  # pop; iconst_0 -> treat special nodes as regular
                elif ins[0] == 0xA2:  # if_icmpge -> if_icmple
                    c[pc] = 0xA4
                elif ins[0] == 0xA1:  # if_icmplt -> if_icmpgt
                    c[pc] = 0xA3
        change(p254.SG, 'defaultAnchor', fn)

    elif mode == 'slider-shadow-leak':
        def fn(k, c):
            # In drawCenteredNoShadow: iconst_0 (false) before GuiGraphics.drawString -> iconst_1 (true)
            walk = list(decode.walk(c))
            for i, (pc, ins) in enumerate(walk[:-1]):
                pc2, ins2 = walk[i + 1]
                if ins == b'\x03' and ins2[0] == 0xB6 and constant_ref(k, struct.unpack('>H', ins2[1:3])[0])[1] == 'drawString':
                    c[pc] = 0x04
                    return
            raise AssertionError('iconst_0 before drawString not found')
        change(p254.UI, 'drawCenteredNoShadow', fn)

    elif mode == 'height-clipped-140':
        def fn(k, c):
            # sipush 170 == 0x11 0x00 0xAA -> sipush 140 == 0x11 0x00 0x8C
            at = bytes(c).find(b'\x11\x00\xaa')
            assert at >= 0
            c[at:at + 3] = b'\x11\x00\x8c'
        change(p254.UI, 'height', fn)

    elif mode == 'rand-missing-plus2':
        def fn(k, c):
            # iconst_5 (0x08) before Random.nextInt(int) -> iconst_4 (0x07)
            walk = list(decode.walk(c))
            for i, (pc, ins) in enumerate(walk[:-1]):
                _, ins2 = walk[i + 1]
                if ins == b'\x08' and ins2[0] == 0xB6 and constant_ref(k, struct.unpack('>H', ins2[1:3])[0]) == (
                    'java/util/Random', 'nextInt', '(I)I'
                ):
                    c[pc] = 0x07
                    return
            raise AssertionError('iconst_5 before nextInt not found')
        change(p254.SG, 'randomStep', fn)

    elif mode == 'rand-radius-3':
        def fn(k, c):
            # iconst_5; invokevirtual nextInt; iconst_2; isub -> bipush 7? Use iconst_5->iconst_5 & iconst_2->iconst_m1 (+1..+5)!
            walk = list(decode.walk(c))
            for i, (pc, ins) in enumerate(walk[:-1]):
                _, ins2 = walk[i + 1]
                if ins == b'\x05' and ins2 == b'\x64':  # iconst_2; isub -> iconst_m1; isub (+1..+5)
                    c[pc] = 0x02
                    return
            raise AssertionError('iconst_2; isub not found')
        change(p254.SG, 'randomStep', fn)

    elif mode == 'rand-skip-zero':
        # Always add +1 so 0 never occurs: clamp(clamp(current) + 1)
        replacement(
            p254.SG, 'randomStep',
            lambda k: b'\x1a' + p246.op(0xB8, k.add_methodref(p254.SG, 'clamp', '(I)I'))
            + b'\x04\x60' + p246.op(0xB8, k.add_methodref(p254.SG, 'clamp', '(I)I')) + b'\xac',
            stack=3,
        )

    elif mode == 'rand-no-undo-remember':
        def fn(k, c):
            for pc, ins in decode.walk(c):
                if ins[0] == 0xB6 and constant_ref(k, struct.unpack('>H', ins[1:3])[0])[1] == 'settledlands$remember':
                    c[pc:pc + 3] = b'\x57\x00\x00'  # pop receiver; nop; nop
                    return
            raise AssertionError('settledlands$remember not found')
        change(p254.UI, 'randomize', fn)

    elif mode == 'rand-no-changed-dirty':
        def fn(k, c):
            for pc, ins in decode.walk(c):
                if ins[0] == 0xB6 and constant_ref(k, struct.unpack('>H', ins[1:3])[0])[1] == 'settledlands$changed':
                    c[pc:pc + 3] = b'\x57\x00\x00'  # pop receiver; nop; nop
                    return
            raise AssertionError('settledlands$changed not found')
        change(p254.UI, 'randomize', fn)

    elif mode == 'rand-click-ignored':
        def fn(k, c):
            # Nop out the randomize click block (353..365) so clicking Randomize falls through to drag
            for pc, ins in decode.walk(c):
                if ins[0] == 0xB8 and constant_ref(k, struct.unpack('>H', ins[1:3])[0])[1] == 'randomize':
                    start = pc - 6  # aload_0 (1) + aload 8 (2) + invokestatic current() (3)
                    end = pc + 6    # invokestatic randomize (3) + pop (1) + iconst_1 (1) + ireturn (1)
                    assert c[start] == 0x2A and c[end - 1] == 0xAC
                    c[start:end] = b'\x00' * (end - start)
                    return
            raise AssertionError('randomize call in mouseClicked not found')
        change(p254.UI, 'mouseClicked', fn)

    elif mode == 'dropdown-cfg-unpatched':
        repl[p254.CFG_SCREEN + '.class'] = old253[p254.CFG_SCREEN + '.class']

    elif mode == 'dropdown-branch-unpatched':
        repl[p254.BRANCH_SCREEN + '.class'] = old253[p254.BRANCH_SCREEN + '.class']

    elif mode == 'dropdown-low-z50':
        def fn(k, c):
            # Replace ldc 500.0f with fconst_2 (2.0f, behind 3D preview Z=95.0f!)
            for pc, ins in decode.walk(c):
                if ins[0] == 0x12:
                    e = k.cp[ins[1]]
                    if e and e['tag'] == 4:
                        c[pc:pc + 2] = b'\x0d\x00'  # fconst_2; nop
                        return
            raise AssertionError('ldc 500.0f not found')
        change(p254.DROP_TOP, 'renderPopupTop', fn)

    elif mode == 'ivy-profile-empty-default':
        replacement(p254.DEF_MODEL, 'defaultTexture', lambda k: p246.op(0x13, k.add_string('')) + b'\xb0', stack=1)

    elif mode == 'ivy-clobber-explicit-vanilla':
        replacement(
            p254.DEF_MODEL, 'loadTexture',
            lambda k: p246.op(0x13, k.add_string('ivy_oleander.bbmodel')) + b'\xb0',
            stack=1,
        )

    elif mode == 'ivy-corpse-trigger-leak':
        replacement(
            p254.DEF_MODEL, 'initialTexture',
            lambda k: p246.op(0x13, k.add_string('ivy_oleander.bbmodel')) + b'\xb0',
            stack=1,
        )

    elif mode == 'ivy-not-extracted':
        repl[p254.CLIENT + '.class'] = old253[p254.CLIENT + '.class']

    else:
        raise ValueError(mode)

    g237.alter_zip(jar, dest, replace=repl)


CASES = [
    ('pos-right-of-node', 'main', 'Characteristics must default to (-360, 0)'),
    ('pos-anchor-rightmost', 'main', 'Characteristics must default to (-360, 0)'),
    ('slider-shadow-leak', 'main', 'NpcStatsGraphUi.draw must emit 0 shadowed strings'),
    ('height-clipped-140', 'main', 'Characteristics hit area must cover full 170px height'),
    ('rand-missing-plus2', 'main', 'all 5 deltas {-2,-1,0,+1,+2} must occur'),
    ('rand-radius-3', 'main', 'randomStep delta out of [-2,+2]'),
    ('rand-skip-zero', 'main', 'all 5 deltas {-2,-1,0,+1,+2} must occur'),
    ('rand-no-undo-remember', 'main', 'Ctrl+Z undo must restore pre-randomize stats'),
    ('rand-no-changed-dirty', 'main', 'randomize must call settledlands$changed()'),
    ('rand-click-ignored', 'ui', 'randomize button does not start node drag'),
    ('dropdown-cfg-unpatched', 'main', 'NpcConfigScreen dropdown popup must render at Z=500'),
    ('dropdown-branch-unpatched', 'main', 'NpcBranchScreen dropdown popup must also render at Z=500.0'),
    ('dropdown-low-z50', 'main', 'NpcConfigScreen dropdown popup must render at Z=500'),
    ('ivy-profile-empty-default', 'main', 'new NpcProfile().texture must default to ivy_oleander.bbmodel'),
    ('ivy-clobber-explicit-vanilla', 'main', 'explicit empty Texture (standard Guide NPC) must be preserved'),
    ('ivy-corpse-trigger-leak', 'main', 'GuideTriggerNpc must keep empty texture'),
    ('ivy-not-extracted', 'main', 'NpcClient setup must extract ivy_oleander.bbmodel'),
]


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--base-jar', required=True, type=pathlib.Path)
    q.add_argument('--gson-source', required=True, type=pathlib.Path)
    q.add_argument('--only')
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    base253 = a.base_jar.resolve()
    gson = a.gson_source.resolve()
    selected = [c for c in CASES if not a.only or c[0] == a.only]
    assert selected

    with tempfile.TemporaryDirectory(prefix='mutants254-') as d:
        tmp = pathlib.Path(d)
        fix_dir = tmp / 'fixture'
        fix_dir.mkdir()
        out_dir, autostub = ui254.make_fixture(java, jar, gson, fix_dir)

        for idx, (mode, runner, signal) in enumerate(selected):
            bad = tmp / f'{mode}.jar'
            mutate(jar, base253, bad, mode)
            val = subprocess.run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(bad)], capture_output=True, text=True)
            assert val.returncode == 0 and 'ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ' in val.stdout, (mode, val.stdout, val.stderr)

            if runner == 'main':
                gdir = tmp / f'game_{idx}'
                gdir.mkdir()
                cp = ':'.join(map(str, [out_dir, bad, autostub]))
                res = subprocess.run(
                    [str(java), '-Xverify:all', '-cp', cp, 'testing.UiIvy254Scenario', 'verify-254',
                     str(gdir), str(bad), str(out_dir), str(autostub)],
                    capture_output=True, text=True,
                )
            else:
                res = subprocess.run(
                    [sys.executable, str(ROOT / 'tests/jvm_graph_ui254.py'), '--java', str(java), '--jar', str(bad)],
                    capture_output=True, text=True,
                )
            combined = res.stdout + res.stderr
            if (res.returncode == 0 or 'AssertionError' not in combined
                    or signal not in combined or 'VerifyError' in combined or 'ClassFormatError' in combined):
                raise AssertionError(f'uncalibrated 254 mutant {mode}: rc={res.returncode}; expected {signal!r}\n{combined}')
            print(f'DETECTED {mode}', flush=True)

    print(f'PASS mutants254: {len(selected)}/{len(selected)} verifier-valid mutants killed', flush=True)


if __name__ == '__main__':
    main()
