#!/usr/bin/env python3
"""
Build 403 - the guides walk through doors: through open ones, and through the closed ones they open.

The author, after build 402: «не только не проходят через закрытые двери, они не проходят даже через
открытые». Both halves are real, and they have three separate causes, all in the guide's door pass:

  * the **path finder was never told** that a guide may open a door. Build 368 answered that question on
    the mob (`GuideNpcDoor368Mixin` adds `canOpenDoors()`), but 1.21 does not ask the mob: `Mob` has no
    `canOpenDoors` (NeoForge 1.21 javadoc), and the flag the finder reads is `NodeEvaluator#canOpenDoors`,
    reached through the public `PathNavigation#getNodeEvaluator()`. The method the mixin adds is called by
    nothing - so every guide path was planned with doors shut and went round them or stopped at them, and
    the door the pass opened was never on the way;
  * the pass **needed the guide to be moving** (`moved < 0.05 -> continue`). A guide standing against a
    closed door does not move - the one mob the pass was written for was the one mob it never looked at.
    That is the deadlock the author photographed;
  * a path **computed while the door was closed stays ended at the door**. Opening it does not lengthen
    the path, so the guide stands in an open doorway with a finished path and goes nowhere.

Build 403 fixes all three with public API only:

  * `NpcDoors368#allowDoorNodes` sets `setCanOpenDoors(true)` on the guide's own node maker on every pass,
    so a closed wooden door is a doorway in the next path it computes;
  * the movement gate is gone; every guide is looked at four times a second, and a guide with an unfinished
    path that has not moved is looked at further (3 blocks instead of 1.9);
  * the pass opens the doors **on the guide's own path** (the next four nodes), so the door is open before
    the guide is pressed against it;
  * a guide that has stood still for a second with an unfinished path gets its path planned again to the
    same target (`PathNavigation#recomputePath`) - the door changed, not the destination;
  * `DoorGuard371` no longer shuts a locked door while a guide is within four blocks of it: a door that
    stands open for a player and is slammed the moment the player turns away is closed again by the time
    the guide walks up to it.

A locked door is still opened by guides only (the lock of 368 is a rule for players and monsters), the
door is still closed behind the guide, and a lock that nothing opened stays shut. No new mixin, no new
language keys, no new classes: two classes of the guide's door pass, the guard, and a comment on the 368
mixin whose method the game never asks for.

Base 402.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build402-open-after-close"
NEW_VERSION = "0.8.0-build403-doors-on-path"
OUT_NAME = "settledlands-0.8.0-build403-doors-on-path.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/DoorGuard371.class",
    "dev/settledlands/mixin/GuideNpcDoor368Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # doors on the path, the path finder's flag, and the path planned again - not a distance test.
    "dev/settledlands/door/NpcDoors368.class": ("openOnPath", "allowDoorNodes", "recomputePath"),
    # a locked door is not shut while a guide is walking through it.
    "dev/settledlands/door/DoorGuard371.class": ("guideNear",),
    "dev/settledlands/mixin/GuideNpcDoor368Mixin.class": ("canOpenDoors",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 403 makes the guides walk through doors. Three causes, all in the guide's door pass: the path "
    "finder was never told that a guide may open a door (in 1.21 that flag lives on the node evaluator, not "
    "on the mob, so the 368 answer was never asked for and every path went round a closed door), the pass "
    "only looked at guides that had already moved (a guide pressed against a closed door never moves, so the "
    "door was never opened for it), and a path planned at a closed door stays ended at that door (so an open "
    "doorway still led nowhere). Now the flag is set on the guide's own node maker, every guide is looked at "
    "four times a second, the doors on the guide's path are opened ahead of it, and a guide that stands still "
    "at a door has its path planned again now that the door is open. A locked door is no longer shut while a "
    "guide is walking through it."
)
PATCHINFO = (
    "Сборка 403 проводит NPC через двери. Причин было три, и все три — в проходе дверей для проводников: "
    "поиск пути никогда не узнавал, что проводник умеет открывать двери (в 1.21 этот признак живёт на "
    "оценщике узлов, а не на мобе, поэтому ответ 368-й сборки никто не спрашивал, и любой путь обходил "
    "закрытую дверь стороной); проход смотрел только на тех, кто уже сдвинулся (проводник, упёршийся в "
    "закрытую дверь, не двигается — значит, дверь для него не открывалась никогда); а путь, построенный "
    "при закрытой двери, заканчивается у этой двери (поэтому и открытый проём никуда не вёл). Теперь "
    "признак ставится оценщику узлов самого проводника, каждый проводник осматривается четыре раза в "
    "секунду, двери на его пути открываются заранее, а тому, кто встал у двери, путь строится заново — "
    "дверь уже открыта. Запертую дверь перестали захлопывать, пока проводник идёт через неё.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out403"

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
