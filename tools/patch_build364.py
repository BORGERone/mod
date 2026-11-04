#!/usr/bin/env python3
"""
Build 364 - the right-click menu of a block's graph offers only the nodes a block can run.

Base 363. Build 351 taught a block's editor to refuse the rows of the stock palette it cannot run: a
click closed the menu and wrote a line into the status bar. The rows stayed in the menu, so the palette
of a block could only be learned by trying all sixteen. The author asked for the list itself to be the
answer.

  * BlockMenu363 asks BlockTrigger351#typeForRow - the same map the palette and the in-game note come
    from - which stock rows a block has, and keeps them in stock order: Реплика, Триггер, Условие If,
    Переменная, В радиусе, Случайность. Nothing is copied: a node type that becomes available later
    appears in the menu without a second edit.
  * BlockMenu363Mixin measures the panel when it opens (the menu is opened inside the screen's
    mouseReleased), paints it with the stock colours and row height, and stops the wheel from moving
    rows that all fit. The copy/delete menu of an existing node is left completely stock.
  * NpcBlockGraph351Mixin maps the row of the shorter panel back to the stock row before it asks for
    the node type, so a click still builds the node the row says.

Two classes added, one replaced:

    BlockMenu363               added: the rows a block has, and the panel that shows them
    BlockMenu363Mixin          added: measure / paint / no-scroll for a block's graph (client)
    NpcBlockGraph351Mixin      replaced: click row is the row of the shorter panel
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build363-name-colour-and-delete"
NEW_VERSION = "0.8.0-build364-block-menu"
OUT_NAME = "settledlands-0.8.0-build364-block-menu.jar"

REMOVED = ()
REPLACED = (
    # a click on a row of the shorter panel asks for the stock row behind it
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
)
ADDED = (
    # the rows a block has, and the panel that paints them
    "dev/settledlands/npc/client/BlockMenu363.class",
    # measure at open, paint in place of the stock panel, no wheel while the menu is open
    "dev/settledlands/mixin/BlockMenu363Mixin.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("BlockMenu363Mixin",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/BlockMenu363Mixin.class": ("drawContextMenu", "BlockMenu363"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 364: the right-click menu of a block's graph offers only the nodes a block can run. "
    "Before, the menu had all sixteen rows of the stock editor and a click on a row a block cannot "
    "use was answered with a line of text - the palette had to be learned by trial. The rows now come "
    "from the same map the editor already used to refuse them (Реплика, Триггер, Условие If, "
    "Переменная, В радиусе, Случайность), painted in the stock panel and order, and the copy/delete "
    "menu of an existing node is unchanged. Everything 363 fixed stays: the name colour and the name "
    "are saved, and the block's «delete graph» button is on the bar."
)
PATCHINFO = (
    "Сборка 364: в меню по правой кнопке на графе блока остались только те ноды, которые блок "
    "умеет выполнять. Раньше в меню были все шестнадцать строк штатного редактора, а клик по "
    "недоступной строке просто писал строку в статус — набор нод блока приходилось выяснять "
    "перебором. Теперь строки берутся из той же карты, которой редактор и раньше отказывал "
    "(Реплика, Триггер, Условие If, Переменная, В радиусе, Случайность), рисуются штатной панелью и в "
    "штатном порядке, а меню копировать/удалить у существующей ноды не тронуто. Всё, что исправила "
    "363, остаётся: цвет имени и имя сохраняются, кнопка «Удалить граф» на месте.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out364"

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
