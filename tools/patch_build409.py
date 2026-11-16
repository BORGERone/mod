#!/usr/bin/env python3
"""
Build 409 - the guide's body is 0.6 blocks wide, so a doorway is a doorway for it.

The author after 408: «персонаж застревает в дверном проёме с открытой дверью, дальше не идет». Different
symptom from every build before it: the door now opens and the path goes through it - the guide reaches the
doorway and is stuck <b>inside</b> it. That is a body problem, not a door problem.

`NpcContent` declares the guide as `sized(0.675F, 2.1F)`: a body 0.675 blocks wide. Two consequences, both
fatal in a one-block doorway:

  * vanilla's `WalkNodeEvaluator` types a wooden door as a walkable doorway only when the mob is **under 0.6**
    blocks wide. At 0.675 a door is not a door to the path finder at all, whatever switches are set on it -
    which is why every door attempt since 368 ended at the door;
  * and it physically cannot fit: an open door leaves about 0.81 blocks of opening, so a 0.675-wide body
    passes only if it goes through dead centre and never brushes the leaf. The author does pass it when he
    possesses the body, so it is possible - and that is exactly why this looks like "sometimes it works".

Build 407 read the guide's scale in the log and blamed the distance, not the body. The author's report here is
the one that separates them: he is IN the doorway, and 0.6 is the only number vanilla will treat as a mob that
walks through doors.

Base 404 again, for one build only (change one thing at a time): the same `NpcDoors368` as build 408, and
`NpcContent` with `0.675F` replaced by `0.6F` - one constant in the class's float pool, 4 bytes, no
recompilation (the class is otherwise untouched: the constant pool keeps its indices and its sizes).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build409-body-fits-doorway"
OUT_NAME = "settledlands-0.8.0-build409-body-fits-doorway.jar"

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
                                                "setCanPassDoors"),
    # the body that actually fits a doorway - vanilla lets a mob through a door only under 0.6 blocks.
    "dev/settledlands/npc/NpcContent.class": ("settledlands:guide",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 409 makes the guide 0.6 blocks wide instead of 0.675. Vanilla's path finder treats a wooden door "
    "as a walkable doorway only for a mob under 0.6 blocks wide, so at 0.675 a door was never a door for the "
    "guide no matter what was set on its node maker - and the body physically only passes an open doorway "
    "dead centre, without brushing the leaf. The reported symptom is the one that separates the body from "
    "the door: the guide now reaches the doorway and sticks in it. The body is one constant in NpcContent, "
    "and it is the only thing this build changes; the door logic is exactly build 408's."
)
PATCHINFO = (
    "Сборка 409 делает тело проводника шириной 0.6 блока вместо 0.675. Ванильный поиск пути считает "
    "деревянную дверь проходимым проёмом только для моба уже 0.6 блоков, поэтому при 0.675 дверь никогда не "
    "была дверью для проводника — что бы ни было выставлено на его поиске пути; и физически тело проходит "
    "открытый проём только строго по центру, не задев полотно. Ваш отчёт и есть то, что отделяет тело от "
    "двери: он доходит до проёма и застревает внутри. Тело — это одна константа в NpcContent, и это "
    "единственное, что меняет сборка; логика дверей ровно такая же, как в 408-й.\n"
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
    # NpcDoors368 is the same class as build 408's; NpcContent is build 408's class with one float changed.
    srcroot = root / "out408"
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
