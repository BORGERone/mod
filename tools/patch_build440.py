#!/usr/bin/env python3
"""
Build 440 - In-Game Loot Pool Editor & Gear Edit Buttons.

Builds on Build 439 (``settledlands-0.8.0-build439-furniture-3d-loot-scatter.jar``):
  1. Gear-shaped edit-pool button next to Loot Pool selector rows:
       * ``LootPoolInspectorMixin436`` places a 20x20 ``LootPoolGearButton440`` directly beside
         the pool dropdown in the «Пул предметов» node inspector (zero synthetic lambdas).
       * ``LootPoolDropdownMixin440`` draws an inline 12x12 gear button on every row inside the
         expanded pool selector dropdown (``NpcUi$NpcDropdown``) and opens the editor on click.
  2. Full in-game Loot Pool editor window (``LootPoolEditorScreen440``):
       * Create, load, rename/save-as, edit, and delete ``config/settledlands/loot_pools/*.txt``.
       * Configure items, min-max counts, rarity, and weights with live drop-% calculation,
         node filter compatibility warning badges, and interactive Roll Preview with item icons.
  3. Searchable item catalog picker (``LootPoolItemPickerScreen440``):
       * Browse all registry items or player inventory items, add by typed ID, add all inventory
         items with stacks, or Shift-click to batch-add multiple items in one visit.
  4. Server <-> Client pool CRUD packets (``ChestLootNet436`` + ``LootPoolManager436`` + ``LootPool436``):
       * ``settledlands:loot_pool_query``, ``settledlands:loot_pool_data``, and
         ``settledlands:loot_pool_save`` gated on OP permissions (``hasPermissions(2)``) and strict
         filename sanitization, broadcasting updated ``settledlands:chest_loot_pools`` lists.
"""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

BASE_VERSION = "0.8.0-build439-furniture-3d-loot-scatter"
NEW_VERSION = "0.8.0-build440-loot-pool-editor-gui"
OUT_NAME = "settledlands-0.8.0-build440-loot-pool-editor-gui.jar"

ROOT = pathlib.Path(__file__).resolve().parent.parent

STUB_LAYERS = (
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
)

SOURCE_FILES = (
    "dev/settledlands/chest/LootPool436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/chest/ChestLootNet436.java",
    "dev/settledlands/npc/client/LootPoolGearButton440.java",
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.java",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.java",
    "dev/settledlands/npc/client/LootPoolCard436.java",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.java",
    "dev/settledlands/mixin/LootPoolDropdownMixin440.java",
)

INNER_SYNC = (
    "dev/settledlands/chest/LootPool436$Parsed.class",
    "dev/settledlands/chest/LootPool436$Range.class",
    "dev/settledlands/chest/LootPool436$PoolEntry.class",
    "dev/settledlands/chest/LootPool436$Drop.class",
    "dev/settledlands/chest/ChestLootNet436$Pools.class",
    "dev/settledlands/chest/ChestLootNet436$ScatterPoses.class",
)

REPLACED = (
    "dev/settledlands/chest/LootPool436.class",
    "dev/settledlands/chest/LootPoolManager436.class",
    "dev/settledlands/chest/ChestLootNet436.class",
    "dev/settledlands/npc/client/LootPoolCard436.class",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
)

ADDED = (
    "dev/settledlands/chest/ChestLootNet436$PoolQuery.class",
    "dev/settledlands/chest/ChestLootNet436$PoolData.class",
    "dev/settledlands/chest/ChestLootNet436$PoolSave.class",
    "dev/settledlands/npc/client/LootPoolGearButton440.class",
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$RowData.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$RowSlot.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$PreviewDrop.class",
    "dev/settledlands/npc/client/LootPoolCard436$TrackedDropdown.class",
    "dev/settledlands/mixin/LootPoolDropdownMixin440.class",
)

