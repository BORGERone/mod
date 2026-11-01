#!/usr/bin/env python3
"""Build 306 - the node workspace is the editor.

The dialogue editor is no longer the stock screen with a palette on top: `open()` now hands the
player to a screen of our own that owns the graph.

  * NpcWorkspace306 - camera, dot canvas, cards, ports, cables, selection, box select, dragging,
    linking from port to port, cable cut, node library with search, three tabs, undo/redo, save
    and the "old editor" fallback. Everything is drawn with WorkspaceTheme305, the port and the
    card geometry the canvas already uses, and every link is read from and written to
    NpcDialogueGraph itself (next / falseNext / varInputNode / choices / triggers /
    randomBranches), so the file format and the save packet are untouched.
  * NpcWorkspaceEntry306Mixin - redirects NpcDialogueGraphScreen.open() to that screen; the old
    screen still opens from the workspace's own button, so no inspector is lost.

Usage: python3 tools/patch_build306.py <base-305.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build305-node-library"
NEW_VERSION = "0.8.0-build306-workspace"
OUT_NAME = "settledlands-0.8.0-build306-workspace.jar"

MIXINS_JSON = "settledlands.mixins.json"
LANG_RU = "assets/settledlands/lang/ru_ru.json"
LANG_EN = "assets/settledlands/lang/en_us.json"

REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/NpcWorkspace306.class",
    "dev/settledlands/npc/client/NpcWorkspace306$Link.class",
    "dev/settledlands/mixin/NpcWorkspaceEntry306Mixin.class",
)
COMMON_MIXINS = ()
CLIENT_MIXINS = ("NpcWorkspaceEntry306Mixin",)

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/NpcWorkspace306.class": (
        "render", "mouseClicked", "mouseDragged", "mouseReleased", "mouseScrolled",
        "keyPressed", "onClose", "save", "links", "addNode", "switchTab"),
    "dev/settledlands/npc/client/NpcWorkspace306$Link.class": ("<init>",),
    "dev/settledlands/mixin/NpcWorkspaceEntry306Mixin.class": (
        "settledlands$openWorkspace",),
}

LANG_ADD_RU = {
    "gui.settledlands.workspace.title": "Ноды NPC",
    "gui.settledlands.workspace.legacy": "Старый редактор",
    "gui.settledlands.workspace.new_graph": "+ Создать граф",
}
LANG_ADD_EN = {
    "gui.settledlands.workspace.title": "NPC nodes",
    "gui.settledlands.workspace.legacy": "Old editor",
    "gui.settledlands.workspace.new_graph": "+ Create graph",
}

CHANGELOG_EN = (
    "Build 306 makes the node workspace the dialogue editor itself. The "
    "\u00abopen graph\u00bb entry point now opens a screen of our own that owns the graph: a dark "
    "dot canvas with pan, zoom and box select, our fifteen node types as cards with round ports, "
    "cables that follow the real links (next, false branch, value input, choices, triggers, random "
    "branches), dragging with grid snap, linking by dragging port to port, cutting a cable with the "
    "right button, delete, undo and redo, three tabs (dialogue, schedule, animation), the node "
    "library with search down the left, and save through the same CfgSave packet as before - the "
    "graph format is untouched. Field editing stays one click away in the old editor, which the "
    "workspace opens with the same graph. The painting layer and the layout are adapted from the "
    "Advanced Control Computer of Gadgets & Gizmos with the authors' permission; every node in the "
    "workspace is a SettledLands node and none of theirs. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 306: \u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440 "
    "\u0434\u0438\u0430\u043b\u043e\u0433\u0430 \u2014 \u044d\u0442\u043e "
    "\u0442\u0435\u043f\u0435\u0440\u044c \u0441\u0432\u043e\u0439 "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441 \u043d\u043e\u0434: "
    "\u0445\u043e\u043b\u0441\u0442 \u0441 \u0442\u043e\u0447\u043a\u0430\u043c\u0438, "
    "\u043d\u0430\u0448\u0438 15 \u0442\u0438\u043f\u043e\u0432 "
    "\u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0430\u043c\u0438, "
    "\u043a\u0440\u0443\u0433\u043b\u044b\u0435 \u043f\u043e\u0440\u0442\u044b, "
    "\u043a\u0430\u0431\u0435\u043b\u0438 \u043f\u043e \u0440\u0435\u0430\u043b\u044c\u043d\u044b\u043c "
    "\u0441\u0432\u044f\u0437\u044f\u043c, \u043f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u0435, "
    "\u0441\u0432\u044f\u0437\u044c \u043f\u043e\u0440\u0442-\u043f\u043e\u0440\u0442, "
    "\u0440\u0435\u0437\u043a\u0430 \u043a\u0430\u0431\u0435\u043b\u044f, undo/redo, \u0442\u0440\u0438 "
    "\u0432\u043a\u043b\u0430\u0434\u043a\u0438, \u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430 "
    "\u0441 \u043f\u043e\u0438\u0441\u043a\u043e\u043c, \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435 "
    "\u0442\u0435\u043c \u0436\u0435 \u043f\u0430\u043a\u0435\u0442\u043e\u043c. \u0420\u0435\u0434\u0430\u043a\u0442\u043e\u0440 "
    "\u043f\u043e\u043b\u0435\u0439 \u2014 \u043a\u043d\u043e\u043f\u043a\u0430 "
    "\u00ab\u0421\u0442\u0430\u0440\u044b\u0439 \u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440\u00bb. "
)


def check_methods(path, names):
    cf = classfile.parse(path.read_bytes(), path.name)
    have = {m.name for m in cf.methods}
    for name in names:
        if name not in have:
            raise SystemExit("%s has no method %s" % (path, name))
    return sorted(have)


def patch_lang(raw, additions):
    data = json.loads(raw.decode("utf-8"))
    added = 0
    for key, value in additions.items():
        if key not in data:
            data[key] = value
            added += 1
    return (json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"), added


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build305-src/out")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    classes = pathlib.Path(args.classes)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name])
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name])
        blobs[name] = src.read_bytes()
        order.append(name)
        print("  added: %s" % name)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    common = list(mixins.get("mixins", []))
    for mixin in COMMON_MIXINS:
        if mixin not in common:
            common.append(mixin)
    mixins["mixins"] = common
    client = list(mixins.get("client", []))
    for mixin in CLIENT_MIXINS:
        if mixin not in client:
            client.append(mixin)
    mixins["client"] = client
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(client)))

    for path, additions in ((LANG_RU, LANG_ADD_RU), (LANG_EN, LANG_ADD_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        blobs[path], added = patch_lang(blobs[path], additions)
        print("  %s: +%d keys" % (path, added))

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
