#!/usr/bin/env python3
"""
Build 366 - the block's right-click menu is the stock panel again: five rows, a scrollbar, the stock
colours, and a click that reaches every row.

Base 365. 365 fitted eleven rows into a panel 266 pixels tall, and the author found four defects in it
at once: the window grew past the canvas, the scrollbar was gone, every row was painted the same grey
instead of the colour of its node type, and nothing below the fifth row could be clicked - the stock
hit test only knows the five rows of its own map and answers -1 for anything under them.

The fix is the one the author asked for: take the existing dropdown list as the model.

  * BlockMenu363 paints the stock panel again - height 20 + 5 x 22 + 4 = 134, cut to the canvas - with
    the sixteen colours of the stock painter (one per node type), the stock scrollbar on the right, and
    a window of five rows that the wheel and the strip move between 0 and count - 5.
  * rowAt() counts from that window, so a row is clickable once it is scrolled into view; stripHit /
    stripClick keep a click on the scrollbar from being mistaken for a row.
  * NpcBlockGraph351Mixin takes the row of a click from BlockMenu363#rowAt instead of the stock
    NpcGraphContextMenu#rowAt, and lets a click on the scrollbar move the window without closing.

Two classes replaced.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build365-block-events"
NEW_VERSION = "0.8.0-build366-menu-fix"
OUT_NAME = "settledlands-0.8.0-build366-menu-fix.jar"

REMOVED = ()
REPLACED = (
    # the stock panel, its colours, its scrollbar and a row hit test of its own
    "dev/settledlands/npc/client/BlockMenu363.class",
    # the clicked row comes from that hit test now
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
)
ADDED = ()
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
    "Build 366: the right-click menu of a block's graph is the stock panel again. Build 365 fitted "
    "eleven rows into a panel nearly twice the height of the stock one, and that broke four things: "
    "the window grew past the canvas, the scrollbar disappeared, every row was painted the same grey "
    "instead of the colour of its node, and no row below the fifth could be clicked - the stock hit "
    "test only knows five rows. The panel is now the stock five-row list with the stock colours, the "
    "stock scrollbar on the right, and a window of five rows that the wheel and the strip move; the "
    "row of a click is counted from that window, so all eleven rows of a block can be reached. "
    "Everything 365 added stays: Кнопка, Время, Таймер, Катсцена and Цель, and every one of them "
    "fired or waited for by the tick pass or the walk."
)
PATCHINFO = (
    "Сборка 366: меню по правой кнопке на графе блока снова штатная панель. В 365 одиннадцать строк "
    "влезли в панель почти вдвое выше штатной, и это сломало четыре вещи сразу: окно стало больше "
    "экрана, пропала полоса прокрутки, все строки красились одним серым вместо цвета своей ноды, и "
    "ни одна строка ниже пятой не нажималась - штатный хит-тест знает только пять строк. Теперь панель "
    "снова штатная: пять строк, цвета штатного меню, полоса прокрутки справа, и окно из пяти строк, "
    "которое двигают колесо и полоса; строка клика считается от этого окна, поэтому доступны все "
    "одиннадцать. Всё, что дала 365, остаётся: Кнопка, Время, Таймер, Катсцена и Цель, и каждая из них "
    "запускается проходом или обходом.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out366"

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
