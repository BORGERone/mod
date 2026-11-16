#!/usr/bin/env python3
"""
Build 422 - the cell of a paste: the fingerprint places the build, not the marker's feet.

The author's report after build 421: «по прежнему не копируется». Build 421's log shows the anchor
running clean - `a marker was placed at BlockPos{x=-1675, y=199, z=-94}` at 03:47:02, then
`pasted at BlockPos{x=-1675, y=198, z=-94}` two seconds later, with no paste in between and no warning.

That pair of lines is the defect. The marker is a mob (`settledlands:guide_trigger` is a
`GuideTriggerNpc extends GuideNpc extends PathfinderMob`), its position is three `double`s, and build
421 read the cell of the paste as `floor(y)` of the marker's feet. Half a block of drift - one tick of
physics, a paste landing a fraction low - turns that into the cell **below** the build, so the graphs
were written one block under the blocks they belong to. Nothing reads a graph there: the block editor,
the door and the chest all ask for the block's own cell. The graphs were restored, and invisible.

The same `(int)` cast appeared a second time, and it is wrong for a different reason: `(int)` truncates
towards zero, so a marker at `x = -1674.5` - which is exactly where this build's own `spawn` puts every
marker, half a block past the cell edge - read as cell `-1674` instead of `-1675`. Every negative
coordinate was off by one block, in the direction of the world origin.

This build makes the blocks decide where the build is:

  * `NpcGraphAnchorCore420#bestPlace` - the cell whose fingerprint matches best, searched in a small
    box around the marker's feet, with identity winning a tie and the nearer cell winning before the
    farther one. A cell that matches nothing is not a place: the marker's own cell is kept, so a marker
    standing on a stranger's build is never moved by a coincidence;
  * `NpcGraphAnchor420#materialiseIfMoved` - `bestPlace` decides where the graphs go, and the log now
    prints both cells, so the next report from the author can be read without guessing;
  * `NpcGraphAnchor420#home` / `#refresh` - a marker is re-sealed around the cell it stamped, not
    around wherever its feet drifted to. Without this the refresh pass would rewrite the list around
    the drifted cell every two seconds, moving every offset and undoing the paste;
  * `NpcGraphAnchor420#cell` - one place that turns an entity into a cell, using `BlockPos.containing`
    (a call the base jar already makes) instead of a bare `(int)` cast.

Base 421. Two classes replaced, nothing added, nothing removed, no new game call.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build421-anchor-registry-fix"
NEW_VERSION = "0.8.0-build422-paste-cell"
OUT_NAME = "settledlands-0.8.0-build422-paste-cell.jar"

REMOVED = ()
# Build 422 changes the rule and the world class build 420 added; every other anchor class is
# byte-identical to what 421 shipped, which the packaging loop below proves.
REPLACED = (
    "dev/settledlands/npc/NpcGraphAnchorCore420.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
)
ADDED = ()

MARKERS = {
    "dev/settledlands/npc/NpcGraphAnchorCore420.class": (
        "bestPlace",
        "SettledLandsAnchor",
    ),
    "dev/settledlands/npc/NpcGraphAnchor420.class": (
        "settledlands",
        "guide_trigger",
        "bestPlace",
        "containing",
    ),
}

CHANGELOG_EN = (
    "Build 422 makes a pasted build's graphs land on the pasted build. Build 421 read the cell of a "
    "paste from the marker entity's own position, and a marker is a mob whose y is a double: half a "
    "block of drift put every restored graph one block under the blocks it belongs to, where nothing "
    "reads it. The cell now comes from the fingerprint of the blocks around the marker, the marker is "
    "re-sealed around the cell it stamped, and cells are read with BlockPos.containing instead of a "
    "cast that truncated every negative coordinate towards zero."
)
PATCHINFO = (
    "Сборка 422: графы вставленной постройки ложатся на саму постройку. В 421 клетка вставки "
    "бралась из позиции сущности-маркера, а маркер - это моб, у которого y дробный: половина блока "
    "дрейфа сдвигала все восстановленные графы на блок ниже блоков, которым они принадлежат, - там "
    "их никто не читает. Теперь клетку выбирает отпечаток блоков вокруг маркера, маркер "
    "перезапечатывается вокруг той клетки, которую сам записал, а клетки считаются через "
    "BlockPos.containing вместо приведения (int), которое для отрицательных координат срезало к нулю.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = root / "out422"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("expected %s in base jar to remove" % name)
        del blobs[name]
        order.remove(name)

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
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
