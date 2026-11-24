#!/usr/bin/env python3
"""
Build 433 - Create Contraption Rotation & Priority .nbt Spawning for «Адский данж».

Builds on Build 432 (``settledlands-0.8.0-build432-deadend-corridors.jar``):
  1. Full Create Contraption & Mechanical Bearing support (``DungeonNbt431``):
       * Preserves ``Running=1`` on ``create:mechanical_bearing`` (only ``create:windmill_bearing`` /
         ``LastGenerated`` / ``QueueAssembly`` bearings reset ``Running=0`` with ``QueueAssembly=1``);
       * Fixes integer-corner contraption placement (``create:stationary_contraption`` and other
         contraptions with whole-block ``pos`` rotate via ``rotX/rotZ`` without the +1 block
         half-cell offset, with ``moveBeforeLoad=true``);
       * Rotates all contraption fields on 0/90/180/270-degree room placement:
         ``ControllerRelative``, ``Axis``, ``Contraption.Anchor``, ``Contraption.Facing``,
         ``Contraption.BoundsFront`` (preserving TAG_Float/TAG_Double element type),
         ``Contraption.Blocks.Palette``, packed long ``BlockPos`` in ``Contraption.Blocks.BlockList``,
         ``Contraption.Actors`` (``Pos``, ``Position``, ``Motion``, ``RelativeMotion``),
         ``Contraption.Seats``, and ``Contraption.Superglue``.
  2. Reliable ``.nbt`` schematic discovery, replacement, and spawn rate
     (``AxiomBlueprint427``, ``CreateSchematic431``, ``DungeonBlueprints427``,
     ``DungeonGenerator427``, ``DungeonCommand425``):
       * ``.nbt`` schematics cleanly override same-key ``.bp`` blueprints (and same-name
         prefixed ``.bp`` rooms when unprefixed) instead of creating ``#nbt`` duplicates;
       * Unprefixed ``.nbt`` rooms in ``auto`` biome mode are preserved and routed into deep
         biomes rather than silently dropped as ``THEME_OTHER``;
       * First-placement priority bonus (``-7.0`` when ``used == 0``) ensures rare/large
         custom ``.nbt`` rooms (such as ``а Астролябия 1.nbt``) spawn reliably even in large
         230+ blueprint packs without spamming duplicates;
       * Honest ``summaryLine()`` / ``/settledlands dungeon info`` / ``reload`` diagnostics show
         active vs filtered/no-lapis ``.nbt`` counts and warn if unreferenced ``.nbt`` files
         sit in another auto-discovery directory (such as ``schematics/``).

Base 432. Zero new mixins. Zero new external MC API calls.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build432-deadend-corridors"
NEW_VERSION = "0.8.0-build433-nbt-contraption-and-spawn"
OUT_NAME = "settledlands-0.8.0-build433-nbt-contraption-and-spawn.jar"

INNER_SYNC = (
    "dev/settledlands/dungeon/AxiomBlueprint427$Marker.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$PieceTemplate.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$SelectionResult.class",
    "dev/settledlands/dungeon/DungeonGenerator427$OpenExit.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlacementAttempt.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Seal.class",
    "dev/settledlands/dungeon/DungeonNbt431$CountingInputStream.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtCompound.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtReader.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtValue.class",
    "dev/settledlands/dungeon/DungeonNbt431$TransformedEntity.class",
)
REPLACED = (
    "dev/settledlands/dungeon/AxiomBlueprint427.class",
    "dev/settledlands/dungeon/CreateSchematic431.class",
    "dev/settledlands/dungeon/DungeonBlueprints427.class",
    "dev/settledlands/dungeon/DungeonCommand425.class",
    "dev/settledlands/dungeon/DungeonGenerator427.class",
    "dev/settledlands/dungeon/DungeonNbt431.class",
)
ADDED = ()

MARKERS = {
    "dev/settledlands/dungeon/AxiomBlueprint427$PieceTemplate.class": ("isNbt",),
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class": (
        "nbtInPoolCount", "nbtFilteredCount", "nbtSkippedNoLapisCount", "unusedNbtInOtherDirs"
    ),
    "dev/settledlands/dungeon/DungeonBlueprints427.class": (
        "stripThemePrefix", "replaceMatchingBpInBucket", "countNbtFiles"
    ),
    "dev/settledlands/dungeon/DungeonNbt431.class": (
        "transformContraptionEntity", "ControllerRelative", "BoundsFront", "BlockList"
    ),
    "dev/settledlands/dungeon/DungeonCommand425.class": (
        "unusedNbtInOtherDirs", "unusedNbtOtherDirHint"
    ),
}

CHANGELOG_EN = (
    "Build 433 fixes Create contraption rotation (mechanical_bearing Running=1 preservation, "
    "exact corner positioning, ControllerRelative/Axis/Anchor/Facing/BoundsFront/BlockList/Actors "
    "transformation for stationary_contraption), makes .nbt schematics replace same-key .bp files, "
    "routes unprefixed .nbt rooms into deep biomes in auto mode, boosts first-time .nbt room "
    "spawn likelihood, and adds clear active/filtered/other-folder .nbt diagnostics."
)
PATCHINFO = (
    "Сборка 433: полноценный поворот механизмов Create (stationary_contraption) и надёжный спавн .nbt комнат.\n"
    "  * Для create:mechanical_bearing сохраняется Running=1 (сброс оставлен только для ветряков),\n"
    "    а сущность create:stationary_contraption при поворотах 0/90/180/270° ставится в точный угол\n"
    "    блока без +1 сдвига, с полным поворотом ControllerRelative, Axis, Contraption.Anchor/Facing,\n"
    "    BoundsFront, Palette, упакованного BlockList и Actors;\n"
    "  * Файлы .nbt автоматически заменяют одноимённые .bp (включая старые версии без механизмов),\n"
    "    поддерживаются без префикса темы в режиме auto и получают мягкий приоритет первого спавна;\n"
    "  * Команды /settledlands dungeon info и reload честно показывают число активных и отсеянных\n"
    "    .nbt и предупреждают, если .nbt остались лежать в другой папке (например schematics/).\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out433",
                    help="directory containing compiled build 433 classes")
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

    for name in INNER_SYNC:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled inner class: %s" % src)
        if name not in blobs:
            raise SystemExit("base jar missing expected inner class: %s" % name)
        data = src.read_bytes()
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
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
