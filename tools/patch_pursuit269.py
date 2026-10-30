#!/usr/bin/env python3
"""Build 269 pursuit implementation, rebuilt from the user-confirmed Build 268 baseline."""
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

BASE_NAME = 'settledlands-0.8.0-build268-profession-skin-fix.jar'
BASE_SHA = 'bc4f01b8461ef36ab4d6a8cda35aaf10967c9becdc07c8f6ccba127aa39f1ea5'
BASE_VERSION = '0.8.0-build268-profession-skin-fix'
NEW_VERSION = '0.8.0-build269-pursuit'
MODEL_NAME = 'ivy_oleander_professions.bbmodel'
MODEL_ASSET = 'assets/settledlands/dragon_default/' + MODEL_NAME
MODEL_SOURCE = ROOT / 'assets/settledlands/dragon_default' / MODEL_NAME
MODEL_SHA = '5c4a89e36e005d3af416c9d73755e09634a1c8553c18330ef2a245ef07953da0'
ECJ_SHA = '74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'

SOURCES = [
    'dev/settledlands/npc/NpcPursuit269.java',
    'dev/settledlands/mixin/NpcPursuitActionMixin269.java',
    'dev/settledlands/mixin/NpcPursuitAiMixin269.java',
    'dev/settledlands/mixin/NpcPursuitGraphUiMixin269.java',
    'dev/settledlands/mixin/ScreenAccessorPursuit269.java',
]

CHANGED = {
    'settledlands.mixins.json',
    'assets/settledlands/lang/ru_ru.json',
    'assets/settledlands/lang/en_us.json',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}
ADDED = {
    'dev/settledlands/npc/NpcPursuit269.class',
    'dev/settledlands/npc/NpcPursuit269$State.class',
    'dev/settledlands/npc/NpcPursuit269$TargetCache.class',
    'dev/settledlands/npc/NpcPursuit269$SlotCache.class',
    'dev/settledlands/mixin/NpcPursuitActionMixin269.class',
    'dev/settledlands/mixin/NpcPursuitAiMixin269.class',
    'dev/settledlands/mixin/NpcPursuitGraphUiMixin269.class',
    'dev/settledlands/mixin/ScreenAccessorPursuit269.class',
}
CHANGELOG = (
    'Build 269: 0.8.0-build269-pursuit adds separate graph commands to start or stop following an NPC identifier or player name, with persisted state and ordered row formation. '
    'Retains the user-confirmed Build 268 profession skin fix; Build 263 cutscene fades are not included.'
)
PATCHINFO = (
    'Сборка 269 от подтверждённого Build268: добавлены отдельные команды графа для начала преследования NPC по идентификатору или игрока по нику и для прекращения; состояние сохраняется, преследователи идут упорядоченными рядами. '
    'Исправление профессии/скина Build268 сохранено; CUTSCENE fade Build263 не включён.'
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
        *sorted((ROOT / 'tools/api269/org/spongepowered').rglob('*.java')),
        ROOT / 'tools/api269/net/minecraft/nbt/CompoundTag.java',
        ROOT / 'tools/api269/net/minecraft/nbt/ListTag.java',
        ROOT / 'tools/api269/net/minecraft/client/gui/Font.java',
        ROOT / 'tools/api269/net/minecraft/client/gui/GuiGraphics.java',
        ROOT / 'tools/api269/net/minecraft/network/chat/Component.java',
        ROOT / 'tools/api269/net/minecraft/network/chat/MutableComponent.java',
        ROOT / 'tools/api269/com/mojang/blaze3d/vertex/PoseStack.java',
        ROOT / 'tools/api269/net/minecraft/world/entity/Entity.java',
        ROOT / 'tools/api269/net/minecraft/world/entity/LivingEntity.java',
        ROOT / 'tools/api269/net/minecraft/world/entity/PathfinderMob.java',
        ROOT / 'tools/api269/net/minecraft/world/entity/ai/navigation/PathNavigation.java',
        ROOT / 'tools/api269/net/minecraft/world/level/Level.java',
        ROOT / 'tools/api269/net/minecraft/server/MinecraftServer.java',
        ROOT / 'tools/api269/net/minecraft/server/level/ServerLevel.java',
        ROOT / 'tools/api269/net/minecraft/server/level/ServerPlayer.java',
        ROOT / 'tools/api269/net/minecraft/world/phys/Vec3.java',
        ROOT / 'tools/api269/net/minecraft/resources/ResourceKey.java',
        ROOT / 'tools/api269/net/minecraft/resources/ResourceLocation.java',
    ]
    classpath = os.pathsep.join((str(base), str(stubs)))
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(api_out), *map(str, api_files)], check=True,
    )
    out = work / 'classes'
    out.mkdir()
    sources = [ROOT / 'src/build269' / rel for rel in SOURCES]
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
    assert set(compiled) == ADDED, (set(compiled) ^ ADDED)
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

    config_name = 'settledlands.mixins.json'
    config = json.loads(entries[config_name].decode('utf-8'))
    action_mixin = 'NpcPursuitActionMixin269'
    ai_mixin = 'NpcPursuitAiMixin269'
    ui_mixin = 'NpcPursuitGraphUiMixin269'
    screen_accessor = 'ScreenAccessorPursuit269'
    assert action_mixin not in config['mixins'] and action_mixin not in config['client']
    assert ai_mixin not in config['mixins'] and ai_mixin not in config['client']
    assert ui_mixin not in config['mixins'] and ui_mixin not in config['client']
    assert screen_accessor not in config['mixins'] and screen_accessor not in config['client']
    config['mixins'].append(action_mixin)
    config['mixins'].append(ai_mixin)
    config['client'].append(ui_mixin)
    config['client'].append(screen_accessor)
    updated[config_name] = (json.dumps(config, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

    ru = {
        'gui.settledlands.graph.pursuit_commands': 'Команды преследования',
        'gui.settledlands.graph.pursuit_start_npc': 'Начать: NPC по идентификатору',
        'gui.settledlands.graph.pursuit_start_player': 'Начать: игрок по нику',
        'gui.settledlands.graph.pursuit_stop': 'Прекратить преследование',
    }
    en = {
        'gui.settledlands.graph.pursuit_commands': 'Pursuit commands',
        'gui.settledlands.graph.pursuit_start_npc': 'Start: NPC identifier',
        'gui.settledlands.graph.pursuit_start_player': 'Start: player name',
        'gui.settledlands.graph.pursuit_stop': 'Stop pursuit',
    }
    updated['assets/settledlands/lang/ru_ru.json'] = patch_lang(entries['assets/settledlands/lang/ru_ru.json'], ru)
    updated['assets/settledlands/lang/en_us.json'] = patch_lang(entries['assets/settledlands/lang/en_us.json'], en)

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
    parser.add_argument('--out', type=pathlib.Path, default=ROOT / 'settledlands-0.8.0-build269-pursuit.jar')
    parser.add_argument('--java', type=pathlib.Path, required=True)
    args = parser.parse_args()
    base, out, java = (p.resolve() for p in (args.base, args.out, args.java))
    assert base.name == BASE_NAME and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    assert java.is_file()
    with tempfile.TemporaryDirectory(prefix='settledlands-269-compile-') as td:
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
    print(f'wrote {out} bytes={out.stat().st_size} sha256={hashlib.sha256(out.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
