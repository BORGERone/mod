#!/usr/bin/env python3
"""Deterministic Build 260: human NPC sounds, path-facing, and dialogue look behavior."""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_joml248
import classedit
import decode
import gen_api256
import gen_api260
import patch_auto_ai259 as previous
from patch_corpse_pose237 import constant_ref

BASE_SHA = 'ab44f422a423a428f198ae4393e55ad8409857e10e85674d1ccfa093790f5da7'
BASE_VERSION = '0.8.0-build259-npc-combo-tuning'
NEW_VERSION = '0.8.0-build260-npc-facing-sounds'
ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255'
GUIDE = 'dev/settledlands/npc/GuideNpc'
BRIDGE = 'dev/settledlands/model/NpcMotionBridge256'
SOUND_EVENTS = 'net/minecraft/sounds/SoundEvents'
SOUND_DESC = 'Lnet/minecraft/sounds/SoundEvent;'
GUIDE_DESC = '(Ldev/settledlands/npc/GuideNpc;)Z'
HEAD_DESC = '(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/entity/player/Player;)V'
CHANGED = {
    ENGINE + '.class', GUIDE + '.class', BRIDGE + '.class',
    'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt',
}
CHANGELOG = (
    'Build 260: use villager ambient/hurt/death sounds for GuideNpc; make the look-at-player toggle '
    'head-only so it cannot turn the body while walking; align the rendered torso to travel direction '
    'when the toggle is off or on; keep dialogue head-look active even when the toggle is off.'
)
PATCHINFO = (
    'сборка 260: заменены звуки скелета на звуки жителя; корпус NPC при ходьбе направлен по движению '
    'при любом состоянии переключателя взгляда; переключатель управляет только головой; во время '
    'диалога NPC смотрит на игрока даже при выключенном переключателе.'
)


def compile_sources(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> dict[str, bytes]:
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == previous.prior.prev.ECJ_SHA
    result: dict[str, bytes] = {}

    # Compile the server-safe AI additions against the exact corrected API silhouette.
    api_engine = work / 'api-engine'
    out_engine = work / 'classes-engine'
    gen_api260.main(api_engine)
    out_engine.mkdir()
    engine_sources = sorted(api_engine.rglob('*.java')) + [
        ROOT / 'src/build260/dev/settledlands/npc/NpcAutoAiEngine255.java'
    ]
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', str(base), '-d', str(out_engine), *map(str, engine_sources)], check=True
    )
    result[ENGINE + '.class'] = (out_engine / (ENGINE + '.class')).read_bytes()

    # Compile the client renderer bridge against the real base classes, compile-only Minecraft APIs,
    # and the vendored JOML source fixture (never packaged into the mod).
    joml = work / 'joml'
    build_joml248.build(java, joml)
    api_bridge = work / 'api-bridge'
    out_bridge = work / 'classes-bridge'
    gen_api256.main(str(api_bridge))
    out_bridge.mkdir()
    bridge_sources = sorted(api_bridge.rglob('*.java')) + [
        ROOT / 'src/build260/dev/settledlands/model/NpcMotionBridge256.java'
    ]
    classpath = str(base) + __import__('os').pathsep + str(joml)
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(out_bridge), *map(str, bridge_sources)], check=True
    )
    result[BRIDGE + '.class'] = (out_bridge / (BRIDGE + '.class')).read_bytes()
    return result


