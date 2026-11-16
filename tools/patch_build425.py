#!/usr/bin/env python3
"""
Build 425 - Hell Dungeon («Адский данж») dimension, void generator, and ``start.slbp`` grid placement.

Adds the ``settledlands:hell_dungeon`` custom dimension (void flat world, nether lighting/effects,
fixed midnight time, ``min_y = -64``, ``height = 384``), packs ``start.slbp`` converted from Axiom
``старт.bp``, registers the 5th Teleport world option («Адский данж» / «Hell Dungeon»), blocks
natural/chunk/patrol/structure/reinforcement mob spawns while preserving spawners/trial spawners and
scripted triggers, and adds ``/settledlands dungeon ...`` management commands.

Base 424. Zero new mixins. Two classes replaced (``TeleportNode424``, ``TeleportActions424``), one
unchanged compiler inner class matched (``TeleportNode424$Parsed``), ten classes added
(``DungeonTemplate425`` + 4 inner classes, ``DungeonData425`` + 1 inner class, ``DungeonManager425``,
``DungeonEvents425``, ``DungeonCommand425``), three data pack entries added, and two language keys
added in ``ru_ru.json`` / ``en_us.json``.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build424-teleport"
NEW_VERSION = "0.8.0-build425-hell-dungeon"
OUT_NAME = "settledlands-0.8.0-build425-hell-dungeon.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/npc/TeleportNode424.class",
    "dev/settledlands/npc/TeleportActions424.class",
)
UNCHANGED_INNER = (
    "dev/settledlands/npc/TeleportNode424$Parsed.class",
)
ADDED = (
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonData425.class",
    "dev/settledlands/dungeon/DungeonData425$Instance.class",
    "dev/settledlands/dungeon/DungeonData425$ReturnPoint.class",
    "dev/settledlands/dungeon/DungeonManager425.class",
    "dev/settledlands/dungeon/DungeonEvents425.class",
    "dev/settledlands/dungeon/DungeonCommand425.class",
)

DATA_FILES = (
    ("data/settledlands/dimension_type/hell_dungeon.json",
     "settledlands/dimension_type/hell_dungeon.json"),
    ("data/settledlands/dimension/hell_dungeon.json",
     "settledlands/dimension/hell_dungeon.json"),
    ("data/settledlands/dungeon/start.slbp",
     "settledlands/dungeon/start.slbp"),
)

MARKERS = {
    "dev/settledlands/npc/TeleportNode424.class": ("settledlands:hell_dungeon",),
    "dev/settledlands/npc/TeleportActions424.class": ("enterFromTeleportNode",),
    "dev/settledlands/dungeon/DungeonTemplate425.class": (
        "/data/settledlands/dungeon/start.slbp",
        "minecraft:nether_bricks",
    ),
    "dev/settledlands/dungeon/DungeonData425.class": ("settledlands_hell_dungeon", "ReturnPoints"),
    "dev/settledlands/dungeon/DungeonManager425.class": ("settledlands:hell_dungeon", "ensureStartPlaced"),
    "dev/settledlands/dungeon/DungeonEvents425.class": ("MobSpawnEvent", "onMobSpawnPosition"),
    "dev/settledlands/dungeon/DungeonCommand425.class": ("dungeon", "leave"),
}

LANG_SET_RU = {
    "gui.settledlands.teleport.world.hell_dungeon": "Адский данж",
    "message.settledlands.dungeon.not_loaded": "Мир «Адский данж» не загружен в этом сохранении — для нового измерения нужен новый мир.",
}
LANG_SET_EN = {
    "gui.settledlands.teleport.world.hell_dungeon": "Hell Dungeon",
    "message.settledlands.dungeon.not_loaded": "Hell Dungeon dimension is not loaded in this save - create a new world for the new dimension.",
}

CHANGELOG_EN = (
    "Build 425 adds the Hell Dungeon dimension (settledlands:hell_dungeon) as an empty void flat "
    "dimension with nether lighting/effects and fixed midnight time, bundles the packed start.slbp "
    "chamber (placed once per 512x512 grid instance, hiding schematic doorway markers and preserving "
    "trial spawners and NPC triggers), registers Hell Dungeon as the 5th built-in world in the "
    "Teleport graph node, blocks natural/chunk/patrol/structure/reinforcement mob spawns while "
    "allowing spawners and scripted triggers, and adds /settledlands dungeon management commands."
)
PATCHINFO = (
    "Сборка 425: измерение «Адский данж» (settledlands:hell_dungeon) и старт.bp.\n"
    "  * Пустое измерение без естественной генерации, свет и эффекты Незера, время 18000;\n"
    "  * Единоразовая постановка стартового зала start.slbp на сетке 512x512 с сохранением "
    "спавнера и триггера;\n"
    "  * «Адский данж» добавлен 5-м миром в узел «Телепорт» и команды /settledlands dungeon.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default=None, help="directory containing compiled build 425 classes")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = pathlib.Path(args.classes) if args.classes else (root / "out425")
    if not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("expected %s in base jar to remove" % name)
        del blobs[name]
        order.remove(name)

    for name in UNCHANGED_INNER:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled inner class: %s" % src)
        if name not in blobs:
            raise SystemExit("base jar missing expected inner class: %s" % name)
        data = src.read_bytes()
        blobs[name] = data
        print("  kept    : %s (%d bytes)" % (name, len(data)))

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

    data425_root = root / "data425"
    for jar_path, rel_path in DATA_FILES:
        src = data425_root / rel_path
        if not src.is_file():
            raise SystemExit("missing Build 425 data file: %s" % src)
        raw = src.read_bytes()
        if jar_path not in blobs:
            order.append(jar_path)
        blobs[jar_path] = raw
        print("  data    : %s (%d bytes)" % (jar_path, len(raw)))

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
    print("  neoforge.mods.toml: version %s, displayName, description prefix" % NEW_VERSION)

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
