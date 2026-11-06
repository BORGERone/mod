#!/usr/bin/env python3
"""
Build 398 - four reports from the author, four fixes.

His words, in the order he wrote them:

  * «сейчас тумблер "открыта/закрыта" визуально в правом окне изменяется только при перезаходе в граф. Так-же
    и в графе сундука». Both inspectors wrote the button's word once, when they were built, and a click only
    changed the node - so the word appeared to lag a whole reopening behind. Both toggles now ask for their own
    word inside the click (`NpcDoorRows382Mixin`, `NpcChestRows396Mixin`), through the same one-element holder
    the sliders use;
  * «после взлома сундука у нас не открывается инвентарь сундука». Three separate things could each produce
    that, and all three are addressed instead of picking one: the solved panel closed itself unconditionally
    and could take a chest window that had already arrived down with it (`HackPanel377#onClose` now closes only
    while it is the screen in front); the answer was sent once and never repeated (`HackPanel377` asks again up
    to two times while no container is open); and the server's own window is no longer the only way in - a
    solved panel lifts the lock of that chest for ten seconds (`ChestLock397#grant`), so one more click opens it
    through the game's own path, with a line in the action bar saying so;
  * «сейчас нельзя вытаскивать из синего узла провод». Every half of a blue pin - the drag, the drop, the cut,
    the stored link and the pass that draws it - is keyed on one predicate, `isValueSourceNode`, and the chest
    card was not in it. One term added (`isChestValueNode`), and the blue socket of a chest's card behaves
    exactly like the door's;
  * «добавь ноде главной у сундука узел "взлом" а главной ноде двери узел "открытие"». Each card had one of
    the two events and now has both, as sockets of the card and never as nodes beside it (build 392):
    `CardEventPin398` holds the geometry, `CardEventPin398Mixin` gives it the drag/drop/cut/draw the stock screen
    gives an IF node's second port, and the chain itself lives in `Node#falseNext` - the second output every
    node already has, written and read by the stock serialiser for every type. On a chest the new socket is
    «взлом»: a cracked chest runs it when its window closes (`ChestGraph396#hackChainStart`, `Pending#cracked`).
    On a door the new socket is «открытие»: `DoorSignal398` runs it from the door's own `useWithoutItem`, only
    when the door really changed from closed to open (`DoorUse373Mixin`). A graph from build 396/397 has only
    one socket wired and keeps working: a cracked chest falls back to it rather than going silent.

Base 397. Ten classes replaced, three added, one client mixin registered, three language keys added and two
hint lines corrected.

Not in this build, and said so in the README: the chest's second socket fires on the close of the window like
the first one (a dialogue under an open chest is a dialogue nobody reads), a door's «открытие» chain does not
fire for a door moved by redstone (the hook is the player's own click), and the author is told plainly that the
chest-window fix could not be watched in a live game from here.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build397-chest-hack"
NEW_VERSION = "0.8.0-build398-card-sockets"
OUT_NAME = "settledlands-0.8.0-build398-card-sockets.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestGraph396Events$Pending.class",
    "dev/settledlands/chest/ChestLock397.class",
    "dev/settledlands/chest/ChestNet397.class",
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/mixin/DoorUse373Mixin.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
    "dev/settledlands/npc/client/ChestCard396.class",
    "dev/settledlands/npc/client/DoorCard382.class",
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
)
ADDED = (
    "dev/settledlands/door/DoorSignal398.class",
    "dev/settledlands/mixin/CardEventPin398Mixin.class",
    "dev/settledlands/npc/client/CardEventPin398.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("CardEventPin398Mixin",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/chest/ChestGraph396.class": ("hackChainStart", "falseNext"),
    "dev/settledlands/chest/ChestGraph396Events.class": ("cracked", "remember"),
    "dev/settledlands/chest/ChestGraph396Events$Pending.class": ("cracked",),
    "dev/settledlands/chest/ChestLock397.class": ("grant", "granted"),
    "dev/settledlands/chest/ChestNet397.class": ("grant", "message.settledlands.chest.again"),
    "dev/settledlands/door/DoorGraph382.class": ("openChainStart", "hasOpenChain"),
    "dev/settledlands/door/DoorSignal398.class": ("открытие", "BlockTrigger351"),
    "dev/settledlands/door/HackPanel377.class": ("settledlands$askAgain", "onClose"),
    "dev/settledlands/mixin/CardEventPin398Mixin.class": ("settledlands$grabCardSocket",
                                                          "settledlands$drawCardSockets"),
    "dev/settledlands/mixin/DoorUse373Mixin.class": ("settledlands$doorOpened", "DoorSignal398"),
    "dev/settledlands/mixin/NpcChestRows396Mixin.class": ("settledlands$lockWord",),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": ("settledlands$sealWord",),
    "dev/settledlands/npc/client/CardEventPin398.class": ("SECOND_ROW", "pin_hack"),
    "dev/settledlands/npc/client/ChestCard396.class": ("CardEventPin398", "socket"),
    "dev/settledlands/npc/client/DoorCard382.class": ("CardEventPin398", "diamond"),
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("isChestValueNode", "ChestGraph396"),
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class": ("MenuPoint",),
}

LANG_SET = {
    "gui.settledlands.door.pin_open": "открытие",
    "gui.settledlands.chest.pin_hack": "взлом",
    "message.settledlands.chest.again": "Замок снят: нажми на сундук ещё раз",
}
LANG_SET_EN = {
    "gui.settledlands.door.pin_open": "opening",
    "gui.settledlands.chest.pin_hack": "hack",
    "message.settledlands.chest.again": "The lock is off: click the chest again",
}
LANG_CHANGE = {
    "assets/settledlands/lang/ru_ru.json": {
        "gui.settledlands.door.pin_out_hint": ("Справа: взлом и синий выход",
                                               "Справа: взлом, открытие и синий выход"),
        "gui.settledlands.chest.pin_hint": ("Справа: открытие и синий выход",
                                            "Справа: открытие, взлом и синий выход"),
    },
    "assets/settledlands/lang/en_us.json": {
        "gui.settledlands.door.pin_out_hint": ("On the right: hack and the blue output",
                                               "On the right: hack, opening and the blue output"),
        "gui.settledlands.chest.pin_hint": ("On the right: the opening and the blue output",
                                            "On the right: the opening, the hack and the blue output"),
    },
}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 398 answers four reports. The «открыта/закрыта» toggle of a door and of a chest now changes its word "
    "on the click itself instead of only after the graph is reopened. A chest whose lock panel is solved really "
    "opens: the panel no longer closes a window that has already replaced it, the answer is repeated while no "
    "container is open, and the lock of that chest is lifted for ten seconds the moment the panel is solved - so "
    "one more click always opens the chest through the game's own path. The blue socket of a chest's card can be "
    "dragged, dropped and cut exactly like the door's. And both cards now carry two event sockets: a chest has "
    "«открытие» and «взлом», a door has «взлом» and «открытие» - the second one two rows under the first, saved in "
    "the node field every type writes, wired with the screen's own undo and dirty flag. A chest opened by a "
    "solved panel runs the «взлом» chain; a door opened by hand runs the «открытие» chain. Graphs of build "
    "396/397 keep working with the single socket they have."
)
PATCHINFO = (
    "Сборка 398 по твоим замечаниям. 1) Тумблер «открыта/закрыта» в правом окне у двери и у сундука теперь "
    "меняет слово сразу по клику, а не после перезахода в граф. 2) Сундук после взлома действительно "
    "открывается: панель больше не закрывает окно, которое её уже заменило, ответ повторяется, пока не открыт "
    "контейнер, а замок этого сундука снимается на десять секунд в момент решения панели - так что ещё один "
    "клик всегда открывает сундук обычным путём игры. 3) Из синего узла карточки сундука теперь можно тянуть "
    "провод, как у двери. 4) У обеих карточек по два узла-события: у сундука «открытие» и «взлом», у двери "
    "«взлом» и «открытие»; второй - двумя строками ниже первого, хранится в поле узла, которое пишется для "
    "любого типа, и ставится тем же путём с точкой отмены, что и стоковые провода. Сундук, открытый решённой "
    "панелью, запускает цепочку «взлом»; дверь, открытая рукой, запускает «открытие». Графы 396/397 работают "
    "со своим единственным узлом.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out398"

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
