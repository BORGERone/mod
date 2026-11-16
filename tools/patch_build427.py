#!/usr/bin/env python3
"""
Build 427 - Procedural Hell Dungeon generation directly from Axiom ``.bp`` folders on disk.

Replaces the single-room fallback behavior with a full native Axiom ``.bp`` scanner and room
stitcher while keeping ``start.slbp`` intact as an automatic fallback when the blueprint folder
is empty:

  * Native pure-Java Axiom ``.bp`` reader (``AxiomBlueprint427``): parses header tags/metadata,
    ``BlockRegion`` 16x16x16 packed palettes, ``BlockEntities``, and ``Entities`` without requiring
    Axiom on the server or pre-packing all rooms into the mod jar;
  * External config & folder discovery (``DungeonConfig427`` / ``DungeonBlueprints427``): auto-creates
    ``config/settledlands/dungeon.properties`` and ``config/settledlands/blueprints/``, and in
    ``blueprints_dir=auto`` inspects ``config/settledlands/blueprints``, ``config/axiom/blueprints``,
    and ``blueprints`` with ``auto`` theme filtering (picking ``infernal`` when mixed dungeon themes
    are present, or loading all valid ``.bp`` pieces otherwise);
  * Deterministic BFS room stitcher (``DungeonGenerator427``): connects rotated ``lapis_block``
    entrances to open ``redstone_block`` exits inside each ``512x512`` cell without bounding-box
    overlap, caps branches with dead-end rooms and ``nether_bricks`` doorway seals (including the
    rear start arch when ``seal_start_entrance=true``), rotates blockstate properties, block
    entities, and entities cleanly across 0/90/180/270-degree turns;
  * Lag-free tick-paced placement (``DungeonTemplate425#placeSlice``, ``DungeonData425``,
    ``DungeonManager425``, ``DungeonEvents425#onServerTick``): builds immediate rooms around the
    entrance synchronously and streams remaining rooms across server ticks prioritizing players in
    ``settledlands:hell_dungeon`` while persisting partial progress across restarts;
  * ``/settledlands dungeon reload`` (``DungeonCommand425``) to rescan ``.bp`` folders live.

Base 426. Zero new mixins.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build426-dungeon-command-fix"
NEW_VERSION = "0.8.0-build427-procedural-dungeon"
OUT_NAME = "settledlands-0.8.0-build427-procedural-dungeon.jar"

REMOVED = ()
INNER_SYNC = (
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonData425$ReturnPoint.class",
)
REPLACED = (
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/dungeon/DungeonData425.class",
    "dev/settledlands/dungeon/DungeonData425$Instance.class",
    "dev/settledlands/dungeon/DungeonManager425.class",
    "dev/settledlands/dungeon/DungeonEvents425.class",
    "dev/settledlands/dungeon/DungeonCommand425.class",
)
ADDED = (
    "dev/settledlands/dungeon/AxiomBlueprint427.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$CountingInputStream.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$Marker.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtCompound.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtReader.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtValue.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$PieceTemplate.class",
    "dev/settledlands/dungeon/DungeonConfig427.class",
    "dev/settledlands/dungeon/DungeonBlueprints427.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class",
    "dev/settledlands/dungeon/DungeonGenerator427.class",
    "dev/settledlands/dungeon/DungeonGenerator427$OpenExit.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Seal.class",
    "dev/settledlands/dungeon/DungeonData425$PieceProgress.class",
    "dev/settledlands/dungeon/DungeonData425$SealProgress.class",
)

MARKERS = {
    "dev/settledlands/dungeon/AxiomBlueprint427.class": ("BlockRegion", "lapis_block", "redstone_block"),
    "dev/settledlands/dungeon/DungeonConfig427.class": ("config/settledlands/dungeon.properties", "blueprints_dir"),
    "dev/settledlands/dungeon/DungeonBlueprints427.class": ("auto->infernal", "auto->all"),
    "dev/settledlands/dungeon/DungeonGenerator427.class": ("rotEntityX", "rotatedWorldBox"),
    "dev/settledlands/dungeon/DungeonTemplate425.class": ("placeSlice", "placeSeal", "rotateProps"),
    "dev/settledlands/dungeon/DungeonData425.class": ("PlanGenerated", "Pieces", "Seals"),
    "dev/settledlands/dungeon/DungeonManager425.class": ("tickPendingGeneration", "ensureInstanceInitialized"),
    "dev/settledlands/dungeon/DungeonEvents425.class": ("ServerTickEvent", "onServerTick"),
    "dev/settledlands/dungeon/DungeonCommand425.class": (
        "com/mojang/brigadier/exceptions/CommandSyntaxException",
        "reload",
    ),
}
FORBIDDEN_MARKERS = {
    "dev/settledlands/dungeon/DungeonCommand425.class": (
        "com/mojang/brigadier/CommandSyntaxException",
    ),
}

LANG_SET_RU = {
    "message.settledlands.dungeon.no_blueprints": "Папка блупринтов .bp пуста — загружен только стартовый зал. Положите файлы .bp из Axiom в config/settledlands/blueprints/ (или config/axiom/blueprints/) и выполните /settledlands dungeon reload.",
}
LANG_SET_EN = {
    "message.settledlands.dungeon.no_blueprints": "Blueprint folder is empty - only the fallback start chamber was placed. Drop Axiom .bp files into config/settledlands/blueprints/ (or config/axiom/blueprints/) and run /settledlands dungeon reload.",
}

CHANGELOG_EN = (
    "Build 427 adds procedural Hell Dungeon generation directly from Axiom .bp files in external "
    "folders on disk (auto-scanning config/settledlands/blueprints, config/axiom/blueprints, and "
    "blueprints, with configurable filtering in config/settledlands/dungeon.properties). Stitches "
    "rooms via lapis_block/redstone_block doorway markers inside each 512x512 cell with full block, "
    "block-entity, and entity rotation, seals dead ends and rear start arches, streams construction "
    "across server ticks near players, and adds /settledlands dungeon reload."
)
PATCHINFO = (
    "Сборка 427: процедурная генерация «Адского данжа» напрямую из папки .bp блупринтов Axiom.\n"
    "  * Нативный ридер Axiom .bp (AxiomBlueprint427): читает блоки, палитры, BlockEntities и Entities "
    "без перепаковки всех комнат внутрь JAR;\n"
    "  * Автопоиск папок config/settledlands/blueprints/, config/axiom/blueprints/, blueprints/ "
    "и настройки в config/settledlands/dungeon.properties;\n"
    "  * Детерминированный стыковщик комнат по lapis_block/redstone_block с поворотами, заделкой "
    "тупиков/швов и пошаговой достройкой по тикам с приоритетом рядом с игроками;\n"
    "  * Команда /settledlands dungeon reload для перечитывания папки блупринтов без перезапуска.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default=None, help="directory containing compiled build 427 classes")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = pathlib.Path(args.classes) if args.classes else (root / "out427")
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

    for name in INNER_SYNC:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled inner class: %s" % src)
        if name not in blobs:
            raise SystemExit("base jar missing expected inner class: %s" % name)
        data = src.read_bytes()
        blobs[name] = data
        print("  synced  : %s (%d bytes)" % (name, len(data)))

    for name in REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        for bad in FORBIDDEN_MARKERS.get(name, ()):
            if bad.encode("utf-8") in data:
                raise SystemExit("%s still contains forbidden marker %r" % (name, bad))
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
