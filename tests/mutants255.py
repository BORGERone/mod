#!/usr/bin/env python3
"""Calibrated mutation check for Build 255 Autonomous NPC AI (Wander + Ivy Melee + UI Sync).

Builds 24 verifier-valid (-Xverify:all) bytecode mutants of Build 255 and asserts that EVERY
single mutant fails the functional test suite for a semantic assertion reason — NEVER due to
VerifyError or ClassFormatError.
"""
import argparse, pathlib, struct, subprocess, sys, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import classedit
import gate_build244 as g244
import jvm_auto_ai255 as ai255
import patch_npc_stats246 as p246


def rewrite_jar(src: pathlib.Path, dst: pathlib.Path, cls_name: str, transform) -> None:
    with zipfile.ZipFile(src, 'r') as zin:
        patched = transform(zin.read(cls_name))
        with zipfile.ZipFile(dst, 'w') as zout:
            for item in zin.infolist():
                zout.writestr(item, patched if item.filename == cls_name else zin.read(item.filename))


def replace_method_body(raw_cls: bytes, name: str, desc: str, code: bytes, max_locals: int = 8) -> bytes:
    k = classedit.Klass(raw_cls)
    m = k.find_method(name, desc)
    assert m is not None, f'missing {name}{desc}'
    parts = classedit.code_parts(k, m)
    return p246.replace_code(raw_cls, name, desc, lambda _k: code, 2, max(max_locals, parts['max_locals']))


def patch_byte_at(raw_cls: bytes, name: str, desc: str, pc: int, old_b: int, new_b: int) -> bytes:
    def fn(_k, c):
        assert c[pc] == old_b, f'{name}@{pc}: expected 0x{old_b:02x}, got 0x{c[pc]:02x}'
        c[pc] = new_b
    return g244.change(raw_cls, name, desc, fn)


def mut_combat_uses_fireball(raw_cls: bytes) -> bytes:
    desc = '(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcAutoAiEngine255$MeleeCombo;)V'
    def fn(k, c):
        # pc 33..37 is aload_2 (0x2c) + getfield MeleeCombo.anim (0xb4 u2) = 4 bytes
        assert c[33] == 0x2C and c[34] == 0xB4, bytes(c[33:38]).hex()
        s_idx = k.add_string('cast_fireball')
        c[33:37] = b'\x13' + struct.pack('>H', s_idx) + b'\x00'  # ldc_w "cast_fireball"; nop
    return g244.change(raw_cls, 'startMeleeCombo', desc, fn)


