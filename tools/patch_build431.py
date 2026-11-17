#!/usr/bin/env python3
"""
Build 431 - Full Create / Vanilla ``.nbt`` Schematic Support, Entity & Super Glue Rotation,
and Post-Placement Kinetic/Multiblock Activation for «Адский данж».

Builds on Build 430 (``settledlands-0.8.0-build430-dungeon-biomes-doors.jar``):
  1. Unified pure-Java NBT & Create/Vanilla ``.nbt`` parser (``DungeonNbt431``,
     ``CreateSchematic431``, ``AxiomBlueprint427``):
       * Supports Create ``Schematic & Quill`` and vanilla ``StructureTemplate`` ``.nbt`` files
         (gzipped or raw NBT, ``palette`` and ``palettes[0]``) alongside Axiom ``.bp`` blueprints;
       * Ignores ``minecraft:structure_void``, detects ``lapis_block`` / ``redstone_block`` seam
         markers, infers ``oldSchemaOrigin`` from multiblock controllers, and preserves 4-phase
         placement order (structural -> Create mechanisms/contraptions -> fragile -> doors).
  2. Full Create blockstate, BlockEntity & Entity rotation (``DungeonNbt431``, ``DungeonTemplate425``):
       * Rotates Create blockstate properties (``axis_along_first``, ``target``,
         ``EncasedCogwheel`` ``top_shaft``/``bottom_shaft``, ``ChainDrive``
         ``connected_along_first_coordinate``/``part``, vertical ``Belt`` ``part``,
         ``AbstractChassisBlock`` ``glueable_*``) and delegates Create blockstates through
         Create's native ``StructureTransform#apply(BlockState)`` when Create is installed;
       * Resets stale kinetic ``Source``/``Network``/``Speed``, sets ``NeedsSpeedUpdate=1b``,
         resets active contraption ``Running=0b`` and queues ``WindmillBearing`` assembly,
         rotates relative BE vectors (``TargetOffset``, ``Connections``, ``TravellingPackages``,
         ``InteractionPoints``, ``Input.Data``, embedded ``Bracket``/``Material`` blockstates),
         translates ``EnginePos``, cleanly re-forms rotated belts/tanks/vaults, and recomputes
         world-space AABB for ``create:super_glue`` (calling ``moveTo`` BEFORE ``load``) and
         ``facing``/``block_pos`` for frames/paintings.
  3. Optional reflection bridge & auto-discovery (``CreateCompat431``, ``DungeonBlueprints427``,
     ``DungeonConfig427``, ``DungeonCommand425``):
       * Calls ``BeltBlock.initBelt``, ``updateConnectivity``, ``attachKinetics``, generator
         ``reActivateSource``/``updateGeneratedRotation``, ``SmartBlockEntity.sendData``, and
         ``Level.sendBlockUpdated`` post-room via cached reflection (zero hard Create dependency);
       * Auto-discovers both ``.bp`` and ``.nbt`` across ``config/settledlands/blueprints``,
         ``config/axiom/blueprints``, ``blueprints``, and Create's ``schematics`` directory.

Base 430. Zero new mixins. Zero unverified external MC API calls.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build430-dungeon-biomes-doors"
NEW_VERSION = "0.8.0-build431-create-nbt-support"
OUT_NAME = "settledlands-0.8.0-build431-create-nbt-support.jar"

REMOVED = (
    "dev/settledlands/dungeon/AxiomBlueprint427$CountingInputStream.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtCompound.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtReader.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$NbtValue.class",
)
INNER_SYNC = (
    "dev/settledlands/dungeon/AxiomBlueprint427$Marker.class",
    "dev/settledlands/dungeon/AxiomBlueprint427$PieceTemplate.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$SelectionResult.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)
REPLACED = (
    "dev/settledlands/dungeon/AxiomBlueprint427.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/dungeon/DungeonBlueprints427.class",
    "dev/settledlands/dungeon/DungeonConfig427.class",
    "dev/settledlands/dungeon/DungeonCommand425.class",
)
ADDED = (
    "dev/settledlands/dungeon/DungeonNbt431.class",
    "dev/settledlands/dungeon/DungeonNbt431$CountingInputStream.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtCompound.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtReader.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtValue.class",
    "dev/settledlands/dungeon/DungeonNbt431$TransformedEntity.class",
    "dev/settledlands/dungeon/CreateSchematic431.class",
    "dev/settledlands/dungeon/CreateCompat431.class",
)

MARKERS = {
    "dev/settledlands/dungeon/AxiomBlueprint427.class": ("CreateSchematic431", "placementPhase"),
    "dev/settledlands/dungeon/DungeonTemplate425.class": ("PLACE_FLAGS", "CreateCompat431", "transformBlockEntityNbt"),
    "dev/settledlands/dungeon/DungeonBlueprints427.class": (".nbt", "isBlueprintOrNbt"),
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class": ("nbtCount",),
    "dev/settledlands/dungeon/DungeonConfig427.class": ("schematics", "min_pieces=85", "max_pieces=110", "max_radius=240"),
    "dev/settledlands/dungeon/DungeonCommand425.class": ("CreateCompat431", "isCreateAvailable"),
    "dev/settledlands/dungeon/DungeonNbt431.class": ("transformBlockEntityNbt", "transformEntityPlacement", "create:super_glue"),
    "dev/settledlands/dungeon/CreateSchematic431.class": ("buildPieceFromStructure", "minecraft:structure_void"),
    "dev/settledlands/dungeon/CreateCompat431.class": ("activateCreateRoom", "StructureTransform", "BeltBlock"),
}
FORBIDDEN_MARKERS = {}

CHANGELOG_EN = (
    "Build 431 adds native Create / vanilla .nbt schematic loading alongside Axiom .bp files "
    "(including schematics/ auto-discovery), proper rotation and world-space translation for Create "
    "kinetics, belts, fluid tanks, item vaults, mechanical arms/crafters, chain conveyors, display "
    "links, copycats, brackets, windmill contraptions, and super_glue entities, plus optional "
    "post-placement Create activation via reflection."
)
PATCHINFO = (
    "Сборка 431: нативная поддержка схематик Create / ванильных .nbt и механизмов Create в «Адском данже».\n"
    "  * Парсер схематик .nbt (Create «Схематика и перо» и Structural Block) наряду с Axiom .bp,\n"
    "    автопоиск по config/settledlands/blueprints, config/axiom/blueprints, blueprints и schematics;\n"
    "  * Корректный 0/90/180/270-градусный поворот блоков Create (ремни, цепные приводы, шасси,\n"
    "    шестерни, направленные механизмы) и через Create StructureTransform при наличии Create;\n"
    "  * Полная трансформация NBT механизмов и сущностей (super_glue AABB, TargetOffset,\n"
    "    InteractionPoints, ChainConveyor, Crafter Input, Bracket/Material, EnginePos, пересборка\n"
    "    лент/танков/сейфов, очистка старых кинетических сетей, перезапуск генераторов и ветряков).\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out431",
                    help="directory containing compiled build 431 classes")
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