MARKERS = {
    "dev/settledlands/chest/LootPool436.class": (
        "normalizeSaveName", "formatPoolFile", "encodeEntriesWire", "passesSpec",
    ),
    "dev/settledlands/chest/LootPoolManager436.class": (
        "savePool", "deletePool", "loadPoolQuiet", "poolExists",
    ),
    "dev/settledlands/chest/ChestLootNet436.class": (
        "loot_pool_query", "loot_pool_data", "loot_pool_save", "sendPoolsToOperators",
    ),
    "dev/settledlands/npc/client/LootPoolGearButton440.class": (
        "drawGearIcon", "drawInlineGearFrame",
    ),
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.class": (
        "LootPoolItemPickerScreen440", "addAllFromInventory",
    ),
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class": (
        "LootPoolEditorScreen440", "handleServerData", "runTestRoll",
    ),
    "dev/settledlands/npc/client/LootPoolCard436.class": (
        "gearButton", "renderDropdownRowGears", "handleDropdownRowGearClick", "openPoolEditor",
    ),
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class": (
        "gearButton", "trackPoolDropdown",
    ),
    "dev/settledlands/mixin/LootPoolDropdownMixin440.class": (
        "settledlands$drawDropdownGear440", "settledlands$clickDropdownGear440",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.loot_pool.edit_btn_tip": "Редактировать пул предметов (открыть редактор)",
    "gui.settledlands.loot_pool.edit_row_tip": "Редактировать пул: %s",
    "gui.settledlands.loot_pool.create_row_tip": "Создать новый пул предметов",
    "gui.settledlands.loot_pool.editor.title": "Редактор пула предметов",
    "gui.settledlands.loot_pool.editor.close": "Закрыть",
    "gui.settledlands.loot_pool.editor.back": "Назад",
    "gui.settledlands.loot_pool.editor.file_lbl": "Файл:",
    "gui.settledlands.loot_pool.editor.switch_tip": "Переключиться на другой существующий пул",
    "gui.settledlands.loot_pool.editor.new_pool": "+ Новый",
    "gui.settledlands.loot_pool.editor.new_tip": "Создать новый пустой .txt файл пула",
    "gui.settledlands.loot_pool.editor.delete": "Удалить пул",
    "gui.settledlands.loot_pool.editor.delete_confirm": "Точно удалить?",
    "gui.settledlands.loot_pool.editor.delete_tip": "Удалить текущий файл пула с сервера",
    "gui.settledlands.loot_pool.editor.delete_warn": "Нажмите ещё раз, чтобы удалить %s",
    "gui.settledlands.loot_pool.editor.deleted": "Файл пула удалён: %s",
    "gui.settledlands.loot_pool.editor.col_item": "Предмет",
    "gui.settledlands.loot_pool.editor.col_count": "Кол-во",
    "gui.settledlands.loot_pool.editor.col_rarity": "Редк.",
    "gui.settledlands.loot_pool.editor.col_weight": "Вес",
    "gui.settledlands.loot_pool.editor.col_chance": "Шанс",
    "gui.settledlands.loot_pool.editor.remove_row": "Удалить строку из пула",
    "gui.settledlands.loot_pool.editor.add_item": "+ Добавить предмет",
    "gui.settledlands.loot_pool.editor.test": "Тест броска",
    "gui.settledlands.loot_pool.editor.test_tip": "Проверить случайное выпадение по текущим настройкам",
    "gui.settledlands.loot_pool.editor.save": "Сохранить",
    "gui.settledlands.loot_pool.editor.save_close": "Сохранить и закрыть",
    "gui.settledlands.loot_pool.editor.loading": "Загрузка пула с сервера...",
    "gui.settledlands.loot_pool.editor.empty_rows": "Пул пуст. Нажмите «+ Добавить предмет» ниже.",
    "gui.settledlands.loot_pool.editor.new_hint": "Новый пул: добавьте предметы и нажмите «Сохранить»",
    "gui.settledlands.loot_pool.editor.saved": "Сохранено: %s",
    "gui.settledlands.loot_pool.editor.err_name": "Укажите корректное имя файла (например dungeon_loot.txt)",
    "gui.settledlands.loot_pool.editor.err_row": "Проверьте предмет, кол-во, редкость и вес (>0) в строке %s",
    "gui.settledlands.loot_pool.editor.err_io": "Не удалось сохранить файл пула на сервере",
    "gui.settledlands.loot_pool.editor.err_too_big": "Пул слишком велик для одного пакета сохранения",
    "gui.settledlands.loot_pool.editor.summary": "Записей: %s | Сумм. вес: %s",
    "gui.settledlands.loot_pool.editor.node_caps": "Узел: Редк≤%s Вес≤%s Шт: %s",
    "gui.settledlands.loot_pool.editor.unknown_item": "Неизвестный предмет: %s (нажмите для замены)",
    "gui.settledlands.loot_pool.editor.filtered_tip": "Отсекается фильтром редкости/веса текущего узла графа",
    "gui.settledlands.loot_pool.editor.preview_lbl": "Тест:",
    "gui.settledlands.loot_pool.editor.preview_none": "Добавьте хотя бы один предмет для теста",
    "gui.settledlands.loot_pool.editor.preview_filtered": "Ничего не выпало (все предметы отсечены фильтром узла)",
    "gui.settledlands.loot_pool.editor.unsaved_warn": "Есть несохранённые изменения — нажмите «Закрыть» или Esc ещё раз",
    "gui.settledlands.loot_pool.editor.switch_warn": "Есть несохранённые изменения — кликните по %s ещё раз для переключения",
    "gui.settledlands.loot_pool.picker.title": "Выбор предмета в пул",
    "gui.settledlands.loot_pool.picker.replace_title": "Замена предмета в строке",
    "gui.settledlands.loot_pool.picker.search": "Поиск предмета",
    "gui.settledlands.loot_pool.picker.search_hint": "Поиск по названию или mod:id...",
    "gui.settledlands.loot_pool.picker.mode_all": "Все предметы",
    "gui.settledlands.loot_pool.picker.mode_inv": "Инвентарь",
    "gui.settledlands.loot_pool.picker.mode_tip": "Переключить: весь каталог игры / только ваш инвентарь",
    "gui.settledlands.loot_pool.picker.add_id": "+ По ID",
    "gui.settledlands.loot_pool.picker.add_id_tip": "Добавить ID из строки поиска (напр. minecraft:diamond)",
    "gui.settledlands.loot_pool.picker.add_inv_all": "+ Всё из инвентаря",
    "gui.settledlands.loot_pool.picker.empty": "Ничего не найдено",
    "gui.settledlands.loot_pool.picker.empty_inv": "В инвентаре нет подходящих предметов",
    "gui.settledlands.loot_pool.picker.hint_multi": "ЛКМ — добавить предмет · Shift+ЛКМ — добавить несколько подряд",
    "gui.settledlands.loot_pool.picker.hint_replace": "ЛКМ — заменить предмет в строке",
    "gui.settledlands.loot_pool.picker.added_multi": "Добавлено: %s (в пуле: %s)",
    "gui.settledlands.loot_pool.picker.inv_all_present": "Все предметы из инвентаря уже есть в этом пуле",
}