MUTANTS = [
    (
        'default-auto-wander-true',
        'dev/settledlands/npc/NpcAutoAi255$ProfileState.class',
        lambda k: patch_byte_at(k, '<init>', '()V', 5, 0x03, 0x04),
    ),
    (
        'default-auto-attack-true',
        'dev/settledlands/npc/NpcAutoAi255$ProfileState.class',
        lambda k: patch_byte_at(k, '<init>', '()V', 16, 0x03, 0x04),
    ),
    (
        'radius-unclamped',
        'dev/settledlands/npc/NpcAutoAi255.class',
        lambda k: replace_method_body(k, 'clampRadius', '(I)I', b'\x1a\xac', max_locals=1),
    ),
    (
        'corpse-trigger-unguarded',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'isEligibleNpc', '(Ldev/settledlands/npc/GuideNpc;)Z', b'\x04\xac', max_locals=1),
    ),
    (
        'wander-disabled',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'tickWander', '(Ldev/settledlands/npc/GuideNpc;)V', b'\xb1', max_locals=2),
    ),
    (
        'hazard-floor-ignored',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'isHazardousFloor', '(Lnet/minecraft/world/level/block/state/BlockState;)Z', b'\x03\xac', max_locals=1),
    ),
    (
        'hazard-airspace-ignored',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'isHazardousAirspace', '(Lnet/minecraft/world/level/block/state/BlockState;)Z', b'\x03\xac', max_locals=1),
    ),
    (
        'home-nbt-save-dropped',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'saveNpcData', '(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/nbt/CompoundTag;)V', b'\xb1', max_locals=2),
    ),
    (
        'home-nbt-load-dropped',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'loadNpcData', '(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/nbt/CompoundTag;)V', b'\xb1', max_locals=2),
    ),
    (
        'return-home-broken',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'navigateTowardHome', '(Ldev/settledlands/npc/GuideNpc;DD)V', b'\xb1', max_locals=6),
    ),
    (
        'scripted-move-reanchor-dropped',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'onScriptedMoveActive', '(Ldev/settledlands/npc/GuideNpc;)V', b'\xb1', max_locals=1),
    ),
    (
        'combat-disabled',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'tickCombat', '(Ldev/settledlands/npc/GuideNpc;)Z', b'\x03\xac', max_locals=2),
    ),
    (
        'attack-peaceful-mobs',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: patch_byte_at(
            k,
            'isValidHostileTarget',
            '(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z',
            84, 0x03, 0x04,
        ),
    ),
    (
        'combat-uses-fireball',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        mut_combat_uses_fireball,
    ),
    (
        'stats-damage-ignored',
        'dev/settledlands/npc/NpcAutoAi255.class',
        lambda k: replace_method_body(k, 'damageMultiplier', '(Ldev/settledlands/npc/NpcProfile;)F', b'\x0c\xae', max_locals=1),
    ),
    (
        'stats-speed-ignored',
        'dev/settledlands/npc/NpcAutoAi255.class',
        lambda k: replace_method_body(k, 'speedMultiplier', '(Ldev/settledlands/npc/NpcProfile;)D', b'\x0f\xaf', max_locals=1),
    ),
    (
        'hurt-retaliation-dropped',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'onHurt', '(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/damagesource/DamageSource;)V', b'\xb1', max_locals=2),
    ),
    (
        'talking-pause-dropped',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        lambda k: replace_method_body(k, 'onTalkingPaused', '(Ldev/settledlands/npc/GuideNpc;)V', b'\xb1', max_locals=1),
    ),
    (
        'config-tab-section-missing',
        'dev/settledlands/npc/client/NpcAutoAiConfigUi255.class',
        lambda k: replace_method_body(k, 'appendSection', '(Ldev/settledlands/npc/client/NpcConfigScreen;I)I', b'\x1b\xac', max_locals=2),
    ),
    (
        'edit-field-sync-dropped',
        'dev/settledlands/npc/NpcAutoAi255.class',
        lambda k: replace_method_body(k, 'applyFieldEdit', '(Ldev/settledlands/npc/NpcProfile;Ljava/lang/String;Ljava/lang/String;)Z', b'\x03\xac', max_locals=3),
    ),
    (
        'graph-card-draw-missing',
        'dev/settledlands/npc/client/NpcAutoAiGraphUi255.class',
        lambda k: replace_method_body(k, 'draw', '(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;)V', b'\xb1', max_locals=2),
    ),
    (
        'graph-card-shadowed-text',
        'dev/settledlands/npc/client/NpcAutoAiGraphUi255.class',
        lambda k: patch_byte_at(k, 'draw', '(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;)V', 237, 0x03, 0x04),
    ),
    (
        'graph-only-save-dropped',
        'dev/settledlands/npc/NpcAutoAi255.class',
        lambda k: replace_method_body(k, 'syncGraphSaveToProfile', '(Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcProfile;)V', b'\xb1', max_locals=2),
    ),
    (
        'profile-copy-dropped',
        'dev/settledlands/npc/NpcAutoAi255.class',
        lambda k: replace_method_body(k, 'copyProfile', '(Ldev/settledlands/npc/NpcProfile;Ldev/settledlands/npc/NpcProfile;)V', b'\xb1', max_locals=2),
    ),
]


def main() -> None:
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    gson = a.gson_source.resolve()

    with tempfile.TemporaryDirectory(prefix='mut255-') as d:
        tmp = pathlib.Path(d)
        out, autostub = ai255.make_fixture(java, jar, gson, tmp)

        def run_jar(jpath: pathlib.Path, tag: str) -> subprocess.CompletedProcess:
            gdir = tmp / f'game-{tag}'
            gdir.mkdir(exist_ok=True)
            cp = ':'.join(map(str, [out, jpath, autostub]))
            return subprocess.run(
                [str(java), '-Xverify:all', '-cp', cp, 'testing.AutoAi255Scenario',
                 str(gdir), str(jpath), str(out), str(autostub)],
                capture_output=True, text=True,
            )

        base_run = run_jar(jar, 'unmutated')
        assert base_run.returncode == 0, ('Unmutated Build 255 failed!', base_run.stdout, base_run.stderr)

        killed = 0
        for name, cls_name, fn in MUTANTS:
            mjar = tmp / f'{name}.jar'
            rewrite_jar(jar, mjar, cls_name, fn)
            res = run_jar(mjar, name)
            combined = (res.stdout or '') + '\n' + (res.stderr or '')
            assert 'VerifyError' not in combined and 'ClassFormatError' not in combined, (
                f'Mutant {name} was invalid bytecode:\n{combined}'
            )
            assert res.returncode != 0, f'Mutant {name} SURVIVED!'
            killed += 1
            print(f'  killed [{killed:02d}/{len(MUTANTS):02d}] {name}', flush=True)

        print(f'PASS mutants 255: {killed}/{len(MUTANTS)} verifier-valid mutants killed')


if __name__ == '__main__':
    main()
