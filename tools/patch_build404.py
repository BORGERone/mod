#!/usr/bin/env python3
"""
Build 404 - the guide walks through the doorway it just opened.

The author after build 403: «всеравно нпс доходит до двери, теперь открывает, но дальше открытой двери не
проходит». The door opens now; the guide still stops at it. Two things were wrong, and neither is about the
pass running:

  * the door was opened when the guide was **already at it** (1.9 blocks). By then the path the guide is
    following was planned at a closed door and it **ends** at the door - a finished path does not become a
    path through an open doorway. The doors are now opened **ahead** of the guide (4 blocks, inside a cone
    around the direction the navigator is taking it), so the next leg of the walk is planned against an
    open doorway;
  * and a guide that does stop at a door it has opened is walked through it: a short path to the point just
    past the doorway, and if the path finder still refuses, the step is ordered straight on the move
    control - the object that actually moves a mob. A walk, not a teleport.

The stuck line now reports the navigator's own state - node count, next node, path target, whether the guide
is pressed against a block - so "the path ended at the door" and "the path goes through and something else
holds the guide" can be told apart from the log.

Base 403: one class replaced (`NpcDoors368`), no new mixin, no new classes, no language keys.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build403-doors-on-path"
NEW_VERSION = "0.8.0-build404-through-the-doorway"
OUT_NAME = "settledlands-0.8.0-build404-through-the-doorway.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
)
ADDED = (
    "dev/settledlands/door/NpcDoors368$Watch.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # doors opened ahead of the guide, and a guide walked through the doorway it stopped at.
    "dev/settledlands/door/NpcDoors368.class": ("openAhead", "pushThrough", "openDoorInReach",
                                                "pathState"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 404 walks the guides through the doorway they open. A door was opened only when a guide was "
    "already at it, and by then the path the guide follows was planned at a closed door and ends there - a "
    "finished path does not turn into a path through an open doorway. Doors are now opened ahead of a "
    "walking guide, in the direction its navigator is taking it, so the next leg is planned against an open "
    "doorway; and a guide that stops at a door it has opened gets a short path to the point just past the "
    "doorway, or a step ordered straight on its move control if the path finder still refuses. It walks "
    "through, it is not teleported. The stuck line in the log now reports the navigator's own state: node "
    "count, next node, path target, and whether the guide is pressed against a block."
)
PATCHINFO = (
    "Сборка 404 проводит проводника через проём, который он открыл. Дверь открывалась только тогда, когда "
    "проводник уже стоял у неё, — а путь, которым он идёт, был построен при закрытой двери и заканчивается "
    "у двери: законченный путь не становится путём через открытый проём. Теперь двери открываются заранее, "
    "впереди идущего проводника, по направлению его навигатора, — следующий участок пути строится уже через "
    "открытую дверь. А тот, кто всё-таки встал у открытой двери, получает короткий путь за проём или шаг, "
    "отданный прямо в управление движением, если поиск пути всё ещё отказывается. Он проходит, а не "
    "телепортируется. Строка в логе про «стоит» теперь показывает состояние навигатора: сколько узлов в "
    "пути, какой следующий, куда путь вёл и упирается ли проводник в блок.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out404"

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