LANG_SET_EN = {
    "gui.settledlands.loot_pool.edit_btn_tip": "Edit item pool (open in-game editor)",
    "gui.settledlands.loot_pool.edit_row_tip": "Edit pool: %s",
    "gui.settledlands.loot_pool.create_row_tip": "Create new item pool",
    "gui.settledlands.loot_pool.editor.title": "Item Pool Editor",
    "gui.settledlands.loot_pool.editor.close": "Close",
    "gui.settledlands.loot_pool.editor.back": "Back",
    "gui.settledlands.loot_pool.editor.file_lbl": "File:",
    "gui.settledlands.loot_pool.editor.switch_tip": "Switch to another existing pool file",
    "gui.settledlands.loot_pool.editor.new_pool": "+ New",
    "gui.settledlands.loot_pool.editor.new_tip": "Create a new empty .txt pool file",
    "gui.settledlands.loot_pool.editor.delete": "Delete pool",
    "gui.settledlands.loot_pool.editor.delete_confirm": "Confirm delete?",
    "gui.settledlands.loot_pool.editor.delete_tip": "Delete this pool file from the server",
    "gui.settledlands.loot_pool.editor.delete_warn": "Click Delete again to remove %s",
    "gui.settledlands.loot_pool.editor.deleted": "Pool file deleted: %s",
    "gui.settledlands.loot_pool.editor.col_item": "Item",
    "gui.settledlands.loot_pool.editor.col_count": "Count",
    "gui.settledlands.loot_pool.editor.col_rarity": "Rarity",
    "gui.settledlands.loot_pool.editor.col_weight": "Weight",
    "gui.settledlands.loot_pool.editor.col_chance": "Chance",
    "gui.settledlands.loot_pool.editor.remove_row": "Remove item from pool",
    "gui.settledlands.loot_pool.editor.add_item": "+ Add item",
    "gui.settledlands.loot_pool.editor.test": "Test roll",
    "gui.settledlands.loot_pool.editor.test_tip": "Preview random loot drops using current entries",
    "gui.settledlands.loot_pool.editor.save": "Save",
    "gui.settledlands.loot_pool.editor.save_close": "Save & Close",
    "gui.settledlands.loot_pool.editor.loading": "Loading pool from server...",
    "gui.settledlands.loot_pool.editor.empty_rows": "Pool is empty. Click \"+ Add item\" below.",
    "gui.settledlands.loot_pool.editor.new_hint": "New pool: add items and click Save",
    "gui.settledlands.loot_pool.editor.saved": "Saved: %s",
    "gui.settledlands.loot_pool.editor.err_name": "Enter a valid file name (e.g. dungeon_loot.txt)",
    "gui.settledlands.loot_pool.editor.err_row": "Check item, count, rarity, and weight (>0) on row %s",
    "gui.settledlands.loot_pool.editor.err_io": "Failed to write pool file on server",
    "gui.settledlands.loot_pool.editor.err_too_big": "Pool is too large for one save packet",
    "gui.settledlands.loot_pool.editor.summary": "Entries: %s | Total weight: %s",
    "gui.settledlands.loot_pool.editor.node_caps": "Node: Rar≤%s Wgt≤%s Rolls: %s",
    "gui.settledlands.loot_pool.editor.unknown_item": "Unknown item: %s (click to replace)",
    "gui.settledlands.loot_pool.editor.filtered_tip": "Excluded by current graph node max rarity/weight filter",
    "gui.settledlands.loot_pool.editor.preview_lbl": "Roll:",
    "gui.settledlands.loot_pool.editor.preview_none": "Add at least one valid item to preview",
    "gui.settledlands.loot_pool.editor.preview_filtered": "No drops (all items excluded by node caps)",
    "gui.settledlands.loot_pool.editor.unsaved_warn": "Unsaved changes — click Close or press Esc again to discard",
    "gui.settledlands.loot_pool.editor.switch_warn": "Unsaved changes — click %s again to switch pools",
    "gui.settledlands.loot_pool.picker.title": "Select Item for Pool",
    "gui.settledlands.loot_pool.picker.replace_title": "Replace Item in Row",
    "gui.settledlands.loot_pool.picker.search": "Search item",
    "gui.settledlands.loot_pool.picker.search_hint": "Search by name or mod:id...",
    "gui.settledlands.loot_pool.picker.mode_all": "All Items",
    "gui.settledlands.loot_pool.picker.mode_inv": "Inventory",
    "gui.settledlands.loot_pool.picker.mode_tip": "Toggle between full item catalog and your inventory",
    "gui.settledlands.loot_pool.picker.add_id": "+ By ID",
    "gui.settledlands.loot_pool.picker.add_id_tip": "Add item ID from search box (e.g. minecraft:diamond)",
    "gui.settledlands.loot_pool.picker.add_inv_all": "+ All from Inv",
    "gui.settledlands.loot_pool.picker.empty": "No matching items",
    "gui.settledlands.loot_pool.picker.empty_inv": "No matching items in inventory",
    "gui.settledlands.loot_pool.picker.hint_multi": "Click — add item · Shift+Click — add multiple without closing",
    "gui.settledlands.loot_pool.picker.hint_replace": "Click — replace item in row",
    "gui.settledlands.loot_pool.picker.added_multi": "Added: %s (in pool: %s)",
    "gui.settledlands.loot_pool.picker.inv_all_present": "All inventory items are already in this pool",
}