def patch_guide_look(raw: bytes) -> bytes:
    klass = classedit.Klass(raw)
    method = klass.find_method('customServerAiStep', '()V')
    assert method is not None
    parts = classedit.code_parts(klass, method)
    code = bytearray(parts['code'])
    instructions = decode.walk(code)

    # Replace `this.profile.lookAtPlayer` with shouldLookAtPlayer(this), retaining the
    # original 10-byte range and all existing frame/branch offsets. The helper also
    # returns true while talking, even when the profile toggle is false.
    profile_ref = (GUIDE, 'profile', 'Ldev/settledlands/npc/NpcProfile;')
    toggle_ref = ('dev/settledlands/npc/NpcProfile', 'lookAtPlayer', 'Z')
    profile_idx = next((i for i, (pc, ins) in enumerate(instructions)
                        if ins[0] == 0xB4 and constant_ref(klass, int.from_bytes(ins[1:3], 'big')) == profile_ref), None)
    assert profile_idx is not None and profile_idx >= 1
    assert instructions[profile_idx - 1][1] == b'\x2a'
    toggle_i = profile_idx + 1
    assert instructions[toggle_i][1][0] == 0xB4
    assert constant_ref(klass, int.from_bytes(instructions[toggle_i][1][1:3], 'big')) == toggle_ref
    branch_i = toggle_i + 1
    branch_pc, branch = instructions[branch_i]
    assert branch[0] == 0x99
    start = instructions[profile_idx - 1][0]
    target = branch_pc + int.from_bytes(branch[1:3], 'big', signed=True)
    helper = klass.add_methodref(ENGINE, 'shouldLookAtPlayer', GUIDE_DESC)
    replacement = b'\x2a\xb8' + helper.to_bytes(2, 'big')
    new_branch_pc = start + 4
    replacement += b'\x99' + (target - new_branch_pc).to_bytes(2, 'big', signed=True) + b'\x00\x00\x00'
    assert len(replacement) == 10
    code[start:start + 10] = replacement

    # Replace the vanilla LookControl call (which rotates body and head) with the head-only helper.
    instructions = decode.walk(code)
    look_ref = ('net/minecraft/world/entity/ai/control/LookControl', 'setLookAt',
                '(Lnet/minecraft/world/entity/Entity;FF)V')
    call_i = next((i for i, (pc, ins) in enumerate(instructions)
                   if ins[0] == 0xB6 and constant_ref(klass, int.from_bytes(ins[1:3], 'big')) == look_ref), None)
    assert call_i is not None and call_i >= 6
    # The six preceding ops are: aload_0, getLookControl, aload_0, getfield trackedLookPlayer, ldc_w, ldc_w.
    start_i = call_i - 6
    start = instructions[start_i][0]
    prior = [ins for _, ins in instructions[start_i:call_i]]
    assert prior[0] == b'\x2a' and prior[2] == b'\x2a'
    assert prior[1][0] == 0xB6 and prior[3][0] == 0xB4 and prior[4][0] == 0x13 and prior[5][0] == 0x13
    assert constant_ref(klass, int.from_bytes(prior[3][1:3], 'big')) == (
        GUIDE, 'trackedLookPlayer', 'Lnet/minecraft/world/entity/player/Player;')
    head_helper = klass.add_methodref(ENGINE, 'lookHeadAtPlayer', HEAD_DESC)
    replacement = b'\x2a\x2a\xb4' + prior[3][1:3] + b'\xb8' + head_helper.to_bytes(2, 'big') + b'\x00' * 9
    assert len(replacement) == 17
    code[start:start + 17] = replacement
    parts['code'] = bytes(code)
    return klass.dump(method, classedit.build_code_body(klass, parts))


def patch_guide_sounds(raw: bytes) -> bytes:
    replacements = (
        ('getAmbientSound', '()Lnet/minecraft/sounds/SoundEvent;', 'SKELETON_AMBIENT', 'VILLAGER_AMBIENT'),
        ('getHurtSound', '(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/sounds/SoundEvent;', 'SKELETON_HURT', 'VILLAGER_HURT'),
        ('getDeathSound', '()Lnet/minecraft/sounds/SoundEvent;', 'SKELETON_DEATH', 'VILLAGER_DEATH'),
    )
    for method_name, desc, old_sound, new_sound in replacements:
        klass = classedit.Klass(raw)
        method = klass.find_method(method_name, desc)
        assert method is not None
        parts = classedit.code_parts(klass, method)
        code = bytearray(parts['code'])
        hits = []
        for pc, ins in decode.walk(code):
            if ins[0] != 0xB2:
                continue
            ref = constant_ref(klass, int.from_bytes(ins[1:3], 'big'))
            if ref == (SOUND_EVENTS, old_sound, SOUND_DESC):
                hits.append(pc)
        assert len(hits) == 1, (method_name, old_sound, hits)
        field = klass.add_fieldref(SOUND_EVENTS, new_sound, SOUND_DESC)
        code[hits[0] + 1:hits[0] + 3] = field.to_bytes(2, 'big')
        parts['code'] = bytes(code)
        raw = klass.dump(method, classedit.build_code_body(klass, parts))
    return raw


def patch_entries(entries: dict[str, bytes], compiled: dict[str, bytes]) -> dict[str, bytes]:
    updated = dict(entries)
    updated.update(compiled)
    updated[GUIDE + '.class'] = patch_guide_sounds(patch_guide_look(entries[GUIDE + '.class']))
    toml_name = 'META-INF/neoforge.mods.toml'
    text = entries[toml_name].decode('utf-8')
    old_version = f'version="{BASE_VERSION}"'
    marker = "description='''"
    assert text.count(old_version) == 1 and text.count(marker) == 1
    text = text.replace(old_version, f'version="{NEW_VERSION}"', 1).replace(
        marker, marker + CHANGELOG + '\n\n', 1
    )
    assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
    updated[toml_name] = text.encode('utf-8')
    updated['settledlands-patch-info.txt'] = (
        PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode('utf-8')
    ).encode('utf-8')
    changed = {name for name in entries if entries[name] != updated[name]}
    assert changed == CHANGED, (changed ^ CHANGED, changed)
    assert set(updated) == set(entries)
    return updated


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, out, java = a.base.resolve(), a.out.resolve(), a.java.resolve()
    assert base != out and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-260-compile-') as td:
        compiled = compile_sources(java, base, pathlib.Path(td))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            updated = patch_entries(original, compiled)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, updated[info.filename])
    print('wrote', out, 'bytes=', out.stat().st_size,
          'sha256=', hashlib.sha256(out.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
