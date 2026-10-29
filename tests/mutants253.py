#!/usr/bin/env python3
"""11 verifier-valid behavioral mutants for Build 253 (shield-tilt removal + NPC copy fix).

Every mutant must pass Java 21 -Xverify:all (no VerifyError) and fail with AssertionError
in Motion253Scenario or NpcCopy253Scenario.
"""
import argparse, hashlib, pathlib, subprocess, sys, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import classedit, gen_api253, patch_shield_copy253 as patch
from jvm_motion253 import make_fixture253, PAL_SHA
from jvm_npc_copy253 import make_copy_fixture


def make_jar_with(jar: pathlib.Path, out: pathlib.Path, replacements: dict[str, bytes]):
    with zipfile.ZipFile(jar) as src, zipfile.ZipFile(out, 'w') as dst:
        for info in src.infolist():
            dst.writestr(info, replacements.get(info.filename, src.read(info.filename)))


def mutate_bbmodel_range(base252_bbmodel: bytes, nop_start: int, nop_end: int) -> bytes:
    k = classedit.Klass(base252_bbmodel)
    m = k.find_method('renderActorHeldItem', patch.ITEM_DESC)
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])
    code[nop_start:nop_end] = b'\x00' * (nop_end - nop_start)
    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def mutate_bbmodel_drop_item_pos(jar_bbmodel: bytes) -> bytes:
    k = classedit.Klass(jar_bbmodel)
    m = k.find_method('renderActorHeldItem', patch.ITEM_DESC)
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])
    # 147..155 is aload_0; aload_1; aload_2; iload 6; invokestatic PlayerMotionBridge.itemPosition (stack neutral)
    assert code[146] == 0x2C and code[147] == 0x2A and code[152] == 0xB8
    code[147:155] = b'\x00' * (155 - 147)
    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


