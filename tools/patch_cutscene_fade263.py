#!/usr/bin/env python3
"""Build 263: per-cutscene-node fade-to-black/from-black transitions."""
import argparse
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools')]
import classedit
import decode
import patch_npc_stats246 as bytecode
from patch_corpse_pose237 import constant_ref

BASE_NAME = 'settledlands-0.8.0-build262-smooth-npc-turning.jar'
BASE_SHA = '21fc8fa7c861c3652f020ce9bb10bac133ac253675084982e65f16cc734f2531'
BASE_VERSION = '0.8.0-build262-smooth-npc-turning'
NEW_VERSION = '0.8.0-build263-cutscene-fade'
ENGINE_SHA = '74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'
CUTSCENE_CAMERA = 'dev/settledlands/npc/CutsceneCamera'
GUIDE_DIALOGUE = 'dev/settledlands/npc/GuideDialogue'
NPC_COPY_EVENTS = 'dev/settledlands/npc/NpcCopyModEvents'

CHANGELOG = (
    'Build 263: add independent fade-to-black and fade-from-black switches to each dialogue cutscene node; '
    'smooth transitions play on the selected scene start and are preserved through sequential cutscene chains.'
)
PATCHINFO = (
    'сборка 263: в ноду катсцены добавлены два независимых тумблера плавного затемнения и появления. '
    'Переходы работают при запуске сцены и сохраняются между последовательно соединёнными катсценами.'
)

SOURCES = [
    'dev/settledlands/npc/CutsceneFadeGraphData263.java',
    'dev/settledlands/npc/CutsceneFadeNodeAccess263.java',
    'dev/settledlands/npc/CutsceneFadePayload263.java',
    'dev/settledlands/npc/CutsceneFadeControl263.java',
    'dev/settledlands/npc/client/CutsceneFadeOverlay263.java',
    'dev/settledlands/mixin/CutsceneGraphFadeDataMixin263.java',
    'dev/settledlands/mixin/CutsceneNodeFadeMixin263.java',
    'dev/settledlands/mixin/CutsceneGraphFadeUiMixin263.java',
    'dev/settledlands/mixin/CutsceneCameraFadeChainMixin263.java',
    'dev/settledlands/mixin/CutsceneCameraFadePlaybackMixin263.java',
]


def compile_sources(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> dict[str, bytes]:
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == ENGINE_SHA

    verify_tmp = work / 'verify-tmp'
    verify_tmp.mkdir()
    env = dict(os.environ, TMPDIR=str(verify_tmp))
    targets = [
        'dev/settledlands/npc/NpcDialogueGraph#save',
        'dev/settledlands/npc/NpcDialogueGraph#load(net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/client/NpcDialogueGraphScreen#widgets',
        'dev/settledlands/npc/CutsceneCamera#play(net.minecraft.server.level.ServerPlayer,java.lang.String)',
        'dev/settledlands/npc/CutsceneCamera#playChained(net.minecraft.server.level.ServerPlayer,java.lang.String)',
        'dev/settledlands/npc/NpcCopyModEvents#onRegisterPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent)',
        'dev/settledlands/npc/client/CutsceneOverlay#onGuiPost(net.neoforged.neoforge.client.event.RenderGuiEvent$Post)',
        'dev/settledlands/npc/CutscenePlayingPayload#register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent)',
    ]
    subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(base), *targets, '--java', str(java)],
        env=env, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    stubs = sorted(verify_tmp.glob('jvmverify-*'))
    assert len(stubs) == 1, stubs
    # jvm_verify emits bytecode-reference stubs, not a complete Java runtime/API.
    # Exclude Java platform and project classes to prevent module/package shadowing.
    for package in ('java', 'javax', 'jdk', 'sun', 'dev'):
        shutil.rmtree(stubs[0] / package, ignore_errors=True)

    out = work / 'classes'
    out.mkdir()
    sources = [ROOT / 'src/build263' / rel for rel in SOURCES]
    sources += sorted((ROOT / 'tools/api263').rglob('*.java'))
    classpath = os.pathsep.join((str(base), str(stubs[0])))
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(out), *map(str, sources)], check=True,
    )
    compiled = {}
    for rel in SOURCES:
        class_file = out / rel.replace('.java', '.class')
        assert class_file.is_file(), class_file
        compiled[class_file.relative_to(out).as_posix()] = class_file.read_bytes()
    inner = out / 'dev/settledlands/npc/CutsceneFadeControl263$SceneFade.class'
    assert inner.is_file()
    compiled[inner.relative_to(out).as_posix()] = inner.read_bytes()
    return compiled


def inject_before(raw: bytes, method: str, desc: str, selector, build, max_stack: int) -> bytes:
    k = classedit.Klass(raw)
    m = k.find_method(method, desc)
    assert m is not None
    parts = classedit.code_parts(k, m)
    pc = selector(k, parts['code'])
    inserted = build(k)
    assert pc not in decode.targets(parts['code']), (method, pc, 'insertion would bypass a branch target')
    parts = classedit.insert(parts, pc, inserted)
    parts['max_stack'] = max(parts['max_stack'], max_stack)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_guide_dialogue(raw: bytes) -> bytes:
    desc = '(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I'

    def selector(k, code):
        hits = []
        walked = decode.walk(code)
        for index, (pc, ins) in enumerate(walked):
            if ins[0] not in (0xB8, 0xB6, 0xB9):
                continue
            ref = constant_ref(k, int.from_bytes(ins[1:3], 'big'))
            if ref == (CUTSCENE_CAMERA, 'play', '(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z'):
                assert index >= 3
                assert walked[index - 3][1] == b'\x2a'       # ServerPlayer local 0
                assert walked[index - 2][1] == b'\x19\x11' # cutscene Node local 17
                previous = walked[index - 1][1]
                assert previous[0] == 0xB4                 # Node.cutsceneName
                field = constant_ref(k, int.from_bytes(previous[1:3], 'big'))
                assert field == ('dev/settledlands/npc/NpcDialogueGraph$Node', 'cutsceneName', 'Ljava/lang/String;')
                hits.append(pc)
        assert len(hits) == 1, hits
        return hits[0]

    def build(k):
        call = k.add_methodref(
            'dev/settledlands/npc/CutsceneFadeControl263', 'prepareInitial',
            '(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;'
            'Ldev/settledlands/npc/NpcDialogueGraph$Node;)V')
        return b'\x2a\x19\x04\x19\x11\xb8' + call.to_bytes(2, 'big')

    return inject_before(raw, 'resolveExecutionTarget', desc, selector, build, max_stack=12)


