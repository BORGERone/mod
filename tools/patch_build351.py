#!/usr/bin/env python3
"""
Build 351 - a block can carry a graph: right click it with the NPC wand to edit, with an empty hand to
run it.

Base 350 (build 347's composition). Seven new classes, three of them mixins:

    BlockTriggerStore351   the graphs, in world data (SavedData), keyed by dimension@x,y,z
    BlockTrigger351        ids for the editor, the save, and the chain walker
    BlockTriggerEvents351  right click on a block: wand -> editor, empty hand -> run
    BlockTriggerClient351  client: open the stock graph editor on the block's graph
    NpcBlockOpen351Mixin   (client)  NpcConfigScreen#open: a block id opens the graph editor
    NpcBlockGraph351Mixin  (client)  palette cut to the four nodes a block can run; one tab only
    NpcBlockSave351Mixin   (common)  GuideNpc#applyConfig: a block save lands in world data

The save hook is inserted at the HEAD of the common mixin list, before the shared-graph hook of build
334, so a block save is cancelled before that hook could mistake it for the shared "Общее" graph.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build350-armor-off"
NEW_VERSION = "0.8.0-build351-block-trigger"
OUT_NAME = "settledlands-0.8.0-build351-block-trigger.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/BlockTriggerStore351.class",
    "dev/settledlands/npc/BlockTrigger351.class",
    "dev/settledlands/npc/BlockTriggerEvents351.class",
    "dev/settledlands/npc/client/BlockTriggerClient351.class",
    "dev/settledlands/mixin/NpcBlockOpen351Mixin.class",
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
    "dev/settledlands/mixin/NpcBlockSave351Mixin.class",
)

MIXINS = ("NpcBlockSave351Mixin",)
MIXINS_FIRST = ("NpcBlockSave351Mixin",)
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("NpcBlockOpen351Mixin", "NpcBlockGraph351Mixin")
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcBlockSave351Mixin.class": (
        "applyConfig", "isBlockEditorId", "BlockTrigger351"),
    "dev/settledlands/mixin/NpcBlockOpen351Mixin.class": (
        "open", "NpcConfigScreen", "BlockTriggerClient351"),
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class": (
        "mouseClicked", "switchTab", "contextMenu", "addAt"),
    "dev/settledlands/npc/BlockTriggerStore351.class": (
        "settledlands_block_triggers", "Blocks", "computeIfAbsent"),
    "dev/settledlands/npc/BlockTrigger351.class": (
        "BlockGraph", "DialogueGraph", "evalCondition", "NpcActions"),
    "dev/settledlands/npc/BlockTriggerEvents351.class": (
        "RightClickBlock", "settledlands"),
    "dev/settledlands/npc/client/BlockTriggerClient351.class": (
        "NpcDialogueGraphScreen", "BlockGraph"),
}

LANG_SET = {
    "message.settledlands.block.open": "Граф блока \u00b7 %s",
    "message.settledlands.block.saved": "Граф блока сохранён: %s \u00b7 узлов: %s",
    "message.settledlands.block.full": "Достигнут лимит блоков с графом (%s)",
    "gui.settledlands.block.palette": "У блока доступны: Триггер, Условие If, Переменная, Случайность",
    "gui.settledlands.block.one_tab": "У блока одна вкладка — главная",
}
LANG_SET_EN = {
    "message.settledlands.block.open": "Block graph \u00b7 %s",
    "message.settledlands.block.saved": "Block graph saved: %s \u00b7 nodes: %s",
    "message.settledlands.block.full": "Block graph limit reached (%s)",
    "gui.settledlands.block.palette": "A block can run: Trigger, If, Variable, Random",
    "gui.settledlands.block.one_tab": "A block has one tab: the main graph",
}

CHANGELOG_EN = (
    "Build 351 gives blocks a graph. Right click a block with the NPC wand and the stock graph editor "
    "opens on that block's own graph - the whole editor: nodes, wires, undo, copy, inspector. Right "
    "click the same block with an empty hand and its chain runs; with no start node set, nothing "
    "happens. The palette of a block holds the four nodes a block can actually run - Trigger (actions "
    "and commands, run with the clicking player as the command source), If, Variable (the same named "
    "points the NPC graphs use) and Random; the rows that need an NPC body (line, button, quest, "
    "animation, movement, route, cutscene) and the rows that need an event a block does not have "
    "(check, damage, time, timer) say so instead of doing nothing. Graphs live in world data, one per "
    "dimension and position, so a broken and replaced block keeps its graph."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 351: \\u0442\\u0440\\u0438\\u0433\\u0433\\u0435"
    "\\u0440 \\u0431\\u043b\\u043e\\u043a\\u043e\\u0432. \\u041f\\u041a\\u041c \\u0436\\u0435"
    "\\u0437\\u043b\\u043e\\u043c \\u043f\\u043e \\u0431\\u043b\\u043e\\u043a\\u0443 \\u2014 "
    "\\u0433\\u0440\\u0430\\u0444 \\u0431\\u043b\\u043e\\u043a\\u0430 \\u0432 \\u0448\\u0442"
    "\\u0430\\u0442\\u043d\\u043e\\u043c \\u0440\\u0435\\u0434\\u0430\\u043a\\u0442\\u043e\\u0440"
    "\\u0435; \\u041f\\u041a\\u041c \\u043f\\u0443\\u0441\\u0442\\u043e\\u0439 \\u0440\\u0443"
    "\\u043a\\u043e\\u0439 \\u2014 \\u0437\\u0430\\u043f\\u0443\\u0441\\u043a \\u0446\\u0435\\u043f"
    "\\u043e\\u0447\\u043a\\u0438."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out351"

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
