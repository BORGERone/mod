#!/usr/bin/env python3
"""Build 300 - why the blue cable vanished, and a smaller round pin.

Reported on 299: the condition now works, but the blue wire's display is still broken.
Found it in the code, not by guessing:

  * build 299 takes the body link out of varInputNode for the whole cable pass (wires HEAD)
    and puts it back at the end. The gate that decides whether to draw ANY blue cable,
    PursuitGraphNode272#hasValueWires, read inputSource(node) - the field that is -1 during
    that pass. So the pass decided "no wires" and drew nothing: the cable disappeared, and
    came back only while dragging. That is the "lag" - the line is there or not depending on
    the pass state, not on the graph.
  * the same read was used by wiredTo(), so the count next to the pin was wrong too.
  * and the card hook was unbalanced: it hid the link at drawNode HEAD for every node,
    including pursuit cards, which are drawn by a bridge that cancels drawNode - the RETURN
    hook never ran there, so the hide leaked into the next frame.

Fixes:

  * hasValueWires and wiredTo ask stockInputSource(), which answers with the hidden link
    while a pass has it hidden - so the cable is drawn every frame;
  * the card hook is skipped for pursuit nodes, so hide and restore always pair up;
  * a repair-only step at render HEAD puts back a link any earlier frame failed to restore;
  * the blue pins are small round dots (radius 3) instead of an 11-pixel cross, on the check
    node, on the stock inputs and on the pursuit card, and the hit radius follows the dot.

Usage: python3 tools/patch_build300.py <base-299.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build299-if-card-scoped"
NEW_VERSION = "0.8.0-build300-wire-and-pin"
OUT_NAME = "settledlands-0.8.0-build300-wire-and-pin.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class",
)
ADDED = ()
COMMON_MIXINS = ()

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("drawPin", "drawValuePins",
                                                              "hasValueWires", "wiredTo",
                                                              "stockInputSource",
                                                              "resetStockInputHide"),
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class": ("row",),
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class": (
        "settledlands$hideLinkForCard",
        "settledlands$restoreLinkAfterCard",
        "settledlands$repairStockLinks",
        "settledlands$drawValueWires"),
}

CHANGELOG_EN = (
    "Build 300 fixes the blue wire drawing and makes the blue pins small round dots. The "
    "cable was not lagging - it was not drawn at all: the pass that decides whether any blue "
    "cable exists read the link field while the link was temporarily taken out of the node, "
    "answered \u00abno wires\u00bb and drew nothing. It now reads the link through the same helper the "
    "drawing itself uses, so the cable is drawn every frame. The card hook is skipped for "
    "pursuit cards, which are drawn by a bridge that cancels the method, so a hide can no "
    "longer leak into the next frame; a repair step at the start of each frame puts back "
    "anything left over. The value pins on the check node, on the stock inputs and on the "
    "pursuit card are now a 3-pixel round dot instead of an 11-pixel cross, and the grab "
    "radius follows the dot. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 300: \u0441\u0438\u043d\u0438\u0439 \u043f\u0440\u043e\u0432\u043e\u0434 \u0441\u043d\u043e\u0432\u0430 \u0440\u0438\u0441\u0443\u0435\u0442\u0441\u044f \u043a\u0430\u0436\u0434\u044b\u0439 \u043a\u0430\u0434\u0440 "
    "(\u0440\u0430\u043d\u044c\u0448\u0435 \u043f\u0440\u043e\u0445\u043e\u0434 \u0441\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u043b \u043f\u043e\u043b\u0435 \u0441\u0432\u044f\u0437\u0438, \u043f\u043e\u043a\u0430 \u0441\u0432\u044f\u0437\u044c \u0431\u044b\u043b\u0430 \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u0441\u043d\u044f\u0442\u0430, "
    "\u0438 \u0440\u0435\u0448\u0430\u043b, \u0447\u0442\u043e \u043f\u0440\u043e\u0432\u043e\u0434\u043e\u0432 \u043d\u0435\u0442). \u0421\u043d\u044f\u0442\u0438\u0435 \u0441\u0432\u044f\u0437\u0438 \u043d\u0430 \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0435 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 "
    "\u043f\u0440\u043e\u0442\u0435\u043a\u0430\u0435\u0442 \u0432 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439 \u043a\u0430\u0434\u0440. \u0421\u0438\u043d\u0438\u0435 \u043f\u0438\u043d\u044b \u0442\u0435\u043f\u0435\u0440\u044c \u043c\u0430\u043b\u0435\u043d\u044c\u043a\u0438\u0435 \u043a\u0440\u0443\u0433\u043b\u044b\u0435 "
    "\u0442\u043e\u0447\u043a\u0438 (\u0440\u0430\u0434\u0438\u0443\u0441 3) \u0432\u043c\u0435\u0441\u0442\u043e \u0431\u043e\u043b\u044c\u0448\u043e\u0433\u043e \u043f\u043b\u044e\u0441\u0430. "
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
    ap.add_argument("--classes", default="build300-src/out")
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
