#!/usr/bin/env python3
"""
Build 368 - locked doors, and guides that open and close doors while walking.

Base 367. Two halves of one mechanic, as the author described it: a special mod key that makes a door
closed for the players and unbreakable for the monsters, while the guides keep walking through it - and
guides that open a door they walk into and close it behind themselves.

  * <b>the key</b> - {@code settledlands:door_key}, one plain item registered on the mod bus exactly the
    way the mod already registers its mob effect ({@code PossessionSystem$ModBus} + {@code RegisterEvent}).
    The item does nothing by itself; the rule is in DoorEvents368, which asks whether the thing in the
    hand is the key.
  * <b>the lock</b> - {@code DoorStore368}, world data keyed by dimension and position, both halves of a
    door written and read, so it does not matter which half the player hits.
  * <b>closed to players</b> - {@code DoorEvents368} cancels the right click on a locked door (and says
    "Дверь заперта" in the action bar), cancels the break event, and {@code DoorBreak368Mixin} guards
    {@code Level#destroyBlock} so the zombie that bashes a door cannot remove it either.
  * <b>open to guides</b> - {@code NpcDoors368} opens a closed door a walking guide has come up to,
    remembers it, and closes it again when no guide is near; {@code GuideNpcDoor368Mixin} answers the
    path finder's "can you open doors?" with yes, so the path goes through the doorway instead of round
    the house.

Nine classes added, two mixins listed as common; the two assets of the item and four strings are added
to the jar.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build367-zone-default"
NEW_VERSION = "0.8.0-build368-door-lock"
OUT_NAME = "settledlands-0.8.0-build368-door-lock.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    # the lock, the rules, the key item and the guide pass
    "dev/settledlands/door/DoorStore368.class",
    "dev/settledlands/door/Doors368.class",
    "dev/settledlands/door/DoorKey368.class",
    "dev/settledlands/door/NpcIds368.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    # the two rules that need a class hook: the path finder's answer, and the destroyed block
    "dev/settledlands/mixin/GuideNpcDoor368Mixin.class",
    "dev/settledlands/mixin/DoorBreak368Mixin.class",
)
MIXINS = ("GuideNpcDoor368Mixin", "DoorBreak368Mixin")
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/GuideNpcDoor368Mixin.class": ("canOpenDoors", "GuideNpc"),
    "dev/settledlands/mixin/DoorBreak368Mixin.class": ("destroyBlock", "DoorEvents368"),
}

LANG_SET = {
    "item.settledlands.door_key": "Ключ двери",
    "message.settledlands.door.locked": "Дверь заперта",
    "message.settledlands.door.key_on": "Дверь заперта ключом",
    "message.settledlands.door.key_off": "Дверь отперта",
}
LANG_SET_EN = {
    "item.settledlands.door_key": "Door Key",
    "message.settledlands.door.locked": "This door is locked",
    "message.settledlands.door.key_on": "Door locked",
    "message.settledlands.door.key_off": "Door unlocked",
}
LANG_CHANGE = {}

# Assets of the new item: the model and its 16x16 texture, mirrored from build303-src/assets368.
EXTRA = (
    "assets/settledlands/models/item/door_key.json",
    "assets/settledlands/textures/item/door_key.png",
)
EXTRA_DIR = "assets368"


CHANGELOG_EN = (
    "Build 368: locked doors. A new item, settledlands:door_key, locks a door for the players: the "
    "right click no longer opens it, the break event no longer breaks it, and the zombie that bashes "
    "doors is stopped at the level itself, so a locked door cannot be griefed. The guides are not "
    "affected - they walk through a locked door exactly as before. The same build teaches the guides "
    "their own doors: a walking guide opens a closed door it has come up to and closes it again once "
    "nobody is in the doorway, and the path finder now knows a guide can open doors, so it paths "
    "through the doorway instead of around the house."
)
PATCHINFO = (
    "Сборка 368: запертые двери. Новый предмет settledlands:door_key запирает дверь для игроков: ПКМ "
    "её больше не открывает, событие разрушения её не ломает, а зомби, ломающий двери, останавливается "
    "на уровне самого мира - запертую дверь загриферить нельзя. На НПС это не распространяется: они "
    "проходят через запертую дверь как раньше. Эта же сборка учит НПС своим дверям: идущий НПС "
    "открывает закрытую дверь, к которой подошёл, и закрывает её, когда в проёме никого нет, а "
    "поиск пути теперь знает, что НПС умеет открывать двери, и ведёт его через проём, а не вокруг дома.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out368"

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
