#!/usr/bin/env python3
"""
Build 388 - a door's graph is a stock graph: the same node types, the same interpreter, the same cards.

The author's report: «мне не нравится реализация нод в графе двери, сделай реализацию как в других
графах». He was right. Build 386 had invented three node types of this mod's own («Открытие», «Кто
открыл», «В радиусе») with their own cards, their own inspector rows and their own little walk, and a door's
graph therefore worked unlike every other graph in the mod.

What a block's graph has done since build 351 is the answer, and build 388 gives the door exactly that:

  * **palette** - `BlockMenu363` has one row set now. A door's create menu <b>is</b> the stock list: the
    eleven rows a block can build (`BlockTrigger351#PALETTE_ROWS`), in the same order, with the stock
    labels and the stock colours. No row of ours is in it.
  * **nodes** - the palette creates real stock types, with their stock cards, their stock inspector rows
    and their stock fields: «Реплика», «Кнопка», «Триггер», «Условие If», «Переменная», «В радиусе»,
    «Время», «Таймер», «Случайность», «Катсцена», «Цель».
  * **runner** - `DoorSignal388` starts the chain at the node marked «Начало» and hands it to
    `BlockTrigger351#fire`, the very interpreter a block uses. So a «Триггер» with `%player%` in its
    action <b>is</b> the «кто открыл» the author asked for two builds ago, in the stock placeholder
    rather than in a type of ours; «Переменная» moves the same `NpcPoints` the NPCs use; «Условие If»
    compares and branches; «Реплика» and «Кнопка» speak through the stock window with the door as the
    speaker; «Катсцена» plays for the opener.
  * **one type of ours, like a block** - the door card (`DoorGraph382#DOOR_TYPE`), which is the door's
    settings, exactly the role `BlockName359#TYPE` plays for a block's name. It is created with the graph,
    it is not in the palette, and it is the only card this mod paints.
  * **migration** - a graph made by build 386 still holds types 41-43, which no longer run. They are
    dropped the first time the graph is opened (`DoorGraph382#migrate`), instead of sitting in it as
    nodes that do nothing.

Removed: `dev/settledlands/door/DoorGraph386.class` (the build-386 walk). Added: `DoorSignal388.class`.

Base 387. Nine classes replaced, one added, one removed, no new mixin, no new language key beyond the
re-pitched palette note.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build387-panel-release"
NEW_VERSION = "0.8.0-build388-door-stock-nodes"
OUT_NAME = "settledlands-0.8.0-build388-door-stock-nodes.jar"

REMOVED = (
    "dev/settledlands/door/DoorGraph386.class",
)
REPLACED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorHack377.class",
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/npc/client/DoorCard382.class",
    "dev/settledlands/npc/client/DoorGraphClient382.class",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
ADDED = (
    "dev/settledlands/door/DoorSignal388.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorSignal388.class": (
        "startNode", "BlockTrigger351", "BlockTalk358"),
    "dev/settledlands/door/DoorGraph382.class": (
        "migrate", "typeForRow", "paletteRows"),
    "dev/settledlands/npc/client/BlockMenu363.class": (
        "doorType", "typeForRow"),
    "dev/settledlands/npc/client/DoorCard382.class": (
        "difficulty", "gui.settledlands.door.hint"),
    "dev/settledlands/npc/client/DoorGraphClient382.class": (
        "migrate", "graphOf"),
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class": (
        "doorType", "added_node"),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "isDoorNode", "propertyChange"),
    "dev/settledlands/door/DoorEvents368.class": (
        "DoorSignal388",),
    "dev/settledlands/door/DoorHack377.class": (
        "DoorSignal388",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {
    "assets/settledlands/lang/ru_ru.json": {
        "gui.settledlands.door.palette": ("Ноды двери: Дверь, Открытие, Кто открыл, В радиусе",
                                          "У двери те же ноды, что у блока"),
    },
    "assets/settledlands/lang/en_us.json": {
        "gui.settledlands.door.palette": ("Door nodes: Door, Opened, Opened by, Within radius",
                                          "A door offers the same nodes as a block"),
    },
}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 388 makes a door's graph a stock graph. The create menu of a door is now the same list a "
    "block's graph offers - «Реплика», «Кнопка», «Триггер», «Условие If», «Переменная», «В радиусе», "
    "«Время», «Таймер», «Случайность», «Катсцена», «Цель» - with the stock labels, colours, cards and "
    "inspector rows, and those nodes run through the same interpreter a block's graph uses: the chain "
    "starts at the node marked «Начало» and everything behaves as it does elsewhere, including «Триггер» "
    "with %player% filled in as the player who opened the door. The only node of this mod's own left in a "
    "door's graph is the door card (keys, speed, difficulty, open/closed), which is created with the graph "
    "the way a block's «Имя» card is. The three invented types of build 386 are gone and are dropped from "
    "an older graph the first time it is opened."
)
PATCHINFO = (
    "Сборка 388: граф двери — обычный граф. Меню создания у двери теперь то же, что у блока («Реплика», "
    "«Кнопка», «Триггер», «Условие If», «Переменная», «В радиусе», «Время», «Таймер», «Случайность», "
    "«Катсцена», «Цель») — со штатными названиями, цветами, карточками и строками инспектора, — и эти "
    "ноды выполняются тем же интерпретатором, что у блока: цепочка начинается с ноды «Начало», «Триггер» "
    "с %player% получает того, кто открыл дверь, «Переменная» двигает те же NpcPoints, что и у NPC, "
    "«Условие If» сравнивает и ветвится, «Реплика»/«Кнопка» говорят через штатное окно с дверью в роли "
    "говорящего, «Катсцена» играет для открывшего. Единственная своя нода в графе двери — карточка двери "
    "(клавиши, скорость, сложность, закрыта/открыта): она создаётся вместе с графом, как карточка «Имя» у "
    "блока. Три выдуманных типа из 386-й убраны и вычищаются из старого графа при первом открытии.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out388"

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
