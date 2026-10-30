#!/usr/bin/env python3
"""Release gate 263: independent cutscene fade options and their persistence/timing."""
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
sys.path[:0] = [str(ROOT / 'tools')]
import classedit
import decode
import patch_cutscene_fade263 as patch

BASE = 'settledlands-0.8.0-build262-smooth-npc-turning.jar'
CHANGED = {
    'META-INF/neoforge.mods.toml',
    'assets/settledlands/lang/en_us.json',
    'assets/settledlands/lang/ru_ru.json',
    'dev/settledlands/npc/GuideDialogue.class',
    'dev/settledlands/npc/NpcCopyModEvents.class',
    'settledlands-patch-info.txt',
    'settledlands.mixins.json',
}
ADDED = {
    'dev/settledlands/mixin/CutsceneCameraFadeChainMixin263.class',
    'dev/settledlands/mixin/CutsceneCameraFadePlaybackMixin263.class',
    'dev/settledlands/mixin/CutsceneGraphFadeDataMixin263.class',
    'dev/settledlands/mixin/CutsceneGraphFadeUiMixin263.class',
    'dev/settledlands/mixin/CutsceneNodeFadeMixin263.class',
    'dev/settledlands/npc/CutsceneFadeControl263$SceneFade.class',
    'dev/settledlands/npc/CutsceneFadeControl263.class',
    'dev/settledlands/npc/CutsceneFadeGraphData263.class',
    'dev/settledlands/npc/CutsceneFadeNodeAccess263.class',
    'dev/settledlands/npc/CutsceneFadePayload263.class',
    'dev/settledlands/npc/client/CutsceneFadeOverlay263.class',
}


def sha(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str], label: str) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
    print(f'OK {label}', flush=True)
    return result.stdout


