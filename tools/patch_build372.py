#!/usr/bin/env python3
"""
Build 372 - the door build explains what it is doing.

Nothing about the mechanic changed in 371's behaviour: this build only makes the mod **say** what
happens, because "ничего не поменялось" can mean four different things and the author should not have to
guess which:

  * the item never registered      -> {@code door: settledlands:door_key registered} at start-up, or not;
  * the click never reaches a door -> {@code door: the key was used on <block> at <pos> - that is not a
                                       DoorBlock}, i.e. a modded door this check does not know;
  * the door is locked and refused -> {@code door: the click on the locked door at <pos> was refused},
                                       with whether the door is open right after the refusal;
  * the key toggled the lock       -> {@code door: the door at <pos> is locked/unlocked by the key,
                                       N place(s) locked};
  * something else opened it       -> {@code door: the guard closed the locked door at <pos>}.

Eight lines per session, then quiet: a report, not a spam. Base 371, ten classes replaced.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build371-door-shut"
NEW_VERSION = "0.8.0-build372-door-log"
OUT_NAME = "settledlands-0.8.0-build372-door-log.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorStore368.class",
    "dev/settledlands/door/Doors368.class",
    "dev/settledlands/door/DoorKey368.class",
    "dev/settledlands/door/NpcIds368.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/DoorTab370.class",
    "dev/settledlands/door/DoorGuard371.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"


CHANGELOG_EN = (
    "Build 372 makes the door mechanic report what it does, so a report of 'nothing changed' can be "
    "answered from the log instead of guessed at: the key's registration at start-up, every lock toggle "
    "with its position and the number of locked places, every refused click with the door's open state "
    "right after the refusal, a key used on a block that is not a DoorBlock, and every door the guard "
    "closed. Eight lines per session. No behaviour changed from 371."
)
PATCHINFO = (
    "Сборка 372 заставляет механику дверей рассказывать, что она делает: регистрация ключа при "
    "запуске, каждое запирание и отпирание с координатами и числом запертых мест, каждый отказанный "
    "клик с состоянием двери сразу после отказа, попытка ключом по блоку, который не является дверью, "
    "и каждая дверь, которую закрыл сторож. Восемь строк за сессию. Поведение не менялось по "
    "сравнению с 371.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out372"

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
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for name in EXTRA:
        src = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / EXTRA_DIR / name.split("assets/settledlands/", 1)[1]
        if not src.is_file():
            raise SystemExit("missing asset: %s" % src)
        data = src.read_bytes()
        if name in blobs:
            raise SystemExit("base jar already has %s" % name)
        order.append(name)
        blobs[name] = data
        print("  asset: %s (%d bytes)" % (name, len(data)))

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
