"""
Build 423 - the cell and the turn are one search.

The author after build 422: «теперь все копируется, но есть проблема с поворотами, графы в итоге
съезжают».

Build 422 made the blocks decide *where* a pasted build is: `place`/`bestPlace` searched the cells
around the marker and the fingerprint picked the best one. That works while the build is not turned. The
moment it is, the blocks around the marker are the **turned** building, so a search with unrotated
offsets lands on the wrong cell - and then `bestTurn` finds the arrangement that fits *that* wrong cell
and applies it. Right cell with the wrong turn, or the right turn pinned to the wrong cell; either way
the graphs are laid over a shifted anchor. Reproduced for turns 2, 4 and 6 (180 and 270 degrees, with
and without the mirror) before this build was written.

Cell and arrangement cannot be decided one after the other: both are unknown and each answer changes
what the other must be. So they are one search now:

  * `NpcGraphAnchorCore420#place` - every arrangement x every neighbouring cell, scored against the
    fingerprint, best pair wins. Ties go to identity and then to the nearer cell, so a marker standing
    where it was sealed is never moved by a repeat of a common block;
  * `NpcGraphAnchorCore420#Placement` - the answer as one value: the shift, the turn, and how many
    blocks agreed, so the caller cannot take one half of it and leave the other behind;
  * `materialiseIfMoved` - places and turns from that one answer, refuses to write at all when nothing
    in reach matches (a pasted marker among strangers), and logs the cell, the shift, the agreement and
    the turn;
  * `home` - the same search, so a marker pasted turned keeps re-sealing around its own build.

The search is 8 x 11 x 11 x 7 looks per fingerprint block, so it runs on at most `SEARCH_SAMPLE` (128)
strided blocks and the winner is then re-scored against the whole fingerprint: 47 ms for a 4000-block
fingerprint, measured. 144 pastes (8 turns x 18 marker drifts) are checked by the JVM probe, and 72 more
with a building edited after the seal.

Base 422. Two classes replaced, one added (the answer type), nothing removed, no new game call.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build422-paste-cell"
NEW_VERSION = "0.8.0-build423-turn-and-cell"
OUT_NAME = "settledlands-0.8.0-build423-turn-and-cell.jar"

REMOVED = ()
# Build 422 changes the rule and the world class build 420 added; every other anchor class is
# byte-identical to what 421 shipped, which the packaging loop below proves.
REPLACED = (
    "dev/settledlands/npc/NpcGraphAnchorCore420.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
)
ADDED = (
    "dev/settledlands/npc/NpcGraphAnchorCore420$Placement.class",
)

MARKERS = {
    "dev/settledlands/npc/NpcGraphAnchorCore420.class": (
        "Placement",
        "SettledLandsAnchor",
        "SEARCH_SAMPLE",
    ),
    "dev/settledlands/npc/NpcGraphAnchor420.class": (
        "settledlands",
        "guide_trigger",
        "Placement",
        "containing",
    ),
    "dev/settledlands/npc/NpcGraphAnchorCore420$Placement.class": (
        "Placement",
    ),
}

CHANGELOG_EN = (
    "Build 423 fixes turned pastes: a build pasted rotated got its graphs laid over a shifted anchor. "
    "The cell of the paste and the arrangement of the build are one question - both are unknown and each "
    "answer changes what the other must be - so they are now searched together, every arrangement "
    "against every neighbouring cell, and the best pair wins. A marker that matches nothing in reach "
    "writes nothing at all, and the log prints the cell, the shift, the agreement and the turn."
)
PATCHINFO = (
    "Сборка 423: вставка с поворотом. Клетка вставки и разворот постройки - это один вопрос: оба "
    "неизвестны, и ответ на один меняет то, каким должен быть другой. Раньше клетка искалась в "
    "исходной ориентации, а поворот подбирался уже потом - при повёрнутой постройке клетка находилась "
    "неверно, и графы ложились со сдвигом. Теперь они ищутся вместе: все восемь разворотов на все "
    "соседние клетки, побеждает лучшая пара. Если рядом нет ничего похожего на постройку, маркер не "
    "пишет вообще, а в лог идут клетка, сдвиг, совпадение и поворот.\n"
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
    srcroot = root / "out423"

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
