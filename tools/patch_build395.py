#!/usr/bin/env python3
"""
Build 395 - a door hacked open stays open while the player is at it.

The author, on build 394: «чето немного странное происходит, дверь после взлома сама закрывается через секунд
5-10», with a server log where the same thing happens twice, to the millisecond: `door_hack` at 14:52:57.623
and `the guard closed the locked door` at 14:53:05.666; again 14:53:16.414 -> 14:53:24.463. That is build
377's eight-second grace (`OPEN_FOR_MS = 8000L`) running out - a guess at how long a player needs, not a rule.

  * `DoorHack377` remembers a cracked door as a plain mark (a set of keys, no timestamps) and answers the
    guard with `crackedOpen`: this mod cracked it, it is still a door, it is still open, and a player is
    within sixteen blocks of it. The rule itself is `keepOpen(marked, door, open, near)`, pure and testable;
  * a mark is taken back the moment any of those four facts stops holding, so it can never outlive the open
    it was made for - a door the player closed is a door the guard may close;
  * `DoorGuard371` asks that question instead of the timestamped one. Its promise is unchanged and still
    kept: a locked door does not stand open in an empty room, and a guide still walks through (371's own
    rule). What changed is that the door no longer closes under the player who just hacked it;
  * no clock is left in the rule anywhere: `OPEN_FOR_MS` and the 8000 go, `cracked()` (the timestamped
    question) goes with them.

Base 394. Two classes (`DoorHack377`, `DoorGuard371`), no new key, no new mixin, nothing else.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build394-door-event-pass"
NEW_VERSION = "0.8.0-build395-door-stays-open"
OUT_NAME = "settledlands-0.8.0-build395-door-stays-open.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGuard371.class",
    "dev/settledlands/door/DoorHack377.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorHack377.class": ("crackedOpen", "keepOpen"),
    "dev/settledlands/door/DoorGuard371.class": ("crackedOpen", "guideOpened"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 395 makes a door hacked open stay open. Build 377 let the guard of build 371 close a cracked door "
    "eight seconds after the panel was solved - the author saw exactly that: «дверь после взлома сама "
    "закрывается через секунд 5-10». The rule has no clock now: a door this mod cracked open is left alone "
    "for as long as it stands open and a player is within sixteen blocks of it, and it is closed again once "
    "the room is empty, which is what the guard was built for. A guide still walks through a locked door."
)
PATCHINFO = (
    "Сборка 395: дверь, взломанная панелью, больше не закрывается сама. 377-я разрешала стражу 371-й "
    "закрыть взломанную дверь через восемь секунд после решения панели — автор это и увидел: «дверь после "
    "взлома сама закрывается через секунд 5-10». Теперь в правиле нет часов: дверь, которую открыл этот мод, "
    "не трогают, пока она открыта и игрок в шестнадцати блоках от неё, а когда комната пуста — страж "
    "закрывает её, для этого он и сделан. Проводник по-прежнему проходит через запертую дверь.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out395"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("base jar has no entry to remove: %s" % name)
        order.remove(name)
        del blobs[name]
        print("  removed: %s" % name)

    for name in REPLACED + ADDED:
        src = srcdir / name
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
