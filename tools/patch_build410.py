#!/usr/bin/env python3
"""
Build 410 - a door in front of a standing guide is still a door on its way.

The author after 409: «теперь он стоит перед закрытой дверью». Build 409 fixed the body (it fits the doorway
now - he passed through it), and 409's own answer to a locked door undid the door:

  * `openAround` was handed the "is the guide moving this fifth of a second" flag and passed it on as the
    verdict "this door is on the guide's path". A guide that has stopped is by definition not moving, so
    every door it stood next to was declared "not on its path" - and a locked door that is not on the path
    is refused by build 408. The pass said "not on a guide's way - it stays shut" to the one door the guide
    was standing at, five times a second, for ever;
  * and the second half of the same deadlock: the door-between-the-guide-and-its-target test forgot the
    target after sixty ticks. One second of standing still and the pass no longer knew where the guide was
    going, so the other road to "on the way" was shut too.

Fix, one thing: the verdict is a fact about the door, not about the clock - `onTheWay` asks whether the door
is a node of the guide's own path or stands between the guide and its remembered target, and a standing
guide keeps its target while its path is unfinished. Everything else is as in 408/409: locked doors on the
way are opened and shut behind, doors off the way stay shut, the body is 0.6.

Base 404 again, one build, one change: `NpcDoors368` (build 410) and `NpcContent` (build 409's 0.6 body).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build410-door-on-the-way"
OUT_NAME = "settledlands-0.8.0-build410-door-on-the-way.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/npc/NpcContent.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # a door kept open until the guide is through it; a locked door on the guide's path is opened too.
    "dev/settledlands/door/NpcDoors368.class": ("stopLeftBehind", "keepOpen", "markWalkedTo",
                                                "setCanPassDoors", "onTheWay"),
    # the body that actually fits a doorway - vanilla lets a mob through a door only under 0.6 blocks.
    "dev/settledlands/npc/NpcContent.class": ("settledlands:guide",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 410 lets a guide open a locked door that stands in front of it while it waits. Build 409 handed "
    "the door pass the 'is the guide moving right now' flag and used it as the answer to 'is this door on "
    "the guide's way' - and a guide that has stopped in front of a door is by definition not moving, so the "
    "door was declared off its path and, being locked, left shut. The same report also forgot the guide's "
    "target after one second of standing still. Now the verdict is a fact about the door - a node of the "
    "guide's own path, or between it and the place it was walking to - and a standing guide keeps its "
    "target while its path is unfinished. The 0.6 body of build 409 stays: with it the guide walked into the "
    "doorway instead of stopping in front of it."
)
PATCHINFO = (
    "Сборка 410 открывает проводнику запертую дверь, перед которой он стоит. В 409-й я передавал в проход "
    "флаг «двигается ли проводник прямо сейчас» и использовал его как ответ на вопрос «эта дверь на его "
    "пути»: остановившийся проводник по определению не двигается, поэтому дверь объявлялась «не на пути», "
    "а запертую дверь не на пути 408-я не открывает. Пять раз в секунду, вечно. Вторая половина того же "
    "тупика: через секунду стояния проход забывал, куда проводник шёл. Теперь «на пути» — это факт про "
    "дверь: узел его собственного пути либо дверь между ним и целью, и цель не забывается, пока его путь "
    "не закончен. Тело 0.6 из 409-й остаётся: с ним проводник дошёл до проёма, а не встал перед дверью.\n"
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
    # Both halves one step on: NpcDoors368 from build 410 (the on-the-way verdict), NpcContent from 409
    # (the 0.6 body - the author's 409 report says the body fits the doorway now, and that half stays).
    srcroot = root / "out410"
    npc_content = root / "out409" / "dev/settledlands/npc/NpcContent.class"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("%s has markers but is in no list" % name)

    for name in ADDED + REPLACED:
        src = npc_content if name.endswith("npc/NpcContent.class") else srcroot / name
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

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET),
                        ("assets/settledlands/lang/en_us.json", LANG_SET_EN)):
        if not table:
            continue
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key in strings and strings[key] == value:
                raise SystemExit("%s already has %s = %r" % (path, key, value))
            strings[key] = value
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, value))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    for path, table in LANG_CHANGE.items():
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, (before, after) in table.items():
            if strings.get(key) != before:
                raise SystemExit("%s: %s is %r, expected %r"
                                 % (path, key, strings.get(key), before))
            strings[key] = after
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, after))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS_REMOVED:
        if name not in common:
            raise SystemExit("%s is not in the common list" % name)
        common.remove(name)
    for name in CLIENT_REMOVED:
        if name not in client:
            raise SystemExit("%s is not in the client list" % name)
        client.remove(name)
    for name in MIXINS_FIRST:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.insert(0, name)
    for name in MIXINS:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.append(name)
    for name in CLIENT_MIXINS:
        if name in common:
            raise SystemExit("%s must not be a common mixin" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
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
