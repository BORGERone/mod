#!/usr/bin/env python3
"""
Build 357 - a block can say a line: the stock «Реплика» node joins the palette of a block graph.

Base 356. Two classes replaced, nothing added or removed:

    BlockTrigger351         replaced: the line node is in PALETTE and the walk says it in the chat
    NpcBlockGraph351Mixin   replaced: row 0 of the stock palette now builds a line node, and the
                            row -> type map moved into BlockTrigger351#typeForRow

A block has no body, so there is no dialogue window to show a replica in - the block speaks in the
chat of the player who triggered the chain. The node itself is the stock one (type 0, the same node
every NPC graph uses), which means the inspector's text box, the canvas card, save/load and copy/paste
all already know it; the only new code is one branch in the walk and one line in the palette.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build356-radius-flag"
NEW_VERSION = "0.8.0-build357-block-line"
OUT_NAME = "settledlands-0.8.0-build357-block-line.jar"

REMOVED = ()
REPLACED = (
    # the line node in the palette, the row map and the line in the chain walk
    "dev/settledlands/npc/BlockTrigger351.class",
    # row 0 of the stock palette -> a line node, through BlockTrigger351#typeForRow
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
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

# The palette note is not new - it names the nodes a block can build, and build 357 adds one. Each
# entry is (what the base jar must say, what this build says), so a changed base is a hard error.
LANG_CHANGE = {
    "assets/settledlands/lang/ru_ru.json": {
        "gui.settledlands.block.palette": (
            "У блока доступны: Триггер, Условие If, Переменная, Случайность, В радиусе",
            "У блока доступны: Реплика, Триггер, Условие If, Переменная, Случайность, В радиусе",
        ),
    },
    "assets/settledlands/lang/en_us.json": {
        "gui.settledlands.block.palette": (
            "A block can run: Trigger, If, Variable, Random, In radius",
            "A block can run: Line, Trigger, If, Variable, Random, In radius",
        ),
    },
}

CHANGELOG_EN = (
    "Build 357 lets a block speak. The stock \"Реплика\" node - the same line node every NPC graph "
    "has - is now in the palette of a block graph: the block says the text in the chat of the player "
    "who triggered it, with the usual %player% placeholder filled, and the chain continues at once. "
    "A block has no body, so there is no dialogue window to wait a click in; the chat is its voice. "
    "The palette note names the new node, and the row -> type map moved into BlockTrigger351 so the "
    "editor, the walker and the gate read one list."
)
PATCHINFO = (
    "Сборка 357: блоку добавлена нода «Реплика» — та же нода-строка, что есть у НПС (тип 0). Блок "
    "говорит текст в чат игроку, который запустил цепочку, с подстановкой %player%; цепочка идёт "
    "дальше сразу, потому что окна диалога у блока нет. В палитре блока теперь шесть нод (Реплика, "
    "Триггер, Условие If, Переменная, Случайность, В радиусе), подпись палитры обновлена, а карта "
    "«строка палитры → тип ноды» переехала в BlockTrigger351#typeForRow.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out357"

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
