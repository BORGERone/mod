#!/usr/bin/env python3
"""Build 310 - the blue wire from the "Урон" node into a pursuit node.

Build 309 rolled the editor back to the 303 graph. This build is a single bug fix on top of it.

The blue value pin of the "Урон" node could be dragged into a pursuit node's blue input, and the
link was stored: the node kept the source id, the server read it and the NPC pursued whoever hit
it. Nothing was drawn. The canvas had two places that looked at a pursuit node's blue input, and
both of them asked whether the node on the other end was the "В радиусе" node specifically, so a
wire from the "Урон" node fell through and no cable was ever painted. The same test also left
that wire uncuttable by the middle click. Both now ask for a blue output in general.

Only one class changes: NpcPursuitValuePinMixin290.

Usage: python3 tools/patch_build310.py <base-309.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build309-classic"
NEW_VERSION = "0.8.0-build310-blue-wire"
OUT_NAME = "settledlands-0.8.0-build310-blue-wire.jar"

REPLACED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class",
)

EXPECTED_METHODS = {
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class":
        "settledlands$paintValueWires",
}

CHANGELOG_EN = (
    "Build 310 fixes the blue wire from the damage node. Drag the blue output of an \"Урон\" "
    "node into the blue input of a pursuit node and the cable is now drawn, blue, from the pin "
    "on the right edge of the damage card into the pin on the left edge of the pursuit card, and "
    "the middle click cuts it like any other wire. Before this build the link was saved and the "
    "NPC did pursue whoever hit it, but no cable appeared on the canvas. Nothing else is "
    "changed against Build 309. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 310: \u0438\u0441\u043f\u0440\u0430\u0432\u043b\u0435\u043d "
    "\u0441\u0438\u043d\u0438\u0439 \u043f\u0440\u043e\u0432\u043e\u0434 \u043e\u0442 \u043d\u043e\u0434\u044b "
    "\u00ab\u0423\u0440\u043e\u043d\u00bb. \u0422\u0435\u043f\u0435\u0440\u044c "
    "\u0440\u0438\u0441\u0443\u0435\u0442\u0441\u044f \u043e\u0442 "
    "\u043f\u0440\u0430\u0432\u043e\u0433\u043e \u043a\u0440\u0430\u044f "
    "\u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0438 \u0443\u0440\u043e\u043d\u0430 \u0432 "
    "\u0441\u0438\u043d\u0438\u0439 \u0432\u0445\u043e\u0434 \u043d\u043e\u0434\u044b "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u044f, \u0438 "
    "\u0441\u0440\u0435\u0434\u043d\u044f\u044f \u043a\u043d\u043e\u043f\u043a\u0430 "
    "\u0440\u0435\u0436\u0435\u0442 \u0435\u0433\u043e \u043a\u0430\u043a "
    "\u043b\u044e\u0431\u043e\u0439 \u0434\u0440\u0443\u0433\u043e\u0439 \u043f\u0440\u043e\u0432\u043e\u0434. "
    "\u041e\u0441\u0442\u0430\u043b\u044c\u043d\u043e\u0435 \u2014 \u043a\u0430\u043a \u0432 309. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    # Compiled classes come from a build directory next to this script's repo root.
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out310"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name not in blobs:
            raise SystemExit("base jar has no entry: %s" % name)
        want = EXPECTED_METHODS.get(name)
        if want and want.encode("utf-8") not in data:
            raise SystemExit("%s does not look like the build we expect (no %s)"
                             % (name, want))
        blobs[name] = data
        print("  replaced: %s (%d bytes)" % (name, len(data)))

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
