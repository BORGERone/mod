#!/usr/bin/env python3
"""
Build 367 - the panel scrolls the way the stock one does, and a radius zone starts switched off.

Base 366. Two reports from the author, both small and both plain in the code once looked at.

  * <b>the scrollbar read inverted</b> - the panel moved the window by {@code scrollY > 0 ? +1 : -1}
    while the stock screen moves it the other way round ({@code scrollY > 0 ? -1 : +1}). The
    direction now lives in one pure function, {@code BlockMenu363#wheelStep}, the strip click goes
    through the same {@code scroll(int)}, and the probe pins the sign.
  * <b>a radius zone was drawn the moment the node was placed</b> - the flag behind the toggle is
    {@code Node#timerRepeat}, a stock field whose own default is {@code true}, so every new «В
    радиусе» and «Цель» came with a particle ring. {@code RadiusPass354#hideByDefault} is the rule and
    {@code RadiusDefault367Mixin} applies it at {@code NpcDialogueGraph#add} - the one place a node is
    born, shared by the NPC editor, the block editor and a paste. A node saved switched on keeps it:
    this is a default for new nodes, not an eraser for old ones.

One class added, two replaced:

    RadiusDefault367Mixin      added: the default-off rule on every node the graph creates (common)
    RadiusPass354              replaced: hideByDefault, next to visible/setVisible
    BlockMenu363               replaced: wheelStep + scroll(int), the stock wheel direction
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build366-menu-fix"
NEW_VERSION = "0.8.0-build367-zone-default"
OUT_NAME = "settledlands-0.8.0-build367-zone-default.jar"

REMOVED = ()
REPLACED = (
    # the default-off rule for a radius node
    "dev/settledlands/npc/RadiusPass354.class",
    # the wheel direction of the panel
    "dev/settledlands/npc/client/BlockMenu363.class",
)
ADDED = (
    # applied to every node NpcDialogueGraph#add creates
    "dev/settledlands/mixin/RadiusDefault367Mixin.class",
)
MIXINS = ("RadiusDefault367Mixin",)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/RadiusDefault367Mixin.class": ("hideByDefault", "add(III)"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 367: the block menu scrolls the stock way - the wheel was inverted, so a notch up moved "
    "the window down and the scrollbar read backwards; the direction is one function now and the "
    "strip click uses it. And the zone of a radius node starts switched off: the saved flag behind "
    "the toggle defaults to true in the stock node, so every new «В радиусе» and «Цель» came with a "
    "particle ring already drawn. Every node NpcDialogueGraph#add creates - in an NPC graph, in a "
    "block graph, pasted - now starts with the flag off, for both radius node types and nothing else. "
    "A node that was saved switched on keeps its ring. Everything of 365 and 366 stays: the five new "
    "block nodes, the stock five-row panel with its colours and its scrollbar."
)
PATCHINFO = (
    "Сборка 367: меню блока листается как штатное - колесо было инвертировано, прокрутка вверх "
    "двигала список вниз, из-за чего полоса читалась наоборот; направление теперь в одной функции, и "
    "клик по полосе идёт через неё же. И зона у ноды с радиусом по умолчанию выключена: сохранённый "
    "флаг тумблера в штатной ноде по умолчанию true, поэтому каждая новая «В радиусе» и «Цель» "
    "появлялись уже с нарисованным кольцом. Каждая нода, которую создаёт NpcDialogueGraph#add - в "
    "графе NPC, в графе блока, при вставке - теперь стартует с выключенным флагом, и только два типа "
    "нод с радиусом. Нода, сохранённая включённой, кольцо сохраняет. Всё из 365 и 366 остаётся: пять "
    "новых нод блока, штатная панель на пять строк с цветами и полосой прокрутки.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out367"

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
