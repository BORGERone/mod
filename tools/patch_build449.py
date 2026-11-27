"""Build 449: Named Trial Mob Pools (config/settledlands/mob_pools/*.txt), shared Trial Reward
Pools (loot_pools/*.txt) with LootPoolEditorScreen440 + CustomBook443, compact dropdown+gear
inspectors for Mob & Reward modules, and a 3D Modpack Mob Catalog with live entity previews."""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent

BASE_VERSION = "0.8.0-build448-card-descriptions-and-hover-tooltips"
NEW_VERSION = "0.8.0-build449-named-trial-pools-and-3d-mob-catalog"
OUT_NAME = "settledlands-0.8.0-build449-named-trial-pools-and-3d-mob-catalog.jar"

STUB_LAYERS = [
    ROOT / "tools" / "api271",
    ROOT / "build303-src" / "stubs",
    ROOT / "build303-src" / "stubs345-src",
    ROOT / "build303-src" / "stubs396-src",
    ROOT / "build303-src" / "stubs420-src",
    ROOT / "build303-src" / "api-src",
    ROOT / "build303-src" / "api3-src",
    ROOT / "build303-src" / "api4-src",
    ROOT / "build303-src" / "api5-src",
    ROOT / "build303-src" / "api416-src",
    ROOT / "build303-src" / "stubs425-src",
    ROOT / "build303-src" / "stubs436-src",
    ROOT / "build303-src" / "stubs438-src",
    ROOT / "build303-src" / "stubs439-src",
    ROOT / "build303-src" / "stubs440-src",
    ROOT / "build303-src" / "stubs443-src",
    ROOT / "build303-src" / "stubs446-src",
    ROOT / "build303-src" / "stubs449-src",
]

SOURCE_FILES = [
    "dev/settledlands/chest/ChestLootNet436.java",
    "dev/settledlands/trial/TrialMobPool449.java",
    "dev/settledlands/trial/TrialMobPoolManager449.java",
    "dev/settledlands/trial/TrialMobPoolNet449.java",
    "dev/settledlands/trial/TrialModule447.java",
    "dev/settledlands/trial/TrialGraph446.java",
    "dev/settledlands/npc/client/TrialMobPreview449.java",
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.java",
    "dev/settledlands/npc/client/TrialMobPoolEditorScreen449.java",
    "dev/settledlands/npc/client/LootPoolGearButton440.java",
    "dev/settledlands/npc/client/LootPoolCard436.java",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.java",
    "dev/settledlands/npc/client/TrialModuleClient447.java",
    "dev/settledlands/npc/client/TrialModuleCard447.java",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.java",
    "dev/settledlands/npc/client/GraphTooltips448.java",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.java",
]

MARKERS = {
    "dev/settledlands/trial/TrialMobPool449.class": ("mob_pool_", "rollWavePreview"),
    "dev/settledlands/trial/TrialModule447.class": ("mobs_pool:", "rewards_pool:"),
    "dev/settledlands/trial/TrialMobPoolManager449.class": ("mob_pools",),
    "dev/settledlands/trial/TrialMobPoolNet449.class": ("sendPoolSave", "sendPools"),
    "dev/settledlands/npc/client/TrialMobPreview449.class": ("renderEntityInInventoryFollowsMouse",),
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.class": ("gui.settledlands.trial.mob_cat.title",),
    "dev/settledlands/npc/client/TrialMobPoolEditorScreen449.class": ("gui.settledlands.trial.mob_pool.editor_title",),
    "dev/settledlands/npc/client/LootPoolCard436.class": ("trackTrialMobDropdown", "trackTrialRewardDropdown"),
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.class": ("mobPoolGearButton", "rewardPoolGearButton"),
}

