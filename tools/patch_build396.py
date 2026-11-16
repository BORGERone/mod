#!/usr/bin/env python3
"""
Build 396 - a chest gets the same graph a door has.

The author, after four door builds: «Все прекрасно, теперь я хочу сделать такой-же граф для сундуков».
«Такой же» is the door's design, not a new one, so this build is the door's stack moved onto a chest:

  * `ChestGraph396` - the chest's own node type (50, outside the stock range 0..14 exactly as the door's 40
    is), the editor ids of the chests (-2_000_300_000..-2_000_399_999, the next block down from the
    doors), the card's two sockets, the card's memory (how many times the chest was opened, who opened it
    last), `defaultGraph` / `withChestNode` / `stripCards` - the 383 rule that a place's graph holds the
    place's card and never the two cards the editor adds - and `save`, the same seam the door and the block
    use;
  * `ChestGraph396Store` - one `SavedData` file in the overworld, one entry per chest, keyed by dimension
    and position, written exactly like `BlockTriggerStore351` and `DoorGraph382Store`;
  * `ChestGraph396Events` - the wand opens the chest's graph (at HIGHEST, so the block hook of build 351
    never opens a block editor on a chest), and a click on a chest that has a graph records the opener and
    the opening, then runs the chain when the chest's window closes (`Player#containerMenu` against
    `Player#inventoryMenu` - the game's own answer, and the pair the base mod itself compares);
  * `ChestCard396` + `ChestGraphClient396` - the card on the canvas and the stock editor on the chest's
    graph, `DoorCard382`'s two socket shapes reused rather than re-invented;
  * seven mixins - the save seam, the editor open, the stats card and the AI card taken away (383), the
    card painter, the five-line inspector with the stock rows parked (359/383), and the stock palette with
    one tab;
  * `RadiusPass354` learns one more place: a chest's graph is walked by the same pass that fires a block's
    and a door's «В радиусе», «Время» and «Таймер» nodes (the 394 lesson, applied to the third kind of
    graph);
  * `BlockMenu363Mixin` measures and paints the panel for a chest as well - the same panel a block and a
    door borrow.

Base 395. Five classes replaced, fourteen added, seven mixins registered, fifteen language keys.

Not in this build, and said so in the README: a container from another mod is not covered (a chest and a
barrel are), a double chest is two graphs (its two halves are two blocks), and a chest's chain runs when the
chest is closed rather than while its window is open - a dialogue under that window is a dialogue nobody
reads.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build395-door-stays-open"
NEW_VERSION = "0.8.0-build396-chest-graph"
OUT_NAME = "settledlands-0.8.0-build396-chest-graph.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/npc/RadiusPass354.class",
    "dev/settledlands/npc/RadiusPass354$Spot.class",
    "dev/settledlands/npc/RadiusPass354$DoorSpot.class",
    "dev/settledlands/npc/RadiusPass354$After.class",
    "dev/settledlands/mixin/BlockMenu363Mixin.class",
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class",
)
ADDED = (
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestGraph396Store.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestGraph396Events$Pending.class",
    "dev/settledlands/npc/client/ChestCard396.class",
    "dev/settledlands/npc/client/ChestGraphClient396.class",
    "dev/settledlands/npc/RadiusPass354$ChestSpot.class",
    "dev/settledlands/mixin/NpcChestSave396Mixin.class",
    "dev/settledlands/mixin/NpcChestOpen396Mixin.class",
    "dev/settledlands/mixin/NpcChestNoStats396Mixin.class",
    "dev/settledlands/mixin/NpcChestNoAi396Mixin.class",
    "dev/settledlands/mixin/NpcChestCard396Mixin.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
    "dev/settledlands/mixin/NpcChestPalette396Mixin.class",
)
MIXINS = (
    "NpcChestSave396Mixin",
)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = (
    "NpcChestOpen396Mixin",
    "NpcChestNoStats396Mixin",
    "NpcChestNoAi396Mixin",
    "NpcChestCard396Mixin",
    "NpcChestRows396Mixin",
    "NpcChestPalette396Mixin",
)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/chest/ChestGraph396.class": ("chainStart", "noteOpened", "stripCards"),
    "dev/settledlands/chest/ChestGraph396Store.class": ("settledlands_chest_graphs", "positionOf"),
    "dev/settledlands/chest/ChestGraph396Events.class": ("inventoryMenu", "containerMenu"),
    "dev/settledlands/npc/client/ChestCard396.class": ("ChestGraph396", "PursuitGraphNode272"),
    "dev/settledlands/npc/client/ChestGraphClient396.class": ("openEditor", "ChestGraph396"),
    "dev/settledlands/npc/RadiusPass354$ChestSpot.class": ("chest",),
    "dev/settledlands/mixin/NpcChestSave396Mixin.class": ("settledlands$chestSave",),
    "dev/settledlands/mixin/NpcChestOpen396Mixin.class": ("settledlands$chestOpen",),
    "dev/settledlands/mixin/NpcChestNoStats396Mixin.class": ("settledlands$chestWithoutStats",),
    "dev/settledlands/mixin/NpcChestNoAi396Mixin.class": ("settledlands$chestWithoutAi",),
    "dev/settledlands/mixin/NpcChestCard396Mixin.class": ("settledlands$chestCard",),
    "dev/settledlands/mixin/NpcChestRows396Mixin.class": ("settledlands$chestRows",),
    "dev/settledlands/mixin/NpcChestPalette396Mixin.class": ("settledlands$chestPalette",),
    "dev/settledlands/mixin/BlockMenu363Mixin.class": ("settledlands$isChest",),
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class": ("ChestGraph396",),
    "dev/settledlands/npc/RadiusPass354.class": ("ChestGraph396Store", "addChests"),
    "dev/settledlands/npc/RadiusPass354$Spot.class": ("place",),
}

LANG_SET = {
    "gui.settledlands.chest.node": "Сундук",
    "gui.settledlands.chest.count": "Открытий: %s",
    "gui.settledlands.chest.opener_none": "Ещё никто не открывал",
    "gui.settledlands.chest.opener_last": "Открыл: %s",
    "gui.settledlands.chest.trigger": "Запуск: когда сундук закроют",
    "gui.settledlands.chest.pin_out": "открытие",
    "gui.settledlands.chest.pin_who": "кто открыл",
    "gui.settledlands.chest.pin_hint": "Справа: открытие и синий выход",
    "gui.settledlands.chest.palette":
        "У сундука те же ноды, что у блока; его два выхода - узлы на карточке",
    "gui.settledlands.chest.one_tab":
        "У сундука один граф: вкладки распорядка и общего недоступны",
    "gui.settledlands.chest.added_node": "%s добавлена",
    "message.settledlands.chest.graph_open": "Граф сундука · %s",
    "message.settledlands.chest.graph_saved": "Граф сундука сохранён · %s",
    "message.settledlands.chest.graph_cleared": "Граф сундука удалён · %s",
    "message.settledlands.chest.graph_full": "Слишком много сундуков с графом: %s",
}
LANG_SET_EN = {
    "gui.settledlands.chest.node": "Chest",
    "gui.settledlands.chest.count": "Opened: %s",
    "gui.settledlands.chest.opener_none": "Nobody has opened it yet",
    "gui.settledlands.chest.opener_last": "Opened by: %s",
    "gui.settledlands.chest.trigger": "Runs when the chest is closed",
    "gui.settledlands.chest.pin_out": "opening",
    "gui.settledlands.chest.pin_who": "who opened",
    "gui.settledlands.chest.pin_hint": "On the right: the opening and the blue output",
    "gui.settledlands.chest.palette":
        "A chest offers the block's nodes; its two outputs are sockets of the card",
    "gui.settledlands.chest.one_tab":
        "A chest has one graph: the schedule and shared tabs are not available",
    "gui.settledlands.chest.added_node": "%s added",
    "message.settledlands.chest.graph_open": "Chest graph · %s",
    "message.settledlands.chest.graph_saved": "Chest graph saved · %s",
    "message.settledlands.chest.graph_cleared": "Chest graph removed · %s",
    "message.settledlands.chest.graph_full": "Too many chests with a graph: %s",
}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 396 gives a chest the graph a door has had since 382. The NPC wand on a chest opens the stock "
    "graph editor on a graph that belongs to that chest; the canvas holds one card of this mod - the chest - "
    "and its two outputs are sockets of that card, not nodes beside it: the round amber socket starts the "
    "chest's chain and the blue diamond hands over the player who opened it. The palette is the block's "
    "palette, so a chest's graph runs the same nodes through the same interpreter a block does, and a chest "
    "whose graph holds «В радиусе», «Время» or «Таймер» is watched by the same pass that fires a block's. "
    "The card shows how many times the chest was opened and who opened it last, and the chain runs when the "
    "chest is closed again - a line spoken under the chest's own inventory window is a line nobody reads. "
    "A chest, a barrel; a container from another mod is not covered, and a double chest is two graphs."
)
PATCHINFO = (
    "Сборка 396: у сундука теперь такой же граф, как у двери. Палочка NPC по сундуку открывает тот же "
    "редактор графов на графе этого сундука; на холсте одна карточка этого мода - сундук - и её два выхода "
    "сделаны УЗЛАМИ на карточке, а не нодами рядом: круглый янтарный узел начинает цепочку сундука, синий "
    "ромб отдаёт игрока, который его открыл. Палитра - палитра блока, поэтому граф сундука исполняет те же "
    "ноды тем же интерпретатором, что и блок, а «В радиусе», «Время» и «Таймер» в графе сундука "
    "обслуживает тот же проход, что и у блока. На карточке видно, сколько раз сундук открывали и кто "
    "открыл последним, а цепочка срабатывает, когда сундук закроют: реплика под открытым окном сундука - "
    "реплика, которую никто не прочтёт. Сундук и бочка; контейнер другого мода не покрыт, а двойной "
    "сундук - это два графа.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out396"

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
