#!/usr/bin/env python3
"""Release gate 260: human NPC sounds, movement-facing, and dialogue look policy."""
import argparse
import hashlib
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
import patch_auto_ai260 as patch
from patch_corpse_pose237 import constant_ref

BASE_SIZE = 2256712


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


def method_fieldrefs(raw: bytes, name: str, desc: str) -> set[tuple[str, str, str]]:
    k = classedit.Klass(raw)
    m = k.find_method(name, desc)
    assert m is not None, (name, desc)
    code = classedit.code_parts(k, m)['code']
    refs = set()
    for _, ins in decode.walk(code):
        if ins[0] in (0xB2, 0xB3, 0xB4, 0xB5):
            refs.add(constant_ref(k, int.from_bytes(ins[1:3], 'big')))
    return refs


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as zin, zipfile.ZipFile(jar) as zout:
        assert zin.testzip() is None and zout.testzip() is None
        old = {i.filename: zin.read(i.filename) for i in zin.infolist()}
        new = {i.filename: zout.read(i.filename) for i in zout.infolist()}
    assert set(new) == set(old)
    assert {name for name in old if old[name] != new[name]} == patch.CHANGED
    for name in old:
        if name not in patch.CHANGED:
            assert old[name] == new[name], name

    guide = new[patch.GUIDE + '.class']
    sounds = {
        ('getAmbientSound', '()Lnet/minecraft/sounds/SoundEvent;', 'VILLAGER_AMBIENT'),
        ('getHurtSound', '(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/sounds/SoundEvent;', 'VILLAGER_HURT'),
        ('getDeathSound', '()Lnet/minecraft/sounds/SoundEvent;', 'VILLAGER_DEATH'),
    }
    for method, desc, expected in sounds:
        refs = method_fieldrefs(guide, method, desc)
        assert (patch.SOUND_EVENTS, expected, patch.SOUND_DESC) in refs, (method, refs)
        assert not any(owner == patch.SOUND_EVENTS and name.startswith('SKELETON_')
                       for owner, name, _ in refs), (method, refs)

    guide_k = classedit.Klass(guide)
    custom = guide_k.find_method('customServerAiStep', '()V')
    assert custom is not None
    custom_code = classedit.code_parts(guide_k, custom)['code']
    custom_refs = []
    for _, ins in decode.walk(custom_code):
        if ins[0] in (0xB6, 0xB8):
            custom_refs.append(constant_ref(guide_k, int.from_bytes(ins[1:3], 'big')))
    assert (patch.ENGINE, 'shouldLookAtPlayer', patch.GUIDE_DESC) in custom_refs
    assert (patch.ENGINE, 'lookHeadAtPlayer', patch.HEAD_DESC) in custom_refs
    assert not any(ref[0] == 'net/minecraft/world/entity/ai/control/LookControl'
                   and ref[1] == 'setLookAt' for ref in custom_refs), custom_refs

    engine_k = classedit.Klass(new[patch.ENGINE + '.class'])
    abi_refs = []
    for idx, entry in enumerate(engine_k.cp):
        if entry and entry['tag'] in (10, 11):
            ref = constant_ref(engine_k, idx)
            if ref[0].endswith('/AttributeInstance') and ref[1] == 'removeModifier':
                abi_refs.append(ref)
    assert abi_refs and set(abi_refs) == {
        ('net/minecraft/world/entity/ai/attributes/AttributeInstance',
         'removeModifier', '(Lnet/minecraft/resources/ResourceLocation;)Z')
    }, abi_refs

    engine_source = (ROOT / 'src/build260/dev/settledlands/npc/NpcAutoAiEngine255.java').read_text(encoding='utf-8')
    assert 'return npc.isTalking() || (profile != null && profile.lookAtPlayer);' in engine_source
    head_method = engine_source.split('public static void lookHeadAtPlayer', 1)[1].split('\n    }', 1)[0]
    assert 'npc.yHeadRot =' in head_method and 'npc.yBodyRot' not in head_method and 'npc.setYRot' not in head_method
    bridge_source = (ROOT / 'src/build260/dev/settledlands/model/NpcMotionBridge256.java').read_text(encoding='utf-8')
    assert 'movement.z, movement.x' in bridge_source and 'npc.yBodyRot = travelYaw;' in bridge_source
    assert 'npc.yBodyRot = savedBodyYaw;' in bridge_source and 'npc.yBodyRotO = savedBodyYawO;' in bridge_source
    assert bridge_source.count('!profile.lookAtPlayer && !npc.isTalking() && npc.getTarget() == null') == 2

    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION and meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)
    assert new[patch.BRIDGE + '.class'] != old[patch.BRIDGE + '.class']
    print('OK five-entry diff; villager voice refs; look toggle only changes head; dialogue override; torso follows movement; build-258 ABI retained', flush=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, jar, java, gson = (p.resolve() for p in (a.base, a.jar, a.java, a.gson_source))
    assert base == (ROOT / 'settledlands-0.8.0-build259-npc-combo-tuning.jar').resolve()
    assert base.is_file() and base.stat().st_size == BASE_SIZE and sha(base) == patch.BASE_SHA
    assert jar.is_file() and java.is_file() and gson.is_file()
    for filename in ('GATE-RESULT-259.txt', 'GATE-BACKUP-RESULT-259.txt'):
        assert 'BUILD 259 GATE: PASS' in (ROOT / filename).read_text(encoding='utf-8')
    print('OK exact Build 259 ancestry and gate evidence', flush=True)

    with tempfile.TemporaryDirectory(prefix='sl260-gate-') as td:
        reproduced = pathlib.Path(td) / 'reproduced260.jar'
        run([sys.executable, str(ROOT / 'tools/patch_auto_ai260.py'), '--base', str(base),
             '--out', str(reproduced), '--java', str(java)], 'deterministic Build 260 rebuild')
        assert reproduced.read_bytes() == jar.read_bytes()
        print(f'OK reproducible JAR sha256={sha(jar)} size={jar.stat().st_size}', flush=True)

    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar), '--base', str(base)],
        'structural JAR validator')
    run([sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base)],
        'ZIP/TOML/assets/mixins validator')
    static_semantics(base, jar)
    targets = [
        'dev/settledlands/npc/NpcAutoAiEngine255#shouldLookAtPlayer(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcAutoAiEngine255#lookHeadAtPlayer(dev.settledlands.npc.GuideNpc,net.minecraft.world.entity.player.Player)',
        'dev/settledlands/npc/NpcAutoAiEngine255#navigationSpeedFactor(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/GuideNpc#customServerAiStep',
    ]
    run([sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        'Java 21 verification of GuideNpc look branch and sound helpers')
    run([sys.executable, str(ROOT / 'tests/jvm_auto_ai260.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'Java 21 scenarios: head-only look, toggle override in dialogue, inherited Stats/Auto-AI/combo behavior')
    run([sys.executable, str(ROOT / 'tests/mutants260.py'), '--java', str(java),
         '--jar', str(jar), '--gson-source', str(gson)],
        'five verifier-valid Auto-AI, speed and look-toggle mutants')
    print('BUILD 260 GATE: PASS (skeleton voice replaced; body follows travel direction; player-look is head-only; dialogue forces look; 1.21.1 ABI and Build 259 mechanics preserved; 5 mutants killed; full game not run)', flush=True)

if __name__ == '__main__':
    main()
