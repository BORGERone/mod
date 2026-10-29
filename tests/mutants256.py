#!/usr/bin/env python3
"""Calibrated mutation check for Build 256:
  1. GuideNpc player-equivalent animation pipeline (FA: Player Extension + Touch'n Motion + held items + combos/corpse guards + fallback)
  2. First-person possession (Inventory/GUI/bed body gate + RenderArmEvent + PlayerRenderer hands + PAL/HnS sword arm transfer)
  3. Corpse 1/60 HP (CorpseRecovery.syncHealth)

Builds 24 verifier-valid (-Xverify:all) bytecode mutants of Build 256 (including mutations inside all 4 Mixin classes,
which are woven directly into their target classes before execution) and asserts that EVERY single mutant fails
for a semantic assertion reason — NEVER due to VerifyError or ClassFormatError.
"""
import argparse
import pathlib
import shutil
import struct
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import classedit
import gate_build244 as g244
import jvm_npc_fp_corpse256 as nfc256
import jvm_recovery256 as rec256
import patch_npc_stats246 as p246


def rewrite_jar(src: pathlib.Path, dst: pathlib.Path, patches: dict[str, bytes]) -> None:
    with zipfile.ZipFile(src, 'r') as zin, zipfile.ZipFile(dst, 'w') as zout:
        for item in zin.infolist():
            data = patches.get(item.filename)
            zout.writestr(item, data if data is not None else zin.read(item.filename))


def replace_method_body(raw_cls: bytes, name: str, desc: str, code_or_fn, max_locals: int = 10, max_stack: int = 4) -> bytes:
    k = classedit.Klass(raw_cls)
    m = k.find_method(name, desc)
    assert m is not None, f'missing {name}{desc}'
    parts = classedit.code_parts(k, m)
    return p246.replace_code(raw_cls, name, desc, code_or_fn, max_stack, max(max_locals, parts['max_locals']))


def disable_instanceof_check(raw_cls: bytes, method_name: str, method_desc: str, target_internal_cls: str) -> bytes:
    def fn(k: classedit.Klass, c: bytearray) -> None:
        hit = None
        for pc in range(len(c) - 3):
            if c[pc] == 0x2B and c[pc + 1] == 0xC1:
                idx = struct.unpack_from('>H', c, pc + 2)[0]
                e = k.cp[idx]
                if e and e['tag'] == 7 and k.utf8(e['a']) == target_internal_cls:
                    hit = pc
                    break
        assert hit is not None, f'instanceof {target_internal_cls} not found in {method_name}'
        c[hit : hit + 4] = b'\x03\x00\x00\x00'  # iconst_0; nop; nop; nop
    return g244.change(raw_cls, method_name, method_desc, fn)


def patch_byte_at(raw_cls: bytes, name: str, desc: str, pc: int, old_b: int, new_b: int) -> bytes:
    def fn(_k, c):
        assert c[pc] == old_b, f'{name}@{pc}: expected 0x{old_b:02x}, got 0x{c[pc]:02x}'
        c[pc] = new_b
    return g244.change(raw_cls, name, desc, fn)


NPC_RENDER_BRIDGE_DESC = (
    '(Ldev/settledlands/model/BbModel;Ldev/settledlands/npc/GuideNpc;FF'
    'Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V'
)
EVAL_CLIP_DESC = (
    '(Ldev/settledlands/model/BbModel;Ldev/settledlands/npc/GuideNpc;F)'
    'Ldev/settledlands/model/NpcMotionBridge256$CustomClipState;'
)
PARTIAL_MASK_DESC = '(Ldev/settledlands/model/BbModel;Ldev/settledlands/npc/GuideNpc;FFFFFF)V'
NPC_HELD_ITEMS_DESC = (
    '(Ldev/settledlands/model/BbModel;Ldev/settledlands/npc/GuideNpc;F'
    'Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V'
)
BB_HELD_ITEM_DESC = (
    '(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;'
    'Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Z)V'
)
HAS_FRESH_RIG_DESC = (
    '(Ltraben/entity_model_features/models/animation/EMFAnimationHandler;'
    'Lnet/minecraft/client/model/PlayerModel;)Z'
)
BLOCK_FP_DESC = (
    '(ZLnet/minecraft/client/Minecraft;Lnet/minecraft/client/player/AbstractClientPlayer;'
    'Lnet/neoforged/neoforge/client/event/RenderPlayerEvent$Pre;)Z'
)
RENDER_ARM_DESC = (
    '(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;'
    'ILnet/minecraft/client/player/AbstractClientPlayer;Z)Z'
)
RESTORE_PARTS_DESC = '(Ljava/util/List;[Ldev/settledlands/npc/client/FirstPersonPossession256$PartSnapshot;)V'
PLAYER_RENDER_MX_DESC = (
    '(Lnet/minecraft/client/player/AbstractClientPlayer;FF'
    'Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;'
    'ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V'
)
PLAYER_HAND_MX_DESC = (
    '(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;'
    'ILnet/minecraft/client/player/AbstractClientPlayer;'
    'Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V'
)
MODEL_MX_DESC = (
    '(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;'
    'IIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V'
)


