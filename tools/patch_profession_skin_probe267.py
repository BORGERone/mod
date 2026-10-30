#!/usr/bin/env python3
"""Build 267 diagnostic profession probe, rebuilt from Build 262 from exact Build 262; excludes Build 263 cutscene fades."""
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

BASE_NAME = 'settledlands-0.8.0-build262-smooth-npc-turning.jar'
BASE_SHA = '21fc8fa7c861c3652f020ce9bb10bac133ac253675084982e65f16cc734f2531'
BASE_VERSION = '0.8.0-build262-smooth-npc-turning'
NEW_VERSION = '0.8.0-build267-profession-skin-probe'
MODEL_NAME = 'ivy_oleander_professions.bbmodel'
MODEL_ASSET = 'assets/settledlands/dragon_default/' + MODEL_NAME
MODEL_SOURCE = ROOT / 'assets/settledlands/dragon_default' / MODEL_NAME
MODEL_SHA = '5c4a89e36e005d3af416c9d73755e09634a1c8553c18330ef2a245ef07953da0'
ECJ_SHA = '74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'

SOURCES = [
    'dev/settledlands/npc/NpcStatsGraph.java',
    'dev/settledlands/npc/NpcProfessionSkin264.java',
    'dev/settledlands/npc/client/NpcStatsGraphUi.java',
    'dev/settledlands/npc/client/NpcProfessionModel264.java',
    'dev/settledlands/mixin/GuideNpcProfessionSkinMixin264.java',
    'dev/settledlands/mixin/NpcProfessionModelStorageMixin264.java',
    'dev/settledlands/mixin/EarlyCorpseTextureMixin.java',
]

CHANGED = {
    'dev/settledlands/npc/NpcStatsGraph.class',
    'dev/settledlands/npc/client/NpcStatsGraphUi.class',
    'settledlands.mixins.json',
    'assets/settledlands/lang/ru_ru.json',
    'assets/settledlands/lang/en_us.json',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
    'dev/settledlands/mixin/EarlyCorpseTextureMixin.class',
}
ADDED = {
    'dev/settledlands/npc/NpcProfessionSkin264.class',
    'dev/settledlands/npc/client/NpcProfessionModel264.class',
    'dev/settledlands/mixin/GuideNpcProfessionSkinMixin264.class',
    'dev/settledlands/mixin/NpcProfessionModelStorageMixin264.class',
    MODEL_ASSET,
}
CHANGELOG = (
    'Build 267: retains Build 265 profession dropdown/texture logic, applies the selected profession path before applyProfile can exit exceptionally, and logs the application/model-file handoff. '
    'Based on Build 262; Build 263 cutscene fade transitions are not included.'
)
PATCHINFO = (
    'Сборка 267 от Build 262 (без переходов CUTSCENE): Build265 dropdown/skin logic; profession texture is pre-set at applyProfile HEAD as well as TAIL, with addressable diagnostic logs; '
    'профессия применяет текстуру к обычному НПС независимо от прежнего пути модели; настройки «муж»/«яп» сохранены.'
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
        *sorted((ROOT / 'tools/api264/org/spongepowered').rglob('*.java')),
        ROOT / 'tools/api264/net/minecraft/nbt/CompoundTag.java',
        ROOT / 'tools/api264/net/minecraft/nbt/ListTag.java',
        ROOT / 'tools/api264/net/minecraft/client/gui/Font.java',
        ROOT / 'tools/api264/net/minecraft/client/gui/GuiGraphics.java',
        ROOT / 'tools/api264/net/minecraft/network/chat/Component.java',
        ROOT / 'tools/api264/net/minecraft/network/chat/MutableComponent.java',
        ROOT / 'tools/api264/com/mojang/blaze3d/vertex/PoseStack.java',
        ROOT / 'tools/api264/net/minecraft/world/entity/Entity.java',
    ]
    classpath = os.pathsep.join((str(base), str(stubs)))
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(api_out), *map(str, api_files)], check=True,
    )
    out = work / 'classes'
    out.mkdir()
    sources = [ROOT / 'src/build267' / rel for rel in SOURCES]
    classpath = os.pathsep.join((str(api_out), str(base), str(stubs)))
    subprocess.run(
        [str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', classpath, '-d', str(out), *map(str, sources)], check=True,
    )
    compiled: dict[str, bytes] = {}
    for rel in SOURCES:
        class_file = out / rel.replace('.java', '.class')
        assert class_file.is_file(), class_file
        compiled[class_file.relative_to(out).as_posix()] = class_file.read_bytes()
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
    updated[MODEL_ASSET] = MODEL_SOURCE.read_bytes()

    config_name = 'settledlands.mixins.json'
    config = json.loads(entries[config_name].decode('utf-8'))
    common = 'GuideNpcProfessionSkinMixin264'
    client = 'NpcProfessionModelStorageMixin264'
    assert common not in config['mixins'] and common not in config['client']
    assert client not in config['mixins'] and client not in config['client']
    config['mixins'].append(common)
    config['client'].append(client)
    updated[config_name] = (json.dumps(config, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

    ru = {
        'gui.settledlands.stats.male': 'муж',
        'gui.settledlands.stats.japanese': 'яп',
    }
    ru.update({f'gui.settledlands.stats.profession.{i}': value for i, value in PROFESSIONS_RU.items()})
    en = {
        'gui.settledlands.stats.male': 'Male',
        'gui.settledlands.stats.japanese': 'JP',
    }
    en.update({f'gui.settledlands.stats.profession.{i}': value for i, value in PROFESSIONS_EN.items()})
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
    parser.add_argument('--out', type=pathlib.Path, default=ROOT / 'settledlands-0.8.0-build267-profession-skin-probe.jar')
    parser.add_argument('--java', type=pathlib.Path, required=True)
    args = parser.parse_args()
    base, out, java = (p.resolve() for p in (args.base, args.out, args.java))
    assert base.name == BASE_NAME and hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA
    assert java.is_file()
    assert MODEL_SOURCE.is_file() and hashlib.sha256(MODEL_SOURCE.read_bytes()).hexdigest() == MODEL_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-265-compile-') as td:
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
