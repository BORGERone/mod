#!/usr/bin/env python3
"""
Build 389 - the door card gets its two outputs: «Импульс» (a pulse on a hack) and the blue «Цель».

The author's request: «сделай теперь из ноды "дверь" один выходной узел сигнала и синий узел цели. Узел
сигнала будет давать импульс если дверь взломать, синий будет передавать ссылку на того кто взломал
дверь».

Build 388 had made a door's graph a stock graph, and that stays: the palette is the block's eleven rows and
the chain is still run by `BlockTrigger351`. What this build adds is the pair of nodes the door itself has
to own, because no stock node means either of the two things:

  * **«Импульс»** (`SIGNAL_TYPE = 41`, amber) - the output of the door. A fresh door is drawn the way the
    author described it: the card, then the signal, then the blue target, wired left to right
    (`DoorGraph382#wireStarter`, called by `defaultGraph` and by every open of a graph that has none). The
    pulse fires <b>only</b> on a hack - a solved lock panel (`DoorHack377` → `DoorSignal388#hack`) - and an
    ordinary click on a door that is not sealed pulses nothing.
  * **«Цель»** (`TARGET_TYPE = 42`, the blue the author asked for) - the reference to the player who hacked
    the door. His name is written into every target node of the graph (`Node#text`, which the stock
    serialiser writes for every type, so it survives the save) and the chain that leaves a target node runs
    <b>as</b> that player, so `%player%` anywhere after it is the hacker.

Everything else keeps build 388's rule: only a meaning the stock graph has no node for gets a type of this
mod, and then it is drawn like the block's «Имя» card and edited with rows of its own.

Two rows are added to the end of a door's create menu for the two nodes; a block's menu is untouched.
Build 386's «В радиусе» (type 43) is still dropped from an old graph - the stock «Проверка» node is the
radius gate now, and 41/42 are no longer stale because they mean something again.

Base 388. Eight classes replaced, none added, none removed, one new mixin, twelve new language keys per
language.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build388-door-stock-nodes"
NEW_VERSION = "0.8.0-build389-door-signal-target"
OUT_NAME = "settledlands-0.8.0-build389-door-signal-target.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorSignal388.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorHack377.class",
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/npc/client/DoorCard382.class",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorGraph382.class": (
        "wireStarter", "signalNodes", "setOpener", "extraType"),
    "dev/settledlands/door/DoorSignal388.class": (
        "hack", "signalNodes", "setOpener"),
    "dev/settledlands/door/DoorHack377.class": (
        "DoorSignal388",),
    "dev/settledlands/door/DoorEvents368.class": (
        "door",),
    "dev/settledlands/npc/client/BlockMenu363.class": (
        "doorType", "extraType"),
    "dev/settledlands/npc/client/DoorCard382.class": (
        "labelKey", "gui.settledlands.door.target_line1"),
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class": (
        "doorType", "applyDefaults"),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "isDoorGraphNode", "isTargetNode"),
}

LANG_SET = {
    "gui.settledlands.door.signal": "Импульс",
    "gui.settledlands.door.target": "Цель",
    "gui.settledlands.door.signal_line1": "Импульс: дверь взломали",
    "gui.settledlands.door.signal_line2": "Выход карточки двери → дальше",
    "gui.settledlands.door.signal_line3": "Срабатывает только на взлом",
    "gui.settledlands.door.signal_line4": "обычный клик импульса не даёт",
    "gui.settledlands.door.target_line1": "Игрок, взломавший дверь",
    "gui.settledlands.door.target_last": "Последний: %s",
    "gui.settledlands.door.target_none": "Ещё никто не взламывал",
    "gui.settledlands.door.target_line3": "Синий выход: ссылка на игрока",
}
LANG_SET_EN = {
    "gui.settledlands.door.signal": "Pulse",
    "gui.settledlands.door.target": "Target",
    "gui.settledlands.door.signal_line1": "Pulse: the door was hacked",
    "gui.settledlands.door.signal_line2": "The output of the door card → onwards",
    "gui.settledlands.door.signal_line3": "Fires on a hack only",
    "gui.settledlands.door.signal_line4": "a plain click pulses nothing",
    "gui.settledlands.door.target_line1": "The player who hacked the door",
    "gui.settledlands.door.target_last": "Last: %s",
    "gui.settledlands.door.target_none": "Nobody has hacked it yet",
    "gui.settledlands.door.target_line3": "Blue output: the reference to that player",
}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 389 gives the door card its two outputs. «Импульс» (amber) is the door's signal: a fresh door's "
    "graph is created as card -> pulse -> target, wired left to right, and the pulse fires only when the "
    "door is hacked - a solved lock panel - never on a plain click. «Цель» (blue) is the reference to the "
    "player who hacked the door: his name is written into every target node (and saved with the graph), and "
    "the chain that leaves it runs as that player, so %player% after the pulse is the hacker. Both nodes can "
    "be added from the end of the door's create menu; they are the only two types this mod owns in a door's "
    "graph besides the card itself, and everything else stays the stock node set build 388 introduced."
)
PATCHINFO = (
    "Сборка 389: у карточки двери появились два выхода. «Импульс» (янтарный) — сигнал двери: граф новой "
    "двери создаётся как карточка → импульс → цель, соединённые слева направо, и импульс срабатывает только "
    "когда дверь взломали (собранная панель), а не при обычном клике. «Цель» (синяя) — ссылка на того, кто "
    "взломал: его ник пишется в каждую ноду-цель (и сохраняется вместе с графом), а цепочка, выходящая из "
    "неё, выполняется от имени этого игрока, поэтому %player% после импульса — это взломщик. Обе ноды можно "
    "добавить из конца меню создания двери; это единственные два типа мода в графе двери кроме самой "
    "карточки, всё остальное — штатный набор нод из 388-й.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out389"

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