MUTANTS = [
    # --- Group 1: NPC player-equivalent animation pipeline ---
    (
        'npc-emf-bridge-disabled',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: replace_method_body(r, 'ensureEmfBridge', '()Z', b'\x03\xac', max_locals=1))],
        'main',
    ),
    (
        'npc-ivy-rig-check-false',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: replace_method_body(r, 'isIvyRig', '(Ldev/settledlands/model/BbModel;)Z', b'\x03\xac', max_locals=2))],
        'main',
    ),
    (
        'npc-held-items-skipped',
        [
            ('dev/settledlands/model/NpcMotionBridge256.class',
             lambda r: replace_method_body(r, 'renderNpcHeldItems', NPC_HELD_ITEMS_DESC, b'\xb1', max_locals=7)),
            ('dev/settledlands/model/BbModel.class',
             lambda r: replace_method_body(r, 'settledlands$renderHeldItem', BB_HELD_ITEM_DESC, b'\xb1', max_locals=8)),
        ],
        'main',
    ),
    (
        'npc-custom-clip-ignored',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: replace_method_body(
              r, 'evaluateCustomClip', EVAL_CLIP_DESC,
              lambda k: p246.op(
                  0xB2,
                  k.add_fieldref(
                      'dev/settledlands/model/NpcMotionBridge256$CustomClipState',
                      'NONE',
                      'Ldev/settledlands/model/NpcMotionBridge256$CustomClipState;',
                  ),
              ) + b'\xb0',
              max_locals=4,
          ))],
        'main',
    ),
    (
        'npc-custom-clip-never-expires',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: replace_method_body(
              r, 'evaluateCustomClip', EVAL_CLIP_DESC,
              lambda k: p246.op(
                  0xB2,
                  k.add_fieldref(
                      'dev/settledlands/model/NpcMotionBridge256$CustomClipState',
                      'FULL_BODY',
                      'Ldev/settledlands/model/NpcMotionBridge256$CustomClipState;',
                  ),
              ) + b'\xb0',
              max_locals=4,
          ))],
        'main',
    ),
    (
        'npc-partial-mask-skipped',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: replace_method_body(r, 'applyPartialCustomMask', PARTIAL_MASK_DESC, b'\xb1', max_locals=9))],
        'main',
    ),
    (
        'npc-corpse-guard-disabled',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: disable_instanceof_check(r, 'renderNpc', NPC_RENDER_BRIDGE_DESC, 'dev/settledlands/npc/GuideCorpseNpc'))],
        'main',
    ),
    (
        'npc-trigger-guard-disabled',
        [('dev/settledlands/model/NpcMotionBridge256.class',
          lambda r: disable_instanceof_check(r, 'renderNpc', NPC_RENDER_BRIDGE_DESC, 'dev/settledlands/npc/GuideTriggerNpc'))],
        'main',
    ),
    (
        'npc-tnm-hook-disabled',
        [('dev/settledlands/model/NpcFreshEmfAccess256.class',
          lambda r: replace_method_body(r, 'hasTnmHook', '()Z', b'\x03\xac', max_locals=1))],
        'main',
    ),
    (
        'npc-fresh-rig-check-false',
        [('dev/settledlands/model/NpcFreshEmfAccess256.class',
          lambda r: replace_method_body(r, 'hasFreshRig', HAS_FRESH_RIG_DESC, b'\x03\xac', max_locals=3))],
        'main',
    ),
    (
        'npc-proxy-crouch-broken',
        [('dev/settledlands/model/NpcMotionPlayerProxy256.class',
          lambda r: replace_method_body(r, 'isCrouching', '()Z', b'\x03\xac', max_locals=1))],
        'main',
    ),
    (
        'npc-proxy-sprint-broken',
        [('dev/settledlands/model/NpcMotionPlayerProxy256.class',
          lambda r: replace_method_body(r, 'isSprinting', '()Z', b'\x03\xac', max_locals=1))],
        'main',
    ),
    (
        'mixin-walk-sync-noop',
        [('dev/settledlands/mixin/WalkAnimationStateMixin256.class',
          lambda r: replace_method_body(r, 'settledlands$copyWalkStateFrom', '(Lnet/minecraft/world/entity/WalkAnimationState;)V', b'\xb1', max_locals=3))],
        'main',
    ),
    (
        'mixin-clientlevel-lookup-noop',
        [('dev/settledlands/mixin/ClientLevelProxyLookupMixin256.class',
          lambda r: replace_method_body(
              r, 'settledlands$lookupNpcMotionProxy',
              '(ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V',
              b'\xb1', max_locals=4,
          ))],
        'main',
    ),

    # --- Group 2: First-person possession & Inventory ---
    (
        'fp-body-gate-always-block',
        [('dev/settledlands/npc/client/PlayerFirstPersonBodyGate256.class',
          lambda r: replace_method_body(r, 'blockFirstPersonRender', BLOCK_FP_DESC, b'\x1a\xac', max_locals=5))],
        'main',
    ),
    (
        'fp-body-gate-never-block',
        [('dev/settledlands/npc/client/PlayerFirstPersonBodyGate256.class',
          lambda r: replace_method_body(r, 'blockFirstPersonRender', BLOCK_FP_DESC, b'\x03\xac', max_locals=5))],
        'main',
    ),
    (
        'fp-body-gate-ignore-pal-pass',
        [('dev/settledlands/npc/client/PlayerFirstPersonBodyGate256.class',
          lambda r: replace_method_body(r, 'isFirstPersonWorldPass', '()Z', b'\x03\xac', max_locals=1))],
        'main',
    ),
    (
        'fp-arm-subscriber-noop',
        [('dev/settledlands/npc/client/FirstPersonArmSubscriber256.class',
          lambda r: replace_method_body(r, 'onRenderArm', '(Lnet/neoforged/neoforge/client/event/RenderArmEvent;)V', b'\xb1', max_locals=2))],
        'main',
    ),
    (
        'fp-arm-render-false',
        [('dev/settledlands/npc/client/FirstPersonPossession256.class',
          lambda r: replace_method_body(r, 'renderPossessedArm', RENDER_ARM_DESC, b'\x03\xac', max_locals=6))],
        'main',
    ),
    (
        'fp-arm-pose-not-restored',
        [('dev/settledlands/npc/client/FirstPersonPossession256.class',
          lambda r: replace_method_body(r, 'restoreParts', RESTORE_PARTS_DESC, b'\xb1', max_locals=4))],
        'main',
    ),
    (
        'mixin-fp-hand-context-noop',
        [('dev/settledlands/mixin/PossessedFirstPersonHandMixin256.class',
          lambda r: replace_method_body(r, 'settledlands$pushPossessedFpContext', PLAYER_RENDER_MX_DESC, b'\xb1', max_locals=8))],
        'main',
    ),
    (
        'mixin-fp-model-arms-noop',
        [('dev/settledlands/mixin/PossessedFirstPersonModelMixin256.class',
          lambda r: replace_method_body(r, 'settledlands$renderPossessedFpModelArms', MODEL_MX_DESC, b'\xb1', max_locals=9))],
        'main',
    ),

    # --- Group 3: Corpse 1/60 HP ---
    (
        'corpse-hp-max-not-60',
        [('dev/settledlands/npc/CorpseRecovery.class',
          lambda r: patch_byte_at(r, 'syncHealth', '(Ldev/settledlands/npc/GuideCorpseNpc;)V', 25, 60, 1))],
        'both',
    ),
    (
        'corpse-hp-current-2-instead-of-1',
        [('dev/settledlands/npc/CorpseRecovery.class',
          lambda r: patch_byte_at(r, 'syncHealth', '(Ldev/settledlands/npc/GuideCorpseNpc;)V', 44, 0x0C, 0x0D))],
        'both',
    ),
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', type=pathlib.Path, required=True)
    ap.add_argument('--jar', type=pathlib.Path, required=True)
    ap.add_argument('--gson-source', type=pathlib.Path, required=True)
    args = ap.parse_args()

    java = args.java.resolve()
    jar = args.jar.resolve()
    gson_src = args.gson_source.resolve()

    with tempfile.TemporaryDirectory(prefix='mutants256-') as td:
        tmp = pathlib.Path(td)
        base_cp = nfc256.make_fixture(java, jar, gson_src, tmp)
        out_dir = pathlib.Path(base_cp.split(':')[0])

        # Backup clean unwoven copies of the 4 Mixin target classes so each Mixin mutant re-weaves cleanly!
        woven_targets = [
            'net/minecraft/world/entity/WalkAnimationState.class',
            'net/minecraft/client/multiplayer/ClientLevel.class',
            'net/minecraft/client/renderer/entity/player/PlayerRenderer.class',
            'net/minecraft/client/model/AgeableListModel.class',
        ]
        # Recompile just those 4 Java files once to get clean pre-weave bytecode
        clean_dir = tmp / 'clean_targets'
        clean_dir.mkdir()
        src_dir = tmp / 'src'
        ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
        import subprocess
        subprocess.run(
            [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
             '-cp', f'{out_dir}:{jar}', '-d', str(clean_dir),
             *[str(src_dir / t.replace('.class', '.java')) for t in woven_targets]],
            check=True,
        )

        # Positive control first
        pos = nfc256.run_scenario(java, base_cp, 'candidate256')
        assert pos.returncode == 0 and 'PASS build 256 end-to-end' in pos.stdout, (pos.stdout, pos.stderr)
        rec_pos = rec256.run_jar(java, jar)
        assert rec_pos['created.max'] == '60.0' and rec_pos['created.hp'] == '1.0', rec_pos

        with zipfile.ZipFile(jar, 'r') as zorig:
            orig_bytes = {name: zorig.read(name) for name in zorig.namelist()}

        killed = 0
        for label, spec, suite in MUTANTS:
            mjar = tmp / f'mutant-{label}.jar'
            patches = {cls_name: fn(orig_bytes[cls_name]) for cls_name, fn in spec}
            rewrite_jar(jar, mjar, patches)

            # Restore clean targets & re-weave Mixins from mutated JAR
            for rel in woven_targets:
                shutil.copy2(clean_dir / rel, out_dir / rel)
            nfc256.weave_mixins_256(mjar, out_dir)

            res = nfc256.run_scenario(java, f'{out_dir}:{mjar}', 'candidate256')
            combined = res.stdout + '\n' + res.stderr
            for forbidden in ('VerifyError', 'ClassFormatError', 'NoSuchMethodError', 'NoSuchFieldError', 'IncompatibleClassChangeError'):
                if forbidden in combined:
                    raise AssertionError(f'Mutant {label} triggered {forbidden} instead of behavioral failure:\n{combined}')
            if res.returncode == 0:
                raise AssertionError(f'Mutant {label} SURVIVED NpcFpCorpse256Scenario!\n{combined}')

            if suite == 'both':
                try:
                    got_hp = rec256.run_jar(java, mjar)
                except AssertionError as exc:
                    err_txt = str(exc)
                    for forbidden in ('VerifyError', 'ClassFormatError'):
                        assert forbidden not in err_txt, (label, err_txt)
                else:
                    if got_hp.get('created.max') == '60.0' and got_hp.get('created.hp') == '1.0':
                        raise AssertionError(f'Mutant {label} SURVIVED CorpseHealthHarness256: {got_hp}')

            killed += 1
            print(f'  killed [{killed}/{len(MUTANTS)}] {label}', flush=True)

        assert killed == len(MUTANTS)
        print(f'PASS mutants 256: {killed}/{len(MUTANTS)} verifier-valid mutants killed', flush=True)


if __name__ == '__main__':
    main()