def patch_payload_registration(raw: bytes) -> bytes:
    desc = '(Lnet/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent;)V'

    def selector(k, code):
        returns = [pc for pc, ins in decode.walk(code) if ins == b'\xb1']
        assert len(returns) == 1, returns
        return returns[0]

    def build(k):
        call = k.add_methodref('dev/settledlands/npc/CutsceneFadePayload263', 'register', desc)
        return b'\x2a\xb8' + call.to_bytes(2, 'big')

    return inject_before(raw, 'onRegisterPayloads', desc, selector, build, max_stack=4)


def patch_json_language(raw: bytes, translations: dict[str, str]) -> bytes:
    obj = json.loads(raw.decode('utf-8'))
    for key, value in translations.items():
        assert key not in obj
        obj[key] = value
    return (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def patch_entries(entries: dict[str, bytes], compiled: dict[str, bytes]) -> dict[str, bytes]:
    updated = dict(entries)
    updated.update(compiled)
    updated['dev/settledlands/npc/GuideDialogue.class'] = patch_guide_dialogue(
        entries['dev/settledlands/npc/GuideDialogue.class'])
    updated['dev/settledlands/npc/NpcCopyModEvents.class'] = patch_payload_registration(
        entries['dev/settledlands/npc/NpcCopyModEvents.class'])

    mixin_name = 'settledlands.mixins.json'
    config = json.loads(entries[mixin_name].decode('utf-8'))
    common = ('CutsceneGraphFadeDataMixin263', 'CutsceneNodeFadeMixin263', 'CutsceneCameraFadeChainMixin263')
    client = ('CutsceneGraphFadeUiMixin263', 'CutsceneCameraFadePlaybackMixin263')
    for name in common:
        assert name not in config['mixins']
        config['mixins'].append(name)
    for name in client:
        assert name not in config['client']
        config['client'].append(name)
    updated[mixin_name] = (json.dumps(config, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

    updated['assets/settledlands/lang/ru_ru.json'] = patch_json_language(
        entries['assets/settledlands/lang/ru_ru.json'], {
            'gui.settledlands.graph.cutscene_fade_out_on': 'Затемнить при старте: ВКЛ',
            'gui.settledlands.graph.cutscene_fade_out_off': 'Затемнить при старте: ВЫКЛ',
            'gui.settledlands.graph.cutscene_fade_in_on': 'Проявить после затемнения: ВКЛ',
            'gui.settledlands.graph.cutscene_fade_in_off': 'Проявить после затемнения: ВЫКЛ',
        })
    updated['assets/settledlands/lang/en_us.json'] = patch_json_language(
        entries['assets/settledlands/lang/en_us.json'], {
            'gui.settledlands.graph.cutscene_fade_out_on': 'Fade to black: ON',
            'gui.settledlands.graph.cutscene_fade_out_off': 'Fade to black: OFF',
            'gui.settledlands.graph.cutscene_fade_in_on': 'Fade from black: ON',
            'gui.settledlands.graph.cutscene_fade_in_off': 'Fade from black: OFF',
        })

    toml_name = 'META-INF/neoforge.mods.toml'
    text = entries[toml_name].decode('utf-8')
    old_version = f'version="{BASE_VERSION}"'
    marker = "description='''"
    assert text.count(old_version) == 1 and text.count(marker) == 1
    text = text.replace(old_version, f'version="{NEW_VERSION}"', 1).replace(
        marker, marker + CHANGELOG + '\n\n', 1)
    assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
    updated[toml_name] = text.encode('utf-8')
    updated['settledlands-patch-info.txt'] = (
        PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode('utf-8')
    ).encode('utf-8')
    expected_changed = {
        'dev/settledlands/npc/GuideDialogue.class',
        'dev/settledlands/npc/NpcCopyModEvents.class',
        'settledlands.mixins.json',
        'assets/settledlands/lang/ru_ru.json',
        'assets/settledlands/lang/en_us.json',
        'META-INF/neoforge.mods.toml',
        'settledlands-patch-info.txt',
    }
    changed = {name for name in entries if updated[name] != entries[name]}
    added = set(updated) - set(entries)
    assert changed == expected_changed, (changed, expected_changed)
    assert len(added) == len(compiled) and added == set(compiled)
    return updated


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', type=pathlib.Path, default=ROOT / BASE_NAME)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    a = ap.parse_args()
    base, out, java = a.base.resolve(), a.out.resolve(), a.java.resolve()
    assert base.name == BASE_NAME and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-263-compile-') as td:
        compiled = compile_sources(java, base, pathlib.Path(td))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            updated = patch_entries(original, compiled)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    target.writestr(info, updated[info.filename])
                for name in sorted(set(updated) - set(original)):
                    info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    target.writestr(info, updated[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    print('wrote', out, 'bytes=', out.stat().st_size,
          'sha256=', hashlib.sha256(out.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