LANG_SET_RU = {
    # Fix 6 missing gui.settledlands.trial.cfg.* keys from Build 447/448
    "gui.settledlands.trial.cfg.ominous_equip": "Зловещая броня: %s",
    "gui.settledlands.trial.cfg.nobreak": "Защита от ломания: %s",
    "gui.settledlands.trial.cfg.destruct": "Саморазрушение: %s",
    "gui.settledlands.trial.cfg.nobreak_r": "Радиус защиты (блоки)",
    "gui.settledlands.trial.cfg.nobreak_s": "Длительность защиты (сек)",
    "gui.settledlands.trial.cfg.nobreak_amp": "Уровень утомления (0-3)",

    # Trial Mob Pool (inspector, dropdown, editor)
    "gui.settledlands.trial.mob_pool.file": "Файл пула мобов:",
    "gui.settledlands.trial.mob_pool.create_new": "<Создать новый...>",
    "gui.settledlands.trial.mob_pool.edit_btn_tip": "Открыть визуальный редактор пула мобов (с 3D-каталогом)",
    "gui.settledlands.trial.mob_pool.create_row_tip": "Создать новый именованный пул мобов",
    "gui.settledlands.trial.mob_pool.edit_row_tip": "Редактировать пул «%s»",
    "gui.settledlands.trial.mob_pool.inspector_status": "Состав: %s (%s)",
    "gui.settledlands.trial.mob_pool.folder_hint": "Папка: mob_pools/",
    "gui.settledlands.trial.mob_pool.editor_title": "Редактор пула мобов",
    "gui.settledlands.trial.mob_pool.delete_tip": "Удалить файл пула мобов на сервере (нажмите дважды)",
    "gui.settledlands.trial.mob_pool.switch_tip": "Выбрать другой сохранённый пул мобов",
    "gui.settledlands.trial.mob_pool.no_pools": "Пока нет сохранённых пулов",
    "gui.settledlands.trial.mob_pool.new_tip": "Создать новый файл пула мобов",
    "gui.settledlands.trial.mob_pool.add_catalog": "+ Каталог мобов",
    "gui.settledlands.trial.mob_pool.add_catalog_tip": "Открыть 3D-каталог всех мобов сборки с поиском и фильтрами",
    "gui.settledlands.trial.mob_pool.test_wave": "Тест волны",
    "gui.settledlands.trial.mob_pool.test_wave_tip": "Смоделировать случайный состав одной волны из этого пула",
    "gui.settledlands.trial.mob_pool.new_hint": "Новый пул: отредактируйте мобов и нажмите «Сохранить».",
    "gui.settledlands.trial.mob_pool.summary": "Мобы в пуле: %s   |   Суммарный вес: %s",
    "gui.settledlands.trial.mob_pool.col_mob": "Моб (нажмите для выбора в 3D-каталоге)",
    "gui.settledlands.trial.mob_pool.err_empty": "В пуле мобов должна быть хотя бы одна строка.",
    "gui.settledlands.trial.mob_pool.change_mob_tip": "%s — нажмите, чтобы заменить моба из 3D-каталога",
    "gui.settledlands.trial.mob_pool.wave_prefix": "Тест волны: %s",

    # 3D Modpack Mob Catalog
    "gui.settledlands.trial.mob_cat.title": "3D-каталог мобов (%s)",
    "gui.settledlands.trial.mob_cat.title_replace": "Выбор моба (%s)",
    "gui.settledlands.trial.mob_cat.search_hint": "Поиск по имени, моду или ID (minecraft:breeze)...",
    "gui.settledlands.trial.mob_cat.filter_all": "Категория: Все",
    "gui.settledlands.trial.mob_cat.filter_hostile": "Категория: Враги",
    "gui.settledlands.trial.mob_cat.filter_friendly": "Категория: Мирные+",
    "gui.settledlands.trial.mob_cat.filter_tip": "Переключать: Все / Враждебные / Мирные и прочие",
    "gui.settledlands.trial.mob_cat.add_id_tip": "Добавить введённый в поиске ID (modid:entity) напрямую",
    "gui.settledlands.trial.mob_cat.bad_id": "Введите корректный ID вида modid:entity_id.",
    "gui.settledlands.trial.mob_cat.added": "+ Добавлен: %s (продолжайте кликать или нажмите Назад)",
    "gui.settledlands.trial.mob_cat.empty": "Ни одного моба по запросу не найдено.",
    "gui.settledlands.trial.mob_cat.hint_multi": "Клик — выбрать  ·  Shift+клик — добавить сразу нескольких мобов",
    "gui.settledlands.trial.mob_cat.hint_replace": "Нажмите на карточку моба, чтобы поставить его в строку",
    "gui.settledlands.trial.mob_cat.tip_action": "ЛКМ — выбрать (Shift+ЛКМ — добавить ещё)",
    "gui.settledlands.trial.mob_cat.cat_hostile": "Враг",
    "gui.settledlands.trial.mob_cat.cat_friendly": "Мирный",
    "gui.settledlands.trial.mob_cat.cat_water": "Водн.",
    "gui.settledlands.trial.mob_cat.cat_ambient": "Лет.",
    "gui.settledlands.trial.mob_cat.cat_misc": "Проч.",

    # Trial Reward Pool (inspector + linked LootPoolEditorScreen440)
    "gui.settledlands.trial.reward_pool.file": "Файл пула наград:",
    "gui.settledlands.trial.reward_pool.edit_btn_tip": "Открыть редактор пула наград (предметы, книги, веса и шансы)",
    "gui.settledlands.trial.reward_pool.inspector_status": "Таблица: %s (%s)",
    "gui.settledlands.trial.reward_pool.folder_hint": "Папка: loot_pools/",
    "gui.settledlands.trial.reward_pool.linked_banner": "Привязан к модулю «Пул наград» #%s (выпадает после волн без ограничений сундука)",

    # Trial module card & summary helpers
    "gui.settledlands.trial.mod.pool_inline": "встроенный",
    "gui.settledlands.trial.mod.more_entries": "...и ещё %s",
    "gui.settledlands.trial.mod.card_pool_file": "Пул: %s (%s)",
    "gui.settledlands.trial.mod.card_mob_inline": "Встроенный (%s мобов)",
    "gui.settledlands.trial.mod.card_rew_inline": "Встроенный (%s наград)",

    # >2s tooltips for Build 449 widgets & dropdowns
    "gui.settledlands.tip448.drop.mob_pool": "Выберите сохранённый пул мобов из mob_pools/*.txt, «<Создать новый...>» или нажмите шестерёнку в строке для открытия 3D-редактора.",
    "gui.settledlands.tip448.drop.reward_pool": "Выберите общий файл наград из loot_pools/*.txt (поддерживаются предметы и кастомные книги) или нажмите шестерёнку в строке.",
    "gui.settledlands.tip448.w.mob_pool_gear": "Открывает визуальный 3D-редактор пула мобов с живыми модельками, весами и тестом волны.",
    "gui.settledlands.tip448.w.reward_pool_gear": "Открывает визуальный редактор пула наград (предметы, кастомные книги, количество и шансы %).",
    "gui.settledlands.tip448.mob_pool.create_item": "Создаёт новый именованный файл пула мобов в config/settledlands/mob_pools/.",
    "gui.settledlands.tip448.mob_pool.pick_item": "Привязывает этот файл пула мобов к модулю (или нажмите шестерёнку справа для редактирования).",
    "gui.settledlands.tip448.reward_pool.create_item": "Создаёт новый именованный файл пула наград в config/settledlands/loot_pools/.",
    "gui.settledlands.tip448.reward_pool.pick_item": "Привязывает этот файл пула наград к модулю (или нажмите шестерёнку справа для редактирования).",
}

