#!/usr/bin/env python3
"""
Build 405 - the door stays open while a guide is in it, and the guide is taken out of the doorway.

The author after build 404: «все еще стоит перед открытой дверью, открывает и закрывает ее. Я попробовал убрать
дверной проем - оставить только дверь, дак он даже не может эту дверь обойти, просто стоит в ней открывает и
закрывает, а потом пропускает точку».

Read as one thing, that is build 404's own rule, not the path finder:

  * the guide walks into the doorway **while the door is open** - an open door has no collision, so the
    doorway is a normal block to stand in;
  * ten seconds later (`CLOSE_AFTER`) the pass closed that door as "stale", **with the guide inside it**. A
    living body inside a solid door block cannot walk out: every horizontal move is a collision and the game
    only slowly pushes it towards the closest free space. The guide stands in a shut door;
  * the pass opens it again (a guide at a door), the guide takes half a step, ten seconds later it is shut
    on it again - «открывает и закрывает»; and every one of those attempts is a failed step of the guide's
    own walk, so after ten of them the engine drops the point - «а потом пропускает точку».

Build 405 removes the clock and adds two things that make the state impossible:

  * a door is **never** closed while a guide is within `GUIDE_SAFE` - the age of the record no longer
    closes anything (a guide that died is not a guide, and `guideNear` already answers for it);
  * the door a guide is **standing in** (feet block or head block) is opened again on every pass
    (`holdOccupied`), so not even another tick of somebody else's rule can shut a body inside a door;
  * and a guide that does not walk out of a doorway is **carried out** of it (`Cross`): a latch per guide
    that orders the step along the way the guide was going, and if the body has not moved a fifth of a
    block by the next pass, moves it 0.85 blocks along that same direction - into cells that were checked
    first (feet, head, and the floor under them), never into a wall, never into a shut door.

Also tightened: the doors opened ahead of a walking guide are asked for 2.5 blocks away inside a narrow
cone (build 404 used four blocks and half a circle, which opened doors the guide was merely walking past).

Base 404: three classes replaced (`NpcDoors368`, and both of its inner classes, whose records changed) plus
one new inner class (`NpcDoors368$Cross`), no mixins, no language keys.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build405-door-held-open"
OUT_NAME = "settledlands-0.8.0-build405-door-held-open.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/NpcDoors368$Watch.class",
)
ADDED = (
    "dev/settledlands/door/NpcDoors368$Cross.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # a door held open under a guide, a never-closing rule, and a latch that takes the guide out of it.
    "dev/settledlands/door/NpcDoors368.class": ("openAhead", "holdOccupied", "crossStep", "nudge",
                                                "pathState"),
    "dev/settledlands/door/NpcDoors368$Cross.class": ("fx", "nudges"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 405 holds a guide's door open and takes the guide out of the doorway. Build 404 opened the door "
    "ahead of the guide and walked it through - and the author saw a door opening and shutting over and "
    "over, with the guide inside it. The cause was build 404's own rule: a door left open for ten seconds "
    "was closed as stale, even with a guide standing in it, and a living body inside a solid door block "
    "cannot walk out. Nothing closes a door a guide is at any more (a guide that died no longer counts as "
    "one), the door a guide is standing in is opened again on every pass, and a guide that does not walk "
    "out of a doorway is carried out of it: the step is ordered along the way it was going, and if the body "
    "has not moved by the next pass it is moved less than a block in that same direction, into cells that "
    "were checked first - never into a wall, never into a shut door. Doors are also no longer opened four "
    "blocks away for a guide that merely walks past them."
)
PATCHINFO = (
    "Сборка 405 держит дверь проводника открытой и выводит его из проёма. В 404 дверь открывалась заранее и "
    "проводник проводился через неё — а автор увидел дверь, которая открывается и закрывается, и проводника "
    "внутри неё. Причина была в правиле самой 404: дверь, открытая дольше десяти секунд, закрывалась как "
    "«забытая», даже если проводник стоял в ней, а тело внутри сплошного блока двери выйти не может. Теперь "
    "ничто не закрывает дверь, у которой стоит проводник (погибший проводник проводником больше не считается), "
    "дверь, в которой стоит проводник, открывается заново на каждом проходе, а тот, кто не выходит из проёма "
    "сам, выводится: шаг отдаётся по тому направлению, куда он шёл, а если тело не сдвинулось к следующему "
    "проходу — оно переносится меньше чем на блок в ту же сторону, только в проверенные клетки: не в стену и "
    "не в закрытую дверь. Двери также больше не открываются за четыре блока для проходящего мимо проводника.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out405"

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
