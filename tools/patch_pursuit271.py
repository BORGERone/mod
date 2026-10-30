#!/usr/bin/env python3
"""Build 271 pursuit graph-node hotfix, rebuilt from Build270 after user-reported runtime crashes."""
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
sys.path.insert(0, str(ROOT / 'tools'))
import classedit

BASE_NAME = 'settledlands-0.8.0-build270-pursuit-hotfix.jar'
BASE_SHA = '3f63e5a0adf09d1c848c8661886e83f0606ffb50c5bf15a0d70976629fe02711'
BASE_VERSION = '0.8.0-build270-pursuit-hotfix'
NEW_VERSION = '0.8.0-build271-pursuit-node-hotfix'
MODEL_NAME = 'ivy_oleander_professions.bbmodel'
MODEL_ASSET = 'assets/settledlands/dragon_default/' + MODEL_NAME
MODEL_SOURCE = ROOT / 'assets/settledlands/dragon_default' / MODEL_NAME
MODEL_SHA = '5c4a89e36e005d3af416c9d73755e09634a1c8553c18330ef2a245ef07953da0'
ECJ_SHA = '74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'

SOURCES = [
    'dev/settledlands/npc/NpcPursuit269.java',
    'dev/settledlands/npc/client/PursuitGraphNodes271.java',
    'dev/settledlands/mixin/NpcPursuitGraphUiMixin271.java',
    'dev/settledlands/mixin/NpcPursuitContextMenuMixin271.java',
]

CHANGED = {
    'dev/settledlands/npc/NpcPursuit269.class',
    'settledlands.mixins.json',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
    'assets/settledlands/lang/ru_ru.json',
    'assets/settledlands/lang/en_us.json',
}
ADDED = {
    'dev/settledlands/npc/client/PursuitGraphNodes271.class',
    'dev/settledlands/npc/client/PursuitGraphNodes271$MenuPoint.class',
    'dev/settledlands/mixin/NpcPursuitGraphUiMixin271.class',
    'dev/settledlands/mixin/NpcPursuitContextMenuMixin271.class',
}
REMOVED = {
    'dev/settledlands/mixin/NpcPursuitGraphUiMixin269.class',
    'dev/settledlands/mixin/ScreenAccessorPursuit269.class',
}
CHANGELOG = (
    'Build 271 fixes the user-reported node-spawn crash, removes the unimplemented abstract Screen invoker, and adds dedicated connected pursuit actions to the graph creation menu. '
    'Inactive pursuit NBT now writes a false marker rather than linking CompoundTag.remove with an incorrect return descriptor. Pursuit and Build268 profession/skin features remain; Build263 fade remains excluded.'
)
PATCHINFO = (
    'Сборка 271 исправляет вылет при создании ноды, добавляет отдельные пункты графа преследования (начать: NPC, начать: игрок, прекратить) и исправляет сохранение данных НПС. Build269/270 не устанавливать; CUTSCENE fade Build263 не включён.'
)

PROFESSIONS_RU = {
    0: 'Без профессии', 1: 'Рыбак', 2: 'Пастух', 3: 'Страж', 4: 'Командир',
    5: 'Крестоносец', 6: 'Фермер', 7: 'Кузнец', 8: 'Торговец', 9: 'Селянин',
    10: 'Маг', 11: 'Целитель', 12: 'Шахтёр', 13: 'Лесоруб', 14: 'Охотник',
    15: 'Трактирщик', 16: 'Волшебник',
}
PROFESSIONS_EN = {
    0: 'No profession', 1: 'Fisher', 2: 'Shepherd', 3: 'Guard', 4: 'Commander',
    5: 'Crusader', 6: 'Farmer', 7: 'Blacksmith', 8: 'Merchant', 9: 'Villager',
    10: 'Mage', 11: 'Healer', 12: 'Miner', 13: 'Lumberjack', 14: 'Hunter',
    15: 'Innkeeper', 16: 'Wizard',
}


