#!/usr/bin/env python3
"""
Build 386 - a door's graph grows up: four nodes, a signal on opening, and 20 seconds.

Three things the author asked for:

  «хочу расширить скорость до 20 секунд» - `DoorGraph382#MAX_SPEED` 10 -> 20. Nothing else had to move:
      the panel takes the seconds as they come, the log line prints them, the store keeps them.

  «хочу добавить больше нод в граф двери, вроде if, в радиусе и т.д.» - the create menu of a door's
      graph goes from one row to four, and every row is a node the door can really run: «Дверь»,
      «Открытие», «Кто открыл», «В радиусе». Stock rows stay out on purpose - a door cannot run a line
      of dialogue, and a palette that offers what cannot run is the lie build 382 refused. The «В
      радиусе» row is the stock «В радиусе» with the one meaning a door has for it: how close the player
      who opened the door must stand.

  «хочу добавить собственную ноду для графа двери "открытие"... так-же будет выходная синяя нода с
      ссылкой на того, кто конкретно открыл дверь» - `DoorGraph386` is that signal. On every real
      opening of a door (an empty-hand click on a shut, unlocked door, or a solved panel) the server
      walks the chain from the «Открытие» node along the stock `next` links:

        * «Кто открыл» (blue) - the name of the opener is written into the node, which is how the author
          sees in the editor that the chain ran and how a later node will read the player;
        * «В радиусе» - the gate: the chain stops unless the opener stands within the node's radius.

      The walk is bounded (32 steps), a node visited twice ends it, a second click of the same opening
      within 400 ms is ignored, and every failure is swallowed - the door is already open and nothing
      here may undo it.

Also in this build: the card painter draws all four types (brown door, amber signal, blue opener,
green radius) in the same node box, and the inspector parks the stock rows for all four and builds the
rows each one has - the door's three sliders and toggle, the radius slider, and read-only lines for the
two signal nodes.

Base 385. One class added, eight replaced, no new mixin.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build385-door-hack-difficulty"
NEW_VERSION = "0.8.0-build386-door-nodes"
OUT_NAME = "settledlands-0.8.0-build386-door-nodes.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorHack377.class",
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/npc/client/DoorCard382.class",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class",
    "dev/settledlands/mixin/NpcDoorCard382Mixin.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
ADDED = (
    "dev/settledlands/door/DoorGraph386.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorGraph386.class": (
        "fire", "MAX_STEPS", "setOpener", "isRadiusNode"),
    "dev/settledlands/door/DoorGraph382.class": (
        "EVENT_TYPE", "RADIUS_TYPE", "PALETTE", "setRadius"),
    "dev/settledlands/npc/client/BlockMenu363.class": (
        "doorType", "typeForRow"),
    "dev/settledlands/npc/client/DoorCard382.class": (
        "labelKey", "radius_line", "who_last"),
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class": (
        "doorType", "applyDefaults"),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "setRadius", "isWhoNode", "propertyChange"),
    "dev/settledlands/mixin/NpcDoorCard382Mixin.class": (
        "isDoorGraphNode", "DoorCard382"),
}

LANG_SET = {
    "gui.settledlands.door.event": "Открытие",
    "gui.settledlands.door.who": "Кто открыл",
    "gui.settledlands.door.radius": "В радиусе",
    "gui.settledlands.door.event_line1": "Сигнал: эту дверь открыли",
    "gui.settledlands.door.event_line2": "Сервер идёт по связи → дальше",
    "gui.settledlands.door.event_line3": "Срабатывает на настоящем открытии",
    "gui.settledlands.door.event_line4": "пустой рукой или через взлом",
    "gui.settledlands.door.who_line1": "Игрок, открывший дверь",
    "gui.settledlands.door.who_line3": "Синяя нода: выход со ссылкой",
    "gui.settledlands.door.who_none": "Ещё никто не открывал",
    "gui.settledlands.door.who_last": "Последний: %s",
    "gui.settledlands.door.radius_line": "Радиус: %s блоков",
    "gui.settledlands.door.radius_line2": "Проходит, если открывший ближе",
    "gui.settledlands.door.radius_line3": "Иначе цепочка встаёт здесь",
    "gui.settledlands.door.added_node": "%s добавлена",
}
LANG_SET_EN = {
    "gui.settledlands.door.event": "Opened",
    "gui.settledlands.door.who": "Opened by",
    "gui.settledlands.door.radius": "Within radius",
    "gui.settledlands.door.event_line1": "Signal: this door was opened",
    "gui.settledlands.door.event_line2": "The server follows the wire → onwards",
    "gui.settledlands.door.event_line3": "Fires on a real opening",
    "gui.settledlands.door.event_line4": "by an empty hand or a solved panel",
    "gui.settledlands.door.who_line1": "The player who opened the door",
    "gui.settledlands.door.who_line3": "Blue node: the output with the player",
    "gui.settledlands.door.who_none": "Nobody has opened it yet",
    "gui.settledlands.door.who_last": "Last: %s",
    "gui.settledlands.door.radius_line": "Radius: %s blocks",
    "gui.settledlands.door.radius_line2": "Passes when the opener is closer",
    "gui.settledlands.door.radius_line3": "Otherwise the chain stops here",
    "gui.settledlands.door.added_node": "%s added",
}
LANG_CHANGE = {
    "assets/settledlands/lang/ru_ru.json": {
        "gui.settledlands.door.palette": ("У двери доступна одна нода: Дверь",
                                          "Ноды двери: Дверь, Открытие, Кто открыл, В радиусе"),
    },
    "assets/settledlands/lang/en_us.json": {
        "gui.settledlands.door.palette": ("A door has one node: Door",
                                          "Door nodes: Door, Opened, Opened by, Within radius"),
    },
}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 386 raises the panel's clock to 20 seconds and gives a door's graph four nodes instead of "
    "one. The create menu now offers «Дверь», «Открытие», «Кто открыл» and «В радиусе»; the signal node "
    "fires on the server every time somebody really opens the door (an empty-hand click on a shut, "
    "unlocked door, or a solved lock panel) and the chain is walked along the wires: the blue «Кто "
    "открыл» node is filled with the name of that player, so the editor shows that the chain ran, and a "
    "«В радиусе» node stops the chain unless the opener stands within its radius. The walk is bounded, "
    "loop-safe and cannot affect the door itself. Stock rows are still not offered for a door: a door "
    "cannot run a line of dialogue."
)
PATCHINFO = (
    "Сборка 386 поднимает потолок скорости панели до 20 секунд и даёт графу двери четыре ноды вместо "
    "одной. В меню создания теперь «Дверь», «Открытие», «Кто открыл» и «В радиусе»; нода сигнала "
    "срабатывает на сервере каждый раз, когда дверь действительно открывают (клик пустой рукой по "
    "закрытой незапертой двери или собранная панель взлома), и сервер идёт по связям: синяя нода «Кто "
    "открыл» заполняется ником этого игрока, поэтому в редакторе видно, что цепочка прошла, а нода «В "
    "радиусе» останавливает цепочку, если открывший дальше её радиуса. Обход ограничен по длине, "
    "защищён от петель и не может повлиять на саму дверь. Строки штатных нод двери по-прежнему не "
    "предлагаются: дверь не умеет вести реплику.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out386"

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