CHANGELOG_EN = (
    "Build 440 adds gear-icon edit buttons beside Loot Pool selector rows (inspector + expanded "
    "dropdown rows) opening an in-game Loot Pool Editor window to create, edit, test-roll, and "
    "delete loot pools (items, min-max counts, rarity, weights) with server sync."
)
PATCHINFO = (
    "Сборка 440: встроенный игровой редактор пулов предметов и кнопки-шестерёнки редактирования.\n"
    "  * Возле выбора пула в инспекторе узла «Пул предметов» и на каждой строке раскрывающегося\n"
    "    списка пулов добавлена кнопка в виде шестерёнки;\n"
    "  * При нажатии открывается игровое окно «Редактор пула предметов»: настройка предметов,\n"
    "    количества (мин-макс), редкости, веса, живого % шанса, тест броска с иконками,\n"
    "    создание/переключение/удаление файлов .txt прямо в игре;\n"
    "  * Встроенный каталог предметов с поиском по названию/ID, вкладкой «Инвентарь» (перенос\n"
    "    стаков в пул в 1 клик) и быстрым мульти-добавлением через Shift+ЛКМ.\n"
)


def find_java():
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


def compile_build440(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build440 compile failed:\n" + r2.stdout + r2.stderr)


def patch_mixins(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    client = list(doc.get("client") or [])
    if "LootPoolDropdownMixin440" not in client:
        client.append("LootPoolDropdownMixin440")
    doc["client"] = client
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out440",
                    help="directory for compiled build 440 classes")
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
        print("compiling Build 440 sources via ECJ (-21)...")
        compile_build440(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in INNER_SYNC:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled inner class: %s" % src)
        if name not in blobs:
            raise SystemExit("base jar missing inner class: %s" % name)
        blobs[name] = src.read_bytes()
        print("  synced  : %s (%d bytes)" % (name, len(blobs[name])))

    for name in REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        if name not in blobs:
            raise SystemExit("base jar has no entry: %s" % name)
        if blobs[name] == data:
            raise SystemExit("%s is byte-identical to base - why is it in REPLACED?" % name)
        print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for name in ADDED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in blobs:
            raise SystemExit("%s already in base jar - put it in REPLACED" % name)
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        print("  added   : %s (%d bytes)" % (name, len(data)))
        order.append(name)
        blobs[name] = data

    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

    mixins_name = "settledlands.mixins.json"
    if mixins_name not in blobs:
        raise SystemExit("base jar missing %s" % mixins_name)
    blobs[mixins_name] = patch_mixins(blobs[mixins_name])
    print("  updated : %s (added client LootPoolDropdownMixin440)" % mixins_name)

    for lp, entries in (
        ("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
        ("assets/settledlands/lang/en_us.json", LANG_SET_EN),
    ):
        if lp not in blobs:
            raise SystemExit("base jar missing %s" % lp)
        blobs[lp] = patch_lang(blobs[lp], entries)
        print("  updated : %s (+%d keys)" % (lp, len(entries)))

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
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
