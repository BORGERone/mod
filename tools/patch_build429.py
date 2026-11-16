#!/usr/bin/env python3
"""
Build 429 - Biome-layered Hell Dungeon progression («к» at start -> «а» and «п» in depth).

Builds on Build 428 (``settledlands-0.8.0-build428-unique-dungeon.jar``) and adds layered biome
generation for the Hell Dungeon:

  * Classifies rooms in ``DungeonBlueprints427`` by Axiom tags and Russian prefixes (``crypt`` / «к»,
    ``infernal`` / «а», ``flesh`` / «п», ``sewer`` / «с», ``cove`` / «б», handling tagless pieces
    such as ``к Коридор 6``) and upgrades ``filter_mode=auto`` on mixed packs to
    ``auto->biomes(к->а+п)`` (with explicit ``filter_mode=biomes`` also supported);
  * Updates ``DungeonConfig427`` default dungeon target size to ``75..95`` pieces so all three
    layers have full room to unfold inside each ``512x512`` cell (while remaining compatible with
    existing ``60..80`` configs);
  * Updates ``DungeonGenerator427`` to build the starting region around ``старт`` strictly from
    «к» (crypt) rooms and split deeper branches into sticky, cohesive «а» (infernal) and «п» (flesh)
    biomes with matching dead-ends (``а Тупик 1..4`` / ``п Тупик 1..4``) and balanced counts;
  * Logs per-biome room breakdowns in ``DungeonManager425`` and ``/settledlands dungeon info``.

Base 428. Zero new mixins.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build428-unique-dungeon"
NEW_VERSION = "0.8.0-build429-biome-dungeon"
OUT_NAME = "settledlands-0.8.0-build429-biome-dungeon.jar"

REMOVED = ()
INNER_SYNC = (
    "dev/settledlands/dungeon/DungeonGenerator427$Seal.class",
)
REPLACED = (
    "dev/settledlands/dungeon/DungeonConfig427.class",
    "dev/settledlands/dungeon/DungeonBlueprints427.class",
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class",
    "dev/settledlands/dungeon/DungeonGenerator427.class",
    "dev/settledlands/dungeon/DungeonGenerator427$OpenExit.class",
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class",
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class",
    "dev/settledlands/dungeon/DungeonManager425.class",
)
ADDED = (
    "dev/settledlands/dungeon/DungeonBlueprints427$SelectionResult.class",
)

MARKERS = {
    "dev/settledlands/dungeon/DungeonConfig427.class": ("unique_dungeon_per_entry", "biomes"),
    "dev/settledlands/dungeon/DungeonBlueprints427.class": ("themeOf", "auto->biomes("),
    "dev/settledlands/dungeon/DungeonBlueprints427$Library.class": ("biomeProgression", "connectorsForTheme"),
    "dev/settledlands/dungeon/DungeonGenerator427.class": ("generateBiomePlan", "tryGenerateBiome"),
    "dev/settledlands/dungeon/DungeonGenerator427$Plan.class": ("cryptCount", "infernalCount", "fleshCount"),
    "dev/settledlands/dungeon/DungeonGenerator427$PlannedPiece.class": ("biome", "depth"),
    "dev/settledlands/dungeon/DungeonManager425.class": ("cryptCount", "infernalCount", "fleshCount"),
}
FORBIDDEN_MARKERS = {}

CHANGELOG_EN = (
    "Build 429 adds biome-layered Hell Dungeon progression: starting chambers around старт are "
    "built strictly from «к» (crypt) blueprints, and deeper branches transition into cohesive, "
    "sticky «а» (infernal) and «п» (flesh) biomes with matching dead-ends and balanced sizes "
    "(auto->biomes(к->а+п) in filter_mode=auto, default 75..95 rooms)."
)
PATCHINFO = (
    "Сборка 429: глубинные биомы «Адского данжа» (начало «к» -> вглубь «а» и «п»).\n"
    "  * В режиме filter_mode=auto (и явном filter_mode=biomes) при смешанной папке данж\n"
    "    стартует строго с комнат «к» (crypt, включая к Коридор 6 без тегов),\n"
    "    а при уходе вглубь ответвления разделяются на цельные биомы «а» (infernal) и «п» (flesh)\n"
    "    со своими залами, развилками и тупиками (а Тупик 1..4 / п Тупик 1..4);\n"
    "  * Целевой размер по умолчанию поднят до 75..95 комнат в ячейке 512x512 (совместимо с 60..80);\n"
    "  * В /settledlands dungeon info и логах показывается разбивка комнат [к:..., а:..., п:...].\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out429",
                    help="directory containing compiled build 429 classes")
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
