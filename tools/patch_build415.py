#!/usr/bin/env python3
"""
Build 415 - open doors right when the guide reaches them, and fit 2-block-high doorways/passages.

After testing Build 414 in-game, the author confirmed that door opening and closing now work well and
asked for two adjustments:

  1) The guide opened doors too early (~3 blocks before reaching the door). `openOnPath` previously
     walked `LOOKAHEAD = 4` path nodes ahead with no distance check, and `REACH` / `STUCK_REACH` were
     `1.9` / `3.0` blocks. Build 415 gates both `openOnPath` and `openAround` by horizontal distance
     (`REACH = 1.25` blocks while walking, `STUCK_REACH = 1.55` when stopped at a door, with `PERIOD = 3`
     ticks so the door opens right as the guide steps up to it without bumping into the closed leaf).

  2) The guide could not pass through a doorway when there was a solid block above the door (any 2-block
     passage). `NpcContent` declared `sized(0.6F, 2.1F)` - a 2.1-block-tall hitbox, which physically hits
     a lintel at y+2 and causes vanilla `WalkNodeEvaluator` (`floor(2.1 + 1.0) = 3`) to require 3 blocks
     of headroom. Build 415 patches the height float in `NpcContent` from `2.1F` (`0x40066666`) to `1.8F`
     (`0x3FE66666`, player height), so the guide fits 2-block doorways and passages cleanly (even over
     carpets), while visual model size continues to come from `GuideNpc#visualScale()`.

Base: Build 414 (`settledlands-0.8.0-build414-door-pass-linked.jar`).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build414-door-pass-linked"
NEW_VERSION = "0.8.0-build415-door-at-hand-and-2block-height"
OUT_NAME = "settledlands-0.8.0-build415-door-at-hand-and-2block-height.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/NpcDoors368$Watch.class",
    "dev/settledlands/npc/NpcContent.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/NpcDoors368.class": (
        "stopLeftBehind", "keepOpen", "markWalkedTo", "baseDoorPos",
        "setCanPassDoors", "canPassDoors", "onTheWay", "inFrontOf", "beat", "LAST_TRACK",
    ),
    "dev/settledlands/door/NpcDoors368$Open.class": ("walkedTo",),
    "dev/settledlands/door/NpcDoors368$Watch.class": ("target", "age"),
    "dev/settledlands/npc/NpcContent.class": ("settledlands:guide",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

CHANGELOG_EN = (
    "Build 415 makes two adjustments requested after Build 414 in-game testing: (1) guides now open doors "
    "only when they come right up to the doorway (1.25 blocks horizontally, checked every 3 ticks) instead "
    "of 3-4 blocks ahead along the path; (2) the guide hitbox height in NpcContent is lowered from 2.1 to "
    "1.8 blocks (player height) so guides walk freely through 2-block-high passages and doorways that have "
    "a block above the door."
)
PATCHINFO = (
    "Сборка 415 вносит две правки по итогам проверки 414-й сборки в игре: (1) проводник больше не открывает "
    "дверь заранее за 3 блока — теперь дверь открывается только когда он непосредственно подошёл к ней "
    "(1.25 блока по горизонтали, проверка каждые 3 тика); (2) высота хитбокса проводника в NpcContent "
    "уменьшена с 2.1 до 1.8 блока (высота игрока), чтобы он свободно проходил в проходы высотой 2 блока и в "
    "дверные проёмы, над которыми стоит блок.\n"
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
    srcroot = root / "out415"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("%s has markers but is in no list" % name)

    for name in ADDED + REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to the base - nothing to replace" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    # Self-check: every compiled/patched class in out415 (including inner classes) must match the jar byte-for-byte.
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s does not match the jar!" % rel)

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    print("  mixins.json: %d common, %d client (first: %s)"
          % (len(common), len(client), common[0]))

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
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d entries)" % (out_path, len(order)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
