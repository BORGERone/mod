#!/usr/bin/env python3
"""
Build 390 - the two outputs of the door card are actually drawn, including on doors that existed before.

The author's report, with a screenshot: «сейчас визуально не видно узлы, так-же я не смог нащупать синий
узел, исправь это». The screenshot shows a door card wired straight to a «Реплика», and no pulse and no
blue target anywhere - and that is exactly what build 389 did to a door whose graph already existed: the
starter shape (card -> «Импульс» -> «Цель») was drawn only for a brand-new graph, and a door that had been
edited in build 382..388 kept its old graph untouched. The author had no way to see the two nodes he had
just asked for.

Two changes, both small:

  * `DoorGraph382#wireStarter` now builds the shape for a graph that already has a chain: the pulse is
    placed in a free spot next to the card, the blue target beside the pulse, and the chain that used to
    leave the card now leaves the pulse (`signal.next = door.next`, `door.next = signal`), with «Начало»
    moved onto the pulse. Nothing the author wired is lost, and the two nodes are on the canvas.
  * the drawing is done <b>once per door</b>, remembered in the graph store (`DoorGraph382Store#starter`
    under the `S` key, saved with the graphs). So the nodes appear on the first open after this build for
    every door that never got them - and a node the author deletes afterwards stays deleted, because the
    door is already marked done.

Base 389. Two classes replaced (`DoorGraph382`, `DoorGraph382Store`), nothing added, nothing removed, no
new mixin, no new language key.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build389-door-signal-target"
NEW_VERSION = "0.8.0-build390-door-outputs-visible"
OUT_NAME = "settledlands-0.8.0-build390-door-outputs-visible.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorGraph382Store.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorGraph382.class": (
        "wireStarter", "freeSpot", "starterDone", "markStarter"),
    "dev/settledlands/door/DoorGraph382Store.class": (
        "starterDone", "markStarter"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 390 makes the door card's two outputs actually appear: the pulse and the blue target are drawn "
    "on the first open of a door whose graph was made before build 389 - the pulse in a free spot next to "
    "the card, the target beside it, and the chain that used to leave the card now leaves the pulse, so "
    "nothing that was wired is lost. The shape is drawn once per door and remembered in the world's door "
    "store, so a node deleted afterwards stays deleted. No other behaviour changes: the pulse still fires "
    "only on a solved lock panel, and the blue target still carries the name of the player who did it."
)
PATCHINFO = (
    "Сборка 390: два выхода карточки двери действительно появляются. «Импульс» и синяя «Цель» рисуются "
    "при первом открытии графа двери, сделанной до 389-й: импульс ставится в свободное место рядом с "
    "карточкой, цель — рядом с импульсом, а цепочка, которая раньше выходила из карточки, теперь выходит "
    "из импульса (ничего из нарисованного не теряется, «Начало» переезжает на импульс). Форма рисуется "
    "один раз на дверь и запоминается в хранилище графов дверей, поэтому удалённая потом нода не "
    "возвращается. Остальное поведение прежнее: импульс срабатывает только на собранную панель, а синяя "
    "«Цель» хранит ник того, кто взломал.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out390"

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
