#!/usr/bin/env python3
"""Release gate 262: preserve walk-facing yaw, then smoothly scan with torso and head."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit
import decode
import patch_auto_ai262 as patch
from patch_corpse_pose237 import constant_ref

BASE_NAME = 'settledlands-0.8.0-build261-idle-look-punchy-animation.jar'
ENGINE_SOURCE = 'src/build262/dev/settledlands/npc/NpcAutoAiEngine255.java'


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as source, zipfile.ZipFile(jar) as candidate:
        assert source.testzip() is None and candidate.testzip() is None
        old = {item.filename: source.read(item.filename) for item in source.infolist()}
        new = {item.filename: candidate.read(item.filename) for item in candidate.infolist()}
    assert set(old) == set(new)
    changed = {name for name in old if old[name] != new[name]}
    assert changed == patch.CHANGED, (changed, patch.CHANGED)
    assert all(old[name] == new[name] for name in set(old) - changed)
    assert old['dev/settledlands/model/NpcMotionBridge256.class'] == new['dev/settledlands/model/NpcMotionBridge256.class']

    engine = new[patch.ENGINE + '.class']
    engine_k = classedit.Klass(engine)
    idle = engine_k.find_method('tickIdleLookAround', '(Ldev/settledlands/npc/GuideNpc;)V')
    movement = engine_k.find_method('tickBodyFacingMovement', '(Ldev/settledlands/npc/GuideNpc;)V')
    run_ai = engine_k.find_method('runMobilityAndAutoAi', '(Ldev/settledlands/npc/GuideNpc;)V')
    assert idle is not None and movement is not None and run_ai is not None
    run_code = classedit.code_parts(engine_k, run_ai)['code']
    run_method_names = []
    for _, ins in decode.walk(run_code):
        if ins[0] in (0xB6, 0xB7, 0xB8, 0xB9):
            ref = patch_auto_method_ref(engine_k, int.from_bytes(ins[1:3], 'big'))
            if ref:
                run_method_names.append(ref[1])
    assert 'tickBodyFacingMovement' in run_method_names and 'tickIdleLookAround' in run_method_names

    source_text = (ROOT / ENGINE_SOURCE).read_text(encoding='utf-8')
    move_src = source_text.split('public static void tickBodyFacingMovement', 1)[1].split('\n    }', 1)[0]
    idle_src = source_text.split('public static void tickIdleLookAround', 1)[1].split('\n    }', 1)[0]
    assert 'npc.yBodyRot = travelYaw;' in move_src and 'npc.setYRot(travelYaw);' in move_src
    assert 'npc.yBodyRot = Mth.rotLerp(0.035F' in idle_src
    assert 'npc.setYRot(Mth.rotLerp(0.035F' in idle_src
    assert 'npc.yHeadRot = Mth.rotLerp(0.12F' in idle_src
    assert '25.0F + npc.getRandom().nextFloat() * 55.0F' in idle_src
    assert 'npc.isTalking()' in idle_src and 'npc.getTarget() != null' in idle_src

    bridge_text = (ROOT / 'src/build261/dev/settledlands/model/NpcMotionBridge256.java').read_text(encoding='utf-8')
    assert 'float travelYaw = movementBodyYaw(npc, partialTick);' in bridge_text
    assert 'npc.yBodyRotO = savedBodyYawO;' in bridge_text and 'npc.yBodyRot = savedBodyYaw;' in bridge_text
    base_bridge = old[patch.ENGINE.replace('npc/NpcAutoAiEngine255', 'model/NpcMotionBridge256') + '.class']
    assert new['dev/settledlands/model/NpcMotionBridge256.class'] == base_bridge

    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION and meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)

    # Verify the long-standing 1.21.1 AttributeInstance ABI reference is retained.
    abi_refs = []
    for idx, entry in enumerate(engine_k.cp):
        if entry and entry['tag'] in (10, 11):
            ref = constant_ref(engine_k, idx)
            if ref and ref[0].endswith('/AttributeInstance') and ref[1] == 'removeModifier':
                abi_refs.append(ref)
    assert abi_refs and set(abi_refs) == {
        ('net/minecraft/world/entity/ai/attributes/AttributeInstance',
         'removeModifier', '(Lnet/minecraft/resources/ResourceLocation;)Z')
    }
    print('OK exact Build 261 diff; walk yaw is retained; post-stop head/torso scans are smooth and random; renderer/ABI preserved', flush=True)


def patch_auto_method_ref(k, cp: int):
    return constant_ref(k, cp)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', type=pathlib.Path, default=ROOT / BASE_NAME)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, jar, java, gson = (p.resolve() for p in (a.base, a.jar, a.java, a.gson_source))
    assert base.name == BASE_NAME and sha(base) == patch.BASE_SHA
    assert jar.is_file() and java.is_file() and gson.is_file()
    for filename in ('GATE-RESULT-261.txt', 'GATE-BACKUP-RESULT-261.txt'):
        assert 'BUILD 261 GATE: PASS' in (ROOT / filename).read_text(encoding='utf-8')
    print('OK exact Build 261 base hash and release evidence', flush=True)

    with tempfile.TemporaryDirectory(prefix='sl262-gate-') as td:
        reproduced = pathlib.Path(td) / 'reproduced262.jar'
        run([sys.executable, str(ROOT / 'tools/patch_auto_ai262.py'), '--base', str(base),
             '--out', str(reproduced), '--java', str(java)], 'deterministic Build 262 rebuild')
        assert reproduced.read_bytes() == jar.read_bytes()
    print(f'OK reproducible JAR sha256={sha(jar)} size={jar.stat().st_size}', flush=True)

    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar)], 'structural JAR validator')
    verify = [sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base)]
    verify.extend(arg for name in sorted(patch.CHANGED) for arg in ('--expect-changed', name))
    run(verify, 'ZIP/TOML/assets/mixins and exact-diff validator')
    static_semantics(base, jar)

    run([sys.executable, str(ROOT / 'tests/jvm_auto_ai262.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 runtime: travel-facing, first post-stop torso step, randomized torso/head scan, dialogue priority, inherited Auto-AI/combo')
    run([sys.executable, str(ROOT / 'tests/jvm_punchy_fp261.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Build 261 animation/first-person regression scenarios')
    run([sys.executable, str(ROOT / 'tests/mutants262.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'verifier-valid post-stop torso mutant')
    print('BUILD 262 GATE: PASS (Build 261 preserved; post-stop transition and randomized torso/head runtime scenarios plus negative control pass; Minecraft/NeoForge itself not launched)', flush=True)

if __name__ == '__main__':
    main()
