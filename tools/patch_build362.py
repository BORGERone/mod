#!/usr/bin/env python3
"""
Build 362 - the tabs of a block's graph are taken off the screen, and the canvas answers the mouse again.

Base 361. Build 361 went out with two defects the author found at once:

  * the tabs were still in the top bar of a block's graph. Two builds asked the tab button to be
    quiet - 360 cleared its visible flag, 361 cleared it and moved it out of the window - and both
    came back with the strip on screen. A widget on a screen is somebody else's to keep showing, so
    362 removes it: the three buttons whose label is a tab label are taken out of the screen's
    renderables and children lists after widgets() has finished, and only then hidden and moved.
    There is no widget left to draw and none left to click.
  * the block's graph had stopped answering the mouse. The click handler of the «Автономный ИИ» UI
    is the graph screen's only way into the click chain - NpcDialogueGraphScreen#mouseClicked is a
    one-line delegation to it, and when the panel is not hit it hands the click on to the
    «Характеристики» UI and the rest of the editor. Build 361 cancelled that method for a block and
    dropped the click with it. 362 hands the click on instead: the panel is skipped, the stock chain
    gets the same call a click that misses the panel would make.

Three classes replaced, none added:

    WidgetPark361             replaced: park() now also removes the widget from the screen's lists
    BlockNoTabs360Mixin       replaced: a tab is removed from the screen at the end of widgets()
    BlockNoAi359Mixin         replaced: the AI click is handed on, not dropped (the 361 regression)
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build361-block-graph-cleanup"
NEW_VERSION = "0.8.0-build362-block-tabs-fix"
OUT_NAME = "settledlands-0.8.0-build362-block-tabs-fix.jar"

REMOVED = ()
REPLACED = (
    # take a widget off the screen, not only ask it to be quiet
    "dev/settledlands/npc/client/WidgetPark361.class",
    # the tab strip of a block's graph: removed at the end of widgets()
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class",
    # the AI panel's click is handed on to the rest of the editor instead of dropped
    "dev/settledlands/mixin/BlockNoAi359Mixin.class",
)
ADDED = ()

MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 362 fixes the two defects of 361. The tabs of a block's graph are gone for good: the three "
    "tab buttons are removed from the screen's own widget lists after the editor has built them, so "
    "there is nothing left to draw or to click - hiding the button, twice tried, was not enough. And a "
    "block's graph answers the mouse again: the click handler of the «Автономный ИИ» panel is the "
    "screen's only way into the click chain, so 362 skips the panel and hands the click on to the rest "
    "of the editor instead of dropping it. Everything 361 brought stays: no AI panel on a block's "
    "canvas, the name card's inspector is the name and the colour, a block's name is its name without "
    "coordinates, and one zone toggle for both radius nodes."
)
PATCHINFO = (
    "Сборка 362: исправлены два дефекта 361. Вкладки у графа блока убраны окончательно — три кнопки "
    "вкладок удаляются из списков виджетов экрана после того, как редактор их построил, поэтому "
    "рисовать и нажимать больше нечего (прятать кнопку, дважды испробовано, оказалось мало). И граф "
    "блока снова отвечает на мышь: обработчик клика панели «Автономный ИИ» — единственный вход экрана "
    "в цепочку кликов, поэтому 362 пропускает панель и передаёт клик дальше по штатной цепочке, а не "
    "выбрасывает его. Всё, что принесла 361, остаётся: панели ИИ на холсте блока нет, в инспекторе "
    "карточки «ИМЯ» только имя и цвет, имя блока — только имя без координат, и один тумблер зоны у "
    "обеих радиусных нод.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out362"

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
