#!/usr/bin/env python3
"""Build 324 - the wire at a point is a wire, and a wire can be pulled out of a point.

  Two things were wrong with the first cut of the hub. The wire that waits at a point was drawn as
  a row of little rectangles, because a waiting wire has no target and the screen only draws links -
  it looked, correctly, like something that was not a cable. It is now drawn by the screen's own
  curve routine, into a buffer of its own, in the same white: the same call, the same width, the
  same dashes.

  And a point could only be aimed by dragging the point itself onto a node, which does not read as
  "pull a wire out of here". Every point now has a small white port on its right, like the ports on
  the cards. Grab the port and a wire follows the cursor, drawn by the same routine; drop it on a
  node and every wire at the point goes there. The dot is still the thing you move and delete.

Usage: python3 tools/patch_build324.py <base-323.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build323-wire-hub"
NEW_VERSION = "0.8.0-build324-pull-wire"
OUT_NAME = "settledlands-0.8.0-build324-pull-wire.jar"

REPLACED = (
    "dev/settledlands/npc/client/NpcWirePointsUi321.class",
    "dev/settledlands/npc/client/NpcWirePointsUi321$Wire.class",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class",
)

ADDED = ()

MIXIN = "NpcWirePointsMixin321"

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/NpcWirePointsUi321.class": "portAt",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class": "settledlands$drawWires",
}

LANG_RU = {}
LANG_EN = {}

CHANGELOG_EN = (
    "Build 324 draws the wire that waits at a point with the same cable routine as every other "
    "wire, instead of a row of rectangles, and adds a port to every point: pull a wire out of the "
    "port, drop it on a node and every wire at that point goes there. The dot itself is still what "
    "moves a point, and a double click or a right click still removes one. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 324: \u043f\u0440\u043e\u0432\u043e\u0434 "
    "\u0443 \u0442\u043e\u0447\u043a\u0438 \u0440\u0438\u0441\u0443\u0435\u0442\u0441\u044f "
    "\u0442\u0435\u043c \u0436\u0435 \u043a\u043e\u0434\u043e\u043c, \u0447\u0442\u043e "
    "\u0438 \u0432\u0441\u0435 \u043e\u0441\u0442\u0430\u043b\u044c\u043d\u044b\u0435 "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u0430, \u0430 \u0443 \u0442\u043e\u0447\u043a\u0438 "
    "\u0435\u0441\u0442\u044c \u043f\u043e\u0440\u0442: \u0438\u0437 \u043d\u0435\u0433\u043e "
    "\u0432\u044b\u0442\u044f\u0433\u0438\u0432\u0430\u0435\u0442\u0441\u044f "
    "\u043f\u0440\u043e\u0432\u043e\u0434, \u0438 \u043a\u0443\u0434\u0430 "
    "\u0431\u0440\u043e\u0441\u0438\u0448\u044c - \u0442\u0443\u0434\u0430 \u0438\u0434\u0443\u0442 "
    "\u0432\u0441\u0435 \u043f\u0440\u043e\u0432\u043e\u0434\u0430 \u0442\u043e\u0447\u043a\u0438. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out324"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED + ADDED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        want = EXPECTED_METHODS.get(name)
        if want and want.encode("utf-8") not in data:
            raise SystemExit("%s does not look like the build we expect (no %s)"
                             % (name, want))
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_RU),
                        ("assets/settledlands/lang/en_us.json", LANG_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        added = 0
        for key, value in table.items():
            if key not in strings:
                strings[key] = value
                added += 1
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  %s: %d keys added" % (path.rsplit("/", 1)[-1], added))

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    # The bends are drawn by the client.
    if MIXIN not in mixins.setdefault("client", []):
        mixins["client"].append(MIXIN)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(mixins.get("mixins", [])),
                                                   len(mixins["client"])))

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
