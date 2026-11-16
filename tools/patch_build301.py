#!/usr/bin/env python3
"""Build 301 - smaller blue pins that scale with the zoom, white ordinary wiring.

Two requests:

  * the blue value pins are too big and do not scale well. They were drawn at a fixed
    screen radius, so they looked oversized when zoomed out and tiny when zoomed in. Now the
    radius is a card unit value (2.0) multiplied by the canvas zoom, i.e. exactly the rule
    the stock ports follow, and they are half the size of a stock port square. The grab
    radius follows the same rule, with a small floor so a pin can still be grabbed on a
    zoomed-out graph.
  * ordinary connection ports and cables must be white instead of coloured by node type.
    The stock card paints its 10x10 edge ports in the node-type colour and the cables in one
    of several relation colours. A @Redirect on the card's fill call turns exactly those
    10x10 edge squares white and leaves every other fill alone, and a @ModifyArg on the
    seventh argument of cable()/curve() in wires() turns every stock cable white - lines,
    relation cables and the dragged rubber band. The blue value pass draws its own curves
    from another method and keeps its colour.

The pursuit card's own execution ports are white too, and its value pins are drawn after the
card transform so their radius follows the zoom like everything else.

Usage: python3 tools/patch_build301.py <base-300.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build300-wire-and-pin"
NEW_VERSION = "0.8.0-build301-pins-and-white-wiring"
OUT_NAME = "settledlands-0.8.0-build301-pins-and-white-wiring.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class",
)
ADDED = ()
COMMON_MIXINS = ()

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("drawPin", "drawValuePin",
                                                              "isPortSquare", "pinHitRadius",
                                                              "drawValuePins", "drawCheckPin"),
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class": ("row",),
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class": (
        "settledlands$whitePort",
        "settledlands$whiteCable",
        "settledlands$whiteBand",
        "settledlands$hideLinkForCard",
        "settledlands$drawValueWires"),
}

CHANGELOG_EN = (
    "Build 301 makes the blue value pins small and zoom-aware and turns ordinary wiring "
    "white. A pin is now a 2-unit dot measured in card space, so it grows and shrinks with "
    "the card exactly like a stock port, and it is half the size of one; the grab radius "
    "follows the same rule. Ordinary connection ports on the cards and ordinary cables are "
    "white instead of being coloured by node type or by relation - the ports through a "
    "narrow filter that matches only the 10x10 edge squares, the cables through the seventh "
    "argument of the two routines every stock cable goes through. The blue value wires keep "
    "their colour, the cards, text and headers are untouched, and nothing else changed. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 301: \u0441\u0438\u043d\u0438\u0435 \u043f\u0438\u043d\u044b \u0441\u0442\u0430\u043b\u0438 \u043c\u0430\u043b\u0435\u043d\u044c\u043a\u0438\u043c\u0438 \u0438 \u043c\u0430\u0441\u0448\u0442\u0430\u0431\u0438\u0440\u0443\u044e\u0442\u0441\u044f "
    "\u0432\u043c\u0435\u0441\u0442\u0435 \u0441 \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u043e\u0439 (\u0440\u0430\u0434\u0438\u0443\u0441 \u0432 \u0435\u0434\u0438\u043d\u0438\u0446\u0430\u0445 \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0438 \u00d7 \u0437\u0443\u043c), \u043a\u0430\u043a \u0443 "
    "\u043e\u0431\u044b\u0447\u043d\u044b\u0445 \u043f\u043e\u0440\u0442\u043e\u0432. \u041e\u0431\u044b\u0447\u043d\u044b\u0435 \u043f\u043e\u0440\u0442\u044b \u043a\u0430\u0440\u0442\u043e\u0447\u0435\u043a \u0438 \u043e\u0431\u044b\u0447\u043d\u044b\u0435 \u043a\u0430\u0431\u0435\u043b\u0438 "
    "\u0442\u0435\u043f\u0435\u0440\u044c \u0431\u0435\u043b\u044b\u0435, \u0430 \u043d\u0435 \u0440\u0430\u0437\u043d\u043e\u0446\u0432\u0435\u0442\u043d\u044b\u0435. \u0421\u0438\u043d\u0438\u0435 \u043f\u0440\u043e\u0432\u043e\u0434\u0430 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0439 "
    "\u043e\u0441\u0442\u0430\u043b\u0438\u0441\u044c \u0441\u0438\u043d\u0438\u043c\u0438. "
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
    ap.add_argument("--classes", default="build301-src/out")
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