def compile_sources(java: pathlib.Path, base: pathlib.Path, work: pathlib.Path) -> dict[str, bytes]:
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == ECJ_SHA
    verify_tmp = work / 'verify-tmp'
    verify_tmp.mkdir()
    env = dict(os.environ, TMPDIR=str(verify_tmp))
    targets = [
        'dev/settledlands/npc/NpcDialogueGraph#save',
        'dev/settledlands/npc/NpcDialogueGraph#load(net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/NpcDialogueGraph$Node#copy',
        'dev/settledlands/npc/client/NpcDialogueGraphScreen#drawNode(net.minecraft.client.gui.GuiGraphics,dev.settledlands.npc.NpcDialogueGraph$Node)',
        'dev/settledlands/npc/client/NpcDialogueGraphScreen#mouseClicked(double,double,int)',
        'dev/settledlands/npc/NpcAutoAiEngine255#runMobilityAndAutoAi(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/GuideNpc#applyProfile',
        'dev/settledlands/model/BbModelStorage#findModelFile(java.lang.String)',
        'dev/settledlands/model/BbModelManager#getModel(java.lang.String)',
    ]
    subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(base), *targets, '--java', str(java)],
        env=env, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    stub_dirs = sorted(verify_tmp.glob('jvmverify-*'))
    assert len(stub_dirs) == 1, stub_dirs
    stubs = stub_dirs[0]
    for package in ('java', 'javax', 'jdk', 'sun', 'dev'):
        shutil.rmtree(stubs / package, ignore_errors=True)

    api_out = work / 'api-classes'
    api_out.mkdir()
    api_files = [
        *sorted((ROOT / 'tools/api271/org/spongepowered').rglob('*.java')),
        ROOT / 'tools/api271/net/minecraft/nbt/CompoundTag.java',
        ROOT / 'tools/api271/net/minecraft/nbt/ListTag.java',
        ROOT / 'tools/api271/net/minecraft/nbt/Tag.java',
        ROOT / 'tools/api271/net/minecraft/client/gui/Font.java',
        ROOT / 'tools/api271/net/minecraft/client/gui/GuiGraphics.java',
        ROOT / 'tools/api271/net/minecraft/network/chat/Component.java',
        ROOT / 'tools/api271/net/minecraft/network/chat/MutableComponent.java',
        ROOT / 'tools/api271/com/mojang/blaze3d/vertex/PoseStack.java',
        ROOT / 'tools/api271/net/minecraft/world/entity/Entity.java',
        ROOT / 'tools/api271/net/minecraft/world/entity/LivingEntity.java',
        ROOT / 'tools/api271/net/minecraft/world/entity/PathfinderMob.java',
        ROOT / 'tools/api271/net/minecraft/world/entity/ai/navigation/PathNavigation.java',
        ROOT / 'tools/api271/net/minecraft/world/level/Level.java',
        ROOT / 'tools/api271/net/minecraft/server/MinecraftServer.java',
        ROOT / 'tools/api271/net/minecraft/server/level/ServerLevel.java',
        ROOT / 'tools/api271/net/minecraft/server/level/ServerPlayer.java',
        ROOT / 'tools/api271/net/minecraft/world/phys/Vec3.java',
        ROOT / 'tools/api271/net/minecraft/resources/ResourceKey.java',
        ROOT / 'tools/api271/net/minecraft/resources/ResourceLocation.java',
    ]
    classpath = os.pathsep.join((str(base), str(stubs)))
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(api_out), *map(str, api_files)], check=True,
    )
    out = work / 'classes'
    out.mkdir()
    sources = [ROOT / 'src/build271' / rel for rel in SOURCES]
    classpath = os.pathsep.join((str(api_out), str(base), str(stubs)))
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(out), *map(str, sources)], check=True,
    )
    compiled: dict[str, bytes] = {}
    class_files = sorted(out.rglob('*.class'))
    assert class_files, 'no compiled classes'
    for class_file in class_files:
        compiled[class_file.relative_to(out).as_posix()] = class_file.read_bytes()
    expected = {rel.replace('.java', '.class') for rel in SOURCES}
    expected.update({
        'dev/settledlands/npc/client/PursuitGraphNodes271$MenuPoint.class',
        'dev/settledlands/npc/NpcPursuit269$State.class',
        'dev/settledlands/npc/NpcPursuit269$TargetCache.class',
        'dev/settledlands/npc/NpcPursuit269$SlotCache.class',
    })
    assert set(compiled) == expected, (set(compiled) ^ expected)
    return compiled


