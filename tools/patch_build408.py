#!/usr/bin/env python3
"""
Build 408 - a locked door on the guide's path is opened for the guide.

The author after 407: «теперь нпс не может открыть дверь и стоит перед ней». He is right, and it is 407's
fault: 407 refused **every** locked door ("a locked door is never opened, not for a tick"), borrowing a rule
that was written for players and monsters. His test door is locked - the log of build 406 shows the pass
opening `BlockPos{x=62, y=64, z=-237}` and the locked-door guard calling the same door locked - so the guide
stopped in front of the door it used to open.

The rule he settled on, in his own words: «нпс должен открывать все двери на его пути, в т.ч. и запертые».
That is the rule 368/403 already followed («a guide is allowed to open a locked door and walk through it»),
and it is what this build restores, with one distinction kept:

  * a locked door **on the guide's path** (a node of its path, or a locked door between it and the place it
    was walking to) is opened for the guide and shut again behind it;
  * a locked door that merely stands next to the guide - not on its way anywhere - is nobody's doorway and
    stays shut, so the pass cannot become a key that opens the whole village.

Nothing else changes: the door is still kept open until the guide is through it, both door switches are still
set on the node maker, and there is still no push and no body move. Base 404: one class replaced.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build408-locked-on-path"
OUT_NAME = "settledlands-0.8.0-build408-locked-on-path.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
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
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 408 lets a guide open a locked door that is on its path. Build 407 refused every locked door, "
    "borrowing a rule that was written for players and monsters, and the guide stopped in front of the very "
    "door it had opened before - the test door is locked. The author's rule, in his words: the guide must "
    "open every door on its way, locked ones included. So a locked door on the guide's path is opened for it "
    "and shut again behind it, while a locked door that merely stands next to the guide stays shut. "
    "Everything else is as in 407: the door is kept open until the guide is through it, the path finder is "
    "told both that a closed wooden door may be opened and that an open wooden door is plain floor, and no "
    "body is ever moved or teleported."
)
PATCHINFO = (
    "Сборка 408 открывает проводнику запертую дверь, если она стоит на его пути. В 407-й я запретил "
    "открывать любую запертую дверь — это правило написано для игроков и монстров, — и проводник встал "
    "перед той самой дверью, которую до этого открывал: ваша тестовая дверь заперта. Ваше правило: нпс "
    "должен открывать все двери на его пути, в том числе запертые. Теперь запертая дверь на пути "
    "проводника открывается для него и закрывается за спиной, а запертая дверь, которая просто стоит "
    "рядом и никуда не ведёт, остаётся закрытой — иначе проход стал бы ключом от всей деревни. Остальное "
    "как в 407-й: дверь держится открытой, пока проводник не пройдёт, поиску пути сказаны обе вещи про "
    "двери, и никаких переносов тела.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out408"

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
