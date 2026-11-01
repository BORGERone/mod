#!/usr/bin/env python3
"""Build 306 - the node workspace of our own.

Build 305 put the node library inside the stock editor. This build takes the next step asked for:
the graph is no longer drawn by the stock screen at all. A screen of ours opens instead, owns the
canvas, the cards and the cables, and keeps the stock editor one button away.

What the new screen does:

  * three tabs - dialogue, walk, animation - each holding its own NpcDialogueGraph, the same ones
    the editor edits, so the file format and the save packet do not change;
  * cards of our own: header per node type, wrapped body, round white ports, nodes dimmed when the
    graph hides them;
  * cables drawn from the graph itself - next, false branch, value wire, choices, triggers, random
    branches - one bezier per cable, colour by kind;
  * drag, box select, pan (middle drag), zoom (wheel), port to port linking, cable cut, delete,
    add node from the library, undo and redo;
  * the node library from Build 305 down the left, with its search field, now feeding this screen;
  * save through the stock packet (NpcNet$CfgSave), the same one the editor sends.

The stock editor is untouched and stays one button away: every field, every inspector and every
panel that existed there still exists there.

Usage: python3 tools/patch_build306.py <base-305.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build305-node-library"
NEW_VERSION = "0.8.0-build306-workspace"
OUT_NAME = "settledlands-0.8.0-build306-workspace.jar"

ADDED = (
    "dev/settledlands/npc/client/NpcWorkspace306.class",
    "dev/settledlands/npc/client/NpcWorkspace306$Link.class",
    "dev/settledlands/mixin/NpcWorkspaceEntry306Mixin.class",
)

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/NpcWorkspace306.class":
        ["drawCables", "drawNodes", "drawTopBar", "links", "mouseClicked", "keyPressed",
         "addNode"],
    "dev/settledlands/mixin/NpcWorkspaceEntry306Mixin.class": ["settledlands$openWorkspace"],
}

MIXIN = "NpcWorkspaceEntry306Mixin"

LANG_RU = {
    "gui.settledlands.workspace.title": "\u041d\u043e\u0434\u044b NPC",
    "gui.settledlands.workspace.legacy": "\u0421\u0442\u0430\u0440\u044b\u0439 \u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440",
    "gui.settledlands.workspace.newGraph": "+ \u0421\u043e\u0437\u0434\u0430\u0442\u044c \u0433\u0440\u0430\u0444",
}
LANG_EN = {
    "gui.settledlands.workspace.title": "NPC nodes",
    "gui.settledlands.workspace.legacy": "Old editor",
    "gui.settledlands.workspace.newGraph": "+ Create graph",
}

CHANGELOG_EN = (
    "Build 306 opens the node graph in a workspace of our own instead of the stock editor. The "
    "canvas, the cards and the cables are ours: three tabs on the graphs that already exist, "
    "dragging, box selection, panning and zooming, port to port linking, a cable cut, deletion, "
    "adding nodes from the library with its search field, and undo. Saving still goes through the "
    "same packet as before, so nothing on disk changes. Every field is still edited in the editor, "
    "which is one button away. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 306: \u0433\u0440\u0430\u0444 \u043d\u043e\u0434 "
    "\u043e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u0442\u0441\u044f \u0432 "
    "\u043d\u0430\u0448\u0435\u043c \u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441\u0435. "
    "\u0425\u043e\u043b\u0441\u0442, \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0438 \u0438 "
    "\u043a\u0430\u0431\u0435\u043b\u0438 \u2014 \u0441\u0432\u043e\u0438: \u0442\u0440\u0438 "
    "\u0432\u043a\u043b\u0430\u0434\u043a\u0438, \u043f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u0435, "
    "\u0440\u0430\u043c\u043a\u0430 \u0432\u044b\u0434\u0435\u043b\u0435\u043d\u0438\u044f, "
    "\u043f\u0430\u043d\u043e\u0440\u0430\u043c\u0430 \u0438 \u0437\u0443\u043c, "
    "\u0441\u0432\u044f\u0437\u044c \u043f\u043e\u0440\u0442-\u043f\u043e\u0440\u0442, "
    "\u0440\u0435\u0437\u043a\u0430 \u043a\u0430\u0431\u0435\u043b\u044f, \u0443\u0434\u0430\u043b\u0435\u043d\u0438\u0435, "
    "\u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0438\u0437 "
    "\u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0438 \u0441 \u043f\u043e\u0438\u0441\u043a\u043e\u043c "
    "\u0438 \u043e\u0442\u043c\u0435\u043d\u0430. \u0421\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435 \u2014 "
    "\u0442\u0435\u043c \u0436\u0435 \u043f\u0430\u043a\u0435\u0442\u043e\u043c. \u041f\u043e\u043b\u044f "
    "\u043f\u0440\u0430\u0432\u044f\u0442\u0441\u044f \u0432 \u0441\u0442\u0430\u0440\u043e\u043c "
    "\u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440\u0435, \u043a\u043d\u043e\u043f\u043a\u0430 \u0440\u044f\u0434\u043e\u043c. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build305-src" / "out306"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in ADDED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for want in EXPECTED_METHODS.get(name, []):
            if want.encode("utf-8") not in data:
                raise SystemExit("%s does not look like the build we expect (no %s)"
                                 % (name, want))
        if name in blobs:
            raise SystemExit("base jar already has %s" % name)
        blobs[name] = data
        order.append(name)
        print("  added: %s (%d bytes)" % (name, len(data)))

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    if MIXIN not in mixins.setdefault("client", []):
        mixins["client"].append(MIXIN)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(mixins.get("mixins", [])),
                                                   len(mixins["client"])))

    for path, extra in (("assets/settledlands/lang/ru_ru.json", LANG_RU),
                        ("assets/settledlands/lang/en_us.json", LANG_EN)):
        if path not in blobs:
            print("  lang: %s missing in base, skipped" % path)
            continue
        lang = json.loads(blobs[path].decode("utf-8"))
        before = len(lang)
        lang.update(extra)
        blobs[path] = (json.dumps(lang, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  lang: %s +%d keys" % (path, len(lang) - before))

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