LANG_SET_EN = {
    "gui.settledlands.trial.cfg.ominous_equip": "Ominous Gear: %s",
    "gui.settledlands.trial.cfg.nobreak": "No-Break Protection: %s",
    "gui.settledlands.trial.cfg.destruct": "Self-Destruct: %s",
    "gui.settledlands.trial.cfg.nobreak_r": "Protection Radius (blocks)",
    "gui.settledlands.trial.cfg.nobreak_s": "Protection Duration (sec)",
    "gui.settledlands.trial.cfg.nobreak_amp": "Mining Fatigue Level (0-3)",

    "gui.settledlands.trial.mob_pool.file": "Mob pool file:",
    "gui.settledlands.trial.mob_pool.create_new": "<Create new...>",
    "gui.settledlands.trial.mob_pool.edit_btn_tip": "Open visual mob pool editor (with 3D mob catalog)",
    "gui.settledlands.trial.mob_pool.create_row_tip": "Create a new named mob pool",
    "gui.settledlands.trial.mob_pool.edit_row_tip": "Edit pool «%s»",
    "gui.settledlands.trial.mob_pool.inspector_status": "Pool: %s (%s)",
    "gui.settledlands.trial.mob_pool.folder_hint": "Folder: mob_pools/",
    "gui.settledlands.trial.mob_pool.editor_title": "Mob Pool Editor",
    "gui.settledlands.trial.mob_pool.delete_tip": "Delete this mob pool file on server (click twice)",
    "gui.settledlands.trial.mob_pool.switch_tip": "Switch to another saved mob pool",
    "gui.settledlands.trial.mob_pool.no_pools": "No saved pools yet",
    "gui.settledlands.trial.mob_pool.new_tip": "Create a new mob pool file",
    "gui.settledlands.trial.mob_pool.add_catalog": "+ Mob Catalog",
    "gui.settledlands.trial.mob_pool.add_catalog_tip": "Open searchable 3D catalog of all modpack mobs",
    "gui.settledlands.trial.mob_pool.test_wave": "Test Wave",
    "gui.settledlands.trial.mob_pool.test_wave_tip": "Simulate a random wave composition from this pool",
    "gui.settledlands.trial.mob_pool.new_hint": "New pool: edit mobs and click «Save».",
    "gui.settledlands.trial.mob_pool.summary": "Mobs in pool: %s   |   Total weight: %s",
    "gui.settledlands.trial.mob_pool.col_mob": "Mob (click to pick from 3D catalog)",
    "gui.settledlands.trial.mob_pool.err_empty": "Mob pool must contain at least one entry.",
    "gui.settledlands.trial.mob_pool.change_mob_tip": "%s — click to pick a mob from the 3D catalog",
    "gui.settledlands.trial.mob_pool.wave_prefix": "Wave roll: %s",

    "gui.settledlands.trial.mob_cat.title": "3D Mob Catalog (%s)",
    "gui.settledlands.trial.mob_cat.title_replace": "Pick Mob (%s)",
    "gui.settledlands.trial.mob_cat.search_hint": "Search by name, mod, or ID (minecraft:breeze)...",
    "gui.settledlands.trial.mob_cat.filter_all": "Category: All",
    "gui.settledlands.trial.mob_cat.filter_hostile": "Category: Hostile",
    "gui.settledlands.trial.mob_cat.filter_friendly": "Category: Friendly+",
    "gui.settledlands.trial.mob_cat.filter_tip": "Cycle filter: All / Hostile / Friendly & Misc",
    "gui.settledlands.trial.mob_cat.add_id_tip": "Add the entity ID typed in search box directly",
    "gui.settledlands.trial.mob_cat.bad_id": "Enter a valid entity ID like modid:entity_id.",
    "gui.settledlands.trial.mob_cat.added": "+ Added: %s (keep clicking or press Back)",
    "gui.settledlands.trial.mob_cat.empty": "No mobs matched your search/filter.",
    "gui.settledlands.trial.mob_cat.hint_multi": "Click — select  ·  Shift+click — add multiple mobs without closing",
    "gui.settledlands.trial.mob_cat.hint_replace": "Click any mob card to assign it to the selected row",
    "gui.settledlands.trial.mob_cat.tip_action": "Click — select (Shift+Click — keep adding)",
    "gui.settledlands.trial.mob_cat.cat_hostile": "Hostile",
    "gui.settledlands.trial.mob_cat.cat_friendly": "Friendly",
    "gui.settledlands.trial.mob_cat.cat_water": "Water",
    "gui.settledlands.trial.mob_cat.cat_ambient": "Ambient",
    "gui.settledlands.trial.mob_cat.cat_misc": "Misc",

    "gui.settledlands.trial.reward_pool.file": "Reward pool file:",
    "gui.settledlands.trial.reward_pool.edit_btn_tip": "Open visual reward pool editor (items, books, weights & chances)",
    "gui.settledlands.trial.reward_pool.inspector_status": "Pool: %s (%s)",
    "gui.settledlands.trial.reward_pool.folder_hint": "Folder: loot_pools/",
    "gui.settledlands.trial.reward_pool.linked_banner": "Linked to module «Reward Pool» #%s (drops after waves without chest limits)",

    "gui.settledlands.trial.mod.pool_inline": "inline",
    "gui.settledlands.trial.mod.more_entries": "...and %s more",
    "gui.settledlands.trial.mod.card_pool_file": "Pool: %s (%s)",
    "gui.settledlands.trial.mod.card_mob_inline": "Inline (%s mobs)",
    "gui.settledlands.trial.mod.card_rew_inline": "Inline (%s rewards)",

    "gui.settledlands.tip448.drop.mob_pool": "Pick a saved mob pool from mob_pools/*.txt, «<Create new...>», or click a row's gear icon to open the 3D editor.",
    "gui.settledlands.tip448.drop.reward_pool": "Pick a shared reward pool from loot_pools/*.txt (supports items & custom books) or click a row's gear icon.",
    "gui.settledlands.tip448.w.mob_pool_gear": "Opens the visual 3D mob pool editor with live entity previews, weights, and wave rolling.",
    "gui.settledlands.tip448.w.reward_pool_gear": "Opens the visual reward pool editor (items, custom books, counts, and % chances).",
    "gui.settledlands.tip448.mob_pool.create_item": "Creates a new named mob pool file in config/settledlands/mob_pools/.",
    "gui.settledlands.tip448.mob_pool.pick_item": "Binds this mob pool file to the module (or click the gear icon on the right to edit it).",
    "gui.settledlands.tip448.reward_pool.create_item": "Creates a new named reward pool file in config/settledlands/loot_pools/.",
    "gui.settledlands.tip448.reward_pool.pick_item": "Binds this reward pool file to the module (or click the gear icon on the right to edit it).",
}

