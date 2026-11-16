#!/usr/bin/env python3
"""
Build 365 - a block's graph gets the rest of the nodes it was missing: Кнопка, Время, Таймер,
Катсцена, Цель.

Base 364. 364 cut the right-click menu down to the six nodes a block could run. The author asked for
the five the block still lacked, and the menu is the small half of that: the tick pass had to learn to
fire them too, or the rows would be rows that do nothing.

  * BlockTrigger351: palette grows to eleven rows (Кнопка = 1, Время = 7, Таймер = 8, Катсцена = 14,
    Цель = 15, besides the six of 364) in stock order, and the walk learns the two that are steps:
    a «Кнопка» opens the stock buttons in the block window and waits for a click, a «Катсцена» starts
    the stock camera scene and waits for the playback. Event nodes are stepped over, as before.
  * BlockTalk358: choices() - the same block window, its rows are the buttons of the node, and a pick
    continues the chain at the node that button is wired to. pick() routes a row of such a window by
    that target; a plain line still continues at node.next.
  * RadiusPass354: the pass fires «Цель» as an event (kind of the node, its own radius and cooldown)
    and gains the two clocks of the stock editor - «Время» (mode hour + 400 tick window, once a day)
    and «Таймер» (seconds, repeat or one shot) - for the nearest player within 32 blocks. A scene
    started by a block is polled here and its chain walks on when the playback is over.
  * BlockTriggerStore351: the "this block has something to fire" flag now counts all event nodes.
  * BlockMenu363: eleven rows do not always fit, so the panel scrolls its own window instead of
    cancelling the wheel; the stock scroller counts sixteen rows and would paint a row nobody chose.

Nothing removed. Seven classes replaced, one inner class added (RadiusPass354$After).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build364-block-menu"
NEW_VERSION = "0.8.0-build365-block-events"
OUT_NAME = "settledlands-0.8.0-build365-block-events.jar"

REMOVED = ()
REPLACED = (
    # eleven rows, the two step nodes of a block, the event step-over
    "dev/settledlands/npc/BlockTrigger351.class",
    # buttons in the block window, and a pick that follows the button it came from
    "dev/settledlands/npc/BlockTalk358.class",
    "dev/settledlands/npc/BlockTalk358$Open.class",
    # Цель, Время, Таймер and the scene resume in the tick pass
    "dev/settledlands/npc/RadiusPass354.class",
    # the event flag counts the clocks too
    "dev/settledlands/npc/BlockTriggerStore351.class",
    "dev/settledlands/npc/BlockTriggerStore351$Entry.class",
    # the panel scrolls its own window
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/mixin/BlockMenu363Mixin.class",
)
ADDED = (
    # one queued scene wait, per player
    "dev/settledlands/npc/RadiusPass354$After.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("BlockMenu363Mixin",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 365: a block's graph gains the five nodes it was missing - Кнопка (buttons), Время (time "
    "of day), Таймер (interval), Катсцена (camera scene) and Цель (a zone of its own). The right-click "
    "menu now offers eleven rows in the stock order. The tick pass fires the clocks as the stock "
    "editor does (Время once a day inside a 400 tick window after its hour, Таймер every N seconds, "
    "repeat or one shot, both for the nearest player within 32 blocks), Цель fires on the body kind "
    "of the node inside its own radius, a «Кнопка» opens the stock buttons in the block window and a "
    "click goes to the node that button is wired to, and a «Катсцена» plays the stock camera scene "
    "and the chain walks on when it ends. The panel scrolls while not all eleven rows fit. Everything "
    "364 and 363 fixed stays: the cut-down menu, the name colour, the delete-graph button, no tabs."
)
PATCHINFO = (
    "Сборка 365: у графа блока появились пять нод, которых не хватало - Кнопка, Время, Таймер, "
    "Катсцена и Цель. В меню по правой кнопке теперь одиннадцать строк в штатном порядке. Тиковый "
    "проход запускает часы так же, как штатный редактор (Время - раз в сутки, в окне 400 тиков после "
    "своего часа; Таймер - каждые N секунд, повторно или один раз; оба - для ближайшего игрока в 32 "
    "блоках), Цель срабатывает по типу тела из своей ноды в своём радиусе, «Кнопка» открывает штатные "
    "кнопки в окне блока, и клик уводит в ту ноду, к которой кнопка привязана, а «Катсцена» проигрывает "
    "штатную сцену камеры, и цепочка идёт дальше, когда сцена закончилась. Пока одиннадцать строк не "
    "влезают, панель прокручивается. Всё, что исправили 364 и 363, остаётся: урезанное меню, цвет "
    "имени, кнопка удаления графа, отсутствие вкладок.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out365"

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
