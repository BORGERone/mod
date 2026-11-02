#!/usr/bin/env python3
"""
Build 336 - a "Реплика" node can roll for one of its phrases.

  The author flips a toggle on the node, writes several phrases separated by a newline, and the node
  shows one of them: the dice are thrown once, when the toggle is flipped, and the result is kept in
  the node - so from then on that node always says that phrase. Nothing re-rolls per conversation.

  The roll is kept in Node#varValue, a saved integer whose default is 0 and which only the variable
  and IF branches read: 0 means "off", 1..n means "on, and this is the phrase". One field carries
  the flag and the result, so save, load, copy, undo and the clipboard already carry it.

  Base: 335. New classes: the rule, the node hook, the on-canvas toggle and its hook.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build335-common-buttons"
NEW_VERSION = "0.8.0-build336-random-line"
OUT_NAME = "settledlands-0.8.0-build336-random-line.jar"

REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcRandomLine336.class",
    "dev/settledlands/mixin/NpcRandomLineNodeMixin336.class",
    "dev/settledlands/npc/client/NpcRandomLineUi336.class",
    "dev/settledlands/mixin/NpcRandomLineUiMixin336.class",
)

# The rule and the node hook are common; the toggle draws and clicks, so it is client only.
MIXINS = ("NpcRandomLineNodeMixin336",)
CLIENT_MIXINS = ("NpcRandomLineUiMixin336",)

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcRandomLineNodeMixin336.class": ("lineFor", "settledlands$randomLine"),
    "dev/settledlands/mixin/NpcRandomLineUiMixin336.class": (
        "drawNode", "mouseClicked", "settledlands$clickRandomLine"),
    "dev/settledlands/npc/NpcRandomLine336.class": ("varValue", "phrases", "reroll"),
    "dev/settledlands/npc/client/NpcRandomLineUi336.class": ("settledlands$canvas", "rand_line_on"),
}

LANG_SET = {
    "gui.settledlands.graph.rand_line_off": "\U0001f3b2 \u0421\u043b\u0443\u0447\u0430\u0439"
                                            "\u043d\u0430\u044f \u0440\u0435\u043f\u043b\u0438"
                                            "\u043a\u0430",
    "gui.settledlands.graph.rand_line_on": "\U0001f3b2 \u0421\u0442\u0440\u043e\u043a\u0430 %s "
                                           "\u0438\u0437 %s",
}
LANG_SET_EN = {
    "gui.settledlands.graph.rand_line_off": "\U0001f3b2 Random line",
    "gui.settledlands.graph.rand_line_on": "\U0001f3b2 Line %s of %s",
}

CHANGELOG_EN = (
    "Build 336 adds a random answer to the \"Line\" node. A small toggle above the node throws the "
    "dice once for that node and remembers the result inside the node itself; write several phrases "
    "separated by a newline and the node always says the phrase it rolled, while every placeholder "
    "in it is still filled per player. Turning the toggle off (and on again) rolls anew; editing the "
    "text does not re-roll, so the line stays the one that was chosen."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 336: \u0443 \u043d\u043e\u0434\u044b "
    "\u00ab\u0420\u0435\u043f\u043b\u0438\u043a\u0430\u00bb \u043f\u043e\u044f\u0432\u0438\u043b"
    "\u0441\u044f \u0442\u0443\u043c\u0431\u043b\u0435\u0440 \u00ab\u0421\u043b\u0443\u0447"
    "\u0430\u0439\u043d\u0430\u044f \u0440\u0435\u043f\u043b\u0438\u043a\u0430\u00bb: "
    "\u0431\u0440\u043e\u0441\u043e\u043a \u0434\u0435\u043b\u0430\u0435\u0442\u0441\u044f "
    "\u043e\u0434\u0438\u043d \u0440\u0430\u0437 \u0438 \u0437\u0430\u043f\u0438\u0441\u044b"
    "\u0432\u0430\u0435\u0442\u0441\u044f \u0432 \u043d\u043e\u0434\u0443. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out336"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

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
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key in strings:
                raise SystemExit("%s already has %s" % (path, key))
            strings[key] = value
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, value))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS:
        if name not in common:
            common.append(name)
    for name in CLIENT_MIXINS:
        if name not in client:
            client.append(name)
        if name in common:
            raise SystemExit("%s must not be a common mixin - it draws on a client screen" % name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common (+%s), %d client (+%s)"
          % (len(common), ", ".join(MIXINS), len(client), ", ".join(CLIENT_MIXINS)))

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
