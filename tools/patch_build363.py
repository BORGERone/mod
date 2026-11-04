#!/usr/bin/env python3
"""
Build 363 - the colour and the name of a block's «Имя» node are saved, and the block's own
«удалить граф» button is back.

Base 362. The author found two defects in 362:

  * changing the colour of the name in the graph does nothing. The two inspector rows of a block's
    name card (build 359) wrote the node directly: BlockName359.setColor / setName, nothing else.
    Every stock row of every other node goes through NpcDialogueGraphScreen#propertyChange, which
    takes an undo point, runs the write and marks the graph changed - and "changed" is the dirty
    flag. A block's editor saves on close only when that flag is set (onClose: if (dirty) save()),
    so the colour was picked, the list showed it, and closing the window threw it away. The name
    written in the same row had the same defect. Both rows now go through propertyChange.
  * the block's «удалить граф» button was gone. It is built by build 355 at x 66, y 10, 104x20 -
    the very box the stock tab strip is built in - and 362 takes the strip away by that box
    (WidgetPark361#isTabStrip). The button matched. A widget this mod owns can now be declared with
    WidgetPark361#keep: the delete button marks itself when it is built and the strip removal skips
    everything marked, so the strip still goes by label or by box and the block keeps its button.

Four classes replaced, none added:

    WidgetPark361             replaced: keep()/isKept() - a widget this mod owns is never parked
    BlockNoTabs360Mixin       replaced: the strip removal skips kept widgets (the delete button)
    NpcBlockDelete355Mixin    replaced: the delete button marks itself as kept
    BlockNameInspector359Mixin replaced: both rows commit through propertyChange (undo + dirty)
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build362-block-tabs-fix"
NEW_VERSION = "0.8.0-build363-name-colour-and-delete"
OUT_NAME = "settledlands-0.8.0-build363-name-colour-and-delete.jar"

REMOVED = ()
REPLACED = (
    # keep(): a widget this mod owns is never taken off the screen by the strip rule
    "dev/settledlands/npc/client/WidgetPark361.class",
    # the strip removal skips kept widgets - the block's delete button lives in the strip's box
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class",
    # the delete button of build 355 marks itself as kept
    "dev/settledlands/mixin/NpcBlockDelete355Mixin.class",
    # the name and the colour of a block's name card commit through propertyChange
    "dev/settledlands/mixin/BlockNameInspector359Mixin.class",
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
    "Build 363 fixes two defects of 362. The colour of a block's name is saved now: the two rows of "
    "the name card's inspector wrote the node directly and never told the screen that anything had "
    "changed, and a block's editor saves on close only when it is marked changed - so the colour was "
    "picked and then thrown away, and so was the name written next to it. Both go through the same "
    "commit the stock rows use, which also gives them an undo point. And the block's «delete graph» "
    "button is back: it is built in the same box as the stock tab strip, which 362 removed by box, so "
    "the button went with it; a widget this mod owns is now marked and never removed with the strip. "
    "Everything 362 brought stays: no tabs on a block's graph, no AI panel, and a canvas that answers "
    "the mouse."
)
PATCHINFO = (
    "Сборка 363: исправлены два дефекта 362. Цвет имени блока теперь сохраняется — две строки "
    "инспектора карточки «ИМЯ» писали в ноду напрямую и не сообщали экрану, что что-то изменилось, а "
    "редактор блока сохраняет при закрытии только с этим флагом, поэтому цвет выбирался и "
    "выбрасывался; то же было и с самим именем. Обе строки теперь коммитят тем же способом, что и "
    "штатные строки, — с точкой отмены. И вернулась кнопка «удалить граф» у блока: она строится в "
    "той же коробке, что и полоса вкладок, которую 362 убирала по коробке, и уезжала вместе с ней; "
    "теперь виджет, который принадлежит моду, помечается и со полосой не удаляется. Всё, что "
    "принесла 362, остаётся: вкладок у графа блока нет, панели ИИ нет, холст отвечает на мышь.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out363"

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
