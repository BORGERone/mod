#!/usr/bin/env python3
"""
Build 411 - the guide opens a locked door that is right in front of it.

The author after 410: «сейчас нпс может проходить через открытые двери, но почему-то разучился их сам
открывать». Exactly right, and it is the shape of my own fix: build 410 answered "is this door on the
guide's way" from facts that only exist when the vanilla navigator is doing the walking - a node of its path,
or the navigator's target. But a guide walks by three engines: its own scripted step, its own mobility routes
(`customMoveTarget`, `activeMobilityTarget`), and `navigateTo` on the vanilla navigator. When the first two are
driving, path and target are empty, "on the way" is false for every door, and since a locked door raises that
question and an unlocked one does not, the visible result is literally what he wrote: open doors pass, closed
ones are never opened.

One thing this build: a third answer, `inFrontOf` - physics instead of bookkeeping. While the guide moves, the
door must lie ahead of it in a narrow cone at arm's length (heading taken from where it actually came from);
while it stands, the door must be close enough to touch. Path, target and geometry are all answers to one
question - "is this door on its way" - so a locked door in the doorway it is walking into opens, and a locked
door set in the wall beside the road still stays shut.

Base 404: `NpcDoors368` (build 411) and `NpcContent` (409's 0.6 body).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build411-opens-doors-itself"
OUT_NAME = "settledlands-0.8.0-build411-opens-doors-itself.jar"

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
                                                "setCanPassDoors", "onTheWay", "inFrontOf"),
    # the body that actually fits a doorway - vanilla lets a mob through a door only under 0.6 blocks.
    "dev/settledlands/npc/NpcContent.class": ("settledlands:guide",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 411 makes the guide open the locked door it is walking into. Build 410 judged whether a door was "
    "on the guide's way by the vanilla navigator's path and target, but the guide walks by its own scripted "
    "step and its own mobility routes too - and whenever one of those was driving, both were empty, so every "
    "door was declared off the way and a locked one was never opened: open doors passed, closed ones did not "
    "open at all. Now there is a third answer, and it is geometry rather than bookkeeping: a door ahead of a "
    "moving guide, in a narrow cone at arm's length, and a door within reach of a standing one. A locked door "
    "in the wall beside the road still stays shut."
)
PATCHINFO = (
    "Сборка 411 открывает проводнику запертую дверь, в которую он идёт. В 410-й я решал «эта дверь на его "
    "пути?» по ванильному навигатору — по узлам пути и по его цели. Но проводник ходит ещё и своим "
    "скриптовым шагом, и своими маршрутами обхода, а тогда путь и цель пусты — и любая дверь объявлялась «не "
    "на пути». Запертую дверь этот вопрос касается, обычную нет, поэтому наружу это выглядело ровно как у "
    "вас: через открытые проходит, закрытые не открывает. Теперь есть третий ответ, и это геометрия, а не "
    "бухгалтерия: дверь впереди идущего проводника в узком конусе на расстоянии руки, и дверь в пределах "
    "досягаемости у стоящего. Запертая дверь в стене сбоку от дороги по-прежнему остаётся закрытой.\n"
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
    srcroot = root / "out411"
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
