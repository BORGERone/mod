#!/usr/bin/env python3
"""
Build 435 - Entity-Ticking Chunk Deferral, Atomic Contraption Room Placement & Spawn Diagnostics.

Builds on Build 434 (``settledlands-0.8.0-build434-contraption-pos-fix.jar``):
  1. Defers entity-containing dungeon pieces until chunks are ``ENTITY_TICKING``
     (``DungeonManager425``):
       * Root cause: Vanilla/NeoForge ``PersistentEntitySectionManager`` starts entity sections
         at ``Visibility.HIDDEN`` and ``ChunkLoadStatus.FRESH`` while dungeon chunks are only at
         ``FullChunkStatus.FULL`` (beyond simulation distance or mid-async entity load when the
         server teleports the player into the dungeon). Calling ``ServerLevel.addFreshEntity``
         in a ``FULL`` / ``FRESH`` chunk inserts the entity into a hidden section without
         tracking/ticking and triggers asynchronous entity load/unload races (and Create
         contraption/bearing tick races on the first tick once loaded).
       * Now defers non-start (``i > 0``) pieces containing entities out of
         ``ensureInstanceInitialized`` and skips them in ``pickNextPiece`` until
         ``ServerLevel.isPositionEntityTicking(BlockPos)`` returns ``true`` across all chunks
         overlapping the piece bounds as well as all transformed block-entity and entity positions.
       * When ready, places the entire contraption room atomically in a single tick
         (``sliceSize = remaining`` when ``remaining <= 16384``) so the bearing, motor,
         blocks, and ``create:stationary_contraption`` entity enter an already-ticking chunk
         together on the exact same server tick.
  2. Contraption spawn & controller-binding diagnostics (``DungeonTemplate425`` &
     ``CreateCompat431``):
       * Checks ``addFreshEntity`` return value, ``entity.isRemoved()``, ``isPositionEntityTicking``,
         and controller attachment outcome, emitting concise INFO/WARN diagnostics on special
         piece placement and contraption attachment failures (or partial Create reflection).

Base 434. Zero new mixins. Reuses ``ServerLevel.isPositionEntityTicking(BlockPos)Z`` (already
invoked by ``AltarNpcReviveEvents`` in the base jar).
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build434-contraption-pos-fix"
NEW_VERSION = "0.8.0-build435-contraption-ticking-spawn"
OUT_NAME = "settledlands-0.8.0-build435-contraption-ticking-spawn.jar"

INNER_SYNC = (
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)
REPLACED = (
    "dev/settledlands/dungeon/CreateCompat431.class",
    "dev/settledlands/dungeon/DungeonManager425.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
)
ADDED = ()

MARKERS = {
    "dev/settledlands/dungeon/CreateCompat431.class": (
        "attachContraption",
        "did not bind entity=",
        "Create compat init failed:",
    ),
    "dev/settledlands/dungeon/DungeonManager425.class": (
        "isPieceEntityAreaReady",
        "isPositionEntityTicking",
        "hasEntities",
        "placed piece '",
    ),
    "dev/settledlands/dungeon/DungeonTemplate425.class": (
        "isPositionEntityTicking",
        "isRemoved",
        "was rejected/removed on spawn",
    ),
}

CHANGELOG_EN = (
    "Build 435 defers entity/contraption dungeon rooms until all overlapping chunks and "
    "entity positions satisfy ServerLevel.isPositionEntityTicking (FULL -> ENTITY_TICKING and "
    "PersistentEntitySectionManager Visibility.TICKING), then builds the entire contraption "
    "room atomically in one server tick alongside addFreshEntity / controller-attach diagnostics."
)
PATCHINFO = (
    "Сборка 435: отложенный атомарный спавн комнат с контрапциями Create в полностью тикающих чанках.\n"
    "  * Комнаты данжа, содержащие сущности (например create:stationary_contraption в Астролябии),\n"
    "    теперь не спавнятся в чанках со статусом FULL/HIDDEN/FRESH до прогрузки игрока и\n"
    "    перехода PersistentEntitySectionManager в активный режим тикания сущностей;\n"
    "  * Генератор ожидает, пока ServerLevel.isPositionEntityTicking вернёт true для всех чанков\n"
    "    и позиций механизмов/сущностей комнаты, после чего возводит комнату целиком за 1 тик\n"
    "    (блоки, подшипник, мотор и сущность контрапции вместе);\n"
    "  * Добавлена диагностика результата addFreshEntity, entity.isRemoved(), ticking и привязки\n"
    "    CreateCompat431.attachContraption к подшипнику.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out435",
                    help="directory containing compiled build 435 classes")
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
