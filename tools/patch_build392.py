#!/usr/bin/env python3
"""
Build 392 - the two outputs of a door are sockets on the door card, not nodes beside it.

The author, after three builds of two cards: «теперь все выглядит так. Ты похоже меня не понял. Ты создал
ноды импульс и цель, а я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде "дверь"». He wanted the two outputs
of build 386/389 - the pulse and the blue reference to whoever hacked the door - to be <b>sockets of the
door card</b>, the way every node in this graph has its output ports, and nothing else on the canvas.

What this build does:

  * the door card carries the two sockets on its right edge: a round amber <b>execution</b> socket on the
    row the canvas gives every output port (that is the card's own {@code next}, so the wire, the drag and
    the cut are the stock ones), and, one row under it, the blue <b>value</b> diamond every card of this
    mod has - it hands over the player who hacked the door;
  * the card keeps the name of the last hacker itself ({@code text}, a field the graph NBT writes for every
    node) and shows it, so the reference is visible with nothing wired to the blue socket;
  * the node types of build 389-391 («Импульс», 41, and the blue «Цель», 42) are gone from the palette and
    are <b>folded</b> into the card's sockets (`DoorGraph382#foldStarter`): the pulse's chain becomes the
    card's chain, every reader of the blue target reads the card, and the name it held moves onto the card.
    A card the author dragged and a chain he built are kept as they are - nothing is re-created;
  * the hack runs the chain the card's output socket points at ({@code DoorGraph382#chainStart}), and a
    «Реплика» wired to the blue socket reads the hacker as {@code %target%} (the same tokens the NPC
    graphs use), because the blue socket hands over a body like «В радиусе» and «Урон» do.

Base 391. Eight classes replaced (three with their inner classes: eleven entries), no mixin added or
removed, four new language keys, nothing else touched.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build391-door-neat-row"
NEW_VERSION = "0.8.0-build392-door-card-sockets"
OUT_NAME = "settledlands-0.8.0-build392-door-card-sockets.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorSignal388.class",
    "dev/settledlands/npc/BlockTalk358.class",
    "dev/settledlands/npc/BlockTalk358$Open.class",
    "dev/settledlands/npc/BlockTalk358$Where.class",
    "dev/settledlands/npc/BlockTrigger351.class",
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/npc/client/DoorCard382.class",
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorGraph382.class": ("foldStarter", "chainStart", "bypass"),
    "dev/settledlands/door/DoorSignal388.class": ("chainStart", "setOpener"),
    "dev/settledlands/npc/client/DoorCard382.class": ("pin_out", "pin_who", "diamond"),
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("isDoorValueNode",),
    "dev/settledlands/npc/BlockTalk358.class": ("%target%",),
    "dev/settledlands/npc/client/BlockMenu363.class": ("doorType",),
    "dev/settledlands/npc/BlockTrigger351.class": ("withBody",),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": ("pin_out_hint",),
}

LANG_SET = {
    "gui.settledlands.door.pin_out": "взлом",
    "gui.settledlands.door.pin_who": "кто взломал",
    "gui.settledlands.door.pin_out_hint": "Справа: взлом и синий выход",
    "gui.settledlands.door.hacked_last": "Взломал: %s",
    "gui.settledlands.door.palette": "У двери те же ноды, что у блока; его два выхода - узлы на карточке",
}
LANG_SET_EN = {
    "gui.settledlands.door.pin_out": "hack",
    "gui.settledlands.door.pin_who": "who hacked",
    "gui.settledlands.door.pin_out_hint": "On the right: hack and the blue output",
    "gui.settledlands.door.hacked_last": "Hacked by: %s",
    "gui.settledlands.door.palette": "A door offers the blocks's nodes; its two outputs are sockets of the card",
}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 392 puts the door's two outputs where the author asked for them: they are sockets of the door "
    "card, not nodes beside it. The card carries the round amber execution socket on the row the canvas "
    "gives every output port (the chain wired to it runs when the door is hacked) and, under it, the blue "
    "value socket that hands over the player who hacked the door - %target% in a «Реплика» wired to it is "
    "his name. The card shows the last hacker itself, so the reference is there even with nothing wired. "
    "The two nodes of builds 389-391 are folded into the sockets on open: the pulse's chain becomes the "
    "card's chain, readers of the blue target read the card, and the author's own chains and node "
    "positions are left as they are."
)
PATCHINFO = (
    "Сборка 392: два выхода двери — это узлы на самой карточке, а не ноды рядом. На карточке круглый "
    "янтарный узел взлома на той же строке, где канвас рисует любой выходной порт (цепочка, подключённая к "
    "нему, идёт при взломе двери), и под ним синий узел, который передаёт ссылку на взломавшего дверь — "
    "%target% в «Реплике», подключённой к нему, это его имя. Ник последнего взломщика карточка хранит и "
    "показывает сама, поэтому ссылка видна и без провода. Две ноды сборок 389-391 сворачиваются в узлы при "
    "открытии графа: цепочка импульса становится цепочкой карточки, читатели синей цели читают карточку, "
    "а собственные цепочки и положение нод автора не трогаются.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out392"

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
