#!/usr/bin/env python3
"""
Build 358 - a block says its line in the dialogue window, and the camera looks at the block.

Base 357. Three classes replaced, six added:

    BlockTrigger351          replaced: the walk hands a line to the window and stops there; the chat
                             is only the fallback when no window can be shown
    RadiusPass354            replaced: a radius node tells the chain which block it lives on
    NpcBlockOpen351Mixin     replaced: a block config view with coordinates is a talk, not an editor
    BlockTalk358             added: the window, the aim packet, the pick/close handshake          (new)
    BlockTalk358$Where       added: the block a chain belongs to                                   (new)
    BlockTalk358$Open        added: one open window                                                (new)
    BlockTalkClient358       added: the client half of the aim                                    (new)
    BlockTalkPick358Mixin    added: a click in a block window continues the block's chain         (new)
    BlockTalkStop358Mixin    added: closing a block window drops the block's chain                (new)

Build 357 said a block's replica in the chat, because a block has no body to show a window for. That
was the wrong call: the window is not the body. NpcNet.DlgView carries an id, a speaker, a line and a
style, and the only thing the screen does with the entity is aim the camera at it - which is null safe
for an id no entity answers for. So a block gets the stock window under its own block id, and the aim
is done by the client from the block's coordinates, sent one packet ahead of the window.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build357-block-line"
NEW_VERSION = "0.8.0-build358-block-talk"
OUT_NAME = "settledlands-0.8.0-build358-block-talk.jar"

REMOVED = ()
REPLACED = (
    # the line of a chain opens the window and the chain waits for it; the chat is the fallback
    "dev/settledlands/npc/BlockTrigger351.class",
    # a radius node hands the chain the block it lives on, so its line can aim too
    "dev/settledlands/npc/RadiusPass354.class",
    # a block config view with coordinates is a talk, without them the editor
    "dev/settledlands/mixin/NpcBlockOpen351Mixin.class",
)
ADDED = (
    # the window itself, the aim packet, the pick/close handshake
    "dev/settledlands/npc/BlockTalk358.class",
    "dev/settledlands/npc/BlockTalk358$Where.class",
    "dev/settledlands/npc/BlockTalk358$Open.class",
    # the client half: aim the head at the block before the window is drawn
    "dev/settledlands/npc/client/BlockTalkClient358.class",
    # a click in a block window continues the block's chain
    "dev/settledlands/mixin/BlockTalkPick358Mixin.class",
    # closing a block window drops the block's chain
    "dev/settledlands/mixin/BlockTalkStop358Mixin.class",
)

MIXINS = ("BlockTalkPick358Mixin", "BlockTalkStop358Mixin")
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/BlockTalkPick358Mixin.class": (
        "dev.settledlands.npc.GuideDialogue", "settledlands$blockTalkPick"),
    "dev/settledlands/mixin/BlockTalkStop358Mixin.class": (
        "dev.settledlands.npc.GuideDialogue", "settledlands$blockTalkStop"),
}

LANG_SET = {}
LANG_SET_EN = {}

# The palette note is not new - it names the nodes a block can build, and build 357 adds one. Each
# entry is (what the base jar must say, what this build says), so a changed base is a hard error.
LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 358 gives a block a voice you can see. The \"Реплика\" node of a block graph no longer "
    "speaks in the chat: it opens the stock dialogue window - the very screen an NPC opens - and the "
    "camera turns to the block while that window is up, so the author reads the line with the block in "
    "view. The chain waits for the window: a click, E or Enter walks on from the node after the line, "
    "Esc drops the chain, exactly like an NPC line. The window blocks the mouse look and the movement "
    "for as long as it is open, so the view stays on the block. The chat of build 357 survives only as "
    "the fallback for the cases where no window can be shown at all - no player, a blank line, or a "
    "style the server cannot build."
)
PATCHINFO = (
    "Сборка 358: «Реплика» блока открывает настоящее окно диалога — то же самое, что открывает НПС, — "
    "и камера при этом повёрнута на блок. Цепочка ждёт окно: ЛКМ, E или Enter идут дальше по ноде после "
    "реплики, Esc обрывает цепочку. Пока окно открыто, обзор мышью и движение заблокированы, чтобы вид "
    "оставался на блоке. Чат из сборки 357 остался только запасным путём — когда окно показать нельзя "
    "(нет игрока, пустой текст, недоступный стиль).\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out358"

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
