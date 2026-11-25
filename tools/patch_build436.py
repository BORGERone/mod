#!/usr/bin/env python3
"""
Build 436 - Refurbished Furniture Storage Support (Chests + Diegesis/Handson) & «Пул предметов»
(Dungeon Loot Pool) Node.

Builds on Build 435 (``settledlands-0.8.0-build435-contraption-ticking-spawn.jar``):
  1. Refurbished Furniture storage integration:
       * ChestGraph396.isChest / isChestAt recognise storage furniture from ``refurbished_furniture``
         (_crate, _drawer, _cooler, _storage_cabinet, _fridge, _mail_box, _storage_jar) backed by
         Container block entities, excluding processing stations.
       * ChestClientEvents436 cancels client wand clicks on chest/furniture targets so ``diegesis``
         does not open a 3D focus screen instead of the chest graph editor.
       * ChestGraph396Events.onTick checks both ``player.containerMenu != player.inventoryMenu`` and
         active ``diegesis`` 3D stations via soft reflection (ChestDiegesisCompat436).
       * ChestOpen401 resolves front faces (hit face or opposite of ``direction`` property) so
         Refurbished drawers/cabinets/fridges also open automatically after lockpicking without
         ``handson_furniture``.
       * DungeonTemplate425.rotateProps rotates ``direction`` properties in schematic palettes.
  2. «Пул предметов» (Loot Pool) chest graph node:
       * Stored in ``NpcDialogueGraph.TRIGGER`` with ``action = "trigger:loot_pool:<file>|<maxRarity>|<maxWeight>|<rolls>"``.
       * Appears in the chest graph editor create menu (BlockMenu363 + NpcChestPalette396Mixin) with
         a custom amber card (LootPoolCard436 / LootPoolGraphUiMixin436) and inspector sidebar
         (LootPoolInspectorMixin436) listing ``config/settledlands/loot_pools/*.txt`` synced from the
         server (ChestLootNet436).
       * Triggers exclusively during ``settledlands:hell_dungeon`` room placement
         (DungeonTemplate425 -> LootPoolManager436 -> NpcGraphAnchor420.materialiseForDungeon),
         honouring rarity/weight caps, weighted probabilities, drop counts/ranges,
         ``RoomyContainer.handson$ownSize()`` slot limits, and StorageJar max stack sizes.
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

BASE_VERSION = "0.8.0-build435-contraption-ticking-spawn"
NEW_VERSION = "0.8.0-build436-furniture-loot-pools"
OUT_NAME = "settledlands-0.8.0-build436-furniture-loot-pools.jar"

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
)

SOURCE_FILES = (
    "dev/settledlands/chest/LootPool436.java",
    "dev/settledlands/chest/ChestDiegesisCompat436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/chest/ChestLootNet436.java",
    "dev/settledlands/chest/ChestClientEvents436.java",
    "dev/settledlands/chest/ChestGraph396.java",
    "dev/settledlands/chest/ChestGraph396Events.java",
    "dev/settledlands/chest/ChestOpen401.java",
    "dev/settledlands/npc/client/LootPoolCard436.java",
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/npc/NpcGraphAnchor420.java",
    "dev/settledlands/dungeon/DungeonTemplate425.java",
    "dev/settledlands/mixin/BlockMenu363Mixin.java",
    "dev/settledlands/mixin/NpcChestPalette396Mixin.java",
    "dev/settledlands/mixin/LootPoolGraphUiMixin436.java",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.java",
    "dev/settledlands/mixin/LootPoolWiringMixin436.java",
    "dev/settledlands/mixin/LootPoolActionsMixin436.java",
)

INNER_SYNC = (
    "dev/settledlands/chest/ChestGraph396Events$Pending.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)

REPLACED = (
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestOpen401.class",
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/mixin/BlockMenu363Mixin.class",
    "dev/settledlands/mixin/NpcChestPalette396Mixin.class",
)

ADDED = (
    "dev/settledlands/chest/LootPool436.class",
    "dev/settledlands/chest/LootPool436$Parsed.class",
    "dev/settledlands/chest/LootPool436$Range.class",
    "dev/settledlands/chest/LootPool436$PoolEntry.class",
    "dev/settledlands/chest/LootPool436$Drop.class",
    "dev/settledlands/chest/ChestDiegesisCompat436.class",
    "dev/settledlands/chest/LootPoolManager436.class",
    "dev/settledlands/chest/ChestLootNet436.class",
    "dev/settledlands/chest/ChestLootNet436$Pools.class",
    "dev/settledlands/chest/ChestClientEvents436.class",
    "dev/settledlands/npc/client/LootPoolCard436.class",
    "dev/settledlands/mixin/LootPoolGraphUiMixin436.class",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
    "dev/settledlands/mixin/LootPoolWiringMixin436.class",
    "dev/settledlands/mixin/LootPoolActionsMixin436.class",
)

MIXINS = (
    "LootPoolWiringMixin436",
    "LootPoolActionsMixin436",
)
CLIENT_MIXINS = (
    "LootPoolGraphUiMixin436",
    "LootPoolInspectorMixin436",
)

MARKERS = {
    "dev/settledlands/chest/LootPool436.class": ("trigger:loot_pool:", "rollDrops", "parseLines"),
    "dev/settledlands/chest/ChestDiegesisCompat436.class": ("isStationOpen", "usableSlotCount"),
    "dev/settledlands/chest/LootPoolManager436.class": ("onDungeonRoomPlaced", "populateContainer"),
    "dev/settledlands/chest/ChestLootNet436.class": ("chest_loot_pools", "sendPools"),
    "dev/settledlands/chest/ChestClientEvents436.class": ("isWand", "setCancellationResult"),
    "dev/settledlands/chest/ChestGraph396.class": ("isFurnitureStorage", "_storage_cabinet"),
    "dev/settledlands/chest/ChestGraph396Events.class": ("isStationOpen",),
    "dev/settledlands/chest/ChestOpen401.class": ("openFace", "getOpposite"),
    "dev/settledlands/npc/client/LootPoolCard436.class": ("poolDropdownEntries", "raritySetter"),
    "dev/settledlands/npc/client/BlockMenu363.class": ("isLootPoolRow",),
    "dev/settledlands/npc/NpcGraphAnchor420.class": ("materialiseForDungeon",),
    "dev/settledlands/dungeon/DungeonTemplate425.class": ("onDungeonRoomPlaced", "direction"),
    "dev/settledlands/mixin/BlockMenu363Mixin.class": ("settledlands$isChest",),
    "dev/settledlands/mixin/NpcChestPalette396Mixin.class": ("isLootPoolRow",),
    "dev/settledlands/mixin/LootPoolGraphUiMixin436.class": ("settledlands$drawLootPoolCard436",),
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class": ("settledlands$buildLootPoolInspector436",),
    "dev/settledlands/mixin/LootPoolWiringMixin436.class": ("settledlands$connectLootPool436",),
    "dev/settledlands/mixin/LootPoolActionsMixin436.class": ("settledlands$skipLootPoolSingle436",),
}

LANG_SET_RU = {
    "gui.settledlands.loot_pool.menu": "+ Пул предметов",
    "gui.settledlands.loot_pool.title": "Пул предметов",
    "gui.settledlands.loot_pool.added": "Узел «Пул предметов» добавлен",
    "gui.settledlands.loot_pool.file": "Файл пула (.txt)",
    "gui.settledlands.loot_pool.none": "<не выбран>",
    "gui.settledlands.loot_pool.any": "любая",
    "gui.settledlands.loot_pool.limits_hint": "Фильтры (пусто = любые; напр. 50 или 0-100)",
    "gui.settledlands.loot_pool.max_rarity": "Макс. редкость",
    "gui.settledlands.loot_pool.max_weight": "Макс. вес",
    "gui.settledlands.loot_pool.rolls": "Кол-во предметов",
    "gui.settledlands.loot_pool.folder_hint": "config/settledlands/loot_pools/*.txt",
    "gui.settledlands.loot_pool.dungeon_only": "Работает при генерации данжа SettledLands",
    "gui.settledlands.loot_pool.card_limits": "Редк≤%s Вес≤%s",
    "gui.settledlands.loot_pool.card_rolls": "Предметов: %s",
    "gui.settledlands.loot_pool.card_hint": "Только данж SettledLands",
}

LANG_SET_EN = {
    "gui.settledlands.loot_pool.menu": "+ Item Pool",
    "gui.settledlands.loot_pool.title": "Item Pool",
    "gui.settledlands.loot_pool.added": "Added Item Pool node",
    "gui.settledlands.loot_pool.file": "Pool file (.txt)",
    "gui.settledlands.loot_pool.none": "<none>",
    "gui.settledlands.loot_pool.any": "any",
    "gui.settledlands.loot_pool.limits_hint": "Caps (blank = any; e.g. 50 or 0-100)",
    "gui.settledlands.loot_pool.max_rarity": "Max rarity",
    "gui.settledlands.loot_pool.max_weight": "Max weight",
    "gui.settledlands.loot_pool.rolls": "Item count",
    "gui.settledlands.loot_pool.folder_hint": "config/settledlands/loot_pools/*.txt",
    "gui.settledlands.loot_pool.dungeon_only": "Runs on SettledLands dungeon generation",
    "gui.settledlands.loot_pool.card_limits": "Rar≤%s Wgt≤%s",
    "gui.settledlands.loot_pool.card_rolls": "Items: %s",
    "gui.settledlands.loot_pool.card_hint": "Dungeon spawn only",
}

CHANGELOG_EN = (
    "Build 436 adds Refurbished Furniture storage support (crates, drawers, coolers, storage "
    "cabinets, fridges, mailboxes, storage jars) to the chest graph & lockpicking system—including "
    "Diegesis/Handson Furniture compatibility—and introduces the Item Pool («Пул предметов») chest "
    "graph node backed by config/settledlands/loot_pools/*.txt for SettledLands dungeon generation."
)
PATCHINFO = (
    "Сборка 436: поддержка мебели для хранения Refurbished Furniture (и совместимость с Diegesis /\n"
    "Handson Furniture), а также узел «Пул предметов» для генерации лута в данже SettledLands.\n"
    "  * Мебель хранения Refurbished Furniture (_crate, _drawer, _cooler, _storage_cabinet,\n"
    "    _fridge, _mail_box, _storage_jar) теперь поддерживает граф сундука, замок и взлом;\n"
    "  * Учитываются 3D-станции Diegesis (закрытие, палочка, слоты RoomyContainer) и лицевая грань\n"
    "    при авто-открытии без handson_furniture + поворот свойства direction в комнатах данжа;\n"
    "  * Узел «Пул предметов» в редакторе графа сундука выбирает .txt-файл из\n"
    "    config/settledlands/loot_pools/, макс. редкость, макс. вес, количество предметов и\n"
    "    наполняет контейнеры исключительно при генерации комнат данжа SettledLands.\n"
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


def compile_build436(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build436 compile failed:\n" + r2.stdout + r2.stderr)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out436",
                    help="directory for compiled build 436 classes")
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
        print("compiling Build 436 sources via ECJ (-21)...")
        compile_build436(base, srcroot)
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

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
                        ("assets/settledlands/lang/en_us.json", LANG_SET_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key in strings and strings[key] == value:
                raise SystemExit("%s already has %s = %r" % (path, key, value))
            strings[key] = value
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, value))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS:
        if name in client:
            raise SystemExit("%s must not be in client list" % name)
        if name not in common:
            common.append(name)
            print("  mixins  : +%s" % name)
    for name in CLIENT_MIXINS:
        if name in common:
            raise SystemExit("%s must not be in common list" % name)
        if name not in client:
            client.append(name)
            print("  client  : +%s" % name)
    if client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, indent=2) + "\n").encode("utf-8")

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
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
