#!/usr/bin/env python3
"""
Build 406 - no teleport, and the door is open twelve blocks before the guide reaches it.

The author's video of build 405: a guide on patrol between two points walks, stops in a doorway, **jumps**,
walks back, and now and then arrives («то идет к ним, но телепортируется между дверьми, то возвращается
назад и т.д. Но через раз и доходит докуда надо»).

The jumps were build 405's own: when ordering the step did not move the guide, it moved the body 0.85 blocks.
That is gone - `nudge`, the free-cell test written for it and the game call it needed are not in the class any
more.

«Через раз и доходит» is the real report, and it is about **when** the door is open relative to when a leg of
the walk is planned:

  * 403 opened a door at the guide's arm's length, 404 four blocks ahead in a half-circle, 405 two and a half;
  * a leg is planned when the leg starts - up to a dozen blocks before the doorway - and a path planned at a
    closed door goes round the house or ends at the door;
  * so whether the guide arrives depended on whether the door happened to be open at planning time. Half the
    time it was open (the pass had opened it for a previous leg), half the time it was not - «через раз».

Now the door is opened **twelve blocks ahead of the guide, on the line to the point its navigator is taking
it** (`openAhead` + `inCorridor`), so every leg of every patrol is planned at an open doorway. And such a door
is **not** shut again on the next pass just because the guide is still far from it (`needed`): a door opened
for a guide is closed when no guide is at it *and* it stands on nobody's line any more.

The latch of 405 stays - it is the answer to "the door is open and the guide is not moving" - but it only
orders steps now, and it starts only when the doorway really is in the way (the guide is inside it, or the
door stands between the guide and its target), so it can no longer push a guide backwards at the end of a
patrol.

Base 405: four classes replaced (`NpcDoors368` and its three inner classes), nothing added, no mixins, no
language keys, and one confirmation removed from `check_mixin_class` together with the call it was written
for.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build405-door-held-open"
NEW_VERSION = "0.8.0-build406-doors-open-ahead"
OUT_NAME = "settledlands-0.8.0-build406-doors-open-ahead.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/NpcDoors368$Watch.class",
    "dev/settledlands/door/NpcDoors368$Cross.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # a door held open under a guide, a never-closing rule, and a latch that takes the guide out of it.
    "dev/settledlands/door/NpcDoors368.class": ("openAhead", "holdOccupied", "crossStep", "inCorridor",
                                                "needed", "pathState"),
    "dev/settledlands/door/NpcDoors368$Cross.class": ("fx", "fz"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 406 takes the teleports out and opens the door early enough. Build 405 moved a guide 0.85 blocks "
    "when the step it ordered did not move it, and the author's video shows exactly that: a patrol guide "
    "jumping between doorways. There is no teleport any more. The other half of the report - a guide that "
    "arrives only half the time - is about when the door is open: a leg of the walk is planned when the leg "
    "starts, up to a dozen blocks before the doorway, and a path planned at a closed door goes round the "
    "house or ends at the door. Doors are now opened twelve blocks ahead of the guide, on the line to the "
    "point its navigator is taking it, and they are not shut again while they still stand on somebody's "
    "line. A door is closed when no guide is at it and it is nobody's doorway any more."
)
PATCHINFO = (
    "Сборка 406 убирает телепорты и открывает дверь достаточно рано. В 405 проводник переносился на 0,85 "
    "блока, если отданный шаг его не сдвигал, — и на видео автора видно ровно это: патрульный проводник "
    "прыгает между дверьми. Телепорта больше нет. Вторая половина отчёта — «через раз и доходит» — про то, "
    "когда дверь открыта: участок пути строится тогда, когда он начинается, за десяток блоков до двери, а "
    "путь, построенный при закрытой двери, идёт в обход дома или заканчивается у двери. Теперь двери "
    "открываются за двенадцать блоков до проводника, на линии к точке, куда его ведёт навигатор, и не "
    "закрываются, пока стоят на чьей-то линии. Дверь закрывается, когда у неё нет проводника и она больше "
    "никому не проём.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out406"

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
