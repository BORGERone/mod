#!/usr/bin/env python3
"""Build 302 - the blue wires again, the stop switch back, and a faster pursuit.

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

Usage: python3 tools/patch_build302.py <base-301.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build301-pins-and-white-wiring"
NEW_VERSION = "0.8.0-build302-wires-stop-speed"
OUT_NAME = "settledlands-0.8.0-build302-wires-stop-speed.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class",
    "dev/settledlands/mixin/NpcPursuitInspectorMixin277.class",
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/npc/NpcPursuit269.class",
    "dev/settledlands/npc/NpcPursuit269$Published.class",
    "dev/settledlands/npc/NpcPursuit269$SlotCache.class",
    "dev/settledlands/npc/NpcPursuit269$State.class",
    "dev/settledlands/npc/NpcPursuit269$TargetCache.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcWhiteWiring302.class",
    "dev/settledlands/mixin/NpcWhitePorts302.class",
)
COMMON_MIXINS = ()
CLIENT_MIXINS = ("NpcWhiteWiring302", "NpcWhitePorts302")

EXPECTED_METHODS = {
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class": (
        "settledlands$drawValueWires", "settledlands$paintValueWires",
        "settledlands$hideStockIfWires"),
    "dev/settledlands/mixin/NpcPursuitInspectorMixin277.class": (
        "settledlands$panel", "settledlands$buildPursuitInspector"),
    "dev/settledlands/mixin/NpcWhiteWiring302.class": (
        "settledlands$whiteCable", "settledlands$whiteBand"),
    "dev/settledlands/mixin/NpcWhitePorts302.class": ("settledlands$whitePort",),
    "dev/settledlands/npc/client/PursuitGraphNode272.class": (
        "drawCheckPin", "drawValuePin", "drawValuePins", "pinHitRadius", "isPortSquare"),
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class": ("row",),
    "dev/settledlands/npc/NpcPursuit269.class": ("tick", "start", "stop"),
    "dev/settledlands/npc/NpcPursuit269$Published.class": (),
    "dev/settledlands/npc/NpcPursuit269$SlotCache.class": (),
    "dev/settledlands/npc/NpcPursuit269$State.class": (),
    "dev/settledlands/npc/NpcPursuit269$TargetCache.class": (),
}

CHANGELOG_EN = (
    "Build 302 brings the blue value wires back, puts the pursuit node's stop switch where "
    "it can be seen, and speeds the pursuit up. The blue wires are drawn by the same code "
    "that drew them before; the build 301 hooks that paint ordinary wiring white moved into "
    "a mixin class of their own that is allowed to attach to nothing, so a hook that cannot "
    "find its target can never take the blue wires with it again. The stop switch now sits "
    "directly under the target-type switch at the top of the pursuit inspector, above the "
    "target, standoff, catch-up and boost rows, so it is on the first screenful and cannot be "
    "clipped off the panel. The blue pin of an \u00abIn Radius\u00bb node is drawn on the port "
    "its wire starts from again. The pursuit walks at what used to be its catch-up speed "
    "(about a quarter faster than before), and the catch-up starts at 1.4x of that and ramps "
    "up with the node's own boost, so it is always faster than the pursuit itself. Existing "
    "nodes keep their saved settings. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 302: \u0441\u0438\u043d\u0438\u0435 \u043f\u0440\u043e\u0432\u043e\u0434\u0430 "
    "\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0439 \u0441\u043d\u043e\u0432\u0430 \u0432\u0438\u0434\u043d\u044b; "
    "\u0442\u0443\u043c\u0431\u043b\u0435\u0440 \u00ab\u0417\u0430\u0432\u0435\u0440\u0448\u0438\u0442\u044c "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u0435\u00bb \u0441\u0442\u043e\u0438\u0442 "
    "\u0432\u0442\u043e\u0440\u044b\u043c \u0432 \u043f\u0430\u043d\u0435\u043b\u0438 \u043d\u043e\u0434\u044b; "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u0435 \u0438\u0434\u0451\u0442 \u0441\u043e "
    "\u0441\u043a\u043e\u0440\u043e\u0441\u0442\u044c\u044e \u0431\u044b\u0432\u0448\u0435\u0433\u043e \u0434\u043e\u0433\u043e\u043d\u0430, "
    "\u0430 \u0434\u043e\u0433\u043e\u043d \u0435\u0449\u0451 \u0431\u044b\u0441\u0442\u0440\u0435\u0435. "
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
    ap.add_argument("--classes", default="build302-src/out")
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
