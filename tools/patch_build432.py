#!/usr/bin/env python3
"""
Build 432 - Dead-End Terminated Corridors for «Адский данж» (Zero Corridor Seal Walls).

Builds on Build 431 (``settledlands-0.8.0-build431-create-nbt-support.jar``):
  1. Reserved Dead-End Cap Invariant (``DungeonGenerator427``):
       * Maintains a collision-free, bounds-checked dead-end cap reservation for every open
         branch exit (re-reserving displaced sibling caps before accepting a room and budgeting
         ``1 + newExits <= slotsRemaining`` against ``max_pieces``);
       * Automatically bridges tight multi-exit rooms (such as ``Перекрёсток 1``) with one
         narrow 1-exit corridor + dead-end when direct adjacent dead-ends would clip corners;
       * Randomizes dead-end selection (``Тупик 1..4``) on closeout while falling back to the
         guaranteed reserved cap, achieving 0 non-start ``nether_bricks`` corridor seals across
         both biome progression and flat modes (with graceful fallback when a custom pool has
         zero dead-end templates).

Base 431. Zero new mixins. Zero new external MC API calls.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build431-create-nbt-support"
NEW_VERSION = "0.8.0-build432-deadend-corridors"
OUT_NAME = "settledlands-0.8.0-build432-deadend-corridors.jar"

INNER_SYNC = (
    "dev/settledlands/dungeon/DungeonGenerator427$OpenExit.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Seal.class",
)
REPLACED = (
    "dev/settledlands/dungeon/DungeonGenerator427.class",
)
ADDED = (
    "dev/settledlands/dungeon/DungeonGenerator427$PlacementAttempt.class",
)

MARKERS = {
    "dev/settledlands/dungeon/DungeonGenerator427.class": (
        "generateBiomePlan",
        "tryGenerateBiome",
        "tryPlaceWithCaps",
        "shortestBridgeCorridors",
        "findCapAvoiding",
    ),
    "dev/settledlands/dungeon/DungeonGenerator427$PlacementAttempt.class": (
        "addedPieces",
        "otherReReservations",
    ),
}

CHANGELOG_EN = (
    "Build 432 guarantees that Hell Dungeon corridors and branches terminate in matching "
    "dead-end rooms instead of flat nether_bricks seal walls (maintaining reserved dead-end "
    "caps on every open exit, re-reserving displaced sibling exits, and auto-bridging tight "
    "3-way crossroads with a short corridor)."
)
PATCHINFO = (
    "Сборка 432: завершение всех коридоров и веток «Адского данжа» реальными тупиками вместо стен незерака.\n"
    "  * Для каждого открытого выхода поддерживается зарезервированный тупик (без пересечений и выхода\n"
    "    за maxRadius) с автоматическим перерезервированием соседних веток и строгим бюджетом комнат;\n"
    "  * Узкие многовыходные перекрёстки (Перекрёсток 1) автоматически дополняются коротким мостом-\n"
    "    коридором до тупика, если прямые тупики задевают углы друг друга;\n"
    "  * На финализации веток равномерно выбираются все варианты тупиков биома (Тупик 1..4),\n"
    "    полностью исключая плоские заглушки из незерских кирпичей на концах коридоров.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out432",
                    help="directory containing compiled build 432 classes")
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
