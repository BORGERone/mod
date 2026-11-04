#!/usr/bin/env python3
"""
Build 369 - the door build that starts.

Build 368 did not launch. The log says it in one line:

    Mixin apply for mod settledlands failed settledlands.mixins.json:DoorBreak368Mixin
      -> net.minecraft.world.level.Level: InvalidInjectionException
      could not find any targets matching 'destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z'

`destroyBlock(BlockPos, boolean)` is a **default method on the `LevelWriter` interface** in 1.21.1
(the javadoc of `LevelWriter` lists it among the defaults), and `Level` does not declare it, so there
was nothing in the class for the injection to attach to. A mixin that cannot attach is not a warning:
it is a failed start.

What changed here, and nothing else in the mechanic:

  * `DoorBreak368Mixin` now injects into the three overloads a level really declares or inherits -
    `destroyBlock(BlockPos, boolean, Entity, int)`, `destroyBlock(BlockPos, boolean, Entity)` and
    `destroyBlock(BlockPos, boolean)` - plus `removeBlock(BlockPos, boolean)`, which is the path an
    explosion takes. Every one of them is declared `require = 0`, so a shape that this exact game
    version does not have is skipped instead of stopping the boot. Lesson recorded in the mixin and
    enforced by `tools/door_lock_check.py`: an injection that cannot be verified offline must never be
    able to kill the launch.
  * Everything else is byte for byte build 368: the key item, the store, the right click and break
    events, the guide that opens and closes doors, the guide's answer to the path finder.

Base 368, nine classes replaced, no assets or strings touched.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build368-door-lock"
NEW_VERSION = "0.8.0-build369-door-fix"
OUT_NAME = "settledlands-0.8.0-build369-door-fix.jar"

REMOVED = ()
# The nine classes of the mechanic, rebuilt with the injection fix.
REPLACED = (
    "dev/settledlands/door/DoorStore368.class",
    "dev/settledlands/door/Doors368.class",
    "dev/settledlands/door/DoorKey368.class",
    "dev/settledlands/door/NpcIds368.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/mixin/GuideNpcDoor368Mixin.class",
    "dev/settledlands/mixin/DoorBreak368Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/GuideNpcDoor368Mixin.class": ("canOpenDoors", "GuideNpc"),
    "dev/settledlands/mixin/DoorBreak368Mixin.class": ("destroyBlock", "DoorEvents368"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"


CHANGELOG_EN = (
    "Build 369 fixes the start of build 368. The mob guard injected into "
    "Level#destroyBlock(BlockPos, boolean) - an overload that 1.21.1 declares as a default method on "
    "the LevelWriter interface, not on Level - so the mixin had no target and the game refused to "
    "boot. The guard now covers the destroyBlock overloads a level does declare plus removeBlock, each "
    "marked optional so a shape this game version lacks is skipped instead of stopping the launch. The "
    "door mechanic itself is unchanged: the key locks a door for the players, a locked door cannot be "
    "broken or blown up, and the guides walk through it and close the doors they open."
)
PATCHINFO = (
    "Сборка 369 исправляет запуск 368. Охрана от мобов вставлялась в "
    "Level#destroyBlock(BlockPos, boolean) - а в 1.21.1 эта перегрузка объявлена как default-метод "
    "интерфейса LevelWriter, а не класса Level, поэтому миксин не нашёл цель и игра не запускалась. "
    "Теперь охрана стоит на тех перегрузках destroyBlock, которые класс действительно объявляет, плюс "
    "на removeBlock (через него ломает взрыв), и каждая помечена как необязательная: если в этой "
    "версии игры такой сигнатуры нет, она просто пропускается, а не убивает запуск. Сама механика "
    "двери не изменилась: ключ запирает дверь для игроков, запертую дверь нельзя сломать и взорвать, "
    "а НПС проходят через неё и закрывают за собой те двери, которые открыли.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out369"

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
