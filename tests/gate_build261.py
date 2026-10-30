#!/usr/bin/env python3
"""Release gate 261: idle NPC gaze, unified NPC animation layers, and Punchy first-person arms."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit
import decode
import patch_auto_ai261 as patch
from patch_corpse_pose237 import constant_ref

BASE_NAME = 'settledlands-0.8.0-build260-npc-facing-sounds.jar'
ENGINE_SOURCE = 'src/build261/dev/settledlands/npc/NpcAutoAiEngine255.java'
BRIDGE_SOURCE = 'src/build261/dev/settledlands/model/NpcMotionBridge256.java'
FP_SOURCE = 'src/build261/dev/settledlands/npc/client/FirstPersonPossession256.java'
PUNCHY_SOURCE = 'src/build261/dev/settledlands/mixin/PunchyFirstPersonContextMixin261.java'


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


def refs(raw: bytes, method_name: str, desc: str):
    k = classedit.Klass(raw)
    method = k.find_method(method_name, desc)
    assert method is not None, (method_name, desc)
    code = classedit.code_parts(k, method)['code']
    out = []
    for _, ins in decode.walk(code):
        if ins[0] in (0xB6, 0xB7, 0xB8, 0xB9):
            out.append(patch.previous.classedit.get_method_ref(k, int.from_bytes(ins[1:3], 'big')))
    return out


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as source, zipfile.ZipFile(jar) as candidate:
        assert source.testzip() is None and candidate.testzip() is None
        old = {item.filename: source.read(item.filename) for item in source.infolist()}
        new = {item.filename: candidate.read(item.filename) for item in candidate.infolist()}
    assert set(new) - set(old) == {patch.PUNCHY_MIXIN + '.class'}
    assert not (set(old) - set(new))
    changed = {name for name in old if old[name] != new[name]}
    assert changed | (set(new) - set(old)) == patch.CHANGED, (changed, patch.CHANGED)
    for name in set(old) - changed:
        assert old[name] == new[name], name

    cfg = json.loads(new[patch.MIXIN_CONFIG].decode('utf-8'))
    assert patch.PUNCHY_MIXIN.split('/')[-1] in cfg['client']
    assert cfg['mixins'] == json.loads(old[patch.MIXIN_CONFIG].decode('utf-8'))['mixins']

    engine = new[patch.ENGINE + '.class']
    engine_k = classedit.Klass(engine)
    idle = engine_k.find_method('tickIdleLookAround', '(Ldev/settledlands/npc/GuideNpc;)V')
    assert idle is not None
    code = classedit.code_parts(engine_k, idle)['code']
    idle_refs = []
    for _, ins in decode.walk(code):
        if ins[0] in (0xB4, 0xB6, 0xB8, 0xB9):
            cp = int.from_bytes(ins[1:3], 'big')
            if ins[0] == 0xB4:
                continue
            idle_refs.append(constant_ref(engine_k, cp))
    assert any(ref and ref[1] == 'rotLerp' for ref in idle_refs)
    assert any(ref and ref[1] == 'getNearestPlayer' for ref in idle_refs)
    source_text = (ROOT / ENGINE_SOURCE).read_text(encoding='utf-8')
    idle_source = source_text.split('public static void tickIdleLookAround', 1)[1].split('\n    }', 1)[0]
    assert 'npc.yHeadRot = Mth.rotLerp' in idle_source
    assert 'npc.yBodyRot =' not in idle_source and 'npc.setYRot' not in idle_source
    assert '25 + npc.getRandom().nextInt(50)' in idle_source

    bridge = (ROOT / BRIDGE_SOURCE).read_text(encoding='utf-8')
    assert 'boolean ivyBridge = lod < 2 && isIvyRig(model) && ensureEmfBridge();' in bridge
    assert 'if (clipState == CustomClipState.FULL_BODY || npc.isTalking())' in bridge
    assert 'applyFullBodyCustomClip(model, npc, rawSwing, rawAmount, age, headYaw, headPitch, pt);' in bridge
    full_clip = bridge.split('private static void applyFullBodyCustomClip', 1)[1].split('\n    }', 1)[0]
    assert 'locomotionPose[i].set(legParts.get(i))' in full_clip and 'preserveLocomotion' in full_clip

    fp = (ROOT / FP_SOURCE).read_text(encoding='utf-8')
    assert 'pushFirstPersonRenderContext(AbstractClientPlayer player' in fp
    assert 'candidate.firstPersonPass' in fp and 'ctx.firstPersonPass' in fp
    punchy = (ROOT / PUNCHY_SOURCE).read_text(encoding='utf-8')
    assert 'net.minecraft.client.renderer.ItemInHandRenderer' in punchy
    assert 'renderHandsWithItems(' in punchy
    assert 'pushFirstPersonRenderContext(player, buffer, packedLight)' in punchy
    assert 'popPlayerRenderContext()' in punchy

    # ABI and inherited Build 260 behavior remain preserved.
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
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION and meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)
    print('OK exact Build 260 ancestry/diff; idle gaze is head-only; Ivy full-body/talk clips layer consistently; walking legs retained; Punchy context is client-only; 1.21.1 ABI retained', flush=True)


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
    for filename in ('GATE-RESULT-260.txt', 'GATE-BACKUP-RESULT-260.txt'):
        assert 'BUILD 260 GATE: PASS' in (ROOT / filename).read_text(encoding='utf-8')
    print('OK exact Build 260 base hash and release evidence', flush=True)

    with __import__('tempfile').TemporaryDirectory(prefix='sl261-gate-') as td:
        reproduced = pathlib.Path(td) / 'reproduced261.jar'
        run([sys.executable, str(ROOT / 'tools/patch_auto_ai261.py'), '--base', str(base),
             '--out', str(reproduced), '--java', str(java)], 'deterministic Build 261 rebuild')
        assert reproduced.read_bytes() == jar.read_bytes()
    print(f'OK reproducible JAR sha256={sha(jar)} size={jar.stat().st_size}', flush=True)

    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar)],
        'structural JAR validator')
    verify = [sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base),
              '--expect-added', patch.PUNCHY_MIXIN + '.class']
    verify.extend(arg for name in sorted(patch.CHANGED - {patch.PUNCHY_MIXIN + '.class'})
                  for arg in ('--expect-changed', name))
    run(verify, 'ZIP/TOML/assets/mixins and exact-diff validator')
    static_semantics(base, jar)

    run([sys.executable, str(ROOT / 'tests/jvm_auto_ai261.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 runtime: idle head scan, body-yaw isolation, dialogue priority, inherited Auto-AI/combo tests')
    run([sys.executable, str(ROOT / 'tests/jvm_punchy_fp261.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 animation/first-person scenarios: Fresh+TnM during hurt/dialogue/full clips; locomotion legs; Punchy-style arm/skin replacement')
    run([sys.executable, str(ROOT / 'tests/mutants261.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'two verifier-valid idle-look and first-person-context mutants')
    print('BUILD 261 GATE: PASS (Build 260 preserved; new renderer/AI scenarios and 2 negative controls pass; Minecraft/NeoForge itself not launched)', flush=True)

if __name__ == '__main__':
    main()