COPY_MUTANTS = [
    (
        'copy-omit-entity-id',
        [
            ('entityTag.putString("id", npc instanceof GuideTriggerNpc ? TRIGGER_ID : GUIDE_ID);', ''),
        ],
    ),
    (
        'copy-numeric-id-tag',
        [
            ('public void putString(String key, String value) {}',
             'public void putString(String key, String value) {}\n    public void putInt(String key, int value) {}'),
            ('entityTag.putString("id", npc instanceof GuideTriggerNpc ? TRIGGER_ID : GUIDE_ID);',
             'entityTag.putInt("id", 1);'),
        ],
    ),
    (
        'copy-wrong-guide-id',
        [
            ('GUIDE_ID = "settledlands:guide";', 'GUIDE_ID = "minecraft:villager";'),
        ],
    ),
    (
        'copy-clobber-guide-subtag',
        [
            ('if (entityTag == null) {\n            entityTag = new CompoundTag();\n        }',
             'entityTag = new CompoundTag();'),
        ],
    ),
    (
        'copy-wrong-trigger-id',
        [
            ('npc instanceof GuideTriggerNpc ? TRIGGER_ID : GUIDE_ID', 'GUIDE_ID'),
        ],
    ),
    (
        'copy-wrong-trigger-egg-item',
        [
            ('npc instanceof GuideTriggerNpc ? NpcContent.triggerEgg() : NpcContent.spawnEgg()',
             'NpcContent.spawnEgg()'),
        ],
    ),
    (
        'copy-corpse-id-leak',
        [
            ('public class GuideTriggerNpc extends GuideNpc {}',
             'public class GuideTriggerNpc extends GuideNpc {}'),
            ('entityTag.putString("id", npc instanceof GuideTriggerNpc ? TRIGGER_ID : GUIDE_ID);',
             'entityTag.putString("id", npc.getClass().getName().endsWith("GuideCorpseNpc") ? "settledlands:guide_corpse" : (npc instanceof GuideTriggerNpc ? TRIGGER_ID : GUIDE_ID));'),
        ],
    ),
]


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--base-jar', required=True, type=pathlib.Path)
    q.add_argument('--pal', required=True, type=pathlib.Path)
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    base_jar = a.base_jar.resolve()
    pal = a.pal.resolve()
    assert hashlib.sha256(pal.read_bytes()).hexdigest() == PAL_SHA

    with zipfile.ZipFile(base_jar) as bz:
        bb252 = bz.read(patch.MODEL + '.class')
    with zipfile.ZipFile(jar) as jz:
        bb253 = jz.read(patch.MODEL + '.class')

    orig_fix = (ROOT / 'src/build253/dev/settledlands/npc/NpcCopyEggFix253.java').read_text(encoding='utf-8')
    ecj = [str(java), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn']

    with tempfile.TemporaryDirectory(prefix='mutants253-') as d:
        tmp = pathlib.Path(d)
        m_out, m_auto, joml = make_fixture253(java, jar, pal, tmp / 'motion_fix')
        c_out, c_auto = make_copy_fixture(java, jar, tmp / 'copy_fix')

        def run_motion(j: pathlib.Path, mode: str):
            cp = ':'.join(map(str, [m_out, j, pal, m_auto, joml]))
            return subprocess.run([str(java), '-Xverify:all', '-cp', cp, 'testing.Motion253Scenario', mode], capture_output=True, text=True)

        def run_copy(j: pathlib.Path):
            cp = ':'.join(map(str, [c_out, j, c_auto]))
            return subprocess.run([str(java), '-Xverify:all', '-cp', cp, 'testing.NpcCopy253Scenario', 'verify-253'], capture_output=True, text=True)

        # Positive baselines on clean 253 candidate
        for mode in ('hns-blocking', 'npc-shield-blocking', 'player-idle-shield-blocking'):
            r = run_motion(jar, mode)
            assert r.returncode == 0 and 'PASS ' + mode in r.stdout, (mode, r.stdout, r.stderr)
        r = run_copy(jar)
        assert r.returncode == 0 and 'PASS build 253 NPC copy:' in r.stdout, (r.stdout, r.stderr)

        killed = 0

        # 4 bytecode-level shield mutants
        shield_mutants = [
            ('shield-reintroduce-full-252-tilt', bb252, 'hns-blocking'),
            ('shield-keep-translate-without-rot', mutate_bbmodel_range(bb252, 202, 217), 'npc-shield-blocking'),
            ('shield-keep-rot-without-translate', mutate_bbmodel_range(bb252, 217, 228), 'npc-shield-blocking'),
            ('shield-drop-pal-item-pos', mutate_bbmodel_drop_item_pos(bb253), 'hns-blocking'),
        ]
        for idx, (label, bad_bb, mode) in enumerate(shield_mutants):
            mjar = tmp / f'shield_{idx}.jar'
            make_jar_with(jar, mjar, {patch.MODEL + '.class': bad_bb})
            r = run_motion(mjar, mode)
            assert r.returncode != 0 and 'VerifyError' not in r.stderr and 'AssertionError' in r.stderr, (label, r.stdout, r.stderr)
            print('DETECTED', label, flush=True)
            killed += 1

        # 7 source-level NPC copy mutants
        for idx, (label, edits) in enumerate(COPY_MUTANTS):
            work = tmp / f'copy_{idx}'
            api = work / 'api'
            gen_api253.main(api)
            src_file = work / 'src/dev/settledlands/npc/NpcCopyEggFix253.java'
            src_file.parent.mkdir(parents=True, exist_ok=True)
            fix_txt = orig_fix
            for old, new in edits:
                if old in fix_txt:
                    assert fix_txt.count(old) == 1, (label, old)
                    fix_txt = fix_txt.replace(old, new, 1)
                else:
                    matched = False
                    for apif in api.rglob('*.java'):
                        t = apif.read_text(encoding='utf-8')
                        if old in t:
                            apif.write_text(t.replace(old, new, 1), encoding='utf-8')
                            matched = True
                            break
                    assert matched, (label, old)
            src_file.write_text(fix_txt, encoding='utf-8')
            classes = work / 'classes'
            classes.mkdir()
            rc = subprocess.run(ecj + ['-d', str(classes), *map(str, sorted(api.rglob('*.java'))), str(src_file)], capture_output=True, text=True)
            assert rc.returncode == 0, (label, rc.stdout, rc.stderr)
            mjar = work / 'mutant.jar'
            make_jar_with(jar, mjar, {patch.COPY_FIX + '.class': (classes / (patch.COPY_FIX + '.class')).read_bytes()})
            r = run_copy(mjar)
            assert r.returncode != 0 and 'VerifyError' not in r.stderr and ('AssertionError' in r.stderr or 'ReportedException' in r.stderr or 'IllegalStateException' in r.stderr), (label, r.stdout, r.stderr)
            print('DETECTED', label, flush=True)
            killed += 1

        assert killed == 11, killed
        print(f'PASS mutants253: {killed}/11 verifier-valid mutants killed', flush=True)


if __name__ == '__main__':
    main()
