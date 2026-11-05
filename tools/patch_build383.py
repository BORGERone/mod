#!/usr/bin/env python3
"""
Build 383 - the door graph, corrected to look and behave like the block graph.

Build 382 shipped a door's graph that opened the stock editor and dropped a door node into it. The
author opened it in game and sent a screenshot with one sentence: "сейчас граф выглядит так, ты
допустил точно те же ошибки что и при создании графа блока, исправь все сразу." The screenshot showed
all three of those mistakes at once:

  1. the tab strip of a body's editor - «Главный / Распорядок / Общее» - still in the top bar;
  2. the inspector of a «Реплика» under the door's rows - a text box, a trigger, a relation change,
     min/max of relation and of points - because the editor's switch has no branch for the door's type
     and falls through to the topic rows;
  3. the door node painted by the stock painter: a pink box (the colour of the last row of the stock
     table) titled «Случайность», with our three lines written on top of it and cut off at the right
     edge, and the toggle line missing under them.

Every one of those was solved once already, for the graph of a block, in builds 359-362. Build 383 does
not invent anything: it ports the answers.

  * `BlockNoTabs360Mixin` (edited) - its "this editor holds one graph" test now answers for a door's
    id as well as a block's (`DoorGraph382.isDoorEditorId`), so the three tab buttons are parked off
    the screen of a door's editor by the same code, both where they are built and after `widgets()`.
  * `NpcDoorNoStats383Mixin` (new, client) - `NpcStatsGraphUi#ensure` is cancelled for a door and any
    stats card (16) or block name card (18) already in the graph is dropped, the way build 359 does it
    for a block. The «Характеристики» card of the author's screenshot is this call.
  * `NpcDoorNoAi383Mixin` (new, client) - `NpcAutoAiGraphUi255#ensure`/`#draw` cancelled for a door,
    and `mouseClicked` **hands the click on** to `NpcStatsGraphUi#mouseClicked` instead of dropping it:
    that method is the screen's only way into the click chain, and dropping it (build 361's mistake)
    makes a canvas unclickable.
  * `NpcDoorRows382Mixin` (rewritten) - the stock rows and labels of the selected node are parked and
    `sidebarHeight` is reset, then the three own rows are built from the top of the panel: the title
    «Дверь», the two sliders and the toggle. Every write goes through `propertyChange(...)`, the
    screen's own commit - build 382 wrote the node directly, which is exactly the way the block's name
    colour was lost in build 362 - and the scroll range is grown by the height of the rows.
  * `NpcDoorCard382Mixin` (rewritten) + `DoorCard382` (new) - `drawNode` is cancelled at HEAD for the
    door's type and the card is painted here, in the box the canvas hit-tests a node with
    (`NpcGraphCanvas#NODE_W` x `NODE_H`, `pose.translate(canvas.screenX/screenY)` + `zoom`): title,
    keys, speed, closed/open and one hint line, all inside the box, so nothing is clipped.
  * `DoorGraph382#stripCards` - the same three card types are also dropped server-side before the graph
    is handed to the editor and before it is stored, so a door's file stays a door's file.

No new external signature: every call is one this mod already makes - `NpcGraphCanvas` fields and
`settledlands$canvas()/font()/isSelected()`, `GuiGraphics#fill/drawString/pose`, `PoseStack
#pushPose/popPose/translate/scale`, `Component.translatable`, and inside the mixins only members the
block mixes already shadow.

Base 382. Four classes replaced, three added.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build382-door-graph"
NEW_VERSION = "0.8.0-build383-door-graph-fix"
OUT_NAME = "settledlands-0.8.0-build383-door-graph-fix.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class",
    "dev/settledlands/mixin/NpcDoorCard382Mixin.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcDoorNoStats383Mixin.class",
    "dev/settledlands/mixin/NpcDoorNoAi383Mixin.class",
    "dev/settledlands/npc/client/DoorCard382.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("NpcDoorNoStats383Mixin", "NpcDoorNoAi383Mixin")
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcDoorNoStats383Mixin.class": (
        "ensure", "NpcStatsGraphUi", "dropType", "isDoorEditorId"),
    "dev/settledlands/mixin/NpcDoorNoAi383Mixin.class": (
        "mouseClicked", "NpcStatsGraphUi", "NpcAutoAiGraphUi255"),
    "dev/settledlands/mixin/NpcDoorCard382Mixin.class": (
        "drawNode", "doorCard", "DoorCard382"),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "widgets", "propertyChange", "NpcSlider"),
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class": (
        "widgets", "isDoorEditorId", "isBlockEditorId"),
    "dev/settledlands/npc/client/DoorCard382.class": (
        "screenX", "zoom", "popPose"),
    "dev/settledlands/door/DoorGraph382.class": (
        "stripCards", "dropType", "isDoorEditorId"),
}

LANG_SET = {
    "gui.settledlands.door.hint": "Клавиши и скорость панели взлома",
}
LANG_SET_EN = {
    "gui.settledlands.door.hint": "Keys and speed of the hack panel",
}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 383 fixes the door graph of build 382 to look and behave like the block graph, which is "
    "what the author asked for after opening it in game. A door's editor no longer shows the tab "
    "strip of a body's editor (the main, schedule and shared tabs are taken off the screen, not "
    "hidden), its inspector no longer shows the rows of a «Replica» node (the stock rows and labels "
    "are parked and only the door's own three rows are built - the title, the two sliders and the "
    "toggle - with every write going through the screen's own propertyChange so no value is lost and "
    "the scroll range grown to fit them), and the door node is no longer painted by the stock painter: "
    "the stock drawNode is cancelled for the door's type and the card is drawn here, in the node box, "
    "with the title, the keys, the speed, the closed/open state and a hint line all inside it. The "
    "«Характеристики» card and the «Автономный ИИ» panel are cancelled for a door the same way they are "
    "cancelled for a block - and the click of the AI panel is handed on, never dropped, so the canvas "
    "keeps answering the mouse. The three card types are dropped from a door's graph on the server too, "
    "before it is opened and before it is stored."
)
PATCHINFO = (
    "Сборка 383 исправляет граф двери 382-й сборки так, чтобы он выглядел и вёлся как граф блока - "
    "это дословный ответ автора после того, как он открыл его в игре. В редакторе двери больше нет "
    "вкладок «Главный / Распорядок / Общее» (полоса вкладок снимается с экрана, а не прячется), в "
    "инспекторе больше нет строк «Реплики» (штатные строки и подписи уводятся, и строятся только три "
    "свои строки - заголовок «Дверь», два ползунка и тумблер, каждая запись идёт через штатный "
    "propertyChange, поэтому значение не теряется, а высота прокрутки увеличена под строки), и карточка "
    "двери больше не рисуется штатным художником: для этого типа drawNode отменяется и карточка "
    "рисуется сама - заголовок, число чисел, скорость, «закрыта/открыта» и подсказка, всё внутри "
    "рамки. Карточка «Характеристики» и панель «Автономный ИИ» для двери отменяются так же, как для "
    "блока, причём клик панели ИИ передаётся дальше, а не глотается, поэтому холст продолжает "
    "отвечать мыши. Те же три типа карточек убираются и на сервере - до открытия графа и до записи, "
    "чтобы в файле двери лежала только дверь. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out383"

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
