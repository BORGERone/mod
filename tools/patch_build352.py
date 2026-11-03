#!/usr/bin/env python3
"""
Build 352 - the "В радиусе" node reaches blocks, blocks with graphs spark while the wand is held, and
the node's visibility toggle draws its radius around the block.

Base 351. Two classes added, three replaced:

    BlockTrigger352          the tick pass: radius node, radius ring, wand highlight (ServerTickEvent)
    NpcBlockCheckRow352Mixin (client) the "Видимость" row of the node's inspector, block editor only

    BlockTriggerStore351     replaced: per-dimension index + the two flags the pass asks about
    BlockTrigger351          replaced: the radius node in the palette, and fire() for the tick pass
    NpcBlockGraph351Mixin    replaced: palette row 5 (В радиусе) added

The pass runs twice a second, walks only the blocks of the dimension, and only does work for entries
whose flags are set - no per-block ticking was introduced.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build351-block-trigger"
NEW_VERSION = "0.8.0-build352-block-radius"
OUT_NAME = "settledlands-0.8.0-build352-block-radius.jar"

REMOVED = ()
REPLACED = (
    # the store gains a per-dimension index and the two flags the tick pass asks about
    "dev/settledlands/npc/BlockTriggerStore351.class",
    # the walker gains the radius node and fire(), used by the tick pass
    "dev/settledlands/npc/BlockTrigger351.class",
    # the palette gains the radius node
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
)
ADDED = (
    "dev/settledlands/npc/BlockTrigger352.class",
    "dev/settledlands/mixin/NpcBlockCheckRow352Mixin.class",
)

MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("NpcBlockCheckRow352Mixin",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/BlockTriggerStore351.class": (
        "settledlands_block_triggers", "ofDimension", "check", "ring"),
    "dev/settledlands/npc/BlockTrigger351.class": (
        "BlockGraph", "DialogueGraph", "evalCondition", "NpcActions", "fire"),
    "dev/settledlands/npc/BlockTrigger352.class": (
        "ServerTickEvent", "ELECTRIC_SPARK", "ENCHANT", "RING_POINTS"),
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class": (
        "mouseClicked", "switchTab", "contextMenu", "addAt"),
    "dev/settledlands/mixin/NpcBlockCheckRow352Mixin.class": (
        "widgets", "visible_on", "setVisible"),
}

LANG_SET = {
    # build 351 keys stay as they are; only the palette text changes and three rows are new
    "gui.settledlands.block.palette": "У блока доступны: Триггер, Условие If, Переменная, Случайность, В радиусе",
    "gui.settledlands.block.visible": "Видимость",
    "gui.settledlands.block.visible_on": "Видимость радиуса: да",
    "gui.settledlands.block.visible_off": "Видимость радиуса: нет",
}
LANG_SET_EN = {
    "gui.settledlands.block.palette": "A block can run: Trigger, If, Variable, Random, In radius",
    "gui.settledlands.block.visible": "Visibility",
    "gui.settledlands.block.visible_on": "Show the radius: yes",
    "gui.settledlands.block.visible_off": "Show the radius: no",
}

CHANGELOG_EN = (
    "Build 352 brings the \"В радиусе\" node to blocks and makes the block graphs visible. The node is "
    "in the block palette now and it is an event node: a block tick pass, twice a second, checks the "
    "radius with the node's own rules (target, radius, per-body cooldown) and, when somebody is in it, "
    "runs the node's action and continues the chain. Its \"Видимость\" row in the block inspector draws "
    "the radius as a ring of particles around the block, and every block that carries a graph sparks "
    "on its edges while the wand of NPC settings is in the player's hand, so the author can see what "
    "is already wired up. Nothing new ticks: the pass walks only the blocks of one dimension and only "
    "the entries whose flags are set."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 352: \\u043d\\u043e\\u0434\\u0430 "
    "\\u00ab\\u0412 \\u0440\\u0430\\u0434\\u0438\\u0443\\u0441\\u0435\\u00bb "
    "\\u0443 \\u0431\\u043b\\u043e\\u043a\\u0430, \\u043f\\u043e\\u0434\\u0441"
    "\\u0432\\u0435\\u0442\\u043a\\u0430 \\u0431\\u043b\\u043e\\u043a\\u043e"
    "\\u0432 \\u0441 \\u0433\\u0440\\u0430\\u0444\\u0430\\u043c\\u0438 \\u043f"
    "\\u0440\\u0438 \\u0436\\u0435\\u0437\\u043b\\u0435 \\u0438 \\u0442\\u0443"
    "\\u043c\\u0431\\u043b\\u0435\\u0440 \\u00ab\\u0412\\u0438\\u0434\\u0438"
    "\\u043c\\u043e\\u0441\\u0442\\u044c\\u00bb."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out352"

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