CHANGELOG_EN = (
    "Build 449: Named Trial Mob Pools (config/settledlands/mob_pools/*.txt) & Shared Trial Reward "
    "Pools (config/settledlands/loot_pools/*.txt) with compact dropdown+gear module inspectors, "
    "LootPoolEditorScreen440 + CustomBook443 integration, searchable 3D Modpack Mob Catalog with "
    "live InventoryScreen entity previews, wave rolling, and 6 missing trial config translations."
)

PATCHINFO = (
    "Settled Lands Build 449 — Named Trial Mob & Reward Pools + 3D Mob Catalog\n"
    "Base: 0.8.0-build448-card-descriptions-and-hover-tooltips\n"
)


def verify_lang_coverage() -> None:
    missing_ru = sorted(k for k in LANG_SET_EN if k not in LANG_SET_RU)
    missing_en = sorted(k for k in LANG_SET_RU if k not in LANG_SET_EN)
    if missing_ru or missing_en:
        raise SystemExit(f"lang parity error: missing_ru={missing_ru}, missing_en={missing_en}")


def find_java() -> str:
    venv_java = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
    if venv_java.is_file():
        return str(venv_java)
    try:
        import jdk4py  # type: ignore
        cand = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
        if cand.is_file():
            return str(cand)
    except Exception:
        pass
    found = shutil.which("java")
    if found:
        return found
    raise SystemExit("no java runtime found")


