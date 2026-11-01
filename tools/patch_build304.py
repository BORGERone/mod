#!/usr/bin/env python3
"""Build 304 - the node workspace in the style of Gadgets & Gizmos.

Four requests:

  * the blue value wires stopped being drawn in 301. The blue pass itself did not change -
    it is byte for byte the 300 code - but the white-wiring hooks were added to the same
    mixin class, and one of them (the @Redirect on the card's own fill call) had no target
    to attach to. Mixin drops the whole class when a handler cannot be attached, so the blue
    pass went with it. The whites now live in mixin classes of their own, with require = 0,
    so a hook that cannot attach leaves the colours alone instead of taking the blue wires
    down - the cable whitening and the port whitening are even separated from each other,
    because the port hook is the one that targets a Minecraft method and the one that can go
    missing. The blue class is byte for byte the 300 class that is known to work.
  * the pursuit node's stop switch moved up: it sits right under the target-type switch
    instead of below six rows, so it cannot be pushed out of the visible part of the panel.
  * the blue pin of an "In Radius" node is drawn where its wire actually starts again - the
    check card is narrower than a pursuit card, so the pursuit card's coordinates put it off
    the port.
  * the pursuit itself now walks at what used to be the catch-up speed, and the catch-up is
    faster than that (1.4x on top of it before the node's own boost). The node's own
    catch-up clamp, the boost ramp and every saved node are unchanged.

Usage: python3 tools/patch_build304.py <base-302.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build303-wires-stop-speed"
NEW_VERSION = "0.8.0-build304-workspace-skin"
OUT_NAME = "settledlands-0.8.0-build304-workspace-skin.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/mixin/NpcWhiteWiring302.class",
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
)
ADDED = (
    "dev/settledlands/npc/client/WorkspaceSkin304.class",
    "dev/settledlands/mixin/NpcWorkspaceMixin304.class",
)
COMMON_MIXINS = ()
CLIENT_MIXINS = ("NpcWorkspaceMixin304",)

EXPECTED_METHODS = {
    "dev/settledlands/mixin/NpcWhiteWiring302.class": (
        "settledlands$whiteCable", "settledlands$whiteBand"),
    "dev/settledlands/mixin/NpcWorkspaceMixin304.class": (
        "settledlands$workspaceCanvas", "settledlands$cardSkin"),
    "dev/settledlands/npc/client/WorkspaceSkin304.class": (
        "drawCanvas", "drawCard", "fillCircle"),
    "dev/settledlands/npc/client/PursuitGraphNode272.class": (
        "drawNode", "drawCheckPin", "pinHitRadius", "stopFlag"),
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class": ("row",),
}

CHANGELOG_EN = (
    "Build 304 gives the node graph the workspace of the Advanced Control Computer from "
    "Gadgets & Gizmos: a very dark canvas with a dot grid instead of the line grid, every card "
    "in a one pixel frame with an accent bar along its top edge, round ports with a dark rim "
    "instead of the coloured squares, the selected card framed in amber, and the cables in the "
    "theme's warm grey. The colours are the theme's own defaults (canvas #0D1215, dots "
    "#1B2429, panel #202A2F, border #39464C, accent #C6873F, text #E8E0CD). Nothing about the "
    "graph changed: same nodes, same text, same clicks, same saved data, and the blue value "
    "wires keep their colour. Both new hooks sit on the screen's own methods, so this build "
    "cannot fail on a Minecraft method name. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 304: \u0440\u0430\u0431\u043e\u0447\u0430\u044f "
    "\u043e\u0431\u043b\u0430\u0441\u0442\u044c \u043d\u043e\u0434 \u043a\u0430\u043a \u0432 "
    "Gadgets & Gizmos: \u0442\u0451\u043c\u043d\u044b\u0439 \u0445\u043e\u043b\u0441\u0442 \u0432 "
    "\u0442\u043e\u0447\u043a\u0443, \u0440\u0430\u043c\u043a\u0430 \u0438 \u0430\u043a\u0446\u0435\u043d\u0442 "
    "\u043d\u0430 \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0430\u0445, \u043a\u0440\u0443\u0433\u043b\u044b\u0435 "
    "\u043f\u043e\u0440\u0442\u044b, \u043f\u0430\u043b\u0438\u0442\u0440\u0430 \u0442\u0435\u043c\u044b. "
)


def check_methods(path, names):
    cf = classfile.parse(path.read_bytes(), path.name)
    have = {m.name for m in cf.methods}
    for name in names:
        if name not in have:
            raise SystemExit("%s has no method %s" % (path, name))
    return sorted(have)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build304-src/out")
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
    print("  mixins.json: %d common, %d client" % (len(common), len(mixins.get("client", []))))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    for stale in ('displayName="Settled Lands"',
                  'displayName="Settled Lands %s"' % BASE_VERSION):
        if stale in toml:
            toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
            break
    else:
        raise SystemExit("base toml has no displayName to bump")
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN.replace("\\u00ab", "\u00ab")
                        .replace("\\u00bb", "\u00bb") + "\n\n", 1)
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