def methods_by_signature(raw: bytes) -> dict[tuple[str, str], bytes]:
    klass = classedit.Klass(raw)
    result = {}
    for method in klass.methods:
        body = next((attr['body'] for attr in method['attrs'] if attr['name'] == 'Code'), b'')
        result[(method['name'], method['desc'])] = body
    return result


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as source, zipfile.ZipFile(jar) as target:
        assert source.testzip() is None and target.testzip() is None
        old = {info.filename: source.read(info.filename) for info in source.infolist()}
        new = {info.filename: target.read(info.filename) for info in target.infolist()}
    assert set(new) - set(old) == ADDED
    assert set(old) - set(new) == set()
    assert {name for name in old if old[name] != new[name]} == CHANGED
    assert all(old[name] == new[name] for name in set(old) - CHANGED)

    for class_name, expected_method in (
        ('dev/settledlands/npc/GuideDialogue.class', 'resolveExecutionTarget'),
        ('dev/settledlands/npc/NpcCopyModEvents.class', 'onRegisterPayloads'),
    ):
        before = methods_by_signature(old[class_name])
        after = methods_by_signature(new[class_name])
        assert set(before) == set(after)
        changed = {sig[0] for sig in before if before[sig] != after[sig]}
        assert changed == {expected_method}, (class_name, changed)

    guide = classedit.Klass(new['dev/settledlands/npc/GuideDialogue.class'])
    desc = '(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I'
    method = guide.find_method('resolveExecutionTarget', desc)
    assert method is not None
    calls = []
    for pc, ins in decode.walk(classedit.code_parts(guide, method)['code']):
        if ins[0] not in (0xB6, 0xB7, 0xB8, 0xB9):
            continue
        ref = patch.constant_ref(guide, int.from_bytes(ins[1:3], 'big'))
        if ref:
            calls.append((pc, ref))
    initial = [pc for pc, ref in calls if ref == (
        'dev/settledlands/npc/CutsceneFadeControl263', 'prepareInitial',
        '(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V')]
    play = [pc for pc, ref in calls if ref == (
        'dev/settledlands/npc/CutsceneCamera', 'play',
        '(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z')]
    assert len(initial) == len(play) == 1 and initial[0] < play[0]

    config = json.loads(new['settledlands.mixins.json'])
    assert 'CutsceneGraphFadeDataMixin263' in config['mixins']
    assert 'CutsceneNodeFadeMixin263' in config['mixins']
    assert 'CutsceneCameraFadeChainMixin263' in config['mixins']
    assert 'CutsceneGraphFadeUiMixin263' in config['client']
    assert 'CutsceneCameraFadePlaybackMixin263' in config['client']
    for mixin in ADDED:
        if '/mixin/' in mixin:
            assert mixin.removeprefix('dev/settledlands/mixin/').removesuffix('.class') in (
                config['mixins'] + config['client'])

    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == patch.NEW_VERSION and meta['description'].startswith(patch.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(patch.PATCHINFO)
    ru = json.loads(new['assets/settledlands/lang/ru_ru.json'])
    en = json.loads(new['assets/settledlands/lang/en_us.json'])
    for key in (
        'gui.settledlands.graph.cutscene_fade_out_on',
        'gui.settledlands.graph.cutscene_fade_out_off',
        'gui.settledlands.graph.cutscene_fade_in_on',
        'gui.settledlands.graph.cutscene_fade_in_off',
    ):
        assert key in ru and key in en
    assert '500L' in (ROOT / 'src/build263/dev/settledlands/npc/client/CutsceneFadeOverlay263.java').read_text()
    helper = (ROOT / 'src/build263/dev/settledlands/npc/CutsceneFadeGraphData263.java').read_text()
    assert 'node.animLoop' not in helper and 'node.moveEscort' not in helper
    assert 'CutsceneFadeNodeAccess263' in helper and 'CutsceneFadeGraphData263.copy' in (
        ROOT / 'src/build263/dev/settledlands/mixin/CutsceneNodeFadeMixin263.java').read_text()
    print('OK exact Build 262 diff; independent node fields, persistence, undo copy, UI and chained play hooks', flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=pathlib.Path, default=ROOT / BASE)
    parser.add_argument('--jar', required=True, type=pathlib.Path)
    parser.add_argument('--java', required=True, type=pathlib.Path)
    args = parser.parse_args()
    base, jar, java = (p.resolve() for p in (args.base, args.jar, args.java))
    assert base.name == BASE and sha(base) == patch.BASE_SHA
    assert jar.is_file() and java.is_file()

    with tempfile.TemporaryDirectory(prefix='sl263-gate-') as td:
        reproduced = pathlib.Path(td) / 'reproduced263.jar'
        run([sys.executable, str(ROOT / 'tools/patch_cutscene_fade263.py'), '--base', str(base),
             '--out', str(reproduced), '--java', str(java)], 'deterministic Build 263 rebuild')
        assert reproduced.read_bytes() == jar.read_bytes()

    run([sys.executable, str(ROOT / 'tools/validate_jar.py'), str(jar), '--base', str(base)],
        'structural JAR validator')
    verify = [sys.executable, str(ROOT / 'tools/verify_jar.py'), str(jar), '--base', str(base)]
    verify.extend(arg for name in sorted(CHANGED) for arg in ('--expect-changed', name))
    verify.extend(arg for name in sorted(ADDED) for arg in ('--expect-added', name))
    run(verify, 'ZIP/TOML/assets/mixins and exact-diff validator')
    static_semantics(base, jar)
    verify_targets = [
        'dev/settledlands/npc/GuideDialogue#resolveExecutionTarget(net.minecraft.server.level.ServerPlayer,net.minecraft.world.entity.Entity,dev.settledlands.npc.GuideNpc,dev.settledlands.npc.GuideDialogue$Session,dev.settledlands.npc.NpcDialogueGraph,int,java.util.Set)',
        'dev/settledlands/npc/NpcCopyModEvents#onRegisterPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent)',
        'dev/settledlands/npc/CutsceneFadeGraphData263#save(dev.settledlands.npc.NpcDialogueGraph,net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/CutsceneFadeGraphData263#load(dev.settledlands.npc.NpcDialogueGraph,net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/CutsceneFadeControl263#prepareInitial(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.NpcDialogueGraph,dev.settledlands.npc.NpcDialogueGraph$Node)',
        'dev/settledlands/npc/CutsceneFadeControl263#prepareChained(net.minecraft.server.level.ServerPlayer,java.lang.String)',
        'dev/settledlands/npc/CutsceneFadePayload263#register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent)',
        'dev/settledlands/npc/client/CutsceneCameraClient#handlePlay(double[][],float,int)',
        'dev/settledlands/mixin/CutsceneNodeFadeMixin263#settledlands$copyFadeFlags(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)',
        'dev/settledlands/mixin/CutsceneGraphFadeDataMixin263#settledlands$saveCutsceneFadeOptions(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)',
        'dev/settledlands/mixin/CutsceneGraphFadeUiMixin263#settledlands$appendCutsceneFadeSwitches(org.spongepowered.asm.mixin.injection.callback.CallbackInfo)',
        'dev/settledlands/mixin/CutsceneCameraFadeChainMixin263#settledlands$prepareChainedFade(net.minecraft.server.level.ServerPlayer,java.lang.String,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)',
        'dev/settledlands/mixin/CutsceneCameraFadeChainMixin263#settledlands$clearFailedChain(net.minecraft.server.level.ServerPlayer,java.lang.String,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)',
        'dev/settledlands/mixin/CutsceneCameraFadeChainMixin263#settledlands$clearOnLogout(net.minecraft.server.level.ServerPlayer,org.spongepowered.asm.mixin.injection.callback.CallbackInfo)',
        'dev/settledlands/mixin/CutsceneCameraFadePlaybackMixin263#settledlands$waitForBlack(double[][],float,int,org.spongepowered.asm.mixin.injection.callback.CallbackInfo)',
        'dev/settledlands/mixin/CutsceneCameraFadePlaybackMixin263#settledlands$holdPlayingSignal(boolean,org.spongepowered.asm.mixin.injection.callback.CallbackInfo)',
        'dev/settledlands/mixin/CutsceneCameraFadePlaybackMixin263#settledlands$restoreAfterPlayback(boolean,org.spongepowered.asm.mixin.injection.callback.CallbackInfo)',
    ]
    run([sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *verify_targets,
         '--java', str(java)], 'Java 21 verifies patched classes and every new mixin/helper callback')
    run([sys.executable, str(ROOT / 'tests/jvm_cutscene_fade263.py'), '--java', str(java), '--jar', str(jar)],
        'Java 21 runtime fade + graph-data regression scenarios')
    print(f'BUILD 263 GATE: PASS | sha256={sha(jar)} | size={jar.stat().st_size} bytes | Minecraft/NeoForge itself not launched', flush=True)


if __name__ == '__main__':
    main()
