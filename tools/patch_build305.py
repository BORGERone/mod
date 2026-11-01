#!/usr/bin/env python3
"""Build 305 - the workspace itself, not a repaint of the old screen.

The previous build only changed colours on the stock screen. This one brings in the actual
workspace from the Advanced Control Computer of Gadgets & Gizmos, used with the authors'
permission, and puts our nodes in it:

  * WorkspaceTheme305 is the ported painting layer - rounded frames, cards with a tinted
    header and an accent dot, round ports, diamond exec ports, list rows, the theme's own
    defaults. It is plain painting: rectangles, circles, diamonds, colour blending. It knows
    nothing about either mod's graph, so our node list stays ours.
  * WorkspaceSkin304 now delegates to it: the canvas, the dots and every card are drawn by the
    ported renderer instead of a second copy of the same shapes.
  * NpcNodeLibrary305 is the library-the-nodes-live-in part of that workspace: a panel down the
    left of the canvas, a search field, a filtered list of all fifteen node types, wheel scroll,
    L to hide it. A click reports a node type and the screen's own add(int) creates the node, so
    undo, selection and the dirty flag behave exactly as before.

Usage: python3 tools/patch_build305.py <base-304.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build304-workspace-skin"
NEW_VERSION = "0.8.0-build305-node-library"
OUT_NAME = "settledlands-0.8.0-build305-node-library.jar"

MIXINS_JSON = "settledlands.mixins.json"
LANG_RU = "assets/settledlands/lang/ru_ru.json"
LANG_EN = "assets/settledlands/lang/en_us.json"

REPLACED = (
    "dev/settledlands/npc/client/WorkspaceSkin304.class",
)
ADDED = (
    "dev/settledlands/npc/client/WorkspaceTheme305.class",
    "dev/settledlands/npc/client/NpcNodeLibrary305.class",
    "dev/settledlands/mixin/NpcLibrary305Mixin.class",
)
COMMON_MIXINS = ()
CLIENT_MIXINS = ("NpcLibrary305Mixin",)

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/WorkspaceSkin304.class": (
        "drawCanvas", "drawCard", "fillCircle"),
    "dev/settledlands/npc/client/WorkspaceTheme305.class": (
        "drawNodeFrame", "drawPort", "drawExecPort", "drawRow", "drawPanel",
        "drawRoundedRect", "drawCircle", "drawDiamond", "mix"),
    "dev/settledlands/npc/client/NpcNodeLibrary305.class": (
        "draw", "contains", "entryAt", "inSearchField", "scroll", "toggle", "query", "setQuery"),
    "dev/settledlands/mixin/NpcLibrary305Mixin.class": (
        "settledlands$libraryWidgets", "settledlands$libraryRender",
        "settledlands$libraryClick", "settledlands$libraryScroll",
        "settledlands$libraryKeys"),
}

LANG_ADD_RU = {
    "gui.settledlands.workspace.library": "Ноды",
    "gui.settledlands.workspace.search": "поиск…",
    "gui.settledlands.workspace.empty": "ничего не найдено",
}
LANG_ADD_EN = {
    "gui.settledlands.workspace.library": "Nodes",
    "gui.settledlands.workspace.search": "search…",
    "gui.settledlands.workspace.empty": "nothing found",
}

CHANGELOG_EN = (
    "Build 305 moves the node editor onto the node workspace instead of repainting the old "
    "screen. The painting layer of the workspace - rounded card frames with a header tinted "
    "towards the node's accent and an accent dot, round data ports, diamond execution ports, "
    "list rows, panels and the theme's own palette - is now ours, adapted from the Advanced "
    "Control Computer of Gadgets & Gizmos with the authors' permission, and every card, cable "
    "and dot on the canvas is drawn by it. On top of it sits the node library: a panel down the "
    "left of the canvas with a search field and the list of all fifteen SettledLands node types "
    "- line, button, trigger, if, variable, condition, damage, time, timer, animation, quest, "
    "move, mobility, random, cutscene. Type to filter, click an entry to add that node, roll the "
    "wheel to scroll, L to hide the panel. Nodes are created by the screen's own add(), so undo, "
    "selection and the unsaved marker behave as before, and nothing about the graph format "
    "changed: every existing dialogue loads untouched. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 305: \u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440 "
    "\u043f\u0435\u0440\u0435\u0435\u0445\u0430\u043b \u043d\u0430 "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441 \u043d\u043e\u0434, "
    "\u0430 \u043d\u0435 \u043d\u0430 \u043f\u0435\u0440\u0435\u043a\u0440\u0430\u0441\u043a\u0443 "
    "\u0441\u0442\u0430\u0440\u043e\u0433\u043e \u044d\u043a\u0440\u0430\u043d\u0430. "
    "\u0421\u043b\u043e\u0439 \u0440\u0438\u0441\u043e\u0432\u0430\u043d\u0438\u044f "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441\u0430 "
    "(\u0440\u0430\u043c\u043a\u0438, \u0448\u0430\u043f\u043a\u0430 "
    "\u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0438, \u043a\u0440\u0443\u0433\u043b\u044b\u0435 "
    "\u043f\u043e\u0440\u0442\u044b, \u0441\u043f\u0438\u0441\u043a\u0438, "
    "\u043f\u0430\u043b\u0438\u0442\u0440\u0430) \u0432\u0437\u044f\u0442 \u0438\u0437 "
    "Gadgets & Gizmos \u0441 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043d\u0438\u044f "
    "\u0430\u0432\u0442\u043e\u0440\u043e\u0432, \u043d\u043e\u0434\u044b \u2014 "
    "\u043d\u0430\u0448\u0438. \u0421\u043b\u0435\u0432\u0430 \u043e\u0442 "
    "\u0445\u043e\u043b\u0441\u0442\u0430 \u2014 \u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430 "
    "\u043d\u043e\u0434 \u0441 \u043f\u043e\u0438\u0441\u043a\u043e\u043c: \u0432\u0441\u0435 15 "
    "\u0442\u0438\u043f\u043e\u0432, \u043a\u043b\u0438\u043a \u0434\u043e\u0431\u0430\u0432\u043b\u044f\u0435\u0442 "
    "\u0443\u0437\u0435\u043b, L \u0441\u043a\u0440\u044b\u0432\u0430\u0435\u0442 "
    "\u043f\u0430\u043d\u0435\u043b\u044c. "
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