def compile_build449(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    src_root = ROOT / "build303-src" / "src"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        merged.mkdir()
        stubs_out.mkdir()
        for layer in STUB_LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(base_jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("stub compile failed:\n" + r1.stdout + r1.stderr)

        targets = [str(src_root / rel) for rel in SOURCE_FILES]
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{base_jar}:{stubs_out}", "-d", str(out_dir)] + targets,
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("build449 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def check_mixins_json(raw: bytes) -> None:
    doc = json.loads(raw.decode("utf-8"))
    client = doc.get("client", [])
    if not client or client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    if client[-1] != "GraphHover448Mixin":
        raise SystemExit("GraphHover448Mixin must remain last in client mixins!")
    if "TrialModuleInspector447Mixin" not in client:
        raise SystemExit("TrialModuleInspector447Mixin missing from client mixins!")


def main(argv):
    verify_lang_coverage()

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out449",
                    help="directory for compiled build 449 classes")
    ap.add_argument("--no-compile", action="store_true",
                    help="reuse existing --classes directory without running ECJ")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(args.classes)
    if not args.no_compile:
        print("compiling Build 449 sources via ECJ (-21)...")
        compile_build449(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = list(zf.namelist())
        blobs = {name: zf.read(name) for name in order}

    outer_stems = [rel[:-5] for rel in SOURCE_FILES]
    stale_removed = set()
    new_order = []
    for name in order:
        if any(name == stem + ".class" or name.startswith(stem + "$") for stem in outer_stems):
            stale_removed.add(name)
            blobs.pop(name, None)
        else:
            new_order.append(name)
    order = new_order

    compiled_map = {}
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        compiled_map[rel] = compiled.read_bytes()

    for rel, data in sorted(compiled_map.items()):
        for marker in MARKERS.get(rel, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (rel, marker))
        action = "replaced" if rel in stale_removed else "added   "
        print("  %s: %s (%d bytes)" % (action, rel, len(data)))
        order.append(rel)
        blobs[rel] = data

    check_mixins_json(blobs["settledlands.mixins.json"])

    for lp, entries in (
        ("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
        ("assets/settledlands/lang/en_us.json", LANG_SET_EN),
    ):
        if lp not in blobs:
            raise SystemExit("base jar missing %s" % lp)
        blobs[lp] = patch_lang(blobs[lp], entries)
        print("  updated : %s (%d keys updated)" % (lp, len(entries)))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    stale = 'displayName="Settled Lands %s"' % BASE_VERSION
    if stale not in toml:
        raise SystemExit("base toml has no displayName to bump")
    toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s" % NEW_VERSION)

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = PATCHINFO.encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zf.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    (outdir / (OUT_NAME + ".sha256sums.txt")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