def patch_lang(raw: bytes, translations: dict[str, str]) -> bytes:
    data = json.loads(raw.decode('utf-8'))
    for key, value in translations.items():
        assert key not in data, key
        data[key] = value
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def patch_entries(entries: dict[str, bytes], compiled: dict[str, bytes]) -> dict[str, bytes]:
    updated = dict(entries)
    updated.update(compiled)
    for name in REMOVED:
        assert name in updated, name
        updated.pop(name)

    config_name = 'settledlands.mixins.json'
    mixins = json.loads(entries[config_name].decode('utf-8'))
    client = mixins['client']
    assert 'NpcPursuitGraphUiMixin269' in client
    assert 'ScreenAccessorPursuit269' in client
    client.remove('NpcPursuitGraphUiMixin269')
    client.remove('ScreenAccessorPursuit269')
    client.extend(['NpcPursuitGraphUiMixin271', 'NpcPursuitContextMenuMixin271'])
    updated[config_name] = (json.dumps(mixins, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

    translations = {
        'assets/settledlands/lang/ru_ru.json': {
            'gui.settledlands.graph.pursuit_menu_start_npc': 'Преследование — начать: NPC',
            'gui.settledlands.graph.pursuit_menu_start_player': 'Преследование — начать: игрок',
            'gui.settledlands.graph.pursuit_menu_stop': 'Преследование — прекратить',
            'gui.settledlands.graph.pursuit_node_start_npc': 'Преследование · Начать (NPC)',
            'gui.settledlands.graph.pursuit_node_start_player': 'Преследование · Начать (игрок)',
            'gui.settledlands.graph.pursuit_node_stop': 'Преследование · Прекратить',
        },
        'assets/settledlands/lang/en_us.json': {
            'gui.settledlands.graph.pursuit_menu_start_npc': 'Pursuit — start: NPC',
            'gui.settledlands.graph.pursuit_menu_start_player': 'Pursuit — start: player',
            'gui.settledlands.graph.pursuit_menu_stop': 'Pursuit — stop',
            'gui.settledlands.graph.pursuit_node_start_npc': 'Pursuit · Start (NPC)',
            'gui.settledlands.graph.pursuit_node_start_player': 'Pursuit · Start (player)',
            'gui.settledlands.graph.pursuit_node_stop': 'Pursuit · Stop',
        },
    }
    for name, values in translations.items():
        updated[name] = patch_lang(entries[name], values)

    toml_name = 'META-INF/neoforge.mods.toml'
    text = entries[toml_name].decode('utf-8')
    old = f'version="{BASE_VERSION}"'
    assert text.count(old) == 1
    text = text.replace(old, f'version="{NEW_VERSION}"')
    marker = "description='''"
    assert text.count(marker) == 1
    start = text.index(marker) + len(marker)
    end = text.index("'''", start)
    text = text[:start] + CHANGELOG + "\n\n" + text[start:end] + text[end:]
    updated[toml_name] = text.encode('utf-8')
    patch_info = entries['settledlands-patch-info.txt'].decode('utf-8')
    updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n\n' + patch_info).encode('utf-8')
    return updated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=pathlib.Path, default=ROOT / BASE_NAME)
    parser.add_argument('--out', type=pathlib.Path, default=ROOT / 'settledlands-0.8.0-build271-pursuit-node-hotfix.jar')
    parser.add_argument('--java', type=pathlib.Path, required=True)
    args = parser.parse_args()
    base, out, java = (p.resolve() for p in (args.base, args.out, args.java))
    assert base.name == BASE_NAME and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    assert java.is_file()
    with tempfile.TemporaryDirectory(prefix='settledlands-271-compile-') as td:
        compiled = compile_sources(java, base, pathlib.Path(td))
        with zipfile.ZipFile(base) as source:
            assert source.testzip() is None and len(source.namelist()) == len(set(source.namelist()))
            original = {info.filename: source.read(info.filename) for info in source.infolist()}
            updated = patch_entries(original, compiled)
            out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(out, 'w') as target:
                for info in source.infolist():
                    if info.filename in updated:
                        target.writestr(info, updated[info.filename])
                for name in sorted(set(updated) - set(original)):
                    info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    target.writestr(info, updated[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    print(f'wrote {out} bytes={out.stat().st_size} sha256={hashlib.sha256(out.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
