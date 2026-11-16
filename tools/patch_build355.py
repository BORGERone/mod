#!/usr/bin/env python3
"""
Build 355 - the radius toggle takes effect on the click, and a block can be cleared with a button.

Base 354. Three classes added, one replaced, one removed:

    NpcRadiusRow355Mixin    (client) the "Видимость" row, now saving right after the flip
    NpcBlockDelete355Mixin  (client) "Удалить граф" in the top bar of the block editor, two clicks
    BlockTrigger351         replaced: an empty graph now removes the entry instead of keeping it

    NpcRadiusRow354Mixin    removed: replaced by the row above

The ring is drawn by the server from the graph it stores; the flag used to live only in the editor
until the graph was saved, so a toggle that looked switched off kept the ring where it was. The row
saves on the click now, and the delete button sends the same save with an empty graph, which the
server turns into "no graph at all".
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build354-radius-motion"
NEW_VERSION = "0.8.0-build355-radius-toggle"
OUT_NAME = "settledlands-0.8.0-build355-radius-toggle.jar"

REMOVED = (
    # the row that only changed the client-side flag; the ring is drawn by the server
    "dev/settledlands/mixin/NpcRadiusRow354Mixin.class",
)
REPLACED = (
    # an empty graph is no graph: the entry is dropped instead of kept
    "dev/settledlands/npc/BlockTrigger351.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcRadiusRow355Mixin.class",
    "dev/settledlands/mixin/NpcBlockDelete355Mixin.class",
)

MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("NpcRadiusRow355Mixin", "NpcBlockDelete355Mixin")
CLIENT_REMOVED = ("NpcRadiusRow354Mixin",)

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcRadiusRow355Mixin.class": (
        "widgets", "visible_on", "save"),
    "dev/settledlands/mixin/NpcBlockDelete355Mixin.class": (
        "widgets", "block.delete", "isBlockEditorId"),
}

LANG_SET = {
    "gui.settledlands.block.delete": "Удалить граф",
    "gui.settledlands.block.delete_ask": "Нажми ещё раз",
    "message.settledlands.block.deleted": "Граф блока удалён · %s",
}
LANG_SET_EN = {
    "gui.settledlands.block.delete": "Delete graph",
    "gui.settledlands.block.delete_ask": "Click again",
    "message.settledlands.block.deleted": "Block graph removed · %s",
}

CHANGELOG_EN = (
    "Build 355 makes the radius toggle and the block graph controllable. The \"Видимость\" row now "
    "saves the moment it is clicked: the ring is drawn by the server, and until the graph was saved "
    "the flag changed only in the editor, so a toggle switched off could leave the ring on. A block "
    "also gets a delete button in the top bar of its editor - two clicks, the second one within five "
    "seconds - which empties the graph, resets the start node and saves; the server then drops the "
    "block's entry entirely, so it stops sparkling and a right click on it does nothing, exactly like "
    "a block that was never configured."
)
PATCHINFO = (
    "Сборка 355: тумблер «Видимость» применяется сразу по клику (строка сохраняет граф, потому что "
    "кольцо рисует сервер), и у блока появилась кнопка «Удалить граф» в верхней панели редактора — "
    "в два клика, второй в течение пяти секунд. Пустой граф на сервере теперь означает «графа нет»: "
    "запись блока удаляется, подсветка гаснет, ПКМ пустой рукой ничего не делает.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out355"

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
