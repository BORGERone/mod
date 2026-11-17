#!/usr/bin/env python3
"""
Build 430 - Larger starting crypt biome («к») with deeper «а»/«п» transitions & door-safe placement.

Builds on Build 429 (``settledlands-0.8.0-build429-biome-dungeon.jar``):
  1. Larger starting biome & further secondary biomes (``DungeonConfig427``, ``DungeonGenerator427``):
       * Raises default target size to ``85..110`` pieces and ``max_radius=240`` (while scaling cleanly
         when an existing ``dungeon.properties`` uses ``60..80`` or ``75..95``);
       * Delays transitions from ``к`` (crypt) into ``а`` (infernal) and ``п`` (flesh) using deeper
         branch depth (``transitionDepth`` up to ``8``), larger early crypt share (``~42%``), and a
         radial minimum distance threshold (``minDist`` up to ``~120`` blocks from spawn) with
         ``innerSafeRadius`` deferral and distance-aware plan scoring across candidate seeds.
  2. Multi-block door & fragile decor placement safety (``AxiomBlueprint427``, ``DungeonTemplate425``):
       * Sorts parsed blueprint blocks into 3 safe phases: (0) structural blocks bottom-up,
         (1) fragile wall/ceiling/floor attachments bottom-up, and (2) door-like multi-blocks
         (``valhelsia_structures:dungeon_door``, etc., excluding trapdoors) grouped by column
         ``(wx, wz, wy)`` so ``lower`` always immediately precedes ``upper``;
       * Sets ``PLACE_FLAGS = 50`` (``Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE |
         Block.UPDATE_SUPPRESS_DROPS``) on all blueprint and seal block placements so neighbor shape
         updates never snap or drop pre-placed doors during construction;
       * Prevents ``placeSlice`` tick boundaries from splitting adjacent door columns across ticks.

Base 429. Zero new mixins.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build429-biome-dungeon"
NEW_VERSION = "0.8.0-build430-dungeon-biomes-doors"
OUT_NAME = "settledlands-0.8.0-build430-dungeon-biomes-doors.jar"

REMOVED = ()
INNER_SYNC = (
    "dev/settledlands/dungeon/AxiomBlueprint427$CountingInputStream.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$Marker.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtCompound.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtReader.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtValue.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$PieceTemplate.class",
    "dev/settledlands/dungeon/DungeonGenerator427$OpenExit.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Seal.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)
REPLACED = (
    "dev/settledlands/dungeon/AxiomBlueprint427.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/dungeon/DungeonConfig427.class",
    "dev/settledlands/dungeon/DungeonGenerator427.class",
)
ADDED = ()

MARKERS = {
    "dev/settledlands/dungeon/AxiomBlueprint427.class": ("placementPhase", "isDoorLike"),
    "dev/settledlands/dungeon/DungeonTemplate425.class": ("PLACE_FLAGS", "isDoorLike"),
    "dev/settledlands/dungeon/DungeonConfig427.class": ("min_pieces=85", "max_pieces=110", "max_radius=240"),
    "dev/settledlands/dungeon/DungeonGenerator427.class": ("generateBiomePlan", "tryGenerateBiome"),
}
FORBIDDEN_MARKERS = {}

CHANGELOG_EN = (
    "Build 430 expands the starting crypt («к») biome and pushes secondary «а» (infernal) and "
    "«п» (flesh) biomes much further from spawn (default 85..110 rooms, max_radius=240), and "
    "fixes multi-block doors (such as valhelsia_structures:dungeon_door) breaking/dropping as items "
    "during blueprint placement via 3-phase bottom-up ordering, tick-split guards, and "
    "UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS flags."
)
PATCHINFO = (
    "Сборка 430: увеличенный стартовый биом «к», дальние «а»/«п» и защита дверей при спавне.\n"
    "  * Стартовый биом крипты («к») вокруг «старт» увеличен (~42-50 комнат), а переходы в\n"
    "    вторичные биомы «а» (infernal) и «п» (flesh) отодвинуты значительно дальше по глубине\n"
    "    и радиусу от спавна (по умолчанию 85..110 комнат, max_radius=240; совместимо с 60..80 и 75..95);\n"
    "  * Исправлено разрушение/выпадение двухблочных дверей (в т.ч. Дверь подземелья из\n"
    "    valhelsia_structures) и хрупкого декора при спавне чертежей: 3-фазный порядок блоков\n"
    "    (конструктив -> декор -> двери снизу вверх по колонке), защита от разрыва двери границей тика\n"
    "    и флаг setBlock 50 (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS).\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out430",
                    help="directory containing compiled build 430 classes")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(args.classes)
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
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
